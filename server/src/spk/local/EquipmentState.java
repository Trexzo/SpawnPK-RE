package spk.local;

import java.util.Arrays;

/**
 * Generic server-owned equipment state.
 *
 * This is intentionally the 14-slot classic equipment container, not the
 * 12-position packet-81 appearance array. The equipment container carries both
 * item ids and quantities: ordinary worn equipment has quantity 1, while the
 * AMMO slot may contain a real stack of arrows/bolts/etc.
 */
final class EquipmentState {
    static final int EQUIPMENT_SLOTS = 14;
    static final int APPEARANCE_SLOTS = 12;
    static final int WEAPON_SLOT = EquipmentSlot.WEAPON.equipmentIndex;
    static final int BLOODREND_ID = 28526;
    static final int EQUIPMENT_WIDGET = 1688;

    private final int[] items = new int[EQUIPMENT_SLOTS];
    private final int[] quantities = new int[EQUIPMENT_SLOTS];

    EquipmentState() {
        Arrays.fill(items, -1);
        EquipmentMetadataRepository.Meta bloodrend = EquipmentMetadataRepository.resolve(BLOODREND_ID);
        if (bloodrend == null) throw new IllegalStateException("Bloodrend equipment metadata unresolved");
        items[bloodrend.slot.equipmentIndex] = BLOODREND_ID;
        quantities[bloodrend.slot.equipmentIndex] = 1;
    }

    int itemAt(int equipmentIndex) {
        if (equipmentIndex < 0 || equipmentIndex >= items.length) return -1;
        return items[equipmentIndex];
    }

    int quantityAt(int equipmentIndex) {
        if (equipmentIndex < 0 || equipmentIndex >= quantities.length) return 0;
        return items[equipmentIndex] < 0 ? 0 : quantities[equipmentIndex];
    }

    int itemAt(EquipmentSlot slot) { return slot == null ? -1 : itemAt(slot.equipmentIndex); }
    int quantityAt(EquipmentSlot slot) { return slot == null ? 0 : quantityAt(slot.equipmentIndex); }
    int weapon() { return itemAt(EquipmentSlot.WEAPON); }
    boolean hasEquipped(int itemId) {
        if(itemId<0)return false;
        for(int id:items) if(id==itemId) return true;
        return false;
    }

    int set(EquipmentSlot slot, int itemId) {
        return setStack(slot,itemId,itemId>=0?1:0);
    }

    int setStack(EquipmentSlot slot,int itemId,int quantity) {
        if (slot == null) throw new IllegalArgumentException("slot");
        if(itemId<0){ itemId=-1; quantity=0; }
        if(itemId>=0 && quantity<=0) throw new IllegalArgumentException("quantity="+quantity);
        int previous = items[slot.equipmentIndex];
        items[slot.equipmentIndex] = itemId;
        quantities[slot.equipmentIndex] = quantity;
        return previous;
    }

    int set(int equipmentIndex, int itemId) {
        EquipmentSlot slot = EquipmentSlot.fromEquipmentIndex(equipmentIndex);
        if (slot == null) throw new IllegalArgumentException("unsupported equipmentIndex=" + equipmentIndex);
        return set(slot, itemId);
    }

    int equip(EquipmentMetadataRepository.Meta meta, int itemId) {
        if (meta == null) throw new IllegalArgumentException("meta");
        if (meta.itemId != itemId) throw new IllegalArgumentException("meta item mismatch");
        return set(meta.slot, itemId);
    }

    int unequip(EquipmentSlot slot) { return set(slot, -1); }
    int setWeapon(int itemId) { return set(EquipmentSlot.WEAPON, itemId); }
    int[] containerItems() { return items.clone(); }
    int[] containerQuantities() { return quantities.clone(); }

    int occupiedSlots() { int n=0; for (int item : items) if (item >= 0) n++; return n; }

    void restoreAccountState(
        int[] nextItems,
        int[] nextQuantities
    ){
        if(nextItems==null||
           nextItems.length!=EQUIPMENT_SLOTS)
            throw new IllegalArgumentException(
                "equipment snapshot length"
            );
        if(nextQuantities==null||
           nextQuantities.length!=EQUIPMENT_SLOTS)
            throw new IllegalArgumentException(
                "equipment quantity snapshot length"
            );

        System.arraycopy(
            nextItems,
            0,
            items,
            0,
            items.length
        );
        System.arraycopy(
            nextQuantities,
            0,
            quantities,
            0,
            quantities.length
        );
    }

    int[] appearanceItems() {
        int[] out = new int[APPEARANCE_SLOTS];
        Arrays.fill(out, -1);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.appearanceIndex >= 0) out[slot.appearanceIndex] = items[slot.equipmentIndex];
        }
        return out;
    }
}
