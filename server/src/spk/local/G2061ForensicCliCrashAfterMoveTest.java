package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Killed subprocess after rename yields historical readable, not grant ACK. */
public final class G2061ForensicCliCrashAfterMoveTest {
    public static void main(String[] args) throws Exception {
        Path scratch=Files.createTempDirectory("g2061-cli-crash-after-");
        Path dir=Files.createDirectory(scratch.resolve("journal"));
        Path marker=scratch.resolve("crashed-after.marker");
        UUID id=UUID.fromString("20612061-cccc-4ccc-8ccc-206120612061");
        try {
            G2046DiskJournalCrossJvmLockTest.Child halted=
                G2046DiskJournalCrossJvmLockTest.start(
                    "crash-after",dir,marker,id);
            G2046DiskJournalCrossJvmLockTest.finish(halted,34);
            G2058ForensicCliProcessTest.Result r=
                G2058ForensicCliProcessTest.invoke(dir,dir.toString());
            r.assertStatus(0,"READABLE_UNAUTHENTICATED_HISTORY");
            G2025DiskAttemptJournalRestartWitnessTest.check(
                r.text.contains("events=1 reservations=1") &&
                Files.exists(dir.resolve(id+".event")) &&
                Files.exists(marker) &&
                r.text.contains("grantAck=false") &&
                r.text.contains("replay=false"),
                "crash-after persisted reservation never asserts actual consumer credit");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2061_FORENSICS_CLI_CRASH_AFTER_PASS"+
            " realJVMHalt34=true readableUnauthenticated=true grantAck=false");
    }
    private G2061ForensicCliCrashAfterMoveTest() {}
}
