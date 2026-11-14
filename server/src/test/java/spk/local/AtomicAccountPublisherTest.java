package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Java 11 Windows/Linux integration: atomic account publication never silently
 * falls back to non-atomic replacement; staged bytes are forced before rename.
 * Only a freshly launched test child is deliberately halted before commit.
 */
public final class AtomicAccountPublisherTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--halt-before-commit".equals(args[0])) {
            Path target = Paths.get(args[1]);
            AtomicAccountPublisher.publish(
                target, value("new-after-halt"),
                "R16 pre-commit test child",
                (staged, destination) -> Runtime.getRuntime().halt(73));
            throw new AssertionError("test child did not halt");
        }
        if (args.length != 0) throw new IllegalArgumentException("arguments");

        Path root = Files.createTempDirectory("spk-r16-atomic-account-");
        try {
            Path target = root.resolve("opensrc.properties");
            byte[] baseline = "balance=original\n".getBytes(StandardCharsets.US_ASCII);
            Files.write(target, baseline);

            // Unsupported atomic move must preserve old bytes, not run a
            // fallback, and must clean this attempt's temporary file.
            try {
                AtomicAccountPublisher.publish(target, value("unsupported"),
                    "R16 injected failure", (staged, destination) -> {
                        throw new AtomicMoveNotSupportedException(
                            staged.toString(), destination.toString(),
                            "injected unsupported atomic move");
                    });
                throw new AssertionError("unsupported atomic move accepted");
            } catch (AtomicMoveNotSupportedException expected) {
                // No optional downgrade to non-atomic replace.
            }
            assertBytes(target, baseline, "unsupported atomic move");
            if (stageCount(root) != 0) {
                throw new AssertionError("failed save leaked staged file");
            }

            // Legacy .tmp is never truncated or adopted as our staged file.
            Path legacyTemp = root.resolve("opensrc.properties.tmp");
            byte[] legacyBytes = "legacy-in-progress".getBytes(StandardCharsets.US_ASCII);
            Files.write(legacyTemp, legacyBytes);
            AtomicAccountPublisher.publish(target, value("committed"),
                "R16 successful replacement");
            if (!"committed".equals(read(target, "balance"))) {
                throw new AssertionError("atomic publish did not replace account");
            }
            assertBytes(legacyTemp, legacyBytes, "legacy temp untouched");
            if (stageCount(root) != 0) {
                throw new AssertionError("successful publish left staged file");
            }

            // Existing non-file account target must be rejected without
            // deleting/changing its directory or contents.
            Path directoryTarget = root.resolve("src.properties");
            Files.createDirectories(directoryTarget);
            Path marker = directoryTarget.resolve("keep");
            Files.write(marker, baseline);
            expectDenied(directoryTarget, "directory target");
            assertBytes(marker, baseline, "directory target retained");

            // Where supported, reject a symlink target without following it.
            Path victim = root.resolve("victim.properties");
            Files.write(victim, baseline);
            Path symlink = root.resolve("symlink.properties");
            try {
                Files.createSymbolicLink(symlink, victim);
                expectDenied(symlink, "symlink target");
                assertBytes(victim, baseline, "symlink victim unchanged");
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
                // Some Windows runners do not allow symlink creation.
            }

            // Kill an exact child JVM AFTER it forces a new staged file, but
            // BEFORE atomic move. The previously committed account survives.
            byte[] committed = Files.readAllBytes(target);
            String javaExecutable = Paths.get(System.getProperty("java.home"),
                "bin", System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "java.exe" : "java").toString();
            Process child = new ProcessBuilder(javaExecutable, "-cp",
                System.getProperty("java.class.path"),
                AtomicAccountPublisherTest.class.getName(),
                "--halt-before-commit", target.toString())
                .redirectErrorStream(true).start();
            try {
                if (!child.waitFor(12, TimeUnit.SECONDS)) {
                    child.destroyForcibly();
                    child.waitFor(5, TimeUnit.SECONDS);
                    throw new AssertionError("staging test child timed out");
                }
                if (child.exitValue() != 73) {
                    throw new AssertionError("staging child exit " + child.exitValue());
                }
            } finally {
                if (child.isAlive()) child.destroyForcibly();
            }
            assertBytes(target, committed, "interrupted before atomic rename");
            if (stageCount(root) < 1) {
                throw new AssertionError("abrupt child left no staged evidence");
            }
            // R51: the forced-but-unpublished stage is ambiguous, not proof
            // of an abandoned writer or permission to overwrite the account.
            // Check exact fixture ownership and byte stability before refusing.
            Path interruptedStage;
            try (Stream<Path> staged = Files.list(root)) {
                Path[] matches = staged.filter(path ->
                    path.getFileName().toString().startsWith(".opensrc.properties.r16-")
                    && path.getFileName().toString().endsWith(".stage"))
                    .toArray(Path[]::new);
                if (matches.length != 1) {
                    throw new AssertionError("expected one test-owned interrupted stage");
                }
                interruptedStage = matches[0];
            }
            byte[] interruptedBytes = Files.readAllBytes(interruptedStage);
            try {
                AtomicAccountPublisher.publish(target, value("after-restart"),
                    "R51 must refuse ambiguous interrupted account");
                throw new AssertionError("R51 allowed overwrite before stage review");
            } catch (IOException expected) {
                if (!expected.getMessage().contains(
                        "R51_AMBIGUOUS_ACCOUNT_STAGING_DENIED")) {
                    throw new AssertionError("unexpected account quarantine failure", expected);
                }
            }
            assertBytes(target, committed, "R51 ambiguous account retained");
            assertBytes(interruptedStage, interruptedBytes, "R51 stage evidence retained");
            if (stageCount(root) != 1) {
                throw new AssertionError("R51 refusal altered staging evidence");
            }

            // This test alone created the stage and halted its exact child.
            // Clear only that disposable fixture after the above assertions.
            // Never auto-clear an operator's real account staging files.
            Files.delete(interruptedStage);
            AtomicAccountPublisher.publish(target, value("after-restart"),
                "R51 reviewed test-only publisher restart");
            if (!"after-restart".equals(read(target, "balance"))) {
                throw new AssertionError("reviewed restart failed");
            }
            if (stageCount(root) != 0) {
                throw new AssertionError("reviewed restart leaked stage");
            }
            assertBytes(legacyTemp, legacyBytes, "legacy temp after restart");

            System.out.println("R16_ACCOUNT_ATOMIC_PUBLISH_INTERRUPT_PASS "
                + "atomicRequired=true forcedStage=true "
                + "simulatedAbruptChildExit=true oldAccountPreserved=true "
                + "ambiguousOrphanQuarantined=true "
                + "testOnlyReviewedResumption=true legacyTmpUnchanged=true");
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException failure) {
                        throw new IllegalStateException("test cleanup " + path, failure);
                    }
                });
            }
        }
    }

    private static Properties value(String balance) {
        Properties p = new Properties();
        p.setProperty("balance", balance);
        return p;
    }

    private static String read(Path path, String key) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        }
        return p.getProperty(key);
    }

    private static void expectDenied(Path target, String reason) throws Exception {
        try {
            AtomicAccountPublisher.publish(target, value("bad"), "R16 deny");
            throw new AssertionError(reason + " unexpectedly admitted");
        } catch (IOException expected) {
            if (!expected.getMessage().contains("R16_ATOMIC_ACCOUNT_PUBLISH_DENIED")) {
                throw new AssertionError(reason + " wrong refusal", expected);
            }
        }
    }

    private static int stageCount(Path root) throws IOException {
        try (Stream<Path> files = Files.list(root)) {
            return (int) files.filter(path ->
                path.getFileName().toString().startsWith(".opensrc.properties.r16-")
                && path.getFileName().toString().endsWith(".stage")).count();
        }
    }

    private static void assertBytes(Path path, byte[] expected, String reason)
            throws IOException {
        if (!Arrays.equals(Files.readAllBytes(path), expected)) {
            throw new AssertionError(reason);
        }
    }

    private AtomicAccountPublisherTest() {}
}
