package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldPersistenceFinalCheckpointQuiesceTest {
    public static void main(String[] args)throws Exception{
        RecordingRepository repository=
            new RecordingRepository();

        World world=
            World.isolatedForTest(
                60_000L,
                repository
            );

        WorldPlayer player=
            new WorldPlayer();

        long generationA=
            world.registerPlayer(
                player,
                LocalAccountProfiles.PRIMARY
            );

        try{
            world.start();

            world.submitAndWait(
                player,
                generationA,
                ()->{
                    player.movement()
                        .setRunEnergy(10);
                    world.persistence()
                        .checkpointDue(100L);
                },
                5_000L
            );

            awaitSaves(
                repository,
                1,
                5_000L
            );

            assertEnergy(
                repository.last(),
                10,
                "initial checkpoint"
            );

            long capturedBefore=
                world.persistence()
                    .checkpointCapturedCount();

            AtomicReference<
                WorldPlayerPersistence.CapturedSave
            > finalCapture=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                generationA,
                ()->{
                    player.movement()
                        .setRunEnergy(20);

                    finalCapture.set(
                        world.persistence()
                            .captureDeferredFinalSave(
                                LocalAccountProfiles.PRIMARY,
                                player,
                                generationA,
                                0,
                                "[checkpoint-quiesce-test] ",
                                "SESSION_END"
                            )
                    );
                },
                5_000L
            );

            if(!world.persistence()
                    .checkpointSuppressed(
                        player.id(),
                        generationA
                    ))
                throw new AssertionError(
                    "final capture did not suppress generation A checkpoints"
                );

            world.submitAndWait(
                player,
                generationA,
                ()->{
                    player.movement()
                        .setRunEnergy(99);
                    world.persistence()
                        .checkpointDue(200L);
                },
                5_000L
            );

            if(world.persistence()
                    .checkpointCapturedCount()!=
                    capturedBefore)
                throw new AssertionError(
                    "suppressed checkpoint was captured"
                );

            WorldPlayerPersistence.CapturedSave captured=
                finalCapture.get();

            if(captured==null)
                throw new AssertionError(
                    "final capture missing"
                );

            WorldPlayerPersistence.SaveTicket finalTicket=
                world.persistence()
                    .submitCapturedWithBackpressure(
                        captured,
                        1_000L
                    );

            finalTicket.completion.get(
                5,
                TimeUnit.SECONDS
            );

            awaitSaves(
                repository,
                2,
                5_000L
            );

            assertEnergy(
                repository.last(),
                20,
                "final generation A save"
            );

            Thread.sleep(50L);

            if(repository.size()!=2)
                throw new AssertionError(
                    "checkpoint wrote after final save count="+
                    repository.size()
                );

            if(!world.unregisterPlayer(
                    player,
                    generationA
                ))
                throw new AssertionError(
                    "generation A unregister failed"
                );

            if(world.persistence()
                    .checkpointSuppressed(
                        player.id(),
                        generationA
                    ))
                throw new AssertionError(
                    "generation A checkpoint suppression leaked after unregister"
                );

            long generationB=
                world.registerPlayer(
                    player,
                    LocalAccountProfiles.PRIMARY
                );

            if(generationB==generationA)
                throw new AssertionError(
                    "replacement generation did not advance"
                );

            world.submitAndWait(
                player,
                generationB,
                ()->{
                    player.movement()
                        .setRunEnergy(77);
                    world.persistence()
                        .checkpointDue(300L);
                },
                5_000L
            );

            awaitSaves(
                repository,
                3,
                5_000L
            );

            assertEnergy(
                repository.last(),
                77,
                "replacement checkpoint"
            );

            if(world.persistence()
                    .checkpointSuppressed(
                        player.id(),
                        generationB
                    ))
                throw new AssertionError(
                    "replacement generation inherited checkpoint suppression"
                );

            if(!world.unregisterPlayer(
                    player,
                    generationB
                ))
                throw new AssertionError(
                    "generation B unregister failed"
                );

            System.out.println(
                "WORLD_PERSISTENCE_FINAL_CHECKPOINT_QUIESCE_PASS "+
                "preFinalCheckpoint=true "+
                "finalCaptureSuppressed=true "+
                "postFinalCheckpointSkipped=true "+
                "finalSnapshotRemainedLatest=true "+
                "suppressionReleased=true "+
                "replacementCheckpoint=true "+
                "repositoryWrites=3"
            );
        }finally{
            world.close();
        }
    }

    private static void awaitSaves(
        RecordingRepository repository,
        int expected,
        long timeoutMillis
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.MILLISECONDS.toNanos(
                timeoutMillis
            );

        while(repository.size()<expected&&
              System.nanoTime()<deadline)
            Thread.sleep(5L);

        if(repository.size()<expected)
            throw new AssertionError(
                "repository save timeout expected="+
                expected+
                " actual="+
                repository.size()
            );
    }

    private static void assertEnergy(
        PlayerSnapshot snapshot,
        int expected,
        String phase
    ){
        if(snapshot==null)
            throw new AssertionError(
                phase+" snapshot missing"
            );

        String actual=
            snapshot.value(
                "movement.runEnergy"
            );

        if(!Integer.toString(expected)
                .equals(actual))
            throw new AssertionError(
                phase+
                " energy expected="+expected+
                " actual="+actual
            );
    }

    private static final class RecordingRepository
        implements PlayerRepository {

        private final List<PlayerSnapshot> saved=
            new ArrayList<>();

        @Override public synchronized Optional<PlayerSnapshot> load(
            String username
        )throws IOException{
            return Optional.empty();
        }

        @Override public synchronized void save(
            PlayerSnapshot snapshot
        )throws IOException{
            saved.add(snapshot);
        }

        synchronized int size(){
            return saved.size();
        }

        synchronized PlayerSnapshot last(){
            return saved.isEmpty()
                ?null
                :saved.get(saved.size()-1);
        }
    }
}
