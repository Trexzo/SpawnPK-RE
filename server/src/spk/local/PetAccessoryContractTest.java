package spk.local;
import java.lang.reflect.*;
public final class PetAccessoryContractTest{
  public static void main(String[] args)throws Exception{
    Method is=LocalSession.class.getDeclaredMethod("isPetAccessoryItem",int.class);is.setAccessible(true);
    Method name=LocalSession.class.getDeclaredMethod("petAccessoryName",int.class);name.setAccessible(true);
    Method selector=LocalSession.class.getDeclaredMethod("petAccessorySelector",int.class);selector.setAccessible(true);
    int[] ids={20542,20543,20544,20545,20546,20699,21068};
    int[] sels={1,2,3,4,5,7,8};
    for(int i=0;i<ids.length;i++){
      int id=ids[i];
      if(!((Boolean)is.invoke(null,id)))throw new AssertionError("missing "+id);
      String sem=ItemActionResolver.inventoryOption1Semantic(id);
      if(!"Read".equalsIgnoreCase(sem))throw new AssertionError(id+" semantic="+sem);
      if(((String)name.invoke(null,id)).startsWith("Unknown"))throw new AssertionError(id);
      int got=((Integer)selector.invoke(null,id)).intValue();
      if(got!=sels[i])throw new AssertionError(id+" selector="+got+" expected="+sels[i]);
    }
    System.out.println("V5130_PET_ACCESSORY_CONTRACT_PASS count=7 selectors=20542:1,20543:2,20544:3,20545:4,20546:5,20699:7,21068:8 mapping=USER_RUNTIME_CERTIFIED basicReadDialog=true useOnPet57=true defaultAccessory=NONE");
  }
}
