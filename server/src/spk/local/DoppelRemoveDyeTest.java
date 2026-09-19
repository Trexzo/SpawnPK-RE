package spk.local;
import java.io.*;
public final class DoppelRemoveDyeTest {
 public static void main(String[] args)throws Exception{
  BankState b=new BankState();ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{17,19,23,29}));
  req(b.spawnItem(28807,1,w).startsWith("ITEM_SPAWN_OK"),"spawn dyed");int slot=find(b,28807);req(slot>=0,"slot");
  String r=b.splitInventoryOne(slot,28807,3241,28824,w);req(r.startsWith("INVENTORY_SPLIT_OK"),r);int dye=find(b,28824);req(dye>=0,"dye returned");req(b.inventoryAt(slot).itemId==3241,"base same slot");
  r=b.combineInventoryOne(dye,28824,slot,3241,28807,w);req(r.startsWith("INVENTORY_COMBINE_OK"),r);req(b.inventoryAt(slot).itemId==28807,"redyed same regular slot");req(find(b,28824)<0,"dye consumed");
  System.out.println("V57_DOPPELGANGER_DYE_ROUNDTRIP_PASS remove28807_to3241_plus28824=true reapply28824_on3241_to28807=true slotStable=true");
 }
 static int find(BankState b,int id){for(int i=0;i<b.inventoryCapacity();i++){BankState.Stack s=b.inventoryAt(i);if(s!=null&&s.itemId==id)return i;}return -1;} static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}
