package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Loaded test UUID is historical evidence, not permission to extend grants. */
public final class G2039DiskJournalReopenedEventAppendTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("g2039-reopen-authority-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult first = f.settle();
            UUID id = UUID.fromString("20392039-bbbb-4bbb-8bbb-203920392039");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(root)) {
                journal.reserve(f.world, first, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
            }
            try (LocalLabExperimentalDiskAttemptJournal reopened =
                     LocalLabExperimentalDiskAttemptJournal.open(root)) {
                String oldId = null, alias = null, duplicate = null;
                try {
                    reopened.reserve(f.world, first, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G4);
                } catch (IllegalStateException expected) {
                    oldId = expected.getMessage();
                }
                try {
                    reopened.reserve(f.world, first,
                        UUID.fromString("20392039-bbbb-4bbb-8bbb-203920392040"),
                        LocalLabTerminalDeliveryAssessment.Consumer.G4);
                } catch (IllegalStateException expected) {
                    alias = expected.getMessage();
                }
                try {
                    reopened.reserve(f.world, first, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G1);
                } catch (IllegalStateException expected) {
                    duplicate = expected.getMessage();
                }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_REOPENED_EVENT_APPEND_FORBIDDEN".equals(oldId) &&
                    "DISK_ATTEMPT_DUPLICATE_DEATH_FINGERPRINT".equals(alias) &&
                    "DISK_ATTEMPT_DUPLICATE_CONSUMER".equals(duplicate) &&
                    reopened.auditedSnapshot().reservations == 1,
                    "reopened old event cannot append or alias terminal");
                MonsterSpawnerPvmRuntime.FinalizeResult next = f.settle();
                reopened.reserve(f.world, next,
                    UUID.fromString("20392039-bbbb-4bbb-8bbb-203920392041"),
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    reopened.auditedSnapshot().reservations == 2 &&
                    f.world.pvmRecords().receiptCount() == 2L &&
                    !reopened.snapshot().safeToReplayAfterRestart(),
                    "new real distinct death still records fresh test UUID");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(root); }
        System.out.println("G2039_DISK_JOURNAL_REOPENED_AUTHORITY_PASS"+
            " oldStageAppendDenied=true aliasDenied=true freshDeathAllowed=true");
    }
    private G2039DiskJournalReopenedEventAppendTest() {}
}
