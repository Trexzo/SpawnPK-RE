package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;

/** An in-place modified, valid rechecksummed record cannot stay 'stable'. */
public final class G2066ForensicsRechecksummedRewriteTest {
    private static String sha(String body) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256")
            .digest(body.getBytes(StandardCharsets.UTF_8));
        char[] hex="0123456789abcdef".toCharArray();
        StringBuilder out=new StringBuilder();
        for(byte b:hash){out.append(hex[(b>>>4)&15]).append(hex[b&15]);}
        return out.toString();
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("g2066-valid-rewrite-");
        try {
            G2064ForensicsTwoPassStableTest.seed(dir);
            Path event=dir.resolve(G2064ForensicsTwoPassStableTest.ID+".event");
            FileTime mtime=Files.getLastModifiedTime(event);
            LocalLabExperimentalDiskJournalForensics.Report r=
                LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoint(
                    dir, () -> {
                        try {
                            byte[] raw=Files.readAllBytes(event);
                            String[] p=new String(raw,StandardCharsets.UTF_8)
                                .split("\n",-1);
                            if(p.length!=6||!"G1".equals(p[3]))
                                throw new AssertionError("fixture not canonical G1");
                            p[3]="G4";
                            String body=p[0]+"\n"+p[1]+"\n"+p[2]+"\n"+p[3]+"\n";
                            byte[] forged=(body+sha(body)+"\n")
                                .getBytes(StandardCharsets.UTF_8);
                            if(raw.length!=forged.length)
                                throw new AssertionError("preserve exact original byte size");
                            Files.write(event,forged);
                            Files.setLastModifiedTime(event,mtime);
                        }catch(Exception failure){throw new java.io.IOException(failure);}
                    });
            G2025DiskAttemptJournalRestartWitnessTest.check(
                r.state==LocalLabExperimentalDiskJournalForensics.State
                    .BLOCKED_UNSTABLE_OBSERVATION &&
                r.observedEvents==1 &&
                r.observedReservations==1 &&
                !r.hasDurableConsumerGrantAcknowledgement(),
                "valid rechecksummed and timestamp-restored changed stage still unstable");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(dir); }
        System.out.println("G2066_FORENSICS_TWO_PASS_VALID_FOREIGN_REWRITE_PASS"
            +" checksumValid=true timestampRestored=true stageDriftDenied=true");
    }
    private G2066ForensicsRechecksummedRewriteTest() {}
}
