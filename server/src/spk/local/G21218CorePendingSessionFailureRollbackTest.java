package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.218 FAIL-CLOSED pending session rollback.
 * An OS session-lock acquisition error must clear pending state
 * without installing a misleading JVM Token or enabling RAW bytes.
 */
public final class G21218CorePendingSessionFailureRollbackTest {
    private static final String USER="g21218-os-error-rollback";
    private G21218CorePendingSessionFailureRollbackTest(){}
    private static void check(boolean condition,String stage){
        if(!condition)throw new AssertionError("G21.218 rollback "+stage);
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21218-rollback-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"true");
        try{
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            Path account=LocalAccountProfiles.accountFile(USER);
            try(World world=World.isolatedForTest(60_000L,repository)){
                AtomicInteger probeCalls=new AtomicInteger();
                LocalJvmAccountSessionLease
                    .pendingSessionAdmissionProbeForTest(
                        (path,username)->{
                            if(USER.equals(username))probeCalls.incrementAndGet();
                        });

                // Hold the real stable OS lock with a separate Java
                // channel. The pending JVM session cannot claim it.
                try(LocalCrossJvmAccountSessionLock.Handle foreign=
                        LocalCrossJvmAccountSessionLock.acquire(account)){
                    boolean denied=false;
                    try{
                        LocalJvmAccountSessionLease.acquire(
                            world.persistence(),USER);
                    }catch(IllegalStateException expected){
                        denied=expected.getMessage()!=null&&
                            expected.getMessage().contains(
                                "CROSS_JVM_ACCOUNT_SESSION_DENIED");
                    }
                    check(denied,
                        "OS FileLock collision admitted competing session");
                    check(probeCalls.get()==1,
                        "pending probe did not execute before OS failure");
                    check(LocalJvmAccountSessionLease
                            .pendingSessionAdmissionsForTest()==0,
                        "OS error leaked pending admission");
                    check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                        "OS error installed an active JVM Token");

                    // The cooperating RAW path also refuses this
                    // foreign OS lock before creating any account file.
                    boolean rawDenied=false;
                    try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                            LocalJvmAccountSessionLease.reserveRawWrite(
                                account,USER)){
                        check(raw==null,"foreign OS lock allowed RAW");
                    }catch(java.io.IOException expected){
                        rawDenied=expected.getMessage()!=null&&
                            expected.getMessage().contains(
                                "CROSS_JVM_RAW_ACCOUNT_WRITE_DENIED");
                    }
                    check(rawDenied,"RAW bypassed real OS lock collision");
                    check(LocalJvmAccountSessionLease
                            .rawWriteReservationsForTest()==0,
                        "RAW failed acquisition leaked negative reservation");
                    check(!Files.exists(account),
                        "denied session/RAW created account file");
                }

                // Probe failure is another non-I/O acquisition abort:
                // cleanup must release its own pending map entry.
                LocalJvmAccountSessionLease
                    .pendingSessionAdmissionProbeForTest(
                        (path,user)->{
                            if(USER.equals(user))
                                throw new IllegalStateException(
                                    "G21.218_TEST_ONLY_PROBE_THROW");
                        });
                boolean probeDenied=false;
                try{
                    LocalJvmAccountSessionLease.acquire(
                        world.persistence(),USER);
                }catch(IllegalStateException expected){
                    probeDenied=expected.getMessage()!=null&&
                        expected.getMessage().contains(
                            "G21.218_TEST_ONLY_PROBE_THROW");
                }
                check(probeDenied,"probe abort was not propagated");
                check(LocalJvmAccountSessionLease
                        .pendingSessionAdmissionsForTest()==0,
                    "probe error stranded pending admission");
                check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                    "probe error created session token");

                LocalJvmAccountSessionLease
                    .pendingSessionAdmissionProbeForTest(null);
                LocalJvmAccountSessionLease.Token recovered=
                    LocalJvmAccountSessionLease.acquire(
                        world.persistence(),USER);
                check(recovered!=null,
                    "subsequent normal opt-in acquisition failed");
                check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                    "recovery token not present");
                recovered.releaseAfterInitializationFailure();
                check(LocalJvmAccountSessionLease.activeCountForTest()==0&&
                        LocalJvmAccountSessionLease
                            .pendingSessionAdmissionsForTest()==0,
                    "normal release failed after previous errors");
            }
            System.out.println(
                "G21218_PENDING_SESSION_OS_FAILURE_ROLLBACK_PASS"+
                " realOsLockBusyDenied=true"+
                " pendingClearedAfterIoFailure=true"+
                " rawIoCollisionDeniedBeforeAccountCreate=true"+
                " noFalseTokenAfterError=true"+
                " pendingClearedAfterInjectedAbort=true"+
                " cleanLaterAcquisition=true"+
                " finalSaveReceipt=false"+
                " restartRecoveryAuthority=false");
        }finally{
            LocalJvmAccountSessionLease.pendingSessionAdmissionProbeForTest(null);
            for(int i=0;i<keys.length;i++){
                if(old[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],old[i]);
            }
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }
}
