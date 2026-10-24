package spk.local;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** R55 append-only permanent negative transition log acceptance.
 * Real external JVM cutpoints; no authorization or reconciliation. */
public final class G21383NegativeHeadTransitionProcessKillRegression {
    private G21383NegativeHeadTransitionProcessKillRegression(){}
    private static final byte[] BASE="r55-original-negative".getBytes(
        StandardCharsets.US_ASCII);
    private static final byte[] CHECKPOINT="r55-checkpoint-1".getBytes(
        StandardCharsets.US_ASCII);
    private static final byte[] FINAL="r55-final-pending".getBytes(
        StandardCharsets.US_ASCII);
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("R55_TRANSITION "+why);
    }
    private static void enable(){
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            "true");
    }
    private static boolean has(Throwable t,String code){
        for(Throwable x=t;x!=null;x=x.getCause())
            if(String.valueOf(x.getMessage()).contains(code))return true;
        return false;
    }
    private static void inspect(
        Path account,int count,LocalNegativeAccountTransitionLog.Phase phase
    )throws Exception{
        LocalNegativeAccountTransitionLog.Observation current=
            LocalNegativeAccountTransitionLog.inspect(account);
        check(current.sequence==count&&current.phase==phase,
            "wrong negative chain state");
        check(!current.admissionAuthorized&&!current.releaseAuthorized,
            "negative observation authorized login/release");
        check(Files.isRegularFile(
            LocalNegativeAccountHeadPrototype.headPath(account),
            LinkOption.NOFOLLOW_LINKS),
            "permanent genesis HEAD absent");
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "experimental log overwrote legacy account");
    }
    private static void base(Path account)throws Exception{
        enable();
        LocalNegativeAccountHeadPublicationGate.publish(
            account,"r55-account",55L,BASE);
        inspect(account,0,LocalNegativeAccountTransitionLog.Phase.ACTIVE);
    }
    private static void holder(
        Path account,Path ready,LocalNegativeAccountTransitionLog.Stage target
    )throws Exception{
        enable();
        LocalNegativeAccountTransitionLog.append(account,0,
            LocalNegativeAccountTransitionLog.Phase.CHECKPOINT,
            CHECKPOINT,stage->{
                if(stage!=target)return;
                Files.write(ready,new byte[]{1},
                    StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
                System.out.println("R55_HOLDER_AT "+target);
                System.out.flush();
                try{Thread.sleep(120000L);}
                catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    throw new IOException("R55_HOLDER_INTERRUPTED",e);
                }
                throw new AssertionError("fixture not killed");
            });
        throw new AssertionError("cutpoint not reached");
    }
    private static void probe(Path account,boolean complete)
        throws Exception{
        enable();
        if(complete){
            inspect(account,1,
                LocalNegativeAccountTransitionLog.Phase.CHECKPOINT);
            boolean stale=false;
            try{
                LocalNegativeAccountTransitionLog.append(account,0,
                    LocalNegativeAccountTransitionLog.Phase.FINAL_PENDING,
                    FINAL,s->{});
            }catch(IOException e){
                stale=has(e,"R55_STALE_EXPECTED_SEQUENCE_NEGATIVE");
            }
            check(stale,"stale expected sequence was allowed");
        }else{
            boolean rejected=false;
            try{LocalNegativeAccountTransitionLog.inspect(account);}
            catch(IOException e){
                rejected=has(e,"R55_INCOMPLETE_TRANSITION_MANUAL_REVIEW");
            }
            check(rejected,"orphan PREPARE was treated as reusable");
            rejected=false;
            try{
                LocalNegativeAccountTransitionLog.append(account,0,
                    LocalNegativeAccountTransitionLog.Phase.CHECKPOINT,
                    CHECKPOINT,s->{});
            }catch(IOException e){
                rejected=has(e,"R55_INCOMPLETE_TRANSITION_MANUAL_REVIEW");
            }
            check(rejected,"fresh process silently repaired orphan step");
        }
        System.out.println("R55_FRESH_JVM_NEGATIVE_PASS"+
            " completed="+complete);
    }
    private static Process spawn(
        String mode,Path account,Path ready,
        LocalNegativeAccountTransitionLog.Stage stage,Path log
    )throws IOException{
        String java=System.getProperty("java.home")+File.separator+"bin"+
            File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
        ProcessBuilder b=new ProcessBuilder(java,"-cp",
            System.getProperty("java.class.path"),
            G21383NegativeHeadTransitionProcessKillRegression
                .class.getName(),"--child",mode,account.toString(),
            ready.toString(),stage.name());
        b.redirectErrorStream(true);
        b.redirectOutput(log.toFile());
        return b.start();
    }
    private static String finished(Process p,Path log)throws Exception{
        if(!p.waitFor(20L,TimeUnit.SECONDS)){
            p.destroyForcibly();
            p.waitFor(10L,TimeUnit.SECONDS);
            throw new AssertionError("child timeout "+log);
        }
        String message=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0,"child failed "+message);
        return message;
    }
    private static void crash(
        Path root,LocalNegativeAccountTransitionLog.Stage stage
    )throws Exception{
        String label=stage.name().toLowerCase();
        Path account=root.resolve(label+".properties");
        Path ready=root.resolve(label+".ready");
        Path holderLog=root.resolve(label+".holder.log");
        Path probeLog=root.resolve(label+".probe.log");
        base(account);
        Process holder=spawn("holder",account,ready,stage,holderLog);
        try{
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(System.nanoTime()<end&&holder.isAlive()&&
                    !Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS))
                Thread.sleep(10L);
            check(holder.isAlive()&&Files.isRegularFile(
                    ready,LinkOption.NOFOLLOW_LINKS),
                "holder not at cutpoint "+
                (Files.exists(holderLog)?new String(
                    Files.readAllBytes(holderLog),StandardCharsets.UTF_8):""));
            Path rivalLog=root.resolve(label+".rival.log");
            Process rival=spawn("rival",account,ready,stage,rivalLog);
            check(finished(rival,rivalLog).contains(
                "R55_RIVAL_OS_LOCK_DENIED"),
                "competing process bypassed stable session lock");
            holder.destroyForcibly();
            check(holder.waitFor(10L,TimeUnit.SECONDS)&&
                holder.exitValue()!=0,"holder not killed");
            Process probe=spawn("probe",account,ready,stage,probeLog);
            check(finished(probe,probeLog).contains(
                "R55_FRESH_JVM_NEGATIVE_PASS"),
                "fresh-process negative inspection failed");
            System.out.println("R55_PROCESS_CUTPOINT_PASS stage="+stage+
                " twoJvmLock=true noAutomaticRepair=true");
        }finally{
            if(holder.isAlive()){
                holder.destroyForcibly();
                holder.waitFor(5L,TimeUnit.SECONDS);
            }
        }
    }
    private static void healthy(Path root)throws Exception{
        Path account=root.resolve("healthy.properties");
        base(account);
        LocalNegativeAccountTransitionLog.Observation first=
            LocalNegativeAccountTransitionLog.append(account,0,
                LocalNegativeAccountTransitionLog.Phase.CHECKPOINT,
                CHECKPOINT,s->{});
        inspect(account,1,
            LocalNegativeAccountTransitionLog.Phase.CHECKPOINT);
        boolean rejected=false;
        try{
            LocalNegativeAccountTransitionLog.append(account,0,
                LocalNegativeAccountTransitionLog.Phase.CHECKPOINT,
                CHECKPOINT,s->{});
        }catch(IOException expected){
            rejected=has(expected,"R55_STALE_EXPECTED_SEQUENCE_NEGATIVE");
        }
        check(rejected,"stale checkpoint accepted");
        LocalNegativeAccountTransitionLog.Observation second=
            LocalNegativeAccountTransitionLog.append(account,1,
                LocalNegativeAccountTransitionLog.Phase.FINAL_PENDING,
                FINAL,s->{});
        inspect(account,2,
            LocalNegativeAccountTransitionLog.Phase.FINAL_PENDING);
        rejected=false;
        try{
            LocalNegativeAccountTransitionLog.append(account,2,
                LocalNegativeAccountTransitionLog.Phase.CHECKPOINT,
                CHECKPOINT,s->{});
        }catch(IOException expected){
            rejected=has(expected,"R55_INVALID_TRANSITION_NEGATIVE");
        }
        check(rejected,"checkpoint after final pending accepted");
        LocalNegativeAccountTransitionLog.Observation review=
            LocalNegativeAccountTransitionLog.append(account,2,
                LocalNegativeAccountTransitionLog.Phase.REVIEW,
                null,s->{});
        inspect(account,3,LocalNegativeAccountTransitionLog.Phase.REVIEW);
        check(!first.admissionAuthorized&&!second.admissionAuthorized&&
                !review.releaseAuthorized,"healthy negative chain granted owner");
        rejected=false;
        try{
            LocalNegativeAccountTransitionLog.append(account,3,
                LocalNegativeAccountTransitionLog.Phase.REVIEW,
                null,s->{});
        }catch(IOException expected){
            rejected=has(expected,"R55_INVALID_TRANSITION_NEGATIVE");
        }
        check(rejected,"irreversible review accepted another step");

        // Tampering with an immutable checkpoint must invalidate the
        // entire chain, even if later FINAL/REVIEW records are intact.
        String original=new String(Files.readAllBytes(
            account.resolveSibling(account.getFileName().toString()+
                ".g21383-step-01-prepare")),StandardCharsets.US_ASCII);
        String digest="";
        for(String line:original.split("\n"))
            if(line.startsWith("snapshotSha256="))
                digest=line.substring("snapshotSha256=".length());
        check(digest.matches("[0-9a-f]{64}"),"missing checkpoint digest");
        Path image=account.resolveSibling(account.getFileName().toString()+
            ".g21383-step-01-image-"+digest+".snapshot");
        byte[] corrupt=Files.readAllBytes(image);
        corrupt[0]=(byte)(corrupt[0]^1);
        Files.write(image,corrupt,StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
        rejected=false;
        try{LocalNegativeAccountTransitionLog.inspect(account);}
        catch(IOException expected){
            rejected=has(expected,"R55_INCOMPLETE_TRANSITION_MANUAL_REVIEW");
        }
        check(rejected,"tampered earlier image produced valid chain");
    }
    public static void main(String[] args)throws Exception{
        if(args.length==5&&"--child".equals(args[0])){
            Path account=Paths.get(args[2]);
            LocalNegativeAccountTransitionLog.Stage stage=
                LocalNegativeAccountTransitionLog.Stage.valueOf(args[4]);
            if("holder".equals(args[1]))holder(account,Paths.get(args[3]),stage);
            else if("probe".equals(args[1]))
                probe(account,stage==
                    LocalNegativeAccountTransitionLog.Stage.COMMIT_FORCED);
            else if("rival".equals(args[1])){
                enable();
                boolean denied=false;
                try{
                    LocalNegativeAccountTransitionLog.append(account,0,
                        LocalNegativeAccountTransitionLog.Phase.CHECKPOINT,
                        CHECKPOINT,s->{});
                }catch(IOException error){
                    denied=has(error,"G21.185 ACCOUNT_SESSION_LOCK_BUSY");
                }
                check(denied,"competing lock holder not excluded");
                System.out.println("R55_RIVAL_OS_LOCK_DENIED");
            }else throw new IllegalArgumentException("unknown mode");
            return;
        }
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("r55-negative-epoch-chain-");
        String p=LocalCrossJvmAccountSessionLock.PROPERTY,
            c=LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY;
        String ap=System.getProperty(p),ac=System.getProperty(c);
        try{
            enable();
            for(LocalNegativeAccountTransitionLog.Stage stage:
                    LocalNegativeAccountTransitionLog.Stage.values())
                crash(root,stage);
            healthy(root);
            System.out.println("R55_NEGATIVE_HEAD_MONOTONIC_TRANSITION_PASS"+
                " externalJvmCrashCutpoints=3"+
                " monotonicSteps=checkpoint,finalPending,review"+
                " immutablePrepareCommitChain=true"+
                " staleSequenceRejected=true"+
                " incompleteStepNeverReplayed=true"+
                " priorCheckpointTamperDetected=true"+
                " crossJvmCooperativeExclusion=true"+
                " noLegacyAccountMutation=true"+
                " noPositiveAdmissionOrRelease=true"+
                " defaultOnGameplay=false"+
                " crossProcessAuthenticatedEpoch=false"+
                " powerLossProven=false");
        }finally{
            if(ap==null)System.clearProperty(p);else System.setProperty(p,ap);
            if(ac==null)System.clearProperty(c);else System.setProperty(c,ac);
            try(Stream<Path> tree=Files.walk(root)){
                for(Path f:tree.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(f);
            }
        }
    }
}
