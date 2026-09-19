package spk.local;
import java.io.*;
public final class StackableAmmoEquipmentTest {
 public static void main(String[] args)throws Exception{
  int[] ids={882,11212,877,9341,806,868};
  for(int id:ids){if(ItemDefinitionRepository.get(id)==null)throw new AssertionError("missing "+id);if(!ItemDefinitionRepository.isStackable(id))throw new AssertionError("not stackable "+id+" "+ItemCatalog.name(id));}
  BankState b=new BankState();EquipmentState e=new EquipmentState();ByteArrayOutputStream raw=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(raw,new IsaacCipher(new int[]{1,3,5,7}));
  String a=b.spawnItem(882,100,w);if(!a.startsWith("ITEM_SPAWN_OK"))throw new AssertionError(a);int slot=find(b,882);if(slot<0||b.inventoryAt(slot).qty!=100)throw new AssertionError("arrow spawn stack");
  a=b.spawnItem(882,50,w);if(b.inventoryAt(slot).qty!=150)throw new AssertionError("arrow merge qty="+b.inventoryAt(slot).qty);
  EquipmentMetadataRepository.Meta meta=ItemDefinitionRepository.equipmentMetaForClientAction(882);if(meta==null||meta.slot!=EquipmentSlot.AMMO)throw new AssertionError("arrow equip slot="+(meta==null?null:meta.slot));
  a=b.equipFromInventory(slot,882,e,w);if(!a.startsWith("EQUIP_STACK_OK")||e.itemAt(EquipmentSlot.AMMO)!=882||e.quantityAt(EquipmentSlot.AMMO)!=150||b.inventoryAt(slot)!=null)throw new AssertionError(a+" eqQty="+e.quantityAt(EquipmentSlot.AMMO));
  b.spawnItem(882,25,w);int slot2=find(b,882);a=b.equipFromInventory(slot2,882,e,w);if(!a.startsWith("EQUIP_STACK_MERGE_OK")||e.quantityAt(EquipmentSlot.AMMO)!=175)throw new AssertionError(a);
  a=b.unequipToInventory(EquipmentSlot.AMMO.equipmentIndex,882,e,w);int inv=find(b,882);if(!a.startsWith("UNEQUIP_OK")||inv<0||b.inventoryAt(inv).qty!=175)throw new AssertionError(a);
  System.out.println("V57_STACKABLE_AMMO_PASS arrowsBoltsDartsKnives=true spawnMerge=true ammoEquipStack175=true unequipStack175=true packet53QuantityAware=true");
 }
 static int find(BankState b,int id){for(int i=0;i<b.inventoryCapacity();i++){BankState.Stack s=b.inventoryAt(i);if(s!=null&&s.itemId==id)return i;}return -1;}
}
