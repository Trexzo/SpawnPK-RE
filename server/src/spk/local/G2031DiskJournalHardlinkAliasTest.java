package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Existing event and lock hardlink aliases invalidate conservative audit. */
public final class G2031DiskJournalHardlinkAliasTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("g2031-journal-links-");
        Path outside = Files.createTempDirectory("g2031-external-links-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20312031-cccc-4ccc-8ccc-203120312031");
            Path record = directory.resolve(id + ".event");
            Path eventAlias = outside.resolve("external-record-hardlink");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                Files.createLink(eventAlias, record);
                String rejected = null;
                try {
                    journal.reserve(f.world, death, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G4);
                } catch (IOException expected) { rejected = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_MULTIPLE_OR_UNKNOWN_LINKS".equals(rejected) &&
                    journal.snapshot().reservations == 1 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "record hardlink alias fails while journal is held open");
            }
            boolean reopenRejected = false;
            try (LocalLabExperimentalDiskAttemptJournal impossible =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                throw new AssertionError("record alias accepted on reopen");
            } catch (IOException expected) {
                reopenRejected = "DISK_ATTEMPT_MULTIPLE_OR_UNKNOWN_LINKS".equals(
                    expected.getMessage());
            }
            G2025DiskAttemptJournalRestartWitnessTest.check(
                reopenRejected, "hardlinked record prevents fresh owner opening");
            Files.delete(eventAlias);

            Path lockAlias = outside.resolve("external-lock-hardlink");
            Files.createLink(lockAlias, directory.resolve(".journal.lock"));
            String lockRejected = null;
            try (LocalLabExperimentalDiskAttemptJournal impossible =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                throw new AssertionError("lock alias accepted");
            } catch (IOException expected) { lockRejected = expected.getMessage(); }
            G2025DiskAttemptJournalRestartWitnessTest.check(
                "DISK_ATTEMPT_MULTIPLE_OR_UNKNOWN_LINKS".equals(lockRejected),
                "lock-file hardlink alias fails on open");
            Files.delete(lockAlias);
            try (LocalLabExperimentalDiskAttemptJournal reopened =
                    LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    reopened.snapshot().reservations == 1 &&
                    !reopened.snapshot().hasAtomicConsumerGrantAcknowledgement(),
                    "after links removed old claim still retained");
            }
        } finally {
            G2025DiskAttemptJournalRestartWitnessTest.erase(outside);
            G2025DiskAttemptJournalRestartWitnessTest.erase(directory);
        }
        System.out.println("G2031_DISK_JOURNAL_HARDLINK_ALIAS_PASS"+
            " eventAliasDenied=true lockAliasDenied=true oldClaimPreserved=true");
    }
    private G2031DiskJournalHardlinkAliasTest() {}
}
