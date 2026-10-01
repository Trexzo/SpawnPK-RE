package spk.local;

import java.util.*;

public final class MonsterSpawnerPvmRuntimeTest {
    private static final String OWNER="pvm-runtime-owner";

    public static void main(String[] args)throws Exception{
        successfulLifecycle();
        aliveAndForeignFailClosed();
        settlementPendingRetry();
        spawnFailurePublishesNothing();
        exactGraphRequired();

        System.out.println(
            "MONSTER_SPAWNER_PVM_RUNTIME_PASS "+
            "singleRuntimeOwner=true "+
            "spawnOwnership=true "+
            "spawnOwnershipAtomic=true "+
            "exactWorldLifecycle=true "+
            "deathFinalization=true "+
            "settlement=true "+
            "settlementPendingRetry=true "+
            "dropResolutionOnce=true "+
            "duplicateDeathBlocked=true "+
            "protocolIndependent=true"
        );
    }

    private static void successfulLifecycle()
        throws Exception{
        Fixture f=new Fixture(false);

        try{
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc npc=
                spawned.spawn.combat.spawn.npc;

            require(
                spawned.runtime.state==
                    MonsterSpawnerPvmRuntime.State.ACTIVE&&
                f.runtime.size()==1&&
                f.runtime.get(npc.id)!=null&&
                f.lifecycle.get(npc.id)!=null&&
                f.binder.get(npc.id)!=null,
                "runtime spawn ownership"
            );

            require(
                f.lifecycle.applyDamage(
                    npc.id,
                    99,
                    50L
                ).newlyDied,
                "runtime lethal fixture"
            );

            MonsterSpawnerPvmRuntime.FinalizeResult result=
                f.runtime.finalizeIfOwned(
                    npc
                );

            require(
                result.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                result.finalization!=null&&
                result.settlement!=null&&
                result.settlement.npcId.equals(
                    npc.id
                )&&
                result.settlement.groundItems.size()==1&&
                f.world.groundItems().size()==1&&
                f.runtime.get(npc.id)==null&&
                f.runtime.size()==0&&
                f.world.npcs().byId(npc.id)==null&&
                f.lifecycle.get(npc.id)==null&&
                f.binder.get(npc.id)==null&&
                f.dropCalls==1,
                "runtime terminal composition"
            );

            MonsterSpawnerPvmRuntime.FinalizeResult duplicate=
                f.runtime.finalizeIfOwned(
                    npc
                );

            require(
                duplicate.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                f.world.groundItems().size()==1&&
                f.dropCalls==1,
                "duplicate runtime finalization"
            );
        }finally{
            f.close();
        }
    }

    private static void aliveAndForeignFailClosed()
        throws Exception{
        Fixture f=new Fixture(false);

        try{
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc npc=
                spawned.spawn.combat.spawn.npc;

            expect(
                IllegalStateException.class,
                ()->f.runtime.finalizeIfOwned(
                    npc
                ),
                "alive owned finalization"
            );

            require(
                f.runtime.get(npc.id)!=null&&
                f.runtime.get(npc.id).state==
                    MonsterSpawnerPvmRuntime.State.ACTIVE&&
                f.world.npcs().byId(npc.id)==npc&&
                f.lifecycle.get(npc.id).alive()&&
                f.dropCalls==0,
                "alive rejection changed runtime"
            );

            WorldNpc foreign=
                f.world.npcs().spawn(
                    1700,
                    3090,
                    3495,
                    0
                );

            MonsterSpawnerPvmRuntime.FinalizeResult notOwned=
                f.runtime.finalizeIfOwned(
                    foreign
                );

            require(
                notOwned.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                f.world.npcs().byId(foreign.id)==foreign,
                "foreign NPC mutated"
            );

            f.world.npcs().remove(
                foreign.id
            );
        }finally{
            f.close();
        }
    }

