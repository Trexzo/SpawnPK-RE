package spk.local;

public final class EquipmentSlotOverrideDataTest {
    public static void main(String[] args) {
        check(542, EquipmentSlot.LEGS);
        check(27475, EquipmentSlot.AMMO);
        check(28802, EquipmentSlot.CAPE);
        check(28831, EquipmentSlot.SHIELD);
        System.out.println("R85_EQUIPMENT_SLOT_OVERRIDE_DATA_PASS fixtures=4 source=equipment_slot_overrides.tsv");
    }

    private static void check(int itemId, EquipmentSlot expected) {
        EquipmentMetadataRepository.Meta meta = EquipmentMetadataRepository.resolve(itemId);
        if (meta == null || meta.slot != expected)
            throw new AssertionError("item=" + itemId + " slot=" + (meta == null ? "null" : meta.slot));
        String evidence = EquipmentMetadataRepository.resolutionEvidence(itemId);
        if (!evidence.contains("CURRENT_ITEM_AMBIGUITY_OVERRIDE"))
            throw new AssertionError("item=" + itemId + " evidence=" + evidence);
    }
}
