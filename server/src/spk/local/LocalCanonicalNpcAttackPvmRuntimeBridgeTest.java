package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

public final class LocalCanonicalNpcAttackPvmRuntimeBridgeTest {
    private static final String OWNER="click-pvm-owner";

    public static void main(String[] args)throws Exception{
        int definition=attackDefinition();

        unconfiguredLeavesDeadCanonical(definition);
        configuredRuntimeFinalizes(definition);
        preTeardownFinalizationRetry(definition);
        unexpectedErrorPropagates(definition);

        System.out.println(
            "CANONICAL_NPC_ATTACK_PVM_RUNTIME_BRIDGE_PASS "+
            "optionalRuntime=true "+
            "exactWorldRuntime=true "+
            "lethalFinalizes=true "+
            "hitBeforeTeardown=true "+
            "dropsSettled=true "+
            "liveLootPending=true "+
            "foreignNpcUntouched=true "+
            "policyNeutral=true"
        );
        System.out.println(
            "CANONICAL_NPC_ATTACK_PVM_FINALIZATION_RETRY_PASS "+
            "preTeardownFailureRetained=true "+
            "noSessionThrow=true "+
            "deadRetry=true "+
            "noSecondDamage=true "+
            "noSecondHitPacket=true "+
            "terminalOnce=true "+
            "lootOnce=true "+
            "foreignUnchanged=true "+
            "errorPropagates=true"
        );
    }