    private static void settlementPendingRetry()
        throws Exception{
        Fixture f=new Fixture(false);

        try{
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                f.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc npc=
                spawned.spawn.combat.spawn.npc;
            Tile deathTile=
                npc.tile();

            GroundItem blocker=
                f.world.groundItems().add(
                    995,
                    Integer.MAX_VALUE,
                    deathTile,
                    "killer",
                    1L,
                    false
                );

            require(
                f.lifecycle.applyDamage(
                    npc.id,
                    99,
                    60L
                ).newlyDied,
                "pending lethal fixture"
            );

            MonsterSpawnerPvmRuntime.FinalizeResult pending=
                f.runtime.finalizeIfOwned(
                    npc
                );

            MonsterSpawnerPvmRuntime.Snapshot snapshot=
                f.runtime.get(
                    npc.id
                );

            require(
                pending.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.SETTLEMENT_PENDING&&
                pending.finalization!=null&&
                pending.settlement==null&&
                snapshot!=null&&
                snapshot.state==
                    MonsterSpawnerPvmRuntime.State.SETTLEMENT_PENDING&&
                snapshot.deathTick!=null&&
                snapshot.deathTick.longValue()==60L&&
                f.world.npcs().byId(npc.id)==null&&
                f.lifecycle.get(npc.id)==null&&
                f.binder.get(npc.id)==null&&
                f.dropCalls==1&&
                f.settlement.get(npc.id)==null,
                "settlement pending state"
            );

            require(
                f.world.groundItems().remove(
                    blocker.id
                ),
                "remove overflow blocker"
            );

            MonsterSpawnerPvmRuntime.FinalizeResult retried=
                f.runtime.retrySettlement(
                    npc.id
                );

            require(
                retried.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                retried.settlement!=null&&
                f.runtime.get(npc.id)==null&&
                f.runtime.size()==0&&
                f.dropCalls==1&&
                f.world.groundItems().findOwned(
                    995,
                    deathTile.x,
                    deathTile.y,
                    deathTile.plane,
                    "killer"
                )!=null,
                "pending settlement retry"
            );
        }finally{
            f.close();
        }
    }

    private static void spawnFailurePublishesNothing()
        throws Exception{
        Fixture f=new Fixture(true);

        try{
            expect(
                IllegalStateException.class,
                ()->f.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                ),
                "lifecycle spawn failure"
            );

            require(
                f.runtime.size()==0&&
                f.world.npcs().size()==0&&
                f.lifecycle.size()==0&&
                f.binder.size()==0&&
                f.spawner.getSession(OWNER)
                    .spawnedNpcIds.isEmpty(),
                "failed spawn published runtime ownership"
            );
        }finally{
            f.close();
        }
    }

    private static void exactGraphRequired()
        throws Exception{
        Fixture f=new Fixture(false);
        World other=
            World.isolatedForTest(600L);

        try{
            NpcDropGroundSettlementService wrongSettlement=
                new NpcDropGroundSettlementService(
                    other,
                    "CUSTOM_LOCALLAB_SETTLEMENT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            expect(
                IllegalArgumentException.class,
                ()->new MonsterSpawnerPvmRuntime(
                    f.world,
                    f.lifecycleBinding,
                    f.finalizer,
                    wrongSettlement
                ),
                "mismatched settlement World"
            );
        }finally{
            other.close();
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
        final boolean failLifecycle;
        int dropCalls;

        final MonsterSpawnerNpcLifecycleBindingService
            lifecycleBinding;
        final NpcDropResolutionService drops;
        final MonsterSpawnerNpcDeathFinalizationService
            finalizer;
        final NpcDropGroundSettlementService settlement;
        final MonsterSpawnerPvmRuntime runtime;

        Fixture(
            boolean failLifecycle
        ){
            this.failLifecycle=failLifecycle;

            lifecycleBinding=
                new MonsterSpawnerNpcLifecycleBindingService(
                    world,
                    combat,
                    lifecycle,
                    context->{
                        if(this.failLifecycle)
                            throw new IllegalStateException(
                                "LIFECYCLE_POLICY_FAILURE"
                            );

                        return new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "pvm-runtime-hp",
                                10,
                                "CUSTOM_LOCALLAB_MONSTER_HP"
                            );
                    }
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

                            return Collections.singletonList(
                                new NpcDropResolutionService.Drop(
                                    995,
                                    10
                                )
                            );
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB_DROP_TEST";
                        }
                    }
                );

            finalizer=
                new MonsterSpawnerNpcDeathFinalizationService(
                    world,
                    combat,
                    lifecycle,
                    drops
                );

            settlement=
                new NpcDropGroundSettlementService(
                    world,
                    "CUSTOM_LOCALLAB_SETTLEMENT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            runtime=
                new MonsterSpawnerPvmRuntime(
                    world,
                    lifecycleBinding,
                    finalizer,
                    settlement
                );
        }

        void close(){
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
                    "pvm-runtime",
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
            "pvm-runtime-combat",
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

    private MonsterSpawnerPvmRuntimeTest(){}
}
