package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * G21.124: NEGATIVE-ONLY four-result typed writer cohort observation.
 *
 * A strong matching set of four separately confirmed opt-in disk
 * results DOES NOT authenticate their author, fence historical/raw/
 * future writers, seal an atomic epoch, or authorize live settlement,
 * archive turnover, unlink, pruning or cleanup.
 */
final class MailboxFourBoundWriterCohortAuditV1 {
    private static final long LOCK_MS=1500L;

    static final class Report {
        final String preparedSha256,terminalSha256;
        final boolean fourIndependentBoundResultsObserved;
        final boolean exactlyOneObservedPairPerRealKind;
        final boolean historicalWriterCensusProved=false;
        final boolean allDefaultRawFutureWritersExcluded=false;
        final boolean independentWriterProvenanceProved=false;
        final boolean multiFileAtomicEpochProved=false;
        final boolean hardwarePowerLossProved=false;
        final boolean archiveRolloverAuthorized=false;
        final boolean recordPruningAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        private Report(String p,String t){
            preparedSha256=p;
            terminalSha256=t;
            fourIndependentBoundResultsObserved=true;
            exactlyOneObservedPairPerRealKind=true;
        }
    }

    static final class TwoObservations {
        final Report before,after;
        final boolean observedAgreement;
        final boolean entireBetweenIntervalFenced=false;
        final boolean atomicMultiFileSnapshotProved=false;
        final boolean cleanupAuthorized=false;
        final boolean rolloverAuthorized=false;
        final boolean grantAuthorized=false;
        private TwoObservations(Report first,Report second){
            before=first;
            after=second;
            observedAgreement=
                first.preparedSha256.equals(second.preparedSha256)&&
                first.terminalSha256.equals(second.terminalSha256);
        }
    }

    private static IOException veto(String why){
        return new IOException(
            "G21.124 FOUR_BOUND_COHORT_UNSAFE_NO_AUTHORITY "+
            why+" epoch=false cleanup=false rollover=false grant=false");
    }

    private static String account(String name)throws IOException{
        if(name==null||!name.matches("[a-z0-9_-]{1,64}"))
            throw veto("NONCANONICAL_ACCOUNT");
        return name;
    }

    private static int observed(
        Map<MailboxWriterKindEvidenceV2.Kind,Integer> map,
        MailboxWriterKindEvidenceV2.Kind type
    ){
        return map.getOrDefault(type,0);
    }

