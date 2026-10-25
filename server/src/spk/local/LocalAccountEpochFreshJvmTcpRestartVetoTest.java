package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R164: a fresh independent JVM creates a new World and attempts a
 * real LocalSession localhost TCP login after untrusted evidence has
 * survived the original publisher JVM. Neither complete nor malformed
 * HEAD, nor orphan object alone, may rehydrate a stale legacy account.
 *
 * Deliberately negative only. No original game GUI, OS power-loss,
 * automatic epoch recovery, settlement, owner handoff or final-save ACK.
 */
public final class LocalAccountEpochFreshJvmTcpRestartVetoTest {
    private enum Evidence { COMPLETE_HEAD, MALFORMED_HEAD, ORPHAN_OBJECT, CLEAN }
    private static final String USER="opensrc";
    private static final byte[] MALFORMED=
        "R164_INCOMPLETE_UNTRUSTED_HEAD".getBytes(StandardCharsets.US_ASCII);
    private static int cases;

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("R164 "+why);
    }

    private static byte[] snapshot(Path account){
        return ("R164 immutable orphan and restart negative object "+
            account.getFileName()).getBytes(StandardCharsets.US_ASCII);
    }

    private static String digest(Path account)throws IOException{
        return LocalAccountEpochHeadCodec.digestSnapshot(snapshot(account));
    }

    private static void createNegative(Path account,Evidence evidence)
            throws IOException{
        if(evidence==Evidence.COMPLETE_HEAD){
            LocalAccountEpochNegativeHeadPublication.publish(
                account,new LocalAccountEpochHeadCodec.Record(
                    USER,1,1,164,LocalAccountEpochHeadCodec.State.ACTIVE,
                    LocalAccountEpochHeadCodec.ZERO_SHA,digest(account)),
                snapshot(account));
        }else if(evidence==Evidence.MALFORMED_HEAD){
            Files.write(
                LocalAccountEpochNegativeHeadPublication.headPath(account),
                MALFORMED,StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        }else if(evidence==Evidence.ORPHAN_OBJECT){
            LocalAccountEpochSnapshotObjects.publish(
                account,snapshot(account));
        }
    }

    private static boolean matches(Throwable error,String needle){
        for(Throwable t=error;t!=null;t=t.getCause())
            if(String.valueOf(t.getMessage()).contains(needle))return true;
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
            check((pre[8]&255)==0,"prelogin handshake");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,"server seed");
            int[] seeds={0x01020304,0x11223344,
                (int)(seed>>>32),(int)seed};
            byte[] frame=
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .loginFrame(seeds,USER);
            out.write(16);out.write(frame.length);out.write(frame);
            out.flush();
            return in.read();
        }
    }

    private static void child(Path account,Evidence evidence)
            throws Exception{
        System.setProperty("spk.local.accountFile",account.toString());
        String[] experimental={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        for(String property:experimental)System.clearProperty(property);
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        if(evidence!=Evidence.CLEAN){
            boolean denied=false;
            String reason=evidence==Evidence.ORPHAN_OBJECT?
                "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION":
                "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY";
            try{repo.loadForWorldSession(USER);}
            catch(IOException ex){denied=matches(ex,reason);}
            check(denied,"read-only fresh JVM did not see exact negative");
        }else{
            check(repo.loadForWorldSession(USER).isPresent(),
                "healthy control account could not be loaded");
        }
        World world=World.isolatedForTest(50L,repo);
        try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(world)){
            if(evidence==Evidence.CLEAN){
                try(Socket socket=server.login(USER)){
                    long deadline=System.nanoTime()+
                        TimeUnit.SECONDS.toNanos(5);
                    while(world.players().byName(USER)==null&&
                            System.nanoTime()<deadline)
                        Thread.sleep(10L);
                    check(world.players().byName(USER)!=null,
                        "healthy TCP control did not attach World player");
                }
                server.waitCompleted();
                System.out.println("R164_CLEAN_TCP_CONTROL_ACCEPTED");
            }else{
                int reply=tcpAttempt(server.listener.getLocalPort());
                check(reply!=2&&world.players().size()==0,
                    "new World admitted retained negative evidence");
                server.waitCompleted();
                check(world.players().size()==0,
                    "negative TCP login attached a World player");
                System.out.println(
                    "R164_FRESH_PROCESS_TCP_NEGATIVE_DENIED evidence="+
                    evidence+" noRecoveryOrFinalAck=true");
            }
            System.out.flush();
        }finally{
            if(!world.closed())world.close();
        }
    }

    private static String javaExecutable(){
        return new File(new File(System.getProperty("java.home"),"bin"),
            System.getProperty("os.name").toLowerCase().contains("win")
            ?"java.exe":"java").getAbsolutePath();
    }

    private static void independent(Path account,Evidence kind,Path log)
            throws Exception{
        Process process=new ProcessBuilder(javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            LocalAccountEpochFreshJvmTcpRestartVetoTest.class.getName(),
            "--fresh-world",account.toString(),kind.name())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{
            if(!process.waitFor(40L,TimeUnit.SECONDS))
                throw new IOException("R164_FRESH_WORLD_TIMEOUT");
            String trace=new String(Files.readAllBytes(log),
                StandardCharsets.UTF_8);
            String expected=kind==Evidence.CLEAN?
                "R164_CLEAN_TCP_CONTROL_ACCEPTED":
                "R164_FRESH_PROCESS_TCP_NEGATIVE_DENIED evidence="+kind;
            check(process.exitValue()==0&&trace.contains(expected),
                "independent World did not match expectation "+
                "exit="+process.exitValue()+" log="+trace);
        }finally{
            if(process.isAlive()){
                process.destroyForcibly();
                process.waitFor(10L,TimeUnit.SECONDS);
            }
        }
    }

    private static void scenario(Evidence kind,boolean exists)
            throws Exception{
        Path root=Files.createTempDirectory("r164-fresh-world-restart-");
        String old=System.getProperty("spk.local.accountFile");
        Path account=root.resolve("opensrc.properties");
        System.setProperty("spk.local.accountFile",account.toString());
        try{
            FilePlayerRepository seed=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            if(exists){
                seed.save(PlayerSnapshotCodec.capture(
                    USER,new WorldPlayer()));
            }
            byte[] before=exists?Files.readAllBytes(account):null;
            Object beforeKey=exists?Files.readAttributes(account,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                    .fileKey():null;
            createNegative(account,kind);
            Path head=LocalAccountEpochNegativeHeadPublication.headPath(
                account);
            boolean headExpected=kind==Evidence.COMPLETE_HEAD||
                kind==Evidence.MALFORMED_HEAD;
            byte[] headBytes=headExpected?Files.readAllBytes(head):null;
            independent(account,kind,root.resolve("fresh-world.log"));
            if(exists){
                check(Arrays.equals(before,Files.readAllBytes(account))&&
                    beforeKey.equals(Files.readAttributes(account,
                        BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                        .fileKey()),
                    "fresh negative World changed old account bytes");
            }else{
                check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                    "fresh negative World created absent account");
            }
            check(Files.exists(head,LinkOption.NOFOLLOW_LINKS)==headExpected&&
                (!headExpected||Arrays.equals(
                    headBytes,Files.readAllBytes(head))),
                "negative HEAD was rewritten/removed");
            if(kind==Evidence.COMPLETE_HEAD||
                    kind==Evidence.ORPHAN_OBJECT)
                LocalAccountEpochSnapshotObjects.verify(
                    account,digest(account),null);
            cases++;
            System.out.println("R164_INDEPENDENT_WORLD_RESTART_DENIED_PASS"+
                " case="+cases+" exists="+exists+" evidence="+kind+
                " immutableForensicsRetained=true"+
                " noAccountBootstrapOrStaleAdmission=true");
        }finally{
            if(old==null)System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",old);
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--fresh-world".equals(args[0])){
            child(Paths.get(args[1]),Evidence.valueOf(args[2]));
            return;
        }
        check(args.length==0,"unexpected CLI");
        for(boolean exists:new boolean[]{false,true})
            for(Evidence kind:new Evidence[]{
                Evidence.COMPLETE_HEAD,Evidence.MALFORMED_HEAD,
                Evidence.ORPHAN_OBJECT})
                scenario(kind,exists);
        // Prove the independent localhost transport actually accepts
        // a clean control. A generic broken TCP login is not success.
        Path healthy=Files.createTempDirectory("r164-healthy-control-");
        String old=System.getProperty("spk.local.accountFile");
        try{
            Path account=healthy.resolve("opensrc.properties");
            System.setProperty("spk.local.accountFile",account.toString());
            new FilePlayerRepository(LocalAccountProfiles::accountFile)
                .save(PlayerSnapshotCodec.capture(USER,new WorldPlayer()));
            independent(account,Evidence.CLEAN,
                healthy.resolve("fresh-clean-world.log"));
        }finally{
            if(old==null)System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",old);
            try(Stream<Path> walk=Files.walk(healthy)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
        check(cases==6,"expected six denied fresh-World scenarios");
        System.out.println("R164_FRESH_JVM_TCP_RESTART_NEGATIVE_MATRIX_PASS"+
            " deniedCases=6 healthyTcpControl=1"+
            " independentWorldProcesses=7 noRecoveryOrFinalAck=true"+
            " noOriginalGraphicalClientOrPowerLossProof=true");
    }
}
