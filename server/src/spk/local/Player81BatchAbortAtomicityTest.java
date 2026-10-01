package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class Player81BatchAbortAtomicityTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        world.registerPlayer(
            source,
            "player81-batch-source"
        );
        world.registerPlayer(
            viewer,
            "player81-batch-viewer"
        );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue();
        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );
        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(
                    new int[]{5,6,7,8}
                )
            );

        Player81WorldSync.register(
            sourceWriter,
            world,
            source,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.register(
            viewerWriter,
            world,
            viewer,
            new DevAuthorityWorkbench()
        );

        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        MovementState sourceMovement=
            new MovementState();
        MovementState viewerMovement=
            new MovementState();

        SharedNpcWorldRelay.register(
            sourceWriter,
            world,
            source,
            sourceNpcs,
            sourceMovement
        );
        SharedNpcWorldRelay.register(
            viewerWriter,
            world,
            viewer,
            viewerNpcs,
            viewerMovement
        );

        try{
            NpcEntity[] sharedHome=
                bootstrapSharedHome(
                    world,
                    sourceNpcs,
                    viewerNpcs,
                    sourceMovement,
                    viewerMovement,
                    sourceWriter,
                    viewerWriter
                );
            NpcEntity sourceTarget=
                sharedHome[0];

            sourceWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );
            viewerWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            sourceWriter.varShort(
                81,
                CombatSync.player81AnimationAndInteraction(
                    15552,
                    sourceTarget.sceneIndex
                )
            );

            int packetsBeforeMask=
                viewerQueue.queuedPackets();

            sourceNpcs.sendMask(
                sourceTarget,
                NpcSyncEncoder.Mask.singleHit(
                    100,
                    6,
                    255,
                    255
                ),
                sourceWriter
            );

            if(viewerQueue.queuedPackets()!=
                    packetsBeforeMask)
                throw new AssertionError(
                    "NPC mask overtook source Player81 barrier"
                );

            long sequenceBeforeAbort=
                sequence(world);
            int packetsBeforeAbort=
                viewerQueue.queuedPackets();

            viewerWriter.beginBatch();
            viewerWriter.varShort(
                81,
                BootstrapPackets.player81WalkStep(4)
            );

            if(sequence(world)!=
                    sequenceBeforeAbort)
                throw new AssertionError(
                    "staged Player81 mutated shared sequence before commit"
                );
            if(viewerQueue.queuedPackets()!=
                    packetsBeforeAbort)
                throw new AssertionError(
                    "staged Player81 emitted before commit"
                );

            viewerWriter.abortBatch();

            if(sequence(world)!=
                    sequenceBeforeAbort)
                throw new AssertionError(
                    "aborted Player81 retained shared publication state"
                );
            if(viewerQueue.queuedPackets()!=
                    packetsBeforeAbort)
                throw new AssertionError(
                    "aborted Player81 leaked transport bytes"
                );

            viewerWriter.beginBatch();
            viewerWriter.varShort(
                81,
                BootstrapPackets.player81WalkStep(4)
            );

            if(sequence(world)!=
                    sequenceBeforeAbort)
                throw new AssertionError(
                    "retry staged Player81 committed before batch admission"
                );

            viewerWriter.endBatch();

            if(sequence(world)<=
                    sequenceBeforeAbort)
                throw new AssertionError(
                    "successful Player81 batch did not commit publication state"
                );

            int packetDelta=
                viewerQueue.queuedPackets()-
                packetsBeforeAbort;

            if(packetDelta<2)
                throw new AssertionError(
                    "pending NPC mask barrier was lost across abort packetDelta="+
                    packetDelta
                );

            testOwnershipCommitBarrier();

            System.out.println(
                "PLAYER81_BATCH_ABORT_ATOMICITY_PASS "+
                "precommitStateStable=true "+
                "abortStateStable=true "+
                "zeroAbortBytes=true "+
                "retryCommittedOnce=true "+
                "relayBarrierRetained=true "+
                "transportSemanticOwnershipAtomic=true"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                sourceWriter
            );
            SharedNpcWorldRelay.unregister(
                viewerWriter
            );
            Player81WorldSync.unregister(
                sourceWriter
            );
            Player81WorldSync.unregister(
                viewerWriter
            );

            if(source.registered())
                world.unregisterPlayer(
                    source
                );
            if(viewer.registered())
                world.unregisterPlayer(
                    viewer
                );

            world.close();
        }
    }

    private static void testOwnershipCommitBarrier()
        throws Exception
    {
        World world=
            World.isolatedForTest(601L);
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "player81-owner-barrier"
        );

        BlockingOutputStream output=
            new BlockingOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                output,
                new IsaacCipher(
                    new int[]{9,10,11,12}
                )
            );

        Player81WorldSync.register(
            writer,
            world,
            player,
            new DevAuthorityWorkbench()
        );

        try{
            writer.beginBatch();
            writer.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            Player81WorldSync.PreparedBatch prepared=
                preparedBatch(writer);

            if(prepared==null||
               prepared.completed)
                throw new AssertionError(
                    "ownership barrier fixture did not stage Player81"
                );

            AtomicReference<Throwable> commitFailure=
                new AtomicReference<>();
            AtomicReference<Throwable> unregisterFailure=
                new AtomicReference<>();
            boolean[] unregistered=
                new boolean[]{false};

            Thread commitThread=
                new Thread(
                    ()->{
                        try{
                            writer.endBatch();
                        }catch(Throwable failure){
                            commitFailure.set(
                                failure
                            );
                        }
                    },
                    "player81-owner-commit"
                );

            CountDownLatch unregisterStarted=
                new CountDownLatch(1);
            Thread unregisterThread=
                new Thread(
                    ()->{
                        unregisterStarted.countDown();

                        try{
                            unregistered[0]=
                                world.unregisterPlayer(
                                    player
                                );
                        }catch(Throwable failure){
                            unregisterFailure.set(
                                failure
                            );
                        }
                    },
                    "player81-owner-unregister"
                );

            commitThread.start();

            if(!output.entered.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "packet81 transport did not reach blocking write"
                );

            unregisterThread.start();

            if(!unregisterStarted.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "unregister thread did not start"
                );

            unregisterThread.join(200L);

            if(!unregisterThread.isAlive())
                throw new AssertionError(
                    "unregister interleaved before transport/semantic commit completed"
                );

            output.release.countDown();

            commitThread.join(5_000L);
            unregisterThread.join(5_000L);

            if(commitThread.isAlive()||
               unregisterThread.isAlive())
                throw new AssertionError(
                    "ownership barrier threads did not complete"
                );

            if(commitFailure.get()!=null)
                throw new AssertionError(
                    "packet81 ownership-coupled commit failed",
                    commitFailure.get()
                );

            if(unregisterFailure.get()!=null)
                throw new AssertionError(
                    "player unregister failed",
                    unregisterFailure.get()
                );

            if(!unregistered[0])
                throw new AssertionError(
                    "player unregister did not complete after commit"
                );

            if(!prepared.completed)
                throw new AssertionError(
                    "semantic Player81 postimage was not committed before unregister"
                );

            if(output.bytes.size()==0)
                throw new AssertionError(
                    "ownership-coupled packet81 commit emitted no bytes"
                );
        }finally{
            output.release.countDown();
            Player81WorldSync.unregister(
                writer
            );

            if(player.registered())
                world.unregisterPlayer(
                    player
                );

            world.close();
        }
    }

    private static Player81WorldSync.PreparedBatch preparedBatch(
        ServerPacketWriter writer
    )throws Exception{
        Field field=
            ServerPacketWriter.class
                .getDeclaredField(
                    "batchPlayer81"
                );
        field.setAccessible(true);
        return (Player81WorldSync.PreparedBatch)
            field.get(writer);
    }

    private static final class BlockingOutputStream
        extends OutputStream
    {
        final ByteArrayOutputStream bytes=
            new ByteArrayOutputStream();
        final CountDownLatch entered=
            new CountDownLatch(1);
        final CountDownLatch release=
            new CountDownLatch(1);

        @Override public void write(int value)
            throws IOException
        {
            write(
                new byte[]{(byte)value},
                0,
                1
            );
        }

        @Override public void write(
            byte[] data,
            int offset,
            int length
        )throws IOException{
            entered.countDown();

            try{
                if(!release.await(
                        5,
                        TimeUnit.SECONDS))
                    throw new IOException(
                        "blocking transport release timeout"
                    );
            }catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                throw new IOException(
                    "blocking transport interrupted",
                    interrupted
                );
            }

            bytes.write(
                data,
                offset,
                length
            );
        }
    }

    private static NpcEntity[] bootstrapSharedHome(
        World world,
        NpcRegistry first,
        NpcRegistry second,
        MovementState firstMovement,
        MovementState secondMovement,
        ServerPacketWriter firstWriter,
        ServerPacketWriter secondWriter
    )throws Exception{
        HomeWorldRuntimePlan firstHome=
            new HomeWorldRuntimePlan(
                world.homeNpcs()
            );
        HomeWorldRuntimePlan secondHome=
            new HomeWorldRuntimePlan(
                world.homeNpcs()
            );

        first.bootstrapHome(
            firstWriter,
            firstMovement,
            new PetState(),
            firstHome
        );
        second.bootstrapHome(
            secondWriter,
            secondMovement,
            new PetState(),
            secondHome
        );

        for(NpcEntity candidate:
                first.snapshot()){
            EntityId id=
                candidate.canonicalId();

            if(id==null)
                continue;

            NpcEntity other=
                second.canonical(id);

            if(other!=null&&
               other.definitionId==
                    candidate.definitionId)
                return new NpcEntity[]{
                    candidate,
                    other
                };
        }

        throw new AssertionError(
            "no shared canonical HOME NPC visible to both viewers"
        );
    }

    private static long sequence(
        World world
    )throws Exception{
        Object state=
            worldState(world);

        if(state==null)
            return 0L;

        Field field=
            state.getClass()
                .getDeclaredField(
                    "sequence"
                );
        field.setAccessible(true);
        return field.getLong(state);
    }

    private static Object worldState(
        World world
    )throws Exception{
        Field field=
            Player81WorldSync.class
                .getDeclaredField(
                    "BY_WORLD"
                );
        field.setAccessible(true);

        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            Map<World,?> states=
                (Map<World,?>)
                    field.get(null);
            return states.get(world);
        }
    }

    private Player81BatchAbortAtomicityTest(){}
}
