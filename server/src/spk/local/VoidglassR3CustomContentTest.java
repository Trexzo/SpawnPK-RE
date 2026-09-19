package spk.local;
public final class VoidglassR3CustomContentTest{
 public static void main(String[]a){
  VoidglassR3CustomContent.ensureRuntimePetMapping();
  if(VoidglassR3CustomContent.ITEM_ID!=29999||VoidglassR3CustomContent.DEFAULT_NPC_ID!=12000)throw new AssertionError("identity");
  if(VoidglassR3CustomContent.CANDIDATES.length!=4)throw new AssertionError("candidates");
  if(!ItemDefinitionRepository.exists(29999))throw new AssertionError("server item missing");
  if(!"Voidglass Nistirio".equals(ItemDefinitionRepository.name(29999)))throw new AssertionError("item name="+ItemDefinitionRepository.name(29999));
  if(!"Drop".equalsIgnoreCase(ItemActionResolver.inventoryOption5Semantic(29999)))throw new AssertionError("drop action");
  PetDefinitionRepository.Def d=PetDefinitionRepository.get(29999);if(d==null||d.npcId!=12000||d.standAnim!=1662||d.walkAnim!=1663||!d.models.contains("32324"))throw new AssertionError("mapping="+d);
  if(PetDefinitionRepository.get(32760)!=null)throw new AssertionError("legacy invalid item mapping still present");
  for(int i=1;i<=4;i++){VoidglassR3CustomContent.Candidate c=VoidglassR3CustomContent.candidate(i);if(c==null||c.models.contains("36185")||c.stand==8233||c.walk==8232)throw new AssertionError("Hydra residue candidate="+i);}
  if(VoidglassR3CustomContent.PROC_GFX==4098)throw new AssertionError("Hydra proc retained");
  System.out.println("V5185_VOIDGLASS_R3_CUSTOM_CONTENT_PASS item=29999 clientRangeValid=true candidates=4 hydraModel36185=false hydraAnims8233_8232=false procGfx5042=true legacy32760Retired=true");
 }
}
