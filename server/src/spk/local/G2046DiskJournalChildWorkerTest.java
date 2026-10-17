package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Spawned by G2046–51 with a SEPARATE JVM, not an in-process thread.
 * Synthetic fixtures and process halts are tests only; never a grant path.
 */
public final class G2046DiskJournalChildWorkerTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 4)
            throw new IllegalArgumentException("mode journal marker id");
        String mode = args[0];
        Path root = Paths.get(args[1]);
        Path marker = Paths.get(args[2]);
        UUID id = UUID.fromString(args[3]);
        if ("hold".equals(mode)) {
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(root)) {
                if (journal.auditedSnapshot().reservations != 1)
                    throw new AssertionError("hold expected one historical stage");
                Files.write(marker, "HOLDING".getBytes(StandardCharsets.UTF_8));
                long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
                while (!Files.exists(root.getParent().resolve("release.signal"))) {
                    if (System.nanoTime() > end) throw new AssertionError("release timed out");
                    Thread.sleep(20);
                }
            }
            System.out.println("CHILD_HELD_LOCK_RELEASED_PASS");
        } else if ("refuse-locked".equals(mode)) {
            String refusal = null;
            try (LocalLabExperimentalDiskAttemptJournal journal =
                    LocalLabExperimentalDiskAttemptJournal.open(root)) {
                throw new AssertionError("another JVM acquired held lock");
            } catch (IOException expected) {
                refusal = expected.getMessage();
            }
            if (!"DISK_ATTEMPT_DIRECTORY_LOCKED".equals(refusal))
                throw new AssertionError("unexpected lock refusal " + refusal);
            System.out.println("CHILD_CROSS_JVM_LOCK_REFUSAL_PASS");
        } else if ("read".equals(mode)) {
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(root)) {
                LocalLabExperimentalDiskAttemptJournal.Snapshot s =
                    journal.auditedSnapshot();
                if (s.reservations != 1 || !s.entries.containsKey(id) ||
                    s.hasAtomicConsumerGrantAcknowledgement() ||
                    s.hasOriginalServerEventAuthority() ||
                    s.safeToReplayAfterRestart())
                    throw new AssertionError("unsafe cross-JVM historical evidence");
                System.out.println("CHILD_HISTORY_ONLY_PASS");
            }
        } else if ("try-old-id".equals(mode)) {
            try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                     new G2017TerminalAttemptFenceRealDeathTest.Fixture();
                 LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(root)) {
                MonsterSpawnerPvmRuntime.FinalizeResult ownDeath = f.settle();
                String refusal = null;
                try {
                    journal.reserve(f.world, ownDeath, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G4);
                } catch (IllegalStateException expected) {
                    refusal = expected.getMessage();
                }
                if (!("DISK_ATTEMPT_CONFLICTING_DEATH_EVIDENCE".equals(refusal) ||
                      "DISK_ATTEMPT_REOPENED_EVENT_APPEND_FORBIDDEN".equals(refusal)) ||
                    journal.auditedSnapshot().reservations != 1 ||
                    f.world.pvmRecords().receiptCount() != 1L)
                    throw new AssertionError("old ID was reused: " + refusal);
                System.out.println("CHILD_CROSS_JVM_REPLAY_REFUSED_PASS");
            }
        } else if ("swap-lock-and-read".equals(mode)) {
            Path replacement = root.getParent().resolve("foreign-lock-inode");
            Files.write(replacement, new byte[0], StandardOpenOption.CREATE_NEW);
            Files.move(replacement, root.resolve(".journal.lock"),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            // Deliberate demonstration of the remaining split-owner boundary:
            // the new inode has no held lock, so a second JVM can open it.
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(root)) {
                if (journal.auditedSnapshot().reservations != 1)
                    throw new AssertionError("foreign lock inode changed old records");
                System.out.println("CHILD_SPLIT_OWNER_LIMIT_DEMONSTRATED_PASS");
            }
        } else if ("corrupt-uncooperative".equals(mode)) {
            Path record = root.resolve(id.toString() + ".event");
            byte[] raw = Files.readAllBytes(record);
            raw[raw.length - 2] =
                (byte)(raw[raw.length - 2] == 'a' ? 'b' : 'a');
            Files.write(record, raw, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("CHILD_UNCOOPERATIVE_TAMPER_APPLIED_PASS");
        } else if ("crash-before".equals(mode) || "crash-after".equals(mode)) {
            try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                    new G2017TerminalAttemptFenceRealDeathTest.Fixture();
                 LocalLabExperimentalDiskAttemptJournal journal =
                    LocalLabExperimentalDiskAttemptJournal.open(root)) {
                MonsterSpawnerPvmRuntime.FinalizeResult terminal = f.settle();
                LocalLabExperimentalDiskAttemptJournal.PublicationTestCutpoint before =
                    "crash-before".equals(mode) ? (pending, destination) -> {
                        Files.write(marker, "BEFORE".getBytes(StandardCharsets.UTF_8));
                        Runtime.getRuntime().halt(33);
                    } : null;
                LocalLabExperimentalDiskAttemptJournal.PublicationTestCutpoint after =
                    "crash-after".equals(mode) ? (pending, destination) -> {
                        Files.write(marker, "AFTER".getBytes(StandardCharsets.UTF_8));
                        Runtime.getRuntime().halt(34);
                    } : null;
                journal.reserveWithTestCutpoints(f.world, terminal, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1,
                    before, after);
                throw new AssertionError("test process failed to halt");
            }
        } else {
            throw new IllegalArgumentException("unknown mode " + mode);
        }
    }
    private G2046DiskJournalChildWorkerTest() {}
}
