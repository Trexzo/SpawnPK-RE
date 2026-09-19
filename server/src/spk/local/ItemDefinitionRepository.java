package spk.local;

/**
 * v5 generic item-definition facade.
 *
 * Broad id/name coverage comes from the supplied current items.json. SpawnPK's
 * current i.bin enriches custom definitions with stackability/actions/clone links.
 * Item-specific mechanics are intentionally not embedded here.
 */
final class ItemDefinitionRepository {
    private ItemDefinitionRepository() {}

    static int count() { return ItemCatalog.count(); }
    static ItemCatalog.Meta get(int id) { return ItemCatalog.get(id); }
    static boolean exists(int id) { return ItemCatalog.exists(id); }
    static String name(int id) { return ItemCatalog.name(id); }
    static boolean isStackable(int id) { return ItemCatalog.isStackable(id); }
    static String stackabilityEvidence(int id) { return ItemCatalog.stackabilityEvidence(id); }
    /** UI action metadata is not sufficient to infer an equipment slot. */
    static boolean hasWieldAction(int id) { return ItemCatalog.inheritedHasAction(id, "Wield") || id == 4151; }
    static String equipActionEvidence(int id) {
        if (ItemCatalog.inheritedHasAction(id,"Wield") || id==4151) return "Wield";
        if (ItemCatalog.inheritedHasAction(id,"Wear")) return "Wear";
        if (ItemCatalog.inheritedHasAction(id,"Equip")) return "Equip";
        return "none";
    }
    static boolean isScytheFamily(int id) { return ItemCatalog.isScytheFamily(id); }
    static EquipmentMetadataRepository.Meta equipmentMeta(int id) { return EquipmentMetadataRepository.resolve(id); }
    static EquipmentMetadataRepository.Meta equipmentMetaForClientAction(int id) { return EquipmentMetadataRepository.resolveForClientEquipAction(id); }
}
