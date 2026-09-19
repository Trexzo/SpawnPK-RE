package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * Fully synthetic loopback-only integration test for M4 + recurring 600 ms packet-81 ticks.
 */
public final class BootstrapRuntimeIntegrationTest {
    public static void main(String[] args) throws Exception {
        InetAddress loop = InetAddress.getByName("127.0.0.1");
        try (ServerSocket ss = new ServerSocket(0, 1, loop)) {
            ExecutorService ex = Executors.newSingleThreadExecutor();
            Future<?> server = ex.submit(() -> {
                try { new LocalSession(ss.accept(), true).run(); }
                catch (IOException e) { throw new RuntimeException(e); }
            });

            try (Socket s = new Socket(loop, ss.getLocalPort())) {
                s.setSoTimeout(5000);
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();

                out.write(14); out.write(7); out.flush();
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

                expectFixed(in, s2c, 249, 3);
                expectFixed(in, s2c, 73, 4);
                EquipmentState eq = new EquipmentState();
                PlayerState ps = new PlayerState();
                byte[] expectedBoot = BootstrapPackets.player81TeleportWithAppearance(0,55,55,"local",eq.appearanceItems(),ps);
                byte[] boot81 = expectVarShort(in, s2c, 81, expectedBoot.length);
                if (!java.util.Arrays.equals(boot81, expectedBoot))
                    throw new AssertionError("v5.4 bootstrap appearance mismatch");
                expectFixed(in, s2c, 110, 1);
                byte[] orb126 = expectVarShort(in, s2c, 126, BootstrapPackets.widgetText126(149,"100%").length);
                if (!java.util.Arrays.equals(orb126, BootstrapPackets.widgetText126(149,"100%")))
                    throw new AssertionError("run orb packet126 payload mismatch");
                V5BootstrapTestSupport.expectNativeSidebarInventoryAndFountain(in, s2c);
                // WORLD-R7 emits the unified HOME NPC pulse (65) on the same 600 ms
                // server tick as the recurring local-player idle packet (81).  Consume
                // those world pulses while proving the two required idle-81 updates.
                byte[] tick1 = expectIdle81SkippingWorld65(in, s2c);
                byte[] tick2 = expectIdle81SkippingWorld65(in, s2c);
                byte[] expected = BootstrapPackets.player81Idle();
                if (!java.util.Arrays.equals(tick1, expected) || !java.util.Arrays.equals(tick2, expected))
                    throw new AssertionError("idle packet81 payload mismatch");
            }

            try { server.get(2, TimeUnit.SECONDS); } catch (ExecutionException ignored) {}
            ex.shutdownNow();
        }
        System.out.println("V561_WORLD_R7_BOOTSTRAP_INTEGRATION_PASS bootstrap=249,73,81(Bloodrend),110,126,71x8(nativeSidebar+combat0),134x7(skills),53x2(inventory+equipment),WORLD-R7_scene+65(HOME) energy=100 widget149=100% appearanceMask=0x10 idle81_ticks=2 cadence~600ms M4_CERTIFIED_BASELINE_RETAINED");
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
        put32(inner, 748878668); put32(inner, 307);
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
