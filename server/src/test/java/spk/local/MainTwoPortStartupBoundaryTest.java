package spk.local;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R18 live-JVM Main boundary regression. Validates fail-closed directory
 * ownership, all-or-nothing loopback socket reservation, then a real boot and
 * private R14 clean shutdown. No graphical client or user profile is used.
 */
public final class MainTwoPortStartupBoundaryTest {
    private static final int GAME = 43594;
    private static final int AUX = 43595;
    private static final int EXIT_TIMEOUT = 18;
    private static final String TOKEN =
        "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("args");

        // Refuse to test if a user or another test already owns either port.
        requirePortAvailable(GAME);
        requirePortAvailable(AUX);

        Path root = Files.createTempDirectory("spk-r18-two-port-boundary-");
        Path account = root.resolve("accounts").resolve("opensrc.properties");
        Path secondary = account.resolveSibling("src.properties");
        Files.createDirectories(account.getParent());
        byte[] oldPrimary = "balance=100002\n".getBytes(StandardCharsets.US_ASCII);
        byte[] oldSecondary = "balance=7\n".getBytes(StandardCharsets.US_ASCII);
        Files.write(account, oldPrimary);
        Files.write(secondary, oldSecondary);

        try {
            // A first JVM holding the R15 lease rejects real Main startup
            // before its listener sockets or World threads are created.
            try (LocalAccountDirectoryLease held =
                     LocalAccountDirectoryLease.acquire(account)) {
                Path log = root.resolve("denied-lease.log");
                Process denied = launch(account, root, log, false);
                try {
                    waitForExit(denied, "lease-denied Main");
                    if (denied.exitValue() == 0 ||
                            !readLog(log).contains(
                                "R15_ACCOUNT_DIRECTORY_LEASE_BLOCKED")) {
                        throw new AssertionError(
                            "Main did not refuse directory already owned");
                    }
                    assertNoServerStarted(log, "lease-denied");
                    requirePortAvailable(GAME);
                    requirePortAvailable(AUX);
                } finally {
                    stopOwnedChild(denied);
                }
            }

            // If AUX is occupied by our own fixture, Main must relinquish
            // the already-reserved GAME listener and exit without starting
            // World. The held AUX listener must remain owned by this test.
            try (ServerSocket occupiedAux = reserve(AUX)) {
                Path log = root.resolve("denied-aux.log");
                Process denied = launch(account, root, log, false);
                try {
                    waitForExit(denied, "AUX-denied Main");
                    if (denied.exitValue() == 0 ||
                            !readLog(log).contains("BindException")) {
                        throw new AssertionError(
                            "Main did not reject occupied AUX socket");
                    }
                    assertNoServerStarted(log, "AUX-denied");
                    requirePortAvailable(GAME);
                    if (!occupiedAux.isBound() || occupiedAux.isClosed()) {
                        throw new AssertionError(
                            "test fixture AUX listener unexpectedly modified");
                    }
                    try (LocalAccountDirectoryLease released =
                             LocalAccountDirectoryLease.acquire(account)) {
                        if (released == null) throw new AssertionError("lease");
                    }
                } finally {
                    stopOwnedChild(denied);
                }
            }
            requirePortAvailable(AUX);

            // Reverse failure: GAME already occupied must not attempt to
            // open AUX and must release its directory lease on exit.
            try (ServerSocket occupiedGame = reserve(GAME)) {
                Path log = root.resolve("denied-game.log");
                Process denied = launch(account, root, log, false);
                try {
                    waitForExit(denied, "GAME-denied Main");
                    if (denied.exitValue() == 0 ||
                            !readLog(log).contains("BindException")) {
                        throw new AssertionError(
                            "Main did not reject occupied GAME socket");
                    }
                    assertNoServerStarted(log, "GAME-denied");
                    requirePortAvailable(AUX);
                    if (!occupiedGame.isBound() || occupiedGame.isClosed()) {
                        throw new AssertionError(
                            "test fixture GAME listener unexpectedly modified");
                    }
                } finally {
                    stopOwnedChild(denied);
                }
            }
            requirePortAvailable(GAME);

            // Verify that subsequent Main JVM can actually open BOTH
            // endpoints, and cleanly close via a PRIVATE stdin token.
            Path log = root.resolve("admitted.log");
            Process admitted = launch(account, root, log, true);
            try {
                waitForBothListeners(admitted);
                try (LocalAccountDirectoryLease unexpected =
                         LocalAccountDirectoryLease.acquire(account)) {
                    throw new AssertionError("active Main did not retain lease");
                } catch (IOException expected) {
                    if (!expected.getMessage().contains(
                            "R15_ACCOUNT_DIRECTORY_LEASE_BLOCKED")) {
                        throw new AssertionError("wrong lease denial", expected);
                    }
                }
                admitted.getOutputStream().write(
                    (TOKEN + "\n").getBytes(StandardCharsets.US_ASCII));
                admitted.getOutputStream().flush();
                admitted.getOutputStream().close();
                waitForExit(admitted, "admitted Main clean shutdown");
                String output = readLog(log);
                if (admitted.exitValue() != 0 ||
                        !output.contains(
                            "R14_TEST_ONLY_STDIN_SHUTDOWN_AUTHORIZED") ||
                        !output.contains("V5123_PERSISTENCE_SHUTDOWN_CLEAN")) {
                    throw new AssertionError("no authorized clean Main shutdown");
                }
            } finally {
                stopOwnedChild(admitted);
            }

            requirePortAvailable(GAME);
            requirePortAvailable(AUX);
            try (LocalAccountDirectoryLease released =
                     LocalAccountDirectoryLease.acquire(account)) {
                if (released == null) throw new AssertionError("lease not released");
            }
            if (!Arrays.equals(Files.readAllBytes(account), oldPrimary) ||
                    !Arrays.equals(Files.readAllBytes(secondary), oldSecondary)) {
                throw new AssertionError("test Main startup altered account bytes");
            }
            System.out.println(
                "R18_REAL_MAIN_TWO_PORT_STARTUP_BOUNDARY_PASS "
                + "heldLeaseRejectsBeforeWorld=true "
                + "auxConflictReleasesGame=true "
                + "gameConflictLeavesAuxFree=true "
                + "actualMainBothListeners=true "
                + "cleanPrivateShutdown=true "
                + "bothProfilesPreserved=true "
                + "allPortsFreed=true");
        } finally {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException failure) {
                        throw new IllegalStateException("cleanup " + p, failure);
                    }
                });
            }
        }
    }

    private static Process launch(
            Path account, Path root, Path log, boolean enableShutdown)
            throws IOException {
        String javaExecutable = Paths.get(System.getProperty("java.home"),
            "bin", System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe" : "java").toString();
        ProcessBuilder builder = new ProcessBuilder(
            javaExecutable,
            "-Djava.net.preferIPv4Stack=true",
            "-Duser.home=" + root,
            "-Dspk.local.accountFile=" + account,
            "-Dspk.local.acceptance.stdinShutdownEnabled=" + enableShutdown,
            "-Dspk.local.acceptance.stdinShutdownToken=" + TOKEN,
            "-cp", System.getProperty("java.class.path"),
            Main.class.getName(), "--bootstrap", "--movement");
        return builder.redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
    }

    private static void waitForBothListeners(Process child)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (child.isAlive() && System.nanoTime() < deadline) {
            if (canConnect(GAME) && canConnect(AUX)) return;
            Thread.sleep(80);
        }
        throw new AssertionError(
            "admitted Main did not bind both ports; childExit=" +
            (child.isAlive() ? "still running" : child.exitValue()));
    }

    private static boolean canConnect(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
            return true;
        } catch (IOException expected) {
            return false;
        }
    }

    private static void waitForExit(Process child, String context)
            throws Exception {
        if (!child.waitFor(EXIT_TIMEOUT, TimeUnit.SECONDS)) {
            throw new AssertionError(context + " process did not exit");
        }
    }

    private static void assertNoServerStarted(Path log, String context)
            throws IOException {
        String output = readLog(log);
        if (output.contains("SpawnPK Local Lab") ||
                output.contains("PULSE :") ||
                output.contains("GAME  :")) {
            throw new AssertionError(
                context + " leaked started World or live listener");
        }
    }

    private static String readLog(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void stopOwnedChild(Process owned) throws Exception {
        if (!owned.isAlive()) return;
        owned.destroyForcibly();
        if (!owned.waitFor(5, TimeUnit.SECONDS)) {
            throw new AssertionError("owned child did not stop");
        }
    }

    private static ServerSocket reserve(int port) throws IOException {
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), port));
            return socket;
        } catch (IOException | RuntimeException failure) {
            try { socket.close(); }
            catch (IOException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }

    private static void requirePortAvailable(int port) throws IOException {
        try (ServerSocket unused = reserve(port)) {
            if (!unused.isBound()) throw new IOException("port " + port);
        }
    }

    private MainTwoPortStartupBoundaryTest() {}
}
