package spk.local;

/** Resolve packet-81 pose data independently from item rendering/equipment state. */
final class EquipmentPoseRepository {
    private EquipmentPoseRepository() {}

    static EquipmentPoseProfile forAppearance(int[] equippedItems) {
        if (equippedItems == null || equippedItems.length <= EquipmentMetadataRepository.WEAPON_APPEARANCE_SLOT)
            return EquipmentPoseProfile.DEFAULT_HUMAN;
        int weapon = equippedItems[EquipmentMetadataRepository.WEAPON_APPEARANCE_SLOT];
        if (weapon < 0) return EquipmentPoseProfile.DEFAULT_HUMAN;
        EquipmentMetadataRepository.Meta meta = EquipmentMetadataRepository.resolveKnownSlot(weapon, EquipmentSlot.WEAPON);
        return meta == null ? EquipmentPoseProfile.DEFAULT_HUMAN : meta.pose;
    }
}
