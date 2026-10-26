package spk.local;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R69: real authenticated TCP World, original G21.202 capture, and
 * original G21.185 OS lock are interrupted AFTER forced R50
 * immutable-object directory publication but BEFORE negative HEAD.
 *
 * A killed child leaves account OLD bytes and a stranded durable
 * NEGATIVE object, not a positive final-save receipt. Fresh JVM,
 * default-off loader MUST refuse it. This tests process termination,
 * not whole-machine power-loss ordering or hostile alias CAS.
 * R72 adds a first-login bootstrap case and fresh-JVM TCP admission veto
 * at this pre-HEAD cutpoint. Both cases remain negative-only.
 */
public final class G21397RealWorldNegativeIntentProcessKillRegressionTest {
    private G21397RealWorldNegativeIntentProcessKillRegressionTest(){}
    private static final String USER="r69-killed-original-world";
    private static final String CODE=
        "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION";
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("R69_KILL "+reason);
    }
    private static boolean has(Throwable x,String fragment){
        for(Throwable e=x;e!=null;e=e.getCause())
            if(String.valueOf(e.getMessage()).contains(fragment))return true;
        return false;
    }
    private static String javaExe(){
        return System.getProperty("java.home")+File.separator+"bin"+
            File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
    }
    private static void configureRoot(Path root,boolean enable)throws Exception{
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        final String[] flags={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease
                .CAPTURED_NEGATIVE_INTENT_LAB_PROPERTY
        };
        for(String name:flags){
            if(enable)System.setProperty(name,"true");
            else System.clearProperty(name);
        }
    }
    @FunctionalInterface interface Condition{
        boolean truth()throws Exception;
    }
    private static void await(Condition cond,String reason)throws Exception{
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<limit){
            if(cond.truth())return;
            Thread.sleep(10);
        }
        check(cond.truth(),"timeout "+reason);
    }
    private static long completedWorldWrites(World world){
        String metrics=world.persistence().metrics();
        String key=",completed=";
        int start=metrics.indexOf(key);
        check(start>=0,"missing World completed-save metric");
        start+=key.length();
        int end=metrics.indexOf(',',start);
        check(end>start,"malformed World completed-save metric");
        return Long.parseLong(metrics.substring(start,end));
    }
    private static void holder(Path root,Path ready,boolean firstTime)
        throws Exception{
        configureRoot(root,true);
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(50L,repository);
        try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(world)){
            try(Socket client=server.login(USER)){
                await(()->world.players().byName(USER)!=null,
                    "genuine TCP World registration");
                WorldPlayer owner=world.players().byName(USER);
                long generation=owner.generation();
                Path account=LocalAccountProfiles.accountFile(USER);
                if(firstTime){
                    await(()->completedWorldWrites(world)>=1L&&
                        Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
                        "actual first-login bootstrap publication");
                    byte[] bootstrap=Files.readAllBytes(account);
                    check(bootstrap.length>0,"first-login bootstrap empty");
                    java.util.Properties properties=new java.util.Properties();
                    try(InputStream in=Files.newInputStream(account)){
                        properties.load(in);
                    }
                    check(!properties.containsKey(
                        "extension.r72_orphan.phase"),
                        "bootstrap already includes final-only mutation");
                    Files.write(root.resolve("first-bootstrap.bin"),bootstrap,
                        StandardOpenOption.CREATE_NEW);
                }
                final String phase=firstTime?
                    "FIRST_LOGIN":"EXISTING_ACCOUNT";
                world.submit(owner,generation,()->
                    owner.snapshotExtensions().replaceNamespace(
                        "r72_orphan",
                        Collections.singletonMap("phase",phase))
                ).get(5,TimeUnit.SECONDS);
                AtomicReference<WorldPlayerPersistence.CapturedSave> capture=
                    new AtomicReference<>();
                world.submit(owner,generation,()->
                    capture.set(world.persistence().captureDeferredSave(
                        USER,owner,generation,0,"[r69] ",
                        "R69_PROCESS_CRASH_FINAL_CANDIDATE"))
                ).get(5,TimeUnit.SECONDS);
                WorldPlayerPersistence.CapturedSave exact=capture.get();
                check(exact!=null&&exact.ownerWitness!=null&&
                    phase.equals(exact.ticket.snapshot.values().get(
                        "extension.r72_orphan.phase")),
                    "no genuine mutated immutable World capture");
                LocalJvmAccountSessionLease
                    .publishCapturedFinalNegativeIntentUnderOriginalLockForLab(
                        LocalAccountProfiles.accountFile(USER),owner,
                        generation,exact.ticket.snapshot,
                        exact.ownerWitness,stage->{
                            if(stage!=LocalNegativeAccountHeadPrototype.Stage
                                    .OBJECT_DIRECTORY_FORCED_BEFORE_HEAD)
                                return;
                            Files.write(ready,new byte[]{1},
                                StandardOpenOption.CREATE_NEW);
                            System.out.println(
                                "R69_REAL_CAPTURE_OBJECT_DIRECTORY_FORCED");
                            System.out.flush();
                            try{
                                Thread.sleep(120000L);
                            }catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "R69_HOLDER_UNEXPECTED_INTERRUPTION",
                                    interrupted);
                            }
                            throw new AssertionError(
                                "R69_HOLDER_MUST_BE_KILLED");
                        });
            }
        }finally{
            if(!world.closed())world.close();
        }
        throw new AssertionError("holder was not killed");
    }
    private static int freshTcpResponse(int port)throws Exception{
        try(Socket socket=new Socket(
                InetAddress.getByName("127.0.0.1"),port)){
            socket.setSoTimeout(5000);
            InputStream in=socket.getInputStream();
            OutputStream out=socket.getOutputStream();
            out.write(14);out.write(7);out.flush();
            byte[] pre=Binary.readExactly(in,9);
            check((pre[8]&255)==0,"prelogin unexpected");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,
                "login seed mismatch");
            int[] seeds={0x01020304,0x11223344,
                (int)(seed>>>32),(int)seed};
            byte[] frame=
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .loginFrame(seeds,USER);
            out.write(16);out.write(frame.length);
            out.write(frame);out.flush();
            return in.read();
        }
    }
    private static void freshProbe(Path root,boolean firstTime)
        throws Exception{
        configureRoot(root,false);
        Path account=LocalAccountProfiles.accountFile(USER);
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "retained account baseline missing");
        if(firstTime){
            byte[] bootstrap=Files.readAllBytes(
                root.resolve("first-bootstrap.bin"));
            check(bootstrap.length>0&&Arrays.equals(
                bootstrap,Files.readAllBytes(account)),
                "first-login bootstrap bytes changed after kill");
        }
        check(!Files.exists(
            LocalNegativeAccountHeadPrototype.headPath(account),
            LinkOption.NOFOLLOW_LINKS),"unexpected negative HEAD");
        boolean denied=false;
        try{
            new FilePlayerRepository(LocalAccountProfiles::accountFile)
                .loadForWorldSession(USER);
        }catch(IOException veto){denied=has(veto,CODE);}
        check(denied,
            "fresh process accepted OLD account despite orphan intent");
        denied=false;
        WorldPlayer target=new WorldPlayer();
        try{
            LocalAccountProfiles.load(
                USER,target.bank(),target.equipment(),target.movement(),
                target.petState(),target.playerState());
        }catch(IOException veto){denied=has(veto,CODE);}
        check(denied,"direct legacy reader ignored orphan forced object");
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World replacement=World.isolatedForTest(50L,repository);
        try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(replacement)){
            int code=freshTcpResponse(server.listener.getLocalPort());
            check(code!=2&&replacement.players().size()==0,
                "fresh TCP accepted orphaned account image");
            server.waitCompleted();
        }finally{
            if(!replacement.closed())replacement.close();
        }
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
        try(LocalCrossJvmAccountSessionLock.Handle lock=
                LocalCrossJvmAccountSessionLock.acquire(account)){
            lock.requireOriginalLockNameStillPresent();
        }
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "fresh process retained killed JVM token");
        System.out.println("R69_FRESH_JVM_NEGATIVE_ORPHAN_VETO_PASS"+
            " firstTime="+firstTime+
            " freshTcpDenied=true");
    }
    private static Process spawn(
        String role,Path root,Path ready,Path log,boolean firstTime
    )throws IOException{
        ProcessBuilder b=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            G21397RealWorldNegativeIntentProcessKillRegressionTest
                .class.getName(),"--child",role,root.toString(),
                ready.toString(),
                firstTime?"FIRST_LOGIN":"EXISTING_ACCOUNT");
        b.redirectErrorStream(true);
        b.redirectOutput(log.toFile());
        return b.start();
    }
    private static String completed(Process p,Path log)throws Exception{
        if(!p.waitFor(20,TimeUnit.SECONDS)){
            p.destroyForcibly();p.waitFor(10,TimeUnit.SECONDS);
            throw new AssertionError("fresh probe timed out");
        }
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0,"child failed: "+output);
        return output;
    }
    private static void run(boolean firstTime)throws Exception{
        Path root=Files.createTempDirectory(firstTime?
            "r72-first-login-orphan-kill-":"r69-real-intent-kill-");
        String[] flags={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease
                .CAPTURED_NEGATIVE_INTENT_LAB_PROPERTY
        };
        String[] saved=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            saved[i]=System.getProperty(flags[i]);
        Process original=null;
        try{
            configureRoot(root,false);
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            Path account=LocalAccountProfiles.accountFile(USER);
            if(firstTime){
                check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                    "first-login account unexpectedly already exists");
            }else{
                repo.save(PlayerSnapshotCodec.capture(
                    USER,new WorldPlayer()));
            }
            byte[] originalBytes=firstTime?null:
                Files.readAllBytes(account);
            Path ready=root.resolve("holder.ready");
            Path log=root.resolve("holder.log");
            original=spawn("holder",root,ready,log,firstTime);
            Process holder=original;
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
            while(holder.isAlive()&&System.nanoTime()<deadline&&
                !Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS))
                Thread.sleep(10L);
            check(holder.isAlive()&&Files.isRegularFile(ready,
                LinkOption.NOFOLLOW_LINKS),
                "actual World did not force object: "+
                (Files.exists(log)?new String(Files.readAllBytes(log),
                    StandardCharsets.UTF_8):"no child log"));
            holder.destroyForcibly();
            check(holder.waitFor(10,TimeUnit.SECONDS)&&
                holder.exitValue()!=0,
                "holder survived process kill");
            byte[] baseline=firstTime?
                Files.readAllBytes(root.resolve("first-bootstrap.bin")):
                originalBytes;
            check(baseline!=null&&baseline.length>0&&
                Arrays.equals(baseline,Files.readAllBytes(account)),
                "killed intent modified established account baseline");
            check(!Files.exists(
                LocalNegativeAccountHeadPrototype.headPath(account),
                LinkOption.NOFOLLOW_LINKS),
                "killed attempt incorrectly reached HEAD publication");
            String prefix=account.getFileName()+".g21378-object-";
            int objects=0;
            try(DirectoryStream<Path> files=
                    Files.newDirectoryStream(account.getParent())){
                for(Path path:files){
                    if(path.getFileName().toString().startsWith(prefix)){
                        objects++;
                        check(Files.isRegularFile(path,
                            LinkOption.NOFOLLOW_LINKS),
                            "forced orphan object was not retained");
                    }
                }
            }
            check(objects==1,"missing exact one forced immutable object");
            Path probeLog=root.resolve("fresh.log");
            check(completed(spawn("probe",root,ready,probeLog,
                firstTime),probeLog)
                .contains("R69_FRESH_JVM_NEGATIVE_ORPHAN_VETO_PASS"),
                "independent restart did not refuse orphan");
            check(Arrays.equals(baseline,Files.readAllBytes(account)),
                "fresh process changed account baseline");
            System.out.println("R69_REAL_WORLD_PRE_HEAD_PROCESS_KILL_PASS"+
                " firstLogin="+firstTime+
                " realTcpWorldCapture=true"+
                " originalSessionOsLock=true"+
                " forcedImmutableImageBeforeHead=true"+
                " childJVMHardKilled=true"+
                " oldAccountBytesPreserved=true"+
                " headMissingButOrphanRetained=true"+
                " freshJvmDefaultOffWorldHydrationDenied=true"+
                " freshJvmDirectLegacyLoadDenied=true"+
                " freshJvmTcpReconnectDenied=true"+
                " killedOsLockReleasedByKernel=true"+
                " noAutoReplayOrRecovery=true"+
                " noPositiveFinalAck=true"+
                " machinePowerLossNotProven=true");
        }finally{
            if(original!=null&&original.isAlive()){
                original.destroyForcibly();
                original.waitFor(10,TimeUnit.SECONDS);
            }
            for(int i=0;i<flags.length;i++){
                if(saved[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],saved[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==5&&"--child".equals(args[0])){
            boolean firstTime="FIRST_LOGIN".equals(args[4]);
            check(firstTime||"EXISTING_ACCOUNT".equals(args[4]),
                "unknown login case");
            if("holder".equals(args[1]))
                holder(Paths.get(args[2]),Paths.get(args[3]),firstTime);
            else if("probe".equals(args[1]))
                freshProbe(Paths.get(args[2]),firstTime);
            else throw new IllegalArgumentException("role");
            return;
        }
        check(args.length==0,"args");
        run(false);
        run(true);
        System.out.println("R72_FIRST_LOGIN_ORPHAN_PROCESS_KILL_MATRIX_PASS"+
            " initialAccountMissing=true"+
            " actualWorldBootstrapBeforeFinalCapture=true"+
            " preHeadOrphanRetained=true"+
            " exactBootstrapBytesPreserved=true"+
            " freshJvmTcpAndDirectAdmissionDenied=true"+
            " noAutoRecovery=true");
    }
}
