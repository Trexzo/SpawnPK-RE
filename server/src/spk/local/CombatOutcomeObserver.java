package spk.local;

/**
 * Domain observer boundary for combat facts.
 *
 * This can later be bridged onto the DomainEventBus without changing
 * gameplay consumers.
 */
@FunctionalInterface
public interface CombatOutcomeObserver {

    void onCombatOutcome(CombatOutcome outcome);
}
