package spk.local;

/**
 * Server-side semantic equipment slots for the classic 317 equipment container.
 *
 * equipmentIndex is the slot used by widget 1688 / packet 53. appearanceIndex is
 * the corresponding item position in the 12-entry player appearance block, or -1
 * for equipment that is not rendered through that array (ring/ammo).
 */
enum EquipmentSlot {
    HEAD(0, 0),
    CAPE(1, 1),
    AMULET(2, 2),
    WEAPON(3, 3),
    CHEST(4, 4),
    SHIELD(5, 5),
    LEGS(7, 7),
    HANDS(9, 9),
    FEET(10, 10),
    RING(12, -1),
    AMMO(13, -1);

    final int equipmentIndex;
    final int appearanceIndex;

    EquipmentSlot(int equipmentIndex, int appearanceIndex) {
        this.equipmentIndex = equipmentIndex;
        this.appearanceIndex = appearanceIndex;
    }

    static EquipmentSlot fromEquipmentIndex(int index) {
        for (EquipmentSlot slot : values()) if (slot.equipmentIndex == index) return slot;
        return null;
    }
}
