package spk.local;

import java.nio.file.*;
import java.util.*;

public final class G165AdventurePersistenceIntegrationTest {
    private static final String PLAYER=
        "adventure-persist";

    public static void main(String[] args)throws Exception{
        Path root=
            Files.createTempDirectory(
                "spawnpk-g165-adventure-"
            );

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->
                    root.resolve(
                        username+
                        ".properties"
                    )
            );

        boolean activated0Restored=false;
        boolean progress2Restored=false;
        boolean complete3Restored=false;
        boolean freshNoSnapshotUnactivated=false;
        boolean malformedFailClosed=false;
        boolean contractDriftRejected=false;
        boolean rewardStateAbsent=false;
        boolean inputStateAbsent=false;

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

            LocalLabAdventureRuntime.ActivationResult
                activated=
                    source.localLabAdventures()
                        .activate(
                            PLAYER
                        );

            SortedMap<String,String> zeroExtension=
                sourcePlayer.snapshotExtensions()
                    .namespace(
                        LocalLabAdventurePersistence
                            .NAMESPACE
                    );

            require(
                activated.activatedNow&&
                "0".equals(
                    zeroExtension.get(
                        "progress"
                    )
                )&&
                "ACTIVE".equals(
                    zeroExtension.get(
                        "state"
                    )
                ),
                "Adventure activation persistence"
            );

            rewardStateAbsent=
                noKeysContaining(
                    zeroExtension,
                    "reward",
                    "claim",
                    "casket",
                    "currency"
                );

            inputStateAbsent=
                noKeysContaining(
                    zeroExtension,
                    "widget",
                    "teleport",
                    "tips",
                    "input",
                    "opcode",
                    "packet"
                );

            repository.save(
                PlayerSnapshotCodec.capture(
                    PLAYER,
                    sourcePlayer
                )
            );
            source.close();

            World zeroWorld=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer zeroPlayer=
                loadAndRegister(
                    zeroWorld,
                    repository,
                    PLAYER
                );

            ObjectiveProgressService.Snapshot
                restoredZero=
                    zeroWorld.localLabAdventures()
                        .objective(
                            PLAYER
                        );

            activated0Restored=
                restoredZero!=null&&
                restoredZero.progress==0L&&
                restoredZero.goal==3L&&
                !restoredZero.complete&&
                zeroWorld.localLabAdventures()
                    .activePlayerCount()==1&&
                !zeroWorld.localLabAdventures()
                    .activate(
                        PLAYER
                    ).activatedNow;

