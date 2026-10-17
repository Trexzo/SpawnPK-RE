package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Held-open journal rejects path reuse after its original directory is moved. */
public final class G2033DiskJournalDirectoryReplacementTest {
    public static void main(String[] args) throws Exception {
        Path original = Files.createTempDirectory("g2033-directory-");
        Path relocated = original.resolveSibling(original.getFileName() + ".relocated");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20332033-aaaa-4aaa-8aaa-203320332033");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                    LocalLabExperimentalDiskAttemptJournal.open(original)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                Files.move(original, relocated, StandardCopyOption.ATOMIC_MOVE);
                Files.createDirectory(original);
                String auditedDenied = null;
                try {
                    journal.auditedSnapshot();
                } catch (IOException expected) {
                    auditedDenied = expected.getMessage();
                }
                String reserveDenied = null;
                try {
                    journal.reserve(f.world, death, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G4);
                } catch (IOException expected) {
                    reserveDenied = expected.getMessage();
                }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_DIRECTORY_IDENTITY_CHANGED".equals(auditedDenied) &&
                    "DISK_ATTEMPT_DIRECTORY_IDENTITY_CHANGED".equals(reserveDenied) &&
                    Files.exists(relocated.resolve(id + ".event")) &&
                    Files.notExists(original.resolve(id + ".event")) &&
                    journal.snapshot().reservations == 1 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "renamed directory and new same path cannot inherit held journal");
            }
        } finally {
            G2025DiskAttemptJournalRestartWitnessTest.erase(original);
            G2025DiskAttemptJournalRestartWitnessTest.erase(relocated);
        }
        System.out.println("G2033_DISK_JOURNAL_DIRECTORY_SWAP_PASS"+
            " oldDirectoryIdentityHeld=true newPathRefused=true oldRecordPreserved=true");
    }
    private G2033DiskJournalDirectoryReplacementTest() {}
}
