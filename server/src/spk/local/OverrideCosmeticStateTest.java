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

        publicationAtomicity();

        System.out.println("V51842_OVERRIDE_COSMETIC_STATE_PASS kellatha21560=true owner22132=true swapReturnsOld=true removeReturnsNew=true realHeadCapeUnchanged=true ammo11212x420Unchanged=true cosmeticEquipPublicationAtomic=true cosmeticReplacePublicationAtomic=true cosmeticOpenBankMirrorAtomic=true cosmeticUnequipFailureAtomic=true");
    }

    private static void publicationAtomicity() throws Exception {
        noPreviousEquipFailureRetry();
        replacementFailureRetry();
        openBankMirrorFailureRetry();
        unequipFailureRetry();
    }

    private static void noPreviousEquipFailureRetry() throws Exception {
        BankState bank=new BankState();
        CosmeticState cosmetic=new CosmeticState();
        ServerPacketWriter good=writer(31);

        req(bank.spawnItem(27454,1,good).startsWith("ITEM_SPAWN_OK"),
            "atomic cosmetic spawn 27454");
        int slot=find(bank,27454);
        req(slot>=0,"atomic cosmetic slot 27454");

        boolean failed=false;
        try{
            bank.equipCosmeticFromInventory(slot,27454,cosmetic,failingWriter(41));
        }catch(java.io.IOException expected){
            failed=true;
        }

        req(failed,"cosmetic equip publication did not fail");
        req(!cosmetic.active(),"failed cosmetic equip activated state");
        req(bank.inventoryCount(27454)==1,"failed cosmetic equip consumed inventory");

        String retry=bank.equipCosmeticFromInventory(slot,27454,cosmetic,good);
        req(retry.startsWith("COSMETIC_EQUIP_OK"),retry);
        req(cosmetic.itemId()==27454,"cosmetic equip retry wrong state");
        req(bank.inventoryCount(27454)==0,"cosmetic equip retry did not consume exactly once");
    }

    private static void replacementFailureRetry() throws Exception {
        BankState bank=new BankState();
        CosmeticState cosmetic=new CosmeticState();
        cosmetic.set(21560);
        ServerPacketWriter good=writer(51);

        req(bank.spawnItem(22132,1,good).startsWith("ITEM_SPAWN_OK"),
            "atomic replacement spawn 22132");
        int slot=find(bank,22132);
        req(slot>=0,"atomic replacement slot 22132");

        boolean failed=false;
        try{
            bank.equipCosmeticFromInventory(slot,22132,cosmetic,failingWriter(61));
        }catch(java.io.IOException expected){
            failed=true;
        }

        req(failed,"cosmetic replacement publication did not fail");
        req(cosmetic.itemId()==21560,"failed cosmetic replacement changed prior cosmetic");
        req(bank.inventoryCount(22132)==1,"failed cosmetic replacement consumed new item");
        req(bank.inventoryCount(21560)==0,"failed cosmetic replacement returned old item");

        String retry=bank.equipCosmeticFromInventory(slot,22132,cosmetic,good);
        req(retry.startsWith("COSMETIC_EQUIP_OK"),retry);
        req(cosmetic.itemId()==22132,"cosmetic replacement retry wrong state");
        req(bank.inventoryCount(22132)==0,"cosmetic replacement retry new item count");
        req(bank.inventoryCount(21560)==1,"cosmetic replacement retry did not return old item exactly once");
    }

    private static void openBankMirrorFailureRetry() throws Exception {
        BankState bank=new BankState();
        CosmeticState cosmetic=new CosmeticState();
        ServerPacketWriter good=writer(71);

        req(bank.spawnItem(27454,1,good).startsWith("ITEM_SPAWN_OK"),
            "open mirror cosmetic spawn");
        bank.open(good);
        int slot=find(bank,27454);
        req(slot>=0,"open mirror cosmetic slot");

        boolean failed=false;
        try{
            bank.equipCosmeticFromInventory(slot,27454,cosmetic,failingWriter(81));
        }catch(java.io.IOException expected){
            failed=true;
        }

        req(failed,"open-bank cosmetic publication did not fail");
        req(!cosmetic.active(),"failed open-bank cosmetic equip activated state");
        req(bank.inventoryCount(27454)==1,"failed open-bank cosmetic equip consumed inventory");

        String retry=bank.equipCosmeticFromInventory(slot,27454,cosmetic,good);
        req(retry.startsWith("COSMETIC_EQUIP_OK"),retry);
        req(cosmetic.itemId()==27454,"open-bank cosmetic retry wrong state");
        req(bank.inventoryCount(27454)==0,"open-bank cosmetic retry did not commit exactly once");
    }

    private static void unequipFailureRetry() throws Exception {
        BankState bank=new BankState();
        CosmeticState cosmetic=new CosmeticState();
        cosmetic.set(27454);
        ServerPacketWriter good=writer(91);

        boolean failed=false;
        try{
            bank.unequipCosmeticToInventory(cosmetic,failingWriter(101));
        }catch(java.io.IOException expected){
            failed=true;
        }

        req(failed,"cosmetic unequip publication did not fail");
        req(cosmetic.itemId()==27454,"failed cosmetic unequip cleared state");
        req(bank.inventoryCount(27454)==0,"failed cosmetic unequip added inventory");

        String retry=bank.unequipCosmeticToInventory(cosmetic,good);
        req(retry.startsWith("COSMETIC_UNEQUIP_OK"),retry);
        req(!cosmetic.active(),"cosmetic unequip retry did not clear state");
        req(bank.inventoryCount(27454)==1,"cosmetic unequip retry did not add exactly once");
    }

    private static ServerPacketWriter writer(int seed) {
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{seed,seed+1,seed+2,seed+3})
        );
    }

    private static ServerPacketWriter failingWriter(int seed) throws Exception {
        OutboundPacketQueue queue=new OutboundPacketQueue(1024);
        queue.offer(new byte[1024]);
        return new ServerPacketWriter(
            queue,
            new IsaacCipher(new int[]{seed,seed+1,seed+2,seed+3})
        );
    }

    private static int find(BankState bank,int id) {
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null&&stack.itemId==id)
                return i;
        }
        return -1;
    }
}
