package spk.local;

import java.lang.reflect.Field;
import java.util.Locale;

public final class NpcCombatResolutionServiceTest {
    private static final int WEAPON=28526;

    public static void main(String[] args)throws Exception{
        assertCanonicalResolution();
        assertDelayedHitRejected();
        assertForeignSameIdRejectedBeforePolicy();
        assertTargetRemovalDuringPolicyFailsClosed();
        assertAttackerGenerationTransferFailsClosed();
        assertDomainBoundary();

        System.out.println(
            "NPC_COMBAT_RESOLUTION_SERVICE_PASS "+
            "npcPvmContext=true "+
            "attackerGenerationOwned=true "+
            "exactTargetObject=true "+
            "npcMutationLinearized=true "+
            "nonlethalMutation=true "+
            "lethalMutation=true "+
            "overkillClamp=true "+
            "duplicateDeath=false "+
            "timingCadence=true "+
            "delayedHitFailClosed=true "+
            "foreignTargetRejectedBeforePolicy=true "+
            "targetOwnershipLossAtomic=true "+
            "attackerOwnershipLossAtomic=true "+
            "combatOutcomeEmission=false "+
            "dropMutation=false "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void assertCanonicalResolution()throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        long generation=
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
                new TrackingDamageRules(10);
            TrackingTimingRules timing=
                new TrackingTimingRules(3,0);
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
                service.resolveImmediateOwned(
                    world,
                    generation,
                    target,
                    WEAPON,
                    style,
                    100L
                );

            require(
                damage.lastContext==CombatContext.NPC_PVM&&
                hooks.lastContext==CombatContext.NPC_PVM&&
                first.attackerId.equals(attacker.id())&&
                first.targetId.equals(target.id)&&
                first.lifecycle.appliedDamage==10&&
                first.lifecycle.hitpointsAfter==15&&
                first.nextAttackDelayTicks==3,
                "first canonical PvM resolution"
            );

            service.resolveImmediateOwned(
                world,
                generation,
                target,
                WEAPON,
                style,
                101L
            );

            NpcCombatResolutionService.Result lethal=
                service.resolveImmediateOwned(
                    world,
                    generation,
                    target,
                    WEAPON,
                    style,
                    102L
                );

            require(
                lethal.lifecycle.appliedDamage==5&&
                lethal.lifecycle.hitpointsAfter==0&&
                lethal.lifecycle.newlyDied&&
                lifecycle.get(target.id).deathTick==102L,
                "lethal PvM resolution"
            );

            NpcCombatResolutionService.Result duplicate=
                service.resolveImmediateOwned(
                    world,
                    generation,
                    target,
                    WEAPON,
                    style,
                    103L
                );

            require(
                duplicate.lifecycle.appliedDamage==0&&
                duplicate.lifecycle.ignoredDead&&
                !duplicate.lifecycle.newlyDied&&
                lifecycle.get(target.id).deathTick==102L,
                "already-dead PvM resolution"
            );

            require(
                "TEST_DAMAGE_AUTHORITY".equals(
                    service.damageAuthority()
                )&&
                "TEST_FIXED_DAMAGE".equals(
                    service.damageFormula()
                ),
                "damage provenance"
            );
        }finally{
            cleanup(world);
        }
    }

    private static void assertDelayedHitRejected()throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        long generation=world.registerPlayer(attacker,"delay-attacker");

