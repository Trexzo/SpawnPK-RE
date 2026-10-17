package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Read-only operator view after actual child JVM halt before event rename. */
public final class G2055DiskForensicsJvmCrashBeforeMoveTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2055-forensics-before-");
        Path journal = Files.createDirectory(scratch.resolve("journal"));
        Path marker = scratch.resolve("child-crashed-before");
        UUID id = UUID.fromString("20552055-cccc-4ccc-8ccc-205520552055");
        try {
            G2046DiskJournalCrossJvmLockTest.Child child =
                G2046DiskJournalCrossJvmLockTest.start(
                    "crash-before", journal, marker, id);
            G2046DiskJournalCrossJvmLockTest.finish(child, 33);
            LocalLabExperimentalDiskJournalForensics.Report r =
                LocalLabExperimentalDiskJournalForensics.inspectExisting(journal);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                Files.exists(marker) &&
                r.state == LocalLabExperimentalDiskJournalForensics.State
                    .BLOCKED_ORPHAN_PENDING &&
                "DISK_FORENSICS_UNRESOLVED_PENDING".equals(r.reasonCode) &&
                Files.notExists(journal.resolve(id + ".event")) &&
                !r.safeToReplayAnyConsumer(),
                "real JVM crash before move is an unresolved temp, not a death event");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2055_FORENSICS_REAL_JVM_BEFORE_MOVE_PASS"+
            " orphanClassified=true noEventCreated=true noReplay=true");
    }
    private G2055DiskForensicsJvmCrashBeforeMoveTest() {}
}
