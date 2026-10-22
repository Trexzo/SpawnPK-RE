package spk.local;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.*;

public final class NpcCombatRuntimeBinderTest {
    public static void main(String[] args)throws Exception{
        resolverRunsWithoutNpcRegistryLock();
        unconfiguredNoMutation();
        validBindPulsesCombat();
        duplicateBindRejected();
        resolverFailureAtomic();
        staleAfterResolverFailsClosed();
        attachFailureNoPublication();
        attachWithoutBinderLifecycleInversion();
        exactUnbindNoDespawn();
        authorityAndBoundary();

        System.out.println(
            "NPC_COMBAT_RUNTIME_BINDER_PASS "+
            "exactNpc=true "+
            "resolverUnlocked=true "+
            "unconfiguredNoMutation=true "+
            "atomicBindPublication=true "+
            "pulseTargetAttached=true "+
            "callerPolicies=true "+
            "engageThenAttack=true "+
            "duplicateBindRejected=true "+
            "resolverFailureAtomic=true "+
            "staleAfterResolverFailClosed=true "+
            "attachFailureAtomic=true "+
            "attachWithoutBinderLifecycleInversion=true "+
            "exactUnbind=true "+
            "noDespawn=true "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void resolverRunsWithoutNpcRegistryLock()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldNpc npc=
            world.npcs().spawn(
                1520,3087,3495,0
            );
        ExecutorService worker=
            Executors.newSingleThreadExecutor();

        try{
            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    world,
                    context->{
                        Future<Boolean> ownership=
                            worker.submit(
                                ()->world.npcs()
                                    .withCurrentMutationOwnershipIfCurrent(
                                        npc,
                                        ()->{}
                                    )
                            );

                        require(
                            ownership.get(
                                2L,
                                TimeUnit.SECONDS
                            ),
                            "resolver worker lost NPC ownership"
                        );

                        return null;
                    }
                );

            NpcCombatRuntimeBinder.BindResult result=
                binder.bind(npc);

            require(
                result.status==
                    NpcCombatRuntimeBinder.BindStatus.UNCONFIGURED&&
                binder.size()==0&&
                world.npcTickTargetCount()==0,
                "resolver-unlocked fixture mutated runtime"
            );
        }finally{
            worker.shutdownNow();
            worker.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
            world.close();
        }
    }

    private static void unconfiguredNoMutation()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldNpc npc=
            world.npcs().spawn(
                1521,3087,3495,0
            );

        try{
            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    world,
                    context->null
                );

            NpcCombatRuntimeBinder.BindResult result=
                binder.bind(npc);

