package spk.local;

import java.lang.reflect.*;
import java.util.*;

/** Exact pinned-client proof of packet65 existing-NPC type0 mask + 0x40 single-hit/HP transport. */
public final class CombatNpc65HitClientParityTest {
    public static void main(String[] args)throws Exception{
        Class<?> cc=Class.forName("rs.Client"), pc=Class.forName("rs.a.k"), nc=Class.forName("rs.a.j");
        Class<?> dc=Class.forName("rs.d.d"), lc=Class.forName("rs.t.a.b"), bc=Class.forName("rs.x.e");
        installDefs(dc,lc,new int[]{1488});
        Object client=unsafeAllocate(cc);setField(client,cc,"p",Class.forName("rs.eventbus.EventBus").getConstructor().newInstance());
        Object player=pc.getConstructor().newInstance();int[] pk=(int[])pc.getField("k").get(player),pl=(int[])pc.getField("l").get(player);pk[0]=55;pl[0]=55;
        setStatic(cc,"eR",player);setStaticInt(cc,"ff",1);
        Object npcs=Array.newInstance(nc,16384);setField(client,cc,"cA",npcs);setField(client,cc,"cC",new int[16384]);setField(client,cc,"jO",new int[16384]);setField(client,cc,"kx",new int[16384]);setFieldInt(client,cc,"cB",0);setFieldInt(client,cc,"jN",0);setFieldInt(client,cc,"kw",0);
        Method decode=cc.getDeclaredMethod("a",bc,int.class);decode.setAccessible(true);
        NpcEntity dummy=new NpcEntity(101,1488,57,55);
        decode(client,decode,bc,NpcSyncEncoder.initial(Collections.singletonList(dummy),55,55));
        Object actor=Array.get(npcs,101);if(actor==null)throw new AssertionError("dummy missing");
        byte[] hit=NpcSyncEncoder.encode(Collections.singletonList(NpcSyncEncoder.Update.mask(dummy,NpcSyncEncoder.Mask.singleHit(7,0,93,100))),Collections.emptyList(),0,0);
        decode(client,decode,bc,hit);
        int cur=nc.getField("M").getInt(actor), max=nc.getField("N").getInt(actor);
        if(cur!=93||max!=100)throw new AssertionError("hp="+cur+"/"+max);
        System.out.println("V56_NPC65_HIT_CLIENT_PARITY_PASS existingType0Mask=true mask=0x40 damageFixture=7 hp=93/100 bytes="+hit.length);
    }
    private static void decode(Object c,Method m,Class<?> bc,byte[] p)throws Exception{Object b=bc.getConstructor(byte[].class).newInstance((Object)p);m.invoke(c,b,p.length);int used=bc.getField("h").getInt(b);if(used!=p.length)throw new AssertionError("used="+used+" len="+p.length);}
    private static void installDefs(Class<?> dc,Class<?> lc,int[] ids)throws Exception{Object loader=lc.getConstructor().newInstance();Object trove=lc.getMethod("f").invoke(loader);Method put=trove.getClass().getMethod("a",int.class,Object.class);for(int id:ids){Object d=dc.getConstructor().newInstance();dc.getField("x").setLong(d,id);dc.getField("L").set(d,new int[]{30000});dc.getField("o").set(d,"npc"+id);dc.getField("p").set(d,new String[]{"Attack",null,null,null,null});dc.getField("r").setByte(d,(byte)1);put.invoke(trove,id,d);}setStatic(dc,"c",loader);}
    private static Object unsafeAllocate(Class<?> c)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);Object x=f.get(null);return u.getMethod("allocateInstance",Class.class).invoke(x,c);}
    private static void setField(Object o,Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}private static void setStatic(Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(null,v);}private static void setStaticInt(Class<?> c,String n,int v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.setInt(null,v);}private static void setFieldInt(Object o,Class<?> c,String n,int v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.setInt(o,v);}
}
