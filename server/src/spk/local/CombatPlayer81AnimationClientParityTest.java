package spk.local;

import java.lang.reflect.*;

/** Exact pinned-client proof of packet81 local-player mask 0x08 animation transport. */
public final class CombatPlayer81AnimationClientParityTest {
    public static void main(String[] args)throws Exception{
        Class<?> cc=Class.forName("rs.Client"), pc=Class.forName("rs.a.k"), bc=Class.forName("rs.x.e"), ac=Class.forName("rs.d.a");
        installAnimations(ac,1000);
        Object client=unsafeAllocate(cc), player=pc.getConstructor().newInstance();
        Object players=Array.newInstance(pc,2048);Array.set(players,2047,player);
        setStatic(cc,"do",players);setStatic(cc,"eR",player);
        setField(client,cc,"kx",new int[2048]);setField(client,cc,"ky",Array.newInstance(bc,2048));setField(client,cc,"kv",new int[2048]);setField(client,cc,"jO",new int[2048]);
        Method decode=cc.getDeclaredMethod("b",int.class,bc);decode.setAccessible(true);
        byte[] payload=CombatSync.player81AnimationOnly(808);
        Object b=bc.getConstructor(byte[].class).newInstance((Object)payload);
        decode.invoke(client,payload.length,b);
        int used=bc.getField("h").getInt(b);if(used!=payload.length)throw new AssertionError("used="+used+" len="+payload.length);
        int f=pc.getField("F").getInt(player);if(f!=808)throw new AssertionError("animation F="+f);
        int i=pc.getField("I").getInt(player);if(i!=0)throw new AssertionError("delay I="+i);
        System.out.println("V56_PLAYER81_ANIMATION_CLIENT_PARITY_PASS mask=0x08 animation=808 delay=0 bytes="+payload.length);
    }
    private static void installAnimations(Class<?> ac,int n)throws Exception{
        Object arr=Array.newInstance(ac,n);for(int i=0;i<n;i++)Array.set(arr,i,ac.getConstructor().newInstance());
        setStatic(ac,"a",arr);setStatic(ac,"b",arr);setStatic(ac,"c",arr);
    }
    private static Object unsafeAllocate(Class<?> c)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);Object x=f.get(null);return u.getMethod("allocateInstance",Class.class).invoke(x,c);}
    private static void setField(Object o,Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}private static void setStatic(Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(null,v);}
}
