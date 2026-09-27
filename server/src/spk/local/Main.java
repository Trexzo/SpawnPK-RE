package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

public final class Main {
    private static final String LOOPBACK = "127.0.0.1";
    private static final int GAME_PORT = 43594;
    private static final int AUX_PORT = 43595;
    private static final byte[] FALLBACK_VERSIONS = (
        "cache_version = 67.0\r\n" +
        "sprite_version = 72.0\r\n" +
        "config_version = 110.0\r\n"
    ).getBytes(StandardCharsets.US_ASCII);

    public static void main(String[] args) throws Exception {
        boolean bootstrap = false;
        boolean movement = false;
        for (String arg : args) {
            if ("--bootstrap".equals(arg)) bootstrap = true;
            else if ("--movement".equals(arg)) movement = true;
            else throw new IllegalArgumentException("unknown argument: " + arg);
        }
        if (movement && !bootstrap) throw new IllegalArgumentException("--movement requires --bootstrap");
        final boolean bootstrapFinal = bootstrap;
        final boolean movementFinal = movement;
        InetAddress bind = InetAddress.getByName(LOOPBACK);
        if (!bind.isLoopbackAddress()) throw new SecurityException("refusing non-loopback bind: " + bind);

        World world = World.shared();
        world.start();

        int kotlinPluginCount;

        try {
            kotlinPluginCount =
                KotlinPluginDirectory.loadStartup(
                    world,
                    Paths.get(
                        "plugins",
                        "kotlin"
                    )
                );
        } catch (Throwable failure) {
            try {
                world.close();
            } catch (Throwable cleanup) {
                failure.addSuppressed(
                    cleanup
                );
            }

            if (failure instanceof Exception)
                throw (Exception)failure;
            if (failure instanceof Error)
                throw (Error)failure;

            throw new RuntimeException(
                failure
            );
        }

        ExecutorService pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "spk-local-session"); t.setDaemon(true); return t;
        });

        LocalServerStartupBinder.Resources startup =
            LocalServerStartupBinder.prepare(
                world,
                pool
            );
        ServerSocket game = startup.game;
        ServerSocket aux = startup.aux;
        LocalServerShutdownCoordinator shutdown =
            startup.shutdown;

        LocalServerStartupBinder.bind(
            shutdown,
            game,
            new InetSocketAddress(
                bind,
                GAME_PORT
            ),
            aux,
            new InetSocketAddress(
                bind,
                AUX_PORT
            )
        );

        LocalServerStartupBinder.runBoundSetup(
            shutdown,
            ()->{
                System.out.println("SpawnPK Local Lab "+BuildInfo.summary());
                System.out.println("GAME  : " + game.getLocalSocketAddress());
                System.out.println("AUX   : " + aux.getLocalSocketAddress() + " (loopback HTTP/cache guard)");
                System.out.println("GUARD : loopback-only; no code path dials a remote host");
                System.out.println("M4    : certified bootstrap=" + bootstrapFinal + " (default false)");
                System.out.println("M5    : authoritative movement=" + movementFinal + " (600 ms server tick)");
                System.out.println("ENGINE: one shared World + one authoritative WorldPulse; command migration staged by subsystem");
                System.out.println("PULSE : " + world.metrics());
                System.out.println("ITEMS : "+ItemDefinitionRepository.count()+" current client-known ids; ::item / ::tabitem <id> [amount]");
                System.out.println("SPAWN : packet71 shortcut 0 -> native root 67027 on sidebar tab "+BootstrapPackets.SPAWN_TAB_INDEX);
                System.out.println(
                    "KOTLIN: startup plugins=" +
                    kotlinPluginCount
                );
            }
        );

        Runtime runtime =
            Runtime.getRuntime();

        Thread shutdownHook =
            LocalServerStartupBinder.installShutdownHook(
                shutdown,
                shutdown::close,
                "spk-local-shutdown",
                (target,name)->
                    new Thread(
                        target,
                        name
                    ),
                runtime::addShutdownHook
            );

        try {
            if (!shutdown.submitAuxiliary(
                    () -> localAux(aux)))
                return;

            while (!shutdown.closing()) {
                Socket s=
                    shutdown.acceptGameSocket();

                if(s==null)
                    break;

                if (!s.getInetAddress().isLoopbackAddress()) {
                    shutdown.rejectSessionSocket(
                        s
                    );
                    continue;
                }

                if (!shutdown.submitSession(
                        s,
                        (LocalServerShutdownCoordinator.SessionFactory)
                            ()->
                                new LocalSession(
                                    s,
                                    bootstrapFinal,
                                    movementFinal,
                                    world
                                )))
                    break;
            }
        } finally {
            MainShutdownFinalizer.run(
                shutdown::close,
                ()->runtime.removeShutdownHook(
                    shutdownHook
                )
            );
        }
    }

    private static void localAux(ServerSocket server) {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                if (!s.getInetAddress().isLoopbackAddress()) continue;
                handleAuxConnection(s);
            } catch (IOException e) {
                if (!server.isClosed()) System.err.println("[local-aux] " + e);
            }
        }
    }

    private static void handleAuxConnection(Socket s) throws IOException {
        s.setSoTimeout(2_000);
        InputStream in = s.getInputStream();
        OutputStream out = s.getOutputStream();
        String first = readAsciiLine(in, 8192);
        if (first == null || first.isEmpty()) {
            System.out.println("[local-aux] " + s.getRemoteSocketAddress() + " empty connection; closed locally");
            return;
        }

        if (first.startsWith("GET ") || first.startsWith("HEAD ") || first.startsWith("POST ")) {
            boolean head = first.startsWith("HEAD ");
            String[] parts = first.split(" ", 3);
            String target = parts.length >= 2 ? parts[1] : "/";
            // Consume remaining HTTP request headers.  Body-bearing requests are not
            // required by startup; POST endpoints receive a harmless local response.
            while (true) {
                String line = readAsciiLine(in, 8192);
                if (line == null || line.isEmpty()) break;
            }

            byte[] body;
            String contentType = "text/plain; charset=us-ascii";
            int status = 200;
            String reason = "OK";
            String classification;

            if (target.contains("/spk_live/versions.txt")) {
                body = localVersions();
                classification = "versions";
            } else if (target.contains("/forum/10-updates") || target.contains("/forums/")) {
                // rs.l.d.e is happy with an empty successful page; EOF from a valid HTTP
                // response is not an exception, unlike v0.3's raw socket close.
                body = new byte[0];
                contentType = "text/html; charset=utf-8";
                classification = "forum-placeholder";
            } else if (target.contains("/Production/tradingpost")) {
                body = "{}\n".getBytes(StandardCharsets.UTF_8);
                contentType = "application/json; charset=utf-8";
                classification = "tradingpost-placeholder";
            } else if (target.endsWith("/cache.zip") || target.endsWith("/sprites.zip") || target.endsWith("/configs.zip")) {
                Path local = localArchiveFor(target);
                if (local != null && Files.isRegularFile(local)) {
                    body = Files.readAllBytes(local);
                    contentType = "application/zip";
                    classification = "local-archive";
                } else {
                    body = "LOCAL_ARCHIVE_NOT_PRESENT\n".getBytes(StandardCharsets.US_ASCII);
                    status = 404;
                    reason = "Not Found";
                    classification = "unexpected-archive-request";
                }
            } else {
                body = new byte[0];
                classification = "blocked-placeholder";
            }

            writeHttp(out, status, reason, contentType, body, head);
            System.out.println("[local-aux] HTTP " + classification + " target=" + target
                             + " status=" + status + " bytes=" + body.length);
            return;
        }

        // Legacy/JAGGRAB or unknown traffic remains local.  Do not invent a protocol
        // reply until a real localhost run proves it is required.
        System.out.println("[local-aux] NON_HTTP firstLine=" + printable(first)
                         + " peer=" + s.getRemoteSocketAddress() + " action=closed-local");
    }

    private static byte[] localVersions() {
        try {
            Path p = Paths.get(System.getProperty("user.home"), ".spawnpk", "versions.dat");
            if (Files.isRegularFile(p)) {
                byte[] data = Files.readAllBytes(p);
                if (data.length > 0 && data.length < 16_384) return data;
            }
        } catch (Throwable ignored) {}
        return FALLBACK_VERSIONS.clone();
    }

    private static Path localArchiveFor(String target) {
        String name;
        if (target.endsWith("/cache.zip")) name = "cache.zip";
        else if (target.endsWith("/sprites.zip")) name = "sprites.zip";
        else if (target.endsWith("/configs.zip")) name = "configs.zip";
        else return null;
        return Paths.get(System.getProperty("user.home"), ".spawnpk", name);
    }

    private static String readAsciiLine(InputStream in, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        while (b.size() < max) {
            int x = in.read();
            if (x < 0) return b.size() == 0 ? null : b.toString(StandardCharsets.ISO_8859_1.name());
            if (x == '\n') break;
            if (x != '\r') b.write(x);
        }
        return b.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static void writeHttp(OutputStream out, int status, String reason, String type, byte[] body, boolean head)
            throws IOException {
        String h = "HTTP/1.1 " + status + " " + reason + "\r\n"
                 + "Content-Type: " + type + "\r\n"
                 + "Content-Length: " + body.length + "\r\n"
                 + "Connection: close\r\n"
                 + "Cache-Control: no-store\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.US_ASCII));
        if (!head) out.write(body);
        out.flush();
    }

    private static String printable(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && i < 160; i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 127) b.append(c); else b.append('?');
        }
        return b.toString();
    }
}
