package spk.local;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Read-only semantic drop-table query boundary.
 *
 * A source NPC must be explicitly declared recovered/known. Unknown sources
 * return Optional.empty() rather than an inferred or fabricated empty table.
 */
final class DropTableCatalogQueryService {
    static final class DropTableEntry {
        private final int sourceNpcId;
        private final int itemId;
        private final Long minQuantity;
        private final Long maxQuantity;
        private final String rarityOrWeightEvidence;
        private final CatalogEvidenceAuthority authority;

        DropTableEntry(
            int sourceNpcId,
            int itemId,
            Long minQuantity,
            Long maxQuantity,
            String rarityOrWeightEvidence,
            CatalogEvidenceAuthority authority
        ) {
            if (sourceNpcId < 0) throw new IllegalArgumentException("sourceNpcId must be non-negative");
            if (itemId < 0) throw new IllegalArgumentException("itemId must be non-negative");
            if ((minQuantity == null) != (maxQuantity == null)) {
                throw new IllegalArgumentException("min/max quantity must both be present or absent");
            }
            if (minQuantity != null) {
                if (minQuantity.longValue() <= 0L || maxQuantity.longValue() <= 0L) {
                    throw new IllegalArgumentException("quantities must be positive");
                }
                if (minQuantity.longValue() > maxQuantity.longValue()) {
                    throw new IllegalArgumentException("minQuantity must not exceed maxQuantity");
                }
            }
            this.sourceNpcId = sourceNpcId;
            this.itemId = itemId;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
            this.rarityOrWeightEvidence = normalizeOptional(rarityOrWeightEvidence);
            this.authority = Objects.requireNonNull(authority, "authority");
        }

        int sourceNpcId() { return sourceNpcId; }
        int itemId() { return itemId; }
        OptionalLong minQuantity() {
            return minQuantity == null ? OptionalLong.empty() : OptionalLong.of(minQuantity.longValue());
        }
        OptionalLong maxQuantity() {
            return maxQuantity == null ? OptionalLong.empty() : OptionalLong.of(maxQuantity.longValue());
        }
        Optional<String> rarityOrWeightEvidence() {
            return Optional.ofNullable(rarityOrWeightEvidence);
        }
        CatalogEvidenceAuthority authority() { return authority; }
    }

    private final Set<Integer> knownSourceNpcIds;
    private final List<DropTableEntry> entries;

    DropTableCatalogQueryService(
        Collection<Integer> knownSourceNpcIds,
        Collection<DropTableEntry> entries
    ) {
        Objects.requireNonNull(knownSourceNpcIds, "knownSourceNpcIds");
        Objects.requireNonNull(entries, "entries");

        LinkedHashSet<Integer> known = new LinkedHashSet<Integer>();
        for (Integer sourceId : knownSourceNpcIds) {
            if (sourceId == null || sourceId.intValue() < 0) {
                throw new IllegalArgumentException("known source NPC ids must be non-negative");
            }
            known.add(sourceId);
        }

        ArrayList<DropTableEntry> copy = new ArrayList<DropTableEntry>();
        for (DropTableEntry entry : entries) {
            DropTableEntry nonNull = Objects.requireNonNull(entry, "entry");
            if (!known.contains(Integer.valueOf(nonNull.sourceNpcId()))) {
                throw new IllegalArgumentException(
                    "Drop entry source was not explicitly declared recovered: " + nonNull.sourceNpcId()
                );
            }
            copy.add(nonNull);
        }

        copy.sort(
            Comparator.comparingInt(DropTableEntry::sourceNpcId)
                .thenComparingInt(DropTableEntry::itemId)
        );
        this.knownSourceNpcIds = Collections.unmodifiableSet(known);
        this.entries = Collections.unmodifiableList(copy);
    }

    Optional<QueryPage<DropTableEntry>> queryByNpc(int sourceNpcId, int offset, int limit) {
        if (!knownSourceNpcIds.contains(Integer.valueOf(sourceNpcId))) {
            return Optional.empty();
        }
        ArrayList<DropTableEntry> matches = new ArrayList<DropTableEntry>();
        for (DropTableEntry entry : entries) {
            if (entry.sourceNpcId() == sourceNpcId) {
                matches.add(entry);
            }
        }
        return Optional.of(QueryPage.slice(matches, offset, limit));
    }

    QueryPage<DropTableEntry> queryByItem(int itemId, int offset, int limit) {
        if (itemId < 0) throw new IllegalArgumentException("itemId must be non-negative");
        ArrayList<DropTableEntry> matches = new ArrayList<DropTableEntry>();
        for (DropTableEntry entry : entries) {
            if (entry.itemId() == itemId) {
                matches.add(entry);
            }
        }
        return QueryPage.slice(matches, offset, limit);
    }

    boolean hasRecoveredSource(int sourceNpcId) {
        return knownSourceNpcIds.contains(Integer.valueOf(sourceNpcId));
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
