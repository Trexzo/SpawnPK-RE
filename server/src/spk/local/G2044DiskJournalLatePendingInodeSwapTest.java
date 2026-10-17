package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Byte-identical new inode at the exact owned pending filename is refused. */
public final class G2044DiskJournalLatePendingInodeSwapTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2044-pending-");
        Path dir = Files.createDirectory(scratch.resolve("journal"));
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20442044-dddd-4ddd-8ddd-204420442044");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                String refusal = null;
                try {
                    journal.reserveWithTestCutpoint(f.world, death, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G1,
                        (pending, destination) -> {
                            Path replacement = scratch.resolve("same-byte-different-inode");
                            Files.write(replacement, Files.readAllBytes(pending));
                            Files.move(replacement, pending,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                        });
                } catch (IOException expected) { refusal = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_PENDING_IDENTITY_CHANGED".equals(refusal) &&
                    Files.notExists(dir.resolve(id + ".event")) &&
                    journal.snapshot().reservations == 0 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "same exact pending bytes on new inode never authorizes move");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2044_DISK_JOURNAL_PENDING_INODE_SWAP_PASS" +
            " sameBytePendingReplacementDenied=true noEventPublished=true");
    }
    private G2044DiskJournalLatePendingInodeSwapTest() {}
}
