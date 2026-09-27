package spk.local;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class LocalAuxResponseDeadline
    implements AutoCloseable {

    static final long DEFAULT_TIMEOUT_MILLIS=
        30_000L;

    interface AbortAction {
        void abort()
            throws Exception;
    }

    interface WaitStrategy {
        boolean await(
            CountDownLatch completed,
            long timeoutMillis
        )throws InterruptedException;
    }

    interface ThreadFactory {
        Thread create(
            Runnable target,
            String name
        );
    }

    interface Factory {
        LocalAuxResponseDeadline start(
            AbortAction abort
        );
    }

    private static final long JOIN_TIMEOUT_MILLIS=
        2_000L;

    private final CountDownLatch completed=
        new CountDownLatch(
            1
        );
    private final AtomicBoolean timedOut=
        new AtomicBoolean();
    private final AtomicReference<Throwable>
        abortFailure=
            new AtomicReference<>();
    private final Thread watchdog;

    static Factory production(){
        return production(
            DEFAULT_TIMEOUT_MILLIS
        );
    }

    static Factory production(
        long timeoutMillis
    ){
        if(timeoutMillis<=0L)
            throw new IllegalArgumentException(
                "timeoutMillis"
            );

        return abort->
            start(
                timeoutMillis,
                abort,
                (completed,timeout)->
                    completed.await(
                        timeout,
                        TimeUnit.MILLISECONDS
                    ),
                (target,name)->
                    new Thread(
                        target,
                        name
                    )
            );
    }

    static LocalAuxResponseDeadline start(
        long timeoutMillis,
        AbortAction abort,
        WaitStrategy waiter,
        ThreadFactory threadFactory
    ){
        if(timeoutMillis<=0L)
            throw new IllegalArgumentException(
                "timeoutMillis"
            );

        return new LocalAuxResponseDeadline(
            timeoutMillis,
            abort,
            waiter,
            threadFactory
        );
    }

    private LocalAuxResponseDeadline(
        long timeoutMillis,
        AbortAction abort,
        WaitStrategy waiter,
        ThreadFactory threadFactory
    ){
        Objects.requireNonNull(
            abort,
            "abort"
        );
        Objects.requireNonNull(
            waiter,
            "waiter"
        );
        Objects.requireNonNull(
            threadFactory,
            "threadFactory"
        );

        watchdog=
            Objects.requireNonNull(
                threadFactory.create(
                    ()->watch(
                        timeoutMillis,
                        abort,
                        waiter
                    ),
                    "spawnpk-local-aux-response-deadline"
                ),
                "watchdog"
            );

        watchdog.setDaemon(
            true
        );
        watchdog.start();
    }

    void complete(){
        completed.countDown();
    }

    boolean timedOut(){
        return timedOut.get();
    }

    boolean watchdogAlive(){
        return watchdog.isAlive();
    }

    Throwable abortFailure(){
        return abortFailure.get();
    }

    @Override public void close()
        throws IOException{
        completed.countDown();

        boolean interrupted=false;

        try{
            watchdog.join(
                JOIN_TIMEOUT_MILLIS
            );

            if(watchdog.isAlive()){
                watchdog.interrupt();
                watchdog.join(
                    JOIN_TIMEOUT_MILLIS
                );
            }
        }catch(InterruptedException failure){
            interrupted=true;
            watchdog.interrupt();

            try{
                watchdog.join(
                    JOIN_TIMEOUT_MILLIS
                );
            }catch(InterruptedException second){
                interrupted=true;
            }
        }finally{
            if(interrupted)
                Thread.currentThread()
                    .interrupt();
        }

        if(watchdog.isAlive())
            throw new IOException(
                "AUX response deadline watchdog did not terminate"
            );

        if(!timedOut.get())
            return;

        SocketTimeoutException timeout=
            new SocketTimeoutException(
                "auxiliary HTTP response deadline exceeded"
            );
        Throwable abortError=
            abortFailure.get();

        if(abortError!=null)
            timeout.addSuppressed(
                abortError
            );

        throw timeout;
    }

    private void watch(
        long timeoutMillis,
        AbortAction abort,
        WaitStrategy waiter
    ){
        final boolean finished;

        try{
            finished=
                waiter.await(
                    completed,
                    timeoutMillis
                );
        }catch(InterruptedException interrupted){
            Thread.currentThread()
                .interrupt();
            return;
        }catch(Throwable failure){
            abortFailure.compareAndSet(
                null,
                failure
            );
            return;
        }

        if(finished||
           completed.getCount()==0L)
            return;

        if(!timedOut.compareAndSet(
                false,
                true
            ))
            return;

        try{
            abort.abort();
        }catch(Throwable failure){
            abortFailure.compareAndSet(
                null,
                failure
            );
        }
    }

    private LocalAuxResponseDeadline(){}
}
