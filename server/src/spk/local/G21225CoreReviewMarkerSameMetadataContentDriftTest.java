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
 * G21.225 TEST ONLY: detect same-inode bounded marker content drift
 * despite restored file size and lastModifiedTime.
 */
public final class G21225CoreReviewMarkerSameMetadataContentDriftTest {
    private G21225CoreReviewMarkerSameMetadataContentDriftTest(){}
    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.225 "+stage);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21225-marker-drift-");
        try{
            Path account=root.resolve("named.properties");
            Path marker=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            String before=Files.readString(
                marker,StandardCharsets.UTF_8);
            FileTime originalModified=Files.getLastModifiedTime(marker);
            Object originalFileKey=Files.readAttributes(
                marker,java.nio.file.attribute.BasicFileAttributes.class)
                .fileKey();
            check(originalFileKey!=null,
                "fixture filesystem lacks file identity witness");
            int start=before.indexOf("untrustedSessionNonce=");
            check(start>0,"forced marker missing nonce");
            int digit=start+"untrustedSessionNonce=".length();
            String after=before.substring(0,digit)+
                (before.charAt(digit)=='a'?'b':'a')+
                before.substring(digit+1);
            check(after.length()==before.length()&&!after.equals(before),
                "same-length mutation fixture invalid");

            LocalDurableAccountReviewIntentPrimitive
                .setAfterMarkerReadForTest(()->{
                    try{
                        Files.writeString(marker,after,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE);
                        Files.setLastModifiedTime(
                            marker,originalModified);
                    }catch(IOException failed){
                        throw new AssertionError(failed);
                    }
                });
            boolean denied=false;
            try{
                LocalDurableAccountReviewIntentPrimitive
                    .requireReviewMarker(account);
            }catch(IOException expected){
                denied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "REVIEW_MARKER_CONTENT_DRIFT_NEGATIVE");
            }finally{
                LocalDurableAccountReviewIntentPrimitive
                    .setAfterMarkerReadForTest(null);
            }
            check(denied,
                "same-size same-mtime marker mutation was accepted");
            check(Files.size(marker)==before.getBytes(
                    StandardCharsets.UTF_8).length,
                "marker size diverged in test");
            check(Files.getLastModifiedTime(marker).equals(
                originalModified),"mtime restoration did not occur");
            Object afterFileKey=Files.readAttributes(
                marker,java.nio.file.attribute.BasicFileAttributes.class)
                .fileKey();
            check(originalFileKey.equals(afterFileKey),
                "test unexpectedly replaced inode");

            boolean admissionDenied=false;
            try{
                LocalDurableAccountReviewIntentPrimitive
                    .requireNoReviewMarker(account);
            }catch(IOException expected){
                admissionDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY");
            }
            check(admissionDenied,
                "marker content drift accidentally authorized login");
            Files.writeString(marker,before,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            System.out.println(
                "G21225_DOUBLE_READ_CONTENT_DRIFT_NEGATIVE_PASS"+
                " forcedMarkerInitiallyValid=true"+
                " exactSameInode=true"+
                " unchangedMarkerSize=true"+
                " restoredLastModifiedTime=true"+
                " inPlaceContentDriftRejected=true"+
                " restoredMarkerAgainValid=true"+
                " admissionStillFailClosed=true"+
                " cryptographicOwnerEpoch=false"+
                " fullConcurrentTamperSafety=false"+
                " autoRecoveryOrGrant=false");
        }finally{
            LocalDurableAccountReviewIntentPrimitive
                .setAfterMarkerReadForTest(null);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
