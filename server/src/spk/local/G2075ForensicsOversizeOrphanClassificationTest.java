package spk.local;
import java.nio.file.Files;
import java.nio.file.Path;
public final class G2075ForensicsOversizeOrphanClassificationTest {
  public static void main(String[] args) throws Exception {
Path root=Files.createTempDirectory("g2075-oversized-");
try {
    G2064ForensicsTwoPassStableTest.seed(root);
    byte[] large=new byte[8192];
    Path pending=root.resolve("crashed-untrusted.tmp");
    Files.write(pending,large);
    LocalLabExperimentalDiskJournalForensics.Report orphan=
        LocalLabExperimentalDiskJournalForensics.inspectExisting(root);
    G2025DiskAttemptJournalRestartWitnessTest.check(
        orphan.state==LocalLabExperimentalDiskJournalForensics.State.BLOCKED_ORPHAN_PENDING &&
        "DISK_FORENSICS_UNRESOLVED_PENDING".equals(orphan.reasonCode) &&
        Files.size(pending)==8192L,
        "oversized pending classified before record read");
    Files.delete(pending); // test-created artifact ONLY
    Path foreign=root.resolve("external-unexpected-filename");
    Files.write(foreign,large);
    LocalLabExperimentalDiskJournalForensics.Report bad=
        LocalLabExperimentalDiskJournalForensics.inspectExisting(root);
    G2025DiskAttemptJournalRestartWitnessTest.check(
        bad.state==LocalLabExperimentalDiskJournalForensics.State.BLOCKED_UNSAFE_PATH &&
        "DISK_FORENSICS_UNKNOWN_ENTRY".equals(bad.reasonCode) &&
        Files.size(foreign)==8192L && !bad.safeToReplayAnyConsumer(),
        "unknown oversized file refused as unknown, not corrupted event");
} finally { G2025DiskAttemptJournalRestartWitnessTest.erase(root); }
System.out.println("G2075_FORENSICS_OVERSIZE_ORPHAN_CLASSIFICATION_PASS orphanAndUnknownDistinct=true");
  }
  private G2075ForensicsOversizeOrphanClassificationTest() {}
}
