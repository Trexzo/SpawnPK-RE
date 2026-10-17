package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Many test UUIDs for ONE canonical terminal result must not mint many events. */
public final class G2040DiskJournalTerminalUuidAliasRaceTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("g2040-alias-race-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            final MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            final AtomicInteger allowed = new AtomicInteger();
            final AtomicInteger refused = new AtomicInteger();
            final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
            final AtomicReference<UUID> winner = new AtomicReference<UUID>();
            final CountDownLatch ready = new CountDownLatch(12);
            final CountDownLatch start = new CountDownLatch(1);
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(root)) {
                List<Thread> actors = new ArrayList<Thread>();
                for (int i = 0; i < 12; i++) {
                    final UUID id = new UUID(0x2040204020404040L, i + 1L);
                    Thread t = new Thread(() -> {
                        ready.countDown();
                        try {
                            start.await();
                            try {
                                journal.reserve(f.world, death, id,
                                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                                winner.set(id);
                                allowed.incrementAndGet();
                            } catch (IllegalStateException expected) {
                                if ("DISK_ATTEMPT_DUPLICATE_TERMINAL_ALIAS".equals(
                                        expected.getMessage())) refused.incrementAndGet();
                                else error.compareAndSet(null, expected);
                            }
                        } catch (Throwable unexpected) {
                            error.compareAndSet(null, unexpected);
                        }
                    }, "g2040-uuid-alias-" + i);
                    actors.add(t);
                    t.start();
                }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    ready.await(10, TimeUnit.SECONDS), "all callers ready");
                start.countDown();
                for (Thread actor : actors) {
                    actor.join(10000);
                    G2025DiskAttemptJournalRestartWitnessTest.check(
                        !actor.isAlive(), "all callers terminate");
                }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    error.get() == null && allowed.get() == 1 &&
                    refused.get() == 11 && journal.auditedSnapshot().entries.size() == 1 &&
                    journal.auditedSnapshot().reservations == 1 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "one real finalization cannot become 12 journal events");
                journal.reserve(f.world, death, winner.get(),
                    LocalLabTerminalDeliveryAssessment.Consumer.G4);
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    journal.auditedSnapshot().reservations == 2 &&
                    journal.auditedSnapshot().entries.size() == 1 &&
                    !journal.snapshot().hasAtomicConsumerGrantAcknowledgement(),
                    "same exact terminal and UUID may use a distinct stage");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(root); }
        System.out.println("G2040_DISK_JOURNAL_UUID_ALIAS_RACE_PASS"+
            " oneOf12UuidAccepted=true elevenAliasesDenied=true"+
            " exactSameTerminalSecondStageAllowed=true noGrant=true");
    }
    private G2040DiskJournalTerminalUuidAliasRaceTest() {}
}
