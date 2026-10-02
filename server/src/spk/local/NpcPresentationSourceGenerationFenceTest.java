package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;

public final class NpcPresentationSourceGenerationFenceTest {
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
                "npc-source-generation"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "npc-source-viewer"
            );

        ByteArrayOutputStream sourceOutA=
            new ByteArrayOutputStream();
        ByteArrayOutputStream viewerOut=
            new ByteArrayOutputStream();

        ServerPacketWriter sourceWriterA=
            writer(sourceOutA,1);
        ServerPacketWriter viewerWriter=
            writer(viewerOut,5);

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
            long now=
                System.currentTimeMillis();

            LinkedHashMap<EntityId,Long>
                recipients=
                    new LinkedHashMap<>();
            recipients.put(
                viewer.id(),
                viewerGeneration
            );

            boolean queuedA=
                world.npcPresentationEvents()
                    .enqueueOwned(
                        now,
                        source.id(),
                        sourceGenerationA,
                        WorldNpcPresentationEvents
                            .Target.scene(
                                target.sceneIndex,
                                target.definitionId
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "source-generation-a"
                            ),
                        0L,
                        recipients
                    );

            if(!queuedA)
                throw new AssertionError(
                    "generation A source event not queued"
                );

            SharedNpcWorldRelay.retireTerminalWriter(
                sourceWriterA
            );

            if(world.npcPresentationEvents()
                    .size()!=0)
                throw new AssertionError(
                    "first terminal retirement did not remove generation A event"
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
                    "npc-source-generation"
                );

            if(sourceGenerationB==
                    sourceGenerationA)
                throw new AssertionError(
                    "source generation did not advance"
                );

            int viewerBytesBefore=
                viewerOut.size();

            SharedNpcWorldRelay.flushAfterPlayer81(
                viewerWriter
            );

            if(viewerOut.size()!=
                    viewerBytesBefore)
                throw new AssertionError(
                    "generation A event emitted after source became B"
                );

            if(world.npcPresentationEvents()
                    .size()!=0)
                throw new AssertionError(
                    "stale source-generation event was not pruned"
                );

            ByteArrayOutputStream sourceOutB=
                new ByteArrayOutputStream();

            sourceWriterB=
                writer(sourceOutB,9);

            SharedNpcWorldRelay.register(
                sourceWriterB,
                world,
                source,
                sourceNpcs,
                source.movement()
            );

            long freshNow=
                System.currentTimeMillis();

            boolean staleProbe=
                world.npcPresentationEvents()
                    .enqueueOwned(
                        freshNow,
                        source.id(),
                        sourceGenerationA,
                        WorldNpcPresentationEvents
                            .Target.scene(
                                target.sceneIndex,
                                target.definitionId
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "stale-a-probe"
                            ),
                        0L,
                        recipients
                    );

            boolean freshB=
                world.npcPresentationEvents()
                    .enqueueOwned(
                        freshNow+1L,
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
                        0L,
                        recipients
                    );

            if(!staleProbe||!freshB)
                throw new AssertionError(
                    "source generation prune fixture not queued"
                );

            /*
             * Writer A remains a terminal BY_WRITER sentinel while healthy
             * generation B owns the active SharedNpc Context. A repeated
             * terminal retirement of A must prune only A-generation events.
             */
            SharedNpcWorldRelay.retireTerminalWriter(
                sourceWriterA
            );

            boolean oldWriterRejected=false;
            try{
                SharedNpcWorldRelay.preflightRegistration(
                    sourceWriterA,
                    world,
                    source
                );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                oldWriterRejected=
                    expected.writer==sourceWriterA;
            }

            if(!oldWriterRejected)
                throw new AssertionError(
                    "terminal source writer sentinel was not retained"
                );

            List<WorldNpcPresentationEvents.Event>
                pending=
                    world.npcPresentationEvents()
                        .pendingFor(
                            viewer.id(),
                            viewerGeneration,
                            freshNow+2L
                        );

            if(pending.size()!=1)
                throw new AssertionError(
                    "A-generation prune did not preserve exactly one B event count="+
                    pending.size()
                );

            if(pending.get(0).sourceGeneration!=
                    sourceGenerationB)
                throw new AssertionError(
                    "remaining event is not generation B"
                );

            viewerBytesBefore=
                viewerOut.size();

            SharedNpcWorldRelay.flushAfterPlayer81(
                viewerWriter
            );

            if(viewerOut.size()<=
                    viewerBytesBefore)
                throw new AssertionError(
                    "generation B event did not emit packet65"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewerGeneration,
                        freshNow+3L
                    ).isEmpty())
                throw new AssertionError(
                    "generation B event not marked delivered"
                );

            System.out.println(
                "NPC_PRESENTATION_SOURCE_GENERATION_FENCE_PASS "+
                "staleARejected=true "+
                "staleAPruned=true "+
                "exactPrunePreservedB=true "+
                "terminalExactPrunePreservedB=true "+
                "terminalSentinelRetained=true "+
                "generationBDelivered=true"
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

    private NpcPresentationSourceGenerationFenceTest(){}
}
