package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Shared helper + true second-JVM cooperative file-lock regression. */
public final class G2046DiskJournalCrossJvmLockTest {
    static final class Child {
        final Process process;
        final Path output;
        Child(Process process, Path output) {
            this.process = process;
            this.output = output;
        }
    }

    static Child start(String mode, Path dir, Path marker, UUID id)
        throws Exception {
        String binary = System.getProperty("os.name").toLowerCase()
            .contains("win") ? "java.exe" : "java";
        String javaExe = Paths.get(System.getProperty("java.home"),
            "bin", binary).toString();
        Path log = dir.getParent().resolve("child-" + mode + "-" +
            UUID.randomUUID().toString() + ".log");
        ProcessBuilder command = new ProcessBuilder(javaExe, "-cp",
            System.getProperty("java.class.path"),
            "spk.local.G2046DiskJournalChildWorker", mode,
            dir.toString(), marker.toString(), id.toString());
        command.redirectErrorStream(true);
        command.redirectOutput(log.toFile());
        return new Child(command.start(), log);
    }

    static String finish(Child child, int expected) throws Exception {
        if (!child.process.waitFor(30, TimeUnit.SECONDS)) {
            child.process.destroyForcibly();
            child.process.waitFor(10, TimeUnit.SECONDS);
            throw new AssertionError("child timed out: " + child.output);
        }
        String out = new String(Files.readAllBytes(child.output),
            StandardCharsets.UTF_8);
        G2025DiskAttemptJournalRestartWitnessTest.check(
            child.process.exitValue() == expected,
            "cross-JVM child exit " + child.process.exitValue() +
            " expected=" + expected + " output=" + out);
        return out;
    }

    static void awaitMarker(Path marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!Files.exists(marker)) {
            if (System.nanoTime() > deadline)
                throw new AssertionError("child never reached marker " + marker);
            Thread.sleep(20);
        }
    }

    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2046-two-jvm-lock-");
        Path dir = Files.createDirectory(scratch.resolve("journal"));
        Path marker = scratch.resolve("child-lock-held.signal");
        UUID id = UUID.fromString("20462046-aaaa-4aaa-8aaa-204620462046");
        Child holding = null;
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
            }
            holding = start("hold", dir, marker, id);
            awaitMarker(marker);
            Child refused = start("refuse-locked", dir, marker, id);
            String locked = finish(refused, 0);
            String mainRefusal = null;
            try (LocalLabExperimentalDiskAttemptJournal impossible =
                    LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                throw new AssertionError("main JVM acquired held cross-JVM lock");
            } catch (IOException expected) { mainRefusal = expected.getMessage(); }
            G2025DiskAttemptJournalRestartWitnessTest.check(
                locked.contains("CHILD_CROSS_JVM_LOCK_REFUSAL_PASS") &&
                "DISK_ATTEMPT_DIRECTORY_LOCKED".equals(mainRefusal),
                "two independently launched processes are denied same held lock");
            Files.write(scratch.resolve("release.signal"), new byte[] {1});
            G2025DiskAttemptJournalRestartWitnessTest.check(
                finish(holding, 0).contains("CHILD_HELD_LOCK_RELEASED_PASS"),
                "cross-JVM owner released cooperative lock");
            holding = null;
            Child later = start("read", dir, marker, id);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                finish(later, 0).contains("CHILD_HISTORY_ONLY_PASS") &&
                f.world.pvmRecords().receiptCount() == 1L,
                "fresh child may only inspect old event, not replay grants");
        } finally {
            if (holding != null && holding.process.isAlive()) {
                holding.process.destroyForcibly();
                holding.process.waitFor(10, TimeUnit.SECONDS);
            }
            G2025DiskAttemptJournalRestartWitnessTest.erase(scratch);
        }
        System.out.println("G2046_CROSS_JVM_COOPERATIVE_LOCK_PASS" +
            " secondJavaDenied=true lockReleased=true historicalReadOnly=true");
    }
    private G2046DiskJournalCrossJvmLockTest() {}
}
