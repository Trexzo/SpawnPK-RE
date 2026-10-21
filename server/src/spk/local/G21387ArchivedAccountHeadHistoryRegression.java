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
 * R59: retain complete immutable HEAD bytes and verify EVERY historic
 * HEAD/image, not merely a chain of receipt SHA-256 strings.
 * Child JVM hard-kill before INTENT must leave a permanent negative.
 */
public final class G21387ArchivedAccountHeadHistoryRegression {
    private G21387ArchivedAccountHeadHistoryRegression(){}
    private static final byte[] INITIAL=
        "r59-old-image".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FINAL=
        "r59-final-image".getBytes(StandardCharsets.US_ASCII);
    private static final String OWNER="r59_world_owner_a";
    @FunctionalInterface interface Action {void run()throws IOException;}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("R59_ARCHIVE "+message);
    }
    private static boolean has(Throwable t,String term){
        for(Throwable x=t;x!=null;x=x.getCause())
            if(String.valueOf(x.getMessage()).contains(term))return true;
        return false;
    }
    private static void rejected(String term,Action action)throws Exception{
        boolean denied=false;
        try{action.run();}
        catch(IOException err){denied=has(err,term);}
        check(denied,"did not refuse "+term);
    }
    private static void enable(){
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
    }
    private static Path archive(Path account,int sequence)
        throws IOException{
        String prefix=account.getFileName().toString()+
            ".g21387-head-"+String.format(java.util.Locale.ROOT,"%04d",sequence)+
            "-";
        Path result=null;
        try(java.nio.file.DirectoryStream<Path> children=
                Files.newDirectoryStream(account.getParent())){
            for(Path path:children){
                if(!path.getFileName().toString().startsWith(prefix))
                    continue;
                check(result==null,"multiple archives for sequence");
                result=path;
            }
        }
        check(result!=null,"missing archive for sequence "+sequence);
        return result;
    }
    private static void healthy(Path root)throws Exception{
        Path account=root.resolve("healthy.properties");
        LocalReusableAccountEpochLab.initialize(account,INITIAL);
        LocalReusableAccountEpochLab.claim(account,0,161L,OWNER);
        LocalReusableAccountEpochLab.prepareFinal(account,1,161L,OWNER);
        LocalReusableAccountEpochLab.settleLabFinal(
            account,2,161L,OWNER,FINAL);
        LocalReusableAccountEpochLab.claim(
            account,3,261L,"r59_world_owner_b");
        LocalReusableAccountEpochLab.Observation state=
            LocalReusableAccountEpochLab.inspect(account);
        check(state.sequence==4&&state.epoch==3&&
            state.phase==LocalReusableAccountEpochLab.Phase.OWNED&&
            !state.admissionAuthorized&&!state.releaseAuthorized,
            "completed reusable LAB granted live admission");
        for(int sequence=0;sequence<=4;sequence++)
            check(Files.isRegularFile(archive(account,sequence),
                LinkOption.NOFOLLOW_LINKS),
                "full historical HEAD was not retained");
        // Corrupt an OLD immutable snapshot. It is no longer the
        // current head's image, so R57's current-image check alone
        // would not detect it.
        Path original=account.resolveSibling(
            account.getFileName()+".g21385-image-0000-"+sha(INITIAL)+
            ".snapshot");
        byte[] mutation=Files.readAllBytes(original);
        mutation[0]^=1;
        Files.write(original,mutation,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
        rejected("R59_HISTORICAL_IMAGE_TAMPERED_NEGATIVE",
            ()->LocalReusableAccountEpochLab.inspect(account));
        rejected("R59_HISTORICAL_IMAGE_TAMPERED_NEGATIVE",
            ()->LocalReusableAccountEpochLab.prepareFinal(
                account,4,261L,"r59_world_owner_b"));
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "historic snapshot experiment touched legacy account");

        Path tamper=root.resolve("archive-tamper.properties");
        LocalReusableAccountEpochLab.initialize(tamper,INITIAL);
        LocalReusableAccountEpochLab.claim(tamper,0,161L,OWNER);
        Path olderHead=archive(tamper,0);
        byte[] originalHead=Files.readAllBytes(olderHead);
        byte[] changed=originalHead.clone();
        changed[5]^=1;
        Files.write(olderHead,changed,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
        rejected("R59_ARCHIVED_HEAD_DIGEST_NEGATIVE",
            ()->LocalReusableAccountEpochLab.inspect(tamper));
        check(Files.exists(olderHead,LinkOption.NOFOLLOW_LINKS),
            "corrupt HEAD archive was automatically retired");

        Path unpaired=root.resolve("unpaired.properties");
        LocalReusableAccountEpochLab.initialize(unpaired,INITIAL);
        byte[] bytes=Files.readAllBytes(archive(unpaired,0));
        Path rogue=unpaired.resolveSibling(unpaired.getFileName()+
            ".g21387-head-0001-"+sha(bytes)+".head");
        Files.write(rogue,bytes,StandardOpenOption.CREATE_NEW);
        rejected("R59_ORPHAN_OR_MISSING_ARCHIVE_MANUAL_REVIEW",
            ()->LocalReusableAccountEpochLab.inspect(unpaired));
        rejected("R59_ORPHAN_OR_MISSING_ARCHIVE_MANUAL_REVIEW",
            ()->LocalReusableAccountEpochLab.claim(
                unpaired,0,161L,OWNER));
        check(Arrays.equals(bytes,Files.readAllBytes(rogue)),
            "orphan archive was automatically replayed");
        Path foreign=root.resolve("foreign.properties");
        LocalReusableAccountEpochLab.initialize(foreign,INITIAL);
        check(LocalReusableAccountEpochLab.inspect(foreign).sequence==0,
            "another account's archival evidence blocked foreign owner");
    }
    private static String sha(byte[] bytes)throws Exception{
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes);
        StringBuilder s=new StringBuilder();
        for(byte b:digest){
            String hex=Integer.toHexString(b&255);
            if(hex.length()==1)s.append('0');
            s.append(hex);
        }
        return s.toString();
    }
    private static void child(String mode,Path account,Path ready)
        throws Exception{
        enable();
        if("holder".equals(mode)){
            LocalReusableAccountEpochLab.claim(account,0,161L,OWNER,stage->{
                if(stage!=LocalReusableAccountEpochLab.Stage
                        .ARCHIVED_HEAD_FORCED)return;
                Files.write(ready,new byte[]{1},
                    StandardOpenOption.CREATE_NEW);
                System.out.println("R59_ARCHIVE_FORCED_CHILD");
                System.out.flush();
                try{Thread.sleep(120000L);}
                catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    throw new IOException("R59_KILLED",e);
                }
                throw new AssertionError("holder not killed");
            });
        }else if("probe".equals(mode)){
            rejected("R59_ORPHAN_OR_MISSING_ARCHIVE_MANUAL_REVIEW",
                ()->LocalReusableAccountEpochLab.inspect(account));
            rejected("R59_ORPHAN_OR_MISSING_ARCHIVE_MANUAL_REVIEW",
                ()->LocalReusableAccountEpochLab.claim(
                    account,0,161L,OWNER));
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "restart modified historical account");
            System.out.println("R59_FRESH_JVM_ORPHAN_ARCHIVE_DENIED");
        }else throw new IllegalArgumentException("role");
    }
    private static Process spawn(
        String role,Path account,Path ready,Path log)throws IOException{
        String java=System.getProperty("java.home")+File.separator+"bin"+
            File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
        ProcessBuilder p=new ProcessBuilder(java,"-cp",
            System.getProperty("java.class.path"),
            G21387ArchivedAccountHeadHistoryRegression.class.getName(),
            "--child",role,account.toString(),ready.toString());
        p.redirectErrorStream(true);
        p.redirectOutput(log.toFile());
        return p.start();
    }
    private static String complete(Process p,Path log)throws Exception{
        if(!p.waitFor(20L,TimeUnit.SECONDS)){
            p.destroyForcibly();p.waitFor(10L,TimeUnit.SECONDS);
            throw new AssertionError("child timed out");
        }
        String value=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0,"child failure "+value);
        return value;
    }
    private static void crash(Path root)throws Exception{
        Path account=root.resolve("crash.properties");
        LocalReusableAccountEpochLab.initialize(account,INITIAL);
        Path ready=root.resolve("crash.ready");
        Path holderLog=root.resolve("holder.log");
        Process holder=spawn("holder",account,ready,holderLog);
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20L);
            while(holder.isAlive()&&System.nanoTime()<deadline&&
                !Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS))
                Thread.sleep(10L);
            check(holder.isAlive()&&Files.isRegularFile(
                ready,LinkOption.NOFOLLOW_LINKS),
                "holder missed archive force cutpoint");
            holder.destroyForcibly();
            check(holder.waitFor(10L,TimeUnit.SECONDS)&&
                holder.exitValue()!=0,"holder survived forced termination");
            Path probeLog=root.resolve("probe.log");
            check(complete(spawn("probe",account,ready,probeLog),
                probeLog).contains("R59_FRESH_JVM_ORPHAN_ARCHIVE_DENIED"),
                "restart accepted orphan archive");
            check(Files.isRegularFile(archive(account,1),
                LinkOption.NOFOLLOW_LINKS),
                "orphan archive removed");
        }finally{
            if(holder.isAlive()){
                holder.destroyForcibly();holder.waitFor(10L,TimeUnit.SECONDS);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            child(args[1],Paths.get(args[2]),Paths.get(args[3]));
            return;
        }
        check(args.length==0,"args");
        Path root=Files.createTempDirectory("r59-history-");
        String lock=LocalCrossJvmAccountSessionLock.PROPERTY;
        String continuity=LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY;
        String a=System.getProperty(lock),b=System.getProperty(continuity);
        try{
            enable();
            healthy(root);
            crash(root);
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "real World session leaked");
            System.out.println("R59_COMPLETE_IMMUTABLE_HEAD_HISTORY_PASS"+
                " historicalHeadBytesRetained=true"+
                " pastSnapshotIntegrityAudited=true"+
                " oldHeadByteTamperDenied=true"+
                " orphanFutureHeadArchiveDenied=true"+
                " killedJvmBeforeIntentFailClosed=true"+
                " historicalPhaseAndEpochAudited=true"+
                " noAccountFileMutation=true"+
                " defaultOnProduction=false"+
                " machinePowerLossProven=false"+
                " liveWorldAdmissionAuthority=false");
        }finally{
            if(a==null)System.clearProperty(lock);
            else System.setProperty(lock,a);
            if(b==null)System.clearProperty(continuity);
            else System.setProperty(continuity,b);
            try(Stream<Path> files=Files.walk(root)){
                for(Path f:files.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(f);
            }
        }
    }
}
