package spk.local;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * R82 OFFLINE ONLY, in-memory receipt per verified REAL source occurrence
 * and each specific observer index. It permits unfinished observers on
 * an explicitly trusted test reissue without repeating an observer that
 * has already started. No listener/multi-binding rollback or retry.
 *
 * The proof supplier is only valid inside an R79-attested World
 * DomainEventBus listener. It must never derive keys from public strings.
 */
final class R82OfflinePerProjectionReceiptLedger
    implements CombatOutcomeObserverGroup.OfflinePerObserverReceiptAdmission {

    @FunctionalInterface
    interface CurrentProvenance {
        R82OfflineCombatOccurrenceKeys.Key sourceKey(
            CombatOutcome fact
        );
    }

    private final CurrentProvenance provenance;
    private final int capacity;
    private final Map<R82OfflineCombatOccurrenceKeys.Key,BitSet> attempts=
        new HashMap<>();
    private long claimed;
    private long duplicate;
    private long unverified;
    private long capacityRejected;

    R82OfflinePerProjectionReceiptLedger(
        int capacity,CurrentProvenance provenance
    ){
        if(capacity<1||capacity>4096)
            throw new IllegalArgumentException(
                "R82_RECEIPT_DENIED invalid capacity="+capacity
            );
        this.capacity=capacity;
        this.provenance=Objects.requireNonNull(
            provenance,"provenance"
        );
    }

    @Override public synchronized boolean tryBegin(
        CombatOutcome fact,int index
    ){
        if(fact==null||index<0||index>=64)
            throw new IllegalArgumentException(
                "R82_RECEIPT_DENIED invalid fact/index"
            );

        R82OfflineCombatOccurrenceKeys.Key key=
            provenance.sourceKey(fact);
        if(key==null){
            unverified++;
            return false;
        }

        BitSet observers=attempts.get(key);
        if(observers==null){
            if(attempts.size()>=capacity){
                capacityRejected++;
                return false;
            }
            observers=new BitSet(64);
            attempts.put(key,observers);
        }
        if(observers.get(index)){
            duplicate++;
            return false;
        }

        // Claim BEFORE the observer, including if it later throws
        // after a partial mutation. Never replay that observer.
        observers.set(index);
        claimed++;
        return true;
    }

    synchronized int sourceCount(){return attempts.size();}
    synchronized long claimCount(){return claimed;}
    synchronized long duplicateCount(){return duplicate;}
    synchronized long unverifiedCount(){return unverified;}
    synchronized long capacityRefusalCount(){return capacityRejected;}
    synchronized boolean hasClaim(
        R82OfflineCombatOccurrenceKeys.Key key,int observerIndex
    ){
        BitSet bits=attempts.get(key);
        return bits!=null&&bits.get(observerIndex);
    }
}
