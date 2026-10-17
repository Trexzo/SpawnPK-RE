package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Executes the operator CLI in a completely independent JVM. */
public final class G2058ForensicCliProcessTest {
    static final class Result {
        final int exit;
        final String text;
        Result(int exit, String text) { this.exit=exit; this.text=text; }
        void assertStatus(int code, String state) {
            G2025DiskAttemptJournalRestartWitnessTest.check(
                exit == code && text.contains("state=" + state) &&
                text.contains("grantAck=false") &&
                text.contains("replay=false") &&
                text.contains("unlocked=true") &&
                text.contains("countsMayBePartial=true") &&
                !text.contains("Exception in thread") &&
                !text.contains(" at spk."),
                "CLI result expected " + code + "/" + state + " observed " + exit);
        }
    }

    static Result invoke(Path dir, String... argv) throws Exception {
        String javaName = System.getProperty("os.name").toLowerCase()
            .contains("win") ? "java.exe" : "java";
        String exe = Paths.get(System.getProperty("java.home"), "bin",
            javaName).toString();
        java.util.ArrayList<String> args = new java.util.ArrayList<String>();
        args.add(exe);
        args.add("-cp");
        args.add(System.getProperty("java.class.path"));
        args.add("spk.local.LocalLabExperimentalDiskJournalInspector");
        java.util.Collections.addAll(args, argv);
        Path out = dir.getParent().resolve("cli-out-" + UUID.randomUUID()+".txt");
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.redirectErrorStream(true);
        builder.redirectOutput(out.toFile());
        Process process = builder.start();
        if (!process.waitFor(25L, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(10L, TimeUnit.SECONDS);
            throw new AssertionError("forensics CLI timed out");
        }
        String text = new String(Files.readAllBytes(out),StandardCharsets.UTF_8);
        return new Result(process.exitValue(), text);
    }

    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2058-cli-missing-");
        try {
            Path absent = scratch.resolve("never-constructed");
            Result r = invoke(absent, absent.toString());
            r.assertStatus(2,"NO_EXISTING_JOURNAL");
            G2025DiskAttemptJournalRestartWitnessTest.check(
                r.text.contains("events=0 reservations=0") &&
                r.text.contains("originalEventAuthority=false") &&
                r.text.contains("ownerEpoch=false") &&
                !r.text.contains(absent.toString()) &&
                Files.notExists(absent),
                "missing journal and path remain untouched");
            Result usage = invoke(absent);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                usage.exit == 64 &&
                usage.text.contains("SPK_JOURNAL_FORENSICS_USAGE") &&
                Files.notExists(absent),
                "no-argument CLI never creates journal");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2058_FORENSICS_CLI_MISSING_PASS"+
            " childJvmExit2=true noDirectoryOrLockCreated=true"+
            " invalidUsageExit64=true noSensitivePathOutput=true");
    }
    private G2058ForensicCliProcessTest() {}
}
