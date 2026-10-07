package spk.local;

import java.nio.file.*;
import java.util.*;

public final class G154TaskScrollPersistenceIntegrationTest {
    private static final String PLAYER=
        "task-scroll-persist";

    public static void main(String[] args)throws Exception{
        Path root=
            Files.createTempDirectory(
                "spawnpk-g154-task-scroll-"
            );

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->
                    root.resolve(
                        username+
                        ".properties"
                    )
            );

        boolean assignedRestored=false;
        boolean progress0Restored=false;
        boolean progress2Restored=false;
        boolean complete3Restored=false;
        boolean freshNoSnapshotUnassigned=false;
        boolean malformedFailClosed=false;
        boolean contractDriftRejected=false;
        boolean runtimeIdRecreated=false;
        boolean rewardStateAbsent=false;
        boolean trackStateAbsent=false;

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

            LocalLabTaskScrollRuntime.AssignmentResult
                sourceAssigned=
                    source.localLabTaskScrolls()
                        .assign(
                            PLAYER,
                            0L
                        );

            SortedMap<String,String> assignedExtension=
                sourcePlayer.snapshotExtensions()
                    .namespace(
                        LocalLabTaskScrollPersistence
                            .NAMESPACE
                    );

            require(
                LocalLabTaskScrollPersistence
                    .VERSION
                    .equals(
                        assignedExtension.get(
                            "version"
                        )
                    )&&
                LocalLabTaskScrollRuntime
                    .AUTHORITY
                    .equals(
                        assignedExtension.get(
                            "authority"
                        )
                    )&&
                "0".equals(
                    assignedExtension.get(
                        "progress"
                    )
                )&&
                "ACTIVE".equals(
                    assignedExtension.get(
                        "state"
                    )
                ),
                "assigned persistence"
            );

            rewardStateAbsent=
                assignedExtension.keySet()
                    .stream()
                    .noneMatch(
                        key->
                            key.toLowerCase(
                                Locale.ROOT
                            ).contains(
                                "reward"
                            )||
                            key.toLowerCase(
                                Locale.ROOT
                            ).contains(
                                "claim"
                            )||
                            key.toLowerCase(
                                Locale.ROOT
                            ).contains(
                                "casket"
                            )
                    );

            trackStateAbsent=
                assignedExtension.keySet()
                    .stream()
                    .noneMatch(
                        key->
                            key.toLowerCase(
                                Locale.ROOT
                            ).contains(
                                "track"
                            )||
                            key.toLowerCase(
                                Locale.ROOT
                            ).contains(
                                "task-scroll-id"
                            )||
                            key.equalsIgnoreCase(
                                "id"
                            )
                    );

            TaskScrollService.TaskScrollId sourceId=
                sourceAssigned.task.taskScrollId;

            PlayerSnapshot zeroSnapshot=
                PlayerSnapshotCodec.capture(
                    PLAYER,
                    sourcePlayer
                );

            repository.save(
                zeroSnapshot
            );
            source.close();

            World zeroWorld=
                World.isolatedForTest(
                    60_000L,
                    repository
                );

            WorldPlayer dummy=
                new WorldPlayer();
            long dummyGeneration=
                zeroWorld.registerPlayer(
                    dummy,
                    "task-scroll-dummy"
                );

            zeroWorld.localLabTaskScrolls()
                .assign(
                    "task-scroll-dummy",
                    0L
                );

            WorldPlayer zeroPlayer=
                loadAndRegister(
                    zeroWorld,
                    repository,
                    PLAYER
                );

            TaskScrollService.Snapshot zeroRestored=
                zeroWorld.localLabTaskScrolls()
                    .active(
                        PLAYER
                    );

            assignedRestored=
                zeroRestored!=null&&
                LocalLabTaskScrollRuntime
                    .TASK_KEY.equals(
                        zeroRestored.definition
                            .taskKey
                    );

            progress0Restored=
                zeroRestored!=null&&
                zeroRestored.objective.progress==0L&&
                zeroRestored.objective.goal==3L&&
                zeroRestored.state==
                    TaskScrollService.State.ACTIVE;

