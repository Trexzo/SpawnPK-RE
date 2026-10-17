package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** A new hardlink to the owned pending inode must block event publication. */
public final class G2042DiskJournalPreMoveTempHardlinkTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("g2042-temp-hardlink-");
        Path external = Files.createTempDirectory("g2042-outside-hardlink-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20422042-bbbb-4bbb-8bbb-204220422042");
            Path alias = external.resolve("foreign-pending-alias");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                String denied = null;
                try {
                    journal.reserveWithTestCutpoint(f.world, death, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G1,
                        (pending, destination) -> Files.createLink(alias, pending));
                } catch (IOException expected) { denied = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_MULTIPLE_OR_UNKNOWN_LINKS".equals(denied) &&
                    Files.exists(alias) &&
                    Files.notExists(dir.resolve(id + ".event")) &&
                    journal.snapshot().reservations == 0 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "no journal record published while owned temp has an alias");
            }
        } finally {
            G2025DiskAttemptJournalRestartWitnessTest.erase(external);
            G2025DiskAttemptJournalRestartWitnessTest.erase(dir);
        }
        System.out.println("G2042_DISK_JOURNAL_PENDING_HARDLINK_PASS" +
            " aliasBeforeMoveDenied=true noEventPublished=true noGrant=true");
    }
    private G2042DiskJournalPreMoveTempHardlinkTest() {}
}
