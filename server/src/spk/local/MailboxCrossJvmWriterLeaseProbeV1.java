package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * G21.117: read-only instant-level evidence of ACTIVE G21.100
 * cooperating writer lease contention, across JVMs on the filesystem
 * supporting the existing advisory account lifecycle lock.
 *
 * This is NOT a history of attempted writers, a generation fence,
 * quiescence epoch, account authorization or cleanup permit.
 */
final class MailboxCrossJvmWriterLeaseProbeV1 {
    private static final long MAX_PROBE_MS=1500L;

    enum State {
        QUIET_AT_SAMPLE_ONLY,
        COOPERATING_LEASE_BUSY_UNKNOWN
    }

    static final class Sample {
        final State state;
        final boolean localAndCrossJvmHistoryKnown=false;
        final boolean historicalWritersCovered=false;
        final boolean futureWritersExcluded=false;
        final boolean uncooperativeWritersExcluded=false;
        final boolean inactivePriorWriterProved=false;
        final boolean generationBoundaryEstablished=false;
        final boolean crashDurabilityProved=false;
        final boolean archiveRolloverAuthorized=false;
        final boolean recordPruningAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Sample(State value){state=value;}
    }

    @FunctionalInterface
    interface BetweenSamples {
        void run()throws IOException;
    }
    static final class TwoSamples {
        final Sample before;
        final Sample after;
        final boolean observedBusy;
        final boolean entireIntervalQuiescentProved=false;
        final boolean allWriterGenerationsFenced=false;
        final boolean cleanupAuthorized=false;
        final boolean rolloverAuthorized=false;
        final boolean grantAuthorized=false;
        private TwoSamples(Sample a,Sample b){
            before=a;after=b;
            observedBusy=
                a.state==State.COOPERATING_LEASE_BUSY_UNKNOWN||
                b.state==State.COOPERATING_LEASE_BUSY_UNKNOWN;
        }
    }

    static Sample sample(
        FilePlayerRepository.PathResolver resolver,
        String account,long timeoutMillis
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.117 INVALID_CANONICAL_ACCOUNT");
        if(timeoutMillis<1L||timeoutMillis>MAX_PROBE_MS)
            throw new IllegalArgumentException(
                "G21.117 invalid bounded probe timeout");
        Path selected=Objects.requireNonNull(
            resolver.resolve(account),"account file")
            .toAbsolutePath().normalize();
        Path lifecycle=MailboxPublicationWriterLifecycle.domain(selected);
        try{
            // Use the EXACT G21.100 cross-process stable advisory lease.
            // The probe is never a writer; its callback performs NO
            // account, sidecar or attempt-evidence I/O.
            return MailboxAccountPublicationCoordinator
                .withExclusivePublicationBounded(
                    lifecycle,timeoutMillis,
                    ()->new Sample(State.QUIET_AT_SAMPLE_ONLY));
        }catch(IOException error){
            // Only the established bounded timeout means active lease
            // contention. Propagate unsafe lock ancestry, symlinks,
            // interruption and unrelated I/O failures without masking.
            if(error.getMessage()!=null&&error.getMessage().startsWith(
                "G21.73 RECOVERY_PUBLICATION_BUSY_NO_GRANT"))
                return new Sample(State.COOPERATING_LEASE_BUSY_UNKNOWN);
            throw error;
        }
    }

    /**
     * Two INSTANT samples with an UNLOCKED interval between them.
     * Even two quiet results cannot detect a completed cross-JVM
     * writer attempt in the gap. This exists to make that limitation
     * directly testable, not to certify an archival epoch.
     */
    static TwoSamples sampleAround(
        FilePlayerRepository.PathResolver resolver,
        String account,long timeoutMillis,BetweenSamples between
    )throws IOException{
        Objects.requireNonNull(between,"between");
        final Sample before=sample(resolver,account,timeoutMillis);
        between.run();
        final Sample after=sample(resolver,account,timeoutMillis);
        return new TwoSamples(before,after);
    }

    private MailboxCrossJvmWriterLeaseProbeV1(){}
}
