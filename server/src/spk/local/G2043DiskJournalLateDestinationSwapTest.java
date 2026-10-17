package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

/** Byte-identical destination inode replacement at the publication cutpoint. */
public final class G2043DiskJournalLateDestinationSwapTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2043-destination-");
        Path dir = Files.createDirectory(scratch.resolve("journal"));
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20432043-cccc-4ccc-8ccc-204320432043");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                Path old = dir.resolve(id + ".event");
                byte[] before = Files.readAllBytes(old);
                Path newInode = scratch.resolve("external-same-byte-copy");
                String refused = null;
                try {
                    journal.reserveWithTestCutpoint(f.world, death, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G4,
                        (pending, destination) -> {
                            Files.write(newInode, before);
                            Files.move(newInode, destination,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                        });
                } catch (IOException expected) { refused = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_DISK_STATE_DIVERGED".equals(refused) &&
                    Arrays.equals(before, Files.readAllBytes(old)) &&
                    journal.snapshot().reservations == 1 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "late outside inode replacement stopped before journal ATOMIC_MOVE");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2043_DISK_JOURNAL_DESTINATION_SWAP_PASS" +
            " sameBytesNewInodeDetected=true oldRecordRetained=true");
    }
    private G2043DiskJournalLateDestinationSwapTest() {}
}
