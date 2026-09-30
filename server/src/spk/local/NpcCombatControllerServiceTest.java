package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class NpcCombatControllerServiceTest {
    private static final String ORCHESTRATION_AUTHORITY=
        "CUSTOM_LOCALLAB_NPC_CONTROLLER";

    public static void main(String[] args)throws Exception{
        explicitBeginAndSharedClock();
        approachThenAttackCadence();
        differentPlaneNoAttack();
        deadTargetRetiredBeforeApproach();
        lethalAttackRetiresEngagement();
        ignoredDeadRaceNoCadence();
        staleTargetRetired();
        staleAttackerRetired();
        reentrantApproachCancelNoMove();
        cadenceClockDriftAtomic();
        damageClockDriftAtomic();
        authorityGuards();
        boundaryGuard();

        System.out.println(
            "NPC_COMBAT_CONTROLLER_PASS "+
            "explicitBegin=true "+
            "approachBeforeAttack=true "+
            "oneStep=true "+
            "movementNoCadenceAdvance=true "+
            "inRangeCadence=true "+
            "canonicalDamage=true "+
            "cadenceAfterAttack=true "+
            "differentPlaneNoAttack=true "+
            "deadTargetRetired=true "+
            "lethalAttackRetires=true "+
            "ignoredDeadRaceNoCadence=true "+
            "staleEngagementRetired=true "+
            "reentrantApproachCancelNoMove=true "+
            "sharedClockBound=true "+
            "cadenceClockDriftAtomic=true "+
            "damageClockDriftAtomic=true "+
            "targetSelectionOwned=false "+
            "aggroOwned=false "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void explicitBeginAndSharedClock()
        throws Exception{
        Fixture f=
            new Fixture(
                "controller-explicit",
                3203,
                3200,
                0,
                3200,
                3200,
                0
            );

        try{
            NpcCombatControllerService.TickResult none=
                f.controller.tick(
                    f.npc.id,
                    0L
                );

            require(
                none.status==
                    NpcCombatControllerService.Status.NONE&&
                f.controller.size()==0&&
                f.damage.calls==0&&
                f.cadence.calls==0,
                "controller created implicit engagement"
            );

            expect(
                IllegalStateException.class,
                ()->f.controller.tick(
                    f.npc.id,
                    1L
                ),
                "controller accepted forged future world tick"
            );

            NpcCombatEngagementService.Snapshot begun=
                f.controller.begin(
                    f.npc,
                    f.player,
                    f.generation,
                    0L
                );

            require(
                begun!=null&&
                begun.attackerId.equals(
                    f.npc.id
                )&&
                begun.targetId.equals(
                    f.player.id()
                )&&
                begun.targetGeneration==
                    f.generation&&
                f.controller.size()==1,
                "explicit engagement begin"
            );
        }finally{
            f.close();
        }
    }

    private static void approachThenAttackCadence()
        throws Exception{
        Fixture f=
            new Fixture(
                "controller-loop",
                3203,
                3200,
                0,
                3200,
                3200,
                0
            );

        try{
            f.controller.begin(
                f.npc,
                f.player,
                f.generation,
                0L
            );

            NpcCombatControllerService.TickResult first=
                f.controller.tick(
                    f.npc.id,
                    0L
                );

            require(
                first.status==
                    NpcCombatControllerService.Status.APPROACHED&&
                first.approach!=null&&
                first.approach.status==
                    NpcCombatApproachService.Status.MOVED&&
                f.npc.x()==3201&&
                f.npc.y()==3200&&
                f.hp()==99&&
                f.damage.calls==0&&
                f.cadence.calls==0&&
                f.controller.get(
                    f.npc.id
                ).nextAttackTick==0L&&
                f.controller.get(
                    f.npc.id
                ).revision==0L,
                "first approach step advanced attack state"
            );

            long tick1=
                f.world.clock().advance();

            NpcCombatControllerService.TickResult second=
                f.controller.tick(
                    f.npc.id,
                    tick1
                );

            require(
                tick1==1L&&
                second.status==
                    NpcCombatControllerService.Status.APPROACHED&&
                f.npc.x()==3202&&
                f.hp()==99&&
                f.damage.calls==0&&
                f.cadence.calls==0&&
                f.controller.get(
                    f.npc.id
                ).nextAttackTick==0L,
                "second approach step advanced attack state"
            );

            long tick2=
                f.world.clock().advance();

            NpcCombatControllerService.TickResult attacked=
                f.controller.tick(
                    f.npc.id,
                    tick2
                );

            NpcCombatEngagementService.Snapshot afterAttack=
                f.controller.get(
                    f.npc.id
                );

            require(
                tick2==2L&&
                attacked.status==
                    NpcCombatControllerService.Status.ATTACKED&&
                attacked.approach!=null&&
                attacked.approach.status==
                    NpcCombatApproachService.Status.IN_RANGE&&
                attacked.cadence!=null&&
                attacked.cadence.status==
                    NpcCombatEngagementService.TickStatus.ATTACKED&&
                f.npc.x()==3202&&
                f.hp()==89&&
                f.damage.calls==1&&
                f.cadence.calls==1&&
                afterAttack!=null&&
                afterAttack.nextAttackTick==5L&&
                afterAttack.revision==1L,
                "in-range attack/cadence composition"
            );

            long tick3=
                f.world.clock().advance();

            NpcCombatControllerService.TickResult waiting=
                f.controller.tick(
                    f.npc.id,
                    tick3
                );

            require(
                tick3==3L&&
                waiting.status==
                    NpcCombatControllerService.Status.WAITING&&
                f.hp()==89&&
                f.damage.calls==1&&
                f.cadence.calls==1&&
                f.controller.get(
                    f.npc.id
                ).nextAttackTick==5L,
                "pre-cadence in-range tick attacked"
            );

            long tick4=
                f.world.clock().advance();

            require(
                f.controller.tick(
                    f.npc.id,
                    tick4
                ).status==
                    NpcCombatControllerService.Status.WAITING&&
                f.hp()==89&&
                f.damage.calls==1,
                "second pre-cadence tick attacked"
            );

            long tick5=
                f.world.clock().advance();

            NpcCombatControllerService.TickResult attackedAgain=
                f.controller.tick(
                    f.npc.id,
                    tick5
                );

            require(
                tick5==5L&&
                attackedAgain.status==
                    NpcCombatControllerService.Status.ATTACKED&&
                f.hp()==79&&
                f.damage.calls==2&&
                f.cadence.calls==2&&
                f.controller.get(
                    f.npc.id
                ).nextAttackTick==8L&&
                f.controller.get(
                    f.npc.id
                ).revision==2L,
                "second cadence attack"
            );
        }finally{
            f.close();
        }
    }

    private static void differentPlaneNoAttack()
        throws Exception{
        Fixture f=
            new Fixture(
                "controller-plane",
                3201,
                3200,
                1,
                3200,
                3200,
                0
            );

        try{
            f.controller.begin(
                f.npc,
                f.player,
                f.generation,
                0L
            );

            NpcCombatControllerService.TickResult result=
                f.controller.tick(
                    f.npc.id,
                    0L
                );

            require(
                result.status==
                    NpcCombatControllerService.Status.DIFFERENT_PLANE&&
                f.npc.x()==3200&&
                f.npc.y()==3200&&
                f.hp()==99&&
                f.damage.calls==0&&
                f.cadence.calls==0&&
                f.controller.get(
                    f.npc.id
                ).revision==0L,
                "different-plane controller attacked/moved"
            );
        }finally{
            f.close();
        }
    }

    private static void deadTargetRetiredBeforeApproach()
        throws Exception{
        Fixture f=
            new Fixture(
                "controller-dead-before",
                3203,
                3200,
                0,
                3200,
                3200,
                0
            );

        try{
            PlayerLifecycleService.DamageResult lethal=
                new PlayerLifecycleService(
                    f.player
                ).applyDamage(
                    999,
                    0L,
                    "CONTROLLER_DEAD_BEFORE_FIXTURE"
                );

            require(
                lethal.died&&
                f.player.lifecycle().dead(),
                "dead-before fixture did not kill target"
            );

            f.controller.begin(
                f.npc,
                f.player,
                f.generation,
                0L
            );

            NpcCombatControllerService.TickResult result=
                f.controller.tick(
                    f.npc.id,
                    0L
                );

            require(
                result.status==
                    NpcCombatControllerService.Status.TARGET_DEAD&&
                f.controller.size()==0&&
                f.npc.x()==3200&&
                f.npc.y()==3200&&
                f.damage.calls==0&&
                f.cadence.calls==0,
                "dead target was approached/attacked"
            );
        }finally{
            f.close();
        }
    }

    private static void lethalAttackRetiresEngagement()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "controller-lethal"
            );

        player.movement()
            .restoreAccountState(
                false,
                100,
                3201,
                3200,
                0
            );

        WorldNpc npc=
            world.npcs().spawn(
                1503,
                3200,
                3200,
                0
            );
        TrackingDamage damage=
            new TrackingDamage(
                500
            );
        TrackingCadence cadence=
            new TrackingCadence(
                3
            );
        NpcCombatControllerService controller=
            new NpcCombatControllerService(
                world,
                fixedApproach(),
                cadence,
                new NpcPlayerCombatResolutionService(
                    damage
                ),
                ORCHESTRATION_AUTHORITY,
                NpcCombatControllerService
                    .APPROACH_BEFORE_ENGAGEMENT_TICK
            );

        try{
            controller.begin(
                npc,
                player,
                generation,
                0L
            );

            NpcCombatControllerService.TickResult result=
                controller.tick(
                    npc.id,
                    0L
                );

            require(
                result.status==
                    NpcCombatControllerService.Status.ATTACKED&&
                hp(player)==0&&
                player.lifecycle().dead()&&
                damage.calls==1&&
                cadence.calls==1&&
                controller.size()==0&&
                controller.get(
                    npc.id
                )==null,
                "lethal attack retained engagement"
            );

            require(
                controller.tick(
                    npc.id,
                    0L
                ).status==
                    NpcCombatControllerService.Status.NONE&&
                damage.calls==1,
                "retired lethal engagement attacked again"
            );
        }finally{
            cleanup(world);
        }
    }

    private static void ignoredDeadRaceNoCadence()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "controller-dead-race"
            );

        player.movement()
            .restoreAccountState(
                false,
                100,
                3201,
                3200,
                0
            );

        WorldNpc npc=
            world.npcs().spawn(
                1504,
                3200,
                3200,
                0
            );
        final int[] damageCalls={0};

        NpcPlayerCombatResolutionService damage=
            new NpcPlayerCombatResolutionService(
                new NpcPlayerCombatResolutionService.DamageResolver(){
                    @Override public int resolve(
                        NpcPlayerCombatResolutionService.DamageContext context
                    ){
                        damageCalls[0]++;

                        PlayerLifecycleService.DamageResult external=
                            new PlayerLifecycleService(
                                player
                            ).applyDamage(
                                999,
                                context.worldTick,
                                "CONTROLLER_DEAD_RACE_FIXTURE"
                            );

                        if(!external.died)
                            throw new AssertionError(
                                "dead-race fixture did not kill target"
                            );

                        return 10;
                    }

                    @Override public String authority(){
                        return "CUSTOM_LOCALLAB_NPC_DAMAGE";
                    }

                    @Override public String formula(){
                        return "DEAD_RACE";
                    }
                }
            );
        TrackingCadence cadence=
            new TrackingCadence(
                3
            );
        NpcCombatControllerService controller=
            new NpcCombatControllerService(
                world,
                fixedApproach(),
                cadence,
                damage,
                ORCHESTRATION_AUTHORITY,
                NpcCombatControllerService
                    .APPROACH_BEFORE_ENGAGEMENT_TICK
            );

        try{
            controller.begin(
                npc,
                player,
                generation,
                0L
            );

            NpcCombatControllerService.TickResult result=
                controller.tick(
                    npc.id,
                    0L
                );

            require(
                result.status==
                    NpcCombatControllerService.Status.TARGET_DEAD&&
                hp(player)==0&&
                player.lifecycle().dead()&&
                damageCalls[0]==1&&
                cadence.calls==1&&
                controller.size()==0,
                "ignored-dead race advanced/retained combat"
            );
        }finally{
            cleanup(world);
        }
    }

    private static void staleTargetRetired()
        throws Exception{
        Fixture f=
            new Fixture(
                "controller-stale-target",
                3201,
                3200,
                0,
                3200,
                3200,
                0
            );

        try{
            f.controller.begin(
                f.npc,
                f.player,
                f.generation,
                0L
            );

            require(
                f.world.unregisterPlayer(
                    f.player,
                    f.generation
                ),
                "target unregister fixture"
            );

            NpcCombatControllerService.TickResult result=
                f.controller.tick(
                    f.npc.id,
                    0L
                );

            require(
                result.status==
                    NpcCombatControllerService.Status.STALE_TARGET&&
                f.controller.size()==0&&
                f.damage.calls==0&&
                f.cadence.calls==0,
                "stale target engagement not retired"
            );
        }finally{
            f.close();
        }
    }

    private static void staleAttackerRetired()
        throws Exception{
        Fixture f=
            new Fixture(
                "controller-stale-attacker",
                3201,
                3200,
                0,
                3200,
                3200,
                0
            );

        try{
            f.controller.begin(
                f.npc,
                f.player,
                f.generation,
                0L
            );

            require(
                f.world.npcs().remove(
                    f.npc.id
                ),
                "attacker removal fixture"
            );

            NpcCombatControllerService.TickResult result=
                f.controller.tick(
                    f.npc.id,
                    0L
                );

            require(
                result.status==
                    NpcCombatControllerService.Status.STALE_ATTACKER&&
                f.controller.size()==0&&
                f.damage.calls==0&&
                f.cadence.calls==0,
                "stale attacker engagement not retired"
            );
        }finally{
            f.close();
        }
    }

    private static void reentrantApproachCancelNoMove()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "controller-reentrant-cancel"
            );

        player.movement()
            .restoreAccountState(
                false,
                100,
                3203,
                3200,
                0
            );

        WorldNpc npc=
            world.npcs().spawn(
                1502,
                3200,
                3200,
                0
            );

        TrackingDamage damage=
            new TrackingDamage(
                10
            );
        TrackingCadence cadence=
            new TrackingCadence(
                3
            );
        NpcCombatControllerService[] holder=
            new NpcCombatControllerService[1];

        NpcCombatApproachService.ApproachPolicy policy=
            new NpcCombatApproachService.ApproachPolicy(){
                @Override public int stopRange(
                    NpcCombatApproachService.Context context
                ){
                    if(!holder[0].cancel(
                            npc))
                        throw new AssertionError(
                            "reentrant controller cancel failed"
                        );
                    return 1;
                }

                @Override public RouteRequest.Policy routePolicy(
                    NpcCombatApproachService.Context context
                ){
                    throw new AssertionError(
                        "route policy reached after reentrant cancel"
                    );
                }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_REENTRANT_APPROACH";
                }

                @Override public String policy(){
                    return "REENTRANT_CANCEL_TEST";
                }
            };

        holder[0]=
            new NpcCombatControllerService(
                world,
                policy,
                cadence,
                new NpcPlayerCombatResolutionService(
                    damage
                ),
                ORCHESTRATION_AUTHORITY,
                NpcCombatControllerService
                    .APPROACH_BEFORE_ENGAGEMENT_TICK
            );

        try{
            holder[0].begin(
                npc,
                player,
                generation,
                0L
            );

            NpcCombatControllerService.TickResult result=
                holder[0].tick(
                    npc.id,
                    0L
                );

            require(
                result.status==
                    NpcCombatControllerService.Status.NONE&&
                npc.x()==3200&&
                npc.y()==3200&&
                hp(player)==99&&
                holder[0].size()==0&&
                damage.calls==0&&
                cadence.calls==0,
                "reentrant approach cancellation moved/attacked"
            );
        }finally{
            cleanup(world);
        }
    }

    private static void cadenceClockDriftAtomic()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "controller-cadence-clock"
            );

        player.movement()
            .restoreAccountState(
                false,
                100,
                3201,
                3200,
                0
            );

        WorldNpc npc=
            world.npcs().spawn(
                1505,
                3200,
                3200,
                0
            );
        TrackingDamage damage=
            new TrackingDamage(
                10
            );
        final int[] cadenceCalls={0};

        NpcCombatEngagementService.CadenceResolver cadence=
            new NpcCombatEngagementService.CadenceResolver(){
                @Override public int nextDelayTicks(
                    NpcCombatEngagementService.Context context
                ){
                    cadenceCalls[0]++;

                    if(cadenceCalls[0]==1){
                        long advanced=
                            world.clock().advance();

                        if(advanced!=1L)
                            throw new AssertionError(
                                "cadence clock drift fixture tick="+
                                advanced
                            );
                    }

                    return 3;
                }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_NPC_CADENCE";
                }

                @Override public String policy(){
                    return "DRIFT_ONCE_THEN_FIXED";
                }
            };

        NpcCombatControllerService controller=
            new NpcCombatControllerService(
                world,
                fixedApproach(),
                cadence,
                new NpcPlayerCombatResolutionService(
                    damage
                ),
                ORCHESTRATION_AUTHORITY,
                NpcCombatControllerService
                    .APPROACH_BEFORE_ENGAGEMENT_TICK
            );

        try{
            controller.begin(
                npc,
                player,
                generation,
                0L
            );

            NpcCombatEngagementService.Snapshot before=
                controller.get(
                    npc.id
                );

            expect(
                IllegalStateException.class,
                ()->controller.tick(
                    npc.id,
                    0L
                ),
                "cadence clock drift"
            );

            NpcCombatEngagementService.Snapshot afterFailure=
                controller.get(
                    npc.id
                );

            require(
                world.clock().tick()==1L&&
                hp(player)==99&&
                cadenceCalls[0]==1&&
                damage.calls==0&&
                afterFailure!=null&&
                afterFailure.nextAttackTick==
                    before.nextAttackTick&&
                afterFailure.revision==
                    before.revision,
                "cadence clock drift published stale attack"
            );

            NpcCombatControllerService.TickResult retry=
                controller.tick(
                    npc.id,
                    1L
                );

            require(
                retry.status==
                    NpcCombatControllerService.Status.ATTACKED&&
                hp(player)==89&&
                cadenceCalls[0]==2&&
                damage.calls==1&&
                controller.get(
                    npc.id
                ).nextAttackTick==4L&&
                controller.get(
                    npc.id
                ).revision==1L,
                "cadence clock drift retry did not attack once"
            );
        }finally{
            cleanup(world);
        }
    }

    private static void damageClockDriftAtomic()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "controller-damage-clock"
            );

        player.movement()
            .restoreAccountState(
                false,
                100,
                3201,
                3200,
                0
            );

        WorldNpc npc=
            world.npcs().spawn(
                1506,
                3200,
                3200,
                0
            );
        final int[] damageCalls={0};

        NpcPlayerCombatResolutionService.DamageResolver damage=
            new NpcPlayerCombatResolutionService.DamageResolver(){
                @Override public int resolve(
                    NpcPlayerCombatResolutionService.DamageContext context
                ){
                    damageCalls[0]++;

                    if(damageCalls[0]==1){
                        long advanced=
                            world.clock().advance();

                        if(advanced!=1L)
                            throw new AssertionError(
                                "damage clock drift fixture tick="+
                                advanced
                            );
                    }

                    return 10;
                }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_NPC_DAMAGE";
                }

                @Override public String formula(){
                    return "DRIFT_ONCE_THEN_FIXED";
                }
            };
        TrackingCadence cadence=
            new TrackingCadence(
                3
            );
        NpcCombatControllerService controller=
            new NpcCombatControllerService(
                world,
                fixedApproach(),
                cadence,
                new NpcPlayerCombatResolutionService(
                    damage
                ),
                ORCHESTRATION_AUTHORITY,
                NpcCombatControllerService
                    .APPROACH_BEFORE_ENGAGEMENT_TICK
            );

        try{
            controller.begin(
                npc,
                player,
                generation,
                0L
            );

            NpcCombatEngagementService.Snapshot before=
                controller.get(
                    npc.id
                );

            expect(
                IllegalStateException.class,
                ()->controller.tick(
                    npc.id,
                    0L
                ),
                "damage clock drift"
            );

            NpcCombatEngagementService.Snapshot afterFailure=
                controller.get(
                    npc.id
                );

            require(
                world.clock().tick()==1L&&
                hp(player)==99&&
                cadence.calls==1&&
                damageCalls[0]==1&&
                afterFailure!=null&&
                afterFailure.nextAttackTick==
                    before.nextAttackTick&&
                afterFailure.revision==
                    before.revision,
                "damage clock drift mutated HP/cadence"
            );

            NpcCombatControllerService.TickResult retry=
                controller.tick(
                    npc.id,
                    1L
                );

            require(
                retry.status==
                    NpcCombatControllerService.Status.ATTACKED&&
                hp(player)==89&&
                cadence.calls==2&&
                damageCalls[0]==2&&
                controller.get(
                    npc.id
                ).nextAttackTick==4L&&
                controller.get(
                    npc.id
                ).revision==1L,
                "damage clock drift retry did not attack once"
            );
        }finally{
            cleanup(world);
        }
    }

    private static void authorityGuards(){
        World world=
            World.isolatedForTest(
                600L
            );

        try{
            expect(
                IllegalArgumentException.class,
                ()->new NpcCombatControllerService(
                    world,
                    fixedApproach(),
                    new TrackingCadence(3),
                    new NpcPlayerCombatResolutionService(
                        new TrackingDamage(10)
                    ),
                    "EXACT_CURRENT_CLIENT",
                    NpcCombatControllerService
                        .APPROACH_BEFORE_ENGAGEMENT_TICK
                ),
                "client orchestration authority"
            );

            expect(
                IllegalArgumentException.class,
                ()->new NpcCombatControllerService(
                    world,
                    fixedApproach(),
                    new TrackingCadence(3),
                    new NpcPlayerCombatResolutionService(
                        new TrackingDamage(10)
                    ),
                    "UNKNOWN_SERVER_AUTHORITY",
                    NpcCombatControllerService
                        .APPROACH_BEFORE_ENGAGEMENT_TICK
                ),
                "unknown orchestration authority"
            );

            expect(
                IllegalArgumentException.class,
                ()->new NpcCombatControllerService(
                    world,
                    fixedApproach(),
                    new TrackingCadence(3),
                    new NpcPlayerCombatResolutionService(
                        new TrackingDamage(10)
                    ),
                    ORCHESTRATION_AUTHORITY,
                    "INVENTED_CONTROLLER_POLICY"
                ),
                "unsupported orchestration policy"
            );
        }finally{
            world.close();
        }
    }

    private static void boundaryGuard(){
        for(Class<?> type:new Class<?>[]{
                NpcCombatControllerService.class,
                NpcCombatControllerService.TickResult.class
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
                        "projectile",
                        "gfx",
                        "animation",
                        "hitsplat",
                        "reward",
                        "drop",
                        "xp",
                        "nearest",
                        "aggro"
                    })
                    require(
                        !name.contains(
                            forbidden
                        ),
                        "unowned controller identity leaked "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }

        for(Method method:
                NpcCombatControllerService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            for(String forbidden:new String[]{
                    "packet",
                    "publish",
                    "projectile",
                    "animate",
                    "reward",
                    "drop",
                    "nearest",
                    "aggro"
                })
                require(
                    !name.contains(
                        forbidden
                    ),
                    "unowned controller behavior leaked "+
                    method.getName()
                );
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

    private static final class TrackingCadence
        implements NpcCombatEngagementService.CadenceResolver {
        final int delay;
        int calls;

        TrackingCadence(int delay){
            this.delay=delay;
        }

        @Override public int nextDelayTicks(
            NpcCombatEngagementService.Context context
        ){
            calls++;
            return delay;
        }

        @Override public String authority(){
            return "CUSTOM_LOCALLAB_NPC_CADENCE";
        }

        @Override public String policy(){
            return "FIXED_CADENCE";
        }
    }

    private static final class TrackingDamage
        implements NpcPlayerCombatResolutionService.DamageResolver {
        final int damage;
        int calls;

        TrackingDamage(int damage){
            this.damage=damage;
        }

        @Override public int resolve(
            NpcPlayerCombatResolutionService.DamageContext context
        ){
            calls++;
            return damage;
        }

        @Override public String authority(){
            return "CUSTOM_LOCALLAB_NPC_DAMAGE";
        }

        @Override public String formula(){
            return "FIXED_DAMAGE";
        }
    }

    private static final class Fixture {
        final World world=
            World.isolatedForTest(
                600L
            );
        final WorldPlayer player=
            new WorldPlayer();
        final long generation;
        final WorldNpc npc;
        final TrackingDamage damage=
            new TrackingDamage(
                10
            );
        final TrackingCadence cadence=
            new TrackingCadence(
                3
            );
        final NpcCombatControllerService controller;

        Fixture(
            String username,
            int playerX,
            int playerY,
            int playerPlane,
            int npcX,
            int npcY,
            int npcPlane
        ){
            generation=
                world.registerPlayer(
                    player,
                    username
                );

            player.movement()
                .restoreAccountState(
                    false,
                    100,
                    playerX,
                    playerY,
                    playerPlane
                );

            npc=
                world.npcs().spawn(
                    1501,
                    npcX,
                    npcY,
                    npcPlane
                );

            controller=
                new NpcCombatControllerService(
                    world,
                    fixedApproach(),
                    cadence,
                    new NpcPlayerCombatResolutionService(
                        damage
                    ),
                    ORCHESTRATION_AUTHORITY,
                    NpcCombatControllerService
                        .APPROACH_BEFORE_ENGAGEMENT_TICK
                );
        }

        int hp(){
            return NpcCombatControllerServiceTest
                .hp(
                    player
                );
        }

        void close(){
            cleanup(
                world
            );
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

    private static void cleanup(
        World world
    ){
        for(WorldPlayer current:
                world.players().snapshot())
            world.unregisterPlayer(
                current
            );

        world.close();
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(
                    failure))
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
            throw new AssertionError(
                label
            );
    }

    private NpcCombatControllerServiceTest(){}
}
