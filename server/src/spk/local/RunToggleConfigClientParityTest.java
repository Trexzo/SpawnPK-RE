package spk.local;

import java.lang.reflect.*;

/** Verifies packet-36 payload bytes against the pinned client's real S()/z() readers. */
public final class RunToggleConfigClientParityTest {
    public static void main(String[] args) throws Exception {
        verify(true); verify(false);
        System.out.println("RUN_TOGGLE_CONFIG36_CLIENT_PARITY_PASS setting=173 on=1 off=0 bytes=3");
    }
    private static void verify(boolean on) throws Exception {
        byte[] payload=BootstrapPackets.config36(173,on?1:0);
        Class<?> c=Class.forName("rs.x.e");
        Object b=c.getConstructor(byte[].class).newInstance((Object)payload);
        int index=((Number)c.getMethod("S").invoke(b)).intValue();
        int value=((Number)c.getMethod("z").invoke(b)).byteValue();
        int consumed=c.getField("h").getInt(b);
        if(index!=173) throw new AssertionError("index="+index);
        if(value!=(on?1:0)) throw new AssertionError("value="+value);
        if(consumed!=3) throw new AssertionError("consumed="+consumed);
    }
}
