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
 * R62: the one original G21.185 FileLock is continuously borrowed
 * through an entire detached LAB final lifecycle, never granting
 * real World admission or authenticated final release.
 * R75 moves even initial immutable LAB creation under the same held
 * lock, rejecting wrong-account, duplicate and released borrow attempts.
 */
public final class G21390BorrowedLockFullFinalLifecycleRegression {
    private G21390BorrowedLockFullFinalLifecycleRegression(){}
    private static final byte[] IMAGE=
        "R62_PREVIOUS_IMAGE".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FINAL=
        "R62_FINAL_IMAGE".getBytes(StandardCharsets.US_ASCII);
    private static final String OWNER="r62_session_owner";
    @FunctionalInterface interface Op{void run()throws IOException;}
    private static void check(boolean value,String why){
        if(!value)throw new AssertionError("R62_BORROWED_FINAL "+why);
    }
    private static void denied(String reason,Op operation)throws Exception{
        boolean rejected=false;
        try{operation.run();}
        catch(IOException error){
            for(Throwable t=error;t!=null;t=t.getCause())
                if(String.valueOf(t.getMessage()).contains(reason))
                    rejected=true;
        }
        check(rejected,"expected refusal "+reason);
    }
    private static void enable(){
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
    }
    private static Process spawn(String role,Path account,Path log)
        throws IOException{
        String java=System.getProperty("java.home")+File.separator+
            "bin"+File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
        ProcessBuilder builder=new ProcessBuilder(java,"-cp",
            System.getProperty("java.class.path"),
            G21390BorrowedLockFullFinalLifecycleRegression.class.getName(),
            "--child",role,account.toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());
        return builder.start();
    }
    private static String finish(Process p,Path log)throws Exception{
        if(!p.waitFor(20,TimeUnit.SECONDS)){
            p.destroyForcibly();p.waitFor(10,TimeUnit.SECONDS);
            throw new AssertionError("child timed out");
        }
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0,"child failed "+output);
        return output;
    }
    private static void child(String role,Path account)throws Exception{
        enable();
        if("contender".equals(role)){
            denied("G21.185 ACCOUNT_SESSION_LOCK_BUSY",()->
                LocalReusableAccountEpochLab.claim(
                    account,0,710L,"r62_rival_owner"));
            System.out.println("R62_RIVAL_EXCLUDED");
        }else if("uncertain".equals(role)){
            denied("R58_ORPHAN_OR_MISSING_JOURNAL_MANUAL_REVIEW",()->
                LocalReusableAccountEpochLab.inspect(account));
            denied("R58_ORPHAN_OR_MISSING_JOURNAL_MANUAL_REVIEW",()->
                LocalReusableAccountEpochLab.claim(
                    account,3,710L,"r62_rival_owner"));
            System.out.println("R62_FRESH_PROCESS_UNCERTAIN_DENIED");
        }else throw new IllegalArgumentException("role");
    }
    private static void normal(Path root)throws Exception{
        Path account=root.resolve("normal.properties");
        LocalCrossJvmAccountSessionLock.Handle ownerLock=
            LocalCrossJvmAccountSessionLock.acquire(account);
        try{
            LocalReusableAccountEpochLab.Observation seed=
                LocalReusableAccountEpochLab.initializeUnderHeldAccountLock(
                    account,ownerLock,IMAGE);
            check(seed.sequence==0&&
                seed.phase==LocalReusableAccountEpochLab.Phase.CLOSED&&
                !seed.admissionAuthorized&&!seed.releaseAuthorized,
                "borrowed initial ledger claimed live authority");
            denied("R57_LEDGER_ALREADY_INITIALIZED",()->
                LocalReusableAccountEpochLab.initializeUnderHeldAccountLock(
                    account,ownerLock,IMAGE));
            denied("R61_BORROWED_LOCK_WRONG_ACCOUNT_DENIED",()->
                LocalReusableAccountEpochLab.initializeUnderHeldAccountLock(
                    root.resolve("other.properties"),ownerLock,IMAGE));
            denied("G21.185 ACCOUNT_SESSION_LOCK_BUSY",()->
                LocalReusableAccountEpochLab.initialize(account,IMAGE));
            Path log=root.resolve("contender.log");
            check(finish(spawn("contender",account,log),log).contains(
                "R62_RIVAL_EXCLUDED"),"cross-JVM session lock lost");
            LocalReusableAccountEpochLab.Observation owned=
                LocalReusableAccountEpochLab.claimUnderHeldAccountLock(
                    account,ownerLock,0,610L,OWNER);
            LocalReusableAccountEpochLab.Observation pending=
                LocalReusableAccountEpochLab
                    .prepareFinalUnderHeldAccountLock(
                        account,ownerLock,1,610L,OWNER);
            check(owned.sequence==1&&pending.sequence==2&&
                pending.phase==LocalReusableAccountEpochLab
                    .Phase.FINAL_PENDING,
                "claim and final prepare did not preserve same handle");
            denied("R57_OWNER_CONTINUITY_DENIED",()->
                LocalReusableAccountEpochLab
                    .settleLabFinalUnderHeldAccountLock(
                        account,ownerLock,2,710L,"r62_rival_owner",
                        FINAL,stage->{}));
            LocalReusableAccountEpochLab.Observation closed=
                LocalReusableAccountEpochLab
                    .settleLabFinalUnderHeldAccountLock(
                        account,ownerLock,2,610L,OWNER,FINAL,stage->{});
            check(closed.sequence==3&&closed.epoch==2&&
                closed.phase==LocalReusableAccountEpochLab.Phase.CLOSED&&
                !closed.admissionAuthorized&&!closed.releaseAuthorized,
                "lab-only CLOSED claimed admission authority");
            check(LocalReusableAccountEpochLab.inspect(account).sequence==3,
                "final record could not be read under original lock");
        }finally{ownerLock.close();}
        denied("R61_BORROWED_LOCK_RELEASED_DENIED",()->
            LocalReusableAccountEpochLab.prepareFinalUnderHeldAccountLock(
                account,ownerLock,3,610L,OWNER));
        denied("R61_BORROWED_LOCK_RELEASED_DENIED",()->
            LocalReusableAccountEpochLab.initializeUnderHeldAccountLock(
                account,ownerLock,IMAGE));
        LocalReusableAccountEpochLab.Observation successor=
            LocalReusableAccountEpochLab.claim(
                account,3,710L,"r62_next_owner");
        check(successor.epoch==3&&successor.sequence==4&&
              !successor.admissionAuthorized,"next LAB epoch invalid");
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "lab modified real account file");
    }
    private static void incomplete(Path root)throws Exception{
        Path account=root.resolve("incomplete.properties");
        LocalCrossJvmAccountSessionLock.Handle ownerLock=
            LocalCrossJvmAccountSessionLock.acquire(account);
        try{
            LocalReusableAccountEpochLab.initializeUnderHeldAccountLock(
                account,ownerLock,IMAGE);
            LocalReusableAccountEpochLab.claimUnderHeldAccountLock(
                account,ownerLock,0,610L,OWNER);
            LocalReusableAccountEpochLab.prepareFinalUnderHeldAccountLock(
                account,ownerLock,1,610L,OWNER);
            denied("R62_SYNTHETIC_POST_MOVE_FAILURE",()->
                LocalReusableAccountEpochLab
                    .settleLabFinalUnderHeldAccountLock(
                        account,ownerLock,2,610L,OWNER,FINAL,stage->{
                            if(stage==LocalReusableAccountEpochLab.Stage
                                .HEAD_MOVED_BEFORE_DIR_FORCE)
                                throw new IOException(
                                    "R62_SYNTHETIC_POST_MOVE_FAILURE");
                        }));
            denied("R58_ORPHAN_OR_MISSING_JOURNAL_MANUAL_REVIEW",()->
                LocalReusableAccountEpochLab.inspect(account));
            denied("R58_ORPHAN_OR_MISSING_JOURNAL_MANUAL_REVIEW",()->
                LocalReusableAccountEpochLab
                    .settleLabFinalUnderHeldAccountLock(
                        account,ownerLock,2,610L,OWNER,FINAL,stage->{}));
        }finally{ownerLock.close();}
        Path log=root.resolve("incomplete.probe.log");
        check(finish(spawn("uncertain",account,log),log).contains(
            "R62_FRESH_PROCESS_UNCERTAIN_DENIED"),
            "independent process accepted incomplete final");
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "failed LAB final touched original account");
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0])){
            child(args[1],Paths.get(args[2]));return;
        }
        check(args.length==0,"args");
        Path root=Files.createTempDirectory("r62-borrowed-final-");
        String p=LocalCrossJvmAccountSessionLock.PROPERTY,
            c=LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY;
        String beforeP=System.getProperty(p),beforeC=System.getProperty(c);
        try{
            enable();
            normal(root);
            incomplete(root);
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "live session token unexpectedly changed");
            System.out.println("R75_BORROWED_LEDGER_INITIALIZATION_PASS"+
                " sameOriginalFileLockFromGenesisThroughFinal=true"+
                " noNestedPOSIXFileChannel=true"+
                " duplicateInitializationDenied=true"+
                " wrongAccountBorrowDenied=true"+
                " releasedBorrowDenied=true"+
                " ordinaryInitialAcquireRefusedWhileOwnerHeld=true"+
                " recoveredLABAuthorityStillFalse=true");
            System.out.println("R62_ORIGINAL_BORROWED_LOCK_FINAL_PASS"+
                " fullLabLifecycleSameHandle=true"+
                " noNestedFileChannel=true"+
                " foreignJvmDenied=true"+
                " foreignOwnerRejected=true"+
                " postmoveUncertainFailsClosed=true"+
                " independentJvmRecoveryDenied=true"+
                " noRealWorldAuthority=true"+
                " noPowerLossClaim=true");
        }finally{
            if(beforeP==null)System.clearProperty(p);
            else System.setProperty(p,beforeP);
            if(beforeC==null)System.clearProperty(c);
            else System.setProperty(c,beforeC);
            try(Stream<Path> all=Files.walk(root)){
                for(Path file:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
    }
}
