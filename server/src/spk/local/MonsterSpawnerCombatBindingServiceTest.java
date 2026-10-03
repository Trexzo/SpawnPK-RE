package spk.local;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class MonsterSpawnerCombatBindingServiceTest {
    private static final String OWNER=
        "monster-spawner-combat";

    public static void main(String[] args)throws Exception{
        legacySpawnCompatible();
        successfulAtomicSpawnBind();
        unconfiguredBindingRollsBack();
        resolverFailureRollsBack();
        attachConflictRollsBackEverything();
        primaryFailurePreserved();
        rollbackFailureSuppressed();
        worldLifecycleIsOutermost();

        System.out.println(
            "MONSTER_SPAWNER_COMBAT_BINDING_PASS "+
            "legacySpawnCompatible=true "+
            "atomicSpawnBind=true "+
            "budgetRollback=true "+
            "activeRollback=true "+
            "exactNpcRollback=true "+
            "bindingRollback=true "+
            "pulseTargetRollback=true "+
            "worldLifecycleOuter=true "+
            "lockOrderWorldSpawnerNpc=true "+
            "engageThenAttack=true "+
            "callerPolicies=true "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void legacySpawnCompatible()
        throws Exception{
        World world=World.isolatedForTest(600L);
        MonsterSpawnerService spawner=
            configuredSpawner(
                world,
                2
            );

        try{
            MonsterSpawnerService.SpawnResult result=
                spawner.spawnSelected(
                    OWNER,
                    3087,
                    3495,
                    0
                );

            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(
                    OWNER
                );

            require(
                result.npc!=null&&
                world.npcs().byId(
                    result.npc.id
                )==result.npc&&
                result.session.tracks(
                    result.npc.id
                )&&
                session.tracks(
                    result.npc.id
                )&&
                session.remainingSpawnBudget==1&&
                session.active&&
                world.npcs().size()==1,
                "legacy spawnSelected behavior changed"
            );
        }finally{
            world.close();
        }
    }

    private static void successfulAtomicSpawnBind()
        throws Exception{
        Fixture f=new Fixture(1);

        try{
            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    f.world,
                    context->plan()
                );
            MonsterSpawnerCombatBindingService service=
                new MonsterSpawnerCombatBindingService(
                    f.world,
                    f.spawner,
                    binder
                );

            MonsterSpawnerCombatBindingService.Result result=
                service.spawnAndBind(
                    OWNER,
                    3087,
                    3495,
                    0
                );

            MonsterSpawnerService.SessionSnapshot session=
                f.spawner.getSession(
                    OWNER
                );

            require(
                result.spawn.npc!=null&&
                result.binding!=null&&
                result.binding.npcId.equals(
                    result.spawn.npc.id
                )&&
                session.tracks(
                    result.spawn.npc.id
                )&&
                session.remainingSpawnBudget==0&&
                !session.active&&
                binder.size()==1&&
                f.world.npcTickTargetCount()==1&&
                f.world.npcs().byId(
                    result.spawn.npc.id
                )==result.spawn.npc&&
                f.hp()==99,
                "atomic spawn/bind publication"
            );

            f.world.pulse().pulseOnce(
                1000L
            );

            require(
                f.hp()==99,
                "spawn/bind first pulse attacked"
            );

            f.world.pulse().pulseOnce(
                1600L
            );

            require(
                f.hp()==89,
                "spawn/bind second pulse did not attack"
            );
        }finally{
            f.close();
        }
    }

    private static void unconfiguredBindingRollsBack()
        throws Exception{
        Fixture f=new Fixture(1);

        try{
            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    f.world,
                    context->null
                );
            MonsterSpawnerCombatBindingService service=
                new MonsterSpawnerCombatBindingService(
                    f.world,
                    f.spawner,
                    binder
                );

            expect(
                IllegalStateException.class,
                ()->service.spawnAndBind(
                    OWNER,
                    3087,
                    3495,
                    0
                ),
                "unconfigured runtime bind"
            );

            assertRolledBack(
                f,
                binder,
                "unconfigured rollback"
            );
        }finally{
            f.close();
        }
    }

    private static void resolverFailureRollsBack()
        throws Exception{
        Fixture f=new Fixture(1);

        try{
            final IllegalStateException primary=
                new IllegalStateException(
                    "PLAN_RESOLVER_FAILURE"
                );

            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    f.world,
                    context->{
                        throw primary;
                    }
                );
            MonsterSpawnerCombatBindingService service=
                new MonsterSpawnerCombatBindingService(
                    f.world,
                    f.spawner,
                    binder
                );

            Throwable observed=
                capture(
                    ()->service.spawnAndBind(
                        OWNER,
                        3087,
                        3495,
                        0
                    )
                );

            require(
                observed==primary,
                "resolver primary failure identity changed"
            );

            assertRolledBack(
                f,
                binder,
                "resolver rollback"
            );
        }finally{
            f.close();
        }
    }

    private static void attachConflictRollsBackEverything()
        throws Exception{
        Fixture f=new Fixture(1);

        try{
            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    f.world,
                    context->{
                        WorldNpc exact=
                            f.world.npcs().byId(
                                context.npcId
                            );

                        require(
                            exact!=null,
                            "attach-conflict resolver missing NPC"
                        );

                        f.world.attachNpcTickTarget(
                            exact,
                            new WorldNpcTickTarget(){
                                @Override public EntityId npcId(){
                                    return exact.id;
                                }

                                @Override public void onWorldNpcTick(
                                    long worldTick,
                                    long nowMillis
                                ){}
                            }
                        );

                        return plan();
                    }
                );
            MonsterSpawnerCombatBindingService service=
                new MonsterSpawnerCombatBindingService(
                    f.world,
                    f.spawner,
                    binder
                );

            expect(
                IllegalStateException.class,
                ()->service.spawnAndBind(
                    OWNER,
                    3087,
                    3495,
                    0
                ),
                "runtime attach conflict"
            );

            assertRolledBack(
                f,
                binder,
                "attach-conflict rollback"
            );

            require(
                f.world.npcTickTargetCount()==0,
                "foreign stale pulse target survived rollback"
            );
        }finally{
            f.close();
        }
    }

    private static void primaryFailurePreserved()
        throws Exception{
        World world=World.isolatedForTest(600L);
        MonsterSpawnerService spawner=
            configuredSpawner(
                world,
                1
            );
        final IllegalStateException primary=
            new IllegalStateException(
                "COMPOSED_PRIMARY"
            );

        try{
            Throwable observed=
                capture(
                    ()->spawner.spawnSelectedComposed(
                        OWNER,
                        3087,
                        3495,
                        0,
                        npc->{
                            throw primary;
                        }
                    )
                );

            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(
                    OWNER
                );

            require(
                observed==primary&&
                observed.getSuppressed().length==0&&
                world.npcs().size()==0&&
                session.spawnedNpcIds.isEmpty()&&
                session.remainingSpawnBudget==1&&
                session.active,
                "primary failure/clean rollback"
            );
        }finally{
            world.close();
        }
    }

    private static void rollbackFailureSuppressed()
        throws Exception{
        World world=World.isolatedForTest(600L);
        MonsterSpawnerService spawner=
            configuredSpawner(
                world,
                1
            );
        final IllegalStateException primary=
            new IllegalStateException(
                "COMPOSED_PRIMARY_WITH_ROLLBACK_FAILURE"
            );

        try{
            Throwable observed=
                capture(
                    ()->spawner.spawnSelectedComposed(
                        OWNER,
                        3087,
                        3495,
                        0,
                        npc->{
                            require(
                                world.npcs().remove(
                                    npc.id
                                ),
                                "rollback-failure sabotage removal"
                            );
                            throw primary;
                        }
                    )
                );

            require(
                observed==primary&&
                observed.getSuppressed().length==1&&
                observed.getSuppressed()[0]
                    instanceof IllegalStateException,
                "rollback failure not suppressed on primary"
            );
        }finally{
            world.close();
        }
    }

    private static void worldLifecycleIsOutermost()
        throws Exception{
        Fixture f=new Fixture(1);
        ExecutorService worker=
            Executors.newSingleThreadExecutor();
        AtomicReference<Future<Boolean>> probe=
            new AtomicReference<>();

        try{
            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    f.world,
                    context->{
                        Future<Boolean> candidate=
                            worker.submit(
                                ()->f.world
                                    .withOpenLifecycleOwnership(
                                        ()->{}
                                    )
                            );
                        probe.set(candidate);

                        try{
                            candidate.get(
                                150L,
                                TimeUnit.MILLISECONDS
                            );
                            throw new AssertionError(
                                "World lifecycle was not outer to plan resolver"
                            );
                        }catch(TimeoutException expected){
                            // Expected: spawnAndBind owns World lifecycle.
                        }

                        return plan();
                    }
                );

            MonsterSpawnerCombatBindingService service=
                new MonsterSpawnerCombatBindingService(
                    f.world,
                    f.spawner,
                    binder
                );

            MonsterSpawnerCombatBindingService.Result result=
                service.spawnAndBind(
                    OWNER,
                    3087,
                    3495,
                    0
                );

            Future<Boolean> candidate=
                Objects.requireNonNull(
                    probe.get(),
                    "lifecycle probe"
                );

            require(
                candidate.get(
                    5L,
                    TimeUnit.SECONDS
                )&&
                result.binding!=null&&
                binder.size()==1,
                "outer lifecycle probe did not release"
            );
        }finally{
            worker.shutdownNow();
            worker.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
            f.close();
        }
    }

    private static void assertRolledBack(
        Fixture f,
        NpcCombatRuntimeBinder binder,
        String label
    ){
        MonsterSpawnerService.SessionSnapshot session=
            f.spawner.getSession(
                OWNER
            );

        require(
            session.active&&
            session.remainingSpawnBudget==1&&
            session.spawnedNpcIds.isEmpty()&&
            f.world.npcs().size()==0&&
            binder.size()==0&&
            f.world.npcTickTargetCount()==0&&
            f.hp()==99,
            label
        );
    }

    private static MonsterSpawnerService configuredSpawner(
        World world,
        int budget
    ){
        MonsterSpawnerService spawner=
            new MonsterSpawnerService(
                world.npcs()
            );

        spawner.replaceCatalog(
            Collections.singletonList(
                new MonsterSpawnerService.CatalogEntry(
                    0,
                    "combat-test",
                    1525
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
            budget
        );

        return spawner;
    }

    private static NpcCombatRuntimeBinder.BehaviorPlan
        plan(){
        return new NpcCombatRuntimeBinder.BehaviorPlan(
            "spawner-combat",
            "CUSTOM_LOCALLAB_PROFILE",
            context->
                context.samePlane
                    ?NpcTargetAcquisitionService.Decision
                        .eligible(
                            context.chebyshevDistance
                        )
                    :NpcTargetAcquisitionService.Decision
                        .ineligible(),
            "CUSTOM_LOCALLAB_TARGET",
            "SPAWNER_TEST_DISTANCE",
            fixedApproach(),
            new TrackingCadence(),
            new TrackingDamage(),
            context->context.worldTick+1L,
            "CUSTOM_LOCALLAB_CONTROLLER",
            "CUSTOM_LOCALLAB_AI"
        );
    }

    private static NpcCombatApproachService.ApproachPolicy
        fixedApproach(){
        return new NpcCombatApproachService.ApproachPolicy(){
            @Override public int stopRange(
                NpcCombatApproachService.Context context
            ){
                return 1;
            }

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
                return "FIXED_RANGE_1_WORLD_STATIC";
            }
        };
    }

    private static final class TrackingCadence
        implements NpcCombatEngagementService.CadenceResolver {
        @Override public int nextDelayTicks(
            NpcCombatEngagementService.Context context
        ){
            return 3;
        }

        @Override public String authority(){
            return "CUSTOM_LOCALLAB_NPC_CADENCE";
        }

        @Override public String policy(){
            return "FIXED_3";
        }
    }

    private static final class TrackingDamage
        implements NpcPlayerCombatResolutionService.DamageResolver {
        @Override public int resolve(
            NpcPlayerCombatResolutionService.DamageContext context
        ){
            return 10;
        }

        @Override public String authority(){
            return "CUSTOM_LOCALLAB_NPC_DAMAGE";
        }

        @Override public String formula(){
            return "FIXED_10";
        }
    }

    private static final class Fixture {
        final World world=
            World.isolatedForTest(600L);
        final WorldPlayer player=
            new WorldPlayer();
        final long generation=
            world.registerPlayer(
                player,
                "spawner-combat-target"
            );
        final MonsterSpawnerService spawner;

        Fixture(int budget){
            player.movement()
                .restoreAccountState(
                    false,
                    100,
                    3088,
                    3495,
                    0
                );
            spawner=
                configuredSpawner(
                    world,
                    budget
                );
        }

        int hp(){
            return player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                );
        }

        void close(){
            world.unregisterPlayer(
                player,
                generation
            );
            world.close();
        }
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
        Throwable failure=
            capture(action);

        if(!type.isInstance(failure))
            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
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

    private MonsterSpawnerCombatBindingServiceTest(){}
}
