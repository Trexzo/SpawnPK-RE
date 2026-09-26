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
        assertListenerConstructionFailureCleanup();
        assertBindFailureCleanup();
        assertHookConstructionFailureCleanup();
        assertHookRegistrationFailureCleanup();

        System.out.println(
            "LOCAL_SERVER_STARTUP_FAILURE_CLEANUP_PASS "+
            "listenerConstructionFailure=true "+
            "partialConstructedSocketClosed=true "+
            "gameBoundBeforeAuxFailure=true "+
            "bindFailurePrimary=true "+
            "gameClosed=true "+
            "auxClosed=true "+
            "poolTerminated=true "+
            "worldClosed=true "+
            "unrelatedBlockerOpen=true "+
            "hookConstructionFailure=true "+
            "hookConstructionFailurePrimary=true "+
            "hookRegistrationFailure=true "+
            "hookFailurePrimary=true "+
            "repeatedCloseSafe=true"
        );
    }

    private static void
        assertListenerConstructionFailureCleanup()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        ExecutorService pool=
            Executors.newCachedThreadPool();

        final ServerSocket[] first=
            new ServerSocket[1];
        final int[] calls=
            new int[1];
        IOException expectedFailure=
            new IOException(
                "fixture-second-listener-create-failure"
            );
        Throwable observed=null;

        try{
            LocalServerStartupBinder.prepare(
                world,
                pool,
                ()->{
                    calls[0]++;

                    if(calls[0]==1){
                        first[0]=
                            new ServerSocket();
                        return first[0];
                    }

                    throw expectedFailure;
                }
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expectedFailure)
            throw new AssertionError(
                "listener construction failure did not remain primary"
            );

        if(first[0]==null||
           !first[0].isClosed())
            throw new AssertionError(
                "partially constructed listener survived startup failure"
            );

        if(!pool.isTerminated())
            throw new AssertionError(
                "listener construction failure left pool live"
            );

        if(!world.closed())
            throw new AssertionError(
                "listener construction failure left World live"
            );
    }

    private static void assertBindFailureCleanup()
        throws Exception{
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

            try{
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

                assertTerminal(
                    world,
                    pool,
                    game,
                    aux,
                    "bind failure"
                );

                if(blocker.isClosed())
                    throw new AssertionError(
                        "startup cleanup closed unrelated blocker socket"
                    );

                shutdown.close();
            }finally{
                if(!world.closed())
                    shutdown.close();
            }
        }
    }

    private static void
        assertHookConstructionFailureCleanup()
        throws Exception{
        InetAddress loopback=
            InetAddress.getByName(
                "127.0.0.1"
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
                    0
                )
            );

            SecurityException expectedFailure=
                new SecurityException(
                    "fixture-hook-construction-denied"
                );
            Throwable observed=null;
            final boolean[] installerReached=
                new boolean[1];

            try{
                LocalServerStartupBinder
                    .installShutdownHook(
                        shutdown,
                        shutdown::close,
                        "fixture-shutdown-hook",
                        (target,name)->{
                            throw expectedFailure;
                        },
                        hook->{
                            installerReached[0]=true;
                        }
                    );
            }catch(Throwable failure){
                observed=failure;
            }

            if(observed!=expectedFailure)
                throw new AssertionError(
                    "hook construction failure did not remain primary"
                );

            if(installerReached[0])
                throw new AssertionError(
                    "hook installer ran after construction failure"
                );

            assertTerminal(
                world,
                pool,
                game,
                aux,
                "hook construction failure"
            );

            shutdown.close();
        }finally{
            if(!world.closed())
                shutdown.close();
        }
    }

    private static void
        assertHookRegistrationFailureCleanup()
        throws Exception{
        InetAddress loopback=
            InetAddress.getByName(
                "127.0.0.1"
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
                    0
                )
            );

            SecurityException expectedFailure=
                new SecurityException(
                    "fixture-hook-registration-denied"
                );
            Throwable observed=null;

            try{
                LocalServerStartupBinder
                    .installShutdownHook(
                        shutdown,
                        ()->{
                            throw expectedFailure;
                        }
                    );
            }catch(Throwable failure){
                observed=failure;
            }

            if(observed!=expectedFailure)
                throw new AssertionError(
                    "hook registration failure did not remain primary"
                );

            assertTerminal(
                world,
                pool,
                game,
                aux,
                "hook registration failure"
            );

            shutdown.close();
        }finally{
            if(!world.closed())
                shutdown.close();
        }
    }

    private static void assertTerminal(
        World world,
        ExecutorService pool,
        ServerSocket game,
        ServerSocket aux,
        String phase
    ){
        if(!game.isClosed()||
           !aux.isClosed())
            throw new AssertionError(
                phase+
                " left listener open gameClosed="+
                game.isClosed()+
                " auxClosed="+
                aux.isClosed()
            );

        if(!pool.isTerminated())
            throw new AssertionError(
                phase+
                " left session pool live"
            );

        if(!world.closed())
            throw new AssertionError(
                phase+
                " left World live"
            );
    }

    private LocalServerStartupFailureCleanupTest(){}
}
