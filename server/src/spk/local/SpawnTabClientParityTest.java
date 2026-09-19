package spk.local;

/**
 * Pinned-client reader parity for the exact packet-71 Spawn-Tab shortcut payload.
 * Static client bytecode separately proves A()==0 is rewritten to root 67027
 * before sidebar[tab] assignment.
 */
public final class SpawnTabClientParityTest {
    public static void main(String[] args) throws Exception {
        if (BootstrapPackets.SPAWN_TAB_INDEX != 13) throw new AssertionError("Spawn/Search must occupy bottom-right tab 13");
        Class<?> c=Class.forName("rs.x.e");
        byte[] p=BootstrapPackets.sidebar71(BootstrapPackets.SPAWN_TAB_SHORTCUT_ROOT, BootstrapPackets.SPAWN_TAB_INDEX);
        Object b=c.getConstructor(byte[].class).newInstance((Object)p);
        int root=((Number)c.getMethod("A").invoke(b)).intValue();
        int tab=((Number)c.getMethod("N").invoke(b)).intValue();
        int consumed=c.getField("h").getInt(b);
        if(root!=0 || tab!=BootstrapPackets.SPAWN_TAB_INDEX || consumed!=3)
            throw new AssertionError("root="+root+" tab="+tab+" consumed="+consumed);
        System.out.println("V522_SPAWN_TAB_PACKET71_CLIENT_PARITY_PASS wireRoot=0 tab="+tab
                         + " staticHandlerResolvedRoot="+BootstrapPackets.SPAWN_TAB_ROOT+" consumed="+consumed);
    }
}
