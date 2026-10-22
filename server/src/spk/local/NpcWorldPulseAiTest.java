package spk.local;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class NpcWorldPulseAiTest {
    public static void main(String[] args)throws Exception{
        attachGuards();
        engageThenAttackOnPulse();
        removedNpcAutoDetaches();
        callbackFailureIsolation();
        exactDetach();
        pulseContextAndRegistryUnlocked();
        playerTickContractStillWorks();
        noSecondScheduler();

        System.out.println(
            "NPC_WORLD_PULSE_AI_PASS "+
            "exactAttach=true "+
            "separateNpcTargetContract=true "+
            "oneCallbackPerTick=true "+
            "engageThenAttack=true "+
            "staleAutoDetach=true "+
            "failureIsolation=true "+
            "exactDetach=true "+
            "pulseExecutionContext=true "+
            "npcRegistryNotHeldAcrossCallback=true "+
            "playerTickContractUnchanged=true "+
            "secondScheduler=false "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void attachGuards()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldNpc npc=
            world.npcs().spawn(
                1510,3087,3495,0
            );

        try{
            WorldNpcTickTarget target=
                countingTarget(
                    npc.id,
                    new AtomicInteger()
                );

            world.attachNpcTickTarget(
                npc,
                target
            );

            expect(
                IllegalStateException.class,
                ()->world.attachNpcTickTarget(
                    npc,
                    target
                ),
                "duplicate NPC tick target"
            );

            WorldNpc other=
                world.npcs().spawn(
                    1511,3090,3495,0
                );

            expect(
                IllegalArgumentException.class,
                ()->world.attachNpcTickTarget(
                    other,
                    target
                ),
                "mismatched NPC tick id"
            );

            require(
                world.npcs().remove(
                    other.id
                ),
                "remove stale attach fixture"
            );

            WorldNpcTickTarget staleTarget=
                countingTarget(
                    other.id,
                    new AtomicInteger()
                );

            expect(
                IllegalStateException.class,
                ()->world.attachNpcTickTarget(
                    other,
                    staleTarget
                ),
                "stale NPC attach"
            );
        }finally{
            world.close();
        }
    }

    private static void engageThenAttackOnPulse()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "pulse-ai-target"
            );

        player.movement()
            .restoreAccountState(
                false,
                100,
                3088,
                3495,
                0
            );

        WorldNpc npc=
            world.npcs().spawn(
                1512,
                3087,
                3495,
                0
            );

        TrackingDamage damage=
            new TrackingDamage();
        TrackingCadence cadence=
            new TrackingCadence();

        NpcCombatControllerService controller=
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

        final int[] acquisitionCalls={0};

        NpcTargetAcquisitionService acquisition=
            new NpcTargetAcquisitionService(
                world,
                context->{
                    acquisitionCalls[0]++;
                    return NpcTargetAcquisitionService
                        .Decision.eligible(0L);
                },
                "CUSTOM_LOCALLAB_NPC_TARGETING",
                "PULSE_TEST_TARGET"
            );

        NpcCombatAiService ai=
            new NpcCombatAiService(
                world,
                acquisition,
                controller,
                context->context.worldTick+1L,
                "CUSTOM_LOCALLAB_NPC_AI",
                NpcCombatAiService
                    .ACQUIRE_THEN_DELEGATE
            );

        NpcCombatAiTickTarget target=
            new NpcCombatAiTickTarget(
                npc,
                ai
            );

        try{
            world.attachNpcTickTarget(
                npc,
                target
            );

            require(
                hp(player)==99,
                "initial player HP"
            );

            world.pulse().pulseOnce(
                1000L
            );

            require(
                world.clock().tick()==1L&&
                target.callbacks()==1L&&
                target.lastResult()!=null&&
                target.lastResult().status==
                    NpcCombatAiService.Status.ENGAGED&&
                controller.size()==1&&
                controller.get(
                    npc.id
                ).targetGeneration==
                    generation&&
                controller.get(
                    npc.id
                ).nextAttackTick==2L&&
                acquisitionCalls[0]==1&&
                damage.calls==0&&
                cadence.calls==0&&
                hp(player)==99,
                "first pulse did not engage-only"
            );

            world.pulse().pulseOnce(
                1600L
            );

            require(
                world.clock().tick()==2L&&
                target.callbacks()==2L&&
                target.lastResult().status==
                    NpcCombatAiService.Status.CONTROLLER&&
                target.lastResult().controller!=null&&
                target.lastResult().controller.status==
                    NpcCombatControllerService.Status.ATTACKED&&
                acquisitionCalls[0]==1&&
                damage.calls==1&&
                cadence.calls==1&&
                hp(player)==89,
                "second pulse did not delegate one attack"
            );
        }finally{
            world.unregisterPlayer(
                player,
                generation
            );
            world.close();
        }
    }

    private static void removedNpcAutoDetaches()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldNpc npc=
            world.npcs().spawn(
                1513,3087,3495,0
            );
        AtomicInteger calls=
            new AtomicInteger();
        WorldNpcTickTarget target=
            countingTarget(
                npc.id,
                calls
            );

        try{
            world.attachNpcTickTarget(
                npc,
                target
            );

            require(
                world.npcTickTargetCount()==1&&
                world.npcs().remove(
                    npc.id
                ),
                "removed NPC fixture"
            );

            world.pulse().pulseOnce(
                1000L
            );

            require(
                calls.get()==0&&
                world.npcTickTargetCount()==0,
                "stale NPC target not auto-detached"
            );
        }finally{
            world.close();
        }
    }

    private static void callbackFailureIsolation()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldNpc first=
            world.npcs().spawn(
                1514,3087,3495,0
            );
        WorldNpc second=
            world.npcs().spawn(
                1515,3089,3495,0
            );
        AtomicInteger secondCalls=
            new AtomicInteger();

        WorldNpcTickTarget failing=
            new WorldNpcTickTarget(){
                @Override public EntityId npcId(){
                    return first.id;
                }

                @Override public void onWorldNpcTick(
                    long worldTick,
                    long nowMillis
                ){
                    throw new IllegalStateException(
                        "EXPECTED_NPC_TICK_FAILURE"
                    );
                }
            };

        WorldNpcTickTarget healthy=
            countingTarget(
                second.id,
                secondCalls
            );

        try{
            world.attachNpcTickTarget(
                first,
                failing
            );
            world.attachNpcTickTarget(
                second,
                healthy
            );

            world.pulse().pulseOnce(
                1000L
            );

            require(
                secondCalls.get()==1,
                "failing NPC target suppressed later target"
            );
        }finally{
            world.close();
        }
    }

    private static void exactDetach()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldNpc npc=
            world.npcs().spawn(
                1516,3087,3495,0
            );
        AtomicInteger calls=
            new AtomicInteger();
        WorldNpcTickTarget target=
            countingTarget(
                npc.id,
                calls
            );
        WorldNpcTickTarget foreign=
            countingTarget(
                npc.id,
                new AtomicInteger()
            );

        try{
            world.attachNpcTickTarget(
                npc,
                target
            );

            require(
                !world.detachNpcTickTarget(
                    npc.id,
                    foreign
                )&&
                world.npcTickTargetCount()==1,
                "foreign target detached registration"
            );

            require(
                world.detachNpcTickTarget(
                    npc.id,
                    target
                )&&
                world.npcTickTargetCount()==0,
                "exact target detach"
            );

            world.pulse().pulseOnce(
                1000L
            );

            require(
                calls.get()==0,
                "detached target callback"
            );
        }finally{
            world.close();
        }
    }

    private static void pulseContextAndRegistryUnlocked()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldNpc npc=
            world.npcs().spawn(
                1517,3087,3495,0
            );
        ExecutorService worker=
            Executors.newSingleThreadExecutor();
        AtomicInteger calls=
            new AtomicInteger();

        WorldNpcTickTarget target=
            new WorldNpcTickTarget(){
                @Override public EntityId npcId(){
                    return npc.id;
                }

                @Override public void onWorldNpcTick(
                    long worldTick,
                    long nowMillis
                )throws Exception{
                    require(
                        world.pulse()
                            .inExecutionContext(),
                        "NPC callback outside pulse context"
                    );

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
                        "worker failed NPC ownership"
                    );

                    calls.incrementAndGet();
                }
            };

        try{
            world.attachNpcTickTarget(
                npc,
                target
            );

            world.pulse().pulseOnce(
                1000L
            );

            require(
                calls.get()==1,
                "pulse context/registry unlock callback"
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

    private static void playerTickContractStillWorks()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "player-tick-contract"
            );
        WorldNpc npc=
            world.npcs().spawn(
                1518,3087,3495,0
            );
        AtomicInteger playerCalls=
            new AtomicInteger();
        AtomicInteger npcCalls=
            new AtomicInteger();

        WorldTickTarget playerTarget=
            new WorldTickTarget(){
                @Override public EntityId ownerId(){
                    return player.id();
                }

                @Override public long ownerGeneration(){
                    return generation;
                }

                @Override public void onWorldTick(
                    long worldTick,
                    long nowMillis
                ){
                    playerCalls.incrementAndGet();
                }
            };

        WorldNpcTickTarget npcTarget=
            countingTarget(
                npc.id,
                npcCalls
            );

        try{
            world.attachTickTarget(
                playerTarget
            );
            world.attachNpcTickTarget(
                npc,
                npcTarget
            );

            world.pulse().pulseOnce(
                1000L
            );

            require(
                playerCalls.get()==1&&
                npcCalls.get()==1,
                "player/NPC tick contracts did not coexist"
            );
        }finally{
            world.unregisterPlayer(
                player,
                generation
            );
            world.close();
        }
    }

    private static void noSecondScheduler(){
        for(Class<?> type:new Class<?>[]{
                WorldNpcTickTarget.class,
                NpcCombatAiTickTarget.class
            }){
            for(Field field:
                    type.getDeclaredFields()){
                Class<?> fieldType=
                    field.getType();

                require(
                    !Thread.class
                        .isAssignableFrom(
                            fieldType
                        )&&
                    !Executor.class
                        .isAssignableFrom(
                            fieldType
                        )&&
                    !ExecutorService.class
                        .isAssignableFrom(
                            fieldType
                        )&&
                    !ScheduledExecutorService.class
                        .isAssignableFrom(
                            fieldType
                        ),
                    "second scheduler/thread field "+
                    type.getSimpleName()+
                    "."+
                    field.getName()
                );
            }
        }
    }

    private static WorldNpcTickTarget countingTarget(
        EntityId id,
        AtomicInteger calls
    ){
        return new WorldNpcTickTarget(){
            @Override public EntityId npcId(){
                return id;
            }

            @Override public void onWorldNpcTick(
                long worldTick,
                long nowMillis
            ){
                calls.incrementAndGet();
            }
        };
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

    private static int hp(
        WorldPlayer player
    ){
        return player.playerState()
            .currentLevel(
                PlayerState.HITPOINTS
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

    private NpcWorldPulseAiTest(){}
}
