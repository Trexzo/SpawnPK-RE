package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** External modification of an old event cannot be overwritten in same JVM. */
public final class G2029DiskJournalLiveTamperTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("g2029-live-corruption-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20292029-aaaa-4aaa-8aaa-202920292029");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                Path record = directory.resolve(id + ".event");
                byte[] tampered = Files.readAllBytes(record);
                tampered[tampered.length - 2] =
                    (byte)(tampered[tampered.length - 2] == 'a' ? 'b' : 'a');
                Files.write(record, tampered,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                byte[] before = Files.readAllBytes(record);
                String refusal = null;
                try {
                    journal.reserve(f.world, death, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G4);
                } catch (IOException expected) {
                    refusal = expected.getMessage();
                }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    refusal != null && refusal.startsWith("DISK_ATTEMPT_") &&
                    java.util.Arrays.equals(before, Files.readAllBytes(record)) &&
                    journal.snapshot().reservations == 1 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "in-place external record corruption cannot be overwritten");
            }
            boolean reopenRejected = false;
            try (LocalLabExperimentalDiskAttemptJournal unexpected =
                    LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                throw new AssertionError("tampered record reopened");
            } catch (IOException expected) { reopenRejected = true; }
            G2025DiskAttemptJournalRestartWitnessTest.check(
                reopenRejected, "corrupted record never silently recovered");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(directory); }
        System.out.println("G2029_DISK_JOURNAL_LIVE_TAMPER_PASS"+
            " sameInodeTamperRefused=true priorBytesPreserved=true noGrant=true");
    }
    private G2029DiskJournalLiveTamperTest() {}
}
