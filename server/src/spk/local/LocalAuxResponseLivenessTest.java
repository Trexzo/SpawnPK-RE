package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalAuxResponseLivenessTest {
    public static void main(
        String[] args
    )throws Exception{
        assertArmedBeforeFirstWriteAndHealthyCompletionCancels();
        assertProgressRefreshesSingleDeadline();
        assertWatchdogNonterminationIsWorkerFatal();
        assertInterruptedWatchdogJoinIsWorkerFatal();
        assertTimeoutAbortsBlockedWriteAndWorkerContinues();
        assertWriteFailureKeepsPrimaryAcrossTimeout();
        assertUncheckedPrimaryKeepsIdentityAcrossTimeout();
        assertFinalWriteTimeoutRaceCannotReturnClean();
        assertTerminalFenceWinsBeforePhysicalClose();
        assertTerminalCloseWinsWithoutSyntheticTimeout();
        assertSuccessfulTimeoutKeepsOwnershipUntilWorkerRelease();
        assertAbortFailurePreservesCoordinatorOwnership();
        assertFailedOpenAbortPublishesDurableFailure();
        assertLargeStreamingProgressIsNotTotalDurationBounded();

        System.out.println(
            "LOCAL_AUX_RESPONSE_LIVENESS_PASS "+
            "armedBeforeFirstWrite=true "+
            "progressRefreshes=true "+
            "singleOutstandingDeadline=true "+
            "watchdogNonterminationFatal=true "+
            "interruptedWatchdogJoinFatal=true "+
            "stalledWriteAborted=true "+
            "abortRetry=true "+
            "timeoutConnectionScoped=true "+
            "timeoutEvidenceDurable=true "+
            "writePrimaryPreserved=true "+
            "uncheckedPrimaryPreserved=true "+
            "finalWriteRaceBounded=true "+
            "sameWorkerContinues=true "+
            "terminalFenceWins=true "+
            "terminalWins=true "+
            "watchdogRetired=true "+
            "successfulTimeoutOwnershipHeld=true "+
            "failedAbortOwnershipRetained=true "+
            "uncheckedAbortSuppressed=true "+
            "failedOpenAbortPublished=true "+
            "largeStreamingExact=true "+
            "noTotalDurationCap=true"
        );
    }

    private static void
        assertArmedBeforeFirstWriteAndHealthyCompletionCancels()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();
        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler
            );

        AtomicInteger writes=
            new AtomicInteger();
        OutputStream target=
            new OutputStream(){
                @Override public void write(
                    int value
                ){
                    if(scheduler.outstandingTasks()!=1)
                        throw new AssertionError(
                            "response watchdog was not armed before first write"
                        );

                    writes.incrementAndGet();
                }

                @Override public void write(
                    byte[] buffer,
                    int offset,
                    int length
                ){
                    if(scheduler.outstandingTasks()!=1)
                        throw new AssertionError(
                            "response watchdog was not armed before header/body write"
                        );

                    writes.incrementAndGet();
                }
            };

        LocalAuxHttpResponse.writeBytes(
            liveness.output(
                target
            ),
            200,
            "OK",
            "text/plain",
            new byte[]{1,2,3},
            false
        );

        Throwable failure=
            liveness.finish(
                null
            );

        if(failure!=null||
           writes.get()<2||
           scheduler.outstandingTasks()!=0||
           !scheduler.shutdown||
           !scheduler.awaited||
           socket.closeCalls!=0||
           liveness.timedOut())
            throw new AssertionError(
                "healthy response did not cancel/join watchdog cleanly",
                failure
            );
    }

    private static void
        assertProgressRefreshesSingleDeadline()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();
        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler
            );
        OutputStream out=
            liveness.output(
                new ByteArrayOutputStream()
            );

        int initialSchedules=
            scheduler.scheduleCalls;

        out.write(
            new byte[]{1}
        );
        out.write(
            new byte[]{2}
        );
        out.flush();

        if(scheduler.scheduleCalls<
                initialSchedules+3||
           scheduler.maxOutstanding!=1||
           scheduler.outstandingTasks()!=1)
            throw new AssertionError(
                "response progress did not refresh exactly one deadline"
            );

        Throwable failure=
            liveness.finish(
                null
            );

        if(failure!=null||
           scheduler.outstandingTasks()!=0)
            throw new AssertionError(
                "refreshed deadline survived finish",
                failure
            );
    }

    private static void
        assertWatchdogNonterminationIsWorkerFatal()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();
        scheduler.terminates=false;

        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler
            );

        Throwable failure=
            liveness.finish(
                null
            );

        if(!(failure instanceof IllegalStateException)||
           failure.getMessage()==null||
           !failure.getMessage().contains(
                "did not terminate"))
            throw new AssertionError(
                "watchdog nontermination was normalized into connection-scoped failure",
                failure
            );
    }

    private static void
        assertInterruptedWatchdogJoinIsWorkerFatal()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();
        scheduler.interruptAwait=true;

        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler
            );

        Throwable failure=
            liveness.finish(
                null
            );
        boolean interrupted=
            Thread.currentThread()
                .isInterrupted();

        Thread.interrupted();

        if(!(failure instanceof IllegalStateException)||
           failure.getMessage()==null||
           !failure.getMessage().contains(
                "interrupted")||
           !interrupted)
            throw new AssertionError(
                "interrupted watchdog join was normalized or interrupt status was lost",
                failure
            );
    }

    private static void
        assertTimeoutAbortsBlockedWriteAndWorkerContinues()
        throws Exception{
        FakeSocket first=
            new FakeSocket();
        FakeSocket second=
            new FakeSocket();
        List<Socket> sockets=
            new ArrayList<>();
        sockets.add(
            first
        );
        sockets.add(
            second
        );

        AtomicInteger accepts=
            new AtomicInteger();
        AtomicInteger secondHandled=
            new AtomicInteger();
        AtomicReference<IOException>
            connectionFailure=
                new AtomicReference<>();
        AtomicInteger timeoutSchedulers=
            new AtomicInteger();
        IOException firstAbortFailure=
            new IOException(
                "fixture-first-timeout-close-failure"
            );
        first.closeFailure=
            firstAbortFailure;
        first.failCloseAttempts=1;

        LocalAuxHttpWorker.run(
            ()->false,
            ()->{
                int index=
                    accepts.getAndIncrement();

                return index<sockets.size()
                    ?sockets.get(index)
                    :null;
            },
            socket->{
                if(socket==second){
                    secondHandled.incrementAndGet();
                    return;
                }

                ManualScheduler scheduler=
                    new ManualScheduler();
                timeoutSchedulers.incrementAndGet();

                LocalAuxResponseLiveness liveness=
                    LocalAuxResponseLiveness.arm(
                        first,
                        scheduler
                    );
                Throwable primary=null;

                try{
                    liveness.output(
                        new TriggeringBlockedOutput(
                            first,
                            scheduler
                        )
                    ).write(
                        7
                    );
                }catch(IOException|
                       RuntimeException|
                       Error failure){
                    primary=failure;
                }

                primary=
                    liveness.finish(
                        primary
                    );

                if(primary instanceof IOException)
                    throw (IOException)primary;

                if(primary instanceof RuntimeException)
                    throw (RuntimeException)primary;

                if(primary instanceof Error)
                    throw (Error)primary;
            },
            socket->{
                if(!socket.isClosed())
                    socket.close();
            },
            connectionFailure::set,
            failure->{}
        );

        Throwable observedConnection=
            connectionFailure.get();

        if(timeoutSchedulers.get()!=1||
           !(observedConnection instanceof IOException)||
           !first.isClosed()||
           first.closeCalls!=2||
           secondHandled.get()!=1||
           !containsSuppressedTimeout(
                observedConnection
            ))
            throw new AssertionError(
                "timed-out response did not retry abort, preserve timeout evidence, or continue worker",
                observedConnection
            );
    }

    private static void
        assertWriteFailureKeepsPrimaryAcrossTimeout()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();
        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler
            );
        IOException writeFailure=
            new IOException(
                "fixture-write-primary"
            );

        scheduler.trigger();

        Throwable observed=
            liveness.finish(
                writeFailure
            );

        if(observed!=writeFailure||
           !containsSuppressedTimeout(
                writeFailure
            ))
            throw new AssertionError(
                "write IOException lost primary identity or durable timeout evidence",
                observed
            );
    }

    private static void
        assertUncheckedPrimaryKeepsIdentityAcrossTimeout()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();
        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler
            );
        RuntimeException expected=
            new IllegalStateException(
                "fixture-unchecked-primary"
            );

        scheduler.trigger();

        Throwable observed=
            liveness.finish(
                expected
            );

        if(observed!=expected||
           !containsSuppressedTimeout(
                expected
            ))
            throw new AssertionError(
                "unchecked response primary was replaced by timeout",
                observed
            );
    }

    private static void
        assertFinalWriteTimeoutRaceCannotReturnClean()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();
        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler
            );
        OutputStream out=
            liveness.output(
                new ByteArrayOutputStream()
            );

        out.write(
            1
        );

        scheduler.trigger();

        Throwable observed=
            liveness.finish(
                null
            );

        if(!(observed instanceof SocketTimeoutException)||
           !liveness.timedOut())
            throw new AssertionError(
                "final successful write/timeout race returned clean success",
                observed
            );
    }

    private static void
        assertTerminalFenceWinsBeforePhysicalClose()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();
        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler,
                ()->true,
                failure->{
                    throw new AssertionError(
                        "terminal-fenced watchdog published failure",
                        failure
                    );
                }
            );

        scheduler.trigger();

        Throwable failure=
            liveness.finish(
                null
            );

        if(failure!=null||
           liveness.timedOut()||
           socket.isClosed()||
           socket.closeCalls!=0)
            throw new AssertionError(
                "published terminal fence did not neutralize response timeout before physical close",
                failure
            );
    }

    private static void
        assertTerminalCloseWinsWithoutSyntheticTimeout()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();
        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler
            );

        socket.close();
        scheduler.trigger();

        Throwable failure=
            liveness.finish(
                null
            );

        if(failure!=null||
           liveness.timedOut()||
           socket.closeCalls!=1||
           scheduler.outstandingTasks()!=0)
            throw new AssertionError(
                "terminal socket close did not neutralize watchdog",
                failure
            );
    }

    private static void
        assertSuccessfulTimeoutKeepsOwnershipUntilWorkerRelease()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        java.util.concurrent.ExecutorService pool=
            java.util.concurrent.Executors
                .newSingleThreadExecutor();
        java.net.ServerSocket game=
            new java.net.ServerSocket();
        java.net.ServerSocket aux=
            new java.net.ServerSocket();
        LocalServerShutdownCoordinator coordinator=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();

        try{
            Socket accepted=
                coordinator
                    .acceptAuxiliarySocket(
                        ()->socket
                    );

            if(accepted!=socket||
               coordinator.activeAuxiliarySocketCount()!=1)
                throw new AssertionError(
                    "fixture auxiliary socket was not coordinator-owned"
                );

            LocalAuxResponseLiveness liveness=
                LocalAuxResponseLiveness.arm(
                    socket,
                    scheduler
                );

            scheduler.trigger();

            if(!socket.isClosed()||
               !liveness.timedOut()||
               coordinator.activeAuxiliarySocketCount()!=1)
                throw new AssertionError(
                    "successful timeout close retired ownership before worker release"
                );

            Throwable failure=
                liveness.finish(
                    null
                );

            if(!(failure instanceof IOException)||
               failure.getMessage()==null||
               !failure.getMessage().contains(
                    "no progress"))
                throw new AssertionError(
                    "successful timeout did not surface as connection-scoped response failure",
                    failure
                );

            coordinator.releaseAuxiliarySocket(
                socket
            );

            if(coordinator.activeAuxiliarySocketCount()!=0)
                throw new AssertionError(
                    "worker release did not retire physically closed timeout socket"
                );
        }finally{
            try{
                coordinator.close();
            }catch(Throwable ignored){
            }
        }
    }

    private static void
        assertAbortFailurePreservesCoordinatorOwnership()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        java.util.concurrent.ExecutorService pool=
            java.util.concurrent.Executors
                .newSingleThreadExecutor();
        java.net.ServerSocket game=
            new java.net.ServerSocket();
        java.net.ServerSocket aux=
            new java.net.ServerSocket();
        LocalServerShutdownCoordinator coordinator=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();

        try{
            Socket accepted=
                coordinator
                    .acceptAuxiliarySocket(
                        ()->socket
                    );

            if(accepted!=socket||
               coordinator.activeAuxiliarySocketCount()!=1)
                throw new AssertionError(
                    "fixture auxiliary socket was not coordinator-owned"
                );

            RuntimeException abortFailure=
                new IllegalStateException(
                    "fixture-timeout-close-runtime"
                );
            socket.closeFailure=
                abortFailure;
            socket.failCloseAttempts=2;
            AtomicReference<Throwable>
                durableFailure=
                    new AtomicReference<>();

            LocalAuxResponseLiveness liveness=
                LocalAuxResponseLiveness.arm(
                    socket,
                    scheduler,
                    durableFailure::set
                );

            scheduler.trigger();

            Throwable observed=
                liveness.finish(
                    null
                );

            if(!(observed instanceof SocketTimeoutException)||
               observed.getSuppressed().length<2||
               observed.getSuppressed()[0]!=abortFailure||
               durableFailure.get()!=observed||
               coordinator.activeAuxiliarySocketCount()!=1||
               socket.isClosed()||
               socket.closeCalls!=2)
                throw new AssertionError(
                    "failed timeout abort did not preserve timeout primary, close evidence, ownership, and durable signal",
                    observed
                );

            socket.closeFailure=null;
            socket.failCloseAttempts=0;
            coordinator.releaseAuxiliarySocket(
                socket
            );

            if(coordinator.activeAuxiliarySocketCount()!=0||
               !socket.isClosed())
                throw new AssertionError(
                    "coordinator retry did not retire failed timeout socket"
                );
        }finally{
            try{
                coordinator.close();
            }catch(Throwable ignored){
            }
        }
    }

    private static void
        assertFailedOpenAbortPublishesDurableFailure()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        java.util.concurrent.ExecutorService pool=
            java.util.concurrent.Executors
                .newSingleThreadExecutor();
        java.net.ServerSocket game=
            new java.net.ServerSocket();
        java.net.ServerSocket aux=
            new java.net.ServerSocket();
        LocalServerShutdownCoordinator coordinator=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );
        FakeSocket socket=
            new FakeSocket();
        socket.closeFailure=
            new IOException(
                "fixture-persistent-timeout-close-failure"
            );
        socket.failCloseAttempts=2;
        ManualScheduler scheduler=
            new ManualScheduler();
        AtomicReference<Throwable> workerFailure=
            new AtomicReference<>();

        try{
            Socket accepted=
                coordinator
                    .acceptAuxiliarySocket(
                        ()->socket
                    );

            if(accepted!=socket)
                throw new AssertionError(
                    "durable-failure fixture socket not accepted"
                );

            LocalAuxResponseLiveness liveness=
                LocalAuxResponseLiveness.arm(
                    socket,
                    scheduler,
                    coordinator::publishAuxiliaryWorkerFailure
                );
            BlockingUntilSocketClosedOutput blocked=
                new BlockingUntilSocketClosedOutput(
                    socket
                );

            Thread responseThread=
                new Thread(
                    ()->{
                        Throwable primary=null;

                        try{
                            liveness.output(
                                blocked
                            ).write(
                                1
                            );
                        }catch(Throwable failure){
                            primary=failure;
                        }

                        workerFailure.set(
                            liveness.finish(
                                primary
                            )
                        );
                    },
                    "fixture-aux-blocked-response"
                );
            responseThread.setDaemon(
                true
            );
            responseThread.start();

            if(!blocked.entered.await(
                    1,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "fixture response write did not block"
                );

            scheduler.trigger();

            if(socket.isClosed()||
               socket.closeCalls!=2||
               !game.isClosed()||
               coordinator.activeAuxiliarySocketCount()!=1||
               !liveness.timedOut()||
               !responseThread.isAlive())
                throw new AssertionError(
                    "failed-open timeout did not leave blocked write owned while publishing durable AUX unhealthy state"
                );

            socket.closeFailure=null;
            socket.failCloseAttempts=0;
            coordinator.releaseAuxiliarySocket(
                socket
            );

            responseThread.join(
                1_000L
            );

            Throwable observed=
                workerFailure.get();

            if(responseThread.isAlive()||
               coordinator.activeAuxiliarySocketCount()!=0||
               !(observed instanceof IOException)||
               !containsSuppressedTimeout(
                    observed
                ))
                throw new AssertionError(
                    "coordinator retry did not release blocked response with preserved timeout evidence",
                    observed
                );
        }finally{
            try{
                coordinator.close();
            }catch(Throwable expected){
                // The published AUX timeout remains terminal evidence by design.
            }
        }
    }

    private static void
        assertLargeStreamingProgressIsNotTotalDurationBounded()
        throws Exception{
        FakeSocket socket=
            new FakeSocket();
        ManualScheduler scheduler=
            new ManualScheduler();
        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler
            );
        byte[] body=
            new byte[
                8 * 1024 * 5 + 123
            ];

        for(int i=0;i<body.length;i++)
            body[i]=
                (byte)(i&255);

        ByteArrayOutputStream target=
            new ByteArrayOutputStream();

        LocalAuxHttpResponse.copyExactly(
            new ByteArrayInputStream(
                body
            ),
            liveness.output(
                target
            ),
            body.length
        );

        Throwable failure=
            liveness.finish(
                null
            );

        if(failure!=null||
           !java.util.Arrays.equals(
                body,
                target.toByteArray()
           )||
           socket.closeCalls!=0||
           scheduler.scheduleCalls<6||
           scheduler.maxOutstanding!=1)
            throw new AssertionError(
                "healthy multi-chunk archive was treated as total-duration timeout or changed bytes",
                failure
            );
    }

    private static boolean containsSuppressedTimeout(
        Throwable failure
    ){
        for(Throwable suppressed:
                failure.getSuppressed())
            if(suppressed instanceof SocketTimeoutException)
                return true;

        return false;
    }

    private static final class
        BlockingUntilSocketClosedOutput
        extends OutputStream {
        private final FakeSocket socket;
        final CountDownLatch entered=
            new CountDownLatch(
                1
            );

        BlockingUntilSocketClosedOutput(
            FakeSocket socket
        ){
            this.socket=socket;
        }

        @Override public void write(
            int value
        )throws IOException{
            entered.countDown();

            try{
                if(!socket.closedLatch.await(
                        2,
                        TimeUnit.SECONDS))
                    throw new AssertionError(
                        "fixture socket was never physically closed"
                    );
            }catch(InterruptedException interrupted){
                Thread.currentThread()
                    .interrupt();
                throw new IOException(
                    "fixture blocked write interrupted",
                    interrupted
                );
            }

            throw new IOException(
                "fixture-write-released-by-close"
            );
        }
    }

    private static final class
        TriggeringBlockedOutput
        extends OutputStream {
        private final FakeSocket socket;
        private final ManualScheduler scheduler;

        TriggeringBlockedOutput(
            FakeSocket socket,
            ManualScheduler scheduler
        ){
            this.socket=socket;
            this.scheduler=scheduler;
        }

        @Override public void write(
            int value
        )throws IOException{
            scheduler.trigger();

            if(socket.isClosed())
                throw new IOException(
                    "fixture-write-aborted"
                );
        }
    }

    private static final class ManualScheduler
        implements LocalAuxResponseLiveness.Scheduler {
        private Runnable current;
        private long token;
        int scheduleCalls;
        int maxOutstanding;
        boolean shutdown;
        boolean awaited;
        boolean terminates=true;
        boolean interruptAwait;

        @Override public synchronized
            LocalAuxResponseLiveness.Cancellable
            schedule(
                Runnable task,
                long delayMillis
            ){
            if(shutdown)
                throw new IllegalStateException(
                    "scheduler shut down"
                );

            if(delayMillis!=
                    LocalAuxResponseLiveness
                        .NO_PROGRESS_TIMEOUT_MILLIS)
                throw new AssertionError(
                    "unexpected response timeout policy "+
                    delayMillis
                );

            long mine=
                ++token;
            current=
                task;
            scheduleCalls++;
            maxOutstanding=
                Math.max(
                    maxOutstanding,
                    outstandingTasks()
                );

            return ()->{
                synchronized(ManualScheduler.this){
                    if(token==mine)
                        current=null;
                }
            };
        }

        synchronized void trigger(){
            Runnable task=
                current;
            current=null;

            if(task!=null)
                task.run();
        }

        @Override public synchronized void shutdownNow(){
            shutdown=true;
            current=null;
        }

        @Override public synchronized boolean awaitTermination(
            long timeoutMillis
        )throws InterruptedException{
            awaited=true;

            if(interruptAwait)
                throw new InterruptedException(
                    "fixture-watchdog-join-interrupt"
                );

            return terminates;
        }

        @Override public synchronized int outstandingTasks(){
            return current==null
                ?0
                :1;
        }
    }

    private static final class FakeSocket
        extends Socket {
        boolean closed;
        int closeCalls;
        int failCloseAttempts;
        Throwable closeFailure;
        final CountDownLatch closedLatch=
            new CountDownLatch(
                1
            );

        @Override public synchronized void close()
            throws IOException{
            closeCalls++;

            if(closeFailure!=null&&
               closeCalls<=failCloseAttempts){
                if(closeFailure instanceof IOException)
                    throw (IOException)closeFailure;
                if(closeFailure instanceof RuntimeException)
                    throw (RuntimeException)closeFailure;
                if(closeFailure instanceof Error)
                    throw (Error)closeFailure;

                throw new IOException(
                    "fixture socket close failure",
                    closeFailure
                );
            }

            closed=true;
            closedLatch.countDown();
        }

        @Override public synchronized boolean isClosed(){
            return closed;
        }
    }

    private LocalAuxResponseLivenessTest(){}
}
