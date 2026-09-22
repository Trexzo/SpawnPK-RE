package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;

public final class NpcPresentationRecipientGenerationFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer viewer=
            new WorldPlayer();

        long generationA=
            world.registerPlayer(
                viewer,
                "npc-recipient-generation"
            );

        ByteArrayOutputStream outA=
            new ByteArrayOutputStream();

        ServerPacketWriter writerA=
            writer(outA,1);

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        String spawned=
            npcs.devSpawnNpc(
                1488,
                0,
                0,
                viewer.movement(),
                writerA
            );

        if(!spawned.startsWith(
                "DEV_NPC_SPAWN_OK"))
            throw new AssertionError(
                "viewer target setup failed: "+
                spawned
            );

        NpcEntity target=
            onlyNpc(npcs.snapshot());

        SharedNpcWorldRelay.register(
            writerA,
            world,
            viewer,
            npcs,
            viewer.movement()
        );

        ServerPacketWriter writerB=null;

        try{
            EntityId sourceId=
                EntityId.next();
            long now=
                System.currentTimeMillis();

            LinkedHashMap<EntityId,Long>
                recipientsA=
                    new LinkedHashMap<>();
            recipientsA.put(
                viewer.id(),
                generationA
            );

            boolean queuedA=
                world.npcPresentationEvents()
                    .enqueueOwned(
                        now,
                        sourceId,
                        WorldNpcPresentationEvents
                            .Target.scene(
                                target.sceneIndex,
                                target.definitionId
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "generation-a"
                            ),
                        0L,
                        recipientsA
                    );

            if(!queuedA)
                throw new AssertionError(
                    "generation A event not queued"
                );

            if(world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generationA,
                        now
                    ).size()!=1)
                throw new AssertionError(
                    "generation A event not visible to A"
                );

            if(!world.unregisterPlayer(
                    viewer,
                    generationA
                ))
                throw new AssertionError(
                    "generation A unregister failed"
                );

            long generationB=
                world.registerPlayer(
                    viewer,
                    "npc-recipient-generation"
                );

            if(generationB==generationA)
                throw new AssertionError(
                    "viewer generation did not advance"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generationB,
                        now+1L
                    ).isEmpty())
                throw new AssertionError(
                    "generation B inherited generation A event"
                );

            if(world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        now+1L
                    ).size()!=1)
                throw new AssertionError(
                    "compatibility identity lookup unexpectedly removed old event before prune"
                );

            SharedNpcWorldRelay.unregister(
                writerA
            );

            ByteArrayOutputStream outB=
                new ByteArrayOutputStream();

            writerB=
                writer(outB,5);

            SharedNpcWorldRelay.register(
                writerB,
                world,
                viewer,
                npcs,
                viewer.movement()
            );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generationB,
                        now+2L
                    ).isEmpty())
                throw new AssertionError(
                    "stale generation A recipient survived relay replacement prune"
                );

            long freshNow=
                System.currentTimeMillis();

            LinkedHashMap<EntityId,Long>
                recipientsB=
                    new LinkedHashMap<>();
            recipientsB.put(
                viewer.id(),
                generationB
            );

            boolean queuedB=
                world.npcPresentationEvents()
                    .enqueueOwned(
                        freshNow,
                        EntityId.next(),
                        WorldNpcPresentationEvents
                            .Target.scene(
                                target.sceneIndex,
                                target.definitionId
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "generation-b"
                            ),
                        0L,
                        recipientsB
                    );

            if(!queuedB)
                throw new AssertionError(
                    "generation B event not queued"
                );

            world.npcPresentationEvents()
                .retainRecipientsOwned(
                    recipientsB,
                    freshNow
                );

            List<WorldNpcPresentationEvents.Event>
                pendingB=
                    world.npcPresentationEvents()
                        .pendingFor(
                            viewer.id(),
                            generationB,
                            freshNow
                        );

            if(pendingB.size()!=1)
                throw new AssertionError(
                    "generation-aware prune removed current B event"
                );

            int bytesBefore=
                outB.size();

            SharedNpcWorldRelay.flushAfterPlayer81(
                writerB
            );

            if(outB.size()<=bytesBefore)
                throw new AssertionError(
                    "generation B event did not emit packet65"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generationB,
                        freshNow+1L
                    ).isEmpty())
                throw new AssertionError(
                    "generation B event not marked delivered"
                );

            System.out.println(
                "NPC_PRESENTATION_RECIPIENT_GENERATION_FENCE_PASS "+
                "generationAVisible=true "+
                "replacementBRejectedOldEvent=true "+
                "staleRecipientPruned=true "+
                "generationBDelivered=true"
            );

            world.unregisterPlayer(
                viewer,
                generationB
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                writerA
            );

            if(writerB!=null)
                SharedNpcWorldRelay.unregister(
                    writerB
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

    private NpcPresentationRecipientGenerationFenceTest(){}
}
