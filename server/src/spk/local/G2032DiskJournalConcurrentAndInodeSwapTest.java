package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real settled death, 12 racing reservations and same-byte inode substitution. */
public final class G2032DiskJournalConcurrentAndInodeSwapTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("g2032-concurrent-disk-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            final MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            final UUID id = UUID.fromString("20322032-dddd-4ddd-8ddd-203220322032");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                final int workers = 12;
                CountDownLatch ready = new CountDownLatch(workers);
                CountDownLatch start = new CountDownLatch(1);
                AtomicInteger winners = new AtomicInteger(), duplicate = new AtomicInteger();
                AtomicReference<Throwable> unexpected = new AtomicReference<Throwable>();
                List<Thread> threads = new ArrayList<Thread>();
                for (int n = 0; n < workers; n++) {
                    Thread t = new Thread(() -> {
                        ready.countDown();
                        try {
                            start.await();
                            try {
                                journal.reserve(f.world, death, id,
                                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                                winners.incrementAndGet();
                            } catch (IllegalStateException expected) {
                                if ("DISK_ATTEMPT_DUPLICATE_CONSUMER".equals(
                                        expected.getMessage())) duplicate.incrementAndGet();
                                else unexpected.compareAndSet(null, expected);
                            }
                        } catch (Throwable error) {
                            unexpected.compareAndSet(null, error);
                        }
                    }, "g2032-journal-claim-" + n);
                    threads.add(t);
                    t.start();
                }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    ready.await(10L, TimeUnit.SECONDS),
                    "all real NPC reservation contenders reached start");
                start.countDown();
                for (Thread t : threads) {
                    t.join(10000L);
                    G2025DiskAttemptJournalRestartWitnessTest.check(
                        !t.isAlive(), "all reservation contenders terminated");
                }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    unexpected.get() == null && winners.get() == 1 &&
                    duplicate.get() == 11 &&
                    journal.snapshot().reservations == 1 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "single journal stage commit survives twelve-thread contention");

                Path event = directory.resolve(id + ".event");
                byte[] before = Files.readAllBytes(event);
                Path newInode = directory.resolve("operator-swap.tmp");
                Files.write(newInode, before);
                Files.move(newInode, event, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
                String refusal = null;
                try {
                    journal.reserve(f.world, death, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G4);
                } catch (IOException expected) { refusal = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_DISK_STATE_DIVERGED".equals(refusal) &&
                    java.util.Arrays.equals(before, Files.readAllBytes(event)) &&
                    journal.snapshot().reservations == 1 &&
                    !journal.snapshot().safeToReplayAfterRestart(),
                    "same-byte new inode must invalidate active cached file identity");
            }
            try (LocalLabExperimentalDiskAttemptJournal reopened =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    reopened.snapshot().reservations == 1 &&
                    !reopened.snapshot().hasAtomicConsumerGrantAcknowledgement(),
                    "reopen parses checksum but cannot assert grant or original inode");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(directory); }
        System.out.println("G2032_DISK_JOURNAL_CONCURRENT_INODE_PASS"+
            " oneOfTwelveClaimsCommitted=true elevenDuplicates=true"+
            " sameByteInodeSwapDeniedWhileOpen=true noGrantAck=true");
    }
    private G2032DiskJournalConcurrentAndInodeSwapTest() {}
}
