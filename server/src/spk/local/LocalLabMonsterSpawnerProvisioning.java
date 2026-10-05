package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Explicit LocalLab-only Monster Spawner provisioning.
 *
 * None of these gameplay values are claimed as recovered SpawnPK server
 * authority. They exist so the already-reconstructed Monster Spawner vertical
 * is reachable in the real localhost server while unknown original balance
 * remains clearly separated from exact-client/proven protocol facts.
 */
final class LocalLabMonsterSpawnerProvisioning {
    static final String CATALOG_AUTHORITY=
        "CUSTOM_LOCALLAB_MONSTER_SPAWNER_CATALOG";
    static final String SESSION_AUTHORITY=
        "CUSTOM_LOCALLAB_MONSTER_SPAWNER_SESSION";
    static final String HP_AUTHORITY=
        "CUSTOM_LOCALLAB_MONSTER_SPAWNER_HP";
    static final String DROP_AUTHORITY=
        "CUSTOM_LOCALLAB_MONSTER_SPAWNER_DROP";
    static final String SETTLEMENT_AUTHORITY=
        "CUSTOM_LOCALLAB_MONSTER_SPAWNER_SETTLEMENT";
    static final String REQUEST_AUTHORITY=
        "CUSTOM_LOCALLAB_MONSTER_SPAWNER_REQUEST";
    static final String TERMINAL_AUTHORITY=
        "LOCAL_LAB_POLICY_G1_PVM_TERMINAL_V1";

    static final int NPC_DEFINITION_ID=1530;
    static final int NPC_HITPOINTS=10;
    /*
     * Explicit G1.5 two-life session policy: activation consumes the first
     * spawn, leaving one canonical budget unit for one replacement after
     * successful death/drop/progression settlement.
     */
    static final int ACTIVATION_BUDGET=2;

