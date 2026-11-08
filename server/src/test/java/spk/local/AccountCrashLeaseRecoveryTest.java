package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R17 real-process recovery proof. A separately launched child acquires the
 * R15 OS lease, force-stages an R16 account save, and is killed before rename.
 * The original account must survive; a second child JVM must take ownership
 * and successfully publish a new snapshot without editing the orphan stage.
 *
 * All paths live under this test's own freshly created temporary directory.
 */
public final class AccountCrashLeaseRecoveryTest {
    private static final long CHILD_TIMEOUT_SECONDS = 18L;

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && "--crash-owner".equals(args[0])) {
            crashOwner(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]));
            return;
        }
        if (args.length == 2 && "--restart-owner".equals(args[0])) {
            restartOwner(Paths.get(args[1]));
            return;
        }
        if (args.length != 0) throw new IllegalArgumentException("arguments");

        Path root = Files.createTempDirectory("spk-r17-crash-restart-");
        Path accounts = root.resolve("accounts");
        Path primary = accounts.resolve("opensrc.properties");
        Path secondary = accounts.resolve("src.properties");
        Path ready = root.resolve("forced-stage-ready");
        Process crashed = null;
        try {
            Files.createDirectories(accounts);
            byte[] before = "balance=100002\n".getBytes(StandardCharsets.US_ASCII);
            Files.write(primary, before);
            byte[] secondaryBefore = "balance=7\n".getBytes(StandardCharsets.US_ASCII);
            Files.write(secondary, secondaryBefore);

            // A different JVM holds the directory lease and has already
            // force()'d the staged bytes, but has not made them visible.
            crashed = child("--crash-owner", primary.toString(),
                ready.toString(), secondary.toString());
            waitForReadyOrFailure(ready, crashed);
            if (!crashed.isAlive()) {
                throw new AssertionError("crash-owner exited before test termination");
            }
            requireLeaseDenied(primary, "primary");
            requireLeaseDenied(secondary, "secondary");
            assertBytes(primary, before, "pre-rename primary");
            assertBytes(secondary, secondaryBefore, "pre-rename secondary");

            // Only kill the test-owned exact child; neither shared servers
            // nor the user's game clients are inspected or affected.
            crashed.destroyForcibly();
            if (!crashed.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("crash-owner did not terminate");
            }
            crashed = null;

            assertBytes(primary, before, "account after abrupt owner death");
            assertBytes(secondary, secondaryBefore,
                "secondary account after abrupt owner death");
            if (stageCount(accounts) < 1) {
                throw new AssertionError("forced staged file disappeared unexpectedly");
            }

            // R15 OS lock must have been released by the dead JVM.
            try (LocalAccountDirectoryLease lease =
                    LocalAccountDirectoryLease.acquire(primary)) {
                if (lease == null) throw new AssertionError("lease not recovered");
            }

            // An independent NEW JVM must acquire the same lease, atomically
            // publish state for both profiles, then cleanly release the lease.
            Process successor = child("--restart-owner", primary.toString());
            try {
                if (!successor.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    successor.destroyForcibly();
                    throw new AssertionError("restarted owner timed out");
                }
                if (successor.exitValue() != 0) {
                    throw new AssertionError(
                        "restarted owner exited " + successor.exitValue());
                }
            } finally {
                if (successor.isAlive()) {
                    successor.destroyForcibly();
                    successor.waitFor(5, TimeUnit.SECONDS);
                }
            }

            if (!"100003".equals(read(primary, "balance"))
                    || !"8".equals(read(secondary, "balance"))) {
                throw new AssertionError("restarted independent JVM persistence");
            }
            if (stageCount(accounts) < 1) {
                throw new AssertionError("orphan stage was unexpectedly reused");
            }
            try (LocalAccountDirectoryLease lease =
                    LocalAccountDirectoryLease.acquire(secondary)) {
                if (lease == null) throw new AssertionError("second-release lease");
            }
            System.out.println(
                "R17_CRASHED_OWNER_ATOMIC_ACCOUNT_RESTART_PASS "
                + "crossJvmOwnerDenied=true forcedStagingBeforeCrash=true "
                + "primaryAndSecondaryOldBytesSurvived=true "
                + "osLeaseRecoveredAfterKill=true freshJvmReconnected=true "
                + "twoProfileAtomicSaves=true orphanStageNotReused=true");
        } finally {
            if (crashed != null && crashed.isAlive()) {
                crashed.destroyForcibly();
                crashed.waitFor(5, TimeUnit.SECONDS);
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException error) {
                        throw new IllegalStateException("test cleanup " + path, error);
                    }
                });
            }
        }
    }

    private static void crashOwner(
            Path primary, Path ready, Path secondary) throws Exception {
        try (LocalAccountDirectoryLease ignored =
                 LocalAccountDirectoryLease.acquire(primary)) {
            // Also proves the directory lease spans the other profile:
            // parent JVM's secondary competing lock must be refused.
            if (!Files.exists(secondary)) {
                throw new AssertionError("secondary fixture missing");
            }
            AtomicAccountPublisher.publish(primary, value("DO_NOT_COMMIT"),
                "R17 forced stage", (staged, target) -> {
                    Files.write(ready, new byte[]{1});
                    // Wait for the harness to terminate THIS dedicated child.
                    // No successful move may ever happen in this branch.
                    for (;;) {
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException ignoredInterrupt) {
                            Thread.currentThread().interrupt();
                            throw new IOException("unexpected interruption");
                        }
                    }
                });
        }
    }

    private static void restartOwner(Path primary) throws Exception {
        try (LocalAccountDirectoryLease ignored =
                 LocalAccountDirectoryLease.acquire(primary)) {
            AtomicAccountPublisher.publish(primary,
                value("100003"), "R17 restarted owner primary");
            AtomicAccountPublisher.publish(
                primary.resolveSibling("src.properties"),
                value("8"), "R17 restarted owner secondary");
        }
    }

    private static void waitForReadyOrFailure(Path ready, Process child)
            throws Exception {
        long deadline = System.nanoTime()
            + TimeUnit.SECONDS.toNanos(CHILD_TIMEOUT_SECONDS);
        while (!Files.exists(ready) && child.isAlive()
                && System.nanoTime() < deadline) {
            Thread.sleep(40L);
        }
        if (!Files.exists(ready)) {
            throw new AssertionError(
                "crash-owner did not reach forced-staging point");
        }
    }

    private static Process child(String mode, String... args)
            throws IOException {
        String javaExecutable = Paths.get(System.getProperty("java.home"),
            "bin", System.getProperty("os.name", "")
                .toLowerCase().contains("win") ? "java.exe" : "java").toString();
        String[] command = new String[5 + args.length];
        command[0] = javaExecutable;
        command[1] = "-cp";
        command[2] = System.getProperty("java.class.path");
        command[3] = AccountCrashLeaseRecoveryTest.class.getName();
        command[4] = mode;
        System.arraycopy(args, 0, command, 5, args.length);
        return new ProcessBuilder(command)
            .redirectErrorStream(true).start();
    }

    private static void requireLeaseDenied(Path account, String profile)
            throws Exception {
        try (LocalAccountDirectoryLease unexpected =
                LocalAccountDirectoryLease.acquire(account)) {
            throw new AssertionError("concurrent " + profile + " owner admitted");
        } catch (IOException expected) {
            if (!expected.getMessage().contains(
                    "R15_ACCOUNT_DIRECTORY_LEASE_BLOCKED")) {
                throw new AssertionError("not an expected owner denial", expected);
            }
        }
    }

    private static int stageCount(Path root) throws IOException {
        try (Stream<Path> files = Files.list(root)) {
            return (int) files.filter(path ->
                path.getFileName().toString()
                    .startsWith(".opensrc.properties.r16-")
                    && path.getFileName().toString().endsWith(".stage")).count();
        }
    }

    private static Properties value(String data) {
        Properties props = new Properties();
        props.setProperty("balance", data);
        return props;
    }

    private static String read(Path target, String key) throws IOException {
        Properties props = new Properties();
        try (InputStream stream = Files.newInputStream(target)) {
            props.load(stream);
        }
        return props.getProperty(key);
    }

    private static void assertBytes(Path path, byte[] expected, String why)
            throws IOException {
        if (!Arrays.equals(Files.readAllBytes(path), expected)) {
            throw new AssertionError(why);
        }
    }

    private AccountCrashLeaseRecoveryTest() {}
}
