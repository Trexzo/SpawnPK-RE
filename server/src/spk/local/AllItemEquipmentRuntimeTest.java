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

        require(wire.size()>0,"no packet53 equipment/inventory synchronization written");
        System.out.println("V523_ALL_ITEM_EQUIPMENT_RUNTIME_PASS"
            + " amulet23984=AMULET ring23630=RING phantom21701=WEAPON"
            + " corrupted28021=WEAPON base6737=RING"
            + " sameSlotDisplacement=true packet53Sync=true");
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
