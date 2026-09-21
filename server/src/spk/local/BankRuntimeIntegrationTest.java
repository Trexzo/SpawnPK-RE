package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** v4 loopback proof: fixed-slot bank overlay, withdraw/store, close/reopen, then movement. */
public final class BankRuntimeIntegrationTest {
    public static void main(String[] args) throws Exception {
        InetAddress loop=InetAddress.getByName("127.0.0.1");
        try(ServerSocket ss=new ServerSocket(0,1,loop)) {
            ExecutorService ex=Executors.newSingleThreadExecutor();
            Future<?> server=ex.submit(()->{try{new LocalSession(ss.accept(),true,true).run();}catch(IOException e){throw new RuntimeException(e);}});
            try(Socket s=new Socket(loop,ss.getLocalPort())) {
                s.setSoTimeout(5000); InputStream in=s.getInputStream(); OutputStream out=s.getOutputStream();
                out.write(14);out.write(7);out.flush();
                byte[] prefix=Binary.readExactly(in,9);if((prefix[8]&255)!=0)throw new AssertionError("prelogin");
                long seed=Binary.i64(Binary.readExactly(in,8),0);
                int[] seeds={0x01020304,0x11223344,(int)(seed>>>32),(int)seed};
                byte[] lp=loginPayload(seeds);out.write(16);out.write(lp.length);out.write(lp);out.flush();
                if((Binary.readExactly(in,3)[0]&255)!=2)throw new AssertionError("login");
                IsaacCipher c2s=new IsaacCipher(seeds.clone());int[] si=seeds.clone();for(int i=0;i<4;i++)si[i]+=50;IsaacCipher s2c=new IsaacCipher(si);
                send185(out,c2s,912);expectFixed(in,s2c,249,3);expectFixed(in,s2c,73,4);expectVarShort(in,s2c,81);expectFixed(in,s2c,110,1);expectVarShort(in,s2c,126);
                V5BootstrapTestSupport.expectNativeSidebarInventoryAndFountain(in,s2c);

                // v4.1: object action is deferred until authoritative movement is adjacent.
                // Walk two east tiles toward a synthetic test bank at 3090,3495, then click it immediately.
                sendVarByte(out,c2s,164,walkBody(3089,3495,false));
                send132(out,c2s,3090,26972,3495);
                byte[] open=expectEventuallyFixed(in,s2c,248,4);
                if(!Arrays.equals(open,BootstrapPackets.interfaceOverlay248(5292,5063)))throw new AssertionError("packet248="+ClientPacketProbe.hex(open,16));
                byte[] bank=expectEventuallyVarShort(in,s2c,53), inv=expectEventuallyVarShort(in,s2c,53);
                int[] bh=header53(bank), ih=header53(inv);
                if(bh[0]!=5382||bh[1]!=352)throw new AssertionError("bank="+Arrays.toString(bh));
                if(ih[0]!=5064||ih[1]!=28)throw new AssertionError("fixed inventory="+Arrays.toString(ih));
                if(itemAt53(inv,0)[0]!=-1)throw new AssertionError("initial inventory must be empty");

                // Withdraw one coin: it must appear in bank overlay inventory 5064.
                send145(out,c2s,5382,0,995);
                bank=expectEventuallyVarShort(in,s2c,53);inv=expectEventuallyVarShort(in,s2c,53);ih=header53(inv);
                if(ih[0]!=5064||ih[1]!=28)throw new AssertionError("withdraw inventory="+Arrays.toString(ih));
                int[] coin=itemAt53(inv,0); if(coin[0]!=995||coin[1]!=1)throw new AssertionError("withdraw coin="+Arrays.toString(coin));

                // Store it back using the SAME generic action serializer but widget 5064.
                send145(out,c2s,5064,0,995);
                bank=expectEventuallyVarShort(in,s2c,53);inv=expectEventuallyVarShort(in,s2c,53);ih=header53(inv);
                if(ih[0]!=5064||ih[1]!=28)throw new AssertionError("store inventory="+Arrays.toString(ih));
                if(itemAt53(inv,0)[0]!=-1)throw new AssertionError("store must clear same inventory slot");

                send0(out,c2s,130);
                send132(out,c2s,3090,26972,3495);
                open=expectEventuallyFixed(in,s2c,248,4);bank=expectEventuallyVarShort(in,s2c,53);inv=expectEventuallyVarShort(in,s2c,53);
                if(header53(inv)[1]!=28||itemAt53(inv,0)[0]!=-1)throw new AssertionError("reopen inventory not empty");

                send0(out,c2s,130);
                sendVarByte(out,c2s,164,walkBody(3088,3495,false));
                expectEventually81(in,s2c,BootstrapPackets.player81WalkStep(3));
            }
            try{server.get(2,TimeUnit.SECONDS);}catch(Exception ignored){}ex.shutdownNow();
        }
        System.out.println("V5_BANK_RUNTIME_INTEGRATION_PASS bootstrap71+53+65 movement164->object132DeferredUntilAdjacent->248(5292,5063)->fixed53(5382/352)+fixed53(5064/28)->withdraw145->store145->close130->reopen->walk164 aligned=true");
    }
    private static int[] header53(byte[] p){return new int[]{Binary.u16(p,0),Binary.u16(p,2)};}
    private static int[] itemAt53(byte[] p,int wanted){
        int slots=Binary.u16(p,2), off=4; if(wanted<0||wanted>=slots)throw new AssertionError("slot");
        for(int i=0;i<slots;i++){
            int q=p[off++]&255; if(q==255){int p0=p[off++]&255,p1=p[off++]&255,p2=p[off++]&255,p3=p[off++]&255;q=(p1<<24)|(p0<<16)|(p3<<8)|p2;}
            int lo=((p[off++]&255)-128)&255, hi=p[off++]&255; int id=((hi<<8)|lo)-1;
            if(i==wanted)return new int[]{id,q};
        }
        throw new AssertionError("unreachable");
    }
    private static void send0(OutputStream o,IsaacCipher c,int op)throws IOException{o.write((op+c.nextInt())&255);o.flush();}
    private static void send185(OutputStream o,IsaacCipher c,int w)throws IOException{o.write((185+c.nextInt())&255);o.write(w>>>8);o.write(w);o.flush();}
    private static void send132(OutputStream o,IsaacCipher c,int x,int id,int y)throws IOException{o.write((132+c.nextInt())&255);o.write((x+128)&255);o.write(x>>>8);o.write(id>>>8);o.write(id);o.write(y>>>8);o.write((y+128)&255);o.flush();}
    private static void send145(OutputStream o,IsaacCipher c,int w,int s,int i)throws IOException{o.write((145+c.nextInt())&255);putBEA(o,w);putBEA(o,s);putBEA(o,i);o.flush();}
    private static void putBEA(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write((v+128)&255);}
    private static void sendVarByte(OutputStream o,IsaacCipher c,int op,byte[] body)throws IOException{o.write((op+c.nextInt())&255);o.write(body.length);o.write(body);o.flush();}
    private static byte[] walkBody(int x,int y,boolean run)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write((x+128)&255);b.write(x>>>8);b.write(y);b.write(y>>>8);b.write(run?255:0);return b.toByteArray();}
    private static void expectFixed(InputStream in,IsaacCipher c,int op,int len)throws IOException{expectFixedBytes(in,c,op,len);}
    private static byte[] expectFixedBytes(InputStream in,IsaacCipher c,int op,int len)throws IOException{int got=((in.read()&255)-c.nextInt())&255;if(got!=op)throw new AssertionError("op="+got+" expected="+op);return Binary.readExactly(in,len);}
    private static byte[] expectVarShort(InputStream in,IsaacCipher c,int op)throws IOException{int got=((in.read()&255)-c.nextInt())&255;if(got!=op)throw new AssertionError("op="+got+" expected="+op);int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}
    private static byte[] expectEventuallyFixed(InputStream in,IsaacCipher c,int wanted,int len)throws IOException{for(int k=0;k<18;k++){int op=((in.read()&255)-c.nextInt())&255;if(op==wanted)return Binary.readExactly(in,len);skip(in,op);}throw new AssertionError("wanted="+wanted);}
    private static byte[] expectEventuallyVarShort(InputStream in,IsaacCipher c,int wanted)throws IOException{for(int k=0;k<18;k++){int op=((in.read()&255)-c.nextInt())&255;if(op==wanted){int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}skip(in,op);}throw new AssertionError("wanted="+wanted);}
    private static void skip(InputStream in,int op)throws IOException{switch(op){case 81:case 53:case 65:case 126:{int n=((in.read()&255)<<8)|(in.read()&255);Binary.readExactly(in,n);break;}case 36:Binary.readExactly(in,3);break;case 248:Binary.readExactly(in,4);break;case 219:break;default:throw new AssertionError("unexpected s2c op="+op);}}
    private static void expectEventually81(InputStream in,IsaacCipher c,byte[] expected)throws IOException{for(int k=0;k<18;k++){int op=((in.read()&255)-c.nextInt())&255;if(op==81){int n=((in.read()&255)<<8)|(in.read()&255);byte[] got=Binary.readExactly(in,n);if(Arrays.equals(got,expected))return;}else skip(in,op);}throw new AssertionError("81 not found");}
    private static byte[] loginPayload(int[] seeds)throws IOException{ByteArrayOutputStream inner=new ByteArrayOutputStream();inner.write(10);for(int v:seeds)put32(inner,v);put32(inner,748878668);put32(inner,307);nl(inner,"local");nl(inner,"localpass");nl(inner,"LOCAL-DEVICE");nl(inner,"LOCAL-CLIENT");byte[] raw=inner.toByteArray();ByteArrayOutputStream p=new ByteArrayOutputStream();p.write(255);p.write(317>>>8);p.write(317);p.write(0);for(int i=0;i<9;i++)put32(p,0);p.write(raw.length);p.write(raw);return p.toByteArray();}
    private static void put32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);}private static void nl(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.ISO_8859_1));o.write(10);}
}