            require(
                activated0Restored,
                "Adventure 0/3 fresh World restore"
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
                        LocalLabAdventurePersistence
                            .NAMESPACE+
                        ".progress"
                    )
                ),
                "captured Adventure progress 2"
            );

            repository.save(
                partialSnapshot
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

            ObjectiveProgressService.Snapshot
                restoredTwo=
                    partialWorld.localLabAdventures()
                        .objective(
                            PLAYER
                        );

            progress2Restored=
                restoredTwo!=null&&
                restoredTwo.progress==2L&&
                restoredTwo.goal==3L&&
                !restoredTwo.complete&&
                !restoredTwo.claimed;

            require(
                progress2Restored,
                "Adventure 2/3 fresh World restore"
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

            ObjectiveProgressService.Snapshot completed=
                partialWorld.localLabAdventures()
                    .objective(
                        PLAYER
                    );

            require(
                completed.progress==3L&&
                completed.complete&&
                !completed.claimed,
                "source Adventure completion"
            );

            repository.save(
                PlayerSnapshotCodec.capture(
                    PLAYER,
                    partialPlayer
                )
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

            ObjectiveProgressService.Snapshot
                restoredComplete=
                    completedWorld.localLabAdventures()
                        .objective(
                            PLAYER
                        );

            complete3Restored=
                restoredComplete!=null&&
                restoredComplete.progress==3L&&
                restoredComplete.goal==3L&&
                restoredComplete.complete&&
                !restoredComplete.claimed&&
                completedWorld.localLabAdventures()
                    .projection(
                        PLAYER
                    ).rows()
                    .get(0)
                    .claimWidget()!=null;

            require(
                complete3Restored,
                "Adventure 3/3 fresh World restore"
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
                "adventure-fresh"
            );

            freshNoSnapshotUnactivated=
                freshWorld.localLabAdventures()
                    .snapshot(
                        "adventure-fresh"
                    )==null&&
                freshWorld.localLabAdventures()
                    .objective(
                        "adventure-fresh"
                    )==null&&
                freshWorld.localLabAdventures()
                    .activePlayerCount()==0&&
                freshPlayer.snapshotExtensions()
                    .namespace(
                        LocalLabAdventurePersistence
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
                "FOREIGN_ADVENTURE_POLICY"
            );

            malformedPlayer.snapshotExtensions()
                .replaceNamespace(
                    LocalLabAdventurePersistence
                        .NAMESPACE,
                    malformed
                );

            malformedWorld.registerPlayer(
                malformedPlayer,
                "adventure-malformed"
            );

            SortedMap<String,String> before=
                new TreeMap<>(
                    malformedPlayer
                        .snapshotExtensions()
                        .namespace(
                            LocalLabAdventurePersistence
                                .NAMESPACE
                        )
                );

            boolean readRejected=false;
            boolean activateRejected=false;

            try{
                malformedWorld.localLabAdventures()
                    .objective(
                        "adventure-malformed"
                    );
            }catch(IllegalStateException expected){
                readRejected=true;
            }

            try{
                malformedWorld.localLabAdventures()
                    .activate(
                        "adventure-malformed"
                    );
            }catch(IllegalStateException expected){
                activateRejected=true;
            }

            malformedFailClosed=
                readRejected&&
                activateRejected&&
                malformedWorld.localLabAdventures()
                    .activePlayerCount()==0&&
                before.equals(
                    malformedPlayer
                        .snapshotExtensions()
                        .namespace(
                            LocalLabAdventurePersistence
                                .NAMESPACE
                        )
                );

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
                LocalLabAdventurePersistence
                    .decode(
                        drift
                    );
            }catch(IllegalArgumentException expected){
                contractDriftRejected=true;
            }

            require(
                activated0Restored&&
                progress2Restored&&
                complete3Restored&&
                freshNoSnapshotUnactivated&&
                malformedFailClosed&&
                contractDriftRejected&&
                rewardStateAbsent&&
                inputStateAbsent,
                "G16.5 acceptance"
            );

            System.out.println(
                "G165_ADVENTURE_PERSISTENCE_PASS"+
                " activated0Restored="+
                    activated0Restored+
                " progress2Restored="+
                    progress2Restored+
                " complete3Restored="+
                    complete3Restored+
                " freshNoSnapshotUnactivated="+
                    freshNoSnapshotUnactivated+
                " malformedFailClosed="+
                    malformedFailClosed+
                " contractDriftRejected="+
                    contractDriftRejected+
                " rewardStateAbsent="+
                    rewardStateAbsent+
                " inputStateAbsent="+
                    inputStateAbsent+
                " originalPersistenceClaim=false"
            );
        }finally{
            deleteTree(
                root
            );
        }
    }

    private static boolean noKeysContaining(
        SortedMap<String,String> values,
        String... needles
    ){
        for(String key:values.keySet()){
            String lower=
                key.toLowerCase(
                    Locale.ROOT
                );

            for(String needle:needles)
                if(lower.contains(
                        needle))
                    return false;
        }

        return true;
    }

    private static TreeMap<String,String> validExtension(
        long progress
    ){
        TreeMap<String,String> values=
            new TreeMap<>();

        values.put(
            "version",
            LocalLabAdventurePersistence.VERSION
        );
        values.put(
            "authority",
            LocalLabAdventureRuntime.AUTHORITY
        );
        values.put(
            "chapter-key",
            LocalLabAdventureRuntime.CHAPTER_KEY
        );
        values.put(
            "objective-key",
            LocalLabAdventureRuntime.OBJECTIVE_KEY
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
                LocalLabAdventureRuntime.GOAL
            )
        );
        values.put(
            "state",
            progress==
                LocalLabAdventureRuntime.GOAL
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
            "missing persisted Adventure snapshot "+
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

    private G165AdventurePersistenceIntegrationTest(){}
}
