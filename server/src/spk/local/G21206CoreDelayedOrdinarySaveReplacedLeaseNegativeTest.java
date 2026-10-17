package spk.local;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** Test-only illegally replaced JVM Token during a queued ordinary SaveTask. */
public final class G21206CoreDelayedOrdinarySaveReplacedLeaseNegativeTest {
    private static final String USER = "g21206-ordinary-owner";
    private static final String NAMESPACE = "core_g21206";
    private G21206CoreDelayedOrdinarySaveReplacedLeaseNegativeTest() {}

    private static void check(boolean ok, String stage) {
        if(!ok) throw new AssertionError("G21.206 " + stage);
    }

    private static void run() throws Exception {
        Path root = Files.createTempDirectory("g21206-save-");
        String key = "spk.local.accountFile";
        String leaseKey = LocalJvmAccountSessionLease.PROPERTY;
        String lockKey = LocalCrossJvmAccountSessionLock.PROPERTY;
        String old = System.getProperty(key);
        String oldLease = System.getProperty(leaseKey);
        String oldLock = System.getProperty(lockKey);
        System.setProperty(key, root.resolve("opensrc.properties").toString());
        System.setProperty(leaseKey, "true");
        System.setProperty(lockKey, "false");

        World world = null;
        WorldPlayer player = null;
        LocalJvmAccountSessionLease.Token original = null;
        LocalJvmAccountSessionLease.Token successor = null;
        long generation = -1L;
        CountDownLatch resumeWorker = new CountDownLatch(1);
        try {
            Path file = LocalAccountProfiles.accountFile(USER);
            Files.createDirectories(file.getParent());
            check(!Files.exists(file, LinkOption.NOFOLLOW_LINKS),
                "fixture account should be missing");
            FilePlayerRepository repository = new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world = World.isolatedForTest(60_000L, repository);
            player = new WorldPlayer();
            original = LocalJvmAccountSessionLease.acquire(
                world.persistence(), USER);
            check(original != null, "JVM lease missing");
            generation = world.registerPlayer(player, USER);
            original.bindAdmittedWorldOwner(player, generation);
            world.start();

            Field field = WorldPlayerPersistence.class.getDeclaredField("io");
            field.setAccessible(true);
            ThreadPoolExecutor io =
                (ThreadPoolExecutor)field.get(world.persistence());
            CountDownLatch entered = new CountDownLatch(1);
            io.execute(() -> {
                entered.countDown();
                try {
                    if(!resumeWorker.await(12L, TimeUnit.SECONDS))
                        throw new IllegalStateException(
                            "G21.206 FIFO test blocker timed out");
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            });
            check(entered.await(5L, TimeUnit.SECONDS),
                "persistence FIFO worker not blocked");

            World ownedWorld = world;
            WorldPlayer owner = player;
            long ownedGeneration = generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket =
                new AtomicReference<>();
            long failedBefore = world.persistence().failedCount();
            int checksBefore = LocalJvmAccountSessionLease
                .captureBoundWorkerChecksForTest();
            world.submitAndWait(owner, ownedGeneration, () -> {
                owner.snapshotExtensions().replaceNamespace(
                    NAMESPACE, Collections.singletonMap(
                        "phase", "ORIGINAL_CAPTURE_UNPUBLISHED"));
                ticket.set(ownedWorld.persistence().captureAndSave(
                    USER, owner, ownedGeneration, 0,
                    "[G21.206] ", "ORDINARY_SAVE"));
            }, 5_000L);
            check(ticket.get() != null && !ticket.get().completion.isDone(),
                "ordinary SaveTicket did not stay pending");
            check(io.getQueue().size() > 0,
                "ordinary SaveTask not queued behind FIFO blocker");

            // Intentionally forbidden lifecycle: still-live owner rebind.
            original.releaseAfterInitializationFailure();
            successor = LocalJvmAccountSessionLease.acquire(
                world.persistence(), USER);
            check(successor != null, "replacement lease missing");
            successor.bindAdmittedWorldOwner(owner, ownedGeneration);
            check(LocalJvmAccountSessionLease
                    .requireCapturedSaveOwnerIfLeased(
                        file, USER, owner, ownedGeneration),
                "path-only guard did not accept replacement");

            resumeWorker.countDown();
            boolean rejected = false;
            try {
                ticket.get().completion.get(10L, TimeUnit.SECONDS);
            } catch(ExecutionException e) {
                Throwable cause = e.getCause();
                rejected = cause instanceof IllegalStateException
                    && cause.getMessage() != null
                    && cause.getMessage().contains(
                        "SAVE_CAPTURE_ORIGINAL_LEASE_LOST_OR_REPLACED");
            }
            check(rejected, "actual SaveTask did not reject original lease");
            check(world.persistence().failedCount() == failedBefore + 1L,
                "ordinary SaveTask failure count differed");
            check(LocalJvmAccountSessionLease
                    .captureBoundWorkerChecksForTest() == checksBefore,
                "rejected token counted as successful capture guard");
            check(!Files.exists(file, LinkOption.NOFOLLOW_LINKS),
                "invalid ordinary snapshot published an account file");
            check(LocalJvmAccountSessionLease.activeCountForTest() == 1,
                "replacement token unexpectedly released");

            System.out.println(
                "G21206_DELAYED_ORDINARY_SAVE_REPLACED_LEASE_NEGATIVE_PASS" +
                " actualWorldCapture=true queuedSaveTask=true" +
                " originalTokenSwapWhileBlocked=true" +
                " sameAccountPlayerGeneration=true" +
                " originalWitnessRejectedByRealWorker=true" +
                " accountFileCreated=false positiveRecovery=false" +
                " crossJvmDurableEpoch=false rawWriterExclusion=false");
        } finally {
            resumeWorker.countDown();
            try {
                if(world != null && player != null && generation > 0)
                    world.unregisterPlayer(player, generation);
                if(successor != null)
                    successor.releaseAfterInitializationFailure();
                if(original != null)
                    original.releaseAfterInitializationFailure();
                if(world != null) world.close();
            } finally {
                if(old == null) System.clearProperty(key);
                else System.setProperty(key, old);
                if(oldLease == null) System.clearProperty(leaseKey);
                else System.setProperty(leaseKey, oldLease);
                if(oldLock == null) System.clearProperty(lockKey);
                else System.setProperty(lockKey, oldLock);
                try(Stream<Path> paths = Files.walk(root)) {
                    for(Path p: paths.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(p);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 0, "unexpected arguments");
        run();
    }
}
