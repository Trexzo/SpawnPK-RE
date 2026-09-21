package spk.local;

import java.lang.reflect.Field;
import java.util.Locale;

public final class NpcCombatResolutionServiceTest {
    public static void main(String[] args){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();

        NpcLifecycleService lifecycle=
            new NpcLifecycleService(
                registry
            );

        WorldPlayer attacker=
            new WorldPlayer();

        WorldNpc target=
            registry.spawn(
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
            CombatStyleRepository.defaultForRoot(
                2423
            );

        NpcCombatResolutionService.Result first=
            service.resolveImmediate(
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

        NpcCombatResolutionService.Result second=
            service.resolveImmediate(
                target,
                28526,
                style,
                101L
            );

        require(
            second.lifecycle.hitpointsAfter==5&&
            !second.lifecycle.newlyDied,
            "second PvM resolution"
        );

        NpcCombatResolutionService.Result lethal=
            service.resolveImmediate(
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
            service.resolveImmediate(
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

        assertDelayedHitRejectedBeforeMutation(
            registry,
            attacker
        );

        assertRegistryOwnershipFence(
            registry,
            attacker
        );

        assertDomainBoundary();

        require(
            "TEST_DAMAGE_AUTHORITY".equals(
                service.damageAuthority()
            )&&
            "TEST_FIXED_DAMAGE".equals(
                service.damageFormula()
            ),
            "damage provenance passthrough"
        );

        System.out.println(
            "NPC_COMBAT_RESOLUTION_SERVICE_PASS "+
            "npcPvmContext=true "+
            "canonicalAttackerTarget=true "+
            "nonlethalMutation=true "+
            "lethalMutation=true "+
            "overkillClamp=true "+
            "duplicateDeath=false "+
            "timingCadence=true "+
            "delayedHitFailClosed=true "+
            "registryOwnershipFence=true "+
            "combatOutcomeEmission=false "+
            "dropMutation=false "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void assertDelayedHitRejectedBeforeMutation(
        WorldNpcRegistry registry,
        WorldPlayer attacker
    ){
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(
                registry
            );

        WorldNpc target=
            registry.spawn(
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

        TrackingDamageRules damage=
            new TrackingDamageRules(
                20
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
            service.resolveImmediate(
                target,
                28526,
                CombatStyleRepository
                    .defaultForRoot(
                        2423
                    ),
                200L
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
            ).hitpoints==50&&
            damage.calls==0&&
            hooks.calls==0,
            "delayed-hit rejection mutated/continued resolution"
        );
    }

    private static void assertRegistryOwnershipFence(
        WorldNpcRegistry registry,
        WorldPlayer attacker
    ){
        NpcLifecycleService lifecycle=
            new NpcLifecycleService(
                registry
            );

        WorldNpc target=
            registry.spawn(
                1491,
                3202,
                3200,
                0
            );

        lifecycle.register(
            target,
            30,
            "CUSTOM_LOCALLAB"
        );

        NpcCombatResolutionService service=
            new NpcCombatResolutionService(
                attacker,
                lifecycle,
                new TrackingDamageRules(
                    5
                ),
                new TrackingTimingRules(
                    4,
                    0
                ),
                CombatSystemHooks.none()
            );

        require(
            registry.remove(
                target.id
            ),
            "fixture registry remove"
        );

        boolean rejected=false;

        try{
            service.resolveImmediate(
                target,
                28526,
                CombatStyleRepository
                    .defaultForRoot(
                        2423
                    ),
                300L
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
            ).hitpoints==30,
            "registry ownership loss did not fail closed"
        );
    }

    private static final class TrackingDamageRules
        implements CombatDamageRules {

        final int damage;
        CombatContext lastContext;
        int calls;

        TrackingDamageRules(
            int damage
        ){
            this.damage=damage;
        }

        @Override public Result calculate(
            Request request
        ){
            calls++;
            lastContext=
                request.context;

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
        int calls;

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
            calls++;

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

    private static void assertDomainBoundary(){
        Class<?>[] types={
            NpcCombatResolutionService.class,
            NpcCombatResolutionService.Result.class
        };

        for(Class<?> type:types){
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
