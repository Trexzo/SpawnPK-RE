package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Loopback v5.2 proof for the requested local workflow:
 * native/debug spawn -> Wear helmet -> Wear boots -> keep Bloodrend -> Remove boots.
 */
public final class EquipmentMultiSlotRuntimeIntegrationTest {
    public static void main(String[] args) throws Exception {
        InetAddress loop=InetAddress.getByName("127.0.0.1");
        try(ServerSocket ss=new ServerSocket(0,1,loop)){
            ExecutorService ex=Executors.newSingleThreadExecutor();
            Future<?> server=ex.submit(()->{try{new LocalSession(ss.accept(),true,true).run();}catch(IOException e){throw new RuntimeException(e);}});
            try(Socket s=new Socket(loop,ss.getLocalPort())){
                s.setSoTimeout(5000);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
                out.write(14);out.write(7);out.flush();byte[] pre=Binary.readExactly(in,9);if((pre[8]&255)!=0)throw new AssertionError();
                long seed=Binary.i64(Binary.readExactly(in,8),0);int[] seeds={0x01020304,0x11223344,(int)(seed>>>32),(int)seed};
                byte[] lp=loginPayload(seeds);out.write(16);out.write(lp.length);out.write(lp);out.flush();
                byte[] login=Binary.readExactly(in,3);if((login[0]&255)!=2||(login[1]&255)!=205||(login[2]&255)!=0)throw new AssertionError("rank triplet="+Arrays.toString(login));
                IsaacCipher c2s=new IsaacCipher(seeds.clone());int[] si=seeds.clone();for(int i=0;i<4;i++)si[i]+=50;IsaacCipher s2c=new IsaacCipher(si);
                send185(out,c2s,912);
                expectFixed(in,s2c,249,3);expectFixed(in,s2c,73,4);expectVarShort(in,s2c,81);expectFixed(in,s2c,110,1);expectVarShort(in,s2c,126);
                V5BootstrapTestSupport.expectNativeSidebarInventoryAndFountain(in,s2c);

                // Spawn requested custom items via the same opcode-103 command path the Spawn Tab uses.
                send103(out,c2s,"tabitem 27034 1");
                byte[] inv=expectEventually53(in,s2c,3214);if(itemAt53(inv,0)[0]!=27034)throw new AssertionError("helmet spawn");
                send103(out,c2s,"tabitem 27486 1");
                inv=expectEventually53(in,s2c,3214);if(itemAt53(inv,1)[0]!=27486)throw new AssertionError("boots spawn");

                // Wear Ultimate slayer helmet from inventory slot 0 (menu action 454 -> opcode41).
                send41(out,c2s,27034,0,3214);
                inv=expectEventually53(in,s2c,3214);if(itemAt53(inv,0)[0]!=-1)throw new AssertionError("helmet source not empty");
                byte[] eq53=expectEventually53(in,s2c,1688);if(itemAt53(eq53,0)[0]!=27034||itemAt53(eq53,3)[0]!=28526)throw new AssertionError("helmet equip");
                byte[] app=expectEventually81(in,s2c);assertAppearance(app,27034,28526,-1);

                // Wear Wanderer's boots from inventory slot 1, retaining helm + Bloodrend.
                send41(out,c2s,27486,1,3214);
                inv=expectEventually53(in,s2c,3214);if(itemAt53(inv,1)[0]!=-1)throw new AssertionError("boots source not empty");
                eq53=expectEventually53(in,s2c,1688);
                if(itemAt53(eq53,0)[0]!=27034||itemAt53(eq53,3)[0]!=28526||itemAt53(eq53,10)[0]!=27486)
                    throw new AssertionError("three-way equipment state");
                app=expectEventually81(in,s2c);assertAppearance(app,27034,28526,27486);

                // Remove boots from native equipment widget (first item action -> opcode145).
                send145(out,c2s,1688,10,27486);
                inv=expectEventually53(in,s2c,3214);if(itemAt53(inv,0)[0]!=27486)throw new AssertionError("boots remove inventory slot0");
                eq53=expectEventually53(in,s2c,1688);if(itemAt53(eq53,10)[0]!=-1)throw new AssertionError("boots still equipped");
                app=expectEventually81(in,s2c);assertAppearance(app,27034,28526,-1);

                sendVarByte(out,c2s,164,walkBody(3088,3495,false));
                expectEventuallySpecific81(in,s2c,BootstrapPackets.player81WalkStep(4));
            }
            try{server.get(2,TimeUnit.SECONDS);}catch(Exception ignored){}ex.shutdownNow();
        }
        System.out.println("V52_MULTI_SLOT_RUNTIME_PASS rank205=true tabitem27034+27486=true"
            + " opcode41WearHead=true opcode41WearFeet=true BloodrendRetained=true"
            + " equipment1688=true opcode145Remove=true appearance81=true movementAfterEquipment=true aligned=true");
    }

