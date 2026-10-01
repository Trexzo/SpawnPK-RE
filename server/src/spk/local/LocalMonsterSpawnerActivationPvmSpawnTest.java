package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class LocalMonsterSpawnerActivationPvmSpawnTest {
    private static final String OWNER="session-pvm-owner";
    private static final String POLICY="CUSTOM_LOCALLAB_SESSION_PVM_POLICY";
    private static final String CATALOG="CUSTOM_LOCALLAB_SESSION_PVM_CATALOG";
    private static final String REQUEST="CUSTOM_LOCALLAB_SESSION_PVM_REQUEST";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,OWNER);

        try{
            Fixture f=new Fixture(world);

            LocalMonsterSpawnerUiHandler ui=
                new LocalMonsterSpawnerUiHandler(
                    f.spawner,
                    OWNER,
                    new LocalMonsterSpawnerUiHandler.ActivationBudgetResolver(){
                        @Override public int spawnBudget(
                            LocalMonsterSpawnerUiHandler.Context context
                        ){
                            require(
                                OWNER.equals(context.ownerRef)&&
                                context.session!=null&&
                                !context.session.active,
                                "activation context"
                            );
                            return 2;
                        }

                        @Override public String authority(){
                            return POLICY;
                        }
                    },
                    new LocalMonsterSpawnerUiHandler.SelectedNpcLabelResolver(){
                        @Override public String label(
                            MonsterSpawnerService.CatalogEntry entry
                        ){
                            return "NPC-"+entry.definitionId;
                        }

                        @Override public String authority(){
                            return CATALOG;
                        }
                    }
                );

            final int[] callbackCalls={0};
            final int[] executorCalls={0};
            final MonsterSpawnerPvmSpawnExecutor.Result[] spawned={null};

            LocalSession.MonsterSpawnerUiFactory factory=
                new LocalSession.MonsterSpawnerUiFactory(){
                    @Override public LocalMonsterSpawnerUiHandler create(
                        World factoryWorld,
                        WorldPlayer factoryPlayer,
                        String canonicalUsername
                    ){
                        require(
                            factoryWorld==world&&
                            factoryPlayer==player&&
                            OWNER.equals(canonicalUsername),
                            "factory exact context"
                        );
                        return ui;
                    }

                    @Override public void onCommittedResult(
                        World callbackWorld,
                        WorldPlayer callbackPlayer,
                        String canonicalUsername,
                        LocalMonsterSpawnerUiHandler.Result result,
                        ServerPacketWriter writer,
                        String tag
                    )throws Exception{
                        callbackCalls[0]++;

                        require(
                            callbackWorld==world&&
                            callbackPlayer==player&&
                            OWNER.equals(canonicalUsername),
                            "callback exact context"
                        );

                        if(result.status==
                                LocalMonsterSpawnerUiHandler.Status.ACTIVATED){
                            executorCalls[0]++;
                            spawned[0]=f.executor.execute(canonicalUsername);
                        }
                    }
                };

            LocalMonsterSpawnerUiHandler resolved=
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    factory,
                    world,
                    player,
                    OWNER
                );

            require(resolved==ui,"late-bound UI adapter");

            Bridge bridge=new Bridge(
                factory,
                world,
                player,
                generation
            );

            LocalSessionUiActionHandler routed=
                new LocalSessionUiActionHandler(
                    player,
                    new NativeItemLibraryService(),
                    new DevControlCenter(),
                    player.bank(),
                    new LocalCompCapeCustomizeHandler(
                        player.bank(),
                        player.playerState()
                    ),
                    petDialogs(player),
                    new LocalGameplayWidgetHandler(
                        player.prayers(),
                        player.playerState(),
                        player.equipment(),
                        player.combatStyles(),
                        player.magic(),
                        player.bank()
                    ),
                    player.movement(),
                    true,
                    player.equipment(),
                    resolved,
                    bridge
                );

            ByteArrayOutputStream wire=new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(new int[]{1,2,3,4})
                );

            routed.handleWidget(
                MonsterSpawnerPresentation.rowWidget(0),
                writer,
                "[activation-pvm] "
            );

            require(
                bridge.forwarded==1&&
                callbackCalls[0]==1&&
                executorCalls[0]==0&&
                world.npcs().size()==0&&
                f.runtime.size()==0,
                "row selection spawned or skipped callback"
            );

            routed.handleWidget(
                MonsterSpawnerPresentation.TOGGLE_WIDGET,
                writer,
                "[activation-pvm] "
            );

            MonsterSpawnerService.SessionSnapshot afterSpawn=
                f.spawner.getSession(OWNER);

            require(
                bridge.forwarded==2&&
                callbackCalls[0]==2&&
                executorCalls[0]==1&&
                spawned[0]!=null&&
                spawned[0].status==
                    MonsterSpawnerPvmSpawnExecutor.Status.SPAWNED&&
                afterSpawn.active&&
                afterSpawn.remainingSpawnBudget==1&&
                afterSpawn.spawnedNpcIds.size()==1&&
                world.npcs().size()==1&&
                f.runtime.size()==1,
                "ACTIVATED callback did not execute exactly one atomic spawn"
            );

            WorldNpc npc=spawned[0].spawn.spawn.combat.spawn.npc;

            require(
                afterSpawn.tracks(npc.id)&&
                world.npcs().byId(npc.id)==npc&&
                world.npcLifecycle().get(npc.id)!=null&&
                f.runtime.get(npc.id)!=null,
                "spawn was not runtime-owned before callback return"
            );

            routed.handleWidget(
                152,
                writer,
                "[activation-pvm] "
            );

            require(
                executorCalls[0]==1&&
                world.npcs().size()==1&&
                f.runtime.size()==1,
                "unrelated widget triggered second spawn"
            );

            routed.handleWidget(
                MonsterSpawnerPresentation.TOGGLE_WIDGET,
                writer,
                "[activation-pvm] "
            );

            MonsterSpawnerService.SessionSnapshot deactivated=
                f.spawner.getSession(OWNER);

            require(
                bridge.forwarded==3&&
                callbackCalls[0]==3&&
                executorCalls[0]==1&&
                !deactivated.active&&
                deactivated.remainingSpawnBudget==0&&
                world.npcs().size()==1&&
                f.runtime.size()==1,
                "DEACTIVATED callback triggered spawn"
            );

            LocalSession.forwardMonsterSpawnerUiResult(
                null,
                world,
                player,
                generation,
                OWNER,
                bridge.last,
                writer,
                "[activation-pvm] "
            );

            require(
                executorCalls[0]==1&&
                callbackCalls[0]==3,
                "absent callback changed spawn behavior"
            );

            System.out.println(
                "LOCAL_SESSION_MONSTER_SPAWNER_PVM_ACTIVATION_PASS "+
                "rowNoSpawn=true "+
                "activatedCallbackOnce=true "+
                "canonicalSpawn=true "+
                "budgetConsumedOnce=true "+
                "runtimeOwnedBeforeReturn=true "+
                "unrelatedNoSpawn=true "+
                "deactivatedNoSpawn=true "+
                "absentCallbackNoop=true "+
                "placementCallerOwned=true "+
                "recipientCallerOwned=true "+
                "catalogCallerOwned=true "+
                "budgetCallerOwned=true "+
                "combatCallerOwned=true "+
                "dropCallerOwned=true "+
                "automaticCadence=false"
            );
        }finally{
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static LocalPetInventoryDialogHandler petDialogs(
        WorldPlayer player
    ){
        return new LocalPetInventoryDialogHandler(
            player.bank(),
            player.miniPets(),
            player.petState(),
            new NpcRegistry(new DevAuthorityWorkbench()),
            player.movement(),
            new PetAccessoryState()
        );
    }

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalSession.MonsterSpawnerUiFactory factory;
        final World world;
        final WorldPlayer player;
        final long generation;
        int forwarded;
        LocalMonsterSpawnerUiHandler.Result last;

        Bridge(
            LocalSession.MonsterSpawnerUiFactory factory,
            World world,
            WorldPlayer player,
            long generation
        ){
            this.factory=factory;
            this.world=world;
            this.player=player;
            this.generation=generation;
        }

        @Override public void saveAccount(String tag,String reason){}
        @Override public void clearDialogNumberKeys(){}
        @Override public void handleDevPanelWidget(
            int widget,
            ServerPacketWriter writer,
            String tag
        ){}
        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        ){}
        @Override public void requestLogout(){}

        @Override public void handleMonsterSpawnerResult(
            LocalMonsterSpawnerUiHandler.Result result,
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            forwarded++;
            last=result;
            LocalSession.forwardMonsterSpawnerUiResult(
                factory,
                world,
                player,
                generation,
                OWNER,
                result,
                writer,
                tag
            );
        }
    }

    private static final class Fixture {
        final World world;
        final MonsterSpawnerService spawner;
        final NpcCombatRuntimeBinder binder;
        final MonsterSpawnerPvmRuntime runtime;
        final MonsterSpawnerPvmSpawnExecutor executor;

        Fixture(World world){
            this.world=world;

            spawner=new MonsterSpawnerService(world.npcs());
            spawner.replaceCatalog(
                Collections.singletonList(
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "session-pvm",
                        1530
                    )
                ),
                CATALOG
            );
            spawner.openSession(OWNER,POLICY);

            NpcLifecycleService lifecycle=world.npcLifecycle();
            binder=
                new NpcCombatRuntimeBinder(
                    world,
                    context->combatPlan()
                );

            MonsterSpawnerCombatBindingService combat=
                new MonsterSpawnerCombatBindingService(
                    world,
                    spawner,
                    binder
                );

            MonsterSpawnerNpcLifecycleBindingService lifecycleBinding=
                new MonsterSpawnerNpcLifecycleBindingService(
                    world,
                    combat,
                    lifecycle,
                    context->
                        new MonsterSpawnerNpcLifecycleBindingService.LifecyclePlan(
                            "session-pvm-hp",
                            10,
                            "CUSTOM_LOCALLAB_SESSION_PVM_HP"
                        )
                );

            NpcDropResolutionService drops=
                new NpcDropResolutionService(
                    world.npcs(),
                    lifecycle,
                    new NpcDropResolutionService.DropResolver(){
                        @Override public List<NpcDropResolutionService.Drop>
                            resolve(
                                NpcDropResolutionService.DeathContext context
                            ){
                            return Collections.singletonList(
                                new NpcDropResolutionService.Drop(995,1)
                            );
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB_SESSION_PVM_DROP";
                        }
                    }
                );

            MonsterSpawnerNpcDeathFinalizationService finalizer=
                new MonsterSpawnerNpcDeathFinalizationService(
                    world,
                    combat,
                    lifecycle,
                    drops
                );

            NpcDropGroundSettlementService settlement=
                new NpcDropGroundSettlementService(
                    world,
                    "CUSTOM_LOCALLAB_SESSION_PVM_SETTLEMENT",
                    NpcDropGroundSettlementService.OWNER_SCOPED_DEATH_TILE
                );

            runtime=
                new MonsterSpawnerPvmRuntime(
                    world,
                    lifecycleBinding,
                    finalizer,
                    settlement
                );

            executor=
                new MonsterSpawnerPvmSpawnExecutor(
                    world,
                    spawner,
                    runtime,
                    new MonsterSpawnerPvmSpawnExecutor.RequestResolver(){
                        @Override public MonsterSpawnerPvmSpawnExecutor.Request
                            resolve(
                                MonsterSpawnerPvmSpawnExecutor.Context context
                            ){
                            require(
                                OWNER.equals(context.ownerRef)&&
                                context.session.active&&
                                context.session.remainingSpawnBudget==2&&
                                context.session.selectedDefinitionId!=null&&
                                context.session.selectedDefinitionId.intValue()==1530,
                                "spawn request context"
                            );

                            return new MonsterSpawnerPvmSpawnExecutor.Request(
                                OWNER,
                                new Tile(3088,3495,0)
                            );
                        }

                        @Override public String authority(){
                            return REQUEST;
                        }
                    }
                );
        }
    }

    private static NpcCombatRuntimeBinder.BehaviorPlan combatPlan(){
        return new NpcCombatRuntimeBinder.BehaviorPlan(
            "session-pvm-combat",
            "CUSTOM_LOCALLAB_SESSION_PVM_PROFILE",
            context->NpcTargetAcquisitionService.Decision.ineligible(),
            "CUSTOM_LOCALLAB_SESSION_PVM_TARGET",
            "NONE",
            new NpcCombatApproachService.ApproachPolicy(){
                @Override public int stopRange(
                    NpcCombatApproachService.Context context
                ){ return 1; }

                @Override public RouteRequest.Policy routePolicy(
                    NpcCombatApproachService.Context context
                ){ return RouteRequest.Policy.WORLD_STATIC_AUTHORITY; }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_SESSION_PVM_APPROACH";
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
                    return "CUSTOM_LOCALLAB_SESSION_PVM_CADENCE";
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
                    return "CUSTOM_LOCALLAB_SESSION_PVM_DAMAGE";
                }

                @Override public String formula(){
                    return "FIXED_1";
                }
            },
            context->context.worldTick+1L,
            "CUSTOM_LOCALLAB_SESSION_PVM_CONTROLLER",
            "CUSTOM_LOCALLAB_SESSION_PVM_AI"
        );
    }

    private static void require(boolean condition,String label){
        if(!condition)throw new AssertionError(label);
    }

    private LocalMonsterSpawnerActivationPvmSpawnTest(){}
}
