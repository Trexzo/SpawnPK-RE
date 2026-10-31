package spk.local;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.TreeMap;

/**
 * P5.48: an already-corrupt persisted source-review marker is
 * negative evidence. A cooperating World save cannot preserve that
 * unknown value, nor overwrite it with a valid value and call the
 * operation an ordinary successful source-account publication.
 *
 * The test deliberately seeds corrupted disk bytes with an
 * UNCOOPERATIVE test-only raw writer. It does not approve such a
 * writer for actual sessions or claim source reward/durability.
 */
public final class PlayableSourceInvalidDiskMarkerGuardTest {
    private PlayableSourceInvalidDiskMarkerGuardTest(){}

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(
            "P548_INVALID_DISK_SOURCE_MARKER "+why);
    }

    private static boolean unavailableAncestorIdentity(Path account)
        throws IOException{
        for(Path dir=account.getParent();dir!=null;dir=dir.getParent()){
            BasicFileAttributes attributes=Files.readAttributes(
                dir,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(attributes.fileKey()==null)return true;
        }
        return false;
    }

    private static PlayerSnapshot snapshot(
        String account,String marker,String kills
    ){
        WorldPlayer player=new WorldPlayer();
        if(marker!=null||kills!=null){
            TreeMap<String,String> ns=new TreeMap<>();
            ns.put("version",PvmRecordService.VERSION);
            ns.put("authority",PvmRecordService.AUTHORITY);
            if(marker!=null)
                ns.put(PvmRecordService.SOURCE_RESTART_REVIEW_KEY,marker);
            if(kills!=null)ns.put("kills",kills);
            player.snapshotExtensions().replaceNamespace(
                PvmRecordService.NAMESPACE,ns);
        }
        return PlayerSnapshotCodec.capture(account,player);
    }

    private static void rawTestOnlyOverwrite(
        Path file,PlayerSnapshot snapshot
    )throws IOException{
        Properties props=snapshot.toLegacyProperties();
        props.setProperty("saved.at","P548-invalid-test-only");
        try(OutputStream out=Files.newOutputStream(file)){
            props.store(out,"intentionally corrupted source file");
        }
    }

    private static void mustRefuseNoMove(
        FilePlayerRepository repository,Path file,
        PlayerSnapshot offered,String reason
    )throws Exception{
        byte[] before=Files.readAllBytes(file);
        try{
            repository.saveForWorld(offered);
            throw new AssertionError(
                "unsafe World source publication unexpectedly succeeded");
        }catch(IOException refused){
            check(refused.toString().contains(reason),
                "World save denied for unexpected reason "+refused);
        }
        check(Arrays.equals(before,Files.readAllBytes(file)),
            "rejected World save replaced malformed source account");
    }

    private static void verifyDisk(
        FilePlayerRepository repository,String account,
        String expectedMarker,String expectedKills
    )throws IOException{
        PlayerSnapshot current=repository.load(account).orElseThrow(
            ()->new AssertionError("source account disk file absent"));
        String prefix="extension."+PvmRecordService.NAMESPACE+".";
        check(expectedMarker.equals(current.value(
                    prefix+PvmRecordService.SOURCE_RESTART_REVIEW_KEY))&&
                expectedKills.equals(current.value(prefix+"kills")),
            "real account postimage changed marker/count unexpectedly");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory(
            Path.of(System.getProperty("user.dir"))
                .toAbsolutePath().normalize(),
            "spk-p548-invalid-source-");
        String account="p548-invalid-account";
        Path file=root.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(
            name->root.resolve(name+".properties"));
        String correct=PvmRecordService.SOURCE_RESTART_REVIEW_VALUE;
        try{
            PlayerSnapshot initial=snapshot(account,correct,"2");
            if(unavailableAncestorIdentity(file)){
                // Never skip the real World save. Unverifiable
                // directory ancestry must be refused BEFORE an
                // account file is published on this Windows/provider
                // capability-negative lane.
                try{
                    repo.saveForWorld(initial);
                    throw new AssertionError(
                        "G21.171 required directory identity was bypassed");
                }catch(IOException refused){
                    check(refused.toString().contains(
                            "G21.171 WORLD_TEMP_ANCESTRY_UNSAFE_NO_AUTHORITY"),
                        "unexpected unsupported-provider reason "+refused);
                }
                check(!Files.exists(file),
                    "source account published under unsafe ancestry");
                System.out.println(
                    "P548_INVALID_DISK_SOURCE_ANCESTRY_NEGATIVE_PASS"+
                    " requiredFileKeyUnavailable=true"+
                    " actualWorldSaveDeniedG21171=true"+
                    " noAccountPublished=true"+
                    " malformedDiskAcceptanceNotCertified=true");
                return;
            }

            // First verify the unchanged successful World save path.
            repo.saveForWorld(initial);
            verifyDisk(repo,account,correct,"2");

            // A test RAW writer puts malformed negative evidence on
            // the actual disk account. Subsequent World writers
            // must refuse BOTH preserving the malformed value and
            // overwriting it with a seemingly corrected value.
            String malformed="unexpected-proof";
            rawTestOnlyOverwrite(
                file,snapshot(account,malformed,"2"));
            verifyDisk(repo,account,malformed,"2");
            mustRefuseNoMove(repo,file,
                snapshot(account,malformed,"3"),
                "P548_WORLD_SAVE_SOURCE_EXISTING_MARKER_INVALID_NO_MOVE");
            mustRefuseNoMove(repo,file,
                snapshot(account,correct,"3"),
                "P548_WORLD_SAVE_SOURCE_EXISTING_MARKER_INVALID_NO_MOVE");
            verifyDisk(repo,account,malformed,"2");

            // Even an existing marker with an ABSENT count is
            // untrusted: no ordinary World save may erase this
            // evidence or infer a safe positive counter.
            rawTestOnlyOverwrite(
                file,snapshot(account,malformed,null));
            mustRefuseNoMove(repo,file,
                snapshot(account,correct,"4"),
                "P548_WORLD_SAVE_SOURCE_EXISTING_MARKER_INVALID_NO_MOVE");

            // Existing valid marker but malformed numeric history
            // remains a separate pre-move veto from P5.46.
            rawTestOnlyOverwrite(
                file,snapshot(account,correct,"bad-count"));
            mustRefuseNoMove(repo,file,
                snapshot(account,correct,"4"),
                "P546_WORLD_SAVE_SOURCE_COUNT_UNPROVEN_NO_MOVE");
            verifyDisk(repo,account,correct,"bad-count");

            // A dedicated test-only raw reset is not an authorized
            // repair workflow. It merely restores the fixture to a
            // known-valid disk preimage so normal World save can
            // demonstrate that the stricter guard is not global.
            rawTestOnlyOverwrite(
                file,snapshot(account,correct,"4"));
            repo.saveForWorld(snapshot(account,correct,"5"));
            verifyDisk(repo,account,correct,"5");
            repo.saveForWorld(snapshot("p548-other-account",null,null));
            check(repo.load("p548-other-account").isPresent(),
                "source marker guard vetoed unrelated account");

            System.out.println(
                "P548_INVALID_DISK_SOURCE_MARKER_PASS"+
                " actualWorldAccountFileReadAndPublication=true"+
                " malformedExistingMarkerPreserveDeniedNoMove=true"+
                " malformedExistingMarkerRewriteDeniedNoMove=true"+
                " missingCountCorruptMarkerStillDenied=true"+
                " validMarkerMalformedCountDeniedNoMove=true"+
                " normalMarkedAndUnrelatedWorldSavesStillAllowed=true"+
                " rawWriterAndCrashDurabilityNotCertified=true");
        }finally{
            try(java.util.stream.Stream<Path> walk=Files.walk(root)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException e){
                        throw new IllegalStateException(
                            "P548 fixture cleanup failed "+p,e);
                    }
                });
            }
        }
    }
}
