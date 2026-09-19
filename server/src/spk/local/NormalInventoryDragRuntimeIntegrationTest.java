package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Loopback regression for the live WORLD-R7 lower-inventory equipment failure:
 * withdraw item -> close bank -> opcode214 move 3214 slot 0 to slot 26 -> opcode41
 * Equip from slot 26.  The authoritative inventory must follow the visual drag.
 */
public final class NormalInventoryDragRuntimeIntegrationTest {
    public static void main(String[] args) throws Exception {
        InetAddress loop=InetAddress.getByName("127.0.0.1");
        try(ServerSocket ss=new ServerSocket(0,1,loop)){
            ExecutorService ex=Executors.newSingleThreadExecutor();
            Future<?> server=ex.submit(()->{try{new LocalSession(ss.accept(),true,true).run();}catch(IOException e){throw new RuntimeException(e);}});
            try(Socket s=new Socket(loop,ss.getLocalPort())){
                s.setSoTimeout(5000); InputStream in=s.getInputStream(); OutputStream out=s.getOutputStream();
                out.write(14); out.write(7); out.flush(); byte[] pre=Binary.readExactly(in,9); if((pre[8]&255)!=0)throw new AssertionError();
                long seed=Binary.i64(Binary.readExactly(in,8),0); int[] seeds={0x01020304,0x11223344,(int)(seed>>>32),(int)seed};
                byte[] lp=loginPayload(seeds); out.write(16); out.write(lp.length); out.write(lp); out.flush(); if((Binary.readExactly(in,3)[0]&255)!=2)throw new AssertionError();
                IsaacCipher c2s=new IsaacCipher(seeds.clone()); int[] si=seeds.clone(); for(int i=0;i<4;i++)si[i]+=50; IsaacCipher s2c=new IsaacCipher(si);
                send185(out,c2s,912);
                expectFixed(in,s2c,249,3); expectFixed(in,s2c,73,4); expectVarShort(in,s2c,81); expectFixed(in,s2c,110,1); expectVarShort(in,s2c,126);
                V5BootstrapTestSupport.expectNativeSidebarInventoryAndFountain(in,s2c);

                // Open synthetic bank and withdraw whip from seeded bank slot 5 -> inv slot 0.
                send132(out,c2s,3088,26972,3495); expectEventuallyFixed(in,s2c,248,4); expectEventuallyVarShort(in,s2c,53); expectEventuallyVarShort(in,s2c,53);
                send145(out,c2s,5382,5,4151); expectEventuallyVarShort(in,s2c,53); byte[] bankInv=expectEventuallyVarShort(in,s2c,53);
                require(itemAt53(bankInv,0)[0]==4151,"whip not withdrawn to slot0");
                send0(out,c2s,130); byte[] normal=expectEventuallyWidget53(in,s2c,3214);
                require(itemAt53(normal,0)[0]==4151,"normal inventory slot0 missing whip after close");

                // The exact live failure: ordinary inventory drag to a bottom-row slot while bank is closed.
                send214(out,c2s,3214,0,0,26);
                byte[] moved=expectEventuallyWidget53(in,s2c,3214);
                require(itemAt53(moved,0)[0]<0,"slot0 not cleared by normal inventory drag");
                require(itemAt53(moved,26)[0]==4151,"slot26 missing whip after normal inventory drag");

                // Equip from the visual/server slot 26. Previous Bloodrend must return to exactly 26.
                send41(out,c2s,4151,26,3214);
                byte[] afterEquip=expectEventuallyWidget53(in,s2c,3214);
                require(itemAt53(afterEquip,26)[0]==EquipmentState.BLOODREND_ID,
                    "Bloodrend did not return to clicked lower slot 26");
                EquipmentState eq=new EquipmentState(); eq.setWeapon(4151);
                byte[] appearance=expectEventuallyVarShort(in,s2c,81);
                byte[] expected=BootstrapPackets.player81AppearanceOnly("local",eq.appearanceItems(),new PlayerState());
                require(Arrays.equals(appearance,expected),"appearance swap mismatch after bottom-row equip");
            }
            try{server.get(2,TimeUnit.SECONDS);}catch(Exception ignored){} ex.shutdownNow();
        }
        System.out.println("V561_NORMAL_INVENTORY_DRAG_RUNTIME_PASS opcode214_widget3214_bankClosed=true move0to26=true opcode41_slot26=true displacedBloodrendReturns26=true WORLD_R7_coexists=true aligned=true");
    }

