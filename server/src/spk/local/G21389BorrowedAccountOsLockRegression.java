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
 * R61: borrowing the original G21.185 OS lock avoids overlapping
 * same-JVM lock acquisition. Wrong selected account, released handle,
 * external process contention and no positive authority are tested.
 */
public final class G21389BorrowedAccountOsLockRegression {
    private G21389BorrowedAccountOsLockRegression(){}
    private static final byte[] IMAGE=
        "R61_SEPARATE_LEDGER_IMAGE".getBytes(StandardCharsets.US_ASCII);
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("R61_BORROWED_LOCK "+message);
    }
    private static boolean has(Throwable t,String term){
        for(Throwable cause=t;cause!=null;cause=cause.getCause())
            if(String.valueOf(cause.getMessage()).contains(term))return true;
        return false;
    }
    @FunctionalInterface interface Op{void run()throws IOException;}
    private static void denied(String reason,Op op)throws Exception{
        boolean no=false;
        try{op.run();}catch(IOException failure){no=has(failure,reason);}
        check(no,"expected refusal "+reason);
    }
    private static void enable(){
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
    }
    private static Process spawn(String mode,Path account,Path log)
        throws IOException{
        String java=System.getProperty("java.home")+File.separator+"bin"+
            File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
        ProcessBuilder builder=new ProcessBuilder(java,"-cp",
            System.getProperty("java.class.path"),
            G21389BorrowedAccountOsLockRegression.class.getName(),
            "--child",mode,account.toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());
        return builder.start();
    }
    private static String completed(Process process,Path log)
        throws Exception{
        if(!process.waitFor(20,TimeUnit.SECONDS)){
            process.destroyForcibly();
            process.waitFor(10,TimeUnit.SECONDS);
            throw new AssertionError("child timeout");
        }
        String result=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(process.exitValue()==0,"child failed "+result);
        return result;
    }
    private static void child(String mode,Path account)throws Exception{
        enable();
        if(!"contender".equals(mode))
            throw new IllegalArgumentException("unexpected child");
        denied("G21.185 ACCOUNT_SESSION_LOCK_BUSY",()->
            LocalReusableAccountEpochLab.claim(account,0,901L,
                "r61_other_world"));
        System.out.println("R61_FRESH_JVM_LOCK_EXCLUDED");
    }
    private static void exercise(Path root)throws Exception{
        Path account=root.resolve("borrowed.properties");
        Path foreign=root.resolve("foreign.properties");
        LocalReusableAccountEpochLab.initialize(account,IMAGE);
        LocalReusableAccountEpochLab.initialize(foreign,IMAGE);
        LocalCrossJvmAccountSessionLock.Handle owned=
            LocalCrossJvmAccountSessionLock.acquire(account);
        try{
            // Same JVM must not attempt another OS FileLock while the
            // session-like owner retains its original acquired handle.
            denied("G21.185 ACCOUNT_SESSION_LOCK_BUSY",()->
                LocalReusableAccountEpochLab.claim(account,0,901L,
                    "r61_other_world"));
            denied("R61_BORROWED_LOCK_WRONG_ACCOUNT_DENIED",()->
                LocalReusableAccountEpochLab.claimUnderHeldAccountLock(
                    foreign,owned,0,901L,"r61_wrong_account"));
            check(LocalReusableAccountEpochLab.inspect(foreign).sequence==0,
                "foreign selected account was mutated");
            Path rivalLog=root.resolve("rival.log");
            check(completed(spawn("contender",account,rivalLog),
                rivalLog).contains("R61_FRESH_JVM_LOCK_EXCLUDED"),
                "foreign process bypassed original OS lock");
            LocalReusableAccountEpochLab.Observation claimed=
                LocalReusableAccountEpochLab.claimUnderHeldAccountLock(
                    account,owned,0,901L,"r61_world_owner");
            check(claimed.phase==LocalReusableAccountEpochLab.Phase.OWNED&&
                claimed.sequence==1&&claimed.epoch==2&&
                !claimed.admissionAuthorized&&!claimed.releaseAuthorized,
                "borrowed lock created real account authority");
            check(LocalReusableAccountEpochLab.inspect(account).sequence==1,
                "HEAD not durably inspected while lock held");
            check(LocalReusableAccountEpochLab.inspect(foreign).sequence==0,
                "foreign account was impacted");
        }finally{
            owned.close();
        }
        denied("R61_BORROWED_LOCK_RELEASED_DENIED",()->
            LocalReusableAccountEpochLab.claimUnderHeldAccountLock(
                account,owned,1,902L,"r61_world_owner"));
        LocalReusableAccountEpochLab.Observation next=
            LocalReusableAccountEpochLab.prepareFinal(
                account,1,901L,"r61_world_owner");
        check(next.sequence==2&&!next.releaseAuthorized,
            "ordinary reacquisition after holder close failed");
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS)&&
              !Files.exists(foreign,LinkOption.NOFOLLOW_LINKS),
            "LAB mutated actual legacy account bytes");
        System.out.println("R61_OS_LOCK_BORROWED_OPERATION_PASS"+
            " sameJvmDoubleAcquireDenied=true"+
            " freshJvmContenderDenied=true"+
            " selectedAccountBound=true"+
            " ownerRetainsClose=true"+
            " releasedHandleDenied=true"+
            " nextNormalLockAcquireAfterClose=true"+
            " labOnlyNonadmitting=true"+
            " liveWorldTokenWiring=false"+
            " crossProcessAuthenticatedOwnership=false");
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0])){
            child(args[1],Paths.get(args[2]));return;
        }
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("r61-held-original-lock-");
        String p=LocalCrossJvmAccountSessionLock.PROPERTY;
        String c=LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY;
        String oldP=System.getProperty(p),oldC=System.getProperty(c);
        try{
            enable();
            exercise(root);
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "real JVM session token leaked");
        }finally{
            if(oldP==null)System.clearProperty(p);
            else System.setProperty(p,oldP);
            if(oldC==null)System.clearProperty(c);
            else System.setProperty(c,oldC);
            try(Stream<Path> all=Files.walk(root)){
                for(Path path:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }
}
