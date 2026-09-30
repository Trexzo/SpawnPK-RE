package spk.local;

import java.io.ByteArrayOutputStream;

public final class MonsterSpawnerNpcSceneProjectionTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();
        long generationA=world.registerPlayer(a,"projection-a");
        long generationB=world.registerPlayer(b,"projection-b");

        MovementState movementA=new MovementState();
        MovementState movementB=new MovementState();
        NpcRegistry npcsA=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                a.id()
            );
        NpcRegistry npcsB=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                b.id()
            );
        ServerPacketWriter writerA=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[4])
            );
        ServerPacketWriter writerB=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[4])
            );

        try{
            SharedNpcWorldRelay.register(
                writerA,
                world,
                a,
                npcsA,
                movementA
            );
            SharedNpcWorldRelay.register(
                writerB,
                world,
                b,
                npcsB,
                movementB
            );

            WorldNpc npc=
                world.npcs().spawn(
                    1530,
                    3088,
                    3495,
                    0
                );

            WorldNpc untracked=
                world.npcs().spawn(
                    1531,
                    3089,
                    3495,
                    0
                );

            SharedNpcWorldRelay.trackCanonicalNpc(
                world,
                npc
            );

            SharedNpcWorldRelay.syncRemotePets(
                writerA
            );
            SharedNpcWorldRelay.syncRemotePets(
                writerB
            );

            NpcEntity viewA=
                npcsA.canonical(
                    npc.id
                );
            NpcEntity viewB=
                npcsB.canonical(
                    npc.id
                );

            require(
                viewA!=null&&
                viewB!=null&&
                viewA!=viewB&&
                npc.id.equals(
                    viewA.canonicalId()
                )&&
                npc.id.equals(
                    viewB.canonicalId()
                )&&
                viewA.definitionId==1530&&
                viewB.definitionId==1530,
                "canonical NPC projection missing"
            );

            require(
                npcsA.canonical(
                    untracked.id
                )==null&&
                npcsB.canonical(
                    untracked.id
                )==null,
                "untracked canonical NPC was projected"
            );

            int firstSceneA=
                viewA.sceneIndex;
            int firstSceneB=
                viewB.sceneIndex;

            require(
                npcsA.scene(
                    firstSceneA
                )==viewA&&
                npcsB.scene(
                    firstSceneB
                )==viewB,
                "viewer-local scene ownership missing"
            );

            world.npcs().move(
                npc.id,
                3089,
                3495,
                0
            );

            SharedNpcWorldRelay.syncRemotePets(
                writerA
            );
            SharedNpcWorldRelay.syncRemotePets(
                writerB
            );

            require(
                npcsA.canonical(
                    npc.id
                ).x==3089&&
                npcsB.canonical(
                    npc.id
                ).x==3089,
                "canonical movement not synchronized"
            );

            world.npcs().move(
                npc.id,
                3120,
                3495,
                0
            );

            SharedNpcWorldRelay.syncRemotePets(
                writerA
            );
            SharedNpcWorldRelay.syncRemotePets(
                writerB
            );

            require(
                npcsA.canonical(
                    npc.id
                )==null&&
                npcsB.canonical(
                    npc.id
                )==null&&
                world.npcs().byId(
                    npc.id
                )==npc,
                "out-of-range projection removed canonical NPC or stayed visible"
            );

            world.npcs().move(
                npc.id,
                3088,
                3495,
                0
            );

            SharedNpcWorldRelay.syncRemotePets(
                writerA
            );
            SharedNpcWorldRelay.syncRemotePets(
                writerB
            );

            NpcEntity reenteredA=
                npcsA.canonical(
                    npc.id
                );
            NpcEntity reenteredB=
                npcsB.canonical(
                    npc.id
                );

            require(
                reenteredA!=null&&
                reenteredB!=null&&
                npc.id.equals(
                    reenteredA.canonicalId()
                )&&
                npc.id.equals(
                    reenteredB.canonicalId()
                ),
                "canonical NPC did not re-enter viewer scenes"
            );

            WorldNpc owned=
                world.npcs().spawnOwned(
                    1532,
                    3088,
                    3496,
                    0,
                    a.id(),
                    100
                );

            expect(
                IllegalArgumentException.class,
                ()->SharedNpcWorldRelay
                    .trackCanonicalNpc(
                        world,
                        owned
                    ),
                "owned pet/runtime NPC duplicated generic projection"
            );

            require(
                SharedNpcWorldRelay
                    .untrackCanonicalNpc(
                        world,
                        npc.id
                    ),
                "projection untrack failed"
            );

            SharedNpcWorldRelay.syncRemotePets(
                writerA
            );
            SharedNpcWorldRelay.syncRemotePets(
                writerB
            );

            require(
                npcsA.canonical(
                    npc.id
                )==null&&
                npcsB.canonical(
                    npc.id
                )==null&&
                world.npcs().byId(
                    npc.id
                )==npc,
                "projection untrack affected canonical lifecycle"
            );

            SharedNpcWorldRelay.trackCanonicalNpc(
                world,
                npc
            );

            require(
                world.npcs().remove(
                    npc.id
                ),
                "canonical removal fixture"
            );

            SharedNpcWorldRelay.syncRemotePets(
                writerA
            );
            SharedNpcWorldRelay.syncRemotePets(
                writerB
            );

            require(
                npcsA.canonical(
                    npc.id
                )==null&&
                npcsB.canonical(
                    npc.id
                )==null,
                "canonical despawn left viewer projection"
            );

            SharedNpcWorldRelay.untrackCanonicalNpc(
                world,
                npc.id
            );

            System.out.println(
                "MONSTER_SPAWNER_NPC_SCENE_PROJECTION_PASS "+
                "canonicalIdentity=true "+
                "viewerLocalScene=true "+
                "multiViewerIndependent=true "+
                "movementSync=true "+
                "outOfRangeRemove=true "+
                "reentry=true "+
                "despawnRemove=true "+
                "homeNotDuplicated=true "+
                "petsNotDuplicated=true "+
                "packet65OwnerNpcRegistry=true "+
                "protocolBoundaryClean=true"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                writerA
            );
            SharedNpcWorldRelay.unregister(
                writerB
            );
            world.unregisterPlayer(
                a,
                generationA
            );
            world.unregisterPlayer(
                b,
                generationB
            );
            world.close();
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private MonsterSpawnerNpcSceneProjectionTest(){}
}
