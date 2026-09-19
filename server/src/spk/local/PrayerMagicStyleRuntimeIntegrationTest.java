package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** End-to-end socket certification for v5.10 Prayer/Magic/Combat Style activation. */
public final class PrayerMagicStyleRuntimeIntegrationTest {
    public static void main(String[] args) throws Exception {
        PrintStream originalOut=System.out;
        ByteArrayOutputStream capturedBytes=new ByteArrayOutputStream();
        PrintStream capture=new PrintStream(new TeeOutputStream(originalOut,capturedBytes),true,"UTF-8");
        System.setOut(capture);
        InetAddress loop=InetAddress.getByName("127.0.0.1");
        try(ServerSocket ss=new ServerSocket(0,1,loop)){
            ExecutorService ex=Executors.newSingleThreadExecutor();
            Future<?> server=ex.submit(()->{
                try { new LocalSession(ss.accept(),true,true).run(); }
                catch(IOException e){ throw new RuntimeException(e); }
            });
            try(Socket s=new Socket(loop,ss.getLocalPort())){
                s.setSoTimeout(5000);
                InputStream in=s.getInputStream(); OutputStream out=s.getOutputStream();
                out.write(14);out.write(7);out.flush();
                byte[] hs=Binary.readExactly(in,9); require((hs[8]&255)==0,"handshake status");
                long serverSeed=Binary.i64(Binary.readExactly(in,8),0);
                int[] seeds={0x01020304,0x11223344,(int)(serverSeed>>>32),(int)serverSeed};
                byte[] login=loginPayload(seeds);
                out.write(16);out.write(login.length);out.write(login);out.flush();
                byte[] loginReply=Binary.readExactly(in,3);require((loginReply[0]&255)==2,"login response");
                IsaacCipher c2s=new IsaacCipher(seeds.clone());
                int[] inbound=seeds.clone();for(int i=0;i<4;i++)inbound[i]+=50;
                IsaacCipher s2c=new IsaacCipher(inbound);

                // Enter the normal bootstrap exactly as the real client does.
                send185(out,c2s,912);
                expectFixed(in,s2c,249,3);
                expectFixed(in,s2c,73,4);
                expectVarShort(in,s2c,81);
                expectFixed(in,s2c,110,1);
                expectVarShort(in,s2c,126);
                V5BootstrapTestSupport.expectNativeSidebarInventoryAndFountain(in,s2c);

                // Prayer: Thick Skin exact cache mapping widget5609 -> config83.
                send185(out,c2s,5609);
                byte[] p=expectEventuallyConfig(in,s2c,83);
                require((p[2]&255)==1,"Thick Skin did not publish config83=1");

                // Combat style: Bloodrend/Scythe Chop widget785 -> varp43 value1.
                send185(out,c2s,785);
                byte[] st=expectEventuallyConfig(in,s2c,43);
                require((st[2]&255)==1,"Scythe Chop did not publish config43=1");

                // Toggle the normal prayer back off before changing books.
                send185(out,c2s,5609);
                byte[] pOff=expectEventuallyConfig(in,s2c,83);
                require((pOff[2]&255)==0,"Thick Skin did not publish config83=0");

                // Curses share many varps with Normal Prayer, so book switching republishes
                // book-specific state before the exact curse click is accepted.
                send103(out,c2s,"prayerbook curses");
                expectEventuallySidebar(in,s2c,22500,5);
                send185(out,c2s,22503); // Curses Protect Item -> config83.
                byte[] curse=expectEventuallyConfig(in,s2c,83);
                require((curse[2]&255)==1,"Curse Protect Item did not publish config83=1");

                // Give exact visible Wind Strike requirements through the existing item command route.
                send103(out,c2s,"item 556 5");
                expectEventuallyVarShort(in,s2c,53);
                send103(out,c2s,"item 558 5");
                expectEventuallyVarShort(in,s2c,53);

                // This packet arrives after bootstrap has fully completed. Scene103/def7605 is
                // present in the exact current WORLD-R7 bootstrap, so successful routing can be
                // distinguished from an absent-target rejection without inventing spell effects.
                send131(out,c2s,103,1152);
                Thread.sleep(150L);

                // Alignment proof after the spell: toggle the active curse back off and require
                // the exact config response. If C2S131 were left pending/misaligned, this fails.
                send185(out,c2s,22503);
                byte[] curseOff=expectEventuallyConfig(in,s2c,83);
                require((curseOff[2]&255)==0,"post-spell curse toggle did not publish config83=0");
            }
            try{server.get(2,TimeUnit.SECONDS);}catch(Exception ignored){}
            ex.shutdownNow();
        }
        capture.flush();
        String logs=capturedBytes.toString("UTF-8");
        require(logs.contains("V510_MAGIC_TARGET SpellTargetRequest{opcode=131,kind=NPC,spell=1152,targetIndex=103"),"continuous-loop spell route log missing");
        require(logs.contains("validation=ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Wind strike"),"Wind Strike visible-requirement acceptance missing");
        require(logs.contains("result=TARGET_NPC_VISIBLE def=7605 effect=UNIMPLEMENTED_SERVER_AUTHORITY"),"visible NPC spell routing result missing");
        System.setOut(originalOut);
        System.out.println("V510_PRAYER_MAGIC_STYLE_RUNTIME_INTEGRATION_PASS normalPrayer=5609/varp83 curses=22503/varp83 style=785/varp43=1 spell=1152/C2S131/scene103 acceptedRouter=true postSpellAligned=true");
    }

