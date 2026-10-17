package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Unaccounted temp and damaged on-disk evidence prevent opening. */
public final class G2027DiskAttemptJournalCorruptionFailClosedTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("g2027-journal-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            Path orphan = directory.resolve("unpublished-attempt.tmp");
            Files.write(orphan, new byte[]{42},
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            String orphanRejected = null;
            try (LocalLabExperimentalDiskAttemptJournal unexpected =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                throw new AssertionError("unsafe temp was discarded silently");
            } catch (IOException expected) {
                orphanRejected = expected.getMessage();
            }
            G2025DiskAttemptJournalRestartWitnessTest.check(
                "DISK_ATTEMPT_UNKNOWN_OR_UNSAFE_FILE".equals(orphanRejected),
                "orphaned temp fails closed");
            Files.delete(orphan);
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20272027-cccc-4ccc-8ccc-202720272027");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
            }
            Path record = directory.resolve(id.toString() + ".event");
            Files.write(record, "altered\n".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);
            String corruption = null;
            try (LocalLabExperimentalDiskAttemptJournal unexpected =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                throw new AssertionError("corrupt event record was accepted");
            } catch (IOException expected) {
                corruption = expected.getMessage();
            }
            G2025DiskAttemptJournalRestartWitnessTest.check(
                corruption != null &&
                (corruption.startsWith("DISK_ATTEMPT_MALFORMED") ||
                 corruption.startsWith("DISK_ATTEMPT_RECORD_INTEGRITY")) &&
                f.world.pvmRecords().receiptCount() == 1L,
                "corruption never authorizes claims or another settlement");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(directory); }
        System.out.println("G2027_DISK_ATTEMPT_CORRUPTION_FAIL_CLOSED_PASS" +
            " orphanTempDenied=true tamperedRecordDenied=true noRecoveryReplay=true");
    }
    private G2027DiskAttemptJournalCorruptionFailClosedTest() {}
}
