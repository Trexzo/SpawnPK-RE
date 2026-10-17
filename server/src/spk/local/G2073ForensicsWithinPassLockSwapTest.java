package spk.local;
import java.nio.file.Files;
import java.nio.file.Path;
public final class G2073ForensicsWithinPassLockSwapTest {
  public static void main(String[] args) throws Exception {
Path scratch=Files.createTempDirectory("g2073-lock-");
Path root=Files.createDirectory(scratch.resolve("journal"));
try {
    G2064ForensicsTwoPassStableTest.seed(root);
    Path lock=root.resolve(".journal.lock");
    LocalLabExperimentalDiskJournalForensics.Report r=
        LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoints(
            root,null,path -> {
                if(!path.equals(lock))return;
                Path replacement=scratch.resolve("new-lock-inode");
                Files.write(replacement,new byte[0]);
                Files.move(replacement,lock,java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            });
    G2025DiskAttemptJournalRestartWitnessTest.check(
        r.state==LocalLabExperimentalDiskJournalForensics.State.BLOCKED_UNSTABLE_OBSERVATION &&
        "DISK_FORENSICS_FILE_CHANGED_DURING_SCAN".equals(r.reasonCode) &&
        !r.hasExclusiveCrossJvmOwnerEpoch() && Files.exists(lock),
        "new lock inode does not prove global owner continuity");
} finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
System.out.println("G2073_FORENSICS_WITHIN_PASS_LOCK_PASS lockSwapDetected=true ownerEpoch=false");
  }
  private G2073ForensicsWithinPassLockSwapTest() {}
}
