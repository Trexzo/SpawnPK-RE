package spk.local;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalSessionConstructionOwnershipTest {
    public static void main(
        String[] args
    )throws Exception{
        assertShutdownWinsAcceptedHandoff();
        assertRejectedSocketRetainsOwnershipUntilClosed();
        assertConstructionFailure();
        assertConcurrentShutdown();
        assertExecutorRejectionRetainsOwnershipUntilClosed();
        assertCheckedCloseFailureRetainsOwnershipAndTerminalRetries();
        assertTerminalCloseFailurePublishedAndOwnershipRetained();
        assertGameListenerCloseFailurePublishesWithoutHandoffHang();
        assertAuxListenerCloseFailurePublished();
        assertSuccessPath();

        System.out.println(
            "LOCAL_SESSION_CONSTRUCTION_OWNERSHIP_PASS "+
            "claimedBeforeFactory=true "+
            "constructionFailurePrimary=true "+
            "socketClosed=true "+
            "activeZero=true "+
            "noTaskOnFailure=true "+
            "concurrentShutdownOwnsSocket=true "+
            "factoryRetirementBarrier=true "+
            "worldOpenUntilFactoryRetires=true "+
            "postFenceSubmit=false "+
            "handoffRejectCloses=true "+
            "retireOwnershipUntilClosed=true "+
            "executorRejectRetirement=true "+
            "terminalCloseLockFree=true "+
            "checkedCloseFailureRetained=true "+
            "terminalCloseRetry=true "+
            "terminalCloseFailurePublished=true "+
            "repeatedCloseFailurePublished=true "+
            "gameListenerFailurePublished=true "+
            "gameListenerFailureNoHandoffHang=true "+
            "lateAcceptHandoffRetired=true "+
            "auxListenerFailurePublished=true "+
            "successPath=true"
        );
    }

    private static void
        assertShutdownWinsAcceptedHandoff()
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
        SocketPair pair=
            SocketPair.open();
        Socket accepted=
            pair.accepted;

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        CountDownLatch acceptorHasSocket=
            new CountDownLatch(1);
        CountDownLatch releaseHandoff=
            new CountDownLatch(1);
        CountDownLatch closeReturned=
            new CountDownLatch(1);
        AtomicReference<Socket> returned=
            new AtomicReference<>();
        AtomicReference<Throwable> acceptFailure=
            new AtomicReference<>();

        Thread accepter=
            new Thread(
                ()->{
                    try{
                        returned.set(
                            shutdown.acceptGameSocket(
                                ()->{
                                    acceptorHasSocket.countDown();

                                    try{
                                        if(!releaseHandoff.await(
                                                5,
                                                TimeUnit.SECONDS))
                                            throw new IOException(
                                                "fixture accept handoff release timed out"
                                            );
                                    }catch(InterruptedException error){
                                        Thread.currentThread()
                                            .interrupt();
                                        throw new IOException(
                                            "fixture accept handoff interrupted",
                                            error
                                        );
                                    }

                                    return accepted;
                                }
                            )
                        );
                    }catch(Throwable failure){
                        acceptFailure.set(
                            failure
                        );
                    }
                },
                "accepted-handoff-fixture"
            );
        accepter.start();

        if(!acceptorHasSocket.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "fixture acceptor did not acquire socket"
            );

        if(shutdown.pendingGameAcceptHandoffs()!=1)
            throw new AssertionError(
                "accepted socket handoff was not coordinator-owned"
            );

        Thread closer=
            new Thread(
                ()->{
                    try{
                        shutdown.close();
                    }finally{
                        closeReturned.countDown();
                    }
                },
                "accepted-handoff-close-fixture"
            );
        closer.start();

        long deadline=
            System.nanoTime()+
                TimeUnit.SECONDS.toNanos(
                    5
                );

        while(!shutdown.closing()&&
              System.nanoTime()<deadline)
            Thread.yield();

        if(!shutdown.closing())
            throw new AssertionError(
                "shutdown fence did not publish"
            );

        if(closeReturned.getCount()==0)
            throw new AssertionError(
                "terminal close returned before accepted handoff retired"
            );

        releaseHandoff.countDown();

        accepter.join(
            5_000L
        );
        closer.join(
            5_000L
        );

        if(accepter.isAlive()||
           closer.isAlive())
            throw new AssertionError(
                "accepted handoff race did not terminate"
            );

        if(acceptFailure.get()!=null)
            throw new AssertionError(
                "accepted handoff failed",
                acceptFailure.get()
            );

        if(returned.get()!=null)
            throw new AssertionError(
                "shutdown-winning accept handoff returned a live socket"
            );

        if(!accepted.isClosed())
            throw new AssertionError(
                "shutdown-winning accepted socket was not closed"
            );

        if(shutdown.pendingGameAcceptHandoffs()!=0||
           shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "accepted handoff ownership survived terminal close"
            );

        if(closeReturned.getCount()!=0)
            throw new AssertionError(
                "terminal close did not finish after handoff retirement"
            );

        pair.close();
    }

    private static void
        assertRejectedSocketRetainsOwnershipUntilClosed()
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
        BlockingCloseSocket socket=
            new BlockingCloseSocket();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        // Seed ownership through the same accepted-socket handoff used by Main.
        Socket accepted=
            shutdown.acceptGameSocket(
                ()->socket
            );

        if(accepted!=socket||
           shutdown.activeSessionCount()!=1)
            throw new AssertionError(
                "fixture rejected socket was not coordinator-owned"
            );

        AtomicReference<Throwable> rejectFailure=
            new AtomicReference<>();

        Thread rejecter=
            new Thread(
                ()->{
                    try{
                        shutdown.rejectSessionSocket(
                            socket
                        );
                    }catch(Throwable failure){
                        rejectFailure.set(
                            failure
                        );
                    }
                },
                "rejected-socket-retire-fixture"
            );
        rejecter.start();

        if(!socket.closeEntered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "rejected socket close did not enter"
            );

        if(shutdown.activeSessionCount()!=1)
            throw new AssertionError(
                "socket ownership retired before close completed"
            );

        CountDownLatch terminalReturned=
            new CountDownLatch(1);
        Thread closer=
            new Thread(
                ()->{
                    try{
                        shutdown.close();
                    }finally{
                        terminalReturned.countDown();
                    }
                },
                "rejected-socket-terminal-fixture"
            );
        closer.start();

        if(!socket.terminalCloseEntered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "terminal close did not reach the owned socket"
            );

        AtomicReference<Boolean> closingObserved=
            new AtomicReference<>();
        Thread observer=
            new Thread(
                ()->closingObserved.set(
                    shutdown.closing()
                ),
                "terminal-lock-observer-fixture"
            );
        observer.start();
        observer.join(
            1_000L
        );

        if(observer.isAlive()){
            socket.releaseClose.countDown();
            observer.join(
                5_000L
            );
            throw new AssertionError(
                "terminal socket close held lifecycle lock"
            );
        }

        if(!Boolean.TRUE.equals(
                closingObserved.get()))
            throw new AssertionError(
                "terminal fence was not observable while socket close was blocked"
            );

        if(terminalReturned.getCount()==0)
            throw new AssertionError(
                "terminal close returned while rejected socket close was blocked"
            );

        socket.releaseClose.countDown();

        rejecter.join(
            5_000L
        );
        closer.join(
            5_000L
        );

        if(rejecter.isAlive()||
           closer.isAlive())
            throw new AssertionError(
                "rejected socket retirement race did not terminate"
            );

        if(rejectFailure.get()!=null)
            throw new AssertionError(
                "successful rejected-socket close unexpectedly failed",
                rejectFailure.get()
            );

        if(shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "rejected socket ownership survived completed close"
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
        SocketPair pair=
            SocketPair.open();
        Socket socket=
            pair.accepted;

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
        pair.close();
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
        SocketPair pair=
            SocketPair.open();
        Socket socket=
            pair.accepted;

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

        CountDownLatch closeReturned=
            new CountDownLatch(1);
        Thread closer=
            new Thread(
                ()->{
                    try{
                        shutdown.close();
                    }finally{
                        closeReturned.countDown();
                    }
                },
                "session-construction-close-fixture"
            );
        closer.start();

        long closeDeadline=
            System.nanoTime()+
                TimeUnit.SECONDS.toNanos(
                    5
                );

        while(!shutdown.closing()&&
              System.nanoTime()<closeDeadline)
            Thread.yield();

        if(!shutdown.closing())
            throw new AssertionError(
                "shutdown fence did not publish while factory was blocked"
            );

        if(!socket.isClosed())
            throw new AssertionError(
                "concurrent shutdown did not close claimed accepted socket"
            );

        if(shutdown.pendingSessionFactoryHandoffs()!=1)
            throw new AssertionError(
                "blocked session factory was not terminally accounted"
            );

        if(closeReturned.getCount()==0||
           !closer.isAlive())
            throw new AssertionError(
                "terminal close returned before session factory retired"
            );

        if(world.closed())
            throw new AssertionError(
                "World closed before in-flight session factory retired"
            );

        releaseFactory.countDown();

        submitter.join(
            5_000L
        );
        closer.join(
            5_000L
        );

        if(submitter.isAlive()||
           closer.isAlive())
            throw new AssertionError(
                "session factory/terminal close did not retire together"
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

        if(shutdown.activeSessionCount()!=0||
           shutdown.pendingSessionFactoryHandoffs()!=0)
            throw new AssertionError(
                "post-fence factory completion retained ownership"
            );

        if(!world.closed())
            throw new AssertionError(
                "World did not close after session factory retired"
            );

        pair.close();
    }

    private static void
        assertExecutorRejectionRetainsOwnershipUntilClosed()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        RejectingExecutor pool=
            new RejectingExecutor();
        ServerSocket game=
            new ServerSocket();
        ServerSocket aux=
            new ServerSocket();
        BlockingCloseSocket socket=
            new BlockingCloseSocket();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        RuntimeException expected=
            pool.rejection;
        AtomicReference<Throwable> observed=
            new AtomicReference<>();

        Thread submitter=
            new Thread(
                ()->{
                    try{
                        shutdown.submitSession(
                            socket,
                            (LocalServerShutdownCoordinator.SessionFactory)
                                ()->()->{}
                        );
                    }catch(Throwable failure){
                        observed.set(
                            failure
                        );
                    }
                },
                "executor-rejection-retire-fixture"
            );
        submitter.start();

        if(!socket.closeEntered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "executor rejection did not enter socket close"
            );

        if(shutdown.activeSessionCount()!=1)
            throw new AssertionError(
                "executor rejection retired ownership before close completed"
            );

        if(observed.get()!=null)
            throw new AssertionError(
                "executor rejection escaped before socket close completed"
            );

        socket.releaseClose.countDown();

        submitter.join(
            5_000L
        );

        if(submitter.isAlive())
            throw new AssertionError(
                "executor rejection retirement did not finish"
            );

        if(observed.get()!=expected)
            throw new AssertionError(
                "executor rejection did not remain primary",
                observed.get()
            );

        if(shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "executor rejection retained socket ownership"
            );

        if(!socket.isClosed())
            throw new AssertionError(
                "executor rejection left socket open"
            );

        shutdown.close();
    }

    private static void
        assertCheckedCloseFailureRetainsOwnershipAndTerminalRetries()
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
        FailOnceCloseSocket socket=
            new FailOnceCloseSocket();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        Socket accepted=
            shutdown.acceptGameSocket(
                ()->socket
            );

        if(accepted!=socket||
           shutdown.activeSessionCount()!=1)
            throw new AssertionError(
                "fail-once socket was not coordinator-owned"
            );

        IOException observed=null;

        try{
            shutdown.rejectSessionSocket(
                socket
            );
        }catch(IOException failure){
            observed=failure;
        }

        if(observed!=socket.failure)
            throw new AssertionError(
                "checked socket close failure was not observable"
            );

        if(socket.isClosed())
            throw new AssertionError(
                "fail-once socket unexpectedly closed on failed attempt"
            );

        if(shutdown.activeSessionCount()!=1)
            throw new AssertionError(
                "checked close failure silently retired open socket"
            );

        shutdown.close();

        if(socket.closeCalls.get()<2||
           !socket.isClosed())
            throw new AssertionError(
                "terminal close did not retry and close retained socket"
            );

        if(shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "ownership did not retire after successful terminal retry"
            );

        if(!world.closed())
            throw new AssertionError(
                "World did not close after successful socket retry"
            );
    }

    private static void
        assertTerminalCloseFailurePublishedAndOwnershipRetained()
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
        AlwaysFailCloseSocket socket=
            new AlwaysFailCloseSocket();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        Socket accepted=
            shutdown.acceptGameSocket(
                ()->socket
            );

        if(accepted!=socket||
           shutdown.activeSessionCount()!=1)
            throw new AssertionError(
                "always-fail socket was not coordinator-owned"
            );

        Throwable terminalFailure=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            terminalFailure=failure;
        }

        if(!(terminalFailure instanceof
                IllegalStateException)||
           terminalFailure.getCause()!=
                socket.failure)
            throw new AssertionError(
                "terminal socket-close failure was not published with exact cause",
                terminalFailure
            );

        if(socket.closeCalls.get()<2)
            throw new AssertionError(
                "terminal close did not retry still-owned socket"
            );

        if(socket.isClosed())
            throw new AssertionError(
                "always-fail socket unexpectedly became closed"
            );

        if(shutdown.activeSessionCount()!=1)
            throw new AssertionError(
                "terminal close failure silently retired open socket"
            );

        if(!world.closed())
            throw new AssertionError(
                "socket retirement failure skipped World teardown"
            );

        Throwable repeatedFailure=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            repeatedFailure=failure;
        }

        if(repeatedFailure!=terminalFailure)
            throw new AssertionError(
                "repeated close did not publish the same terminal failure",
                repeatedFailure
            );

        if(shutdown.activeSessionCount()!=1)
            throw new AssertionError(
                "repeated failed close changed retained ownership"
            );
    }

    private static void
        assertGameListenerCloseFailurePublishesWithoutHandoffHang()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        TrackingExecutor pool=
            new TrackingExecutor();
        AlwaysFailCloseServerSocket game=
            new AlwaysFailCloseServerSocket(
                "fixture-game-listener-close-failure"
            );
        ServerSocket aux=
            new ServerSocket();
        Socket accepted=
            new Socket();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        CountDownLatch acceptEntered=
            new CountDownLatch(1);
        CountDownLatch releaseAccept=
            new CountDownLatch(1);
        AtomicReference<Socket> acceptResult=
            new AtomicReference<>();
        AtomicReference<Throwable> acceptFailure=
            new AtomicReference<>();

        Thread accepter=
            new Thread(
                ()->{
                    try{
                        acceptResult.set(
                            shutdown.acceptGameSocket(
                                ()->{
                                    acceptEntered.countDown();

                                    try{
                                        releaseAccept.await();
                                    }catch(InterruptedException error){
                                        Thread.currentThread()
                                            .interrupt();
                                        throw new IOException(
                                            "fixture accept interrupted",
                                            error
                                        );
                                    }

                                    return accepted;
                                }
                            )
                        );
                    }catch(Throwable failure){
                        acceptFailure.set(
                            failure
                        );
                    }
                },
                "listener-close-failure-accept-fixture"
            );
        accepter.start();

        if(!acceptEntered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "listener-failure accept handoff did not start"
            );

        if(shutdown.pendingGameAcceptHandoffs()!=1)
            throw new AssertionError(
                "listener-failure accept handoff was not published"
            );

        AtomicReference<Throwable> terminalFailure=
            new AtomicReference<>();
        Thread closer=
            new Thread(
                ()->{
                    try{
                        shutdown.close();
                    }catch(Throwable failure){
                        terminalFailure.set(
                            failure
                        );
                    }
                },
                "listener-close-failure-terminal-fixture"
            );
        closer.start();
        closer.join(
            5_000L
        );

        if(closer.isAlive()){
            releaseAccept.countDown();
            accepter.join(
                5_000L
            );
            throw new AssertionError(
                "failed-open game listener stranded terminal close on accept handoff"
            );
        }

        Throwable observed=
            terminalFailure.get();

        if(!(observed instanceof
                IllegalStateException)||
           observed.getCause()!=
                game.failure)
            throw new AssertionError(
                "game listener close failure was not terminal primary",
                observed
            );

        if(!world.closed())
            throw new AssertionError(
                "game listener close failure skipped World teardown"
            );

        if(shutdown.pendingGameAcceptHandoffs()!=1)
            throw new AssertionError(
                "terminal failure corrupted still-blocked accept bookkeeping"
            );

        Throwable repeated=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            repeated=failure;
        }

        if(repeated!=observed)
            throw new AssertionError(
                "repeated close did not observe same game-listener terminal failure",
                repeated
            );

        releaseAccept.countDown();

        accepter.join(
            5_000L
        );

        if(accepter.isAlive())
            throw new AssertionError(
                "released late accept handoff did not retire"
            );

        if(acceptFailure.get()!=null)
            throw new AssertionError(
                "late accepted socket close unexpectedly failed",
                acceptFailure.get()
            );

        if(acceptResult.get()!=null)
            throw new AssertionError(
                "late terminal accept returned live socket"
            );

        if(!accepted.isClosed())
            throw new AssertionError(
                "late terminal accepted socket remained open"
            );

        if(shutdown.pendingGameAcceptHandoffs()!=0||
           shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "late accept handoff bookkeeping did not retire"
            );
    }

    private static void
        assertAuxListenerCloseFailurePublished()
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
        AlwaysFailCloseServerSocket aux=
            new AlwaysFailCloseServerSocket(
                "fixture-aux-listener-close-failure"
            );

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        Throwable observed=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            observed=failure;
        }

        if(!(observed instanceof
                IllegalStateException)||
           observed.getCause()!=
                aux.failure)
            throw new AssertionError(
                "aux listener close failure was not published",
                observed
            );

        if(!world.closed())
            throw new AssertionError(
                "aux listener close failure skipped World teardown"
            );

        Throwable repeated=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            repeated=failure;
        }

        if(repeated!=observed)
            throw new AssertionError(
                "repeated close did not observe same aux-listener failure",
                repeated
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
        SocketPair pair=
            SocketPair.open();
        Socket socket=
            pair.accepted;

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
        pair.close();
    }

    private static final class AlwaysFailCloseServerSocket
        extends ServerSocket {

        final IOException failure;
        final AtomicInteger closeCalls=
            new AtomicInteger();

        AlwaysFailCloseServerSocket(
            String message
        )throws IOException{
            super();
            failure=
                new IOException(
                    message
                );
        }

        @Override public void close()
            throws IOException{
            closeCalls.incrementAndGet();
            throw failure;
        }
    }

    private static final class FailOnceCloseSocket
        extends Socket {

        final IOException failure=
            new IOException(
                "fixture-first-socket-close-failure"
            );
        final AtomicInteger closeCalls=
            new AtomicInteger();

        @Override public void close()
            throws IOException{
            if(closeCalls.incrementAndGet()==1)
                throw failure;

            super.close();
        }
    }

    private static final class AlwaysFailCloseSocket
        extends Socket {

        final IOException failure=
            new IOException(
                "fixture-terminal-socket-close-failure"
            );
        final AtomicInteger closeCalls=
            new AtomicInteger();

        @Override public void close()
            throws IOException{
            closeCalls.incrementAndGet();
            throw failure;
        }
    }

    private static final class BlockingCloseSocket
        extends Socket {

        final CountDownLatch closeEntered=
            new CountDownLatch(1);
        final CountDownLatch terminalCloseEntered=
            new CountDownLatch(1);
        final CountDownLatch releaseClose=
            new CountDownLatch(1);
        final AtomicInteger closeCalls=
            new AtomicInteger();

        @Override public void close()
            throws IOException{
            int call=
                closeCalls.incrementAndGet();

            closeEntered.countDown();

            if(call>=2)
                terminalCloseEntered.countDown();

            boolean interrupted=false;

            for(;;)
                try{
                    releaseClose.await();
                    break;
                }catch(InterruptedException error){
                    interrupted=true;
                }

            try{
                super.close();
            }finally{
                if(interrupted)
                    Thread.currentThread()
                        .interrupt();
            }
        }
    }

    private static final class SocketPair
        implements AutoCloseable {

        final Socket client;
        final Socket accepted;

        private SocketPair(
            Socket client,
            Socket accepted
        ){
            this.client=client;
            this.accepted=accepted;
        }

        static SocketPair open()
            throws Exception{
            InetAddress loopback=
                InetAddress.getByName(
                    "127.0.0.1"
                );

            try(ServerSocket listener=
                    new ServerSocket()){
                listener.bind(
                    new InetSocketAddress(
                        loopback,
                        0
                    )
                );

                Socket client=
                    new Socket(
                        loopback,
                        listener.getLocalPort()
                    );
                Socket accepted=
                    listener.accept();

                return new SocketPair(
                    client,
                    accepted
                );
            }
        }

        @Override public void close()
            throws Exception{
            try{
                accepted.close();
            }finally{
                client.close();
            }
        }
    }

    private static final class RejectingExecutor
        extends AbstractExecutorService {

        final RejectedExecutionException rejection=
            new RejectedExecutionException(
                "fixture-executor-rejection"
            );
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
            throw rejection;
        }
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
