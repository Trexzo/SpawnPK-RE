package spk.local;
public final class PetDefinitionRepositoryTest{
 public static void main(String[]args){
  VoidglassR3CustomContent.ensureRuntimePetMapping();
  if(PetDefinitionRepository.count()!=301)throw new AssertionError("mapped="+PetDefinitionRepository.count());
  if(PetDefinitionRepository.ambiguousCount()!=12)throw new AssertionError("ambiguous="+PetDefinitionRepository.ambiguousCount());
  check(20776,3098,3033,3034);check(22519,3843,7396,7395);check(23484,3845,7396,7395);check(28888,8330,7416,7411);check(28891,8334,7876,7877);check(25425,5159,7472,7473);check(24145,4311,6236,6236);check(21067,5875,1310,1311);check(21654,6347,8080,8081);check(22954,6958,7575,8336);check(22959,6969,7575,8329);check(24238,6991,8504,8505);check(27340,8124,8504,8505);check(27345,8129,5538,5539);check(24016,5160,7472,7473);check(24017,5161,7472,7473);check(24018,5162,7472,7473);check(24019,5163,7472,7473);
  check(29999,12000,-1,-1);if(PetDefinitionRepository.get(32760)!=null)throw new AssertionError("legacy invalid mapping retained");
  if(PetDefinitionRepository.isAmbiguous(25425)||PetDefinitionRepository.isAmbiguous(24238))throw new AssertionError("certified mappings regressed to ambiguous");
  System.out.println("V5185_PET_DEFINITION_REPOSITORY_PASS mapped=301 ambiguous=12 certifiedCoreRetained=true customVoidglass29999to12000=true legacy32760Retired=true productionAuthority=false");
 }
 private static void check(int item,int npc,int stand,int walk){PetDefinitionRepository.Def d=PetDefinitionRepository.get(item);if(d==null||d.npcId!=npc||d.standAnim!=stand||d.walkAnim!=walk)throw new AssertionError("item="+item+" def="+d);}
}
