package spk.local;

import java.util.Objects;

/**
 * Immutable semantic per-item death policy.
 *
 * Player death timing, keep-count selection and skull/prayer precedence remain
 * owned by the death lifecycle/caller. This type only evaluates an already
 * selected item's explicit policy definition.
 */
final class ItemDeathPolicy {
    enum Category {
        UNKNOWN,
        AUTO_KEEP,
        ORDINARY_KEEP,
        ORDINARY_LOSS,
        TRANSFORM_ON_DEATH
    }

    enum Disposition {
        UNKNOWN,
        KEEP,
        LOSE,
        TRANSFORM
    }

    static final class ItemId {
        private final int value;

        ItemId(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("item id must be non-negative");
            }
            this.value = value;
        }

        int value() { return value; }

        @Override public boolean equals(Object other) {
            return other instanceof ItemId && value == ((ItemId) other).value;
        }

        @Override public int hashCode() { return Integer.valueOf(value).hashCode(); }
        @Override public String toString() { return Integer.toString(value); }
    }

    static final class DeathTransition {
        private final ItemId sourceItem;
        private final long sourceQuantity;
        private final Disposition disposition;
        private final ItemId destinationItem;
        private final long destinationQuantity;
        private final ItemPolicyAuthority authority;

        private DeathTransition(
            ItemId sourceItem,
            long sourceQuantity,
            Disposition disposition,
            ItemId destinationItem,
            long destinationQuantity,
            ItemPolicyAuthority authority
        ) {
            this.sourceItem = sourceItem;
            this.sourceQuantity = sourceQuantity;
            this.disposition = disposition;
            this.destinationItem = destinationItem;
            this.destinationQuantity = destinationQuantity;
            this.authority = authority;
        }

        ItemId sourceItem() { return sourceItem; }
        long sourceQuantity() { return sourceQuantity; }
        Disposition disposition() { return disposition; }
        ItemId destinationItem() { return destinationItem; }
        long destinationQuantity() { return destinationQuantity; }
        ItemPolicyAuthority authority() { return authority; }
    }

    private final ItemId itemId;
    private final Category category;
    private final ItemId transformDestination;
    private final ItemPolicyAuthority authority;

    private ItemDeathPolicy(
        ItemId itemId,
        Category category,
        ItemId transformDestination,
        ItemPolicyAuthority authority
    ) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.category = Objects.requireNonNull(category, "category");
        this.authority = Objects.requireNonNull(authority, "authority");

        if (category == Category.TRANSFORM_ON_DEATH) {
            this.transformDestination = Objects.requireNonNull(transformDestination, "transformDestination");
        } else {
            if (transformDestination != null) {
                throw new IllegalArgumentException("Transform destination is only valid for TRANSFORM_ON_DEATH");
            }
            this.transformDestination = null;
        }
    }

    static ItemDeathPolicy unknown(ItemId itemId) {
        return new ItemDeathPolicy(
            itemId,
            Category.UNKNOWN,
            null,
            ItemPolicyAuthority.UNKNOWN_SERVER_AUTHORITY
        );
    }

    static ItemDeathPolicy of(
        ItemId itemId,
        Category category,
        ItemId transformDestination,
        ItemPolicyAuthority authority
    ) {
        return new ItemDeathPolicy(itemId, category, transformDestination, authority);
    }

    ItemId itemId() { return itemId; }
    Category category() { return category; }
    ItemId transformDestination() { return transformDestination; }
    ItemPolicyAuthority authority() { return authority; }

    DeathTransition evaluate(long quantity) {
        if (quantity <= 0L) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        switch (category) {
            case AUTO_KEEP:
            case ORDINARY_KEEP:
                return new DeathTransition(itemId, quantity, Disposition.KEEP, null, 0L, authority);
            case ORDINARY_LOSS:
                return new DeathTransition(itemId, quantity, Disposition.LOSE, null, 0L, authority);
            case TRANSFORM_ON_DEATH:
                return new DeathTransition(
                    itemId,
                    quantity,
                    Disposition.TRANSFORM,
                    transformDestination,
                    quantity,
                    authority
                );
            case UNKNOWN:
            default:
                return new DeathTransition(itemId, quantity, Disposition.UNKNOWN, null, 0L, authority);
        }
    }
}
