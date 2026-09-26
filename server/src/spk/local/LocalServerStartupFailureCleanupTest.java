package spk.local;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalServerStartupFailureCleanupTest {
    public static void main(
        String[] args
    )throws Exception{
        InetAddress loopback=
            InetAddress.getByName(
                "127.0.0.1"
            );

        try(ServerSocket blocker=
                new ServerSocket()){
            blocker.bind(
                new InetSocketAddress(
                    loopback,
                    0
                )
            );

            World world=
                World.isolatedForTest(
                    25L
                );
            world.start();

            ExecutorService pool=
                Executors.newCachedThreadPool();

            ServerSocket game=
                new ServerSocket();
            ServerSocket aux=
                new ServerSocket();

            LocalServerShutdownCoordinator shutdown=
                new LocalServerShutdownCoordinator(
                    world,
                    pool,
                    game,
                    aux
                );

            IOException bindFailure=null;

            try{
                LocalServerStartupBinder.bind(
                    shutdown,
                    game,
                    new InetSocketAddress(
                        loopback,
                        0
                    ),
                    aux,
                    new InetSocketAddress(
                        loopback,
                        blocker.getLocalPort()
                    )
                );
            }catch(IOException expected){
                bindFailure=expected;
            }

            if(bindFailure==null)
                throw new AssertionError(
                    "forced auxiliary bind failure was not propagated"
                );

            if(!game.isClosed()||
               !aux.isClosed())
                throw new AssertionError(
                    "partial listener acquisition survived bind failure gameClosed="+
                    game.isClosed()+
                    " auxClosed="+
                    aux.isClosed()
                );

            if(!pool.isTerminated())
                throw new AssertionError(
                    "session pool survived startup bind failure"
                );

            if(!world.closed())
                throw new AssertionError(
                    "World survived startup bind failure"
                );

            if(blocker.isClosed())
                throw new AssertionError(
                    "startup cleanup closed unrelated blocker socket"
                );

            shutdown.close();

            if(!pool.isTerminated()||
               !world.closed())
                throw new AssertionError(
                    "repeated startup cleanup changed terminal state"
                );

            System.out.println(
                "LOCAL_SERVER_STARTUP_FAILURE_CLEANUP_PASS "+
                "gameBoundBeforeAuxFailure=true "+
                "bindFailurePrimary=true "+
                "gameClosed=true "+
                "auxClosed=true "+
                "poolTerminated=true "+
                "worldClosed=true "+
                "unrelatedBlockerOpen=true "+
                "repeatedCloseSafe=true"
            );
        }
    }

    private LocalServerStartupFailureCleanupTest(){}
}
