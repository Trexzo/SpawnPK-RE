package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * A NEGATIVE PROOF: a hostile peer can replace the lock path, and a second
 * process can open its new inode. This is NOT globally safe ownership.
 */
public final class G2048DiskJournalCrossJvmLockSwapNegativeProofTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2048-split-owner-");
        Path dir = Files.createDirectory(scratch.resolve("journal"));
        UUID id = UUID.fromString("20482048-cccc-4ccc-8ccc-204820482048");
        Path marker = scratch.resolve("unused.signal");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            try (LocalLabExperimentalDiskAttemptJournal incumbent =
                     LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                incumbent.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                G2046DiskJournalCrossJvmLockTest.Child uncooperative =
                    G2046DiskJournalCrossJvmLockTest.start(
                        "swap-lock-and-read", dir, marker, id);
                String output = G2046DiskJournalCrossJvmLockTest.finish(
                    uncooperative, 0);
                String oldOwnerRefusal = null;
                try { incumbent.auditedSnapshot(); }
                catch (IOException expected) {
                    oldOwnerRefusal = expected.getMessage();
                }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    output.contains("CHILD_SPLIT_OWNER_LIMIT_DEMONSTRATED_PASS") &&
                    "DISK_ATTEMPT_LOCK_IDENTITY_CHANGED".equals(oldOwnerRefusal) &&
                    incumbent.snapshot().reservations == 1 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "replacement lock permits unsafe new owner, incumbent detects path drift");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2048_CROSS_JVM_SPLIT_LOCK_LIMIT_PASS" +
            " secondOwnerPossible=true originalOwnerDetectsSwap=true" +
            " globalOwnerEpochAbsent=true noGrant=true");
    }
    private G2048DiskJournalCrossJvmLockSwapNegativeProofTest() {}
}
