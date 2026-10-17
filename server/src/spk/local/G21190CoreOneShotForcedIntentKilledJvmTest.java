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
 * G21.190 positive narrow one-shot admission witness:
 * forced ACTIVE marker is published before real World membership.
 * A holder Java process is killed after BOOTSTRAP and unsaved World
 * mutation; fresh JVM socket login to the stale account is DENIED.
 *
 * Opt-in only, marker NEVER automatically retired even after a
 * successful final save. NOT a usable crash-safe account lifecycle.
 */
public final class G21190CoreOneShotForcedIntentKilledJvmTest {
    private static final String USER="g21190-killed";
    private static final String NS="core_one_shot_session";
    private static final String KEY="phase";
    private static final String UNSAVED="KILLED_UNSAVED";
    private static final String STORED="extension."+NS+"."+KEY;

    private G21190CoreOneShotForcedIntentKilledJvmTest(){}

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.190 "+why);
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
                        "G21190_HOLDER_READY".getBytes(
                            StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                    System.out.println(
                        "G21190_HOLDER_READY"+
                        " bootstrapCommitted=true"+
                        " WorldMutationUnpublished=true"+
                        " actualTcpSocketHeld=true");
                    System.out.flush();
                    Thread.sleep(120_000L);
                    throw new AssertionError(
                        "G21.190 holder was not killed before save");
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
                "G21190_CHILD_BEFORE_KILL_DENIED"+
                " liveCrossProcessOwnership=true");
        }finally{
            world.close();
        }
    }

    private static void afterKillDenied()throws Exception{
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        check(diskPhase(repo)==null,
            "old bootstrap must lack killed World mutation");
        Path marker=LocalDurableAccountReviewIntentPrimitive
            .markerPath(LocalAccountProfiles.accountFile(USER));
        LocalDurableAccountReviewIntentPrimitive.requireReviewMarker(
            LocalAccountProfiles.accountFile(USER));
        byte[] before=Files.readAllBytes(marker);
        World world=World.isolatedForTest(50L,repo);
        try{
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                check(attemptedLogin(
                    listener.listener.getLocalPort())!=2,
                    "fresh JVM admitted stale account despite marker");
                listener.waitCompleted();
            }
            check(world.players().size()==0,
                "post-kill refused login registered a World owner");
            check(diskPhase(repo)==null,
                "post-kill refused login changed stale snapshot");
            check(Arrays.equals(before,Files.readAllBytes(marker)),
                "post-kill refused login changed ACTIVE marker");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "post-kill denied session retained a lease");
            System.out.println(
                "G21190_CHILD_AFTER_KILL_DENIED"+
                " actualTcpLoginRejected=true"+
                " stableForcedIntent=true"+
                " noHydrationOrFinalSave=true");
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
            "-D"+LocalReviewIntentAdmissionVeto.PROPERTY+"=true",
            "-D"+LocalOneShotActiveReviewOnAdmission.PROPERTY+"=true",
            "-cp",System.getProperty("java.class.path"),
            G21190CoreOneShotForcedIntentKilledJvmTest.class.getName(),
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
                "G21.190 child timed out marker="+marker);
        }
        String out=new String(
            Files.readAllBytes(transcript),StandardCharsets.UTF_8);
        check(p.exitValue()==0,
            "child failed exit="+p.exitValue()+" output="+out);
        check(out.contains(marker),
            "child missing "+marker+" output="+out);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21190-crash-");
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
        String oldReview=System.getProperty(
            LocalReviewIntentAdmissionVeto.PROPERTY);
        String oldOneShot=System.getProperty(
            LocalOneShotActiveReviewOnAdmission.PROPERTY);
        System.setProperty("spk.local.accountFile",settings.toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalReviewIntentAdmissionVeto.PROPERTY,"true");
        System.setProperty(
            LocalOneShotActiveReviewOnAdmission.PROPERTY,"true");
        Process holderProcess=null;
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            Path file=LocalAccountProfiles.accountFile(USER);
            Path lock=file.resolveSibling(
                file.getFileName().toString()+".session.lck");
            Path alias=LocalAccountProfiles.accountFile("src");
            Path marker=LocalDurableAccountReviewIntentPrimitive
                .markerPath(file);
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
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(file);
            byte[] forcedIntent=Files.readAllBytes(marker);
            check(forcedIntent.length>0,
                "one-shot ACTIVE marker not published");

            Process denied=startChild(settings,deniedLog,
                "denied",ready);
            finishedChild(denied,deniedLog,
                "G21190_CHILD_BEFORE_KILL_DENIED");
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

            // The one-shot forced sidecar survives owner process death.
            // The OS lock is gone, but this OPTIONAL new admission gate
            // rejects stale BOOTSTRAP instead of treating it as clean.
            check(Arrays.equals(
                forcedIntent,Files.readAllBytes(marker)),
                "forced intent bytes changed after process kill");
            Process next=startChild(settings,afterLog,
                "after-kill-denied",ready);
            finishedChild(next,afterLog,
                "G21190_CHILD_AFTER_KILL_DENIED");
            check(Arrays.equals(bootstrap,Files.readAllBytes(file)),
                "post-kill denied login changed stale account");
            check(Arrays.equals(
                forcedIntent,Files.readAllBytes(marker)),
                "post-kill denied login changed intent");
            check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "secondary src profile unexpectedly created");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "new process unlinked session lockfile");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "test launcher leaked JVM session token");

            System.out.println(
                "G21190_ONE_SHOT_DIAGNOSTICS"+
                " realTcpHolder=true competingChildDenied=true"+
                " forcedProcessKill=true bootstrapBytesUnchanged=true"+
                " activeReviewWrittenBeforeAdmission=true"+
                " activeReviewBytesSurvivedKill=true"+
                " freshJvmSocketLoginDenied=true"+
                " staleBootstrapNotRehydrated=true");
            System.out.println(
                "G21190_ONE_SHOT_PASS"+
                " optInOneShotForcedBeforeRegistration=true"+
                " crashNegativeDenialForMarkedAccount=true"+
                " markerNeverAutoRetired=true"+
                " defaultOff=true"+
                " healthyAccountReusableWithoutManualReview=false"+
                " generalCrashDurableAccountReceipt=false"+
                " rawWriterExclusion=false"+
                " powerLossCutpointsProven=false"+
                " originalGraphicalClientProven=false"+
                " grants=false replay=false recovery=false");
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
            if(oldReview==null)
                System.clearProperty(
                    LocalReviewIntentAdmissionVeto.PROPERTY);
            else System.setProperty(
                LocalReviewIntentAdmissionVeto.PROPERTY,oldReview);
            if(oldOneShot==null)
                System.clearProperty(
                    LocalOneShotActiveReviewOnAdmission.PROPERTY);
            else System.setProperty(
                LocalOneShotActiveReviewOnAdmission.PROPERTY,oldOneShot);
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
            else if("after-kill-denied".equals(args[1]))afterKillDenied();
            else throw new IllegalArgumentException(
                "unknown child mode");
        }else{
            check(args.length==0,"unexpected args");
            run();
        }
    }
}
