package spk.local;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Protocol-independent Collection Log aggregate service.
 *
 * Progress is driven only by authoritative semantic observations. Reward
 * settlement and presentation mapping are intentionally outside this service.
 */
final class CollectionLogService {
    enum DiscoveryResult {
        FIRST_DISCOVERY,
        FIRST_DISCOVERY_COMPLETED,
        ALREADY_OBTAINED
    }

    static final class CollectionEntryObserved {
        private final CollectionLogDefinition.CollectionId collectionId;
        private final CollectionLogDefinition.EntryId entryId;

        CollectionEntryObserved(
            CollectionLogDefinition.CollectionId collectionId,
            CollectionLogDefinition.EntryId entryId
        ) {
            this.collectionId = Objects.requireNonNull(collectionId, "collectionId");
            this.entryId = Objects.requireNonNull(entryId, "entryId");
        }

        CollectionLogDefinition.CollectionId collectionId() { return collectionId; }
        CollectionLogDefinition.EntryId entryId() { return entryId; }
    }

    static final class ProgressSnapshot {
        private final CollectionLogDefinition.CollectionId collectionId;
        private final Set<CollectionLogDefinition.EntryId> obtainedEntries;
        private final int obtainedCount;
        private final int totalCount;
        private final boolean complete;
        private final Long killCount;

        private ProgressSnapshot(
            CollectionLogDefinition.CollectionId collectionId,
            Set<CollectionLogDefinition.EntryId> obtainedEntries,
            int obtainedCount,
            int totalCount,
            boolean complete,
            Long killCount
        ) {
            this.collectionId = collectionId;
            this.obtainedEntries = Collections.unmodifiableSet(
                new LinkedHashSet<CollectionLogDefinition.EntryId>(obtainedEntries)
            );
            this.obtainedCount = obtainedCount;
            this.totalCount = totalCount;
            this.complete = complete;
            this.killCount = killCount;
        }

        CollectionLogDefinition.CollectionId collectionId() { return collectionId; }
        Set<CollectionLogDefinition.EntryId> obtainedEntries() { return obtainedEntries; }
        int obtainedCount() { return obtainedCount; }
        int totalCount() { return totalCount; }
        boolean complete() { return complete; }
        OptionalLong killCount() {
            return killCount == null ? OptionalLong.empty() : OptionalLong.of(killCount.longValue());
        }
    }

    private static final class ProgressState {
        private final LinkedHashSet<CollectionLogDefinition.EntryId> obtained =
            new LinkedHashSet<CollectionLogDefinition.EntryId>();
        private Long killCount;
    }

    private final Map<CollectionLogDefinition.CollectionId, CollectionLogDefinition> definitions;
    private final Map<CollectionLogDefinition.CollectionId, ProgressState> progress =
        new LinkedHashMap<CollectionLogDefinition.CollectionId, ProgressState>();

    CollectionLogService(Collection<CollectionLogDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("At least one collection definition is required");
        }

        LinkedHashMap<CollectionLogDefinition.CollectionId, CollectionLogDefinition> copy =
            new LinkedHashMap<CollectionLogDefinition.CollectionId, CollectionLogDefinition>();

        for (CollectionLogDefinition definition : definitions) {
            CollectionLogDefinition nonNull = Objects.requireNonNull(definition, "definition");
            if (copy.put(nonNull.id(), nonNull) != null) {
                throw new IllegalArgumentException("Duplicate collection id: " + nonNull.id());
            }
        }

        this.definitions = Collections.unmodifiableMap(copy);
    }

    DiscoveryResult observe(CollectionEntryObserved event) {
        Objects.requireNonNull(event, "event");
        CollectionLogDefinition definition = requireDefinition(event.collectionId());
        if (!definition.requiredEntries().contains(event.entryId())) {
            throw new IllegalArgumentException(
                "Unknown entry " + event.entryId() + " for collection " + event.collectionId()
            );
        }

        ProgressState state = state(event.collectionId());
        if (!state.obtained.add(event.entryId())) {
            return DiscoveryResult.ALREADY_OBTAINED;
        }

        return state.obtained.size() == definition.requiredEntries().size()
            ? DiscoveryResult.FIRST_DISCOVERY_COMPLETED
            : DiscoveryResult.FIRST_DISCOVERY;
    }

    void observeKillCount(CollectionLogDefinition.CollectionId collectionId, long authoritativeCount) {
        CollectionLogDefinition definition = requireDefinition(collectionId);
        if (!definition.supportsKillCount()) {
            throw new IllegalStateException("Collection does not define a kill-count statistic");
        }
        if (authoritativeCount < 0L) {
            throw new IllegalArgumentException("Kill count must be non-negative");
        }
        state(collectionId).killCount = Long.valueOf(authoritativeCount);
    }

    ProgressSnapshot snapshot(CollectionLogDefinition.CollectionId collectionId) {
        CollectionLogDefinition definition = requireDefinition(collectionId);
        ProgressState state = progress.get(collectionId);
        Set<CollectionLogDefinition.EntryId> obtained =
            state == null
                ? Collections.<CollectionLogDefinition.EntryId>emptySet()
                : state.obtained;
        Long killCount = state == null ? null : state.killCount;

        int obtainedCount = obtained.size();
        int totalCount = definition.requiredEntries().size();
        return new ProgressSnapshot(
            definition.id(),
            obtained,
            obtainedCount,
            totalCount,
            obtainedCount == totalCount,
            killCount
        );
    }

    List<CollectionLogDefinition> definitions() {
        return Collections.unmodifiableList(new ArrayList<CollectionLogDefinition>(definitions.values()));
    }

    private ProgressState state(CollectionLogDefinition.CollectionId id) {
        ProgressState state = progress.get(id);
        if (state == null) {
            state = new ProgressState();
            progress.put(id, state);
        }
        return state;
    }

    private CollectionLogDefinition requireDefinition(CollectionLogDefinition.CollectionId id) {
        Objects.requireNonNull(id, "collectionId");
        CollectionLogDefinition definition = definitions.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown collection: " + id);
        }
        return definition;
    }
}
