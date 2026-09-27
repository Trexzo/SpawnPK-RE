package spk.local;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalAuxiliaryWorkerFailureTest {
    public static void main(String[] args)
        throws Exception{
        assertPendingFailureFailsFreshAccept();
        assertAcceptedHandoffCannotCommitAfterFailure();
        assertAcceptedHandoffFailedOpenRemainsOwned();
        assertAcceptedHandoffOwnershipWhileCloseBlocked();
        assertWakeFailureSuppressedAndRetried();
        assertFailedOpenWakeStillBlocksServing();
        assertIdlePollKeepsOneAcceptHandoff();
        assertTerminalPollRetiresOneAcceptHandoff();
        assertRealAcceptFailureAfterIdlePolls();
        assertFailedOpenWakePollsInFlightAccept();
        assertDuplicateFailureKeepsFirstPrimary();
        assertExactErrorTaskBoundaryIdentity();
        assertPrePublicationLateFailureJoinsFirstTerminalResult();
        assertLatePostTerminalFailurePreservesPublishedIdentity();
        assertCleanAuxiliaryExitDoesNotFabricateFailure();

        System.out.println(
            "LOCAL_AUXILIARY_WORKER_FAILURE_PASS "+
            "caughtAtTaskBoundary=true "+
            "freshAcceptFailsFast=true "+
            "acceptedHandoffRejected=true "+
            "failedOpenAcceptedSocketRetained=true "+
            "blockedCloseOwnershipContinuous=true "+
            "workerFailureIdentity=true "+
            "wakeCloseAttempted=true "+
            "wakeCloseRetry=true "+
            "wakeFailureSuppressed=true "+
            "failedOpenStillAuthoritative=true "+
            "idlePollHandoffContinuous=true "+
            "terminalPollExit=true "+
            "realAcceptFailurePreserved=true "+
            "pollTimeoutNotDiagnostic=true "+
            "handoffRetiredExactlyOnce=true "+
            "failedOpenInFlightAcceptBounded=true "+
            "duplicateKeepsFirst=true "+
            "exactErrorIdentity=true "+
            "prePublicationLateFailureIncluded=true "+
            "postTerminalEvidenceReconciled=true "+
            "terminalIdentityStable=true "+
            "cleanExitNoFailure=true"
        );
    }

    private static void assertPendingFailureFailsFreshAccept()
        throws Exception{
        Fixture fixture=
            new Fixture(
                new TrackingServerSocket(0)
            );
        RuntimeException expected=
            new RuntimeException(
                "fixture-aux-worker-pending"
            );

        try{
            fixture.shutdown.submitAuxiliary(
                ()->{ throw expected; }
            );

            await(
                ()->fixture.game.closeCalls>=1,
                "aux worker failure did not attempt game-listener wake"
            );

            AtomicBoolean acceptCalled=
                new AtomicBoolean();
            Throwable observed=null;

            try{
                fixture.shutdown.acceptGameSocket(
                    ()->{
                        acceptCalled.set(true);
                        return new TrackingSocket();
                    }
                );
            }catch(Throwable failure){
                observed=failure;
            }

            if(observed!=expected)
                throw new AssertionError(
                    "pending worker failure identity changed",
                    observed
                );

            if(acceptCalled.get())
                throw new AssertionError(
                    "fresh game accept blocked/called after worker failure was pending"
                );
        }finally{
            fixture.close();
        }
    }

    private static void assertAcceptedHandoffCannotCommitAfterFailure()
        throws Exception{
        Fixture fixture=
            new Fixture(
                new TrackingServerSocket(0)
            );
        RuntimeException expected=
            new RuntimeException(
                "fixture-aux-worker-race"
            );
        Object lock=new Object();
        AtomicBoolean acceptEntered=
            new AtomicBoolean();
        AtomicBoolean releaseAccept=
            new AtomicBoolean();
        TrackingSocket accepted=
            new TrackingSocket();
        AtomicReference<Throwable> observed=
            new AtomicReference<>();

        Thread acceptThread=
            new Thread(
                ()->{
                    try{
                        fixture.shutdown.acceptGameSocket(
                            ()->{
                                acceptEntered.set(true);

                                synchronized(lock){
                                    while(!releaseAccept.get())
                                        try{
                                            lock.wait();
                                        }catch(InterruptedException error){
                                            Thread.currentThread().interrupt();
                                            throw new IOException(
                                                error
                                            );
                                        }
                                }

                                return accepted;
                            }
                        );
                    }catch(Throwable failure){
                        observed.set(failure);
                    }
                },
                "aux-worker-race-accept"
            );

        try{
            acceptThread.start();

            await(
                acceptEntered::get,
                "game accept did not enter injected handoff"
            );

            fixture.shutdown.submitAuxiliary(
                ()->{ throw expected; }
            );

            await(
                ()->fixture.game.closeCalls>=1,
                "worker failure did not wake game listener during accept race"
            );

            synchronized(lock){
                releaseAccept.set(true);
                lock.notifyAll();
            }

            acceptThread.join(5_000L);

            if(acceptThread.isAlive())
                throw new AssertionError(
                    "game accept race did not terminate"
                );

            if(observed.get()!=expected)
                throw new AssertionError(
                    "accepted handoff did not surface exact worker failure",
                    observed.get()
                );

            if(!accepted.isClosed())
                throw new AssertionError(
                    "accepted socket committed after worker failure"
                );

            if(fixture.shutdown.activeSessionCount()!=0)
                throw new AssertionError(
                    "rejected accepted socket retained active ownership"
                );
        }finally{
            synchronized(lock){
                releaseAccept.set(true);
                lock.notifyAll();
            }
            fixture.close();
        }
    }

    private static void assertAcceptedHandoffFailedOpenRemainsOwned()
        throws Exception{
        Fixture fixture=
            new Fixture(
                new TrackingServerSocket(0)
            );
        RuntimeException expected=
            new RuntimeException(
                "fixture-aux-worker-race-failed-open"
            );
        Object lock=new Object();
        AtomicBoolean acceptEntered=
            new AtomicBoolean();
        AtomicBoolean releaseAccept=
            new AtomicBoolean();
        FailOnceTrackingSocket accepted=
            new FailOnceTrackingSocket();
        AtomicReference<Throwable> observed=
            new AtomicReference<>();

        Thread acceptThread=
            new Thread(
                ()->{
                    try{
                        fixture.shutdown.acceptGameSocket(
                            ()->{
                                acceptEntered.set(true);

                                synchronized(lock){
                                    while(!releaseAccept.get())
                                        try{
                                            lock.wait();
                                        }catch(InterruptedException error){
                                            Thread.currentThread().interrupt();
                                            throw new IOException(
                                                error
                                            );
                                        }
                                }

                                return accepted;
                            }
                        );
                    }catch(Throwable failure){
                        observed.set(
                            failure
                        );
                    }
                },
                "aux-worker-race-failed-open"
            );

        try{
            acceptThread.start();

            await(
                acceptEntered::get,
                "failed-open game accept did not enter handoff"
            );

            fixture.shutdown.submitAuxiliary(
                ()->{ throw expected; }
            );

            await(
                ()->fixture.game.closeCalls>=1,
                "failed-open race did not publish AUX failure"
            );

            synchronized(lock){
                releaseAccept.set(true);
                lock.notifyAll();
            }

            acceptThread.join(
                5_000L
            );

            if(acceptThread.isAlive())
                throw new AssertionError(
                    "failed-open accepted handoff did not terminate"
                );

            if(observed.get()!=expected)
                throw new AssertionError(
                    "failed-open accepted handoff lost worker primary",
                    observed.get()
                );

            if(accepted.isClosed())
                throw new AssertionError(
                    "fail-once accepted socket unexpectedly closed on first retirement"
                );

            if(fixture.shutdown.activeSessionCount()!=1)
                throw new AssertionError(
                    "failed-open accepted socket lost coordinator ownership"
                );

            if(!containsIdentity(
                    expected.getSuppressed(),
                    accepted.failure))
                throw new AssertionError(
                    "accepted-socket close failure was not subordinate to worker primary"
                );

            fixture.shutdown.close();

            if(!accepted.isClosed()||
               fixture.shutdown.activeSessionCount()!=0)
                throw new AssertionError(
                    "terminal retry did not retire failed-open accepted socket"
                );
        }finally{
            synchronized(lock){
                releaseAccept.set(true);
                lock.notifyAll();
            }

            fixture.close();
        }
    }

    private static void assertAcceptedHandoffOwnershipWhileCloseBlocked()
        throws Exception{
        Fixture fixture=
            new Fixture(
                new TrackingServerSocket(0)
            );
        RuntimeException expected=
            new RuntimeException(
                "fixture-aux-worker-blocked-close"
            );
        Object acceptLock=
            new Object();
        AtomicBoolean acceptEntered=
            new AtomicBoolean();
        AtomicBoolean releaseAccept=
            new AtomicBoolean();
        BlockingFirstCloseSocket accepted=
            new BlockingFirstCloseSocket();
        AtomicReference<Throwable> acceptFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> terminalFailure=
            new AtomicReference<>();

        Thread acceptThread=
            new Thread(
                ()->{
                    try{
                        fixture.shutdown.acceptGameSocket(
                            ()->{
                                acceptEntered.set(true);

                                synchronized(acceptLock){
                                    while(!releaseAccept.get())
                                        try{
                                            acceptLock.wait();
                                        }catch(InterruptedException error){
                                            Thread.currentThread().interrupt();
                                            throw new IOException(
                                                error
                                            );
                                        }
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
                "aux-worker-blocked-close-accept"
            );

        Thread terminalThread=
            new Thread(
                ()->{
                    try{
                        fixture.shutdown.close();
                    }catch(Throwable failure){
                        terminalFailure.set(
                            failure
                        );
                    }
                },
                "aux-worker-blocked-close-terminal"
            );

        try{
            acceptThread.start();

            await(
                acceptEntered::get,
                "blocking-close game accept did not enter handoff"
            );

            fixture.shutdown.submitAuxiliary(
                ()->{ throw expected; }
            );

            await(
                ()->fixture.game.closeCalls>=1,
                "blocking-close AUX failure did not publish"
            );

            synchronized(acceptLock){
                releaseAccept.set(true);
                acceptLock.notifyAll();
            }

            if(!accepted.firstCloseEntered.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "worker-winning accepted socket did not enter physical close"
                );

            if(fixture.shutdown.pendingGameAcceptHandoffs()!=0)
                throw new AssertionError(
                    "game accept handoff remained published while socket close was blocked"
                );

            if(fixture.shutdown.activeSessionCount()!=1)
                throw new AssertionError(
                    "blocked physical close created accepted-socket ownership gap"
                );

            terminalThread.start();

            await(
                fixture.shutdown::closing,
                "terminal owner did not observe shutdown during blocked accepted close"
            );

            if(!terminalThread.isAlive()||
               fixture.shutdown.activeSessionCount()!=1)
                throw new AssertionError(
                    "terminal owner did not remain coordinated with blocked owned socket"
                );

            accepted.releaseFirstClose.countDown();

            acceptThread.join(
                5_000L
            );
            terminalThread.join(
                5_000L
            );

            if(acceptThread.isAlive()||
               terminalThread.isAlive())
                throw new AssertionError(
                    "blocked accepted-socket ownership fixture did not converge"
                );

            if(acceptFailure.get()!=expected)
                throw new AssertionError(
                    "blocked accepted close lost exact worker primary",
                    acceptFailure.get()
                );

            if(terminalFailure.get()!=null)
                throw new AssertionError(
                    "terminal retry unexpectedly failed",
                    terminalFailure.get()
                );

            if(!accepted.isClosed()||
               fixture.shutdown.activeSessionCount()!=0)
                throw new AssertionError(
                    "blocked accepted socket did not retire without ownership leak"
                );
        }finally{
            synchronized(acceptLock){
                releaseAccept.set(true);
                acceptLock.notifyAll();
            }
            accepted.releaseFirstClose.countDown();

            acceptThread.join(
                1_000L
            );
            terminalThread.join(
                1_000L
            );
            fixture.close();
        }
    }

    private static void assertWakeFailureSuppressedAndRetried()
        throws Exception{
        TrackingServerSocket game=
            new TrackingServerSocket(1);
        Fixture fixture=
            new Fixture(game);
        RuntimeException expected=
            new RuntimeException(
                "fixture-aux-worker-wake-retry"
            );

        try{
            fixture.shutdown.submitAuxiliary(
                ()->{ throw expected; }
            );

            await(
                ()->game.closeCalls>=2,
                "fail-once game listener was not retried"
            );

            if(!game.isClosed())
                throw new AssertionError(
                    "fail-once wake listener did not close on retry"
                );

            Throwable observed=null;

            try{
                fixture.shutdown.acceptGameSocket(
                    ()->new TrackingSocket()
                );
            }catch(Throwable failure){
                observed=failure;
            }

            if(observed!=expected)
                throw new AssertionError(
                    "wake retry replaced worker primary",
                    observed
                );

            if(!containsIdentity(
                    expected.getSuppressed(),
                    game.failure))
                throw new AssertionError(
                    "wake close failure not suppressed behind worker primary"
                );
        }finally{
            fixture.close();
        }
    }

    private static void assertFailedOpenWakeStillBlocksServing()
        throws Exception{
        TrackingServerSocket game=
            new TrackingServerSocket(
                Integer.MAX_VALUE
            );
        Fixture fixture=
            new Fixture(game);
        RuntimeException expected=
            new RuntimeException(
                "fixture-aux-worker-failed-open"
            );
        AtomicBoolean acceptCalled=
            new AtomicBoolean();

        try{
            fixture.shutdown.submitAuxiliary(
                ()->{ throw expected; }
            );

            await(
                ()->game.closeCalls>=2,
                "failed-open listener did not receive bounded wake attempts"
            );

            Throwable observed=null;

            try{
                fixture.shutdown.acceptGameSocket(
                    ()->{
                        acceptCalled.set(true);
                        return new TrackingSocket();
                    }
                );
            }catch(Throwable failure){
                observed=failure;
            }

            if(observed!=expected)
                throw new AssertionError(
                    "failed-open wake lost worker authority",
                    observed
                );

            if(acceptCalled.get())
                throw new AssertionError(
                    "failed-open listener allowed a new game accept after AUX death"
                );
        }finally{
            // Make the fixture physically closable for ordinary teardown.
            game.failUntil=game.closeCalls;
            fixture.close();
        }
    }

    private static void assertIdlePollKeepsOneAcceptHandoff()
        throws Exception{
        Fixture fixture=
            new Fixture(
                new TrackingServerSocket(0)
            );
        AtomicInteger polls=
            new AtomicInteger();
        TrackingSocket accepted=
            new TrackingSocket();

        try{
            Socket observed=
                fixture.shutdown.acceptGameSocket(
                    ()->{
                        int call=
                            polls.incrementAndGet();

                        if(fixture.shutdown
                                .pendingGameAcceptHandoffs()!=1)
                            throw new AssertionError(
                                "idle poll released/reacquired game accept handoff"
                            );

                        if(call<=2)
                            throw new SocketTimeoutException(
                                "fixture-idle-poll-"+call
                            );

                        return accepted;
                    }
                );

            if(observed!=accepted||
               polls.get()!=3)
                throw new AssertionError(
                    "ordinary idle polls did not continue accepting"
                );

            if(fixture.shutdown
                    .pendingGameAcceptHandoffs()!=0)
                throw new AssertionError(
                    "completed game accept retained handoff"
                );

            fixture.shutdown.rejectSessionSocket(
                accepted
            );
        }finally{
            fixture.close();
        }
    }

    private static void assertTerminalPollRetiresOneAcceptHandoff()
        throws Exception{
        TrackingServerSocket game=
            new TrackingServerSocket(
                Integer.MAX_VALUE
            );
        Fixture fixture=
            new Fixture(game);
        CountDownLatch secondPollEntered=
            new CountDownLatch(1);
        CountDownLatch releaseSecondPoll=
            new CountDownLatch(1);
        AtomicInteger acceptCalls=
            new AtomicInteger();
        AtomicReference<Socket> acceptedResult=
            new AtomicReference<>();
        AtomicReference<Throwable> acceptFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> terminalFailure=
            new AtomicReference<>();

        Thread acceptThread=
            new Thread(
                ()->{
                    try{
                        acceptedResult.set(
                            fixture.shutdown.acceptGameSocket(
                                ()->{
                                    int call=
                                        acceptCalls.incrementAndGet();

                                    if(call==1)
                                        throw new SocketTimeoutException(
                                            "fixture-terminal-idle-poll"
                                        );

                                    secondPollEntered.countDown();

                                    try{
                                        releaseSecondPoll.await();
                                    }catch(InterruptedException error){
                                        Thread.currentThread()
                                            .interrupt();
                                        throw new IOException(
                                            error
                                        );
                                    }

                                    throw new SocketTimeoutException(
                                        "fixture-terminal-exit-poll"
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
                "aux-worker-terminal-poll-accept"
            );

        Thread terminalThread=
            new Thread(
                ()->{
                    try{
                        fixture.shutdown.close();
                    }catch(Throwable failure){
                        terminalFailure.set(
                            failure
                        );
                    }
                },
                "aux-worker-terminal-poll-close"
            );

        try{
            acceptThread.start();

            if(!secondPollEntered.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "terminal poll fixture did not complete initial idle poll"
                );

            if(fixture.shutdown
                    .pendingGameAcceptHandoffs()!=1)
                throw new AssertionError(
                    "initial idle poll released game accept handoff"
                );

            terminalThread.start();

            await(
                fixture.shutdown::closing,
                "terminal poll fixture did not publish closing fence"
            );

            if(fixture.shutdown
                    .pendingGameAcceptHandoffs()!=1)
                throw new AssertionError(
                    "terminal close observed false zero-handoff gap before next poll"
                );

            releaseSecondPoll.countDown();

            acceptThread.join(
                5_000L
            );
            terminalThread.join(
                5_000L
            );

            if(acceptThread.isAlive()||
               terminalThread.isAlive())
                throw new AssertionError(
                    "terminal poll fixture did not converge"
                );

            if(acceptFailure.get()!=null)
                throw new AssertionError(
                    "terminal poll leaked timeout/serving failure",
                    acceptFailure.get()
                );

            if(acceptedResult.get()!=null)
                throw new AssertionError(
                    "terminal poll committed a socket"
                );

            if(acceptCalls.get()!=2)
                throw new AssertionError(
                    "terminal poll accept call count mismatch: "+
                    acceptCalls.get()
                );

            if(fixture.shutdown
                    .pendingGameAcceptHandoffs()!=0)
                throw new AssertionError(
                    "terminal poll did not retire game accept handoff exactly once"
                );

            if(terminalFailure.get()==null)
                throw new AssertionError(
                    "failed-open listener terminal fixture unexpectedly had no terminal evidence"
                );
        }finally{
            releaseSecondPoll.countDown();
            acceptThread.join(
                1_000L
            );
            terminalThread.join(
                1_000L
            );

            game.failUntil=game.closeCalls;

            try{
                fixture.close();
            }catch(Throwable expected){
                // This fixture deliberately publishes a failed-open listener
                // terminal primary. Repeated close must preserve that truth.
            }
        }
    }

    private static void assertRealAcceptFailureAfterIdlePolls()
        throws Exception{
        Fixture fixture=
            new Fixture(
                new TrackingServerSocket(0)
            );
        IOException expected=
            new IOException(
                "fixture-real-accept-failure-after-polls"
            );
        AtomicInteger calls=
            new AtomicInteger();
        Throwable observed=null;

        try{
            try{
                fixture.shutdown.acceptGameSocket(
                    ()->{
                        int call=
                            calls.incrementAndGet();

                        if(fixture.shutdown
                                .pendingGameAcceptHandoffs()!=1)
                            throw new AssertionError(
                                "real-failure fixture lost handoff across idle poll"
                            );

                        if(call<=2)
                            throw new SocketTimeoutException(
                                "fixture-real-failure-idle-"+call
                            );

                        throw expected;
                    }
                );
            }catch(Throwable failure){
                observed=failure;
            }

            if(observed!=expected)
                throw new AssertionError(
                    "real accept IOException changed after idle polls",
                    observed
                );

            if(calls.get()!=3)
                throw new AssertionError(
                    "real accept failure call count mismatch: "+
                    calls.get()
                );

            if(fixture.shutdown
                    .pendingGameAcceptHandoffs()!=0)
                throw new AssertionError(
                    "real accept failure retained game accept handoff"
                );

            if(expected.getSuppressed().length!=0)
                throw new AssertionError(
                    "internal poll timeout leaked into real accept diagnostics"
                );
        }finally{
            fixture.close();
        }
    }

    private static void assertFailedOpenWakePollsInFlightAccept()
        throws Exception{
        TrackingServerSocket game=
            new TrackingServerSocket(
                Integer.MAX_VALUE
            );
        Fixture fixture=
            new Fixture(game);
        RuntimeException expected=
            new RuntimeException(
                "fixture-aux-worker-inflight-failed-open"
            );
        CountDownLatch acceptEntered=
            new CountDownLatch(1);
        CountDownLatch emitPoll=
            new CountDownLatch(1);
        AtomicInteger acceptCalls=
            new AtomicInteger();
        AtomicReference<Throwable> observed=
            new AtomicReference<>();

        Thread acceptThread=
            new Thread(
                ()->{
                    try{
                        fixture.shutdown.acceptGameSocket(
                            ()->{
                                acceptCalls.incrementAndGet();
                                acceptEntered.countDown();

                                try{
                                    emitPoll.await();
                                }catch(InterruptedException error){
                                    Thread.currentThread()
                                        .interrupt();
                                    throw new IOException(
                                        error
                                    );
                                }

                                throw new SocketTimeoutException(
                                    "fixture-scripted-accept-poll"
                                );
                            }
                        );
                    }catch(Throwable failure){
                        observed.set(
                            failure
                        );
                    }
                },
                "aux-worker-inflight-failed-open"
            );

        try{
            acceptThread.start();

            if(!acceptEntered.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "in-flight game accept did not start"
                );

            if(fixture.shutdown
                    .pendingGameAcceptHandoffs()!=1)
                throw new AssertionError(
                    "in-flight game accept did not publish one handoff"
                );

            fixture.shutdown.submitAuxiliary(
                ()->{ throw expected; }
            );

            await(
                ()->game.closeCalls>=2,
                "failed-open listener did not receive both bounded wake attempts"
            );

            if(game.isClosed())
                throw new AssertionError(
                    "failed-open listener unexpectedly closed"
                );

            if(fixture.shutdown
                    .pendingGameAcceptHandoffs()!=1)
                throw new AssertionError(
                    "failed-open wake lost in-flight accept handoff before poll"
                );

            emitPoll.countDown();

            acceptThread.join(
                5_000L
            );

            if(acceptThread.isAlive())
                throw new AssertionError(
                    "bounded accept poll did not release in-flight serving boundary"
                );

            if(observed.get()!=expected)
                throw new AssertionError(
                    "in-flight failed-open accept lost exact AUX worker primary",
                    observed.get()
                );

            if(acceptCalls.get()!=1)
                throw new AssertionError(
                    "worker failure caused unexpected extra accept polls: "+
                    acceptCalls.get()
                );

            if(fixture.shutdown
                    .pendingGameAcceptHandoffs()!=0)
                throw new AssertionError(
                    "worker-failure poll retained game accept handoff"
                );

            if(fixture.shutdown
                    .activeSessionCount()!=0)
                throw new AssertionError(
                    "worker-failure poll committed a game socket"
                );

            if(expected.getSuppressed().length==0)
                throw new AssertionError(
                    "failed-open listener wake failures were not retained diagnostically"
                );
        }finally{
            emitPoll.countDown();
            acceptThread.join(
                1_000L
            );

            // Make the fixture physically closable for ordinary teardown.
            game.failUntil=game.closeCalls;
            fixture.close();
        }
    }

    private static void assertDuplicateFailureKeepsFirstPrimary()
        throws Exception{
        Fixture fixture=
            new Fixture(
                new TrackingServerSocket(0)
            );
        RuntimeException first=
            new RuntimeException(
                "fixture-aux-worker-first"
            );
        RuntimeException second=
            new RuntimeException(
                "fixture-aux-worker-second"
            );

        try{
            fixture.shutdown.submitAuxiliary(
                ()->{ throw first; }
            );

            await(
                ()->fixture.game.closeCalls>=1,
                "first AUX failure did not publish"
            );

            fixture.shutdown.submitAuxiliary(
                ()->{ throw second; }
            );

            await(
                ()->containsIdentity(
                    first.getSuppressed(),
                    second
                ),
                "duplicate AUX failure was not retained behind first primary"
            );

            Throwable observed=null;

            try{
                fixture.shutdown.acceptGameSocket(
                    ()->new TrackingSocket()
                );
            }catch(Throwable failure){
                observed=failure;
            }

            if(observed!=first)
                throw new AssertionError(
                    "duplicate AUX failure replaced first primary",
                    observed
                );
        }finally{
            fixture.close();
        }
    }

    private static void assertExactErrorTaskBoundaryIdentity()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        ManualExecutor pool=
            new ManualExecutor();
        TrackingServerSocket game=
            new TrackingServerSocket(0);
        ServerSocket aux=
            new ServerSocket();
        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );
        AssertionError expected=
            new AssertionError(
                "fixture-aux-worker-error"
            );

        if(!shutdown.submitAuxiliary(
                ()->{ throw expected; }))
            throw new AssertionError(
                "Error identity fixture AUX task was rejected"
            );

        Throwable escaped=null;

        try{
            pool.runStored();
        }catch(Throwable failure){
            escaped=failure;
        }

        if(escaped!=expected)
            throw new AssertionError(
                "AUX task-boundary Error was wrapped/swallowed",
                escaped
            );

        Throwable surfaced=null;

        try{
            shutdown.acceptGameSocket(
                ()->new TrackingSocket()
            );
        }catch(Throwable failure){
            surfaced=failure;
        }

        if(surfaced!=expected)
            throw new AssertionError(
                "published AUX Error identity changed at game accept boundary",
                surfaced
            );

        shutdown.close();
    }

    private static void assertPrePublicationLateFailureJoinsFirstTerminalResult()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        ManualExecutor pool=
            new ManualExecutor();
        TrackingServerSocket game=
            new TrackingServerSocket(0);
        ServerSocket aux=
            new ServerSocket();
        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );
        FailThenBlockAuxiliarySocket ownedAux=
            new FailThenBlockAuxiliarySocket();

        Socket accepted=
            shutdown.acceptAuxiliarySocket(
                ()->ownedAux
            );

        if(accepted!=ownedAux||
           shutdown.activeAuxiliarySocketCount()!=1)
            throw new AssertionError(
                "pre-publication fixture did not establish active AUX socket ownership"
            );

        RuntimeException workerFailure=
            new RuntimeException(
                "fixture-pre-publication-worker"
            );

        if(!shutdown.submitAuxiliary(
                ()->{ throw workerFailure; }))
            throw new AssertionError(
                "pre-publication fixture AUX task was rejected"
            );

        AtomicReference<Throwable> terminalObserved=
            new AtomicReference<>();

        Thread terminalThread=
            new Thread(
                ()->{
                    try{
                        shutdown.close();
                    }catch(Throwable failure){
                        terminalObserved.set(
                            failure
                        );
                    }
                },
                "aux-worker-pre-publication-terminal"
            );

        terminalThread.start();

        if(!ownedAux.secondCloseEntered.await(
                5,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "terminal owner did not reach post-pool AUX retry fence"
            );

        Throwable taskObserved=null;

        try{
            pool.runStored();
        }catch(Throwable failure){
            taskObserved=failure;
        }

        if(taskObserved!=workerFailure)
            throw new AssertionError(
                "pre-publication AUX task did not escape exact worker failure",
                taskObserved
            );

        ownedAux.releaseSecondClose.countDown();

        terminalThread.join(
            5_000L
        );

        if(terminalThread.isAlive())
            throw new AssertionError(
                "pre-publication terminal owner did not finish"
            );

        Throwable firstTerminal=
            terminalObserved.get();

        if(firstTerminal==null)
            throw new AssertionError(
                "pre-publication fixture did not publish terminal failure"
            );

        if(!containsRecursiveIdentity(
                firstTerminal,
                workerFailure))
            throw new AssertionError(
                "worker failure published after earlier drain was absent from first terminal result",
                firstTerminal
            );

        Throwable repeated=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            repeated=failure;
        }

        if(repeated!=firstTerminal)
            throw new AssertionError(
                "pre-publication worker evidence changed terminal identity",
                repeated
            );
    }

    private static void assertLatePostTerminalFailurePreservesPublishedIdentity()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        world.start();

        ManualExecutor pool=
            new ManualExecutor();
        TrackingServerSocket game=
            new TrackingServerSocket(
                Integer.MAX_VALUE
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
        RuntimeException workerFailure=
            new RuntimeException(
                "fixture-late-post-terminal-aux-worker"
            );

        if(!shutdown.submitAuxiliary(
                ()->{ throw workerFailure; }))
            throw new AssertionError(
                "late terminal fixture AUX task was rejected"
            );

        Throwable terminalPrimary=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            terminalPrimary=failure;
        }

        if(terminalPrimary==null)
            throw new AssertionError(
                "fixture did not establish an initial terminal failure"
            );

        Throwable taskObserved=null;

        try{
            pool.runStored();
        }catch(Throwable failure){
            taskObserved=failure;
        }

        if(taskObserved!=workerFailure)
            throw new AssertionError(
                "late AUX task did not rethrow exact worker failure",
                taskObserved
            );

        if(!containsIdentity(
                terminalPrimary.getSuppressed(),
                workerFailure))
            throw new AssertionError(
                "post-terminal AUX failure was not attached to published terminal primary"
            );

        // Permit physical listener recovery on the repeated observation path.
        game.failUntil=
            game.closeCalls;

        Throwable repeated=null;

        try{
            shutdown.close();
        }catch(Throwable failure){
            repeated=failure;
        }finally{
            pool.shutdownNow();

            if(!world.closed())
                world.close();
        }

        if(repeated!=terminalPrimary)
            throw new AssertionError(
                "late worker evidence replaced published terminal failure identity",
                repeated
            );
    }

    private static void assertCleanAuxiliaryExitDoesNotFabricateFailure()
        throws Exception{
        Fixture fixture=
            new Fixture(
                new TrackingServerSocket(0)
            );
        AtomicBoolean ran=
            new AtomicBoolean();

        try{
            if(!fixture.shutdown.submitAuxiliary(
                    ()->ran.set(true)))
                throw new AssertionError(
                    "clean auxiliary task was rejected"
                );

            await(
                ran::get,
                "clean auxiliary task did not run"
            );

            TrackingSocket socket=
                new TrackingSocket();

            Socket accepted=
                fixture.shutdown.acceptGameSocket(
                    ()->socket
                );

            if(accepted!=socket)
                throw new AssertionError(
                    "clean auxiliary completion fabricated serving failure"
                );

            fixture.shutdown.rejectSessionSocket(
                socket
            );
        }finally{
            fixture.close();
        }
    }

    private static boolean containsRecursiveIdentity(
        Throwable root,
        Throwable expected
    ){
        if(root==expected)
            return true;

        Throwable cause=
            root.getCause();

        if(cause!=null&&
           cause!=root&&
           containsRecursiveIdentity(
               cause,
               expected
           ))
            return true;

        for(Throwable suppressed:
                root.getSuppressed())
            if(suppressed!=root&&
               containsRecursiveIdentity(
                   suppressed,
                   expected
               ))
                return true;

        return false;
    }

    private static boolean containsIdentity(
        Throwable[] values,
        Throwable expected
    ){
        for(Throwable value:values)
            if(value==expected)
                return true;

        return false;
    }

    private static void await(
        BooleanProbe probe,
        String message
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.SECONDS.toNanos(5);

        while(System.nanoTime()<deadline){
            if(probe.get())
                return;

            Thread.sleep(5L);
        }

        throw new AssertionError(
            message
        );
    }

    private interface BooleanProbe {
        boolean get()
            throws Exception;
    }

    private static final class Fixture
        implements AutoCloseable {
        final World world;
        final ExecutorService pool;
        final TrackingServerSocket game;
        final ServerSocket aux;
        final LocalServerShutdownCoordinator shutdown;

        Fixture(
            TrackingServerSocket game
        )throws Exception{
            this.game=game;
            world=World.isolatedForTest(25L);
            world.start();
            pool=Executors.newCachedThreadPool();
            aux=new ServerSocket();
            shutdown=
                new LocalServerShutdownCoordinator(
                    world,
                    pool,
                    game,
                    aux
                );
        }

        @Override public void close()
            throws Exception{
            Throwable failure=null;

            try{
                shutdown.close();
            }catch(Throwable error){
                failure=error;
            }

            pool.shutdownNow();
            pool.awaitTermination(
                5,
                TimeUnit.SECONDS
            );

            if(failure!=null)
                throwAsException(
                    failure
                );
        }
    }

    private static final class TrackingSocket
        extends Socket {
        private boolean closed;

        @Override public synchronized void close()
            throws IOException{
            closed=true;
        }

        @Override public synchronized boolean isClosed(){
            return closed;
        }
    }

    private static final class BlockingFirstCloseSocket
        extends Socket {
        final CountDownLatch firstCloseEntered=
            new CountDownLatch(1);
        final CountDownLatch releaseFirstClose=
            new CountDownLatch(1);
        final IOException firstFailure=
            new IOException(
                "fixture-blocked-first-close"
            );
        int closeCalls;
        boolean closed;

        @Override public synchronized void close()
            throws IOException{
            closeCalls++;

            if(closeCalls==1){
                firstCloseEntered.countDown();

                boolean interrupted=false;

                for(;;)
                    try{
                        releaseFirstClose.await();
                        break;
                    }catch(InterruptedException error){
                        interrupted=true;
                    }

                if(interrupted)
                    Thread.currentThread()
                        .interrupt();

                throw firstFailure;
            }

            closed=true;
        }

        @Override public synchronized boolean isClosed(){
            return closed;
        }
    }

    private static final class FailThenBlockAuxiliarySocket
        extends Socket {
        final CountDownLatch secondCloseEntered=
            new CountDownLatch(1);
        final CountDownLatch releaseSecondClose=
            new CountDownLatch(1);
        final IOException firstFailure=
            new IOException(
                "fixture-aux-first-close"
            );
        int closeCalls;
        boolean closed;

        @Override public synchronized void close()
            throws IOException{
            closeCalls++;

            if(closeCalls==1)
                throw firstFailure;

            if(closeCalls==2){
                secondCloseEntered.countDown();

                boolean interrupted=false;

                for(;;)
                    try{
                        releaseSecondClose.await();
                        break;
                    }catch(InterruptedException error){
                        interrupted=true;
                    }

                if(interrupted)
                    Thread.currentThread()
                        .interrupt();
            }

            closed=true;
        }

        @Override public synchronized boolean isClosed(){
            return closed;
        }
    }

    private static final class FailOnceTrackingSocket
        extends Socket {
        int closeCalls;
        boolean closed;
        final IOException failure=
            new IOException(
                "fixture-accepted-socket-close"
            );

        @Override public synchronized void close()
            throws IOException{
            closeCalls++;

            if(closeCalls==1)
                throw failure;

            closed=true;
        }

        @Override public synchronized boolean isClosed(){
            return closed;
        }
    }

    private static final class ManualExecutor
        extends AbstractExecutorService {
        private Runnable stored;
        private boolean shutdown;

        @Override public synchronized void execute(
            Runnable command
        ){
            if(shutdown)
                throw new java.util.concurrent.RejectedExecutionException();

            if(stored!=null)
                throw new IllegalStateException(
                    "manual executor already has a task"
                );

            stored=
                java.util.Objects.requireNonNull(
                    command,
                    "command"
                );
        }

        synchronized void runStored(){
            Runnable task=stored;

            if(task==null)
                throw new IllegalStateException(
                    "manual executor has no stored task"
                );

            stored=null;
            task.run();
        }

        @Override public synchronized void shutdown(){
            shutdown=true;
        }

        @Override public synchronized List<Runnable> shutdownNow(){
            shutdown=true;
            return Collections.emptyList();
        }

        @Override public synchronized boolean isShutdown(){
            return shutdown;
        }

        @Override public synchronized boolean isTerminated(){
            return shutdown;
        }

        @Override public boolean awaitTermination(
            long timeout,
            TimeUnit unit
        ){
            return true;
        }
    }

    private static final class TrackingServerSocket
        extends ServerSocket {
        volatile int failUntil;
        volatile int closeCalls;
        volatile boolean closed;
        final IOException failure=
            new IOException(
                "fixture-game-listener-close"
            );

        TrackingServerSocket(
            int failUntil
        )throws IOException{
            this.failUntil=failUntil;
        }

        @Override public synchronized void close()
            throws IOException{
            closeCalls++;

            if(closeCalls<=failUntil)
                throw failure;

            closed=true;
        }

        @Override public synchronized boolean isClosed(){
            return closed;
        }
    }

    private static void throwAsException(
        Throwable failure
    )throws Exception{
        if(failure instanceof Exception)
            throw (Exception)failure;
        if(failure instanceof Error)
            throw (Error)failure;
        throw new RuntimeException(failure);
    }

    private LocalAuxiliaryWorkerFailureTest(){}
}
