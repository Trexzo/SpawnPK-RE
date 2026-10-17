package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/** Child JVM cannot turn parent event file into a second credit authorization. */
public final class G2047DiskJournalCrossJvmHistoricalTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2047-two-jvm-history-");
        Path dir = Files.createDirectory(scratch.resolve("journal"));
        Path marker = scratch.resolve("unused.signal");
        UUID id = UUID.fromString("20472047-bbbb-4bbb-8bbb-204720472047");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
            }
            byte[] original = Files.readAllBytes(dir.resolve(id + ".event"));
            G2046DiskJournalCrossJvmLockTest.Child read =
                G2046DiskJournalCrossJvmLockTest.start("read", dir, marker, id);
            String inspected = G2046DiskJournalCrossJvmLockTest.finish(read, 0);
            G2046DiskJournalCrossJvmLockTest.Child attempt =
                G2046DiskJournalCrossJvmLockTest.start(
                    "try-old-id", dir, marker, id);
            String blocked = G2046DiskJournalCrossJvmLockTest.finish(attempt, 0);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                inspected.contains("CHILD_HISTORY_ONLY_PASS") &&
                blocked.contains("CHILD_CROSS_JVM_REPLAY_REFUSED_PASS") &&
                Arrays.equals(original, Files.readAllBytes(dir.resolve(id + ".event"))) &&
                f.world.pvmRecords().receiptCount() == 1L,
                "new JVM may inspect but cannot extend old event UUID");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2047_CROSS_JVM_HISTORICAL_REFUSAL_PASS" +
            " freshProcessReads=true forgedStageRefused=true noReplay=true");
    }
    private G2047DiskJournalCrossJvmHistoricalTest() {}
}
