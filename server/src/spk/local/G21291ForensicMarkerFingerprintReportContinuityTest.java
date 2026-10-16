package spk.local;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * G21.291: stable negative-sidecar fingerprints allow two independent
 * read-only operator reports to show byte differences without granting
 * authenticity, final-save, recovery, or admission authority.
 */
public final class G21291ForensicMarkerFingerprintReportContinuityTest {
    private G21291ForensicMarkerFingerprintReportContinuityTest(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.291 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21291-fingerprint-report-");
        try{
            Path account=root.resolve("selected.properties");
            byte[] original="selected-stable-account".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(account,original);
            Path marker=LocalDurableAccountReviewIntentPrimitive.markerPath(
                account);
            byte[] content="untrusted-marker-state=BEFORE\n".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(marker,content);
            FileTime mtime=Files.getLastModifiedTime(marker,
                LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes before=Files.readAttributes(marker,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);

            LocalAccountForensicEvidence.Evidence first=
                LocalAccountForensicEvidence.inspect(account);
            String fp1=first.negativeSidecarContentWitnesses.get(
                "activeReview");
            check(fp1!=null&&fp1.matches("SHA256:[0-9a-f]{64}"),
                "initial regular marker fingerprint not reported");
            check(first.negativeSidecarContentWitnesses.size()==5&&
                "ABSENT".equals(first.negativeSidecarContentWitnesses
                    .get("diskCommit")),
                "exact five selected markers not represented");
            check(first.report().contains("activeReviewContent="+fp1)&&
                first.report().contains("finalSaveAck=false"),
                "operator report missed digest or invented final receipt");

            try(FileChannel channel=FileChannel.open(marker,
                    StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                channel.position(content.length-3);
                channel.write(ByteBuffer.wrap(new byte[]{'Z'}));
                channel.force(true);
            }
            Files.setLastModifiedTime(marker,mtime);
            BasicFileAttributes after=Files.readAttributes(marker,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            check(before.fileKey().equals(after.fileKey())&&
                before.size()==after.size()&&
                mtime.equals(after.lastModifiedTime()),
                "same inode size and restored mtime precondition");

            LocalAccountForensicEvidence.Evidence second=
                LocalAccountForensicEvidence.inspect(account);
            String fp2=second.negativeSidecarContentWitnesses.get(
                "activeReview");
            check(fp2!=null&&fp2.matches("SHA256:[0-9a-f]{64}")&&
                !fp1.equals(fp2),
                "separate stable reports hid same-inode byte changes");
            check(first.accountSha256.equals(second.accountSha256)&&
                first.observedNegativeNames==1&&
                second.observedNegativeNames==1&&
                first.negativeSidecars.equals(second.negativeSidecars),
                "qualitative observation unexpectedly changed");
            boolean immutable=false;
            try{
                second.negativeSidecarContentWitnesses.put(
                    "activeReview","FORGED_AUTHORITY");
            }catch(UnsupportedOperationException expected){
                immutable=true;
            }
            check(immutable&&second.report().contains(
                    "activeReviewContent="+fp2)&&
                second.report().contains("loginAuthorized=false")&&
                second.report().contains("recoveryAuthorized=false")&&
                Arrays.equals(original,Files.readAllBytes(account)),
                "fingerprint map mutable or report promoted to authority");
            try(Stream<Path> items=Files.list(root)){
                check(items.count()==2,
                    "read-only forensic inspection created files");
            }
            System.out.println(
                "G21291_FORENSIC_MARKER_REPORT_FINGERPRINT_PASS"+
                " twoIndependentReports=true"+
                " metadataRestoredButFingerprintChanged=true"+
                " observedMarkersRemainNegative=true"+
                " deeplyImmutableWitnessMap=true"+
                " noRecoveryOrAdmissionAuthority=true");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:(Iterable<Path>)paths.sorted(
                    Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
}
