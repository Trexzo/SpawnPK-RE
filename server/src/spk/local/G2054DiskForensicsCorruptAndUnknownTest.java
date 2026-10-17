package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;

/** Corrupt disk bytes and unknown files are reported without auto-repair. */
public final class G2054DiskForensicsCorruptAndUnknownTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("g2054-forensics-corrupt-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20542054-bbbb-4bbb-8bbb-205420542054");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(root)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
            }
            Path event = root.resolve(id + ".event");
            byte[] changed = Files.readAllBytes(event);
            changed[changed.length - 2] = (byte)(changed[changed.length - 2] == 'a' ? 'b' : 'a');
            Files.write(event, changed, StandardOpenOption.TRUNCATE_EXISTING);
            LocalLabExperimentalDiskJournalForensics.Report damaged =
                LocalLabExperimentalDiskJournalForensics.inspectExisting(root);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                damaged.state == LocalLabExperimentalDiskJournalForensics.State
                    .BLOCKED_MALFORMED_OR_CORRUPT_RECORD &&
                Arrays.equals(changed, Files.readAllBytes(event)) &&
                !damaged.safeToReplayAnyConsumer() &&
                f.world.pvmRecords().receiptCount() == 1L,
                "invalid checksum is diagnostic, never silently repaired");
            Files.delete(event);
            Path unknown = root.resolve("unexpected-operator-sidefile");
            Files.write(unknown, new byte[] {9});
            LocalLabExperimentalDiskJournalForensics.Report foreign =
                LocalLabExperimentalDiskJournalForensics.inspectExisting(root);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                foreign.state == LocalLabExperimentalDiskJournalForensics.State
                    .BLOCKED_UNSAFE_PATH &&
                "DISK_FORENSICS_UNKNOWN_ENTRY".equals(foreign.reasonCode) &&
                Files.exists(unknown) &&
                !foreign.hasExclusiveCrossJvmOwnerEpoch(),
                "unexpected operator file remains untouched and blocks clean report");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(root); }
        System.out.println("G2054_FORENSICS_CORRUPTION_UNKNOWN_PASS"+
            " checksumDenied=true unexpectedFileDenied=true filesNotRepaired=true");
    }
    private G2054DiskForensicsCorruptAndUnknownTest() {}
}
