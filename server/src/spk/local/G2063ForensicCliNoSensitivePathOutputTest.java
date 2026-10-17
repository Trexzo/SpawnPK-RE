package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Stable operator result has no supplied absolute directory or username leak. */
public final class G2063ForensicCliNoSensitivePathOutputTest {
    public static void main(String[] args) throws Exception {
        Path scratch=Files.createTempDirectory("g2063-private-marker-");
        Path dir=Files.createDirectory(scratch.resolve("name-private-account"));
        try {
            // Synthetic unauthenticated filename is not treated as event,
            // and must not leak into CLI output.
            Files.write(dir.resolve(".journal.lock"),new byte[0]);
            Path foreign=dir.resolve("email-private-user-should-not-appear");
            Files.write(foreign,new byte[] {42});
            G2058ForensicCliProcessTest.Result r=
                G2058ForensicCliProcessTest.invoke(dir,dir.toString());
            r.assertStatus(3,"BLOCKED_UNSAFE_PATH");
            G2025DiskAttemptJournalRestartWitnessTest.check(
                !r.text.contains(scratch.toString()) &&
                !r.text.contains("email-private-user-should-not-appear") &&
                !r.text.contains("name-private-account") &&
                r.text.contains("events=0 reservations=0") &&
                Files.exists(foreign),
                "CLI diagnostic must not emit account names or supplied path");
            G2058ForensicCliProcessTest.Result extra=
                G2058ForensicCliProcessTest.invoke(
                    dir,dir.toString(),"extra");
            G2025DiskAttemptJournalRestartWitnessTest.check(
                extra.exit==64 &&
                !extra.text.contains(dir.toString()) &&
                extra.text.contains("SPK_JOURNAL_FORENSICS_USAGE"),
                "invalid argument count sanitized");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2063_FORENSICS_CLI_REDACTION_PASS"+
            " stdoutContainsNoSensitivePath=true invalidArgsSafe=true");
    }
    private G2063ForensicCliNoSensitivePathOutputTest() {}
}
