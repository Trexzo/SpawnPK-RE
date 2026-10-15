package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.218: a PENDING per-account OS-lock acquisition must not hold
 * the entire JVM session registry monitor or admit same-file RAW.
 */
public final class G21218CorePendingSessionOsAdmissionIsolationTest {
    private G21218CorePendingSessionOsAdmissionIsolationTest(){}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.218 "+why);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21218-pending-os-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY};
        String[] before=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            before[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"true");
        ExecutorService workers=Executors.newSingleThreadExecutor();
        CountDownLatch hookEntered=new CountDownLatch(1);
        CountDownLatch permitFinish=new CountDownLatch(1);
        LocalJvmAccountSessionLease.Token secondary=null;
        LocalJvmAccountSessionLease.Token first=null;
        World worldA=null;
        World worldB=null;
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            worldA=World.isolatedForTest(60_000L,repo);
            worldB=World.isolatedForTest(60_000L,repo);
            final World pendingWorld=worldA;
            String a="g21218-primary",b="g21218-unrelated";
            Path pathA=LocalAccountProfiles.accountFile(a);
            Path pathB=LocalAccountProfiles.accountFile(b);
            LocalJvmAccountSessionLease.setBeforeSessionOsAcquireForTest(
                ()->{
                    if(Thread.currentThread().getName().contains(
                            "pool-")){
                        hookEntered.countDown();
                        try{
                            if(!permitFinish.await(10L,TimeUnit.SECONDS))
                                throw new AssertionError(
                                    "pending OS fixture timed out");
                        }catch(InterruptedException interrupted){
                            Thread.currentThread().interrupt();
                            throw new AssertionError(
                                "pending OS fixture interrupted",interrupted);
                        }
                    }
                });
            Future<LocalJvmAccountSessionLease.Token> pending=
                workers.submit(()->LocalJvmAccountSessionLease.acquire(
                    pendingWorld.persistence(),a));
            check(hookEntered.await(5L,TimeUnit.SECONDS),
                "OS admission seam was never entered");
            check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==1,
                "exact one pending account OS admission expected");

            long start=System.nanoTime();
            secondary=LocalJvmAccountSessionLease.acquire(
                worldB.persistence(),b);
            check(secondary!=null,"independent account session refused");
            check(TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime()-start)<4_000L,
                "unrelated account blocked on pending OS acquisition");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "unrelated admitted owner count wrong");

            boolean sameDenied=false;
            try{
                LocalJvmAccountSessionLease.acquire(
                    worldB.persistence(),a);
            }catch(IllegalStateException expected){
                sameDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "JVM_ACCOUNT_SESSION_ADMISSION_PENDING");
            }
            check(sameDenied,
                "second same-path session entered pending OS admission");

            boolean rawDenied=false;
            try{
                LocalJvmAccountSessionLease.reserveRawWrite(pathA,a);
            }catch(IOException expected){
                rawDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "RAW_WRITE_DENIED_PENDING_SESSION_ADMISSION");
            }
            check(rawDenied,
                "same-path RAW admitted during pending session");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "refused RAW leaked a reservation");
            check(Files.isRegularFile(
                LocalAccountProfiles.accountFile(a))==false,
                "fixture unexpectedly wrote account bytes");

            permitFinish.countDown();
            first=pending.get(10L,TimeUnit.SECONDS);
            check(first!=null,"pending admitted owner missing");
            check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0,
                "pending account reservation not cleared");
            check(LocalJvmAccountSessionLease.activeCountForTest()==2,
                "both unrelated account owners not registered");
            check(!pathA.equals(pathB),"fixture paths coincide");
            System.out.println(
                "G21218_PENDING_SESSION_OS_ADMISSION_ISOLATION_PASS"+
                " pendingAdmissionRecorded=true"+
                " unrelatedOwnerAcceptedWithoutGlobalOsWait=true"+
                " sameFileSecondOwnerDenied=true"+
                " sameFileRawWriterDenied=true"+
                " bothUnrelatedOsLocksHeld=true"+
                " pendingStateCleaned=true"+
                " durableEpoch=false"+
                " crossProcessCrashRecovery=false");
        }finally{
            permitFinish.countDown();
            LocalJvmAccountSessionLease.setBeforeSessionOsAcquireForTest(null);
            workers.shutdownNow();
            workers.awaitTermination(5L,TimeUnit.SECONDS);
            try{
                if(first!=null)first.releaseAfterInitializationFailure();
                if(secondary!=null)
                    secondary.releaseAfterInitializationFailure();
                if(worldA!=null)worldA.close();
                if(worldB!=null)worldB.close();
            }finally{
                for(int i=0;i<keys.length;i++){
                    if(before[i]==null)System.clearProperty(keys[i]);
                    else System.setProperty(keys[i],before[i]);
                }
                try(Stream<Path> walk=Files.walk(root)){
                    for(Path p:walk.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))
                        Files.deleteIfExists(p);
                }
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
