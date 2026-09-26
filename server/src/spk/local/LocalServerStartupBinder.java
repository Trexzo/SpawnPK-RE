package spk.local;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Failure-atomic acquisition of the LocalLab listener/socket startup boundary.
 *
 * Listener construction is guarded before a coordinator can exist; once both
 * sockets exist, the ordinary shutdown coordinator owns World, pool and sockets
 * for bind and shutdown-hook registration failure cleanup.
 */
final class LocalServerStartupBinder {
    interface ListenerFactory {
        ServerSocket create()
            throws IOException;
    }

    interface ShutdownHookFactory {
        Thread create(
            Runnable target,
            String name
        );
    }

    interface ShutdownHookInstaller {
        void install(
            Thread hook
        );
    }

    static final class Resources {
        final ServerSocket game;
        final ServerSocket aux;
        final LocalServerShutdownCoordinator shutdown;

        private Resources(
            ServerSocket game,
            ServerSocket aux,
            LocalServerShutdownCoordinator shutdown
        ){
            this.game=game;
            this.aux=aux;
            this.shutdown=shutdown;
        }
    }

    static Resources prepare(
        World world,
        ExecutorService pool
    )throws IOException{
        return prepare(
            world,
            pool,
            ServerSocket::new
        );
    }

    static Resources prepare(
        World world,
        ExecutorService pool,
        ListenerFactory factory
    )throws IOException{
        Objects.requireNonNull(
            world,
            "world"
        );
        Objects.requireNonNull(
            pool,
            "pool"
        );
        Objects.requireNonNull(
            factory,
            "factory"
        );

        ServerSocket game=null;
        ServerSocket aux=null;

        try{
            game=
                Objects.requireNonNull(
                    factory.create(),
                    "game socket"
                );
            aux=
                Objects.requireNonNull(
                    factory.create(),
                    "aux socket"
                );

            return new Resources(
                game,
                aux,
                new LocalServerShutdownCoordinator(
                    world,
                    pool,
                    game,
                    aux
                )
            );
        }catch(Throwable failure){
            cleanupBeforeCoordinator(
                game,
                aux,
                pool,
                world,
                failure
            );

            if(failure instanceof IOException)
                throw (IOException)failure;
            rethrowUnchecked(
                failure
            );
            throw new AssertionError(
                "unreachable"
            );
        }
    }

    static void bind(
        LocalServerShutdownCoordinator shutdown,
        ServerSocket game,
        SocketAddress gameAddress,
        ServerSocket aux,
        SocketAddress auxAddress
    )throws IOException{
        Objects.requireNonNull(
            shutdown,
            "shutdown"
        );
        Objects.requireNonNull(
            game,
            "game"
        );
        Objects.requireNonNull(
            gameAddress,
            "gameAddress"
        );
        Objects.requireNonNull(
            aux,
            "aux"
        );
        Objects.requireNonNull(
            auxAddress,
            "auxAddress"
        );

        try{
            game.bind(
                gameAddress
            );
            aux.bind(
                auxAddress
            );
        }catch(Throwable failure){
            try{
                shutdown.close();
            }catch(Throwable cleanup){
                if(cleanup!=failure)
                    failure.addSuppressed(
                        cleanup
                    );
            }

            if(failure instanceof IOException)
                throw (IOException)failure;
            if(failure instanceof RuntimeException)
                throw (RuntimeException)failure;
            if(failure instanceof Error)
                throw (Error)failure;

            throw new IOException(
                "LocalLab listener startup failed",
                failure
            );
        }
    }

    static Thread installShutdownHook(
        LocalServerShutdownCoordinator shutdown,
        Runnable target,
        String name,
        ShutdownHookFactory factory,
        ShutdownHookInstaller installer
    ){
        Objects.requireNonNull(
            shutdown,
            "shutdown"
        );
        Objects.requireNonNull(
            target,
            "target"
        );
        Objects.requireNonNull(
            name,
            "name"
        );
        Objects.requireNonNull(
            factory,
            "factory"
        );
        Objects.requireNonNull(
            installer,
            "installer"
        );

        try{
            Thread hook=
                Objects.requireNonNull(
                    factory.create(
                        target,
                        name
                    ),
                    "shutdown hook"
                );

            installer.install(
                hook
            );

            return hook;
        }catch(Throwable failure){
            try{
                shutdown.close();
            }catch(Throwable cleanup){
                if(cleanup!=failure)
                    failure.addSuppressed(
                        cleanup
                    );
            }

            rethrowUnchecked(
                failure
            );
            throw new AssertionError(
                "unreachable"
            );
        }
    }

    static void installShutdownHook(
        LocalServerShutdownCoordinator shutdown,
        Runnable installer
    ){
        Objects.requireNonNull(
            shutdown,
            "shutdown"
        );
        Objects.requireNonNull(
            installer,
            "installer"
        );

        try{
            installer.run();
        }catch(Throwable failure){
            try{
                shutdown.close();
            }catch(Throwable cleanup){
                if(cleanup!=failure)
                    failure.addSuppressed(
                        cleanup
                    );
            }

            rethrowUnchecked(
                failure
            );
        }
    }

    private static void cleanupBeforeCoordinator(
        ServerSocket game,
        ServerSocket aux,
        ExecutorService pool,
        World world,
        Throwable primary
    ){
        runCleanup(
            primary,
            ()->closeQuietly(aux)
        );
        runCleanup(
            primary,
            ()->closeQuietly(game)
        );
        runCleanup(
            primary,
            pool::shutdownNow
        );
        runCleanup(
            primary,
            world::close
        );
    }

    private static void runCleanup(
        Throwable primary,
        Runnable cleanup
    ){
        try{
            cleanup.run();
        }catch(Throwable failure){
            if(failure!=primary)
                primary.addSuppressed(
                    failure
                );
        }
    }

    private static void closeQuietly(
        ServerSocket socket
    ){
        if(socket==null)
            return;

        try{
            socket.close();
        }catch(IOException ignored){
        }
    }

    private static void rethrowUnchecked(
        Throwable failure
    ){
        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;
        if(failure instanceof Error)
            throw (Error)failure;

        throw new RuntimeException(
            failure
        );
    }

    private LocalServerStartupBinder(){}
}
