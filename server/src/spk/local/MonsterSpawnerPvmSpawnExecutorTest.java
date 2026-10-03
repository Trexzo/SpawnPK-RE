package spk.local;

import java.util.Collections;
import java.util.List;

public final class MonsterSpawnerPvmSpawnExecutorTest {
    private static final String OWNER="spawn-executor-owner";

    public static void main(String[] args)throws Exception{
        notReadySkipsPolicy();
        policyFailureDoesNotMutate();
        authorityDriftFailsBeforePolicy();
        authorityDriftDuringPolicyFailsBeforeSpawn();
        staleSessionDoesNotSpawn();
        exactActiveSnapshotSpawns();
        exactGraphRequired();

        System.out.println(
            "MONSTER_SPAWNER_PVM_SPAWN_EXECUTOR_PASS "+
            "notReadyNoPolicy=true "+
            "policyFailureAtomic=true "+
            "authorityDriftRejected=true "+
            "authorityDriftDuringPolicyRejected=true "+
            "staleSnapshotNoSpawn=true "+
            "exactSnapshotSpawn=true "+
            "budgetConsumedOnce=true "+
            "runtimeOwned=true "+
            "exactGraph=true "+
            "policyOwned=true"
        );
    }

    private static void notReadySkipsPolicy()
        throws Exception{
        Fixture f=new Fixture();
        int[] calls={0};

        try{
            MonsterSpawnerPvmSpawnExecutor executor=
                f.executor(
                    context->{
                        calls[0]++;
                        return new MonsterSpawnerPvmSpawnExecutor.Request(
                            OWNER,
                            new Tile(3088,3495,0)
                        );
                    }
                );

            MonsterSpawnerPvmSpawnExecutor.Result result=
                executor.execute(
                    OWNER
                );

            require(
                result.status==
                    MonsterSpawnerPvmSpawnExecutor.Status.NOT_READY&&
                result.spawn==null&&
                calls[0]==0&&
                f.world.npcs().size()==0&&
                f.runtime.size()==0,
                "inactive session invoked policy or spawned"
            );
        }finally{
            f.close();
        }
    }

    private static void policyFailureDoesNotMutate()
        throws Exception{
        Fixture f=new Fixture();
        f.spawner.activate(
            OWNER,
            1
        );

        try{
            MonsterSpawnerPvmSpawnExecutor executor=
                f.executor(
                    context->{
                        throw new IllegalStateException(
                            "REQUEST_POLICY_FAILURE"
                        );
                    }
                );

            expect(
                IllegalStateException.class,
                ()->executor.execute(
                    OWNER
                ),
                "request policy failure"
            );

            MonsterSpawnerService.SessionSnapshot after=
                f.spawner.getSession(
                    OWNER
                );

            require(
                after.active&&
                after.remainingSpawnBudget==1&&
                after.spawnedNpcIds.isEmpty()&&
                f.world.npcs().size()==0&&
                f.runtime.size()==0,
                "policy failure mutated spawn state"
            );
        }finally{
            f.close();
        }
    }

    private static void authorityDriftFailsBeforePolicy()
        throws Exception{
        Fixture f=new Fixture();
        f.spawner.activate(
            OWNER,
            1
        );

        final String[] authority={
            "CUSTOM_LOCALLAB_SPAWN_REQUEST_A"
        };
        final int[] resolveCalls={0};

        try{
            MonsterSpawnerPvmSpawnExecutor executor=
                new MonsterSpawnerPvmSpawnExecutor(
                    f.world,
                    f.spawner,
                    f.runtime,
                    new MonsterSpawnerPvmSpawnExecutor
                        .RequestResolver(){
                        @Override public MonsterSpawnerPvmSpawnExecutor.Request
                            resolve(
                                MonsterSpawnerPvmSpawnExecutor.Context context
                            ){
                            resolveCalls[0]++;

                            return new MonsterSpawnerPvmSpawnExecutor.Request(
                                OWNER,
                                new Tile(3088,3495,0)
                            );
                        }

                        @Override public String authority(){
                            return authority[0];
                        }
                    }
                );

            require(
                "CUSTOM_LOCALLAB_SPAWN_REQUEST_A".equals(
                    executor.requestAuthority()
                ),
                "captured request authority"
            );

            authority[0]=
                "CUSTOM_LOCALLAB_SPAWN_REQUEST_B";

            expect(
                IllegalStateException.class,
                ()->executor.execute(
                    OWNER
                ),
                "request authority drift"
            );

            MonsterSpawnerService.SessionSnapshot after=
                f.spawner.getSession(
                    OWNER
                );

            require(
                resolveCalls[0]==0&&
                after.active&&
                after.remainingSpawnBudget==1&&
                after.spawnedNpcIds.isEmpty()&&
                f.world.npcs().size()==0&&
                f.lifecycle.size()==0&&
                f.runtime.size()==0,
                "authority drift invoked policy or mutated spawn state"
            );
        }finally{
            f.close();
        }
    }

