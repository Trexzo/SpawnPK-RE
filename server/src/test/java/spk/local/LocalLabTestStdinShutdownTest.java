package spk.local;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** R14 positive/negative offline test: never actually exits the test JVM. */
public final class LocalLabTestStdinShutdownTest {
    private static final String TOKEN = "a".repeat(64);

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void testArm(String candidate, int expectedCalls) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Thread watcher = LocalLabTestStdinShutdown.arm(
            input(candidate), TOKEN, calls::incrementAndGet
        );
        watcher.join(1500);
        require(!watcher.isAlive(), "private stdin watcher leaked");
        require(calls.get() == expectedCalls, "incorrect private stdin authorization");
    }

    public static void main(String[] args) throws Exception {
        testArm(TOKEN + "\n", 1);
        testArm(TOKEN + "\r\n", 1);
        testArm("b".repeat(64) + "\n", 0);
        testArm("c".repeat(63) + "\n", 0);
        testArm("wrong-token\n", 0);
        testArm(TOKEN + "0\n", 0);
        testArm(TOKEN, 0); // Require terminated line, not a bare EOF.

        String beforeEnable = System.getProperty(LocalLabTestStdinShutdown.ENABLED);
        String beforeToken = System.getProperty(LocalLabTestStdinShutdown.TOKEN);
        try {
            AtomicInteger calls = new AtomicInteger();
            System.clearProperty(LocalLabTestStdinShutdown.ENABLED);
            System.setProperty(LocalLabTestStdinShutdown.TOKEN, TOKEN);
            Thread disabled = LocalLabTestStdinShutdown.armFromProperties(
                input(TOKEN + "\n"), calls::incrementAndGet
            );
            require(disabled == null && calls.get() == 0,
                "test-only stdin reader unexpectedly enabled by token alone");

            System.setProperty(LocalLabTestStdinShutdown.ENABLED, "true");
            System.setProperty(LocalLabTestStdinShutdown.TOKEN, "invalid");
            boolean rejected = false;
            try {
                LocalLabTestStdinShutdown.armFromProperties(
                    input(TOKEN + "\n"), calls::incrementAndGet
                );
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected, "invalid configured token not rejected");

            System.setProperty(LocalLabTestStdinShutdown.TOKEN, TOKEN);
            Thread authorized = LocalLabTestStdinShutdown.armFromProperties(
                input(TOKEN + "\n"), calls::incrementAndGet
            );
            require(authorized != null, "explicitly enabled reader missing");
            authorized.join(1500);
            require(calls.get() == 1, "explicit private stdin command not executed once");
        } finally {
            if (beforeEnable == null) System.clearProperty(LocalLabTestStdinShutdown.ENABLED);
            else System.setProperty(LocalLabTestStdinShutdown.ENABLED, beforeEnable);
            if (beforeToken == null) System.clearProperty(LocalLabTestStdinShutdown.TOKEN);
            else System.setProperty(LocalLabTestStdinShutdown.TOKEN, beforeToken);
        }
        System.out.println(
            "R14_TEST_ONLY_STDIN_SHUTDOWN_GUARD_PASS "
            + "defaultDisabled=true exactTokenOnly=true oneShot=true "
            + "malformedAndEOFRejected=true normalJvmExitInjectedForTest=true"
        );
    }
}
