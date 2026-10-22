package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

public final class RegionAckReplayCommitFenceTest {
    private static final int QUEUE_CAPACITY=1<<20;

    private static final class Bridge
        implements LocalRegionStreamHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;
        int petFollowResets;

        @Override public String username(){
            return "region-ack";
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }

        @Override public void replaceScenePublisher(
            SceneUpdatePublisher replacement
        ){
            publisher=replacement;
        }

        @Override public void resetPetFollowRuntime(){
            petFollowResets++;
        }
    }

    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "region-ack"
            );

        try{
            MovementState movement=
                player.movement();
            OutboundPacketQueue queue=
                new OutboundPacketQueue(
                    QUEUE_CAPACITY
                );
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(
                        new int[]{731,732,733,734}
                    )
                );
            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(
                    dev
                );
            HomeWorldRuntimePlan home=
                new HomeWorldRuntimePlan();
            RegionLoadLifecycle lifecycle=
                new RegionLoadLifecycle();
            Bridge bridge=
                new Bridge();

            bridge.publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        3040,
                        3480,
                        0
                    )
                );

            npcs.bootstrapHome(
                writer,
                movement,
                new PetState(),
                home
            );
            drain(
                queue
            );

            movement.enterTransientRegion(
                MovementState.INITIAL_X,
                3527,
                0,
                3040,
                3480
            );

            LocalRegionStreamHandler handler=
                new LocalRegionStreamHandler(
                    true,
                    world,
                    player,
                    movement,
                    home,
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

            if(!handler.maybeStream(
                    writer,
                    "[region-ack-setup] "
                ))
                throw new AssertionError(
                    "AUTO_HOME_REATTACH setup was not handled"
                );

            if(!movement.inHomeWindow()||
               !lifecycle.pending()||
               lifecycle.pendingSequence()!=1L)
                throw new AssertionError(
                    "AUTO_HOME_REATTACH setup did not establish pending lifecycle"
                );

            drain(
                queue
            );

            /*
             * Make the viewer-presentation preimage visibly different from
             * bootstrap so replay rollback has something exact to restore.
             */
            home.restoreViewerPresentation(
                new HomeWorldRuntimePlan.ViewerPresentationSnapshot(
                    false,
                    Collections.emptySet()
                )
            );

            List<NpcEntity> npcBefore=
                npcs.snapshot();
            HomeWorldRuntimePlan.ViewerPresentationSnapshot
                homeBefore=
                    home.snapshotViewerPresentation();
            SceneUpdatePublisher publisherBefore=
                bridge.publisher;
            SceneCoordinateContext.Snapshot sceneBefore=
                publisherBefore.context().snapshot();

            GroundItem item=
                world.groundItems().add(
                    995,
                    250,
                    new Tile(
                        movement.x(),
                        movement.y(),
                        movement.plane()
                    ),
                    null,
                    1L,
                    false
                );
            GroundItemRegistry.BatchMutation mutation=
                new GroundItemRegistry.BatchMutation(
                    item,
                    250,
                    0
                );
            long now=
                System.currentTimeMillis();

            if(!world.groundItemPresentationEvents()
                    .enqueueSpawn(
                        now,
                        mutation,
                        player,
                        generation
                    ))
                throw new AssertionError(
                    "ground snapshot event enqueue failed"
                );

            LocalGroundItemPresentationRelay relay=
                new LocalGroundItemPresentationRelay(
                    world,
                    player,
                    movement
                );

            RegionLoadLifecycle.Completion completion=
                lifecycle.prepareComplete();

            if(!completion.matched||
               completion.sequence!=1L||
               !"AUTO_HOME_REATTACH".equals(
                    completion.reason
               )||
               !lifecycle.pending())
                throw new AssertionError(
                    "prepared region ACK consumed lifecycle"
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    queue,
                    QUEUE_CAPACITY
                );

            writer.beginBatch();
            boolean groundSnapshotPublished=
                handler.completeRegionLoad(
                    completion,
                    writer,
                    "[region-ack-abort] "
                );

            if(!groundSnapshotPublished)
                throw new AssertionError(
                    "HOME replay did not publish ground snapshot"
                );

            if(relay.consumeSnapshotCoveredAfterSnapshot(
                    now,
                    true
                )!=1||
               relay.stagedDeliveryCount()!=1)
                throw new AssertionError(
                    "HOME replay did not stage ground acknowledgement"
                );

            boolean failed=false;

            try{
                writer.endBatch();
            }catch(java.io.IOException expected){
                failed=true;
                try{
                    writer.abortBatch();
                }catch(Throwable abortFailure){
                    expected.addSuppressed(
                        abortFailure
                    );
                }
            }finally{
                pressure.release();
            }

            if(!failed)
                throw new AssertionError(
                    "forced HOME replay admission failure did not escape"
                );

            if(!handler.abortRegionStreamBatch())
                throw new AssertionError(
                    "HOME replay presentation snapshot did not abort"
                );

            if(relay.abortStagedDeliveries()!=1)
                throw new AssertionError(
                    "HOME replay ground acknowledgement did not abort"
                );

            requirePreimage(
                lifecycle,
                npcs,
                home,
                bridge,
                npcBefore,
                homeBefore,
                publisherBefore,
                sceneBefore,
                player,
                generation,
                now,
                world,
                relay
            );

            RegionLoadLifecycle.Completion retry=
                lifecycle.prepareComplete();

            if(!retry.matched||
               retry.sequence!=completion.sequence)
                throw new AssertionError(
                    "region ACK retry identity changed"
                );

            writer.beginBatch();

            if(!handler.completeRegionLoad(
                    retry,
                    writer,
                    "[region-ack-retry] "
                ))
                throw new AssertionError(
                    "HOME replay retry did not publish"
                );

            if(relay.consumeSnapshotCoveredAfterSnapshot(
                    now,
                    true
                )!=1)
                throw new AssertionError(
                    "HOME replay retry did not restage ground acknowledgement"
                );

            writer.endBatch();

            if(!handler.commitRegionStreamBatch())
                throw new AssertionError(
                    "HOME replay presentation snapshot did not commit"
                );

            if(relay.commitStagedDeliveries(
                    System.currentTimeMillis()
                )!=1)
                throw new AssertionError(
                    "HOME replay ground acknowledgement did not commit"
                );

            if(!lifecycle.commitCompletion(
                    retry
                ))
                throw new AssertionError(
                    "region ACK lifecycle did not commit"
                );

            if(lifecycle.pending()||
               lifecycle.pendingSequence()!=0L)
                throw new AssertionError(
                    "committed region ACK remained pending"
                );

            if(world.groundItemPresentationEvents()
                    .pendingFor(
                        player.id(),
                        generation,
                        System.currentTimeMillis()
                    ).size()!=0)
                throw new AssertionError(
                    "committed HOME snapshot ground event remained pending"
                );

            if(queue.queuedBytes()<=0)
                throw new AssertionError(
                    "HOME replay retry produced no committed bytes"
                );

            if(npcs.visibleCount()<=
                    npcBefore.size())
                throw new AssertionError(
                    "HOME replay retry did not advance NPC presentation"
                );

            if(!home.snapshotViewerPresentation()
                    .bootstrapEstablished)
                throw new AssertionError(
                    "HOME replay retry did not advance HOME presentation"
                );

            System.out.println(
                "REGION_ACK_REPLAY_COMMIT_FENCE_PASS "+
                "failureRetainsPending=true "+
                "failureRestoresPresentation=true "+
                "failureDropsGroundAcks=true "+
                "retryCommitsOnce=true"
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

    private static void requirePreimage(
        RegionLoadLifecycle lifecycle,
        NpcRegistry npcs,
        HomeWorldRuntimePlan home,
        Bridge bridge,
        List<NpcEntity> npcBefore,
        HomeWorldRuntimePlan.ViewerPresentationSnapshot homeBefore,
        SceneUpdatePublisher publisherBefore,
        SceneCoordinateContext.Snapshot sceneBefore,
        WorldPlayer player,
        long generation,
        long now,
        World world,
        LocalGroundItemPresentationRelay relay
    ){
        if(!lifecycle.pending()||
           lifecycle.pendingSequence()!=1L)
            throw new AssertionError(
                "failed HOME replay consumed pending lifecycle"
            );

        List<NpcEntity> npcAfter=
            npcs.snapshot();

        if(npcAfter.size()!=
                npcBefore.size())
            throw new AssertionError(
                "failed HOME replay changed NPC projection size"
            );

        for(int i=0;i<npcBefore.size();i++)
            if(npcBefore.get(i)!=
                    npcAfter.get(i))
                throw new AssertionError(
                    "failed HOME replay changed NPC projection identity index="+
                    i
                );

        HomeWorldRuntimePlan.ViewerPresentationSnapshot
            homeAfter=
                home.snapshotViewerPresentation();

        if(homeAfter.bootstrapEstablished!=
                homeBefore.bootstrapEstablished||
           !homeAfter.clientVisibleWorldSceneIndexes.equals(
                homeBefore.clientVisibleWorldSceneIndexes
           ))
            throw new AssertionError(
                "failed HOME replay changed HOME viewer presentation"
            );

        if(bridge.publisher!=
                publisherBefore||
           bridge.publisher.context().currentChunkX()!=
                sceneBefore.chunkBaseLocalX||
           bridge.publisher.context().currentChunkY()!=
                sceneBefore.chunkBaseLocalY)
            throw new AssertionError(
                "failed HOME replay changed scene publisher/context"
            );

        if(relay.stagedDeliveryCount()!=0)
            throw new AssertionError(
                "failed HOME replay retained staged ground acknowledgements"
            );

        if(world.groundItemPresentationEvents()
                .pendingFor(
                    player.id(),
                    generation,
                    now
                ).size()!=1)
            throw new AssertionError(
                "failed HOME replay consumed ground presentation event"
            );
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

    private RegionAckReplayCommitFenceTest(){}
}
