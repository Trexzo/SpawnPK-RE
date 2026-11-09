package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * R29 localhost wire admission — no original GUI, credentials or live port.
 * The actual LocalLoginTransport must reject a client-chosen control-code
 * alias before World registration; regular synthetic LocalLab logins remain
 * byte-compatible with build 308/revision 317.
 */
public final class R29LocalLoginAliasAdmissionTest {
    private static int assertions;
    private static final String SENTINEL = "R29IDENTITYSENTINEL";

    public static void main(String[] args) throws Exception {
        for(String valid: new String[]{
            "local","localtest","opensrc","src","testprofile",
            "R29_Synthetic","Test User-01","x",
            "abcdefghijklmnopqrstuvwx"
        }){
            check(LocalLoginAliasPolicy.requireValid(valid).equals(valid),
                  "valid LocalLab alias changed");
        }
        for(String invalid: new String[]{
            "", " ", " leading", "trailing ", "abcdefghijklmnopqrstuvwxy",
            "\rEXPLOIT", "INJECT\rFOLLOWING", "EVIL\u001b[2J", "bad\talias",
            "bad\u0000alias", "bad\nNEXT", "bad\u0085NEXT",
            "slash/alias", "path\\alias", "period.alias", "nonascii\u00e4",
        }){
            try{
                LocalLoginAliasPolicy.requireValid(invalid);
                throw new AssertionError("R29 invalid login alias accepted");
            }catch(IOException expected){
                check(expected.getMessage().startsWith("invalid local login alias"),
                      "structured refusal");
                check(!expected.getMessage().contains(invalid),
                      "bad alias echoed in IOException");
            }
        }

        check("<redacted>".equals(LocalLoginAliasPolicy.redactForLog(SENTINEL)),
              "transport diagnostics must redact identity");
        check("<redacted>".equals(LocalLoginAliasPolicy.redactForLog("\rCRLF")),
              "diagnostic redaction also removes raw controls");

        checkTransport("local",true);
        checkTransport("testprofile",true);
        checkTransport("R29IDENTITYSENTINEL",true);
        checkTransport("INJECT\rV512_WORLD_REGISTER",false);
        checkTransport("BAD\u001b[31m",false);
        checkTransport("a".repeat(25),false);
        checkTransport("",false);

        // In addition to wire rejection, test one of the downstream
        // account-selection log sites with a legal arbitrary alias.
        World world=World.isolatedForTest(50L);
        PrintStream prior=System.out;
        ByteArrayOutputStream diagnostic=new ByteArrayOutputStream();
        try{
            System.setOut(new PrintStream(diagnostic,true,"UTF-8"));
            LocalAccountLifecycle.Selection selected=
                LocalAccountLifecycle.select(world,SENTINEL,"[r29-test] ");
            check(SENTINEL.equals(selected.username),"alias selection preserved");
            check(!selected.persistent,"nonpersistent alias stays nonpersistent");
        }finally{
            System.setOut(prior);
            world.close();
        }
        String text=diagnostic.toString("UTF-8");
        check(!text.contains(SENTINEL),"downstream profile alias was logged");
        check(text.contains("loginAlias=<redacted>"),"downstream alias masked");

        System.out.println("R29_V308_LOGIN_ALIAS_WIRE_ADMISSION_PASS"
                +" assertions="+assertions
                +" validLocalProfiles=true aliasControlRejected=true"
                +" beforeWorldRegistration=true redactedDiagnostics=true"
                +" originalGuiStarted=false");
    }

    private static void checkTransport(String alias, boolean accepted)
            throws Exception{
        InetAddress loop=InetAddress.getByName("127.0.0.1");
        ExecutorService service=Executors.newSingleThreadExecutor();
        try(ServerSocket listener=new ServerSocket(0,1,loop)){
            Future<Boolean> result=service.submit(()->{
                try(Socket socket=listener.accept()){
                    try{
                        LocalLoginTransport.readLogin(
                            socket,socket.getInputStream(),
                            socket.getOutputStream(),"[r29-wire] ");
                        return true;
                    }catch(IOException expected){
                        if(!expected.getMessage().startsWith("invalid local login alias"))
                            throw new AssertionError("unexpected wire parse failure",expected);
                        if(!alias.isEmpty())
                            check(!expected.getMessage().contains(alias),
                                  "invalid alias leaked into wire exception");
                        return false;
                    }
                }
            });
            try(Socket client=new Socket(loop,listener.getLocalPort())){
                client.setSoTimeout(5000);
                OutputStream out=client.getOutputStream();
                InputStream in=client.getInputStream();
                out.write(14);
                out.write(7);
                out.flush();
                byte[] prefix=Binary.readExactly(in,17);
                check(prefix[8]==0,"prelogin status");
                byte[] data=payload(alias);
                out.write(16);
                out.write(data.length);
                out.write(data);
                out.flush();
            }
            check(result.get(5,TimeUnit.SECONDS)==accepted,
                  "wire alias acceptance mismatch");
        }finally{
            service.shutdownNow();
            service.awaitTermination(3,TimeUnit.SECONDS);
        }
    }

    private static byte[] payload(String alias)throws IOException{
        ByteArrayOutputStream inner=new ByteArrayOutputStream();
        inner.write(10);
        for(int seed:new int[]{11,22,33,44})write32(inner,seed);
        write32(inner,748878668);
        write32(inner,308);
        nl(inner,alias); nl(inner,"syntheticpass");
        nl(inner,"synthetic-device"); nl(inner,"synthetic-client");
        byte[] raw=inner.toByteArray();
        if(raw.length>255)throw new AssertionError("fixture oversize");
        ByteArrayOutputStream frame=new ByteArrayOutputStream();
        frame.write(255);
        frame.write(317>>>8);
        frame.write(317);
        frame.write(0);
        for(int i=0;i<9;i++)write32(frame,0);
        frame.write(raw.length);
        frame.write(raw);
        return frame.toByteArray();
    }

    private static void write32(OutputStream out,int v)throws IOException{
        out.write(v>>>24);
        out.write(v>>>16);
        out.write(v>>>8);
        out.write(v);
    }
    private static void nl(OutputStream out,String value)throws IOException{
        out.write(value.getBytes(StandardCharsets.ISO_8859_1));
        out.write(10);
    }
    private static void check(boolean test,String description){
        assertions++;
        if(!test)throw new AssertionError("R29 "+description);
    }

    private R29LocalLoginAliasAdmissionTest(){}
}
