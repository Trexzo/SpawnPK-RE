package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** R71: actual final-save/checkpoint collision and forced shutdown tests. */
public final class R71OfflineFinalSaveCheckpointFenceTest {
    private static final String USER="opensrc";

    static final class PausedLoad {
        final CountDownLatch arrived=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
        final AtomicInteger interrupted=new AtomicInteger();
    }

    static final class CountingRepository implements PlayerRepository {
        private final FilePlayerRepository delegate;
        final AtomicInteger saves=new AtomicInteger();
        final AtomicInteger committed=new AtomicInteger();
        final AtomicBoolean failNext=new AtomicBoolean();
        final AtomicReference<PausedLoad> nextLoad=new AtomicReference<>();

        CountingRepository(Path accountRoot){
            delegate=new FilePlayerRepository(
                name->accountRoot.resolve(name+".properties")
            );
        }
        @Override public Optional<PlayerSnapshot> load(String user)
                throws IOException{
            PausedLoad p=nextLoad.getAndSet(null);
            if(p!=null){
                p.arrived.countDown();
                // Intentional uninterruptible TEST-OWNED load for the
                // real WorldPlayerPersistence forced-shutdown case.
                while(p.release.getCount()!=0L){
                    try{
                        p.release.await(25L,TimeUnit.MILLISECONDS);
                    }catch(InterruptedException ignored){
                        p.interrupted.incrementAndGet();
                    }
                }
            }
            return delegate.load(user);
        }
        @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
            saves.incrementAndGet();
            if(failNext.compareAndSet(true,false))
                throw new IOException(
                    "R71_TEST_REPOSITORY_PREWRITE_FAILURE"
                );
            delegate.save(snapshot);
            committed.incrementAndGet();
        }
    }

    static final class Fixture implements AutoCloseable {
        final Path root,journal,accounts,generations,sessions;
        final CountingRepository repo;
        final World world;
        final WorldPlayer actor=new WorldPlayer();
        final WorldPlayerPersistence persistence;
        final R71OfflineFinalSaveCheckpointFence gate;
        final PlayerSnapshot initial;
        final R64OfflineSessionOwnership.Token first;
        boolean closed;

        Fixture(String name)throws Exception{
            root=Files.createTempDirectory("spk-r71-"+name+"-");
            journal=root.resolve("journal");
            accounts=root.resolve("accounts");
            generations=root.resolve("generations");
            sessions=root.resolve("sessions");
            for(Path p:new Path[]{journal,accounts,generations,sessions})
                Files.createDirectory(p);
            repo=new CountingRepository(accounts);
            world=World.isolatedForTest(60_000L,repo);
            persistence=world.persistence();
            gate=new R71OfflineFinalSaveCheckpointFence(
                journal,accounts,generations,sessions
            );
            R60TradePairCoordinator.initializeEmpty(journal);
            actor.bank().spawnItem(995,100,packets());
            initial=PlayerSnapshotCodec.capture(USER,actor);
            new FilePlayerRepository(
                u->accounts.resolve(u+".properties")
            ).save(initial);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,USER
            );
            first=R64OfflineSessionOwnership.claimFirst(
                journal,accounts,generations,sessions,USER
            );
            persistence.installOfflineIoWriteAdmissionForTest(gate);
            persistence.installOfflineCheckpointOwnerCaptureForTest(gate);
            world.registerPlayer(actor,USER);
            world.start();
            gate.bindActorForTest(actor,actor.generation(),first);
        }

        void checkpoint(long tick,int item)throws Exception{
            world.submitAndWait(actor,()->{
                if(item!=0)
                    actor.bank().spawnItem(item,1,packets());
                persistence.checkpointDue(tick);
            },5_000L);
        }

        WorldPlayerPersistence.CapturedSave captureManual(int item)
                throws Exception{
            AtomicReference<WorldPlayerPersistence.CapturedSave> result=
                new AtomicReference<>();
            world.submitAndWait(actor,()->{
                if(item!=0)
                    actor.bank().spawnItem(item,1,packets());
                result.set(persistence.captureDeferredSave(
                    USER,actor,actor.generation(),0,
                    "[r71-offline] ","STALE_MANUAL"
                ));
            },5_000L);
            return result.get();
        }

        WorldPlayerPersistence.CapturedSave finalCapture(
            R64OfflineSessionOwnership.Token owner,int item
        )throws Exception{
            AtomicReference<WorldPlayerPersistence.CapturedSave> result=
                new AtomicReference<>();
            world.submitAndWait(actor,()->{
                if(item!=0)
                    actor.bank().spawnItem(item,1,packets());
                WorldPlayerPersistence.CapturedSave captured=
                    persistence.captureDeferredFinalSave(
                        USER,actor,actor.generation(),0,
                        "[r71-offline] ","FINAL_LOGOUT"
                    );
                try{
                    gate.sealFinalSaveForTest(captured,owner);
                }catch(IOException error){
                    throw new IllegalStateException(
                        "test-only final seal denied",error
                    );
                }
                result.set(captured);
            },5_000L);
            require(result.get()!=null,"final snapshot not captured");
            return result.get();
        }

        R64OfflineSessionOwnership.Token releasedOwner()throws Exception{
            R64OfflineSessionOwnership.Review r=
                R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,USER
                );
            require(r.status==
                    R64OfflineSessionOwnership.Status.BLOCK_RELEASED &&
                    !r.loginAuthorized(),
                "final logout not durably RELEASED: "+r.status);
            R63AccountSaveAdmission.Check acc=
                R63AccountSaveAdmission.inspect(
                    journal,accounts,generations,USER
                );
            require(acc.status==
                    R63AccountSaveAdmission.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS,
                "account ledger mismatch after final save");
            return new R64OfflineSessionOwnership.Token(
                USER,first.owner,first.epoch,
                R64OfflineSessionOwnership.Phase.RELEASED,acc.token
            );
        }

        R64OfflineSessionOwnership.Token newOwner(
            R64OfflineSessionOwnership.Token released
        )throws Exception{
            R64OfflineSessionOwnership.Token next=
                R64OfflineSessionOwnership.replaceOwner(
                    journal,accounts,generations,sessions,released
                );
            PlayerSnapshot canonical=repo.load(USER).orElseThrow(
                ()->new IOException("missing canonical after logout")
            );
            world.submitAndWait(actor,()->{
                PlayerSnapshotCodec.applyValidated(canonical,actor);
                persistence.releaseCheckpointSuppression(
                    actor.id(),actor.generation()
                );
            },5_000L);
            gate.bindActorForTest(actor,actor.generation(),next);
            return next;
        }

        byte[][] evidence()throws IOException{
            return new byte[][]{
                Files.readAllBytes(accounts.resolve(
                    "opensrc.properties")),
                Files.readAllBytes(generations.resolve(
                    "opensrc.r63-generation.properties")),
                Files.readAllBytes(sessions.resolve(
                    "opensrc.r64-session.properties"))
            };
        }

        void unchanged(byte[][] before,String why)throws IOException{
            byte[][] after=evidence();
            for(int i=0;i<before.length;i++)
                require(Arrays.equals(before[i],after[i]),
                    "test-owned durable account evidence changed: "+why+
                    " fileIndex="+i);
        }

        @Override public void close()throws Exception{
            if(closed)return;
            closed=true;
            if(actor.registered())
                world.unregisterPlayer(actor);
            world.close();
            try(java.util.stream.Stream<Path> paths=Files.walk(root)){
                paths.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException problem){
                        throw new IllegalStateException(
                            "test-only cleanup "+p,problem
                        );
                    }
                });
            }
        }
    }

    public static void main(String[] args)throws Exception{
        finalSaveBeatsAlreadyQueuedCheckpoint();
        failedFinalNeverRearmsCheckpoint();
        shutdownDropsQueuedFinalWithoutReusingTicket();
        backpressureDropsFinalWithoutRearming();
        System.out.println(
            "R71_OFFLINE_FINAL_SAVE_CHECKPOINT_SHUTDOWN_PASS "+
            "realCheckpointQueuedBeforeFinalSealDeniedAtCommit=true "+
            "finalSaveAndOwnerReleaseOneOsLock=true "+
            "postFinalCheckpointSuppressionWorks=true "+
            "oldManualCallbackRefusedAfterLogout=true "+
            "explicitNextEpochCheckpointStillCommits=true "+
            "failedFinalSealRemainsFailClosed=true "+
            "droppedShutdownFinalTicketRevoked=true "+
            "forcedShutdownPreservesAccountGenerationAndOwner=true "+
            "cancelledCheckpointNeverReachesRepository=true "+
            "fullIoQueueRevokesRejectedFinalTicket=true "+
            "noLiveSessionInstallationOrPhysicalPowerLossClaim=true"
        );
    }

    private static void finalSaveBeatsAlreadyQueuedCheckpoint()
            throws Exception{
        try(Fixture f=new Fixture("final-collision")){
            // Prepare a different old manual save; it must be refused
            // after final logout, not resurrected from the IO queue.
            WorldPlayerPersistence.CapturedSave stale=
                f.captureManual(566);
            f.gate.bindManualForTest(stale,f.first);

            R71OfflineFinalSaveCheckpointFence.Pause waiting=
                new R71OfflineFinalSaveCheckpointFence.Pause();
            f.gate.pauseNextCheckpointForTest(waiting);
            f.checkpoint(100L,385);
            try{
                require(waiting.arrived.await(10L,TimeUnit.SECONDS),
                    "real queued A checkpoint never reached worker");
                require(f.persistence.checkpointCapturedCount()==1L &&
                        f.repo.saves.get()==0,
                    "checkpoint was not paused before canonical write");
                WorldPlayerPersistence.CapturedSave finalA=
                    f.finalCapture(f.first,560);
                require(f.gate.pendingFinalTickets()==1,
                    "exact final save ticket not frozen");
                // Existing World checkpoint suppression on final capture
                // must prevent later checkpoints from entering the queue.
                f.checkpoint(200L,561);
                require(f.persistence.checkpointCapturedCount()==1L,
                    "checkpoint emitted after final capture suppression");
                byte[][] before=f.evidence();

                waiting.release.countDown();
                awaitMetric(f.persistence::failedCount,1L,
                    "old checkpoint did not refuse under final seal");
                require(f.repo.saves.get()==0 &&
                        f.gate.checkpointsDenied()==1 &&
                        f.persistence.checkpointWrittenCount()==0L,
                    "stale checkpoint reached canonical repository");
                f.unchanged(before,"old checkpoint after final seal");

                WorldPlayerPersistence.SaveTicket finalTicket=
                    f.persistence.submitCapturedWithBackpressure(
                        finalA,5_000L
                    );
                finalTicket.completion.get(15L,TimeUnit.SECONDS);
                require(f.repo.committed.get()==1&&
                        f.gate.finalsCommitted()==1 &&
                        f.gate.pendingFinalTickets()==0 &&
                        f.gate.sealedAccountCount()==1,
                    "final save did not commit and release exactly once");
                R64OfflineSessionOwnership.Token released=
                    f.releasedOwner();
                require(released.account.generation==2L,
                    "final save did not advance generation exactly once");

                int beforeOld=f.repo.saves.get();
                WorldPlayerPersistence.SaveTicket late=
                    f.persistence.submitCapturedWithBackpressure(
                        stale,5_000L
                    );
                Throwable rejected=failure(late.completion);
                require(rejected instanceof IOException &&
                        rejected.getMessage().contains(
                            "R64_SESSION_DENIED stale owner"),
                    "old manual callback escaped released-session fence: "+
                    rejected);
                require(f.repo.saves.get()==beforeOld,
                    "stale manual callback reached repository");
                try{
                    f.persistence.submitCapturedWithBackpressure(
                        finalA,5_000L
                    );
                    throw new AssertionError(
                        "final CapturedSave accepted duplicate submit"
                    );
                }catch(IllegalStateException expected){
                    require(expected.getMessage().contains(
                        "captured save already submitted"),
                        "wrong duplicate final ticket refusal");
                }

                R64OfflineSessionOwnership.Token next=
                    f.newOwner(released);
                require(next.epoch==2L&&
                        next.account.generation==2L,
                    "explicit replacement owner not created");
                f.checkpoint(300L,565);
                awaitMetric(f.persistence::checkpointWrittenCount,1L,
                    "fresh owner checkpoint did not commit after logout");
                R64OfflineSessionOwnership.Review check=
                    R64OfflineSessionOwnership.inspect(
                        f.journal,f.accounts,f.generations,f.sessions,USER
                    );
                require(check.status==
                        R64OfflineSessionOwnership.Status
                            .READY_FOR_OTHER_OFFLINE_CHECKS &&
                        check.token.account.generation==3L,
                    "successor checkpoint failed to advance generation");
                require(f.repo.committed.get()==2,
                    "new owner checkpoint wrote incorrect number of files");
            }finally{
                waiting.release.countDown();
            }
        }
    }

    private static void failedFinalNeverRearmsCheckpoint()
            throws Exception{
        try(Fixture f=new Fixture("failed-final")){
            byte[][] before=f.evidence();
            WorldPlayerPersistence.CapturedSave finalA=
                f.finalCapture(f.first,560);
            f.repo.failNext.set(true);
            WorldPlayerPersistence.SaveTicket failedTicket=
                f.persistence.submitCapturedWithBackpressure(
                    finalA,5_000L
                );
            Throwable error=failure(failedTicket.completion);
            require(error instanceof IOException &&
                    error.getMessage().contains(
                        "R71_TEST_REPOSITORY_PREWRITE_FAILURE"),
                "pre-write injected final failure not propagated: "+error);
            require(f.repo.committed.get()==0 &&
                    f.gate.finalsCommitted()==0 &&
                    f.gate.pendingFinalTickets()==0 &&
                    f.gate.sealedAccountCount()==1,
                "failed final was incorrectly committed or rearmed");
            f.unchanged(before,"failed final account publication");
            // No automatic checkpoint retry is allowed after the
            // terminal seal even though its I/O save failed.
            f.checkpoint(100L,565);
            require(f.persistence.checkpointCapturedCount()==0L &&
                    f.repo.committed.get()==0,
                "final-save failure reopened checkpoint capture");
            R64OfflineSessionOwnership.Review review=
                R64OfflineSessionOwnership.inspect(
                    f.journal,f.accounts,f.generations,f.sessions,USER
                );
            require(review.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    review.token.account.generation==1L,
                "failed final advanced or released session");
        }
    }

    private static void shutdownDropsQueuedFinalWithoutReusingTicket()
            throws Exception{
        try(Fixture f=new Fixture("forced-shutdown")){
            ExecutorService loader=Executors.newSingleThreadExecutor();
            PausedLoad blocked=new PausedLoad();
            f.repo.nextLoad.set(blocked);
            try{
                Future<Optional<PlayerSnapshot>> loadFuture=
                    loader.submit(()->f.persistence.load(USER));
                require(blocked.arrived.await(10L,TimeUnit.SECONDS),
                    "real IO executor did not block on load");
                // Both tasks are queued BEHIND the blocked load.
                f.checkpoint(100L,385);
                WorldPlayerPersistence.CapturedSave finalA=
                    f.finalCapture(f.first,560);
                WorldPlayerPersistence.SaveTicket queued=
                    f.persistence.submitCapturedWithBackpressure(
                        finalA,5_000L
                    );
                require(f.persistence.queuedWrites()==2 &&
                        f.gate.pendingFinalTickets()==1,
                    "checkpoint and final not queued behind blocked load");
                byte[][] before=f.evidence();

                // Real forced close: after bounded termination, queued
                // CheckpointDrainTask and SaveTask are both rejected.
                f.persistence.close();
                Throwable dropped=failure(queued.completion);
                require(dropped instanceof RejectedExecutionException &&
                        f.gate.pendingFinalTickets()==0 &&
                        f.gate.finalsDropped()==1 &&
                        f.gate.finalsCommitted()==0,
                    "shutdown failed to revoke pending final save ticket");
                require(f.repo.saves.get()==0 &&
                        f.persistence.checkpointWrittenCount()==0L,
                    "shutdown queued work reached repository");
                f.unchanged(before,"forced shutdown queued checkpoint/final");
                require(f.gate.sealedAccountCount()==1,
                    "shutdown mistakenly cleared terminal seal");
                // The consumed CapturedSave cannot be resubmitted to
                // reuse its old owner token after shutdown.
                try{
                    f.persistence.submitCapturedWithBackpressure(
                        finalA,0L
                    );
                    throw new AssertionError(
                        "shutdown dropped final save could be resubmitted"
                    );
                }catch(IllegalStateException expected){
                    require(expected.getMessage().contains(
                        "captured save already submitted"),
                        "wrong final replay rejection after shutdown");
                }
                blocked.release.countDown();
                try{loadFuture.get(10L,TimeUnit.SECONDS);}
                catch(ExecutionException rejectedLoad){
                    // Depending on shutdown timing, the in-flight load
                    // future is either settled exceptionally or completes
                    // with its original canonical snapshot.
                }
            }finally{
                blocked.release.countDown();
                loader.shutdownNow();
                require(loader.awaitTermination(10L,TimeUnit.SECONDS),
                    "test-owned loading caller did not terminate");
            }
        }
    }

    /**
     * R71 backpressure regression: the real bounded single-worker queue
     * is full of 64 already-submitted test-only (unbound) saves while its
     * I/O worker is paused in a repository load. Final save admission
     * times out BEFORE queueing, and its terminal token must be revoked.
     * The prior checkpoint seal remains fail-closed throughout.
     */
    private static void backpressureDropsFinalWithoutRearming()
            throws Exception{
        try(Fixture f=new Fixture("queue-backpressure")){
            ExecutorService loader=Executors.newSingleThreadExecutor();
            PausedLoad blocked=new PausedLoad();
            f.repo.nextLoad.set(blocked);
            try{
                Future<Optional<PlayerSnapshot>> loading=
                    loader.submit(()->f.persistence.load(USER));
                require(blocked.arrived.await(10L,TimeUnit.SECONDS),
                    "I/O worker not parked before backpressure test");
                for(int i=0;
                    i<WorldPlayerPersistence.MAX_PENDING_WRITES;i++){
                    // Deliberately no test-owner ticket binding: the
                    // queued work must never be granted a current token.
                    WorldPlayerPersistence.CapturedSave captured=
                        f.captureManual(0);
                    WorldPlayerPersistence.SaveTicket submitted=
                        f.persistence.submitCapturedWithBackpressure(
                            captured,0L
                        );
                    require(!submitted.completion.isDone(),
                        "bounded I/O queue unexpectedly drained");
                }
                require(f.persistence.queuedWrites()==
                        WorldPlayerPersistence.MAX_PENDING_WRITES,
                    "test-only queue did not reach its real capacity");

                WorldPlayerPersistence.CapturedSave finalA=
                    f.finalCapture(f.first,560);
                byte[][] before=f.evidence();
                WorldPlayerPersistence.SaveTicket denied=
                    f.persistence.submitCapturedWithBackpressure(
                        finalA,0L
                    );
                Throwable why=failure(denied.completion);
                require(why instanceof RejectedExecutionException &&
                        why.getMessage().contains(
                            "admission timed out"),
                    "full queue did not reject final-save admission: "+why);
                require(f.gate.finalsDropped()==1 &&
                        f.gate.pendingFinalTickets()==0 &&
                        f.gate.sealedAccountCount()==1 &&
                        f.gate.finalsCommitted()==0,
                    "backpressure left a reusable final-save token");
                require(f.repo.saves.get()==0,
                    "backpressure queue wrote canonical bytes");
                f.unchanged(before,"full queue final-save refusal");

                // Shutdown of the original real executor rejects all
                // 64 queued test tasks; none may be silently promoted
                // to the current session or alter durable generation.
                f.persistence.close();
                require(f.repo.saves.get()==0 &&
                        f.gate.pendingFinalTickets()==0,
                    "shutdown of saturated queue wrote or rearmed save");
                f.unchanged(before,"saturated queue forced shutdown");
                blocked.release.countDown();
                try{loading.get(10L,TimeUnit.SECONDS);}
                catch(ExecutionException ignored){
                    // In-flight load may be settled as failed at shutdown.
                }
            }finally{
                blocked.release.countDown();
                loader.shutdownNow();
                require(loader.awaitTermination(10L,TimeUnit.SECONDS),
                    "backpressure test loader thread did not terminate");
            }
        }
    }

    @FunctionalInterface private interface Metric{long get();}
    private static void awaitMetric(
        Metric metric,long value,String why
    )throws InterruptedException{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(metric.get()<value&&System.nanoTime()<deadline)
            Thread.sleep(15L);
        require(metric.get()>=value,
            why+" observed="+metric.get()+" required="+value);
    }

    private static Throwable failure(CompletableFuture<Void> result)
            throws Exception{
        try{
            result.get(15L,TimeUnit.SECONDS);
            throw new AssertionError("expected failed save");
        }catch(ExecutionException expected){
            return expected.getCause();
        }
    }

    private static ServerPacketWriter packets(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
    }

    private static void require(boolean condition,String error){
        if(!condition)throw new AssertionError(error);
    }

    private R71OfflineFinalSaveCheckpointFenceTest(){}
}
