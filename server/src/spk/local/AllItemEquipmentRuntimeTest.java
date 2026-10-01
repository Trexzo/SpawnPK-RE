package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * v5.2.3 direct runtime-state regression for items that failed in the first
 * v5.2.2 live equipment pass plus one ordinary base-cache opcode-41 item.
 */
public final class AllItemEquipmentRuntimeTest {
    public static void main(String[] args) throws Exception {
        BankState bank = new BankState();
        EquipmentState equipment = new EquipmentState();
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        ServerPacketWriter writer = new ServerPacketWriter(wire, new IsaacCipher(new int[]{0,0,0,0}));

        spawn(bank, writer, 23984); // Wanderer's amulet (i)
        spawn(bank, writer, 23630); // Wanderer's ring (i)
        spawn(bank, writer, 21701); // Phantom scythe
        spawn(bank, writer, 28021); // Corrupted scythe of vitur
        spawn(bank, writer, 6737);  // Berserker ring: normal base-cache fixture

        equip(bank, equipment, writer, 0, 23984, EquipmentSlot.AMULET);
        equip(bank, equipment, writer, 1, 23630, EquipmentSlot.RING);
        equip(bank, equipment, writer, 2, 21701, EquipmentSlot.WEAPON);
        require(equipment.weapon()==21701,"Phantom scythe not equipped");
        require(bank.inventoryAt(2)!=null && bank.inventoryAt(2).itemId==EquipmentState.BLOODREND_ID,
            "Bloodrend was not returned to source slot");

        // Corrupted scythe was originally in slot 3. Equipping it must replace the
        // Phantom scythe and return Phantom to the same clicked inventory slot.
        equip(bank, equipment, writer, 3, 28021, EquipmentSlot.WEAPON);
        require(equipment.weapon()==28021,"Corrupted scythe not equipped");
        require(bank.inventoryAt(3)!=null && bank.inventoryAt(3).itemId==21701,
            "Phantom scythe was not returned to source slot");

        // Normal cache item: the real client opcode-41 click is trusted proof that
        // the item is equipable even when items.tsv does not mirror its action array.
        equip(bank, equipment, writer, 4, 6737, EquipmentSlot.RING);
        require(equipment.itemAt(EquipmentSlot.RING)==6737,"base-cache ring not equipped");
        require(bank.inventoryAt(4)!=null && bank.inventoryAt(4).itemId==23630,
            "old Wanderer ring was not displaced back to source slot");

        EquipmentMetadataRepository.Meta phantom=EquipmentMetadataRepository.resolve(21701);
        EquipmentMetadataRepository.Meta corrupt=EquipmentMetadataRepository.resolve(28021);
        require(phantom!=null && phantom.twoHanded && phantom.pose==EquipmentPoseProfile.SCYTHE_SPAWNPK_FAMILY,
            "Phantom scythe family metadata");
        require(corrupt!=null && corrupt.twoHanded && corrupt.pose==EquipmentPoseProfile.SCYTHE_SPAWNPK_FAMILY,
            "Corrupted scythe family metadata");

        publicationAtomicity();

        require(wire.size()>0,"no packet53 equipment/inventory synchronization written");
        System.out.println("V523_ALL_ITEM_EQUIPMENT_RUNTIME_PASS"
            + " amulet23984=AMULET ring23630=RING phantom21701=WEAPON"
            + " corrupted28021=WEAPON base6737=RING"
            + " sameSlotDisplacement=true packet53Sync=true"
            + " ammoMergePublicationAtomic=true"
            + " ordinaryEquipPublicationAtomic=true"
            + " twoHandShieldDisplacementAtomic=true"
            + " shieldTwoHandDisplacementAtomic=true"
            + " unequipOpenBankPublicationAtomic=true");
    }

