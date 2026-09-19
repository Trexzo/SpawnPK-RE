package spk.local;

import java.util.Arrays;

/** v5.4 proof that production-observed weapon poses flow through item lineage/family data. */
public final class WeaponPoseRepositoryTest {
    public static void main(String[] args) {
        if(WeaponPoseRepository.directCount()!=80)
            throw new AssertionError("direct pose corpus="+WeaponPoseRepository.directCount());

        int[] elder={7518,823,7520,820,821,822,7519};
        EquipmentMetadataRepository.Meta base=EquipmentMetadataRepository.resolveKnownSlot(20485,EquipmentSlot.WEAPON);
        EquipmentMetadataRepository.Meta ethereal=EquipmentMetadataRepository.resolveKnownSlot(21005,EquipmentSlot.WEAPON);
        EquipmentMetadataRepository.Meta ornament=EquipmentMetadataRepository.resolveKnownSlot(28030,EquipmentSlot.WEAPON);
        assertPose("20485 direct Elder maul",base,elder);
        assertPose("21005 Ethereal elder maul clone",ethereal,elder);
        assertPose("28030 Elder maul (or) canonical",ornament,elder);

        EquipmentMetadataRepository.Meta whip=EquipmentMetadataRepository.resolveKnownSlot(4151,EquipmentSlot.WEAPON);
        assertPose("4151 Abyssal whip",whip,new int[]{808,823,1660,820,821,822,1661});

        WeaponPoseRepository.Resolution or=WeaponPoseRepository.resolve(28030);
        if(or==null || !or.evidence.contains("canonical_weapon_family".toUpperCase(java.util.Locale.ROOT))) {
            // evidence is intentionally upper-case V54_CANONICAL_WEAPON_FAMILY=...
            if(or==null || !or.evidence.contains("V54_CANONICAL_WEAPON_FAMILY=elder maul"))
                throw new AssertionError("28030 evidence="+(or==null?"null":or.evidence));
        }
        System.out.println("V54_WEAPON_POSE_REPOSITORY_PASS directObserved=80 elder20485=true ethereal21005Clone=true elderOr28030Family=true whip4151=true elderPose="+Arrays.toString(elder));
    }
    private static void assertPose(String label,EquipmentMetadataRepository.Meta m,int[] want){
        if(m==null) throw new AssertionError(label+" meta null");
        if(!Arrays.equals(m.pose.toArray(),want))
            throw new AssertionError(label+" got="+Arrays.toString(m.pose.toArray())+" want="+Arrays.toString(want)+" evidence="+m.evidence);
    }
}
