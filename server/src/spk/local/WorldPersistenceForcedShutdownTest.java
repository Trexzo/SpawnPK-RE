package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldPersistenceForcedShutdownTest {
    public static void main(String[] args)throws Exception{
        BlockingRepository repository=
            new BlockingRepository();

        World world=
            World.isolatedForTest(
                20L,
                repository
            );

        WorldPlayer player=
            new WorldPlayer();

        AtomicReference<
            WorldPlayerPersistence.SaveTicket
        > first=
            new AtomicReference<>();

        AtomicReference<
            WorldPlayerPersistence.SaveTicket
        > second=
            new AtomicReference<>();

        boolean closed=false;

        try{
            world.registerPlayer(
                player,
                "opensrc"
            );
            world.start();

            world.submitAndWait(
                player,
                ()->{
                    first.set(
                        world.persistence()
                            .captureAndSave(
                                "opensrc",
                                player,
                                0,
                                "[forced-shutdown-test] ",
                                "FIRST"
                            )
                    );

                    second.set(
                        world.persistence()
                            .captureAndSave(
                                "opensrc",
                                player,
                                0,
                                "[forced-shutdown-test] ",
                                "SECOND"
                            )
                    );
                },
                5_000L
            );

            if(!repository.firstStarted.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "first persistence write never started"
                );

            if(world.persistence()
                    .queuedWrites()!=1)
                throw new AssertionError(
                    "expected one queued save, queued="+
                    world.persistence()
                        .queuedWrites()
                );

            long started=
                System.nanoTime();

            world.close();
            closed=true;

            long closeMillis=
                TimeUnit.NANOSECONDS
                    .toMillis(
                        System.nanoTime()-
                        started
                    );

            assertExceptional(
                "in-flight",
                first.get()
                    .completion
            );

            assertExceptional(
                "queued",
                second.get()
                    .completion
            );

            if(repository.saveCalls.get()!=1)
                throw new AssertionError(
                    "queued save unexpectedly executed calls="+
                    repository.saveCalls.get()
                );

            if(world.persistence()
                    .checkpointRejectedCount()!=0L)
                throw new AssertionError(
                    "explicit save drop polluted checkpoint rejection metric="+
                    world.persistence()
                        .checkpointRejectedCount()
                );

            if(closeMillis<4_500L)
                throw new AssertionError(
                    "forced shutdown path was not exercised closeMs="+
                    closeMillis
                );

            System.out.println(
                "WORLD_PERSISTENCE_FORCED_SHUTDOWN_PASS "+
                "inFlightTerminal=true "+
                "queuedTerminal=true "+
                "queuedFailed=true "+
                "checkpointMetricClean=true "+
                "repositoryCalls="+
                    repository.saveCalls.get()+
                " closeMs="+closeMillis
            );
        }finally{
            repository.release.countDown();

            if(!closed)
                world.close();
        }
    }

    private static void assertExceptional(
        String label,
        CompletableFuture<Void> future
    )throws Exception{
        if(future==null)
            throw new AssertionError(
                label+" future missing"
            );

        if(!future.isDone())
            throw new AssertionError(
                label+" future not terminal"
            );

        if(!future.isCompletedExceptionally())
            throw new AssertionError(
                label+" future did not fail"
            );

        try{
            future.get(
                1,
                TimeUnit.SECONDS
            );

            throw new AssertionError(
                label+" future unexpectedly succeeded"
            );
        }catch(ExecutionException expected){
            if(expected.getCause()==null)
                throw new AssertionError(
                    label+" failure missing cause"
                );
        }
    }

    private static final class BlockingRepository
        implements PlayerRepository {

        final AtomicInteger saveCalls=
            new AtomicInteger();

        final CountDownLatch firstStarted=
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
            int call=
                saveCalls.incrementAndGet();

            if(call!=1)
                return;

            firstStarted.countDown();

            try{
                release.await();
            }catch(InterruptedException e){
                Thread.currentThread()
                    .interrupt();

                throw new IOException(
                    "forced shutdown interrupted repository write",
                    e
                );
            }
        }
    }
}
