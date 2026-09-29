package spk.local;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalServerStartupFailureCleanupTest {
    public static void main(
        String[] args
    )throws Exception{
        assertPoolConstructionFailureCleanup();
        assertListenerConstructionFailureCleanup();
        assertPreCoordinatorSocketCloseFailureSuppressed();
        assertBindFailureCleanup();
        assertGameAcceptPollConfigurationFailureCleanup();
        assertAuxiliaryAcceptPollConfigurationFailureCleanup();
        assertPostBindSetupFailureCleanup();
        assertHookConstructionFailureCleanup();
        assertHookRegistrationFailureCleanup();

        System.out.println(
            "LOCAL_SERVER_STARTUP_FAILURE_CLEANUP_PASS "+
            "poolConstructionFailure=true "+
            "poolFailurePrimary=true "+
            "listenerFactoryNotReached=true "+
            "listenerConstructionFailure=true "+
            "partialConstructedSocketClosed=true "+
            "preCoordinatorCloseFailureSuppressed=true "+
            "gameBoundBeforeAuxFailure=true "+
            "bindFailurePrimary=true "+
            "gameAcceptPollConfigured=true "+
            "gameAcceptPollConfigurationFailure=true "+
            "auxAcceptPollConfigured=true "+
            "auxAcceptPollConfigurationFailure=true "+
            "gameClosed=true "+
            "auxClosed=true "+
            "poolTerminated=true "+
            "worldClosed=true "+
            "unrelatedBlockerOpen=true "+
            "postBindSetupFailure=true "+
            "postBindSetupFailurePrimary=true "+
            "hookConstructionFailure=true "+
            "hookConstructionFailurePrimary=true "+
            "hookRegistrationFailure=true "+
            "hookFailurePrimary=true "+
            "repeatedCloseSafe=true"
        );
    }

    private static void
        assertPoolConstructionFailureCleanup()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        SecurityException expectedFailure=
            new SecurityException(
                "fixture-pool-construction-failure"
            );
        Throwable observed=null;
        final int[] listenerCalls=
            new int[1];

        try{
            LocalServerStartupBinder.prepare(
                world,
                ()->{
                    throw expectedFailure;
                },
                ()->{
                    listenerCalls[0]++;
                    return new ServerSocket();
                }
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expectedFailure)
            throw new AssertionError(
                "pool construction failure did not remain primary"
            );

        if(listenerCalls[0]!=0)
            throw new AssertionError(
                "listener factory ran after pool acquisition failure"
            );

        if(!world.closed())
            throw new AssertionError(
                "pool construction failure left World live"
            );
    }

    private static void
        assertPreCoordinatorSocketCloseFailureSuppressed()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        ExecutorService pool=
            Executors.newCachedThreadPool();

        IOException closeFailure=
            new IOException(
                "fixture-listener-close-failure"
            );
        FailingCloseServerSocket[] first=
            new FailingCloseServerSocket[1];
        IOException expectedFailure=
            new IOException(
                "fixture-second-listener-construction-failure"
            );
        final int[] calls=
            new int[1];
        Throwable observed=null;

        try{
            LocalServerStartupBinder.prepare(
                world,
                pool,
                ()->{
                    calls[0]++;

                    if(calls[0]==1){
                        first[0]=
                            new FailingCloseServerSocket(
                                closeFailure
                            );
                        return first[0];
                    }

                    throw expectedFailure;
                }
            );
        }catch(Throwable failure){
            observed=failure;
        }finally{
            if(first[0]!=null)
                first[0].forceClose();
        }

        if(observed!=expectedFailure)
            throw new AssertionError(
                "listener construction failure did not remain primary when close also failed"
            );

        boolean suppressed=false;

        for(Throwable failure:
                observed.getSuppressed())
            if(failure==closeFailure)
                suppressed=true;

        if(!suppressed)
            throw new AssertionError(
                "listener close failure was not attached as suppressed"
            );

        if(!pool.isTerminated())
            throw new AssertionError(
                "socket-close cleanup failure skipped pool termination"
            );

        if(!world.closed())
            throw new AssertionError(
                "socket-close cleanup failure skipped World close"
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
        assertGameAcceptPollConfigurationFailureCleanup()
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
        SocketException expectedFailure=
            new SocketException(
                "fixture-game-accept-poll-configuration-failure"
            );
        PollConfigurationFailServerSocket game=
            new PollConfigurationFailServerSocket(
                expectedFailure
            );
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
            Throwable observed=null;

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
            }catch(Throwable failure){
                observed=failure;
            }

            if(observed!=expectedFailure)
                throw new AssertionError(
                    "game accept poll configuration failure did not remain primary",
                    observed
                );

            if(!game.boundWhenConfigured)
                throw new AssertionError(
                    "game accept poll was configured before successful listener bind"
                );

            assertTerminal(
                world,
                pool,
                game,
                aux,
                "game accept poll configuration failure"
            );

            shutdown.close();
        }finally{
            if(!world.closed())
                shutdown.close();
        }
    }

    private static void
        assertAuxiliaryAcceptPollConfigurationFailureCleanup()
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
        SocketException expectedFailure=
            new SocketException(
                "fixture-aux-accept-poll-configuration-failure"
            );
        PollRecordingServerSocket game=
            new PollRecordingServerSocket();
        PollConfigurationFailServerSocket aux=
            new PollConfigurationFailServerSocket(
                expectedFailure
            );

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        try{
            Throwable observed=null;

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
            }catch(Throwable failure){
                observed=failure;
            }

            if(observed!=expectedFailure)
                throw new AssertionError(
                    "auxiliary accept poll configuration failure did not remain primary",
                    observed
                );

            if(!aux.boundWhenConfigured)
                throw new AssertionError(
                    "auxiliary accept poll was configured before successful listener bind"
                );

            if(game.configuredTimeout!=
                    LocalServerShutdownCoordinator
                        .GAME_ACCEPT_POLL_TIMEOUT_MILLIS)
                throw new AssertionError(
                    "auxiliary poll failure occurred before game poll configuration"
                );

            assertTerminal(
                world,
                pool,
                game,
                aux,
                "auxiliary accept poll configuration failure"
            );

            shutdown.close();
        }finally{
            if(!world.closed())
                shutdown.close();
        }
    }

    private static void
        assertPostBindSetupFailureCleanup()
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

            if(game.getSoTimeout()!=
                    LocalServerShutdownCoordinator
                        .GAME_ACCEPT_POLL_TIMEOUT_MILLIS)
                throw new AssertionError(
                    "game listener bounded accept poll was not configured"
                );

            if(aux.getSoTimeout()!=
                    LocalServerShutdownCoordinator
                        .AUXILIARY_ACCEPT_POLL_TIMEOUT_MILLIS)
                throw new AssertionError(
                    "auxiliary listener bounded accept poll was not configured"
                );

            ExceptionInInitializerError expectedFailure=
                new ExceptionInInitializerError(
                    "fixture-post-bind-catalog-init-failure"
                );
            Throwable observed=null;

            try{
                LocalServerStartupBinder
                    .runBoundSetup(
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
                    "post-bind setup failure did not remain primary"
                );

            assertTerminal(
                world,
                pool,
                game,
                aux,
                "post-bind setup failure"
            );

            shutdown.close();
        }finally{
            if(!world.closed())
                shutdown.close();
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
                        shutdown::close,
                        "fixture-shutdown-hook",
                        (target,name)->
                            new Thread(
                                target,
                                name
                            ),
                        hook->{
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

    private static final class PollRecordingServerSocket
        extends ServerSocket {

        private int configuredTimeout=-1;

        PollRecordingServerSocket()
        throws IOException{}

        @Override public void setSoTimeout(
            int timeout
        )throws SocketException{
            super.setSoTimeout(
                timeout
            );
            configuredTimeout=timeout;
        }
    }

    private static final class PollConfigurationFailServerSocket
        extends ServerSocket {

        private final SocketException failure;
        private boolean boundWhenConfigured;

        PollConfigurationFailServerSocket(
            SocketException failure
        )throws IOException{
            this.failure=failure;
        }

        @Override public void setSoTimeout(
            int timeout
        )throws SocketException{
            boundWhenConfigured=isBound();
            throw failure;
        }
    }

    private static final class FailingCloseServerSocket
        extends ServerSocket {

        private final IOException failure;
        private boolean fail=true;

        FailingCloseServerSocket(
            IOException failure
        )throws IOException{
            this.failure=failure;
        }

        @Override public void close()
            throws IOException{
            if(fail)
                throw failure;

            super.close();
        }

        void forceClose()
            throws IOException{
            fail=false;
            super.close();
        }
    }

    private LocalServerStartupFailureCleanupTest(){}
}
