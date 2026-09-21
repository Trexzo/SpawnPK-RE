package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Loopback proof of native Customize -> root63036 -> compcolors + Confirm/Cancel close lifecycle. */
public final class CompCapeCustomizationTest {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("spk-comp-v54-");Path file=dir.resolve("opensrc.properties");
        String old=System.getProperty("spk.local.accountFile");System.setProperty("spk.local.accountFile",file.toString());
        try{
            InetAddress loop=InetAddress.getByName("127.0.0.1");
            try(ServerSocket ss=new ServerSocket(0,1,loop)){
                ExecutorService ex=Executors.newSingleThreadExecutor();
                Future<?> server=ex.submit(()->{try{new LocalSession(ss.accept(),true,true).run();}catch(IOException e){throw new RuntimeException(e);}});
                try(Socket s=new Socket(loop,ss.getLocalPort())){
                    s.setSoTimeout(5000);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
                    out.write(14);out.write(7);out.flush();byte[] pre=Binary.readExactly(in,9);if((pre[8]&255)!=0)throw new AssertionError();
                    long seed=Binary.i64(Binary.readExactly(in,8),0);int[] seeds={0x01020304,0x11223344,(int)(seed>>>32),(int)seed};byte[] lp=loginPayload(seeds);out.write(16);out.write(lp.length);out.write(lp);out.flush();if((Binary.readExactly(in,3)[0]&255)!=2)throw new AssertionError();
                    IsaacCipher c2s=new IsaacCipher(seeds.clone());int[] si=seeds.clone();for(int i=0;i<4;i++)si[i]+=50;IsaacCipher s2c=new IsaacCipher(si);
                    send185(out,c2s,912);expectFixed(in,s2c,249,3);expectFixed(in,s2c,73,4);expectVarShort(in,s2c,81);expectFixed(in,s2c,110,1);expectVarShort(in,s2c,126);V5BootstrapTestSupport.expectNativeSidebarInventoryAndFountain(in,s2c);

                    send103(out,c2s,"tabitem 23063 1");byte[] inv=expectEventually53(in,s2c,3214);int slot=findItem(inv,23063);if(slot<0)throw new AssertionError("comp cape not spawned");
                    send75(out,c2s,3214,slot,23063);byte[] root=expectEventuallyFixed(in,s2c,97,2);if(Binary.u16(root,0)!=63036)throw new AssertionError("root="+Binary.u16(root,0));
                    send103(out,c2s,"compcolors 1 2 3 4 5 6");
                    send185(out,c2s,63027);
                    expectEventuallyFixed(in,s2c,219,0);

                    // Re-open and prove the exact current-client Cancel widget closes too.
                    send75(out,c2s,3214,slot,23063);
                    root=expectEventuallyFixed(in,s2c,97,2);
                    if(Binary.u16(root,0)!=63036)throw new AssertionError("reopen root="+Binary.u16(root,0));
                    send185(out,c2s,63031);
                    expectEventuallyFixed(in,s2c,219,0);
                }
                try{server.get(7,TimeUnit.SECONDS);}finally{ex.shutdownNow();}
            }
            PlayerState ps=new PlayerState();AccountStore.load(new BankState(),new EquipmentState(),new MovementState(),new PetState(),ps);
            if(!Arrays.equals(ps.compSelectors(),new int[]{1,2,3,4,5,6}))throw new AssertionError("persisted selectors="+Arrays.toString(ps.compSelectors()));
            System.out.println("V55_COMP_CAPE_CUSTOMIZATION_PASS item23063 opcode75=true nativeRoot63036=true confirm63027=close219 cancel63031=close219 compcolors=true selectors=1,2,3,4,5,6 persisted=true");
        }finally{
            if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old);
            try{Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(IOException ignored){}});}catch(IOException ignored){}
        }
    }
    private static int findItem(byte[] p,int wanted){int slots=Binary.u16(p,2),off=4;for(int i=0;i<slots;i++){int q=p[off++]&255;if(q==255)off+=4;int lo=((p[off++]&255)-128)&255,hi=p[off++]&255,id=((hi<<8)|lo)-1;if(id==wanted&&q>0)return i;}return -1;}
    private static void send185(OutputStream o,IsaacCipher c,int w)throws IOException{o.write((185+c.nextInt())&255);o.write(w>>>8);o.write(w);o.flush();}
    private static void send103(OutputStream o,IsaacCipher c,String cmd)throws IOException{byte[] b=(cmd+"\n").getBytes(StandardCharsets.ISO_8859_1);o.write((103+c.nextInt())&255);o.write(b.length);o.write(b);o.flush();}
    private static void send75(OutputStream o,IsaacCipher c,int widget,int slot,int item)throws IOException{o.write((75+c.nextInt())&255);putLEA(o,widget);putLE(o,slot);putBEA(o,item);o.flush();}
    private static void putLEA(OutputStream o,int v)throws IOException{o.write((v+128)&255);o.write(v>>>8);}private static void putLE(OutputStream o,int v)throws IOException{o.write(v);o.write(v>>>8);}private static void putBEA(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write((v+128)&255);}
    private static void expectFixed(InputStream in,IsaacCipher c,int op,int n)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);Binary.readExactly(in,n);}private static byte[] expectVarShort(InputStream in,IsaacCipher c,int op)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}
    private static byte[] expectEventuallyFixed(InputStream in,IsaacCipher c,int wanted,int len)throws IOException{for(int i=0;i<50;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==wanted)return Binary.readExactly(in,len);skip(in,op);}throw new AssertionError("s2c "+wanted);}
    private static byte[] expectEventually53(InputStream in,IsaacCipher c,int widget)throws IOException{for(int i=0;i<50;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==53){int n=((in.read()&255)<<8)|(in.read()&255);byte[] p=Binary.readExactly(in,n);if(Binary.u16(p,0)==widget)return p;}else skip(in,op);}throw new AssertionError("53 widget="+widget);}
    private static void skip(InputStream in,int op)throws IOException{switch(op){case 81:case 53:case 65:case 126:{int n=((in.read()&255)<<8)|(in.read()&255);Binary.readExactly(in,n);break;}case 36:case 71:Binary.readExactly(in,3);break;case 134:Binary.readExactly(in,6);break;case 110:case 106:Binary.readExactly(in,1);break;case 248:Binary.readExactly(in,4);break;case 97:Binary.readExactly(in,2);break;case 219:case 27:break;default:throw new AssertionError("unexpected s2c "+op);}}
    private static byte[] loginPayload(int[] seeds)throws IOException{ByteArrayOutputStream inner=new ByteArrayOutputStream();inner.write(10);for(int v:seeds)put32(inner,v);put32(inner,748878668);put32(inner,307);nl(inner,"opensrc");nl(inner,"localpass");nl(inner,"LOCAL-DEVICE");nl(inner,"LOCAL-CLIENT");byte[] raw=inner.toByteArray();ByteArrayOutputStream p=new ByteArrayOutputStream();p.write(255);p.write(317>>>8);p.write(317);p.write(0);for(int i=0;i<9;i++)put32(p,0);p.write(raw.length);p.write(raw);return p.toByteArray();}
    private static void put32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);}private static void nl(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.ISO_8859_1));o.write(10);}
}
