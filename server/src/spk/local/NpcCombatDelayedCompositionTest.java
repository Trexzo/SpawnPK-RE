package spk.local;

import java.lang.reflect.Field;
import java.util.Locale;

public final class NpcCombatDelayedCompositionTest {
    private static final int WEAPON=28526;
    private static final CombatStyleRepository.Style STYLE=
        CombatStyleRepository.defaultForRoot(
            2423
        );

    public static void main(String[] args)throws Exception{
        immediateCompatibility();
        zeroDelayImmediate();
        positiveDelayScheduled();
        sharedClockBound();
        policyClockDriftAtomic();
        delayedServiceBindingPreflight();
        schedulerFailureAtomic();
        exactOwnershipPreflight();
        domainBoundary();

        System.out.println(
            "NPC_COMBAT_DELAYED_COMPOSITION_PASS "+
            "immediateCompatibility=true "+
            "zeroDelayImmediate=true "+
            "positiveDelayScheduled=true "+
            "noEarlyHpMutation=true "+
            "timingOnce=true "+
            "hooksOnce=true "+
            "damageOnce=true "+
            "sharedClockBound=true "+
            "policyClockDriftAtomic=true "+
            "publicationTickBound=true "+
            "delayedServiceBinding=true "+
            "schedulerFailureAtomic=true "+
            "dueDamageOnce=true "+
            "cadencePreserved=true "+
            "exactOwnership=true "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void immediateCompatibility()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-compat",
                40,
                7,
                4,
                2
            );

        try{
            boolean rejected=false;

            try{
                f.resolver.resolveImmediateOwned(
                    f.world,
                    f.generation,
                    f.npc,
                    WEAPON,
                    STYLE,
                    0L
                );
            }catch(IllegalStateException expected){
                rejected=true;
            }

            require(
                rejected&&
                f.hp()==40&&
                f.timing.calls==1&&
                f.hooks.calls==0&&
                f.damage.calls==0&&
                f.world.events().size()==0,
                "legacy immediate delayed-hit compatibility"
            );
        }finally{
            f.close();
        }
    }

