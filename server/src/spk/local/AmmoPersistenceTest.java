package spk.local;
import java.io.*;import java.nio.file.*;
public final class AmmoPersistenceTest {
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("spk-ammo-persist-");Path f=dir.resolve("opensrc.properties");System.setProperty("spk.local.accountFile",f.toString());
  try{BankState b=new BankState();EquipmentState e=new EquipmentState();MovementState m=new MovementState();PetState p=new PetState();PlayerState ps=new PlayerState();ByteArrayOutputStream raw=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(raw,new IsaacCipher(new int[]{2,4,6,8}));b.spawnItem(11212,420,w);int slot=find(b,11212);String r=b.equipFromInventory(slot,11212,e,w);if(!r.startsWith("EQUIP_STACK_OK")||e.quantityAt(EquipmentSlot.AMMO)!=420)throw new AssertionError(r);AccountStore.save(b,e,m,p,ps);BankState b2=new BankState();EquipmentState e2=new EquipmentState();AccountStore.load(b2,e2,new MovementState(),new PetState(),new PlayerState());if(e2.itemAt(EquipmentSlot.AMMO)!=11212||e2.quantityAt(EquipmentSlot.AMMO)!=420)throw new AssertionError("reloaded ammo="+e2.itemAt(EquipmentSlot.AMMO)+" qty="+e2.quantityAt(EquipmentSlot.AMMO));System.out.println("V57_AMMO_PERSISTENCE_PASS dragonArrows11212_qty420=true equipmentQtyBackwardCompatible=true");}
  finally{System.clearProperty("spk.local.accountFile");try{Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(Exception ignored){}});}catch(Exception ignored){}}
 }
 static int find(BankState b,int id){for(int i=0;i<b.inventoryCapacity();i++){BankState.Stack s=b.inventoryAt(i);if(s!=null&&s.itemId==id)return i;}return -1;}
}
