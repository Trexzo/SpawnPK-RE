package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * CUSTOM_LOCALLAB test-only, *stdin-only* graceful JVM shutdown admission.
 *
 * This is intentionally NOT a socket endpoint or a client packet. Enabled
 * only by explicit test property and a 64-lowercase-hex private pipe token.
 * Production startup does not read System.in or permit remote shutdown.
 *
 * Windows subprocess.terminate() uses TerminateProcess and skips JVM shutdown
 * hooks. This acceptance-only private pipe requests normal System.exit(0)
 * instead, allowing the existing LocalServerShutdownCoordinator to finish
 * player persistence and free its own sockets. A bad/unknown input is ignored.
 */
final class LocalLabTestStdinShutdown {
    static final String ENABLED = "spk.local.acceptance.stdinShutdownEnabled";
    static final String TOKEN = "spk.local.acceptance.stdinShutdownToken";

    private LocalLabTestStdinShutdown() {}

    static Thread armFromProperties(InputStream input, Runnable normalJvmExit) {
        String flag = System.getProperty(ENABLED, "false");
        if ("false".equals(flag)) {
            return null;
        }
        if (!"true".equals(flag)) {
            throw new IllegalArgumentException("R14 invalid test stdin enable value");
        }
        String token = System.getProperty(TOKEN);
        if (token == null || !token.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                "R14 enabled test stdin shutdown requires 64-hex private token"
            );
        }
        if (input == null || normalJvmExit == null) {
            throw new IllegalArgumentException("R14 missing private stdin or exit callback");
        }
        return arm(input, token, normalJvmExit);
    }

    static Thread arm(InputStream input, String exactToken, Runnable exit) {
        if (!exactToken.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("R14 invalid private token");
        }
        Thread watcher = new Thread(() -> {
            try {
                String command = firstLine(input);
                if (exactToken.equals(command)) {
                    System.out.println(
                        "R14_TEST_ONLY_STDIN_SHUTDOWN_AUTHORIZED "
                        + "authority=CUSTOM_LOCALLAB_PRIVATE_TEST_PIPE"
                    );
                    exit.run();
                }
            } catch (IOException error) {
                System.err.println("R14_TEST_ONLY_STDIN_SHUTDOWN_PIPE_CLOSED");
            }
        }, "spk-r14-private-test-stdin");
        watcher.setDaemon(true);
        watcher.start();
        return watcher;
    }

    private static String firstLine(InputStream in) throws IOException {
        byte[] data = new byte[64];
        int used = 0;
        while (true) {
            int next = in.read();
            if (next < 0) return null;
            if (next == '\n') {
                return new String(data, 0, used, StandardCharsets.US_ASCII);
            }
            if (next == '\r') continue;
            if (next < '0' || next > 'f' || used >= data.length) {
                return null;
            }
            data[used++] = (byte) next;
        }
    }
}
