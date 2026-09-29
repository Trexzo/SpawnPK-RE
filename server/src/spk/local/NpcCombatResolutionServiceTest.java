package spk.local;

import java.lang.reflect.Field;
import java.util.Locale;

public final class NpcCombatResolutionServiceTest {
    public static void main(String[] args)throws Exception{
        ordinaryResolution();
        staleAttackerBeforeMutationRejected();
        lostTargetBeforeMutationRejected();
        delayedHitRejectedBeforeMutation();
        domainBoundary();

        System.out.println(
            "NPC_COMBAT_RESOLUTION_SERVICE_PASS "+
            "npcPvmContext=true "+
            "canonicalAttackerGeneration=true "+
            "canonicalNpcOwnership=true "+
            "nonlethalMutation=true "+
            "lethalMutation=true "+
            "overkillClamp=true "+
            "duplicateDeath=false "+
            "timingCadence=true "+
            "delayedHitFailClosed=true "+
            "staleAttackerRejected=true "+
            "lostTargetRejected=true "+
            "combatOutcomeEmission=false "+
            "dropMutation=false "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void ordinaryResolution()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker=
            new WorldPlayer();

        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "pvm-attacker"
            );

        try{
            NpcLifecycleService lifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );

            WorldNpc target=
                world.npcs().spawn(
                    1489,
                    3200,
                    3200,
                    0
                );

            lifecycle.register(
                target,
                25,
                "CUSTOM_LOCALLAB"
            );

            TrackingDamageRules damage=
                new TrackingDamageRules(
                    10
                );
            TrackingTimingRules timing=
                new TrackingTimingRules(
                    3,
                    0
                );
            TrackingHooks hooks=
                new TrackingHooks();

            NpcCombatResolutionService service=
                new NpcCombatResolutionService(
                    attacker,
                    lifecycle,
                    damage,
                    timing,
                    hooks
                );

            CombatStyleRepository.Style style=
                CombatStyleRepository
                    .defaultForRoot(
                        2423
                    );

            NpcCombatResolutionService.Result first=
                service.resolveImmediateOwned(
                    world,
                    attackerGeneration,
                    target,
                    28526,
                    style,
                    100L
                );

            require(
                damage.lastContext==
                    CombatContext.NPC_PVM&&
                hooks.lastContext==
                    CombatContext.NPC_PVM,
                "PvM context did not reach damage/hooks"
            );

            require(
                first.attackerId.equals(
                    attacker.id()
                )&&
                first.targetId.equals(
                    target.id
                )&&
                first.lifecycle.appliedDamage==10&&
                first.lifecycle.hitpointsBefore==25&&
                first.lifecycle.hitpointsAfter==15&&
                !first.lifecycle.newlyDied&&
                !first.lifecycle.ignoredDead&&
                first.nextAttackDelayTicks==3,
                "first PvM resolution"
            );

            service.resolveImmediateOwned(
                world,
                attackerGeneration,
                target,
                28526,
                style,
                101L
            );

            NpcCombatResolutionService.Result lethal=
                service.resolveImmediateOwned(
                    world,
                    attackerGeneration,
                    target,
                    28526,
                    style,
                    102L
                );

            require(
                lethal.damage.damage==10&&
                lethal.lifecycle.appliedDamage==5&&
                lethal.lifecycle.hitpointsBefore==5&&
                lethal.lifecycle.hitpointsAfter==0&&
                lethal.lifecycle.newlyDied&&
                !lethal.lifecycle.ignoredDead&&
                lifecycle.get(
                    target.id
                ).deathTick==102L,
                "lethal PvM resolution"
            );

            NpcCombatResolutionService.Result deadAgain=
                service.resolveImmediateOwned(
                    world,
                    attackerGeneration,
                    target,
                    28526,
                    style,
                    103L
                );

            require(
                deadAgain.lifecycle.appliedDamage==0&&
                !deadAgain.lifecycle.newlyDied&&
                deadAgain.lifecycle.ignoredDead&&
                lifecycle.get(
                    target.id
                ).deathTick==102L,
                "duplicate dead PvM resolution"
            );

            require(
                "TEST_DAMAGE_AUTHORITY".equals(
                    service.damageAuthority()
                )&&
                "TEST_FIXED_DAMAGE".equals(
                    service.damageFormula()
                ),
                "damage provenance passthrough"
            );
        }finally{
            for(WorldPlayer player:
                    world.players().snapshot())
                world.unregisterPlayer(
                    player
                );

            world.close();
        }
    }

    private static void staleAttackerBeforeMutationRejected()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker=
            new WorldPlayer();

        long generationA=
            world.registerPlayer(
                attacker,
                "pvm-stale-attacker"
            );

        try{
            NpcLifecycleService lifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );

            WorldNpc target=
                world.npcs().spawn(
                    1490,
                    3201,
                    3200,
                    0
                );

            lifecycle.register(
                target,
                50,
                "CUSTOM_LOCALLAB"
            );

            final long[] generationB=
                new long[]{-1L};

            CombatSystemHooks transfer=
                new CombatSystemHooks(){
                    @Override public Snapshot beforeDamage(
                        CombatContext context,
                        int weaponId,
                        long worldTick
                    ){
                        require(
                            world.unregisterPlayer(
                                attacker,
                                generationA
                            ),
                            "fixture attacker unregister"
                        );

                        generationB[0]=
                            world.registerPlayer(
                                attacker,
                                "pvm-stale-attacker"
                            );

                        return CombatSystemHooks.none()
                            .beforeDamage(
                                context,
                                weaponId,
                                worldTick
                            );
                    }
                };

            NpcCombatResolutionService service=
                new NpcCombatResolutionService(
                    attacker,
                    lifecycle,
                    new TrackingDamageRules(
                        20
                    ),
                    new TrackingTimingRules(
                        4,
                        0
                    ),
                    transfer
                );

            boolean staleRejected=false;

            try{
                service.resolveImmediateOwned(
                    world,
                    generationA,
                    target,
                    28526,
                    null,
                    200L
                );
            }catch(
                NpcCombatResolutionService
                    .StaleAttackerOwnershipException expected
            ){
                staleRejected=true;
            }

            require(
                staleRejected&&
                generationB[0]>generationA&&
                world.players().owns(
                    attacker,
                    generationB[0]
                )&&
                lifecycle.get(
                    target.id
                ).hitpoints==50,
                "stale attacker mutated NPC HP"
            );
        }finally{
            for(WorldPlayer player:
                    world.players().snapshot())
                world.unregisterPlayer(
                    player
                );

            world.close();
        }
    }

    private static void lostTargetBeforeMutationRejected()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                attacker,
                "pvm-target-loss"
            );

        try{
            NpcLifecycleService lifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );

            WorldNpc target=
                world.npcs().spawn(
                    1491,
                    3202,
                    3200,
                    0
                );

            lifecycle.register(
                target,
                40,
                "CUSTOM_LOCALLAB"
            );

            CombatSystemHooks removeTarget=
                new CombatSystemHooks(){
                    @Override public Snapshot beforeDamage(
                        CombatContext context,
                        int weaponId,
                        long worldTick
                    ){
                        require(
                            world.npcs().remove(
                                target.id
                            ),
                            "fixture target remove"
                        );

                        return CombatSystemHooks.none()
                            .beforeDamage(
                                context,
                                weaponId,
                                worldTick
                            );
                    }
                };

            NpcCombatResolutionService service=
                new NpcCombatResolutionService(
                    attacker,
                    lifecycle,
                    new TrackingDamageRules(
                        10
                    ),
                    new TrackingTimingRules(
                        4,
                        0
                    ),
                    removeTarget
                );

            boolean lostRejected=false;

            try{
                service.resolveImmediateOwned(
                    world,
                    generation,
                    target,
                    28526,
                    null,
                    300L
                );
            }catch(
                IllegalStateException expected
            ){
                lostRejected=true;
            }

            require(
                lostRejected&&
                lifecycle.get(
                    target.id
                ).hitpoints==40,
                "lost target mutated NPC HP"
            );
        }finally{
            for(WorldPlayer player:
                    world.players().snapshot())
                world.unregisterPlayer(
                    player
                );

            world.close();
        }
    }

    private static void delayedHitRejectedBeforeMutation()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                attacker,
                "pvm-delayed"
            );

        try{
            NpcLifecycleService lifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );

            WorldNpc target=
                world.npcs().spawn(
                    1492,
                    3203,
                    3200,
                    0
                );

            lifecycle.register(
                target,
                30,
                "CUSTOM_LOCALLAB"
            );

            TrackingDamageRules damage=
                new TrackingDamageRules(
                    10
                );
            TrackingHooks hooks=
                new TrackingHooks();

            NpcCombatResolutionService service=
                new NpcCombatResolutionService(
                    attacker,
                    lifecycle,
                    damage,
                    new TrackingTimingRules(
                        4,
                        2
                    ),
                    hooks
                );

            boolean rejected=false;

            try{
                service.resolveImmediateOwned(
                    world,
                    generation,
                    target,
                    28526,
                    null,
                    400L
                );
            }catch(
                IllegalStateException expected
            ){
                rejected=true;
            }

            require(
                rejected&&
                lifecycle.get(
                    target.id
                ).hitpoints==30&&
                damage.calls==0&&
                hooks.calls==0,
                "delayed-hit rejection mutated/continued resolution"
            );
        }finally{
            for(WorldPlayer player:
                    world.players().snapshot())
                world.unregisterPlayer(
                    player
                );

            world.close();
        }
    }

    private static final class TrackingDamageRules
        implements CombatDamageRules {

        final int damage;
        CombatContext lastContext;
        int calls;

        TrackingDamageRules(int damage){
            this.damage=damage;
        }

        @Override public Result calculate(
            Request request
        ){
            calls++;
            lastContext=request.context;

            return new Result(
                damage,
                damage,
                authority(),
                formula()
            );
        }

        @Override public String authority(){
            return "TEST_DAMAGE_AUTHORITY";
        }

        @Override public String formula(){
            return "TEST_FIXED_DAMAGE";
        }
    }

    private static final class TrackingTimingRules
        implements CombatAttackTimingRules {

        final int speed;
        final int hitDelay;

        TrackingTimingRules(
            int speed,
            int hitDelay
        ){
            this.speed=speed;
            this.hitDelay=hitDelay;
        }

        @Override public Result resolve(
            Request request
        ){
            return new Result(
                speed,
                hitDelay,
                "TEST_CADENCE",
                "TEST_HIT_DELAY",
                "TEST_HIT_DELAY_RULE"
            );
        }
    }

    private static final class TrackingHooks
        implements CombatSystemHooks {

        CombatContext lastContext;
        int calls;

        @Override public Snapshot beforeDamage(
            CombatContext context,
            int weaponId,
            long worldTick
        ){
            calls++;
            lastContext=context;

            return CombatSystemHooks.none()
                .beforeDamage(
                    context,
                    weaponId,
                    worldTick
                );
        }
    }

    private static void domainBoundary(){
        for(Class<?> type:new Class<?>[]{
                NpcCombatResolutionService.class,
                NpcCombatResolutionService.Result.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("scene")||
                   name.contains("widget")||
                   name.contains("clientindex")||
                   name.contains("reward")||
                   name.contains("drop"))
                    throw new AssertionError(
                        "presentation/economy identity leaked into PvM resolution "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
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

    private NpcCombatResolutionServiceTest(){}
}
