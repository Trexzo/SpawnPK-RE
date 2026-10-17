package spk.local;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Actual child JVM halt between forced temp bytes and publication. */
public final class G2049DiskJournalRealJvmCrashBeforeMoveTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2049-crash-before-");
        Path dir = Files.createDirectory(scratch.resolve("journal"));
        Path marker = scratch.resolve("reached-before-move");
        UUID id = UUID.fromString("20492049-dddd-4ddd-8ddd-204920492049");
        try {
            G2046DiskJournalCrossJvmLockTest.Child child =
                G2046DiskJournalCrossJvmLockTest.start(
                    "crash-before", dir, marker, id);
            String output = G2046DiskJournalCrossJvmLockTest.finish(child, 33);
            boolean tempPresent = false;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path p : stream)
                    if (p.getFileName().toString().endsWith(".tmp")) tempPresent = true;
            }
            String refused = null;
            try (LocalLabExperimentalDiskAttemptJournal neverReplay =
                     LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                throw new AssertionError("orphaned pending temp accepted");
            } catch (IOException expected) { refused = expected.getMessage(); }
            G2025DiskAttemptJournalRestartWitnessTest.check(
                Files.exists(marker) && tempPresent &&
                Files.notExists(dir.resolve(id + ".event")) &&
                "DISK_ATTEMPT_UNKNOWN_OR_UNSAFE_FILE".equals(refused),
                "real process death BEFORE move leaves inspectable orphan and fails closed");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2049_REAL_JVM_CRASH_BEFORE_MOVE_PASS" +
            " orphanPendingRetained=true noEventPublished=true" +
            " reopenFailsClosed=true neverReplay=true");
    }
    private G2049DiskJournalRealJvmCrashBeforeMoveTest() {}
}
