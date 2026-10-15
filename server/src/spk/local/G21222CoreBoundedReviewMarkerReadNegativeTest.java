package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.222 bounded NOFOLLOW marker parser TEST ONLY.
 * All marker mutations are inside an isolated temporary fixture;
 * no real ACTIVE sidecar may ever be removed by this test/protocol.
 */
public final class G21222CoreBoundedReviewMarkerReadNegativeTest {
    private G21222CoreBoundedReviewMarkerReadNegativeTest(){}
    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.222 "+stage);
    }
    private static boolean rejected(Path account,String fragment){
        try{
            LocalDurableAccountReviewIntentPrimitive.requireReviewMarker(
                account);
            return false;
        }catch(IOException expected){
            return expected.getMessage()!=null&&
                expected.getMessage().contains(fragment);
        }
    }
    private static void stillNegative(Path account)throws Exception{
        boolean denied=false;
        try{
            LocalDurableAccountReviewIntentPrimitive
                .requireNoReviewMarker(account);
        }catch(IOException expected){
            denied=expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY");
        }
        check(denied,"existing file was not a negative admission marker");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21222-review-bound-");
        try{
            Path account=root.resolve("named.properties");
            Path marker=LocalDurableAccountReviewIntentPrimitive
                .markerPath(account);
            // Genuine G21.188 durable file+directory forced marker.
            Path created=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            check(marker.equals(created),
                "G21.188 created different marker path");
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            stillNegative(account);
            check(Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS),
                "valid forced ACTIVE marker not regular");

            // Oversized file is rejected BEFORE unbounded read.
            byte[] giant=new byte[4097];
            java.util.Arrays.fill(giant,(byte)'A');
            Files.write(marker,giant,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            check(rejected(account,"REVIEW_MARKER_OVERSIZE_NEGATIVE_NO_CLEAR"),
                "oversized marker not refused");
            stillNegative(account);
            check(Files.size(marker)==4097L,
                "reader mutated oversized marker bytes");

            // A small syntactically plausible marker containing
            // invalid UTF-8 must also remain a negative, never clean.
            byte[] broken="G21188_ACTIVE_REVIEW_V1\n".getBytes(
                StandardCharsets.UTF_8);
            byte[] invalid=new byte[broken.length+2];
            System.arraycopy(broken,0,invalid,0,broken.length);
            invalid[broken.length]=(byte)0xC3;
            invalid[broken.length+1]=(byte)0x28;
            Files.write(marker,invalid,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            check(rejected(account,"REVIEW_MARKER_MALFORMED_UTF8_NEGATIVE"),
                "malformed UTF-8 marker was accepted");
            stillNegative(account);

            // A valid encoding with the wrong review header is not
            // a grant or an opportunity to clear the marker.
            Files.writeString(marker,
                "UNTRUSTED-OTHER-STATE\nstate=ACTIVE_NEEDS_MANUAL_REVIEW\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            check(rejected(account,"REVIEW_MARKER_INVALID_NO_AUTO_CLEAR"),
                "wrong-format ACTIVE marker accepted");
            stillNegative(account);

            // TEST fixture only. A symbolic-link marker must NEVER
            // be followed into an unrelated file during inspection.
            boolean symlinkChecked=false;
            if(!System.getProperty("os.name").toLowerCase()
                    .contains("win")){
                Path other=root.resolve("target-bytes.txt");
                Files.writeString(other,
                    "G21188_ACTIVE_REVIEW_V1\n"+
                    "state=ACTIVE_NEEDS_MANUAL_REVIEW\n",
                    StandardCharsets.UTF_8);
                Files.delete(marker);
                Files.createSymbolicLink(marker,other);
                symlinkChecked=true;
                check(rejected(account,
                    "REVIEW_MARKER_NOT_REGULAR_NO_AUTHORITY"),
                    "symlink marker was followed as regular");
                stillNegative(account);
                check(Files.isSymbolicLink(marker),
                    "review parser unexpectedly deleted test symlink");
            }
            System.out.println(
                "G21222_BOUNDED_NOFOLLOW_REVIEW_MARKER_PASS"+
                " actualForcedG21188MarkerInitiallyValid=true"+
                " markerOversize4097Refused=true"+
                " boundedMemory4097Bytes=true"+
                " strictMalformedUtf8Denied=true"+
                " wrongHeaderDenied=true"+
                " symlinkMarkerRejected="+symlinkChecked+
                " markerExistenceStillVetoesAdmission=true"+
                " autoMarkerRetirement=false"+
                " replayOrGrantAuthority=false");
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
