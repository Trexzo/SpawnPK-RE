package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** 43 real settled NPCs exercise 258 stages, retaining 256 across reopen. */
public final class G2028DiskAttemptJournalCapacityRetentionTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("g2028-journal-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            int accepted = 0, denied = 0;
            MonsterSpawnerPvmRuntime.FinalizeResult first = null;
            UUID firstId = null;
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                for (int i = 0; i < 43; i++) {
                    MonsterSpawnerPvmRuntime.FinalizeResult terminal = f.settle();
                    UUID key = new UUID(0x2028202820284028L, (long)i + 1L);
                    if (first == null) { first = terminal; firstId = key; }
                    for (LocalLabTerminalDeliveryAssessment.Consumer stage :
                            LocalLabTerminalDeliveryAssessment.Consumer.values()) {
                        try {
                            journal.reserve(f.world, terminal, key, stage);
                            accepted++;
                        } catch (IllegalStateException expected) {
                            G2025DiskAttemptJournalRestartWitnessTest.check(
                                "DISK_ATTEMPT_CAPACITY_FAIL_CLOSED".equals(
                                    expected.getMessage()),
                                "only capacity can deny final two reservations");
                            denied++;
                        }
                    }
                }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    accepted == 256 && denied == 2 &&
                    journal.snapshot().reservations == 256 &&
                    f.world.pvmRecords().receiptCount() == 43L &&
                    !journal.snapshot().hasAtomicConsumerGrantAcknowledgement(),
                    "capacity exact without evicting old disk claims");
            }
            try (LocalLabExperimentalDiskAttemptJournal reopened =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                String duplicate = null;
                try {
                    reopened.reserve(f.world, first, firstId,
                        LocalLabTerminalDeliveryAssessment.Consumer.G1);
                } catch (IllegalStateException expected) {
                    duplicate = expected.getMessage();
                }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    reopened.snapshot().reservations == 256 &&
                    "DISK_ATTEMPT_DUPLICATE_CONSUMER".equals(duplicate) &&
                    !reopened.snapshot().safeToReplayAfterRestart() &&
                    f.world.pvmRecords().receiptCount() == 43L,
                    "first claim persists across reopen at saturated capacity");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(directory); }
        System.out.println("G2028_DISK_ATTEMPT_CAPACITY_PASS genuineDeaths=43" +
            " retainedClaims=256 capacityDenials=2 reopenDuplicateDenied=true" +
            " noEvictionOrReplay=true");
    }
    private G2028DiskAttemptJournalCapacityRetentionTest() {}
}
