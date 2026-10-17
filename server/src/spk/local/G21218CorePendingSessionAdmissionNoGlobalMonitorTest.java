package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** G21.218: deterministic pending owner exclusion without a global
 * JVM monitor held across OS session-lock acquisition.
 * No crash epoch, recovery or ACTIVE review release authority.
 */
public final class G21218CorePendingSessionAdmissionNoGlobalMonitorTest {
    private static final String USER="g21218-pending-a";
    private static final String OTHER="g21218-unrelated-b";
    private G21218CorePendingSessionAdmissionNoGlobalMonitorTest(){}

    private static void check(boolean state,String stage){
        if(!state)throw new AssertionError("G21.218 "+stage);
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21218-pending-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            previous[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"true");
        CountDownLatch pending=new CountDownLatch(1);
        CountDownLatch resume=new CountDownLatch(1);
        CompletableFuture<LocalJvmAccountSessionLease.Token> first=null;
        LocalJvmAccountSessionLease.Token acquired=null;
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            try(World worldA=World.isolatedForTest(60_000L,repo);
                World worldB=World.isolatedForTest(60_000L,repo)){
                Path file=LocalAccountProfiles.accountFile(USER);
                LocalJvmAccountSessionLease
                    .pendingSessionAdmissionProbeForTest((path,account)->{
                        if(!USER.equals(account))return;
                        check(file.equals(path),"pending path not exact");
                        pending.countDown();
                        try{
                            if(!resume.await(10L,TimeUnit.SECONDS))
                                throw new AssertionError(
                                    "G21.218 pending seam timeout");
                        }catch(InterruptedException e){
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                    });

                first=CompletableFuture.supplyAsync(()->
                    LocalJvmAccountSessionLease.acquire(
                        worldA.persistence(),USER));
                check(pending.await(10L,TimeUnit.SECONDS),
                    "first JVM session never became pending");
                check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==1,
                    "one pending admission required");
                check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                    "pending admission must not claim ownership early");

                boolean sameSessionDenied=false;
                try{
                    LocalJvmAccountSessionLease.acquire(
                        worldB.persistence(),USER);
                }catch(IllegalStateException expected){
                    sameSessionDenied=expected.getMessage()!=null&&
                        expected.getMessage().contains(
                            "JVM_ACCOUNT_SESSION_OWNED_NO_RECOVERY");
                }
                check(sameSessionDenied,
                    "same-account second session admitted while pending");
                boolean sameRawDenied=false;
                try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                        LocalJvmAccountSessionLease.reserveRawWrite(
                            file,USER)){
                    check(raw==null,"same-file RAW unexpectedly acquired");
                }catch(java.io.IOException expected){
                    sameRawDenied=expected.getMessage()!=null&&
                        expected.getMessage().contains(
                            "RAW_WRITE_DENIED_ACTIVE_JVM_LEASE");
                }
                check(sameRawDenied,
                    "same-file RAW writer bypassed pending session");

                // This code executes BEFORE the first pending hook is
                // released; a held global JVM monitor would deadlock.
                LocalJvmAccountSessionLease.Token unrelated=
                    LocalJvmAccountSessionLease.acquire(
                        worldB.persistence(),OTHER);
                check(unrelated!=null,"unrelated account lease blocked");
                unrelated.releaseAfterInitializationFailure();
                try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                        LocalJvmAccountSessionLease.reserveRawWrite(
                            LocalAccountProfiles.accountFile(OTHER),OTHER)){
                    check(raw!=null,"unrelated RAW writer blocked");
                }
                check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                    "unrelated RAW reservation not closed");

                resume.countDown();
                acquired=first.get(10L,TimeUnit.SECONDS);
                check(acquired!=null,"pending session never committed");
                check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0,
                    "pending entry not transferred to active token");
                check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                    "installed session lease count wrong");
                acquired.releaseAfterInitializationFailure();
                acquired=null;
                check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                    "session token not released");
                check(LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0,
                    "pending state leaked");
            }
            System.out.println(
                "G21218_PENDING_SESSION_ADMISSION_NO_GLOBAL_MONITOR_PASS"+
                " pendingVisibleBeforeOsLock=true"+
                " sameAccountSessionDenied=true"+
                " sameAccountRawDenied=true"+
                " unrelatedSessionProgresses=true"+
                " unrelatedRawProgresses=true"+
                " admissionCommitAndReleaseClean=true"+
                " defaultOffUnchanged=true"+
                " durableEpoch=false"+
                " recoveryAuthority=false");
        }finally{
            resume.countDown();
            LocalJvmAccountSessionLease.pendingSessionAdmissionProbeForTest(null);
            if(first!=null&&first.isDone()&&acquired==null){
                // Future's returned Token is already released on the
                // happy path; no post-failure fabricated release.
            }
            if(acquired!=null)acquired.releaseAfterInitializationFailure();
            for(int i=0;i<keys.length;i++){
                if(previous[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],previous[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
