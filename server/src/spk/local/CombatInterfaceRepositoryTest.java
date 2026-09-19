package spk.local;
public final class CombatInterfaceRepositoryTest {
 public static void main(String[] a){
  eq(776,CombatInterfaceRepository.forWeapon(28526),"Bloodrend scythe");
  eq(425,CombatInterfaceRepository.forWeapon(20485),"Elder maul");
  eq(12290,CombatInterfaceRepository.forWeapon(4151),"Abyssal whip");
  eq(328,CombatInterfaceRepository.forWeapon(2415),"Saradomin staff");
  eq(4705,CombatInterfaceRepository.forWeapon(11694),"Armadyl godsword");
  eq(5855,CombatInterfaceRepository.forWeapon(-1),"unarmed");
  System.out.println("V55_COMBAT_INTERFACE_REPOSITORY_PASS scythe=776 maul=425 whip=12290 staff=328 godsword=4705 unarmed=5855 tab=0");
 }
 private static void eq(int x,int y,String n){if(x!=y)throw new AssertionError(n+" got="+y+" expected="+x);}
}
