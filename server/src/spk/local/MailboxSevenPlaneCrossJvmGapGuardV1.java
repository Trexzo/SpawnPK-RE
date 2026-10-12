package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * G21.128: opt-in negative-only guard of the CALLER-SUPPLIED
 * seven-plane between-pass callback using the EXISTING G21.100
 * cross-process advisory lifecycle lease.
 *
 * Only this callback interval is continuously leased. Captures,
 * semantic checks, pre/post intervals, earlier/later operations and
 * uncooperative/raw writers are NOT fenced. No archive epoch,
 * rollover, pruning, cleanup or World settlement authority.
 */
final class MailboxSevenPlaneCrossJvmGapGuardV1 {
    private static final long MAX_ACQUISITION_MS=1500L;

    /** Same bounded lease entry used by the real seven-plane
     * callback. Exposed to test active foreign contention separately
     * from the independently locked pre/post seven-plane captures.
     */
    static <T> T withGuardedCallback(
        Path selected,long acquisitionMillis,
        MailboxAccountPublicationCoordinator.Operation<T> action
    )throws IOException{
        Objects.requireNonNull(action,"action");
        if(acquisitionMillis<1L||
           acquisitionMillis>MAX_ACQUISITION_MS)
            throw new IllegalArgumentException(
                "G21.128 invalid bounded gap-lease timeout");
        Path file=Objects.requireNonNull(selected,"selected")
            .toAbsolutePath().normalize();
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(file),
                acquisitionMillis,action);
    }

    static MailboxFourBoundResultLeavesArchiveV1.Comparison
        comparePinnedWithGuardedInterpass(
        FilePlayerRepository.PathResolver resolver,
        String account,String resultLeavesSha256,
        String v1Portable,String v1Sha,String accountSha,
        String preparedSha,String commitSha,String v2Sha,
        long acquisitionMillis,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses callback
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Objects.requireNonNull(callback,"callback");
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IOException(
                "G21.128 NONCANONICAL_ACCOUNT_NO_AUTHORITY");
        if(acquisitionMillis<1L||
           acquisitionMillis>MAX_ACQUISITION_MS)
            throw new IllegalArgumentException(
                "G21.128 invalid bounded gap-lease timeout");
        final Path file=Objects.requireNonNull(
            resolver.resolve(account),"account file")
            .toAbsolutePath().normalize();
        final FilePlayerRepository.PathResolver exact=who->{
            if(!account.equals(who))
                throw new IllegalArgumentException(
                    "G21.128 ACCOUNT_RESOLVER_DRIFT_NO_AUTHORITY");
            return file;
        };
        // G21.125/126 deliberately use their own G21.100 leases.
        // NEVER acquire the OS lifecycle lock outside their full
        // comparison; that would nest FileLock and deadlock/overlap.
        // Only the previously UNLOCKED between-pass callback is
        // continuously fenced against cooperating foreign JVMs.
        return MailboxSevenPlaneLocalWriterWatchV1
            .comparePinnedWithLocalAttemptWatch(
                exact,account,resultLeavesSha256,
                v1Portable,v1Sha,accountSha,
                preparedSha,commitSha,v2Sha,()->{
                    withGuardedCallback(file,acquisitionMillis,()->{
                        callback.run();
                        return Boolean.TRUE;
                    });
                });
    }

    private MailboxSevenPlaneCrossJvmGapGuardV1(){}
}
