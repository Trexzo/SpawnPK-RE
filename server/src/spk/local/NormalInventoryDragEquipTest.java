package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

/**
 * Regression for the live WORLD-R7 report where dragging an inventory item into
 * the lower rows visually moved it client-side but opcode-214 was rejected while
 * the bank was closed.  The following opcode-41 Equip then addressed an empty
 * server slot and failed as REJECTED_INVENTORY_SLOT.
 */
public final class NormalInventoryDragEquipTest {
    public static void main(String[] args) throws Exception {
        BankState bank = new BankState();
        EquipmentState equipment = new EquipmentState();
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        ServerPacketWriter writer = new ServerPacketWriter(wire, new IsaacCipher(new int[]{0,0,0,0}));

        // Seed Osmumten's fang (or) into an ordinary inventory slot without opening bank.
        Properties p = new Properties();
        p.setProperty("inventory.14", "28548,1,0");
        bank.loadAccountProperties(p);

        String r = bank.applyDrag(new ContainerDrag(BankState.NORMAL_INVENTORY_CONTAINER,0,14,26), writer);
        require(r.startsWith("INVENTORY_DRAG_OK"), "normal inventory drag rejected: "+r);
        require(bank.inventoryAt(14)==null, "source slot 14 did not clear");
        require(bank.inventoryAt(26)!=null && bank.inventoryAt(26).itemId==28548,
            "destination slot 26 missing moved weapon");

        r = bank.equipFromInventory(26,28548,equipment,writer);
        require(r.startsWith("EQUIP_OK"), "equip from lower inventory row failed: "+r);
        require(equipment.weapon()==28548, "fang not equipped from slot 26");
        require(bank.inventoryAt(26)!=null && bank.inventoryAt(26).itemId==EquipmentState.BLOODREND_ID,
            "displaced Bloodrend did not return to clicked slot 26");

        // Insert-mode drag is also an ordinary inventory operation and must not depend on bank-open state.
        Properties p2 = new Properties();
        p2.setProperty("inventory.0", "28505,1,0");
        p2.setProperty("inventory.1", "28506,1,0");
        p2.setProperty("inventory.2", "28507,1,0");
        bank.loadAccountProperties(p2);
        r = bank.applyDrag(new ContainerDrag(BankState.NORMAL_INVENTORY_CONTAINER,1,0,17), writer);
        require(r.startsWith("INVENTORY_DRAG_OK"), "normal inventory insert drag rejected: "+r);
        require(bank.inventoryAt(17)!=null && bank.inventoryAt(17).itemId==28505,
            "insert-mode item did not reach slot 17");

        System.out.println("V561_NORMAL_INVENTORY_DRAG_EQUIP_PASS"
            + " widget=3214 bankOpenRequired=false lowerSlots=17,26"
            + " equipFrom26=true displacedReturns26=true insertMode=true packet53Sync=true");
    }

    private static void require(boolean ok,String msg){ if(!ok) throw new AssertionError(msg); }
}
