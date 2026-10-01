package spk.local;

import java.util.Collections;

public final class MonsterSpawnerNpcLifecycleBindingTest {
    private static final String OWNER="monster-spawner-lifecycle";

    public static void main(String[] args)throws Exception{
        legacyCombatSpawnCompatible();
        atomicSpawnCombatLifecycle();
        registrationCommitAtomic();
        registrationCommitFailureRollback();
        resolverUnlocked();
        resolverFailureRollback();
        invalidHpRollback();
        lifecycleFailureRollback();
        authorityBoundary();

        System.out.println(
            "MONSTER_SPAWNER_NPC_LIFECYCLE_BINDING_PASS "+
            "atomicSpawnCombatLifecycle=true "+
            "registrationCommitAtomic=true "+
            "commitFailureRollback=true "+
            "callerMaxHp=true "+
            "lifecycleAlive=true "+
            "canonicalDamageable=true "+
            "resolverUnlocked=true "+
            "resolverFailureRollback=true "+
            "invalidHpRollback=true "+
            "lifecycleFailureRollback=true "+
            "budgetRollback=true "+
            "callerAuthority=true "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void legacyCombatSpawnCompatible()
        throws Exception{
        Fixture f=new Fixture();
        try{
            MonsterSpawnerCombatBindingService.Result result=
                f.combat().spawnAndBind(
                    OWNER,
                    3087,
                    3495,
                    0
                );

            require(
                f.lifecycle.get(result.spawn.npc.id)==null&&
                f.binder.get(result.spawn.npc.id)!=null&&
                f.world.npcTickTargetCount()==1,
                "legacy combat-only spawn changed behavior"
            );

            f.combat().despawnAndUnbind(
                OWNER,
                result.spawn.npc.id
            );
        }finally{
            f.close();
        }
    }

    private static void atomicSpawnCombatLifecycle()
        throws Exception{
        Fixture f=new Fixture();
        try{
            MonsterSpawnerNpcLifecycleBindingService service=
                f.lifecycleBinding(
                    context->
                        new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "test-hp-37",
                                37,
                                "CUSTOM_LOCALLAB_MONSTER_HP"
                            )
                );

            MonsterSpawnerNpcLifecycleBindingService.Result
                result=
                    service.spawnBindAndRegister(
                        OWNER,
                        3087,
                        3495,
                        0
                    );

            EntityId id=result.combat.spawn.npc.id;
            NpcLifecycleService.Snapshot lifecycle=
                f.lifecycle.get(id);

            require(
                f.world.npcs().byId(id)==
                    result.combat.spawn.npc&&
                f.spawner.getSession(OWNER).tracks(id)&&
                f.binder.get(id)!=null&&
                f.world.npcTickTargetCount()==1&&
                lifecycle!=null&&
                lifecycle.state==
                    NpcLifecycleService.State.ALIVE&&
                lifecycle.hitpoints==37&&
                lifecycle.maxHitpoints==37&&
                "CUSTOM_LOCALLAB_MONSTER_HP".equals(
                    lifecycle.sourceAuthority
                )&&
                "test-hp-37".equals(
                    result.lifecyclePlanKey
                ),
                "atomic lifecycle publication"
            );

            NpcLifecycleService.DamageResult damage=
                f.lifecycle.applyDamage(
                    id,
                    7,
                    f.world.clock().tick()
                );

            require(
                damage.appliedDamage==7&&
                damage.hitpointsBefore==37&&
                damage.hitpointsAfter==30&&
                !damage.newlyDied&&
                f.lifecycle.get(id).hitpoints==30,
                "spawned combat NPC not canonically damageable"
            );
        }finally{
            f.close();
        }
    }

    private static void registrationCommitAtomic()
        throws Exception{
        Fixture f=new Fixture();

        try{
            MonsterSpawnerNpcLifecycleBindingService service=
                f.lifecycleBinding(
                    context->
                        new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "commit-hp-23",
                                23,
                                "CUSTOM_LOCALLAB_MONSTER_HP"
                            )
                );

            final int[] calls={0};

            MonsterSpawnerNpcLifecycleBindingService.Result result=
                service.spawnBindAndRegisterComposed(
                    OWNER,
                    3087,
                    3495,
                    0,
                    (npc,lifecycle,planKey)->{
                        calls[0]++;

                        MonsterSpawnerService.SessionSnapshot session=
                            f.spawner.getSession(
                                OWNER
                            );

                        require(
                            f.world.npcs().byId(
                                npc.id
                            )==npc&&
                            session!=null&&
                            session.tracks(
                                npc.id
                            )&&
                            f.binder.get(
                                npc.id
                            )!=null&&
                            f.world.npcTickTargetCount()==1&&
                            f.lifecycle.get(
                                npc.id
                            )!=null&&
                            lifecycle.npcId.equals(
                                npc.id
                            )&&
                            "commit-hp-23".equals(
                                planKey
                            ),
                            "registration commit did not observe full composed graph"
                        );
                    }
                );

            require(
                calls[0]==1&&
                f.world.npcs().byId(
                    result.combat.spawn.npc.id
                )==result.combat.spawn.npc,
                "registration commit publication"
            );
        }finally{
            f.close();
        }
    }

