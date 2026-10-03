package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class GroundPresentationBatchCommitFenceTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer viewer=new WorldPlayer();
        long generation=
            world.registerPlayer(
                viewer,
                "ground-batch-viewer"
            );

        OutboundPacketQueue queue=
            new OutboundPacketQueue();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{81,82,83,84}
                )
            );
        SceneUpdatePublisher publisher=
            new SceneUpdatePublisher(
                writer,
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0
                )
            );
        LocalGroundItemPresentationRelay relay=
            new LocalGroundItemPresentationRelay(
                world,
                viewer,
                viewer.movement()
            );

        long now=System.currentTimeMillis();

        try{
            GroundItem item=
                world.groundItems().add(
                    995,
                    100,
                    new Tile(
                        viewer.movement().x(),
                        viewer.movement().y(),
                        viewer.movement().plane()
                    ),
                    null,
                    1L,
                    false
                );
            GroundItemRegistry.BatchMutation mutation=
                new GroundItemRegistry.BatchMutation(
                    item,
                    100,
                    0
                );

            if(!world.groundItemPresentationEvents()
                    .enqueueSpawn(
                        now,
                        mutation,
                        viewer,
                        generation
                    ))
                throw new AssertionError(
                    "ground presentation event enqueue failed"
                );

            writer.beginBatch();

            if(relay.publishPending(
                    now,
                    publisher
                )!=1||
               relay.stagedDeliveryCount()!=1)
                throw new AssertionError(
                    "ground event was not staged exactly once"
                );

            if(world.groundItemPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now
                    ).size()!=1)
                throw new AssertionError(
                    "ground event consumed before packet batch commit"
                );

            writer.abortBatch();

            if(relay.abortStagedDeliveries()!=1)
                throw new AssertionError(
                    "ground staged acknowledgement was not aborted"
                );

            if(world.groundItemPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now
                    ).size()!=1)
                throw new AssertionError(
                    "aborted packet batch lost ground event"
                );

            writer.beginBatch();

            if(relay.publishPending(
                    now,
                    publisher
                )!=1||
               relay.stagedDeliveryCount()!=1)
                throw new AssertionError(
                    "ground event was not retryable after abort"
                );

            writer.endBatch();

            if(relay.commitStagedDeliveries(
                    System.currentTimeMillis()
                )!=1)
                throw new AssertionError(
                    "committed ground batch did not settle exact event"
                );

            if(!world.groundItemPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        System.currentTimeMillis()
                    ).isEmpty())
                throw new AssertionError(
                    "committed ground event remained pending"
                );

            if(queue.queuedBytes()<=0)
                throw new AssertionError(
                    "committed ground retry produced no queued bytes"
                );

            drain(queue);

            GroundItem second=
                world.groundItems().add(
                    4151,
                    1,
                    new Tile(
                        viewer.movement().x(),
                        viewer.movement().y(),
                        viewer.movement().plane()
                    ),
                    null,
                    2L,
                    false
                );
            GroundItemRegistry.BatchMutation secondMutation=
                new GroundItemRegistry.BatchMutation(
                    second,
                    1,
                    0
                );
            long staleNow=System.currentTimeMillis();

            if(!world.groundItemPresentationEvents()
                    .enqueueSpawn(
                        staleNow,
                        secondMutation,
                        viewer,
                        generation
                    ))
                throw new AssertionError(
                    "stale-generation fixture enqueue failed"
                );

            writer.beginBatch();

            if(relay.publishPending(
                    staleNow,
                    publisher
                )!=1)
                throw new AssertionError(
                    "stale-generation fixture did not stage"
                );

            writer.endBatch();

            if(!world.unregisterPlayer(
                    viewer,
                    generation))
                throw new AssertionError(
                    "could not retire viewer generation"
                );

            long replacementGeneration=
                world.registerPlayer(
                    viewer,
                    "ground-batch-viewer"
                );

            boolean staleRejected=false;

            try{
                relay.commitStagedDeliveries(
                    System.currentTimeMillis()
                );
            }catch(IllegalStateException expected){
                staleRejected=true;
            }

            if(!staleRejected)
                throw new AssertionError(
                    "stale viewer generation was acknowledged"
                );

            if(relay.stagedDeliveryCount()!=0)
                throw new AssertionError(
                    "stale staged acknowledgement not cleared"
                );

            List<WorldGroundItemPresentationEvents.Event>
                replacementPending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            viewer.id(),
                            replacementGeneration,
                            System.currentTimeMillis()
                        );

            if(!replacementPending.isEmpty())
                throw new AssertionError(
                    "old-generation ground event leaked to replacement"
                );

            System.out.println(
                "GROUND_PRESENTATION_BATCH_COMMIT_FENCE_PASS "+
                "abortRetainsEvent=true "+
                "retryPublishes=true "+
                "commitConsumesEvent=true "+
                "staleGenerationRejected=true"
            );
        }finally{
            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewer.generation()
                );

            world.close();
        }
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        queue.drainTo(
            out,
            1<<20
        );
    }

    private GroundPresentationBatchCommitFenceTest(){}
}
