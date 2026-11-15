package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R71 OFFLINE ONLY: synchronized capture-order freshness fence composed
 * with the R70 coordinated R60/R63/R64 account-save protocol.
 *
 * An ordinal is issued by WorldPlayerPersistence at immutable snapshot
 * capture on the World thread. It is NOT the IO enqueue/dequeue sequence.
 * When an older deferred snapshot arrives after a newer committed save,
 * this hook refuses it before any canonical account or metadata mutation.
 *
 * The highest admitted ordinal is in-memory only and scoped to this test
 * fixture/owner; it is NOT a durable cross-restart watermark. R64 ownership
 * still rejects cross-JVM owner replacement under the cooperative OS lock.
 * No production World or LocalSession ever installs this implementation.
 */
final class R71OfflineOrderedPersistenceAdmission
        implements WorldPlayerPersistence.OfflineOrderedSaveAdmission {
    private final Path journal;
    private final Path accounts;
    private final Path generations;
    private final Path sessions;
    private R64OfflineSessionOwnership.Token current;
    private long highestAcceptedCaptureOrdinal;
    private final AtomicInteger written=new AtomicInteger();
    private final AtomicInteger identical=new AtomicInteger();
    private final AtomicInteger staleRejected=new AtomicInteger();

    R71OfflineOrderedPersistenceAdmission(
        Path journal,Path accounts,Path generations,Path sessions,
        R64OfflineSessionOwnership.Token initial
    ){
        this.journal=Objects.requireNonNull(journal,"journal");
        this.accounts=Objects.requireNonNull(accounts,"accounts");
        this.generations=Objects.requireNonNull(generations,"generations");
        this.sessions=Objects.requireNonNull(sessions,"sessions");
        this.current=Objects.requireNonNull(initial,"initial");
    }

    /** Missing capture-order metadata is never silently accepted. */
    @Override public void withAdmission(
        PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter rawWriter
    )throws IOException{
        throw new IOException(
            "R71_SAVE_DENIED missing capture ordinal"
        );
    }

    @Override public synchronized void withOrderedAdmission(
        long captureOrdinal,
        PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter rawWriter
    )throws IOException{
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(rawWriter,"rawWriter");
        if(captureOrdinal<=0L)
            throw new IOException(
                "R71_SAVE_DENIED invalid capture ordinal"
            );
        if(!current.username.equals(snapshot.username()))
            throw new IOException(
                "R71_SAVE_DENIED cross-account snapshot"
            );

        R60TradePairCoordinator.withExclusive(journal,false,()->{
            // Ownership and R63/R59 ambiguity must be checked before even
            // an identical-snapshot or stale-watermark return.
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,current
            );
            if(captureOrdinal<=highestAcceptedCaptureOrdinal){
                staleRejected.incrementAndGet();
                throw new IOException(
                    "R71_SAVE_DENIED stale capture ordinal"
                );
            }

            String digest=R57TradePairJournal.digest(snapshot);
            if(digest.equals(current.account.snapshotDigest)){
                highestAcceptedCaptureOrdinal=captureOrdinal;
                identical.incrementAndGet();
                return null;
            }

            current=R64OfflineSessionOwnership.saveOwnedUnderLock(
                journal,accounts,generations,sessions,current,
                snapshot,false
            );
            highestAcceptedCaptureOrdinal=captureOrdinal;
            written.incrementAndGet();
            return null;
        });
    }

    synchronized long highestAcceptedOrdinal(){
        return highestAcceptedCaptureOrdinal;
    }

    synchronized R64OfflineSessionOwnership.Token currentToken(){
        return current;
    }

    int committedWrites(){return written.get();}
    int identicalWrites(){return identical.get();}
    int rejectedOldWrites(){return staleRejected.get();}
}