    private static void send185(OutputStream out,IsaacCipher c,int widget)throws IOException{
        out.write((185+c.nextInt())&255); out.write(widget>>>8); out.write(widget); out.flush();
    }
    private static void send103(OutputStream out,IsaacCipher c,String text)throws IOException{
        byte[] b=text.getBytes(StandardCharsets.ISO_8859_1);
        out.write((103+c.nextInt())&255);out.write(b.length+1);out.write(b);out.write(10);out.flush();
    }
    private static void send131(OutputStream out,IsaacCipher c,int scene,int spell)throws IOException{
        out.write((131+c.nextInt())&255);
        out.write((scene+128)&255);out.write(scene>>>8); // LE short-A
        out.write(spell>>>8);out.write((spell+128)&255); // BE short-A
        out.flush();
    }
    private static void expectFixed(InputStream in,IsaacCipher c,int opcode,int len)throws IOException{
        int got=((in.read()&255)-c.nextInt())&255;require(got==opcode,"expected opcode "+opcode+" got "+got);Binary.readExactly(in,len);
    }
    private static byte[] expectVarShort(InputStream in,IsaacCipher c,int opcode)throws IOException{
        int got=((in.read()&255)-c.nextInt())&255;require(got==opcode,"expected opcode "+opcode+" got "+got);return readVarShortBody(in);
    }
    private static byte[] expectEventuallyConfig(InputStream in,IsaacCipher c,int index)throws IOException{
        for(int i=0;i<40;i++){
            int op=((in.read()&255)-c.nextInt())&255;
            if(op==36){byte[] b=Binary.readExactly(in,3);int idx=(b[0]&255)|((b[1]&255)<<8);if(idx==index)return b;continue;}
            skipKnown(in,op);
        }
        throw new AssertionError("config36 index "+index+" not observed");
    }
    private static byte[] expectEventuallyVarShort(InputStream in,IsaacCipher c,int wanted)throws IOException{
        for(int i=0;i<60;i++){
            int op=((in.read()&255)-c.nextInt())&255;
            if(op==wanted)return readVarShortBody(in);
            skipKnown(in,op);
        }
        throw new AssertionError("varshort opcode "+wanted+" not observed");
    }
    private static void expectEventuallySidebar(InputStream in,IsaacCipher c,int root,int tab)throws IOException{
        byte[] expected=BootstrapPackets.sidebar71(root,tab);
        for(int i=0;i<80;i++){
            int op=((in.read()&255)-c.nextInt())&255;
            if(op==71){byte[] b=Binary.readExactly(in,3);if(java.util.Arrays.equals(b,expected))return;continue;}
            skipKnown(in,op);
        }
        throw new AssertionError("sidebar71 root="+root+" tab="+tab+" not observed");
    }
    private static void skipKnown(InputStream in,int op)throws IOException{
        switch(op){
            case 53:case 65:case 81:case 126: readVarShortBody(in); return;
            case 36: Binary.readExactly(in,3); return;
            case 71: Binary.readExactly(in,3); return;
            case 106:case 110: Binary.readExactly(in,1); return;
            case 134: Binary.readExactly(in,6); return;
            case 249: Binary.readExactly(in,3); return;
            case 73: Binary.readExactly(in,4); return;
            case 85:case 101: Binary.readExactly(in,2); return;
            case 151: Binary.readExactly(in,4); return;
            case 219:case 27: return;
            default: throw new AssertionError("unexpected server opcode while seeking response: "+op);
        }
    }
    private static byte[] readVarShortBody(InputStream in)throws IOException{
        int len=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,len);
    }
    private static byte[] loginPayload(int[] seeds)throws IOException{
        ByteArrayOutputStream rsa=new ByteArrayOutputStream();rsa.write(10);for(int v:seeds)put32(rsa,v);
        put32(rsa,748878668);put32(rsa,307);nl(rsa,"local");nl(rsa,"localpass");nl(rsa,"LOCAL-DEVICE");nl(rsa,"LOCAL-CLIENT");
        byte[] r=rsa.toByteArray();ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(255);b.write(1);b.write(317);b.write(0);
        for(int i=0;i<9;i++)put32(b,0);b.write(r.length);b.write(r);return b.toByteArray();
    }
    private static void put32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);}
    private static void nl(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.ISO_8859_1));o.write(10);}
    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream a,b; TeeOutputStream(OutputStream a,OutputStream b){this.a=a;this.b=b;}
        public void write(int v)throws IOException{a.write(v);b.write(v);}
        public void write(byte[] x,int o,int l)throws IOException{a.write(x,o,l);b.write(x,o,l);}
        public void flush()throws IOException{a.flush();b.flush();}
    }
    private static void require(boolean c,String m){if(!c)throw new AssertionError(m);}
}
