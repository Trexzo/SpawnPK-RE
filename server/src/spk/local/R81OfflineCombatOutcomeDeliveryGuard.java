package spk.local;

import java.util.Objects;

/**
 * R81 OFFLINE ONLY. Delivery wrapper outside CombatOutcomeObserverGroup.
 * The original group is deliberately unaware of replay, queues, or retries.
 *
 * Must only be placed inside R79's exact-instance producer-authenticated
 * DomainEventBus listener; R80 checks both World actor generations here and
 * again inside the group. Ownership strings alone do not confer source proof.
 *
 * First admission is consumed before any projection write. A partially
 * completed first call is never retried or rolled back by this wrapper.
 */
final class R81OfflineCombatOutcomeDeliveryGuard
    implements CombatOutcomeObserver {
    private final R80OfflineCombatProjectionOwnershipFence owner;
    private final R81OfflineCombatOutcomeReplayFence ledger;
    private final CombatOutcomeObserverGroup group;

    R81OfflineCombatOutcomeDeliveryGuard(
        R80OfflineCombatProjectionOwnershipFence owner,
        R81OfflineCombatOutcomeReplayFence ledger,
        CombatOutcomeObserverGroup group
    ){
        this.owner=Objects.requireNonNull(owner,"owner");
        this.ledger=Objects.requireNonNull(ledger,"ledger");
        this.group=Objects.requireNonNull(group,"group");
    }

    @Override
    public void onCombatOutcome(CombatOutcome fact){
        Objects.requireNonNull(fact,"fact");
        if(!owner.permitsFanout(fact,0))
            return;
        if(!ledger.beginFirstDelivery(fact))
            return;
        group.onCombatOutcome(fact);
    }
}
