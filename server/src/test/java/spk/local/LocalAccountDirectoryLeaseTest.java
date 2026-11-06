package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/** Executable Java 11 R15 lease checks, including a real second JVM. */
public final class LocalAccountDirectoryLeaseTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 3 && "--child-hold".equals(args[0])) {
            try (LocalAccountDirectoryLease ignored =
                     LocalAccountDirectoryLease.acquire(Paths.get(args[1]))) {
                Files.write(Paths.get(args[2]), new byte[] {1});
                System.in.read();
            }
            return;
        }
        if (args.length != 0) throw new IllegalArgumentException("unexpected test args");

        Path root = Files.createTempDirectory("spk-r15-lease-");
        Path account = root.resolve("accounts").resolve("opensrc.properties");
        Process ownedChild = null;
        try {
            Files.createDirectories(account.getParent());
            byte[] original = "bank.0=995,100002,0\n".getBytes(StandardCharsets.US_ASCII);
            Files.write(account, original);

            try (LocalAccountDirectoryLease first =
                     LocalAccountDirectoryLease.acquire(account)) {
                requireBlocked(account, "same-JVM competing owner");
            }
            try (LocalAccountDirectoryLease recovered =
                     LocalAccountDirectoryLease.acquire(account)) {
                if (recovered == null) throw new AssertionError("reacquire");
            }

            Path ready = root.resolve("child-ready");
            String javaExecutable = Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "java.exe" : "java").toString();
            ownedChild = new ProcessBuilder(
                javaExecutable, "-cp", System.getProperty("java.class.path"),
                LocalAccountDirectoryLeaseTest.class.getName(),
                "--child-hold", account.toString(), ready.toString()
            ).redirectErrorStream(true).start();

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (!Files.exists(ready) && ownedChild.isAlive()
                    && System.nanoTime() < deadline) {
                Thread.sleep(40);
            }
            if (!Files.exists(ready)) {
                throw new AssertionError("real child JVM did not acquire lease");
            }
            requireBlocked(account, "different-JVM competing owner");

            // Release only our own child, then prove OS lock becomes available.
            ownedChild.getOutputStream().close();
            if (!ownedChild.waitFor(10, TimeUnit.SECONDS) || ownedChild.exitValue() != 0) {
                throw new AssertionError("real child JVM did not exit cleanly");
            }
            ownedChild = null;
            try (LocalAccountDirectoryLease afterChild =
                     LocalAccountDirectoryLease.acquire(account)) {
                if (afterChild == null) throw new AssertionError("post-JVM release");
            }
            if (!java.util.Arrays.equals(Files.readAllBytes(account), original)) {
                throw new AssertionError("refused owner changed persistent account");
            }
            System.out.println("R15_ACCOUNT_DIRECTORY_TWO_JVM_EXCLUSION_PASS");
        } finally {
            if (ownedChild != null) {
                ownedChild.getOutputStream().close();
                if (!ownedChild.waitFor(5, TimeUnit.SECONDS)) {
                    ownedChild.destroyForcibly();  // exact child started by this test
                    ownedChild.waitFor(5, TimeUnit.SECONDS);
                }
            }
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException ignored) {}
                });
            }
        }
    }

    private static void requireBlocked(Path account, String context) throws Exception {
        try (LocalAccountDirectoryLease unexpected =
                 LocalAccountDirectoryLease.acquire(account)) {
            throw new AssertionError(context + ": duplicate owner admitted");
        } catch (IOException expected) {
            if (!expected.getMessage().contains("R15_ACCOUNT_DIRECTORY_LEASE_BLOCKED")) {
                throw new AssertionError(context + ": not a lease denial", expected);
            }
        }
    }

    private LocalAccountDirectoryLeaseTest() {}
}
