package spk.local;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R19 actual Main-process early-failure test. Deliberately throws after the
 * World thread has started, then verifies that it was closed before the
 * separate Java process exited, and that both ports and the directory lease
 * can be acquired by the parent.
 *
 * Each test child uses only an invocation-owned temporary directory.
 */
public final class MainStartupRollbackTest {
    private static final int[] PORTS = {43594, 43595};

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("args");
        for (int port : PORTS) requireFree(port);

        Path root = Files.createTempDirectory("spk-r19-startup-rollback-");
        Path parent = root.resolve("accounts");
        Path primary = parent.resolve("opensrc.properties");
        Path secondary = parent.resolve("src.properties");
        Files.createDirectories(parent);
        byte[] originalPrimary = "money=100002\n".getBytes(StandardCharsets.US_ASCII);
        byte[] originalSecondary = "money=7\n".getBytes(StandardCharsets.US_ASCII);
        Files.write(primary, originalPrimary);
        Files.write(secondary, originalSecondary);
        try {
            // Invalid startup-only flags must fail before even acquiring the
            // directory lease, constructing World, or opening any listener.
            expectFailure(root, primary, "bad-flag",
                "unexpected", null, false,
                "R14 invalid test stdin enable value",
                false, false);
            expectFailure(root, primary, "bad-token",
                "true", "invalid-token", false,
                "R14 enabled test stdin shutdown requires 64-hex private token",
                false, false);

            if (Files.exists(parent.resolve(
                    LocalAccountDirectoryLease.LOCK_FILENAME))) {
                throw new AssertionError(
                    "malformed preflight created the account lease lock file");
            }
            assertAccountBytes(primary, originalPrimary, secondary, originalSecondary);

            // Test-only injected exception runs AFTER world.start(), while the
            // R14 shutdown hook and coordinator are already registered. The
            // original exception must reach the child exit as nonzero, but
            // shutdown has to complete synchronously first.
            expectFailure(root, primary, "post-world-start",
                "false", null, true,
                "R19_TEST_ONLY_POST_WORLD_START_FAILURE_INJECTED",
                true, true);
            assertAccountBytes(primary, originalPrimary, secondary, originalSecondary);

            try (LocalAccountDirectoryLease recovered =
                    LocalAccountDirectoryLease.acquire(primary)) {
                if (recovered == null) {
                    throw new AssertionError("directory lease not reacquired");
                }
            }

            for (int port : PORTS) requireFree(port);
            System.out.println(
                "R19_REAL_MAIN_POST_WORLD_FAILURE_ROLLBACK_PASS "
                + "malformedPropertiesRejectedBeforeLease=true "
                + "worldStartedBeforeInjectedFailure=true "
                + "worldPersistenceShutdownClean=true "
                + "bothPortsReleased=true "
                + "accountLeaseReacquired=true "
                + "primaryAndSecondaryUnchanged=true");
        } finally {
            try (Stream<Path> files = Files.walk(root)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException failure) {
                        throw new IllegalStateException(
                            "R19 fixture cleanup failed: " + p, failure);
                    }
                });
            }
        }
    }

    private static void expectFailure(
            Path root, Path account, String label,
            String flag, String token, boolean inject,
            String expectedMessage, boolean hasLease,
            boolean cleanWorldShutdown) throws Exception {
        Path log = root.resolve(label + ".log");
        Process child = launch(root, account, log, flag, token, inject);
        try {
            if (!child.waitFor(18, TimeUnit.SECONDS)) {
                throw new AssertionError(label + " test child timed out");
            }
            String text = new String(Files.readAllBytes(log),
                StandardCharsets.UTF_8);
            if (child.exitValue() == 0
                    || !text.contains(expectedMessage)) {
                throw new AssertionError(
                    label + " did not exit with expected refusal: " +
                    text.substring(0, Math.min(600, text.length())));
            }
            boolean gotLease = text.contains(
                "R15_ACCOUNT_DIRECTORY_LEASE_ACQUIRED");
            if (gotLease != hasLease) {
                throw new AssertionError(
                    label + " incorrect lease acquisition: " + gotLease);
            }
            boolean worldClosed = text.contains(
                "V5123_PERSISTENCE_SHUTDOWN_CLEAN");
            if (worldClosed != cleanWorldShutdown) {
                throw new AssertionError(
                    label + " leaked World or misreported shutdown");
            }
            if (text.contains("SpawnPK Local Lab")
                    || text.contains("GAME  :")
                    || text.contains("PULSE :")) {
                throw new AssertionError(
                    label + " announced gameplay readiness during failure");
            }
            for (int port : PORTS) requireFree(port);
        } finally {
            if (child.isAlive()) {
                // Only the exact test-owned child may be stopped.
                child.destroyForcibly();
                if (!child.waitFor(5, TimeUnit.SECONDS)) {
                    throw new AssertionError(label + " child did not terminate");
                }
            }
        }
    }

    private static Process launch(
            Path root, Path account, Path log,
            String flag, String token, boolean inject)
            throws IOException {
        String executable = Paths.get(System.getProperty("java.home"), "bin",
            System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe" : "java").toString();
        java.util.ArrayList<String> cmd = new java.util.ArrayList<>();
        cmd.add(executable);
        cmd.add("-Duser.home=" + root.toAbsolutePath());
        cmd.add("-Djava.net.preferIPv4Stack=true");
        cmd.add("-Dspk.local.accountFile=" + account.toAbsolutePath());
        cmd.add("-Dspk.local.acceptance.stdinShutdownEnabled=" + flag);
        if (token != null) {
            cmd.add("-Dspk.local.acceptance.stdinShutdownToken=" + token);
        }
        cmd.add("-Dspk.local.acceptance.injectFailureAfterWorldStart=" + inject);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(Main.class.getName());
        cmd.add("--bootstrap");
        cmd.add("--movement");
        return new ProcessBuilder(cmd).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
    }

    private static void assertAccountBytes(
            Path primary, byte[] expectedPrimary,
            Path secondary, byte[] expectedSecondary) throws IOException {
        if (!Arrays.equals(Files.readAllBytes(primary), expectedPrimary)
                || !Arrays.equals(Files.readAllBytes(secondary), expectedSecondary)) {
            throw new AssertionError("existing account fixture was modified");
        }
    }

    private static void requireFree(int port) throws IOException {
        try (ServerSocket testSocket = new ServerSocket()) {
            testSocket.setReuseAddress(false);
            testSocket.bind(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), port));
        }
    }

    private MainStartupRollbackTest() {}
}
