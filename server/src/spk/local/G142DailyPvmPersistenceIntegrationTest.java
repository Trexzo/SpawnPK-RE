package spk.local;

import java.nio.file.*;
import java.util.*;

public final class G142DailyPvmPersistenceIntegrationTest {
    private static final String PLAYER="daily-persist";

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory(
            "spawnpk-g142-daily-"
        );

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->
                    root.resolve(
                        username+
                        ".properties"
                    )
            );

        boolean extensionNamespace=false;
        boolean assignedPersisted=false;
        boolean partial2FreshWorld=false;
        boolean completed3FreshWorld=false;
        boolean restoredKillCompletes=false;
        boolean repeatedStatusStable=false;
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
                PLAYER
            );

            PlayerSnapshot before=
                PlayerSnapshotCodec.capture(
                    PLAYER,
                    sourcePlayer
                );

            LocalLabDailyChallengeRuntime.StatusResult
                assigned=
                    source.localLabDailyChallenges()
                        .status(
                            PLAYER
                        );

            SortedMap<String,String> assignedExtension=
                sourcePlayer.snapshotExtensions()
                    .namespace(
                        LocalLabDailyChallengePersistence
                            .NAMESPACE
                    );

            extensionNamespace=
                LocalLabDailyChallengePersistence
                    .VERSION
                    .equals(
                        assignedExtension.get(
                            "version"
                        )
                    )&&
                LocalLabDailyChallengeRuntime
                    .AUTHORITY
                    .equals(
                        assignedExtension.get(
                            "authority"
                        )
                    );

            assignedPersisted=
                assigned.assignedNow&&
                assigned.challenge.current==0L&&
                "0".equals(
                    assignedExtension.get(
                        "progress"
                    )
                )&&
                "ACTIVE".equals(
                    assignedExtension.get(
                        "state"
                    )
                );

            PlayerSnapshot afterAssignment=
                PlayerSnapshotCodec.capture(
                    PLAYER,
                    sourcePlayer
                );

            coreSchemaUntouched=
                coreValues(before).equals(
                    coreValues(
                        afterAssignment
                    )
                );

            require(
                extensionNamespace&&
                assignedPersisted&&
                coreSchemaUntouched,
                "Daily assignment extension"
            );

            LocalLabDailyChallengeRuntime.ProgressResult
                first=
                    source.localLabDailyChallenges()
                        .recordMonsterSpawnerFinalization(
                            PLAYER,
                            LocalLabMonsterSpawnerProvisioning
                                .NPC_DEFINITION_ID,
                            1L
                        );
            LocalLabDailyChallengeRuntime.ProgressResult
                second=
                    source.localLabDailyChallenges()
                        .recordMonsterSpawnerFinalization(
                            PLAYER,
                            LocalLabMonsterSpawnerProvisioning
                                .NPC_DEFINITION_ID,
                            2L
                        );

            require(
                first.progressed&&
                first.challenge.current==1L&&
                second.progressed&&
                second.challenge.current==2L,
                "source Daily progress 2/3"
            );

            PlayerSnapshot partialSnapshot=
                PlayerSnapshotCodec.capture(
                    PLAYER,
                    sourcePlayer
                );

            require(
                "2".equals(
                    partialSnapshot.value(
                        PlayerSnapshotExtensionState
                            .PREFIX+
                        LocalLabDailyChallengePersistence
                            .NAMESPACE+
                        ".progress"
                    )
                ),
                "captured Daily progress 2"
            );

            repository.save(
                partialSnapshot
            );
            source.close();

            World partialWorld=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer partialPlayer=
                loadAndRegister(
                    partialWorld,
                    repository,
                    PLAYER
                );

            LocalLabDailyChallengeRuntime.StatusResult
                restoredPartial=
                    partialWorld
                        .localLabDailyChallenges()
                        .status(
                            PLAYER
                        );

            partial2FreshWorld=
                !restoredPartial.assignedNow&&
                restoredPartial.challenge.current==2L&&
                restoredPartial.challenge.target==3L&&
                !restoredPartial.challenge.complete;

            LocalLabDailyChallengeRuntime.StatusResult
                repeatedPartial=
                    partialWorld
                        .localLabDailyChallenges()
                        .status(
                            PLAYER
                        );

            repeatedStatusStable=
                !repeatedPartial.assignedNow&&
                repeatedPartial.challenge.current==2L&&
                repeatedPartial.challenge.target==3L;

            require(
                partial2FreshWorld&&
                repeatedStatusStable,
                "fresh World Daily partial restore"
            );

            boolean credited=
                LocalLabMonsterSpawnerProvisioning
                    .creditTerminalProgression(
                        partialWorld,
                        PLAYER,
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID,
                        new Tile(
                            MovementState.INITIAL_X,
                            MovementState.INITIAL_Y,
                            0
                        ),
                        3L
                    );

            DailyChallengeApplicationService.ChallengeSnapshot
                completed=
                    partialWorld
                        .localLabDailyChallenges()
                        .get(
                            PLAYER
                        )
                        .challenge(
                            LocalLabDailyChallengeRuntime
                                .CHALLENGE_KEY
                        );

            restoredKillCompletes=
                credited&&
                completed.current==3L&&
                completed.complete;

            require(
                restoredKillCompletes,
                "restored Daily kill completion"
            );

            PlayerSnapshot completedSnapshot=
                PlayerSnapshotCodec.capture(
                    PLAYER,
                    partialPlayer
                );

            repository.save(
                completedSnapshot
            );
            partialWorld.close();

            World completedWorld=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer completedPlayer=
                loadAndRegister(
                    completedWorld,
                    repository,
                    PLAYER
                );

            LocalLabDailyChallengeRuntime.StatusResult
                restoredComplete=
                    completedWorld
                        .localLabDailyChallenges()
                        .status(
                            PLAYER
                        );

            completed3FreshWorld=
                !restoredComplete.assignedNow&&
                restoredComplete.challenge.current==3L&&
                restoredComplete.challenge.target==3L&&
                restoredComplete.challenge.complete;

            LocalLabDailyChallengeRuntime.StatusResult
                repeatedComplete=
                    completedWorld
                        .localLabDailyChallenges()
                        .status(
                            PLAYER
                        );

            repeatedStatusStable&=
                !repeatedComplete.assignedNow&&
                repeatedComplete.challenge.current==3L&&
                repeatedComplete.challenge.complete;

            require(
                completed3FreshWorld&&
                repeatedStatusStable,
                "fresh World Daily completed restore"
            );

            completedWorld.close();

            World malformedWorld=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer malformedPlayer=
                new WorldPlayer();

            TreeMap<String,String> malformed=
                new TreeMap<>();
            malformed.put(
                "version",
                LocalLabDailyChallengePersistence.VERSION
            );
            malformed.put(
                "authority",
                "FOREIGN_DAILY_POLICY"
            );
            malformed.put(
                "challenge-key",
                LocalLabDailyChallengeRuntime.CHALLENGE_KEY
            );
            malformed.put(
                "objective-key",
                LocalLabDailyChallengeRuntime.OBJECTIVE_KEY
            );
            malformed.put("progress","1");
            malformed.put("goal","3");
            malformed.put("state","ACTIVE");

            malformedPlayer.snapshotExtensions()
                .replaceNamespace(
                    LocalLabDailyChallengePersistence
                        .NAMESPACE,
                    malformed
                );

            malformedWorld.registerPlayer(
                malformedPlayer,
                "daily-malformed"
            );

            SortedMap<String,String> malformedBefore=
                new TreeMap<>(
                    malformedPlayer
                        .snapshotExtensions()
                        .namespace(
                            LocalLabDailyChallengePersistence
                                .NAMESPACE
                        )
                );

            boolean statusRejected=false;
            boolean progressRejected=false;

            try{
                malformedWorld
                    .localLabDailyChallenges()
                    .status(
                        "daily-malformed"
                    );
            }catch(IllegalStateException expected){
                statusRejected=true;
            }

            try{
                malformedWorld
                    .localLabDailyChallenges()
                    .recordMonsterSpawnerFinalization(
                        "daily-malformed",
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID,
                        4L
                    );
            }catch(IllegalStateException expected){
                progressRejected=true;
            }

            SortedMap<String,String> malformedAfter=
                malformedPlayer
                    .snapshotExtensions()
                    .namespace(
                        LocalLabDailyChallengePersistence
                            .NAMESPACE
                    );

            malformedFailClosed=
                statusRejected&&
                progressRejected&&
                malformedBefore.equals(
                    malformedAfter
                )&&
                "FOREIGN_DAILY_POLICY"
                    .equals(
                        malformedAfter.get(
                            "authority"
                        )
                    )&&
                malformedWorld
                    .localLabDailyChallenges()
                    .get(
                        "daily-malformed"
                    )==null;

            require(
                malformedFailClosed,
                "malformed Daily persistence was overwritten"
            );

            malformedWorld.close();

            System.out.println(
                "G142_DAILY_PVM_PERSISTENCE_PASS"+
                " extensionNamespace="+
                    extensionNamespace+
                " assignedPersisted="+
                    assignedPersisted+
                " partial2FreshWorld="+
                    partial2FreshWorld+
                " completed3FreshWorld="+
                    completed3FreshWorld+
                " restoredKillCompletes="+
                    restoredKillCompletes+
                " repeatedStatusStable="+
                    repeatedStatusStable+
                " malformedFailClosed="+
                    malformedFailClosed+
                " coreSchemaUntouched="+
                    coreSchemaUntouched+
                " resetPolicyClaim=false"+
                " rewardPolicyClaim=false"+
                " originalPersistenceClaim=false"
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
            "missing persisted Daily snapshot "+
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

    private static SortedMap<String,String> coreValues(
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

    private G142DailyPvmPersistenceIntegrationTest(){}
}
