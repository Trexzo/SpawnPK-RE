package spk.local;

import java.lang.reflect.*;
import java.util.*;

/**
 * Exact pinned-client proof for the v5.3 NPC substrate: multiple initial NPCs,
 * masked pet spawn with interaction target, movement, and removal.
 */
public final class NpcPacket65DynamicClientParityTest {
    public static void main(String[] args)throws Exception{
        Class<?> cc=Class.forName("rs.Client"), pc=Class.forName("rs.a.k"), nc=Class.forName("rs.a.j");
        Class<?> dc=Class.forName("rs.d.d"), lc=Class.forName("rs.t.a.b"), bc=Class.forName("rs.x.e");
        installDefs(dc,lc,new int[]{1799,1488,1489,3098});

        Object client=unsafeAllocate(cc); setField(client,cc,"p",Class.forName("rs.eventbus.EventBus").getConstructor().newInstance());
        Object player=pc.getConstructor().newInstance(); int[] pk=(int[])pc.getField("k").get(player), pl=(int[])pc.getField("l").get(player); pk[0]=55;pl[0]=55;
        setStatic(cc,"eR",player); setStaticInt(cc,"ff",1);
        Object npcs=Array.newInstance(nc,16384); setField(client,cc,"cA",npcs); setField(client,cc,"cC",new int[16384]);setField(client,cc,"jO",new int[16384]);setField(client,cc,"kx",new int[16384]);setFieldInt(client,cc,"cB",0);setFieldInt(client,cc,"jN",0);setFieldInt(client,cc,"kw",0);
        Method decode=cc.getDeclaredMethod("a",bc,int.class);decode.setAccessible(true);

        NpcEntity f=new NpcEntity(1,1799,58,58), pvp=new NpcEntity(2,1488,57,55), pvm=new NpcEntity(3,1489,57,57);
        byte[] initial=NpcSyncEncoder.initial(Arrays.asList(f,pvp,pvm),55,55); decode(client,decode,bc,initial);
        assertActive(client,cc,3,1,2,3);

        NpcEntity pet=new NpcEntity(4,3098,54,55,true,20776,1);
        ArrayList<NpcSyncEncoder.Update> old=new ArrayList<>(); old.add(NpcSyncEncoder.Update.retain(f));old.add(NpcSyncEncoder.Update.retain(pvp));old.add(NpcSyncEncoder.Update.retain(pvm));
        byte[] spawn=NpcSyncEncoder.encode(old,Collections.singletonList(pet),55,55); decode(client,decode,bc,spawn);
        assertActive(client,cc,4,1,2,3,4);
        Object petObj=Array.get(npcs,4); if(petObj==null)throw new AssertionError("pet missing");
        Field mf=nc.getField("m"); int target=mf.getInt(petObj); if(target!=32769)throw new AssertionError("target="+target);
        long defId=dc.getField("x").getLong(nc.getField("aG").get(petObj)); if(defId!=3098)throw new AssertionError("def="+defId);
        int[] nk=(int[])nc.getField("k").get(petObj), nl=(int[])nc.getField("l").get(petObj); if(nk[0]!=54||nl[0]!=55)throw new AssertionError("pet local="+nk[0]+","+nl[0]);

        ArrayList<NpcSyncEncoder.Update> move=new ArrayList<>();move.add(NpcSyncEncoder.Update.retain(f));move.add(NpcSyncEncoder.Update.retain(pvp));move.add(NpcSyncEncoder.Update.retain(pvm));move.add(NpcSyncEncoder.Update.walk(pet,4));
        byte[] walk=NpcSyncEncoder.encode(move,Collections.emptyList(),0,0);decode(client,decode,bc,walk);
        nk=(int[])nc.getField("k").get(petObj);nl=(int[])nc.getField("l").get(petObj);if(nk[0]!=55||nl[0]!=55)throw new AssertionError("walk local="+nk[0]+","+nl[0]);

        ArrayList<NpcSyncEncoder.Update> rem=new ArrayList<>();rem.add(NpcSyncEncoder.Update.retain(f));rem.add(NpcSyncEncoder.Update.retain(pvp));rem.add(NpcSyncEncoder.Update.retain(pvm));rem.add(NpcSyncEncoder.Update.remove(pet));
        byte[] remove=NpcSyncEncoder.encode(rem,Collections.emptyList(),0,0);decode(client,decode,bc,remove);assertActive(client,cc,3,1,2,3);
        System.out.println("V53_NPC65_DYNAMIC_CLIENT_PARITY_PASS initial3=true pet3098Scene4=true ownerTarget32769=true walk=true remove=true bytes="+initial.length+","+spawn.length+","+walk.length+","+remove.length);
    }
    private static void decode(Object c,Method m,Class<?> bc,byte[] p)throws Exception{Object b=bc.getConstructor(byte[].class).newInstance((Object)p);m.invoke(c,b,p.length);int used=bc.getField("h").getInt(b);if(used!=p.length)throw new AssertionError("packet65 consumed="+used+" len="+p.length);}
    private static void assertActive(Object c,Class<?> cc,int count,int... wanted)throws Exception{int n=getInt(c,cc,"cB");if(n!=count)throw new AssertionError("count="+n+" wanted="+count);int[] a=(int[])get(c,cc,"cC");for(int i=0;i<wanted.length;i++)if(a[i]!=wanted[i])throw new AssertionError("idx["+i+"]="+a[i]);}
    private static void installDefs(Class<?> dc,Class<?> lc,int[] ids)throws Exception{Object loader=lc.getConstructor().newInstance();Object trove=lc.getMethod("f").invoke(loader);Method put=trove.getClass().getMethod("a",int.class,Object.class);for(int id:ids){Object d=dc.getConstructor().newInstance();dc.getField("x").setLong(d,id);dc.getField("L").set(d,new int[]{30000+id%1000});dc.getField("o").set(d,"npc"+id);dc.getField("p").set(d,new String[5]);dc.getField("r").setByte(d,(byte)1);put.invoke(trove,id,d);}setStatic(dc,"c",loader);}
    private static Object unsafeAllocate(Class<?> c)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);Object x=f.get(null);return u.getMethod("allocateInstance",Class.class).invoke(x,c);}
    private static void setField(Object o,Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}private static Object get(Object o,Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}private static int getInt(Object o,Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.getInt(o);}private static void setFieldInt(Object o,Class<?> c,String n,int v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.setInt(o,v);}private static void setStatic(Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(null,v);}private static void setStaticInt(Class<?> c,String n,int v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.setInt(null,v);}
}