    private static Report observeOnce(
        FilePlayerRepository.PathResolver resolver,String name,
        String v1Portable,String v1Sha,String accountPin,
        String preparedPin,String commitPin,String v2Sha
    )throws IOException{
        // G21.119 is a previously certified, separately pinned
        // two-pass check and must run OUTSIDE a G21.100 lease because
        // it owns that lifecycle itself. No claim of one atomic epoch.
        MailboxRealWriterSemanticBoundaryV1.Report prior=
            MailboxRealWriterSemanticBoundaryV1
                .requirePinnedRealWriterCandidate(
                    resolver,name,v1Portable,v1Sha,accountPin,
                    preparedPin,commitPin,v2Sha);
        MailboxWriterKindEvidenceV2.Report typed=
            prior.fivePlane.typedWriterKindReport;
        Map<MailboxWriterKindEvidenceV2.Kind,Integer> map=
            typed.pairedByKind;
        if(prior.realWriterKindPairs!=4||
           prior.diagnosticPairs!=0||
           prior.fivePlane.matchedRecords!=8||
           prior.fivePlane.archive.started!=4||
           prior.fivePlane.archive.completed!=4||
           typed.typedBegins!=4||typed.typedCompletes!=4||
           typed.untypedV1Leaves!=0||
           observed(map,MailboxWriterKindEvidenceV2.Kind
               .STRICT_PREPARED)!=1||
           observed(map,MailboxWriterKindEvidenceV2.Kind
               .PREPARED_JOURNAL)!=1||
           observed(map,MailboxWriterKindEvidenceV2.Kind
               .WORLD_TERMINAL)!=1||
           observed(map,MailboxWriterKindEvidenceV2.Kind
               .DISK_COMMIT)!=1)
            throw veto("NOT_EXACTLY_FOUR_REAL_RESULT_ATTEMPTS");

        final Path selected=Objects.requireNonNull(
            resolver.resolve(name),"account file")
            .toAbsolutePath().normalize();
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(selected),
                LOCK_MS,()->{
                    // All FOUR result observations share one G21.100
                    // account lifecycle lease; each uses existing
                    // bounded G21.39 account publication checks.
                    MailboxStrictPreparedResultBindingV3.Report strict=
                        MailboxStrictPreparedResultBindingV3
                            .inspectInside(selected,name);
                    MailboxPreparedJournalResultBindingV3.Report journal=
                        MailboxPreparedJournalResultBindingV3
                            .inspectInside(selected,name);
                    MailboxWorldTerminalResultBindingV3.Report terminal=
                        MailboxWorldTerminalResultBindingV3
                            .inspectInside(selected,name);
                    MailboxCommitResultBindingV3.Report commit=
                        MailboxCommitResultBindingV3
                            .inspectInside(selected,name);
                    if(strict.status!=
                        MailboxStrictPreparedResultBindingV3.Status
                            .BOUND_HISTORICAL_PREPARED_WITH_TERMINAL_JOURNAL_NO_AUTHORITY||
                       journal.status!=
                        MailboxPreparedJournalResultBindingV3.Status
                            .BOUND_PREPARED_JOURNAL_MATCH_NO_AUTHORITY||
                       terminal.status!=
                        MailboxWorldTerminalResultBindingV3.Status
                            .BOUND_REAL_TERMINAL_DISK_MATCH_NO_AUTHORITY||
                       commit.status!=
                        MailboxCommitResultBindingV3.Status
                            .BOUND_COMMIT_DISK_MATCH_NO_AUTHORITY||
                       strict.boundAttempts!=1||
                       journal.boundAttempts!=1||
                       terminal.boundAttempts!=1||
                       commit.boundAttempts!=1)
                        throw veto("MISSING_UNBOUND_OR_MULTIPLE_RESULT");
                    final String p=strict.preparedSha256;
                    final String t=terminal.terminalSha256;
                    if(p==null||t==null||
                       !p.equals(journal.preparedSha256)||
                       !p.equals(terminal.preparedSha256)||
                       !t.equals(journal.terminalSha256)||
                       !t.equals(commit.terminalSha256)||
                       !accountPin.equals(
                           terminal.terminalFileBytesSha256)||
                       !preparedPin.equals(journal.journalBytesSha256)||
                       !commitPin.equals(commit.commitBytesSha256)||
                       prior.diskCommit==null||
                       !t.equals(prior.diskCommit.terminalSha256)||
                       strict.originalPreparedFileStillPresent)
                        throw veto("FOUR_RESULT_CONTENT_HASH_DIVERGENCE");
                    // Historical strict PREPARED raw bytes were
                    // legitimately replaced by terminal; NEVER
                    // equate strict.strictFileSha256 to accountPin.
                    return new Report(p,t);
                });
    }

    /**
     * Read-only two-observation opt-in. The callback runs WITHOUT
     * the lifecycle lock between observations. Changes that persist
     * to a checked plane are denied by existing independent pins;
     * transient writes/rollback, outside writers and future activity
     * can be invisible. Success is NEVER an archive/settlement token.
     */
    static TwoObservations comparePinnedFourBoundResults(
        FilePlayerRepository.PathResolver resolver,String name,
        String v1Portable,String v1Sha,String accountPin,
        String preparedPin,String commitPin,String v2Sha,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Objects.requireNonNull(between,"between");
        final String selectedAccount=account(name);
        final Path file=Objects.requireNonNull(
            resolver.resolve(selectedAccount),"account file")
            .toAbsolutePath().normalize();
        final FilePlayerRepository.PathResolver exact=who->{
            if(!selectedAccount.equals(who))
                throw new IllegalArgumentException(
                    "G21.124 ACCOUNT_RESOLVER_DRIFT_NO_AUTHORITY");
            return file;
        };
        Report first=observeOnce(exact,selectedAccount,
            v1Portable,v1Sha,accountPin,preparedPin,commitPin,v2Sha);
        between.run();
        Report second=observeOnce(exact,selectedAccount,
            v1Portable,v1Sha,accountPin,preparedPin,commitPin,v2Sha);
        if(!first.preparedSha256.equals(second.preparedSha256)||
           !first.terminalSha256.equals(second.terminalSha256))
            throw veto("DIFFERENT_RESULT_COHORT_BETWEEN_SAMPLES");
        return new TwoObservations(first,second);
    }

    private MailboxFourBoundWriterCohortAuditV1(){}
}
