package spk.local;
public final class EngineR81DescriptionInheritanceTest{
 public static void main(String[]a){
  String b=ItemAuthorityRepository.get(25425).effectText,w=ItemAuthorityRepository.get(24238).effectText;
  for(int id:new int[]{24016,24017,24018,24019}){ItemAuthorityRepository.Entry e=ItemAuthorityRepository.get(id);req(e.effectText.equals(b),"behemoth "+id);req(e.effectProvenance.contains("25425"),"behemoth provenance "+id);}
  for(int id:new int[]{27340,27341,27342}){ItemAuthorityRepository.Entry e=ItemAuthorityRepository.get(id);req(e.effectText.equals(w),"wolper "+id);req(e.effectProvenance.contains("24238"),"wolper provenance "+id);}
  req(ItemAuthorityRepository.get(27343).effectText.trim().isEmpty(),"ethereal must not inherit evil wolper");
  req(ItemAuthorityRepository.withEffectText()==1729,"item effect count="+ItemAuthorityRepository.withEffectText());req(PetResearchAuthorityRepository.withEffectText()==235,"pet desc count="+PetResearchAuthorityRepository.withEffectText());
  System.out.println("V5181_ENGINE_R81_DESCRIPTION_INHERITANCE_PASS behemothVariants=4 source25425=true resvanoEvilWolperVariants=3 source24238=true ethereal27343Excluded=true itemEffects=1729 petDescriptions=235");
 }
 static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}
