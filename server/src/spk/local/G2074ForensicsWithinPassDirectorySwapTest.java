package spk.local;
import java.nio.file.Files;
import java.nio.file.Path;
public final class G2074ForensicsWithinPassDirectorySwapTest {
  public static void main(String[] args) throws Exception {
Path scratch=Files.createTempDirectory("g2074-dir-");
Path root=Files.createDirectory(scratch.resolve("journal"));
Path moved=scratch.resolve("moved-journal");
try {
    G2064ForensicsTwoPassStableTest.seed(root);
    java.util.concurrent.atomic.AtomicBoolean once=new java.util.concurrent.atomic.AtomicBoolean();
    LocalLabExperimentalDiskJournalForensics.Report r=
        LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoints(
            root,null,path -> {
                if(!once.compareAndSet(false,true))return;
                Files.move(root,moved,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                Files.createDirectory(root);
            });
    G2025DiskAttemptJournalRestartWitnessTest.check(
        once.get() &&
        r.state==LocalLabExperimentalDiskJournalForensics.State.BLOCKED_UNSTABLE_OBSERVATION &&
        !r.safeToReplayAnyConsumer() &&
        Files.exists(moved.resolve(G2064ForensicsTwoPassStableTest.ID+".event")) &&
        Files.notExists(root.resolve(G2064ForensicsTwoPassStableTest.ID+".event")),
        "directory reused during first file read cannot appear stable");
} finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
System.out.println("G2074_FORENSICS_WITHIN_PASS_DIRECTORY_PASS originalEventRetained=true");
  }
  private G2074ForensicsWithinPassDirectorySwapTest() {}
}
