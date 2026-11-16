package spk.local;

import java.util.IdentityHashMap;
import java.util.Objects;

/**
 * R81 OFFLINE TEST ONLY: bounded, synchronized, first-delivery-wins
 * receipts for exact immutable CombatOutcome INSTANCE identities.
 *
 * This must be installed only behind R79's real producer -> DomainEventBus
 * per-listener source proof and R80's owner-generation admission. It does
 * NOT authenticate standalone event strings, deduplicate a newly created
 * fact with the same fields, survive JVM restart, or implement durable
 * exactly-once delivery.
 *
 * Claim happens BEFORE the first observer. A partial or failed fanout
 * deliberately consumes its receipt: we refuse retries rather than
 * rerunning an already-committed objective, score or collection update.
 * Never evict a receipt and silently permit replay. On bounded capacity
 * exhaustion reject new events fail-closed; leave old receipts intact.
 */
final class R81OfflineCombatProjectionReceiptLedger
    implements CombatOutcomeObserverGroup.OfflineFirstDeliveryAdmission {

    private final IdentityHashMap<CombatOutcome,Boolean> seen=
        new IdentityHashMap<>();
    /**
     * This is proof of the EXACT live-in-scope source object, not
     * proof derived from attacker or victim string fields.
     */
    @FunctionalInterface
    interface OfflineSourceProof {
        boolean isVerifiedNow(CombatOutcome fact);
    }

    private final int maxReceipts;
    private final OfflineSourceProof sourceProof;
    private long firstDeliveries;
    private long duplicateRefusals;
    private long capacityRefusals;
    private long unverifiedRefusals;

    R81OfflineCombatProjectionReceiptLedger(
        int maxReceipts,OfflineSourceProof sourceProof
    ){
        if(maxReceipts<1||maxReceipts>4096)
            throw new IllegalArgumentException(
                "R81_RECEIPT_DENIED invalid bound "+maxReceipts
            );
        this.maxReceipts=maxReceipts;
        this.sourceProof=Objects.requireNonNull(
            sourceProof,"source proof"
        );
    }

    @Override public synchronized boolean tryBegin(CombatOutcome fact){
        Objects.requireNonNull(fact,"fact");
        if(seen.containsKey(fact)){
            duplicateRefusals++;
            return false;
        }
        // Only an R79 source-proven, current DomainEventBus callback
        // can claim a *new* event. A later replay of an already
        // claimed instance is denied above, even without a current
        // source proof. A forged new object is never marked seen.
        if(!sourceProof.isVerifiedNow(fact)){
            unverifiedRefusals++;
            return false;
        }
        if(seen.size()>=maxReceipts){
            capacityRefusals++;
            return false;
        }

        // No observer has executed yet; a later failure must not
        // release this claim or enable duplicate side effects.
        seen.put(fact,Boolean.TRUE);
        firstDeliveries++;
        return true;
    }

    synchronized int receiptCount(){return seen.size();}
    synchronized long firstDeliveryCount(){return firstDeliveries;}
    synchronized long duplicateRefusalCount(){return duplicateRefusals;}
    synchronized long capacityRefusalCount(){return capacityRefusals;}
    synchronized long unverifiedRefusalCount(){return unverifiedRefusals;}
}
