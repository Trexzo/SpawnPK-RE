package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Ordered in-process composition for CombatOutcomeObserver consumers.
 *
 * This is deliberately not an event bus: it owns no queue, replay, retry,
 * persistence or cross-thread delivery.
 */
final class CombatOutcomeObserverGroup
    implements CombatOutcomeObserver {

    @FunctionalInterface
    interface FailureHandler {
        void onFailure(
            int observerIndex,
            CombatOutcomeObserver observer,
            CombatOutcome outcome,
            RuntimeException failure
        );
    }

    private final List<CombatOutcomeObserver> observers;
    private final FailureHandler failureHandler;

    /**
     * R80 OFFLINE ONLY. Revalidate a verified World actor before EACH
     * observer, including after a previous observer returns or fails.
     * A terminal denial STOPS the fanout, not a retry/replay and not
     * an exception isolated into the old failure-handler loop.
     */
    @FunctionalInterface
    interface OfflineProjectionAdmission {
        boolean permits(CombatOutcome fact,int observerIndex);
    }

    private volatile OfflineProjectionAdmission offlineProjectionAdmission;

    /**
     * R81 OFFLINE ONLY. A verified source's exact immutable combat-fact
     * instance may begin this observer fanout only once. The caller-owned
     * receipt ledger claims BEFORE the first side effect, without retry,
     * replay, or rollback after a partial callback sequence.
     */
    @FunctionalInterface
    interface OfflineFirstDeliveryAdmission {
        boolean tryBegin(CombatOutcome fact);
    }

    private volatile OfflineFirstDeliveryAdmission offlineFirstDeliveryAdmission;

    /**
     * R82 OFFLINE ONLY. One independently recorded receipt per
     * sub-observer and verified source occurrence ID. A claimed
     * observer is skipped on a trusted reissue; unclaimed later
     * observers may continue. Claim happens BEFORE the callback.
     * A failing/partially-mutating observer is not retried.
     */
    @FunctionalInterface
    interface OfflinePerObserverReceiptAdmission {
        boolean tryBegin(CombatOutcome fact,int observerIndex);
    }

    private volatile OfflinePerObserverReceiptAdmission offlinePerObserverReceipt;

    void installOfflinePerObserverReceiptForTest(
        OfflinePerObserverReceiptAdmission admission
    ){
        Objects.requireNonNull(admission,"admission");
        synchronized(this){
            if(offlinePerObserverReceipt!=null||
               offlineFirstDeliveryAdmission!=null)
                throw new IllegalStateException(
                    "R82_RECEIPT_GATE_DENIED conflicting or duplicate receipt"
                );
            offlinePerObserverReceipt=admission;
        }
    }

    void installOfflineFirstDeliveryAdmissionForTest(
        OfflineFirstDeliveryAdmission admission
    ){
        Objects.requireNonNull(admission,"admission");
        synchronized(this){
            if(offlineFirstDeliveryAdmission!=null||
               offlinePerObserverReceipt!=null)
                throw new IllegalStateException(
                    "R81_RECEIPT_GATE_DENIED conflicting or duplicate receipt"
                );
            offlineFirstDeliveryAdmission=admission;
        }
    }

    void installOfflineProjectionAdmissionForTest(
        OfflineProjectionAdmission gate
    ){
        Objects.requireNonNull(gate,"gate");
        synchronized(this){
            if(offlineProjectionAdmission!=null)
                throw new IllegalStateException(
                    "R80_FANOUT_GATE_DENIED already installed"
                );
            offlineProjectionAdmission=gate;
        }
    }

    CombatOutcomeObserverGroup(
        List<? extends CombatOutcomeObserver> observers,
        FailureHandler failureHandler
    ){
        Objects.requireNonNull(
            observers,
            "observers"
        );

        if(observers.isEmpty())
            throw new IllegalArgumentException(
                "observers empty"
            );

        ArrayList<CombatOutcomeObserver> copy=
            new ArrayList<>();

        for(CombatOutcomeObserver observer:
                observers)
            copy.add(
                Objects.requireNonNull(
                    observer,
                    "observer"
                )
            );

        this.observers=
            Collections.unmodifiableList(
                copy
            );
        this.failureHandler=
            Objects.requireNonNull(
                failureHandler,
                "failureHandler"
            );
    }

    @Override
    public void onCombatOutcome(
        CombatOutcome outcome
    ){
        CombatOutcome fact=
            Objects.requireNonNull(
                outcome,
                "outcome"
            );

        for(int i=0;i<observers.size();i++){
            OfflineProjectionAdmission gate=offlineProjectionAdmission;
            if(gate!=null&&!gate.permits(fact,i))
                return; // terminal/expired generation is not an observer failure

            if(i==0){
                OfflineFirstDeliveryAdmission receipt=
                    offlineFirstDeliveryAdmission;
                // The owner-generation check above MUST precede the
                // first consumption claim. Failed admission must not
                // burn an unprocessed event's receipt.
                if(receipt!=null&&!receipt.tryBegin(fact))
                    return;
            }

            OfflinePerObserverReceiptAdmission perObserver=
                offlinePerObserverReceipt;
            if(perObserver!=null&&!perObserver.tryBegin(fact,i))
                continue; // earlier attempt already consumed this projection

            CombatOutcomeObserver observer=
                observers.get(i);

            try{
                observer.onCombatOutcome(
                    fact
                );
            }catch(RuntimeException failure){
                try{
                    failureHandler.onFailure(
                        i,
                        observer,
                        fact,
                        failure
                    );
                }catch(RuntimeException ignored){
                    /*
                     * Diagnostics are deliberately failure-isolated too.
                     * A broken failure reporter must not block later semantic
                     * consumers from receiving an already-produced fact.
                     */
                }
            }
        }
    }

    List<CombatOutcomeObserver> observers(){
        return observers;
    }

    int size(){
        return observers.size();
    }
}
