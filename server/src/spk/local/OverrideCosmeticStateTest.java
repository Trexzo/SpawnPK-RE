package spk.local;

import java.io.ByteArrayOutputStream;

public final class OverrideCosmeticStateTest {
    private static void req(boolean ok,String msg){ if(!ok) throw new AssertionError(msg); }
    public static void main(String[] args) throws Exception {
        BankState bank=new BankState();
        EquipmentState eq=new EquipmentState();
        PlayerState ps=new PlayerState();
        ServerPacketWriter w=new ServerPacketWriter(new ByteArrayOutputStream(),new IsaacCipher(new int[]{1,2,3,4}));

        // Sentinel real equipment must survive cosmetic override unchanged.
        eq.set(EquipmentSlot.HEAD,4151);
        eq.set(EquipmentSlot.CAPE,20570);
        eq.setStack(EquipmentSlot.AMMO,11212,420);
        int head=eq.itemAt(EquipmentSlot.HEAD), cape=eq.itemAt(EquipmentSlot.CAPE), ammo=eq.itemAt(EquipmentSlot.AMMO), ammoQty=eq.quantityAt(EquipmentSlot.AMMO);

        req(bank.spawnItem(21560,1,w).startsWith("ITEM_SPAWN_OK"),"spawn Kellatha");
        int slotK=-1; for(int i=0;i<bank.inventorySlots();i++){ BankState.Stack s=bank.inventoryAt(i); if(s!=null&&s.itemId==21560){slotK=i;break;} }
        req(slotK>=0,"Kellatha slot");
        String r1=CosmeticOverrideService.apply(bank,slotK,21560,ps.cosmetic(),w);
        req(r1.startsWith("COSMETIC_OVERRIDE_OK"),r1);
        req(ps.cosmetic().itemId()==21560,"bs kellatha");
        req(eq.itemAt(EquipmentSlot.HEAD)==head,"head changed");
        req(eq.itemAt(EquipmentSlot.CAPE)==cape,"cape changed");
        req(eq.itemAt(EquipmentSlot.AMMO)==ammo&&eq.quantityAt(EquipmentSlot.AMMO)==ammoQty,"ammo changed");

        req(bank.spawnItem(22132,1,w).startsWith("ITEM_SPAWN_OK"),"spawn owner partyhat");
        int slotP=-1; for(int i=0;i<bank.inventorySlots();i++){ BankState.Stack s=bank.inventoryAt(i); if(s!=null&&s.itemId==22132){slotP=i;break;} }
        req(slotP>=0,"partyhat slot");
        String r2=CosmeticOverrideService.apply(bank,slotP,22132,ps.cosmetic(),w);
        req(r2.startsWith("COSMETIC_OVERRIDE_OK"),r2);
        req(ps.cosmetic().itemId()==22132,"bs partyhat");
        req(bank.inventoryCount(21560)==1,"old cosmetic not returned");
        req(eq.itemAt(EquipmentSlot.HEAD)==head&&eq.itemAt(EquipmentSlot.CAPE)==cape,"real equipment changed on swap");
        req(eq.itemAt(EquipmentSlot.AMMO)==ammo&&eq.quantityAt(EquipmentSlot.AMMO)==ammoQty,"ammo changed on swap");

        String r3=bank.unequipCosmeticToInventory(ps.cosmetic(),w);
        req(r3.startsWith("COSMETIC_UNEQUIP_OK"),r3);
        req(!ps.cosmetic().active(),"cosmetic not cleared");
        req(bank.inventoryCount(22132)==1,"partyhat not returned");
        req(eq.itemAt(EquipmentSlot.HEAD)==head&&eq.itemAt(EquipmentSlot.CAPE)==cape,"real equipment changed on remove");
        req(eq.itemAt(EquipmentSlot.AMMO)==ammo&&eq.quantityAt(EquipmentSlot.AMMO)==ammoQty,"ammo changed on remove");

        System.out.println("V51842_OVERRIDE_COSMETIC_STATE_PASS kellatha21560=true owner22132=true swapReturnsOld=true removeReturnsNew=true realHeadCapeUnchanged=true ammo11212x420Unchanged=true");
    }
}
