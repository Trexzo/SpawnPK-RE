package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * P5.5: pinned 185 selector + confirm + quick ON/OFF through LocalSession.
 * Checks root97, config36, control126, repeated ON/OFF and reconnect.
 * Synthetic pinned-v308 TCP; no actual client UI, prayer drain or effect.
 */
public final class PlayableQuickPrayerTcpReconnectTest {
    private static final int THICK_SKIN=5609;
    private static final int PIETY=18014;
    private static final int PRAYER_POINTS=10;

    public static void main(String[] args)throws Exception {
        if(args.length!=0)throw new AssertionError("P5.5 unexpected args");
        Path root=Files.createTempDirectory("spk-playable-p55-prayer-");
        FilePlayerRepository repo=new FilePlayerRepository(
            name->root.resolve(name+".properties"));
        WorldPlayer starting=new WorldPlayer();
        starting.playerState().setCurrentLevel(
            PlayerState.PRAYER,PRAYER_POINTS);
        repo.save(PlayerSnapshotCodec.capture("local",starting));
        World origin=World.isolatedForTest(50L,repo);
        try {
            InetAddress loop=InetAddress.getByName("127.0.0.1");
            try(ServerSocket ss=new ServerSocket(0,1,loop)){
                ExecutorService executor=Executors.newSingleThreadExecutor();
                Future<?> future=executor.submit(()->{
                    try{
                        new LocalSession(ss.accept(),true,true,origin).run();
                    }catch(IOException failure){
                        throw new RuntimeException(failure);
                    }
                });
                try{
                    try(Socket s=new Socket(loop,ss.getLocalPort())){
                        s.setSoTimeout(5000);
                        InputStream in=s.getInputStream();
                        OutputStream out=s.getOutputStream();
                        out.write(14);out.write(7);out.flush();
                        byte[] pre=Binary.readExactly(in,9);
                        if((pre[8]&255)!=0)
                            throw new AssertionError("P5.5 prelogin");
                        long randomSeed=Binary.i64(Binary.readExactly(in,8),0);
                        int[] seeds={0x01020304,0x11223344,
                            (int)(randomSeed>>>32),(int)randomSeed};
                        byte[] payload=loginPayload(seeds);
                        out.write(16);out.write(payload.length);
                        out.write(payload);out.flush();
                        if((Binary.readExactly(in,3)[0]&255)!=2)
                            throw new AssertionError("P5.5 login");
                        IsaacCipher c2s=new IsaacCipher(seeds.clone());
                        int[] serverSeed=seeds.clone();
                        for(int i=0;i<serverSeed.length;i++)serverSeed[i]+=50;
                        IsaacCipher s2c=new IsaacCipher(serverSeed);
                        send185(out,c2s,912);
                        expectFixed(in,s2c,249,3);
                        expectFixed(in,s2c,73,4);
                        expectVarShort(in,s2c,81);
                        expectFixed(in,s2c,110,1);
                        expectVarShort(in,s2c,126);
                        V5BootstrapTestSupport
                            .expectNativeSidebarInventoryAndFountain(
                                in,s2c,99,PRAYER_POINTS);

                        // Exact pinned-v308 185: open Quick Prayer selector.
                        send185(out,c2s,
                            QuickPrayerSelectionPresentation.SELECT_WIDGET);
                        byte[] root=expectEventuallyFixed(in,s2c,97,2);
                        if(!Arrays.equals(root,
                            BootstrapPackets.interface97(
                                QuickPrayerSelectionPresentation.NORMAL_ROOT)))
                            throw new AssertionError(
                                "P5.5 Quick Prayer normal root not opened");

                        // Confirm exactly Thick Skin in the same session.
                        send185(out,c2s,
                            QuickPrayerSelectionPresentation.FIRST_SELECTOR);
                        send185(out,c2s,
                            QuickPrayerSelectionPresentation.CONFIRM_WIDGET);

                        // Quick ON must send exact config36 then control126.
                        send185(out,c2s,
                            QuickPrayerSelectionPresentation.QUICK_ON_WIDGET);
                        requireConfig(in,s2c,83,1,"Quick Thick Skin ON");
                        requireControl(in,s2c,"QUICK_PRAYERS_ON");

                        // A second ON remains idempotent: only control126.
                        send185(out,c2s,
                            QuickPrayerSelectionPresentation.QUICK_ON_WIDGET);
                        requireControl(in,s2c,"QUICK_PRAYERS_ON");

                        // Quick OFF must reset the same selected prayer.
                        send185(out,c2s,
                            QuickPrayerSelectionPresentation.QUICK_OFF_WIDGET);
                        requireConfig(in,s2c,83,0,"Quick Thick Skin OFF");
                        requireControl(in,s2c,"QUICK_PRAYERS_OFF");

                        // ON again must restore config before token.
                        send185(out,c2s,
                            QuickPrayerSelectionPresentation.QUICK_ON_WIDGET);
                        requireConfig(in,s2c,83,1,"Quick Thick Skin re-ON");
                        requireControl(in,s2c,"QUICK_PRAYERS_ON");

                        // Later movement verifies correct C2S/S2C ISAAC
                        // framing after all selector/action roundtrips.
                        sendVarByte(out,c2s,164,
                            walkBody(3088,3495,false));
                        expectEventually81(in,s2c,
                            BootstrapPackets.player81WalkStep(4));
                    }
                    future.get(10,TimeUnit.SECONDS);
                }finally{
                    executor.shutdownNow();
                    executor.awaitTermination(2,TimeUnit.SECONDS);
                }
            }
            if(!origin.closed())origin.close();
            Optional<PlayerSnapshot> saved=repo.loadForWorldSession("local");
            if(!saved.isPresent())
                throw new AssertionError("P5.5 final save missing");
            World next=World.isolatedForTest(50L,repo);
            try{
                WorldPlayer player=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(saved.get(),player);
                long gen=next.registerPlayer(player,"local");
                if(player.playerState().currentLevel(PlayerState.PRAYER)
                       !=PRAYER_POINTS ||
                   player.playerState().xp(PlayerState.PRAYER)
                       !=PlayerState.XP_99 ||
                   player.bank().bankAt(0).itemId!=995 ||
                   player.bank().bankAt(0).qty!=100_000)
                    throw new AssertionError(
                        "P5.5 prayer/account preimage not restored");
                // Gameplay prayer activations are session-only today. This
                // is a factual negative boundary, not original-game parity.
                if(player.prayers().activeCount()!=0)
                    throw new AssertionError(
                        "P5.5 activation unexpectedly persisted");
                if(!next.unregisterPlayer(player,gen))
                    throw new AssertionError("P5.5 fresh World unregister");
            }finally{
                if(!next.closed())next.close();
            }
            System.out.println(
                "P55_PLAYABLE_QUICK_PRAYER_TCP_PASS"+
                " selector5001=true select17202=true confirm17241=true"+
                " quickOn5000Idempotent=true off4999=true"+
                " config36AndControl126=true movementIsaacAligned=true"+
                " normalFinalSave=true prayerPoints10Restored=true"+
                " activationAndSelectionSessionOnly=true noDrainOrEffectProof=true"+
                " noOriginalGUIOrCrashDurabilityProof=true");
        }finally{
            if(!origin.closed())origin.close();
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }

    private static void requireControl(InputStream in,IsaacCipher cipher,
        String control)throws IOException {
        byte[] got=expectEventuallyVarShort(in,cipher,126);
        byte[] expected=BootstrapPackets.widgetText126(1,control);
        if(!Arrays.equals(got,expected))
            throw new AssertionError("P5.5 control="+control+
                " got="+ClientPacketProbe.hex(got,80));
    }

    private static void requireConfig(InputStream in,IsaacCipher cipher,
        int varp,int value,String label)throws IOException{
        byte[] got=expectEventuallyFixed(in,cipher,36,3);
        byte[] expected=BootstrapPackets.config36(varp,value);
        if(!Arrays.equals(got,expected))
            throw new AssertionError("P5.5 "+label+
                " got="+ClientPacketProbe.hex(got,16)+
                " expected="+ClientPacketProbe.hex(expected,16));
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
    private static void skip(InputStream in,int op)throws IOException{switch(op){case 81:case 53:case 65:case 126:{int n=((in.read()&255)<<8)|(in.read()&255);Binary.readExactly(in,n);break;}case 36:case 71:case 249:Binary.readExactly(in,3);break;case 97:Binary.readExactly(in,2);break;case 134:Binary.readExactly(in,6);break;case 110:case 106:Binary.readExactly(in,1);break;case 248:Binary.readExactly(in,4);break;case 219:case 27:break;default:throw new AssertionError("unexpected s2c "+op);}}
    private static byte[] loginPayload(int[] seeds)throws IOException{ByteArrayOutputStream inner=new ByteArrayOutputStream();inner.write(10);for(int v:seeds)put32(inner,v);put32(inner,748878668);put32(inner,307);nl(inner,"local");nl(inner,"localpass");nl(inner,"LOCAL-DEVICE");nl(inner,"LOCAL-CLIENT");byte[] raw=inner.toByteArray();ByteArrayOutputStream p=new ByteArrayOutputStream();p.write(255);p.write(317>>>8);p.write(317);p.write(0);for(int i=0;i<9;i++)put32(p,0);p.write(raw.length);p.write(raw);return p.toByteArray();}
    private static void put32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);}private static void nl(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.ISO_8859_1));o.write(10);}
}
