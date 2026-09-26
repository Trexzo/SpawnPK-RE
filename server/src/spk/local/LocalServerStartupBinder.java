package spk.local;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.Objects;

/**
 * Failure-atomic acquisition of the two LocalLab listener sockets.
 *
 * The shutdown coordinator must already own the World, pool and both unbound
 * sockets before this helper is called, so a partial bind can use the exact
 * ordinary terminal path.
 */
final class LocalServerStartupBinder {
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

    private LocalServerStartupBinder(){}
}
