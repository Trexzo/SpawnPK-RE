package spk.local;
public final class ScoobyBehemothPetTest{
 public static void main(String[]a){
  int[] want={5160,5161,5162,5163};
  for(int i=0;i<4;i++){int item=24016+i;PetDefinitionRepository.Def d=PetDefinitionRepository.get(item);if(d==null||d.npcId!=want[i])throw new AssertionError(item+" -> "+d);if(PetDefinitionRepository.isAmbiguous(item))throw new AssertionError("still ambiguous "+item);}
  System.out.println("V593_SCOOBY_BEHEMOTH_4WAY_PASS 24016=5160_blackwhite 24017=5161_blackorange 24018=5162_bluewhite 24019=5163_blackgreen_pendingFinalInventorySpriteConfirm devWorldAndSpriteOverrides=true");
 }
}