    private static void authorityDriftDuringPolicyFailsBeforeSpawn()
        throws Exception{
        Fixture f=new Fixture();
        f.spawner.activate(
            OWNER,
            1
        );

        final String[] authority={
            "CUSTOM_LOCALLAB_SPAWN_REQUEST_A"
        };
        final int[] resolveCalls={0};

        try{
            MonsterSpawnerPvmSpawnExecutor executor=
                new MonsterSpawnerPvmSpawnExecutor(
                    f.world,
                    f.spawner,
                    f.runtime,
                    new MonsterSpawnerPvmSpawnExecutor
                        .RequestResolver(){
                        @Override public MonsterSpawnerPvmSpawnExecutor.Request
                            resolve(
                                MonsterSpawnerPvmSpawnExecutor.Context context
                            ){
                            resolveCalls[0]++;
                            authority[0]=
                                "CUSTOM_LOCALLAB_SPAWN_REQUEST_B";

                            return new MonsterSpawnerPvmSpawnExecutor.Request(
                                OWNER,
                                new Tile(3088,3495,0)
                            );
                        }

                        @Override public String authority(){
                            return authority[0];
                        }
                    }
                );

            expect(
                IllegalStateException.class,
                ()->executor.execute(
                    OWNER
                ),
                "request authority drift during policy"
            );

            MonsterSpawnerService.SessionSnapshot after=
                f.spawner.getSession(
                    OWNER
                );

            require(
                resolveCalls[0]==1&&
                after.active&&
                after.remainingSpawnBudget==1&&
                after.spawnedNpcIds.isEmpty()&&
                f.world.npcs().size()==0&&
                f.lifecycle.size()==0&&
                f.runtime.size()==0,
                "authority drift during policy mutated spawn state"
            );
        }finally{
            f.close();
        }
    }

    private static void staleSessionDoesNotSpawn()
        throws Exception{
        Fixture f=new Fixture();
        f.spawner.activate(
            OWNER,
            1
        );
        int[] calls={0};

        try{
            MonsterSpawnerPvmSpawnExecutor executor=
                f.executor(
                    context->{
                        calls[0]++;

                        f.spawner.deactivate(
                            OWNER
                        );
                        f.spawner.activate(
                            OWNER,
                            2
                        );

                        return new MonsterSpawnerPvmSpawnExecutor.Request(
                            OWNER,
                            new Tile(3088,3495,0)
                        );
                    }
                );

            MonsterSpawnerPvmSpawnExecutor.Result result=
                executor.execute(
                    OWNER
                );

            MonsterSpawnerService.SessionSnapshot after=
                f.spawner.getSession(
                    OWNER
                );

            require(
                result.status==
                    MonsterSpawnerPvmSpawnExecutor.Status.STALE_SESSION&&
                result.spawn==null&&
                calls[0]==1&&
                after.active&&
                after.remainingSpawnBudget==2&&
                after.spawnedNpcIds.isEmpty()&&
                f.world.npcs().size()==0&&
                f.runtime.size()==0,
                "stale request spawned or consumed replacement budget"
            );
        }finally{
            f.close();
        }
    }

    private static void exactActiveSnapshotSpawns()
        throws Exception{
        Fixture f=new Fixture();
        f.spawner.activate(
            OWNER,
            2
        );
        int[] calls={0};

        try{
            MonsterSpawnerPvmSpawnExecutor executor=
                f.executor(
                    context->{
                        calls[0]++;

                        require(
                            context.session.active&&
                            context.session.remainingSpawnBudget==2&&
                            context.session.selectedDefinitionId!=null&&
                            context.session.selectedDefinitionId.intValue()==1530,
                            "request context lost active selected identity"
                        );

                        return new MonsterSpawnerPvmSpawnExecutor.Request(
                            OWNER,
                            new Tile(3088,3495,0)
                        );
                    }
                );

            MonsterSpawnerPvmSpawnExecutor.Result result=
                executor.execute(
                    OWNER
                );

            MonsterSpawnerService.SessionSnapshot after=
                f.spawner.getSession(
                    OWNER
                );

            WorldNpc npc=
                result.spawn==null
                    ?null
                    :result.spawn.spawn.combat.spawn.npc;

            require(
                result.status==
                    MonsterSpawnerPvmSpawnExecutor.Status.SPAWNED&&
                npc!=null&&
                calls[0]==1&&
                after.active&&
                after.remainingSpawnBudget==1&&
                after.spawnedNpcIds.size()==1&&
                after.tracks(npc.id)&&
                f.world.npcs().byId(npc.id)==npc&&
                f.world.npcLifecycle().get(npc.id)!=null&&
                f.runtime.get(npc.id)!=null&&
                f.runtime.size()==1&&
                executor.requestAuthority().equals(
                    "CUSTOM_LOCALLAB_SPAWN_REQUEST"
                ),
                "exact active request did not compose one canonical PvM spawn"
            );
        }finally{
            f.close();
        }
    }

