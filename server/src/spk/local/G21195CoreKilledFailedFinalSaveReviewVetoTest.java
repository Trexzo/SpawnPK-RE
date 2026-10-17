package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.195: actual forced World BOOTSTRAP + failed final-save in holder
 * JVM, then kill that JVM and verify a fresh external JVM rejects the
 * stale account because the forced ACTIVE review intent survives.
 *
 * This is experimental fail-closed admission evidence, not recovery.
 */
public final class G21195CoreKilledFailedFinalSaveReviewVetoTest {
    private static final String USER="g21195-cross-jvm-fail";
    private static final String NS="core_g21195";
    private static final String KEY="phase";
    private static final String LOST="UNSAVED_BEFORE_FAILURE";
    private static final String STORED="extension."+NS+"."+KEY;

    private G21195CoreKilledFailedFinalSaveReviewVetoTest(){}

    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError("G21.195 "+message);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition condition,String stage)
        throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<end){
            if(condition.ready())return;
            Thread.sleep(10L);
        }
        check(condition.ready(),"timeout "+stage);
    }

    private static long completed(World world){
        String value=world.persistence().metrics();
        String key=",completed=";
        int at=value.indexOf(key);
        check(at>=0,"persistence completed counter missing");
        int start=at+key.length();
        int end=value.indexOf(',',start);
        check(end>start,"persistence completed counter malformed");
        return Long.parseLong(value.substring(start,end));
    }

    private static Path account(){
        return LocalAccountProfiles.accountFile(USER);
    }

    private static String diskPhase()throws Exception{
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        Optional<PlayerSnapshot> value=repo.load(USER);
        check(value.isPresent(),"named account snapshot missing");
        return value.get().values().get(STORED);
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
            check((pre[8]&255)==0,"prelogin failed");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,
                "server seed mismatch");
            int[] seeds={0x01020304,0x11223344,
                (int)(seed>>>32),(int)seed};
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
        AtomicBoolean armed=new AtomicBoolean();
        AtomicInteger injected=new AtomicInteger();
        FilePlayerRepository failingRepo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile,username->{
                if(USER.equals(username)&&armed.get()){
                    injected.incrementAndGet();
                    throw new IOException(
                        "G21.195 TEST_ONLY_FINAL_SAVE_BEFORE_REPLACE");
                }
            });
        World world=World.isolatedForTest(50L,failingRepo);
        try{
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(Socket socket=server.login(USER)){
                    await(()->world.players().byName(USER)!=null,
                        "real holder TCP membership");
                    await(()->completed(world)>=1L,
                        "World BOOTSTRAP task completion");
                    await(()->failingRepo.forcedWorldPostimageCountForTest()>0,
                        "opt-in forced BOOTSTRAP postimage");
                    LocalDurableAccountReviewIntentPrimitive
                        .requireReviewMarker(account());
                    check(diskPhase()==null,
                        "BOOTSTRAP already stores future mutation");
                    WorldPlayer owner=world.players().byName(USER);
                    world.submit(owner,owner.generation(),()->{
                        owner.snapshotExtensions().replaceNamespace(
                            NS,Collections.singletonMap(KEY,LOST));
                    }).get(5L,TimeUnit.SECONDS);
                    check(LOST.equals(owner.snapshotExtensions()
                        .namespace(NS).get(KEY)),"mutation not applied");
                    armed.set(true);
                }
                await(()->world.players().size()==0,
                    "failed-final logout");
                server.waitCompleted();
            }
            check(injected.get()==1,
                "expected one injected final save failure");
            check(world.persistence().failedCount()>=1,
                "World final save not marked failed");
            check(diskPhase()==null,
                "failed final save replaced BOOTSTRAP bytes");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "failed final save failed to retain negative lease");
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account());
            check(Files.isRegularFile(account(),
                LinkOption.NOFOLLOW_LINKS),
                "forced BOOTSTRAP file absent");
            Files.write(ready,
                "G21195_HOLDER_FAILED_READY".getBytes(
                    StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
            System.out.println(
                "G21195_HOLDER_FAILED_READY"+
                " realTcp=true forcedBootstrap=true"+
                " failedFinalSave=true retainedJvmLease=true"+
                " forcedActiveReview=true");
            System.out.flush();
            Thread.sleep(120_000L);
            throw new AssertionError(
                "G21.195 holder was not force-killed");
        }finally{
            world.close();
        }
    }

    private static void denied(String stage)throws Exception{
        check(diskPhase()==null,
            "relogin child did not see stale BOOTSTRAP");
        LocalDurableAccountReviewIntentPrimitive
            .requireReviewMarker(account());
        if("denied-after-kill".equals(stage)){
            // Unlike live-holder denial, this independently proves
            // the OS-owned lock became reacquirable after JVM death.
            try(LocalCrossJvmAccountSessionLock.Handle handle=
                    LocalCrossJvmAccountSessionLock.acquire(account())){
                check(Files.isRegularFile(handle.fileForTest(),
                    LinkOption.NOFOLLOW_LINKS),
                    "post-kill OS lock file not regular");
            }
            boolean markerVeto=false;
            try{
                LocalDurableAccountReviewIntentPrimitive
                    .requireNoReviewMarker(account());
            }catch(IOException expected){
                check(expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY"),
                    "post-kill marker failed with wrong reason");
                markerVeto=true;
            }
            check(markerVeto,
                "fresh JVM treated surviving ACTIVE intent as clean");
        }
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(50L,repository);
        try{
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                check(attemptedLogin(
                    server.listener.getLocalPort())!=2,
                    "stale BOOTSTRAP TCP admission allowed "+stage);
                server.waitCompleted();
            }
            check(world.players().size()==0,
                "denied child created World member "+stage);
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "denied child retained a new JVM lease "+stage);
            check(diskPhase()==null,
                "denied child modified stale account "+stage);
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account());
            System.out.println(
                "G21195_CHILD_LOGIN_DENIED_PASS"+
                " stage="+stage+
                " accountNotRehydrated=true"+
                " noJvmLeaseRetained=true"+
                " activeMarkerPreserved=true");
        }finally{
            world.close();
        }
    }

    private static Process spawn(
        Path settings,Path ready,Path output,String mode
    )throws Exception{
        String exe=System.getProperty("java.home")+
            java.io.File.separator+"bin"+java.io.File.separator+
            (System.getProperty("os.name","").toLowerCase()
                .contains("win")?"java.exe":"java");
        ProcessBuilder builder=new ProcessBuilder(
            exe,
            "-Dspk.local.accountFile="+settings,
            "-D"+LocalJvmAccountSessionLease.PROPERTY+"=true",
            "-D"+LocalCrossJvmAccountSessionLock.PROPERTY+"=true",
            "-D"+LocalReviewIntentAdmissionVeto.PROPERTY+"=true",
            "-D"+LocalOneShotActiveReviewOnAdmission.PROPERTY+"=true",
            "-D"+FilePlayerRepository
                .EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY+"=true",
            "-cp",System.getProperty("java.class.path"),
            G21195CoreKilledFailedFinalSaveReviewVetoTest
                .class.getName(),
            "--child",mode,ready.toString()
        );
        builder.redirectErrorStream(true);
        builder.redirectOutput(output.toFile());
        return builder.start();
    }

    private static void completedChild(
        Process child,Path output,String wanted
    )throws Exception{
        if(!child.waitFor(25L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("G21.195 child timeout "+wanted);
        }
        String log=new String(Files.readAllBytes(output),
            StandardCharsets.UTF_8);
        check(child.exitValue()==0,
            "child failed exit="+child.exitValue()+" log="+log);
        check(log.contains(wanted),
            "child lacks "+wanted+" log="+log);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21195-failed-jvm-");
        Path settings=root.resolve("opensrc.properties");
        Path ready=root.resolve("holder.ready");
        Path holderLog=root.resolve("holder.log");
        Path liveLog=root.resolve("live-denied.log");
        Path afterLog=root.resolve("after-kill-denied.log");
        String old=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",settings.toString());
        Process holderProcess=null;
        try{
            Path file=account();
            Path marker=LocalDurableAccountReviewIntentPrimitive
                .markerPath(file);
            check(!Files.exists(file,LinkOption.NOFOLLOW_LINKS),
                "test account initially exists");
            holderProcess=spawn(settings,ready,holderLog,"holder");
            final Process live=holderProcess;
            await(()->Files.isRegularFile(
                ready,LinkOption.NOFOLLOW_LINKS),
                "failed holder ready");
            check(live.isAlive(),
                "holder exited before intentional kill");
            check(diskPhase()==null,
                "holder saved lost World mutation");
            byte[] accountBefore=Files.readAllBytes(file);
            byte[] markerBefore=Files.readAllBytes(marker);
            check(accountBefore.length>0&&markerBefore.length>0,
                "saved account or ACTIVE marker empty");
            Process current=spawn(
                settings,ready,liveLog,"denied-live");
            completedChild(current,liveLog,
                "G21195_CHILD_LOGIN_DENIED_PASS");
            check(live.isAlive(),
                "holder exited before force-kill");

            live.destroyForcibly();
            check(live.waitFor(10L,TimeUnit.SECONDS),
                "holder JVM did not die");
            check(live.exitValue()!=0,
                "holder JVM exited normally");
            holderProcess=null;
            check(Arrays.equals(
                accountBefore,Files.readAllBytes(file)),
                "holder kill changed BOOTSTRAP account");
            check(Arrays.equals(
                markerBefore,Files.readAllBytes(marker)),
                "holder kill changed ACTIVE intent");

            Process after=spawn(
                settings,ready,afterLog,"denied-after-kill");
            completedChild(after,afterLog,
                "G21195_CHILD_LOGIN_DENIED_PASS");
            check(Arrays.equals(
                accountBefore,Files.readAllBytes(file)),
                "post-kill refusal rewrote account");
            check(Arrays.equals(
                markerBefore,Files.readAllBytes(marker)),
                "post-kill refusal changed marker");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "supervisor acquired a session token");
            System.out.println(
                "G21195_CROSS_JVM_FAILED_FINAL_REVIEW_PASS"+
                " holderRealTcp=true"+
                " forcedBootstrapPostimage=true"+
                " finalSaveFaultBeforeRename=true"+
                " holderJvmForceKilled=true"+
                " activeReviewSurvivesKill=true"+
                " freshJvmTcpDenied=true"+
                " staleBootstrapNotRehydrated=true"+
                " extraOwnerLease=false"+
                " automaticRecovery=false"+
                " markerRetirement=false"+
                " powerLossProof=false"+
                " rawWriterExclusion=false");
        }finally{
            if(holderProcess!=null&&holderProcess.isAlive()){
                holderProcess.destroyForcibly();
                holderProcess.waitFor(5L,TimeUnit.SECONDS);
            }
            if(old==null)System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",old);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0])){
            Path ready=Paths.get(args[2]);
            if("holder".equals(args[1]))holder(ready);
            else if("denied-live".equals(args[1])||
                    "denied-after-kill".equals(args[1]))
                denied(args[1]);
            else throw new IllegalArgumentException(
                "G21.195 invalid child mode");
        }else{
            check(args.length==0,"invalid arguments");
            run();
        }
    }
}
