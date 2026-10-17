package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.223 TEST ONLY: a real forced ACTIVE sidecar must be formatted
 * canonically for negative diagnostic reading, NEVER admission/clear.
 */
public final class G21223CoreReviewMarkerCanonicalFieldNegativeTest {
    private G21223CoreReviewMarkerCanonicalFieldNegativeTest(){}
    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.223 "+stage);
    }
    private static boolean rejects(Path account,String fragment){
        try{
            LocalDurableAccountReviewIntentPrimitive.requireReviewMarker(
                account);
            return false;
        }catch(IOException expected){
            return expected.getMessage()!=null&&
                expected.getMessage().contains(fragment);
        }
    }
    private static void replace(Path marker,String text)throws IOException{
        Files.writeString(marker,text,StandardCharsets.UTF_8,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
    }
    private static void admissionStillDenied(Path account)
        throws IOException{
        try{
            LocalDurableAccountReviewIntentPrimitive
                .requireNoReviewMarker(account);
        }catch(IOException expected){
            check(expected.getMessage().contains(
                "ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY"),
                "unexpected admission denial");
            return;
        }
        throw new AssertionError("G21.223 sidecar falsely admitted");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21223-canonical-review-");
        try{
            Path account=root.resolve("target.properties");
            Path marker=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            String baseline=Files.readString(
                marker,StandardCharsets.UTF_8);
            check(baseline.startsWith(
                "G21188_ACTIVE_REVIEW_V1\n"),
                "forced marker missing historical header");
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            admissionStillDenied(account);
            String[] fields=baseline.split("\n",-1);
            check(fields.length==5&&fields[4].isEmpty(),
                "unexpected original marker field count");

            replace(marker,baseline+"state=ACTIVE_NEEDS_MANUAL_REVIEW\n");
            check(rejects(account,"REVIEW_MARKER_CANONICAL_FIELDS_REQUIRED"),
                "duplicate state record accepted");
            admissionStillDenied(account);

            replace(marker,baseline+"ignored-extra-property=yes\n");
            check(rejects(account,"REVIEW_MARKER_CANONICAL_FIELDS_REQUIRED"),
                "untrusted extension record accepted");
            admissionStillDenied(account);

            String wrongAccount=baseline.replace(fields[1],
                "accountSha256="+
                "0000000000000000000000000000000000000000000000000000000000000000");
            replace(marker,wrongAccount);
            check(rejects(account,"REVIEW_MARKER_CANONICAL_FIELDS_REQUIRED"),
                "cross-account fingerprint accepted");
            admissionStillDenied(account);

            String missingNonce=baseline.replace(fields[2],
                "untrustedSessionNonce=");
            replace(marker,missingNonce);
            check(rejects(account,"REVIEW_MARKER_NONCANONICAL_NONCE"),
                "empty owner nonce accepted");
            admissionStillDenied(account);

            String malformedNonce=baseline.replace(fields[2],
                "untrustedSessionNonce=not-a-uuid");
            replace(marker,malformedNonce);
            check(rejects(account,"REVIEW_MARKER_NONCANONICAL_NONCE"),
                "malformed nonce accepted");
            admissionStillDenied(account);

            String reordered=fields[0]+"\n"+fields[3]+"\n"+
                fields[2]+"\n"+fields[1]+"\n";
            replace(marker,reordered);
            check(rejects(account,"REVIEW_MARKER_CANONICAL_FIELDS_REQUIRED"),
                "reordered marker fields accepted");
            admissionStillDenied(account);

            String stateGarbage=baseline.replace(
                "state=ACTIVE_NEEDS_MANUAL_REVIEW\n",
                "state=ACTIVE_NEEDS_MANUAL_REVIEW\nstatus=COMPLETE\n");
            replace(marker,stateGarbage);
            check(rejects(account,"REVIEW_MARKER_CANONICAL_FIELDS_REQUIRED"),
                "forged success adjunct accepted");
            admissionStillDenied(account);

            replace(marker,baseline);
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            admissionStillDenied(account);
            check(Files.size(marker)==baseline.getBytes(
                StandardCharsets.UTF_8).length,
                "reader mutated original file");
            System.out.println(
                "G21223_CANONICAL_REVIEW_MARKER_NEGATIVE_PASS"+
                " genuineForcedMarkerValid=true"+
                " duplicateStateRejected=true"+
                " extraFieldRejected=true"+
                " wrongAccountDigestRejected=true"+
                " missingNonceRejected=true"+
                " malformedNonceRejected=true"+
                " reorderedFieldsRejected=true"+
                " forgedCompletionRejected=true"+
                " validMarkerStillVetoesLogin=true"+
                " noCleanupNoGrantNoRecovery=true");
        }finally{
            try(Stream<Path> entries=Files.walk(root)){
                for(Path item:entries.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(item);
            }
        }
    }
}
