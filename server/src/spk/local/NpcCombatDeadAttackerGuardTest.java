package spk.local;

import java.util.concurrent.atomic.AtomicInteger;

public final class NpcCombatDeadAttackerGuardTest {
    public static void main(String[] args)throws Exception{
        deadIdleInert();
        deadEngagementCancelled();
        staleDeadAttackerPreservesStaleStatus();
        aliveLifecycleUnchanged();
        legacyUnregisteredUnchanged();

        System.out.println(
            "NPC_COMBAT_DEAD_ATTACKER_GUARD_PASS "+
            "worldLifecycleAuthority=true "+
            "deadIdleInert=true "+
            "deadEngagementCancelled=true "+
            "noDeadDamage=true "+
            "noDeadApproach=true "+
            "staleAttackerPreserved=true "+
            "aliveUnchanged=true "+
            "legacyUnregisteredUnchanged=true "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void deadIdleInert()
        throws Exception{
        World world=World.isolatedForTest(600L);
        AtomicInteger acquisitionCalls=
            new AtomicInteger();
        WorldNpc npc=
            world.npcs().spawn(
                1700,
                3087,
                3495,
                0
            );

        try{
            world.npcLifecycle()
                .register(
                    npc,
                    10,
                    "CUSTOM_LOCALLAB_TEST_HP"
                );
            world.npcLifecycle()
                .applyDamage(
                    npc.id,
                    10,
                    0L
                );

            NpcCombatAiService ai=
                ai(
                    world,
                    acquisitionCalls,
                    true
                );

            NpcCombatAiService.Result result=
                ai.tick(
                    npc,
                    world.clock().tick()
                );

            require(
                result.status==
                    NpcCombatAiService.Status.ATTACKER_DEAD&&
                acquisitionCalls.get()==0,
                "dead idle NPC reached acquisition"
            );
        }finally{
            world.close();
        }
    }

    private static void deadEngagementCancelled()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "dead-attacker-target"
            );
        WorldNpc npc=
            world.npcs().spawn(
                1701,
                3087,
                3495,
                0
            );
        AtomicInteger acquisitionCalls=
            new AtomicInteger();
        AtomicInteger damageCalls=
            new AtomicInteger();

        try{
            player.movement()
                .restoreAccountState(
                    false,
                    100,
                    3088,
                    3495,
                    0
                );

            world.npcLifecycle()
                .register(
                    npc,
                    12,
                    "CUSTOM_LOCALLAB_TEST_HP"
                );

            NpcCombatControllerService controller=
                controller(
                    world,
                    damageCalls,
                    true
                );

            controller.begin(
                npc,
                player,
                generation,
                0L
            );

            require(
                controller.get(npc.id)!=null,
                "engagement fixture missing"
            );

            world.npcLifecycle()
                .applyDamage(
                    npc.id,
                    99,
                    0L
                );

            int hpBefore=
                player.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    );

            NpcCombatAiService ai=
                new NpcCombatAiService(
                    world,
                    acquisition(
                        world,
                        acquisitionCalls
                    ),
                    controller,
                    context->context.worldTick,
                    "CUSTOM_LOCALLAB_AI",
                    NpcCombatAiService
                        .ACQUIRE_THEN_DELEGATE
                );

            NpcCombatAiService.Result result=
                ai.tick(
                    npc,
                    world.clock().tick()
                );

            require(
                result.status==
                    NpcCombatAiService.Status.ATTACKER_DEAD&&
                controller.get(npc.id)==null&&
                acquisitionCalls.get()==0&&
                damageCalls.get()==0&&
                player.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    )==hpBefore,
                "dead engaged NPC remained combat-active"
            );
        }finally{
            world.unregisterPlayer(
                player,
                generation
            );
            world.close();
        }
    }

    private static void staleDeadAttackerPreservesStaleStatus()
        throws Exception{
        World world=World.isolatedForTest(600L);
        AtomicInteger acquisitionCalls=
            new AtomicInteger();
        WorldNpc npc=
            world.npcs().spawn(
                1704,
                3087,
                3495,
                0
            );

        try{
            world.npcLifecycle()
                .register(
                    npc,
                    10,
                    "CUSTOM_LOCALLAB_TEST_HP"
                );
            world.npcLifecycle()
                .applyDamage(
                    npc.id,
                    10,
                    0L
                );

            require(
                world.npcs().remove(
                    npc.id
                ),
                "stale-dead fixture canonical removal failed"
            );

            NpcCombatAiService.Result result=
                ai(
                    world,
                    acquisitionCalls,
                    true
                ).tick(
                    npc,
                    world.clock().tick()
                );

            require(
                result.status==
                    NpcCombatAiService.Status.STALE_ATTACKER&&
                acquisitionCalls.get()==0,
                "removed dead NPC did not preserve STALE_ATTACKER semantics"
            );
        }finally{
            world.close();
        }
    }

    private static void aliveLifecycleUnchanged()
        throws Exception{
        World world=World.isolatedForTest(600L);
        AtomicInteger acquisitionCalls=
            new AtomicInteger();
        WorldNpc npc=
            world.npcs().spawn(
                1702,
                3087,
                3495,
                0
            );

        try{
            world.npcLifecycle()
                .register(
                    npc,
                    15,
                    "CUSTOM_LOCALLAB_TEST_HP"
                );

            NpcCombatAiService.Result result=
                ai(
                    world,
                    acquisitionCalls,
                    false
                ).tick(
                    npc,
                    world.clock().tick()
                );

            require(
                result.status==
                    NpcCombatAiService.Status.NO_TARGET&&
                acquisitionCalls.get()==1,
                "alive lifecycle NPC behavior changed"
            );
        }finally{
            world.close();
        }
    }

    private static void legacyUnregisteredUnchanged()
        throws Exception{
        World world=World.isolatedForTest(600L);
        AtomicInteger acquisitionCalls=
            new AtomicInteger();
        WorldNpc npc=
            world.npcs().spawn(
                1703,
                3087,
                3495,
                0
            );

        try{
            require(
                world.npcLifecycle()
                    .get(npc.id)==null,
                "legacy fixture unexpectedly registered"
            );

            NpcCombatAiService.Result result=
                ai(
                    world,
                    acquisitionCalls,
                    false
                ).tick(
                    npc,
                    world.clock().tick()
                );

            require(
                result.status==
                    NpcCombatAiService.Status.NO_TARGET&&
                acquisitionCalls.get()==1,
                "unregistered legacy NPC behavior changed"
            );
        }finally{
            world.close();
        }
    }

    private static NpcCombatAiService ai(
        World world,
        AtomicInteger acquisitionCalls,
        boolean failIfCombatReached
    ){
        AtomicInteger damageCalls=
            new AtomicInteger();

        return new NpcCombatAiService(
            world,
            acquisition(
                world,
                acquisitionCalls
            ),
            controller(
                world,
                damageCalls,
                failIfCombatReached
            ),
            context->context.worldTick,
            "CUSTOM_LOCALLAB_AI",
            NpcCombatAiService
                .ACQUIRE_THEN_DELEGATE
        );
    }

    private static NpcTargetAcquisitionService
        acquisition(
            World world,
            AtomicInteger calls
        ){
        return new NpcTargetAcquisitionService(
            world,
            context->{
                calls.incrementAndGet();
                return NpcTargetAcquisitionService
                    .Decision.ineligible();
            },
            "CUSTOM_LOCALLAB_TARGET",
            "DEAD_ATTACKER_GUARD_TEST"
        );
    }

    private static NpcCombatControllerService controller(
        World world,
        AtomicInteger damageCalls,
        boolean failIfCombatReached
    ){
        NpcCombatApproachService.ApproachPolicy
            approach=
                new NpcCombatApproachService
                    .ApproachPolicy(){
                    @Override public int stopRange(
                        NpcCombatApproachService
                            .Context context
                    ){
                        if(failIfCombatReached)
                            throw new AssertionError(
                                "dead NPC reached approach"
                            );
                        return 1;
                    }

                    @Override public RouteRequest.Policy
                        routePolicy(
                            NpcCombatApproachService
                                .Context context
                        ){
                        if(failIfCombatReached)
                            throw new AssertionError(
                                "dead NPC reached route policy"
                            );
                        return RouteRequest.Policy
                            .WORLD_STATIC_AUTHORITY;
                    }

                    @Override public String authority(){
                        return "CUSTOM_LOCALLAB_APPROACH";
                    }

                    @Override public String policy(){
                        return "RANGE_1";
                    }
                };

        NpcCombatEngagementService.CadenceResolver
            cadence=
                new NpcCombatEngagementService
                    .CadenceResolver(){
                    @Override public int nextDelayTicks(
                        NpcCombatEngagementService
                            .Context context
                    ){
                        if(failIfCombatReached)
                            throw new AssertionError(
                                "dead NPC reached cadence"
                            );
                        return 3;
                    }

                    @Override public String authority(){
                        return "CUSTOM_LOCALLAB_CADENCE";
                    }

                    @Override public String policy(){
                        return "FIXED_3";
                    }
                };

        NpcPlayerCombatResolutionService.DamageResolver
            damage=
                new NpcPlayerCombatResolutionService
                    .DamageResolver(){
                    @Override public int resolve(
                        NpcPlayerCombatResolutionService
                            .DamageContext context
                    ){
                        damageCalls.incrementAndGet();
                        if(failIfCombatReached)
                            throw new AssertionError(
                                "dead NPC reached damage"
                            );
                        return 1;
                    }

                    @Override public String authority(){
                        return "CUSTOM_LOCALLAB_DAMAGE";
                    }

                    @Override public String formula(){
                        return "FIXED_1";
                    }
                };

        return new NpcCombatControllerService(
            world,
            approach,
            cadence,
            new NpcPlayerCombatResolutionService(
                damage
            ),
            "CUSTOM_LOCALLAB_CONTROLLER",
            NpcCombatControllerService
                .APPROACH_BEFORE_ENGAGEMENT_TICK
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private NpcCombatDeadAttackerGuardTest(){}
}
