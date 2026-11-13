package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * R40: complete two-session LocalSession/World continuity with a disposable
 * persistent opensrc account: login, walk, durable save, full v308-shaped
 * reconnect, server's exact initial packets followed by position-restoring
 * 81 without appearance, and final clean teardown. No original GUI or owner data.
 */
public final class R40PersistentV308ReconnectPositionTest {
    private static int assertions;
    private static void check(boolean yes,String why){
        assertions++;
        if(!yes)throw new AssertionError("R40 "+why);
    }
    public static void main(String[] args) throws Exception {
        java.nio.file.Path root=java.nio.file.Files.createTempDirectory("spk-r40-reconnect-world-");
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

                out.write(14); out.write(V308PreloginNameHash.hash5("opensrc")); out.flush();
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
                byte[] expectedBoot = BootstrapPackets.player81TeleportWithAppearance(0,55,55,"opensrc",eq.appearanceItems(),ps);
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
                java.nio.file.Path profile=root.resolve("opensrc.properties");
                check(java.nio.file.Files.isRegularFile(profile),
                    "persistent opensrc profile saved at disconnect");
                java.util.Optional<PlayerSnapshot> persisted=
                    new FilePlayerRepository(
                        name->root.resolve(name+".properties"))
                        .load("opensrc");
                check(persisted.isPresent(),
                    "persisted opensrc profile reloads from disk");
                check(persisted.get().username().equals("opensrc"),
                    "persisted profile identity survives reload");
                check("3088".equals(persisted.get().value("movement.worldX")) &&
                      "3495".equals(persisted.get().value("movement.worldY")) &&
                      "0".equals(persisted.get().value("movement.plane")),
                    "first walk coordinates durably saved under actual schema keys");

                Future<?> second=ex.submit(() -> {
                    try { new LocalSession(ss.accept(), true, true,world).run(); }
                    catch(IOException e){ throw new RuntimeException(e); }
                });
                try(Socket returning=new Socket(loop,ss.getLocalPort())){
                    returning.setSoTimeout(10000);
                    InputStream againIn=returning.getInputStream();
                    OutputStream againOut=returning.getOutputStream();
                    againOut.write(14);
                    againOut.write(V308PreloginNameHash.hash5("opensrc"));
                    againOut.flush();
                    byte[] preAgain=Binary.readExactly(againIn,17);
                    check((preAgain[8]&255)==0,
                        "second prelogin accepted");
                    long seedAgain=Binary.i64(preAgain,9);
                    check(seedAgain==LocalLoginTransport.SERVER_SEED,
                        "second prelogin seed exact");
                    int[] seedSet={0x22446688,0x13579bdf,
                        (int)(seedAgain>>>32),(int)seedAgain};
                    byte[] secondPayload=loginPayload(seedSet);
                    againOut.write(16);
                    againOut.write(secondPayload.length);
                    againOut.write(secondPayload);
                    againOut.flush();
                    byte[] successAgain=Binary.readExactly(againIn,3);
                    check((successAgain[0]&255)==2 &&
                          (successAgain[1]&255)==205 &&
                          (successAgain[2]&255)==0,
                        "second login success triplet");

                    IsaacCipher c2sAgain=new IsaacCipher(seedSet.clone());
                    againOut.write((185+c2sAgain.nextInt())&255);
                    againOut.write(912>>>8);againOut.write(912);
                    againOut.flush();
                    int[] inbound=seedSet.clone();
                    for(int i=0;i<inbound.length;i++)inbound[i]+=50;
                    IsaacCipher decoder=new IsaacCipher(inbound);

                    check(java.util.Arrays.equals(
                            readFixed(againIn,decoder,249,3),
                            BootstrapPackets.packet249(0,1)),
                        "reconnect initial opcode249 exact");
                    check(java.util.Arrays.equals(
                            readFixed(againIn,decoder,73,4),
                            BootstrapPackets.region73(385,436)),
                        "reconnect initial opcode73 exact");
                    EquipmentState gear=new EquipmentState();
                    PlayerState stats=new PlayerState();
                    byte[] baseAppearance=
                        BootstrapPackets.player81TeleportWithAppearance(
                            0,55,55,"opensrc",
                            gear.appearanceItems(),stats);
                    check(java.util.Arrays.equals(
                            expectVarShort(againIn,decoder,81,
                                baseAppearance.length),baseAppearance),
                        "reconnect base opcode81 appearance exact");
                    expectFixed(againIn,decoder,110,1);
                    byte[] baselineOrb=
                        BootstrapPackets.widgetText126(149,"100%");
                    check(java.util.Arrays.equals(
                            expectVarShort(againIn,decoder,126,
                                baselineOrb.length),baselineOrb),
                        "reconnect run orb opcode126 exact");

                    // LocalSessionBootstrapPublisher publishes restored
                    // position immediately after certified spawn+orb packets.
                    // Method signature is (plane, localY, localX).
                    byte[] restored=
                        BootstrapPackets.player81TeleportNoAppearance(0,55,56);
                    check(java.util.Arrays.equals(
                            expectVarShort(againIn,decoder,81,restored.length),
                            restored),
                        "second session authoritative saved walk position "+
                        "3088,3495 restored by S2C81");
                    check(world.players().size()==1,
                        "one registered player after reconnect");
                    V5BootstrapTestSupport
                        .expectNativeSidebarInventoryAndFountain(
                            againIn,decoder);
                    byte[] postLoginIdle=
                        expectIdle81SkippingWorld65(againIn,decoder);
                    check(java.util.Arrays.equals(
                            postLoginIdle,BootstrapPackets.player81Idle()),
                        "reconnected player receives World tick after restore");
                }
                second.get(8,TimeUnit.SECONDS);
                check(world.players().size()==0,
                    "reconnected player unregistered after EOF");
                check(world.tickTargetsSnapshot().isEmpty(),
                    "reconnected tick target detached after EOF");
                java.util.Optional<PlayerSnapshot> twice=
                    new FilePlayerRepository(
                        name->root.resolve(name+".properties"))
                        .load("opensrc");
                check(twice.isPresent() &&
                      "3088".equals(twice.get().value("movement.worldX")) &&
                      "3495".equals(twice.get().value("movement.worldY")),
                    "second session final save preserves prior movement");
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
        System.out.println("R40_PERSISTENT_V308_RECONNECT_POSITION_PASS assertions="
            +assertions+" v308Revision308=true persistentOpensrc=true"
            +" twoFullLogin185Widget912=true initial249_73_81Exact=true"
            +" walk164To81=true savedPosition3088_3495=true"
            +" reconnectRestore81Exact=true secondWorldTick=true"
            +" twoSessionsUnregistered=true finalSaveReload=true"
            +" originalGui=false");
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
        if(raw<0)throw new EOFException("R40 fixed packet truncated");
        int seen=(raw-c.nextInt())&255;
        check(seen==opcode,"first scene opcode="+seen+" wanted="+opcode);
        return Binary.readExactly(in,len);
    }

    private static byte[] expectWalk81(InputStream in,IsaacCipher cipher)
        throws IOException {
        byte[] wanted=BootstrapPackets.player81WalkStep(4);
        for(int k=0;k<32;k++){
            int raw=in.read();
            if(raw<0)throw new EOFException("R40 walk packet EOF");
            int opcode=(raw-cipher.nextInt())&255;
            check(opcode==65||opcode==81,
                "unexpected world tick opcode="+opcode);
            int hi=in.read(),lo=in.read();
            if(hi<0||lo<0)throw new EOFException("R40 packet length EOF");
            int n=(hi<<8)|lo;
            byte[] body=Binary.readExactly(in,n);
            if(opcode==81&&java.util.Arrays.equals(body,wanted))
                return body;
        }
        throw new AssertionError("R40 no authoritative walk81");
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
        nl(inner, "opensrc"); nl(inner, "r39-disposable-no-live-password"); nl(inner, "LOCAL-DEVICE"); nl(inner, "LOCAL-CLIENT");
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