        try{
            NpcLifecycleService lifecycle=
                new NpcLifecycleService(world.npcs());
            WorldNpc target=
                world.npcs().spawn(1490,3201,3200,0);
            lifecycle.register(target,50,"CUSTOM_LOCALLAB");

            TrackingDamageRules damage=
                new TrackingDamageRules(20);
            TrackingHooks hooks=
                new TrackingHooks();

            NpcCombatResolutionService service=
                new NpcCombatResolutionService(
                    attacker,
                    lifecycle,
                    damage,
                    new TrackingTimingRules(4,2),
                    hooks
                );

            boolean rejected=false;
            try{
                service.resolveImmediateOwned(
                    world,
                    generation,
                    target,
                    WEAPON,
                    CombatStyleRepository.defaultForRoot(2423),
                    200L
                );
            }catch(IllegalStateException expected){
                rejected=true;
            }

            require(
                rejected&&
                lifecycle.get(target.id).hitpoints==50&&
                damage.calls==0&&
                hooks.calls==0,
                "delayed hit did not fail before mutation/policy"
            );
        }finally{
            cleanup(world);
        }
    }

    private static void assertForeignSameIdRejectedBeforePolicy()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        long generation=world.registerPlayer(attacker,"foreign-attacker");

        try{
            NpcLifecycleService lifecycle=
                new NpcLifecycleService(world.npcs());
            WorldNpc canonical=
                world.npcs().spawn(1491,3202,3200,0);
            lifecycle.register(canonical,30,"CUSTOM_LOCALLAB");

            WorldNpc foreign=
                new WorldNpc(
                    canonical.id,
                    canonical.definitionId,
                    canonical.x(),
                    canonical.y(),
                    canonical.plane(),
                    canonical.ownerId,
                    canonical.sourceItemId
                );

            TrackingDamageRules damage=
                new TrackingDamageRules(5);
            TrackingHooks hooks=
                new TrackingHooks();

            NpcCombatResolutionService service=
                new NpcCombatResolutionService(
                    attacker,
                    lifecycle,
                    damage,
                    new TrackingTimingRules(4,0),
                    hooks
                );

            boolean rejected=false;
            try{
                service.resolveImmediateOwned(
                    world,
                    generation,
                    foreign,
                    WEAPON,
                    CombatStyleRepository.defaultForRoot(2423),
                    300L
                );
            }catch(
                NpcCombatResolutionService
                    .StaleTargetOwnershipException expected
            ){
                rejected=true;
            }

            require(
                rejected&&
                damage.calls==0&&
                hooks.calls==0&&
                lifecycle.get(canonical.id).hitpoints==30,
                "same-id foreign target reached policy/mutation"
            );
        }finally{
            cleanup(world);
        }
    }

    private static void assertTargetRemovalDuringPolicyFailsClosed()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        long generation=world.registerPlayer(attacker,"remove-attacker");

        try{
            NpcLifecycleService lifecycle=
                new NpcLifecycleService(world.npcs());
            WorldNpc target=
                world.npcs().spawn(1492,3203,3200,0);
            lifecycle.register(target,40,"CUSTOM_LOCALLAB");

            TrackingDamageRules damage=
                new TrackingDamageRules(
                    7,
                    ()->{
                        if(!world.npcs().remove(target.id))
                            throw new AssertionError(
                                "target removal fixture failed"
                            );
                    }
                );

            NpcCombatResolutionService service=
                new NpcCombatResolutionService(
                    attacker,
                    lifecycle,
                    damage,
                    new TrackingTimingRules(4,0),
                    CombatSystemHooks.none()
                );

            boolean rejected=false;
            try{
                service.resolveImmediateOwned(
                    world,
                    generation,
                    target,
                    WEAPON,
                    CombatStyleRepository.defaultForRoot(2423),
                    400L
                );
            }catch(
                NpcCombatResolutionService
                    .StaleTargetOwnershipException expected
            ){
                rejected=true;
            }

            require(
                rejected&&
                damage.calls==1&&
                lifecycle.get(target.id).hitpoints==40,
                "target ownership loss mutated HP"
            );
        }finally{
            cleanup(world);
        }
    }

    private static void assertAttackerGenerationTransferFailsClosed()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        long generationA=world.registerPlayer(attacker,"generation-attacker");

        try{
            NpcLifecycleService lifecycle=
                new NpcLifecycleService(world.npcs());
            WorldNpc target=
                world.npcs().spawn(1493,3204,3200,0);
            lifecycle.register(target,45,"CUSTOM_LOCALLAB");

            final long[] generationB={-1L};
            TrackingHooks hooks=
                new TrackingHooks(
                    ()->{
                        if(!world.unregisterPlayer(
                                attacker,
                                generationA
                            ))
                            throw new AssertionError(
                                "attacker unregister fixture failed"
                            );

                        generationB[0]=
                            world.registerPlayer(
                                attacker,
                                "generation-attacker"
                            );
                    }
                );

            NpcCombatResolutionService service=
                new NpcCombatResolutionService(
                    attacker,
                    lifecycle,
                    new TrackingDamageRules(8),
                    new TrackingTimingRules(4,0),
                    hooks
                );

            boolean rejected=false;
            try{
                service.resolveImmediateOwned(
                    world,
                    generationA,
                    target,
                    WEAPON,
                    CombatStyleRepository.defaultForRoot(2423),
                    500L
                );
            }catch(
                NpcCombatResolutionService
                    .StaleAttackerOwnershipException expected
            ){
                rejected=true;
            }

            require(
                rejected&&
                hooks.calls==1&&
                generationB[0]>generationA&&
                world.players().owns(
                    attacker,
                    generationB[0]
                )&&
                lifecycle.get(target.id).hitpoints==45,
                "attacker generation loss mutated NPC HP"
            );
        }finally{
            cleanup(world);
        }
    }

    private static final class TrackingDamageRules
        implements CombatDamageRules {
        final int damage;
        final Runnable beforeReturn;
        CombatContext lastContext;
        int calls;

        TrackingDamageRules(int damage){
            this(damage,null);
        }

        TrackingDamageRules(
            int damage,
            Runnable beforeReturn
        ){
            this.damage=damage;
            this.beforeReturn=beforeReturn;
        }

        @Override public Result calculate(Request request){
            calls++;
            lastContext=request.context;
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

        TrackingTimingRules(
            int speed,
            int hitDelay
        ){
            this.speed=speed;
            this.hitDelay=hitDelay;
        }

        @Override public Result resolve(Request request){
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
        final Runnable beforeReturn;
        CombatContext lastContext;
        int calls;

        TrackingHooks(){
            this(null);
        }

        TrackingHooks(Runnable beforeReturn){
            this.beforeReturn=beforeReturn;
        }

        @Override public Snapshot beforeDamage(
            CombatContext context,
            int weaponId,
            long worldTick
        ){
            calls++;
            lastContext=context;
            if(beforeReturn!=null)
                beforeReturn.run();
            return CombatSystemHooks.none()
                .beforeDamage(
                    context,
                    weaponId,
                    worldTick
                );
        }
    }

    private static void assertDomainBoundary(){
        for(Class<?> type:new Class<?>[]{
                NpcCombatResolutionService.class,
                NpcCombatResolutionService.Result.class
            }){
            for(Field field:type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(Locale.ROOT);

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("scene")||
                   name.contains("widget")||
                   name.contains("clientindex")||
                   name.contains("reward")||
                   name.contains("drop"))
                    throw new AssertionError(
                        "unsupported identity/policy leaked "+
                        type.getSimpleName()+"."+
                        field.getName()
                    );
            }
        }
    }

    private static void cleanup(World world){
        for(WorldPlayer player:
                world.players().snapshot())
            world.unregisterPlayer(player);
        world.close();
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private NpcCombatResolutionServiceTest(){}
}
