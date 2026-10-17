package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Checked snapshot is an opt-in disk audit, never a consumer ACK. */
public final class G2035DiskJournalAuditedSnapshotTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("g2035-audited-read-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20352035-cccc-4ccc-8ccc-203520352035");
            LocalLabExperimentalDiskAttemptJournal.Snapshot first;
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                first = journal.auditedSnapshot();
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    first.reservations == 1 &&
                    first.entries.get(id).reserved.size() == 1 &&
                    !first.hasAtomicConsumerGrantAcknowledgement() &&
                    !first.safeToReplayAfterRestart(),
                    "verified one durable reservation but no grants");
                Path unknown = directory.resolve("untracked-crash.tmp");
                Files.write(unknown, new byte[] {1});
                String refusal = null;
                try { journal.auditedSnapshot(); }
                catch (IOException expected) { refusal = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_UNKNOWN_OR_UNSAFE_FILE".equals(refusal) &&
                    Files.exists(unknown) &&
                    first.reservations == 1,
                    "late unknown temp invalidates audited read without mutating old snapshot");
                // Only this test fixture, not production, deletes its injected temp.
                Files.delete(unknown);
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G4);
                LocalLabExperimentalDiskAttemptJournal.Snapshot second =
                    journal.auditedSnapshot();
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    first.reservations == 1 &&
                    second.reservations == 2 &&
                    second.entries.get(id).reserved.size() == 2 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "checked snapshots are immutable detached copies");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(directory); }
        System.out.println("G2035_DISK_JOURNAL_AUDITED_SNAPSHOT_PASS"+
            " checkedDiskEvidence=true lateUnknownRefused=true detached=true noAck=true");
    }
    private G2035DiskJournalAuditedSnapshotTest() {}
}