    private static void unconfiguredLeavesDeadCanonical(
        int definition
    )throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "killer"
            );

        player.movement().restoreAccountState(
            false,
            100,
            3087,
            3495,
            0
        );

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                player.id()
            );

        ByteArrayOutputStream relayBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter relayWriter=
            writer(relayBytes);

        WorldNpc target=null;

        try{
            SharedNpcWorldRelay.register(
                relayWriter,
                world,
                player,
                npcs,
                player.movement()
            );

            target=
                world.npcs().spawn(
                    definition,
                    3088,
                    3495,
                    0
                );

            world.npcLifecycle().register(
                target,
                10,
                "CUSTOM_LOCALLAB_OPTIONAL_RUNTIME_HP"
            );

            SharedNpcWorldRelay.trackCanonicalNpc(
                world,
                target
            );
            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity view=
                requireView(
                    npcs,
                    target
                );

            final int[] finalizerCalls={0};
            final Object[] finalizerResult={null};

            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    player,
                    ()->generation,
                    player.equipment(),
                    player.combatStyles(),
                    npcs,
                    (npc,expectedGeneration)->{},
                    npc->{
                        finalizerCalls[0]++;
                        finalizerResult[0]=
                            world.finalizeMonsterSpawnerPvmIfOwned(
                                npc
                            );
                    }
                );

            ByteArrayOutputStream hitBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result result=
                handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(hitBytes)
                );

            NpcLifecycleService.Snapshot dead=
                world.npcLifecycle().get(
                    target.id
                );

            require(
                result!=null&&
                result.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                result.newlyDied&&
                result.hitpointsAfter==0&&
                hitBytes.size()>0,
                "unconfigured lethal click"
            );

            require(
                finalizerCalls[0]==1&&
                finalizerResult[0]==null&&
                world.npcs().byId(target.id)==target&&
                dead!=null&&dead.dead()&&
                world.groundItems().size()==0,
                "unconfigured World unexpectedly finalized NPC"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                relayWriter
            );

            if(target!=null){
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,
                    target.id
                );
                world.npcLifecycle()
                    .unregisterExact(
                        target
                    );
                world.npcs().remove(
                    target.id
                );
            }

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void configuredRuntimeFinalizes(
        int definition
    )throws Exception{
        World world=World.isolatedForTest(600L);
        World other=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "killer"
            );

        player.movement().restoreAccountState(
            false,
            100,
            3087,
            3495,
            0
        );

        RuntimeGraph graph=
            new RuntimeGraph(
                world,
                definition,
                OWNER
            );
        RuntimeGraph alternate=
            new RuntimeGraph(
                world,
                definition,
                "click-pvm-alternate"
            );
        RuntimeGraph foreignWorldGraph=
            new RuntimeGraph(
                other,
                definition,
                "click-pvm-foreign-world"
            );

        expect(
            IllegalArgumentException.class,
            ()->world.installMonsterSpawnerPvmRuntime(
                foreignWorldGraph.runtime
            ),
            "cross-World runtime registration"
        );

        world.installMonsterSpawnerPvmRuntime(
            graph.runtime
        );
        world.installMonsterSpawnerPvmRuntime(
            graph.runtime
        );

        expect(
            IllegalStateException.class,
            ()->world.installMonsterSpawnerPvmRuntime(
                alternate.runtime
            ),
            "distinct runtime replacement"
        );

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                player.id()
            );

        ByteArrayOutputStream relayBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter relayWriter=
            writer(relayBytes);

        WorldNpc foreign=null;

        try{
            SharedNpcWorldRelay.register(
                relayWriter,
                world,
                player,
                npcs,
                player.movement()
            );

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime.spawnAndBind(
                    OWNER,
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc target=
                spawned.spawn.combat.spawn.npc;

            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity view=
                requireView(
                    npcs,
                    target
                );

            final int[] finalizerCalls={0};
            final MonsterSpawnerPvmRuntime.FinalizeResult[]
                terminal={null};
            ByteArrayOutputStream hitBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    player,
                    ()->generation,
                    player.equipment(),
                    player.combatStyles(),
                    npcs,
                    (npc,expectedGeneration)->{},
                    npc->{
                        require(
                            hitBytes.size()>0,
                            "terminal teardown ran before hit packet publication"
                        );

                        finalizerCalls[0]++;
                        terminal[0]=
                            world.finalizeMonsterSpawnerPvmIfOwned(
                                npc
                            );
                    }
                );

            LocalCanonicalNpcAttackHandler.Result lethal=
                handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(hitBytes)
                );

            GroundItem loot=
                world.groundItems().findOwned(
                    995,
                    3088,
                    3495,
                    0,
                    "killer"
                );

            require(
                lethal!=null&&
                lethal.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                lethal.newlyDied&&
                lethal.hitpointsAfter==0&&
                finalizerCalls[0]==1,
                "runtime-owned lethal click"
            );

            require(
                terminal[0]!=null&&
                terminal[0].status==
                    MonsterSpawnerPvmRuntime
                        .FinalizeStatus.FINALIZED&&
                graph.runtime.get(target.id)==null&&
                graph.runtime.size()==0&&
                world.npcs().byId(target.id)==null&&
                world.npcLifecycle().get(target.id)==null&&
                graph.binder.get(target.id)==null&&
                graph.dropCalls==1&&
                loot!=null&&
                loot.amount==5,
                "configured runtime did not terminalize exactly once"
            );

            List<WorldGroundItemPresentationEvents.Event>
                pending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            player.id(),
                            generation,
                            System.currentTimeMillis()
                        );

            require(
                pending.size()==1&&
                pending.get(0).itemId==995&&
                pending.get(0).newAmount==5,
                "settled owner loot was not pending live presentation"
            );

            foreign=
                world.npcs().spawn(
                    definition,
                    3088,
                    3495,
                    0
                );

            world.npcLifecycle().register(
                foreign,
                10,
                "CUSTOM_LOCALLAB_FOREIGN_CANONICAL_HP"
            );

            SharedNpcWorldRelay.trackCanonicalNpc(
                world,
                foreign
            );
            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity foreignView=
                requireView(
                    npcs,
                    foreign
                );

            final MonsterSpawnerPvmRuntime.FinalizeResult[]
                foreignTerminal={null};
            ByteArrayOutputStream foreignHitBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler foreignHandler=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    player,
                    ()->generation,
                    player.equipment(),
                    player.combatStyles(),
                    npcs,
                    (npc,expectedGeneration)->{},
                    npc->
                        foreignTerminal[0]=
                            world.finalizeMonsterSpawnerPvmIfOwned(
                                npc
                            )
                );

            LocalCanonicalNpcAttackHandler.Result foreignHit=
                foreignHandler.handle(
                    new NpcAction(
                        72,
                        foreignView.sceneIndex
                    ),
                    foreignView,
                    writer(foreignHitBytes)
                );

            NpcLifecycleService.Snapshot foreignDead=
                world.npcLifecycle().get(
                    foreign.id
                );

            require(
                foreignHit.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                foreignHit.newlyDied&&
                foreignTerminal[0]!=null&&
                foreignTerminal[0].status==
                    MonsterSpawnerPvmRuntime
                        .FinalizeStatus.NOT_OWNED&&
                world.npcs().byId(foreign.id)==foreign&&
                foreignDead!=null&&
                foreignDead.dead()&&
                graph.dropCalls==1&&
                world.groundItems().findOwned(
                    995,
                    3088,
                    3495,
                    0,
                    "killer"
                ).amount==5,
                "configured runtime mutated foreign canonical NPC"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                relayWriter
            );

            if(foreign!=null){
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,
                    foreign.id
                );

                if(world.npcLifecycle().get(
                        foreign.id
                    )!=null)
                    world.npcLifecycle()
                        .unregisterExact(
                            foreign
                        );

                world.npcs().remove(
                    foreign.id
                );
            }

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            other.close();
            world.close();
        }
    }

    private static void preTeardownFinalizationRetry(
        int definition
    )throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "killer"
            );

        player.movement().restoreAccountState(
            false,
            100,
            3087,
            3495,
            0
        );

        RuntimeGraph graph=
            new RuntimeGraph(
                world,
                definition,
                "click-pvm-retry-owner",
                DropFailureMode.RUNTIME_ONCE
            );

        world.installMonsterSpawnerPvmRuntime(
            graph.runtime
        );

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                player.id()
            );

        ByteArrayOutputStream relayBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter relayWriter=
            writer(relayBytes);

        try{
            SharedNpcWorldRelay.register(
                relayWriter,
                world,
                player,
                npcs,
                player.movement()
            );

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime.spawnAndBind(
                    "click-pvm-retry-owner",
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc target=
                spawned.spawn.combat.spawn.npc;

            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity view=
                requireView(
                    npcs,
                    target
                );

            final int[] finalizerCalls={0};
            final MonsterSpawnerPvmRuntime.FinalizeResult[]
                terminal={null};

            ByteArrayOutputStream lethalBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    player,
                    ()->generation,
                    player.equipment(),
                    player.combatStyles(),
                    npcs,
                    (npc,expectedGeneration)->{},
                    npc->{
                        finalizerCalls[0]++;
                        terminal[0]=
                            world.finalizeMonsterSpawnerPvmIfOwned(
                                npc
                            );
                    }
                );

            LocalCanonicalNpcAttackHandler.Result lethal=
                handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(lethalBytes)
                );

            MonsterSpawnerPvmRuntime.Snapshot pending=
                graph.runtime.get(
                    target.id
                );
            NpcLifecycleService.Snapshot dead=
                world.npcLifecycle().get(
                    target.id
                );

            require(
                lethal.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                lethal.newlyDied&&
                lethal.hitpointsAfter==0&&
                lethalBytes.size()>0&&
                finalizerCalls[0]==1&&
                terminal[0]!=null&&
                terminal[0].status==
                    MonsterSpawnerPvmRuntime
                        .FinalizeStatus.FINALIZATION_PENDING,
                "pre-teardown failure escaped lethal bridge"
            );

            require(
                pending!=null&&
                pending.state==
                    MonsterSpawnerPvmRuntime
                        .State.FINALIZATION_PENDING&&
                pending.deathTick!=null&&
                dead!=null&&dead.dead()&&
                world.npcs().byId(target.id)==target&&
                graph.binder.get(target.id)!=null&&
                graph.dropAttempts==1&&
                graph.dropCalls==0&&
                world.groundItems().size()==0,
                "retryable dead runtime state"
            );

            long cadenceDue=
                handler.nextAllowedAttackTick();
            ByteArrayOutputStream retryBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result retry=
                handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(retryBytes)
                );

            GroundItem loot=
                world.groundItems().findOwned(
                    995,
                    3088,
                    3495,
                    0,
                    "killer"
                );

            require(
                retry.status==
                    LocalCanonicalNpcAttackHandler.Status.TARGET_DEAD&&
                !retry.newlyDied&&
                retry.appliedDamage==0&&
                retryBytes.size()==0&&
                handler.nextAllowedAttackTick()==cadenceDue&&
                finalizerCalls[0]==2&&
                terminal[0]!=null&&
                terminal[0].status==
                    MonsterSpawnerPvmRuntime
                        .FinalizeStatus.FINALIZED,
                "dead-target finalization retry"
            );

            require(
                graph.runtime.get(target.id)==null&&
                graph.runtime.size()==0&&
                world.npcs().byId(target.id)==null&&
                world.npcLifecycle().get(target.id)==null&&
                graph.binder.get(target.id)==null&&
                graph.dropAttempts==2&&
                graph.dropCalls==1&&
                graph.drops.size()==1&&
                loot!=null&&loot.amount==5,
                "retry terminalization/loot exactly once"
            );

            List<WorldGroundItemPresentationEvents.Event>
                pendingLoot=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            player.id(),
                            generation,
                            System.currentTimeMillis()
                        );

            require(
                pendingLoot.size()==1&&
                pendingLoot.get(0).itemId==995&&
                pendingLoot.get(0).newAmount==5,
                "retry loot live event"
            );

            ByteArrayOutputStream staleBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler.Result stale=
                handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(staleBytes)
                );

            require(
                stale.status==
                    LocalCanonicalNpcAttackHandler.Status.STALE_CANONICAL&&
                staleBytes.size()==0&&
                finalizerCalls[0]==2&&
                graph.dropAttempts==2&&
                graph.dropCalls==1,
                "post-terminal stale click duplicated retry"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                relayWriter
            );

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void unexpectedErrorPropagates(
        int definition
    )throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "killer"
            );

        player.movement().restoreAccountState(
            false,
            100,
            3087,
            3495,
            0
        );

        RuntimeGraph graph=
            new RuntimeGraph(
                world,
                definition,
                "click-pvm-error-owner",
                DropFailureMode.ERROR_ONCE
            );

        world.installMonsterSpawnerPvmRuntime(
            graph.runtime
        );

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                player.id()
            );

        ByteArrayOutputStream relayBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter relayWriter=
            writer(relayBytes);

        try{
            SharedNpcWorldRelay.register(
                relayWriter,
                world,
                player,
                npcs,
                player.movement()
            );

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime.spawnAndBind(
                    "click-pvm-error-owner",
                    "killer",
                    3088,
                    3495,
                    0
                );

            WorldNpc target=
                spawned.spawn.combat.spawn.npc;

            SharedNpcWorldRelay.syncRemotePets(
                relayWriter
            );

            NpcEntity view=
                requireView(
                    npcs,
                    target
                );

            ByteArrayOutputStream hitBytes=
                new ByteArrayOutputStream();

            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,
                    player,
                    ()->generation,
                    player.equipment(),
                    player.combatStyles(),
                    npcs,
                    (npc,expectedGeneration)->{},
                    npc->
                        world.finalizeMonsterSpawnerPvmIfOwned(
                            npc
                        )
                );

            expect(
                AssertionError.class,
                ()->handler.handle(
                    new NpcAction(
                        72,
                        view.sceneIndex
                    ),
                    view,
                    writer(hitBytes)
                ),
                "unexpected Error propagation"
            );

            MonsterSpawnerPvmRuntime.Snapshot runtime=
                graph.runtime.get(
                    target.id
                );
            NpcLifecycleService.Snapshot dead=
                world.npcLifecycle().get(
                    target.id
                );

            require(
                hitBytes.size()>0&&
                runtime!=null&&
                runtime.state==
                    MonsterSpawnerPvmRuntime.State.ACTIVE&&
                dead!=null&&dead.dead()&&
                world.npcs().byId(target.id)==target&&
                graph.dropAttempts==1&&
                graph.dropCalls==0&&
                world.groundItems().size()==0,
                "Error path was converted into retryable semantic status"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                relayWriter
            );

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private enum DropFailureMode {
        NONE,
        RUNTIME_ONCE,
        ERROR_ONCE
    }

    private static final class RuntimeGraph {
        final World world;
        final MonsterSpawnerService spawner;
        final NpcLifecycleService lifecycle;
        final NpcCombatRuntimeBinder binder;
        final MonsterSpawnerCombatBindingService combat;
        final MonsterSpawnerNpcLifecycleBindingService
            lifecycleBinding;
        final NpcDropResolutionService drops;
        final MonsterSpawnerNpcDeathFinalizationService
            finalizer;
        final NpcDropGroundSettlementService settlement;
        final MonsterSpawnerPvmRuntime runtime;

        final DropFailureMode dropFailureMode;
        int dropAttempts;
        int dropCalls;

        RuntimeGraph(
            World world,
            int definition,
            String owner
        ){
            this(
                world,
                definition,
                owner,
                DropFailureMode.NONE
            );
        }

        RuntimeGraph(
            World world,
            int definition,
            String owner,
            DropFailureMode dropFailureMode
        ){
            this.world=world;
            this.dropFailureMode=
                java.util.Objects.requireNonNull(
                    dropFailureMode,
                    "dropFailureMode"
                );
            this.lifecycle=world.npcLifecycle();

            spawner=
                new MonsterSpawnerService(
                    world.npcs()
                );

            spawner.replaceCatalog(
                Collections.singletonList(
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "click-pvm",
                        definition
                    )
                ),
                "CUSTOM_LOCALLAB_CLICK_PVM_CATALOG"
            );
            spawner.openSession(
                owner,
                "CUSTOM_LOCALLAB_CLICK_PVM_SPAWNER"
            );
            spawner.selectRow(
                owner,
                0
            );
            spawner.activate(
                owner,
                1
            );

            binder=
                new NpcCombatRuntimeBinder(
                    world,
                    context->combatPlan()
                );

            combat=
                new MonsterSpawnerCombatBindingService(
                    world,
                    spawner,
                    binder
                );

            lifecycleBinding=
                new MonsterSpawnerNpcLifecycleBindingService(
                    world,
                    combat,
                    lifecycle,
                    context->
                        new MonsterSpawnerNpcLifecycleBindingService
                            .LifecyclePlan(
                                "click-pvm-hp",
                                10,
                                "CUSTOM_LOCALLAB_CLICK_PVM_HP"
                            )
                );

            drops=
                new NpcDropResolutionService(
                    world.npcs(),
                    lifecycle,
                    new NpcDropResolutionService.DropResolver(){
                        @Override public List<
                            NpcDropResolutionService.Drop
                        > resolve(
                            NpcDropResolutionService
                                .DeathContext context
                        ){
                            dropAttempts++;

                            if(dropAttempts==1&&
                               dropFailureMode==
                                   DropFailureMode.RUNTIME_ONCE)
                                throw new IllegalStateException(
                                    "TEST_PRE_TEARDOWN_RUNTIME_FAILURE"
                                );

                            if(dropAttempts==1&&
                               dropFailureMode==
                                   DropFailureMode.ERROR_ONCE)
                                throw new AssertionError(
                                    "TEST_PRE_TEARDOWN_ERROR"
                                );

                            dropCalls++;

                            return Collections.singletonList(
                                new NpcDropResolutionService.Drop(
                                    995,
                                    5
                                )
                            );
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB_CLICK_PVM_DROP";
                        }
                    }
                );

            finalizer=
                new MonsterSpawnerNpcDeathFinalizationService(
                    world,
                    combat,
                    lifecycle,
                    drops
                );

            settlement=
                new NpcDropGroundSettlementService(
                    world,
                    "CUSTOM_LOCALLAB_CLICK_PVM_SETTLEMENT",
                    NpcDropGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            runtime=
                new MonsterSpawnerPvmRuntime(
                    world,
                    lifecycleBinding,
                    finalizer,
                    settlement
                );
        }
    }

    private static NpcCombatRuntimeBinder.BehaviorPlan
        combatPlan(){
        return new NpcCombatRuntimeBinder.BehaviorPlan(
            "click-pvm-combat",
            "CUSTOM_LOCALLAB_CLICK_PVM_PROFILE",
            context->
                NpcTargetAcquisitionService.Decision
                    .ineligible(),
            "CUSTOM_LOCALLAB_CLICK_PVM_TARGET",
            "NONE",
            new NpcCombatApproachService.ApproachPolicy(){
                @Override public int stopRange(
                    NpcCombatApproachService.Context context
                ){ return 1; }

                @Override public RouteRequest.Policy routePolicy(
                    NpcCombatApproachService.Context context
                ){
                    return RouteRequest.Policy
                        .WORLD_STATIC_AUTHORITY;
                }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_CLICK_PVM_APPROACH";
                }

                @Override public String policy(){
                    return "FIXED_RANGE_1";
                }
            },
            new NpcCombatEngagementService.CadenceResolver(){
                @Override public int nextDelayTicks(
                    NpcCombatEngagementService.Context context
                ){ return 3; }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_CLICK_PVM_CADENCE";
                }

                @Override public String policy(){
                    return "FIXED_3";
                }
            },
            new NpcPlayerCombatResolutionService.DamageResolver(){
                @Override public int resolve(
                    NpcPlayerCombatResolutionService.DamageContext context
                ){ return 1; }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_CLICK_PVM_NPC_DAMAGE";
                }

                @Override public String formula(){
                    return "FIXED_1";
                }
            },
            context->context.worldTick+1L,
            "CUSTOM_LOCALLAB_CLICK_PVM_CONTROLLER",
            "CUSTOM_LOCALLAB_CLICK_PVM_AI"
        );
    }

    private static int attackDefinition(){
        for(int definition=0;
            definition<16384;
            definition++){
            NpcEntity candidate;

            try{
                candidate=
                    new NpcEntity(
                        100,
                        definition,
                        3088,
                        3495
                    );
            }catch(IllegalArgumentException ignored){
                continue;
            }

            NpcInteractionRouter.Route route=
                NpcInteractionRouter.resolve(
                    new NpcAction(
                        72,
                        candidate.sceneIndex
                    ),
                    candidate
                );

            if(route.service==
                    NpcInteractionRouter.Service.ATTACK&&
               !CombatTargetRepository
                    .isCombatDummy(
                        definition
                    ))
                return definition;
        }

        throw new AssertionError(
            "no non-dummy exact option-2 Attack definition"
        );
    }

    private static NpcEntity requireView(
        NpcRegistry npcs,
        WorldNpc npc
    ){
        NpcEntity view=
            npcs.canonical(
                npc.id
            );

        require(
            view!=null&&
            npc.id.equals(
                view.canonicalId()
            ),
            "canonical projection missing id="+
            npc.id
        );

        return view;
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                new int[4]
            )
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

    private LocalCanonicalNpcAttackPvmRuntimeBridgeTest(){}
}
