package spk.local;

import java.nio.file.*;
import java.util.*;

public final class G6BloodSlayerCompletionCountTest {
    private static final String OWNER="g6-completion-count";

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory(
            "spawnpk-g6-slayer-count-"
        );

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->root.resolve(
                    username+".properties"
                )
            );

        boolean startsZero=false;
        boolean firstMonsterCompletionOne=false;
        boolean duplicateSafe=false;
        boolean bossOutsideNoIncrement=false;
        boolean bossCompletionTwo=false;
        boolean modeSwitchPreservesTwo=false;
        boolean secondMonsterCompletionThree=false;
        boolean freshWorldRestoresThree=false;
        boolean legacyV1LoadsZero=false;
        boolean modeV2LoadsZero=false;
        boolean completionV2PreservesCount=false;
        boolean malformedCountRejected=false;
        boolean statusExposesCount=false;

        try{
            World world=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer player=new WorldPlayer();
            world.registerPlayer(player,OWNER);

            LocalLabSlayerRuntime runtime=
                world.localLabSlayer();

            LocalLabSlayerRuntime.StartResult first=
                runtime.startMonsterHunter(
                    OWNER,
                    world.clock().tick()
                );

            startsZero=
                first.created&&
                first.status.completions==0L;

            require(
                startsZero,
                "new Slayer player did not start at zero completions"
            );

            LocalLabSlayerRuntime.KillCreditResult
                firstKill=
                    runtime.recordMonsterSpawnerKill(
                        OWNER,
                        LocalLabSlayerRuntime.TARGET_DEFINITION_ID,
                        world.clock().tick()
                    );

            firstMonsterCompletionOne=
                firstKill.completedNow&&
                firstKill.status.completions==1L;

            require(
                firstMonsterCompletionOne,
                "first completion did not advance count to one"
            );

            LocalLabSlayerRuntime.KillCreditResult
                duplicate=
                    runtime.recordMonsterSpawnerKill(
                        OWNER,
                        LocalLabSlayerRuntime.TARGET_DEFINITION_ID,
                        world.clock().tick()
                    );

            duplicateSafe=
                !duplicate.completedNow&&
                duplicate.status.completions==1L;

            require(
                duplicateSafe,
                "duplicate/no-active kill changed completion count"
            );

            LocalLabSlayerRuntime.StartResult boss=
                runtime.startBossHunter(
                    OWNER,
                    world.clock().tick()
                );

            require(
                boss.created&&
                boss.status.active()&&
                boss.status.completions==1L,
                "Boss Hunter renewal did not preserve Monster completion count"
            );

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
                    LocalLabSlayerRuntime.BOSS_REGION_ID,
                    0
                );

            require(
                outside!=null&&
                bossTile!=null&&
                LocalLabSlayerRuntime.regionId(outside)!=
                    LocalLabSlayerRuntime.BOSS_REGION_ID&&
                LocalLabSlayerRuntime.regionId(bossTile)==
                    LocalLabSlayerRuntime.BOSS_REGION_ID,
                "missing Boss Hunter region authority"
            );

            LocalLabSlayerRuntime.KillCreditResult
                outsideBoss=
                    runtime.recordMonsterSpawnerFinalization(
                        OWNER,
                        LocalLabSlayerRuntime.TARGET_DEFINITION_ID,
                        outside,
                        world.clock().tick()
                    );

            bossOutsideNoIncrement=
                !outsideBoss.progressed&&
                !outsideBoss.completedNow&&
                outsideBoss.status.active()&&
                outsideBoss.status.completions==1L;

            require(
                bossOutsideNoIncrement,
                "out-of-region Boss credit changed completion count"
            );

            LocalLabSlayerRuntime.KillCreditResult
                bossKill=
                    runtime.recordMonsterSpawnerFinalization(
                        OWNER,
                        LocalLabSlayerRuntime.TARGET_DEFINITION_ID,
                        bossTile,
                        world.clock().tick()
                    );

            bossCompletionTwo=
                bossKill.completedNow&&
                bossKill.status.complete()&&
                bossKill.status.completions==2L;

            require(
                bossCompletionTwo,
                "Boss Hunter completion did not advance total count to two"
            );

            LocalLabSlayerRuntime.StartResult renewed=
                runtime.startMonsterHunter(
                    OWNER,
                    world.clock().tick()
                );

            modeSwitchPreservesTwo=
                renewed.created&&
                renewed.status.active()&&
                renewed.status.completions==2L;

            require(
                modeSwitchPreservesTwo,
                "cross-mode renewal did not preserve total completion count"
            );

            LocalLabSlayerRuntime.KillCreditResult
                secondKill=
                    runtime.recordMonsterSpawnerKill(
                        OWNER,
                        LocalLabSlayerRuntime.TARGET_DEFINITION_ID,
                        world.clock().tick()
                    );

            secondMonsterCompletionThree=
                secondKill.completedNow&&
                secondKill.status.complete()&&
                secondKill.status.completions==3L;

            require(
                secondMonsterCompletionThree,
                "second Monster completion did not advance total count to three"
            );

            LocalSlayerCommandHandler.Result commandStatus=
                new LocalSlayerCommandHandler(
                    player,
                    runtime
                ).handle(
                    new String[]{"slayer","status"},
                    world.clock().tick()
                );

            statusExposesCount=
                runtime.status(OWNER)
                    .completions==3L&&
                commandStatus!=null&&
                commandStatus.logText.contains(
                    "bloodSlayerCompletions=3"
                )&&
                commandStatus.clientMessage.contains(
                    "completions=3"
                );

            require(
                statusExposesCount,
                "runtime/command status did not expose completion count"
            );

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture(
                    OWNER,
                    player
                );

            require(
                LocalLabSlayerPersistence.VERSION.equals(
                    snapshot.value(
                        "extension."+
                        LocalLabSlayerPersistence.NAMESPACE+
                        ".version"
                    )
                )&&
                "3".equals(
                    snapshot.value(
                        "extension."+
                        LocalLabSlayerPersistence.NAMESPACE+
                        ".completion-count"
                    )
                ),
                "completion count was not persisted"
            );

            repository.save(snapshot);
            world.close();

            World restoredWorld=
                World.isolatedForTest(
                    60_000L,
                    repository
                );
            WorldPlayer restored=
                loadAndRegister(
                    restoredWorld,
                    repository,
                    OWNER
                );

            LocalLabSlayerRuntime.StatusSnapshot
                restoredStatus=
                    restoredWorld.localLabSlayer()
                        .status(OWNER);

            freshWorldRestoresThree=
                restoredStatus.persistenceValid()&&
                restoredStatus.complete()&&
                restoredStatus.completions==3L;

            require(
                freshWorldRestoresThree,
                "fresh World did not restore completion count"
            );

            restoredWorld.close();

            SortedMap<String,String> v2=
                new TreeMap<>(
                    snapshotExtensions(snapshot)
                );
            TreeMap<String,String> v1=
                new TreeMap<>(v2);
            v1.put(
                "version",
                LocalLabSlayerPersistence.LEGACY_VERSION
            );
            v1.put(
                "authority",
                LocalLabSlayerRuntime.LEGACY_AUTHORITY
            );
            v1.remove("completion-count");

            LocalLabSlayerPersistence.Snapshot legacy=
                LocalLabSlayerPersistence.decode(v1);

            legacyV1LoadsZero=
                legacy!=null&&
                legacy.completions==0L;

            require(
                legacyV1LoadsZero,
                "legacy v1 snapshot did not default completions to zero"
            );

            TreeMap<String,String> modeV2=
                new TreeMap<>(v2);
            modeV2.put(
                "version",
                LocalLabSlayerPersistence.MODE_VERSION
            );
            modeV2.remove("completion-count");

            LocalLabSlayerPersistence.Snapshot
                decodedModeV2=
                    LocalLabSlayerPersistence.decode(
                        modeV2
                    );

            modeV2LoadsZero=
                decodedModeV2!=null&&
                decodedModeV2.mode==
                    BloodSlayerModeService.Mode
                        .MONSTER_HUNTER_PVM&&
                decodedModeV2.completions==0L;

            require(
                modeV2LoadsZero,
                "mode-aware v2 snapshot did not default completions to zero"
            );

            TreeMap<String,String> completionV2=
                new TreeMap<>(v2);
            completionV2.put(
                "version",
                LocalLabSlayerPersistence.MODE_VERSION
            );
            completionV2.put(
                "authority",
                LocalLabSlayerRuntime.LEGACY_AUTHORITY
            );

            LocalLabSlayerPersistence.Snapshot
                decodedCompletionV2=
                    LocalLabSlayerPersistence.decode(
                        completionV2
                    );

            completionV2PreservesCount=
                decodedCompletionV2!=null&&
                decodedCompletionV2.mode==
                    BloodSlayerModeService.Mode
                        .MONSTER_HUNTER_PVM&&
                decodedCompletionV2.completions==3L;

            require(
                completionV2PreservesCount,
                "completion-aware sibling v2 snapshot lost count"
            );

            TreeMap<String,String> malformed=
                new TreeMap<>(v2);
            malformed.put(
                "completion-count",
                "-1"
            );

            try{
                LocalLabSlayerPersistence.decode(
                    malformed
                );
            }catch(IllegalArgumentException expected){
                malformedCountRejected=true;
            }

            require(
                malformedCountRejected,
                "negative completion count was accepted"
            );

            System.out.println(
                "G63_BLOOD_SLAYER_COMPLETION_COUNT_PASS"+
                " startsZero="+startsZero+
                " firstMonsterCompletionOne="+firstMonsterCompletionOne+
                " duplicateSafe="+duplicateSafe+
                " modeSwitchPreservesTwo="+modeSwitchPreservesTwo+
                " bossCompletionTwo="+bossCompletionTwo+
                " secondMonsterCompletionThree="+secondMonsterCompletionThree+
                " freshWorldRestoresThree="+freshWorldRestoresThree+
                " legacyV1LoadsZero="+legacyV1LoadsZero+
                " modeV2LoadsZero="+modeV2LoadsZero+
                " completionV2PreservesCount="+completionV2PreservesCount+
                " malformedCountRejected="+malformedCountRejected+
                " statusExposesCount="+statusExposesCount+
                " pointsClaim=false"+
                " rewardClaim=false"+
                " originalSpawnpkTaskPolicyClaim=false"
            );
        }finally{
            deleteTree(root);
        }
    }

    private static SortedMap<String,String>
        snapshotExtensions(
            PlayerSnapshot snapshot
        ){
        TreeMap<String,String> out=
            new TreeMap<>();
        String prefix=
            "extension."+
            LocalLabSlayerPersistence.NAMESPACE+
            ".";

        for(Map.Entry<String,String> entry:
                snapshot.values().entrySet())
            if(entry.getKey().startsWith(prefix))
                out.put(
                    entry.getKey().substring(
                        prefix.length()
                    ),
                    entry.getValue()
                );

        return out;
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
            "missing persisted snapshot"
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

    private static void deleteTree(
        Path root
    )throws Exception{
        if(root==null||!Files.exists(root))
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
            throw new AssertionError(message);
    }

    private G6BloodSlayerCompletionCountTest(){}
}