    private static int[] itemAt53(byte[] p,int wanted){int slots=Binary.u16(p,2),off=4;for(int i=0;i<slots;i++){int q=p[off++]&255;if(q==255){int p0=p[off++]&255,p1=p[off++]&255,p2=p[off++]&255,p3=p[off++]&255;q=(p1<<24)|(p0<<16)|(p3<<8)|p2;}int lo=((p[off++]&255)-128)&255,hi=p[off++]&255,id=((hi<<8)|lo)-1;if(i==wanted)return new int[]{id,q};}throw new AssertionError();}
    private static void send0(OutputStream o,IsaacCipher c,int op)throws IOException{o.write((op+c.nextInt())&255);o.flush();}
    private static void send185(OutputStream o,IsaacCipher c,int w)throws IOException{o.write((185+c.nextInt())&255);o.write(w>>>8);o.write(w);o.flush();}
    private static void send132(OutputStream o,IsaacCipher c,int x,int id,int y)throws IOException{o.write((132+c.nextInt())&255);o.write((x+128)&255);o.write(x>>>8);o.write(id>>>8);o.write(id);o.write(y>>>8);o.write((y+128)&255);o.flush();}
    private static void send145(OutputStream o,IsaacCipher c,int w,int sl,int item)throws IOException{o.write((145+c.nextInt())&255);putBEA(o,w);putBEA(o,sl);putBEA(o,item);o.flush();}
    private static void send41(OutputStream o,IsaacCipher c,int item,int slot,int widget)throws IOException{o.write((41+c.nextInt())&255);o.write(item>>>8);o.write(item);putBEA(o,slot);putBEA(o,widget);o.flush();}
    private static void send214(OutputStream o,IsaacCipher c,int widget,int mode,int source,int dest)throws IOException{
        o.write((214+c.nextInt())&255); putLEA(o,widget); o.write((-mode)&255); putLEA(o,source); putLE(o,dest); o.flush();
    }
    private static void putBEA(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write((v+128)&255);}
    private static void putLEA(OutputStream o,int v)throws IOException{o.write((v+128)&255);o.write(v>>>8);}
    private static void putLE(OutputStream o,int v)throws IOException{o.write(v);o.write(v>>>8);}
    private static void expectFixed(InputStream in,IsaacCipher c,int op,int n)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);Binary.readExactly(in,n);}
    private static byte[] expectVarShort(InputStream in,IsaacCipher c,int op)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}
    private static byte[] expectEventuallyFixed(InputStream in,IsaacCipher c,int wanted,int len)throws IOException{for(int i=0;i<40;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==wanted)return Binary.readExactly(in,len);skip(in,op);}throw new AssertionError("wanted fixed "+wanted);}
    private static byte[] expectEventuallyVarShort(InputStream in,IsaacCipher c,int wanted)throws IOException{for(int i=0;i<40;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==wanted){int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}skip(in,op);}throw new AssertionError("wanted var "+wanted);}
    private static byte[] expectEventuallyWidget53(InputStream in,IsaacCipher c,int widget)throws IOException{for(int i=0;i<60;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==53){int n=((in.read()&255)<<8)|(in.read()&255);byte[] p=Binary.readExactly(in,n);if(Binary.u16(p,0)==widget)return p;}else skip(in,op);}throw new AssertionError("wanted widget53 "+widget);}
    private static void skip(InputStream in,int op)throws IOException{switch(op){case 81:case 53:case 65:case 126:{int n=((in.read()&255)<<8)|(in.read()&255);Binary.readExactly(in,n);break;}case 36:case 71:Binary.readExactly(in,3);break;case 110:case 106:Binary.readExactly(in,1);break;case 248:Binary.readExactly(in,4);break;case 219:case 27:break;default:throw new AssertionError("unexpected s2c "+op);}}
    private static byte[] loginPayload(int[] seeds)throws IOException{ByteArrayOutputStream inner=new ByteArrayOutputStream();inner.write(10);for(int v:seeds)put32(inner,v);put32(inner,748878668);put32(inner,307);nl(inner,"local");nl(inner,"localpass");nl(inner,"LOCAL-DEVICE");nl(inner,"LOCAL-CLIENT");byte[] raw=inner.toByteArray();ByteArrayOutputStream p=new ByteArrayOutputStream();p.write(255);p.write(317>>>8);p.write(317);p.write(0);for(int i=0;i<9;i++)put32(p,0);p.write(raw.length);p.write(raw);return p.toByteArray();}
    private static void put32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);} private static void nl(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.ISO_8859_1));o.write(10);}
    private static void require(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
}
