package spk.local;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * P5.47 deterministic negative-only G21.39 World-save cutpoint.
 *
 * A nonparticipating RAW test writer changes the *selected account
 * file* during insideWorldPublication, AFTER World serialized its
 * stale temp but BEFORE World would ATOMIC_MOVE it. The World must
 * re-read the current source-negative disk image in that exact
 * critical section, refuse stale/invalid marker and count, and not
 * move its own outdated temp over the intruding newer bytes.
 *
 * This test-only RAW injection is NOT a safe way to write accounts;
 * it does not prove protection from a write after the last check,
 * power loss, JVM restart or a cross-JVM lease.
 */
public final class PlayableSourceWorldPreMoveRaceTest {
    private PlayableSourceWorldPreMoveRaceTest(){}

    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError(
            "P547_SOURCE_WORLD_PREMOVE "+why);
    }

    private static PlayerSnapshot snapshot(
        String account,String kills,String marker
    ){
        WorldPlayer player=new WorldPlayer();
        if(kills!=null||marker!=null){
            TreeMap<String,String> extension=new TreeMap<>();
            extension.put("version",PvmRecordService.VERSION);
            extension.put("authority",PvmRecordService.AUTHORITY);
            if(kills!=null)extension.put("kills",kills);
            if(marker!=null)
                extension.put(
                    PvmRecordService.SOURCE_RESTART_REVIEW_KEY,
                    marker);
            player.snapshotExtensions().replaceNamespace(
                PvmRecordService.NAMESPACE,extension);
        }
        return PlayerSnapshotCodec.capture(account,player);
    }

    /**
     * Deliberately uncooperative TEST-ONLY account rewrite. The
     * callback is reached under the World publication .lock but
     * ignores it; real RAW production writes are never authorized
     * by this test seam.
     */
    private static void overwriteWithoutCooperation(
        Path file,PlayerSnapshot newDisk
    )throws IOException{
        Properties values=newDisk.toLegacyProperties();
        values.setProperty("saved.at","P547-test-injected-postimage");
        try(OutputStream out=Files.newOutputStream(file)){
            values.store(out,"P547 negative race injection only");
        }
    }

    private static void disk(
        FilePlayerRepository repository,String account,
        String count,String marker
    )throws Exception{
        PlayerSnapshot current=repository.load(account)
            .orElseThrow(()->new AssertionError(
                "source account disk image absent"));
        String prefix="extension."+PvmRecordService.NAMESPACE+".";
        require(count.equals(current.value(prefix+"kills"))&&
            marker.equals(current.value(
                prefix+PvmRecordService.SOURCE_RESTART_REVIEW_KEY)),
            "observed postimage does not match injected newer source state");
    }

    private static void rejectWithoutMovingWorldTemp(
        FilePlayerRepository repository,PlayerSnapshot offered,
        Path selectedFile,AtomicReference<PlayerSnapshot> pending,
        PlayerSnapshot injected,String expected,
        AtomicInteger callbackCount
    )throws Exception{
        int before=callbackCount.get();
        pending.set(injected);
        try{
            repository.saveForWorld(offered);
            throw new AssertionError(
                "stale World temp incorrectly published: "+expected);
        }catch(IOException denied){
            require(denied.toString().contains(expected),
                "World publisher rejected for wrong reason "+denied);
        }finally{
            pending.set(null);
        }
        require(callbackCount.get()==before+1,
            "test injection not reached exactly inside World publication");
        // The file is the foreign *injected* image. A rejected World
        // save must not replace it, even though the account bytes
        // demonstrably changed earlier IN the injected callback.
        require(Files.isRegularFile(selectedFile),
            "rejected World save deleted selected account image");
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        // The GitHub Windows runner's system TEMP may include a
        // redirected RUNNER~1 junction. The production G21.171
        // NOFOLLOW ancestry guard correctly forbids that root.
        // Gradle configures the repository as the test workingDir.
        Path root=Files.createTempDirectory(
            Path.of(System.getProperty("user.dir"))
                .toAbsolutePath().normalize(),
            "spk-p547-source-cutpoint-");
        String name="p547-source-cutpoint";
        Path accountFile=root.resolve(name+".properties");
        FilePlayerRepository.PathResolver files=
            account->root.resolve(account+".properties");
        AtomicReference<PlayerSnapshot> pending=
            new AtomicReference<>();
        AtomicInteger callbackCount=new AtomicInteger();
        FilePlayerRepository ordinary=
            new FilePlayerRepository(files);
        FilePlayerRepository interrupted=
            new FilePlayerRepository(
                files,
                account->{},
                account->{
                    PlayerSnapshot intrusive=pending.get();
                    if(intrusive!=null){
                        overwriteWithoutCooperation(
                            accountFile,intrusive);
                        callbackCount.incrementAndGet();
                    }
                });
        String valid=
            PvmRecordService.SOURCE_RESTART_REVIEW_VALUE;
        PlayerSnapshot empty=snapshot(name,null,null);
        PlayerSnapshot one=snapshot(name,"1",valid);
        PlayerSnapshot two=snapshot(name,"2",valid);
        PlayerSnapshot markerMissing=snapshot(name,"2",null);
        PlayerSnapshot invalidMarker=snapshot(name,"2","invalid-marker");
        PlayerSnapshot invalidCount=snapshot(name,"not-a-number",valid);
        try{
            ordinary.saveForWorld(empty);
            require(ordinary.load(name).isPresent(),
                "initial source-free World image not persisted");

            // First: World has already serialized the old unmarked
            // account. Just before its move, another actor exposes
            // the complete source-marked count=1 file postimage.
            rejectWithoutMovingWorldTemp(
                interrupted,empty,accountFile,pending,one,
                "P546_WORLD_SAVE_SOURCE_MARKER_REGRESSION_NO_MOVE",
                callbackCount);
            disk(ordinary,name,"1",valid);

            // Second: the World offers stale marked count=1, but a
            // test RAW writer advances disk to source count=2 at the
            // exact late G21.39 cutpoint. World MUST NOT undo it.
            rejectWithoutMovingWorldTemp(
                interrupted,one,accountFile,pending,two,
                "P546_WORLD_SAVE_SOURCE_COUNT_REGRESSION_NO_MOVE",
                callbackCount);
            disk(ordinary,name,"2",valid);

            // Other malformed prospective World postimages must
            // fail closed with byte-for-byte untouched disk data.
            for(PlayerSnapshot malformed:
                    Arrays.asList(markerMissing,invalidMarker,
                        invalidCount)){
                byte[] before=Files.readAllBytes(accountFile);
                String code=malformed==invalidCount
                    ?"P546_WORLD_SAVE_SOURCE_COUNT_UNPROVEN_NO_MOVE"
                    :"P546_WORLD_SAVE_SOURCE_MARKER_REGRESSION_NO_MOVE";
                try{
                    ordinary.saveForWorld(malformed);
                    throw new AssertionError(
                        "unsafe marker/count World save succeeded "+code);
                }catch(IOException refused){
                    require(refused.toString().contains(code),
                        "malformed source refused for wrong reason "+refused);
                }
                require(Arrays.equals(before,
                    Files.readAllBytes(accountFile)),
                    "malformed prospective source image moved over disk");
            }
            disk(ordinary,name,"2",valid);

            // Non-injected current World save and different account
            // must still work normally; this is NOT a global ban.
            ordinary.saveForWorld(two);
            disk(ordinary,name,"2",valid);
            ordinary.saveForWorld(snapshot("p547-other-account",null,null));
            require(ordinary.load("p547-other-account").isPresent(),
                "unrelated account save denied by source history gate");
            require(callbackCount.get()==2,
                "unexpected extra source RAW cutpoint invocations");
            System.out.println(
                "P547_SOURCE_WORLD_PREMOVE_RACE_PASS"+
                " twoRealPublicationCutpointInjections=true"+
                " noStaleWorldTempReplacedNewerSourcePostimage=true"+
                " sourceMarkerAndCountBothLateVetoed=true"+
                " malformedCandidateMarkerOrCountDenied=true"+
                " unrelatedAccountAndCurrentSnapshotStillWritable=true"+
                " noOriginalClientOrCrashDurabilityClaim=true");
        }finally{
            try(java.util.stream.Stream<Path> walk=Files.walk(root)){
                walk.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException e){
                        throw new IllegalStateException(
                            "P547 cleanup failed "+path,e);
                    }
                });
            }
        }
    }
}
