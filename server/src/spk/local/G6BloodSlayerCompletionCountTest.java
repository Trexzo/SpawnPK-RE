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
        boolean firstCompletionOne=false;
        boolean duplicateSafe=false;
        boolean renewalPreservesOne=false;
        boolean bossCompletionNoIncrement=false;
        boolean secondCompletionTwo=false;
        boolean freshWorldRestoresTwo=false;
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

            firstCompletionOne=
                firstKill.completedNow&&
                firstKill.status.completions==1L;

            require(
                firstCompletionOne,
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

            Tile bossTile=
                WorldCollisionAuthority.safeTile(
                    LocalLabSlayerRuntime.BOSS_REGION_ID,
                    0
                );

            require(
                bossTile!=null&&
                LocalLabSlayerRuntime.regionId(bossTile)==
                    LocalLabSlayerRuntime.BOSS_REGION_ID,
                "missing Boss Hunter region tile"
            );

            LocalLabSlayerRuntime.KillCreditResult
                bossKill=
                    runtime.recordMonsterSpawnerFinalization(
                        OWNER,
                        LocalLabSlayerRuntime.TARGET_DEFINITION_ID,
                        bossTile,
                        world.clock().tick()
                    );

            bossCompletionNoIncrement=
                bossKill.completedNow&&
                bossKill.status.complete()&&
                bossKill.status.completions==1L;

            require(
                bossCompletionNoIncrement,
                "Boss Hunter completion mutated Monster completion count"
            );

            LocalLabSlayerRuntime.StartResult renewed=
                runtime.startMonsterHunter(
                    OWNER,
                    world.clock().tick()
                );

            renewalPreservesOne=
                renewed.created&&
                renewed.status.active()&&
                renewed.status.completions==1L;

            require(
                renewalPreservesOne,
                "cross-mode renewal did not preserve first Monster completion"
            );

            LocalLabSlayerRuntime.KillCreditResult
                secondKill=
                    runtime.recordMonsterSpawnerKill(
                        OWNER,
                        LocalLabSlayerRuntime.TARGET_DEFINITION_ID,
                        world.clock().tick()
                    );

            secondCompletionTwo=
                secondKill.completedNow&&
                secondKill.status.complete()&&
                secondKill.status.completions==2L;

            require(
                secondCompletionTwo,
                "second completion did not advance count to two"
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
                    .completions==2L&&
                commandStatus!=null&&
                commandStatus.logText.contains(
                    "monsterHunterCompletions=2"
                )&&
                commandStatus.clientMessage.contains(
                    "completions=2"
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
                "2".equals(
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

            freshWorldRestoresTwo=
                restoredStatus.persistenceValid()&&
                restoredStatus.complete()&&
                restoredStatus.completions==2L;

            require(
                freshWorldRestoresTwo,
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
                decodedCompletionV2.completions==2L;

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
                "G6_BLOOD_SLAYER_COMPLETION_COUNT_PASS"+
                " startsZero="+startsZero+
                " firstCompletionOne="+firstCompletionOne+
                " duplicateSafe="+duplicateSafe+
                " renewalPreservesOne="+renewalPreservesOne+
                " bossCompletionNoIncrement="+bossCompletionNoIncrement+
                " secondCompletionTwo="+secondCompletionTwo+
                " freshWorldRestoresTwo="+freshWorldRestoresTwo+
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
