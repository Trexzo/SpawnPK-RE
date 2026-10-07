package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.*;

public final class G71BloodSlayerBountyHunterIntegrationTest {
    private static final String IMMEDIATE_ATTACKER="g71-immediate-attacker";
    private static final String IMMEDIATE_VICTIM="g71-immediate-victim";
    private static final String DELAYED_ATTACKER="g71-delayed-attacker";
    private static final String DELAYED_VICTIM="g71-delayed-victim";

    public static void main(String[] args)throws Exception{
        boolean exact54111=false;
        boolean bountyAssigned=false;
        boolean pvmIsolation=false;
        boolean pvpIsolation=false;
        boolean immediateSettlementCredit=false;
        boolean duplicateDeathSafe=false;
        boolean sharedCompletionCount=false;
        boolean freshWorldSharedCount=false;
        boolean delayedSettlementCredit=false;
        boolean freshWorldBountyRestore=false;
        boolean g6V3Migration=false;
        boolean g6V3BountyRejected=false;
        boolean slaughterStillUnsupported=false;

        MemoryPlayers immediateRepository=
            new MemoryPlayers();

        World immediateWorld=
            World.isolatedForTest(
                60_000L,
                immediateRepository
            );
        WorldPlayer immediateAttacker=
            new WorldPlayer();
        WorldPlayer immediateVictim=
            new WorldPlayer();

        long immediateAttackerGeneration=
            immediateWorld.registerPlayer(
                immediateAttacker,
                IMMEDIATE_ATTACKER
            );
        long immediateVictimGeneration=
            immediateWorld.registerPlayer(
                immediateVictim,
                IMMEDIATE_VICTIM
            );

        try{
            LocalLabSlayerRuntime runtime=
                immediateWorld.localLabSlayer();
            LocalBloodSlayerUiHandler ui=
                new LocalBloodSlayerUiHandler(
                    immediateAttacker,
                    runtime
                );

            BloodSlayerPresentation.Input bountyInput=
                BloodSlayerPresentation.resolveWidget(
                    BloodSlayerPresentation
                        .BOUNTY_HUNTER_WIDGET
                );

            exact54111=
                bountyInput!=null&&
                bountyInput.kind==
                    BloodSlayerPresentation.InputKind
                        .SELECT_MODE&&
                bountyInput.mode==
                    BloodSlayerModeService.Mode
                        .BOUNTY_HUNTER_PK;

            require(
                exact54111,
                "exact Bounty Hunter widget 54111 routing"
            );

            LocalBloodSlayerUiHandler.Result selected=
                ui.handle(
                    bountyInput,
                    immediateWorld.clock().tick()
                );

            require(
                selected.status==
                    LocalBloodSlayerUiHandler.Status
                        .MODE_SELECTED&&
                selected.taskStatus.selectedMode==
                    BloodSlayerModeService.Mode
                        .BOUNTY_HUNTER_PK&&
                selected.taskStatus.task==null,
                "Bounty Hunter mode selection failed"
            );

            LocalBloodSlayerUiHandler.Result assigned=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    immediateWorld.clock().tick()
                );

            bountyAssigned=
                assigned.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_ASSIGNED&&
                assigned.taskStatus.active()&&
                assigned.taskStatus.selectedMode==
                    BloodSlayerModeService.Mode
                        .BOUNTY_HUNTER_PK&&
                LocalLabSlayerRuntime.BOUNTY_TASK_KEY
                    .equals(
                        assigned.taskStatus.task
                            .definition.taskKey
                    )&&
                LocalLabSlayerRuntime.BOUNTY_OBJECTIVE_KEY
                    .equals(
                        assigned.taskStatus.task
                            .objective.key
                    )&&
                assigned.taskStatus.completions==0L;

            require(
                bountyAssigned,
                "exact Get Task did not assign LocalLab Bounty task"
            );

            LocalLabSlayerRuntime.KillCreditResult pvmKill=
                runtime.recordMonsterSpawnerKill(
                    IMMEDIATE_ATTACKER,
                    LocalLabSlayerRuntime
                        .TARGET_DEFINITION_ID,
                    immediateWorld.clock().tick()
                );

            pvmIsolation=
                pvmKill.activeTask&&
                !pvmKill.progressed&&
                !pvmKill.completedNow&&
                pvmKill.status.active()&&
                pvmKill.status.task.objective
                    .progress==0L&&
                pvmKill.status.completions==0L;

            require(
                pvmIsolation,
                "NPC kill progressed Bounty Hunter"
            );

            LocalBloodSlayerUiHandler.Result unsupported=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .SLAUGHTER_WIDGET
                    ),
                    immediateWorld.clock().tick()
                );

            slaughterStillUnsupported=
                unsupported.status==
                    LocalBloodSlayerUiHandler.Status
                        .UNSUPPORTED_MODE&&
                runtime.status(IMMEDIATE_ATTACKER)
                    .selectedMode==
                    BloodSlayerModeService.Mode
                        .BOUNTY_HUNTER_PK;

            require(
                slaughterStillUnsupported,
                "Slaughter unexpectedly entered supported LocalLab policy"
            );

            require(
                immediateVictim.playerState()
                    .setCurrentLevel(
                        PlayerState.HITPOINTS,
                        9
                    ),
                "immediate victim HP fixture"
            );

            PlayerCombatResolutionService immediateCombat=
                new PlayerCombatResolutionService(
                    immediateAttacker,
                    CombatDamageRules.localLabFallback(),
                    CombatAttackTimingRules
                        .recoveredCompatibility(),
                    CombatSystemHooks.forPlayer(
                        immediateAttacker
                    )
                );

            CombatStyleRepository.Style style=
                CombatStyleRepository
                    .defaultForRoot(12290);

            require(
                style!=null,
                "missing melee style fixture"
            );

            PlayerCombatResolutionService.Result lethal=
                immediateCombat.resolveImmediateOwned(
                    immediateWorld,
                    immediateAttackerGeneration,
                    immediateVictim,
                    immediateVictimGeneration,
                    4151,
                    style,
                    10L
                );

            require(
                lethal.lifecycle.died&&
                immediateVictim.lifecycle().dead()&&
                immediateVictim.lifecycle()
                    .deathAttribution()!=null&&
                immediateAttacker.id().equals(
                    immediateVictim.lifecycle()
                        .deathAttribution()
                        .attackerId
                )&&
                immediateVictim.lifecycle()
                    .deathAttribution()
                    .attackerGeneration==
                        immediateAttackerGeneration&&
                "PLAYER_PVP".equals(
                    immediateVictim.lifecycle()
                        .deathAttribution()
                        .context
                ),
                "immediate canonical PvP attribution missing"
            );

            SettlementHarness immediateSettlement=
                new SettlementHarness(
                    immediateWorld,
                    immediateVictim
                );

            immediateSettlement.coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g7-immediate] "
                );

            LocalLabSlayerRuntime.StatusSnapshot
                afterImmediate=
                    runtime.status(
                        IMMEDIATE_ATTACKER
                    );

            immediateSettlementCredit=
                afterImmediate.complete()&&
                afterImmediate.selectedMode==
                    BloodSlayerModeService.Mode
                        .BOUNTY_HUNTER_PK&&
                afterImmediate.task.objective
                    .progress==1L&&
                afterImmediate.completions==1L;

            require(
                immediateSettlementCredit,
                "canonical immediate PvP settlement did not credit Bounty"
            );

            immediateSettlement.coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g7-immediate-replay] "
                );

            LocalLabSlayerRuntime.StatusSnapshot
                afterReplay=
                    runtime.status(
                        IMMEDIATE_ATTACKER
                    );

            duplicateDeathSafe=
                afterReplay.complete()&&
                afterReplay.task.taskId.equals(
                    afterImmediate.task.taskId
                )&&
                afterReplay.completions==1L;

            require(
                duplicateDeathSafe,
                "same death settlement replay duplicated Bounty credit"
            );

            runtime.selectMonsterHunterMode(
                IMMEDIATE_ATTACKER
            );

            LocalLabSlayerRuntime.StartResult monster=
                runtime.startMonsterHunter(
                    IMMEDIATE_ATTACKER,
                    immediateWorld.clock().tick()
                );

            require(
                monster.created&&
                monster.status.active()&&
                monster.status.completions==1L,
                "Bounty -> Monster renewal lost shared completion count"
            );

            WorldPlayer pvpIsolationVictim=
                new WorldPlayer();
            long pvpIsolationVictimGeneration=
                immediateWorld.registerPlayer(
                    pvpIsolationVictim,
                    "g71-pvp-isolation-victim"
                );

            require(
                pvpIsolationVictim.playerState()
                    .setCurrentLevel(
                        PlayerState.HITPOINTS,
                        9
                    ),
                "PvP isolation victim HP fixture"
            );

            PlayerCombatResolutionService.Result
                isolationLethal=
                    immediateCombat.resolveImmediateOwned(
                        immediateWorld,
                        immediateAttackerGeneration,
                        pvpIsolationVictim,
                        pvpIsolationVictimGeneration,
                        4151,
                        style,
                        11L
                    );

            require(
                isolationLethal.lifecycle.died,
                "PvP isolation fixture did not die"
            );

            new SettlementHarness(
                immediateWorld,
                pvpIsolationVictim
            ).coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g7-pvp-isolation] "
                );

            LocalLabSlayerRuntime.StatusSnapshot
                afterPvpIsolation=
                    runtime.status(
                        IMMEDIATE_ATTACKER
                    );

            pvpIsolation=
                afterPvpIsolation.active()&&
                afterPvpIsolation.selectedMode==
                    BloodSlayerModeService.Mode
                        .MONSTER_HUNTER_PVM&&
                afterPvpIsolation.task.objective
                    .progress==0L&&
                afterPvpIsolation.completions==1L;

            require(
                pvpIsolation,
                "canonical PvP kill progressed Monster Hunter"
            );

            LocalLabSlayerRuntime.KillCreditResult
                monsterCompleted=
                    runtime.recordMonsterSpawnerKill(
                        IMMEDIATE_ATTACKER,
                        LocalLabSlayerRuntime
                            .TARGET_DEFINITION_ID,
                        immediateWorld.clock().tick()
                    );

            sharedCompletionCount=
                monsterCompleted.completedNow&&
                monsterCompleted.status.complete()&&
                monsterCompleted.status.completions==2L;

            require(
                sharedCompletionCount,
                "Monster completion did not share Bounty completion counter"
            );

            immediateRepository.save(
                PlayerSnapshotCodec.capture(
                    IMMEDIATE_ATTACKER,
                    immediateAttacker
                )
            );
        }finally{
            immediateWorld.close();
        }

        World restoredSharedWorld=
            World.isolatedForTest(
                60_000L,
                immediateRepository
            );

        try{
            WorldPlayer restored=
                loadAndRegister(
                    restoredSharedWorld,
                    immediateRepository,
                    IMMEDIATE_ATTACKER
                );

            LocalLabSlayerRuntime.StatusSnapshot status=
                restoredSharedWorld.localLabSlayer()
                    .status(
                        IMMEDIATE_ATTACKER
                    );

            freshWorldSharedCount=
                restored!=null&&
                status.persistenceValid()&&
                status.complete()&&
                status.selectedMode==
                    BloodSlayerModeService.Mode
                        .MONSTER_HUNTER_PVM&&
                status.completions==2L;

            require(
                freshWorldSharedCount,
                "fresh World lost shared Bounty/PvM completion count"
            );
        }finally{
            restoredSharedWorld.close();
        }

        MemoryPlayers delayedRepository=
            new MemoryPlayers();

        World delayedWorld=
            World.isolatedForTest(
                60_000L,
                delayedRepository
            );
        WorldPlayer delayedAttacker=
            new WorldPlayer();
        WorldPlayer delayedVictim=
            new WorldPlayer();

        long delayedAttackerGeneration=
            delayedWorld.registerPlayer(
                delayedAttacker,
                DELAYED_ATTACKER
            );
        long delayedVictimGeneration=
            delayedWorld.registerPlayer(
                delayedVictim,
                DELAYED_VICTIM
            );

        try{
            LocalBloodSlayerUiHandler delayedUi=
                new LocalBloodSlayerUiHandler(
                    delayedAttacker,
                    delayedWorld.localLabSlayer()
                );

            require(
                delayedUi.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .BOUNTY_HUNTER_WIDGET
                    ),
                    delayedWorld.clock().tick()
                ).status==
                    LocalBloodSlayerUiHandler.Status
                        .MODE_SELECTED,
                "delayed Bounty selection"
            );

            require(
                delayedUi.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    delayedWorld.clock().tick()
                ).status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_ASSIGNED,
                "delayed Bounty assignment"
            );

            require(
                delayedVictim.playerState()
                    .setCurrentLevel(
                        PlayerState.HITPOINTS,
                        10
                    ),
                "delayed victim HP fixture"
            );

            PlayerPvpDelayedHitService delayed=
                new PlayerPvpDelayedHitService(
                    delayedWorld,
                    "CUSTOM_LOCALLAB",
                    PlayerPvpDelayedHitService
                        .REQUIRE_CURRENT_PLAYER_GENERATIONS
                );

            PlayerPvpDelayedHitService.Snapshot scheduled=
                delayed.scheduleAtExpectedTick(
                    delayedAttacker,
                    delayedAttackerGeneration,
                    delayedVictim,
                    delayedVictimGeneration,
                    10,
                    2,
                    "CUSTOM_LOCALLAB",
                    "G7_TEST_FIXED_DAMAGE",
                    delayedWorld.clock().tick()
                );

            delayedWorld.events()
                .runDue(
                    scheduled.dueTick
                );

            PlayerPvpDelayedHitService.Snapshot delivered=
                delayed.get(
                    scheduled.hitId
                );

            require(
                delivered!=null&&
                delivered.state==
                    PlayerPvpDelayedHitService.State
                        .DELIVERED&&
                delivered.died&&
                delayedVictim.lifecycle().dead()&&
                delayedVictim.lifecycle()
                    .deathAttribution()!=null&&
                delayedAttacker.id().equals(
                    delayedVictim.lifecycle()
                        .deathAttribution()
                        .attackerId
                )&&
                delayedVictim.lifecycle()
                    .deathAttribution()
                    .attackerGeneration==
                        delayedAttackerGeneration&&
                "PLAYER_PVP".equals(
                    delayedVictim.lifecycle()
                        .deathAttribution()
                        .context
                ),
                "delayed canonical PvP attribution missing"
            );

            SettlementHarness delayedSettlement=
                new SettlementHarness(
                    delayedWorld,
                    delayedVictim
                );

            delayedSettlement.coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g7-delayed] "
                );

            LocalLabSlayerRuntime.StatusSnapshot delayedStatus=
                delayedWorld.localLabSlayer()
                    .status(
                        DELAYED_ATTACKER
                    );

            delayedSettlementCredit=
                delayedStatus.complete()&&
                delayedStatus.selectedMode==
                    BloodSlayerModeService.Mode
                        .BOUNTY_HUNTER_PK&&
                delayedStatus.task.objective
                    .progress==1L&&
                delayedStatus.completions==1L;

            require(
                delayedSettlementCredit,
                "canonical delayed PvP settlement did not credit Bounty"
            );

            delayedRepository.save(
                PlayerSnapshotCodec.capture(
                    DELAYED_ATTACKER,
                    delayedAttacker
                )
            );
        }finally{
            delayedWorld.close();
        }

        World restoredBountyWorld=
            World.isolatedForTest(
                60_000L,
                delayedRepository
            );

        try{
            loadAndRegister(
                restoredBountyWorld,
                delayedRepository,
                DELAYED_ATTACKER
            );

            LocalLabSlayerRuntime.StatusSnapshot status=
                restoredBountyWorld.localLabSlayer()
                    .status(
                        DELAYED_ATTACKER
                    );

            freshWorldBountyRestore=
                status.persistenceValid()&&
                status.complete()&&
                status.selectedMode==
                    BloodSlayerModeService.Mode
                        .BOUNTY_HUNTER_PK&&
                LocalLabSlayerRuntime.BOUNTY_TASK_KEY
                    .equals(
                        status.task.definition.taskKey
                    )&&
                status.completions==1L;

            require(
                freshWorldBountyRestore,
                "fresh World did not restore Bounty v4 state"
            );
        }finally{
            restoredBountyWorld.close();
        }

        TreeMap<String,String> g6=
            new TreeMap<>();

        g6.put(
            "version",
            LocalLabSlayerPersistence.G6_VERSION
        );
        g6.put(
            "authority",
            LocalLabSlayerRuntime.G6_AUTHORITY
        );
        g6.put(
            "task-key",
            LocalLabSlayerRuntime.BOSS_TASK_KEY
        );
        g6.put(
            "mode",
            BloodSlayerModeService.Mode
                .BOSS_HUNTER_PVM
                .name()
        );
        g6.put("state","COMPLETED");
        g6.put("progress","1");
        g6.put("goal","1");
        g6.put("source-assigned-tick","3");
        g6.put("source-transition-tick","4");
        g6.put("completion-count","9");

        LocalLabSlayerPersistence.Snapshot g6Decoded=
            LocalLabSlayerPersistence.decode(
                g6
            );

        require(
            g6Decoded!=null&&
            g6Decoded.mode==
                BloodSlayerModeService.Mode
                    .BOSS_HUNTER_PVM&&
            g6Decoded.state==
                LocalLabSlayerPersistence.State
                    .COMPLETED&&
            g6Decoded.completions==9L,
            "certified G6 v3 did not decode under historical authority"
        );

        MemoryPlayers migrationRepository=
            new MemoryPlayers();
        WorldPlayer migrationSeed=
            new WorldPlayer();

        migrationSeed.snapshotExtensions()
            .replaceNamespace(
                LocalLabSlayerPersistence
                    .NAMESPACE,
                g6
            );

        migrationRepository.save(
            PlayerSnapshotCodec.capture(
                "g71-v3-migration",
                migrationSeed
            )
        );

        World migrationWorld=
            World.isolatedForTest(
                60_000L,
                migrationRepository
            );

        try{
            WorldPlayer migrated=
                loadAndRegister(
                    migrationWorld,
                    migrationRepository,
                    "g71-v3-migration"
                );

            LocalLabSlayerRuntime runtime=
                migrationWorld.localLabSlayer();

            LocalLabSlayerRuntime.StatusSnapshot restored=
                runtime.status(
                    "g71-v3-migration"
                );

            require(
                restored.persistenceValid()&&
                restored.complete()&&
                restored.selectedMode==
                    BloodSlayerModeService.Mode
                        .BOSS_HUNTER_PVM&&
                restored.completions==9L,
                "G6 v3 runtime restore failed"
            );

            LocalLabSlayerRuntime.StartResult renewed=
                runtime.startBossHunter(
                    "g71-v3-migration",
                    migrationWorld.clock().tick()
                );

            SortedMap<String,String> migratedExtension=
                migrated.snapshotExtensions()
                    .namespace(
                        LocalLabSlayerPersistence
                            .NAMESPACE
                    );

            g6V3Migration=
                renewed.created&&
                renewed.status.active()&&
                renewed.status.completions==9L&&
                LocalLabSlayerPersistence.VERSION
                    .equals(
                        migratedExtension.get(
                            "version"
                        )
                    )&&
                LocalLabSlayerRuntime.AUTHORITY
                    .equals(
                        migratedExtension.get(
                            "authority"
                        )
                    );

            require(
                g6V3Migration,
                "G6 v3 did not migrate to current v4/G7 on renewal"
            );
        }finally{
            migrationWorld.close();
        }

        TreeMap<String,String> impossibleG6Bounty=
            new TreeMap<>(g6);

        impossibleG6Bounty.put(
            "mode",
            BloodSlayerModeService.Mode
                .BOUNTY_HUNTER_PK
                .name()
        );
        impossibleG6Bounty.put(
            "task-key",
            LocalLabSlayerRuntime
                .BOUNTY_TASK_KEY
        );

        try{
            LocalLabSlayerPersistence.decode(
                impossibleG6Bounty
            );
        }catch(IllegalArgumentException expected){
            g6V3BountyRejected=true;
        }

        require(
            g6V3BountyRejected,
            "historical G6 v3 illegally accepted Bounty mode"
        );

        require(
            BloodSlayerPresentation.WIDGET_ACTION_OPCODE==185,
            "Blood Slayer widget opcode drift"
        );

        System.out.println(
            "G71_BLOOD_SLAYER_BOUNTY_HUNTER_PASS"+
            " exact54111="+exact54111+
            " bountyAssigned="+bountyAssigned+
            " pvmIsolation="+pvmIsolation+
            " pvpIsolation="+pvpIsolation+
            " immediateSettlementCredit="+
                immediateSettlementCredit+
            " duplicateDeathSafe="+duplicateDeathSafe+
            " sharedCompletionCount="+
                sharedCompletionCount+
            " freshWorldSharedCount="+
                freshWorldSharedCount+
            " delayedSettlementCredit="+
                delayedSettlementCredit+
            " freshWorldBountyRestore="+
                freshWorldBountyRestore+
            " g6V3Migration="+g6V3Migration+
            " g6V3BountyRejected="+
                g6V3BountyRejected+
            " slaughterStillUnsupported="+
                slaughterStillUnsupported+
            " bloodSlayerPointsClaim=false"+
            " slayerPointsClaim=false"+
            " rewardClaim=false"+
            " originalSpawnpkTaskPolicyClaim=false"+
            " originalSpawnpkPkPolicyClaim=false"
        );
    }

    private static final class SettlementHarness {
        final World world;
        final WorldPlayer player;
        final MovementState movement;
        final BankState bank;
        final EquipmentState equipment;
        final PetState petState;
        final PetEffectState petEffects;
        final CombatStyleState combatStyles;
        final DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        final NpcRegistry npcs=
            new NpcRegistry(dev);
        final HomeWorldRuntimePlan homeWorld=
            new HomeWorldRuntimePlan();
        final CombatEngine combat=
            new CombatEngine(dev);
        final ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{41,42,43,44}
                )
            );
        final SceneUpdatePublisher publisher;
        final LocalPlayerInteractionHandler
            playerInteractions;
        final LocalBankObjectInteractionHandler
            bankObjects;
        final LocalRoutedNpcInteractionHandler
            routedNpcs;
        final LocalGroundItemInteractionHandler
            groundItems;
        final LocalPetDropPickupHandler
            petDropPickup;
        final LocalPetRuntimeCommandHandler
            petRuntime;
        final RegionBridge regionBridge=
            new RegionBridge();
        final TickBridge tickBridge=
            new TickBridge();
        final LocalWorldTickCoordinator coordinator;

        SettlementHarness(
            World world,
            WorldPlayer player
        )throws Exception{
            this.world=Objects.requireNonNull(
                world,
                "world"
            );
            this.player=Objects.requireNonNull(
                player,
                "player"
            );
            this.movement=player.movement();
            this.bank=player.bank();
            this.equipment=player.equipment();
            this.petState=player.petState();
            this.petEffects=player.petEffects();
            this.combatStyles=
                player.combatStyles();

            publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            playerInteractions=
                new LocalPlayerInteractionHandler(
                    world,
                    player,
                    movement,
                    equipment
                );

            bankObjects=
                new LocalBankObjectInteractionHandler(
                    bank,
                    movement
                );

            routedNpcs=
                new LocalRoutedNpcInteractionHandler(
                    npcs,
                    bank,
                    movement,
                    null,
                    player,
                    equipment
                );

            groundItems=
                new LocalGroundItemInteractionHandler(
                    world,
                    bank,
                    movement
                );

            PetBridge petBridge=
                new PetBridge(
                    player.username(),
                    publisher
                );

            petDropPickup=
                new LocalPetDropPickupHandler(
                    world,
                    bank,
                    movement,
                    petState,
                    petEffects,
                    player.miniPets(),
                    npcs,
                    new VoidglassPetState(),
                    new PetAccessoryState(),
                    dev,
                    petBridge
                );

            petRuntime=
                new LocalPetRuntimeCommandHandler(
                    petState,
                    petEffects,
                    npcs,
                    movement
                );

            regionBridge.publisher=publisher;
            regionBridge.username=
                player.username();

            LocalRegionStreamHandler regionStreams=
                new LocalRegionStreamHandler(
                    false,
                    world,
                    player,
                    movement,
                    homeWorld,
                    npcs,
                    playerInteractions,
                    combat,
                    regionBridge
                );

            tickBridge.publisher=publisher;

            coordinator=
                new LocalWorldTickCoordinator(
                    false,
                    world,
                    player,
                    movement,
                    equipment,
                    combatStyles,
                    petEffects,
                    npcs,
                    homeWorld,
                    combat,
                    regionStreams,
                    playerInteractions,
                    bankObjects,
                    routedNpcs,
                    groundItems,
                    petDropPickup,
                    petRuntime,
                    tickBridge
                );
        }
    }

    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge {
        private final String username;
        private final SceneUpdatePublisher publisher;

        PetBridge(
            String username,
            SceneUpdatePublisher publisher
        ){
            this.username=username;
            this.publisher=publisher;
        }

        @Override public String username(){
            return username;
        }
        @Override public boolean persistentAccount(){
            return true;
        }
        @Override public long sessionWorldTick(){
            return 1L;
        }
        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }
        @Override public void saveAccount(
            String tag,
            String reason
        ){}
        @Override public int syncScopesightPassive(
            ServerPacketWriter serverPackets
        ){
            return 0;
        }
        @Override public void resetPetFollowDeadline(){}
        @Override public void ensurePetFollowScheduled(long now){}
    }

    private static final class RegionBridge
        implements LocalRegionStreamHandler.SessionBridge {
        SceneUpdatePublisher publisher;
        String username;

        @Override public String username(){
            return username;
        }
        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }
        @Override public void replaceScenePublisher(
            SceneUpdatePublisher replacement
        ){
            publisher=replacement;
        }
        @Override public void resetPetFollowRuntime(){}
    }

    private static final class TickBridge
        implements LocalWorldTickCoordinator.SessionBridge {
        SceneUpdatePublisher publisher;
        long petDeadline=Long.MAX_VALUE;

        @Override public Player81WorldSync.Context player81Sync(){
            return null;
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }

        @Override public void saveAccount(
            String tag,
            String reason
        ){}

        @Override public void savePlayerAccount(
            WorldPlayer player,
            long expectedGeneration,
            String tag,
            String reason
        ){}

        @Override public void publishOpponentOverlay(
            NpcEntity target,
            ServerPacketWriter writer,
            String tag,
            String reason
        ){}

        @Override public void clearOpponentOverlay(
            ServerPacketWriter writer,
            String tag,
            String reason
        ){}

        @Override public long petFollowDeadline(){
            return petDeadline;
        }

        @Override public void setPetFollowDeadline(
            long value
        ){
            petDeadline=value;
        }

        @Override public void ensurePetFollowScheduled(long now){}
        @Override public void ensurePetTestSequenceScheduled(long now){}
    }

    private static WorldPlayer loadAndRegister(
        World world,
        MemoryPlayers repository,
        String username
    )throws Exception{
        Optional<PlayerSnapshot> loaded=
            repository.load(
                username
            );

        require(
            loaded.isPresent(),
            "missing persisted snapshot "+
            username
        );

        WorldPlayer player=
            new WorldPlayer();

        PlayerSnapshotCodec.applyValidated(
            loaded.get(),
            player
        );

        world.registerPlayer(
            player,
            username
        );

        return player;
    }

    private static final class MemoryPlayers
        implements PlayerRepository {
        private final HashMap<String,PlayerSnapshot>
            values=
                new HashMap<>();

        @Override public synchronized
            Optional<PlayerSnapshot> load(
                String username
            ){
            return Optional.ofNullable(
                values.get(
                    normalize(username)
                )
            );
        }

        @Override public synchronized void save(
            PlayerSnapshot snapshot
        ){
            values.put(
                normalize(
                    snapshot.username()
                ),
                snapshot
            );
        }

        private static String normalize(
            String value
        ){
            return value==null
                ?""
                :value.trim()
                    .toLowerCase(
                        Locale.ROOT
                    );
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private G71BloodSlayerBountyHunterIntegrationTest(){}
}
