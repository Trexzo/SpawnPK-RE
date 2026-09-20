package spk.local;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable semantic Collection Log definition. */
final class CollectionLogDefinition {
    static final class CollectionId {
        private final String value;

        CollectionId(String value) {
            this.value = requireId(value, "collection id");
        }

        String value() { return value; }

        @Override public boolean equals(Object other) {
            return other instanceof CollectionId && value.equals(((CollectionId) other).value);
        }

        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    static final class CategoryId {
        private final String value;

        CategoryId(String value) {
            this.value = requireId(value, "category id");
        }

        String value() { return value; }

        @Override public boolean equals(Object other) {
            return other instanceof CategoryId && value.equals(((CategoryId) other).value);
        }

        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    static final class EntryId {
        private final String value;

        EntryId(String value) {
            this.value = requireId(value, "entry id");
        }

        String value() { return value; }

        @Override public boolean equals(Object other) {
            return other instanceof EntryId && value.equals(((EntryId) other).value);
        }

        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    private final CollectionId id;
    private final CategoryId categoryId;
    private final String name;
    private final Set<EntryId> requiredEntries;
    private final boolean supportsKillCount;
    private final CollectionLogEvidenceAuthority authority;

    CollectionLogDefinition(
        CollectionId id,
        CategoryId categoryId,
        String name,
        Set<EntryId> requiredEntries,
        boolean supportsKillCount,
        CollectionLogEvidenceAuthority authority
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.name = requireName(name);
        Objects.requireNonNull(requiredEntries, "requiredEntries");
        if (requiredEntries.isEmpty()) {
            throw new IllegalArgumentException("Collection must contain at least one required entry");
        }

        LinkedHashSet<EntryId> copy = new LinkedHashSet<EntryId>();
        for (EntryId entry : requiredEntries) {
            if (!copy.add(Objects.requireNonNull(entry, "entry"))) {
                throw new IllegalArgumentException("Duplicate collection entry: " + entry);
            }
        }

        this.requiredEntries = Collections.unmodifiableSet(copy);
        this.supportsKillCount = supportsKillCount;
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    CollectionId id() { return id; }
    CategoryId categoryId() { return categoryId; }
    String name() { return name; }
    Set<EntryId> requiredEntries() { return requiredEntries; }
    boolean supportsKillCount() { return supportsKillCount; }
    CollectionLogEvidenceAuthority authority() { return authority; }

    private static String requireId(String value, String label) {
        if (value == null) throw new NullPointerException(label);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return normalized;
    }

    private static String requireName(String value) {
        if (value == null) throw new NullPointerException("name");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("name must not be empty");
        return normalized;
    }
}
