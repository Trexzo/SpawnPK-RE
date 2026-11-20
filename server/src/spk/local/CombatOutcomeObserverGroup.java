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
     * R81 OFFLINE ONLY. Decide whether an exact verified fact instance may
     * enter this group's projection fanout for the first time. Never retries
     * an interrupted first fanout: this is at-most-once, not exactly-once.
     */
    @FunctionalInterface
    interface OfflineReplayAdmission {
        boolean beginFirstDelivery(CombatOutcome fact);
    }

    private volatile OfflineReplayAdmission offlineReplayAdmission;

    void installOfflineReplayAdmissionForTest(OfflineReplayAdmission gate){
        Objects.requireNonNull(gate,"gate");
        synchronized(this){
            if(offlineReplayAdmission!=null)
                throw new IllegalStateException(
                    "R81_REPLAY_GATE_DENIED already installed"
                );
            if(offlineProjectionAdmission==null)
                throw new IllegalStateException(
                    "R81_REPLAY_GATE_DENIED R80 owner gate required"
                );
            offlineReplayAdmission=gate;
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

            // Admit *before* the first mutation, after the R80 owner
            // preflight. A duplicate does not re-enter partially applied
            // objectives, match counters, or Collection Log resolution.
            if(i==0){
                OfflineReplayAdmission replay=offlineReplayAdmission;
                if(replay!=null&&!replay.beginFirstDelivery(fact))
                    return;
            }

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
