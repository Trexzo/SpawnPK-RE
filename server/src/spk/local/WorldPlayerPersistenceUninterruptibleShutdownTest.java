package spk.local;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldPlayerPersistenceUninterruptibleShutdownTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        UninterruptibleRepository repository=
            new UninterruptibleRepository();
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
                        "[uninterruptible-shutdown-test] ",
                        "IN_FLIGHT"
                    )
                ),
                5_000L
            );

            WorldPlayerPersistence.SaveTicket ticket=
                ticketRef.get();

            if(ticket==null)
                throw new AssertionError(
                    "save ticket missing"
                );

            if(!repository.started.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "repository save did not start"
                );

            long started=
                System.nanoTime();

            persistence.close();

            long closeMillis=
                TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime()-started
                );

            if(closeMillis<5_500L)
                throw new AssertionError(
                    "forced shutdown path not exercised closeMillis="+
                    closeMillis
                );

            if(repository.finished.getCount()==0L)
                throw new AssertionError(
                    "repository unexpectedly terminated during close"
                );

            if(repository.interrupts.get()==0)
                throw new AssertionError(
                    "shutdownNow did not interrupt active repository save"
                );

            if(!ticket.completion.isDone()||
               !ticket.completion
                    .isCompletedExceptionally())
                throw new AssertionError(
                    "in-flight ticket not terminal after close"
                );

            Throwable failure=
                failure(
                    ticket.completion
                );

            if(!(failure instanceof
                    RejectedExecutionException)||
               failure.getMessage()==null||
               !failure.getMessage().contains(
                    "SHUTDOWN_IN_FLIGHT"))
                throw new AssertionError(
                    "wrong in-flight shutdown failure "+
                    failure
                );

            if(persistence.failedCount()!=1)
                throw new AssertionError(
                    "unexpected failure accounting before release "+
                    persistence.metrics()
                );

            if(!persistence.metrics().contains(
                    "completed=0"))
                throw new AssertionError(
                    "in-flight shutdown counted completion early "+
                    persistence.metrics()
                );

            repository.release.countDown();

            if(!repository.finished.await(
                    3,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "repository did not finish after release"
                );

            // Join the already-shutdown worker after allowing the deliberately
            // uninterruptible repository call to return.
            persistence.close();

            if(!ticket.completion
                    .isCompletedExceptionally())
                throw new AssertionError(
                    "late repository return changed terminal ticket"
                );

            if(persistence.failedCount()!=1||
               !persistence.metrics().contains(
                   "completed=0"))
                throw new AssertionError(
                    "late repository return double-counted settlement "+
                    persistence.metrics()
                );

            System.out.println(
                "WORLD_PLAYER_PERSISTENCE_UNINTERRUPTIBLE_SHUTDOWN_PASS "+
                "ticketTerminalOnClose=true "+
                "interruptIgnored=true "+
                "lateReturnIgnored=true "+
                "failed=1 completed=0"
            );
        }finally{
            repository.release.countDown();

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

    private static final class UninterruptibleRepository
        implements PlayerRepository {

        final CountDownLatch started=
            new CountDownLatch(1);
        final CountDownLatch release=
            new CountDownLatch(1);
        final CountDownLatch finished=
            new CountDownLatch(1);
        final AtomicInteger interrupts=
            new AtomicInteger();

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
                while(true){
                    try{
                        release.await();
                        return;
                    }catch(InterruptedException ignored){
                        interrupts.incrementAndGet();
                    }
                }
            }finally{
                finished.countDown();
            }
        }
    }

    private WorldPlayerPersistenceUninterruptibleShutdownTest(){}
}
