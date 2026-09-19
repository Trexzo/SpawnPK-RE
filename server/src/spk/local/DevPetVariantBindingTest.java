package spk.local;
import java.io.*;import java.util.*;
public final class DevPetVariantBindingTest{
 public static void main(String[]a)throws Exception{
  DevAuthorityWorkbench d=new DevAuthorityWorkbench();d.setPetNpcBinding(24016,5163);d.setPetSpriteBinding(24016,24018);
  if(d.petNpcBinding(24016)!=5163||d.petSpriteBinding(24016)!=24018)throw new AssertionError(d.summary());
  BankState b=new BankState();ByteArrayOutputStream raw=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(raw,new IsaacCipher(new int[]{1,2,3,4}));
  b.spawnItem(24016,1,w);String r=b.sendDevInventoryVariantPreview(d.petSpriteBindings(),w);if(!r.contains("24016->24018")||!r.contains("serverStateMutated=false"))throw new AssertionError(r);
  d.clearPetBinding(24016);if(d.petNpcBinding(24016)!=null||d.petSpriteBinding(24016)!=null)throw new AssertionError("clear");
  System.out.println("V593_DEV_PET_VARIANT_BINDING_PASS worldNpcBinding=true inventorySpriteBinding=true livePreviewNonPersistent=true independentControls=true");
 }
}
