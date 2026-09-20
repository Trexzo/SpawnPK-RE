package spk.local;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Semantic repair funding model.
 *
 * The visible preference order is represented, but prices/conversion rates and
 * actual value settlement remain external authority.
 */
final class RepairFunding {
    enum Resource {
        REPAIR_SCROLLS,
        BLOOD_SHARDS,
        COINS
    }

    interface EligibilityHook {
        boolean mayUse(
            ItemDeathPolicy.ItemId itemId,
            Resource resource,
            RepairResourceAccount account
        );
    }

    static final class RepairResourceAccount {
        private final Map<Resource, Long> balances;

        RepairResourceAccount(Map<Resource, Long> balances) {
            Objects.requireNonNull(balances, "balances");
            EnumMap<Resource, Long> copy = new EnumMap<Resource, Long>(Resource.class);
            for (Resource resource : Resource.values()) {
                Long value = balances.get(resource);
                long balance = value == null ? 0L : value.longValue();
                if (balance < 0L) {
                    throw new IllegalArgumentException("Negative repair balance: " + resource);
                }
                copy.put(resource, Long.valueOf(balance));
            }
            this.balances = Collections.unmodifiableMap(copy);
        }

        long balance(Resource resource) {
            return balances.get(Objects.requireNonNull(resource, "resource")).longValue();
        }

        Map<Resource, Long> balances() {
            return balances;
        }
    }

    static final class FundingPlan {
        private final ItemDeathPolicy.ItemId itemId;
        private final Resource resource;
        private final long availableBalance;

        private FundingPlan(
            ItemDeathPolicy.ItemId itemId,
            Resource resource,
            long availableBalance
        ) {
            this.itemId = itemId;
            this.resource = resource;
            this.availableBalance = availableBalance;
        }

        ItemDeathPolicy.ItemId itemId() { return itemId; }
        Resource resource() { return resource; }
        long availableBalance() { return availableBalance; }
    }

    private static final List<Resource> VISIBLE_PREFERENCE =
        Collections.unmodifiableList(java.util.Arrays.asList(
            Resource.REPAIR_SCROLLS,
            Resource.BLOOD_SHARDS,
            Resource.COINS
        ));

    private RepairFunding() {
    }

    static List<Resource> visiblePreference() {
        return VISIBLE_PREFERENCE;
    }

    static Optional<FundingPlan> chooseAvailable(
        ItemDeathPolicy.ItemId itemId,
        RepairResourceAccount account,
        EligibilityHook eligibility
    ) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(eligibility, "eligibility");

        for (Resource resource : VISIBLE_PREFERENCE) {
            long available = account.balance(resource);
            if (available > 0L && eligibility.mayUse(itemId, resource, account)) {
                return Optional.of(new FundingPlan(itemId, resource, available));
            }
        }
        return Optional.empty();
    }
}
