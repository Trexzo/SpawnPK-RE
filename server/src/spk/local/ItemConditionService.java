package spk.local;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Slot-independent semantic item condition state.
 *
 * This service models deterministic condition-unit consumption for definitions
 * explicitly supplied by authority. It does not mutate inventory/equipment.
 */
final class ItemConditionService {
    static final class Definition {
        private final ItemDeathPolicy.ItemId itemId;
        private final long maximumUnits;
        private final ItemDeathPolicy.ItemId depletedDestination;
        private final ItemPolicyAuthority authority;

        Definition(
            ItemDeathPolicy.ItemId itemId,
            long maximumUnits,
            ItemDeathPolicy.ItemId depletedDestination,
            ItemPolicyAuthority authority
        ) {
            this.itemId = Objects.requireNonNull(itemId, "itemId");
            if (maximumUnits <= 0L) {
                throw new IllegalArgumentException("maximumUnits must be positive");
            }
            this.maximumUnits = maximumUnits;
            this.depletedDestination = depletedDestination;
            this.authority = Objects.requireNonNull(authority, "authority");
        }

        ItemDeathPolicy.ItemId itemId() { return itemId; }
        long maximumUnits() { return maximumUnits; }
        ItemDeathPolicy.ItemId depletedDestination() { return depletedDestination; }
        ItemPolicyAuthority authority() { return authority; }
    }

    static final class ConditionKey {
        private final String value;

        ConditionKey(String value) {
            if (value == null) throw new NullPointerException("condition key");
            String normalized = value.trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException("condition key must not be empty");
            this.value = normalized;
        }

        String value() { return value; }

        @Override public boolean equals(Object other) {
            return other instanceof ConditionKey && value.equals(((ConditionKey) other).value);
        }

        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    static final class Snapshot {
        private final ConditionKey key;
        private final ItemDeathPolicy.ItemId itemId;
        private final long remainingUnits;
        private final long maximumUnits;
        private final boolean depleted;
        private final ItemDeathPolicy.ItemId depletedDestination;
        private final ItemPolicyAuthority authority;

        private Snapshot(
            ConditionKey key,
            ItemDeathPolicy.ItemId itemId,
            long remainingUnits,
            long maximumUnits,
            ItemDeathPolicy.ItemId depletedDestination,
            ItemPolicyAuthority authority
        ) {
            this.key = key;
            this.itemId = itemId;
            this.remainingUnits = remainingUnits;
            this.maximumUnits = maximumUnits;
            this.depleted = remainingUnits == 0L;
            this.depletedDestination = depletedDestination;
            this.authority = authority;
        }

        ConditionKey key() { return key; }
        ItemDeathPolicy.ItemId itemId() { return itemId; }
        long remainingUnits() { return remainingUnits; }
        long maximumUnits() { return maximumUnits; }
        boolean depleted() { return depleted; }
        ItemDeathPolicy.ItemId depletedDestination() { return depletedDestination; }
        ItemPolicyAuthority authority() { return authority; }
    }

    private static final class State {
        private final Definition definition;
        private long remainingUnits;

        private State(Definition definition) {
            this.definition = definition;
            this.remainingUnits = definition.maximumUnits();
        }
    }

    private final Map<ConditionKey, State> states = new LinkedHashMap<ConditionKey, State>();

    Snapshot create(ConditionKey key, Definition definition) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(definition, "definition");
        if (states.containsKey(key)) {
            throw new IllegalStateException("Condition key already exists: " + key);
        }
        State state = new State(definition);
        states.put(key, state);
        return snapshot(key);
    }

    Snapshot consume(ConditionKey key, long units) {
        if (units <= 0L) {
            throw new IllegalArgumentException("units must be positive");
        }
        State state = requireState(key);
        state.remainingUnits = Math.max(0L, state.remainingUnits - units);
        return snapshot(key);
    }

    Snapshot snapshot(ConditionKey key) {
        State state = requireState(key);
        Definition definition = state.definition;
        return new Snapshot(
            key,
            definition.itemId(),
            state.remainingUnits,
            definition.maximumUnits(),
            definition.depletedDestination(),
            definition.authority()
        );
    }

    private State requireState(ConditionKey key) {
        Objects.requireNonNull(key, "key");
        State state = states.get(key);
        if (state == null) {
            throw new IllegalArgumentException("Unknown condition key: " + key);
        }
        return state;
    }
}
