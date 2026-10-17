package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Read-only inspector does not follow redirected parent or hardlinked event. */
public final class G2057DiskForensicsSymlinkHardlinkDenialTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2057-forensics-links-");
        Path parent = Files.createDirectory(scratch.resolve("real"));
        Path journalDir = Files.createDirectory(parent.resolve("journal"));
        Path aliasParent = scratch.resolve("redirected");
        Path hardlink = scratch.resolve("foreign-event-alias");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20572057-eeee-4eee-8eee-205720572057");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(journalDir)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
            }
            Files.createSymbolicLink(aliasParent, parent);
            LocalLabExperimentalDiskJournalForensics.Report redirected =
                LocalLabExperimentalDiskJournalForensics.inspectExisting(
                    aliasParent.resolve("journal"));
            G2025DiskAttemptJournalRestartWitnessTest.check(
                redirected.state == LocalLabExperimentalDiskJournalForensics.State
                    .BLOCKED_UNSAFE_PATH &&
                !redirected.safeToReplayAnyConsumer(),
                "symlinked ancestor must not be traversed as clean history");
            Files.createLink(hardlink, journalDir.resolve(id + ".event"));
            LocalLabExperimentalDiskJournalForensics.Report linked =
                LocalLabExperimentalDiskJournalForensics.inspectExisting(journalDir);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                linked.state == LocalLabExperimentalDiskJournalForensics.State
                    .BLOCKED_UNSAFE_PATH &&
                Files.exists(hardlink) &&
                f.world.pvmRecords().receiptCount() == 1L,
                "outside event hardlink refuses rather than classifying as trusted");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2057_FORENSICS_SYMLINK_HARDLINK_PASS"+
            " ancestorDenied=true eventAliasDenied=true noMutation=true");
    }
    private G2057DiskForensicsSymlinkHardlinkDenialTest() {}
}
