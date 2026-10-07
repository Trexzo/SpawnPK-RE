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

    /*
     * Exact current client corpus: definition 1 ("Man") exposes option-2
     * Attack (opcode 72) and is not one of the max-hit dummy definitions.
     * Combat/drop/HP balance below remains explicit CUSTOM_LOCALLAB policy.
     */
    static final int NPC_DEFINITION_ID=1;
    static final int NPC_HITPOINTS=10;
    static final int ACTIVATION_BUDGET=1;

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

        MonsterSpawnerPvmRuntime runtime=
            new MonsterSpawnerPvmRuntime(
                checkedWorld,
                lifecycleBinding,
                finalizer,
                settlement
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
                            resolveCurrentOwnerAdjacentTile(
                                checkedWorld,
                                context.ownerRef
                            )
                        );
                    }

                    @Override public String authority(){
                        return REQUEST_AUTHORITY;
                    }
                }
            );

        G1MonsterSpawnerPvmLoopService loop=
            new G1MonsterSpawnerPvmLoopService(
                checkedWorld,
                service,
                executor,
                checkedWorld.pvmRecords()
            );

        runtime.installTerminalObserver(
            result->{
                /*
                 * G1 owns canonical finalization/record/respawn first. G4.1
                 * consumes only the already-finalized death and cannot create
                 * kill authority for a stale/unregistered recipient.
                 */
                loop.onFinalized(result);

                if(result.status!=
                        MonsterSpawnerPvmRuntime
                            .FinalizeStatus.FINALIZED||
                   result.finalization==null)
                    return;

                MonsterSpawnerNpcDeathFinalizationService.Result
                    finalization=result.finalization;

                creditTerminalProgression(
                    checkedWorld,
                    finalization.recipientRef,
                    finalization.definitionId,
                    finalization.deathTile,
                    finalization.deathTick
                );
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

    /**
     * Shared progression seam for one already-terminal, post-settlement
     * Monster Spawner death.
     *
     * G4 Blood Slayer remains first. G14 Daily Challenges, G15 Task Scroll,
     * G16 Adventure and G18 Daily Money Making consume the same validated
     * finalization only after the established progression hooks ahead of them.
     */
    static boolean creditTerminalProgression(
        World world,
        String recipientRef,
        int definitionId,
        Tile deathTile,
        long deathTick
    ){
        World checkedWorld=
            Objects.requireNonNull(
                world,
                "world"
            );
        String recipientRefChecked=
            PartyService.requireRef(
                recipientRef
            );
        Tile tile=
            Objects.requireNonNull(
                deathTile,
                "deathTile"
            );

        WorldPlayer recipient=
            checkedWorld.players().byName(
                recipientRefChecked
            );

        if(recipient==null)
            return false;

        long generation=
            recipient.generation();

        if(!checkedWorld.players().owns(
                recipient,
                generation))
            return false;

        checkedWorld.localLabSlayer()
            .recordMonsterSpawnerFinalization(
                recipientRefChecked,
                definitionId,
                tile,
                deathTick
            );

        checkedWorld.localLabDailyChallenges()
            .recordMonsterSpawnerFinalization(
                recipientRefChecked,
                definitionId,
                deathTick
            );

        checkedWorld.localLabTaskScrolls()
            .recordMonsterSpawnerFinalization(
                recipientRefChecked,
                definitionId,
                deathTick
            );

        checkedWorld.localLabAdventures()
            .recordMonsterSpawnerFinalization(
                recipientRefChecked,
                definitionId,
                deathTick
            );

        checkedWorld.localLabDailyMoneyMaking()
            .recordMonsterSpawnerFinalization(
                recipientRefChecked,
                definitionId,
                deathTick
            );

        return true;
    }

    static Tile resolveCurrentOwnerAdjacentTile(
        World world,
        String ownerRef
    ){
        World checkedWorld=
            Objects.requireNonNull(
                world,
                "world"
            );
        String owner=
            PartyService.requireRef(
                ownerRef
            );
        WorldPlayer player=
            checkedWorld.players()
                .byName(
                    owner
                );

        if(player==null||
           !checkedWorld.players().owns(
                player,
                player.generation()))
            throw new IllegalStateException(
                "LocalLab Monster Spawner placement requires exact current owner="+
                owner
            );

        MovementState movement=
            player.movement();
        int x=movement.x();
        int y=movement.y();
        int plane=movement.plane();

        final int[][] directions={
            {1,0},
            {-1,0},
            {0,1},
            {0,-1}
        };

        for(int[] direction:directions){
            int targetX=x+direction[0];
            int targetY=y+direction[1];

            if(WorldCollisionAuthority.canStep(
                    x,
                    y,
                    plane,
                    targetX,
                    targetY))
                return new Tile(
                    targetX,
                    targetY,
                    plane
                );
        }

        throw new IllegalStateException(
            "LocalLab Monster Spawner has no collision-safe adjacent tile owner="+
            owner+
            " tile="+
            x+","+
            y+","+
            plane
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
