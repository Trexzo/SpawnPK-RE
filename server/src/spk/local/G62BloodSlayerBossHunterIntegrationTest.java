package spk.local;

import java.util.*;

public final class G62BloodSlayerBossHunterIntegrationTest {
    private static final String OWNER=
        "g62-boss-hunter";

    public static void main(String[] args)throws Exception{
        MemoryPlayers repository=
            new MemoryPlayers();

        boolean exact54110=false;
        boolean bossAssigned=false;
        boolean outsideRegionRejected=false;
        boolean bossRegionCompleted=false;
        boolean crossModeRenewal=false;
        boolean oldBossHistoryFrozen=false;
        boolean freshWorldBossRestore=false;
        boolean freshWorldBossRenewal=false;
        boolean legacyV1Readable=false;
        boolean malformedV2FailClosed=false;

        World world=
            World.isolatedForTest(
                60_000L,
                repository
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            OWNER
        );

        PlayerSnapshot completedBossSnapshot;

        try{
            LocalBloodSlayerUiHandler ui=
                new LocalBloodSlayerUiHandler(
                    player,
                    world.localLabSlayer()
                );

            BloodSlayerPresentation.Input bossInput=
                BloodSlayerPresentation.resolveWidget(
                    BloodSlayerPresentation
                        .BOSS_HUNTER_WIDGET
                );

            exact54110=
                bossInput!=null&&
                bossInput.kind==
                    BloodSlayerPresentation.InputKind
                        .SELECT_MODE&&
                bossInput.mode==
                    BloodSlayerModeService.Mode
                        .BOSS_HUNTER_PVM;

            require(
                exact54110,
                "exact Boss Hunter widget 54110 routing"
            );

            LocalBloodSlayerUiHandler.Result selected=
                ui.handle(
                    bossInput,
                    world.clock().tick()
                );

            require(
                selected.status==
                    LocalBloodSlayerUiHandler.Status
                        .MODE_SELECTED&&
                selected.mode==
                    BloodSlayerModeService.Mode
                        .BOSS_HUNTER_PVM&&
                selected.taskStatus.task==null,
                "Boss Hunter selection failed"
            );

            LocalBloodSlayerUiHandler.Result assigned=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    world.clock().tick()
                );

            bossAssigned=
                assigned.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_ASSIGNED&&
                assigned.taskStatus.active()&&
                assigned.taskStatus.selectedMode==
                    BloodSlayerModeService.Mode
                        .BOSS_HUNTER_PVM&&
                LocalLabSlayerRuntime.BOSS_TASK_KEY
                    .equals(
                        assigned.taskStatus.task
                            .definition.taskKey
                    )&&
                LocalLabSlayerRuntime.BOSS_OBJECTIVE_KEY
                    .equals(
                        assigned.taskStatus.task
                            .objective.key
                    );

            require(
                bossAssigned,
                "Boss Hunter task assignment failed"
            );

            SortedMap<String,String> extension=
                player.snapshotExtensions()
                    .namespace(
                        LocalLabSlayerPersistence
                            .NAMESPACE
                    );

            require(
                LocalLabSlayerPersistence.VERSION.equals(
                    extension.get("version")
                )&&
                LocalLabSlayerRuntime.AUTHORITY.equals(
                    extension.get("authority")
                )&&
                BloodSlayerModeService.Mode
                    .BOSS_HUNTER_PVM
                    .name()
                    .equals(
                        extension.get("mode")
                    )&&
                LocalLabSlayerRuntime.BOSS_TASK_KEY
                    .equals(
                        extension.get("task-key")
                    ),
                "Boss Hunter v2 persistence identity mismatch "+
                extension
            );

            SlayerTaskService.TaskId firstBossId=
                assigned.taskStatus.task.taskId;

            Tile outside=
                WorldCollisionAuthority.safeTile(
                    LocalTeleportDestinationCatalog
                        .get(
                            TeleportNavigationService
                                .EntryKind.TRAINING
                        )
                        .regionId,
                    0
                );
            Tile bossTile=
                WorldCollisionAuthority.safeTile(
                    LocalLabSlayerRuntime
                        .BOSS_REGION_ID,
                    0
                );

            require(
                outside!=null&&
                bossTile!=null&&
                LocalLabSlayerRuntime.regionId(
                    outside
                )!=
                    LocalLabSlayerRuntime
                        .BOSS_REGION_ID&&
                LocalLabSlayerRuntime.regionId(
                    bossTile
                )==
                    LocalLabSlayerRuntime
                        .BOSS_REGION_ID,
                "Boss Hunter test region authority"
            );

            LocalLabSlayerRuntime.KillCreditResult
                outsideKill=
                    world.localLabSlayer()
                        .recordMonsterSpawnerFinalization(
                            OWNER,
                            LocalLabSlayerRuntime
                                .TARGET_DEFINITION_ID,
                            outside,
                            world.clock().tick()
                        );

            outsideRegionRejected=
                outsideKill.eligibleDefinition&&
                outsideKill.activeTask&&
                !outsideKill.progressed&&
                !outsideKill.completedNow&&
                outsideKill.status.active()&&
                outsideKill.status.task.objective
                    .progress==0L;

            require(
                outsideRegionRejected,
                "Boss Hunter progressed outside region 16168"
            );

            LocalMonsterSpawnerActivationRuntime
                provisioned=
                    LocalLabMonsterSpawnerProvisioning
                        .create(world);

            provisioned.service()
                .openSession(
                    OWNER,
                    LocalLabMonsterSpawnerProvisioning
                        .SESSION_AUTHORITY
                );
            provisioned.service()
                .selectRow(
                    OWNER,
                    0
                );
            provisioned.service()
                .activate(
                    OWNER,
                    LocalLabMonsterSpawnerProvisioning
                        .ACTIVATION_BUDGET
                );

            MonsterSpawnerPvmRuntime.SpawnResult
                spawned=
                    provisioned.runtime()
                        .spawnAndBind(
                            OWNER,
                            OWNER,
                            bossTile.x,
                            bossTile.y,
                            bossTile.plane
                        );

            WorldNpc bossNpc=
                spawned.spawn.combat.spawn.npc;

            require(
                bossNpc.definitionId==
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID&&
                LocalLabSlayerRuntime.regionId(
                    bossNpc.tile()
                )==
                    LocalLabSlayerRuntime
                        .BOSS_REGION_ID,
                "provisioned Boss Hunter NPC identity/region"
            );

            require(
                world.npcLifecycle()
                    .applyDamage(
                        bossNpc.id,
                        99,
                        1L
                    )
                    .newlyDied,
                "provisioned Boss Hunter lethal finalization fixture"
            );

            MonsterSpawnerPvmRuntime.FinalizeResult
                finalized=
                    provisioned.runtime()
                        .finalizeIfOwned(
                            bossNpc
                        );

            LocalLabSlayerRuntime.StatusSnapshot
                bossCompletedStatus=
                    world.localLabSlayer()
                        .status(OWNER);

            bossRegionCompleted=
                finalized.status==
                    MonsterSpawnerPvmRuntime
                        .FinalizeStatus.FINALIZED&&
                bossCompletedStatus.complete()&&
                bossCompletedStatus.task.taskId
                    .equals(firstBossId)&&
                bossCompletedStatus.task.objective
                    .progress==1L;

            require(
                bossRegionCompleted,
                "canonical Boss-region finalization did not complete Boss Hunter"
            );

            LocalBloodSlayerUiHandler.Result selectMonster=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .MONSTER_HUNTER_WIDGET
                    ),
                    world.clock().tick()
                );

            require(
                selectMonster.status==
                    LocalBloodSlayerUiHandler.Status
                        .MODE_SELECTED,
                "Monster Hunter switch after Boss completion"
            );

            LocalBloodSlayerUiHandler.Result monster=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    world.clock().tick()
                );

            require(
                monster.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_ASSIGNED&&
                LocalLabSlayerRuntime.TASK_KEY.equals(
                    monster.taskStatus.task
                        .definition.taskKey
                ),
                "Monster Hunter assignment after Boss completion"
            );

            LocalLabSlayerRuntime.KillCreditResult
                monsterKill=
                    world.localLabSlayer()
                        .recordMonsterSpawnerKill(
                            OWNER,
                            LocalLabSlayerRuntime
                                .TARGET_DEFINITION_ID,
                            world.clock().tick()
                        );

            require(
                monsterKill.completedNow&&
                monsterKill.status.complete(),
                "cross-mode Monster Hunter completion"
            );

            ui.handle(
                bossInput,
                world.clock().tick()
            );

            LocalBloodSlayerUiHandler.Result secondBoss=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    world.clock().tick()
                );

            crossModeRenewal=
                secondBoss.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_ASSIGNED&&
                secondBoss.taskStatus.active()&&
                !secondBoss.taskStatus.task.taskId
                    .equals(firstBossId)&&
                secondBoss.taskStatus.task.objective
                    .progress==0L;

            SlayerTaskService.Snapshot oldBoss=
                world.localLabSlayer()
                    .slayer()
                    .get(firstBossId);

            oldBossHistoryFrozen=
                oldBoss!=null&&
                oldBoss.state==
                    SlayerTaskService.State
                        .COMPLETED&&
                oldBoss.objective.complete&&
                oldBoss.objective.progress==1L;

            require(
                crossModeRenewal&&
                oldBossHistoryFrozen,
                "Boss Hunter cross-mode renewal corrupted history"
            );

            LocalLabSlayerRuntime.KillCreditResult
                secondBossKill=
                    world.localLabSlayer()
                        .recordMonsterSpawnerFinalization(
                            OWNER,
                            LocalLabSlayerRuntime
                                .TARGET_DEFINITION_ID,
                            bossTile,
                            world.clock().tick()
                        );

            require(
                secondBossKill.completedNow&&
                secondBossKill.status.complete(),
                "renewed Boss Hunter did not complete"
            );

            completedBossSnapshot=
                PlayerSnapshotCodec.capture(
                    OWNER,
                    player
                );
            repository.save(
                completedBossSnapshot
            );
        }finally{
            world.close();
        }

        World fresh=
            World.isolatedForTest(
                60_000L,
                repository
            );
        WorldPlayer restored=
            loadAndRegister(
                fresh,
                repository,
                OWNER
            );

        try{
            LocalLabSlayerRuntime.StatusSnapshot
                restoredStatus=
                    fresh.localLabSlayer()
                        .status(OWNER);

            freshWorldBossRestore=
                restoredStatus.persistenceValid()&&
                restoredStatus.complete()&&
                restoredStatus.selectedMode==
                    BloodSlayerModeService.Mode
                        .BOSS_HUNTER_PVM&&
                LocalLabSlayerRuntime.BOSS_TASK_KEY
                    .equals(
                        restoredStatus.task.definition
                            .taskKey
                    )&&
                restoredStatus.task.objective
                    .progress==1L;

            require(
                freshWorldBossRestore,
                "fresh World did not restore completed Boss Hunter"
            );

            SlayerTaskService.TaskId restoredId=
                restoredStatus.task.taskId;

            LocalBloodSlayerUiHandler ui=
                new LocalBloodSlayerUiHandler(
                    restored,
                    fresh.localLabSlayer()
                );

            LocalBloodSlayerUiHandler.Result renewed=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    fresh.clock().tick()
                );

            freshWorldBossRenewal=
                renewed.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_ASSIGNED&&
                renewed.taskStatus.active()&&
                renewed.taskStatus.selectedMode==
                    BloodSlayerModeService.Mode
                        .BOSS_HUNTER_PVM&&
                !renewed.taskStatus.task.taskId
                    .equals(restoredId)&&
                renewed.taskStatus.task.objective
                    .progress==0L&&
                fresh.localLabSlayer()
                    .slayer()
                    .get(restoredId)
                    .objective.progress==1L;

            require(
                freshWorldBossRenewal,
                "fresh World completed Boss Hunter did not renew"
            );
        }finally{
            fresh.close();
        }

        TreeMap<String,String> legacy=
            new TreeMap<>();
        legacy.put(
            "version",
            LocalLabSlayerPersistence
                .LEGACY_VERSION
        );
        legacy.put(
            "authority",
            LocalLabSlayerRuntime
                .LEGACY_AUTHORITY
        );
        legacy.put(
            "task-key",
            LocalLabSlayerRuntime.TASK_KEY
        );
        legacy.put(
            "mode",
            BloodSlayerModeService.Mode
                .MONSTER_HUNTER_PVM
                .name()
        );
        legacy.put("state","ACTIVE");
        legacy.put("progress","0");
        legacy.put(
            "goal",
            Long.toString(
                LocalLabSlayerRuntime
                    .OBJECTIVE_GOAL
            )
        );
        legacy.put("source-assigned-tick","0");
        legacy.put("source-transition-tick","-1");

        LocalLabSlayerPersistence.Snapshot
            legacyDecoded=
                LocalLabSlayerPersistence
                    .decode(legacy);

        require(
            legacyDecoded!=null&&
            legacyDecoded.mode==
                BloodSlayerModeService.Mode
                    .MONSTER_HUNTER_PVM&&
            legacyDecoded.state==
                LocalLabSlayerPersistence.State
                    .ACTIVE&&
            legacyDecoded.progress==0L,
            "legacy v1 Monster Hunter codec decode"
        );

        final String legacyOwner=
            "g62-legacy-monster";
        WorldPlayer legacySeed=
            new WorldPlayer();
        legacySeed.snapshotExtensions()
            .replaceNamespace(
                LocalLabSlayerPersistence
                    .NAMESPACE,
                legacy
            );
        repository.save(
            PlayerSnapshotCodec.capture(
                legacyOwner,
                legacySeed
            )
        );

        World legacyWorld=
            World.isolatedForTest(
                60_000L,
                repository
            );

        try{
            loadAndRegister(
                legacyWorld,
                repository,
                legacyOwner
            );

            LocalLabSlayerRuntime.StatusSnapshot
                legacyStatus=
                    legacyWorld.localLabSlayer()
                        .status(
                            legacyOwner
                        );

            legacyV1Readable=
                legacyStatus.persistenceValid()&&
                legacyStatus.active()&&
                legacyStatus.selectedMode==
                    BloodSlayerModeService.Mode
                        .MONSTER_HUNTER_PVM&&
                LocalLabSlayerRuntime.TASK_KEY
                    .equals(
                        legacyStatus.task.definition
                            .taskKey
                    )&&
                legacyStatus.task.objective
                    .progress==0L;
        }finally{
            legacyWorld.close();
        }

        require(
            legacyV1Readable,
            "legacy v1 Monster Hunter fresh-World restore failed"
        );

        TreeMap<String,String> malformed=
            new TreeMap<>(
                completedBossSnapshot
                    .values()
            );
        String prefix=
            "extension."+
            LocalLabSlayerPersistence
                .NAMESPACE+
            ".";

        TreeMap<String,String> malformedExtension=
            new TreeMap<>();

        for(Map.Entry<String,String> entry:
                malformed.entrySet())
            if(entry.getKey().startsWith(prefix))
                malformedExtension.put(
                    entry.getKey().substring(
                        prefix.length()
                    ),
                    entry.getValue()
                );

        malformedExtension.put(
            "task-key",
            LocalLabSlayerRuntime.TASK_KEY
        );

        try{
            LocalLabSlayerPersistence.decode(
                malformedExtension
            );
        }catch(IllegalArgumentException expected){
            malformedV2FailClosed=true;
        }

        require(
            malformedV2FailClosed,
            "malformed v2 Boss mode/task combination accepted"
        );

        System.out.println(
            "G62_BLOOD_SLAYER_BOSS_HUNTER_PASS"+
            " exact54110="+exact54110+
            " bossAssigned="+bossAssigned+
            " outsideRegionRejected="+
                outsideRegionRejected+
            " bossRegionCompleted="+
                bossRegionCompleted+
            " crossModeRenewal="+
                crossModeRenewal+
            " oldBossHistoryFrozen="+
                oldBossHistoryFrozen+
            " freshWorldBossRestore="+
                freshWorldBossRestore+
            " freshWorldBossRenewal="+
                freshWorldBossRenewal+
            " legacyV1Readable="+
                legacyV1Readable+
            " malformedV2FailClosed="+
                malformedV2FailClosed+
            " definition1OriginalBossClaim=false"+
            " pointsClaim=false"+
            " rewardClaim=false"+
            " originalSpawnpkTaskPolicyClaim=false"
        );
    }

    private static WorldPlayer loadAndRegister(
        World world,
        MemoryPlayers repository,
        String username
    )throws Exception{
        Optional<PlayerSnapshot> loaded=
            repository.load(username);

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

    private G62BloodSlayerBossHunterIntegrationTest(){}
}
