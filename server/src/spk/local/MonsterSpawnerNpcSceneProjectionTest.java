package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class MonsterSpawnerNpcSceneProjectionTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();
        long generationA=world.registerPlayer(
            a,
            "projection-a"
        );
        long generationB=world.registerPlayer(
            b,
            "projection-b"
        );

        MovementState movementA=a.movement();
        MovementState movementB=b.movement();

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

            String dev=
                npcsA.devSpawnNpc(
                    1600,
                    1,
                    1,
                    movementA,
                    writerA
                );

            require(
                dev.startsWith(
                    "DEV_NPC_SPAWN_OK"
                ),
                "DEV scene preallocation failed"
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

            SharedNpcWorldRelay
                .trackCanonicalNpc(
                    world,
                    npc
                );

            sync(
                writerA,
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
                viewA.sceneIndex!=
                    viewB.sceneIndex&&
                npc.id.equals(
                    viewA.canonicalId()
                )&&
                npc.id.equals(
                    viewB.canonicalId()
                )&&
                viewA.definitionId==1530&&
                viewB.definitionId==1530,
                "canonical multi-view projection missing"
            );

            require(
                npcsA.sharedCanonicalProjection(
                    viewA.sceneIndex
                )&&
                npcsB.sharedCanonicalProjection(
                    viewB.sceneIndex
                ),
                "projection did not use production shared lane"
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

            String devRemove=
                npcsA.devRemoveAllNpcs(
                    writerA
                );

            require(
                devRemove.equals(
                    "DEV_NPC_REMOVE_ALL count=1"
                )&&
                npcsA.canonical(
                    npc.id
                )==viewA,
                "DEV ownership overlapped production canonical projection"
            );

            int sceneA=viewA.sceneIndex;
            int sceneB=viewB.sceneIndex;

            world.npcs().move(
                npc.id,
                3089,
                3495,
                0
            );

            sync(
                writerA,
                writerB
            );

            require(
                npcsA.canonical(
                    npc.id
                ).sceneIndex==sceneA&&
                npcsB.canonical(
                    npc.id
                ).sceneIndex==sceneB&&
                npcsA.canonical(
                    npc.id
                ).x==3089&&
                npcsB.canonical(
                    npc.id
                ).x==3089,
                "representable canonical movement did not reuse viewer scenes"
            );

            world.npcs().move(
                npc.id,
                3120,
                3495,
                0
            );

            sync(
                writerA,
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
                "out-of-range projection affected canonical ownership"
            );

            world.npcs().move(
                npc.id,
                3088,
                3495,
                0
            );

            sync(
                writerA,
                writerB
            );

            require(
                npcsA.canonical(
                    npc.id
                )!=null&&
                npcsB.canonical(
                    npc.id
                )!=null,
                "canonical NPC did not re-enter viewer scenes"
            );

            List<WorldHomeNpcService.VisibleNpc>
                homeVisible=
                    world.homeNpcs()
                        .visibleCanonical(
                            movementA.x(),
                            movementA.y()
                        );

            require(
                !homeVisible.isEmpty(),
                "HOME ownership fixture empty"
            );

            WorldNpc homeNpc=
                world.npcs().byId(
                    homeVisible.get(0)
                        .canonicalId
                );

            require(
                homeNpc!=null,
                "HOME canonical fixture missing"
            );

            expect(
                IllegalArgumentException.class,
                ()->SharedNpcWorldRelay
                    .trackCanonicalNpc(
                        world,
                        homeNpc
                    ),
                "HOME canonical duplicated generic projection"
            );

            WorldNpc petNpc=
                world.petNpcs()
                    .ensureMain(
                        a.id(),
                        1532,
                        100,
                        3088,
                        3496,
                        0
                    );

            expect(
                IllegalArgumentException.class,
                ()->SharedNpcWorldRelay
                    .trackCanonicalNpc(
                        world,
                        petNpc
                    ),
                "pet canonical duplicated generic projection"
            );

            require(
                SharedNpcWorldRelay
                    .untrackCanonicalNpc(
                        world,
                        npc.id
                    ),
                "explicit projection untrack failed"
            );

            sync(
                writerA,
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
                "untrack changed canonical lifecycle"
            );

            SharedNpcWorldRelay
                .trackCanonicalNpc(
                    world,
                    npc
                );
            sync(
                writerA,
                writerB
            );

            /*
             * Region transition proof: the production generic lane must use the
             * viewer's actual packet-73 window + plane, not the legacy HOME-only
             * MovementState.insideLoadedRegion(...) helper used by DEV/remote-pet
             * fixtures.
             */
            npcsA.detachRegionViewPreservingFollowers(
                writerA
            );
            movementA.enterTransientRegion(
                3200,
                3200,
                1,
                3180,
                3180
            );

            WorldNpc transientNpc=
                world.npcs().spawn(
                    1533,
                    3201,
                    3200,
                    1
                );

            SharedNpcWorldRelay
                .trackCanonicalNpc(
                    world,
                    transientNpc
                );

            sync(
                writerA,
                writerB
            );

            require(
                npcsA.canonical(
                    transientNpc.id
                )!=null&&
                npcsB.canonical(
                    transientNpc.id
                )==null,
                "current transient packet-65 window/plane projection failed"
            );

            SharedNpcWorldRelay
                .untrackCanonicalNpc(
                    world,
                    transientNpc.id
                );
            SharedNpcWorldRelay
                .syncCanonicalNpcs(
                    writerA
                );

            require(
                npcsA.canonical(
                    transientNpc.id
                )==null,
                "transient projection untrack failed"
            );

            world.npcs().remove(
                transientNpc.id
            );
            movementA.returnHome();
            SharedNpcWorldRelay
                .syncCanonicalNpcs(
                    writerA
                );

            require(
                npcsA.canonical(
                    npc.id
                )!=null,
                "HOME re-entry after transient region did not restore tracked NPC"
            );

            require(
                world.npcs().remove(
                    npc.id
                ),
                "canonical despawn fixture"
            );

            sync(
                writerA,
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

            require(
                !SharedNpcWorldRelay
                    .untrackCanonicalNpc(
                        world,
                        npc.id
                    ),
                "missing canonical NPC was not pruned from tracked projection set"
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
            world.petNpcs()
                .removeMainAndMini(
                    a.id()
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

    private static void sync(
        ServerPacketWriter... writers
    ){
        for(ServerPacketWriter writer:
                writers)
            SharedNpcWorldRelay
                .syncCanonicalNpcs(
                    writer
                );
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
