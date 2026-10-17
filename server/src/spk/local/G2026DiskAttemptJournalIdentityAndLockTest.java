package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** World identity, conflicting deaths, and a second cooperative journal owner. */
public final class G2026DiskAttemptJournalIdentityAndLockTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("g2026-journal-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            World foreign = World.isolatedForTest(60_000L);
            try {
                MonsterSpawnerPvmRuntime.FinalizeResult first = f.settle();
                UUID fixedTestId = UUID.fromString(
                    "20262026-bbbb-4bbb-8bbb-202620262026");
                try (LocalLabExperimentalDiskAttemptJournal journal =
                         LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                    String foreignDenied = null;
                    try {
                        journal.reserve(foreign, first, fixedTestId,
                            LocalLabTerminalDeliveryAssessment.Consumer.G1);
                    } catch (IllegalStateException expected) {
                        foreignDenied = expected.getMessage();
                    }
                    G2025DiskAttemptJournalRestartWitnessTest.check(
                        "SETTLED_DEATH_ENVELOPE_SOURCE_WORLD_MISMATCH".equals(
                            foreignDenied) && journal.snapshot().reservations == 0,
                        "foreign world cannot publish reservation");
                    boolean lockRefused = false;
                    try (LocalLabExperimentalDiskAttemptJournal impossible =
                             LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                        throw new AssertionError("simultaneous journal owner opened");
                    } catch (IOException expected) {
                        lockRefused = expected.getMessage().contains(
                            "DISK_ATTEMPT_DIRECTORY_LOCKED");
                    }
                    G2025DiskAttemptJournalRestartWitnessTest.check(
                        lockRefused, "second cooperative owner refused");
                    journal.reserve(f.world, first, fixedTestId,
                        LocalLabTerminalDeliveryAssessment.Consumer.G1);
                    MonsterSpawnerPvmRuntime.FinalizeResult second = f.settle();
                    String conflict = null;
                    try {
                        journal.reserve(f.world, second, fixedTestId,
                            LocalLabTerminalDeliveryAssessment.Consumer.G4);
                    } catch (IllegalStateException expected) {
                        conflict = expected.getMessage();
                    }
                    G2025DiskAttemptJournalRestartWitnessTest.check(
                        "DISK_ATTEMPT_CONFLICTING_DEATH_EVIDENCE".equals(conflict) &&
                        journal.snapshot().reservations == 1 &&
                        f.world.pvmRecords().receiptCount() == 2L &&
                        !journal.snapshot().hasOriginalServerEventAuthority(),
                        "reused caller UUID cannot silently alias a second death");
                }
            } finally { foreign.close(); }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(directory); }
        System.out.println("G2026_DISK_ATTEMPT_IDENTITY_LOCK_PASS foreignWorldDenied=true" +
            " concurrentOwnerDenied=true conflictingDeathDenied=true noGrant=true");
    }
    private G2026DiskAttemptJournalIdentityAndLockTest() {}
}
