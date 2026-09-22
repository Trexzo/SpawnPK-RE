package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

public final class SharedNpcRelayViewerGenerationFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer viewer=
            new WorldPlayer();

        long generationA=
            world.registerPlayer(
                viewer,
                "relay-viewer-generation-owner"
            );

        ByteArrayOutputStream staleOut=
            new ByteArrayOutputStream();

        ServerPacketWriter staleWriter=
            new ServerPacketWriter(
                staleOut,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
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
                staleWriter
            );

        if(!spawned.startsWith(
                "DEV_NPC_SPAWN_OK"))
            throw new AssertionError(
                "viewer-local target setup failed: "+
                spawned
            );

        NpcEntity target=
            onlyNpc(viewerNpcs.snapshot());

        SharedNpcWorldRelay.register(
            staleWriter,
            world,
            viewer,
            viewerNpcs,
            viewer.movement()
        );

        ServerPacketWriter freshWriter=null;

        try{
            EntityId sourceId=
                EntityId.next();

            long now=
                System.currentTimeMillis();

            boolean queued=
                world.npcPresentationEvents()
                    .enqueue(
                        now,
                        sourceId,
                        WorldNpcPresentationEvents
                            .Target.scene(
                                target.sceneIndex,
                                target.definitionId
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "stale-viewer"
                            ),
                        0L,
                        Collections.singleton(
                            viewer.id()
                        )
                    );

            if(!queued)
                throw new AssertionError(
                    "presentation event setup failed"
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
                    "relay-viewer-generation-owner"
                );

            if(generationB==generationA)
                throw new AssertionError(
                    "replacement generation did not advance"
                );

            int staleBytesBefore=
                staleOut.size();

            SharedNpcWorldRelay.flushAfterPlayer81(
                staleWriter
            );

            if(staleOut.size()!=staleBytesBefore)
                throw new AssertionError(
                    "stale relay viewer emitted packet65"
                );

            List<WorldNpcPresentationEvents.Event>
                stillPending=
                    world.npcPresentationEvents()
                        .pendingFor(
                            viewer.id(),
                            now+1L
                        );

            if(stillPending.size()!=1)
                throw new AssertionError(
                    "stale relay viewer consumed presentation event count="+
                    stillPending.size()
                );

            SharedNpcWorldRelay.syncRemotePets(
                staleWriter
            );

            if(staleOut.size()!=staleBytesBefore)
                throw new AssertionError(
                    "stale remote-pet sync emitted output"
                );

            SharedNpcWorldRelay.unregister(
                staleWriter
            );

            ByteArrayOutputStream freshOut=
                new ByteArrayOutputStream();

            freshWriter=
                new ServerPacketWriter(
                    freshOut,
                    new IsaacCipher(
                        new int[]{5,6,7,8}
                    )
                );

            SharedNpcWorldRelay.register(
                freshWriter,
                world,
                viewer,
                viewerNpcs,
                viewer.movement()
            );

            long freshNow=
                System.currentTimeMillis();

            boolean freshQueued=
                world.npcPresentationEvents()
                    .enqueue(
                        freshNow,
                        EntityId.next(),
                        WorldNpcPresentationEvents
                            .Target.scene(
                                target.sceneIndex,
                                target.definitionId
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "fresh-viewer"
                            ),
                        0L,
                        Collections.singleton(
                            viewer.id()
                        )
                    );

            if(!freshQueued)
                throw new AssertionError(
                    "fresh presentation event setup failed"
                );

            int freshBytesBefore=
                freshOut.size();

            SharedNpcWorldRelay.flushAfterPlayer81(
                freshWriter
            );

            if(freshOut.size()<=freshBytesBefore)
                throw new AssertionError(
                    "fresh generation did not receive packet65"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        freshNow+1L
                    ).isEmpty())
                throw new AssertionError(
                    "fresh generation did not consume presentation event"
                );

            System.out.println(
                "SHARED_NPC_RELAY_VIEWER_GENERATION_FENCE_PASS "+
                "staleFlushRejected=true "+
                "staleEventPreserved=true "+
                "stalePetSyncRejected=true "+
                "replacementFlushWorks=true"
            );

            world.unregisterPlayer(
                viewer,
                generationB
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                staleWriter
            );

            if(freshWriter!=null)
                SharedNpcWorldRelay.unregister(
                    freshWriter
                );

            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewer.generation()
                );

            world.close();
        }
    }

    private static NpcEntity onlyNpc(
        List<NpcEntity> npcs
    ){
        if(npcs.size()!=1)
            throw new AssertionError(
                "expected exactly one viewer-local NPC, got "+
                npcs.size()
            );
        return npcs.get(0);
    }

    private SharedNpcRelayViewerGenerationFenceTest(){}
}
