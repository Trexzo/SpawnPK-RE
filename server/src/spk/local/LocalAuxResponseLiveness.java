package spk.local;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

final class LocalAuxResponseLiveness {
    static final long NO_PROGRESS_TIMEOUT_MILLIS=
        5_000L;
    static final long WATCHDOG_JOIN_MILLIS=
        1_000L;

    interface Cancellable {
        void cancel();
    }

    interface Scheduler {
        Cancellable schedule(
            Runnable task,
            long delayMillis
        );

        void shutdownNow();

        boolean awaitTermination(
            long timeoutMillis
        )throws InterruptedException;

        int outstandingTasks();
    }

    private final Object lock=
        new Object();
    private final Socket socket;
    private final Scheduler scheduler;
    private final BooleanSupplier terminalClosing;
    private final Consumer<Throwable> fatalFailure;

    private Cancellable deadline;
    private long generation;
    private boolean finished;
    private boolean timedOut;
    private SocketTimeoutException timeoutFailure;

    static LocalAuxResponseLiveness arm(
        Socket socket
    ){
        return arm(
            socket,
            ()->false,
            failure->{}
        );
    }

    static LocalAuxResponseLiveness arm(
        Socket socket,
        Consumer<Throwable> fatalFailure
    ){
        return arm(
            socket,
            ()->false,
            fatalFailure
        );
    }

    static LocalAuxResponseLiveness arm(
        Socket socket,
        BooleanSupplier terminalClosing,
        Consumer<Throwable> fatalFailure
    ){
        return new LocalAuxResponseLiveness(
            socket,
            new ExecutorScheduler(),
            terminalClosing,
            fatalFailure
        );
    }

    static LocalAuxResponseLiveness arm(
        Socket socket,
        Scheduler scheduler
    ){
        return arm(
            socket,
            scheduler,
            ()->false,
            failure->{}
        );
    }

    static LocalAuxResponseLiveness arm(
        Socket socket,
        Scheduler scheduler,
        Consumer<Throwable> fatalFailure
    ){
        return arm(
            socket,
            scheduler,
            ()->false,
            fatalFailure
        );
    }

    static LocalAuxResponseLiveness arm(
        Socket socket,
        Scheduler scheduler,
        BooleanSupplier terminalClosing,
        Consumer<Throwable> fatalFailure
    ){
        return new LocalAuxResponseLiveness(
            socket,
            scheduler,
            terminalClosing,
            fatalFailure
        );
    }

    private LocalAuxResponseLiveness(
        Socket socket,
        Scheduler scheduler,
        BooleanSupplier terminalClosing,
        Consumer<Throwable> fatalFailure
    ){
        this.socket=
            Objects.requireNonNull(
                socket,
                "socket"
            );
        this.scheduler=
            Objects.requireNonNull(
                scheduler,
                "scheduler"
            );
        this.terminalClosing=
            Objects.requireNonNull(
                terminalClosing,
                "terminalClosing"
            );
        this.fatalFailure=
            Objects.requireNonNull(
                fatalFailure,
                "fatalFailure"
            );

        synchronized(lock){
            scheduleDeadlineLocked();
        }
    }

    OutputStream output(
        OutputStream delegate
    ){
        Objects.requireNonNull(
            delegate,
            "delegate"
        );

        return new FilterOutputStream(
            delegate
        ){
            @Override public void write(
                int value
            )throws IOException{
                out.write(
                    value
                );
                progress();
            }

            @Override public void write(
                byte[] buffer
            )throws IOException{
                out.write(
                    buffer
                );
                progress();
            }

            @Override public void write(
                byte[] buffer,
                int offset,
                int length
            )throws IOException{
                out.write(
                    buffer,
                    offset,
                    length
                );
                progress();
            }

            @Override public void flush()
                throws IOException{
                out.flush();
                progress();
            }

            @Override public void close()
                throws IOException{
                out.close();
            }
        };
    }

