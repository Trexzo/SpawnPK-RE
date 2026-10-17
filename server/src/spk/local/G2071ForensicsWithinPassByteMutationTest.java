package spk.local;
import java.nio.file.Files;
import java.nio.file.Path;
public final class G2071ForensicsWithinPassByteMutationTest {
  public static void main(String[] args) throws Exception {
Path root=Files.createTempDirectory("g2071-bytes-");
try {
    G2064ForensicsTwoPassStableTest.seed(root);
    Path event=root.resolve(G2064ForensicsTwoPassStableTest.ID+".event");
    LocalLabExperimentalDiskJournalForensics.Report r=
        LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoints(
            root,null,path -> {
                if(!path.equals(event))return;
                byte[] b=Files.readAllBytes(path);
                b[b.length-2]=(byte)(b[b.length-2]=='a'?'b':'a');
                Files.write(path,b);
            });
    G2025DiskAttemptJournalRestartWitnessTest.check(
        r.state==LocalLabExperimentalDiskJournalForensics.State.BLOCKED_UNSTABLE_OBSERVATION &&
        "DISK_FORENSICS_FILE_CHANGED_DURING_SCAN".equals(r.reasonCode) &&
        Files.exists(event) && !r.safeToReplayAnyConsumer(),
        "event bytes changed during scan refused without repair");
} finally { G2025DiskAttemptJournalRestartWitnessTest.erase(root); }
System.out.println("G2071_FORENSICS_WITHIN_PASS_BYTES_PASS corruptionDetected=true noRepair=true");
  }
  private G2071ForensicsWithinPassByteMutationTest() {}
}
