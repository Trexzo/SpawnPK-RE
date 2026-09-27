package spk.local;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalSessionConstructionOwnershipTest {
    public static void main(
        String[] args
    )throws Exception{
        assertConstructionFailure();
        assertConcurrentShutdown();
        assertSuccessPath();

        System.out.println(
            "LOCAL_SESSION_CONSTRUCTION_OWNERSHIP_PASS "+
            "claimedBeforeFactory=true "+
            "constructionFailurePrimary=true "+
            "socketClosed=true "+
            "activeZero=true "+
            "noTaskOnFailure=true "+
            "concurrentShutdownOwnsSocket=true "+
            "postFenceSubmit=false "+
            "successPath=true"
        );
    }

    private static void assertConstructionFailure()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        TrackingExecutor pool=
            new TrackingExecutor();
        ServerSocket game=
            new ServerSocket();
        ServerSocket aux=
            new ServerSocket();
        Socket socket=
            new Socket();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        RuntimeException expected=
            new RuntimeException(
                "fixture-session-construction-failure"
            );
        Throwable observed=null;
        final boolean[] claimed=
            new boolean[1];

        try{
            shutdown.submitSession(
                socket,
                (LocalServerShutdownCoordinator.SessionFactory)
                    ()->{
                        claimed[0]=
                            shutdown.activeSessionCount()==1;

                        throw expected;
                    }
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(!claimed[0])
            throw new AssertionError(
                "accepted socket was not claimed before factory execution"
            );

        if(observed!=expected)
            throw new AssertionError(
                "session construction failure did not remain primary"
            );

        if(!socket.isClosed())
            throw new AssertionError(
                "session construction failure left accepted socket open"
            );

        if(shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "session construction failure retained active socket ownership"
            );

        if(pool.executeCalls!=0)
            throw new AssertionError(
                "failed session construction reached executor"
            );

        shutdown.close();
    }

    private static void assertConcurrentShutdown()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        TrackingExecutor pool=
            new TrackingExecutor();
        ServerSocket game=
            new ServerSocket();
        ServerSocket aux=
            new ServerSocket();
        Socket socket=
            new Socket();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        CountDownLatch factoryEntered=
            new CountDownLatch(1);
        CountDownLatch releaseFactory=
            new CountDownLatch(1);
        AtomicBoolean taskRan=
            new AtomicBoolean();
        AtomicReference<Boolean> submitted=
            new AtomicReference<>();
        AtomicReference<Throwable> submitFailure=
            new AtomicReference<>();

        Thread submitter=
            new Thread(
                ()->{
                    try{
                        submitted.set(
                            shutdown.submitSession(
                                socket,
                                (LocalServerShutdownCoordinator.SessionFactory)
                                    ()->{
                                        if(shutdown.activeSessionCount()!=1)
                                            throw new AssertionError(
                                                "socket not claimed while factory blocked"
                                            );

                                        factoryEntered.countDown();

                                        if(!releaseFactory.await(
                                                5,
                                                TimeUnit.SECONDS))
                                            throw new AssertionError(
                                                "fixture factory release timed out"
                                            );

                                        return ()->
                                            taskRan.set(
                                                true
                                            );
                                    }
                            )
                        );
                    }catch(Throwable failure){
                        submitFailure.set(
                            failure
                        );
                    }
                },
                "session-construction-fixture"
            );
        submitter.start();

        if(!factoryEntered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "session factory did not enter"
            );

        Thread closer=
            new Thread(
                shutdown::close,
                "session-construction-close-fixture"
            );
        closer.start();
        closer.join(
            5_000L
        );

        if(closer.isAlive())
            throw new AssertionError(
                "shutdown waited for blocked session factory"
            );

        if(!socket.isClosed())
            throw new AssertionError(
                "concurrent shutdown did not close claimed accepted socket"
            );

        releaseFactory.countDown();
        submitter.join(
            5_000L
        );

        if(submitter.isAlive())
            throw new AssertionError(
                "session submitter did not leave after shutdown fence"
            );

        if(submitFailure.get()!=null)
            throw new AssertionError(
                "post-fence session factory completion failed",
                submitFailure.get()
            );

        if(!Boolean.FALSE.equals(
                submitted.get()))
            throw new AssertionError(
                "session was submitted after shutdown fence"
            );

        if(taskRan.get())
            throw new AssertionError(
                "session task ran after shutdown fence"
            );

        if(pool.executeCalls!=0)
            throw new AssertionError(
                "post-fence session reached executor"
            );

        if(shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "post-fence factory completion retained active socket"
            );
    }

    private static void assertSuccessPath()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        ExecutorService pool=
            Executors.newSingleThreadExecutor();
        ServerSocket game=
            new ServerSocket();
        ServerSocket aux=
            new ServerSocket();
        Socket socket=
            new Socket();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        CountDownLatch ran=
            new CountDownLatch(1);
        final boolean[] claimed=
            new boolean[1];

        boolean submitted=
            shutdown.submitSession(
                socket,
                (LocalServerShutdownCoordinator.SessionFactory)
                    ()->{
                        claimed[0]=
                            shutdown.activeSessionCount()==1;

                        return ran::countDown;
                    }
            );

        if(!submitted||
           !claimed[0])
            throw new AssertionError(
                "healthy session was not claimed/submitted"
            );

        if(!ran.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "healthy session did not execute"
            );

        long deadline=
            System.nanoTime()+
                TimeUnit.SECONDS.toNanos(
                    5
                );

        while(shutdown.activeSessionCount()!=0&&
              System.nanoTime()<deadline)
            Thread.yield();

        if(shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "healthy session retained active socket ownership"
            );

        if(!socket.isClosed())
            throw new AssertionError(
                "healthy session did not close accepted socket"
            );

        shutdown.close();
    }

    private static final class TrackingExecutor
        extends AbstractExecutorService {

        int executeCalls;
        boolean shutdown;

        @Override public void shutdown(){
            shutdown=true;
        }

        @Override public List<Runnable> shutdownNow(){
            shutdown=true;
            return Collections.emptyList();
        }

        @Override public boolean isShutdown(){
            return shutdown;
        }

        @Override public boolean isTerminated(){
            return shutdown;
        }

        @Override public boolean awaitTermination(
            long timeout,
            TimeUnit unit
        ){
            return shutdown;
        }

        @Override public void execute(
            Runnable command
        ){
            executeCalls++;
            throw new AssertionError(
                "executor must not receive failed session construction"
            );
        }
    }

    private LocalSessionConstructionOwnershipTest(){}
}
