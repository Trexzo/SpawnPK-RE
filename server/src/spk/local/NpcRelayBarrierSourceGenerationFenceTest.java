package spk.local;

import java.util.LinkedHashMap;

public final class NpcRelayBarrierSourceGenerationFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGenerationA=
            world.registerPlayer(
                source,
                "relay-barrier-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "relay-barrier-viewer"
            );

        OutboundPacketQueue sourceQueueA=
            new OutboundPacketQueue();
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue();

        ServerPacketWriter sourceWriterA=
            writer(sourceQueueA,1);
        ServerPacketWriter viewerWriter=
            writer(viewerQueue,5);

        Player81WorldSync.Context sourceSyncA=
            Player81WorldSync.register(
                sourceWriterA,
                world,
                source,
                new DevAuthorityWorkbench()
            );
        Player81WorldSync.Context viewerSync=
            Player81WorldSync.register(
                viewerWriter,
                world,
                viewer,
                new DevAuthorityWorkbench()
            );

        NpcRegistry sourceNpcsA=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        String spawned=
            viewerNpcs.devSpawnNpc(
                1488,
                0,
                0,
                viewer.movement(),
                viewerWriter
            );

        if(!spawned.startsWith(
                "DEV_NPC_SPAWN_OK"))
            throw new AssertionError(
                "viewer target setup failed: "+
                spawned
            );

        NpcEntity target=
            viewerNpcs.snapshot().get(0);

        SharedNpcWorldRelay.register(
            sourceWriterA,
            world,
            source,
            sourceNpcsA,
            source.movement()
        );
        SharedNpcWorldRelay.register(
            viewerWriter,
            world,
            viewer,
            viewerNpcs,
            viewer.movement()
        );

        ServerPacketWriter sourceWriterB=null;

        try{
            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );

            Player81WorldSync.transformForTest(
                sourceSyncA,
                CombatSync.player81AnimationOnly(
                    827
                )
            );

            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );

            long oldCursor=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id()
                    );

            if(oldCursor<=0L)
                throw new AssertionError(
                    "generation A consumed cursor missing"
                );

            if(!world.unregisterPlayer(
                    source,
                    sourceGenerationA
                ))
                throw new AssertionError(
                    "source generation A unregister failed"
                );

            long sourceGenerationB=
                world.registerPlayer(
                    source,
                    "relay-barrier-source"
                );

            if(sourceGenerationB==
                    sourceGenerationA)
                throw new AssertionError(
                    "source generation did not advance"
                );

            OutboundPacketQueue sourceQueueB=
                new OutboundPacketQueue();

            sourceWriterB=
                writer(sourceQueueB,9);

            Player81WorldSync.Context sourceSyncB=
                Player81WorldSync.register(
                    sourceWriterB,
                    world,
                    source,
                    new DevAuthorityWorkbench()
                );

            if(sourceSyncB.ownerGeneration!=
                    sourceGenerationB)
                throw new AssertionError(
                    "fresh source B Player81 context mismatch"
                );

            NpcRegistry sourceNpcsB=
                new NpcRegistry(
                    new DevAuthorityWorkbench()
                );

            SharedNpcWorldRelay.register(
                sourceWriterB,
                world,
                source,
                sourceNpcsB,
                source.movement()
            );

            long identityBeforeB=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id()
                    );

            if(identityBeforeB!=oldCursor)
                throw new AssertionError(
                    "identity cursor fixture changed expected="+
                    oldCursor+
                    " actual="+identityBeforeB
                );

            if(Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationB
                    )!=-1L)
                throw new AssertionError(
                    "exact B cursor accepted generation A remote Track"
                );

            LinkedHashMap<EntityId,Long>
                recipients=
                    new LinkedHashMap<>();
            recipients.put(
                viewer.id(),
                viewerGeneration
            );

            long now=
                System.currentTimeMillis();

            boolean queued=
                world.npcPresentationEvents()
                    .enqueueOwned(
                        now,
                        source.id(),
                        sourceGenerationB,
                        WorldNpcPresentationEvents
                            .Target.scene(
                                target.sceneIndex,
                                target.definitionId
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "generation-b-barrier"
                            ),
                        oldCursor,
                        recipients
                    );

            if(!queued)
                throw new AssertionError(
                    "generation B relay event not queued"
                );

            int bytesBeforeBlockedFlush=
                viewerQueue.queuedBytes();

            SharedNpcWorldRelay.flushAfterPlayer81(
                viewerWriter
            );

            if(viewerQueue.queuedBytes()!=
                    bytesBeforeBlockedFlush)
                throw new AssertionError(
                    "generation A cursor released generation B relay event"
                );

            if(world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewerGeneration,
                        now+1L
                    ).size()!=1)
                throw new AssertionError(
                    "blocked B relay event did not remain pending"
                );

            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );

            if(Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationB
                    )!=0L)
                throw new AssertionError(
                    "fresh generation B Track should start with zero cursor"
                );

            Player81WorldSync.transformForTest(
                sourceSyncB,
                CombatSync.player81AnimationOnly(
                    828
                )
            );

            long bBarrier=
                Player81WorldSync
                    .latestPublishedEventSequence(
                        sourceWriterB
                    );

            if(bBarrier<=oldCursor)
                throw new AssertionError(
                    "generation B source event sequence did not advance beyond old cursor"
                );

            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );

            long consumedB=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationB
                    );

            if(consumedB<bBarrier)
                throw new AssertionError(
                    "viewer did not consume generation B Player81 event barrier="+
                    bBarrier+
                    " consumed="+consumedB
                );

            int bytesBeforeDelivery=
                viewerQueue.queuedBytes();

            SharedNpcWorldRelay.flushAfterPlayer81(
                viewerWriter
            );

            if(viewerQueue.queuedBytes()<=
                    bytesBeforeDelivery)
                throw new AssertionError(
                    "generation B relay event did not deliver after exact B cursor advanced"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewerGeneration,
                        now+2L
                    ).isEmpty())
                throw new AssertionError(
                    "generation B relay event not marked delivered"
                );

            System.out.println(
                "NPC_RELAY_BARRIER_SOURCE_GENERATION_FENCE_PASS "+
                "identityACursorPresent=true "+
                "exactBRejectedOldTrack=true "+
                "relayHeldUntilBConsumed=true "+
                "relayDeliveredAfterBConsumed=true"
            );

            world.unregisterPlayer(
                source,
                sourceGenerationB
            );
            world.unregisterPlayer(
                viewer,
                viewerGeneration
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                sourceWriterA
            );

            if(sourceWriterB!=null)
                SharedNpcWorldRelay.unregister(
                    sourceWriterB
                );

            SharedNpcWorldRelay.unregister(
                viewerWriter
            );

            Player81WorldSync.unregister(
                sourceWriterA
            );

            if(sourceWriterB!=null)
                Player81WorldSync.unregister(
                    sourceWriterB
                );

            Player81WorldSync.unregister(
                viewerWriter
            );

            if(source.registered())
                world.unregisterPlayer(
                    source,
                    source.generation()
                );

            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewer.generation()
                );

            world.close();
        }
    }

    private static ServerPacketWriter writer(
        OutboundPacketQueue queue,
        int seed
    ){
        return new ServerPacketWriter(
            queue,
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    private NpcRelayBarrierSourceGenerationFenceTest(){}
}
