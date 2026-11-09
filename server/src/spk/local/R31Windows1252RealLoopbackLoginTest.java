package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Bounded, synthetic, ephemeral TCP test. Never launches the original JAR,
 * registers an account or publishes a WorldPlayer session. Reproduces the
 * precise original v308 BigInteger-prefixed login over actual loopback IO.
 */
public final class R31Windows1252RealLoopbackLoginTest {
    private static final String PROPERTY="spk.local.login.decodeWindows1252";
    private static final Charset CP1252=Charset.forName("windows-1252");
    private static int checks;

    public static void main(String[] argv) throws Exception {
        String previous=System.getProperty(PROPERTY);
        try {
            System.clearProperty(PROPERTY);
            check(!Boolean.getBoolean(PROPERTY),"legacy mode default off");
            String legacy=exercise("\u20acRune",16, false,false);
            eq("\u0080Rune",legacy,"default ISO decodes 0x80 as control");

            System.setProperty(PROPERTY,"true");
            eq("\u20acRune",exercise("\u20acRune",16,true,false),
                "opt-in original Windows Cp1252 username");
            eq("local",exercise("local",18,true,false),
                "opt-in reconnect keeps ASCII and type18");
            String rejected=exercise("\u20acRune",16,true,true);
            check(rejected.contains("username hash5 mismatch"),
                "strict mismatch rejected post frame");
            check(!rejected.contains("Rune"),
                "diagnostic must not disclose supplied username");

            System.setProperty(PROPERTY,"false");
            eq("\u0080Rune",exercise("\u20acRune",16,false,false),
                "runtime property can restore old decoder");
        } finally {
            if(previous==null)System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY,previous);
        }
        System.out.println("R31_REAL_LOCALHOST_CP1252_LOGIN_PASS"
            +" assertions="+checks
            +" syntheticOnly=true"
            +" actualTcp=true"
            +" legacyISOUnchanged=true"
            +" optInWindows1252Correct=true"
            +" strictMismatchRejected=true"
            +" noOriginalClientLaunched=true"
            +" accountsMutated=0");
    }

    private static String exercise(String name,int type,boolean strict,
                                   boolean intentionallyWrongHash)
            throws Exception {
        final InetAddress loop=InetAddress.getByName("127.0.0.1");
        try (ServerSocket listener=new ServerSocket(0,1,loop)) {
            ExecutorService pool=Executors.newSingleThreadExecutor();
            try {
                Future<String> result=pool.submit(() -> {
                    try(Socket peer=listener.accept()){
                        try {
                            LoginFrame frame=LocalLoginTransport.readLogin(
                                peer,peer.getInputStream(),peer.getOutputStream(),
                                "[r31-local-only] ",strict);
                            return frame.username;
                        } catch(IOException expected) {
                            if(!intentionallyWrongHash)throw expected;
                            return expected.getMessage();
                        }
                    }
                });
                try(Socket client=new Socket(loop,listener.getLocalPort())){
                    client.setSoTimeout(5000);
                    OutputStream out=client.getOutputStream();
                    InputStream in=client.getInputStream();
                    int hash=V308PreloginNameHash.hash5(name);
                    if(intentionallyWrongHash)hash=(hash+1)&31;
                    out.write(14);out.write(hash);out.flush();
                    byte[] pre=Binary.readExactly(in,17);
                    for(int i=0;i<9;i++)check(pre[i]==0,
                                               "17-byte prelogin reply");
                    check(Binary.i64(pre,9)==LocalLoginTransport.SERVER_SEED,
                          "v308 login server seed");
                    byte[] wire=R31OriginalV308LoginWireCodecTest.packet(
                        name,"SAFE_TEST_PASS","LOCAL-DEVICE",
                        "LOCAL-CLIENT",CP1252,308,type);
                    out.write(wire);
                    out.flush();
                    client.shutdownOutput();
                }
                String decoded=result.get(5,TimeUnit.SECONDS);
                check(decoded!=null,"transport result present");
                return decoded;
            } finally {
                pool.shutdownNow();
                if(!pool.awaitTermination(5,TimeUnit.SECONDS))
                    throw new AssertionError("R31 local socket worker leaked");
            }
        }
    }

    private static void eq(String expected,String actual,String label){
        check(expected.equals(actual),label);
    }
    private static void check(boolean valid,String label){
        checks++;
        if(!valid)throw new AssertionError("R31 "+label);
    }
    private R31Windows1252RealLoopbackLoginTest(){}
}
