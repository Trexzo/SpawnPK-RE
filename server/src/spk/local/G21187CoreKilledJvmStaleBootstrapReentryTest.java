package spk.local;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.187: process-kill NEGATIVE acceptance. A genuine socket session
 * modifies World state after a confirmed BOOTSTRAP account publication,
 * but its JVM is killed before final save. The OS lock then vanishes,
 * admitting a second real TCP session into the stale BOOTSTRAP bytes.
 *
 * No recovery, safe-replay, durable account fence or original client
 * behavior is implied. Uses only disposable, test-private account data.
 */
public final class G21187CoreKilledJvmStaleBootstrapReentryTest {
    private static final String USER="g21187-killed";
    private static final String NS="core_killed_session";
    private static final String KEY="phase";
    private static final String UNSAVED="KILLED_UNSAVED";
    private static final String STORED="extension."+NS+"."+KEY;

    private G21187CoreKilledJvmStaleBootstrapReentryTest(){}

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.187 "+why);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition c,String label,long millis)
        throws Exception{
        long deadline=System.nanoTime()+
            TimeUnit.MILLISECONDS.toNanos(millis);
        while(System.nanoTime()<deadline){
            if(c.ready())return;
            Thread.sleep(10L);
        }
        check(c.ready(),"timeout "+label);
    }

    private static long completed(World world){
        String m=world.persistence().metrics();
        String key=",completed=";
        int at=m.indexOf(key);
        check(at>=0,"no completed persistence metric");
        int start=at+key.length(),end=m.indexOf(',',start);
        check(end>start,"malformed completed metric");
        return Long.parseLong(m.substring(start,end));
    }

    private static void change(World world,WorldPlayer owner,String value)
        throws Exception{
        world.submit(owner,owner.generation(),()->{
            owner.snapshotExtensions().replaceNamespace(
                NS,Collections.singletonMap(KEY,value));
        }).get(5L,TimeUnit.SECONDS);
        check(value.equals(owner.snapshotExtensions()
            .namespace(NS).get(KEY)),"World mutation not applied");
    }

    private static String diskPhase(FilePlayerRepository repo)
        throws Exception{
        Optional<PlayerSnapshot> snapshot=repo.load(USER);
        check(snapshot.isPresent(),"account not available on disk");
        return snapshot.get().values().get(STORED);
    }