    private static void exactGraphRequired()
        throws Exception{
        Fixture f=new Fixture();

        try{
            MonsterSpawnerService alternate=
                new MonsterSpawnerService(
                    f.world.npcs()
                );

            expect(
                IllegalArgumentException.class,
                ()->new MonsterSpawnerPvmSpawnExecutor(
                    f.world,
                    alternate,
                    f.runtime,
                    resolver(
                        context->
                            new MonsterSpawnerPvmSpawnExecutor.Request(
                                OWNER,
                                new Tile(3088,3495,0)
                            )
                    )
                ),
                "wrong service graph"
            );
        }finally{
            f.close();
        }
    }

    private interface RequestBody {
        MonsterSpawnerPvmSpawnExecutor.Request resolve(
            MonsterSpawnerPvmSpawnExecutor.Context context
        ) throws Exception;
    }

    private static MonsterSpawnerPvmSpawnExecutor.RequestResolver
        resolver(
            RequestBody body
        ){
        return new MonsterSpawnerPvmSpawnExecutor.RequestResolver(){
            @Override public MonsterSpawnerPvmSpawnExecutor.Request
                resolve(
                    MonsterSpawnerPvmSpawnExecutor.Context context
                )throws Exception{
                return body.resolve(
                    context
                );
            }

            @Override public String authority(){
                return "CUSTOM_LOCALLAB_SPAWN_REQUEST";
            }
        };
    }

    private static final class Fixture {
        final World world=
            World.isolatedForTest(
                600L
            );
        final MonsterSpawnerService spawner=
            new MonsterSpawnerService(
                world.npcs()
            );
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
        final MonsterSpawnerNpcLifecycleBindingService
            lifecycleBinding=
                new MonsterSpawnerNpcLifecycleBindingService(
                    world,
                    combat,
                    lifecycle,
                    context->
                        new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "spawn-executor-hp",
                                10,
                                "CUSTOM_LOCALLAB_MONSTER_HP"
                            )
                );
        final NpcDropResolutionService drops=
            new NpcDropResolutionService(
                world.npcs(),
                lifecycle,
                new NpcDropResolutionService.DropResolver(){
                    @Override public List<NpcDropResolutionService.Drop>
                        resolve(
                            NpcDropResolutionService.DeathContext context
                        ){
                        return Collections.singletonList(
                            new NpcDropResolutionService.Drop(
                                995,
                                1
                            )
                        );
                    }

                    @Override public String authority(){
                        return "CUSTOM_LOCALLAB_DROP_TEST";
                    }
                }
            );
        final MonsterSpawnerNpcDeathFinalizationService
            finalizer=
                new MonsterSpawnerNpcDeathFinalizationService(
                    world,
                    combat,
                    lifecycle,
                    drops
                );
        final NpcDropGroundSettlementService settlement=
            new NpcDropGroundSettlementService(
                world,
                "CUSTOM_LOCALLAB_SETTLEMENT",
                NpcDropGroundSettlementService
                    .OWNER_SCOPED_DEATH_TILE
            );
        final MonsterSpawnerPvmRuntime runtime=
            new MonsterSpawnerPvmRuntime(
                world,
                lifecycleBinding,
                finalizer,
                settlement
            );

        Fixture(){
            spawner.replaceCatalog(
                Collections.singletonList(
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "spawn-executor",
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
        }

        MonsterSpawnerPvmSpawnExecutor executor(
            RequestBody body
        ){
            return new MonsterSpawnerPvmSpawnExecutor(
                world,
                spawner,
                runtime,
                resolver(
                    body
                )
            );
        }

        void close(){
            world.close();
        }
    }

    private static NpcCombatRuntimeBinder.BehaviorPlan
        combatPlan(){
        return new NpcCombatRuntimeBinder.BehaviorPlan(
            "spawn-executor-combat",
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

    private MonsterSpawnerPvmSpawnExecutorTest(){}
}
