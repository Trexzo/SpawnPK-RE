package spk.local;
import java.nio.file.Files;
import java.nio.file.Path;
public final class G2070ForensicsWithinPassPositiveTest {
  public static void main(String[] args) throws Exception {
Path root=Files.createTempDirectory("g2070-positive-");
try {
    G2064ForensicsTwoPassStableTest.seed(root);
    java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
    LocalLabExperimentalDiskJournalForensics.Report r=
        LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoints(
            root,null,path -> { calls.incrementAndGet(); });
    G2025DiskAttemptJournalRestartWitnessTest.check(
        calls.get()==2 &&
        r.state==LocalLabExperimentalDiskJournalForensics.State.READABLE_UNAUTHENTICATED_HISTORY &&
        r.observedEvents==1 && r.observedReservations==1 &&
        !r.hasDurableConsumerGrantAcknowledgement() && !r.safeToReplayAnyConsumer(),
        "genuine settled NPC and unchanged repeated file observations");
    G2058ForensicCliProcessTest.invoke(root,root.toString())
        .assertStatus(0,"READABLE_UNAUTHENTICATED_HISTORY");
} finally { G2025DiskAttemptJournalRestartWitnessTest.erase(root); }
System.out.println("G2070_FORENSICS_WITHIN_PASS_POSITIVE_PASS readOnly=true noCreditAck=true");
  }
  private G2070ForensicsWithinPassPositiveTest() {}
}
