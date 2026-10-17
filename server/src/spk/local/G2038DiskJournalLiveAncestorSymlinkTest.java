package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Redirected ancestor is refused even when target remains the SAME inode. */
public final class G2038DiskJournalLiveAncestorSymlinkTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2038-ancestor-live-");
        Path parent = Files.createDirectory(scratch.resolve("original-parent"));
        Path journalDir = Files.createDirectory(parent.resolve("journal"));
        Path movedParent = scratch.resolve("relocated-parent");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20382038-aaaa-4aaa-8aaa-203820382038");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(journalDir)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                Files.move(parent, movedParent, StandardCopyOption.ATOMIC_MOVE);
                Files.createSymbolicLink(parent, movedParent);
                String read = null, append = null;
                try { journal.auditedSnapshot(); }
                catch (IOException expected) { read = expected.getMessage(); }
                try {
                    journal.reserve(f.world, death, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G4);
                } catch (IOException expected) { append = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_UNSAFE_ANCESTOR_LINK".equals(read) &&
                    "DISK_ATTEMPT_UNSAFE_ANCESTOR_LINK".equals(append) &&
                    Files.exists(movedParent.resolve("journal").resolve(id + ".event")) &&
                    journal.snapshot().reservations == 1 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "same underlying journal inode through symlink still unsafe");
            }
        } finally {
            Files.deleteIfExists(parent);
            G2025DiskAttemptJournalRestartWitnessTest.erase(movedParent);
            G2025DiskAttemptJournalRestartWitnessTest.erase(scratch);
        }
        System.out.println("G2038_DISK_JOURNAL_LIVE_ANCESTOR_PASS"+
            " originalDirectorySameInode=true redirectDetected=true noAppend=true");
    }
    private G2038DiskJournalLiveAncestorSymlinkTest() {}
}
