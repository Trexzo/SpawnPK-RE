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

            LocalMonsterSpawnerUiHandler.ActivationBudgetResolver
                activationBudget=
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
                    };

            LocalMonsterSpawnerUiHandler.SelectedNpcLabelResolver
                selectedLabel=
                    new LocalMonsterSpawnerUiHandler.SelectedNpcLabelResolver(){
                        @Override public String label(
                            MonsterSpawnerService.CatalogEntry entry
                        ){
                            return "NPC-"+entry.definitionId;
                        }

                        @Override public String authority(){
                            return CATALOG;
                        }
                    };

            boolean foreignServiceRejected=false;
            try{
                new LocalMonsterSpawnerActivationRuntime(
                    world,
                    new MonsterSpawnerService(
                        new WorldNpcRegistry()
                    ),
                    f.runtime,
                    f.executor,
                    POLICY,
                    activationBudget,
                    selectedLabel
                );
            }catch(IllegalArgumentException expected){
                foreignServiceRejected=true;
            }

            require(
                foreignServiceRejected,
                "shared activation runtime accepted foreign service registry"
            );

            boolean clientAuthorityRejected=false;
            try{
                new LocalMonsterSpawnerActivationRuntime(
                    world,
                    f.spawner,
                    f.runtime,
                    f.executor,
                    "EXACT_CURRENT_CLIENT",
                    activationBudget,
                    selectedLabel
                );
            }catch(IllegalArgumentException expected){
                clientAuthorityRejected=true;
            }

            require(
                clientAuthorityRejected,
                "shared activation runtime accepted client session authority"
            );

            LocalMonsterSpawnerActivationRuntime factory=
                new LocalMonsterSpawnerActivationRuntime(
                    world,
                    f.spawner,
                    f.runtime,
                    f.executor,
                    POLICY,
                    activationBudget,
                    selectedLabel
                );

            LocalMonsterSpawnerUiHandler resolved=
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    factory,
                    world,
                    player,
                    generation,
                    OWNER
                );

            require(
                resolved!=null&&
                resolved.isBoundToOwner(OWNER)&&
                resolved.isBoundTo(world),
                "late-bound shared activation runtime UI adapter"
            );

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
                afterSpawn.active&&
                afterSpawn.remainingSpawnBudget==1&&
                afterSpawn.spawnedNpcIds.size()==1&&
                world.npcs().size()==1&&
                f.runtime.size()==1,
                "ACTIVATED callback did not execute exactly one atomic spawn"
            );

            WorldNpc npc=
                world.npcs().byId(
                    afterSpawn.spawnedNpcIds.get(0)
                );

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
                !deactivated.active&&
                deactivated.remainingSpawnBudget==0&&
                world.npcs().size()==1&&
                f.runtime.size()==1,
                "DEACTIVATED callback triggered spawn"
            );

            LocalSession.notifyMonsterSpawnerSessionClosed(
                factory,
                world,
                player,
                generation,
                OWNER
            );

            require(
                f.spawner.getSession(OWNER)!=null&&
                f.spawner.getSession(OWNER).tracks(npc.id),
                "tracked session retired on LocalSession close"
            );

            final String reconnectOwner=
                "session-pvm-reconnect";
            WorldPlayer reconnectPlayer=
                new WorldPlayer();
            long reconnectGeneration=
                world.registerPlayer(
                    reconnectPlayer,
                    reconnectOwner
                );

            LocalMonsterSpawnerUiHandler firstReconnect=
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    factory,
                    world,
                    reconnectPlayer,
                    reconnectGeneration,
                    reconnectOwner
                );

            require(
                firstReconnect!=null&&
                f.spawner.getSession(reconnectOwner)!=null,
                "shared runtime did not open reconnect fixture session"
            );

            LocalSession.notifyMonsterSpawnerSessionClosed(
                factory,
                world,
                reconnectPlayer,
                reconnectGeneration,
                reconnectOwner
            );

            require(
                f.spawner.getSession(reconnectOwner)==null,
                "idle LocalSession close did not retire shared session"
            );

            require(
                world.unregisterPlayer(
                    reconnectPlayer,
                    reconnectGeneration
                ),
                "reconnect fixture first generation unregister"
            );

            WorldPlayer replacementPlayer=
                new WorldPlayer();
            long replacementGeneration=
                world.registerPlayer(
                    replacementPlayer,
                    reconnectOwner
                );

            LocalMonsterSpawnerUiHandler reopened=
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    factory,
                    world,
                    replacementPlayer,
                    replacementGeneration,
                    reconnectOwner
                );

            require(
                reopened!=null&&
                f.spawner.getSession(reconnectOwner)!=null,
                "same owner did not reopen after idle retirement"
            );

            LocalSession.notifyMonsterSpawnerSessionClosed(
                factory,
                world,
                replacementPlayer,
                replacementGeneration,
                reconnectOwner
            );

            require(
                f.spawner.getSession(reconnectOwner)==null&&
                world.unregisterPlayer(
                    replacementPlayer,
                    replacementGeneration
                ),
                "reopened idle session cleanup"
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
                world.npcs().size()==1&&
                f.runtime.size()==1,
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
                "sharedActivationRuntime=true "+
                "worldRuntimeInstalled=true "+
                "trackedSessionRetained=true "+
                "idleSessionRetired=true "+
                "sameOwnerReconnect=true "+
                "exactGraphFence=true "+
                "serverAuthorityFence=true "+
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
