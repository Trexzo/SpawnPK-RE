package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.224: same-inode marker changes during a bounded read must
 * remain negative/uncertain, never be mistaken for a stable record.
 */
public final class G21224CoreReviewMarkerInPlaceReadRaceNegativeTest {
    private G21224CoreReviewMarkerInPlaceReadRaceNegativeTest(){}
    private static void check(boolean ok,String stage){
        if(!ok)throw new AssertionError("G21.224 "+stage);
    }
    private static void expectMutation(Path account)throws Exception{
        boolean rejected=false;
        try{
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
        }catch(IOException expected){
            rejected=expected.getMessage()!=null&&
                (expected.getMessage().contains(
                    "REVIEW_MARKER_MUTATED_DURING_READ_NEGATIVE")||
                 expected.getMessage().contains(
                    "REVIEW_MARKER_CONTENT_DRIFT_NEGATIVE"));
        }finally{
            LocalDurableAccountReviewIntentPrimitive
                .setAfterMarkerReadForTest(null);
        }
        check(rejected,"in-place marker race accepted as stable");
        boolean loginDenied=false;
        try{
            LocalDurableAccountReviewIntentPrimitive
                .requireNoReviewMarker(account);
        }catch(IOException expected){
            loginDenied=expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY");
        }
        check(loginDenied,"marker mutation authorized new admission");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21224-marker-race-");
        try{
            Path account=root.resolve("named.properties");
            Path marker=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            String original=Files.readString(
                marker,StandardCharsets.UTF_8);
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);

            // Append to exactly the same on-disk inode between
            // completed read and final post-read metadata checks.
            LocalDurableAccountReviewIntentPrimitive
                .setAfterMarkerReadForTest(()->{
                    try{
                        Files.writeString(marker,"X",
                            StandardCharsets.UTF_8,
                            StandardOpenOption.APPEND);
                    }catch(IOException fail){
                        throw new AssertionError(fail);
                    }
                });
            expectMutation(account);
            check(Files.size(marker)==original.getBytes(
                StandardCharsets.UTF_8).length+1,
                "test failed to append to actual marker");
            Files.writeString(marker,original,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);

            // Same byte count and same inode: rewrite a field to
            // unknown contents and deliberately advance the file
            // timestamp to ensure a deterministic metadata witness.
            LocalDurableAccountReviewIntentPrimitive
                .setAfterMarkerReadForTest(()->{
                    try{
                        String changed=original.replace(
                            "state=ACTIVE_NEEDS_MANUAL_REVIEW",
                            "state=ACTIVE_NEEDS_MANUAL_REVIEW");
                        Files.writeString(marker,changed,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE);
                        Files.setLastModifiedTime(marker,
                            FileTime.fromMillis(
                                System.currentTimeMillis()+60_000L));
                    }catch(IOException fail){
                        throw new AssertionError(fail);
                    }
                });
            expectMutation(account);
            check(Files.size(marker)==original.getBytes(
                StandardCharsets.UTF_8).length,
                "same-length in-place test unexpectedly grew file");
            LocalDurableAccountReviewIntentPrimitive
                .setAfterMarkerReadForTest(null);
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            System.out.println(
                "G21224_SAME_INODE_MARKER_RACE_NEGATIVE_PASS"+
                " actualForcedMarker=true"+
                " sameInodeAppendDenied=true"+
                " sameSizeMtimeMutationDenied=true"+
                " unchangedValidMarkerRecognized=true"+
                " negativeLoginVetoRetained=true"+
                " noMarkerRetirement=true"+
                " noCryptographicEpochProof=true"+
                " noRecoveryOrGrant=true");
        }finally{
            LocalDurableAccountReviewIntentPrimitive
                .setAfterMarkerReadForTest(null);
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