    static LocalMonsterSpawnerActivationRuntime create(
        World world
    ){
        World checkedWorld=
            Objects.requireNonNull(
                world,
                "world"
            );

        MonsterSpawnerService service=
            new MonsterSpawnerService(
                checkedWorld.npcs()
            );

        List<MonsterSpawnerService.CatalogEntry>
            catalog=
                new ArrayList<>();

        for(int row=0;
            row<MonsterSpawnerService.CLIENT_ROW_COUNT;
            row++)
            catalog.add(
                new MonsterSpawnerService.CatalogEntry(
                    row,
                    "locallab:monster-spawner:placeholder:"+
                        row,
                    NPC_DEFINITION_ID
                )
            );

        service.replaceCatalog(
            catalog,
            CATALOG_AUTHORITY
        );

        NpcLifecycleService lifecycle=
            checkedWorld.npcLifecycle();

        NpcCombatRuntimeBinder binder=
            new NpcCombatRuntimeBinder(
                checkedWorld,
                context->combatPlan()
            );

        MonsterSpawnerCombatBindingService combat=
            new MonsterSpawnerCombatBindingService(
                checkedWorld,
                service,
                binder
            );

        MonsterSpawnerNpcLifecycleBindingService lifecycleBinding=
            new MonsterSpawnerNpcLifecycleBindingService(
                checkedWorld,
                combat,
                lifecycle,
                context->
                    new MonsterSpawnerNpcLifecycleBindingService.LifecyclePlan(
                        "locallab-monster-spawner-hp",
                        NPC_HITPOINTS,
                        HP_AUTHORITY
                    )
            );

        NpcDropResolutionService drops=
            new NpcDropResolutionService(
                checkedWorld.npcs(),
                lifecycle,
                new NpcDropResolutionService.DropResolver(){
                    @Override public List<NpcDropResolutionService.Drop>
                        resolve(
                            NpcDropResolutionService.DeathContext context
                        ){
                        return Collections.singletonList(
                            new NpcDropResolutionService.Drop(
                                995,
                                1
                            )
                        );
                    }

                    @Override public String authority(){
                        return DROP_AUTHORITY;
                    }
                }
            );

        MonsterSpawnerNpcDeathFinalizationService finalizer=
            new MonsterSpawnerNpcDeathFinalizationService(
                checkedWorld,
                combat,
                lifecycle,
                drops
            );

        NpcDropGroundSettlementService settlement=
            new NpcDropGroundSettlementService(
                checkedWorld,
                SETTLEMENT_AUTHORITY,
                NpcDropGroundSettlementService
                    .OWNER_SCOPED_DEATH_TILE
            );

        G1PvmProgressionService progression=
            new G1PvmProgressionService(
                checkedWorld
            );

        MonsterSpawnerPvmRuntime runtime=
            new MonsterSpawnerPvmRuntime(
                checkedWorld,
                lifecycleBinding,
                finalizer,
                settlement,
                new MonsterSpawnerPvmRuntime.TerminalPolicy(){
                    @Override public void onSettled(
                        MonsterSpawnerPvmRuntime.TerminalContext context
                    ){
                        G1PvmProgressionService.Result result=
                            progression.settle(
                                context.recipientRef,
                                context.finalization.npcId,
                                context.finalization.deathTick
                            );

                        System.out.println(
                            "[g1-pvm] G1_PVM_PROGRESSION "+
                            result+
                            " npc="+
                            context.finalization.npcId+
                            " deathTick="+
                            context.finalization.deathTick+
                            " authority="+
                            G1PvmProgressionService.AUTHORITY
                        );
                    }

                    @Override public Tile replacementTile(
                        MonsterSpawnerPvmRuntime.TerminalContext context
                    ){
                        if(!context.session.active||
                           context.session.remainingSpawnBudget<=0)
                            return null;

                        return new Tile(
                            MovementState.INITIAL_X+1,
                            MovementState.INITIAL_Y,
                            0
                        );
                    }

                    @Override public String authority(){
                        return TERMINAL_AUTHORITY;
                    }
                }
            );

        MonsterSpawnerPvmSpawnExecutor executor=
            new MonsterSpawnerPvmSpawnExecutor(
                checkedWorld,
                service,
                runtime,
                new MonsterSpawnerPvmSpawnExecutor.RequestResolver(){
                    @Override public MonsterSpawnerPvmSpawnExecutor.Request
                        resolve(
                            MonsterSpawnerPvmSpawnExecutor.Context context
                        ){
                        if(context.session.selectedDefinitionId==null||
                           context.session.selectedDefinitionId.intValue()!=
                                NPC_DEFINITION_ID)
                            throw new IllegalStateException(
                                "LocalLab Monster Spawner request received unsupported selection owner="+
                                context.ownerRef
                            );

                        return new MonsterSpawnerPvmSpawnExecutor.Request(
                            context.ownerRef,
                            new Tile(
                                MovementState.INITIAL_X+1,
                                MovementState.INITIAL_Y,
                                0
                            )
                        );
                    }

                    @Override public String authority(){
                        return REQUEST_AUTHORITY;
                    }
                }
            );

        return new LocalMonsterSpawnerActivationRuntime(
            checkedWorld,
            service,
            runtime,
            executor,
            SESSION_AUTHORITY,
            new LocalMonsterSpawnerUiHandler
                .ActivationBudgetResolver(){
                @Override public int spawnBudget(
                    LocalMonsterSpawnerUiHandler.Context context
                ){
                    return ACTIVATION_BUDGET;
                }

                @Override public String authority(){
                    return SESSION_AUTHORITY;
                }
            },
            new LocalMonsterSpawnerUiHandler
                .SelectedNpcLabelResolver(){
                @Override public String label(
                    MonsterSpawnerService.CatalogEntry entry
                ){
                    return "LocalLab placeholder "+
                        (entry.rowIndex+1)+
                        " (NPC "+
                        entry.definitionId+
                        ")";
                }

                @Override public String authority(){
                    return CATALOG_AUTHORITY;
                }
            }
        );
    }

    private static NpcCombatRuntimeBinder.BehaviorPlan
        combatPlan(){
        return new NpcCombatRuntimeBinder.BehaviorPlan(
            "locallab-monster-spawner-combat",
            "CUSTOM_LOCALLAB_MONSTER_SPAWNER_PROFILE",
            context->
                NpcTargetAcquisitionService.Decision
                    .ineligible(),
            "CUSTOM_LOCALLAB_MONSTER_SPAWNER_TARGET",
            "NONE",
            new NpcCombatApproachService.ApproachPolicy(){
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
                    return "CUSTOM_LOCALLAB_MONSTER_SPAWNER_APPROACH";
                }

                @Override public String policy(){
                    return "FIXED_RANGE_1";
                }
            },
            new NpcCombatEngagementService.CadenceResolver(){
                @Override public int nextDelayTicks(
                    NpcCombatEngagementService.Context context
                ){
                    return 3;
                }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_MONSTER_SPAWNER_CADENCE";
                }

                @Override public String policy(){
                    return "FIXED_3";
                }
            },
            new NpcPlayerCombatResolutionService.DamageResolver(){
                @Override public int resolve(
                    NpcPlayerCombatResolutionService.DamageContext context
                ){
                    return 1;
                }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_MONSTER_SPAWNER_DAMAGE";
                }

                @Override public String formula(){
                    return "FIXED_1";
                }
            },
            context->context.worldTick+1L,
            "CUSTOM_LOCALLAB_MONSTER_SPAWNER_CONTROLLER",
            "CUSTOM_LOCALLAB_MONSTER_SPAWNER_AI"
        );
    }

    private LocalLabMonsterSpawnerProvisioning(){}
}
