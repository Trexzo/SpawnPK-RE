package spk.local;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R57 real child-JVM process contention + file-backed reusable ledger
 * smoke. No World runtime, recovery admission or ownership authority.
 */
public final class G21385ReusableEpochLedgerLabRegression {
    private G21385ReusableEpochLedgerLabRegression(){}
    private static final byte[] INITIAL=
        "R57_INITIAL".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FINAL=
        "R57_FINAL".getBytes(StandardCharsets.US_ASCII);
    private static void check(boolean good,String msg){
        if(!good)throw new AssertionError("R57_LEDGER "+msg);
    }
    private static boolean has(Throwable x,String code){
        for(Throwable c=x;c!=null;c=c.getCause())
            if(String.valueOf(c.getMessage()).contains(code))return true;
        return false;
    }
    private static void enable(){
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            "true");
    }
    private static void state(
        Path account,long epoch,int sequence,
        LocalReusableAccountEpochLab.Phase phase
    )throws Exception{
        LocalReusableAccountEpochLab.Observation o=
            LocalReusableAccountEpochLab.inspect(account);
        check(o.epoch==epoch&&o.sequence==sequence&&o.phase==phase,
            "ledger phase or sequence changed");
        check(!o.admissionAuthorized&&!o.releaseAuthorized,
            "lab state claimed live account authority");
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "lab wrote historical account bytes");
    }
    private static void healthy(Path a)throws Exception{
        enable();
        LocalReusableAccountEpochLab.Observation seeded=
            LocalReusableAccountEpochLab.initialize(a,INITIAL);
        check(!seeded.admissionAuthorized&&
                seeded.phase==LocalReusableAccountEpochLab.Phase.CLOSED,
            "seed granted account login");
        state(a,1,0,LocalReusableAccountEpochLab.Phase.CLOSED);
        LocalReusableAccountEpochLab.claim(a,0,701L,"world_lab_a");
        state(a,2,1,LocalReusableAccountEpochLab.Phase.OWNED);
        boolean rejected=false;
        try{LocalReusableAccountEpochLab.claim(a,0,702L,"world_lab_b");}
        catch(IOException ex){rejected=has(ex,"R57_STALE_SEQUENCE_DENIED");}
        check(rejected,"second World claimed stale sequence");
        rejected=false;
        try{LocalReusableAccountEpochLab.prepareFinal(
            a,1,702L,"world_lab_b");}
        catch(IOException ex){rejected=has(ex,"R57_OWNER_CONTINUITY_DENIED");}
        check(rejected,"foreign World prepared final");
        LocalReusableAccountEpochLab.prepareFinal(
            a,1,701L,"world_lab_a");
        state(a,2,2,LocalReusableAccountEpochLab.Phase.FINAL_PENDING);
        rejected=false;
        try{LocalReusableAccountEpochLab.claim(a,2,702L,"world_lab_b");}
        catch(IOException ex){rejected=has(ex,"R57_WRONG_PHASE_DENIED");}
        check(rejected,"unsettled final reused account");
        LocalReusableAccountEpochLab.settleLabFinal(
            a,2,701L,"world_lab_a",FINAL);
        state(a,2,3,LocalReusableAccountEpochLab.Phase.CLOSED);
        LocalReusableAccountEpochLab.claim(a,3,801L,"world_lab_b");
        state(a,3,4,LocalReusableAccountEpochLab.Phase.OWNED);
        rejected=false;
        try{LocalReusableAccountEpochLab.prepareFinal(
            a,4,701L,"world_lab_a");}
        catch(IOException ex){rejected=has(ex,"R57_OWNER_CONTINUITY_DENIED");}
        check(rejected,"superseded World reclaimed ownership");
        System.out.println("R57_LAB_HEALTHY_MONOTONIC_PASS"+
            " firstEpoch=2 nextEpoch=3"+
            " untrustedOwnerContinuity=true"+
            " staleSequenceDenied=true"+
            " pendingFinalNotReusable=true"+
            " allPhasesNonAdmitting=true");
    }
    private static void child(
        String mode,Path account,Path ready
    )throws Exception{
        enable();
        if("holder".equals(mode)){
            LocalReusableAccountEpochLab.settleLabFinal(
                account,2,777L,"world_lab_c",FINAL,stage->{
                    if(stage!=LocalReusableAccountEpochLab.Stage
                            .SNAPSHOT_FORCED)return;
                    Files.write(ready,new byte[]{1});
                    System.out.println("R57_HOLDER_IMAGE_FORCED");
                    System.out.flush();
                    try{Thread.sleep(120_000L);}
                    catch(InterruptedException ex){
                        Thread.currentThread().interrupt();
                        throw new IOException("R57_HOLDER_INTERRUPTED",ex);
                    }
                    throw new AssertionError("holder not killed");
                });
        }else if("rival".equals(mode)){
            boolean denied=false;
            try{
                LocalReusableAccountEpochLab.settleLabFinal(
                    account,2,777L,"world_lab_c",FINAL);
            }catch(IOException ex){
                denied=has(ex,"G21.185 ACCOUNT_SESSION_LOCK_BUSY");
            }
            check(denied,"rival bypassed holder lock");
            System.out.println("R57_RIVAL_LOCK_DENIED");
        }else if("probe".equals(mode)){
            boolean denied=false;
            try{LocalReusableAccountEpochLab.inspect(account);}
            catch(IOException ex){
                denied=has(ex,"R57_ORPHAN_FUTURE_IMAGE_MANUAL_REVIEW");
            }
            check(denied,"fresh JVM reused orphan uncommitted image");
            denied=false;
            try{
                LocalReusableAccountEpochLab.settleLabFinal(
                    account,2,777L,"world_lab_c",FINAL);
            }catch(IOException ex){
                denied=has(ex,"R57_ORPHAN_FUTURE_IMAGE_MANUAL_REVIEW");
            }
            check(denied,"orphan final image was silently replayed");
            System.out.println("R57_RESTART_ORPHAN_REFUSED");
        }else throw new IllegalArgumentException("mode");
    }
    private static Process spawn(
        String mode,Path a,Path ready,Path log
    )throws IOException{
        String java=System.getProperty("java.home")+File.separator+"bin"+
            File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
        ProcessBuilder builder=new ProcessBuilder(java,"-cp",
            System.getProperty("java.class.path"),
            G21385ReusableEpochLedgerLabRegression.class.getName(),
            "--child",mode,a.toString(),ready.toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());
        return builder.start();
    }
    private static String end(Process p,Path log)throws Exception{
        if(!p.waitFor(20,TimeUnit.SECONDS)){
            p.destroyForcibly();p.waitFor(10,TimeUnit.SECONDS);
            throw new AssertionError("child timeout");
        }
        String out=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0,"child failed: "+out);
        return out;
    }
    private static void crash(Path root)throws Exception{
        Path account=root.resolve("crash.properties");
        enable();
        LocalReusableAccountEpochLab.initialize(account,INITIAL);
        LocalReusableAccountEpochLab.claim(account,0,777L,"world_lab_c");
        LocalReusableAccountEpochLab.prepareFinal(
            account,1,777L,"world_lab_c");
        Path ready=root.resolve("crash.ready");
        Path holderLog=root.resolve("crash.holder.log");
        Process holder=spawn("holder",account,ready,holderLog);
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(System.nanoTime()<deadline&&holder.isAlive()&&
                    !Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS))
                Thread.sleep(10L);
            check(holder.isAlive()&&Files.isRegularFile(
                    ready,LinkOption.NOFOLLOW_LINKS),
                "holder did not reach image-force cutpoint");
            Path rivalLog=root.resolve("crash.rival.log");
            check(end(spawn("rival",account,ready,rivalLog),
                rivalLog).contains("R57_RIVAL_LOCK_DENIED"),
                "cross-JVM contender succeeded");
            holder.destroyForcibly();
            check(holder.waitFor(10,TimeUnit.SECONDS)&&
                holder.exitValue()!=0,"holder not hard-killed");
            Path probeLog=root.resolve("crash.probe.log");
            check(end(spawn("probe",account,ready,probeLog),
                    probeLog).contains("R57_RESTART_ORPHAN_REFUSED"),
                "fresh JVM failed orphan check");
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "crash fixture modified legacy account");
        }finally{
            if(holder.isAlive()){
                holder.destroyForcibly();holder.waitFor(10,TimeUnit.SECONDS);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            child(args[1],Paths.get(args[2]),Paths.get(args[3]));
            return;
        }
        check(args.length==0,"args");
        Path root=Files.createTempDirectory("r57-ledger-");
        String key=LocalCrossJvmAccountSessionLock.PROPERTY,
            continuity=LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY;
        String before=System.getProperty(key),b=System.getProperty(continuity);
        try{
            Path negative=root.resolve("default-off.properties");
            System.clearProperty(key);System.clearProperty(continuity);
            boolean denied=false;
            try{LocalReusableAccountEpochLab.initialize(negative,INITIAL);}
            catch(IOException ex){
                denied=has(ex,"R57_REQUIRES_EXISTING_CROSS_JVM_LOCK_CONTINUITY");
            }
            check(denied,"default-off ledger unexpectedly active");
            check(!Files.exists(negative,LinkOption.NOFOLLOW_LINKS),
                "default-off created account");
            healthy(root.resolve("healthy.properties"));
            crash(root);
            System.out.println("R57_REUSABLE_ACCOUNT_EPOCH_LAB_PASS"+
                " separateLabNamespace=true"+
                " cooperatingLockHeld=true"+
                " claimPrepareSettleReclaim=true"+
                " staleAndForeignOwnerRejected=true"+
                " childJvmRivalRejected=true"+
                " killedFinalImageOrphanDenied=true"+
                " noExistingAccountBytesChanged=true"+
                " noLiveLoginAdmission=true"+
                " noWorldOwnershipAuthentication=true"+
                " noPowerLossDurabilityClaim=true");
        }finally{
            if(before==null)System.clearProperty(key);
            else System.setProperty(key,before);
            if(b==null)System.clearProperty(continuity);
            else System.setProperty(continuity,b);
            try(Stream<Path> scan=Files.walk(root)){
                for(Path file:scan.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
}