            runtimeIdRecreated=
                zeroRestored!=null&&
                !sourceId.equals(
                    zeroRestored.taskScrollId
                )&&
                !assignedExtension.containsKey(
                    "task-scroll-id"
                )&&
                !assignedExtension.containsKey(
                    "id"
                );

            require(
                assignedRestored&&
                progress0Restored&&
                runtimeIdRecreated,
                "zero-progress fresh World restore"
            );

            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    zeroWorld,
                    PLAYER,
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                    new Tile(
                        MovementState.INITIAL_X,
                        MovementState.INITIAL_Y,
                        0
                    ),
                    1L
                );
            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    zeroWorld,
                    PLAYER,
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                    new Tile(
                        MovementState.INITIAL_X,
                        MovementState.INITIAL_Y,
                        0
                    ),
                    2L
                );

            PlayerSnapshot partialSnapshot=
                PlayerSnapshotCodec.capture(
                    PLAYER,
                    zeroPlayer
                );

            require(
                "2".equals(
                    partialSnapshot.value(
                        PlayerSnapshotExtensionState
                            .PREFIX+
                        LocalLabTaskScrollPersistence
                            .NAMESPACE+
                        ".progress"
                    )
                ),
                "captured Task Scroll progress 2"
            );

            repository.save(
                partialSnapshot
            );

            if(zeroWorld.players().owns(
                    dummy,
                    dummyGeneration))
                zeroWorld.unregisterPlayer(
                    dummy,
                    dummyGeneration
                );

            zeroWorld.close();

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

            TaskScrollService.Snapshot partial=
                partialWorld.localLabTaskScrolls()
                    .active(
                        PLAYER
                    );

            progress2Restored=
                partial!=null&&
                partial.objective.progress==2L&&
                partial.objective.goal==3L&&
                !partial.objective.complete&&
                partial.state==
                    TaskScrollService.State.ACTIVE;

            require(
                progress2Restored,
                "partial Task Scroll restore"
            );

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

            TaskScrollService.Snapshot completed=
                partialWorld.localLabTaskScrolls()
                    .active(
                        PLAYER
                    );

            require(
                completed.objective.progress==3L&&
                completed.objective.complete&&
                completed.state==
                    TaskScrollService.State
                        .COMPLETE_UNCLAIMED,
                "source completed Task Scroll"
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
            loadAndRegister(
                completedWorld,
                repository,
                PLAYER
            );

            TaskScrollService.Snapshot completeRestored=
                completedWorld.localLabTaskScrolls()
                    .active(
                        PLAYER
                    );

            complete3Restored=
                completeRestored!=null&&
                completeRestored.objective.progress==3L&&
                completeRestored.objective.complete&&
                !completeRestored.objective.claimed&&
                !completeRestored.tracked&&
                completeRestored.state==
                    TaskScrollService.State
                        .COMPLETE_UNCLAIMED;

            require(
                complete3Restored,
                "completed Task Scroll restore"
            );

            completedWorld.close();

            World freshWorld=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer freshPlayer=
                new WorldPlayer();

            freshWorld.registerPlayer(
                freshPlayer,
                "task-scroll-fresh"
            );

            freshNoSnapshotUnassigned=
                freshWorld.localLabTaskScrolls()
                    .active(
                        "task-scroll-fresh"
                    )==null&&
                freshWorld.localLabTaskScrolls()
                    .assignmentCount()==0&&
                freshPlayer.snapshotExtensions()
                    .namespace(
                        LocalLabTaskScrollPersistence
                            .NAMESPACE
                    ).isEmpty();

            freshWorld.close();

            World malformedWorld=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer malformedPlayer=
                new WorldPlayer();

            TreeMap<String,String> malformed=
                validExtension(
                    1L
                );
            malformed.put(
                "authority",
                "FOREIGN_TASK_SCROLL_POLICY"
            );

            malformedPlayer.snapshotExtensions()
                .replaceNamespace(
                    LocalLabTaskScrollPersistence
                        .NAMESPACE,
                    malformed
                );

            malformedWorld.registerPlayer(
                malformedPlayer,
                "task-scroll-malformed"
            );

            SortedMap<String,String> malformedBefore=
                new TreeMap<>(
                    malformedPlayer
                        .snapshotExtensions()
                        .namespace(
                            LocalLabTaskScrollPersistence
                                .NAMESPACE
                        )
                );

            boolean malformedActiveRejected=false;
            boolean malformedAssignRejected=false;

            try{
                malformedWorld.localLabTaskScrolls()
                    .active(
                        "task-scroll-malformed"
                    );
            }catch(IllegalStateException expected){
                malformedActiveRejected=true;
            }

            try{
                malformedWorld.localLabTaskScrolls()
                    .assign(
                        "task-scroll-malformed",
                        1L
                    );
            }catch(IllegalStateException expected){
                malformedAssignRejected=true;
            }

            malformedFailClosed=
                malformedActiveRejected&&
                malformedAssignRejected&&
                malformedBefore.equals(
                    malformedPlayer
                        .snapshotExtensions()
                        .namespace(
                            LocalLabTaskScrollPersistence
                                .NAMESPACE
                        )
                )&&
                malformedWorld.localLabTaskScrolls()
                    .assignmentCount()==0;

            malformedWorld.close();

            TreeMap<String,String> drift=
                validExtension(
                    1L
                );
            drift.put(
                "goal",
                "4"
            );

            try{
                LocalLabTaskScrollPersistence
                    .decode(
                        drift
                    );
            }catch(IllegalArgumentException expected){
                contractDriftRejected=true;
            }

            require(
                assignedRestored&&
                progress0Restored&&
                progress2Restored&&
                complete3Restored&&
                freshNoSnapshotUnassigned&&
                malformedFailClosed&&
                contractDriftRejected&&
                runtimeIdRecreated&&
                rewardStateAbsent&&
                trackStateAbsent,
                "G15.4 acceptance"
            );

            System.out.println(
                "G154_TASK_SCROLL_PERSISTENCE_PASS"+
                " assignedRestored="+
                    assignedRestored+
                " progress0Restored="+
                    progress0Restored+
                " progress2Restored="+
                    progress2Restored+
                " complete3Restored="+
                    complete3Restored+
                " freshNoSnapshotUnassigned="+
                    freshNoSnapshotUnassigned+
                " malformedFailClosed="+
                    malformedFailClosed+
                " contractDriftRejected="+
                    contractDriftRejected+
                " runtimeIdRecreated="+
                    runtimeIdRecreated+
                " rewardStateAbsent="+
                    rewardStateAbsent+
                " trackStateAbsent="+
                    trackStateAbsent+
                " originalPersistenceClaim=false"
            );
        }finally{
            deleteTree(
                root
            );
        }
    }

    private static TreeMap<String,String> validExtension(
        long progress
    ){
        TreeMap<String,String> values=
            new TreeMap<>();

        values.put(
            "version",
            LocalLabTaskScrollPersistence.VERSION
        );
        values.put(
            "authority",
            LocalLabTaskScrollRuntime.AUTHORITY
        );
        values.put(
            "task-key",
            LocalLabTaskScrollRuntime.TASK_KEY
        );
        values.put(
            "objective-key",
            LocalLabTaskScrollRuntime.OBJECTIVE_KEY
        );
        values.put(
            "progress",
            Long.toString(
                progress
            )
        );
        values.put(
            "goal",
            Long.toString(
                LocalLabTaskScrollRuntime.GOAL
            )
        );
        values.put(
            "state",
            progress==
                LocalLabTaskScrollRuntime.GOAL
                ?"COMPLETE_UNCLAIMED"
                :"ACTIVE"
        );

        return values;
    }

    private static WorldPlayer loadAndRegister(
        World world,
        FilePlayerRepository repository,
        String username
    )throws Exception{
        Optional<PlayerSnapshot> loaded=
            repository.load(
                username
            );

        require(
            loaded.isPresent(),
            "missing persisted Task Scroll snapshot "+
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

    private G154TaskScrollPersistenceIntegrationTest(){}
}