    Throwable finish(
        Throwable primary
    ){
        Cancellable pending;

        synchronized(lock){
            if(!finished){
                finished=true;
                generation++;
            }

            pending=deadline;
            deadline=null;
        }

        if(pending!=null)
            pending.cancel();

        scheduler.shutdownNow();

        Throwable result=
            primary;
        SocketTimeoutException timeout;

        synchronized(lock){
            timeout=timeoutFailure;
        }

        if(timeout!=null)
            result=
                LocalAuxHttpWorker
                    .preserveFailureOrder(
                        result,
                        timeout
                    );

        try{
            if(!scheduler.awaitTermination(
                    WATCHDOG_JOIN_MILLIS))
                result=
                    LocalAuxHttpWorker
                        .preserveFailureOrder(
                            result,
                            new IllegalStateException(
                                "auxiliary response watchdog did not terminate"
                            )
                        );
        }catch(InterruptedException interrupted){
            Thread.currentThread()
                .interrupt();

            result=
                LocalAuxHttpWorker
                    .preserveFailureOrder(
                        result,
                        new IllegalStateException(
                            "interrupted while joining auxiliary response watchdog",
                            interrupted
                        )
                    );
        }

        return result;
    }

    boolean timedOut(){
        synchronized(lock){
            return timedOut;
        }
    }

    int outstandingTasks(){
        return scheduler.outstandingTasks();
    }

    private void progress(){
        synchronized(lock){
            if(finished||
               timedOut||
               socket.isClosed())
                return;

            if(deadline!=null)
                deadline.cancel();

            scheduleDeadlineLocked();
        }
    }

    private void scheduleDeadlineLocked(){
        long expected=
            ++generation;

        deadline=
            scheduler.schedule(
                ()->expire(
                    expected
                ),
                NO_PROGRESS_TIMEOUT_MILLIS
            );
    }

    private void expire(
        long expected
    ){
        SocketTimeoutException timeout;

        synchronized(lock){
            if(finished||
               expected!=generation)
                return;

            deadline=null;

            if(socket.isClosed()||
               terminalClosing.getAsBoolean())
                return;

            timedOut=true;
            timeout=
                new SocketTimeoutException(
                    "auxiliary HTTP response made no progress before deadline"
                );
            timeoutFailure=
                timeout;
        }

        Throwable first=
            closeSocketOnce();

        if(first!=null&&
           first!=timeout)
            timeout.addSuppressed(
                first
            );

        if(!socket.isClosed()){
            Throwable second=
                closeSocketOnce();

            if(second!=null&&
               second!=timeout&&
               second!=first)
                timeout.addSuppressed(
                    second
                );
        }

        if(socket.isClosed())
            return;

        IOException failedOpen=
            new IOException(
                "auxiliary response timeout could not close socket after bounded retry"
            );
        timeout.addSuppressed(
            failedOpen
        );

        try{
            fatalFailure.accept(
                timeout
            );
        }catch(Throwable publicationFailure){
            if(publicationFailure!=timeout)
                timeout.addSuppressed(
                    publicationFailure
                );
        }
    }

    private Throwable closeSocketOnce(){
        try{
            socket.close();
            return null;
        }catch(Throwable failure){
            return failure;
        }
    }

    private static final class ExecutorScheduler
        implements Scheduler {
        private final ScheduledThreadPoolExecutor
            executor;

        ExecutorScheduler(){
            ThreadFactory factory=
                runnable->{
                    Thread thread=
                        new Thread(
                            runnable,
                            "spk-local-aux-response-watchdog"
                        );
                    thread.setDaemon(
                        true
                    );
                    return thread;
                };

            executor=
                new ScheduledThreadPoolExecutor(
                    1,
                    factory
                );
            executor.setRemoveOnCancelPolicy(
                true
            );
            executor
                .setExecuteExistingDelayedTasksAfterShutdownPolicy(
                    false
                );
            executor
                .setContinueExistingPeriodicTasksAfterShutdownPolicy(
                    false
                );
        }

        @Override public Cancellable schedule(
            Runnable task,
            long delayMillis
        ){
            ScheduledFuture<?> future=
                executor.schedule(
                    task,
                    delayMillis,
                    TimeUnit.MILLISECONDS
                );

            return ()->
                future.cancel(
                    false
                );
        }

        @Override public void shutdownNow(){
            executor.shutdownNow();
        }

        @Override public boolean awaitTermination(
            long timeoutMillis
        )throws InterruptedException{
            return executor.awaitTermination(
                timeoutMillis,
                TimeUnit.MILLISECONDS
            );
        }

        @Override public int outstandingTasks(){
            return executor.getQueue()
                .size();
        }
    }

    private LocalAuxResponseLiveness(){}
}
