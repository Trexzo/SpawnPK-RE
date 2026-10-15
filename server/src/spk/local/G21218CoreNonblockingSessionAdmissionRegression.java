package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.218 real concurrent admission; G21.185 OS work stays off global monitor. */
public final class G21218CoreNonblockingSessionAdmissionRegression {
    private G21218CoreNonblockingSessionAdmissionRegression(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.218 "+why);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21218-admission-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY};
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],root.resolve("opensrc.properties").toString());
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"true");
        CountDownLatch entered=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        AtomicReference<Throwable> error=new AtomicReference<>();
        AtomicReference<LocalJvmAccountSessionLease.Token> acquired=
            new AtomicReference<>();
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World one=World.isolatedForTest(60_000L,repository);
        World different=World.isolatedForTest(60_000L,repository);
        Thread worker=null;
        LocalJvmAccountSessionLease.Token other=null;
        try{
            LocalJvmAccountSessionLease.setBeforeOsSessionLockForTest(()->{
                if(Thread.currentThread().getName().equals("g21218-held-io")){
                    entered.countDown();
                    try{
                        if(!release.await(10L,TimeUnit.SECONDS))
                            throw new IllegalStateException("G21.218 latch timeout");
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                }
            });
            worker=new Thread(()->{
                try{acquired.set(LocalJvmAccountSessionLease.acquire(
                    one.persistence(),"g21218-first"));}
                catch(Throwable bad){error.set(bad);}
            },"g21218-held-io");
            worker.start();
            check(entered.await(5L,TimeUnit.SECONDS),
                "OS admission seam not entered");
            check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==1,
                "account not marked pending during OS admission");

            long begin=System.nanoTime();
            other=LocalJvmAccountSessionLease.acquire(
                different.persistence(),"g21218-other");
            long nanos=System.nanoTime()-begin;
            check(other!=null,"unrelated account admission failed");
            check(nanos<TimeUnit.SECONDS.toNanos(3L),
                "unrelated admission waited for held global map monitor");

            boolean sameDenied=false;
            try{
                LocalJvmAccountSessionLease.acquire(
                    different.persistence(),"g21218-first");
            }catch(IllegalStateException expected){
                sameDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "JVM_ACCOUNT_SESSION_OWNED_NO_RECOVERY");
            }
            check(sameDenied,"same-path session bypassed pending owner");

            boolean rawDenied=false;
            try{
                LocalJvmAccountSessionLease.reserveRawWrite(
                    LocalAccountProfiles.accountFile("g21218-first"),
                    "g21218-first");
            }catch(java.io.IOException expected){
                rawDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "RAW_WRITE_DENIED_ACTIVE_JVM_LEASE");
            }
            check(rawDenied,"RAW writer bypassed pending session owner");

            release.countDown();
            worker.join(10_000L);
            check(!worker.isAlive(),"pending acquisition never completed");
            check(error.get()==null,"OS admission worker failed: "+error.get());
            check(acquired.get()!=null,"pending session never admitted");
            check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0,
                "pending owner leaked after successful OS acquire");

            acquired.get().releaseAfterInitializationFailure();
            acquired.set(null);
            other.releaseAfterInitializationFailure();
            other=null;

            // A failing pre-OS hook cannot leave an orphan pending
            // admission that prevents subsequent legitimate sessions.
            LocalJvmAccountSessionLease.setBeforeOsSessionLockForTest(
                ()->{throw new IllegalStateException("G21.218 INJECTED");});
            boolean failed=false;
            try{
                LocalJvmAccountSessionLease.acquire(
                    one.persistence(),"g21218-failure");
            }catch(IllegalStateException expected){
                failed=expected.getMessage()!=null&&
                    expected.getMessage().contains("INJECTED");
            }
            check(failed,"pre-OS failure did not propagate");
            check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0,
                "failed OS admission left pending negative");
            LocalJvmAccountSessionLease.setBeforeOsSessionLockForTest(null);
            LocalJvmAccountSessionLease.Token recovered=
                LocalJvmAccountSessionLease.acquire(
                    one.persistence(),"g21218-failure");
            check(recovered!=null,"retry after injected failure denied");
            recovered.releaseAfterInitializationFailure();
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "JVM session leases leaked");
            System.out.println(
                "G21218_SESSION_OS_IO_OUTSIDE_GLOBAL_MONITOR_PASS"+
                " unrelatedAdmissionProgressed=true"+
                " pendingSameAccountDenied=true"+
                " pendingRawWriterDenied=true"+
                " failureRollbackRetry=true"+
                " noGlobalMonitorOverOsOperation=true"+
                " crossJvmDurableEpoch=false"+
                " crashRecovery=false");
        }finally{
            release.countDown();
            LocalJvmAccountSessionLease.setBeforeOsSessionLockForTest(null);
            if(worker!=null){worker.join(5_000L);}
            if(acquired.get()!=null)
                acquired.get().releaseAfterInitializationFailure();
            if(other!=null)other.releaseAfterInitializationFailure();
            one.close();
            different.close();
            for(int i=0;i<keys.length;i++){
                if(old[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],old[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