    private static void zeroDelayImmediate()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-zero",
                40,
                7,
                3,
                0
            );

        try{
            NpcCombatResolutionService.AttackResolution result=
                f.resolver.resolveOwned(
                    f.world,
                    f.generation,
                    f.npc,
                    WEAPON,
                    STYLE,
                    19L,
                    f.delayed
                );

            require(
                result.delivery==
                    NpcCombatResolutionService
                        .Delivery.IMMEDIATE&&
                result.lifecycle!=null&&
                result.scheduledHit==null&&
                result.lifecycle.appliedDamage==7&&
                result.lifecycle.hitpointsAfter==33&&
                result.nextAttackDelayTicks==3&&
                f.hp()==33&&
                f.delayed.size()==0&&
                f.world.events().size()==0&&
                f.timing.calls==1&&
                f.hooks.calls==1&&
                f.damage.calls==1,
                "zero-delay canonical immediate composition"
            );
        }finally{
            f.close();
        }
    }

    private static void positiveDelayScheduled()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-positive",
                40,
                7,
                5,
                2
            );

        try{
            NpcCombatResolutionService.AttackResolution result=
                f.resolver.resolveOwned(
                    f.world,
                    f.generation,
                    f.npc,
                    WEAPON,
                    STYLE,
                    0L,
                    f.delayed
                );

            require(
                result.delivery==
                    NpcCombatResolutionService
                        .Delivery.SCHEDULED&&
                result.lifecycle==null&&
                result.scheduledHit!=null&&
                result.scheduledHit.state==
                    NpcPvmDelayedHitService
                        .State.SCHEDULED&&
                result.scheduledHit.requestedDamage==7&&
                result.scheduledHit.scheduledFromTick==0L&&
                result.scheduledHit.dueTick==2L&&
                result.nextAttackDelayTicks==5&&
                f.hp()==40&&
                f.world.events().size()==1&&
                f.timing.calls==1&&
                f.hooks.calls==1&&
                f.damage.calls==1,
                "positive-delay schedule composition"
            );

            long tick1=f.world.clock().advance();

            require(
                tick1==1L&&
                f.world.events().runDue(
                    tick1
                )==0&&
                f.hp()==40,
                "delayed composition mutated HP early"
            );

            long tick2=f.world.clock().advance();

            require(
                tick2==2L&&
                f.world.events().runDue(
                    tick2
                )==1,
                "delayed composition due event"
            );

            NpcPvmDelayedHitService.Snapshot delivered=
                f.delayed.get(
                    result.scheduledHit.hitId
                );

            require(
                delivered.state==
                    NpcPvmDelayedHitService
                        .State.DELIVERED&&
                delivered.appliedDamage==7&&
                delivered.hitpointsBefore==40&&
                delivered.hitpointsAfter==33&&
                f.hp()==33&&
                f.timing.calls==1&&
                f.hooks.calls==1&&
                f.damage.calls==1,
                "delayed composition exact due damage"
            );

            require(
                f.world.events().runDue(
                    tick2
                )==0&&
                f.hp()==33,
                "delayed composition delivered twice"
            );
        }finally{
            f.close();
        }
    }

    private static void sharedClockBound()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-clock",
                40,
                7,
                4,
                2
            );

        try{
            require(
                f.world.clock().advance()==1L,
                "clock fixture advance"
            );

            boolean rejected=false;

            try{
                f.resolver.resolveOwned(
                    f.world,
                    f.generation,
                    f.npc,
                    WEAPON,
                    STYLE,
                    0L,
                    f.delayed
                );
            }catch(IllegalStateException expected){
                rejected=true;
            }

            require(
                rejected&&
                f.hp()==40&&
                f.timing.calls==1&&
                f.hooks.calls==0&&
                f.damage.calls==0&&
                f.delayed.size()==0&&
                f.world.events().size()==0,
                "shared-clock mismatch crossed policy/schedule"
            );
        }finally{
            f.close();
        }
    }

    private static void policyClockDriftAtomic()
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
                "delayed-policy-clock"
            );

        try{
            NpcLifecycleService lifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );
            WorldNpc npc=
                world.npcs().spawn(
                    1499,
                    3211,
                    3200,
                    0
                );
            lifecycle.register(
                npc,
                40,
                "CUSTOM_LOCALLAB"
            );

            TrackingTimingRules timing=
                new TrackingTimingRules(
                    4,
                    2
                );
            TrackingHooks hooks=
                new TrackingHooks();
            TrackingDamageRules damage=
                new TrackingDamageRules(
                    7,
                    ()->{
                        long tick=
                            world.clock()
                                .advance();

                        if(tick!=1L)
                            throw new AssertionError(
                                "policy clock drift fixture tick="+
                                tick
                            );
                    }
                );

            NpcCombatResolutionService resolver=
                new NpcCombatResolutionService(
                    attacker,
                    lifecycle,
                    damage,
                    timing,
                    hooks
                );
            NpcPvmDelayedHitService delayed=
                delayed(
                    world,
                    lifecycle
                );

            boolean rejected=false;

            try{
                resolver.resolveOwned(
                    world,
                    generation,
                    npc,
                    WEAPON,
                    STYLE,
                    0L,
                    delayed
                );
            }catch(IllegalStateException expected){
                rejected=true;
            }

            require(
                rejected&&
                world.clock().tick()==1L&&
                lifecycle.get(
                    npc.id
                ).hitpoints==40&&
                timing.calls==1&&
                hooks.calls==1&&
                damage.calls==1&&
                delayed.size()==0&&
                world.events().size()==0,
                "policy clock drift escaped schedule authority"
            );
        }finally{
            cleanup(world);
        }
    }


    private static void delayedServiceBindingPreflight()
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
                "delayed-binding"
            );

        try{
            NpcLifecycleService resolverLifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );
            NpcLifecycleService foreignLifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );
            WorldNpc npc=
                world.npcs().spawn(
                    1500,
                    3212,
                    3200,
                    0
                );

            resolverLifecycle.register(
                npc,
                40,
                "CUSTOM_LOCALLAB_RESOLVER"
            );
            foreignLifecycle.register(
                npc,
                40,
                "CUSTOM_LOCALLAB_FOREIGN"
            );

            TrackingTimingRules timing=
                new TrackingTimingRules(
                    4,
                    2
                );
            TrackingHooks hooks=
                new TrackingHooks();
            TrackingDamageRules damage=
                new TrackingDamageRules(
                    7
                );

            NpcCombatResolutionService resolver=
                new NpcCombatResolutionService(
                    attacker,
                    resolverLifecycle,
                    damage,
                    timing,
                    hooks
                );
            NpcPvmDelayedHitService foreignDelayed=
                delayed(
                    world,
                    foreignLifecycle
                );

            boolean rejected=false;

            try{
                resolver.resolveOwned(
                    world,
                    generation,
                    npc,
                    WEAPON,
                    STYLE,
                    0L,
                    foreignDelayed
                );
            }catch(IllegalArgumentException expected){
                rejected=true;
            }

            require(
                rejected&&
                resolverLifecycle.get(
                    npc.id
                ).hitpoints==40&&
                foreignLifecycle.get(
                    npc.id
                ).hitpoints==40&&
                timing.calls==0&&
                hooks.calls==0&&
                damage.calls==0&&
                foreignDelayed.size()==0&&
                world.events().size()==0,
                "foreign delayed-hit dependencies reached combat policy"
            );
        }finally{
            cleanup(world);
        }
    }


    private static void schedulerFailureAtomic()
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
                "delayed-scheduler-failure"
            );

        try{
            NpcLifecycleService lifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );
            WorldNpc npc=
                world.npcs().spawn(
                    1498,
                    3210,
                    3200,
                    0
                );
            lifecycle.register(
                npc,
                40,
                "CUSTOM_LOCALLAB"
            );

            TrackingTimingRules timing=
                new TrackingTimingRules(
                    4,
                    2
                );
            TrackingHooks hooks=
                new TrackingHooks();
            TrackingDamageRules damage=
                new TrackingDamageRules(
                    7,
                    ()->{
                        if(!world.npcs().remove(
                                npc.id))
                            throw new AssertionError(
                                "scheduler failure target-removal fixture"
                            );
                    }
                );

            NpcCombatResolutionService resolver=
                new NpcCombatResolutionService(
                    attacker,
                    lifecycle,
                    damage,
                    timing,
                    hooks
                );
            NpcPvmDelayedHitService delayed=
                delayed(
                    world,
                    lifecycle
                );

            boolean rejected=false;

            try{
                resolver.resolveOwned(
                    world,
                    generation,
                    npc,
                    WEAPON,
                    STYLE,
                    0L,
                    delayed
                );
            }catch(IllegalStateException expected){
                rejected=true;
            }

            require(
                rejected&&
                lifecycle.get(
                    npc.id
                ).hitpoints==40&&
                timing.calls==1&&
                hooks.calls==1&&
                damage.calls==1&&
                delayed.size()==0&&
                world.events().size()==0,
                "scheduler rejection leaked HP/scheduled state"
            );
        }finally{
            cleanup(world);
        }
    }

    private static void exactOwnershipPreflight()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-ownership",
                40,
                7,
                4,
                2
            );

        try{
            WorldNpc foreign=
                new WorldNpc(
                    f.npc.id,
                    f.npc.definitionId,
                    f.npc.x(),
                    f.npc.y(),
                    f.npc.plane(),
                    f.npc.ownerId,
                    f.npc.sourceItemId
                );

            boolean rejected=false;

            try{
                f.resolver.resolveOwned(
                    f.world,
                    f.generation,
                    foreign,
                    WEAPON,
                    STYLE,
                    0L,
                    f.delayed
                );
            }catch(
                NpcCombatResolutionService
                    .StaleTargetOwnershipException expected
            ){
                rejected=true;
            }

            require(
                rejected&&
                f.hp()==40&&
                f.timing.calls==0&&
                f.hooks.calls==0&&
                f.damage.calls==0&&
                f.delayed.size()==0,
                "foreign target reached delayed composition policy"
            );
        }finally{
            f.close();
        }
    }

    private static void domainBoundary(){
        for(Class<?> type:new Class<?>[]{
                NpcCombatResolutionService.class,
                NpcCombatResolutionService
                    .AttackResolution.class,
                NpcCombatResolutionService
                    .Delivery.class
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
                   name.contains("drop")||
                   name.contains("projectile")||
                   name.contains("gfx")||
                   name.contains("hitsplat"))
                    throw new AssertionError(
                        "presentation/protocol policy leaked "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static final class Fixture
        implements AutoCloseable {
        final World world=
            World.isolatedForTest(
                600L
            );
        final WorldPlayer player=
            new WorldPlayer();
        final long generation;
        final NpcLifecycleService lifecycle;
        final WorldNpc npc;
        final TrackingDamageRules damage;
        final TrackingTimingRules timing;
        final TrackingHooks hooks;
        final NpcCombatResolutionService resolver;
        final NpcPvmDelayedHitService delayed;

        Fixture(
            String playerRef,
            int hitpoints,
            int resolvedDamage,
            int attackSpeed,
            int hitDelay
        ){
            generation=
                world.registerPlayer(
                    player,
                    playerRef
                );
            lifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );
            npc=
                world.npcs().spawn(
                    1497,
                    3200,
                    3200,
                    0
                );
            lifecycle.register(
                npc,
                hitpoints,
                "CUSTOM_LOCALLAB"
            );
            damage=
                new TrackingDamageRules(
                    resolvedDamage
                );
            timing=
                new TrackingTimingRules(
                    attackSpeed,
                    hitDelay
                );
            hooks=
                new TrackingHooks();
            resolver=
                new NpcCombatResolutionService(
                    player,
                    lifecycle,
                    damage,
                    timing,
                    hooks
                );
            delayed=
                delayed(
                    world,
                    lifecycle
                );
        }

        int hp(){
            return lifecycle.get(
                npc.id
            ).hitpoints;
        }

        @Override public void close(){
            cleanup(world);
        }
    }

    private static NpcPvmDelayedHitService delayed(
        World world,
        NpcLifecycleService lifecycle
    ){
        return new NpcPvmDelayedHitService(
            world,
            lifecycle,
            "CUSTOM_LOCALLAB",
            NpcPvmDelayedHitService
                .REQUIRE_CURRENT_ATTACKER_GENERATION
        );
    }

    private static final class TrackingDamageRules
        implements CombatDamageRules {
        final int damage;
        final Runnable beforeReturn;
        int calls;

        TrackingDamageRules(int damage){
            this(
                damage,
                null
            );
        }

        TrackingDamageRules(
            int damage,
            Runnable beforeReturn
        ){
            this.damage=damage;
            this.beforeReturn=beforeReturn;
        }

        @Override public Result calculate(
            Request request
        ){
            calls++;

            if(request.context!=
                    CombatContext.NPC_PVM)
                throw new AssertionError(
                    "wrong damage context "+
                    request.context
                );

            if(beforeReturn!=null)
                beforeReturn.run();

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
        int calls;

        @Override public Snapshot beforeDamage(
            CombatContext context,
            int weaponId,
            long worldTick
        ){
            calls++;

            if(context!=CombatContext.NPC_PVM)
                throw new AssertionError(
                    "wrong hook context "+
                    context
                );

            return CombatSystemHooks.none()
                .beforeDamage(
                    context,
                    weaponId,
                    worldTick
                );
        }
    }

    private static void cleanup(
        World world
    ){
        for(WorldPlayer player:
                world.players().snapshot())
            world.unregisterPlayer(
                player
            );

        world.close();
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

    private NpcCombatDelayedCompositionTest(){}
}