            require(
                result.status==
                    NpcCombatRuntimeBinder.BindStatus.UNCONFIGURED&&
                result.binding==null&&
                binder.size()==0&&
                world.npcTickTargetCount()==0&&
                world.npcs().byId(
                    npc.id
                )==npc,
                "unconfigured bind mutated world"
            );
        }finally{
            world.close();
        }
    }

    private static void validBindPulsesCombat()
        throws Exception{
        Fixture f=new Fixture();

        try{
            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    f.world,
                    context->{
                        require(
                            context.npcId.equals(
                                f.npc.id
                            )&&
                            context.definitionId==
                                f.npc.definitionId&&
                            context.tile.equals(
                                f.npc.tile()
                            ),
                            "binder semantic context"
                        );
                        return plan();
                    }
                );

            NpcCombatRuntimeBinder.BindResult result=
                binder.bind(
                    f.npc
                );

            require(
                result.status==
                    NpcCombatRuntimeBinder.BindStatus.BOUND&&
                result.binding!=null&&
                result.binding.bound&&
                result.binding.npcId.equals(
                    f.npc.id
                )&&
                result.binding.definitionId==
                    f.npc.definitionId&&
                "test-melee".equals(
                    result.binding.planKey
                )&&
                "CUSTOM_LOCALLAB_PROFILE".equals(
                    result.binding.sourceAuthority
                )&&
                binder.size()==1&&
                worldTargetCount(f)==1&&
                f.hp()==99,
                "valid runtime bind publication"
            );

            f.world.pulse().pulseOnce(
                1000L
            );

            require(
                f.world.clock().tick()==1L&&
                f.hp()==99,
                "first bound pulse attacked"
            );

            f.world.pulse().pulseOnce(
                1600L
            );

            require(
                f.world.clock().tick()==2L&&
                f.hp()==89,
                "second bound pulse did not attack"
            );
        }finally{
            f.close();
        }
    }

    private static void duplicateBindRejected()
        throws Exception{
        Fixture f=new Fixture();

        try{
            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    f.world,
                    context->plan()
                );

            require(
                binder.bind(
                    f.npc
                ).status==
                    NpcCombatRuntimeBinder.BindStatus.BOUND,
                "initial duplicate fixture bind"
            );

            expect(
                IllegalStateException.class,
                ()->binder.bind(
                    f.npc
                ),
                "duplicate binder registration"
            );

            require(
                binder.size()==1&&
                f.world.npcTickTargetCount()==1,
                "duplicate bind changed publication"
            );
        }finally{
            f.close();
        }
    }

    private static void resolverFailureAtomic()
        throws Exception{
        Fixture f=new Fixture();

        try{
            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    f.world,
                    context->{
                        throw new IllegalStateException(
                            "PLAN_FAILURE"
                        );
                    }
                );

            expect(
                IllegalStateException.class,
                ()->binder.bind(
                    f.npc
                ),
                "plan resolver failure"
            );

            require(
                binder.size()==0&&
                f.world.npcTickTargetCount()==0&&
                f.world.npcs().byId(
                    f.npc.id
                )==f.npc&&
                f.hp()==99,
                "resolver failure published runtime"
            );
        }finally{
            f.close();
        }
    }

    private static void staleAfterResolverFailsClosed()
        throws Exception{
        Fixture f=new Fixture();

        try{
            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    f.world,
                    context->{
                        require(
                            f.world.npcs().remove(
                                f.npc.id
                            ),
                            "resolver NPC removal"
                        );
                        return plan();
                    }
                );

            NpcCombatRuntimeBinder.BindResult result=
                binder.bind(
                    f.npc
                );

            require(
                result.status==
                    NpcCombatRuntimeBinder.BindStatus.STALE_ATTACKER&&
                result.binding==null&&
                binder.size()==0&&
                f.world.npcTickTargetCount()==0,
                "stale resolver result bound runtime"
            );
        }finally{
            f.close();
        }
    }

    private static void attachFailureNoPublication()
        throws Exception{
        Fixture f=new Fixture();

        try{
            WorldNpcTickTarget foreign=
                new WorldNpcTickTarget(){
                    @Override public EntityId npcId(){
                        return f.npc.id;
                    }

                    @Override public void onWorldNpcTick(
                        long worldTick,
                        long nowMillis
                    ){}
                };

            f.world.attachNpcTickTarget(
                f.npc,
                foreign
            );

            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    f.world,
                    context->plan()
                );

            expect(
                IllegalStateException.class,
                ()->binder.bind(
                    f.npc
                ),
                "pulse attach conflict"
            );

            require(
                binder.size()==0&&
                f.world.npcTickTargetCount()==1&&
                f.world.npcs().byId(
                    f.npc.id
                )==f.npc,
                "attach failure published binder state"
            );
        }finally{
            f.close();
        }
    }

    private static void attachWithoutBinderLifecycleInversion()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldNpc npcA=
            world.npcs().spawn(
                1523,
                3087,
                3495,
                0
            );
        WorldNpc npcB=
            world.npcs().spawn(
                1524,
                3090,
                3495,
                0
            );
        NpcCombatRuntimeBinder binder=
            new NpcCombatRuntimeBinder(
                world,
                context->plan()
            );
        ExecutorService workers=
            Executors.newFixedThreadPool(2);
        CountDownLatch lifecycleHeld=
            new CountDownLatch(1);
        CountDownLatch allowOwnerBind=
            new CountDownLatch(1);
        AtomicReference<Thread> blockedBindThread=
            new AtomicReference<>();
        AtomicReference<NpcCombatRuntimeBinder.BindResult>
            ownerResult=
                new AtomicReference<>();

        try{
            Future<Boolean> lifecycleOwner=
                workers.submit(
                    ()->world.withOpenLifecycleOwnership(
                        ()->{
                            lifecycleHeld.countDown();

                            if(!allowOwnerBind.await(
                                    5L,
                                    TimeUnit.SECONDS))
                                throw new AssertionError(
                                    "owner bind release timeout"
                                );

                            ownerResult.set(
                                binder.bind(
                                    npcA
                                )
                            );
                        }
                    )
                );

            require(
                lifecycleHeld.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "lifecycle owner did not enter"
            );

            Future<NpcCombatRuntimeBinder.BindResult>
                blockedBind=
                    workers.submit(
                        ()->{
                            blockedBindThread.set(
                                Thread.currentThread()
                            );
                            return binder.bind(
                                npcB
                            );
                        }
                    );

            awaitBlocked(
                blockedBindThread,
                "second bind did not block on World lifecycle"
            );

            allowOwnerBind.countDown();

            require(
                lifecycleOwner.get(
                    3L,
                    TimeUnit.SECONDS
                ),
                "lifecycle owner failed"
            );

            NpcCombatRuntimeBinder.BindResult a=
                ownerResult.get();

            require(
                a!=null&&
                a.status==
                    NpcCombatRuntimeBinder.BindStatus.BOUND,
                "lifecycle owner could not enter binder while peer awaited lifecycle"
            );

            NpcCombatRuntimeBinder.BindResult b=
                blockedBind.get(
                    5L,
                    TimeUnit.SECONDS
                );

            require(
                b.status==
                    NpcCombatRuntimeBinder.BindStatus.BOUND&&
                binder.size()==2&&
                world.npcTickTargetCount()==2,
                "both non-inverting binds did not publish exactly once"
            );
        }finally{
            allowOwnerBind.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
            world.close();
        }
    }

    private static void awaitBlocked(
        AtomicReference<Thread> thread,
        String label
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.SECONDS.toNanos(5L);

        while(System.nanoTime()<deadline){
            Thread current=
                thread.get();

            if(current!=null&&
               current.getState()==
                    Thread.State.BLOCKED)
                return;

            Thread.sleep(5L);
        }

        throw new AssertionError(label);
    }

    private static void exactUnbindNoDespawn()
        throws Exception{
        Fixture f=new Fixture();

        try{
            NpcCombatRuntimeBinder binder=
                new NpcCombatRuntimeBinder(
                    f.world,
                    context->plan()
                );

            require(
                binder.bind(
                    f.npc
                ).status==
                    NpcCombatRuntimeBinder.BindStatus.BOUND&&
                binder.size()==1&&
                f.world.npcTickTargetCount()==1,
                "unbind fixture bind"
            );

            require(
                binder.unbind(
                    f.npc.id
                )&&
                binder.size()==0&&
                binder.get(
                    f.npc.id
                )==null&&
                f.world.npcTickTargetCount()==0&&
                f.world.npcs().byId(
                    f.npc.id
                )==f.npc&&
                !binder.unbind(
                    f.npc.id
                ),
                "exact unbind/despawn boundary"
            );

            f.world.pulse().pulseOnce(
                1000L
            );

            require(
                f.hp()==99,
                "unbound NPC still executed AI"
            );
        }finally{
            f.close();
        }
    }

    private static void authorityAndBoundary()
        throws Exception{
        expect(
            IllegalArgumentException.class,
            ()->new NpcCombatRuntimeBinder.BehaviorPlan(
                "bad-source",
                "EXACT_CURRENT_CLIENT",
                context->
                    NpcTargetAcquisitionService.Decision
                        .ineligible(),
                "CUSTOM_LOCALLAB_TARGET",
                "TARGET",
                fixedApproach(),
                new TrackingCadence(),
                new TrackingDamage(),
                context->context.worldTick,
                "CUSTOM_LOCALLAB_CONTROLLER",
                "CUSTOM_LOCALLAB_AI"
            ),
            "client plan source authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new NpcCombatRuntimeBinder.BehaviorPlan(
                "bad-target",
                "CUSTOM_LOCALLAB_PROFILE",
                context->
                    NpcTargetAcquisitionService.Decision
                        .ineligible(),
                "UNKNOWN_SERVER_AUTHORITY",
                "TARGET",
                fixedApproach(),
                new TrackingCadence(),
                new TrackingDamage(),
                context->context.worldTick,
                "CUSTOM_LOCALLAB_CONTROLLER",
                "CUSTOM_LOCALLAB_AI"
            ),
            "unknown target authority"
        );

        for(Class<?> type:new Class<?>[]{
                NpcCombatRuntimeBinder.class,
                NpcCombatRuntimeBinder.Context.class,
                NpcCombatRuntimeBinder.BindingSnapshot.class
            }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                for(String forbidden:new String[]{
                        "packet",
                        "opcode",
                        "widget",
                        "sceneindex",
                        "socket",
                        "isaac",
                        "hitsplat",
                        "projectile",
                        "reward",
                        "drop",
                        "xp"
                    })
                    require(
                        !name.contains(
                            forbidden
                        ),
                        "presentation/reward identity leaked "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static NpcCombatRuntimeBinder.BehaviorPlan
        plan(){
        return new NpcCombatRuntimeBinder.BehaviorPlan(
            "test-melee",
            "CUSTOM_LOCALLAB_PROFILE",
            context->
                NpcTargetAcquisitionService.Decision
                    .eligible(
                        context.samePlane
                            ?context.chebyshevDistance
                            :1000000L
                    ),
            "CUSTOM_LOCALLAB_TARGET",
            "TEST_DISTANCE_PRIORITY",
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
                "binder-target"
            );
        final WorldNpc npc=
            world.npcs().spawn(
                1522,
                3087,
                3495,
                0
            );

        Fixture(){
            player.movement()
                .restoreAccountState(
                    false,
                    100,
                    3088,
                    3495,
                    0
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

    private static int worldTargetCount(
        Fixture f
    ){
        return f.world.npcTickTargetCount();
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

    private NpcCombatRuntimeBinderTest(){}
}
