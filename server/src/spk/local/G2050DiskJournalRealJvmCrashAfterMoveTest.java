package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Actual child JVM halt immediately AFTER rename but BEFORE local evidence ACK. */
public final class G2050DiskJournalRealJvmCrashAfterMoveTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2050-crash-after-");
        Path dir = Files.createDirectory(scratch.resolve("journal"));
        Path marker = scratch.resolve("reached-after-move");
        UUID id = UUID.fromString("20502050-eeee-4eee-8eee-205020502050");
        try {
            G2046DiskJournalCrossJvmLockTest.Child child =
                G2046DiskJournalCrossJvmLockTest.start(
                    "crash-after", dir, marker, id);
            G2046DiskJournalCrossJvmLockTest.finish(child, 34);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                Files.exists(marker) && Files.exists(dir.resolve(id + ".event")),
                "child halted after real event ATOMIC_MOVE");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                LocalLabExperimentalDiskAttemptJournal.Snapshot s =
                    journal.auditedSnapshot();
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    s.reservations == 1 && s.entries.size() == 1 &&
                    s.entries.get(id).reserved.contains(
                        LocalLabTerminalDeliveryAssessment.Consumer.G1) &&
                    !s.hasAtomicConsumerGrantAcknowledgement() &&
                    !s.safeToReplayAfterRestart() &&
                    !s.hasOriginalServerEventAuthority(),
                    "valid disk reservation after crash cannot be interpreted as a grant ACK");
            }
            G2046DiskJournalCrossJvmLockTest.Child oldEventAttempt =
                G2046DiskJournalCrossJvmLockTest.start(
                    "try-old-id", dir, marker, id);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                G2046DiskJournalCrossJvmLockTest.finish(
                    oldEventAttempt, 0).contains("CHILD_CROSS_JVM_REPLAY_REFUSED_PASS"),
                "third independent process cannot replay/extend historical stage");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2050_REAL_JVM_CRASH_AFTER_MOVE_PASS" +
            " publishedReservationRecoveredReadOnly=true" +
            " noCreditAck=true noSafeReplay=true");
    }
    private G2050DiskJournalRealJvmCrashAfterMoveTest() {}
}
