package spk.local;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

/**
 * Applies one validated semantic PlayerLoadout revision to canonical player
 * inventory/equipment state.
 *
 * Packet publication, automatic respawn policy, skill mutation and pet mutation
 * remain outside this service.
 */
final class PlayerLoadoutApplyService {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_LOADOUT_APPLY_V1";

    static final class Result {
        final PlayerLoadoutId loadoutId;
        final PlayerLoadoutVersion version;
        final int inventoryOccupiedSlots;
        final int equipmentOccupiedSlots;
        final boolean firstAcknowledgement;
        final String resolverAuthority;
        final String applyAuthority;

        private Result(
            PlayerLoadout loadout,
            int inventoryOccupiedSlots,
            int equipmentOccupiedSlots,
            boolean firstAcknowledgement,
            String resolverAuthority
        ){
            this.loadoutId=loadout.id;
            this.version=loadout.version;
            this.inventoryOccupiedSlots=
                inventoryOccupiedSlots;
            this.equipmentOccupiedSlots=
                equipmentOccupiedSlots;
            this.firstAcknowledgement=
                firstAcknowledgement;
            this.resolverAuthority=
                resolverAuthority;
            this.applyAuthority=AUTHORITY;
        }
    }

    private static final class ProjectedState {
        final int[] inventoryItems=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        final int[] inventoryQuantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        final int[] equipmentItems=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        final int[] equipmentQuantities=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        int inventoryOccupiedSlots;
        int equipmentOccupiedSlots;

        ProjectedState(){
            Arrays.fill(
                inventoryItems,
                -1
            );
            Arrays.fill(
                equipmentItems,
                -1
            );
        }
    }

    private final WorldPlayer player;
    private final LoadoutService loadouts;
    private final PlayerLoadoutSemanticResolver resolver;

    PlayerLoadoutApplyService(
        WorldPlayer player,
        LoadoutService loadouts,
        PlayerLoadoutSemanticResolver resolver
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.loadouts=
            Objects.requireNonNull(
                loadouts,
                "loadouts"
            );
        this.resolver=
            Objects.requireNonNull(
                resolver,
                "resolver"
            );

        requireGameplayAuthority(
            resolver.authority()
        );
    }

    Result apply(
        LoadoutService.LoadoutApplyPlan plan
    ){
        LoadoutService.LoadoutApplyPlan checked=
            Objects.requireNonNull(
                plan,
                "plan"
            );

        if(!checked.validation.valid)
            throw new IllegalArgumentException(
                "cannot apply invalid loadout plan"
            );

        ProjectedState projected=
            project(
                checked.loadout
            );

        synchronized(loadouts){
            LoadoutService.Snapshot current=
                loadouts.get(
                    checked.loadout.ownerRef,
                    checked.loadout.id
                );

            if(current==null||
               current.loadout!=
                    checked.loadout||
               !current.loadout.version.equals(
                    checked.loadout.version))
                throw new IllegalStateException(
                    "stale loadout apply plan id="+
                    checked.loadout.id+
                    " version="+
                    checked.loadout.version
                );

            synchronized(player.mutationLock()){
                player.bank()
                    .replaceInventorySemantic(
                        projected.inventoryItems,
                        projected.inventoryQuantities
                    );
                player.equipment()
                    .restoreAccountState(
                        projected.equipmentItems,
                        projected.equipmentQuantities
                    );

                boolean first=
                    loadouts
                        .acknowledgeApplied(
                            checked
                        );

                return new Result(
                    checked.loadout,
                    projected.inventoryOccupiedSlots,
                    projected.equipmentOccupiedSlots,
                    first,
                    resolver.authority()
                );
            }
        }
    }

    String resolverAuthority(){
        return resolver.authority();
    }

    private ProjectedState project(
        PlayerLoadout loadout
    ){
        PlayerLoadout checked=
            Objects.requireNonNull(
                loadout,
                "loadout"
            );

        ProjectedState out=
            new ProjectedState();
        HashSet<Integer> inventoryItemIds=
            new HashSet<>();
        int inventorySlot=0;

        for(PlayerLoadout.InventoryEntry entry:
                checked.inventory){
            PlayerLoadoutSemanticResolver.Item item=
                Objects.requireNonNull(
                    resolver.resolveItem(
                        entry.itemKey
                    ),
                    "resolved inventory item"
                );

            if(!inventoryItemIds.add(
                    item.itemId))
                throw new IllegalArgumentException(
                    "multiple loadout keys resolve to inventory item="+
                    item.itemId
                );

            if(entry.quantity>
                    Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                    "inventory quantity overflow item="+
                    item.itemId+
                    " quantity="+
                    entry.quantity
                );

            if(item.stackable){
                if(inventorySlot>=
                        BankState.INVENTORY_CAPACITY)
                    throw new IllegalArgumentException(
                        "loadout inventory capacity exceeded"
                    );

                out.inventoryItems[
                    inventorySlot
                ]=item.itemId;
                out.inventoryQuantities[
                    inventorySlot
                ]=(int)entry.quantity;
                inventorySlot++;
                continue;
            }

            if(entry.quantity>
                    BankState.INVENTORY_CAPACITY-
                    inventorySlot)
                throw new IllegalArgumentException(
                    "loadout inventory capacity exceeded item="+
                    item.itemId+
                    " quantity="+
                    entry.quantity
                );

            for(long i=0L;
                i<entry.quantity;
                i++){
                out.inventoryItems[
                    inventorySlot
                ]=item.itemId;
                out.inventoryQuantities[
                    inventorySlot
                ]=1;
                inventorySlot++;
            }
        }

        out.inventoryOccupiedSlots=
            inventorySlot;

        HashSet<EquipmentSlot> equipmentSlots=
            new HashSet<>();

        for(PlayerLoadout.EquipmentEntry entry:
                checked.equipment){
            EquipmentSlot slot=
                Objects.requireNonNull(
                    resolver.resolveEquipmentSlot(
                        entry.slotKey
                    ),
                    "resolved equipment slot"
                );
            PlayerLoadoutSemanticResolver.Item item=
                Objects.requireNonNull(
                    resolver.resolveItem(
                        entry.itemKey
                    ),
                    "resolved equipment item"
                );

            if(!equipmentSlots.add(
                    slot))
                throw new IllegalArgumentException(
                    "multiple loadout keys resolve to equipment slot="+
                    slot
                );

            if(entry.quantity>
                    Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                    "equipment quantity overflow slot="+
                    slot+
                    " quantity="+
                    entry.quantity
                );

            if(!item.stackable&&
               entry.quantity!=1L)
                throw new IllegalArgumentException(
                    "non-stackable equipment quantity slot="+
                    slot+
                    " item="+
                    item.itemId+
                    " quantity="+
                    entry.quantity
                );

            EquipmentMetadataRepository.Meta meta=
                EquipmentMetadataRepository.resolve(
                    item.itemId
                );

            if(meta==null||
               meta.slot!=slot)
                throw new IllegalArgumentException(
                    "loadout equipment slot mismatch item="+
                    item.itemId+
                    " expected="+
                    slot+
                    " resolved="+
                    (meta==null
                        ?"UNRESOLVED"
                        :meta.slot)
                );

            out.equipmentItems[
                slot.equipmentIndex
            ]=item.itemId;
            out.equipmentQuantities[
                slot.equipmentIndex
            ]=(int)entry.quantity;
            out.equipmentOccupiedSlots++;
        }

        return out;
    }

    private static String requireGameplayAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "resolverAuthority"
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "resolverAuthority blank"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define loadout resolution actual="+
                clean
            );

        return clean;
    }
}
