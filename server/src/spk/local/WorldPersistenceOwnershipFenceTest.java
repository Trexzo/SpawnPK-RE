package spk.local;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldPersistenceOwnershipFenceTest {
    public static void main(String[] args)throws Exception{
        CountingRepository ownerRepository=
            new CountingRepository();
        CountingRepository foreignRepository=
            new CountingRepository();

        World ownerWorld=
            World.isolatedForTest(
                60_000L,
                ownerRepository
            );
        World foreignWorld=
            World.isolatedForTest(
                60_000L,
                foreignRepository
            );

        WorldPlayer owner=
            new WorldPlayer();
        WorldPlayer anchor=
            new WorldPlayer();
        WorldPlayer foreign=
            new WorldPlayer();

        try{
            long generationA=
                ownerWorld.registerPlayer(
                    owner,
                    "persistence-owner"
                );
            long anchorGeneration=
                ownerWorld.registerPlayer(
                    anchor,
                    "persistence-anchor"
                );
            long foreignGeneration=
                foreignWorld.registerPlayer(
                    foreign,
                    "persistence-foreign"
                );

            ownerWorld.start();
            foreignWorld.start();

            AtomicReference<
                WorldPlayerPersistence.CapturedSave
            > capturedA=
                new AtomicReference<>();

            ownerWorld.submitAndWait(
                owner,
                generationA,
                ()->capturedA.set(
                    ownerWorld.persistence()
                        .captureDeferredSave(
                            "persistence-owner",
                            owner,
                            generationA,
                            0,
                            "[persistence-owner-test] ",
                            "GENERATION_A"
                        )
                ),
                5_000L
            );

            WorldPlayerPersistence.CapturedSave staleCaptured=
                capturedA.get();

            if(staleCaptured==null)
                throw new AssertionError(
                    "generation A capture missing"
                );

            if(staleCaptured.ticket.sequence!=1L)
                throw new AssertionError(
                    "unexpected first save sequence "+
                    staleCaptured.ticket.sequence
                );

            if(!ownerWorld.unregisterPlayer(
                    owner,
                    generationA
                ))
                throw new AssertionError(
                    "generation A unregister failed"
                );

            long generationB=
                ownerWorld.registerPlayer(
                    owner,
                    "persistence-owner"
                );

            if(generationB==generationA)
                throw new AssertionError(
                    "replacement generation did not advance"
                );

            boolean staleAdmissionRejected=false;

            try{
                ownerWorld.persistence()
                    .submitCapturedWithBackpressure(
                        staleCaptured,
                        100L
                    );
            }catch(IllegalStateException expected){
                staleAdmissionRejected=true;
            }

            if(!staleAdmissionRejected)
                throw new AssertionError(
                    "stale captured save admitted after ownership advance"
                );

            if(ownerRepository.saves.get()!=0)
                throw new AssertionError(
                    "stale captured save reached repository"
                );

            AtomicReference<Boolean> staleCaptureRejected=
                new AtomicReference<>(false);
            AtomicReference<
                WorldPlayerPersistence.CapturedSave
            > capturedB=
                new AtomicReference<>();

            ownerWorld.submitAndWait(
                owner,
                generationB,
                ()->{
                    try{
                        ownerWorld.persistence()
                            .captureDeferredSave(
                                "persistence-owner",
                                owner,
                                generationA,
                                0,
                                "[persistence-owner-test] ",
                                "STALE_GENERATION"
                            );
                    }catch(IllegalStateException expected){
                        staleCaptureRejected.set(true);
                    }

                    capturedB.set(
                        ownerWorld.persistence()
                            .captureDeferredSave(
                                "persistence-owner",
                                owner,
                                generationB,
                                0,
                                "[persistence-owner-test] ",
                                "GENERATION_B"
                            )
                    );
                },
                5_000L
            );

            if(!staleCaptureRejected.get())
                throw new AssertionError(
                    "stale generation capture accepted"
                );

            WorldPlayerPersistence.CapturedSave validB=
                capturedB.get();

            if(validB==null)
                throw new AssertionError(
                    "generation B capture missing"
                );

            if(validB.ticket.sequence!=2L)
                throw new AssertionError(
                    "rejected stale capture consumed sequence "+
                    validB.ticket.sequence
                );

            AtomicReference<Boolean> foreignRejected=
                new AtomicReference<>(false);

            ownerWorld.submitAndWait(
                anchor,
                anchorGeneration,
                ()->{
                    try{
                        ownerWorld.persistence()
                            .captureDeferredSave(
                                "persistence-foreign",
                                foreign,
                                foreignGeneration,
                                0,
                                "[persistence-owner-test] ",
                                "FOREIGN_WORLD"
                            );
                    }catch(IllegalStateException expected){
                        foreignRejected.set(true);
                    }
                },
                5_000L
            );

            if(!foreignRejected.get())
                throw new AssertionError(
                    "foreign World player capture accepted"
                );

            if(!foreignWorld.unregisterPlayer(
                    foreign,
                    foreignGeneration
                ))
                throw new AssertionError(
                    "foreign player unregister failed"
                );

            AtomicReference<Boolean> unregisteredRejected=
                new AtomicReference<>(false);

            ownerWorld.submitAndWait(
                anchor,
                anchorGeneration,
                ()->{
                    try{
                        ownerWorld.persistence()
                            .captureDeferredSave(
                                "persistence-foreign",
                                foreign,
                                foreignGeneration,
                                0,
                                "[persistence-owner-test] ",
                                "UNREGISTERED"
                            );
                    }catch(IllegalStateException expected){
                        unregisteredRejected.set(true);
                    }
                },
                5_000L
            );

            if(!unregisteredRejected.get())
                throw new AssertionError(
                    "unregistered player capture accepted"
                );

            WorldPlayerPersistence.SaveTicket ticket=
                ownerWorld.persistence()
                    .submitCapturedWithBackpressure(
                        validB,
                        1_000L
                    );

            ticket.completion.get(
                5,
                TimeUnit.SECONDS
            );

            if(ownerRepository.saves.get()!=1)
                throw new AssertionError(
                    "valid generation B save count "+
                    ownerRepository.saves.get()
                );

            if(foreignRepository.saves.get()!=0)
                throw new AssertionError(
                    "foreign repository unexpectedly written"
                );

            System.out.println(
                "WORLD_PERSISTENCE_OWNERSHIP_FENCE_PASS "+
                "staleCaptureRejected=true "+
                "staleAdmissionRejected=true "+
                "replacementGenerationAccepted=true "+
                "foreignWorldRejected=true "+
                "unregisteredRejected=true "+
                "rejectedCaptureNoSequenceGrowth=true "+
                "repositoryWrites=1"
            );
        }finally{
            try{
                ownerWorld.close();
            }finally{
                foreignWorld.close();
            }
        }
    }

    private static final class CountingRepository
        implements PlayerRepository {

        final AtomicInteger saves=
            new AtomicInteger();

        @Override public Optional<PlayerSnapshot> load(
            String username
        )throws IOException{
            return Optional.empty();
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
            saves.incrementAndGet();
        }
    }
}
