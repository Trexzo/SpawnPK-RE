package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldPersistenceFinalCaptureRollbackTest {
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

        long generation=
            world.registerPlayer(
                player,
                LocalAccountProfiles.PRIMARY
            );

        AtomicBoolean captureFailed=
            new AtomicBoolean();
        AtomicReference<
            WorldPlayerPersistence.CapturedSave
        > ordinaryCapture=
            new AtomicReference<>();
        AtomicReference<
            WorldPlayerPersistence.CapturedSave
        > validFinalCapture=
            new AtomicReference<>();

        try{
            world.start();

            world.submitAndWait(
                player,
                generation,
                ()->{
                    try{
                        world.persistence()
                            .captureDeferredFinalSave(
                                "   ",
                                player,
                                generation,
                                0,
                                "[final-capture-rollback-test] ",
                                "INVALID_FINAL"
                            );
                    }catch(IllegalArgumentException expected){
                        captureFailed.set(
                            "username".equals(
                                expected.getMessage()
                            )
                        );
                    }

                    ordinaryCapture.set(
                        world.persistence()
                            .captureDeferredSave(
                                LocalAccountProfiles.PRIMARY,
                                player,
                                generation,
                                0,
                                "[final-capture-rollback-test] ",
                                "SEQUENCE_PROBE"
                            )
                    );
                },
                5_000L
            );

            if(!captureFailed.get())
                throw new AssertionError(
                    "invalid final snapshot capture did not propagate original failure"
                );

            if(world.persistence()
                    .checkpointSuppressed(
                        player.id(),
                        generation
                    ))
                throw new AssertionError(
                    "failed final capture leaked checkpoint suppression"
                );

            WorldPlayerPersistence.CapturedSave sequenceProbe=
                ordinaryCapture.get();

            if(sequenceProbe==null)
                throw new AssertionError(
                    "sequence probe capture missing"
                );

            if(sequenceProbe.ticket.sequence!=1L)
                throw new AssertionError(
                    "failed final capture consumed save sequence "+
                    sequenceProbe.ticket.sequence
                );

            world.submitAndWait(
                player,
                generation,
                ()->{
                    player.movement()
                        .setRunEnergy(33);
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
                33,
                "post-failure checkpoint"
            );

            if(world.persistence()
                    .checkpointCapturedCount()!=1L)
                throw new AssertionError(
                    "post-failure checkpoint was not captured"
                );

            world.submitAndWait(
                player,
                generation,
                ()->{
                    player.movement()
                        .setRunEnergy(44);

                    validFinalCapture.set(
                        world.persistence()
                            .captureDeferredFinalSave(
                                LocalAccountProfiles.PRIMARY,
                                player,
                                generation,
                                0,
                                "[final-capture-rollback-test] ",
                                "VALID_FINAL"
                            )
                    );
                },
                5_000L
            );

            WorldPlayerPersistence.CapturedSave valid=
                validFinalCapture.get();

            if(valid==null)
                throw new AssertionError(
                    "valid final capture missing"
                );

            if(valid.ticket.sequence!=3L)
                throw new AssertionError(
                    "unexpected valid final sequence "+
                    valid.ticket.sequence
                );

            if(!world.persistence()
                    .checkpointSuppressed(
                        player.id(),
                        generation
                    ))
                throw new AssertionError(
                    "valid final capture did not establish suppression"
                );

            if(!world.unregisterPlayer(
                    player,
                    generation
                ))
                throw new AssertionError(
                    "player unregister failed"
                );

            if(world.persistence()
                    .checkpointSuppressed(
                        player.id(),
                        generation
                    ))
                throw new AssertionError(
                    "valid final suppression survived unregister"
                );

            System.out.println(
                "WORLD_PERSISTENCE_FINAL_CAPTURE_ROLLBACK_PASS "+
                "originalFailurePropagated=true "+
                "suppressionRolledBack=true "+
                "rejectedCaptureNoSequenceGrowth=true "+
                "checkpointStillActive=true "+
                "validFinalSuppressed=true "+
                "unregisterReleased=true"
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
