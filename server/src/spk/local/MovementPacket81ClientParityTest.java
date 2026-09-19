package spk.local;

import java.lang.reflect.*;

/** Offline proof that v1 walk/run packet-81 payloads are accepted by the pinned client's full decoder. */
public final class MovementPacket81ClientParityTest {
    public static void main(String[] args) throws Exception {
        Class<?> clientClass = Class.forName("rs.Client");
        Class<?> playerClass = Class.forName("rs.a.k");
        Class<?> actorClass = Class.forName("rs.a.c");
        Class<?> bufferClass = Class.forName("rs.x.e");
        Object client = unsafeAllocate(clientClass);
        Object player = playerClass.getConstructor().newInstance();
        Object players = Array.newInstance(playerClass, 2048);
        Array.set(players, 2047, player);
        setStatic(clientClass,"do",players); setStatic(clientClass,"eR",player);
        setField(client,clientClass,"kx",new int[2048]);
        setField(client,clientClass,"ky",Array.newInstance(bufferClass,2048));
        setField(client,clientClass,"kv",new int[2048]);
        setField(client,clientClass,"jO",new int[2048]);
        Method decode=clientClass.getDeclaredMethod("b",int.class,bufferClass); decode.setAccessible(true);

        decode(client, bufferClass, decode, BootstrapPackets.player81TeleportWithAppearance(0,55,55,"localtest"));
        assertHead(actorClass,player,55,55,"bootstrap");
        decode(client, bufferClass, decode, BootstrapPackets.player81WalkStep(4)); // east
        assertHead(actorClass,player,56,55,"walk-east");
        decode(client, bufferClass, decode, BootstrapPackets.player81WalkStep(1)); // north
        assertHead(actorClass,player,56,56,"walk-north");
        decode(client, bufferClass, decode, BootstrapPackets.player81RunSteps(7,6)); // SE then S
        assertHead(actorClass,player,57,54,"run-se-s");

        System.out.println("M5_PACKET81_CLIENT_PARITY_PASS bootstrap=55,55 walk=56,55->56,56 run=57,54");
    }
    private static void decode(Object client, Class<?> bufferClass, Method decode, byte[] payload) throws Exception {
        Object b=bufferClass.getConstructor(byte[].class).newInstance((Object)payload);
        decode.invoke(client,payload.length,b);
        int consumed=bufferClass.getField("h").getInt(b);
        if (consumed!=payload.length) throw new AssertionError("consumed="+consumed+" len="+payload.length);
    }
    private static void assertHead(Class<?> actorClass,Object player,int x,int y,String phase) throws Exception {
        int[] xs=(int[])actorClass.getField("k").get(player);
        int[] ys=(int[])actorClass.getField("l").get(player);
        if (xs[0]!=x || ys[0]!=y) throw new AssertionError(phase+" head="+xs[0]+","+ys[0]+" expected="+x+","+y);
    }
    private static Object unsafeAllocate(Class<?> c) throws Exception {
        Class<?> uc=Class.forName("sun.misc.Unsafe"); Field f=uc.getDeclaredField("theUnsafe"); f.setAccessible(true);
        Object u=f.get(null); return uc.getMethod("allocateInstance",Class.class).invoke(u,c);
    }
    private static void setField(Object o,Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private static void setStatic(Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(null,v);}
}
