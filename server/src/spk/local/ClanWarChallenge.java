package spk.local;

import java.util.Objects;

/**
 * Fail-closed lifecycle for a semantic challenge between two clans.
 *
 * Admission, instance creation, scoring, combat enforcement and rewards are
 * intentionally outside this foundation.
 */
final class ClanWarChallenge {
    enum State {
        PROPOSED,
        ACCEPTED,
        DECLINED,
        CANCELLED
    }

    static final class ChallengeId {
        private final String value;

        ChallengeId(String value) {
            if (value == null) {
                throw new NullPointerException("challenge id");
            }
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("challenge id must not be empty");
            }
            this.value = normalized;
        }

        String value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ChallengeId && value.equals(((ChallengeId) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    static final class Snapshot {
        private final ChallengeId id;
        private final ClanAggregate.ClanId challenger;
        private final ClanAggregate.ClanId challenged;
        private final ClanWarDefinition definition;
        private final State state;

        private Snapshot(
            ChallengeId id,
            ClanAggregate.ClanId challenger,
            ClanAggregate.ClanId challenged,
            ClanWarDefinition definition,
            State state
        ) {
            this.id = id;
            this.challenger = challenger;
            this.challenged = challenged;
            this.definition = definition;
            this.state = state;
        }

        ChallengeId id() { return id; }
        ClanAggregate.ClanId challenger() { return challenger; }
        ClanAggregate.ClanId challenged() { return challenged; }
        ClanWarDefinition definition() { return definition; }
        State state() { return state; }
    }

    private final ChallengeId id;
    private final ClanAggregate.ClanId challenger;
    private final ClanAggregate.ClanId challenged;
    private final ClanWarDefinition definition;
    private State state = State.PROPOSED;

    ClanWarChallenge(
        ChallengeId id,
        ClanAggregate.ClanId challenger,
        ClanAggregate.ClanId challenged,
        ClanWarDefinition definition
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.challenger = Objects.requireNonNull(challenger, "challenger");
        this.challenged = Objects.requireNonNull(challenged, "challenged");
        this.definition = Objects.requireNonNull(definition, "definition");

        if (challenger.equals(challenged)) {
            throw new IllegalArgumentException("A clan cannot challenge itself");
        }
    }

    State accept() {
        return transition(State.ACCEPTED);
    }

    State decline() {
        return transition(State.DECLINED);
    }

    State cancel() {
        return transition(State.CANCELLED);
    }

    Snapshot snapshot() {
        return new Snapshot(id, challenger, challenged, definition, state);
    }

    private State transition(State target) {
        if (state == target) {
            return state;
        }
        if (state != State.PROPOSED) {
            throw new IllegalStateException("Challenge is already terminal: " + state);
        }
        state = target;
        return state;
    }
}
