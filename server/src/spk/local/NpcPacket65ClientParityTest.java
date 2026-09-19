package spk.local;

import java.lang.reflect.*;

/** Pinned-client proof for the minimal packet-65 NPC spawn used by v4. */
public final class NpcPacket65ClientParityTest {
    public static void main(String[] args) throws Exception {
        Class<?> clientClass = Class.forName("rs.Client");
        Class<?> playerClass = Class.forName("rs.a.k");
        Class<?> npcClass = Class.forName("rs.a.j");
        Class<?> defClass = Class.forName("rs.d.d");
        Class<?> loaderClass = Class.forName("rs.t.a.b");
        Class<?> bufferClass = Class.forName("rs.x.e");

        // Install one synthetic definition entry into the client's own custom-NPC loader.
        Object loader = loaderClass.getConstructor().newInstance();
        Object def = defClass.getConstructor().newInstance();
        defClass.getField("x").setLong(def, 1799L);
        defClass.getField("L").set(def, new int[]{32999});
        defClass.getField("o").set(def, "<col=00ffff>Blood fountain</col>");
        defClass.getField("p").set(def, new String[5]);
        defClass.getField("r").setByte(def, (byte)1);
        Object trove = loaderClass.getMethod("f").invoke(loader);
        Method put = trove.getClass().getMethod("a", int.class, Object.class);
        put.invoke(trove, 1799, def);
        setStatic(defClass, "c", loader);

        Object client = unsafeAllocate(clientClass);
        Object eventBus = Class.forName("rs.eventbus.EventBus").getConstructor().newInstance();
        setField(client, clientClass, "p", eventBus);
        Object player = playerClass.getConstructor().newInstance();
        int[] pk=(int[])playerClass.getField("k").get(player);
        int[] pl=(int[])playerClass.getField("l").get(player);
        pk[0]=55; pl[0]=55;
        setStatic(clientClass, "eR", player);
        setStaticInt(clientClass, "ff", 1);

        Object npcs = Array.newInstance(npcClass, 16384);
        setField(client, clientClass, "cA", npcs);
        setField(client, clientClass, "cC", new int[16384]);
        setField(client, clientClass, "jO", new int[16384]);
        setField(client, clientClass, "kx", new int[16384]);
        setFieldInt(client, clientClass, "cB", 0);
        setFieldInt(client, clientClass, "jN", 0);
        setFieldInt(client, clientClass, "kw", 0);

        byte[] payload=BootstrapPackets.npc65SingleSpawn(1,1799,3,3);
        Object buffer=bufferClass.getConstructor(byte[].class).newInstance((Object)payload);
        Method decode=clientClass.getDeclaredMethod("a", bufferClass, int.class);
        decode.setAccessible(true);
        decode.invoke(client, buffer, payload.length);

        int consumed=bufferClass.getField("h").getInt(buffer);
        int count=getFieldInt(client,clientClass,"cB");
        int[] indices=(int[])getField(client,clientClass,"cC");
        Object npc=Array.get(npcs,1);
        if(consumed!=payload.length)throw new AssertionError("consumed="+consumed+" len="+payload.length);
        if(count!=1 || indices[0]!=1 || npc==null)throw new AssertionError("npc list count="+count+" idx="+indices[0]);
        Object parsedDef=npcClass.getField("aG").get(npc);
        long parsedId=defClass.getField("x").getLong(parsedDef);
        int[] nk=(int[])npcClass.getField("k").get(npc);
        int[] nl=(int[])npcClass.getField("l").get(npc);
        if(parsedId!=1799L)throw new AssertionError("definition="+parsedId);
        if(nk[0]!=58 || nl[0]!=58)throw new AssertionError("npcLocal="+nk[0]+","+nl[0]);

        System.out.println("V4_NPC65_CLIENT_PARITY_PASS npcIndex=1 definition=1799 model=32999"
                         + " local=58,58 world=3090,3498 payload="+payload.length+" consumed="+consumed);
    }

    private static Object unsafeAllocate(Class<?> c)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);Object x=f.get(null);return u.getMethod("allocateInstance",Class.class).invoke(x,c);}
    private static void setField(Object o,Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}    
    private static Object getField(Object o,Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}    
    private static void setFieldInt(Object o,Class<?> c,String n,int v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.setInt(o,v);}    
    private static int getFieldInt(Object o,Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.getInt(o);}    
    private static void setStatic(Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(null,v);}    
    private static void setStaticInt(Class<?> c,String n,int v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.setInt(null,v);}    
}
