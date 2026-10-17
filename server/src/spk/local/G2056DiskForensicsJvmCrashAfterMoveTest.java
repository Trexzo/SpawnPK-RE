package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Read-only operator view after actual child JVM halt after ATOMIC_MOVE. */
public final class G2056DiskForensicsJvmCrashAfterMoveTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2056-forensics-after-");
        Path journal = Files.createDirectory(scratch.resolve("journal"));
        Path marker = scratch.resolve("child-crashed-after");
        UUID id = UUID.fromString("20562056-dddd-4ddd-8ddd-205620562056");
        try {
            G2046DiskJournalCrossJvmLockTest.Child child =
                G2046DiskJournalCrossJvmLockTest.start(
                    "crash-after", journal, marker, id);
            G2046DiskJournalCrossJvmLockTest.finish(child, 34);
            LocalLabExperimentalDiskJournalForensics.Report r =
                LocalLabExperimentalDiskJournalForensics.inspectExisting(journal);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                Files.exists(marker) &&
                r.state == LocalLabExperimentalDiskJournalForensics.State
                    .READABLE_UNAUTHENTICATED_HISTORY &&
                r.observedEvents == 1 && r.observedReservations == 1 &&
                Files.exists(journal.resolve(id + ".event")) &&
                !r.hasDurableConsumerGrantAcknowledgement() &&
                !r.hasAuthenticatedOriginalDeathEvent() &&
                !r.safeToReplayAnyConsumer(),
                "readable post-move record does not prove callback or player credit");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2056_FORENSICS_REAL_JVM_AFTER_MOVE_PASS"+
            " historicalReservationSeen=true durableGrantAck=false noReplay=true");
    }
    private G2056DiskForensicsJvmCrashAfterMoveTest() {}
}
