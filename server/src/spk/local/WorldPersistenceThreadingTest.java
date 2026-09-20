package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldPersistenceThreadingTest {
    public static void main(String[] args)throws Exception{
        RecordingRepository repository=
            new RecordingRepository();

        World world=
            World.isolatedForTest(
                20L,
                repository
            );

        WorldPlayer player=
            new WorldPlayer();

        try{
            world.registerPlayer(
                player,
                "opensrc"
            );
            world.start();

            AtomicReference<String> captureThread=
                new AtomicReference<>();
            AtomicReference<
                WorldPlayerPersistence.SaveTicket
            > first=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    player.movement().setRunEnergy(
                        71
                    );
                    player.playerState().setSpecialEnergy(
                        43
                    );

                    captureThread.set(
                        Thread.currentThread()
                            .getName()
                    );

                    first.set(
                        world.persistence()
                            .captureAndSave(
                                "opensrc",
                                player,
                                12345,
                                "[persistence-test] ",
                                "FIRST"
                            )
                    );
                },
                5_000L
            );

            WorldPlayerPersistence.SaveTicket firstTicket=
                first.get();

            if(firstTicket==null)
                throw new AssertionError(
                    "first capture missing"
                );

            firstTicket.completion.get(
                5,
                TimeUnit.SECONDS
            );

            if(!"71".equals(
                    firstTicket.snapshot.value(
                        "movement.runEnergy"
                    ))||
               !"43".equals(
                    firstTicket.snapshot.value(
                        "combat.special.energy"
                    ))||
               !"12345".equals(
                    firstTicket.snapshot.value(
                        "pet.accessoryItem"
                    )))
                throw new AssertionError(
                    "captured snapshot mismatch "+
                    firstTicket.snapshot.values()
                );

            if(repository.saveThreads.isEmpty())
                throw new AssertionError(
                    "repository save missing"
                );

            String ioThread=
                repository.saveThreads.get(0);

            if(ioThread.equals(
                    captureThread.get()))
                throw new AssertionError(
                    "repository I/O ran on World thread "+
                    ioThread
                );

            if(!captureThread.get().startsWith(
                    "spk-world-pulse"))
                throw new AssertionError(
                    "capture did not run on World thread: "+
                    captureThread.get()
                );

            if(!ioThread.startsWith(
                    "spk-player-persistence-"))
                throw new AssertionError(
                    "unexpected I/O worker: "+
                    ioThread
                );

            AtomicReference<
                WorldPlayerPersistence.SaveTicket
            > second=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    player.movement().setRunEnergy(
                        42
                    );

                    second.set(
                        world.persistence()
                            .captureAndSave(
                                "opensrc",
                                player,
                                0,
                                "[persistence-test] ",
                                "SECOND"
                            )
                    );

                    player.movement().setRunEnergy(
                        9
                    );
                },
                5_000L
            );

            second.get().completion.get(
                5,
                TimeUnit.SECONDS
            );

            if(!"42".equals(
                    second.get().snapshot.value(
                        "movement.runEnergy"
                    )))
                throw new AssertionError(
                    "snapshot changed after live mutation"
                );

            if(repository.savedEnergy.size()!=2||
               repository.savedEnergy.get(0)!=71||
               repository.savedEnergy.get(1)!=42)
                throw new AssertionError(
                    "save ordering mismatch "+
                    repository.savedEnergy
                );

            boolean rejected=false;
            try{
                world.persistence()
                    .captureAndSave(
                        "opensrc",
                        player,
                        0,
                        "[persistence-test] ",
                        "OFF_WORLD"
                    );
            }catch(IllegalStateException expected){
                rejected=true;
            }

            if(!rejected)
                throw new AssertionError(
                    "off-World capture was accepted"
                );

            CompletableFuture<Void> loadOnWorld=
                world.submit(
                    player,
                    ()->world.persistence()
                        .load("opensrc")
                );

            boolean loadRejected=false;
            try{
                loadOnWorld.get(
                    5,
                    TimeUnit.SECONDS
                );
            }catch(ExecutionException e){
                loadRejected=
                    e.getCause() instanceof
                        IllegalStateException;
            }

            if(!loadRejected)
                throw new AssertionError(
                    "repository load was allowed on World thread"
                );

            System.out.println(
                "WORLD_PERSISTENCE_THREADING_PASS "+
                "captureThread="+captureThread.get()+
                " ioThread="+ioThread+
                " orderedSnapshots=true "+
                "immutableAfterCapture=true "+
                "offWorldCaptureRejected=true "+
                "worldLoadRejected=true"
            );
        }finally{
            world.unregisterPlayer(player);
            world.close();
        }
    }

    private static final class RecordingRepository
        implements PlayerRepository {

        final List<String> saveThreads=
            Collections.synchronizedList(
                new ArrayList<>()
            );

        final List<Integer> savedEnergy=
            Collections.synchronizedList(
                new ArrayList<>()
            );

        @Override public Optional<PlayerSnapshot> load(
            String username
        ){
            return Optional.empty();
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
            saveThreads.add(
                Thread.currentThread()
                    .getName()
            );

            savedEnergy.add(
                Integer.parseInt(
                    snapshot.value(
                        "movement.runEnergy"
                    )
                )
            );
        }
    }
}
