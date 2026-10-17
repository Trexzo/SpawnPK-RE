package spk.local;
import java.nio.file.Files;
import java.nio.file.Path;
public final class G2072ForensicsWithinPassInodeSwapTest {
  public static void main(String[] args) throws Exception {
Path scratch=Files.createTempDirectory("g2072-inode-");
Path root=Files.createDirectory(scratch.resolve("journal"));
try {
    G2064ForensicsTwoPassStableTest.seed(root);
    Path event=root.resolve(G2064ForensicsTwoPassStableTest.ID+".event");
    byte[] content=Files.readAllBytes(event);
    LocalLabExperimentalDiskJournalForensics.Report r=
        LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoints(
            root,null,path -> {
                if(!path.equals(event))return;
                Path replacement=scratch.resolve("replacement-inode");
                Files.write(replacement,content);
                Files.move(replacement,event,java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            });
    G2025DiskAttemptJournalRestartWitnessTest.check(
        r.state==LocalLabExperimentalDiskJournalForensics.State.BLOCKED_UNSTABLE_OBSERVATION &&
        "DISK_FORENSICS_FILE_CHANGED_DURING_SCAN".equals(r.reasonCode) &&
        java.util.Arrays.equals(content,Files.readAllBytes(event)),
        "same byte event replacement with different inode refused mid scan");
} finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
System.out.println("G2072_FORENSICS_WITHIN_PASS_INODE_PASS sameBytes=true newInodeDenied=true");
  }
  private G2072ForensicsWithinPassInodeSwapTest() {}
}
