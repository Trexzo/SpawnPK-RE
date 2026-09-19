package spk.local;

import java.util.Arrays;

public final class BootstrapStaticTest {
    public static void main(String[] args) throws Exception {
        byte[] idle = BootstrapPackets.player81Idle();
        if (!Arrays.equals(idle, new byte[]{0x00,0x7f,(byte)0xf0})) throw new AssertionError(Arrays.toString(idle));
        byte[] tp = BootstrapPackets.player81TeleportNoAppearance(0,55,55);
        if (tp.length != 5) throw new AssertionError("teleport len=" + tp.length);
        byte[] appearance = BootstrapPackets.appearanceBlock("localtest");
        if (appearance.length != 62) throw new AssertionError("appearance len=" + appearance.length);
        byte[] tpAppearance = BootstrapPackets.player81TeleportWithAppearance(0,55,55,"localtest");
        if (tpAppearance.length != 69) throw new AssertionError("teleport+appearance len=" + tpAppearance.length);
        if ((tpAppearance[5] & 255) != 16) throw new AssertionError("mask != 0x10");
        if ((tpAppearance[6] & 255) != ((-62)&255)) throw new AssertionError("O() length encoding mismatch");
        byte[] r = BootstrapPackets.region73(385,436);
        if (r.length != 4) throw new AssertionError();
        byte[] p249 = BootstrapPackets.packet249(0,1);
        byte[] e110 = BootstrapPackets.runEnergy110(100);
        if (!Arrays.equals(p249, new byte[]{(byte)128,(byte)129,0})) throw new AssertionError(Arrays.toString(p249));
        if (e110.length != 1 || (e110[0] & 255) != 100) throw new AssertionError("run energy 110");
        byte[] w126 = BootstrapPackets.widgetText126(149, "100%");
        byte[] expected126 = new byte[]{'1','0','0','%',10,0,0x15};
        if (!Arrays.equals(w126, expected126)) throw new AssertionError("widget126=" + Arrays.toString(w126));
        System.out.println("BOOTSTRAP_STATIC_ENCODING_PASS idle81=00-7F-F0 teleport81_bits=5 appearance=62 packet81_withAppearance=69 region73=4 packet249=3 runEnergy110=100 widget126_149=100%");
    }
}
