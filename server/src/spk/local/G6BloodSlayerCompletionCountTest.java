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
        boolean secondCompletionTwo=false;
        boolean freshWorldRestoresTwo=false;
        boolean legacyV1LoadsZero=false;
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
                "renewal did not preserve first completion"
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

            statusExposesCount=
                runtime.status(OWNER)
                    .completions==2L;

            require(
                statusExposesCount,
                "runtime status did not expose completion count"
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
                " secondCompletionTwo="+secondCompletionTwo+
                " freshWorldRestoresTwo="+freshWorldRestoresTwo+
                " legacyV1LoadsZero="+legacyV1LoadsZero+
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
