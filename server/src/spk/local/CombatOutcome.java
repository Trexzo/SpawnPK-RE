package spk.local;

import java.util.Objects;

/**
 * Immutable semantic fact emitted after combat resolution.
 *
 * This class intentionally contains no packet, client, reward or scoring
 * knowledge. Consumers decide independently whether the fact matters.
 */
public final class CombatOutcome {

    private final String attacker;
    private final String victim;
    private final CombatOutcomeType type;
    private final long worldTick;
    private final String context;

    public CombatOutcome(
        String attacker,
        String victim,
        CombatOutcomeType type,
        long worldTick,
        String context
    ) {
        this.attacker = Objects.requireNonNull(attacker, "attacker");
        this.victim = Objects.requireNonNull(victim, "victim");
        this.type = Objects.requireNonNull(type, "type");
        this.context = Objects.requireNonNull(context, "context");

        if (worldTick < 0) {
            throw new IllegalArgumentException("worldTick");
        }

        this.worldTick = worldTick;
    }

    public String attacker() {
        return attacker;
    }

    public String victim() {
        return victim;
    }

    public CombatOutcomeType type() {
        return type;
    }

    public long worldTick() {
        return worldTick;
    }

    public String context() {
        return context;
    }
}