    private static int attemptedLogin(int port)throws Exception{
        try(Socket socket=new Socket(
            InetAddress.getByName("127.0.0.1"),port)){
            socket.setSoTimeout(5000);
            InputStream in=socket.getInputStream();
            OutputStream out=socket.getOutputStream();
            out.write(14);
            out.write(7);
            out.flush();
            byte[] pre=Binary.readExactly(in,9);
            check((pre[8]&255)==0,"prelogin mismatch");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,
                "unexpected login seed");
            int[] seeds={
                0x01020304,0x11223344,
                (int)(seed>>>32),(int)seed
            };
            byte[] frame=
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .loginFrame(seeds,USER);
            out.write(16);
            out.write(frame.length);
            out.write(frame);
            out.flush();
            return in.read();
        }
    }

    private static void holder(Path ready)throws Exception{
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(1_000L,repo);
        try{
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(Socket socket=listener.login(USER)){
                    await(()->world.players().byName(USER)!=null,
                        "holder real socket registration",8000L);
                    await(()->completed(world)>=1L,
                        "holder BOOTSTRAP task completed",8000L);
                    Path account=LocalAccountProfiles.accountFile(USER);
                    check(Files.isRegularFile(
                        account,LinkOption.NOFOLLOW_LINKS),
                        "holder BOOTSTRAP file not committed");
                    check(diskPhase(repo)==null,
                        "unsaved extension was already on disk");
                    WorldPlayer owner=world.players().byName(USER);
                    change(world,owner,UNSAVED);
                    check(LocalJvmAccountSessionLease
                        .activeCountForTest()==1,
                        "live holder token missing");

                    // The parent observes this file and FORCE-KILLS
                    // the JVM. No normal socket closure/final save.
                    Files.write(ready,
                        "G21187_HOLDER_READY".getBytes(
                            StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                    System.out.println(
                        "G21187_HOLDER_READY"+
                        " bootstrapCommitted=true"+
                        " WorldMutationUnpublished=true"+
                        " actualTcpSocketHeld=true");
                    System.out.flush();
                    Thread.sleep(120_000L);
                    throw new AssertionError(
                        "G21.187 holder was not killed before save");
                }
            }
        }finally{
            world.close();
        }
    }

    private static void denied()throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                check(attemptedLogin(
                    listener.listener.getLocalPort())!=2,
                    "competing actual socket was admitted");
                listener.waitCompleted();
            }
            check(world.players().size()==0,
                "denied child registered account player");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "denied process retained account token");
            System.out.println(
                "G21187_CHILD_BEFORE_KILL_DENIED"+
                " liveCrossProcessOwnership=true");
        }finally{
            world.close();
        }
    }

    private static void relogin()throws Exception{
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        check(diskPhase(repo)==null,
            "stale BOOTSTRAP should lack killed World mutation");
        World world=World.isolatedForTest(50L,repo);
        try{
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(Socket socket=listener.login(USER)){
                    await(()->world.players().byName(USER)!=null,
                        "fresh JVM stale account socket admitted",8000L);
                    WorldPlayer owner=world.players().byName(USER);
                    check(owner.snapshotExtensions()
                        .namespace(NS).get(KEY)==null,
                        "fresh World unexpectedly recovered killed mutation");
                    check(LocalJvmAccountSessionLease
                        .activeCountForTest()==1,
                        "fresh JVM token not acquired");
                    change(world,owner,"AFTER_KILL_RELOGIN");
                }
                await(()->world.players().size()==0,
                    "fresh JVM final logout",8000L);
                listener.waitCompleted();
            }
            check("AFTER_KILL_RELOGIN".equals(diskPhase(repo)),
                "fresh JVM did not save its new post-kill state");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "fresh JVM token leaked");
            System.out.println(
                "G21187_CHILD_AFTER_KILL_ADMITTED"+
                " staleBootstrapHydrated=true"+
                " killedWorldMutationLost=true"+
                " freshFinalSaveCommitted=true");
        }finally{
            world.close();
        }
    }

    private static Process startChild(
        Path settings,Path transcript,String mode,Path ready
    )throws Exception{
        String exe=System.getProperty("java.home")+
            java.io.File.separator+"bin"+java.io.File.separator+
            (System.getProperty("os.name","").toLowerCase()
                .contains("win")?"java.exe":"java");
        ProcessBuilder b=new ProcessBuilder(
            exe,
            "-Dspk.local.accountFile="+settings,
            "-D"+LocalJvmAccountSessionLease.PROPERTY+"=true",
            "-D"+LocalCrossJvmAccountSessionLock.PROPERTY+"=true",
            "-cp",System.getProperty("java.class.path"),
            G21187CoreKilledJvmStaleBootstrapReentryTest.class.getName(),
            "--child",mode,ready.toString()
        );
        b.redirectErrorStream(true);
        b.redirectOutput(transcript.toFile());
        return b.start();
    }

    private static void finishedChild(
        Process p,Path transcript,String marker
    )throws Exception{
        if(!p.waitFor(25L,TimeUnit.SECONDS)){
            p.destroyForcibly();
            p.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError(
                "G21.187 child timed out marker="+marker);
        }
        String out=new String(
            Files.readAllBytes(transcript),StandardCharsets.UTF_8);
        check(p.exitValue()==0,
            "child failed exit="+p.exitValue()+" output="+out);
        check(out.contains(marker),
            "child missing "+marker+" output="+out);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21187-crash-");
        Path settings=root.resolve("opensrc.properties");
        Path ready=root.resolve("holder.ready");
        Path holderLog=root.resolve("holder.log");
        Path deniedLog=root.resolve("denied.log");
        Path afterLog=root.resolve("relogin.log");

        String oldFile=System.getProperty("spk.local.accountFile");
        String oldJvm=System.getProperty(
            LocalJvmAccountSessionLease.PROPERTY);
        String oldCross=System.getProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY);
        System.setProperty("spk.local.accountFile",settings.toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        Process holderProcess=null;
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            Path file=LocalAccountProfiles.accountFile(USER);
            Path lock=file.resolveSibling(
                file.getFileName().toString()+".session.lck");
            Path alias=LocalAccountProfiles.accountFile("src");
            check(!Files.exists(file,LinkOption.NOFOLLOW_LINKS),
                "account fixture already exists");
            holderProcess=startChild(settings,holderLog,
                "holder",ready);
            final Process liveHolder=holderProcess;
            await(()->Files.isRegularFile(
                ready,LinkOption.NOFOLLOW_LINKS),
                "holder genuine socket and unsaved World state",15000L);
            check(liveHolder.isAlive(),
                "holder died before forced process crash");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "holder OS lockfile missing");
            check(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),
                "BOOTSTRAP account missing before kill");
            check(diskPhase(repo)==null,
                "killed candidate mutation already saved");
            byte[] bootstrap=Files.readAllBytes(file);
            check(bootstrap.length>0,"BOOTSTRAP empty");

            Process denied=startChild(settings,deniedLog,
                "denied",ready);
            finishedChild(denied,deniedLog,
                "G21187_CHILD_BEFORE_KILL_DENIED");
            check(liveHolder.isAlive(),
                "holder died before explicit kill action");
            check(Arrays.equals(bootstrap,Files.readAllBytes(file)),
                "competing login changed BOOTSTRAP bytes");

            liveHolder.destroyForcibly();
            check(liveHolder.waitFor(10L,TimeUnit.SECONDS),
                "forced holder process did not exit");
            check(liveHolder.exitValue()!=0,
                "holder exit looked like graceful shutdown");
            holderProcess=null;
            check(Arrays.equals(bootstrap,Files.readAllBytes(file)),
                "killed holder unexpectedly published final snapshot");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "process death removed the stable lockfile");

            // This is the intended NEGATIVE finding: the new JVM is
            // admitted to stale bytes because OS releases file locks
            // after process death and there is NO durable negative.
            Process next=startChild(settings,afterLog,
                "relogin",ready);
            finishedChild(next,afterLog,
                "G21187_CHILD_AFTER_KILL_ADMITTED");
            check("AFTER_KILL_RELOGIN".equals(diskPhase(repo)),
                "stale post-kill login did not save its own update");
            check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "secondary src profile unexpectedly created");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "new process unlinked session lockfile");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "test launcher leaked JVM session token");

            System.out.println(
                "G21187_KILLED_JVM_DIAGNOSTICS"+
                " realTcpHolder=true competingChildDenied=true"+
                " forcedProcessKill=true abruptFinalSaveMissing=true"+
                " bootstrapBytesUnchanged=true"+
                " crossProcessLockReleasedByOs=true"+
                " freshJvmActualTcpLoginAllowed=true"+
                " killedWorldMutationMissing=true"+
                " freshJvmNewSnapshotCommitted=true");
            System.out.println(
                "G21187_KILLED_JVM_PASS"+
                " negativeStaleBootstrapReentryProven=true"+
                " crashDurableAccountNegative=false"+
                " safeAutomaticRecovery=false"+
                " crossProcessLockIsDurableIntent=false"+
                " rawWriterExclusion=false"+
                " originalGraphicalClientProven=false"+
                " settlement=false grants=false replay=false"+
                " cleanup=false rollover=false unlink=false"+
                " durableSessionRelease=false ack=false");
        }finally{
            if(holderProcess!=null&&holderProcess.isAlive()){
                holderProcess.destroyForcibly();
                holderProcess.waitFor(5L,TimeUnit.SECONDS);
            }
            if(oldFile==null)
                System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",oldFile);
            if(oldJvm==null)
                System.clearProperty(LocalJvmAccountSessionLease.PROPERTY);
            else System.setProperty(
                LocalJvmAccountSessionLease.PROPERTY,oldJvm);
            if(oldCross==null)
                System.clearProperty(
                    LocalCrossJvmAccountSessionLock.PROPERTY);
            else System.setProperty(
                LocalCrossJvmAccountSessionLock.PROPERTY,oldCross);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0])){
            if("holder".equals(args[1]))holder(
                java.nio.file.Paths.get(args[2]));
            else if("denied".equals(args[1]))denied();
            else if("relogin".equals(args[1]))relogin();
            else throw new IllegalArgumentException(
                "unknown child mode");
        }else{
            check(args.length==0,"unexpected args");
            run();
        }
    }
}
