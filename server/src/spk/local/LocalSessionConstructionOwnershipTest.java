package spk.local;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
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
        assertFactoryFailureSuppressesUncheckedCloseFailure();
        assertConcurrentShutdown();
        assertExecutorRejectionRetainsOwnershipUntilClosed();
        assertCheckedCloseFailureRetainsOwnershipAndTerminalRetries();
        assertTerminalCloseFailurePublishedAndOwnershipRetained();
        assertTerminalFailOnceCloseFailureRemainsPublished();
        assertFailOnceGameListenerRetriedBeforeHandoffBarrier();
        assertFailOnceAuxListenerRetiredByOwnerRetry();
        assertResidualListenerRetriedByRepeatedClose();
        assertRecoveredListenerRejoinsAcceptHandoffBarrier();
        assertGameListenerCloseFailurePublishesWithoutHandoffHang();
        assertLateTerminalAcceptCloseFailureRetainsOwnership();
        assertAuxListenerCloseFailurePublished();
        assertUncheckedListenerCloseFailurePublished();
        assertAuxiliaryAcceptedSocketOwnership();
        assertAuxiliaryIdlePollKeepsOneHandoff();
        assertAuxiliaryTerminalAfterIdlePolls();
        assertAuxiliaryRealFailureAfterIdlePolls();
        assertAuxiliaryFailedOpenListenerPollRetiresWorker();
        assertAuxiliaryAcceptHandoffTerminalRace();
        assertSuccessPath();

        System.out.println(
            "LOCAL_SESSION_CONSTRUCTION_OWNERSHIP_PASS "+
            "claimedBeforeFactory=true "+
            "constructionFailurePrimary=true "+
            "uncheckedCloseSuppressed=true "+
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
            "terminalRetryDoesNotEraseFailure=true "+
            "repeatedCloseFailurePublished=true "+
            "listenerOwnerRetry=true "+
            "listenerRetryPreservesFailure=true "+
            "listenerRepeatedCloseRetry=true "+
            "recoveredListenerHandoffBarrier=true "+
            "recoveredListenerLateFailureDrained=true "+
            "gameListenerFailurePublished=true "+
            "gameListenerFailureNoHandoffHang=true "+
            "lateAcceptHandoffRetired=true "+
            "lateAcceptCloseFailureOwned=true "+
            "lateAcceptResidualRetry=true "+
            "lateAcceptFailureRetained=true "+
            "auxListenerFailurePublished=true "+
            "uncheckedListenerFailurePublished=true "+
            "auxSocketOwned=true "+
            "auxSocketCloseFailureRetained=true "+
            "auxSocketRetry=true "+
            "auxSocketOwnershipZero=true "+
            "auxAcceptHandoff=true "+
            "auxIdlePollHandoffContinuous=true "+
            "auxPollTimeoutNotDiagnostic=true "+
            "auxTerminalPollExit=true "+
            "auxHandoffRetiredExactlyOnce=true "+
            "auxRealAcceptFailurePreserved=true "+
            "auxFailedOpenPollExit=true "+
            "auxFailedOpenPoolTerminates=true "+
            "auxHandoffBoundedPool=true "+
            "auxPostFenceReject=true "+
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

    private static void
        assertFactoryFailureSuppressesUncheckedCloseFailure()
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
        FailOnceUncheckedCloseSocket socket=
            new FailOnceUncheckedCloseSocket();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        RuntimeException expected=
            new RuntimeException(
                "fixture-factory-primary"
            );
        Throwable observed=null;

        try{
            shutdown.submitSession(
                socket,
                (LocalServerShutdownCoordinator.SessionFactory)
                    ()->{
                        throw expected;
                    }
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "unchecked socket close replaced factory primary",
                observed
            );

        Throwable[] suppressed=
            observed.getSuppressed();

        if(suppressed.length!=1||
           suppressed[0]!=socket.failure)
            throw new AssertionError(
                "unchecked socket close was not suppressed behind factory primary"
            );

        if(socket.isClosed())
            throw new AssertionError(
                "unchecked fail-once socket unexpectedly closed"
            );

        if(shutdown.activeSessionCount()!=1)
            throw new AssertionError(
                "unchecked failed-open socket lost coordinator ownership"
            );

        shutdown.close();

        if(!socket.isClosed()||
           shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "terminal retry did not retire unchecked failed-open socket"
            );

        if(!world.closed())
            throw new AssertionError(
                "World did not close after unchecked socket retry"
            );
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

        long socketCloseDeadline=
            System.nanoTime()+
                TimeUnit.SECONDS.toNanos(
                    5
                );

        while(!socket.isClosed()&&
              System.nanoTime()<socketCloseDeadline)
            Thread.yield();

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
        assertTerminalFailOnceCloseFailureRemainsPublished()
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

        shutdown.acceptGameSocket(
            ()->socket
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
                socket.failure)
            throw new AssertionError(
                "first terminal socket close failure was erased by successful retry",
                observed
            );

        if(socket.closeCalls.get()<2||
           !socket.isClosed())
            throw new AssertionError(
                "terminal fail-once socket did not recover on retry"
            );

        if(shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "successful terminal retry did not retire socket ownership"
            );

        if(!world.closed())
            throw new AssertionError(
                "terminal fail-once close failure skipped World teardown"
            );

        Throwable repeated=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            repeated=failure;
        }

        if(repeated!=observed)
            throw new AssertionError(
                "repeated close did not preserve first terminal socket failure",
                repeated
            );
    }

    private static void
        assertFailOnceGameListenerRetriedBeforeHandoffBarrier()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        TrackingExecutor pool=
            new TrackingExecutor();
        FailCountCloseServerSocket game=
            new FailCountCloseServerSocket(
                1,
                "fixture-fail-once-game-listener"
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
        AtomicReference<Throwable> acceptFailure=
            new AtomicReference<>();

        Thread accepter=
            new Thread(
                ()->{
                    try{
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
                        );
                    }catch(Throwable failure){
                        acceptFailure.set(
                            failure
                        );
                    }
                },
                "fail-once-game-listener-accept"
            );
        accepter.start();

        if(!acceptEntered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "fail-once listener accept handoff did not publish"
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
                "fail-once-game-listener-close"
            );
        closer.start();

        long deadline=
            System.nanoTime()+
                TimeUnit.SECONDS.toNanos(
                    5
                );

        while(game.closeCalls.get()<2&&
              System.nanoTime()<deadline)
            Thread.yield();

        if(game.closeCalls.get()<2||
           !game.isClosed())
            throw new AssertionError(
                "owner did not retry and retire fail-once game listener"
            );

        if(!closer.isAlive())
            throw new AssertionError(
                "closed game listener bypassed pending accept handoff barrier"
            );

        releaseAccept.countDown();
        accepter.join(
            5_000L
        );
        closer.join(
            5_000L
        );

        if(accepter.isAlive()||
           closer.isAlive())
            throw new AssertionError(
                "fail-once listener fixture did not retire"
            );

        if(acceptFailure.get()!=null)
            throw new AssertionError(
                "fail-once listener accept handoff failed unexpectedly",
                acceptFailure.get()
            );

        Throwable observed=
            terminalFailure.get();

        if(!(observed instanceof
                IllegalStateException)||
           observed.getCause()!=
                game.failure)
            throw new AssertionError(
                "successful listener retry erased first close failure",
                observed
            );

        if(!accepted.isClosed()||
           shutdown.pendingGameAcceptHandoffs()!=0)
            throw new AssertionError(
                "fail-once listener handoff did not retire cleanly"
            );
    }

    private static void
        assertFailOnceAuxListenerRetiredByOwnerRetry()
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
        FailCountCloseServerSocket aux=
            new FailCountCloseServerSocket(
                1,
                "fixture-fail-once-aux-listener"
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
                "fail-once aux listener failure was not retained",
                observed
            );

        if(aux.closeCalls.get()<2||
           !aux.isClosed())
            throw new AssertionError(
                "owner did not retry and retire fail-once aux listener"
            );

        if(!world.closed())
            throw new AssertionError(
                "fail-once aux retry skipped World teardown"
            );
    }

    private static void
        assertResidualListenerRetriedByRepeatedClose()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        TrackingExecutor pool=
            new TrackingExecutor();
        FailCountCloseServerSocket game=
            new FailCountCloseServerSocket(
                2,
                "fixture-fail-twice-game-listener"
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

        Throwable first=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            first=failure;
        }

        if(!(first instanceof
                IllegalStateException)||
           first.getCause()!=
                game.failure)
            throw new AssertionError(
                "residual listener first failure was not published",
                first
            );

        if(game.isClosed()||
           game.closeCalls.get()!=2)
            throw new AssertionError(
                "owner retry did not leave expected residual listener"
            );

        Throwable repeated=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            repeated=failure;
        }

        if(repeated!=first)
            throw new AssertionError(
                "residual listener retry changed terminal failure identity",
                repeated
            );

        if(!game.isClosed()||
           game.closeCalls.get()<3)
            throw new AssertionError(
                "repeated close did not retire residual listener"
            );
    }

    private static void
        assertRecoveredListenerRejoinsAcceptHandoffBarrier()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        TrackingExecutor pool=
            new TrackingExecutor();
        FailCountCloseServerSocket game=
            new FailCountCloseServerSocket(
                2,
                "fixture-recovered-listener-handoff"
            );
        ServerSocket aux=
            new ServerSocket();
        FailOnceCloseSocket accepted=
            new FailOnceCloseSocket();

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
        AtomicReference<Throwable> acceptFailure=
            new AtomicReference<>();

        Thread accepter=
            new Thread(
                ()->{
                    try{
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
                        );
                    }catch(Throwable failure){
                        acceptFailure.set(
                            failure
                        );
                    }
                },
                "recovered-listener-handoff-accept"
            );
        accepter.start();

        if(!acceptEntered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "recovered-listener fixture did not publish accept handoff"
            );

        Throwable first=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            first=failure;
        }

        if(!(first instanceof
                IllegalStateException)||
           first.getCause()!=
                game.failure)
            throw new AssertionError(
                "owner did not publish fail-twice listener failure",
                first
            );

        if(game.isClosed()||
           game.closeCalls.get()!=2||
           shutdown.pendingGameAcceptHandoffs()!=1)
            throw new AssertionError(
                "owner did not leave expected recoverable listener/handoff state"
            );

        AtomicReference<Throwable> repeatedFailure=
            new AtomicReference<>();
        Thread repeated=
            new Thread(
                ()->{
                    try{
                        shutdown.close();
                    }catch(Throwable failure){
                        repeatedFailure.set(
                            failure
                        );
                    }
                },
                "recovered-listener-handoff-repeat"
            );
        repeated.start();

        long deadline=
            System.nanoTime()+
                TimeUnit.SECONDS.toNanos(
                    5
                );

        while(!game.isClosed()&&
              System.nanoTime()<deadline)
            Thread.yield();

        if(!game.isClosed()||
           game.closeCalls.get()<3)
            throw new AssertionError(
                "repeated close did not recover game listener"
            );

        if(!repeated.isAlive())
            throw new AssertionError(
                "repeated close returned before recovered accept handoff retired"
            );

        releaseAccept.countDown();
        accepter.join(
            5_000L
        );
        repeated.join(
            5_000L
        );

        if(accepter.isAlive()||
           repeated.isAlive())
            throw new AssertionError(
                "recovered listener handoff reconciliation did not finish"
            );

        if(acceptFailure.get()!=
                accepted.failure)
            throw new AssertionError(
                "late accepted socket close failure was not observed",
                acceptFailure.get()
            );

        if(repeatedFailure.get()!=first)
            throw new AssertionError(
                "recovered listener reconciliation changed terminal failure identity",
                repeatedFailure.get()
            );

        boolean lateFailureRetained=false;

        for(Throwable suppressed:
                first.getSuppressed())
            if(suppressed==
                    accepted.failure)
                lateFailureRetained=true;

        if(!lateFailureRetained)
            throw new AssertionError(
                "recovered listener did not drain late handoff failure in same close"
            );

        if(!accepted.isClosed()||
           accepted.closeCalls.get()<2)
            throw new AssertionError(
                "recovered listener did not retry late accepted socket"
            );

        if(shutdown.pendingGameAcceptHandoffs()!=0||
           shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "recovered listener handoff/socket ownership did not retire"
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
        assertLateTerminalAcceptCloseFailureRetainsOwnership()
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
                "fixture-late-accept-listener-close-failure"
            );
        ServerSocket aux=
            new ServerSocket();
        FailOnceCloseSocket accepted=
            new FailOnceCloseSocket();

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
        AtomicReference<Throwable> acceptFailure=
            new AtomicReference<>();

        Thread accepter=
            new Thread(
                ()->{
                    try{
                        shutdown.acceptGameSocket(
                            ()->{
                                acceptEntered.countDown();

                                try{
                                    releaseAccept.await();
                                }catch(InterruptedException error){
                                    Thread.currentThread()
                                        .interrupt();
                                    throw new IOException(
                                        "fixture late accept interrupted",
                                        error
                                    );
                                }

                                return accepted;
                            }
                        );
                    }catch(Throwable failure){
                        acceptFailure.set(
                            failure
                        );
                    }
                },
                "late-terminal-accept-close-failure-fixture"
            );
        accepter.start();

        if(!acceptEntered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "late terminal accept fixture did not publish handoff"
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
                game.failure)
            throw new AssertionError(
                "late accept fixture did not establish listener terminal failure",
                terminalFailure
            );

        if(shutdown.pendingGameAcceptHandoffs()!=1)
            throw new AssertionError(
                "terminal completion did not leave blocked accept handoff published"
            );

        releaseAccept.countDown();
        accepter.join(
            5_000L
        );

        if(accepter.isAlive())
            throw new AssertionError(
                "late failed-close accept handoff did not retire"
            );

        if(acceptFailure.get()!=
                accepted.failure)
            throw new AssertionError(
                "late accepted socket close failure was not observable",
                acceptFailure.get()
            );

        if(accepted.isClosed())
            throw new AssertionError(
                "late fail-once accepted socket unexpectedly closed"
            );

        if(shutdown.pendingGameAcceptHandoffs()!=0||
           shutdown.activeSessionCount()!=1)
            throw new AssertionError(
                "late failed-close accepted socket lost coordinator ownership"
            );

        Throwable repeated=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            repeated=failure;
        }

        if(repeated!=terminalFailure)
            throw new AssertionError(
                "residual retry changed the published terminal failure",
                repeated
            );

        if(!containsSuppressedIdentity(
                terminalFailure,
                accepted.failure))
            throw new AssertionError(
                "late accepted-socket close failure was not retained in terminal diagnostics"
            );

        if(!accepted.isClosed()||
           accepted.closeCalls.get()<2)
            throw new AssertionError(
                "repeated close did not retry and close residual accepted socket"
            );

        if(shutdown.activeSessionCount()!=0)
            throw new AssertionError(
                "residual accepted socket ownership did not retire after retry"
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

    private static void
        assertUncheckedListenerCloseFailurePublished()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        TrackingExecutor pool=
            new TrackingExecutor();
        AlwaysFailUncheckedServerSocket game=
            new AlwaysFailUncheckedServerSocket();
        ServerSocket aux=
            new ServerSocket();

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
                game.failure)
            throw new AssertionError(
                "unchecked listener close failure was not terminally published",
                observed
            );

        if(!world.closed())
            throw new AssertionError(
                "unchecked listener close failure skipped World teardown"
            );

        Throwable repeated=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            repeated=failure;
        }

        if(repeated!=observed)
            throw new AssertionError(
                "repeated close did not observe same unchecked listener failure",
                repeated
            );
    }

    private static void
        assertAuxiliaryAcceptedSocketOwnership()
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
        Socket healthy=
            new Socket();
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
            shutdown.acceptAuxiliarySocket(
                ()->healthy
            );

        if(accepted!=healthy||
           shutdown.activeAuxiliarySocketCount()!=1||
           shutdown.pendingAuxiliaryAcceptHandoffs()!=0)
            throw new AssertionError(
                "healthy auxiliary accept did not atomically transfer ownership"
            );

        shutdown.releaseAuxiliarySocket(
            healthy
        );

        if(!healthy.isClosed()||
           shutdown.activeAuxiliarySocketCount()!=0)
            throw new AssertionError(
                "healthy auxiliary socket ownership did not retire"
            );

        Socket terminalCandidate=
            shutdown.acceptAuxiliarySocket(
                ()->socket
            );

        if(terminalCandidate!=socket||
           shutdown.activeAuxiliarySocketCount()!=1)
            throw new AssertionError(
                "terminal fixture auxiliary socket was not owned"
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
                socket.failure)
            throw new AssertionError(
                "auxiliary socket close failure was not terminal evidence",
                observed
            );

        if(!socket.isClosed()||
           socket.closeCalls.get()<2)
            throw new AssertionError(
                "terminal auxiliary retry did not physically close socket"
            );

        if(shutdown.activeAuxiliarySocketCount()!=0||
           shutdown.pendingAuxiliaryAcceptHandoffs()!=0)
            throw new AssertionError(
                "terminal auxiliary ownership/handoff did not retire"
            );

        if(shutdown.acceptAuxiliarySocket(
                ()->new Socket()
            )!=null)
            throw new AssertionError(
                "post-fence auxiliary accept was not rejected"
            );

        Throwable repeated=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            repeated=failure;
        }

        if(repeated!=observed)
            throw new AssertionError(
                "repeated close changed auxiliary terminal failure identity",
                repeated
            );
    }

    private static void
        assertAuxiliaryIdlePollKeepsOneHandoff()
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
        Socket accepted=
            new Socket();
        AtomicInteger calls=
            new AtomicInteger();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        try{
            Socket observed=
                shutdown.acceptAuxiliarySocket(
                    ()->{
                        int call=
                            calls.incrementAndGet();

                        if(shutdown
                                .pendingAuxiliaryAcceptHandoffs()!=1)
                            throw new AssertionError(
                                "auxiliary idle poll released/reacquired handoff"
                            );

                        if(call<=2)
                            throw new SocketTimeoutException(
                                "fixture-aux-idle-poll-"+call
                            );

                        return accepted;
                    }
                );

            if(observed!=accepted||
               calls.get()!=3||
               shutdown.pendingAuxiliaryAcceptHandoffs()!=0||
               shutdown.activeAuxiliarySocketCount()!=1)
                throw new AssertionError(
                    "auxiliary idle polls did not preserve one handoff through accepted ownership"
                );

            shutdown.releaseAuxiliarySocket(
                accepted
            );

            if(!accepted.isClosed()||
               shutdown.activeAuxiliarySocketCount()!=0)
                throw new AssertionError(
                    "auxiliary idle-poll accepted socket did not retire"
                );
        }finally{
            shutdown.close();
        }
    }

    private static void
        assertAuxiliaryTerminalAfterIdlePolls()
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
        BlockingCloseServerSocket aux=
            new BlockingCloseServerSocket();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        CountDownLatch thirdAcceptEntered=
            new CountDownLatch(1);
        CountDownLatch releaseThirdPoll=
            new CountDownLatch(1);
        AtomicInteger calls=
            new AtomicInteger();
        AtomicReference<Socket> result=
            new AtomicReference<>();
        AtomicReference<Throwable> acceptFailure=
            new AtomicReference<>();

        Thread accepter=
            new Thread(
                ()->{
                    try{
                        result.set(
                            shutdown
                                .acceptAuxiliarySocket(
                                    ()->{
                                        int call=
                                            calls.incrementAndGet();

                                        if(shutdown
                                                .pendingAuxiliaryAcceptHandoffs()!=1)
                                            throw new AssertionError(
                                                "terminal AUX poll fixture lost continuous handoff"
                                            );

                                        if(call<=2)
                                            throw new SocketTimeoutException(
                                                "fixture-aux-terminal-idle-"+call
                                            );

                                        thirdAcceptEntered
                                            .countDown();

                                        try{
                                            releaseThirdPoll
                                                .await();
                                        }catch(InterruptedException error){
                                            Thread.currentThread()
                                                .interrupt();
                                            throw new IOException(
                                                "fixture terminal AUX poll interrupted",
                                                error
                                            );
                                        }

                                        throw new SocketTimeoutException(
                                            "fixture-aux-terminal-exit"
                                        );
                                    }
                                )
                        );
                    }catch(Throwable failure){
                        acceptFailure.set(
                            failure
                        );
                    }
                },
                "aux-terminal-after-idle-polls"
            );
        accepter.start();

        if(!thirdAcceptEntered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "terminal AUX poll fixture did not complete healthy idle polls"
            );

        if(calls.get()!=3||
           shutdown.pendingAuxiliaryAcceptHandoffs()!=1)
            throw new AssertionError(
                "healthy AUX polls did not retain exactly one handoff"
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
                "aux-terminal-after-idle-close"
            );
        closer.start();

        try{
            if(!aux.closeEntered.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "terminal AUX poll fixture did not publish closing fence"
                );

            if(!shutdown.closing()||
               shutdown.pendingAuxiliaryAcceptHandoffs()!=1)
                throw new AssertionError(
                    "terminal fence observed false zero-handoff gap before AUX poll"
                );

            releaseThirdPoll.countDown();

            accepter.join(
                5_000L
            );

            if(accepter.isAlive())
                throw new AssertionError(
                    "terminal AUX poll did not release in-flight accept"
                );

            if(result.get()!=null||
               acceptFailure.get()!=null||
               shutdown.pendingAuxiliaryAcceptHandoffs()!=0)
                throw new AssertionError(
                    "terminal AUX poll escaped timeout/failure or retained handoff",
                    acceptFailure.get()
                );

            aux.releaseClose.countDown();

            closer.join(
                5_000L
            );

            if(closer.isAlive())
                throw new AssertionError(
                    "terminal AUX poll close fixture did not converge"
                );

            if(terminalFailure.get()!=null)
                throw new AssertionError(
                    "clean terminal AUX poll fabricated terminal failure",
                    terminalFailure.get()
                );

            if(calls.get()!=3||
               shutdown.activeAuxiliarySocketCount()!=0)
                throw new AssertionError(
                    "terminal AUX poll committed socket or repeated after terminal exit"
                );
        }finally{
            releaseThirdPoll.countDown();
            aux.releaseClose.countDown();

            accepter.join(
                1_000L
            );
            closer.join(
                1_000L
            );
        }
    }

    private static void
        assertAuxiliaryRealFailureAfterIdlePolls()
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
        IOException expected=
            new IOException(
                "fixture-aux-real-accept-failure-after-polls"
            );
        AtomicInteger calls=
            new AtomicInteger();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        Throwable observed=null;

        try{
            try{
                shutdown.acceptAuxiliarySocket(
                    ()->{
                        int call=
                            calls.incrementAndGet();

                        if(shutdown
                                .pendingAuxiliaryAcceptHandoffs()!=1)
                            throw new AssertionError(
                                "auxiliary real-failure fixture lost handoff across idle poll"
                            );

                        if(call<=2)
                            throw new SocketTimeoutException(
                                "fixture-aux-real-failure-idle-"+call
                            );

                        throw expected;
                    }
                );
            }catch(Throwable failure){
                observed=failure;
            }

            if(observed!=expected||
               calls.get()!=3||
               shutdown.pendingAuxiliaryAcceptHandoffs()!=0||
               expected.getSuppressed().length!=0)
                throw new AssertionError(
                    "real auxiliary accept IOException changed or retained idle poll diagnostics",
                    observed
                );
        }finally{
            shutdown.close();
        }
    }

    private static void
        assertAuxiliaryFailedOpenListenerPollRetiresWorker()
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
            Executors.newSingleThreadExecutor();
        ServerSocket game=
            new ServerSocket();
        FailCountCloseServerSocket aux=
            new FailCountCloseServerSocket(
                2,
                "fixture-aux-failed-open-poll-listener"
            );

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

            CountDownLatch acceptStarted=
                new CountDownLatch(1);

            if(!shutdown.submitAuxiliary(
                    ()->{
                        acceptStarted.countDown();

                        try{
                            Socket socket=
                                shutdown
                                    .acceptAuxiliarySocket();

                            if(socket!=null)
                                throw new AssertionError(
                                    "terminal auxiliary poll returned a live socket"
                                );
                        }catch(IOException failure){
                            throw new RuntimeException(
                                failure
                            );
                        }
                    }))
                throw new AssertionError(
                    "failed-open auxiliary poll worker was not submitted"
                );

            if(!acceptStarted.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "failed-open auxiliary worker did not enter accept"
                );

            long handoffDeadline=
                System.nanoTime()+
                TimeUnit.SECONDS.toNanos(
                    5
                );

            while(shutdown
                        .pendingAuxiliaryAcceptHandoffs()!=1&&
                  System.nanoTime()<
                        handoffDeadline)
                Thread.yield();

            if(shutdown
                    .pendingAuxiliaryAcceptHandoffs()!=1)
                throw new AssertionError(
                    "failed-open auxiliary accept handoff did not publish"
                );

            Throwable first=null;

            try{
                shutdown.close();
            }catch(Throwable failure){
                first=failure;
            }

            if(!(first instanceof
                    IllegalStateException)||
               first.getCause()!=
                    aux.failure)
                throw new AssertionError(
                    "failed-open auxiliary listener failure was not terminal primary",
                    first
                );

            if(!pool.isTerminated())
                throw new AssertionError(
                    "bounded auxiliary accept poll did not let executor terminate"
                );

            if(shutdown
                    .pendingAuxiliaryAcceptHandoffs()!=0)
                throw new AssertionError(
                    "terminal auxiliary poll did not retire handoff exactly once"
                );

            if(containsThrowableMessage(
                    first,
                    "session executor did not terminate after forced shutdown")||
               containsThrowableMessage(
                    first,
                    "auxiliary accept handoff unresolved count="))
                throw new AssertionError(
                    "failed-open auxiliary listener fabricated pool/handoff nontermination despite bounded poll",
                    first
                );

            if(aux.isClosed()||
               aux.closeCalls.get()!=2)
                throw new AssertionError(
                    "fixture did not preserve failed-open listener after bounded owner retries"
                );

            Throwable repeated=null;

            try{
                shutdown.close();
            }catch(Throwable failure){
                repeated=failure;
            }

            if(repeated!=first)
                throw new AssertionError(
                    "failed-open auxiliary listener recovery changed terminal failure identity",
                    repeated
                );

            if(!aux.isClosed()||
               aux.closeCalls.get()<3)
                throw new AssertionError(
                    "repeated close did not physically retire recovered auxiliary listener"
                );
        }finally{
            if(!aux.isClosed())
                try{
                    aux.close();
                }catch(Throwable ignored){
                }

            if(!world.closed())
                try{
                    shutdown.close();
                }catch(Throwable ignored){
                }
        }
    }

    private static void
        assertAuxiliaryAcceptHandoffTerminalRace()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        NonTerminatingExecutor pool=
            new NonTerminatingExecutor();
        ServerSocket game=
            new ServerSocket();
        ServerSocket aux=
            new ServerSocket();
        FailOnceCloseSocket accepted=
            new FailOnceCloseSocket();

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
                            shutdown.acceptAuxiliarySocket(
                                ()->{
                                    acceptEntered.countDown();

                                    try{
                                        releaseAccept.await();
                                    }catch(InterruptedException error){
                                        Thread.currentThread()
                                            .interrupt();
                                        throw new IOException(
                                            "fixture auxiliary accept interrupted",
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
                "auxiliary-accept-handoff-fixture"
            );
        accepter.start();

        if(!acceptEntered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "auxiliary accept handoff did not publish before accept"
            );

        if(shutdown.pendingAuxiliaryAcceptHandoffs()!=1||
           shutdown.activeAuxiliarySocketCount()!=0)
            throw new AssertionError(
                "auxiliary pre-accept handoff state mismatch"
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
                "auxiliary-handoff-terminal-fixture"
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
                "auxiliary handoff bypassed bounded pool shutdown"
            );
        }

        Throwable first=
            terminalFailure.get();

        if(first==null)
            throw new AssertionError(
                "unresolved auxiliary handoff allowed clean terminal completion"
            );

        if(!pool.shutdown||
           !pool.shutdownNow)
            throw new AssertionError(
                "terminal close did not reach bounded pool shutdown policy"
            );

        if(!containsThrowableMessage(
                first,
                "session executor did not terminate after forced shutdown"))
            throw new AssertionError(
                "pool nontermination was not terminal evidence",
                first
            );

        if(!containsThrowableMessage(
                first,
                "auxiliary accept handoff unresolved count=1"))
            throw new AssertionError(
                "unresolved auxiliary handoff was not terminal evidence",
                first
            );

        if(shutdown.pendingAuxiliaryAcceptHandoffs()!=1||
           shutdown.activeAuxiliarySocketCount()!=0)
            throw new AssertionError(
                "terminal publication corrupted unresolved auxiliary handoff state"
            );

        releaseAccept.countDown();

        accepter.join(
            5_000L
        );

        if(accepter.isAlive())
            throw new AssertionError(
                "released auxiliary handoff did not retire"
            );

        if(acceptResult.get()!=null)
            throw new AssertionError(
                "terminal-winning auxiliary accept returned live socket"
            );

        if(acceptFailure.get()!=
                accepted.failure)
            throw new AssertionError(
                "terminal auxiliary handoff close failure was not observable",
                acceptFailure.get()
            );

        if(shutdown.pendingAuxiliaryAcceptHandoffs()!=0||
           shutdown.activeAuxiliarySocketCount()!=1||
           accepted.isClosed())
            throw new AssertionError(
                "late auxiliary handoff failure did not retain socket ownership"
            );

        Throwable repeated=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            repeated=failure;
        }

        if(repeated!=first)
            throw new AssertionError(
                "auxiliary recovery changed published terminal failure identity",
                repeated
            );

        if(!accepted.isClosed()||
           accepted.closeCalls.get()<2||
           shutdown.pendingAuxiliaryAcceptHandoffs()!=0||
           shutdown.activeAuxiliarySocketCount()!=0)
            throw new AssertionError(
                "repeated close did not reconcile late auxiliary socket ownership"
            );

        if(!containsSuppressedIdentity(
                first,
                accepted.failure))
            throw new AssertionError(
                "late auxiliary socket failure was not retained diagnostically"
            );
    }

    private static boolean containsThrowableMessage(
        Throwable failure,
        String text
    ){
        if(failure==null)
            return false;

        String message=
            failure.getMessage();

        if(message!=null&&
           message.contains(
               text
           ))
            return true;

        if(containsThrowableMessage(
                failure.getCause(),
                text))
            return true;

        for(Throwable suppressed:
                failure.getSuppressed())
            if(containsThrowableMessage(
                    suppressed,
                    text))
                return true;

        return false;
    }

    private static boolean containsSuppressedIdentity(
        Throwable failure,
        Throwable expected
    ){
        if(failure==null)
            return false;

        for(Throwable suppressed:
                failure.getSuppressed()){
            if(suppressed==expected)
                return true;

            if(containsSuppressedIdentity(
                    suppressed,
                    expected))
                return true;
        }

        return containsSuppressedIdentity(
            failure.getCause(),
            expected
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

    private static final class BlockingCloseServerSocket
        extends ServerSocket {

        final CountDownLatch closeEntered=
            new CountDownLatch(1);
        final CountDownLatch releaseClose=
            new CountDownLatch(1);

        BlockingCloseServerSocket()
            throws IOException{
            super();
        }

        @Override public void close()
            throws IOException{
            closeEntered.countDown();

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

    private static final class AlwaysFailUncheckedServerSocket
        extends ServerSocket {

        final RuntimeException failure=
            new RuntimeException(
                "fixture-unchecked-listener-close-failure"
            );

        AlwaysFailUncheckedServerSocket()
            throws IOException{
            super();
        }

        @Override public void close(){
            throw failure;
        }
    }

    private static final class FailOnceUncheckedCloseSocket
        extends Socket {

        final RuntimeException failure=
            new RuntimeException(
                "fixture-unchecked-socket-close-failure"
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

    private static final class FailCountCloseServerSocket
        extends ServerSocket {

        final IOException failure;
        final int failures;
        final AtomicInteger closeCalls=
            new AtomicInteger();

        FailCountCloseServerSocket(
            int failures,
            String message
        )throws IOException{
            super();

            if(failures<0)
                throw new IllegalArgumentException(
                    "failures"
                );

            this.failures=failures;
            failure=
                new IOException(
                    message
                );
        }

        @Override public void close()
            throws IOException{
            if(closeCalls.incrementAndGet()<=
                    failures)
                throw failure;

            super.close();
        }
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

    private static final class NonTerminatingExecutor
        extends AbstractExecutorService {

        boolean shutdown;
        boolean shutdownNow;

        @Override public void shutdown(){
            shutdown=true;
        }

        @Override public List<Runnable> shutdownNow(){
            shutdown=true;
            shutdownNow=true;
            return Collections.emptyList();
        }

        @Override public boolean isShutdown(){
            return shutdown;
        }

        @Override public boolean isTerminated(){
            return false;
        }

        @Override public boolean awaitTermination(
            long timeout,
            TimeUnit unit
        ){
            return false;
        }

        @Override public void execute(
            Runnable command
        ){
            throw new AssertionError(
                "fixture does not execute auxiliary accept through pool"
            );
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
