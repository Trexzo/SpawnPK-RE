package spk.local;
public final class EngineR5ItemAuthorityTest {
 public static void main(String[]args)throws Exception{
  if(ItemAuthorityRepository.count()!=30000)throw new AssertionError("item count="+ItemAuthorityRepository.count());
  ItemAuthorityRepository.Entry s=ItemAuthorityRepository.get(28860);if(s==null)throw new AssertionError("scorching missing");
  if(!"Scorching bow (i)".equals(ItemAuthorityRepository.stripTags(s.name)))throw new AssertionError("scorching name="+s.name);
  if(!s.wield||s.wear)throw new AssertionError("scorching equip flags");
  String all=(s.effectText+" "+s.mechanicsSummary).toLowerCase(java.util.Locale.ROOT);
  if(!all.contains("+1 attack speed"))throw new AssertionError("scorching static speed evidence missing: "+all);
  if(ItemAuthorityRepository.byExactName("Scorching bow (i)")!=s)throw new AssertionError("exact-name lookup");
  ItemAuthorityRepository.Entry oath=ItemAuthorityRepository.get(28883);if(oath==null||!oath.effectText.toLowerCase(java.util.Locale.ROOT).contains("max hit"))throw new AssertionError("oath effect");
  V913WeaponRuntimeAuthority.Profile rp=V913WeaponRuntimeAuthority.resolve(28860);if(rp==null||rp.attackAnimation!=15409||rp.actorGfx!=4080||rp.projectileId!=4079)throw new AssertionError("runtime authority join");
  if(ItemAuthorityRepository.equippableCount()<4000)throw new AssertionError("equippable universe too small="+ItemAuthorityRepository.equippableCount());
  System.out.println("V5150_ENGINE_R5_ITEM_AUTHORITY_PASS rows="+ItemAuthorityRepository.count()+" equippable="+ItemAuthorityRepository.equippableCount()+" effectRows="+ItemAuthorityRepository.withEffectText()+" mechanicsRows="+ItemAuthorityRepository.withStaticMechanics()+" scorchingStaticAndRuntimeJoin=true");
 }
}
