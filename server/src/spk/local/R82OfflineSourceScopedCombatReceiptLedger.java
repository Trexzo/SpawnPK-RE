package spk.local;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * R82 OFFLINE ONLY: scope-local ordinals issued from the ACTUAL R79
 * PlayerCombatResolutionService outcome callback. The source ordinal
 * is not derived from player strings, World ticks or event equality.
 *
 * A World/JVM restart creates a new issuer scope. This ledger is NEVER
 * durable and cannot claim cross-process exactly-once semantics.
 *
 * R79 proves the exact CombatOutcome object while the synchronous
 * DomainEventBus callback runs. The source supplies an ordinal at the
 * verified emission, and the ledger consumes it at most once across
 * multiple independent CombatOutcomeObserverGroup subscribers.
 */
final class R82OfflineSourceScopedCombatReceiptLedger
    implements
        R79OfflinePairedPvpOwnershipGate.OfflineSourceEmissionIssuer,
        CombatOutcomeObserverGroup.OfflineFirstDeliveryAdmission {

    private static final class CurrentEmission {
        final CombatOutcome fact;
        final long ordinal;
        CurrentEmission(CombatOutcome fact,long ordinal){
            this.fact=fact;
            this.ordinal=ordinal;
        }
    }

    private final R79OfflinePairedPvpOwnershipGate source;
    private final UUID scope=UUID.randomUUID();
    private final int capacity;
    private final ThreadLocal<CurrentEmission> current=
        new ThreadLocal<>();
    private final Set<Long> consumed=new HashSet<>();
    private long lastIssuedOrdinal;
    private long duplicateDenied;
    private long unprovenDenied;
    private long capacityDenied;
    private long accepted;

    /**
     * R83 OFFLINE ONLY. The receipt must be synchronously PREPARED
     * by a caller-owned fixture journal BEFORE the first observer
     * can mutate any projection. A denial consumes no in-memory
     * receipt and never runs the observer; disk ambiguity is left
     * quarantined rather than replayed.
     */
    @FunctionalInterface
    interface OfflinePreparedAdmission {
        boolean prepare(String sourceScope,long ordinal,CombatOutcome fact);
    }

    private OfflinePreparedAdmission offlinePreparedAdmission;
    private long preparedDenied;

    synchronized void installOfflinePreparedAdmissionForTest(
        OfflinePreparedAdmission admission
    ){
        Objects.requireNonNull(admission,"admission");
        if(offlinePreparedAdmission!=null||lastIssuedOrdinal!=0L||
           !consumed.isEmpty())
            throw new IllegalStateException(
                "R83_PREPARE_GATE_DENIED already installed or used"
            );
        offlinePreparedAdmission=admission;
    }

    R82OfflineSourceScopedCombatReceiptLedger(
        R79OfflinePairedPvpOwnershipGate source,int capacity
    ){
        this.source=Objects.requireNonNull(source,"source");
        if(capacity<1||capacity>4096)
            throw new IllegalArgumentException(
                "R82_RECEIPT_DENIED invalid capacity "+capacity
            );
        this.capacity=capacity;
    }

    @Override public synchronized void onSourceEmission(
        CombatOutcome fact,long ordinal
    ){
        Objects.requireNonNull(fact,"fact");
        if(ordinal<=0L||ordinal!=lastIssuedOrdinal+1L||
           current.get()!=null)
            throw new IllegalStateException(
                "R82_SOURCE_DENIED nonmonotonic/nested ordinal"
            );
        // Issued by the R79 test adapter ONLY at the verified
        // PlayerCombatResolutionService outcome callback. We do not
        // accept a reusable self-declared source ID in CombatOutcome.
        lastIssuedOrdinal=ordinal;
        current.set(new CurrentEmission(fact,ordinal));
    }

    @Override public synchronized void afterSourceEmission(
        CombatOutcome fact
    ){
        CurrentEmission issued=current.get();
        if(issued==null||issued.fact!=fact)
            throw new IllegalStateException(
                "R82_SOURCE_DENIED source-scope mismatch"
            );
        current.remove();
    }

    @Override public synchronized boolean tryBegin(CombatOutcome fact){
        Objects.requireNonNull(fact,"fact");
        CurrentEmission issued=current.get();
        if(issued==null||issued.fact!=fact||
           !source.isVerifiedFactInCurrentListenerForTest(fact)){
            unprovenDenied++;
            return false;
        }
        // Both independent observer groups see the SAME source-issued
        // ordinal but have different Java callback/subscriber handles.
        // Never restart a partial or failed fanout under a second group.
        if(consumed.contains(issued.ordinal)){
            duplicateDenied++;
            return false;
        }
        if(consumed.size()>=capacity){
            capacityDenied++;
            return false;
        }
        // The producer proof and available receipt capacity are
        // checked above. The R83 hook is *optional*, never live.
        // It must persist its PREPARED marker before returning true.
        OfflinePreparedAdmission gate=offlinePreparedAdmission;
        if(gate!=null &&
           !gate.prepare(scope.toString(),issued.ordinal,fact)){
            preparedDenied++;
            return false;
        }
        consumed.add(issued.ordinal);
        accepted++;
        return true;
    }

    synchronized int receiptCount(){return consumed.size();}
    synchronized long acceptedCount(){return accepted;}
    synchronized long duplicateCount(){return duplicateDenied;}
    synchronized long unprovenCount(){return unprovenDenied;}
    synchronized long capacityCount(){return capacityDenied;}
    synchronized long preparedDenialCount(){return preparedDenied;}
    synchronized long issuedCount(){return lastIssuedOrdinal;}
    String scopeForAudit(){return scope.toString();}
}
