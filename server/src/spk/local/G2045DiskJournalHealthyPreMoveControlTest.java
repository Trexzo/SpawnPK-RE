package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Genuine healthy initial and stage extension continue through strict pre-move. */
public final class G2045DiskJournalHealthyPreMoveControlTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("g2045-healthy-publication-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20452045-eeee-4eee-8eee-204520452045");
            AtomicInteger cutpoints = new AtomicInteger();
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                LocalLabExperimentalDiskAttemptJournal.PublicationTestCutpoint hook =
                    (pending, destination) -> {
                        G2025DiskAttemptJournalRestartWitnessTest.check(
                            Files.isRegularFile(pending) &&
                            pending.getFileName().toString().endsWith(".tmp") &&
                            destination.getFileName().toString().equals(id + ".event") &&
                            Files.size(pending) > 0L,
                            "healthy hook follows real forced pending NIO bytes");
                        cutpoints.incrementAndGet();
                    };
                journal.reserveWithTestCutpoint(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1, hook);
                journal.reserveWithTestCutpoint(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G4, hook);
                LocalLabExperimentalDiskAttemptJournal.Snapshot snapshot =
                    journal.auditedSnapshot();
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    cutpoints.get() == 2 && snapshot.reservations == 2 &&
                    snapshot.entries.size() == 1 &&
                    snapshot.entries.get(id).reserved.size() == 2 &&
                    f.world.pvmRecords().receiptCount() == 1L &&
                    !snapshot.hasAtomicConsumerGrantAcknowledgement() &&
                    !snapshot.safeToReplayAfterRestart(),
                    "ordinary new and existing journal commits still succeed");
            }
            try (LocalLabExperimentalDiskAttemptJournal reopened =
                     LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    reopened.auditedSnapshot().reservations == 2 &&
                    !reopened.auditedSnapshot().hasOriginalServerEventAuthority(),
                    "healthy read-only history persists after close/open");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(dir); }
        System.out.println("G2045_DISK_JOURNAL_HEALTHY_PREMOVE_PASS" +
            " forcedTempChecks=2 newAndExistingEventHealthy=true" +
            " historicalReadOnly=true");
    }
    private G2045DiskJournalHealthyPreMoveControlTest() {}
}
