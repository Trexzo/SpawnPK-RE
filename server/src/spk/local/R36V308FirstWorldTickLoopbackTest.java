package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * Original-v308-shaped synthetic login, S2C scene bootstrap, first C2S movement and owned World cleanup. No GUI or live account.
 */
public final class R36V308FirstWorldTickLoopbackTest {
    private static int assertions;
    private static void check(boolean yes,String why){
        assertions++;
        if(!yes)throw new AssertionError("R36 "+why);
    }
    public static void main(String[] args) throws Exception {
        java.nio.file.Path root=java.nio.file.Files.createTempDirectory("spk-r36-first-world-");
        InetAddress loop = InetAddress.getByName("127.0.0.1");
        try (World world=World.isolatedForTest(600,
                new FilePlayerRepository(name->root.resolve(name+".properties")));
             ServerSocket ss = new ServerSocket(0, 1, loop)) {
            ExecutorService ex = Executors.newSingleThreadExecutor();
            Future<?> server = ex.submit(() -> {
                try { new LocalSession(ss.accept(), true, true,world).run(); }
                catch (IOException e) { throw new RuntimeException(e); }
            });

            try (Socket s = new Socket(loop, ss.getLocalPort())) {
                s.setSoTimeout(10000);
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();

                out.write(14); out.write(V308PreloginNameHash.hash5("local")); out.flush();
                byte[] prefix = Binary.readExactly(in, 9);
                if ((prefix[8] & 0xff) != 0) throw new AssertionError("prelogin status != 0");
                long serverSeed = Binary.i64(Binary.readExactly(in, 8), 0);

                int[] seeds = {0x01020304, 0x11223344, (int)(serverSeed >>> 32), (int)serverSeed};
                byte[] payload = loginPayload(seeds);
                out.write(16); out.write(payload.length); out.write(payload); out.flush();

                byte[] ok = Binary.readExactly(in, 3);
                if ((ok[0]&255)!=2 || (ok[1]&255)!=205 || (ok[2]&255)!=0)
                    throw new AssertionError("login success triplet mismatch");

                IsaacCipher c2s = new IsaacCipher(seeds.clone());
                out.write((185 + c2s.nextInt()) & 255);
                out.write(912 >>> 8); out.write(912); out.flush();

                int[] inSeeds = seeds.clone();
                for (int i=0;i<inSeeds.length;i++) inSeeds[i] += 50;
                IsaacCipher s2c = new IsaacCipher(inSeeds);

                check(java.util.Arrays.equals(
                    readFixed(in,s2c,249,3),
                    BootstrapPackets.packet249(0,1)),
                    "v308 packet249 exact bytes");
                check(java.util.Arrays.equals(
                    readFixed(in,s2c,73,4),
                    BootstrapPackets.region73(385,436)),
                    "v308 packet73 exact bytes");
                check(world.players().size()==1,
                    "one player admitted after verified first widget");
                EquipmentState eq = new EquipmentState();
                PlayerState ps = new PlayerState();
                byte[] expectedBoot = BootstrapPackets.player81TeleportWithAppearance(0,55,55,"local",eq.appearanceItems(),ps);
                byte[] boot81 = expectVarShort(in, s2c, 81, expectedBoot.length);
                check(java.util.Arrays.equals(boot81,expectedBoot),
                    "first S2C81 appearance/teleport exact bytes");
                expectFixed(in, s2c, 110, 1);
                byte[] orb126 = expectVarShort(in, s2c, 126, BootstrapPackets.widgetText126(149,"100%").length);
                check(java.util.Arrays.equals(orb126,
                    BootstrapPackets.widgetText126(149,"100%")),
                    "run orb S2C126 100 percent");
                V5BootstrapTestSupport.expectNativeSidebarInventoryAndFountain(in, s2c);
                // WORLD-R7 emits the unified HOME NPC pulse (65) on the same 600 ms
                // server tick as the recurring local-player idle packet (81).  Consume
                // those world pulses while proving the two required idle-81 updates.
                byte[] tick1 = expectIdle81SkippingWorld65(in, s2c);
                byte[] tick2 = expectIdle81SkippingWorld65(in, s2c);
                byte[] expected = BootstrapPackets.player81Idle();
                check(java.util.Arrays.equals(tick1,expected),
                    "first world idle S2C81 exact");
                check(java.util.Arrays.equals(tick2,expected),
                    "second world idle S2C81 exact");
                // Client packet164 var-byte and exact wire body from the
                // original ClientPacketProbe movement family.
                out.write((164+c2s.nextInt())&255);
                out.write(5);
                out.write((3088+128)&255);
                out.write(3088>>>8);
                out.write(3495);
                out.write(3495>>>8);
                out.write(0);
                out.flush();
                byte[] walk=expectWalk81(in,s2c);
                check(java.util.Arrays.equals(walk,
                    BootstrapPackets.player81WalkStep(4)),
                    "C2S164 east -> first authoritative S2C81 walk");
                check(world.players().size()==1,
                    "player remains registered after walking tick");
            }

            try{
                server.get(8,TimeUnit.SECONDS);
                check(world.players().size()==0,
                    "disconnect removes WorldPlayer");
                check(world.tickTargetsSnapshot().isEmpty(),
                    "disconnect removes tick target");
                check(!java.nio.file.Files.exists(
                    root.resolve("local.properties")),
                    "nonpersistent login must never write account file");
            }finally{
                ex.shutdownNow();
                check(ex.awaitTermination(8,TimeUnit.SECONDS),
                    "no retained session worker");
            }
        }finally{
            try(java.util.stream.Stream<java.nio.file.Path> walk=
                    java.nio.file.Files.walk(root)){
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
                    try{java.nio.file.Files.deleteIfExists(p);}
                    catch(IOException e){throw new IllegalStateException(e);}
                });
            }
        }
        System.out.println("R36_V308_FIRST_WORLD_TICK_LOOPBACK_PASS assertions="
            +assertions+" v308Revision308=true initial249_73_81Exact=true"
            +" twoIdleWorldTicks=true movement164To81Walk=true"
            +" isolatedWorldAndAccount=true cleanDisconnect=true originalGui=false");
    }


    private static byte[] expectIdle81SkippingWorld65(InputStream in, IsaacCipher c) throws IOException {
        for (int i=0;i<8;i++) {
            int op = (((in.read() & 255) - c.nextInt()) & 255);
            int len = ((in.read() & 255) << 8) | (in.read() & 255);
            byte[] payload = Binary.readExactly(in, len);
            if (op == 65) continue;
            if (op != 81) throw new AssertionError("idle tick expected 65/81 got="+op);
            if (len != 3) throw new AssertionError("idle81 len expected=3 got="+len);
            return payload;
        }
        throw new AssertionError("idle81 not observed after WORLD pulses");
    }

    private static byte[] readFixed(InputStream in,IsaacCipher c,
                                    int opcode,int len)throws IOException{
        int raw=in.read();
        if(raw<0)throw new EOFException("R36 fixed packet truncated");
        int seen=(raw-c.nextInt())&255;
        check(seen==opcode,"first scene opcode="+seen+" wanted="+opcode);
        return Binary.readExactly(in,len);
    }

    private static byte[] expectWalk81(InputStream in,IsaacCipher cipher)
        throws IOException {
        byte[] wanted=BootstrapPackets.player81WalkStep(4);
        for(int k=0;k<32;k++){
            int raw=in.read();
            if(raw<0)throw new EOFException("R36 walk packet EOF");
            int opcode=(raw-cipher.nextInt())&255;
            check(opcode==65||opcode==81,
                "unexpected world tick opcode="+opcode);
            int hi=in.read(),lo=in.read();
            if(hi<0||lo<0)throw new EOFException("R36 packet length EOF");
            int n=(hi<<8)|lo;
            byte[] body=Binary.readExactly(in,n);
            if(opcode==81&&java.util.Arrays.equals(body,wanted))
                return body;
        }
        throw new AssertionError("R36 no authoritative walk81");
    }

    private static void expectFixed(InputStream in, IsaacCipher c, int expectedOpcode, int len) throws IOException {
        int op = (((in.read() & 255) - c.nextInt()) & 255);
        if (op != expectedOpcode) throw new AssertionError("opcode expected="+expectedOpcode+" got="+op);
        Binary.readExactly(in, len);
    }

    private static byte[] expectVarShortAny(InputStream in, IsaacCipher c, int expectedOpcode) throws IOException {
        int op = (((in.read() & 255) - c.nextInt()) & 255);
        if (op != expectedOpcode) throw new AssertionError("opcode expected="+expectedOpcode+" got="+op);
        int len = ((in.read() & 255) << 8) | (in.read() & 255);
        return Binary.readExactly(in, len);
    }

    private static byte[] expectVarShort(InputStream in, IsaacCipher c, int expectedOpcode, int expectedLen) throws IOException {
        int op = (((in.read() & 255) - c.nextInt()) & 255);
        if (op != expectedOpcode) throw new AssertionError("opcode expected="+expectedOpcode+" got="+op);
        int len = ((in.read() & 255) << 8) | (in.read() & 255);
        if (len != expectedLen) throw new AssertionError("len expected="+expectedLen+" got="+len);
        return Binary.readExactly(in, len);
    }

    private static byte[] loginPayload(int[] seeds) throws IOException {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        inner.write(10);
        for (int v : seeds) put32(inner, v);
        put32(inner, 748878668); put32(inner, 308);
        nl(inner, "local"); nl(inner, "localpass"); nl(inner, "LOCAL-DEVICE"); nl(inner, "LOCAL-CLIENT");
        byte[] raw = inner.toByteArray();
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(255); payload.write(317 >>> 8); payload.write(317); payload.write(0);
        for (int i=0;i<9;i++) put32(payload, 0);
        payload.write(raw.length); payload.write(raw);
        return payload.toByteArray();
    }

    private static void put32(OutputStream o, int v) throws IOException {
        o.write(v >>> 24); o.write(v >>> 16); o.write(v >>> 8); o.write(v);
    }
    private static void nl(OutputStream o, String s) throws IOException {
        o.write(s.getBytes(StandardCharsets.ISO_8859_1)); o.write(10);
    }
}
