package spk.local;

import java.io.*;

/** Inventory sprite/gallery publication is visual-only and leaves server stacks untouched. */
public final class DevInventorySpritePreviewTest {
 public static void main(String[] args)throws Exception{
  BankState b=new BankState();
  int slot=b.addInventoryOne(995,new ServerPacketWriter(new ByteArrayOutputStream(),new IsaacCipher(new int[]{1,2,3,4})));
  BankState.Stack before=b.inventoryAt(slot); if(before==null||before.itemId!=995)throw new AssertionError("setup");
  ByteArrayOutputStream raw=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(raw,new IsaacCipher(new int[]{4,3,2,1}));
  String one=b.sendDevInventorySpritePreview(slot,24016,w);if(!one.contains("serverStateMutated=false"))throw new AssertionError(one);
  BankState.Stack after=b.inventoryAt(slot);if(after==null||after.itemId!=995||after.qty!=before.qty)throw new AssertionError("preview mutated inventory");
  String gal=b.sendDevInventorySpriteGallery(0,new int[]{24016,24017,24018},w);if(!gal.contains("24016@0")||!gal.contains("24018@2"))throw new AssertionError(gal);
  after=b.inventoryAt(slot);if(after==null||after.itemId!=995)throw new AssertionError("gallery mutated inventory");
  String restore=b.restoreDevInventoryPreview(w);if(!restore.contains("authoritative=true"))throw new AssertionError(restore);
  if(raw.size()==0)throw new AssertionError("no packets");
  System.out.println("V591_DEV_INVENTORY_SPRITE_PREVIEW_PASS single=true gallery24016_24017_24018=true authoritativeInventoryUnchanged=true restore=true persisted=false");
 }
}
