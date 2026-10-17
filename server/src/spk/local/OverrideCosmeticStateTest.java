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

        overridePublicationAtomicity();

        System.out.println("V51842_OVERRIDE_COSMETIC_STATE_PASS kellatha21560=true owner22132=true swapReturnsOld=true removeReturnsNew=true realHeadCapeUnchanged=true ammo11212x420Unchanged=true overridePublicationAtomic=true overrideReplacementAtomic=true overrideReturnedOldPreferredSlot=true overrideOpenBankMirrorAtomic=true");
    }

    private static void overridePublicationAtomicity() throws Exception {
        noPriorOverrideFailureRetry();
        replacementOverrideFailureRetry();
        openBankOverrideFailureRetry();
    }

    private static void noPriorOverrideFailureRetry() throws Exception {
        BankState bank=new BankState();
        CosmeticState cosmetic=new CosmeticState();
        ServerPacketWriter good=writer(31);

        req(bank.spawnItem(21560,1,good).startsWith("ITEM_SPAWN_OK"),
            "atomic override spawn 21560");
        int slot=find(bank,21560);
        req(slot>=0,"atomic override slot 21560");

        boolean failed=false;
        try{
            CosmeticOverrideService.apply(
                bank,
                slot,
                21560,
                cosmetic,
                failingWriter(41)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        req(failed,"Override publication did not fail");
        req(!cosmetic.active(),"failed Override activated cosmetic");
        req(bank.inventoryCount(21560)==1,
            "failed Override consumed new cosmetic");

        String retry=CosmeticOverrideService.apply(
            bank,
            slot,
            21560,
            cosmetic,
            good
        );

        req(retry.startsWith("COSMETIC_OVERRIDE_OK"),retry);
        req(cosmetic.itemId()==21560,
            "Override retry wrong cosmetic");
        req(bank.inventoryCount(21560)==0,
            "Override retry did not consume exactly once");
    }

    private static void replacementOverrideFailureRetry() throws Exception {
        BankState bank=new BankState();
        CosmeticState cosmetic=new CosmeticState();
        cosmetic.set(21560);
        ServerPacketWriter good=writer(51);

        req(bank.spawnItem(22132,1,good).startsWith("ITEM_SPAWN_OK"),
            "atomic override replacement spawn 22132");
        int slot=find(bank,22132);
        req(slot>=0,"atomic override replacement slot");

        boolean failed=false;
        try{
            CosmeticOverrideService.apply(
                bank,
                slot,
                22132,
                cosmetic,
                failingWriter(61)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        req(failed,"Override replacement publication did not fail");
        req(cosmetic.itemId()==21560,
            "failed Override replacement changed cosmetic");
        req(bank.inventoryCount(22132)==1,
            "failed Override replacement consumed new item");
        req(bank.inventoryCount(21560)==0,
            "failed Override replacement returned old item");

        String retry=CosmeticOverrideService.apply(
            bank,
            slot,
            22132,
            cosmetic,
            good
        );

        req(retry.startsWith("COSMETIC_OVERRIDE_OK"),retry);
        req(cosmetic.itemId()==22132,
            "Override replacement retry wrong cosmetic");
        req(bank.inventoryCount(22132)==0,
            "Override replacement retry new item count");
        req(bank.inventoryCount(21560)==1,
            "Override replacement retry old item count");
        req(bank.inventoryAt(slot)!=null &&
            bank.inventoryAt(slot).itemId==21560,
            "Override replacement did not return old cosmetic to preferred consumed slot");
        req(retry.contains("returnedOldSlot="+slot),
            "Override replacement result lost exact preferred slot: "+retry);
    }

    private static void openBankOverrideFailureRetry() throws Exception {
        BankState bank=new BankState();
        CosmeticState cosmetic=new CosmeticState();
        ServerPacketWriter good=writer(71);

        req(bank.spawnItem(21560,1,good).startsWith("ITEM_SPAWN_OK"),
            "open-bank Override spawn");
        bank.open(good);
        int slot=find(bank,21560);
        req(slot>=0,"open-bank Override slot");

        boolean failed=false;
        try{
            CosmeticOverrideService.apply(
                bank,
                slot,
                21560,
                cosmetic,
                failingWriter(81)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        req(failed,"open-bank Override publication did not fail");
        req(!cosmetic.active(),
            "failed open-bank Override activated cosmetic");
        req(bank.inventoryCount(21560)==1,
            "failed open-bank Override consumed inventory");

        String retry=CosmeticOverrideService.apply(
            bank,
            slot,
            21560,
            cosmetic,
            good
        );

        req(retry.startsWith("COSMETIC_OVERRIDE_OK"),retry);
        req(cosmetic.itemId()==21560,
            "open-bank Override retry wrong cosmetic");
        req(bank.inventoryCount(21560)==0,
            "open-bank Override retry did not commit exactly once");
    }

    private static ServerPacketWriter writer(int seed) {
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{seed,seed+1,seed+2,seed+3})
        );
    }

    private static ServerPacketWriter failingWriter(int seed)
        throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(new byte[1024]);

        return new ServerPacketWriter(
            queue,
            new IsaacCipher(new int[]{seed,seed+1,seed+2,seed+3})
        );
    }

    private static int find(BankState bank,int itemId) {
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null&&stack.itemId==itemId)
                return i;
        }
        return -1;
    }
}
