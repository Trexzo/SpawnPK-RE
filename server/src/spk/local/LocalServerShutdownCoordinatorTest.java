package spk.local;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocalServerShutdownCoordinatorTest {
    public static void main(String[] args)throws Exception{
        InetAddress loopback=
            InetAddress.getByName(
                "127.0.0.1"
            );

        World world=
            World.isolatedForTest(
                60_000L
            );

        ExecutorService pool=
            Executors.newCachedThreadPool();

        ServerSocket game=
            new ServerSocket();

        ServerSocket aux=
            new ServerSocket();

        Socket client=null;
        Socket accepted=null;

        LocalServerShutdownCoordinator shutdown=null;

        try{
            game.bind(
                new InetSocketAddress(
                    loopback,
                    0
                )
            );

            aux.bind(
                new InetSocketAddress(
                    loopback,
                    0
                )
            );

            client=
                new Socket(
                    loopback,
                    game.getLocalPort()
                );

            accepted=
                game.accept();

            shutdown=
                new LocalServerShutdownCoordinator(
                    world,
                    pool,
                    game,
                    aux
                );

            CountDownLatch auxEntered=
                new CountDownLatch(1);

            if(!shutdown.submitAuxiliary(
                    ()->{
                        auxEntered.countDown();

                        try{
                            aux.accept();
                            throw new AssertionError(
                                "aux accept unexpectedly succeeded"
                            );
                        }catch(SocketException expected){
                            if(!aux.isClosed())
                                throw new AssertionError(
                                    "aux accept failed before shutdown close",
                                    expected
                                );
                        }catch(IOException error){
                            throw new RuntimeException(
                                error
                            );
                        }
                    }))
                throw new AssertionError(
                    "auxiliary task rejected before shutdown"
                );

            if(!auxEntered.await(
                    2,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "auxiliary accept task did not start"
                );

            CountDownLatch sessionEntered=
                new CountDownLatch(1);
            CountDownLatch cleanupRan=
                new CountDownLatch(1);
            AtomicBoolean worldOpenDuringCleanup=
                new AtomicBoolean();

            final Socket sessionSocket=
                accepted;

            if(!shutdown.submitSession(
                    sessionSocket,
                    ()->{
                        sessionEntered.countDown();

                        try{
                            sessionSocket
                                .getInputStream()
                                .read();

                            throw new AssertionError(
                                "session read unexpectedly reached EOF without close"
                            );
                        }catch(SocketException expected){
                            // Active game socket must be closed by shutdown.
                        }catch(IOException error){
                            if(!sessionSocket.isClosed())
                                throw new RuntimeException(
                                    error
                                );
                        }finally{
                            try{
                                world.start();
                                worldOpenDuringCleanup.set(
                                    true
                                );
                            }catch(IllegalStateException closedTooEarly){
                                worldOpenDuringCleanup.set(
                                    false
                                );
                            }finally{
                                cleanupRan.countDown();
                            }
                        }
                    }))
                throw new AssertionError(
                    "session rejected before shutdown"
                );

            if(!sessionEntered.await(
                    2,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "session task did not start"
                );

            if(shutdown.activeSessionCount()!=1)
                throw new AssertionError(
                    "active session tracking mismatch before shutdown="+
                    shutdown.activeSessionCount()
                );

            shutdown.close();

            if(!cleanupRan.await(
                    1,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "session cleanup did not run"
                );

            if(!worldOpenDuringCleanup.get())
                throw new AssertionError(
                    "World closed before session cleanup"
                );

            if(!game.isClosed()||
               !aux.isClosed())
                throw new AssertionError(
                    "listener sockets not closed game="+
                    game.isClosed()+
                    " aux="+
                    aux.isClosed()
                );

            if(!sessionSocket.isClosed())
                throw new AssertionError(
                    "active game socket not closed"
                );

            if(!pool.isTerminated())
                throw new AssertionError(
                    "session pool did not terminate"
                );

            if(shutdown.activeSessionCount()!=0)
                throw new AssertionError(
                    "active session tracking not empty="+
                    shutdown.activeSessionCount()
                );

            Socket rejected=
                new Socket();

            boolean acceptedAfterClose=
                shutdown.submitSession(
                    rejected,
                    ()->{}
                );

            if(acceptedAfterClose)
                throw new AssertionError(
                    "post-shutdown session submission accepted"
                );

            if(!rejected.isClosed())
                throw new AssertionError(
                    "post-shutdown rejected socket not closed"
                );

            boolean worldClosed=false;

            try{
                world.start();
            }catch(IllegalStateException expected){
                worldClosed=true;
            }

            if(!worldClosed)
                throw new AssertionError(
                    "World not closed after coordinator shutdown"
                );

            shutdown.close();

            if(!pool.isTerminated()||
               shutdown.activeSessionCount()!=0)
                throw new AssertionError(
                    "idempotent shutdown changed terminal state"
                );

            System.out.println(
                "LOCAL_SERVER_SHUTDOWN_COORDINATOR_PASS "+
                "activeSocketClosed=true "+
                "auxUnblocked=true "+
                "sessionCleanupBeforeWorldClose=true "+
                "poolTerminated=true "+
                "postShutdownRejected=true "+
                "worldClosed=true "+
                "idempotent=true"
            );
        }finally{
            if(client!=null)
                try{
                    client.close();
                }catch(IOException ignored){
                }

            if(accepted!=null)
                try{
                    accepted.close();
                }catch(IOException ignored){
                }

            if(shutdown!=null)
                shutdown.close();
            else{
                try{
                    game.close();
                }catch(IOException ignored){
                }

                try{
                    aux.close();
                }catch(IOException ignored){
                }

                pool.shutdownNow();
                world.close();
            }
        }
    }

    private LocalServerShutdownCoordinatorTest(){}
}
