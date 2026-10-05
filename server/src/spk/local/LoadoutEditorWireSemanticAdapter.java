package spk.local;

import java.util.*;

/**
 * Converts one complete exact-v308 loadout-editor save pair into the semantic
 * inventory/equipment sections used by PlayerLoadout.
 *
 * This adapter deliberately does not choose a semantic loadout id/version.
 * The exact client save pair carries no such target identity, so target
 * selection remains a separate fail-closed runtime boundary.
 */
final class LoadoutEditorWireSemanticAdapter {
    static final String TRANSPORT_AUTHORITY=
        "EXACT_CURRENT_CLIENT";
    static final String POLICY_AUTHORITY=
        "CUSTOM_LOCALLAB_PLAYABILITY";

    static final class Snapshot {
        final List<PlayerLoadout.InventoryEntry> inventory;
        final List<PlayerLoadout.EquipmentEntry> equipment;
        final String transportAuthority;
        final String policyAuthority;

        Snapshot(
            List<PlayerLoadout.InventoryEntry> inventory,
            List<PlayerLoadout.EquipmentEntry> equipment
        ){
            this.inventory=
                Collections.unmodifiableList(
                    new ArrayList<>(inventory)
                );
            this.equipment=
                Collections.unmodifiableList(
                    new ArrayList<>(equipment)
                );
            this.transportAuthority=
                TRANSPORT_AUTHORITY;
            this.policyAuthority=
                POLICY_AUTHORITY;
        }
    }

    static Snapshot adapt(
        LoadoutEditorSaveClientRequest request
    ){
        Objects.requireNonNull(
            request,
            "request"
        );

        if(request.inventory().size()!=
                LoadoutEditorCompatibilityPairer
                    .INVENTORY_ENTRY_COUNT)
            throw new IllegalArgumentException(
                "inventory entry count="+
                request.inventory().size()
            );

        if(request.equipment().size()!=
                LoadoutEditorCompatibilityPairer
                    .EQUIPMENT_ENTRY_COUNT)
            throw new IllegalArgumentException(
                "equipment entry count="+
                request.equipment().size()
            );

        LinkedHashMap<Integer,Long> inventoryByItem=
            new LinkedHashMap<>();

        for(int slot=0;
            slot<request.inventory().size();
            slot++){
            LoadoutEditorSaveClientRequest.WireEntry entry=
                request.inventory().get(slot);

            if(isEmpty(entry)){
                requireCanonicalEmpty(
                    entry,
                    "inventory",
                    slot
                );
                continue;
            }

            requireOccupied(
                entry,
                "inventory",
                slot
            );
            requireCatalogItem(
                entry.itemId,
                "inventory",
                slot
            );

            long previous=
                inventoryByItem
                    .getOrDefault(
                        entry.itemId,
                        0L
                    );
            long combined=
                Math.addExact(
                    previous,
                    (long)entry.amount
                );

            if(combined>
                    Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                    "inventory aggregate exceeds int item="+
                    entry.itemId+
                    " qty="+
                    combined
                );

            inventoryByItem.put(
                entry.itemId,
                combined
            );
        }

        ArrayList<PlayerLoadout.InventoryEntry>
            semanticInventory=
                new ArrayList<>();

        for(Map.Entry<Integer,Long> value:
                inventoryByItem.entrySet())
            semanticInventory.add(
                new PlayerLoadout.InventoryEntry(
                    PlayabilityLoadoutMaterializer
                        .itemKey(
                            value.getKey()
                        ),
                    value.getValue()
                )
            );

        ArrayList<PlayerLoadout.EquipmentEntry>
            semanticEquipment=
                new ArrayList<>();

        for(int index=0;
            index<request.equipment().size();
            index++){
            LoadoutEditorSaveClientRequest.WireEntry entry=
                request.equipment().get(index);
            EquipmentSlot slot=
                EquipmentSlot
                    .fromEquipmentIndex(
                        index
                    );

            if(slot==null){
                if(entry.itemId!=0||
                   entry.amount!=0)
                    throw new IllegalArgumentException(
                        "equipment placeholder index="+
                        index+
                        " must be 0,0 actual="+
                        entry
                    );
                continue;
            }

            if(isEmpty(entry)){
                requireCanonicalEmpty(
                    entry,
                    "equipment",
                    index
                );
                continue;
            }

            requireOccupied(
                entry,
                "equipment",
                index
            );
            requireCatalogItem(
                entry.itemId,
                "equipment",
                index
            );

            EquipmentMetadataRepository.Meta meta=
                EquipmentMetadataRepository
                    .resolve(
                        entry.itemId
                    );

            if(meta==null)
                throw new IllegalArgumentException(
                    "equipment metadata unresolved item="+
                    entry.itemId+
                    " slot="+
                    slot
                );

            if(meta.slot!=slot)
                throw new IllegalArgumentException(
                    "equipment slot mismatch item="+
                    entry.itemId+
                    " outputIndex="+
                    index+
                    " mapped="+
                    slot+
                    " resolved="+
                    meta.slot
                );

            semanticEquipment.add(
                new PlayerLoadout.EquipmentEntry(
                    PlayabilityLoadoutMaterializer
                        .slotKey(slot),
                    PlayabilityLoadoutMaterializer
                        .itemKey(
                            entry.itemId
                        ),
                    entry.amount
                )
            );
        }

        return new Snapshot(
            semanticInventory,
            semanticEquipment
        );
    }

    private static boolean isEmpty(
        LoadoutEditorSaveClientRequest.WireEntry entry
    ){
        return entry.itemId<0||
            entry.amount<=0;
    }

    private static void requireCanonicalEmpty(
        LoadoutEditorSaveClientRequest.WireEntry entry,
        String section,
        int index
    ){
        if(entry.itemId!=-1||
           entry.amount!=0)
            throw new IllegalArgumentException(
                section+
                " malformed empty index="+
                index+
                " actual="+
                entry+
                " expected=-1,0"
            );
    }

    private static void requireOccupied(
        LoadoutEditorSaveClientRequest.WireEntry entry,
        String section,
        int index
    ){
        if(entry.itemId<0||
           entry.amount<=0)
            throw new IllegalArgumentException(
                section+
                " malformed occupied index="+
                index+
                " actual="+
                entry
            );
    }

    private static void requireCatalogItem(
        int itemId,
        String section,
        int index
    ){
        if(!ItemCatalog.exists(itemId))
            throw new IllegalArgumentException(
                section+
                " unknown current item index="+
                index+
                " item="+
                itemId
            );
    }

    private LoadoutEditorWireSemanticAdapter(){}
}
