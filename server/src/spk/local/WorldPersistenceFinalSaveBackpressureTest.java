package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldPersistenceFinalSaveBackpressureTest {
    public static void main(String[] args)throws Exception{
        BlockingFirstRepository repository=
            new BlockingFirstRepository();

        World world=
            World.isolatedForTest(
                60_000L,
                repository
            );

        WorldPlayer player=
            new WorldPlayer();

        try{
            long generation=
                world.registerPlayer(
                    player,
                    LocalAccountProfiles.PRIMARY
                );

            world.start();

            AtomicReference<
                WorldPlayerPersistence.SaveTicket
            > blockerRef=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                generation,
                ()->{
                    player.movement()
                        .setRunEnergy(10);

                    blockerRef.set(
                        world.persistence()
                            .captureAndSave(
                                LocalAccountProfiles.PRIMARY,
                                player,
                                0,
                                "[final-save-test] ",
                                "BLOCKER"
                            )
                    );
                },
                5_000L
            );

            if(!repository.firstStarted.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "blocking repository save never started"
                );

            ArrayList<
                WorldPlayerPersistence.SaveTicket
            > queued=
                new ArrayList<>();

            world.submitAndWait(
                player,
                generation,
                ()->{
                    for(int i=0;
                        i<WorldPlayerPersistence
                            .MAX_PENDING_WRITES;
                        i++){
                        player.movement()
                            .setRunEnergy(
                                20+(i%50)
                            );

                        WorldPlayerPersistence.SaveTicket ticket=
                            world.persistence()
                                .captureAndSave(
                                    LocalAccountProfiles.PRIMARY,
                                    player,
                                    0,
                                    "[final-save-test] ",
                                    "QUEUED_"+i
                                );

                        if(ticket.completion.isDone())
                            throw new AssertionError(
                                "backlog save rejected before queue full index="+
                                i
                            );

                        queued.add(ticket);
                    }
                },
                5_000L
            );

            if(world.persistence()
                    .queuedWrites()!=
               WorldPlayerPersistence.MAX_PENDING_WRITES)
                throw new AssertionError(
                    "persistence queue did not fill: "+
                    world.persistence().metrics()
                );

            AtomicReference<
                WorldPlayerPersistence.CapturedSave
            > finalCaptureRef=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                generation,
                ()->{
                    player.movement()
                        .setRunEnergy(99);

                    finalCaptureRef.set(
                        world.persistence()
                            .captureDeferredSave(
                                LocalAccountProfiles.PRIMARY,
                                player,
                                0,
                                "[final-save-test] ",
                                "SESSION_END"
                            )
                    );
                },
                5_000L
            );

            WorldPlayerPersistence.CapturedSave finalCapture=
                finalCaptureRef.get();

            if(finalCapture==null)
                throw new AssertionError(
                    "final capture missing"
                );

            if(finalCapture.ticket.completion.isDone())
                throw new AssertionError(
                    "deferred final capture was prematurely completed"
                );

            CountDownLatch submitStarted=
                new CountDownLatch(1);
            AtomicReference<
                WorldPlayerPersistence.SaveTicket
            > admittedRef=
                new AtomicReference<>();
            AtomicReference<Throwable>
                submitFailure=
                    new AtomicReference<>();

            Thread submitter=
                new Thread(
                    ()->{
                        submitStarted.countDown();

                        try{
                            admittedRef.set(
                                world.persistence()
                                    .submitCapturedWithBackpressure(
                                        finalCapture,
                                        5_000L
                                    )
                            );
                        }catch(Throwable failure){
                            submitFailure.set(
                                failure
                            );
                        }
                    },
                    "final-save-admission-test"
                );

            submitter.start();

            if(!submitStarted.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "final save submitter did not start"
                );

            Thread.sleep(100L);

            if(!submitter.isAlive())
                throw new AssertionError(
                    "full-queue final save did not wait for capacity"
                );

            if(finalCapture.ticket
                    .completion.isDone())
                throw new AssertionError(
                    "full-queue final save rejected before capacity opened"
                );

            AtomicBoolean worldResponsive=
                new AtomicBoolean();

            world.submitAndWait(
                player,
                generation,
                ()->worldResponsive.set(true),
                2_000L
            );

            if(!worldResponsive.get())
                throw new AssertionError(
                    "final save admission blocked World execution"
                );

            repository.releaseFirst.countDown();

            submitter.join(5_000L);

            if(submitter.isAlive())
                throw new AssertionError(
                    "final save admission did not settle after capacity opened"
                );

            if(submitFailure.get()!=null)
                throw new AssertionError(
                    "final save admission failed",
                    submitFailure.get()
                );

            WorldPlayerPersistence.SaveTicket admitted=
                admittedRef.get();

            if(admitted!=finalCapture.ticket)
                throw new AssertionError(
                    "final save admission did not preserve exact capture ticket"
                );

            admitted.completion.get(
                5,
                TimeUnit.SECONDS
            );

            for(WorldPlayerPersistence.SaveTicket ticket:
                    queued)
                ticket.completion.get(
                    5,
                    TimeUnit.SECONDS
                );

            blockerRef.get().completion.get(
                5,
                TimeUnit.SECONDS
            );

            List<Integer> saved=
                repository.savedEnergySnapshot();

            int expectedSaves=
                1+
                WorldPlayerPersistence.MAX_PENDING_WRITES+
                1;

            if(saved.size()!=expectedSaves)
                throw new AssertionError(
                    "unexpected persistence save count "+
                    saved.size()+
                    " expected="+expectedSaves+
                    " values="+saved
                );

            if(saved.get(saved.size()-1)!=99)
                throw new AssertionError(
                    "final disconnect snapshot was not persisted last: "+
                    saved
                );

            if(world.persistence().failedCount()!=0)
                throw new AssertionError(
                    "transient saturation counted as save failure: "+
                    world.persistence().metrics()
                );

            System.out.println(
                "WORLD_PERSISTENCE_FINAL_SAVE_BACKPRESSURE_PASS "+
                "queueSaturated=true "+
                "finalCaptureDeferred=true "+
                "admissionWaitedOffWorld=true "+
                "worldRemainedResponsive=true "+
                "fifoPreserved=true "+
                "finalSnapshotPersisted=true "+
                "saves="+saved.size()
            );
        }finally{
            repository.releaseFirst.countDown();

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );

            world.close();
        }

        assertReservedFinalRetiresBeforeIoAndOrdersLoad();
        assertTimedOutFinalWorldActionOwnsReservation();
        assertQueuedCheckpointCannotOverwriteReservedFinal();
    }

    private static void assertReservedFinalRetiresBeforeIoAndOrdersLoad()
        throws Exception
    {
        BlockingFirstRepository repository=
            new BlockingFirstRepository();

        World world=
            World.isolatedForTest(
                60_000L,
                repository
            );
        WorldPlayer player=
            new WorldPlayer();

        try{
            long generation=
                world.registerPlayer(
                    player,
                    LocalAccountProfiles.PRIMARY
                );

            world.start();

            AtomicReference<
                WorldPlayerPersistence.SaveTicket
            > blocker=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                generation,
                ()->{
                    player.movement()
                        .setRunEnergy(10);

                    blocker.set(
                        world.persistence()
                            .captureAndSave(
                                LocalAccountProfiles.PRIMARY,
                                player,
                                generation,
                                0,
                                "[final-reservation-test] ",
                                "BLOCKER"
                            )
                    );
                },
                5_000L
            );

            if(!repository.firstStarted.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "reservation fixture blocker did not start"
                );

            ArrayList<
                WorldPlayerPersistence.SaveTicket
            > queued=
                new ArrayList<>();

            world.submitAndWait(
                player,
                generation,
                ()->{
                    for(int i=0;
                        i<WorldPlayerPersistence
                            .MAX_PENDING_WRITES;
                        i++){
                        player.movement()
                            .setRunEnergy(
                                20+(i%50)
                            );

                        queued.add(
                            world.persistence()
                                .captureAndSave(
                                    LocalAccountProfiles.PRIMARY,
                                    player,
                                    generation,
                                    0,
                                    "[final-reservation-test] ",
                                    "QUEUED_"+i
                                )
                        );
                    }
                },
                5_000L
            );

            if(world.persistence().queuedWrites()!=
                    WorldPlayerPersistence
                        .MAX_PENDING_WRITES)
                throw new AssertionError(
                    "reservation fixture did not fill persistence queue"
                );

            AtomicReference<
                WorldPlayerPersistence.FinalSaveReservation
            > reservationRef=
                new AtomicReference<>();
            AtomicReference<Throwable>
                reservationFailure=
                    new AtomicReference<>();
            CountDownLatch reservationStarted=
                new CountDownLatch(1);

            Thread reserver=
                new Thread(
                    ()->{
                        reservationStarted.countDown();

                        try{
                            reservationRef.set(
                                world.persistence()
                                    .reserveFinalSaveWithBackpressure(
                                        5_000L
                                    )
                            );
                        }catch(Throwable failure){
                            reservationFailure.set(
                                failure
                            );
                        }
                    },
                    "final-save-fifo-reserver"
                );

            reserver.start();

            if(!reservationStarted.await(
                    2,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "final-save reservation thread did not start"
                );

            Thread.sleep(100L);

            if(!reserver.isAlive())
                throw new AssertionError(
                    "full persistence queue did not backpressure final-save reservation"
                );

            AtomicBoolean worldResponsive=
                new AtomicBoolean();

            world.submitAndWait(
                player,
                generation,
                ()->worldResponsive.set(
                    true
                ),
                2_000L
            );

            if(!worldResponsive.get())
                throw new AssertionError(
                    "final-save reservation blocked World execution"
                );

            repository.releaseFirst.countDown();

            reserver.join(
                5_000L
            );

            if(reserver.isAlive())
                throw new AssertionError(
                    "final-save reservation did not acquire FIFO position"
                );

            if(reservationFailure.get()!=null)
                throw new AssertionError(
                    "final-save reservation failed",
                    reservationFailure.get()
                );

            WorldPlayerPersistence.FinalSaveReservation
                reservation=
                    reservationRef.get();

            if(reservation==null)
                throw new AssertionError(
                    "final-save reservation missing"
                );

            AtomicReference<
                WorldPlayerPersistence.CapturedSave
            > capturedRef=
                new AtomicReference<>();
            AtomicBoolean removed=
                new AtomicBoolean();

            world.submitAndWait(
                player,
                generation,
                ()->{
                    player.movement()
                        .setRunEnergy(99);

                    WorldPlayerPersistence.CapturedSave
                        captured=
                            world.persistence()
                                .captureDeferredFinalSave(
                                    LocalAccountProfiles.PRIMARY,
                                    player,
                                    generation,
                                    0,
                                    "[final-reservation-test] ",
                                    "SESSION_END"
                                );

                    if(!world.unregisterPlayer(
                            player,
                            generation))
                        throw new AssertionError(
                            "atomic final capture did not unregister exact player generation"
                        );

                    capturedRef.set(
                        captured
                    );
                    removed.set(
                        true
                    );
                },
                5_000L
            );

            if(!removed.get()||
               world.players().owns(
                   player,
                   generation)||
               player.registered())
                throw new AssertionError(
                    "final capture released World command before exact player retirement"
                );

            int priorSaveCount=
                1+
                WorldPlayerPersistence
                    .MAX_PENDING_WRITES;
            awaitSaveCount(
                repository,
                priorSaveCount,
                5_000L
            );

            AtomicReference<
                Optional<PlayerSnapshot>
            > loaded=
                new AtomicReference<>();
            AtomicReference<Throwable>
                loadFailure=
                    new AtomicReference<>();
            CountDownLatch loadReturned=
                new CountDownLatch(1);

            Thread loader=
                new Thread(
                    ()->{
                        try{
                            loaded.set(
                                world.persistence()
                                    .load(
                                        LocalAccountProfiles.PRIMARY
                                    )
                            );
                        }catch(Throwable failure){
                            loadFailure.set(
                                failure
                            );
                        }finally{
                            loadReturned.countDown();
                        }
                    },
                    "replacement-load-behind-final-reservation"
                );

            loader.start();

            if(loadReturned.await(
                    150L,
                    TimeUnit.MILLISECONDS))
                throw new AssertionError(
                    "replacement load overtook unpublished final-save reservation"
                );

            WorldPlayerPersistence.CapturedSave
                captured=
                    capturedRef.get();

            if(captured==null)
                throw new AssertionError(
                    "atomic final capture missing"
                );

            WorldPlayerPersistence.SaveTicket
                finalTicket=
                    reservation.publish(
                        captured
                    );

            finalTicket.completion.get(
                5,
                TimeUnit.SECONDS
            );

            if(!loadReturned.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "replacement load did not complete after final-save publication"
                );

            loader.join(
                1_000L
            );

            if(loader.isAlive())
                throw new AssertionError(
                    "replacement load thread retained"
                );

            if(loadFailure.get()!=null)
                throw new AssertionError(
                    "replacement load failed",
                    loadFailure.get()
                );

            Optional<PlayerSnapshot> loadedSnapshot=
                loaded.get();

            if(loadedSnapshot==null||
               !loadedSnapshot.isPresent())
                throw new AssertionError(
                    "replacement load returned no final snapshot"
                );

            String loadedEnergy=
                loadedSnapshot.get().value(
                    "movement.runEnergy"
                );

            if(!"99".equals(
                    loadedEnergy))
                throw new AssertionError(
                    "replacement load did not observe final snapshot energy="+
                    loadedEnergy
                );

            awaitSaveCount(
                repository,
                priorSaveCount+1,
                5_000L
            );

            List<Integer> saved=
                repository.savedEnergySnapshot();

            if(saved.get(
                    saved.size()-1)!=99)
                throw new AssertionError(
                    "reserved final save was not last before replacement load values="+
                    saved
                );

            if(world.persistence().failedCount()!=0)
                throw new AssertionError(
                    "reserved final save introduced persistence failure "+
                    world.persistence().metrics()
                );

            blocker.get().completion.get(
                5,
                TimeUnit.SECONDS
            );

            for(WorldPlayerPersistence.SaveTicket ticket:
                    queued)
                ticket.completion.get(
                    5,
                    TimeUnit.SECONDS
                );

            System.out.println(
                "WORLD_PERSISTENCE_FINAL_RETIREMENT_PASS "+
                "reservationBackpressuredOffWorld=true "+
                "worldResponsiveDuringReservation=true "+
                "finalCaptureRetiredWorldOwnership=true "+
                "replacementLoadBlockedBehindReservation=true "+
                "replacementLoadedFinalSnapshot=true"
            );
        }finally{
            repository.releaseFirst.countDown();

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );

            world.close();
        }
    }

    private static void assertTimedOutFinalWorldActionOwnsReservation()
        throws Exception
    {
        RecordingRepository repository=
            new RecordingRepository();
        World world=
            World.isolatedForTest(
                60_000L,
                repository
            );
        WorldPlayer player=
            new WorldPlayer();
        CountDownLatch releaseAction=
            new CountDownLatch(1);

        try{
            long generation=
                world.registerPlayer(
                    player,
                    LocalAccountProfiles.PRIMARY
                );

            world.start();

            WorldPlayerPersistence.FinalSaveReservation
                reservation=
                    world.persistence()
                        .reserveFinalSaveWithBackpressure(
                            5_000L
                        );

            CountDownLatch actionEntered=
                new CountDownLatch(1);
            AtomicReference<
                WorldPlayerPersistence.SaveTicket
            > ticket=
                new AtomicReference<>();

            CompletableFuture<Void> finalAction=
                world.submit(
                    player,
                    generation,
                    ()->{
                        actionEntered.countDown();

                        releaseAction.await();

                        player.movement()
                            .setRunEnergy(77);

                        WorldPlayerPersistence.CapturedSave
                            captured=
                                world.persistence()
                                    .captureDeferredFinalSave(
                                        LocalAccountProfiles.PRIMARY,
                                        player,
                                        generation,
                                        0,
                                        "[final-timeout-test] ",
                                        "SESSION_END"
                                    );

                        if(!world.unregisterPlayer(
                                player,
                                generation))
                            throw new AssertionError(
                                "late final action could not retire exact generation"
                            );

                        ticket.set(
                            reservation.publish(
                                captured
                            )
                        );
                    }
                );

            finalAction.whenComplete(
                (ignored,failure)->{
                    if(failure!=null)
                        reservation.abort(
                            failure
                        );
                }
            );

            if(!actionEntered.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "final timeout action did not start"
                );

            boolean timedOut=false;

            try{
                finalAction.get(
                    25L,
                    TimeUnit.MILLISECONDS
                );
            }catch(TimeoutException expected){
                timedOut=true;
            }

            if(!timedOut)
                throw new AssertionError(
                    "final action did not reproduce caller-side timeout"
                );

            releaseAction.countDown();

            finalAction.get(
                5,
                TimeUnit.SECONDS
            );

            WorldPlayerPersistence.SaveTicket
                finalTicket=
                    ticket.get();

            if(finalTicket==null)
                throw new AssertionError(
                    "timed-out final action did not publish reserved save"
                );

            finalTicket.completion.get(
                5,
                TimeUnit.SECONDS
            );

            if(player.registered())
                throw new AssertionError(
                    "timed-out final action retained World ownership"
                );

            Optional<PlayerSnapshot> stored=
                repository.load(
                    LocalAccountProfiles.PRIMARY
                );

            if(!stored.isPresent()||
               !"77".equals(
                   stored.get().value(
                       "movement.runEnergy"
                   )))
                throw new AssertionError(
                    "timed-out final action lost final snapshot"
                );
        }finally{
            releaseAction.countDown();

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );

            world.close();
        }

        RecordingRepository cancelledRepository=
            new RecordingRepository();
        World cancelledWorld=
            World.isolatedForTest(
                60_000L,
                cancelledRepository
            );
        WorldPlayer cancelledPlayer=
            new WorldPlayer();

        try{
            long generation=
                cancelledWorld.registerPlayer(
                    cancelledPlayer,
                    LocalAccountProfiles.PRIMARY
                );

            WorldPlayerPersistence.FinalSaveReservation
                reservation=
                    cancelledWorld.persistence()
                        .reserveFinalSaveWithBackpressure(
                            5_000L
                        );

            AtomicBoolean actionRan=
                new AtomicBoolean();

            CompletableFuture<Void> finalAction=
                cancelledWorld.submit(
                    cancelledPlayer,
                    generation,
                    ()->actionRan.set(
                        true
                    )
                );

            finalAction.whenComplete(
                (ignored,failure)->{
                    if(failure!=null)
                        reservation.abort(
                            failure
                        );
                }
            );

            if(!cancelledWorld.unregisterPlayer(
                    cancelledPlayer,
                    generation))
                throw new AssertionError(
                    "outer unregister did not retire queued final action owner"
                );

            try{
                finalAction.get(
                    1,
                    TimeUnit.SECONDS
                );
                throw new AssertionError(
                    "queued final action survived outer unregister"
                );
            }catch(ExecutionException|
                    CancellationException expected){
                // expected: command cleanup owns reservation abort.
            }

            if(actionRan.get())
                throw new AssertionError(
                    "cancelled final action executed after outer unregister"
                );

            AtomicReference<Throwable>
                loadFailure=
                    new AtomicReference<>();
            Thread loader=
                new Thread(
                    ()->{
                        try{
                            cancelledWorld.persistence()
                                .load(
                                    LocalAccountProfiles.PRIMARY
                                );
                        }catch(Throwable failure){
                            loadFailure.set(
                                failure
                            );
                        }
                    },
                    "final-timeout-abort-load"
                );
            loader.setDaemon(
                true
            );
            loader.start();
            loader.join(
                1_000L
            );

            if(loader.isAlive())
                throw new AssertionError(
                    "aborted final reservation retained persistence worker"
                );

            if(loadFailure.get()!=null)
                throw new AssertionError(
                    "load behind aborted final reservation failed",
                    loadFailure.get()
                );

            System.out.println(
                "WORLD_PERSISTENCE_FINAL_TIMEOUT_HANDOFF_PASS "+
                "callerTimeoutDoesNotAbortInFlight=true "+
                "lateWorldActionPublished=true "+
                "outerUnregisterCancelsQueuedAction=true "+
                "cancelledReservationReleasedWorker=true"
            );
        }finally{
            if(cancelledPlayer.registered())
                cancelledWorld.unregisterPlayer(
                    cancelledPlayer,
                    cancelledPlayer.generation()
                );

            cancelledWorld.close();
        }
    }

    private static void assertQueuedCheckpointCannotOverwriteReservedFinal()
        throws Exception
    {
        RecordingRepository repository=
            new RecordingRepository();
        World world=
            World.isolatedForTest(
                60_000L,
                repository
            );
        WorldPlayer player=
            new WorldPlayer();

        try{
            long generation=
                world.registerPlayer(
                    player,
                    LocalAccountProfiles.PRIMARY
                );

            world.start();

            /*
             * With an otherwise idle persistence worker this reservation runs
             * first and waits for publication. The checkpoint drain scheduled
             * below is therefore FIFO-behind the reserved final slot.
             */
            WorldPlayerPersistence.FinalSaveReservation
                reservation=
                    world.persistence()
                        .reserveFinalSaveWithBackpressure(
                            5_000L
                        );

            long capturedBefore=
                world.persistence()
                    .checkpointCapturedCount();

            world.submitAndWait(
                player,
                generation,
                ()->{
                    player.movement()
                        .setRunEnergy(
                            41
                        );

                    world.persistence()
                        .checkpointDue(
                            WorldPlayerPersistence
                                .AUTOSAVE_INTERVAL_TICKS
                        );
                },
                5_000L
            );

            if(world.persistence()
                    .checkpointCapturedCount()!=
               capturedBefore+1L)
                throw new AssertionError(
                    "checkpoint-behind-final fixture did not capture autosave"
                );

            AtomicReference<
                WorldPlayerPersistence.SaveTicket
            > finalTicket=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                generation,
                ()->{
                    player.movement()
                        .setRunEnergy(
                            99
                        );

                    WorldPlayerPersistence.CapturedSave
                        captured=
                            world.persistence()
                                .captureDeferredFinalSave(
                                    LocalAccountProfiles.PRIMARY,
                                    player,
                                    generation,
                                    0,
                                    "[final-checkpoint-fence-test] ",
                                    "SESSION_END"
                                );

                    if(!world.unregisterPlayer(
                            player,
                            generation))
                        throw new AssertionError(
                            "checkpoint-fence final action could not retire exact generation"
                        );

                    finalTicket.set(
                        reservation.publish(
                            captured
                        )
                    );
                },
                5_000L
            );

            WorldPlayerPersistence.SaveTicket ticket=
                finalTicket.get();

            if(ticket==null)
                throw new AssertionError(
                    "checkpoint-fence final ticket missing"
                );

            ticket.completion.get(
                5,
                TimeUnit.SECONDS
            );

            Optional<PlayerSnapshot> loaded=
                world.persistence()
                    .load(
                        LocalAccountProfiles.PRIMARY
                    );

            if(!loaded.isPresent())
                throw new AssertionError(
                    "checkpoint-fence final snapshot missing"
                );

            String energy=
                loaded.get().value(
                    "movement.runEnergy"
                );

            if(!"99".equals(
                    energy
                ))
                throw new AssertionError(
                    "queued checkpoint overwrote final snapshot energy="+
                    energy
                );

            System.out.println(
                "WORLD_PERSISTENCE_FINAL_CHECKPOINT_FENCE_PASS "+
                "queuedCheckpointDiscarded=true "+
                "finalSnapshotRemainsAuthoritative=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );

            world.close();
        }
    }

    private static final class RecordingRepository
        implements PlayerRepository {
        volatile PlayerSnapshot stored;

        @Override public Optional<PlayerSnapshot> load(
            String username
        ){
            return Optional.ofNullable(
                stored
            );
        }

        @Override public void save(
            PlayerSnapshot snapshot
        ){
            stored=snapshot;
        }
    }

    private static void awaitSaveCount(
        BlockingFirstRepository repository,
        int expected,
        long timeoutMillis
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.MILLISECONDS.toNanos(
                timeoutMillis
            );

        while(repository.savedEnergySnapshot()
                    .size()<expected&&
              System.nanoTime()<deadline)
            Thread.sleep(5L);

        int actual=
            repository.savedEnergySnapshot()
                .size();

        if(actual<expected)
            throw new AssertionError(
                "persistence save count timeout expected="+
                expected+
                " actual="+actual
            );
    }

    private static final class BlockingFirstRepository
        implements PlayerRepository {

        final CountDownLatch firstStarted=
            new CountDownLatch(1);
        final CountDownLatch releaseFirst=
            new CountDownLatch(1);
        final AtomicInteger calls=
            new AtomicInteger();
        final List<Integer> savedEnergy=
            Collections.synchronizedList(
                new ArrayList<>()
            );
        volatile PlayerSnapshot stored;

        @Override public Optional<PlayerSnapshot> load(
            String username
        ){
            return Optional.ofNullable(
                stored
            );
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
            int call=
                calls.incrementAndGet();

            if(call==1){
                firstStarted.countDown();

                try{
                    releaseFirst.await();
                }catch(InterruptedException failure){
                    Thread.currentThread()
                        .interrupt();

                    throw new IOException(
                        "blocking first save interrupted",
                        failure
                    );
                }
            }

            savedEnergy.add(
                Integer.parseInt(
                    snapshot.value(
                        "movement.runEnergy"
                    )
                )
            );
            stored=snapshot;
        }

        List<Integer> savedEnergySnapshot(){
            synchronized(savedEnergy){
                return new ArrayList<>(
                    savedEnergy
                );
            }
        }
    }

    private WorldPersistenceFinalSaveBackpressureTest(){}
}
