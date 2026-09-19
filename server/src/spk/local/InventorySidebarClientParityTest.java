package spk.local;

/** Verifies v3.1 packet-71/106 sidebar bytes with the pinned client's real A()/N()/O() readers. */
public final class InventorySidebarClientParityTest {
    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("rs.x.e");

        byte[] p71 = BootstrapPackets.sidebar71(3213, 3);
        Object b = c.getConstructor(byte[].class).newInstance((Object)p71);
        int root = ((Number)c.getMethod("A").invoke(b)).intValue();
        int tab = ((Number)c.getMethod("N").invoke(b)).intValue();
        int consumed = c.getField("h").getInt(b);
        if (root != 3213 || tab != 3 || consumed != 3)
            throw new AssertionError("packet71 root="+root+" tab="+tab+" consumed="+consumed);

        byte[] p106 = BootstrapPackets.selectTab106(3);
        b = c.getConstructor(byte[].class).newInstance((Object)p106);
        int selected = ((Number)c.getMethod("O").invoke(b)).intValue();
        consumed = c.getField("h").getInt(b);
        if (selected != 3 || consumed != 1)
            throw new AssertionError("packet106 tab="+selected+" consumed="+consumed);

        System.out.println("V31_INVENTORY_SIDEBAR_CLIENT_PARITY_PASS root71=3213 tab71=3 selected106=3 bytes=3+1");
    }
}
