package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class RegionRebaseBatchCommitFenceTest {
    private static final int QUEUE_CAPACITY=1024;

    private static final class Bridge
        implements LocalRegionStreamHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;

        @Override public String username(){
            return "region-batch";
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }

        @Override public void replaceScenePublisher(
            SceneUpdatePublisher replacement
        ){
            publisher=replacement;
        }

        @Override public void resetPetFollowRuntime(){}
    }

    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "region-batch"
        );

        try{
            MovementState movement=
                player.movement();

            movement.enterTransientRegion(
                4064,
                4192,
                0,
                4064,
                4192
            );

            int oldBaseX=
                movement.loadedBaseX();
            int oldBaseY=
                movement.loadedBaseY();
            boolean oldTransient=
                movement.transientRegion();

            int[] seed=
                new int[]{951,952,953,954};
            OutboundPacketQueue queue=
                new OutboundPacketQueue(
                    QUEUE_CAPACITY
                );
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(
                        seed.clone()
                    )
                );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(
                    dev
                );
            RegionLoadLifecycle lifecycle=
                new RegionLoadLifecycle();
            Bridge bridge=
                new Bridge();

            SceneUpdatePublisher oldPublisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        oldBaseX,
                        oldBaseY,
                        0
                    )
                );
            bridge.publisher=
                oldPublisher;

            LocalRegionStreamHandler handler=
                new LocalRegionStreamHandler(
                    true,
                    world,
                    player,
                    movement,
                    new HomeWorldRuntimePlan(),
                    npcs,
                    new LocalPlayerInteractionHandler(
                        world,
                        player,
                        movement,
                        player.equipment()
                    ),
                    new CombatEngine(
                        dev
                    ),
                    lifecycle,
                    bridge
                );

            OutboundPacketQueue.BatchReservation
                pressure=
                    OutboundPacketQueue.reserveBatch(
                        queue,
                        QUEUE_CAPACITY
                    );

            writer.beginBatch();

            require(
                handler.maybeStream(
                    writer,
                    "[region-batch-abort] "
                ),
                "outer-batch rebase was not staged"
            );

            require(
                movement.loadedBaseX()==4016&&
                movement.loadedBaseY()==4144&&
                movement.transientRegion(),
                "prospective rebase state did not advance"
            );
            require(
                lifecycle.pending()&&
                lifecycle.pendingSequence()==1L,
                "prospective region lifecycle did not advance"
            );
            require(
                bridge.publisher!=oldPublisher,
                "prospective scene publisher was not replaced"
            );
            require(
                handler.regionStreamBatchStaged(),
                "region rollback snapshot was not staged"
            );

            boolean failed=false;

            try{
                LocalSession.endWorldTickBatch(
                    writer
                );
            }catch(IOException expected){
                failed=true;
            }finally{
                pressure.release();
            }

            require(
                failed,
                "forced outer rebase admission failure did not escape"
            );

            require(
                handler.abortRegionStreamBatch(),
                "region rollback snapshot did not abort"
            );

            require(
                queue.queuedBytes()==0,
                "aborted rebase leaked packet bytes="+
                queue.queuedBytes()
            );
            require(
                movement.loadedBaseX()==oldBaseX&&
                movement.loadedBaseY()==oldBaseY&&
                movement.transientRegion()==oldTransient,
                "aborted rebase did not restore exact loaded window"
            );
            require(
                !lifecycle.pending()&&
                lifecycle.pendingSequence()==0L,
                "aborted rebase left stale region-load pending"
            );
            require(
                bridge.publisher==oldPublisher,
                "aborted rebase did not restore scene publisher identity"
            );
            require(
                !handler.regionStreamBatchStaged(),
                "aborted region snapshot remained staged"
            );

            writer.beginBatch();

            require(
                handler.maybeStream(
                    writer,
                    "[region-batch-retry] "
                ),
                "rebase retry was blocked"
            );

            LocalSession.endWorldTickBatch(
                writer
            );

            require(
                handler.commitRegionStreamBatch(),
                "committed rebase snapshot did not settle"
            );

            require(
                movement.loadedBaseX()==4016&&
                movement.loadedBaseY()==4144&&
                movement.transientRegion(),
                "committed rebase did not retain new loaded window"
            );
            require(
                lifecycle.pending()&&
                lifecycle.pendingSequence()==1L,
                "retry did not recreate exact region-load lifecycle"
            );
            require(
                bridge.publisher!=oldPublisher,
                "committed rebase restored stale scene publisher"
            );

            byte[] bytes=
                drain(
                    queue
                );

            ByteArrayInputStream in=
                new ByteArrayInputStream(
                    bytes
                );
            IsaacCipher decoder=
                new IsaacCipher(
                    seed.clone()
                );

            int first=
                ((in.read()&255)-
                 decoder.nextInt())&255;
            int second=
                ((in.read()&255)-
                 decoder.nextInt())&255;

            require(
                first==219,
                "retry first opcode expected 219 got="+
                first
            );
            require(
                second==73,
                "retry second opcode expected 73 got="+
                second
            );

            byte[] payload=
                Binary.readExactly(
                    in,
                    4
                );

            require(
                Arrays.equals(
                    payload,
                    BootstrapPackets.region73(
                        508,
                        524
                    )
                ),
                "retry packet73 payload drift"
            );
            require(
                in.read()==-1,
                "retry emitted unexpected extra region bytes"
            );

            System.out.println(
                "REGION_REBASE_BATCH_COMMIT_FENCE_PASS "+
                "abortRestoresLoadedWindow=true "+
                "abortClearsStalePending=true "+
                "retryReemitsRegion73=true "+
                "commitAdvancesRegionState=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );
            world.close();
        }
    }

    private static byte[] drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        queue.drainTo(
            out,
            1<<20
        );

        return out.toByteArray();
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private RegionRebaseBatchCommitFenceTest(){}
}
