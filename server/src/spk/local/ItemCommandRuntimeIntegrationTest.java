package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Loopback v4.1 proof: generic ::item command -> visible inventory packet -> movement remains aligned. */
public final class ItemCommandRuntimeIntegrationTest {
    public static void main(String[] args) throws Exception {
        InetAddress loop=InetAddress.getByName("127.0.0.1");
        try(ServerSocket ss=new ServerSocket(0,1,loop)){
            ExecutorService ex=Executors.newSingleThreadExecutor();
            Future<?> server=ex.submit(()->{try{new LocalSession(ss.accept(),true,true).run();}catch(IOException e){throw new RuntimeException(e);}});
            try(Socket s=new Socket(loop,ss.getLocalPort())){
                s.setSoTimeout(5000);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
                out.write(14);out.write(7);out.flush();byte[] pre=Binary.readExactly(in,9);if((pre[8]&255)!=0)throw new AssertionError();
                long seed=Binary.i64(Binary.readExactly(in,8),0);int[] seeds={0x01020304,0x11223344,(int)(seed>>>32),(int)seed};
                byte[] lp=loginPayload(seeds);out.write(16);out.write(lp.length);out.write(lp);out.flush();if((Binary.readExactly(in,3)[0]&255)!=2)throw new AssertionError();
                IsaacCipher c2s=new IsaacCipher(seeds.clone());int[] si=seeds.clone();for(int i=0;i<4;i++)si[i]+=50;IsaacCipher s2c=new IsaacCipher(si);
                send185(out,c2s,912);
                expectFixed(in,s2c,249,3);expectFixed(in,s2c,73,4);expectVarShort(in,s2c,81);expectFixed(in,s2c,110,1);expectVarShort(in,s2c,126);
                V5BootstrapTestSupport.expectNativeSidebarInventoryAndFountain(in,s2c);

                send103(out,c2s,"item 28536 1");
                byte[] inv=expectEventuallyVarShort(in,s2c,53);
                if(Binary.u16(inv,0)!=3214)throw new AssertionError("widget="+Binary.u16(inv,0));
                int[] slot0=itemAt53(inv,0);
                if(slot0[0]!=28536||slot0[1]!=1)throw new AssertionError("spawn="+Arrays.toString(slot0));

                sendVarByte(out,c2s,164,walkBody(3088,3495,false));
                expectEventually81(in,s2c,BootstrapPackets.player81WalkStep(4));
            }
            try{server.get(2,TimeUnit.SECONDS);}catch(Exception ignored){}ex.shutdownNow();
        }
        System.out.println("V5_ITEM_COMMAND_RUNTIME_PASS command=item_28536_1 inventory3214=true movementAfterSpawn=true aligned=true");
    }
    private static int[] itemAt53(byte[] p,int wanted){int slots=Binary.u16(p,2),off=4;for(int i=0;i<slots;i++){int q=p[off++]&255;if(q==255){int p0=p[off++]&255,p1=p[off++]&255,p2=p[off++]&255,p3=p[off++]&255;q=(p1<<24)|(p0<<16)|(p3<<8)|p2;}int lo=((p[off++]&255)-128)&255,hi=p[off++]&255,id=((hi<<8)|lo)-1;if(i==wanted)return new int[]{id,q};}throw new AssertionError();}
    private static void send185(OutputStream o,IsaacCipher c,int w)throws IOException{o.write((185+c.nextInt())&255);o.write(w>>>8);o.write(w);o.flush();}
    private static void send103(OutputStream o,IsaacCipher c,String cmd)throws IOException{byte[] b=(cmd+"\n").getBytes(StandardCharsets.ISO_8859_1);o.write((103+c.nextInt())&255);o.write(b.length);o.write(b);o.flush();}
    private static void sendVarByte(OutputStream o,IsaacCipher c,int op,byte[] b)throws IOException{o.write((op+c.nextInt())&255);o.write(b.length);o.write(b);o.flush();}
    private static byte[] walkBody(int x,int y,boolean run)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write((x+128)&255);b.write(x>>>8);b.write(y);b.write(y>>>8);b.write(run?255:0);return b.toByteArray();}
    private static void expectFixed(InputStream in,IsaacCipher c,int op,int n)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);Binary.readExactly(in,n);}
    private static byte[] expectVarShort(InputStream in,IsaacCipher c,int op)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}
    private static byte[] expectEventuallyVarShort(InputStream in,IsaacCipher c,int wanted)throws IOException{for(int i=0;i<20;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==wanted){int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}skip(in,op);}throw new AssertionError("wanted var "+wanted);}
    private static void expectEventually81(InputStream in,IsaacCipher c,byte[] wanted)throws IOException{for(int i=0;i<20;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==81){int n=((in.read()&255)<<8)|(in.read()&255);byte[] p=Binary.readExactly(in,n);if(Arrays.equals(p,wanted))return;}else skip(in,op);}throw new AssertionError("81");}
    private static void skip(InputStream in,int op)throws IOException{switch(op){case 81:case 53:case 65:case 126:{int n=((in.read()&255)<<8)|(in.read()&255);Binary.readExactly(in,n);break;}case 36:case 71:Binary.readExactly(in,3);break;case 110:case 106:Binary.readExactly(in,1);break;case 248:Binary.readExactly(in,4);break;case 219:case 27:break;default:throw new AssertionError("unexpected s2c "+op);}}
    private static byte[] loginPayload(int[] seeds)throws IOException{ByteArrayOutputStream inner=new ByteArrayOutputStream();inner.write(10);for(int v:seeds)put32(inner,v);put32(inner,748878668);put32(inner,307);nl(inner,"local");nl(inner,"localpass");nl(inner,"LOCAL-DEVICE");nl(inner,"LOCAL-CLIENT");byte[] raw=inner.toByteArray();ByteArrayOutputStream p=new ByteArrayOutputStream();p.write(255);p.write(317>>>8);p.write(317);p.write(0);for(int i=0;i<9;i++)put32(p,0);p.write(raw.length);p.write(raw);return p.toByteArray();}
    private static void put32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);}private static void nl(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.ISO_8859_1));o.write(10);}
}
