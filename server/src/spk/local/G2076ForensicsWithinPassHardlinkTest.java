package spk.local;
import java.nio.file.Files;
import java.nio.file.Path;
public final class G2076ForensicsWithinPassHardlinkTest {
  public static void main(String[] args) throws Exception {
Path scratch=Files.createTempDirectory("g2076-link-");
Path root=Files.createDirectory(scratch.resolve("journal"));
Path outside=scratch.resolve("external-hardlink");
try {
    G2064ForensicsTwoPassStableTest.seed(root);
    Path event=root.resolve(G2064ForensicsTwoPassStableTest.ID+".event");
    LocalLabExperimentalDiskJournalForensics.Report r=
        LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoints(
            root,null,path -> {
                if(path.equals(event))Files.createLink(outside,event);
            });
    G2025DiskAttemptJournalRestartWitnessTest.check(
        r.state==LocalLabExperimentalDiskJournalForensics.State.BLOCKED_UNSTABLE_OBSERVATION &&
        "DISK_FORENSICS_FILE_CHANGED_DURING_SCAN".equals(r.reasonCode) &&
        Files.exists(outside) && !r.hasDurableConsumerGrantAcknowledgement(),
        "hardlink added during event read must refuse observation");
} finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
System.out.println("G2076_FORENSICS_WITHIN_PASS_HARDLINK_PASS aliasDenied=true noGrantAck=true");
  }
  private G2076ForensicsWithinPassHardlinkTest() {}
}
