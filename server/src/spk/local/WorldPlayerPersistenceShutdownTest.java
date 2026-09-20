package spk.local;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldPlayerPersistenceShutdownTest {
    public static void main(String[] args)throws Exception{
        testForcedShutdownSettlesDroppedWork();
        testCleanShutdownPreservesCompletedSave();

        System.out.println(
            "WORLD_PLAYER_PERSISTENCE_SHUTDOWN_PASS "+
            "droppedFutureSettled=true "+
            "checkpointClassified=true "+
            "inFlightInterrupted=true "+
            "cleanShutdown=true"
        );
    }

    private static void testForcedShutdownSettlesDroppedWork()
        throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        BlockingRepository repository=
            new BlockingRepository();
        WorldPlayerPersistence persistence=
            new WorldPlayerPersistence(
                world,
                repository
            );

        try{
            world.registerPlayer(
                player,
                LocalAccountProfiles.PRIMARY
            );
            world.start();

            AtomicReference<
                WorldPlayerPersistence.SaveTicket
            > firstRef=new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->firstRef.set(
                    persistence.captureAndSave(
                        LocalAccountProfiles.PRIMARY,
                        player,
                        0,
                        "[shutdown-test] ",
                        "IN_FLIGHT"
                    )
                ),
                5_000L
            );

            if(!repository.started.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "blocking repository never started"
                );

            AtomicReference<
                WorldPlayerPersistence.SaveTicket
            > queuedRef=new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    queuedRef.set(
                        persistence.captureAndSave(
                            LocalAccountProfiles.PRIMARY,
                            player,
                            0,
                            "[shutdown-test] ",
                            "QUEUED_MANUAL"
                        )
                    );

                    persistence.checkpointDue(
                        WorldPlayerPersistence
                            .AUTOSAVE_INTERVAL_TICKS
                    );
                },
                5_000L
            );

            WorldPlayerPersistence.SaveTicket first=
                firstRef.get();
            WorldPlayerPersistence.SaveTicket queued=
                queuedRef.get();

            if(first==null||queued==null)
                throw new AssertionError(
                    "save tickets not captured"
                );

            if(first.completion.isDone())
                throw new AssertionError(
                    "in-flight save unexpectedly completed"
                );

            if(queued.completion.isDone())
                throw new AssertionError(
                    "queued save unexpectedly completed"
                );

            if(persistence.queuedWrites()!=2)
                throw new AssertionError(
                    "expected manual+checkpoint queued, got "+
                    persistence.queuedWrites()
                );

            if(persistence.checkpointCapturedCount()!=1)
                throw new AssertionError(
                    "checkpoint capture missing"
                );

            long started=System.nanoTime();
            persistence.close();
            long closeMillis=
                TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime()-started
                );

            if(!first.completion.isDone()||
               !first.completion
                    .isCompletedExceptionally())
                throw new AssertionError(
                    "interrupted in-flight save future not terminal"
                );

            if(!queued.completion.isDone()||
               !queued.completion
                    .isCompletedExceptionally())
                throw new AssertionError(
                    "dropped queued save future not terminal"
                );

            Throwable queuedFailure=
                failure(queued.completion);

            if(!(queuedFailure instanceof
                    RejectedExecutionException)||
               queuedFailure.getMessage()==null||
               !queuedFailure.getMessage().contains(
                    "SHUTDOWN_FORCED"))
                throw new AssertionError(
                    "queued save failure classification "+
                    queuedFailure
                );

            Throwable firstFailure=
                failure(first.completion);

            if(!(firstFailure instanceof
                    IOException))
                throw new AssertionError(
                    "in-flight save was not interrupted "+
                    firstFailure
                );

            if(persistence
                    .checkpointRejectedCount()!=1)
                throw new AssertionError(
                    "manual save misclassified as checkpoint rejection: "+
                    persistence.metrics()
                );

            if(persistence
                    .checkpointWrittenCount()!=0)
                throw new AssertionError(
                    "dropped checkpoint was written"
                );

            if(persistence.failedCount()!=3)
                throw new AssertionError(
                    "forced shutdown failure accounting "+
                    persistence.metrics()
                );

            if(persistence.queuedWrites()!=0)
                throw new AssertionError(
                    "shutdown left queued work"
                );

            if(closeMillis<4_000L)
                throw new AssertionError(
                    "test did not exercise forced-shutdown timeout path: "+
                    closeMillis+"ms"
                );
        }finally{
            repository.release.countDown();

            if(player.registered())
                world.unregisterPlayer(player);

            persistence.close();
            world.close();
        }
    }

    private static void testCleanShutdownPreservesCompletedSave()
        throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        ImmediateRepository repository=
            new ImmediateRepository();
        WorldPlayerPersistence persistence=
            new WorldPlayerPersistence(
                world,
                repository
            );

        try{
            world.registerPlayer(
                player,
                LocalAccountProfiles.PRIMARY
            );
            world.start();

            AtomicReference<
                WorldPlayerPersistence.SaveTicket
            > ticketRef=new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->ticketRef.set(
                    persistence.captureAndSave(
                        LocalAccountProfiles.PRIMARY,
                        player,
                        0,
                        "[shutdown-test] ",
                        "CLEAN"
                    )
                ),
                5_000L
            );

            WorldPlayerPersistence.SaveTicket ticket=
                ticketRef.get();

            if(ticket==null)
                throw new AssertionError(
                    "clean save ticket missing"
                );

            ticket.completion.get(
                5,
                TimeUnit.SECONDS
            );

            persistence.close();

            if(!ticket.completion.isDone()||
               ticket.completion
                    .isCompletedExceptionally())
                throw new AssertionError(
                    "clean save completion changed"
                );

            if(repository.saves.get()!=1||
               persistence.failedCount()!=0||
               persistence
                    .checkpointRejectedCount()!=0)
                throw new AssertionError(
                    "clean shutdown accounting "+
                    persistence.metrics()
                );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);

            persistence.close();
            world.close();
        }
    }

    private static Throwable failure(
        CompletableFuture<Void> future
    ){
        try{
            future.join();
            throw new AssertionError(
                "future unexpectedly succeeded"
            );
        }catch(CompletionException e){
            return e.getCause();
        }catch(CancellationException e){
            return e;
        }
    }

    private static final class BlockingRepository
        implements PlayerRepository {

        final CountDownLatch started=
            new CountDownLatch(1);
        final CountDownLatch release=
            new CountDownLatch(1);

        @Override public Optional<PlayerSnapshot> load(
            String username
        ){
            return Optional.empty();
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
            started.countDown();

            try{
                release.await();
            }catch(InterruptedException e){
                Thread.currentThread()
                    .interrupt();

                throw new IOException(
                    "blocked repository interrupted",
                    e
                );
            }
        }
    }

    private static final class ImmediateRepository
        implements PlayerRepository {

        final AtomicInteger saves=
            new AtomicInteger();

        @Override public Optional<PlayerSnapshot> load(
            String username
        ){
            return Optional.empty();
        }

        @Override public void save(
            PlayerSnapshot snapshot
        ){
            saves.incrementAndGet();
        }
    }

    private WorldPlayerPersistenceShutdownTest(){}
}