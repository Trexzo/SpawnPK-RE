package spk.local;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

public final class RemotePetTrackGenerationFenceTest {
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
                "remote-pet-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "remote-pet-viewer"
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
            PetDefinitionRepository.Def pet=
                PetDefinitionRepository.get(
                    24019
                );

            if(pet==null)
                throw new AssertionError(
                    "missing pet 24019"
                );

            String spawnA=
                sourceNpcsA.spawnPet(
                    pet,
                    source.movement(),
                    sourceWriterA
                );

            if(!spawnA.startsWith(
                    "PET_SPAWN_OK"))
                throw new AssertionError(
                    spawnA
                );

            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            Object trackA=
                remoteTrack(
                    viewerWriter,
                    source.id()
                );

            if(trackA==null)
                throw new AssertionError(
                    "generation A remote pet track missing"
                );

            long trackGenerationA=
                trackGeneration(trackA);

            if(trackGenerationA!=
                    sourceGenerationA)
                throw new AssertionError(
                    "track A generation expected="+
                    sourceGenerationA+
                    " actual="+
                    trackGenerationA
                );

            int sceneA=
                trackMainScene(trackA);

            if(sceneA<0||
               viewerNpcs.scene(sceneA)==null)
                throw new AssertionError(
                    "generation A mirror missing"
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
                    "remote-pet-source"
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
                    "source generation B Player81 context mismatch"
                );

            NpcRegistry sourceNpcsB=
                new NpcRegistry(
                    new DevAuthorityWorkbench()
                );

            String spawnB=
                sourceNpcsB.spawnPet(
                    pet,
                    source.movement(),
                    sourceWriterB
                );

            if(!spawnB.startsWith(
                    "PET_SPAWN_OK"))
                throw new AssertionError(
                    spawnB
                );

            SharedNpcWorldRelay.register(
                sourceWriterB,
                world,
                source,
                sourceNpcsB,
                source.movement()
            );

            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );

            int beforeReplacementSync=
                viewerQueue.queuedBytes();

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            Object trackB=
                remoteTrack(
                    viewerWriter,
                    source.id()
                );

            if(trackB==null)
                throw new AssertionError(
                    "generation B remote pet track missing"
                );

            if(trackB==trackA)
                throw new AssertionError(
                    "generation B reused generation A RemotePetTrack object"
                );

            long trackGenerationB=
                trackGeneration(trackB);

            if(trackGenerationB!=
                    sourceGenerationB)
                throw new AssertionError(
                    "track B generation expected="+
                    sourceGenerationB+
                    " actual="+
                    trackGenerationB
                );

            int sceneB=
                trackMainScene(trackB);

            if(sceneB<0||
               viewerNpcs.scene(sceneB)==null)
                throw new AssertionError(
                    "generation B mirror missing"
                );

            if(viewerQueue.queuedBytes()<=
                    beforeReplacementSync)
                throw new AssertionError(
                    "generation change did not emit remove/remirror packet65 work"
                );

            int beforeStableSync=
                viewerQueue.queuedBytes();

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            Object stableB=
                remoteTrack(
                    viewerWriter,
                    source.id()
                );

            if(stableB!=trackB)
                throw new AssertionError(
                    "same-generation B sync recreated track"
                );

            if(trackGeneration(stableB)!=
                    sourceGenerationB)
                throw new AssertionError(
                    "stable B track generation changed"
                );

            if(viewerQueue.queuedBytes()!=
                    beforeStableSync)
                throw new AssertionError(
                    "same-generation stable sync emitted unnecessary packet65 work"
                );

            System.out.println(
                "REMOTE_PET_TRACK_GENERATION_FENCE_PASS "+
                "generationATrackCaptured=true "+
                "generationChangeRecreatedTrack=true "+
                "generationBTrackCaptured=true "+
                "sameGenerationBStable=true"
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

    private static Object remoteTrack(
        ServerPacketWriter viewerWriter,
        EntityId sourceId
    )throws Exception{
        Field byWriterField=
            SharedNpcWorldRelay.class
                .getDeclaredField(
                    "BY_WRITER"
                );
        byWriterField.setAccessible(true);

        Object context;

        synchronized(SharedNpcWorldRelay.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<
                ServerPacketWriter,
                Object
            > byWriter=
                (IdentityHashMap<
                    ServerPacketWriter,
                    Object
                >)
                byWriterField.get(null);

            context=
                byWriter.get(
                    viewerWriter
                );
        }

        if(context==null)
            return null;

        Field remoteField=
            context.getClass()
                .getDeclaredField(
                    "remote"
                );
        remoteField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<EntityId,Object> remote=
            (Map<EntityId,Object>)
                remoteField.get(context);

        return remote.get(sourceId);
    }

    private static long trackGeneration(
        Object track
    )throws Exception{
        Field field=
            track.getClass()
                .getDeclaredField(
                    "sourceGeneration"
                );
        field.setAccessible(true);
        return field.getLong(track);
    }

    private static int trackMainScene(
        Object track
    )throws Exception{
        Field field=
            track.getClass()
                .getDeclaredField(
                    "mainScene"
                );
        field.setAccessible(true);
        return field.getInt(track);
    }

    private RemotePetTrackGenerationFenceTest(){}
}
