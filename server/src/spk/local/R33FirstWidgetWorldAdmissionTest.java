package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R33 authentic-shaped (but synthetic) v308 first-widget traffic through
 * *real* LocalSession + isolated World, on ephemeral localhost TCP.
 * No original JAR, account credentials or external network, with a private
 * temporary FilePlayerRepository and no owner account/Windows firewall I/O.
 */
public final class R33FirstWidgetWorldAdmissionTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path temp=Files.createTempDirectory("spawnpk-r33-session-");
        String property="spk.local.accountFile";
        String old=System.getProperty(property);
        try {
            System.setProperty(property,temp.resolve("r33-account.properties").toString());
            attempt(temp,185,912,true,false);
            for(int wrong:new int[]{0,152,911,913,65535}){
                attempt(temp,185,wrong,false,false);
            }
            attempt(temp,184,912,false,false);
            attempt(temp,185,912,false,true);
            // No first opcode at all: immediate EOF and idle timeout must
            // both exit without registering or saving a WorldPlayer.
            attempt(temp,-1,0,false,false);
            attempt(temp,-2,0,false,false);
            System.out.println("R33_REAL_LOCALSESSION_FIRST_WIDGET_PASS assertions="
                +assertions
                +" correct185Widget912Bootstraps=true"
                +" incorrectWidgetsNoBootstrap=5"
                +" badFirstOpcodeNoBootstrap=true"
                +" truncatedWidgetNoBootstrap=true"
                +" noWorldBeforeValidFirstWidget=true"
                +" idleTimeoutAndImmediateEofNoWorld=true"
                +" worldMembershipAndTickTargetsClean=true"
                +" originalGuiLaunched=false");
        } finally {
            if(old==null)System.clearProperty(property);
            else System.setProperty(property,old);
            try(Stream<Path> walk=Files.walk(temp)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{
                    try {Files.deleteIfExists(p);}
                    catch(Exception e){throw new IllegalStateException(
                        "R33 private temporary test cleanup failed",e);}
                });
            }
        }
    }

    private static void attempt(Path temp,int opcode,int widget,
                                boolean expectBootstrap,boolean truncated)
            throws Exception {
        InetAddress local=InetAddress.getByName("127.0.0.1");
        FilePlayerRepository repo=new FilePlayerRepository(
            name -> temp.resolve("r33-"+name+".properties"));
        try(World world=World.isolatedForTest(600,repo);
            ServerSocket ss=new ServerSocket(0,1,local)) {
            ExecutorService executor=Executors.newSingleThreadExecutor();
            try {
                Future<?> session=executor.submit(() -> {
                    try {
                        new LocalSession(ss.accept(),true,false,world).run();
                    } catch(java.io.IOException e) {
                        throw new IllegalStateException("R33 server socket",e);
                    }
                });
                try(Socket client=new Socket(local,ss.getLocalPort())) {
                    // Initial full LocalSession dependency initialization can
                    // take longer on the first cold Java11 JVM than a bare
                    // LocalLoginTransport; preserve strict bounded admission.
                    client.setSoTimeout(15000);
                    InputStream input=client.getInputStream();
                    OutputStream out=client.getOutputStream();
                    out.write(14);
                    out.write(V308PreloginNameHash.hash5("local"));
                    out.flush();
                    byte[] pre;
                    try {
                        pre=Binary.readExactly(input,17);
                    } catch(java.net.SocketTimeoutException timeout) {
                        // Surface an asynchronous server-constructor failure
                        // instead of disguising it as a protocol timeout.
                        if(session.isDone())session.get(2,TimeUnit.SECONDS);
                        throw new AssertionError("R33 prelogin timeout while server worker still initializing",timeout);
                    }
                    for(int i=0;i<9;i++)check(pre[i]==0,
                                              "prelogin status byte");
                    long serverSeed=Binary.i64(pre,9);
                    check(serverSeed==LocalLoginTransport.SERVER_SEED,
                          "prelogin server seed");
                    int[] seeds={0x01020304,0x11223344,
                        (int)(serverSeed>>>32),(int)serverSeed};
                    byte[] login=payload(seeds);
                    out.write(16);out.write(login.length);out.write(login);out.flush();
                    byte[] response=Binary.readExactly(input,3);
                    check((response[0]&255)==2 &&
                          (response[1]&255)==205 &&
                          (response[2]&255)==0,
                          "v308 genuine login success triplet");
                    // The original R32 code registered a player *before*
                    // reading any first widget. This assertion catches even
                    // transient early registration, not just final cleanup.
                    check(world.players().size()==0,
                          "no WorldPlayer before verified first widget");
                    check(world.tickTargetsSnapshot().isEmpty(),
                          "no world tick target before admission");
                    IsaacCipher c2s=new IsaacCipher(seeds.clone());
                    if(opcode==-1){
                        client.shutdownOutput(); // readFirst185() returns EOF.
                    }else if(opcode==-2){
                        // Write nothing: server's 5-second first-widget
                        // timeout must close, not attach an idle tick target.
                    }else{
                        out.write((opcode+c2s.nextInt())&255);
                        if(truncated){
                            out.write(widget>>>8);
                            out.flush();
                            client.shutdownOutput();
                        }else{
                            out.write(widget>>>8);out.write(widget&255);out.flush();
                        }
                    }

                    if(expectBootstrap) {
                        int[] inboundSeeds=seeds.clone();
                        for(int i=0;i<4;i++)inboundSeeds[i]+=50;
                        IsaacCipher s2c=new IsaacCipher(inboundSeeds);
                        int raw=input.read();
                        check(raw>=0 &&
                              ((raw-s2c.nextInt())&255)==249,
                              "valid original first widget causes real opcode249 bootstrap");
                    } else {
                        // The server must close, *without* publishing
                        // even one initial gameplay byte after the success
                        // triplet, when malformed first-widget traffic arrives.
                        int raw=input.read();
                        check(raw==-1,
                              "invalid first widget must close before world bootstrap bytes");
                    }
                }
                session.get(7,TimeUnit.SECONDS);
                check(world.players().size()==0,
                      "no leaked WorldPlayer after session close");
                check(world.tickTargetsSnapshot().size()==0,
                      "no leaked world tick target after session close");
            } finally {
                executor.shutdownNow();
                if(!executor.awaitTermination(7,TimeUnit.SECONDS))
                    throw new AssertionError("R33 session worker did not finish");
            }
        }
    }

    private static byte[] payload(int[] seeds)throws Exception {
        ByteArrayOutputStream inner=new ByteArrayOutputStream();
        DataOutputStream data=new DataOutputStream(inner);
        data.writeByte(10);
        for(int seed:seeds)data.writeInt(seed);
        data.writeInt(748878668);
        data.writeInt(308);
        for(String text:new String[]{"local","R33_SYNTHETIC_PASSWORD",
                                      "R33_SYNTHETIC_DEVICE","R33_SYNTHETIC_CLIENT"}) {
            data.write(text.getBytes(StandardCharsets.ISO_8859_1));
            data.writeByte(10);
        }
        data.flush();
        byte[] body=inner.toByteArray();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream wire=new DataOutputStream(bytes);
        wire.writeByte(255);wire.writeShort(317);wire.writeByte(0);
        for(int i=0;i<9;i++)wire.writeInt(0);
        wire.writeByte(body.length);wire.write(body);wire.flush();
        byte[] frame=bytes.toByteArray();
        if(frame.length>255)throw new AssertionError("R33 fixture too large");
        return frame;
    }

    private static void check(boolean passed,String message) {
        assertions++;
        if(!passed)throw new AssertionError("R33 "+message);
    }

    private R33FirstWidgetWorldAdmissionTest(){}
}
