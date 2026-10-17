package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class NpcCombatAiServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_NPC_AI";

    public static void main(String[] args)throws Exception{
        idleNoTarget();
        acquisitionEngagesOnly();
        nextTickDelegatesWithoutReacquire();
        staleTargetBetweenAcquireAndBegin();
        staleAttackerFailsClosed();
        resolverFailureAtomic();
        resolverClockDriftFailsClosed();
        authorityAndBoundary();

        System.out.println(
            "NPC_COMBAT_AI_PASS "+
            "idleAcquisition=true "+
            "engageOnlyFirstTick=true "+
            "exactTargetGeneration=true "+
            "callerInitialAttackTick=true "+
            "controllerDelegated=true "+
            "noReacquireWhileEngaged=true "+
            "staleTargetFailClosed=true "+
            "staleAttackerFailClosed=true "+
            "resolverFailureAtomic=true "+
            "sharedClockBound=true "+
            "aggroPolicyOwned=false "+
            "initialTimingOwned=false "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void idleNoTarget()
        throws Exception{
        Fixture f=new Fixture(false);

        try{
            NpcCombatAiService ai=
                f.ai(
                    context->
                        NpcTargetAcquisitionService.Decision
                            .ineligible(),
                    context->context.worldTick
                );

            NpcCombatAiService.Result result=
                ai.tick(
                    f.npc,
                    0L
                );

            require(
                result.status==
                    NpcCombatAiService.Status.NO_TARGET&&
                result.acquisition!=null&&
                result.acquisition.status==
                    NpcTargetAcquisitionService.Status.NONE&&
                result.engagement==null&&
                result.controller==null&&
                f.controller.size()==0&&
                f.damage.calls==0&&
                f.cadence.calls==0,
                "idle no-target path"
            );
        }finally{
            f.close();
        }
    }

    private static void acquisitionEngagesOnly()
        throws Exception{
        Fixture f=new Fixture(true);

        try{
            final int[] resolverCalls={0};

            NpcCombatAiService ai=
                f.ai(
                    context->
                        NpcTargetAcquisitionService.Decision
                            .eligible(3L),
                    context->{
                        resolverCalls[0]++;

                        require(
                            context.attackerId.equals(
                                f.npc.id
                            )&&
                            context.targetId.equals(
                                f.player.player.id()
                            )&&
                            context.targetGeneration==
                                f.player.generation&&
                            context.targetPriority==3L&&
                            context.worldTick==0L,
                            "initial timing semantic context"
                        );

                        return 1L;
                    }
                );

            int hpBefore=f.hp();

            NpcCombatAiService.Result result=
                ai.tick(
                    f.npc,
                    0L
                );

            NpcCombatEngagementService.Snapshot active=
                f.controller.get(
                    f.npc.id
                );

            require(
                result.status==
                    NpcCombatAiService.Status.ENGAGED&&
                result.acquisition!=null&&
                result.acquisition.target.player==
                    f.player.player&&
                result.engagement!=null&&
                result.controller==null&&
                active!=null&&
                active.targetGeneration==
                    f.player.generation&&
                active.nextAttackTick==1L&&
                active.revision==0L&&
                f.hp()==hpBefore&&
                f.damage.calls==0&&
                f.cadence.calls==0&&
                resolverCalls[0]==1&&
                f.npc.x()==3087&&
                f.npc.y()==3495,
                "acquisition tick moved/attacked or lost timing"
            );
        }finally{
            f.close();
        }
    }

    private static void nextTickDelegatesWithoutReacquire()
        throws Exception{
        Fixture f=new Fixture(true);

        try{
            final int[] acquisitionCalls={0};
            final int[] timingCalls={0};

            NpcCombatAiService ai=
                f.ai(
                    context->{
                        acquisitionCalls[0]++;
                        return NpcTargetAcquisitionService
                            .Decision.eligible(0L);
                    },
                    context->{
                        timingCalls[0]++;
                        return context.worldTick+1L;
                    }
                );

            NpcCombatAiService.Result first=
                ai.tick(
                    f.npc,
                    0L
                );

            require(
                first.status==
                    NpcCombatAiService.Status.ENGAGED&&
                acquisitionCalls[0]==1&&
                timingCalls[0]==1&&
                f.damage.calls==0,
                "first AI tick"
            );

            long tick=
                f.world.clock().advance();

            require(
                tick==1L,
                "fixture world tick"
            );

            NpcCombatAiService.Result second=
                ai.tick(
                    f.npc,
                    tick
                );

            require(
                second.status==
                    NpcCombatAiService.Status.CONTROLLER&&
                second.acquisition==null&&
                second.controller!=null&&
                second.controller.status==
                    NpcCombatControllerService.Status.ATTACKED&&
                acquisitionCalls[0]==1&&
                timingCalls[0]==1&&
                f.damage.calls==1&&
                f.cadence.calls==1&&
                f.hp()==89&&
                f.controller.get(
                    f.npc.id
                ).revision==1L,
                "engaged AI reacquired or failed controller delegation"
            );
        }finally{
            f.close();
        }
    }

    private static void staleTargetBetweenAcquireAndBegin()
        throws Exception{
        Fixture f=new Fixture(true);

        try{
            NpcCombatAiService ai=
                f.ai(
                    context->
                        NpcTargetAcquisitionService.Decision
                            .eligible(0L),
                    context->{
                        require(
                            f.world.unregisterPlayer(
                                f.player.player,
                                f.player.generation
                            ),
                            "resolver target removal"
                        );
                        return context.worldTick;
                    }
                );

            NpcCombatAiService.Result result=
                ai.tick(
                    f.npc,
                    0L
                );

            require(
                result.status==
                    NpcCombatAiService.Status.STALE_TARGET&&
                result.acquisition!=null&&
                result.engagement==null&&
                f.controller.size()==0&&
                f.damage.calls==0&&
                f.cadence.calls==0,
                "stale acquired target created engagement"
            );
        }finally{
            f.close();
        }
    }

    private static void staleAttackerFailsClosed()
        throws Exception{
        Fixture f=new Fixture(true);

        try{
            require(
                f.world.npcs().remove(
                    f.npc.id
                ),
                "stale attacker removal"
            );

            NpcCombatAiService ai=
                f.ai(
                    context->
                        NpcTargetAcquisitionService.Decision
                            .eligible(0L),
                    context->context.worldTick
                );

            NpcCombatAiService.Result result=
                ai.tick(
                    f.npc,
                    0L
                );

            require(
                result.status==
                    NpcCombatAiService.Status.STALE_ATTACKER&&
                result.engagement==null&&
                f.controller.size()==0&&
                f.damage.calls==0,
                "stale attacker engaged"
            );
        }finally{
            f.close();
        }
    }

    private static void resolverFailureAtomic()
        throws Exception{
        Fixture f=new Fixture(true);

        try{
            NpcCombatAiService ai=
                f.ai(
                    context->
                        NpcTargetAcquisitionService.Decision
                            .eligible(0L),
                    context->{
                        throw new IllegalStateException(
                            "INITIAL_TICK_FAILURE"
                        );
                    }
                );

            expect(
                IllegalStateException.class,
                ()->ai.tick(
                    f.npc,
                    0L
                ),
                "initial tick resolver failure"
            );

            require(
                f.controller.size()==0&&
                f.damage.calls==0&&
                f.cadence.calls==0&&
                f.npc.x()==3087&&
                f.npc.y()==3495&&
                f.hp()==99,
                "resolver failure mutated combat"
            );
        }finally{
            f.close();
        }
    }

    private static void resolverClockDriftFailsClosed()
        throws Exception{
        Fixture f=new Fixture(true);

        try{
            NpcCombatAiService ai=
                f.ai(
                    context->
                        NpcTargetAcquisitionService.Decision
                            .eligible(0L),
                    context->{
                        f.world.clock().advance();
                        return context.worldTick+1L;
                    }
                );

            expect(
                IllegalStateException.class,
                ()->ai.tick(
                    f.npc,
                    0L
                ),
                "resolver clock drift"
            );

            require(
                f.world.clock().tick()==1L&&
                f.controller.size()==0&&
                f.damage.calls==0&&
                f.cadence.calls==0&&
                f.hp()==99,
                "clock drift created engagement/attack"
            );
        }finally{
            f.close();
        }
    }

    private static void authorityAndBoundary()
        throws Exception{
        Fixture f=new Fixture(false);

        try{
            NpcTargetAcquisitionService acquisition=
                f.acquisition(
                    context->
                        NpcTargetAcquisitionService.Decision
                            .ineligible()
                );

            expect(
                IllegalArgumentException.class,
                ()->new NpcCombatAiService(
                    f.world,
                    acquisition,
                    f.controller,
                    context->context.worldTick,
                    "EXACT_CURRENT_CLIENT",
                    NpcCombatAiService
                        .ACQUIRE_THEN_DELEGATE
                ),
                "client AI authority"
            );

            expect(
                IllegalArgumentException.class,
                ()->new NpcCombatAiService(
                    f.world,
                    acquisition,
                    f.controller,
                    context->context.worldTick,
                    "UNKNOWN_SERVER_AUTHORITY",
                    NpcCombatAiService
                        .ACQUIRE_THEN_DELEGATE
                ),
                "unknown AI authority"
            );

            expect(
                IllegalArgumentException.class,
                ()->new NpcCombatAiService(
                    f.world,
                    acquisition,
                    f.controller,
                    context->context.worldTick,
                    AUTHORITY,
                    "INVENTED_AGGRO_POLICY"
                ),
                "unsupported AI orchestration policy"
            );

            NpcCombatAiService ai=
                new NpcCombatAiService(
                    f.world,
                    acquisition,
                    f.controller,
                    context->context.worldTick,
                    AUTHORITY,
                    NpcCombatAiService
                        .ACQUIRE_THEN_DELEGATE
                );

            require(
                AUTHORITY.equals(
                    ai.authority()
                )&&
                NpcCombatAiService
                    .ACQUIRE_THEN_DELEGATE
                    .equals(
                        ai.policy()
                    ),
                "AI provenance"
            );

            for(Class<?> type:new Class<?>[]{
                    NpcCombatAiService.class,
                    NpcCombatAiService.Context.class,
                    NpcCombatAiService.Result.class
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
                            "xp",
                            "nearest",
                            "radius",
                            "leash",
                            "retaliate"
                        })
                        require(
                            !name.contains(
                                forbidden
                            ),
                            "unowned AI identity/policy leaked "+
                            type.getSimpleName()+
                            "."+
                            field.getName()
                        );
                }
            }

            for(Method method:
                    NpcCombatAiService.class
                        .getDeclaredMethods()){
                String name=
                    method.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                for(String forbidden:new String[]{
                        "nearest",
                        "radius",
                        "leash",
                        "retaliate",
                        "packet",
                        "publish"
                    })
                    require(
                        !name.contains(
                            forbidden
                        ),
                        "hidden AI policy leaked "+
                        method.getName()
                    );
            }
        }finally{
            f.close();
        }
    }

    private static final class PlayerRef {
        final WorldPlayer player;
        final long generation;

        PlayerRef(
            WorldPlayer player,
            long generation
        ){
            this.player=player;
            this.generation=generation;
        }
    }

    private static final class TrackingCadence
        implements NpcCombatEngagementService.CadenceResolver {
        int calls;

        @Override public int nextDelayTicks(
            NpcCombatEngagementService.Context context
        ){
            calls++;
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
        int calls;

        @Override public int resolve(
            NpcPlayerCombatResolutionService.DamageContext context
        ){
            calls++;
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
        final WorldNpc npc=
            world.npcs().spawn(
                1506,
                3087,
                3495,
                0
            );
        final TrackingCadence cadence=
            new TrackingCadence();
        final TrackingDamage damage=
            new TrackingDamage();
        final NpcCombatControllerService controller=
            new NpcCombatControllerService(
                world,
                fixedApproach(),
                cadence,
                new NpcPlayerCombatResolutionService(
                    damage
                ),
                "CUSTOM_LOCALLAB_NPC_CONTROLLER",
                NpcCombatControllerService
                    .APPROACH_BEFORE_ENGAGEMENT_TICK
            );
        final PlayerRef player;

        Fixture(boolean withPlayer){
            if(withPlayer){
                WorldPlayer target=
                    new WorldPlayer();
                long generation=
                    world.registerPlayer(
                        target,
                        "ai-target"
                    );

                target.movement()
                    .restoreAccountState(
                        false,
                        100,
                        3088,
                        3495,
                        0
                    );

                player=
                    new PlayerRef(
                        target,
                        generation
                    );
            }else{
                player=null;
            }
        }

        NpcTargetAcquisitionService acquisition(
            NpcTargetAcquisitionService.AcquisitionPolicy policy
        ){
            return new NpcTargetAcquisitionService(
                world,
                policy,
                "CUSTOM_LOCALLAB_NPC_TARGETING",
                "TEST_TARGET_POLICY"
            );
        }

        NpcCombatAiService ai(
            NpcTargetAcquisitionService.AcquisitionPolicy policy,
            NpcCombatAiService.InitialAttackTickResolver resolver
        ){
            return new NpcCombatAiService(
                world,
                acquisition(policy),
                controller,
                resolver,
                AUTHORITY,
                NpcCombatAiService
                    .ACQUIRE_THEN_DELEGATE
            );
        }

        int hp(){
            return player.player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                );
        }

        void close(){
            for(WorldPlayer current:
                    world.players().snapshot())
                world.unregisterPlayer(
                    current
                );

            world.close();
        }
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

    private NpcCombatAiServiceTest(){}
}
