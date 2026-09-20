package spk.local;

import java.io.*;import java.util.*;

public final class CosmeticDedicatedSlotTest {
    public static void main(String[] args)throws Exception{
        BankState b=new BankState();EquipmentState e=new EquipmentState();PlayerState p=new PlayerState();
        e.setStack(EquipmentSlot.AMMO,11212,420);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{2,3,4,5}));
        String add=b.spawnItem(27454,1,w);if(!add.startsWith("ITEM_SPAWN_OK"))throw new AssertionError(add);
        int slot=-1;for(int i=0;i<28;i++){BankState.Stack st=b.inventoryAt(i);if(st!=null&&st.itemId==27454){slot=i;break;}}
        String eq=b.equipCosmeticFromInventory(slot,27454,p.cosmetic(),w);if(!eq.startsWith("COSMETIC_EQUIP_OK"))throw new AssertionError(eq);
        p.syncEquipmentPresentation(e);if(p.nativeIconItemId()!=27454||e.itemAt(EquipmentSlot.AMMO)!=11212||e.quantityAt(EquipmentSlot.AMMO)!=420)throw new AssertionError("dedicated state");
        SortedMap<String,String> props=PersistenceSchemaTestSupport.capturePlayer(p);PlayerState p2=new PlayerState();PersistenceSchemaTestSupport.restorePlayer(p2,props);if(p2.nativeIconItemId()!=27454)throw new AssertionError("persist cosmetic");
        System.out.println("V511_COSMETIC_DEDICATED_SLOT_PASS cosmetic27454=true ammo11212x420=true coexist=true persistence=true");
    }
}
