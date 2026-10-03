package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class SceneContextBatchCommitFenceTest {
    public static void main(String[] args)throws Exception{
        int[] seed={91,92,93,94};
        OutboundPacketQueue queue=
            new OutboundPacketQueue(
                1024
            );
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    seed
                )
            );
        SceneCoordinateContext context=
            new SceneCoordinateContext(
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y,
                0
            );
        SceneUpdatePublisher publisher=
            new SceneUpdatePublisher(
                writer,
                context
            );

        int initialChunkX=8;
        int initialChunkY=8;
        context.setCurrent(
            initialChunkX,
            initialChunkY
        );

        Tile target=
            new Tile(
                MovementState.REGION_BASE_X+24,
                MovementState.REGION_BASE_Y+24,
                0
            );
        int targetChunkX=
            context.chunkXFor(
                target.x
            );
        int targetChunkY=
            context.chunkYFor(
                target.y
            );

        SceneCoordinateContext.Snapshot snapshot=
            LocalSession.snapshotWorldTickSceneContext(
                publisher
            );

        queue.offer(
            new byte[1020]
        );

        writer.beginBatch();
        publisher.groundSpawn(
            995,
            100,
            target
        );

        require(
            context.isCurrent(
                targetChunkX,
                targetChunkY
            ),
            "prospective scene context did not advance"
        );

        boolean admissionFailed=false;

        try{
            LocalSession.endWorldTickBatch(
                writer
            );
        }catch(IOException expected){
            admissionFailed=true;
        }

        require(
            admissionFailed,
            "world-tick batch admission unexpectedly succeeded"
        );

        LocalSession.restoreWorldTickSceneContext(
            publisher,
            snapshot
        );

        require(
            context.isCurrent(
                initialChunkX,
                initialChunkY
            ),
            "world-tick abort did not restore scene context"
        );

        require(
            queue.queuedBytes()==1020,
            "failed batch leaked scene bytes"
        );

        drain(queue);

        writer.beginBatch();
        publisher.groundSpawn(
            995,
            100,
            target
        );
        LocalSession.endWorldTickBatch(
            writer
        );

        byte[] retryBytes=
            drain(queue);

        require(
            context.isCurrent(
                targetChunkX,
                targetChunkY
            ),
            "successful retry did not retain target scene context"
        );

        OutboundPacketQueue controlQueue=
            new OutboundPacketQueue(
                1024
            );
        ServerPacketWriter controlWriter=
            new ServerPacketWriter(
                controlQueue,
                new IsaacCipher(
                    seed
                )
            );
        SceneCoordinateContext controlContext=
            new SceneCoordinateContext(
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y,
                0
            );
        controlContext.setCurrent(
            initialChunkX,
            initialChunkY
        );
        SceneUpdatePublisher controlPublisher=
            new SceneUpdatePublisher(
                controlWriter,
                controlContext
            );

        controlWriter.beginBatch();
        controlPublisher.groundSpawn(
            995,
            100,
            target
        );
        LocalSession.endWorldTickBatch(
            controlWriter
        );

        byte[] controlBytes=
            drain(controlQueue);

        require(
            Arrays.equals(
                retryBytes,
                controlBytes
            ),
            "retry bytes differ from fresh scene-base publication"
        );

        OutboundPacketQueue invalidateQueue=
            new OutboundPacketQueue(
                1024
            );
        ServerPacketWriter invalidateWriter=
            new ServerPacketWriter(
                invalidateQueue,
                new IsaacCipher(
                    new int[]{95,96,97,98}
                )
            );
        SceneCoordinateContext invalidateContext=
            new SceneCoordinateContext(
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y,
                0
            );
        invalidateContext.setCurrent(
            initialChunkX,
            initialChunkY
        );
        SceneUpdatePublisher invalidatePublisher=
            new SceneUpdatePublisher(
                invalidateWriter,
                invalidateContext
            );
        SceneCoordinateContext.Snapshot invalidateSnapshot=
            LocalSession.snapshotWorldTickSceneContext(
                invalidatePublisher
            );

        invalidateQueue.offer(
            new byte[1020]
        );
        invalidateWriter.beginBatch();
        invalidatePublisher.clear8x8(
            new Tile(
                MovementState.REGION_BASE_X+8,
                MovementState.REGION_BASE_Y+8,
                0
            )
        );

        require(
            invalidateContext.currentChunkX()==-1&&
            invalidateContext.currentChunkY()==-1,
            "clear8x8 did not prospectively invalidate scene context"
        );

        boolean invalidateFailed=false;

        try{
            LocalSession.endWorldTickBatch(
                invalidateWriter
            );
        }catch(IOException expected){
            invalidateFailed=true;
        }

        require(
            invalidateFailed,
            "clear8x8 outer admission unexpectedly succeeded"
        );

        LocalSession.restoreWorldTickSceneContext(
            invalidatePublisher,
            invalidateSnapshot
        );

        require(
            invalidateContext.isCurrent(
                initialChunkX,
                initialChunkY
            ),
            "aborted clear8x8 did not restore scene context"
        );

        System.out.println(
            "SCENE_CONTEXT_BATCH_COMMIT_FENCE_PASS "+
            "abortRestoresChunkBase=true "+
            "retryReemitsBase=true "+
            "commitAdvancesChunkBase=true "+
            "invalidateAbortRestores=true"
        );
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

    private SceneContextBatchCommitFenceTest(){}
}
