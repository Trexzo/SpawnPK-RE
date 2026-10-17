package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Killed subprocess before event rename becomes CLI-blocked orphan, not recovery. */
public final class G2060ForensicCliCrashBeforeMoveTest {
    public static void main(String[] args) throws Exception {
        Path scratch=Files.createTempDirectory("g2060-cli-crash-before-");
        Path dir=Files.createDirectory(scratch.resolve("journal"));
        Path marker=scratch.resolve("crashed-before.marker");
        UUID id=UUID.fromString("20602060-bbbb-4bbb-8bbb-206020602060");
        try {
            G2046DiskJournalCrossJvmLockTest.Child halted=
                G2046DiskJournalCrossJvmLockTest.start(
                    "crash-before",dir,marker,id);
            G2046DiskJournalCrossJvmLockTest.finish(halted,33);
            G2058ForensicCliProcessTest.Result r=
                G2058ForensicCliProcessTest.invoke(dir,dir.toString());
            r.assertStatus(3,"BLOCKED_ORPHAN_PENDING");
            G2025DiskAttemptJournalRestartWitnessTest.check(
                r.text.contains("reason=DISK_FORENSICS_UNRESOLVED_PENDING") &&
                Files.exists(marker) &&
                Files.notExists(dir.resolve(id+".event")) &&
                !r.text.contains(dir.toString()),
                "crash-before temp not treated as published event");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2060_FORENSICS_CLI_CRASH_BEFORE_PASS"+
            " realJVMHalt33=true blockedExit3=true noReplay=true");
    }
    private G2060ForensicCliCrashBeforeMoveTest() {}
}
