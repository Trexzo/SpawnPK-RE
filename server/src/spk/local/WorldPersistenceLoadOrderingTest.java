package spk.local;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldPersistenceLoadOrderingTest {
    public static void main(String[] args)
        throws Exception {

        orderedLoadBehindSave();
        forcedCloseSettlesLoad();

        System.out.println(
            "WORLD_PERSISTENCE_LOAD_ORDERING_PASS "+
            "loadWaitedForPriorSave=true "+
            "loadedNewestSnapshot=true "+
            "loadUsedPersistenceWorker=true "+
            "forcedCloseSettledLoad=true"
        );
    }

    private static void orderedLoadBehindSave()
        throws Exception {

        BlockingSaveRepository repository=
            new BlockingSaveRepository();

        World world=
            World.isolatedForTest(
                20L,
                repository
            );
        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "opensrc"
            );

        world.start();

        AtomicReference<
            WorldPlayerPersistence.SaveTicket
        > saveTicket=
            new AtomicReference<>();

        AtomicReference<
            Optional<PlayerSnapshot>
        > loaded=
            new AtomicReference<>();

        AtomicReference<Throwable> loadFailure=
            new AtomicReference<>();

        CountDownLatch loadReturned=
            new CountDownLatch(1);

        try{
            world.submitAndWait(
                player,
                generation,
                ()->{
                    player.movement()
                        .setRunEnergy(77);

                    saveTicket.set(
                        world.persistence()
                            .captureAndSave(
                                "opensrc",
                                player,
                                0,
                                "[load-order] ",
                                "BLOCKED_SAVE"
                            )
                    );
                },
                5_000L
            );

            if(!repository.saveEntered.await(
                    2,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "save did not enter repository"
                );

            Thread loader=
                new Thread(
                    ()->{
                        try{
                            loaded.set(
                                world.persistence()
                                    .load("opensrc")
                            );
                        }catch(Throwable error){
                            loadFailure.set(error);
                        }finally{
                            loadReturned.countDown();
                        }
                    },
                    "replacement-login-load"
                );

            loader.start();

            if(loadReturned.await(
                    150,
                    TimeUnit.MILLISECONDS))
                throw new AssertionError(
                    "load overtook blocked prior save"
                );

            repository.releaseSave.countDown();

            saveTicket.get()
                .completion.get(
                    2,
                    TimeUnit.SECONDS
                );

            if(!loadReturned.await(
                    2,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "load did not finish after save"
                );

            loader.join(1_000L);

            if(loader.isAlive())
                throw new AssertionError(
                    "loader thread retained"
                );

            if(loadFailure.get()!=null)
                throw new AssertionError(
                    "ordered load failed",
                    loadFailure.get()
                );

            Optional<PlayerSnapshot> snapshot=
                loaded.get();

            if(snapshot==null||
               !snapshot.isPresent())
                throw new AssertionError(
                    "ordered load returned no snapshot"
                );

            if(!"77".equals(
                    snapshot.get().value(
                        "movement.runEnergy"
                    )))
                throw new AssertionError(
                    "load saw stale snapshot "+
                    snapshot.get().values()
                );

            if(repository.loadThread.get()==null||
               !repository.loadThread.get()
                    .startsWith(
                        "spk-player-persistence-"
                    ))
                throw new AssertionError(
                    "load did not use persistence worker: "+
                    repository.loadThread.get()
                );
        }finally{
            repository.releaseSave.countDown();

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );

            world.close();
        }
    }

    private static void forcedCloseSettlesLoad()
        throws Exception {

        BlockingLoadRepository repository=
            new BlockingLoadRepository();

        World world=
            World.isolatedForTest(
                20L,
                repository
            );

        AtomicReference<Throwable> loadFailure=
            new AtomicReference<>();

        CountDownLatch loadReturned=
            new CountDownLatch(1);

        Thread loader=
            new Thread(
                ()->{
                    try{
                        world.persistence()
                            .load("opensrc");
                    }catch(Throwable error){
                        loadFailure.set(error);
                    }finally{
                        loadReturned.countDown();
                    }
                },
                "forced-close-load"
            );

        loader.start();

        if(!repository.loadEntered.await(
                2,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "load did not enter repository"
            );

        Thread closer=
            new Thread(
                world::close,
                "forced-close-world"
            );

        closer.start();

        if(!loadReturned.await(
                10,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "forced close left load waiter blocked"
            );

        Throwable failure=
            loadFailure.get();

        if(failure==null)
            throw new AssertionError(
                "forced close did not reject in-flight load"
            );

        repository.releaseLoad.countDown();

        loader.join(1_000L);
        closer.join(2_000L);

        if(loader.isAlive())
            throw new AssertionError(
                "forced-close loader retained"
            );

        if(closer.isAlive())
            throw new AssertionError(
                "World close did not return"
            );
    }

    private static final class BlockingSaveRepository
        implements PlayerRepository {

        final CountDownLatch saveEntered=
            new CountDownLatch(1);
        final CountDownLatch releaseSave=
            new CountDownLatch(1);
        final AtomicReference<String> loadThread=
            new AtomicReference<>();

        volatile PlayerSnapshot stored;

        @Override public Optional<PlayerSnapshot> load(
            String username
        ){
            loadThread.set(
                Thread.currentThread()
                    .getName()
            );

            return Optional.ofNullable(
                stored
            );
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
            saveEntered.countDown();

            awaitUninterruptibly(
                releaseSave
            );

            stored=snapshot;
        }
    }

    private static final class BlockingLoadRepository
        implements PlayerRepository {

        final CountDownLatch loadEntered=
            new CountDownLatch(1);
        final CountDownLatch releaseLoad=
            new CountDownLatch(1);

        @Override public Optional<PlayerSnapshot> load(
            String username
        ){
            loadEntered.countDown();

            awaitUninterruptibly(
                releaseLoad
            );

            return Optional.empty();
        }

        @Override public void save(
            PlayerSnapshot snapshot
        ){}
    }

    private static void awaitUninterruptibly(
        CountDownLatch latch
    ){
        boolean interrupted=false;

        for(;;){
            try{
                latch.await();
                break;
            }catch(InterruptedException ignored){
                interrupted=true;
            }
        }

        if(interrupted)
            Thread.currentThread()
                .interrupt();
    }

    private WorldPersistenceLoadOrderingTest(){}
}
