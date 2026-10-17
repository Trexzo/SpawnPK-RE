package spk.local;

import java.nio.file.*;
import java.util.*;

public final class LocalLabSlayerPersistenceTest {
    private static final String OWNER="slayer-persist";

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory(
            "spawnpk-g4-slayer-"
        );

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->
                    root.resolve(
                        username+
                        ".properties"
                    )
            );

        boolean activeFreshWorld=false;
        boolean canonicalRestore=false;
        boolean restoredKillCompletes=false;
        boolean completedFreshWorld=false;
        boolean completedRenewal=false;
        boolean duplicateSafe=false;
        boolean malformedFailClosed=false;
        boolean coreSchemaUntouched=false;

        try{
            World source=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer sourcePlayer=
                new WorldPlayer();

            source.registerPlayer(
                sourcePlayer,
                OWNER
            );

            PlayerSnapshot before=
                PlayerSnapshotCodec.capture(
                    OWNER,
                    sourcePlayer
                );

            LocalLabSlayerRuntime.StartResult
                started=
                    source.localLabSlayer()
                        .startMonsterHunter(
                            OWNER,
                            source.clock().tick()
                        );

            require(
                started.created&&
                started.status.active()&&
                started.status.persistenceValid(),
                "source Slayer assignment failed"
            );

            SortedMap<String,String> extension=
                sourcePlayer.snapshotExtensions()
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
                LocalLabSlayerRuntime.TASK_KEY.equals(
                    extension.get("task-key")
                )&&
                "ACTIVE".equals(
                    extension.get("state")
                )&&
                "0".equals(
                    extension.get("progress")
                ),
                "active Slayer extension mismatch "+
                extension
            );

            PlayerSnapshot activeSnapshot=
                PlayerSnapshotCodec.capture(
                    OWNER,
                    sourcePlayer
                );

            coreSchemaUntouched=
                coreValues(before)
                    .equals(
                        coreValues(
                            activeSnapshot
                        )
                    );

            require(
                coreSchemaUntouched,
                "Slayer persistence mutated core schema keys"
            );

            repository.save(
                activeSnapshot
            );
            source.close();

            World activeWorld=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer activePlayer=
                loadAndRegister(
                    activeWorld,
                    repository,
                    OWNER
                );

            LocalLabSlayerRuntime.StatusSnapshot
                activeRestored=
                    activeWorld.localLabSlayer()
                        .status(
                            OWNER
                        );

            activeFreshWorld=
                activeRestored.persistenceValid()&&
                activeRestored.active()&&
                activeRestored.task.objective.progress==0L&&
                activeRestored.task.objective.goal==1L;

            canonicalRestore=
                activeWorld.localLabSlayer()
                    .slayer()
                    .active(OWNER)!=null&&
                activeWorld.localLabSlayer()
                    .bloodSlayer()
                    .get(OWNER)
                    .selectedMode==
                        BloodSlayerModeService.Mode
                            .MONSTER_HUNTER_PVM;

            require(
                activeFreshWorld&&
                canonicalRestore,
                "fresh World did not restore canonical active Slayer state"
            );

            LocalLabSlayerRuntime.KillCreditResult
                completed=
                    activeWorld.localLabSlayer()
                        .recordMonsterSpawnerKill(
                            OWNER,
                            LocalLabSlayerRuntime
                                .TARGET_DEFINITION_ID,
                            activeWorld.clock().tick()
                        );

            restoredKillCompletes=
                completed.progressed&&
                completed.completedNow&&
                completed.status.complete()&&
                completed.status.task.objective.progress==1L;

            require(
                restoredKillCompletes,
                "restored active Slayer task did not complete canonically"
            );

            PlayerSnapshot completedSnapshot=
                PlayerSnapshotCodec.capture(
                    OWNER,
                    activePlayer
                );

            require(
                "COMPLETED".equals(
                    completedSnapshot.value(
                        "extension."+
                        LocalLabSlayerPersistence.NAMESPACE+
                        ".state"
                    )
                )&&
                "1".equals(
                    completedSnapshot.value(
                        "extension."+
                        LocalLabSlayerPersistence.NAMESPACE+
                        ".progress"
                    )
                ),
                "completed Slayer extension mismatch"
            );

            repository.save(
                completedSnapshot
            );
            activeWorld.close();

            World completedWorld=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer completedPlayer=
                loadAndRegister(
                    completedWorld,
                    repository,
                    OWNER
                );

            LocalLabSlayerRuntime.StatusSnapshot
                completedRestored=
                    completedWorld.localLabSlayer()
                        .status(
                            OWNER
                        );

            completedFreshWorld=
                completedRestored.persistenceValid()&&
                completedRestored.complete()&&
                completedRestored.task.objective.progress==1L;

            SlayerTaskService.TaskId
                completedTaskId=
                    completedRestored.task.taskId;

            LocalLabSlayerRuntime.StartResult
                renewed=
                    completedWorld.localLabSlayer()
                        .startMonsterHunter(
                            OWNER,
                            completedWorld.clock().tick()
                        );

            completedRenewal=
                renewed.created&&
                renewed.status.active()&&
                renewed.status.task.objective.progress==0L&&
                !renewed.status.task.taskId.equals(
                    completedTaskId
                )&&
                completedWorld.localLabSlayer()
                    .slayer()
                    .get(completedTaskId)
                    .state==
                        SlayerTaskService.State
                            .COMPLETED&&
                completedWorld.localLabSlayer()
                    .slayer()
                    .get(completedTaskId)
                    .objective.progress==1L;

            LocalLabSlayerRuntime.StartResult
                duplicateStart=
                    completedWorld.localLabSlayer()
                        .startMonsterHunter(
                            OWNER,
                            completedWorld.clock().tick()
                        );

            duplicateSafe=
                !duplicateStart.created&&
                duplicateStart.status.active()&&
                duplicateStart.status.task.taskId.equals(
                    renewed.status.task.taskId
                )&&
                completedWorld.localLabSlayer()
                    .slayer()
                    .taskCount()==2;

            require(
                completedFreshWorld&&
                completedRenewal&&
                duplicateSafe,
                "completed Slayer restore did not renew exactly once"
            );

            TreeMap<String,String> malformedValues=
                new TreeMap<>(
                    completedSnapshot.values()
                );
            malformedValues.put(
                "extension."+
                LocalLabSlayerPersistence.NAMESPACE+
                ".authority",
                "WRONG_AUTHORITY"
            );

            PlayerSnapshot malformed=
                new PlayerSnapshot(
                    PlayerSnapshot.CURRENT_VERSION,
                    "broken-slayer",
                    malformedValues
                );

            repository.save(
                malformed
            );
            completedWorld.close();

            World malformedWorld=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer malformedPlayer=
                loadAndRegister(
                    malformedWorld,
                    repository,
                    "broken-slayer"
                );

            LocalLabSlayerRuntime.StatusSnapshot
                invalidStatus=
                    malformedWorld.localLabSlayer()
                        .status(
                            "broken-slayer"
                        );

            LocalLabSlayerRuntime.StartResult
                invalidStart=
                    malformedWorld.localLabSlayer()
                        .startMonsterHunter(
                            "broken-slayer",
                            malformedWorld.clock()
                                .tick()
                        );

            malformedFailClosed=
                !invalidStatus.persistenceValid()&&
                invalidStatus.task==null&&
                !invalidStart.created&&
                !invalidStart.status.persistenceValid()&&
                malformedWorld.localLabSlayer()
                    .slayer()
                    .taskCount()==0&&
                "WRONG_AUTHORITY".equals(
                    malformedPlayer
                        .snapshotExtensions()
                        .namespace(
                            LocalLabSlayerPersistence
                                .NAMESPACE
                        )
                        .get("authority")
                );

            require(
                malformedFailClosed,
                "malformed Slayer persistence was not fail-closed"
            );

            malformedWorld.close();

            System.out.println(
                "G4_BLOOD_SLAYER_PERSISTENCE_PASS"+
                " extensionNamespace=true"+
                " activeFreshWorld="+activeFreshWorld+
                " canonicalRestore="+canonicalRestore+
                " restoredKillCompletes="+restoredKillCompletes+
                " completedFreshWorld="+completedFreshWorld+
                " completedRenewal="+completedRenewal+
                " duplicateSafe="+duplicateSafe+
                " malformedFailClosed="+malformedFailClosed+
                " coreSchemaUntouched="+coreSchemaUntouched+
                " pointsClaim=false"+
                " rewardClaim=false"+
                " native54100WidgetClaim=false"+
                " originalSpawnpkTaskPolicyClaim=false"
            );
        }finally{
            deleteTree(root);
        }
    }

    private static WorldPlayer loadAndRegister(
        World world,
        FilePlayerRepository repository,
        String username
    )throws Exception{
        Optional<PlayerSnapshot> loaded=
            repository.load(username);

        require(
            loaded.isPresent(),
            "missing persisted Slayer snapshot "+
            username
        );

        WorldPlayer player=new WorldPlayer();

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

    private static SortedMap<String,String>
        coreValues(
            PlayerSnapshot snapshot
        ){
        TreeMap<String,String> core=
            new TreeMap<>();

        for(Map.Entry<String,String> entry:
                snapshot.values().entrySet())
            if(!entry.getKey().startsWith(
                    PlayerSnapshotExtensionState
                        .PREFIX))
                core.put(
                    entry.getKey(),
                    entry.getValue()
                );

        return core;
    }

    private static void deleteTree(
        Path root
    )throws Exception{
        if(root==null||
           !Files.exists(root))
            return;

        try(java.util.stream.Stream<Path> paths=
                Files.walk(root)){
            Iterator<Path> iterator=
                paths.sorted(
                    Comparator.reverseOrder()
                ).iterator();

            while(iterator.hasNext())
                Files.deleteIfExists(
                    iterator.next()
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

    private LocalLabSlayerPersistenceTest(){}
}
