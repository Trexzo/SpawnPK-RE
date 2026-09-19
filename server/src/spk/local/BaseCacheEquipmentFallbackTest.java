package spk.local;

/** Proves actual opcode-41 equip clicks can classify base-cache items whose actions are not mirrored in items.tsv. */
public final class BaseCacheEquipmentFallbackTest {
    public static void main(String[] args) {
        expect(4081, EquipmentSlot.AMULET); // Salve amulet; base-cache action absent in LocalLab TSV
        expect(6737, EquipmentSlot.RING);   // Berserker ring
        expect(1127, EquipmentSlot.CHEST); // Rune platebody
        expect(1079, EquipmentSlot.LEGS);  // Rune platelegs
        expect(1163, EquipmentSlot.HEAD);  // Rune full helm
        expect(1201, EquipmentSlot.SHIELD);// Rune kiteshield
        expect(4131, EquipmentSlot.FEET);  // Rune boots
        expect(7462, EquipmentSlot.HANDS); // Barrows gloves
        expect(6570, EquipmentSlot.CAPE);  // Fire cape
        expect(892, EquipmentSlot.AMMO);   // Rune arrow
        expect(4718, EquipmentSlot.WEAPON);// Dharok greataxe
        expect(4726, EquipmentSlot.WEAPON);// Guthan warspear
        expect(4747, EquipmentSlot.WEAPON);// Torag hammers
        expect(4732, EquipmentSlot.HEAD);  // Karil coif
        expect(4734, EquipmentSlot.WEAPON);// Karil crossbow
        expect(4736, EquipmentSlot.CHEST); // Karil leathertop
        expect(4738, EquipmentSlot.LEGS);  // Karil leatherskirt
        System.out.println("V523_BASE_CACHE_OPCODE41_SLOT_FALLBACK_PASS");
    }
    private static void expect(int id, EquipmentSlot slot) {
        EquipmentMetadataRepository.Meta m=EquipmentMetadataRepository.resolveForClientEquipAction(id);
        require(m!=null,"unresolved id="+id+" name="+ItemDefinitionRepository.name(id));
        require(m.slot==slot,"id="+id+" got="+m.slot+" expected="+slot+" evidence="+m.evidence);
    }
    private static void require(boolean ok,String msg){ if(!ok) throw new AssertionError(msg); }
}
