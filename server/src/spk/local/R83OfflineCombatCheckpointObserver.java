package spk.local;

import java.util.Objects;

/**
 * R83 OFFLINE ONLY. Instrument one real projection observer without
 * modifying CombatOutcomeObserverGroup, any projection service, or the
 * live event bus. Records callback entry and normal return; a thrown
 * callback is UNCONFIRMED even if it performed a mutation beforehand.
 *
 * This wrapper is diagnostic and MUST NOT be used to trigger retry.
 * It is not authenticated without the upstream R79 producer proof.
 */
final class R83OfflineCombatCheckpointObserver
    implements CombatOutcomeObserver {
    private final R80OfflineCombatProjectionOwnershipFence owner;
    private final R83OfflineCombatCheckpointAudit audit;
    private final R83OfflineCombatCheckpointAudit.Consumer consumer;
    private final CombatOutcomeObserver delegate;

    R83OfflineCombatCheckpointObserver(
        R80OfflineCombatProjectionOwnershipFence owner,
        R83OfflineCombatCheckpointAudit audit,
        R83OfflineCombatCheckpointAudit.Consumer consumer,
        CombatOutcomeObserver delegate
    ) {
        this.owner=Objects.requireNonNull(owner,"owner");
        this.audit=Objects.requireNonNull(audit,"audit");
        this.consumer=Objects.requireNonNull(consumer,"consumer");
        this.delegate=Objects.requireNonNull(delegate,"delegate");
    }

    @Override
    public void onCombatOutcome(CombatOutcome fact) {
        Objects.requireNonNull(fact,"fact");
        owner.requireAdmitted(fact);
        // These fixture bindings apply to attacker PLAYER_KILL only;
        // do not infer a checkpoint for unmatched PLAYER_DEATH facts.
        if(fact.type()!=CombatOutcomeType.PLAYER_KILL ||
           fact.context()!=CombatOutcomeContext.PLAYER_PVP) {
            delegate.onCombatOutcome(fact);
            return;
        }
        audit.begin(fact,consumer);
        try {
            delegate.onCombatOutcome(fact);
        } catch (RuntimeException failure) {
            audit.uncertain(fact,consumer);
            throw failure;
        }
        audit.returned(fact,consumer);
    }
}
