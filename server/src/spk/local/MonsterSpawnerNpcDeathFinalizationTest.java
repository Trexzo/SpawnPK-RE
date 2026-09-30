package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

public final class MonsterSpawnerNpcDeathFinalizationTest {
    private static final String OWNER="death-finalize-owner";

    public static void main(String[] args)throws Exception{
        successfulFinalization();
        dropFailurePreservesDeadRuntime();
        aliveRejectedBeforeDrops();

        System.out.println(
            "MONSTER_SPAWNER_NPC_DEATH_FINALIZATION_PASS "+
            "exactDeadNpc=true "+
            "worldLifecycleAuthority=true "+
            "dropsBeforeTeardown=true "+
            "dropFailureAtomic=true "+
            "lifecycleRemoved=true "+
            "combatUnbound=true "+
            "canonicalRemoved=true "+
            "projectionRemoved=true "+
            "dropsExactlyOnce=true "+
            "duplicateFailClosed=true "+
            "rewardsOwned=false "+
            "respawnOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void successfulFinalization()
        throws Exception{
        Fixture f=new Fixture(false);

        try{
            MonsterSpawnerNpcLifecycleBindingService.Result spawned=
                f.spawn();

            WorldNpc npc=
                spawned.combat.spawn.npc;

            f.project(
                npc
            );

            require(
                f.viewerNpcs.canonical(
                    npc.id
                )!=null,
                "canonical projection missing before death"
            );

            NpcLifecycleService.DamageResult lethal=
                f.lifecycle.applyDamage(
                    npc.id,
                    99,
                    41L
                );

            require(
                lethal.newlyDied&&
                f.lifecycle.get(npc.id).dead(),
                "lethal fixture"
            );

            MonsterSpawnerNpcDeathFinalizationService.Result result=
                f.finalizer().finalizeDead(
                    OWNER,
                    npc,
                    "player:killer"
                );

            require(
                result.npcId.equals(npc.id)&&
                result.definitionId==npc.definitionId&&
                result.deathTick==41L&&
                "player:killer".equals(
                    result.recipientRef
                )&&
                result.drops.drops.size()==1&&
                result.drops.drops.get(0).itemId==995&&
                result.drops.drops.get(0).amount==100&&
                f.dropCalls==1,
                "finalization result"
            );

            require(
                f.world.npcs().byId(npc.id)==null&&
                f.lifecycle.get(npc.id)==null&&
                f.binder.get(npc.id)==null&&
                f.world.npcTickTargetCount()==0&&
                !f.spawner.getSession(OWNER)
                    .tracks(npc.id),
                "terminal state not fully removed"
            );

            require(
                f.drops.get(npc.id)==result.drops&&
                f.dropCalls==1,
                "drop resolution not retained exactly once"
            );

            SharedNpcWorldRelay.syncRemotePets(
                f.viewerWriter
            );

            require(
                f.viewerNpcs.canonical(
                    npc.id
                )==null,
                "viewer projection retained after untrack + sync"
            );

            expect(
                IllegalStateException.class,
                ()->f.finalizer().finalizeDead(
                    OWNER,
                    npc,
                    "player:killer"
                ),
                "duplicate finalization"
            );

            require(
                f.dropCalls==1,
                "duplicate finalization reran drop resolver"
            );
        }finally{
            f.close();
        }
    }

    private static void dropFailurePreservesDeadRuntime()
        throws Exception{
        Fixture f=new Fixture(true);

        try{
            MonsterSpawnerNpcLifecycleBindingService.Result spawned=
                f.spawn();
            WorldNpc npc=
                spawned.combat.spawn.npc;

            f.project(npc);

            require(
                f.lifecycle.applyDamage(
                    npc.id,
                    99,
                    47L
                ).newlyDied,
                "drop-failure lethal fixture"
            );

            expect(
                IllegalStateException.class,
                ()->f.finalizer().finalizeDead(
                    OWNER,
                    npc,
                    "player:drop-failure"
                ),
                "drop resolver failure"
            );

            NpcLifecycleService.Snapshot dead=
                f.lifecycle.get(
                    npc.id
                );

            require(
                f.dropCalls==1&&
                f.world.npcs().byId(npc.id)==npc&&
                dead!=null&&
                dead.dead()&&
                dead.deathTick==47L&&
                f.binder.get(npc.id)!=null&&
                f.world.npcTickTargetCount()==1&&
                f.spawner.getSession(OWNER)
                    .tracks(npc.id)&&
                f.viewerNpcs.canonical(npc.id)!=null,
                "drop failure mutated terminal runtime"
            );
        }finally{
            f.close();
        }
    }

    private static void aliveRejectedBeforeDrops()
        throws Exception{
        Fixture f=new Fixture(false);

        try{
            MonsterSpawnerNpcLifecycleBindingService.Result spawned=
                f.spawn();
            WorldNpc npc=
                spawned.combat.spawn.npc;

            expect(
                IllegalStateException.class,
                ()->f.finalizer().finalizeDead(
                    OWNER,
                    npc,
                    "player:alive"
                ),
                "alive finalization"
            );

            require(
                f.dropCalls==0&&
                f.world.npcs().byId(npc.id)==npc&&
                f.lifecycle.get(npc.id).alive()&&
                f.binder.get(npc.id)!=null&&
                f.spawner.getSession(OWNER)
                    .tracks(npc.id),
                "alive rejection mutated state"
            );
        }finally{
            f.close();
        }
    }

    private static final class Fixture {
        final World world=
            World.isolatedForTest(600L);
        final MonsterSpawnerService spawner=
            configuredSpawner(world);
        final NpcLifecycleService lifecycle=
            world.npcLifecycle();
        final NpcCombatRuntimeBinder binder=
            new NpcCombatRuntimeBinder(
                world,
                context->combatPlan()
            );
        final MonsterSpawnerCombatBindingService combat=
            new MonsterSpawnerCombatBindingService(
                world,
                spawner,
                binder
            );
        final boolean failDrops;
        int dropCalls;

        final WorldPlayer viewer=
            new WorldPlayer();
        final long viewerGeneration=
            world.registerPlayer(
                viewer,
                "death-finalize-viewer"
            );
        final NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                viewer.id()
            );
        final ByteArrayOutputStream viewerBytes=
            new ByteArrayOutputStream();
        final ServerPacketWriter viewerWriter=
            writer(viewerBytes);

        final NpcDropResolutionService drops;

        Fixture(
            boolean failDrops
        ){
            this.failDrops=failDrops;

            viewer.movement()
                .restoreAccountState(
                    false,
                    100,
                    3087,
                    3495,
                    0
                );

            SharedNpcWorldRelay.register(
                viewerWriter,
                world,
                viewer,
                viewerNpcs,
                viewer.movement()
            );

            drops=
                new NpcDropResolutionService(
                    world.npcs(),
                    lifecycle,
                    new NpcDropResolutionService.DropResolver(){
                        @Override public List<NpcDropResolutionService.Drop>
                            resolve(
                                NpcDropResolutionService.DeathContext context
                            ){
                            dropCalls++;

                            if(Fixture.this.failDrops)
                                throw new IllegalStateException(
                                    "DROP_POLICY_FAILURE"
                                );

                            return Collections.singletonList(
                                new NpcDropResolutionService.Drop(
                                    995,
                                    100
                                )
                            );
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB_DROP_TEST";
                        }
                    }
                );
        }

        MonsterSpawnerNpcLifecycleBindingService.Result spawn()
            throws Exception{
            return new MonsterSpawnerNpcLifecycleBindingService(
                world,
                combat,
                lifecycle,
                context->
                    new MonsterSpawnerNpcLifecycleBindingService
                        .LifecyclePlan(
                            "death-finalize-hp",
                            10,
                            "CUSTOM_LOCALLAB_MONSTER_HP"
                        )
            ).spawnBindAndRegister(
                OWNER,
                3088,
                3495,
                0
            );
        }

        void project(
            WorldNpc npc
        ){
            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            require(
                viewerNpcs.canonical(
                    npc.id
                )!=null,
                "projection fixture"
            );
        }

        MonsterSpawnerNpcDeathFinalizationService finalizer(){
            return new MonsterSpawnerNpcDeathFinalizationService(
                world,
                combat,
                lifecycle,
                drops
            );
        }

        void close(){
            SharedNpcWorldRelay.unregister(
                viewerWriter
            );

            if(world.players().owns(
                    viewer,
                    viewerGeneration))
                world.unregisterPlayer(
                    viewer,
                    viewerGeneration
                );

            world.close();
        }
    }

    private static MonsterSpawnerService configuredSpawner(
        World world
    ){
        MonsterSpawnerService spawner=
            new MonsterSpawnerService(
                world.npcs()
            );

        spawner.replaceCatalog(
            Collections.singletonList(
                new MonsterSpawnerService.CatalogEntry(
                    0,
                    "death-finalize",
                    1530
                )
            ),
            "CUSTOM_LOCALLAB_CATALOG"
        );
        spawner.openSession(
            OWNER,
            "CUSTOM_LOCALLAB_SPAWNER"
        );
        spawner.selectRow(
            OWNER,
            0
        );
        spawner.activate(
            OWNER,
            1
        );

        return spawner;
    }

    private static NpcCombatRuntimeBinder.BehaviorPlan
        combatPlan(){
        return new NpcCombatRuntimeBinder.BehaviorPlan(
            "death-finalize-combat",
            "CUSTOM_LOCALLAB_PROFILE",
            context->
                NpcTargetAcquisitionService.Decision
                    .ineligible(),
            "CUSTOM_LOCALLAB_TARGET",
            "NONE",
            new NpcCombatApproachService.ApproachPolicy(){
                @Override public int stopRange(
                    NpcCombatApproachService.Context context
                ){ return 1; }

                @Override public RouteRequest.Policy routePolicy(
                    NpcCombatApproachService.Context context
                ){
                    return RouteRequest.Policy
                        .WORLD_STATIC_AUTHORITY;
                }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_NPC_APPROACH";
                }

                @Override public String policy(){
                    return "FIXED_RANGE_1";
                }
            },
            new NpcCombatEngagementService.CadenceResolver(){
                @Override public int nextDelayTicks(
                    NpcCombatEngagementService.Context context
                ){ return 3; }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_NPC_CADENCE";
                }

                @Override public String policy(){
                    return "FIXED_3";
                }
            },
            new NpcPlayerCombatResolutionService.DamageResolver(){
                @Override public int resolve(
                    NpcPlayerCombatResolutionService.DamageContext context
                ){ return 1; }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_NPC_DAMAGE";
                }

                @Override public String formula(){
                    return "FIXED_1";
                }
            },
            context->context.worldTick+1L,
            "CUSTOM_LOCALLAB_CONTROLLER",
            "CUSTOM_LOCALLAB_AI"
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream bytes
    ){
        return new ServerPacketWriter(
            bytes,
            new IsaacCipher(
                new int[4]
            )
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
            throw new AssertionError(label);
    }

    private MonsterSpawnerNpcDeathFinalizationTest(){}
}
