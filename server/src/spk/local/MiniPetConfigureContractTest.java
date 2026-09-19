package spk.local;
public final class MiniPetConfigureContractTest {
  public static void main(String[] args){
    if(MiniPetDefinitionRepository.count()!=21)throw new AssertionError("mini count "+MiniPetDefinitionRepository.count());
    for(MiniPetDefinitionRepository.Def d:MiniPetDefinitionRepository.all()){
      String a=ItemActionResolver.inventoryOption1Semantic(d.itemId);
      if(!"Configure".equalsIgnoreCase(a))throw new AssertionError(d.itemId+" option1="+a);
      String drop=ItemActionResolver.inventoryOption5Semantic(d.itemId);
      if(!"Drop".equalsIgnoreCase(drop))throw new AssertionError(d.itemId+" option5="+drop);
    }
    if(ItemActionResolver.inventoryAction(24019,4)!=null && "Switch-color".equalsIgnoreCase(ItemActionResolver.inventoryAction(24019,4)))
      throw new AssertionError("24019 must not gain fake Switch-color");
    System.out.println("V5127_MINIPET_CONFIGURE_CONTRACT_PASS count=21 option1=Configure option5=Drop item24019NoFakeSwitchColor=true");
  }
}