    private static void registrationCommitFailureRollback()
        throws Exception{
        Fixture f=new Fixture();

        try{
            MonsterSpawnerNpcLifecycleBindingService service=
                f.lifecycleBinding(
                    context->
                        new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "commit-failure-hp",
                                19,
                                "CUSTOM_LOCALLAB_MONSTER_HP"
                            )
                );

            final IllegalStateException primary=
                new IllegalStateException(
                    "REGISTRATION_COMMIT_FAILURE"
                );

            Throwable observed=
                capture(
                    ()->service.spawnBindAndRegisterComposed(
                        OWNER,
                        3087,
                        3495,
                        0,
                        (npc,lifecycle,planKey)->{
                            require(
                                f.world.npcs().byId(
                                    npc.id
                                )==npc&&
                                f.spawner.getSession(
                                    OWNER
                                ).tracks(
                                    npc.id
                                )&&
                                f.binder.get(
                                    npc.id
                                )!=null&&
                                f.lifecycle.get(
                                    npc.id
                                )!=null,
                                "commit failure probe missing composed ownership"
                            );

                            throw primary;
                        }
                    )
                );

            require(
                observed==primary,
                "registration commit failure identity lost"
            );

            assertCleanRollback(
                f,
                "registration commit failure rollback"
            );
        }finally{
            f.close();
        }
    }

    private static void resolverFailureRollback()
        throws Exception{
        Fixture f=new Fixture();
        try{
            final IllegalStateException primary=
                new IllegalStateException(
                    "LIFECYCLE_RESOLVER_FAILURE"
                );

            MonsterSpawnerNpcLifecycleBindingService service=
                f.lifecycleBinding(
                    context->{ throw primary; }
                );

            Throwable observed=
                capture(
                    ()->service.spawnBindAndRegister(
                        OWNER,
                        3087,
                        3495,
                        0
                    )
                );

            require(
                observed==primary,
                "resolver failure identity lost"
            );

            assertCleanRollback(
                f,
                "resolver failure rollback"
            );
        }finally{
            f.close();
        }
    }

    private static void invalidHpRollback()
        throws Exception{
        Fixture f=new Fixture();
        try{
            MonsterSpawnerNpcLifecycleBindingService service=
                f.lifecycleBinding(
                    context->
                        new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "invalid-zero",
                                0,
                                "CUSTOM_LOCALLAB_MONSTER_HP"
                            )
                );

            expect(
                IllegalArgumentException.class,
                ()->service.spawnBindAndRegister(
                    OWNER,
                    3087,
                    3495,
                    0
                ),
                "invalid HP"
            );

            assertCleanRollback(
                f,
                "invalid HP rollback"
            );
        }finally{
            f.close();
        }
    }

    private static void resolverUnlocked()
        throws Exception{
        Fixture f=new Fixture();
        try{
            final Throwable[] competitorFailure={null};

            MonsterSpawnerNpcLifecycleBindingService service=
                f.lifecycleBinding(
                    context->{
                        java.util.concurrent.CountDownLatch done=
                            new java.util.concurrent.CountDownLatch(1);

                        Thread competitor=
                            new Thread(
                                ()->{
                                    try{
                                        f.world.npcs().snapshot();
                                    }catch(Throwable failure){
                                        competitorFailure[0]=failure;
                                    }finally{
                                        done.countDown();
                                    }
                                },
                                "npc-lifecycle-resolver-registry-probe"
                            );

                        competitor.start();

                        if(!done.await(
                                2L,
                                java.util.concurrent.TimeUnit.SECONDS))
                            throw new AssertionError(
                                "lifecycle resolver ran while WorldNpcRegistry mutation ownership was retained"
                            );

                        if(competitorFailure[0]!=null)
                            throw new AssertionError(
                                "registry competitor failed",
                                competitorFailure[0]
                            );

                        return new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "resolver-unlocked",
                                31,
                                "CUSTOM_LOCALLAB_MONSTER_HP"
                            );
                    }
                );

            MonsterSpawnerNpcLifecycleBindingService.Result result=
                service.spawnBindAndRegister(
                    OWNER,
                    3087,
                    3495,
                    0
                );

            require(
                result.lifecycle.maxHitpoints==31&&
                f.lifecycle.get(
                    result.combat.spawn.npc.id
                )!=null,
                "resolver-unlocked success publication"
            );
        }finally{
            f.close();
        }
    }

    private static void lifecycleFailureRollback()
        throws Exception{
        Fixture f=new Fixture();
        try{
            final boolean[] injected={false};

            MonsterSpawnerNpcLifecycleBindingService service=
                f.lifecycleBinding(
                    context->{
                        WorldNpc npc=
                            f.world.npcs().byId(
                                context.npcId
                            );

                        require(
                            npc!=null,
                            "resolver could not observe fresh canonical NPC"
                        );

                        f.lifecycle.register(
                            npc,
                            19,
                            "CUSTOM_LOCALLAB_INJECTED_DUPLICATE"
                        );
                        injected[0]=true;

                        return new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "duplicate-registration",
                                23,
                                "CUSTOM_LOCALLAB_MONSTER_HP"
                            );
                    }
                );

            Throwable observed=
                capture(
                    ()->service.spawnBindAndRegister(
                        OWNER,
                        3087,
                        3495,
                        0
                    )
                );

            require(
                injected[0]&&
                observed instanceof IllegalStateException,
                "lifecycle duplicate registration failure not observed"
            );

            assertCleanRollback(
                f,
                "lifecycle registration failure rollback"
            );
        }finally{
            f.close();
        }
    }

    private static void authorityBoundary()
        throws Exception{
        Fixture f=new Fixture();
        try{
            NpcLifecycleService privateLifecycle=
                new NpcLifecycleService(
                    f.world.npcs()
                );

            expect(
                IllegalArgumentException.class,
                ()->new MonsterSpawnerNpcLifecycleBindingService(
                    f.world,
                    f.combat(),
                    privateLifecycle,
                    context->
                        new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "private-lifecycle",
                                10,
                                "CUSTOM_LOCALLAB_MONSTER_HP"
                            )
                ),
                "private lifecycle authority"
            );
        }finally{
            f.close();
        }

        expect(
            IllegalArgumentException.class,
            ()->new MonsterSpawnerNpcLifecycleBindingService
                .LifecyclePlan(
                    "client-hp",
                    10,
                    "EXACT_CURRENT_CLIENT"
                ),
            "client lifecycle authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new MonsterSpawnerNpcLifecycleBindingService
                .LifecyclePlan(
                    "unknown-hp",
                    10,
                    "UNKNOWN_SERVER_AUTHORITY"
                ),
            "unknown lifecycle authority"
        );
    }

    private static void assertCleanRollback(
        Fixture f,
        String label
    ){
        MonsterSpawnerService.SessionSnapshot session=
            f.spawner.getSession(OWNER);

        require(
            session!=null&&
            session.active&&
            session.remainingSpawnBudget==1&&
            session.spawnedNpcIds.isEmpty()&&
            f.binder.size()==0&&
            f.world.npcTickTargetCount()==0&&
            f.lifecycle.size()==0&&
            f.world.npcs().size()==0,
            label
        );
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

        MonsterSpawnerCombatBindingService combat(){
            return new MonsterSpawnerCombatBindingService(
                world,
                spawner,
                binder
            );
        }

        MonsterSpawnerNpcLifecycleBindingService
            lifecycleBinding(
                MonsterSpawnerNpcLifecycleBindingService
                    .LifecyclePlanResolver resolver
            ){
            return new MonsterSpawnerNpcLifecycleBindingService(
                world,
                combat(),
                lifecycle,
                resolver
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
                    "lifecycle-test",
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
            "lifecycle-combat",
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
                    NpcPlayerCombatResolutionService
                        .DamageContext context
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

    private static Throwable capture(
        ThrowingRunnable action
    ){
        try{
            action.run();
        }catch(Throwable failure){
            return failure;
        }

        throw new AssertionError(
            "expected failure"
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
        String label
    ){
        Throwable failure=capture(action);

        if(!type.isInstance(failure))
            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
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

    private MonsterSpawnerNpcLifecycleBindingTest(){}
}
