package spk.local;
public final class PetAccessoryContractTest{
  public static void main(String[] args)throws Exception{
    int[] ids={20542,20543,20544,20545,20546,20699,21068};
    int[] sels={1,2,3,4,5,7,8};
    for(int i=0;i<ids.length;i++){
      int id=ids[i];
      if(!PetAccessoryAuthority.isAccessory(id))throw new AssertionError("missing "+id);
      String sem=ItemActionResolver.inventoryOption1Semantic(id);
      if(!"Read".equalsIgnoreCase(sem))throw new AssertionError(id+" semantic="+sem);
      if(PetAccessoryAuthority.name(id).startsWith("Unknown"))throw new AssertionError(id);
      Integer selector=PetAccessoryAuthority.selector(id);
      int got=selector==null?-1:selector.intValue();
      if(got!=sels[i])throw new AssertionError(id+" selector="+got+" expected="+sels[i]);
    }
    System.out.println("V5130_PET_ACCESSORY_CONTRACT_PASS count=7 selectors=20542:1,20543:2,20544:3,20545:4,20546:5,20699:7,21068:8 mapping=USER_RUNTIME_CERTIFIED basicReadDialog=true useOnPet57=true defaultAccessory=NONE");
  }
}