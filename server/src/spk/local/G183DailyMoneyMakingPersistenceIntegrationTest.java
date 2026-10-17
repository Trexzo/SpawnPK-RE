package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;

public final class G183DailyMoneyMakingPersistenceIntegrationTest {
    private static final int[] SEED={171,172,173,174};

    public static void main(String[] args)throws Exception{
        Path root=
            Files.createTempDirectory(
                "spawnpk-g183-dmm-"
            );

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->
                    root.resolve(
                        username+
                        ".properties"
                    )
            );

        boolean easyRestored=false;
        boolean mediumRestored=false;
        boolean hardRestored=false;
        boolean freshNoSnapshotUnselected=false;
        boolean malformedFailClosed=false;
        boolean contractDriftRejected=false;
        boolean trackedObjectiveAbsent=false;
        boolean rewardStateAbsent=false;
        boolean teleportStateAbsent=false;

        try{
            easyRestored=
                roundTrip(
                    repository,
                    "dmm-persist-easy",
                    DailyMoneyMakingPresentation
                        .EASY_WIDGET,
                    DailyMoneyMakingStateService
                        .Difficulty.EASY
                );

            mediumRestored=
                roundTrip(
                    repository,
                    "dmm-persist-medium",
                    DailyMoneyMakingPresentation
                        .MEDIUM_WIDGET,
                    DailyMoneyMakingStateService
                        .Difficulty.MEDIUM
                );

            hardRestored=
                roundTrip(
                    repository,
                    "dmm-persist-hard",
                    DailyMoneyMakingPresentation
                        .HARD_WIDGET,
                    DailyMoneyMakingStateService
                        .Difficulty.HARD
                );

            WorldPlayer fresh=
                new WorldPlayer();

            LocalDailyMoneyMakingUiHandler
                freshHandler=
                    new LocalDailyMoneyMakingUiHandler(
                        fresh
                    );

            freshNoSnapshotUnselected=
                freshHandler.snapshot()
                    .selectedDifficulty==null&&
                freshHandler.snapshot()
                    .trackedObjectiveKey==null&&
                fresh.snapshotExtensions()
                    .namespace(
                        LocalDailyMoneyMakingPersistence
                            .NAMESPACE
                    ).isEmpty();

            TreeMap<String,String> valid=
                validExtension(
                    DailyMoneyMakingStateService
                        .Difficulty.EASY
                );

            trackedObjectiveAbsent=
                noKeysContaining(
                    valid,
                    "track",
                    "objective",
                    "progress",
                    "goal"
                );

            rewardStateAbsent=
                noKeysContaining(
                    valid,
                    "reward",
                    "claim",
                    "item",
                    "currency",
                    "casket"
                );

            teleportStateAbsent=
                noKeysContaining(
                    valid,
                    "teleport",
                    "destination",
                    "eligibility",
                    "widget",
                    "packet",
                    "opcode",
                    "target"
                );

            WorldPlayer malformedPlayer=
                new WorldPlayer();

            TreeMap<String,String> malformed=
                validExtension(
                    DailyMoneyMakingStateService
                        .Difficulty.MEDIUM
                );
            malformed.put(
                "authority",
                "FOREIGN_DAILY_MONEY_MAKING_POLICY"
            );

            malformedPlayer.snapshotExtensions()
                .replaceNamespace(
                    LocalDailyMoneyMakingPersistence
                        .NAMESPACE,
                    malformed
                );

            SortedMap<String,String> before=
                new TreeMap<>(
                    malformedPlayer
                        .snapshotExtensions()
                        .namespace(
                            LocalDailyMoneyMakingPersistence
                                .NAMESPACE
                        )
                );

            LocalDailyMoneyMakingUiHandler
                malformedHandler=
                    new LocalDailyMoneyMakingUiHandler(
                        malformedPlayer
                    );

            ByteArrayOutputStream malformedWire=
                new ByteArrayOutputStream();

            LocalDailyMoneyMakingUiHandler.Result
                rejected=
                    LocalSessionUiActionHandler
                        .dispatchDailyMoneyMakingWidget(
                            malformedHandler,
                            DailyMoneyMakingPresentation
                                .EASY_WIDGET,
                            new ServerPacketWriter(
                                malformedWire,
                                new IsaacCipher(
                                    SEED.clone()
                                )
                            )
                        );

            malformedFailClosed=
                rejected!=null&&
                "INVALID_PERSISTENCE".equals(
                    rejected.status
                )&&
                !rejected.stateChanged&&
                rejected.snapshot
                    .selectedDifficulty==null&&
                rejected.snapshot
                    .trackedObjectiveKey==null&&
                malformedWire.size()==0&&
                before.equals(
                    malformedPlayer
                        .snapshotExtensions()
                        .namespace(
                            LocalDailyMoneyMakingPersistence
                                .NAMESPACE
                        )
                );

            TreeMap<String,String> drift=
                validExtension(
                    DailyMoneyMakingStateService
                        .Difficulty.HARD
                );
            drift.put(
                "tracked-objective",
                "dmm:foreign"
            );

            try{
                LocalDailyMoneyMakingPersistence
                    .decode(
                        drift
                    );
            }catch(IllegalArgumentException expected){
                contractDriftRejected=true;
            }

            require(
                easyRestored&&
                mediumRestored&&
                hardRestored&&
                freshNoSnapshotUnselected&&
                malformedFailClosed&&
                contractDriftRejected&&
                trackedObjectiveAbsent&&
                rewardStateAbsent&&
                teleportStateAbsent,
                "G18.3 acceptance"
            );

            System.out.println(
                "G183_DAILY_MONEY_MAKING_PERSISTENCE_PASS"+
                " easyRestored="+easyRestored+
                " mediumRestored="+mediumRestored+
                " hardRestored="+hardRestored+
                " freshNoSnapshotUnselected="+
                    freshNoSnapshotUnselected+
                " malformedFailClosed="+
                    malformedFailClosed+
                " contractDriftRejected="+
                    contractDriftRejected+
                " trackedObjectiveAbsent="+
                    trackedObjectiveAbsent+
                " rewardStateAbsent="+
                    rewardStateAbsent+
                " teleportStateAbsent="+
                    teleportStateAbsent+
                " originalPersistenceClaim=false"
            );
        }finally{
            deleteTree(
                root
            );
        }
    }

    private static boolean roundTrip(
        FilePlayerRepository repository,
        String username,
        int widget,
        DailyMoneyMakingStateService.Difficulty
            expected
    )throws Exception{
        World source=
            World.isolatedForTest(
                60_000L,
                repository
            );
        WorldPlayer sourcePlayer=
            new WorldPlayer();

        source.registerPlayer(
            sourcePlayer,
            username
        );

        LocalDailyMoneyMakingUiHandler handler=
            new LocalDailyMoneyMakingUiHandler(
                sourcePlayer
            );

        ByteArrayOutputStream sourceWire=
            new ByteArrayOutputStream();

        LocalDailyMoneyMakingUiHandler.Result
            selected=
                LocalSessionUiActionHandler
                    .dispatchDailyMoneyMakingWidget(
                        handler,
                        widget,
                        new ServerPacketWriter(
                            sourceWire,
                            new IsaacCipher(
                                SEED.clone()
                            )
                        )
                    );

        require(
            selected!=null&&
            selected.succeeded()&&
            selected.snapshot
                .selectedDifficulty==expected&&
            sourceWire.size()>0,
            "source Daily Money Making selection "+
            expected
        );

        SortedMap<String,String> extension=
            sourcePlayer.snapshotExtensions()
                .namespace(
                    LocalDailyMoneyMakingPersistence
                        .NAMESPACE
                );

        require(
            LocalDailyMoneyMakingPersistence
                .VERSION
                .equals(
                    extension.get(
                        "version"
                    )
                )&&
            LocalDailyMoneyMakingPersistence
                .AUTHORITY
                .equals(
                    extension.get(
                        "authority"
                    )
                )&&
            expected.name().equals(
                extension.get(
                    "selected-difficulty"
                )
            )&&
            extension.size()==3,
            "source persistence "+
            expected
        );

        repository.save(
            PlayerSnapshotCodec.capture(
                username,
                sourcePlayer
            )
        );
        source.close();

        Optional<PlayerSnapshot> loaded=
            repository.load(
                username
            );

        require(
            loaded.isPresent(),
            "missing Daily Money Making snapshot "+
            username
        );

        World freshWorld=
            World.isolatedForTest(
                60_000L,
                repository
            );
        WorldPlayer freshPlayer=
            new WorldPlayer();

        PlayerSnapshotCodec.applyValidated(
            loaded.get(),
            freshPlayer
        );

        freshWorld.registerPlayer(
            freshPlayer,
            username
        );

        LocalDailyMoneyMakingUiHandler restored=
            new LocalDailyMoneyMakingUiHandler(
                freshPlayer
            );

        boolean restoredState=
            restored.snapshot()
                .selectedDifficulty==expected&&
            restored.snapshot()
                .trackedObjectiveKey==null;

        ByteArrayOutputStream republishWire=
            new ByteArrayOutputStream();

        LocalDailyMoneyMakingUiHandler.Result
            republished=
                LocalSessionUiActionHandler
                    .dispatchDailyMoneyMakingWidget(
                        restored,
                        widget,
                        new ServerPacketWriter(
                            republishWire,
                            new IsaacCipher(
                                SEED.clone()
                            )
                        )
                    );

        boolean duplicateStable=
            republished!=null&&
            republished.succeeded()&&
            !republished.stateChanged&&
            republished.snapshot
                .selectedDifficulty==expected&&
            republished.snapshot
                .trackedObjectiveKey==null&&
            republishWire.size()>0;

        freshWorld.close();

        return restoredState&&
            duplicateStable;
    }

    private static TreeMap<String,String>
        validExtension(
            DailyMoneyMakingStateService.Difficulty
                difficulty
        )
    {
        return new TreeMap<>(
            LocalDailyMoneyMakingPersistence
                .encode(
                    difficulty
                )
        );
    }

    private static boolean noKeysContaining(
        Map<String,String> values,
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

    private G183DailyMoneyMakingPersistenceIntegrationTest(){}
}
