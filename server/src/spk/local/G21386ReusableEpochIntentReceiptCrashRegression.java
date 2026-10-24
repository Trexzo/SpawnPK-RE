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

/**
 * R58: forced immutable intent + receipt must prevent reopening an
 * old CLOSED head after an interrupted CLAIM and must detect rolled
 * back previous HEAD history. Child-JVM evidence only, not power-loss
 * proof, authenticated ownership or positive login authority.
 */
public final class G21386ReusableEpochIntentReceiptCrashRegression {
    private G21386ReusableEpochIntentReceiptCrashRegression(){}
    private static final byte[] IMAGE=
        "R58_ACCOUNT_IMAGE".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FINAL=
        "R58_ACCOUNT_FINAL".getBytes(StandardCharsets.US_ASCII);
    private static final String NONCE="world_lab_r58";
    @FunctionalInterface interface Action{void run()throws IOException;}
    private static void check(boolean x,String reason){
        if(!x)throw new AssertionError("R58_JOURNAL "+reason);
    }
    private static boolean has(Throwable t,String fragment){
        for(Throwable e=t;e!=null;e=e.getCause())
            if(String.valueOf(e.getMessage()).contains(fragment))
                return true;
        return false;
    }
    private static void denies(String fragment,Action a)throws Exception{
        boolean rejected=false;
        try{a.run();}
        catch(IOException error){rejected=has(error,fragment);}
        check(rejected,"did not reject "+fragment);
    }
    private static void enable(){
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            "true");
    }
    private static Path head(Path account){
        return account.resolveSibling(
            account.getFileName().toString()+".g21385-ledger-head");
    }
    private static Path receipt(Path account,int sequence){
        return account.resolveSibling(
            account.getFileName().toString()+".g21386-receipt-"+
            String.format(java.util.Locale.ROOT,"%04d",sequence));
    }
    private static void noAccountMutation(Path account)throws Exception{
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "lab wrote legacy account");
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "live World session leaked");
    }
    private static void healthy(Path root)throws Exception{
        Path account=root.resolve("healthy.properties");
        enable();
        LocalReusableAccountEpochLab.initialize(account,IMAGE);
        byte[] genesisHead=Files.readAllBytes(head(account));
        LocalReusableAccountEpochLab.claim(account,0,400L,NONCE);
        LocalReusableAccountEpochLab.prepareFinal(account,1,400L,NONCE);
        LocalReusableAccountEpochLab.settleLabFinal(
            account,2,400L,NONCE,FINAL);
        LocalReusableAccountEpochLab.Observation closed=
            LocalReusableAccountEpochLab.inspect(account);
        check(closed.sequence==3&&closed.epoch==2&&
              closed.phase==LocalReusableAccountEpochLab.Phase.CLOSED&&
              !closed.admissionAuthorized&&!closed.releaseAuthorized,
              "lab CLOSED was treated as positive grant");
        check(Files.exists(receipt(account,0))&&
              Files.exists(receipt(account,1))&&
              Files.exists(receipt(account,2))&&
              Files.exists(receipt(account,3)),
              "historical immutable receipts missing");
        // Old CLOSED head is byte-identical but superseded; immutable
        // intent/receipt evidence must catch its pathname rollback.
        Files.write(head(account),genesisHead,StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
        denies("R58_ORPHAN_OR_MISSING_JOURNAL_MANUAL_REVIEW",
            ()->LocalReusableAccountEpochLab.inspect(account));
        denies("R58_ORPHAN_OR_MISSING_JOURNAL_MANUAL_REVIEW",
            ()->LocalReusableAccountEpochLab.claim(
                account,0,500L,"world_lab_r58_other"));
        noAccountMutation(account);

        // Separate valid chain with a corrupted historical receipt:
        // the current HEAD bytes are unchanged, but history is wrong.
        Path tamper=root.resolve("tamper.properties");
        LocalReusableAccountEpochLab.initialize(tamper,IMAGE);
        LocalReusableAccountEpochLab.claim(tamper,0,400L,NONCE);
        Path genesisReceipt=receipt(tamper,0);
        byte[] prior=Files.readAllBytes(genesisReceipt);
        byte[] mutated=prior.clone();
        int change=prior.length/2;
        mutated[change]=(byte)(mutated[change]^1);
        Files.write(genesisReceipt,mutated,StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
        denies("R58_",()->LocalReusableAccountEpochLab.inspect(tamper));
        noAccountMutation(tamper);
    }
    private static void holder(
        Path account,Path ready,LocalReusableAccountEpochLab.Stage target
    )throws Exception{
        enable();
        LocalReusableAccountEpochLab.claim(account,0,333L,NONCE,stage->{
            if(stage!=target)return;
            Files.write(ready,new byte[]{1},StandardOpenOption.CREATE_NEW);
            System.out.println("R58_HOLDER_AT "+target);
            System.out.flush();
            try{Thread.sleep(120000L);}
            catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                throw new IOException("R58_INTERRUPTED",interrupted);
            }
            throw new AssertionError("holder was not killed");
        });
    }
    private static void probe(
        Path account,boolean receiptForced
    )throws Exception{
        enable();
        if(receiptForced){
            LocalReusableAccountEpochLab.Observation completed=
                LocalReusableAccountEpochLab.inspect(account);
            check(completed.sequence==1&&completed.epoch==2&&
                completed.phase==LocalReusableAccountEpochLab.Phase.OWNED&&
                !completed.admissionAuthorized&&!completed.releaseAuthorized,
                "complete but lab-only HEAD was treated as a grant");
        }else{
            denies("R58_ORPHAN_OR_MISSING_JOURNAL_MANUAL_REVIEW",
                ()->LocalReusableAccountEpochLab.inspect(account));
            denies("R58_ORPHAN_OR_MISSING_JOURNAL_MANUAL_REVIEW",
                ()->LocalReusableAccountEpochLab.claim(
                    account,0,444L,"world_lab_r58_other"));
        }
        noAccountMutation(account);
        System.out.println("R58_FRESH_PROCESS_STATE_PASS"+
            " receiptForced="+receiptForced);
    }
    private static Process spawn(String mode,Path a,Path ready,
        LocalReusableAccountEpochLab.Stage stage,Path log)throws IOException{
        String java=System.getProperty("java.home")+File.separator+"bin"+
            File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
        ProcessBuilder b=new ProcessBuilder(java,"-cp",
            System.getProperty("java.class.path"),
            G21386ReusableEpochIntentReceiptCrashRegression.class.getName(),
            "--child",mode,a.toString(),ready.toString(),stage.name());
        b.redirectErrorStream(true);
        b.redirectOutput(log.toFile());
        return b.start();
    }
    private static String end(Process p,Path log)throws Exception{
        if(!p.waitFor(20,TimeUnit.SECONDS)){
            p.destroyForcibly();
            p.waitFor(10,TimeUnit.SECONDS);
            throw new AssertionError("child timed out");
        }
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0,"child failed: "+output);
        return output;
    }
    private static void crash(
        Path root,LocalReusableAccountEpochLab.Stage stage
    )throws Exception{
        String label=stage.name().toLowerCase(
            java.util.Locale.ROOT);
        Path account=root.resolve(label+".properties");
        Path ready=root.resolve(label+".ready");
        Path holderLog=root.resolve(label+".holder.log");
        Path probeLog=root.resolve(label+".probe.log");
        enable();
        LocalReusableAccountEpochLab.initialize(account,IMAGE);
        Process holder=spawn("holder",account,ready,stage,holderLog);
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(System.nanoTime()<deadline&&holder.isAlive()&&
                    !Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS))
                Thread.sleep(10L);
            check(holder.isAlive()&&Files.isRegularFile(
                    ready,LinkOption.NOFOLLOW_LINKS),
                "holder cutpoint missing "+stage+
                (Files.exists(holderLog)?new String(
                    Files.readAllBytes(holderLog),StandardCharsets.UTF_8):""));
            holder.destroyForcibly();
            check(holder.waitFor(10,TimeUnit.SECONDS)&&
                holder.exitValue()!=0,"holder not killed");
            check(end(spawn("probe",account,ready,stage,probeLog),
                probeLog).contains("R58_FRESH_PROCESS_STATE_PASS"),
                "restarted process was not fail-closed");
        }finally{
            if(holder.isAlive()){
                holder.destroyForcibly();holder.waitFor(10,TimeUnit.SECONDS);
            }
        }
        System.out.println("R58_PROCESS_CUTPOINT_PASS stage="+stage);
    }
    public static void main(String[] args)throws Exception{
        if(args.length==5&&"--child".equals(args[0])){
            LocalReusableAccountEpochLab.Stage stage=
                LocalReusableAccountEpochLab.Stage.valueOf(args[4]);
            Path account=Paths.get(args[2]);
            if("holder".equals(args[1]))
                holder(account,Paths.get(args[3]),stage);
            else if("probe".equals(args[1]))
                probe(account,
                    stage==LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED);
            else throw new IllegalArgumentException("mode");
            return;
        }
        check(args.length==0,"args");
        Path root=Files.createTempDirectory("r58-ledger-journal-");
        String p=LocalCrossJvmAccountSessionLock.PROPERTY,
            c=LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY;
        String oldP=System.getProperty(p),oldC=System.getProperty(c);
        try{
            enable();
            healthy(root);
            crash(root,LocalReusableAccountEpochLab.Stage.INTENT_FORCED);
            crash(root,LocalReusableAccountEpochLab.Stage
                .HEAD_MOVED_BEFORE_DIR_FORCE);
            crash(root,LocalReusableAccountEpochLab.Stage.RECEIPT_FORCED);
            System.out.println("R58_REUSABLE_LEDGER_JOURNAL_PASS"+
                " writeAheadIntentForced=true"+
                " forcedReceiptAfterHeadDirectory=true"+
                " oldClosedHeadRollbackDenied=true"+
                " historicalReceiptTamperDenied=true"+
                " killedProcessCutpoints=3"+
                " incompleteClaimNeverReused=true"+
                " allStatesNonadmitting=true"+
                " actualWorldSessionWiring=false"+
                " machinePowerLossProven=false"+
                " externallyAtomicCAS=false");
        }finally{
            if(oldP==null)System.clearProperty(p);
            else System.setProperty(p,oldP);
            if(oldC==null)System.clearProperty(c);
            else System.setProperty(c,oldC);
            try(Stream<Path> files=Files.walk(root)){
                for(Path f:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(f);
            }
        }
    }
}
