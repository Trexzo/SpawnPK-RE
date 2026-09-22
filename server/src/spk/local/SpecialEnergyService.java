package spk.local;

import java.util.Objects;

/**
 * Protocol-independent spend/restore semantics over PlayerState's canonical
 * 0..100 special-energy resource.
 *
 * Weapon costs, special effects, activation, regeneration and packet
 * publication remain caller/combat/presentation concerns.
 */
final class SpecialEnergyService {
    static final int MAX_ENERGY = 100;

    enum SpendStatus {
        SPENT,
        INSUFFICIENT_ENERGY
    }

    static final class Snapshot {
        final int energy;
        final int maximum;
        final String policyAuthority;

        private Snapshot(
            int energy,
            String policyAuthority
        ) {
            this.energy = energy;
            this.maximum = MAX_ENERGY;
            this.policyAuthority = policyAuthority;
        }
    }

    static final class SpendResult {
        final SpendStatus status;
        final int requested;
        final int applied;
        final int before;
        final int after;
        final String policyAuthority;

        private SpendResult(
            SpendStatus status,
            int requested,
            int applied,
            int before,
            int after,
            String policyAuthority
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
            this.policyAuthority = policyAuthority;
        }

        boolean spent() {
            return status ==
                SpendStatus.SPENT;
        }
    }

    static final class RestoreResult {
        final int requested;
        final int applied;
        final int before;
        final int after;
        final String policyAuthority;

        private RestoreResult(
            int requested,
            int applied,
            int before,
            int after,
            String policyAuthority
        ) {
            this.requested = requested;
            this.applied = applied;
            this.before = before;
            this.after = after;
            this.policyAuthority = policyAuthority;
        }
    }

    private final PlayerState player;
    private final Object mutationLock;
    private final String policyAuthority;

    SpecialEnergyService(
        WorldPlayer owner,
        String policyAuthority
    ) {
        WorldPlayer playerOwner =
            Objects.requireNonNull(
                owner,
                "owner"
            );

        this.player =
            playerOwner.playerState();
        this.mutationLock =
            playerOwner.mutationLock();
        this.policyAuthority =
            requireGameplayAuthority(
                policyAuthority
            );
    }

    Snapshot snapshot() {
        synchronized (mutationLock) {
            return new Snapshot(
                currentEnergy(),
                policyAuthority
            );
        }
    }

    SpendResult trySpend(
        int cost
    ) {
        if (cost <= 0 ||
            cost > MAX_ENERGY) {
            throw new IllegalArgumentException(
                "special-energy cost=" +
                cost +
                " expected=1.." +
                MAX_ENERGY
            );
        }

        synchronized (mutationLock) {
            int before =
                currentEnergy();

            if (before < cost) {
                return new SpendResult(
                    SpendStatus.INSUFFICIENT_ENERGY,
                    cost,
                    0,
                    before,
                    before,
                    policyAuthority
                );
            }

            int after =
                before - cost;

            player.setSpecialEnergy(
                after
            );

            int observed =
                currentEnergy();

            if (observed != after) {
                throw new IllegalStateException(
                    "special-energy spend write mismatch expected=" +
                    after +
                    " actual=" +
                    observed
                );
            }

            return new SpendResult(
                SpendStatus.SPENT,
                cost,
                cost,
                before,
                after,
                policyAuthority
            );
        }
    }

    RestoreResult restore(
        int amount
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                "special-energy restore must be positive amount=" +
                amount
            );
        }

        synchronized (mutationLock) {
            int before =
                currentEnergy();

            long candidate =
                (long)before +
                (long)amount;

            int after =
                candidate >= MAX_ENERGY
                    ? MAX_ENERGY
                    : (int)candidate;

            int applied =
                after - before;

            player.setSpecialEnergy(
                after
            );

            int observed =
                currentEnergy();

            if (observed != after) {
                throw new IllegalStateException(
                    "special-energy restore write mismatch expected=" +
                    after +
                    " actual=" +
                    observed
                );
            }

            return new RestoreResult(
                amount,
                applied,
                before,
                after,
                policyAuthority
            );
        }
    }

    String policyAuthority() {
        return policyAuthority;
    }

    private int currentEnergy() {
        int energy =
            player.specialEnergy();

        if (energy < 0 ||
            energy > MAX_ENERGY) {
            throw new IllegalStateException(
                "PlayerState special energy outside 0.." +
                MAX_ENERGY +
                " actual=" +
                energy
            );
        }

        return energy;
    }

    private static String requireGameplayAuthority(
        String value
    ) {
        if (value == null) {
            throw new NullPointerException(
                "policyAuthority"
            );
        }

        String clean =
            value.trim();

        if (clean.isEmpty()) {
            throw new IllegalArgumentException(
                "policyAuthority blank"
            );
        }

        if ("EXACT_CURRENT_CLIENT".equals(
                clean) ||
            "UNKNOWN_SERVER_AUTHORITY".equals(
                clean)) {
            throw new IllegalArgumentException(
                "client/unknown authority cannot define special-energy gameplay policy actual=" +
                clean
            );
        }

        return clean;
    }
}
