package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * G21.119: NEGATIVE read-only separation of opt-in diagnostic attempts
 * from observed real Mailbox writer-kind labels AND the independently
 * classified G21.86 disk COMMIT terminal/intention sidecars.
 *
 * A typed v2 KIND is a caller-supplied label, not an authenticated
 * proof that a real business operation executed. Even the strongest
 * matching status is not a settlement, historical writer census,
 * future writer fence or durable coherent multi-file transaction.
 */
final class MailboxRealWriterSemanticBoundaryV1 {
    enum Status {
        DIAGNOSTIC_ONLY_NOT_BUSINESS_EVIDENCE,
        DIAGNOSTIC_MIXED_WITH_REAL_NOT_BUSINESS_ONLY,
        MISSING_REAL_WRITER_KINDS_NO_AUTHORITY,
        REAL_KIND_LABELS_WITHOUT_DISK_COMMIT,
        FOUR_REAL_KIND_LABELS_AND_DISK_MATCH_NO_GRANT
    }

    static final class Report {
        final Status status;
        final MailboxWriterArchiveLiveReconciliationV1.Report fivePlane;
        final MailboxGuardedDiskCommitRecord.Observation diskCommit;
        final int realWriterKindPairs;
        final int diagnosticPairs;
        final boolean allFourRealLabelsObserved;
        final boolean sourceWriterKindAuthenticityProved=false;
        final boolean labelUuidBoundToDiskCommitProved=false;
        final boolean historicalWriterParticipationProved=false;
        final boolean allFutureWritersFenced=false;
        final boolean epochOrTransactionAtomicityProved=false;
        final boolean businessOperationOrLiveApplyProved=false;
        final boolean archiveRolloverAuthorized=false;
        final boolean recordPruningAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(
            Status s,MailboxWriterArchiveLiveReconciliationV1.Report w,
            MailboxGuardedDiskCommitRecord.Observation d,
            int real,int diagnostics,boolean allKinds
        ){
            status=s;
            fivePlane=w;
            diskCommit=d;
            realWriterKindPairs=real;
            diagnosticPairs=diagnostics;
            allFourRealLabelsObserved=allKinds;
        }
    }

    private static int count(
        Map<MailboxWriterKindEvidenceV2.Kind,Integer> map,
        MailboxWriterKindEvidenceV2.Kind key
    ){
        return map.getOrDefault(key,0);
    }

    /**
     * Pins must be independently supplied; this class never generates
     * or authenticates them. The G21.115 two-pass pinned witness is
     * checked BEFORE the semantic classification, then G21.86 is
     * inspected read-only using its established account publication
     * lock. This is NOT one atomic cross-pass epoch. Any intervening
     * uncooperative writer remains outside this observational claim.
     */
    static Report inspectPinned(
        FilePlayerRepository.PathResolver resolver,
        String account,String v1Portable,String v1Sha,
        String accountSha,String preparedPin,String commitPin,
        String v2Sha
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.119 NONCANONICAL_ACCOUNT_NO_AUTHORITY");
        // Resolve once; all downstream observations use this path.
        final Path selected=Objects.requireNonNull(
            resolver.resolve(account),"account path")
            .toAbsolutePath().normalize();
        FilePlayerRepository.PathResolver exact=who->{
            if(!account.equals(who))
                throw new IllegalArgumentException(
                    "G21.119 SELECTED_ACCOUNT_DRIFT_NO_AUTHORITY");
            return selected;
        };
        MailboxWriterArchiveLiveReconciliationV1.Report witness=
            MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedTypedRecoveryLeaves(
                    exact,account,v1Portable,v1Sha,accountSha,
                    preparedPin,commitPin,v2Sha);
        MailboxWriterKindEvidenceV2.Report typed=
            Objects.requireNonNull(witness.typedWriterKindReport,
                "G21.119 expected typed witness");
        if(typed.status!=
            MailboxWriterKindEvidenceV2.Status
                .OBSERVED_TYPED_PAIRED_NO_AUTHORITY||
           typed.untypedV1Leaves!=0)
            throw new IOException(
                "G21.119 UNKNOWN_OR_INCOMPLETE_TYPED_NO_AUTHORITY");

        Map<MailboxWriterKindEvidenceV2.Kind,Integer> pairs=
            typed.pairedByKind;
        int diagnostic=count(
            pairs,MailboxWriterKindEvidenceV2.Kind
                .COOPERATING_DIAGNOSTIC);
        int strict=count(pairs,MailboxWriterKindEvidenceV2.Kind
            .STRICT_PREPARED);
        int journal=count(pairs,MailboxWriterKindEvidenceV2.Kind
            .PREPARED_JOURNAL);
        int terminal=count(pairs,MailboxWriterKindEvidenceV2.Kind
            .WORLD_TERMINAL);
        int commit=count(pairs,MailboxWriterKindEvidenceV2.Kind
            .DISK_COMMIT);
        int real=strict+journal+terminal+commit;
        boolean hasFour=strict>0&&journal>0&&terminal>0&&commit>0;

        // When diagnostic evidence is present, do not promote it to a
        // fifth business kind, even if all four real labels also exist.
        if(diagnostic>0)
            return new Report(
                real==0
                    ?Status.DIAGNOSTIC_ONLY_NOT_BUSINESS_EVIDENCE
                    :Status.DIAGNOSTIC_MIXED_WITH_REAL_NOT_BUSINESS_ONLY,
                witness,null,real,diagnostic,hasFour);

        if(!hasFour)
            return new Report(
                Status.MISSING_REAL_WRITER_KINDS_NO_AUTHORITY,
                witness,null,real,0,false);

        // Typed labels can be fabricated by a caller of the v1/v2
        // primitive. Actual G21.86 checked disk evidence is mandatory
        // to even classify this as a provisional business candidate.
        MailboxGuardedDiskCommitRecord.Observation disk=
            new MailboxGuardedDiskCommitRecord(exact).inspect(account);
        boolean matched=
            disk.status==MailboxGuardedDiskCommitRecord.Status
                .DISK_COMMIT_MATCH_NO_LIVE_APPLY&&
            disk.recordValidated&&disk.diskCommitRecordMatched&&
            !disk.transactionCommitted&&!disk.liveApplied&&
            !disk.grantAuthorized&&!disk.replayAuthorized&&
            !disk.restartAdmissionAuthorized&&
            !disk.clientAckAuthorized;
        return new Report(
            matched
                ?Status.FOUR_REAL_KIND_LABELS_AND_DISK_MATCH_NO_GRANT
                :Status.REAL_KIND_LABELS_WITHOUT_DISK_COMMIT,
            witness,disk,real,0,true);
    }

    /**
     * Explicit strict NEGATIVE fence for callers seeking a read-only
     * real-kind candidate. Success conveys NO authorization or grant.
     */
    static Report requirePinnedRealWriterCandidate(
        FilePlayerRepository.PathResolver resolver,
        String account,String v1Portable,String v1Sha,
        String accountSha,String preparedPin,String commitPin,
        String v2Sha
    )throws IOException{
        Report r=inspectPinned(resolver,account,v1Portable,v1Sha,
            accountSha,preparedPin,commitPin,v2Sha);
        if(r.status!=Status.FOUR_REAL_KIND_LABELS_AND_DISK_MATCH_NO_GRANT)
            throw new IOException(
                "G21.119 REAL_WRITER_SEMANTIC_GATE_NO_AUTHORITY "+
                "status="+r.status+" rollover=false cleanup=false"+
                " grant=false replay=false");
        return r;
    }

    private MailboxRealWriterSemanticBoundaryV1(){}
}
