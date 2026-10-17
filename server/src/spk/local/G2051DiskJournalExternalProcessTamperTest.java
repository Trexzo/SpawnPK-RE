package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** A noncooperating JVM can modify held journal bytes, which audit rejects. */
public final class G2051DiskJournalExternalProcessTamperTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2051-two-jvm-tamper-");
        Path dir = Files.createDirectory(scratch.resolve("journal"));
        Path marker = scratch.resolve("unused.signal");
        UUID id = UUID.fromString("20512051-ffff-4fff-8fff-205120512051");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            try (LocalLabExperimentalDiskAttemptJournal journal =
                    LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                G2046DiskJournalCrossJvmLockTest.Child attacker =
                    G2046DiskJournalCrossJvmLockTest.start(
                        "corrupt-uncooperative", dir, marker, id);
                String output = G2046DiskJournalCrossJvmLockTest.finish(attacker, 0);
                String refused = null;
                try { journal.auditedSnapshot(); }
                catch (IOException expected) { refused = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    output.contains("CHILD_UNCOOPERATIVE_TAMPER_APPLIED_PASS") &&
                    "DISK_ATTEMPT_RECORD_INTEGRITY_FAILURE".equals(refused) &&
                    Files.exists(dir.resolve(id + ".event")) &&
                    journal.snapshot().reservations == 1 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "held lock excludes cooperative writer but cannot stop outside tamper");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2051_EXTERNAL_JVM_NONCOOPERATIVE_TAMPER_PASS" +
            " outsideWriteSucceeded=true auditedCorruptionDenied=true" +
            " noCrossProcessEpoch=true noGrant=true");
    }
    private G2051DiskJournalExternalProcessTamperTest() {}
}
