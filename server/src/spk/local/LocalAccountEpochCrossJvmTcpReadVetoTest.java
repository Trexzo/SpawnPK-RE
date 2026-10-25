package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * R163: distinct JVM inserts R153/R154 negative evidence in an actual
 * LocalSession localhost TCP login read window. Synthetic client only.
 * Neither negative HEAD nor orphan object authorizes admission/recovery.
 */
public final class LocalAccountEpochCrossJvmTcpReadVetoTest {
    enum Evidence { HEAD_AND_OBJECT, ORPHAN_OBJECT_ONLY }
    private static final String USER="opensrc";
    private static int cases;
    private static void check(boolean ok,String what){
        if(!ok)throw new AssertionError("R163 "+what);
    }
    private static byte[] image(Path account){
        return ("R163 independent forced negative object "+
            account.getFileName()).getBytes(StandardCharsets.US_ASCII);
    }
    private static String digest(Path account)throws IOException{
        return LocalAccountEpochHeadCodec.digestSnapshot(image(account));
    }
    private static void publish(Path account,Evidence kind)throws Exception{
        if(kind==Evidence.HEAD_AND_OBJECT){
            LocalAccountEpochNegativeHeadPublication.publish(
                account,new LocalAccountEpochHeadCodec.Record(
                    USER,1,1,163,LocalAccountEpochHeadCodec.State.REVIEW,
                    LocalAccountEpochHeadCodec.ZERO_SHA,digest(account)),
                image(account));
        }else{
            LocalAccountEpochSnapshotObjects.publish(account,image(account));
        }
        System.out.println("R163_FOREIGN_JVM_PUBLISHED "+kind);
        System.out.flush();
    }
    private static String javaBin(){
        return new File(new File(System.getProperty("java.home"),"bin"),
            System.getProperty("os.name").toLowerCase().contains("win")
                ?"java.exe":"java").getAbsolutePath();
    }
    private static void external(Path account,Evidence kind,Path log)
            throws IOException{
        Process p=new ProcessBuilder(javaBin(),"-cp",
            System.getProperty("java.class.path"),
            LocalAccountEpochCrossJvmTcpReadVetoTest.class.getName(),
            "--publish",account.toString(),kind.name())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{
            if(!p.waitFor(30,TimeUnit.SECONDS))
                throw new IOException("R163_FOREIGN_JVM_TIMEOUT");
            String trace=new String(Files.readAllBytes(log),
                StandardCharsets.UTF_8);
            if(p.exitValue()!=0||!trace.contains(
                "R163_FOREIGN_JVM_PUBLISHED "+kind))
                throw new IOException("R163_FOREIGN_JVM_FAILED "+trace);
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IOException("R163_FOREIGN_JVM_INTERRUPTED",e);
        }finally{
            if(p.isAlive()){
                p.destroyForcibly();
                try{p.waitFor(10,TimeUnit.SECONDS);}
                catch(InterruptedException e){Thread.currentThread().interrupt();}
            }
        }
    }
    private static boolean contains(Throwable t,String message){
        for(Throwable x=t;x!=null;x=x.getCause())
            if(String.valueOf(x.getMessage()).contains(message))return true;
        return false;
    }
    private static int tcpAttempt(int port)throws Exception{
        try(Socket socket=new Socket(
            InetAddress.getByName("127.0.0.1"),port)){
            socket.setSoTimeout(5000);
            InputStream in=socket.getInputStream();
            OutputStream out=socket.getOutputStream();
            out.write(14);out.write(7);out.flush();
            byte[] pre=Binary.readExactly(in,9);
            check((pre[8]&255)==0,"prelogin refused");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,"server seed drift");
            int[] seeds={0x01020304,0x11223344,
                (int)(seed>>>32),(int)seed};
            byte[] frame=
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .loginFrame(seeds,USER);
            check(frame.length<256,"oversized login");
            out.write(16);out.write(frame.length);out.write(frame);
            out.flush();
            return in.read();
        }
    }
    private static void oneCase(boolean exists,Evidence kind)
            throws Exception{
        Path root=Files.createTempDirectory("r163-tcp-read-");
        String old=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        World world=null;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            FilePlayerRepository seed=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            if(exists)
                seed.save(PlayerSnapshotCodec.capture(USER,new WorldPlayer()));
            byte[] before=exists?Files.readAllBytes(account):null;
            Object key=exists?Files.readAttributes(account,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                    .fileKey():null;
            Path childLog=root.resolve("publisher.log");
            AtomicInteger injected=new AtomicInteger();
            FilePlayerRepository reader=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                x->{},x->{},x->{
                    check(USER.equals(x),"read selected different account");
                    check(exists?Arrays.equals(before,
                        Files.readAllBytes(account)):
                        !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                        "selected account changed before read callback");
                    injected.incrementAndGet();
                    external(account,kind,childLog);
                });
            world=World.isolatedForTest(50L,reader);
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                int result=tcpAttempt(server.listener.getLocalPort());
                check(result!=2&&world.players().size()==0,
                    "TCP login accepted foreign negative evidence");
                server.waitCompleted();
                check(world.players().size()==0,
                    "denied TCP client attached to World");
            }
            check(injected.get()==1,"TCP never reached selected read");
            Path head=LocalAccountEpochNegativeHeadPublication
                .headPath(account);
            check(Files.exists(head,LinkOption.NOFOLLOW_LINKS)==
                (kind==Evidence.HEAD_AND_OBJECT),"unexpected HEAD");
            LocalAccountEpochSnapshotObjects.verify(
                account,digest(account),null);
            check(exists?Arrays.equals(before,Files.readAllBytes(account))&&
                key.equals(Files.readAttributes(account,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                    .fileKey()):
                !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "denied TCP login mutated legacy account");
            String error=kind==Evidence.HEAD_AND_OBJECT?
                "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY":
                "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION";
            boolean denied=false;
            try{seed.loadForWorldSession(USER);}
            catch(IOException e){denied=contains(e,error);}
            check(denied,"fresh repository accepted negative evidence");
            cases++;
            System.out.println("R163_REAL_TCP_CROSS_JVM_NEGATIVE_PASS"+
                " case="+cases+" existing="+exists+" evidence="+kind+
                " readWindowReached=true WorldAttachDenied=true"+
                " originalAccountPreserved=true noRecoveryOrFinalAck=true");
        }finally{
            if(world!=null&&!world.closed())world.close();
            if(old==null)System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",old);
            try(Stream<Path> entries=Files.walk(root)){
                for(Path x:entries.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(x);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--publish".equals(args[0])){
            publish(Paths.get(args[1]),Evidence.valueOf(args[2]));
            return;
        }
        check(args.length==0,"unexpected CLI");
        for(boolean existing:new boolean[]{false,true})
            for(Evidence kind:Evidence.values())oneCase(existing,kind);
        check(cases==4,"expected four TCP negative-read cases");
        System.out.println("R163_REAL_TCP_CROSS_JVM_NEGATIVE_MATRIX_PASS"+
            " cases=4 noOriginalClientOrPowerLossProof=true");
    }
}
