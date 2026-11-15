package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * R71 OFFLINE-ONLY final-save / checkpoint ordering authority.
 *
 * At a test-owned World actor's final-save capture, seal that exact R64
 * owner epoch and pin one CapturedSave sequence + immutable digest. A
 * checkpoint reaching the REAL WorldPlayerPersistence I/O worker after
 * the seal is rejected before the canonical repository, even when it
 * was captured before final logout.
 *
 * The final save runs with the same real R60 OS lock across R64/R63
 * account publication AND the RELEASED owner record, so no cooperative
 * takeover can interleave between final save and logout.
 *
 * The seal is fail-closed and remains after a repository error, timeout,
 * forced shutdown or dropped final ticket. There is NO implicit rearm
 * or old-token promotion. Only an explicitly newer owner epoch may
 * capture a later checkpoint; actual login is never authorized.
 *
 * This hook is never installed from LocalSession or production World.
 * The callback token is a test fixture, not an authenticated identity.
 */
final class R71OfflineFinalSaveCheckpointFence
    implements WorldPlayerPersistence.OfflineCheckpointOwnerCapture,
               WorldPlayerPersistence.OfflineIoWriteAdmission {

    static final class Pause {
        final CountDownLatch arrived=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
    }

    private static final class FinalSeal {
        final R64OfflineSessionOwnership.Token owner;
        final long sequence;
        final String finalDigest;
        FinalSeal(
            R64OfflineSessionOwnership.Token owner,
            long sequence,String finalDigest
        ){
            this.owner=owner;
            this.sequence=sequence;
            this.finalDigest=finalDigest;
        }
    }

    private final Path journal,accounts,generations,sessions;
    private final R70OfflineCheckpointAuthority checkpointCapture;
    private final R69OfflineIoCommitGate manualGate;
    private final Object sealLock=new Object();
    private final Map<String,FinalSeal> sealedOwners=new HashMap<>();
    private final Map<Long,FinalSeal> queuedFinals=new HashMap<>();
    private final AtomicReference<Pause> pauseNextCheckpoint=
        new AtomicReference<>();
    private final AtomicInteger checkpointDenied=new AtomicInteger();
    private final AtomicInteger checkpointCommitted=new AtomicInteger();
    private final AtomicInteger finalCommitted=new AtomicInteger();
    private final AtomicInteger finalDropped=new AtomicInteger();

    R71OfflineFinalSaveCheckpointFence(
        Path journal,Path accounts,Path generations,Path sessions
    ){
        this.journal=Objects.requireNonNull(journal,"journal");
        this.accounts=Objects.requireNonNull(accounts,"accounts");
        this.generations=Objects.requireNonNull(generations,"generations");
        this.sessions=Objects.requireNonNull(sessions,"sessions");
        checkpointCapture=new R70OfflineCheckpointAuthority(
            journal,accounts,generations,sessions
        );
        manualGate=new R69OfflineIoCommitGate(
            journal,accounts,generations,sessions
        );
    }

    void bindActorForTest(
        WorldPlayer actor,long generation,
        R64OfflineSessionOwnership.Token owner
    )throws IOException{
        checkpointCapture.bindForTest(actor,generation,owner);
    }

    void unbindActorForTest(WorldPlayer actor){
        checkpointCapture.unbindForTest(actor);
    }

    @Override public R64OfflineSessionOwnership.Token captureOwnerForTest(
        WorldPlayer actor,long generation,PlayerSnapshot snapshot
    ){
        return checkpointCapture.captureOwnerForTest(
            actor,generation,snapshot
        );
    }

    /**
     * Call immediately after captureDeferredFinalSave on the test-owned
     * World execution thread. CapturedSave includes snapshot and World
     * generation from THAT actor. Sealing never acquires the R60 OS lock
     * from inside the World actor's mutationLock, avoiding lock inversion.
     *
     * Any concurrent checkpoint commit linearizes before OR after the
     * seal under sealLock. A checkpoint after the seal cannot advance
     * the old owner's account generation ahead of the final save.
     */
    void sealFinalSaveForTest(
        WorldPlayerPersistence.CapturedSave captured,
        R64OfflineSessionOwnership.Token expected
    )throws IOException{
        Objects.requireNonNull(captured,"captured");
        Objects.requireNonNull(expected,"expected");
        if(expected.phase!=R64OfflineSessionOwnership.Phase.ACTIVE||
           !expected.username.equals(
               captured.ticket.snapshot.username()
           )||
           captureOwnerForTest(
               captured.owner,captured.expectedGeneration,
               captured.ticket.snapshot
           )!=expected)
            throw new IOException(
                "R71_FINAL_SEAL_DENIED actor/session mismatch"
            );
        String digest=R57TradePairJournal.digest(
            captured.ticket.snapshot
        );
        FinalSeal sealed=new FinalSeal(
            expected,captured.ticket.sequence,digest
        );
        synchronized(sealLock){
            FinalSeal previous=sealedOwners.get(expected.username);
            if((previous!=null&&
                expected.epoch<=previous.owner.epoch)||
               queuedFinals.containsKey(sealed.sequence))
                throw new IOException(
                    "R71_FINAL_SEAL_DENIED same/older terminal epoch"
                );
            // A later explicitly bound session epoch may seal its own
            // final snapshot. The new seal rejects ALL checkpoints from
            // the old epochs. Any still-queued earlier final ticket
            // cannot pass the identity check at its eventual commit.
            sealedOwners.put(expected.username,sealed);
            queuedFinals.put(sealed.sequence,sealed);
        }
    }

    /** Optional non-final manual test callbacks retain R69 validation. */
    void bindManualForTest(
        WorldPlayerPersistence.CapturedSave captured,
        R64OfflineSessionOwnership.Token owner
    )throws IOException{
        manualGate.bindForTest(captured,owner);
    }

    void pauseNextCheckpointForTest(Pause pause){
        Objects.requireNonNull(pause,"pause");
        if(!pauseNextCheckpoint.compareAndSet(null,pause))
            throw new IllegalStateException(
                "R71_CHECKPOINT_PAUSE_ALREADY_ARMED"
            );
    }

    private static boolean obsoleteUnderSeal(
        FinalSeal terminal,R64OfflineSessionOwnership.Token captured
    ){
        // A current or older epoch can never inherit authority from an
        // explicitly sealed owner. A later epoch must have been granted
        // independently via the durable R64 replacement protocol.
        return terminal!=null&&
            (captured==null||
             captured.epoch<=terminal.owner.epoch);
    }

    @Override public void persistCheckpointAtCommit(
        long sequence,PlayerSnapshot snapshot,PlayerRepository repository,
        R64OfflineSessionOwnership.Token checkpointOwner
    )throws IOException{
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(repository,"repository");
        // Test-only deterministic pause BEFORE acquiring sealLock: a
        // World-thread final-save seal can race with a checkpoint already
        // in the REAL asynchronous persistence worker.
        Pause pause=pauseNextCheckpoint.getAndSet(null);
        if(pause!=null){
            pause.arrived.countDown();
            try{
                if(!pause.release.await(15L,TimeUnit.SECONDS))
                    throw new IOException(
                        "R71_CHECKPOINT_PAUSE_TIMEOUT"
                    );
            }catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                throw new IOException(
                    "R71_CHECKPOINT_PAUSE_INTERRUPTED",interrupted
                );
            }
        }
        synchronized(sealLock){
            if(checkpointOwner==null||
               checkpointOwner.phase!=
                   R64OfflineSessionOwnership.Phase.ACTIVE||
               !checkpointOwner.username.equals(snapshot.username())||
               obsoleteUnderSeal(
                   sealedOwners.get(snapshot.username()),checkpointOwner
               )){
                checkpointDenied.incrementAndGet();
                throw new IOException(
                    "R71_CHECKPOINT_DENIED sealed/stale/missing owner"
                );
            }
            R60TradePairCoordinator.withExclusive(journal,false,()->{
                // No owner is reconstructed from the latest account; the
                // immutable token traveling with THIS checkpoint is used.
                R64OfflineSessionOwnership.saveOwnedUnderLock(
                    journal,accounts,generations,sessions,
                    checkpointOwner,snapshot,false,repository
                );
                checkpointCommitted.incrementAndGet();
                return null;
            });
        }
    }

    @Override public void persistAtCommit(
        long sequence,PlayerSnapshot snapshot,PlayerRepository repository,
        boolean checkpoint
    )throws IOException{
        if(checkpoint)
            throw new IOException(
                "R71_CHECKPOINT_DENIED missing captured owner"
            );
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(repository,"repository");
        synchronized(sealLock){
            FinalSeal pending=queuedFinals.remove(sequence);
            if(pending==null){
                // Any expired/dropped final ticket cannot be revived.
                // An explicitly bound normal save still goes through R69.
                manualGate.persistAtCommit(
                    sequence,snapshot,repository,false
                );
                return;
            }
            if(!pending.owner.username.equals(snapshot.username())||
               !pending.finalDigest.equals(
                   R57TradePairJournal.digest(snapshot)
               )||
               sealedOwners.get(snapshot.username())!=pending)
                throw new IOException(
                    "R71_FINAL_COMMIT_DENIED captured ticket changed"
                );
            R60TradePairCoordinator.withExclusive(journal,false,()->{
                R64OfflineSessionOwnership.Token saved=
                    R64OfflineSessionOwnership.saveOwnedUnderLock(
                        journal,accounts,generations,sessions,
                        pending.owner,snapshot,false,repository
                    );
                R64OfflineSessionOwnership.releaseOwnerUnderLock(
                    journal,accounts,generations,sessions,saved
                );
                finalCommitted.incrementAndGet();
                return null;
            });
        }
    }

    /** Called by the real worker when shutdown/backpressure drops a
     * queued final SaveTask. Do not clear the seal: its checkpoint
     * quarantine must survive even if final publication never ran. */
    @Override public void onDroppedSaveForTest(long sequence){
        synchronized(sealLock){
            if(queuedFinals.remove(sequence)!=null)
                finalDropped.incrementAndGet();
        }
    }

    int pendingFinalTickets(){
        synchronized(sealLock){return queuedFinals.size();}
    }
    int sealedAccountCount(){
        synchronized(sealLock){return sealedOwners.size();}
    }
    int checkpointsDenied(){return checkpointDenied.get();}
    int checkpointsCommitted(){return checkpointCommitted.get();}
    int finalsCommitted(){return finalCommitted.get();}
    int finalsDropped(){return finalDropped.get();}
}
