package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * R30 local-only evidence for the OPTIONAL v308 prelogin name/hash check.
 * Original rs.Client.a(name,password,flag) hashes aload_1 and writes that
 * same aload_1 into the first inner login NL field; no real GUI executed.
 */
public final class R30OptionalStrictPreloginNameHashTest {
    private static int assertions;
    private static final String FLAG="spk.local.prelogin.enforceUsernameHash";

    public static void main(String[] args) throws Exception {
        if (Boolean.getBoolean(FLAG)) {
            throw new AssertionError("R30 default-off strict gate unexpectedly enabled");
        }
        exchange("local", V308PreloginNameHash.hash5("local"), true, true);
        exchange("localtest", V308PreloginNameHash.hash5("localtest"), true, true);
        exchange("opensrc", V308PreloginNameHash.hash5("opensrc"), true, true);
        exchange("local", 7, true, false);
        exchange("localtest", 2, true, false);
        // Preserve older synthetic fixture compatibility when disabled.
        exchange("local", 7, false, true);
        exchange("localtest", 2, false, true);
        String before=System.getProperty(FLAG);
        try {
            System.setProperty(FLAG,"true");
            yes(Boolean.getBoolean(FLAG),"strict JVM property enabled");
            exchangeWithDefault("local", 7, false);
            exchangeWithDefault("local", 2, true);
        } finally {
            if(before==null)System.clearProperty(FLAG);
            else System.setProperty(FLAG,before);
        }
        yes(!Boolean.getBoolean(FLAG),"strict gate default off restored");
        System.out.println(
            "R30_V308_OPTIONAL_STRICT_PRELOGIN_NAME_HASH_PASS assertions="+assertions+
            " realLocalhostSocket=true"+
            " strictMatchesAccepted=true"+
            " strictMismatchDeniedAfterLoginFrame=true"+
            " legacyDefaultPreserved=true"+
            " noClientGui=true");
    }

    private static void exchange(String name,int hash,boolean strict,boolean expectPass)
            throws Exception {
        run(name,hash,strict,expectPass,false);
    }

    private static void exchangeWithDefault(String name,int hash,boolean expectPass)
            throws Exception {
        run(name,hash,false,expectPass,true);
    }

    private static void run(String name,int hash,boolean strict,
                            boolean expectPass,boolean defaultOverload)
            throws Exception {
        InetAddress loop=InetAddress.getByName("127.0.0.1");
        try(ServerSocket listener=new ServerSocket(0,1,loop)){
            ExecutorService executor=Executors.newSingleThreadExecutor();
            try{
                Future<String> server=executor.submit(() -> {
                    try(Socket peer=listener.accept()){
                        try{
                            LoginFrame frame=defaultOverload
                                ? LocalLoginTransport.readLogin(
                                    peer,peer.getInputStream(),peer.getOutputStream(),"[r30] ")
                                : LocalLoginTransport.readLogin(
                                    peer,peer.getInputStream(),peer.getOutputStream(),"[r30] ",strict);
                            if(!name.equals(frame.username))
                                throw new AssertionError("wrong parsed username");
                            return "ACCEPT";
                        } catch(IOException rejected){
                            return rejected.getMessage();
                        }
                    }
                });
                try(Socket client=new Socket(loop,listener.getLocalPort())){
                    client.setSoTimeout(5000);
                    InputStream in=client.getInputStream();
                    OutputStream out=client.getOutputStream();
                    out.write(14);
                    out.write(hash);
                    out.flush();
                    byte[] pre=Binary.readExactly(in,17);
                    for(int i=0;i<9;i++)yes(pre[i]==0,"prelogin status");
                    long serverSeed=Binary.i64(pre,9);
                    yes(serverSeed==LocalLoginTransport.SERVER_SEED,"server seed");
                    byte[] payload=loginPayload(name,serverSeed);
                    out.write(16);
                    out.write(payload.length);
                    out.write(payload);
                    out.flush();
                    client.shutdownOutput();
                }
                String result=server.get(5,TimeUnit.SECONDS);
                if(expectPass){
                    yes("ACCEPT".equals(result),"valid strict/default wire match accepted");
                } else{
                    yes(result!=null && result.contains("username hash5 mismatch"),
                        "mismatched strict name/hash refused");
                    yes(!result.contains(name),"rejection diagnostic contains no username");
                }
            } finally {
                executor.shutdownNow();
                if(!executor.awaitTermination(5,TimeUnit.SECONDS))
                    throw new AssertionError("R30 test thread leak");
            }
        }
    }

    private static byte[] loginPayload(String name,long seed)throws IOException {
        ByteArrayOutputStream inner=new ByteArrayOutputStream();
        inner.write(10);
        put32(inner,0x01020304);
        put32(inner,0x11223344);
        put32(inner,(int)(seed>>>32));
        put32(inner,(int)seed);
        put32(inner,748878668);
        put32(inner,308);
        for(String f:new String[]{name,"R30_SYNTHETIC_PASS",
                "R30_SYNTHETIC_DEVICE","R30_SYNTHETIC_CLIENT"}){
            inner.write(f.getBytes(StandardCharsets.ISO_8859_1));
            inner.write(10);
        }
        byte[] body=inner.toByteArray();
        if(body.length>255)throw new AssertionError("inner frame size >255");
        ByteArrayOutputStream outer=new ByteArrayOutputStream();
        outer.write(255);
        outer.write(317>>>8);
        outer.write(317);
        outer.write(0);
        for(int i=0;i<9;i++)put32(outer,0);
        outer.write(body.length);
        outer.write(body);
        return outer.toByteArray();
    }

    private static void put32(OutputStream out,int value)throws IOException {
        out.write(value>>>24);
        out.write(value>>>16);
        out.write(value>>>8);
        out.write(value);
    }

    private static void yes(boolean condition,String message){
        assertions++;
        if(!condition)throw new AssertionError("R30 "+message);
    }

    private R30OptionalStrictPreloginNameHashTest(){}
}
