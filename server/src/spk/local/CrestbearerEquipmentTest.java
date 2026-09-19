package spk.local;

/** v5.2.2 regression for the live-failed Crestbearer equip set and melee comp cape. */
public final class CrestbearerEquipmentTest {
    public static void main(String[] args) throws Exception {
        assertSlot(23141,EquipmentSlot.HEAD,EquipmentMetadataRepository.Coverage.FULL_HELM);
        assertSlot(23142,EquipmentSlot.CHEST,EquipmentMetadataRepository.Coverage.FULL_BODY);
        assertSlot(23143,EquipmentSlot.LEGS,EquipmentMetadataRepository.Coverage.NONE);
        assertSlot(22105,EquipmentSlot.HEAD,EquipmentMetadataRepository.Coverage.NONE);
        assertSlot(22106,EquipmentSlot.CHEST,EquipmentMetadataRepository.Coverage.NONE);
        assertSlot(22107,EquipmentSlot.LEGS,EquipmentMetadataRepository.Coverage.NONE);
        assertSlot(28708,EquipmentSlot.HEAD,EquipmentMetadataRepository.Coverage.FULL_HELM);
        assertSlot(28707,EquipmentSlot.CHEST,EquipmentMetadataRepository.Coverage.FULL_BODY);
        assertSlot(28706,EquipmentSlot.LEGS,EquipmentMetadataRepository.Coverage.NONE);
        assertSlot(28872,EquipmentSlot.HEAD,EquipmentMetadataRepository.Coverage.NONE);
        assertSlot(28871,EquipmentSlot.CHEST,EquipmentMetadataRepository.Coverage.NONE);
        assertSlot(28870,EquipmentSlot.LEGS,EquipmentMetadataRepository.Coverage.NONE);
        assertSlot(19050,EquipmentSlot.CAPE,EquipmentMetadataRepository.Coverage.NONE);
        EquipmentMetadataRepository.Meta grand=EquipmentMetadataRepository.resolve(23063);
        if(grand==null||grand.slot!=EquipmentSlot.CAPE)throw new AssertionError("grand melee comp cape unresolved");

        int[] app=new int[12];java.util.Arrays.fill(app,-1);
        app[0]=23141;app[1]=23063;app[3]=28526;app[4]=23142;app[7]=23143;app[10]=28701;
        byte[] block=BootstrapPackets.appearanceBlock("opensrc",app);
        if(block.length<40)throw new AssertionError("appearance too short");
        System.out.println("V522_CRESTBEARER_EQUIPMENT_PASS melee=23141_HEAD+23142_CHEST_FULLBODY+23143_LEGS mage=22105_22106_22107 dyed=28708_28707_28706 hide=28872_28871_28870 grandComp23063=CAPE appearanceBuilt=true");
    }
    private static void assertSlot(int id,EquipmentSlot slot,EquipmentMetadataRepository.Coverage coverage){
        EquipmentMetadataRepository.Meta m=EquipmentMetadataRepository.resolve(id);
        if(m==null||m.slot!=slot||m.coverage!=coverage)throw new AssertionError(id+" -> "+(m==null?"null":m.slot+"/"+m.coverage));
    }
}
