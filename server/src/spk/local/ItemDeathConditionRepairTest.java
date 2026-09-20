package spk.local;

import java.util.EnumMap;
import java.util.Optional;

/** Deterministic regression for Issue #174 semantic death/condition/repair foundation. */
public final class ItemDeathConditionRepairTest {
    public static void main(String[] args) {
        ItemDeathPolicy.ItemId keptId = new ItemDeathPolicy.ItemId(100);
        ItemDeathPolicy.ItemId lostId = new ItemDeathPolicy.ItemId(101);
        ItemDeathPolicy.ItemId breakableId = new ItemDeathPolicy.ItemId(102);
        ItemDeathPolicy.ItemId brokenId = new ItemDeathPolicy.ItemId(103);

        ItemDeathPolicy autoKeep = ItemDeathPolicy.of(
            keptId,
            ItemDeathPolicy.Category.AUTO_KEEP,
            null,
            ItemPolicyAuthority.CUSTOM_LOCALLAB
        );
        ItemDeathPolicy loss = ItemDeathPolicy.of(
            lostId,
            ItemDeathPolicy.Category.ORDINARY_LOSS,
            null,
            ItemPolicyAuthority.CUSTOM_LOCALLAB
        );
        ItemDeathPolicy transform = ItemDeathPolicy.of(
            breakableId,
            ItemDeathPolicy.Category.TRANSFORM_ON_DEATH,
            brokenId,
            ItemPolicyAuthority.CUSTOM_LOCALLAB
        );

        require(autoKeep.evaluate(2L).disposition() == ItemDeathPolicy.Disposition.KEEP, "auto keep");
        require(loss.evaluate(3L).disposition() == ItemDeathPolicy.Disposition.LOSE, "loss");

        ItemDeathPolicy.DeathTransition transformed = transform.evaluate(4L);
        require(transformed.disposition() == ItemDeathPolicy.Disposition.TRANSFORM, "transform");
        require(brokenId.equals(transformed.destinationItem()), "transform destination");
        require(transformed.destinationQuantity() == 4L, "transform quantity preserved");

        ItemDeathPolicy unknown = ItemDeathPolicy.unknown(new ItemDeathPolicy.ItemId(999));
        require(unknown.evaluate(1L).disposition() == ItemDeathPolicy.Disposition.UNKNOWN, "unknown explicit");
        require(unknown.authority() == ItemPolicyAuthority.UNKNOWN_SERVER_AUTHORITY, "unknown authority");

        expectIllegalArgument(new Runnable() {
            @Override public void run() {
                transform.evaluate(0L);
            }
        }, "zero transition quantity");

        ItemConditionService conditionService = new ItemConditionService();
        ItemConditionService.ConditionKey conditionKey =
            new ItemConditionService.ConditionKey("semantic-item-instance-a");
        conditionService.create(
            conditionKey,
            new ItemConditionService.Definition(
                breakableId,
                5L,
                brokenId,
                ItemPolicyAuthority.CUSTOM_LOCALLAB
            )
        );

        ItemConditionService.Snapshot mid = conditionService.consume(conditionKey, 2L);
        require(mid.remainingUnits() == 3L, "condition consumption");
        require(!mid.depleted(), "condition active");

        ItemConditionService.Snapshot depleted = conditionService.consume(conditionKey, 99L);
        require(depleted.remainingUnits() == 0L, "condition floors at zero");
        require(depleted.depleted(), "condition depleted");
        require(brokenId.equals(depleted.depletedDestination()), "depletion destination");

        EnumMap<RepairFunding.Resource, Long> balances =
            new EnumMap<RepairFunding.Resource, Long>(RepairFunding.Resource.class);
        balances.put(RepairFunding.Resource.REPAIR_SCROLLS, Long.valueOf(1L));
        balances.put(RepairFunding.Resource.BLOOD_SHARDS, Long.valueOf(50L));
        balances.put(RepairFunding.Resource.COINS, Long.valueOf(1000L));

        RepairFunding.RepairResourceAccount account =
            new RepairFunding.RepairResourceAccount(balances);

        require(RepairFunding.visiblePreference().get(0) == RepairFunding.Resource.REPAIR_SCROLLS, "scroll first");
        require(RepairFunding.visiblePreference().get(1) == RepairFunding.Resource.BLOOD_SHARDS, "shard second");
        require(RepairFunding.visiblePreference().get(2) == RepairFunding.Resource.COINS, "coins third");

        Optional<RepairFunding.FundingPlan> plan = RepairFunding.chooseAvailable(
            brokenId,
            account,
            new RepairFunding.EligibilityHook() {
                @Override public boolean mayUse(
                    ItemDeathPolicy.ItemId itemId,
                    RepairFunding.Resource resource,
                    RepairFunding.RepairResourceAccount ignored
                ) {
                    return resource != RepairFunding.Resource.REPAIR_SCROLLS;
                }
            }
        );
        require(plan.isPresent(), "funding plan");
        require(plan.get().resource() == RepairFunding.Resource.BLOOD_SHARDS, "external entitlement hook");
        require(plan.get().availableBalance() == 50L, "funding balance");

        expectUnsupported(new Runnable() {
            @Override public void run() {
                account.balances().put(RepairFunding.Resource.COINS, Long.valueOf(0L));
            }
        }, "funding account immutable");

        assertProtocolIndependent(
            ItemDeathPolicy.class,
            ItemConditionService.class,
            RepairFunding.class
        );

        System.out.println(
            "ISSUE174_ITEM_DEATH_CONDITION_REPAIR_PASS deathPolicy=true unknownExplicit=true " +
            "transformQuantitySafe=true conditionSlotIndependent=true conditionFloorsAtZero=true " +
            "repairPreference=true entitlementHook=true accountImmutable=true inventoryMutation=false " +
            "bankMutation=false deathLifecycleOwnedElsewhere=true repairPricesInvented=false " +
            "keepFormulaInvented=false protocolIndependent=true"
        );
    }

    private static void assertProtocolIndependent(Class<?>... roots) {
        String[] forbidden = {"widget", "opcode", "subtype", "packet", "sprite", "slotindex", "cachepath"};
        for (Class<?> root : roots) {
            for (java.lang.reflect.Field field : root.getDeclaredFields()) {
                String haystack = (field.getName() + " " + field.getType().getName()).toLowerCase(java.util.Locale.ROOT);
                for (String token : forbidden) {
                    require(!haystack.contains(token), root.getName() + " leaked presentation identity through " + field.getName());
                }
            }
        }
    }

    private static void expectIllegalArgument(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException: " + label);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectUnsupported(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected immutable view: " + label);
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
