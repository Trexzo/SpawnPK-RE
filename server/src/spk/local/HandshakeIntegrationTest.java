package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class HandshakeIntegrationTest {
    public static void main(String[] args) throws Exception {
        InetAddress loop = InetAddress.getByName("127.0.0.1");
        try (ServerSocket ss = new ServerSocket(0, 1, loop)) {
            ExecutorService ex = Executors.newSingleThreadExecutor();
            Future<?> server = ex.submit(() -> {
                try { new LocalSession(ss.accept(), false).run(); }
                catch (IOException e) { throw new RuntimeException(e); }
            });

            try (Socket s = new Socket(loop, ss.getLocalPort())) {
                s.setSoTimeout(5000);
                InputStream in = s.getInputStream(); OutputStream out = s.getOutputStream();
                out.write(14); out.write(7); out.flush();
                byte[] prefix = Binary.readExactly(in, 9);
                if ((prefix[8] & 0xff) != 0) throw new AssertionError("prelogin status != 0");
                long serverSeed = Binary.i64(Binary.readExactly(in, 8), 0);
                if (serverSeed != 0x0123456789ABCDEFL) throw new AssertionError("server seed mismatch");

                int[] seeds = {0x01020304, 0x11223344, (int)(serverSeed >>> 32), (int)serverSeed};
                byte[] payload = loginPayload(seeds);
                out.write(16); out.write(payload.length); out.write(payload); out.flush();

                byte[] ok = Binary.readExactly(in, 3);
                if ((ok[0]&255)!=2 || (ok[1]&255)!=205 || (ok[2]&255)!=0)
                    throw new AssertionError("login success triplet mismatch");

                IsaacCipher outbound = new IsaacCipher(seeds.clone());
                out.write((185 + outbound.nextInt()) & 255);
                out.write(912 >>> 8); out.write(912); out.flush();
            }
            server.get(5, TimeUnit.SECONDS);
            ex.shutdownNow();
        }
        System.out.println("LOCAL_HANDSHAKE_INTEGRATION_PASS prelogin+seed+login+success+isaac185/widget912");
    }

    private static byte[] loginPayload(int[] seeds) throws IOException {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        inner.write(10);
        for (int v : seeds) put32(inner, v);
        put32(inner, 748878668); // harmless synthetic/current-format config slot for parser exercise
        put32(inner, 307);
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
