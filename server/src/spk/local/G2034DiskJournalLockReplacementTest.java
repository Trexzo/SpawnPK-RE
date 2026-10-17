package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Cooperative lock on old inode does not validate a new same-name lock file. */
public final class G2034DiskJournalLockReplacementTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("g2034-lock-replace-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20342034-bbbb-4bbb-8bbb-203420342034");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                Path replacement = directory.resolve(".operator-lock-replacement");
                Files.write(replacement, new byte[0]);
                Files.move(replacement, directory.resolve(".journal.lock"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                String readRefusal = null, writeRefusal = null;
                try { journal.auditedSnapshot(); }
                catch (IOException expected) { readRefusal = expected.getMessage(); }
                try {
                    journal.reserve(f.world, death, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G4);
                } catch (IOException expected) { writeRefusal = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_LOCK_IDENTITY_CHANGED".equals(readRefusal) &&
                    "DISK_ATTEMPT_LOCK_IDENTITY_CHANGED".equals(writeRefusal) &&
                    journal.snapshot().reservations == 1 &&
                    Files.exists(directory.resolve(id + ".event")) &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "new inode at lock path does not inherit original held exclusive lock");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(directory); }
        System.out.println("G2034_DISK_JOURNAL_LOCK_SWAP_PASS"+
            " lockPathIdentityPinned=true auditRefused=true appendRefused=true");
    }
    private G2034DiskJournalLockReplacementTest() {}
}
