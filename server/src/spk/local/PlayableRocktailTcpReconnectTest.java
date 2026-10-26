package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * P5.1: LocalSession loopback test for pinned v308 opcode122 Eat.
 * Synthetic wire client, CUSTOM_LOCALLAB healing, not original GUI.
 */
public final class PlayableRocktailTcpReconnectTest {
    private static final int ROCKTAIL=G1RocktailConsumableHandler.ITEM_ID;

    public static void main(String[] args)throws Exception {
        if(args.length!=0)throw new AssertionError("P5.1 unexpected args");
        Path root=Files.createTempDirectory("spk-playable-p51-tcp-");
        FilePlayerRepository repository=new FilePlayerRepository(
            name->root.resolve(name+".properties"));
        WorldPlayer seedPlayer=new WorldPlayer();
        seedPlayer.playerState().setCurrentLevel(PlayerState.HITPOINTS,50);
        repository.save(PlayerSnapshotCodec.capture("local",seedPlayer));
        World origin=World.isolatedForTest(50L,repository);
        try {
            InetAddress loop=InetAddress.getByName("127.0.0.1");
            try(ServerSocket ss=new ServerSocket(0,1,loop)) {
                ExecutorService ex=Executors.newSingleThreadExecutor();
                Future<?> server=ex.submit(()->{
                    try {
                        new LocalSession(ss.accept(),true,true,origin).run();
                    } catch(IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                try {
                    try(Socket s=new Socket(loop,ss.getLocalPort())) {
                        s.setSoTimeout(5000);
                        InputStream in=s.getInputStream();
                        OutputStream out=s.getOutputStream();
                        out.write(14);
                        out.write(7);
                        out.flush();
                        byte[] pre=Binary.readExactly(in,9);
                        if((pre[8]&255)!=0)
                            throw new AssertionError("P5.1 prelogin");
                        long seed=Binary.i64(Binary.readExactly(in,8),0);
                        int[] seeds={0x01020304,0x11223344,
                            (int)(seed>>>32),(int)seed};
                        byte[] lp=loginPayload(seeds);
                        out.write(16);
                        out.write(lp.length);
                        out.write(lp);
                        out.flush();
                        if((Binary.readExactly(in,3)[0]&255)!=2)
                            throw new AssertionError("P5.1 login");
                        IsaacCipher c2s=new IsaacCipher(seeds.clone());
                        int[] si=seeds.clone();
                        for(int i=0;i<4;i++)si[i]+=50;
                        IsaacCipher s2c=new IsaacCipher(si);
                        send185(out,c2s,912);
                        expectFixed(in,s2c,249,3);
                        expectFixed(in,s2c,73,4);
                        expectVarShort(in,s2c,81);
                        expectFixed(in,s2c,110,1);
                        expectVarShort(in,s2c,126);
                        V5BootstrapTestSupport
                            .expectNativeSidebarInventoryAndFountain(
                                in,s2c,50);

                        // Bank slot 3 contains the default 20 Rocktails.
                        send132(out,c2s,3088,26972,3495);
                        expectEventuallyFixed(in,s2c,248,4);
                        expectEventuallyVarShort(in,s2c,53);
                        expectEventuallyVarShort(in,s2c,53);
                        send145(out,c2s,5382,3,ROCKTAIL);
                        expectEventuallyVarShort(in,s2c,53);
                        byte[] overlay=expectEventuallyVarShort(in,s2c,53);
                        int[] withdrawn=itemAt53(overlay,0);
                        if(withdrawn[0]!=ROCKTAIL||withdrawn[1]!=1)
                            throw new AssertionError("P5.1 withdraw="+
                                Arrays.toString(withdrawn));
                        send0(out,c2s,130);
                        byte[] closed=expectEventuallyVarShort(in,s2c,53);
                        if(Binary.u16(closed,0)!=3214||
                           itemAt53(closed,0)[0]!=ROCKTAIL)
                            throw new AssertionError("P5.1 bank close widget");

                        // Exact pinned v308 opcode122: widget LE-A,
                        // slot BE-A, item LE, then S2C 53 and 134.
                        send122(out,c2s,3214,0,ROCKTAIL);
                        byte[] empty=expectEventuallyVarShort(in,s2c,53);
                        if(Binary.u16(empty,0)!=3214||
                           itemAt53(empty,0)[0]!=-1)
                            throw new AssertionError("P5.1 food not removed");
                        byte[] hp=expectEventuallyFixed(in,s2c,134,6);
                        byte[] expected=BootstrapPackets.skill134(
                            PlayerState.HITPOINTS,PlayerState.XP_99,60);
                        if(!Arrays.equals(hp,expected))
                            throw new AssertionError("P5.1 skill134="+
                                ClientPacketProbe.hex(hp,16));

                        // Replay the consumed slot and confirm later move
                        // remains framed. Saved HP must still be just 60.
                        send122(out,c2s,3214,0,ROCKTAIL);
                        sendVarByte(out,c2s,164,
                            walkBody(3088,3495,false));
                        expectEventually81(in,s2c,
                            BootstrapPackets.player81WalkStep(4));
                    }
                    server.get(10,TimeUnit.SECONDS);
                } finally {
                    ex.shutdownNow();
                    ex.awaitTermination(2,TimeUnit.SECONDS);
                }
            }

            if(!origin.closed())origin.close();
            Optional<PlayerSnapshot> saved=
                repository.loadForWorldSession("local");
            if(!saved.isPresent())
                throw new AssertionError("P5.1 TCP final account missing");
            World restarted=World.isolatedForTest(50L,repository);
            try {
                WorldPlayer restored=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(saved.get(),restored);
                long gen=restarted.registerPlayer(restored,"local");
                BankState.Stack stock=restored.bank().bankAt(3);
                if(restored.playerState().currentLevel(
                       PlayerState.HITPOINTS)!=60||
                   restored.bank().inventoryCount(ROCKTAIL)!=0||
                   stock==null||stock.itemId!=ROCKTAIL||
                   stock.qty!=19)
                    throw new AssertionError(
                        "P5.1 stale Eat or heal persistence mismatch"+
                        " hp="+restored.playerState().currentLevel(
                            PlayerState.HITPOINTS)+
                        " inv="+restored.bank().inventoryCount(ROCKTAIL)+
                        " bank="+stock);
                if(!restarted.unregisterPlayer(restored,gen))
                    throw new AssertionError("P5.1 fresh World unregister");
            } finally {
                if(!restarted.closed())restarted.close();
            }
            System.out.println(
                "P51_PLAYABLE_ROCKTAIL_TCP_RECONNECT_PASS"+
                " native122=true inventory53Empty=true"+
                " skill134Hp60=true duplicateDenied=true"+
                " localSessionLogout=true freshWorldHp60=true"+
                " freshWorldBankRocktails19=true"+
                " noOriginalGUIOrPowerLossProof=true");
        } finally {
            if(!origin.closed())origin.close();
            try(Stream<Path> files=Files.walk(root)) {
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }

    /** Pinned client wire: widget LE-A, slot BE-A, item LE. */
    private static void send122(
        OutputStream out,IsaacCipher cipher,int widget,int slot,int item
    )throws IOException {
        out.write((122+cipher.nextInt())&255);
        out.write((widget+128)&255);
        out.write((widget>>>8)&255);
        out.write((slot>>>8)&255);
        out.write((slot+128)&255);
        out.write(item&255);
        out.write((item>>>8)&255);
        out.flush();
    }

    private static int[] itemAt53(byte[] p,int wanted){int slots=Binary.u16(p,2),off=4;for(int i=0;i<slots;i++){int q=p[off++]&255;if(q==255){int p0=p[off++]&255,p1=p[off++]&255,p2=p[off++]&255,p3=p[off++]&255;q=(p1<<24)|(p0<<16)|(p3<<8)|p2;}int lo=((p[off++]&255)-128)&255,hi=p[off++]&255,id=((hi<<8)|lo)-1;if(i==wanted)return new int[]{id,q};}throw new AssertionError();}
    private static void send0(OutputStream o,IsaacCipher c,int op)throws IOException{o.write((op+c.nextInt())&255);o.flush();}
    private static void send185(OutputStream o,IsaacCipher c,int w)throws IOException{o.write((185+c.nextInt())&255);o.write(w>>>8);o.write(w);o.flush();}
    private static void send132(OutputStream o,IsaacCipher c,int x,int id,int y)throws IOException{o.write((132+c.nextInt())&255);o.write((x+128)&255);o.write(x>>>8);o.write(id>>>8);o.write(id);o.write(y>>>8);o.write((y+128)&255);o.flush();}
    private static void send145(OutputStream o,IsaacCipher c,int w,int sl,int item)throws IOException{o.write((145+c.nextInt())&255);putBEA(o,w);putBEA(o,sl);putBEA(o,item);o.flush();}
    private static void send41(OutputStream o,IsaacCipher c,int item,int slot,int widget)throws IOException{o.write((41+c.nextInt())&255);o.write(item>>>8);o.write(item);putBEA(o,slot);putBEA(o,widget);o.flush();}
    private static void putBEA(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write((v+128)&255);}
    private static void sendVarByte(OutputStream o,IsaacCipher c,int op,byte[] b)throws IOException{o.write((op+c.nextInt())&255);o.write(b.length);o.write(b);o.flush();}
    private static byte[] walkBody(int x,int y,boolean run)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write((x+128)&255);b.write(x>>>8);b.write(y);b.write(y>>>8);b.write(run?255:0);return b.toByteArray();}
    private static void expectFixed(InputStream in,IsaacCipher c,int op,int n)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);Binary.readExactly(in,n);}
    private static byte[] expectVarShort(InputStream in,IsaacCipher c,int op)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}
    private static byte[] expectEventuallyFixed(InputStream in,IsaacCipher c,int wanted,int len)throws IOException{for(int i=0;i<20;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==wanted)return Binary.readExactly(in,len);skip(in,op);}throw new AssertionError("wanted fixed "+wanted);}
    private static byte[] expectEventuallyVarShort(InputStream in,IsaacCipher c,int wanted)throws IOException{for(int i=0;i<20;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==wanted){int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}skip(in,op);}throw new AssertionError("wanted var "+wanted);}
    private static void expectEventually81(InputStream in,IsaacCipher c,byte[] wanted)throws IOException{for(int i=0;i<20;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==81){int n=((in.read()&255)<<8)|(in.read()&255);byte[] p=Binary.readExactly(in,n);if(Arrays.equals(p,wanted))return;}else skip(in,op);}throw new AssertionError("81");}
    private static void skip(InputStream in,int op)throws IOException{switch(op){case 81:case 53:case 65:case 126:{int n=((in.read()&255)<<8)|(in.read()&255);Binary.readExactly(in,n);break;}case 134:Binary.readExactly(in,6);break;case 36:case 71:Binary.readExactly(in,3);break;case 110:case 106:Binary.readExactly(in,1);break;case 248:Binary.readExactly(in,4);break;case 219:case 27:break;default:throw new AssertionError("unexpected s2c "+op);}}
    private static byte[] loginPayload(int[] seeds)throws IOException{ByteArrayOutputStream inner=new ByteArrayOutputStream();inner.write(10);for(int v:seeds)put32(inner,v);put32(inner,748878668);put32(inner,307);nl(inner,"local");nl(inner,"localpass");nl(inner,"LOCAL-DEVICE");nl(inner,"LOCAL-CLIENT");byte[] raw=inner.toByteArray();ByteArrayOutputStream p=new ByteArrayOutputStream();p.write(255);p.write(317>>>8);p.write(317);p.write(0);for(int i=0;i<9;i++)put32(p,0);p.write(raw.length);p.write(raw);return p.toByteArray();}
    private static void put32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);}private static void nl(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.ISO_8859_1));o.write(10);}
}
