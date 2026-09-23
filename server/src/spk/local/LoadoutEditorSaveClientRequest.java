package spk.local;

import java.util.*;

/**
 * One complete exact-v308 in-game loadout-editor save transport snapshot.
 *
 * This remains transport/application data: item ids and compatibility equipment
 * ordinals are not semantic gameplay item/slot keys.
 */
final class LoadoutEditorSaveClientRequest
    implements ClientRequest {

    static final class WireEntry {
        final int itemId;
        final int amount;

        WireEntry(int itemId,int amount){
            this.itemId=itemId;
            this.amount=amount;
        }

        @Override public String toString(){
            return itemId+","+amount;
        }
    }

    private final List<WireEntry> inventory;
    private final List<WireEntry> equipment;
    private final long inventoryPacketSequence;
    private final long equipmentPacketSequence;
    private final ClientRequestMetadata metadata;

    LoadoutEditorSaveClientRequest(
        List<WireEntry> inventory,
        List<WireEntry> equipment,
        long inventoryPacketSequence,
        long equipmentPacketSequence
    ){
        this.inventory=immutable(
            inventory,
            LoadoutEditorCompatibilityPairer.INVENTORY_ENTRY_COUNT,
            "inventory"
        );
        this.equipment=immutable(
            equipment,
            LoadoutEditorCompatibilityPairer.EQUIPMENT_ENTRY_COUNT,
            "equipment"
        );

        if(inventoryPacketSequence<1L||
           equipmentPacketSequence!=inventoryPacketSequence+1L)
            throw new IllegalArgumentException(
                "loadout save packet sequences must be consecutive inventory="+
                inventoryPacketSequence+
                " equipment="+equipmentPacketSequence
            );

        this.inventoryPacketSequence=inventoryPacketSequence;
        this.equipmentPacketSequence=equipmentPacketSequence;
        this.metadata=
            ClientRequestMetadata.exactCurrent(
                103,
                "PAIRED_VAR_BYTE_CLD1_INVENTORY28_CLD2_EQUIPMENT14",
                "V308_CLIENT_LOADOUT_EDITOR_SAVE_PAIR"
            );
    }

    List<WireEntry> inventory(){
        return inventory;
    }

    List<WireEntry> equipment(){
        return equipment;
    }

    long inventoryPacketSequence(){
        return inventoryPacketSequence;
    }

    long equipmentPacketSequence(){
        return equipmentPacketSequence;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    private static List<WireEntry> immutable(
        List<WireEntry> values,
        int expected,
        String field
    ){
        Objects.requireNonNull(values,field);
        if(values.size()!=expected)
            throw new IllegalArgumentException(
                field+" entries="+values.size()+
                " expected="+expected
            );

        ArrayList<WireEntry> copy=
            new ArrayList<>(expected);
        for(WireEntry value:values)
            copy.add(
                Objects.requireNonNull(
                    value,
                    field+" entry"
                )
            );
        return Collections.unmodifiableList(copy);
    }

    @Override public String toString(){
        return "LoadoutEditorSaveClientRequest{inventory="+
            inventory.size()+
            ",equipment="+
            equipment.size()+
            ",seq="+inventoryPacketSequence+
            "->"+equipmentPacketSequence+
            ",metadata="+metadata+
            "}";
    }
}
