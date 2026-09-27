package spk.local;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalAuxiliaryWorkerFailureTest {
    public static void main(String[] args)
        throws Exception{
        assertPendingFailureFailsFreshAccept();
        assertAcceptedHandoffCannotCommitAfterFailure();
        assertWakeFailureSuppressedAndRetried();
        assertFailedOpenWakeStillBlocksServing();
        assertDuplicateFailureKeepsFirstPrimary();
        assertCleanAuxiliaryExitDoesNotFabricateFailure();

        System.out.println(
            "LOCAL_AUXILIARY_WORKER_FAILURE_PASS "+
            "caughtAtTaskBoundary=true "+
            "freshAcceptFailsFast=true "+
            "acceptedHandoffRejected=true "+
            "workerFailureIdentity=true "+
            "wakeCloseAttempted=true "+
            "wakeCloseRetry=true "+
            "wakeFailureSuppressed=true "+
            "failedOpenStillAuthoritative=true "+
            "duplicateKeepsFirst=true "+
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