    private static void publicationAtomicity() throws Exception {
        testAmmoMergeFailureRetry();
        testOrdinaryEquipFailureRetry();
        testTwoHandDisplacesShieldFailureRetry();
        testShieldDisplacesTwoHandFailureRetry();
        testUnequipOpenBankFailureRetry();
    }

    private static void testAmmoMergeFailureRetry() throws Exception {
        BankState bank=new BankState();
        EquipmentState equipment=new EquipmentState();
        ServerPacketWriter good=writer(11);

        String spawned=bank.spawnItem(11212,20,good);
        require(spawned.startsWith("ITEM_SPAWN_OK"),"ammo spawn: "+spawned);
        int slot=find(bank,11212);
        require(slot>=0,"ammo slot");

        equipment.setStack(EquipmentSlot.AMMO,11212,100);

        boolean failed=false;
        try{
            bank.equipFromInventory(
                slot,
                11212,
                equipment,
                failingWriter(21)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        require(failed,"ammo merge publication did not fail");
        require(bank.inventoryCount(11212)==20,"failed ammo merge consumed inventory");
        require(equipment.itemAt(EquipmentSlot.AMMO)==11212 &&
                equipment.quantityAt(EquipmentSlot.AMMO)==100,
            "failed ammo merge mutated equipment");

        String retry=bank.equipFromInventory(slot,11212,equipment,good);
        require(retry.startsWith("EQUIP_STACK_MERGE_OK"),"ammo retry: "+retry);
        require(bank.inventoryCount(11212)==0,"ammo retry inventory");
        require(equipment.quantityAt(EquipmentSlot.AMMO)==120,"ammo retry quantity");
    }

    private static void testOrdinaryEquipFailureRetry() throws Exception {
        BankState bank=new BankState();
        EquipmentState equipment=new EquipmentState();
        ServerPacketWriter good=writer(31);

        spawn(bank,good,4151);
        int slot=find(bank,4151);
        require(slot>=0,"whip slot");

        boolean failed=false;
        try{
            bank.equipFromInventory(
                slot,
                4151,
                equipment,
                failingWriter(41)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        require(failed,"ordinary equip publication did not fail");
        require(equipment.weapon()==EquipmentState.BLOODREND_ID,
            "failed ordinary equip changed weapon");
        require(bank.inventoryAt(slot)!=null &&
                bank.inventoryAt(slot).itemId==4151,
            "failed ordinary equip changed inventory");

        String retry=bank.equipFromInventory(slot,4151,equipment,good);
        require(retry.startsWith("EQUIP_OK"),"ordinary retry: "+retry);
        require(equipment.weapon()==4151,"ordinary retry weapon");
        require(bank.inventoryAt(slot)!=null &&
                bank.inventoryAt(slot).itemId==EquipmentState.BLOODREND_ID,
            "ordinary retry displacement");
    }

    private static void testTwoHandDisplacesShieldFailureRetry() throws Exception {
        BankState bank=new BankState();
        EquipmentState equipment=new EquipmentState();
        ServerPacketWriter good=writer(51);

        equipment.setWeapon(-1);
        equipment.set(EquipmentSlot.SHIELD,9065);
        spawn(bank,good,24023);
        int slot=find(bank,24023);
        require(slot>=0,"two-hand slot");

        boolean failed=false;
        try{
            bank.equipFromInventory(
                slot,
                24023,
                equipment,
                failingWriter(61)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        require(failed,"two-hand equip publication did not fail");
        require(equipment.weapon()<0,"failed two-hand equip changed weapon");
        require(equipment.itemAt(EquipmentSlot.SHIELD)==9065,
            "failed two-hand equip cleared shield");
        require(bank.inventoryAt(slot)!=null &&
                bank.inventoryAt(slot).itemId==24023,
            "failed two-hand equip changed inventory");

        String retry=bank.equipFromInventory(slot,24023,equipment,good);
        require(retry.startsWith("EQUIP_OK"),"two-hand retry: "+retry);
        require(equipment.weapon()==24023,"two-hand retry weapon");
        require(equipment.itemAt(EquipmentSlot.SHIELD)<0,"two-hand retry shield");
        require(bank.inventoryAt(slot)!=null &&
                bank.inventoryAt(slot).itemId==9065,
            "two-hand retry displaced shield");
    }

    private static void testShieldDisplacesTwoHandFailureRetry() throws Exception {
        BankState bank=new BankState();
        EquipmentState equipment=new EquipmentState();
        ServerPacketWriter good=writer(71);

        spawn(bank,good,9065);
        int slot=find(bank,9065);
        require(slot>=0,"shield slot");
        require(equipment.weapon()==EquipmentState.BLOODREND_ID,
            "shield fixture default weapon");
        require(EquipmentMetadataRepository.resolveKnownSlot(
                    equipment.weapon(),
                    EquipmentSlot.WEAPON
                ).twoHanded,
            "shield fixture weapon not two-handed");

        boolean failed=false;
        try{
            bank.equipFromInventory(
                slot,
                9065,
                equipment,
                failingWriter(81)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        require(failed,"shield equip publication did not fail");
        require(equipment.weapon()==EquipmentState.BLOODREND_ID,
            "failed shield equip cleared two-hand weapon");
        require(equipment.itemAt(EquipmentSlot.SHIELD)<0,
            "failed shield equip changed shield");
        require(bank.inventoryAt(slot)!=null &&
                bank.inventoryAt(slot).itemId==9065,
            "failed shield equip changed inventory");

        String retry=bank.equipFromInventory(slot,9065,equipment,good);
        require(retry.startsWith("EQUIP_OK"),"shield retry: "+retry);
        require(equipment.weapon()<0,"shield retry weapon");
        require(equipment.itemAt(EquipmentSlot.SHIELD)==9065,
            "shield retry shield");
        require(bank.inventoryAt(slot)!=null &&
                bank.inventoryAt(slot).itemId==EquipmentState.BLOODREND_ID,
            "shield retry displaced weapon");
    }

    private static void testUnequipOpenBankFailureRetry() throws Exception {
        BankState bank=new BankState();
        EquipmentState equipment=new EquipmentState();
        ServerPacketWriter good=writer(91);

        equipment.setWeapon(4151);
        bank.open(good);

        boolean failed=false;
        try{
            bank.unequipToInventory(
                EquipmentSlot.WEAPON.equipmentIndex,
                4151,
                equipment,
                failingWriter(101)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        require(failed,"open-bank unequip publication did not fail");
        require(equipment.weapon()==4151,"failed unequip changed equipment");
        require(bank.inventoryCount(4151)==0,"failed unequip changed inventory");

        String retry=bank.unequipToInventory(
            EquipmentSlot.WEAPON.equipmentIndex,
            4151,
            equipment,
            good
        );
        require(retry.startsWith("UNEQUIP_OK"),"unequip retry: "+retry);
        require(equipment.weapon()<0,"unequip retry equipment");
        require(bank.inventoryCount(4151)==1,"unequip retry inventory");
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

    private static int find(BankState bank,int itemId) {
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null && stack.itemId==itemId)
                return i;
        }
        return -1;
    }

    private static void spawn(BankState bank, ServerPacketWriter writer, int id) throws Exception {
        String r=bank.spawnItem(id,1,writer);
        require(r.startsWith("ITEM_SPAWN_OK"),"spawn "+id+": "+r);
    }

    private static void equip(BankState bank, EquipmentState equipment, ServerPacketWriter writer,
                              int invSlot, int id, EquipmentSlot expected) throws Exception {
        String r=bank.equipFromInventory(invSlot,id,equipment,writer);
        require(r.startsWith("EQUIP_OK"),"equip "+id+": "+r);
        require(equipment.itemAt(expected)==id,"equip "+id+" slot "+expected+": "+r);
    }

    private static void require(boolean ok,String msg){ if(!ok) throw new AssertionError(msg); }
}
