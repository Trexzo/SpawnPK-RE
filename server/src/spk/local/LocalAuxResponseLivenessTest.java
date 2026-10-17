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
        assertWatchdogCancelFailureOrdering();
        assertWatchdogCancelFailureDoesNotSkipLaterRetirement();
        assertWatchdogCancelSameFailureDoesNotSelfSuppress();
        assertWatchdogShutdownFailureOrdering();
        assertWatchdogShutdownFailureStillAwaitsRetirement();
        assertWatchdogShutdownSameFailureDoesNotSelfSuppress();
        assertTimeoutEvidenceSurvivesWatchdogShutdownFailure();
        assertTimeoutAbortsBlockedWriteAndWorkerContinues();
        assertWriteFailureKeepsPrimaryAcrossTimeout();
        assertUncheckedPrimaryKeepsIdentityAcrossTimeout();
        assertFinalWriteTimeoutRaceCannotReturnClean();
        assertTerminalFenceWinsBeforePhysicalClose();
        assertTerminalWinsBetweenValidationAndTimeoutClaim();
        assertHealthyFinishWinsBeforeTimeoutClaim();
        assertProgressWinsBeforeStaleTimeoutClaim();
        assertTimeoutClaimWinsBeforeTerminalFence();
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
            "watchdogCancelFailureOrdered=true "+
            "watchdogCancelContinuesRetirement=true "+
            "watchdogCancelSelfSuppressionSafe=true "+
            "watchdogShutdownFailureOrdered=true "+
            "watchdogShutdownAwaited=true "+
            "watchdogShutdownSelfSuppressionSafe=true "+
            "watchdogShutdownTimeoutRetained=true "+
            "stalledWriteAborted=true "+
            "abortRetry=true "+
            "timeoutConnectionScoped=true "+
            "timeoutEvidenceDurable=true "+
            "writePrimaryPreserved=true "+
            "uncheckedPrimaryPreserved=true "+
            "finalWriteRaceBounded=true "+
            "sameWorkerContinues=true "+
            "terminalFenceWins=true "+
            "terminalClaimSerialized=true "+
            "healthyFinishClaimRevalidated=true "+
            "progressGenerationClaimRevalidated=true "+
            "timeoutClaimSerialized=true "+
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
        assertWatchdogCancelFailureOrdering()
        throws Exception{
        RuntimeException responseRuntime=
            new IllegalStateException(
                "fixture-response-before-cancel-runtime"
            );
        RuntimeException cancelRuntime=
            new SecurityException(
                "fixture-deadline-cancel-runtime"
            );
        ManualScheduler runtimeScheduler=
            new ManualScheduler();
        runtimeScheduler.cancelFailure=
            cancelRuntime;
        LocalAuxResponseLiveness runtimeLiveness=
            LocalAuxResponseLiveness.arm(
                new FakeSocket(),
                runtimeScheduler
            );

        Throwable runtimeObserved=
            runtimeLiveness.finish(
                responseRuntime
            );

        assertPrimaryWithSingleSuppressed(
            "response RuntimeException + deadline cancel RuntimeException",
            runtimeObserved,
            responseRuntime,
            cancelRuntime
        );

        if(!runtimeScheduler.cancelCalled||
           !runtimeScheduler.shutdown||
           !runtimeScheduler.awaited)
            throw new AssertionError(
                "deadline cancel RuntimeException skipped later watchdog retirement"
            );

        IOException responseIo=
            new IOException(
                "fixture-response-before-cancel-io"
            );
        RuntimeException cancelOverIo=
            new SecurityException(
                "fixture-deadline-cancel-over-io"
            );
        ManualScheduler ioScheduler=
            new ManualScheduler();
        ioScheduler.cancelFailure=
            cancelOverIo;
        LocalAuxResponseLiveness ioLiveness=
            LocalAuxResponseLiveness.arm(
                new FakeSocket(),
                ioScheduler
            );

        Throwable ioObserved=
            ioLiveness.finish(
                responseIo
            );

        assertPrimaryWithSingleSuppressed(
            "response IOException + deadline cancel RuntimeException",
            ioObserved,
            cancelOverIo,
            responseIo
        );

        if(!ioScheduler.cancelCalled||
           !ioScheduler.shutdown||
           !ioScheduler.awaited)
            throw new AssertionError(
                "deadline cancel-over-I/O skipped later watchdog retirement"
            );

        Error cancelError=
            new AssertionError(
                "fixture-deadline-cancel-error"
            );
        ManualScheduler errorScheduler=
            new ManualScheduler();
        errorScheduler.cancelFailure=
            cancelError;
        LocalAuxResponseLiveness errorLiveness=
            LocalAuxResponseLiveness.arm(
                new FakeSocket(),
                errorScheduler
            );

        Throwable errorObserved=
            errorLiveness.finish(
                null
            );

        if(errorObserved!=cancelError||
           !errorScheduler.cancelCalled||
           !errorScheduler.shutdown||
           !errorScheduler.awaited)
            throw new AssertionError(
                "clean response did not preserve exact deadline cancel Error and continue retirement",
                errorObserved
            );
    }

    private static void
        assertWatchdogCancelFailureDoesNotSkipLaterRetirement()
        throws Exception{
        RuntimeException cancel=
            new SecurityException(
                "fixture-deadline-cancel-before-shutdown"
            );
        RuntimeException shutdown=
            new IllegalStateException(
                "fixture-watchdog-shutdown-after-cancel"
            );
        ManualScheduler scheduler=
            new ManualScheduler();
        scheduler.cancelFailure=
            cancel;
        scheduler.shutdownFailure=
            shutdown;
        scheduler.terminates=false;

        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                new FakeSocket(),
                scheduler
            );

        Throwable observed=
            liveness.finish(
                null
            );

        if(observed!=cancel||
           !scheduler.cancelCalled||
           !scheduler.shutdown||
           !scheduler.awaited)
            throw new AssertionError(
                "deadline cancel failure skipped shutdown or bounded await",
                observed
            );

        Throwable[] suppressed=
            observed.getSuppressed();

        if(suppressed.length!=2||
           suppressed[0]!=shutdown||
           !(suppressed[1]
                instanceof IllegalStateException)||
           suppressed[1].getMessage()==null||
           !suppressed[1].getMessage()
                .contains(
                    "did not terminate"
                ))
            throw new AssertionError(
                "cancel/shutdown/nontermination failure ordering mismatch"
            );
    }

    private static void
        assertWatchdogCancelSameFailureDoesNotSelfSuppress()
        throws Exception{
        RuntimeException same=
            new SecurityException(
                "fixture-same-response-deadline-cancel"
            );
        ManualScheduler scheduler=
            new ManualScheduler();
        scheduler.cancelFailure=
            same;

        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                new FakeSocket(),
                scheduler
            );

        Throwable observed=
            liveness.finish(
                same
            );

        if(observed!=same||
           observed.getSuppressed().length!=0||
           !scheduler.cancelCalled||
           !scheduler.shutdown||
           !scheduler.awaited)
            throw new AssertionError(
                "same response/deadline cancel failure self-suppressed or skipped retirement",
                observed
            );
    }

    private static void
        assertWatchdogShutdownFailureOrdering()
        throws Exception{
        RuntimeException responseRuntime=
            new IllegalStateException(
                "fixture-response-runtime-primary"
            );
        RuntimeException shutdownRuntime=
            new SecurityException(
                "fixture-watchdog-shutdown-runtime"
            );
        ManualScheduler runtimeScheduler=
            new ManualScheduler();
        runtimeScheduler.shutdownFailure=
            shutdownRuntime;
        LocalAuxResponseLiveness runtimeLiveness=
            LocalAuxResponseLiveness.arm(
                new FakeSocket(),
                runtimeScheduler
            );

        Throwable runtimeObserved=
            runtimeLiveness.finish(
                responseRuntime
            );

        assertPrimaryWithSingleSuppressed(
            "response RuntimeException + watchdog shutdown RuntimeException",
            runtimeObserved,
            responseRuntime,
            shutdownRuntime
        );

        if(!runtimeScheduler.awaited)
            throw new AssertionError(
                "watchdog retirement was not awaited after shutdown RuntimeException"
            );

        IOException responseIo=
            new IOException(
                "fixture-response-io-primary"
            );
        RuntimeException shutdownOverIo=
            new SecurityException(
                "fixture-watchdog-shutdown-over-io"
            );
        ManualScheduler ioScheduler=
            new ManualScheduler();
        ioScheduler.shutdownFailure=
            shutdownOverIo;
        LocalAuxResponseLiveness ioLiveness=
            LocalAuxResponseLiveness.arm(
                new FakeSocket(),
                ioScheduler
            );

        Throwable ioObserved=
            ioLiveness.finish(
                responseIo
            );

        assertPrimaryWithSingleSuppressed(
            "response IOException + watchdog shutdown RuntimeException",
            ioObserved,
            shutdownOverIo,
            responseIo
        );

        if(!ioScheduler.awaited)
            throw new AssertionError(
                "watchdog retirement was not awaited after shutdown-over-I/O failure"
            );

        Error shutdownError=
            new AssertionError(
                "fixture-watchdog-shutdown-error"
            );
        ManualScheduler errorScheduler=
            new ManualScheduler();
        errorScheduler.shutdownFailure=
            shutdownError;
        LocalAuxResponseLiveness errorLiveness=
            LocalAuxResponseLiveness.arm(
                new FakeSocket(),
                errorScheduler
            );

        Throwable errorObserved=
            errorLiveness.finish(
                null
            );

        if(errorObserved!=shutdownError||
           !errorScheduler.awaited)
            throw new AssertionError(
                "clean response did not preserve exact watchdog shutdown Error and await retirement",
                errorObserved
            );
    }

    private static void
        assertWatchdogShutdownFailureStillAwaitsRetirement()
        throws Exception{
        RuntimeException shutdown=
            new SecurityException(
                "fixture-watchdog-shutdown-nontermination"
            );
        ManualScheduler nonterminating=
            new ManualScheduler();
        nonterminating.shutdownFailure=
            shutdown;
        nonterminating.terminates=false;
        LocalAuxResponseLiveness first=
            LocalAuxResponseLiveness.arm(
                new FakeSocket(),
                nonterminating
            );

        Throwable nonterminationObserved=
            first.finish(
                null
            );

        if(nonterminationObserved!=shutdown||
           !nonterminating.awaited||
           nonterminationObserved.getSuppressed().length!=1||
           !(nonterminationObserved.getSuppressed()[0]
                instanceof IllegalStateException)||
           nonterminationObserved.getSuppressed()[0]
                .getMessage()==null||
           !nonterminationObserved.getSuppressed()[0]
                .getMessage()
                .contains(
                    "did not terminate"
                ))
            throw new AssertionError(
                "watchdog shutdown failure skipped/lost nontermination evidence",
                nonterminationObserved
            );

        RuntimeException interruptedShutdown=
            new SecurityException(
                "fixture-watchdog-shutdown-interrupted-await"
            );
        ManualScheduler interrupted=
            new ManualScheduler();
        interrupted.shutdownFailure=
            interruptedShutdown;
        interrupted.interruptAwait=true;
        LocalAuxResponseLiveness second=
            LocalAuxResponseLiveness.arm(
                new FakeSocket(),
                interrupted
            );

        Throwable interruptedObserved=
            second.finish(
                null
            );
        boolean interruptRestored=
            Thread.currentThread()
                .isInterrupted();

        Thread.interrupted();

        if(interruptedObserved!=
                interruptedShutdown||
           !interrupted.awaited||
           !interruptRestored||
           interruptedObserved
                .getSuppressed().length!=1||
           !(interruptedObserved
                .getSuppressed()[0]
                instanceof IllegalStateException)||
           interruptedObserved
                .getSuppressed()[0]
                .getMessage()==null||
           !interruptedObserved
                .getSuppressed()[0]
                .getMessage()
                .contains(
                    "interrupted"
                ))
            throw new AssertionError(
                "watchdog shutdown failure skipped/lost interrupted join evidence",
                interruptedObserved
            );
    }

    private static void
        assertWatchdogShutdownSameFailureDoesNotSelfSuppress()
        throws Exception{
        RuntimeException same=
            new SecurityException(
                "fixture-same-response-watchdog-shutdown"
            );
        ManualScheduler scheduler=
            new ManualScheduler();
        scheduler.shutdownFailure=
            same;
        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                new FakeSocket(),
                scheduler
            );

        Throwable observed=
            liveness.finish(
                same
            );

        if(observed!=same||
           observed.getSuppressed().length!=0||
           !scheduler.awaited)
            throw new AssertionError(
                "same response/watchdog shutdown failure self-suppressed or skipped await",
                observed
            );
    }

    private static void assertPrimaryWithSingleSuppressed(
        String label,
        Throwable observed,
        Throwable expectedPrimary,
        Throwable expectedSuppressed
    ){
        if(observed!=expectedPrimary)
            throw new AssertionError(
                label+
                " primary identity mismatch",
                observed
            );

        Throwable[] suppressed=
            observed.getSuppressed();

        if(suppressed.length!=1||
           suppressed[0]!=expectedSuppressed)
            throw new AssertionError(
                label+
                " suppression ordering mismatch"
            );
    }

    private static void
        assertTimeoutEvidenceSurvivesWatchdogShutdownFailure()
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

        scheduler.trigger();

        if(!liveness.timedOut())
            throw new AssertionError(
                "fixture timeout did not commit before watchdog shutdown failure"
            );

        RuntimeException shutdown=
            new SecurityException(
                "fixture-watchdog-shutdown-after-timeout"
            );
        scheduler.shutdownFailure=
            shutdown;

        Throwable observed=
            liveness.finish(
                null
            );

        if(observed!=shutdown||
           !scheduler.awaited||
           observed.getSuppressed().length!=1||
           !(observed.getSuppressed()[0]
                instanceof SocketTimeoutException))
            throw new AssertionError(
                "watchdog shutdown failure erased durable timeout evidence",
                observed
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
                commit->false,
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
        assertTerminalWinsBetweenValidationAndTimeoutClaim()
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
        CountDownLatch claimReached=
            new CountDownLatch(1);
        CountDownLatch releaseClaim=
            new CountDownLatch(1);
        AtomicInteger publications=
            new AtomicInteger();
        AtomicReference<Throwable> triggerFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> terminalFailure=
            new AtomicReference<>();

        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler,
                commit->{
                    claimReached.countDown();

                    try{
                        releaseClaim.await();
                    }catch(InterruptedException error){
                        Thread.currentThread()
                            .interrupt();
                        throw new IllegalStateException(
                            "fixture timeout claim interrupted",
                            error
                        );
                    }

                    return coordinator
                        .claimAuxiliaryResponseTimeout(
                            commit
                        );
                },
                failure->
                    publications.incrementAndGet()
            );

        Thread trigger=
            new Thread(
                ()->{
                    try{
                        scheduler.trigger();
                    }catch(Throwable failure){
                        triggerFailure.set(
                            failure
                        );
                    }
                },
                "fixture-terminal-before-timeout-claim"
            );
        Thread terminal=
            new Thread(
                ()->{
                    try{
                        coordinator.close();
                    }catch(Throwable failure){
                        terminalFailure.set(
                            failure
                        );
                    }
                },
                "fixture-terminal-claim-owner"
            );

        try{
            trigger.start();

            if(!claimReached.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "watchdog did not reach pre-claim pause"
                );

            if(coordinator.closing())
                throw new AssertionError(
                    "terminal fence published before fixture requested it"
                );

            terminal.start();

            long deadline=
                System.nanoTime()+
                TimeUnit.SECONDS.toNanos(
                    5
                );

            while(!coordinator.closing()&&
                  System.nanoTime()<deadline)
                Thread.yield();

            if(!coordinator.closing())
                throw new AssertionError(
                    "terminal fence did not publish while timeout claim was paused"
                );

            releaseClaim.countDown();

            trigger.join(
                5_000L
            );
            terminal.join(
                5_000L
            );

            if(trigger.isAlive()||
               terminal.isAlive())
                throw new AssertionError(
                    "terminal-before-timeout-claim fixture did not converge"
                );

            Throwable finishFailure=
                liveness.finish(
                    null
                );

            if(triggerFailure.get()!=null||
               terminalFailure.get()!=null||
               finishFailure!=null||
               liveness.timedOut()||
               socket.closeCalls!=0||
               publications.get()!=0)
                throw new AssertionError(
                    "terminal-first ordering fabricated timeout authority",
                    triggerFailure.get()!=null
                        ?triggerFailure.get()
                        :finishFailure
                );
        }finally{
            releaseClaim.countDown();
            trigger.join(
                1_000L
            );
            terminal.join(
                1_000L
            );

            if(!coordinator.closing())
                try{
                    coordinator.close();
                }catch(Throwable ignored){
                }
        }
    }

    private static void
        assertHealthyFinishWinsBeforeTimeoutClaim()
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
        CountDownLatch claimReached=
            new CountDownLatch(1);
        CountDownLatch releaseClaim=
            new CountDownLatch(1);
        AtomicInteger publications=
            new AtomicInteger();
        AtomicReference<Throwable> triggerFailure=
            new AtomicReference<>();

        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler,
                commit->{
                    claimReached.countDown();

                    try{
                        releaseClaim.await();
                    }catch(InterruptedException error){
                        Thread.currentThread()
                            .interrupt();
                        throw new IllegalStateException(
                            "fixture healthy-finish claim interrupted",
                            error
                        );
                    }

                    return coordinator
                        .claimAuxiliaryResponseTimeout(
                            commit
                        );
                },
                failure->
                    publications.incrementAndGet()
            );

        Thread trigger=
            new Thread(
                ()->{
                    try{
                        scheduler.trigger();
                    }catch(Throwable failure){
                        triggerFailure.set(
                            failure
                        );
                    }
                },
                "fixture-healthy-finish-before-timeout-claim"
            );

        try{
            trigger.start();

            if(!claimReached.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "watchdog did not reach healthy-finish pre-claim pause"
                );

            Throwable finishFailure=
                liveness.finish(
                    null
                );

            if(finishFailure!=null)
                throw new AssertionError(
                    "healthy finish failed before timeout claim",
                    finishFailure
                );

            releaseClaim.countDown();

            trigger.join(
                5_000L
            );

            if(trigger.isAlive())
                throw new AssertionError(
                    "healthy-finish claim fixture did not converge"
                );

            if(triggerFailure.get()!=null||
               liveness.timedOut()||
               socket.closeCalls!=0||
               publications.get()!=0)
                throw new AssertionError(
                    "healthy finish was converted into timeout after precheck",
                    triggerFailure.get()
                );
        }finally{
            releaseClaim.countDown();
            trigger.join(
                1_000L
            );

            try{
                coordinator.close();
            }catch(Throwable ignored){
            }
        }
    }

    private static void
        assertProgressWinsBeforeStaleTimeoutClaim()
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
        CountDownLatch firstClaimReached=
            new CountDownLatch(1);
        CountDownLatch releaseFirstClaim=
            new CountDownLatch(1);
        AtomicInteger claimCalls=
            new AtomicInteger();
        AtomicInteger publications=
            new AtomicInteger();
        AtomicReference<Throwable> triggerFailure=
            new AtomicReference<>();

        LocalAuxResponseLiveness liveness=
            LocalAuxResponseLiveness.arm(
                socket,
                scheduler,
                commit->{
                    int call=
                        claimCalls.incrementAndGet();

                    if(call==1){
                        firstClaimReached.countDown();

                        try{
                            releaseFirstClaim.await();
                        }catch(InterruptedException error){
                            Thread.currentThread()
                                .interrupt();
                            throw new IllegalStateException(
                                "fixture progress claim interrupted",
                                error
                            );
                        }
                    }

                    return coordinator
                        .claimAuxiliaryResponseTimeout(
                            commit
                        );
                },
                failure->
                    publications.incrementAndGet()
            );
        OutputStream out=
            liveness.output(
                new ByteArrayOutputStream()
            );

        Thread staleTrigger=
            new Thread(
                ()->{
                    try{
                        scheduler.trigger();
                    }catch(Throwable failure){
                        triggerFailure.set(
                            failure
                        );
                    }
                },
                "fixture-progress-before-stale-timeout-claim"
            );

        try{
            staleTrigger.start();

            if(!firstClaimReached.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "stale deadline did not reach pre-claim pause"
                );

            int schedulesBeforeProgress=
                scheduler.scheduleCalls;

            out.write(
                1
            );

            if(scheduler.scheduleCalls!=
                    schedulesBeforeProgress+1||
               scheduler.outstandingTasks()!=1)
                throw new AssertionError(
                    "successful progress did not arm exactly one fresh deadline"
                );

            releaseFirstClaim.countDown();

            staleTrigger.join(
                5_000L
            );

            if(staleTrigger.isAlive())
                throw new AssertionError(
                    "stale timeout claim did not converge after progress"
                );

            if(triggerFailure.get()!=null||
               liveness.timedOut()||
               socket.closeCalls!=0||
               publications.get()!=0||
               scheduler.outstandingTasks()!=1)
                throw new AssertionError(
                    "stale generation claimed timeout after successful progress",
                    triggerFailure.get()
                );

            scheduler.trigger();

            if(!liveness.timedOut()||
               !socket.isClosed()||
               socket.closeCalls!=1||
               claimCalls.get()!=2)
                throw new AssertionError(
                    "fresh generation did not retain normal timeout behavior"
                );

            Throwable finishFailure=
                liveness.finish(
                    null
                );

            if(!(finishFailure instanceof
                    SocketTimeoutException))
                throw new AssertionError(
                    "fresh generation timeout evidence missing",
                    finishFailure
                );
        }finally{
            releaseFirstClaim.countDown();
            staleTrigger.join(
                1_000L
            );

            try{
                coordinator.close();
            }catch(Throwable ignored){
            }
        }
    }

    private static void
        assertTimeoutClaimWinsBeforeTerminalFence()
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
        CountDownLatch timeoutCommitted=
            new CountDownLatch(1);

        try{
            Socket accepted=
                coordinator
                    .acceptAuxiliarySocket(
                        ()->socket
                    );

            if(accepted!=socket||
               coordinator.activeAuxiliarySocketCount()!=1)
                throw new AssertionError(
                    "timeout-first fixture did not establish AUX socket ownership"
                );

            LocalAuxResponseLiveness liveness=
                LocalAuxResponseLiveness.arm(
                    socket,
                    scheduler,
                    commit->
                        coordinator
                            .claimAuxiliaryResponseTimeout(
                                ()->{
                                    boolean claimed=
                                        commit.getAsBoolean();

                                    if(claimed)
                                        timeoutCommitted
                                            .countDown();

                                    return claimed;
                                }
                            ),
                    failure->{
                        throw new AssertionError(
                            "successful timeout abort unexpectedly published worker-fatal failure",
                            failure
                        );
                    }
                );

            Thread trigger=
                new Thread(
                    scheduler::trigger,
                    "fixture-timeout-before-terminal"
                );
            trigger.start();

            if(!timeoutCommitted.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "timeout claim did not commit before terminal fence"
                );

            trigger.join(
                5_000L
            );

            if(trigger.isAlive())
                throw new AssertionError(
                    "timeout-first watchdog did not finish bounded abort"
                );

            if(!liveness.timedOut()||
               !socket.isClosed()||
               socket.closeCalls!=1||
               coordinator.activeAuxiliarySocketCount()!=1)
                throw new AssertionError(
                    "timeout-first claim did not retain timeout evidence and coordinator ownership"
                );

            Throwable terminalFailure=null;

            try{
                coordinator.close();
            }catch(Throwable failure){
                terminalFailure=failure;
            }

            Throwable responseFailure=
                liveness.finish(
                    null
                );

            if(terminalFailure!=null||
               !(responseFailure instanceof SocketTimeoutException)||
               !liveness.timedOut()||
               coordinator.activeAuxiliarySocketCount()!=0)
                throw new AssertionError(
                    "later terminal cleanup erased timeout truth or retained AUX ownership",
                    terminalFailure!=null
                        ?terminalFailure
                        :responseFailure
                );
        }finally{
            if(!coordinator.closing())
                try{
                    coordinator.close();
                }catch(Throwable ignored){
                }
        }
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
        boolean cancelCalled;
        boolean shutdown;
        boolean awaited;
        boolean terminates=true;
        boolean interruptAwait;
        Throwable cancelFailure;
        Throwable shutdownFailure;

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
                    cancelCalled=true;

                    if(token==mine)
                        current=null;

                    if(cancelFailure
                            instanceof RuntimeException)
                        throw (RuntimeException)
                            cancelFailure;

                    if(cancelFailure
                            instanceof Error)
                        throw (Error)
                            cancelFailure;
                }
            };
        }

        void trigger(){
            Runnable task;

            synchronized(this){
                task=current;
                current=null;
            }

            if(task!=null)
                task.run();
        }

        @Override public synchronized void shutdownNow(){
            shutdown=true;
            current=null;

            if(shutdownFailure instanceof RuntimeException)
                throw (RuntimeException)shutdownFailure;

            if(shutdownFailure instanceof Error)
                throw (Error)shutdownFailure;
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
