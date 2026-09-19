package spk.local;
import java.util.*;
public final class WeaponPoseSemanticFamilyTest {
 public static void main(String[] a){
  check(11791,EquipmentPoseProfile.STAFF_MAGIC_FAMILY,"Staff of the dead");
  check(12006,EquipmentPoseProfile.WHIP_FAMILY,"Abyssal tentacle");
  check(15039,EquipmentPoseProfile.MAUL_HEAVY_FAMILY,"Chaotic maul");
  check(23053,EquipmentPoseProfile.TWO_HANDED_SWORD_FAMILY,"Zaros godsword");
  System.out.println("V55_WEAPON_POSE_SEMANTIC_FAMILY_PASS staff11791 whip12006 maul15039 twoHanded23053 productionConsensusOnly=true");
 }
 private static void check(int id,EquipmentPoseProfile p,String n){EquipmentMetadataRepository.Meta m=EquipmentMetadataRepository.resolve(id);if(m==null||m.slot!=EquipmentSlot.WEAPON)throw new AssertionError(n+" unresolved");if(!Arrays.equals(m.pose.toArray(),p.toArray()))throw new AssertionError(n+" pose="+Arrays.toString(m.pose.toArray()));}
}
