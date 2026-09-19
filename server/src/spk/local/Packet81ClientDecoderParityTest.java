package spk.local;

import java.lang.reflect.*;

/**
 * Offline-only parity test: invoke the pinned client's complete private packet-81
 * decoder on the exact v0.3 candidate payload without launching the game client.
 */
public final class Packet81ClientDecoderParityTest {
    public static void main(String[] args) throws Exception {
        Class<?> clientClass = Class.forName("rs.Client");
        Class<?> playerClass = Class.forName("rs.a.k");
        Class<?> bufferClass = Class.forName("rs.x.e");

        Object client = unsafeAllocate(clientClass);
        Object player = playerClass.getConstructor().newInstance();

        Object players = Array.newInstance(playerClass, 2048);
        Array.set(players, 2047, player);
        setStatic(clientClass, "do", players);
        setStatic(clientClass, "eR", player);

        setField(client, clientClass, "kx", new int[2048]);
        setField(client, clientClass, "ky", Array.newInstance(bufferClass, 2048));
        setField(client, clientClass, "kv", new int[2048]);
        setField(client, clientClass, "jO", new int[2048]);
        // Unsafe allocation leaves jN/kw/ku at zero, which is exactly the empty
        // other-player state required by this candidate.

        byte[] payload = BootstrapPackets.player81TeleportWithAppearance(0,55,55,"localtest");
        Object buffer = bufferClass.getConstructor(byte[].class).newInstance((Object)payload);
        Method packet81 = clientClass.getDeclaredMethod("b", int.class, bufferClass);
        packet81.setAccessible(true);
        packet81.invoke(client, payload.length, buffer);

        int consumed = bufferClass.getField("h").getInt(buffer);
        if (consumed != payload.length) throw new AssertionError("consumed="+consumed+" len="+payload.length);
        String name = String.valueOf(playerClass.getMethod("o").invoke(player));
        if (!"localtest".equals(name)) throw new AssertionError("displayName="+name);
        Field plane = clientClass.getDeclaredField("dw"); plane.setAccessible(true);
        int planeValue = plane.getInt(null);
        if (planeValue != 0) throw new AssertionError("plane="+planeValue);

        System.out.println("PACKET81_FULL_CLIENT_DECODER_PARITY_PASS payload="+payload.length
                         +" consumed="+consumed+" displayName="+name+" plane="+planeValue);
    }

    private static Object unsafeAllocate(Class<?> c) throws Exception {
        Class<?> uc = Class.forName("sun.misc.Unsafe");
        Field f = uc.getDeclaredField("theUnsafe"); f.setAccessible(true);
        Object u = f.get(null);
        return uc.getMethod("allocateInstance", Class.class).invoke(u, c);
    }

    private static void setField(Object o, Class<?> c, String name, Object value) throws Exception {
        Field f=c.getDeclaredField(name); f.setAccessible(true); f.set(o,value);
    }
    private static void setStatic(Class<?> c, String name, Object value) throws Exception {
        Field f=c.getDeclaredField(name); f.setAccessible(true); f.set(null,value);
    }
}
