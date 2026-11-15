package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R70 OFFLINE ONLY: a real WorldPlayerPersistence IO task is committed by
 * R64's coordinated account + R63 ledger + owner-record publisher, rather
 * than by a raw unversioned PlayerRepository.save.
 *
 * The hook is installed only by an isolated test. Production World and
 * LocalSession do not reference it. It uses R60's cooperative OS lock and
 * carries the newly issued R64 token forward after each successful write.
 *
 * Crash atomicity across all three files is NOT proven: a partial failure
 * leaves the account quarantined by existing R63/R64 inspection, never
 * silently repaired.
 */
final class R70OfflineCoordinatedPersistenceAdmission
        implements WorldPlayerPersistence.OfflineSaveAdmission {
    private final Path journal;
    private final Path accounts;
    private final Path generations;
    private final Path sessions;
    private final boolean injectAfterLedgerBeforeOwnerForTest;
    private R64OfflineSessionOwnership.Token current;
    private final AtomicInteger accepted=new AtomicInteger();
    private final AtomicInteger unchanged=new AtomicInteger();

    R70OfflineCoordinatedPersistenceAdmission(
        Path journal,Path accounts,Path generations,Path sessions,
        R64OfflineSessionOwnership.Token owner,
        boolean injectAfterLedgerBeforeOwnerForTest
    ){
        this.journal=Objects.requireNonNull(journal,"journal");
        this.accounts=Objects.requireNonNull(accounts,"accounts");
        this.generations=Objects.requireNonNull(
            generations,"generations"
        );
        this.sessions=Objects.requireNonNull(sessions,"sessions");
        this.current=Objects.requireNonNull(owner,"owner");
        this.injectAfterLedgerBeforeOwnerForTest=
            injectAfterLedgerBeforeOwnerForTest;
    }

    /**
     * The real WorldPlayerPersistence worker calls this once per save,
     * including coalesced checkpoints and submitted deferred final saves.
     * All calls from its single IO thread are serialized. Synchronization
     * here also prevents callers of a test instance from racing its token.
     *
     * The supplied raw repository writer must NEVER run: that callback has
     * no R63/R64 metadata-commit protocol. R64.saveOwnedUnderLock is the
     * only permitted mutation route inside this hook.
     */
    @Override public synchronized void withAdmission(
        PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter rawWriter
    )throws IOException{
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(rawWriter,"rawWriter");
        if(!current.username.equals(snapshot.username()))
            throw new IOException(
                "R70_COMMIT_DENIED cross-account snapshot"
            );

        R60TradePairCoordinator.withExclusive(journal,false,()->{
            // Revalidate even for an identical snapshot; a replayed old
            // no-op must not survive a later cross-process owner takeover.
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,current
            );
            String digest=R57TradePairJournal.digest(snapshot);
            if(digest.equals(current.account.snapshotDigest)){
                unchanged.incrementAndGet();
                return null;
            }

            R64OfflineSessionOwnership.Token next=
                R64OfflineSessionOwnership.saveOwnedUnderLock(
                    journal,accounts,generations,sessions,current,
                    snapshot,injectAfterLedgerBeforeOwnerForTest
                );
            current=next;
            accepted.incrementAndGet();
            return null;
        });
    }

    synchronized R64OfflineSessionOwnership.Token currentOwnerForTest(){
        return current;
    }

    int acceptedWrites(){return accepted.get();}
    int unchangedWrites(){return unchanged.get();}
}
