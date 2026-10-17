package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class SceneContextBatchCommitFenceTest {
    private static final int QUEUE_CAPACITY=1024;
    private static final int[] SEED=
        new int[]{801,802,803,804};

    public static void main(String[] args)throws Exception{
        assertBaseAbortRetryCommit();
        assertInvalidateAbortRestore();

        System.out.println(
            "SCENE_CONTEXT_BATCH_COMMIT_FENCE_PASS "+
            "abortRestoresChunkBase=true "+
            "retryReemitsBase=true "+
            "commitAdvancesChunkBase=true "+
            "invalidateAbortRestores=true"
        );
    }

    private static void assertBaseAbortRetryCommit()
        throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    SEED.clone()
                )
            );
        SceneCoordinateContext context=
            new SceneCoordinateContext(
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y,
                0
            );
        context.setCurrent(
            0,
            0
        );
        SceneUpdatePublisher publisher=
            new SceneUpdatePublisher(
                writer,
                context
            );

        LocalSession.ScenePublisherBatchSnapshot snapshot=
            LocalSession.ScenePublisherBatchSnapshot
                .capture(
                    publisher
                );

        OutboundPacketQueue.BatchReservation pressure=
            OutboundPacketQueue.reserveBatch(
                queue,
                QUEUE_CAPACITY
            );

        boolean failed=false;
        writer.beginBatch();
        publisher.groundSpawn(
            995,
            1,
            new Tile(
                MovementState.REGION_BASE_X+9,
                MovementState.REGION_BASE_Y+9,
                0
            )
        );

        if(context.currentChunkX()!=8||
           context.currentChunkY()!=8)
            throw new AssertionError(
                "prospective scene base did not advance"
            );

        try{
            LocalSession.endWorldTickBatch(
                writer
            );
        }catch(IOException expected){
            failed=true;
        }finally{
            pressure.release();
        }

        if(!failed)
            throw new AssertionError(
                "outer scene batch admission did not fail"
            );

        SceneUpdatePublisher restored=
            snapshot.abortAndRestore();

        if(restored!=publisher||
           context.currentChunkX()!=0||
           context.currentChunkY()!=0||
           queue.queuedBytes()!=0)
            throw new AssertionError(
                "scene base abort did not restore exact preimage"
            );

        writer.beginBatch();
        publisher.groundSpawn(
            995,
            1,
            new Tile(
                MovementState.REGION_BASE_X+9,
                MovementState.REGION_BASE_Y+9,
                0
            )
        );
        LocalSession.endWorldTickBatch(
            writer
        );

        if(context.currentChunkX()!=8||
           context.currentChunkY()!=8)
            throw new AssertionError(
                "successful retry did not retain committed scene base"
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        queue.drainTo(
            wire,
            Integer.MAX_VALUE
        );

        byte[] bytes=
            wire.toByteArray();

        if(bytes.length<1)
            throw new AssertionError(
                "scene retry emitted no bytes"
            );

        IsaacCipher control=
            new IsaacCipher(
                SEED.clone()
            );
        int expectedOpcode=
            (85+control.nextInt())&255;

        if((bytes[0]&255)!=expectedOpcode)
            throw new AssertionError(
                "scene retry did not re-emit opcode85 first expected="+
                expectedOpcode+
                " actual="+
                (bytes[0]&255)
            );
    }

    private static void assertInvalidateAbortRestore()
        throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{811,812,813,814}
                )
            );
        SceneCoordinateContext context=
            new SceneCoordinateContext(
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y,
                0
            );
        context.setCurrent(
            16,
            24
        );
        SceneUpdatePublisher publisher=
            new SceneUpdatePublisher(
                writer,
                context
            );
        LocalSession.ScenePublisherBatchSnapshot snapshot=
            LocalSession.ScenePublisherBatchSnapshot
                .capture(
                    publisher
                );

        OutboundPacketQueue.BatchReservation pressure=
            OutboundPacketQueue.reserveBatch(
                queue,
                QUEUE_CAPACITY
            );

        boolean failed=false;
        writer.beginBatch();
        publisher.clear8x8(
            new Tile(
                MovementState.REGION_BASE_X+16,
                MovementState.REGION_BASE_Y+24,
                0
            )
        );

        if(context.currentChunkX()!=-1||
           context.currentChunkY()!=-1)
            throw new AssertionError(
                "clear8x8 did not prospectively invalidate context"
            );

        try{
            LocalSession.endWorldTickBatch(
                writer
            );
        }catch(IOException expected){
            failed=true;
        }finally{
            pressure.release();
        }

        if(!failed)
            throw new AssertionError(
                "clear8x8 outer admission did not fail"
            );

        snapshot.abortAndRestore();

        if(context.currentChunkX()!=16||
           context.currentChunkY()!=24||
           queue.queuedBytes()!=0)
            throw new AssertionError(
                "clear8x8 abort did not restore exact context"
            );
    }

    private SceneContextBatchCommitFenceTest(){}
}
