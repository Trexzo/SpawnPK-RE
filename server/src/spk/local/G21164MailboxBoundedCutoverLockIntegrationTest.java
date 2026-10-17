package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.164: the opt-in G21.163 deny-only file cutover must not wait forever
 * on a cooperating account publication owner. Uses actual file locks and
 * real temporary roots, not a mocked lock or an authority-bearing decision.
 */
public final class G21164MailboxBoundedCutoverLockIntegrationTest {
    private static void check(boolean ok,String reason) {
        if(!ok)throw new AssertionError("G21.164 "+reason);
    }

    @FunctionalInterface
    private interface Checked {
        void run()throws Exception;
    }

    private static void veto(Checked work,String expected)throws Exception {
        try {
            work.run();
        }catch(IOException denied){
            check(String.valueOf(denied.getMessage()).contains(expected),
                "unexpected checked denial: "+denied.getMessage()+
                " expected "+expected);
            return;
        }
        throw new AssertionError("G21.164 missed checked veto "+expected);
    }

    private static void run()throws Exception {
        Path root=Files.createTempDirectory("g21164-bounded-cutover-");
        try {
            FilePlayerRepository.PathResolver resolver=
                account->root.resolve(account+".properties");
            FilePlayerRepository repository=new FilePlayerRepository(resolver);
            byte[] candidate=new byte[]{1};
            String busyAccount="g21164-busy";
            String otherAccount="g21164-independent";
            String unsafeAccount="g21164-bad-lock-leaf";
            Path busyFile=resolver.resolve(busyAccount);
            CountDownLatch locked=new CountDownLatch(1);
            CountDownLatch release=new CountDownLatch(1);
            AtomicReference<Throwable> holderFailure=new AtomicReference<>();

            // This is an actual G21.39 cooperating publisher owning
            // the SAME account's JVM monitor and OS advisory lock.
            Thread owner=new Thread(()->{
                try {
                    MailboxAccountPublicationCoordinator.withExclusivePublication(
                        busyFile,()->{
                            locked.countDown();
                            try {
                                if(!release.await(12,TimeUnit.SECONDS))
                                    throw new IOException(
                                        "G21.164 fixture owner release timed out");
                            }catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "G21.164 fixture owner interrupted",
                                    interrupted);
                            }
                            return null;
                        });
                }catch(Throwable failure){
                    holderFailure.set(failure);
                }
            },"g21164-cooperating-lock-owner");

            owner.start();
            long busyWaitMillis=0L;
            try {
                check(locked.await(5,TimeUnit.SECONDS),
                    "competing owner never acquired lock");

                // Account-local scope: another account still completes
                // its genuine disk inspection and fail-closed denial.
                veto(()->repository.requireFreshCutoverUnderPublicationLock(
                    otherAccount,candidate),
                    "NO_POSITIVE_CUTOVER_STATE_MISSING_ACCOUNT_QUARANTINE");

                long started=System.nanoTime();
                veto(()->repository.requireFreshCutoverUnderPublicationLock(
                    busyAccount,candidate),
                    "RECOVERY_PUBLICATION_BUSY_NO_GRANT");
                busyWaitMillis=TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime()-started);
                check(busyWaitMillis<9000L,
                    "optional cutover lock wait was not bounded");
            }finally {
                release.countDown();
                owner.join(6000L);
            }
            check(!owner.isAlive()&&holderFailure.get()==null,
                "competing holder failed or did not release");

            // The failed acquisition must leave no JVM monitor lease.
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "timed-out lock left active JVM leases");
            // After the real cooperating owner releases, the original
            // G21.71 on-disk read still runs and denies missing accounts.
            veto(()->repository.requireFreshCutoverUnderPublicationLock(
                busyAccount,candidate),
                "NO_POSITIVE_CUTOVER_STATE_MISSING_ACCOUNT_QUARANTINE");

            // G21.73's NOFOLLOW lock-leaf guard must be used by this
            // facade: a directory cannot be opened as a safe lock file.
            Path lockLeaf=MailboxAccountPublicationCoordinator.lockPath(
                resolver.resolve(unsafeAccount));
            Files.createDirectory(lockLeaf);
            veto(()->repository.requireFreshCutoverUnderPublicationLock(
                unsafeAccount,candidate),
                "RECOVERY_LOCK_FILE_UNSAFE_NO_GRANT");
            check(Files.isDirectory(lockLeaf),
                "unsafe lock leaf was modified");

            check(!Files.exists(busyFile)&&
                !Files.exists(resolver.resolve(otherAccount))&&
                !Files.exists(resolver.resolve(unsafeAccount)),
                "cutover denial created/modified an account");
            boolean noLeases=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeases,"active JVM lease or watch leaked");

            System.out.println("G21164_BOUNDED_CUTOVER_LOCK_DIAGNOSTICS"+
                " realContendedAccount=1"+
                " busyCheckedVetoes=1 independentAccountVetoes=1"+
                " postReleaseDenials=1 unsafeLockLeafVetoes=1"+
                " timedOutCooperatingOwnerPreserved=true"+
                " acquisitionWaitMillis="+busyWaitMillis+
                " noAccountMutation=true zeroJvmLeases="+noLeases);
            System.out.println("G21164_BOUNDED_CUTOVER_LOCK_PASS"+
                " sameCooperatingLockThroughInspectionAndDenial=true"+
                " lockAcquisitionBounded=true nofollowLockLeaf=true"+
                " rawWritersExcluded=false independentProvenance=false"+
                " atomicEpoch=false cleanup=false rollover=false"+
                " pruning=false unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally {
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception {
        check(args.length==0,"unexpected args");
        run();
    }
}
