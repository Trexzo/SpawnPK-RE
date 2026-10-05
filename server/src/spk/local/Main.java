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
        final LocalSession.MonsterSpawnerUiFactory
            monsterSpawnerUiFactory;

        try {
            kotlinPluginCount =
                KotlinPluginDirectory.loadStartup(
                    world,
                    Paths.get(
                        "plugins",
                        "kotlin"
                    )
                );

            monsterSpawnerUiFactory=
                LocalLabMonsterSpawnerProvisioning
                    .create(
                        world
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

        LocalServerStartupBinder.Resources startup =
            LocalServerStartupBinder.prepare(
                world,
                ()->
                    Executors.newCachedThreadPool(
                        r->{
                            Thread t=
                                new Thread(
                                    r,
                                    "spk-local-session"
                                );
                            t.setDaemon(
                                true
                            );
                            return t;
                        }
                    )
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
                System.out.println(
                    "MONSTER_SPAWNER: CUSTOM_LOCALLAB shared runtime configured; command=::monsterspawner"
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

        Throwable servingFailure=null;

        try {
            if (!shutdown.submitAuxiliary(
                    () -> localAux(aux, shutdown)))
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
                                    world,
                                    monsterSpawnerUiFactory
                                )))
                    break;
            }
        } catch (Throwable failure) {
            servingFailure=failure;
        } finally {
            MainShutdownFinalizer.runPreserving(
                servingFailure,
                shutdown::close,
                ()->runtime.removeShutdownHook(
                    shutdownHook
                )
            );
        }
    }

    private static void localAux(
        ServerSocket server,
        LocalServerShutdownCoordinator shutdown
    ) {
        LocalAuxHttpWorker.run(
            server::isClosed,
            shutdown::acceptAuxiliarySocket,
            socket->{
                if(!socket.getInetAddress()
                        .isLoopbackAddress())
                    return;

                handleAuxConnection(
                    socket,
                    shutdown::claimAuxiliaryResponseTimeout,
                    shutdown::publishAuxiliaryWorkerFailure
                );
            },
            shutdown::releaseAuxiliarySocket,
            failure->
                System.err.println(
                    "[local-aux] "+
                    failure
                ),
            failure->
                System.err.println(
                    "[local-aux] socket retirement failed "+
                    failure
                )
        );
    }

    static void handleAuxConnection(Socket s) throws IOException {
        handleAuxConnection(
            s,
            commit->commit.getAsBoolean(),
            failure->{}
        );
    }

    static void handleAuxConnection(
        Socket s,
        LocalAuxResponseLiveness.TimeoutAuthority
            timeoutAuthority,
        java.util.function.Consumer<Throwable> livenessFailure
    ) throws IOException {
        InputStream in = s.getInputStream();
        OutputStream out = s.getOutputStream();
        LocalAuxHttpRequestReader request =
            LocalAuxHttpRequestReader.forSocket(
                s,
                in
            );
        String first =
            request.readRequestLine();
        if (first == null || first.isEmpty()) {
            System.out.println("[local-aux] " + s.getRemoteSocketAddress() + " empty connection; closed locally");
            return;
        }

        if (first.startsWith("GET ") || first.startsWith("HEAD ") || first.startsWith("POST ")) {
            boolean head = first.startsWith("HEAD ");
            String[] parts = first.split(" ", 3);
            String target = parts.length >= 2 ? parts[1] : "/";
            // Consume remaining HTTP request headers under the same absolute
            // request deadline and aggregate byte/line budget as the request line.
            // Body-bearing requests are not required by startup; POST endpoints
            // receive the same harmless local response behavior as before.
            request.consumeHeaders();

            LocalAuxResponseLiveness liveness=
                LocalAuxResponseLiveness.arm(
                    s,
                    timeoutAuthority,
                    livenessFailure
                );
            OutputStream responseOut=
                liveness.output(
                    out
                );
            Throwable responseFailure=null;

            try{
                byte[] body;
                String contentType = "text/plain; charset=us-ascii";
                int status = 200;
                String reason = "OK";
                String classification;
                boolean responseComplete=false;

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
                    Long length =
                        local==null
                            ?null
                            :LocalAuxArchiveAccess
                                .writeIfAvailable(
                                    responseOut,
                                    local,
                                    head
                                );

                    if(length!=null) {
                        System.out.println(
                            "[local-aux] HTTP local-archive target=" + target +
                            " status=200 bytes=" + length.longValue()
                        );
                        responseComplete=true;
                        body=null;
                        classification=null;
                    }else{
                        body = "LOCAL_ARCHIVE_NOT_PRESENT\n".getBytes(StandardCharsets.US_ASCII);
                        status = 404;
                        reason = "Not Found";
                        classification = "unexpected-archive-request";
                    }
                } else {
                    body = new byte[0];
                    classification = "blocked-placeholder";
                }

                if(!responseComplete){
                    LocalAuxHttpResponse.writeBytes(
                        responseOut,
                        status,
                        reason,
                        contentType,
                        body,
                        head
                    );
                    System.out.println("[local-aux] HTTP " + classification + " target=" + target
                                     + " status=" + status + " bytes=" + body.length);
                }
            }catch(IOException|
                   RuntimeException|
                   Error failure){
                responseFailure=failure;
            }

            responseFailure=
                liveness.finish(
                    responseFailure
                );

            if(responseFailure instanceof IOException)
                throw (IOException)responseFailure;

            if(responseFailure instanceof RuntimeException)
                throw (RuntimeException)responseFailure;

            if(responseFailure instanceof Error)
                throw (Error)responseFailure;

            if(responseFailure!=null)
                throw new IOException(
                    "unexpected auxiliary response failure",
                    responseFailure
                );

            return;
        }

        // Legacy/JAGGRAB or unknown traffic remains local.  Do not invent a protocol
        // reply until a real localhost run proves it is required.
        System.out.println("[local-aux] NON_HTTP firstLine=" + printable(first)
                         + " peer=" + s.getRemoteSocketAddress() + " action=closed-local");
    }

    private static byte[] localVersions() {
        return LocalAuxVersions.readUserHome(
            FALLBACK_VERSIONS
        );
    }

    private static Path localArchiveFor(String target) {
        return LocalAuxArchivePath.resolve(
            target
        );
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
