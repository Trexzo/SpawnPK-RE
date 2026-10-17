package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual ground-settled death, two unchanged unlocked scans still parse. */
public final class G2064ForensicsTwoPassStableTest {
    static final UUID ID = UUID.fromString("20642064-aaaa-4aaa-8aaa-206420642064");

    static void seed(Path directory) throws Exception {
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture();
             LocalLabExperimentalDiskAttemptJournal journal =
                 LocalLabExperimentalDiskAttemptJournal.open(directory)) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            journal.reserve(f.world, death, ID,
                LocalLabTerminalDeliveryAssessment.Consumer.G1);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                f.world.pvmRecords().receiptCount() == 1L,
                "one real canonical ground receipt");
        }
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("g2064-scan-stable-");
        try {
            seed(dir);
            AtomicInteger scans=new AtomicInteger();
            LocalLabExperimentalDiskJournalForensics.Report result =
                LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoint(
                    dir, scans::incrementAndGet);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                scans.get()==1 &&
                result.state==LocalLabExperimentalDiskJournalForensics.State
                    .READABLE_UNAUTHENTICATED_HISTORY &&
                result.observedEvents==1 && result.observedReservations==1 &&
                !result.hasAuthenticatedOriginalDeathEvent() &&
                !result.hasDurableConsumerGrantAcknowledgement() &&
                !result.hasExclusiveCrossJvmOwnerEpoch() &&
                !result.safeToReplayAnyConsumer() &&
                result.isUnlockedReadOnlyObservation(),
                "two identical scans readable without original event/grant authority");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(dir); }
        System.out.println("G2064_FORENSICS_TWO_PASS_STABLE_PASS"
            +" scans=2 readOnly=true grantAck=false replay=false");
    }
    private G2064ForensicsTwoPassStableTest() {}
}
