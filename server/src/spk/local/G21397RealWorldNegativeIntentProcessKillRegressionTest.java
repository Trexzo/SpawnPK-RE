package spk.local;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
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
    private static void holder(Path root,Path ready)throws Exception{
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
                AtomicReference<WorldPlayerPersistence.CapturedSave> capture=
                    new AtomicReference<>();
                world.submit(owner,generation,()->
                    capture.set(world.persistence().captureDeferredSave(
                        USER,owner,generation,0,"[r69] ",
                        "R69_PROCESS_CRASH_FINAL_CANDIDATE"))
                ).get(5,TimeUnit.SECONDS);
                WorldPlayerPersistence.CapturedSave exact=capture.get();
                check(exact!=null&&exact.ownerWitness!=null,
                    "no genuine immutable World capture");
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
    private static void freshProbe(Path root)throws Exception{
        configureRoot(root,false);
        Path account=LocalAccountProfiles.accountFile(USER);
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "original account missing");
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
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
        try(LocalCrossJvmAccountSessionLock.Handle lock=
                LocalCrossJvmAccountSessionLock.acquire(account)){
            lock.requireOriginalLockNameStillPresent();
        }
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "fresh process retained killed JVM token");
        System.out.println("R69_FRESH_JVM_NEGATIVE_ORPHAN_VETO_PASS");
    }
    private static Process spawn(String role,Path root,Path ready,Path log)
        throws IOException{
        ProcessBuilder b=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            G21397RealWorldNegativeIntentProcessKillRegressionTest
                .class.getName(),"--child",role,root.toString(),
                ready.toString());
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
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("r69-real-intent-kill-");
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
            repo.save(PlayerSnapshotCodec.capture(
                USER,new WorldPlayer()));
            Path account=LocalAccountProfiles.accountFile(USER);
            byte[] originalBytes=Files.readAllBytes(account);
            Path ready=root.resolve("holder.ready");
            Path log=root.resolve("holder.log");
            original=spawn("holder",root,ready,log);
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
            check(Arrays.equals(originalBytes,Files.readAllBytes(account)),
                "killed intent modified healthy original account bytes");
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
            check(completed(spawn("probe",root,ready,probeLog),probeLog)
                .contains("R69_FRESH_JVM_NEGATIVE_ORPHAN_VETO_PASS"),
                "independent restart did not refuse orphan");
            check(Arrays.equals(originalBytes,Files.readAllBytes(account)),
                "fresh process changed old account");
            System.out.println("R69_REAL_WORLD_PRE_HEAD_PROCESS_KILL_PASS"+
                " realTcpWorldCapture=true"+
                " originalSessionOsLock=true"+
                " forcedImmutableImageBeforeHead=true"+
                " childJVMHardKilled=true"+
                " oldAccountBytesPreserved=true"+
                " headMissingButOrphanRetained=true"+
                " freshJvmDefaultOffWorldHydrationDenied=true"+
                " freshJvmDirectLegacyLoadDenied=true"+
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
        if(args.length==4&&"--child".equals(args[0])){
            if("holder".equals(args[1]))
                holder(Paths.get(args[2]),Paths.get(args[3]));
            else if("probe".equals(args[1]))
                freshProbe(Paths.get(args[2]));
            else throw new IllegalArgumentException("role");
            return;
        }
        check(args.length==0,"args");
        run();
    }
}
