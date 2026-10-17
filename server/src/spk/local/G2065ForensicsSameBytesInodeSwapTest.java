package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Same-byte replacement with another fileKey must NOT count as a stable scan. */
public final class G2065ForensicsSameBytesInodeSwapTest {
    public static void main(String[] args) throws Exception {
        Path scratch=Files.createTempDirectory("g2065-inode-between-scans-");
        Path dir=Files.createDirectory(scratch.resolve("journal"));
        try {
            G2064ForensicsTwoPassStableTest.seed(dir);
            Path event=dir.resolve(G2064ForensicsTwoPassStableTest.ID+".event");
            byte[] original=Files.readAllBytes(event);
            LocalLabExperimentalDiskJournalForensics.Report r=
                LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoint(
                    dir, () -> {
                        Path newInode=scratch.resolve("same-bytes-new-inode");
                        Files.write(newInode,original);
                        Files.move(newInode,event,StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    });
            G2025DiskAttemptJournalRestartWitnessTest.check(
                r.state==LocalLabExperimentalDiskJournalForensics.State
                    .BLOCKED_UNSTABLE_OBSERVATION &&
                "DISK_FORENSICS_UNSTABLE_OBSERVATION".equals(r.reasonCode) &&
                Arrays.equals(original,Files.readAllBytes(event)) &&
                !r.safeToReplayAnyConsumer(),
                "same-byte replacement inode visible between scans refuses clean report");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2065_FORENSICS_TWO_PASS_SAME_BYTE_INODE_PASS"
            +" originalBytesEqual=true inodeChanged=true stableReportDenied=true");
    }
    private G2065ForensicsSameBytesInodeSwapTest() {}
}
