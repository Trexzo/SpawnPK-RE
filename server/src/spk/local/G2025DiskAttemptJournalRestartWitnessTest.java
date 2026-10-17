package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/** Real settled NPC; journal close/reopen retains test-only reservations. */
public final class G2025DiskAttemptJournalRestartWitnessTest {
    static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError("G2025 " + reason);
    }

    static void erase(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : (Iterable<Path>)walk.sorted(Comparator.reverseOrder())::iterator)
                Files.deleteIfExists(path);
        }
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("g2025-journal-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID externalTestId = UUID.fromString(
                "20252025-aaaa-4aaa-8aaa-202520252025");
            LocalLabExperimentalDiskAttemptJournal.Snapshot previous;
            try (LocalLabExperimentalDiskAttemptJournal disk =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                disk.reserve(f.world, death, externalTestId,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                previous = disk.snapshot();
                disk.reserve(f.world, death, externalTestId,
                    LocalLabTerminalDeliveryAssessment.Consumer.G4);
                check(previous.reservations == 1 &&
                    disk.snapshot().reservations == 2 &&
                    !previous.hasAtomicConsumerGrantAcknowledgement(),
                    "snapshot is detached from later disk reservation");
            }
            try (LocalLabExperimentalDiskAttemptJournal reopened =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                check(reopened.snapshot().reservations == 2 &&
                    reopened.snapshot().entries.get(externalTestId).reserved.size() == 2,
                    "two claims survive close and reopen");
                String denied = null;
                try {
                    reopened.reserve(f.world, death, externalTestId,
                        LocalLabTerminalDeliveryAssessment.Consumer.G1);
                } catch (IllegalStateException expected) {
                    denied = expected.getMessage();
                }
                check("DISK_ATTEMPT_DUPLICATE_CONSUMER".equals(denied) &&
                    reopened.snapshot().reservations == 2 &&
                    !reopened.snapshot().safeToReplayAfterRestart() &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "disk duplicate is denied without replaying drop or reward");
            }
        } finally { erase(directory); }
        System.out.println("G2025_DISK_ATTEMPT_REOPEN_PASS realSettledDeath=true" +
            " stageReservations=2 repeatedStageDenied=true detachedSnapshot=true" +
            " noGrantAckOrRestartReplay=true");
    }
    private G2025DiskAttemptJournalRestartWitnessTest() {}
}
