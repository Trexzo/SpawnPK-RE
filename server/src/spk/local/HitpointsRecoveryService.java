package spk.local;

import java.util.Objects;

/**
 * Lifecycle-safe healing over the canonical WorldPlayer / PlayerState HP
 * channel.
 *
 * Food, potion, regeneration, respawn, packet policy and maximum-HP
 * calculation remain external. Callers resolve the maximum before entering
 * this service so no arbitrary policy callback executes under player ownership.
 */
final class HitpointsRecoveryService {
    enum Status {
        HEALED,
        ALREADY_AT_OR_ABOVE_MAXIMUM,
        DEAD
    }

    static final class Result {
        final Status status;
        final int requested;
        final int applied;
        final int before;
        final int after;
        final int maximum;
        final String maximumAuthority;

        private Result(
            Status status,
            int requested,
            int applied,
            int before,
            int after,
            int maximum,
            String maximumAuthority
        ) {
            this.status =
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.requested = requested;
            this.applied = applied;
            this.before = before;
            this.after = after;
            this.maximum = maximum;
            this.maximumAuthority =
                maximumAuthority;
        }

        boolean healed() {
            return status ==
                Status.HEALED;
        }

        boolean maximumResolved() {
            return maximum > 0;
        }
    }

    private final WorldPlayer player;
    private final PlayerState state;
    private final PlayerLifecycleState lifecycle;

    HitpointsRecoveryService(
        WorldPlayer player
    ) {
        this.player =
            Objects.requireNonNull(
                player,
                "player"
            );
        this.state =
            player.playerState();
        this.lifecycle =
            player.lifecycle();
    }

    Result heal(
        int amount,
        int maximum,
        String maximumAuthority
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                "heal amount must be positive amount=" +
                amount
            );
        }

        String authority =
            requireGameplayAuthority(
                maximumAuthority
            );

        synchronized (
            player.mutationLock()
        ) {
            int before =
                state.currentLevel(
                    PlayerState.HITPOINTS
                );

            if (lifecycle.dead()) {
                return new Result(
                    Status.DEAD,
                    amount,
                    0,
                    before,
                    before,
                    0,
                    authority
                );
            }

            if (before <= 0) {
                throw new IllegalStateException(
                    "alive lifecycle with non-positive hitpoints hp=" +
                    before
                );
            }

            if (maximum < 1 ||
                maximum > 255) {
                throw new IllegalArgumentException(
                    "maximum hitpoints=" +
                    maximum +
                    " expected=1..255"
                );
            }

            if (before >= maximum) {
                return new Result(
                    Status.ALREADY_AT_OR_ABOVE_MAXIMUM,
                    amount,
                    0,
                    before,
                    before,
                    maximum,
                    authority
                );
            }

            long candidate =
                (long)before +
                (long)amount;

            int after =
                candidate >= maximum
                    ? maximum
                    : (int)candidate;

            int applied =
                after -
                before;

            boolean changed =
                state.setCurrentLevel(
                    PlayerState.HITPOINTS,
                    after
                );

            int observed =
                state.currentLevel(
                    PlayerState.HITPOINTS
                );

            if (!changed ||
                observed != after) {
                throw new IllegalStateException(
                    "hitpoints heal write mismatch expected=" +
                    after +
                    " actual=" +
                    observed
                );
            }

            if (lifecycle.dead()) {
                throw new IllegalStateException(
                    "healing changed while lifecycle became dead"
                );
            }

            return new Result(
                Status.HEALED,
                amount,
                applied,
                before,
                after,
                maximum,
                authority
            );
        }
    }

    private static String requireGameplayAuthority(
        String value
    ) {
        if (value == null) {
            throw new NullPointerException(
                "maximumAuthority"
            );
        }

        String clean =
            value.trim();

        if (clean.isEmpty()) {
            throw new IllegalArgumentException(
                "maximumAuthority blank"
            );
        }

        if ("EXACT_CURRENT_CLIENT".equals(
                clean) ||
            "UNKNOWN_SERVER_AUTHORITY".equals(
                clean)) {
            throw new IllegalArgumentException(
                "client/unknown authority cannot define maximum hitpoints actual=" +
                clean
            );
        }

        return clean;
    }
}
