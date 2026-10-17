package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/** Actual canonical NPC settlement with read-only forensic event count. */
public final class G2053DiskForensicsRealNpcHistoryTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("g2053-forensics-history-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20532053-aaaa-4aaa-8aaa-205320532053");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                    LocalLabExperimentalDiskAttemptJournal.open(root)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G4);
            }
            Path event = root.resolve(id + ".event");
            byte[] before = Files.readAllBytes(event);
            byte[] lockBefore = Files.readAllBytes(root.resolve(".journal.lock"));
            LocalLabExperimentalDiskJournalForensics.Report r =
                LocalLabExperimentalDiskJournalForensics.inspectExisting(root);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                r.state == LocalLabExperimentalDiskJournalForensics.State
                    .READABLE_UNAUTHENTICATED_HISTORY &&
                r.observedEvents == 1 && r.observedReservations == 2 &&
                Arrays.equals(before, Files.readAllBytes(event)) &&
                Arrays.equals(lockBefore, Files.readAllBytes(root.resolve(".journal.lock"))) &&
                f.world.pvmRecords().receiptCount() == 1L &&
                !r.hasAuthenticatedOriginalDeathEvent() &&
                !r.hasDurableConsumerGrantAcknowledgement() &&
                !r.safeToReplayAnyConsumer(),
                "operator inspection parses real history but cannot mint a grant");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(root); }
        System.out.println("G2053_FORENSICS_REAL_HISTORY_PASS"+
            " genuineNpcDeaths=1 stagesObserved=2 filesUnchanged=true noAck=true");
    }
    private G2053DiskForensicsRealNpcHistoryTest() {}
}
