package spk.local;

/** Verifies the exact v3.2 bank main+inventory-overlay contract with pinned client readers. */
public final class BankOverlayClientParityTest {
    public static void main(String[] args) throws Exception {
        Class<?> c=Class.forName("rs.x.e");
        byte[] p=BootstrapPackets.interfaceOverlay248(BankState.BANK_ROOT,BankState.BANK_INVENTORY_ROOT);
        Object b=c.getConstructor(byte[].class).newInstance((Object)p);
        int main=((Number)c.getMethod("T").invoke(b)).intValue();
        int side=((Number)c.getMethod("A").invoke(b)).intValue();
        int consumed=c.getField("h").getInt(b);
        if(main!=5292||side!=5063||consumed!=4)
            throw new AssertionError("packet248 main="+main+" side="+side+" consumed="+consumed);

        byte[] inv=BootstrapPackets.itemContainer53(BankState.BANK_INVENTORY_CONTAINER,new int[]{995},new int[]{1});
        b=c.getConstructor(byte[].class).newInstance((Object)inv);
        int widget=((Number)c.getMethod("A").invoke(b)).intValue();
        int count=((Number)c.getMethod("A").invoke(b)).intValue();
        int qty=((Number)c.getMethod("y").invoke(b)).intValue();
        int item=((Number)c.getMethod("U").invoke(b)).intValue()-1;
        consumed=c.getField("h").getInt(b);
        if(widget!=5064||count!=1||qty!=1||item!=995||consumed!=inv.length)
            throw new AssertionError("overlay53 widget="+widget+" count="+count+" qty="+qty+" item="+item+" consumed="+consumed);
        System.out.println("V32_BANK_OVERLAY_CLIENT_PARITY_PASS packet248 main=5292 side=5063 inventoryWidget=5064 item=995 qty=1");
    }
}
