package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalAuxResponseLivenessTest {
    public static void main(
        String[] args
    )throws Exception{
        assertArmedBeforeFirstWriteAndHealthyCompletionCancels();
        assertProgressRefreshesSingleDeadline();
        assertTimeoutAbortsBlockedWriteAndWorkerContinues();
        assertTerminalCloseWinsWithoutSyntheticTimeout();
        assertSuccessfulTimeoutKeepsOwnershipUntilWorkerRelease();
        assertAbortFailurePreservesCoordinatorOwnership();
        assertLargeStreamingProgressIsNotTotalDurationBounded();

        System.out.println(
            "LOCAL_AUX_RESPONSE_LIVENESS_PASS "+
            "armedBeforeFirstWrite=true "+
            "progressRefreshes=true "+
            "singleOutstandingDeadline=true "+
            "stalledWriteAborted=true "+
            "timeoutConnectionScoped=true "+
            "sameWorkerContinues=true "+
            "terminalWins=true "+
            "watchdogRetired=true "+
            "successfulTimeoutOwnershipHeld=true "+
            "failedAbortOwnershipRetained=true "+
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

        if(timeoutSchedulers.get()!=1||
           connectionFailure.get()==null||
           !first.isClosed()||
           first.closeCalls!=1||
           secondHandled.get()!=1)
            throw new AssertionError(
                "timed-out response did not remain connection-scoped or worker did not continue"
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

            if(failure!=null)
                throw new AssertionError(
                    "successful timeout produced cleanup failure",
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

            IOException abortFailure=
                new IOException(
                    "fixture-timeout-close-failure"
                );
            socket.closeFailure=
                abortFailure;

            LocalAuxResponseLiveness liveness=
                LocalAuxResponseLiveness.arm(
                    socket,
                    scheduler
                );

            scheduler.trigger();

            Throwable observed=
                liveness.finish(
                    null
                );

            if(observed!=abortFailure||
               coordinator.activeAuxiliarySocketCount()!=1||
               socket.isClosed())
                throw new AssertionError(
                    "failed timeout abort retired coordinator ownership or lost close failure",
                    observed
                );

            socket.closeFailure=null;
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
        ){
            awaited=true;
            return true;
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
        IOException closeFailure;

        @Override public synchronized void close()
            throws IOException{
            closeCalls++;

            if(closeFailure!=null)
                throw closeFailure;

            closed=true;
        }

        @Override public synchronized boolean isClosed(){
            return closed;
        }
    }

    private LocalAuxResponseLivenessTest(){}
}
