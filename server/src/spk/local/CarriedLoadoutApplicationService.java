package spk.local;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Protocol-independent application boundary from one semantic PlayerLoadout to
 * canonical carried state.
 *
 * Policy remains caller-owned: semantic item keys and equipment slot keys are
 * resolved through explicit callbacks. Planning is mutation-free. Commit is an
 * exact-preimage compare-and-set over inventory + equipment under the player's
 * canonical mutation lock.
 */
final class CarriedLoadoutApplicationService {
    interface ItemResolver {
        ResolvedItem resolve(String itemKey);
    }

    interface SlotResolver {
        EquipmentSlot resolve(String slotKey);
    }

    static final class ResolvedItem {
        final int itemId;
        final boolean stackable;

        ResolvedItem(int itemId,boolean stackable){
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId="+itemId
                );

            this.itemId=itemId;
            this.stackable=stackable;
        }
    }

    static final class Plan {
        final PlayerLoadoutId loadoutId;
        final PlayerLoadoutVersion loadoutVersion;
        final String ownerRef;
        final String sourceAuthority;
        final String policyAuthority;

        final int[] expectedInventoryItems;
        final int[] expectedInventoryQuantities;
        final int[] expectedEquipmentItems;
        final int[] expectedEquipmentQuantities;

        final int[] nextInventoryItems;
        final int[] nextInventoryQuantities;
        final int[] nextEquipmentItems;
        final int[] nextEquipmentQuantities;

        private Plan(
            PlayerLoadout loadout,
            String policyAuthority,
            int[] expectedInventoryItems,
            int[] expectedInventoryQuantities,
            int[] expectedEquipmentItems,
            int[] expectedEquipmentQuantities,
            int[] nextInventoryItems,
            int[] nextInventoryQuantities,
            int[] nextEquipmentItems,
            int[] nextEquipmentQuantities
        ){
            this.loadoutId=loadout.id;
            this.loadoutVersion=loadout.version;
            this.ownerRef=loadout.ownerRef;
            this.sourceAuthority=loadout.sourceAuthority;
            this.policyAuthority=policyAuthority;

            this.expectedInventoryItems=
                expectedInventoryItems.clone();
            this.expectedInventoryQuantities=
                expectedInventoryQuantities.clone();
            this.expectedEquipmentItems=
                expectedEquipmentItems.clone();
            this.expectedEquipmentQuantities=
                expectedEquipmentQuantities.clone();

            this.nextInventoryItems=
                nextInventoryItems.clone();
            this.nextInventoryQuantities=
                nextInventoryQuantities.clone();
            this.nextEquipmentItems=
                nextEquipmentItems.clone();
            this.nextEquipmentQuantities=
                nextEquipmentQuantities.clone();
        }

        int inventoryOccupiedSlots(){
            return occupied(
                nextInventoryItems
            );
        }

        int equipmentOccupiedSlots(){
            return occupied(
                nextEquipmentItems
            );
        }
    }

    static final class CommitResult {
        final PlayerLoadoutId loadoutId;
        final PlayerLoadoutVersion loadoutVersion;
        final String ownerRef;
        final int inventoryOccupiedSlots;
        final int equipmentOccupiedSlots;
        final String policyAuthority;

        private CommitResult(Plan plan){
            this.loadoutId=plan.loadoutId;
            this.loadoutVersion=plan.loadoutVersion;
            this.ownerRef=plan.ownerRef;
            this.inventoryOccupiedSlots=
                plan.inventoryOccupiedSlots();
            this.equipmentOccupiedSlots=
                plan.equipmentOccupiedSlots();
            this.policyAuthority=
                plan.policyAuthority;
        }
    }

    private final WorldPlayer player;
    private final BankState bank;
    private final EquipmentState equipment;
    private final Object mutationLock;
    private final String policyAuthority;

    CarriedLoadoutApplicationService(
        WorldPlayer player,
        String policyAuthority
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.bank=player.bank();
        this.equipment=player.equipment();
        this.mutationLock=player.mutationLock();
        this.policyAuthority=
            requireGameplayAuthority(
                policyAuthority
            );
    }

    Plan plan(
        PlayerLoadout loadout,
        ItemResolver itemResolver,
        SlotResolver slotResolver
    ){
        PlayerLoadout checked=
            Objects.requireNonNull(
                loadout,
                "loadout"
            );
        ItemResolver items=
            Objects.requireNonNull(
                itemResolver,
                "itemResolver"
            );
        SlotResolver slots=
            Objects.requireNonNull(
                slotResolver,
                "slotResolver"
            );

        if(checked.hasSkillProfile()||
           checked.hasPetSelection())
            throw new IllegalArgumentException(
                "carried-state loadout application does not own skill/pet mutation"
            );

        int[] nextInventoryItems=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] nextInventoryQuantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        Arrays.fill(
            nextInventoryItems,
            -1
        );

        int inventorySlot=0;

        for(PlayerLoadout.InventoryEntry entry:
                checked.inventory){
            ResolvedItem item=
                requireResolvedItem(
                    items.resolve(
                        entry.itemKey
                    ),
                    entry.itemKey
                );

            if(item.stackable){
                if(entry.quantity>
                        Integer.MAX_VALUE)
                    throw new IllegalArgumentException(
                        "stackable loadout quantity overflow key="+
                        entry.itemKey+
                        " quantity="+
                        entry.quantity
                    );

                if(inventorySlot>=
                        BankState
                            .INVENTORY_CAPACITY)
                    throw new IllegalArgumentException(
                        "loadout inventory capacity exceeded"
                    );

                nextInventoryItems[
                    inventorySlot
                ]=item.itemId;
                nextInventoryQuantities[
                    inventorySlot
                ]=(int)entry.quantity;
                inventorySlot++;
                continue;
            }

            long remainingSlots=
                BankState.INVENTORY_CAPACITY-
                inventorySlot;

            if(entry.quantity>
                    remainingSlots)
                throw new IllegalArgumentException(
                    "loadout inventory capacity exceeded key="+
                    entry.itemKey+
                    " quantity="+
                    entry.quantity+
                    " remainingSlots="+
                    remainingSlots
                );

            for(long i=0L;
                i<entry.quantity;
                i++){
                nextInventoryItems[
                    inventorySlot
                ]=item.itemId;
                nextInventoryQuantities[
                    inventorySlot
                ]=1;
                inventorySlot++;
            }
        }

        int[] nextEquipmentItems=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        int[] nextEquipmentQuantities=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        Arrays.fill(
            nextEquipmentItems,
            -1
        );

        Set<EquipmentSlot> resolvedSlots=
            new HashSet<>();

        for(PlayerLoadout.EquipmentEntry entry:
                checked.equipment){
            EquipmentSlot slot=
                slots.resolve(
                    entry.slotKey
                );

            if(slot==null)
                throw new IllegalArgumentException(
                    "unresolved equipment slot key="+
                    entry.slotKey
                );

            if(!resolvedSlots.add(slot))
                throw new IllegalArgumentException(
                    "duplicate resolved equipment slot="+
                    slot
                );

            ResolvedItem item=
                requireResolvedItem(
                    items.resolve(
                        entry.itemKey
                    ),
                    entry.itemKey
                );

            if(entry.quantity>
                    Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                    "equipment loadout quantity overflow key="+
                    entry.itemKey+
                    " quantity="+
                    entry.quantity
                );

            nextEquipmentItems[
                slot.equipmentIndex
            ]=item.itemId;
            nextEquipmentQuantities[
                slot.equipmentIndex
            ]=(int)entry.quantity;
        }

        synchronized(mutationLock){
            int[] expectedInventoryItems=
                new int[
                    BankState
                        .INVENTORY_CAPACITY
                ];
            int[] expectedInventoryQuantities=
                new int[
                    BankState
                        .INVENTORY_CAPACITY
                ];
            int[] expectedEquipmentItems=
                equipment.containerItems();
            int[] expectedEquipmentQuantities=
                equipment.containerQuantities();

            for(int slot=0;
                slot<
                    BankState
                        .INVENTORY_CAPACITY;
                slot++){
                BankState.Stack stack=
                    bank.inventoryAt(
                        slot
                    );

                if(stack==null){
                    expectedInventoryItems[
                        slot
                    ]=-1;
                    expectedInventoryQuantities[
                        slot
                    ]=0;
                }else{
                    expectedInventoryItems[
                        slot
                    ]=stack.itemId;
                    expectedInventoryQuantities[
                        slot
                    ]=stack.qty;
                }
            }

            return new Plan(
                checked,
                policyAuthority,
                expectedInventoryItems,
                expectedInventoryQuantities,
                expectedEquipmentItems,
                expectedEquipmentQuantities,
                nextInventoryItems,
                nextInventoryQuantities,
                nextEquipmentItems,
                nextEquipmentQuantities
            );
        }
    }

    CommitResult commit(Plan plan){
        Plan checked=
            Objects.requireNonNull(
                plan,
                "plan"
            );

        if(!policyAuthority.equals(
                checked.policyAuthority))
            throw new IllegalArgumentException(
                "plan policy authority mismatch expected="+
                policyAuthority+
                " actual="+
                checked.policyAuthority
            );

        synchronized(mutationLock){
            requireCurrentPreimage(
                checked
            );

            try{
                bank.replaceInventorySemantic(
                    checked.nextInventoryItems,
                    checked.nextInventoryQuantities
                );
                equipment.restoreAccountState(
                    checked.nextEquipmentItems,
                    checked.nextEquipmentQuantities
                );

                requireCurrentPostimage(
                    checked
                );
            }catch(RuntimeException failure){
                rollback(
                    checked,
                    failure
                );
                throw failure;
            }catch(Error failure){
                rollback(
                    checked,
                    failure
                );
                throw failure;
            }

            return new CommitResult(
                checked
            );
        }
    }

    String policyAuthority(){
        return policyAuthority;
    }

    private void requireCurrentPreimage(
        Plan plan
    ){
        int[] currentInventoryItems=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] currentInventoryQuantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];

        for(int slot=0;
            slot<
                BankState.INVENTORY_CAPACITY;
            slot++){
            BankState.Stack stack=
                bank.inventoryAt(slot);

            currentInventoryItems[slot]=
                stack==null
                    ?-1
                    :stack.itemId;
            currentInventoryQuantities[
                slot
            ]=
                stack==null
                    ?0
                    :stack.qty;
        }

        if(!Arrays.equals(
                currentInventoryItems,
                plan.expectedInventoryItems)||
           !Arrays.equals(
                currentInventoryQuantities,
                plan.expectedInventoryQuantities)||
           !Arrays.equals(
                equipment.containerItems(),
                plan.expectedEquipmentItems)||
           !Arrays.equals(
                equipment.containerQuantities(),
                plan.expectedEquipmentQuantities))
            throw new IllegalStateException(
                "carried loadout preimage changed before commit"
            );
    }

    private void requireCurrentPostimage(
        Plan plan
    ){
        for(int slot=0;
            slot<
                BankState.INVENTORY_CAPACITY;
            slot++){
            BankState.Stack stack=
                bank.inventoryAt(slot);
            int item=
                stack==null
                    ?-1
                    :stack.itemId;
            int quantity=
                stack==null
                    ?0
                    :stack.qty;

            if(item!=
                    plan.nextInventoryItems[
                        slot
                    ]||
               quantity!=
                    plan.nextInventoryQuantities[
                        slot
                    ])
                throw new IllegalStateException(
                    "inventory loadout postimage mismatch slot="+
                    slot
                );
        }

        if(!Arrays.equals(
                equipment.containerItems(),
                plan.nextEquipmentItems)||
           !Arrays.equals(
                equipment.containerQuantities(),
                plan.nextEquipmentQuantities))
            throw new IllegalStateException(
                "equipment loadout postimage mismatch"
            );
    }

    private void rollback(
        Plan plan,
        Throwable primary
    ){
        try{
            bank.replaceInventorySemantic(
                plan.expectedInventoryItems,
                plan.expectedInventoryQuantities
            );
            equipment.restoreAccountState(
                plan.expectedEquipmentItems,
                plan.expectedEquipmentQuantities
            );
        }catch(Throwable rollbackFailure){
            primary.addSuppressed(
                rollbackFailure
            );
        }
    }

    private static ResolvedItem
        requireResolvedItem(
            ResolvedItem item,
            String itemKey
        ){
        if(item==null)
            throw new IllegalArgumentException(
                "unresolved item key="+
                itemKey
            );

        return item;
    }

    private static int occupied(
        int[] items
    ){
        int count=0;

        for(int item:items)
            if(item>=0)
                count++;

        return count;
    }

    private static String requireGameplayAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "policyAuthority"
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "policyAuthority blank"
            );

        if("EXACT_CURRENT_CLIENT".equals(
                clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(
                clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define carried loadout policy actual="+
                clean
            );

        return clean;
    }
}
