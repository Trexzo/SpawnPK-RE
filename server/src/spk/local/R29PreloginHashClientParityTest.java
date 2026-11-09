package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * R29 client-derived v308 prelogin hash: test the actual LocalLab login
 * transport on ephemeral loopback sockets, without GUI or credentials.
 */
public final class R29PreloginHashClientParityTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        eq(2, V308PreloginNameHash.hash5("local"), "local hash5");
        eq(29, V308PreloginNameHash.hash5("localtest"), "localtest hash5");
        eq(10, V308PreloginNameHash.hash5("myau"), "myau hash5");
        eq(12, V308PreloginNameHash.hash5("opensrc"), "opensrc hash5");
        eq(24, V308PreloginNameHash.hash5("spawnpk"), "spawnpk hash5");
        eq(27, V308PreloginNameHash.hash5("ab\uD83D\uDE00cd"),
           "Java UTF-16 surrogate code-unit parity");
        eq(2, V308PreloginNameHash.hash5("LOCAL"), "ASCII uppercase");
        eq(29, V308PreloginNameHash.hash5("LocalTest"), "case folding");
        eq(0, V308PreloginNameHash.hash5(""), "empty hash5");
        eq(0, V308PreloginNameHash.hash5("___"), "unknown chars base37 zero");
        eq(V308PreloginNameHash.hash5("abcdefghijkl"),
            V308PreloginNameHash.hash5("abcdefghijkl_IGNORED"),
            "first 12 characters only");
        eq(V308PreloginNameHash.hash5("local"),
            V308PreloginNameHash.hash5("local  "),
            "trailing base37 zeros stripped");
        for (String name : new String[] { "a", "z", "09", "local", "localtest",
                                          "123456789012", "ABCDEFGHIJKL" }) {
            int hash = V308PreloginNameHash.hash5(name);
            yes(hash >= 0 && hash <= 31, "hash always five-bit");
        }
        try {
            V308PreloginNameHash.hash5(null);
            throw new AssertionError("null accepted");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }

        valid("local", 2, 16);
        valid("localtest", 29, 18);
        // Legacy synthetic fixtures use a legal but unrelated hash 7.
        // Preserve compatibility pending a separate migration of *all*
        // historical socket fixture producers.
        valid("local", 7, 16);
        invalid(14, 32, "userHash5");
        invalid(14, 255, "userHash5");
        invalid(15, 2, "prelogin");
        System.out.println("R29_V308_PRELOGIN_NAME_HASH_CLIENT_PARITY_PASS"
                + " assertions=" + assertions
                + " clientHashes=local:2,localtest:29"
                + " hashRange=0..31"
                + " actualSocketHandshake=true"
                + " invalid32And255BlockedBeforeSeed=true"
                + " legacySynthetic7Preserved=true");
    }

    private static void valid(String name, int hash5, int type) throws Exception {
        InetAddress loop = InetAddress.getByName("127.0.0.1");
        try (ServerSocket listener = new ServerSocket(0, 1, loop)) {
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                Future<LoginFrame> result = worker.submit(() -> {
                    try (Socket peer = listener.accept()) {
                        return LocalLoginTransport.readLogin(
                            peer, peer.getInputStream(), peer.getOutputStream(),
                            "[r29-local-only] ");
                    }
                });
                try (Socket client = new Socket(loop, listener.getLocalPort())) {
                    client.setSoTimeout(5000);
                    InputStream in = client.getInputStream();
                    OutputStream out = client.getOutputStream();
                    out.write(new byte[] { 14, (byte) hash5 });
                    out.flush();
                    byte[] pre = Binary.readExactly(in, 17);
                    for (int i=0; i<9; i++) eq(0, pre[i] & 255, "prelogin reply status");
                    long seed = Binary.i64(pre, 9);
                    eq(LocalLoginTransport.SERVER_SEED, seed, "server seed");
                    byte[] payload = loginPayload(name, seed);
                    out.write(type);
                    out.write(payload.length);
                    out.write(payload);
                    out.flush();
                }
                LoginFrame frame = result.get(5, TimeUnit.SECONDS);
                eq(name, frame.username, "correct username persisted");
                eq(type, frame.loginType, "login type");
                eq(317, frame.revision, "protocol 317");
                eq(308, frame.configValue2, "build config 308");
            } finally {
                worker.shutdownNow();
                if (!worker.awaitTermination(5, TimeUnit.SECONDS))
                    throw new AssertionError("worker thread did not end");
            }
        }
    }

    private static void invalid(int type, int hash, String expected) throws Exception {
        InetAddress loop = InetAddress.getByName("127.0.0.1");
        try (ServerSocket listener = new ServerSocket(0, 1, loop)) {
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                Future<String> result = worker.submit(() -> {
                    try (Socket peer = listener.accept()) {
                        try {
                            LocalLoginTransport.readLogin(
                                peer, peer.getInputStream(), peer.getOutputStream(),
                                "[r29-invalid-local-only] ");
                            return "unexpected-success";
                        } catch (IOException rejected) {
                            return rejected.getMessage();
                        }
                    }
                });
                try (Socket client = new Socket(loop, listener.getLocalPort())) {
                    client.setSoTimeout(5000);
                    OutputStream out = client.getOutputStream();
                    out.write(new byte[] {(byte)type, (byte)hash});
                    out.flush();
                    // The invalid request is rejected before the 17-byte
                    // prelogin response, not after a seed is disclosed.
                    eq(-1, client.getInputStream().read(),
                       "invalid prelogin response should be EOF");
                }
                String message = result.get(5, TimeUnit.SECONDS);
                yes(message.contains(expected), "specific prelogin rejection");
            } finally {
                worker.shutdownNow();
                if (!worker.awaitTermination(5, TimeUnit.SECONDS))
                    throw new AssertionError("worker thread did not end");
            }
        }
    }

    private static byte[] loginPayload(String name, long seed) throws IOException {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        inner.write(10);
        for (int n: new int[]{0x01020304,0x11223344,
                (int)(seed >>> 32),(int)seed}) put32(inner,n);
        put32(inner,748878668);
        put32(inner,308);
        for(String field: new String[]{name,"SYNTHETIC_PASS","SYNTHETIC_DEVICE",
                                       "SYNTHETIC_CLIENT"}) {
            inner.write(field.getBytes(StandardCharsets.ISO_8859_1));
            inner.write(10);
        }
        byte[] body = inner.toByteArray();
        if(body.length > 255) throw new AssertionError("fixture inner too long");
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        outer.write(255);outer.write(317 >>> 8);outer.write(317);outer.write(0);
        for(int i=0;i<9;i++)put32(outer,0);
        outer.write(body.length);
        outer.write(body);
        if(outer.size() > 255)throw new AssertionError("fixture outer too long");
        return outer.toByteArray();
    }

    private static void put32(OutputStream out,int v) throws IOException {
        out.write(v >>> 24); out.write(v >>> 16);
        out.write(v >>> 8); out.write(v);
    }

    private static void yes(boolean condition, String message) {
        assertions++;
        if(!condition)throw new AssertionError("R29 "+message);
    }
    private static void eq(long expected,long actual,String message) {
        assertions++;
        if(expected!=actual)throw new AssertionError(
            "R29 "+message+" expected="+expected+" actual="+actual);
    }
    private static void eq(String expected,String actual,String message) {
        assertions++;
        if(!expected.equals(actual))throw new AssertionError("R29 "+message);
    }
    private R29PreloginHashClientParityTest(){}
}
