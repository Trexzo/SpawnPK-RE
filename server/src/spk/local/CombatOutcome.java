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
    private final CombatOutcomeContext context;
    private final long worldTick;
    private final String sourceAuthority;

    public CombatOutcome(
        String attacker,
        String victim,
        CombatOutcomeType type,
        CombatOutcomeContext context,
        long worldTick,
        String sourceAuthority
    ) {
        this.attacker = requireRef(attacker, "attacker");
        this.victim = requireRef(victim, "victim");
        this.type = Objects.requireNonNull(type, "type");
        this.context = Objects.requireNonNull(context, "context");

        if (worldTick < 0) {
            throw new IllegalArgumentException("worldTick");
        }

        this.worldTick = worldTick;
        this.sourceAuthority = requireRef(sourceAuthority, "sourceAuthority");
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

    public CombatOutcomeContext context() {
        return context;
    }

    public long worldTick() {
        return worldTick;
    }

    public String sourceAuthority() {
        return sourceAuthority;
    }

    private static String requireRef(String value, String label) {
        Objects.requireNonNull(value, label);
        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label);
        }

        return normalized;
    }
}
