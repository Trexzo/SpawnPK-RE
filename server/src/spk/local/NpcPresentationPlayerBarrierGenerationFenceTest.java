package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;

public final class NpcPresentationPlayerBarrierGenerationFenceTest {
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
                "barrier-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "barrier-viewer"
            );

        ByteArrayOutputStream sourceOutA=
            new ByteArrayOutputStream();
        ByteArrayOutputStream viewerOut=
            new ByteArrayOutputStream();

        ServerPacketWriter sourceWriterA=
            writer(sourceOutA,1);
        ServerPacketWriter viewerWriter=
            writer(viewerOut,5);

        Player81WorldSync.Context sourceA=
            Player81WorldSync.register(
                sourceWriterA,
                world,
                source,
                new DevAuthorityWorkbench()
            );
        Player81WorldSync.Context viewerContext=
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
            onlyNpc(
                viewerNpcs.snapshot()
            );

        SharedNpcWorldRelay.register(
            sourceWriterA,
            world,
            source,
            sourceNpcs,
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
                viewerContext,
                BootstrapPackets.player81Idle()
            );

            Player81WorldSync.transformForTest(
                sourceA,
                CombatSync.player81AnimationOnly(
                    827
                )
            );

            Player81WorldSync.transformForTest(
                viewerContext,
                BootstrapPackets.player81Idle()
            );

            long generationACursor=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationA
                    );

            if(generationACursor<=0L)
                throw new AssertionError(
                    "generation A cursor did not advance"
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
                    "barrier-source"
                );

            if(sourceGenerationB==
                    sourceGenerationA)
                throw new AssertionError(
                    "source generation did not advance"
                );

            sourceWriterB=
                writer(
                    new ByteArrayOutputStream(),
                    9
                );

            Player81WorldSync.Context sourceB=
                Player81WorldSync.register(
                    sourceWriterB,
                    world,
                    source,
                    new DevAuthorityWorkbench()
                );

            SharedNpcWorldRelay.register(
                sourceWriterB,
                world,
                source,
                sourceNpcs,
                source.movement()
            );

            if(sourceB.ownerGeneration!=
                    sourceGenerationB)
                throw new AssertionError(
                    "fresh source B context generation mismatch"
                );

            long identityDuringReplacement=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id()
                    );

            if(identityDuringReplacement!=
                    generationACursor)
                throw new AssertionError(
                    "identity-only transition cursor fixture changed"
                );

            if(Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationB
                    )!=-1L)
                throw new AssertionError(
                    "exact B cursor accepted while viewer still tracked A"
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

            boolean queuedBlocked=
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
                                "blocked-b"
                            ),
                        generationACursor,
                        recipients
                    );

            if(!queuedBlocked)
                throw new AssertionError(
                    "blocked generation B event not queued"
                );

            int viewerBytesBefore=
                viewerOut.size();

            SharedNpcWorldRelay.flushAfterPlayer81(
                viewerWriter
            );

            if(viewerOut.size()!=
                    viewerBytesBefore)
                throw new AssertionError(
                    "generation A cursor released generation B NPC event"
                );

            int removedBlocked=
                world.npcPresentationEvents()
                    .removeSourceGeneration(
                        source.id(),
                        sourceGenerationB,
                        now+1L
                    );

            if(removedBlocked!=1)
                throw new AssertionError(
                    "blocked event cleanup count="+
                    removedBlocked
                );

            Player81WorldSync.transformForTest(
                viewerContext,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                viewerContext,
                BootstrapPackets.player81Idle()
            );

            if(Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationB
                    )!=0L)
                throw new AssertionError(
                    "fresh generation B track should begin at cursor zero"
                );

            Player81WorldSync.transformForTest(
                sourceB,
                CombatSync.player81AnimationOnly(
                    828
                )
            );
            Player81WorldSync.transformForTest(
                viewerContext,
                BootstrapPackets.player81Idle()
            );

            long generationBCursor=
                Player81WorldSync
                    .consumedEventSequence(
                        viewerWriter,
                        source.id(),
                        sourceGenerationB
                    );

            if(generationBCursor<=0L)
                throw new AssertionError(
                    "generation B cursor did not advance"
                );

            long freshNow=
                System.currentTimeMillis();

            boolean queuedFresh=
                world.npcPresentationEvents()
                    .enqueueOwned(
                        freshNow,
                        source.id(),
                        sourceGenerationB,
                        WorldNpcPresentationEvents
                            .Target.scene(
                                target.sceneIndex,
                                target.definitionId
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "fresh-b"
                            ),
                        generationBCursor,
                        recipients
                    );

            if(!queuedFresh)
                throw new AssertionError(
                    "fresh generation B event not queued"
                );

            viewerBytesBefore=
                viewerOut.size();

            SharedNpcWorldRelay.flushAfterPlayer81(
                viewerWriter
            );

            if(viewerOut.size()<=
                    viewerBytesBefore)
                throw new AssertionError(
                    "exact generation B barrier did not emit packet65"
                );

            List<WorldNpcPresentationEvents.Event>
                pending=
                    world.npcPresentationEvents()
                        .pendingFor(
                            viewer.id(),
                            viewerGeneration,
                            freshNow+1L
                        );

            if(!pending.isEmpty())
                throw new AssertionError(
                    "fresh generation B event not marked delivered"
                );

            System.out.println(
                "NPC_PRESENTATION_PLAYER_BARRIER_GENERATION_FENCE_PASS "+
                "generationACursorCaptured=true "+
                "staleACursorRejectedForB=true "+
                "freshBTrackRequired=true "+
                "generationBBarrierDelivered=true"
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
            Player81WorldSync.unregister(
                sourceWriterA
            );

            SharedNpcWorldRelay.unregister(
                sourceWriterA
            );

            if(sourceWriterB!=null){
                Player81WorldSync.unregister(
                    sourceWriterB
                );
                SharedNpcWorldRelay.unregister(
                    sourceWriterB
                );
            }

            Player81WorldSync.unregister(
                viewerWriter
            );
            SharedNpcWorldRelay.unregister(
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
        ByteArrayOutputStream out,
        int seed
    ){
        return new ServerPacketWriter(
            out,
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

    private static NpcEntity onlyNpc(
        List<NpcEntity> npcs
    ){
        if(npcs.size()!=1)
            throw new AssertionError(
                "expected one local NPC, got "+
                npcs.size()
            );
        return npcs.get(0);
    }

    private NpcPresentationPlayerBarrierGenerationFenceTest(){}
}
