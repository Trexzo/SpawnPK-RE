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

    interface TimeoutAuthority {
        boolean claim(
            BooleanSupplier commit
        );
    }

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
    private final TimeoutAuthority timeoutAuthority;
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
            commit->commit.getAsBoolean(),
            failure->{}
        );
    }

    static LocalAuxResponseLiveness arm(
        Socket socket,
        Consumer<Throwable> fatalFailure
    ){
        return arm(
            socket,
            commit->commit.getAsBoolean(),
            fatalFailure
        );
    }

    static LocalAuxResponseLiveness arm(
        Socket socket,
        TimeoutAuthority timeoutAuthority,
        Consumer<Throwable> fatalFailure
    ){
        return new LocalAuxResponseLiveness(
            socket,
            new ExecutorScheduler(),
            timeoutAuthority,
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
            commit->commit.getAsBoolean(),
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
            commit->commit.getAsBoolean(),
            fatalFailure
        );
    }

    static LocalAuxResponseLiveness arm(
        Socket socket,
        Scheduler scheduler,
        TimeoutAuthority timeoutAuthority,
        Consumer<Throwable> fatalFailure
    ){
        return new LocalAuxResponseLiveness(
            socket,
            scheduler,
            timeoutAuthority,
            fatalFailure
        );
    }

    private LocalAuxResponseLiveness(
        Socket socket,
        Scheduler scheduler,
        TimeoutAuthority timeoutAuthority,
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
        this.timeoutAuthority=
            Objects.requireNonNull(
                timeoutAuthority,
                "timeoutAuthority"
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

        Throwable result=
            primary;

        if(pending!=null)
            try{
                pending.cancel();
            }catch(RuntimeException|
                   Error cancelFailure){
                result=
                    LocalAuxHttpWorker
                        .preserveFailureOrder(
                            result,
                            cancelFailure
                        );
            }

        try{
            scheduler.shutdownNow();
        }catch(RuntimeException|
               Error shutdownFailure){
            result=
                LocalAuxHttpWorker
                    .preserveFailureOrder(
                        result,
                        shutdownFailure
                    );
        }

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
        synchronized(lock){
            if(finished||
               expected!=generation)
                return;

            deadline=null;

            if(socket.isClosed())
                return;
        }

        final SocketTimeoutException[] claimedTimeout=
            new SocketTimeoutException[1];

        boolean claimed=
            timeoutAuthority.claim(
                ()->{
                    synchronized(lock){
                        if(finished||
                           expected!=generation||
                           socket.isClosed())
                            return false;

                        timedOut=true;
                        timeoutFailure=
                            new SocketTimeoutException(
                                "auxiliary HTTP response made no progress before deadline"
                            );
                        claimedTimeout[0]=
                            timeoutFailure;
                        return true;
                    }
                }
            );

        if(!claimed)
            return;

        SocketTimeoutException timeout=
            claimedTimeout[0];

        if(timeout==null)
            throw new IllegalStateException(
                "auxiliary response timeout authority committed without timeout evidence"
            );

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

}