    private static void assertAppearance(byte[] p,int head,int weapon,int feet)throws Exception{
        // Parse the mask-only packet with the exact pinned client to avoid hand-decoding packet81.
        Class<?> cc=Class.forName("rs.Client"), pc=Class.forName("rs.a.k"), bc=Class.forName("rs.x.e");
        Object client=unsafeAllocate(cc), player=pc.getConstructor().newInstance(), players=java.lang.reflect.Array.newInstance(pc,2048);
        java.lang.reflect.Array.set(players,2047,player);setStatic(cc,"do",players);setStatic(cc,"eR",player);
        setField(client,cc,"kx",new int[2048]);setField(client,cc,"ky",java.lang.reflect.Array.newInstance(bc,2048));setField(client,cc,"kv",new int[2048]);setField(client,cc,"jO",new int[2048]);
        java.lang.reflect.Method m=cc.getDeclaredMethod("b",int.class,bc);m.setAccessible(true);Object b=bc.getConstructor(byte[].class).newInstance((Object)p);m.invoke(client,p.length,b);
        int[] br=(int[])pc.getField("br").get(player);
        int expectedHead = head < 0 ? 0 : 512+head;
        int expectedWeapon = weapon < 0 ? 0 : 512+weapon;
        // Appearance position 10 falls back to the default feet identity kit (256+42)
        // when no item is equipped there; packet81 does not encode "empty player feet".
        int expectedFeet = feet < 0 ? 298 : 512+feet;
        if(br[0]!=expectedHead||br[3]!=expectedWeapon||br[10]!=expectedFeet)
            throw new AssertionError("appearance head/weapon/feet="+br[0]+","+br[3]+","+br[10]
                +" expected="+expectedHead+","+expectedWeapon+","+expectedFeet);
    }
    private static Object unsafeAllocate(Class<?> c)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");java.lang.reflect.Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);Object x=f.get(null);return u.getMethod("allocateInstance",Class.class).invoke(x,c);}
    private static void setField(Object o,Class<?> c,String n,Object v)throws Exception{java.lang.reflect.Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}private static void setStatic(Class<?> c,String n,Object v)throws Exception{java.lang.reflect.Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(null,v);}
    private static int[] itemAt53(byte[] p,int wanted){int slots=Binary.u16(p,2),off=4;for(int i=0;i<slots;i++){int q=p[off++]&255;if(q==255){int p0=p[off++]&255,p1=p[off++]&255,p2=p[off++]&255,p3=p[off++]&255;q=(p1<<24)|(p0<<16)|(p3<<8)|p2;}int lo=((p[off++]&255)-128)&255,hi=p[off++]&255,id=((hi<<8)|lo)-1;if(i==wanted)return new int[]{id,q};}throw new AssertionError();}
    private static void send185(OutputStream o,IsaacCipher c,int w)throws IOException{o.write((185+c.nextInt())&255);o.write(w>>>8);o.write(w);o.flush();}
    private static void send103(OutputStream o,IsaacCipher c,String cmd)throws IOException{byte[] b=(cmd+"\n").getBytes(StandardCharsets.ISO_8859_1);o.write((103+c.nextInt())&255);o.write(b.length);o.write(b);o.flush();}
    private static void send41(OutputStream o,IsaacCipher c,int item,int slot,int widget)throws IOException{o.write((41+c.nextInt())&255);o.write(item>>>8);o.write(item);putBEA(o,slot);putBEA(o,widget);o.flush();}
    private static void send145(OutputStream o,IsaacCipher c,int w,int sl,int item)throws IOException{o.write((145+c.nextInt())&255);putBEA(o,w);putBEA(o,sl);putBEA(o,item);o.flush();}
    private static void putBEA(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write((v+128)&255);}
    private static void sendVarByte(OutputStream o,IsaacCipher c,int op,byte[] b)throws IOException{o.write((op+c.nextInt())&255);o.write(b.length);o.write(b);o.flush();}
    private static byte[] walkBody(int x,int y,boolean run)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write((x+128)&255);b.write(x>>>8);b.write(y);b.write(y>>>8);b.write(run?255:0);return b.toByteArray();}
    private static void expectFixed(InputStream in,IsaacCipher c,int op,int n)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);Binary.readExactly(in,n);}
    private static byte[] expectVarShort(InputStream in,IsaacCipher c,int op)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}
    private static byte[] expectEventually53(InputStream in,IsaacCipher c,int widget)throws IOException{for(int i=0;i<30;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==53){int n=((in.read()&255)<<8)|(in.read()&255);byte[] p=Binary.readExactly(in,n);if(Binary.u16(p,0)==widget)return p;}else skip(in,op);}throw new AssertionError("53 widget "+widget);}
    private static byte[] expectEventually81(InputStream in,IsaacCipher c)throws IOException{for(int i=0;i<30;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==81){int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}skip(in,op);}throw new AssertionError("81");}
    private static void expectEventuallySpecific81(InputStream in,IsaacCipher c,byte[] wanted)throws IOException{for(int i=0;i<30;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==81){int n=((in.read()&255)<<8)|(in.read()&255);byte[] p=Binary.readExactly(in,n);if(Arrays.equals(p,wanted))return;}else skip(in,op);}throw new AssertionError("specific81");}
    private static void skip(InputStream in,int op)throws IOException{switch(op){case 81:case 53:case 65:case 126:{int n=((in.read()&255)<<8)|(in.read()&255);Binary.readExactly(in,n);break;}case 36:case 71:Binary.readExactly(in,3);break;case 110:case 106:Binary.readExactly(in,1);break;case 248:Binary.readExactly(in,4);break;case 219:case 27:break;default:throw new AssertionError("unexpected s2c "+op);}}
    private static byte[] loginPayload(int[] seeds)throws IOException{ByteArrayOutputStream inner=new ByteArrayOutputStream();inner.write(10);for(int v:seeds)put32(inner,v);put32(inner,748878668);put32(inner,307);nl(inner,"local");nl(inner,"localpass");nl(inner,"LOCAL-DEVICE");nl(inner,"LOCAL-CLIENT");byte[] raw=inner.toByteArray();ByteArrayOutputStream p=new ByteArrayOutputStream();p.write(255);p.write(317>>>8);p.write(317);p.write(0);for(int i=0;i<9;i++)put32(p,0);p.write(raw.length);p.write(raw);return p.toByteArray();}
    private static void put32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);}private static void nl(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.ISO_8859_1));o.write(10);}
}
