package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * G21.160: ONE bounded, read-only admission composition for
 * historical/current seven-plane evidence and candidate signer.
 *
 * The two reports are caller-retained snapshots after their original
 * lease; comparing them neither extends a lock nor authenticates
 * history or an atomic epoch. A mathematical signature and matching
 * caller-selected precommitment can only be REVIEWED, never admitted.
 *
 * Production recovery admission REQUIRES G21.159 independent signer
 * root admission, which is intentionally unavailable in this build.
 * No production recovery or World reward path invokes this new API.
 */
final class MailboxHistoricalRecoveryAdmissionV1 {
    enum Status {
        MATCHED_UNTRUSTED_CANDIDATE_REVIEW_NO_AUTHORITY,
        CHANGED_OBSERVED_EVIDENCE_NO_AUTHORITY,
        UNCERTAIN_OBSERVED_EVIDENCE_NO_AUTHORITY
    }

    static final class Review {
        final Status status;
        final SortedSet<String> changedSources;
        final SortedSet<String> uncertainSources;
        final boolean historicalAndCurrentObservedMatch;
        final boolean candidateSignatureReviewed;
        final boolean independentlyAuthenticatedProvenance=false;
        final boolean productionSignerTrustAdmitted=false;
        final boolean originalFileKeyIdentityAuthenticated=false;
        final boolean rawOrDefaultWriterExclusionProved=false;
        final boolean historicalAndFutureWritersExcluded=false;
        final boolean atomicRecoveryEpochProved=false;
        final boolean hardwarePowerLossProved=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean pruningAuthorized=false;
        final boolean rolloverAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Review(
            Status state,SortedSet<String> changed,
            SortedSet<String> uncertain,boolean reviewed
        ){
            status=state;
            changedSources=Collections.unmodifiableSortedSet(
                new TreeSet<>(changed));
            uncertainSources=Collections.unmodifiableSortedSet(
                new TreeSet<>(uncertain));
            historicalAndCurrentObservedMatch=
                state==Status.MATCHED_UNTRUSTED_CANDIDATE_REVIEW_NO_AUTHORITY;
            candidateSignatureReviewed=reviewed;
        }
    }

    private static IOException veto(String reason){
        return new IOException(
            "G21.160 RECOVERY_ADMISSION_UNSAFE_NO_AUTHORITY "+
            reason+" root=false epoch=false cleanup=false grant=false");
    }

    private static String account(String candidate)throws IOException{
        if(candidate==null||
           !candidate.matches("[a-z0-9_-]{1,64}"))
            throw veto("INVALID_RECOVERY_ACCOUNT_CONTEXT");
        return candidate;
    }

    private static MailboxSevenPlaneHistoricalIdentityDeltaV1.Report
        delta(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current
        )throws IOException{
        return MailboxSevenPlaneHistoricalIdentityDeltaV1
            .compare(historical,current);
    }

    /**
     * A negative-only review: differences/unknown keys are surfaced
     * with no signature trust, no writer fence, and no permission.
     * Matched case additionally verifies the untrusted candidate
     * detached signature and separately caller-proposed signer pin.
     */
    static Review reviewCandidate(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        String namedAccount,byte[] candidateKeyDer,
        String candidateKeyFingerprint,byte[] detachedSignatureDer,
        String proposedPinAccount,String proposedPinFingerprint
    )throws IOException{
        String scoped=account(namedAccount);
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report observed=
            delta(historical,current);
        if(observed.status==
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                .CHANGED_OBSERVED_METADATA_NO_AUTHORITY)
            return new Review(Status.CHANGED_OBSERVED_EVIDENCE_NO_AUTHORITY,
                observed.changedSources,observed.uncertainSources,false);
        if(observed.status==
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                .UNCERTAIN_MISSING_FILE_KEY_NO_AUTHORITY)
            return new Review(Status.UNCERTAIN_OBSERVED_EVIDENCE_NO_AUTHORITY,
                observed.changedSources,observed.uncertainSources,false);
        if(observed.status!=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                .MATCHED_OBSERVED_METADATA_NO_AUTHORITY||
           !observed.changedSources.isEmpty()||
           !observed.uncertainSources.isEmpty())
            throw veto("UNKNOWN_HISTORICAL_DELTA_STATUS");
        MailboxProductionSignerTrustAdmissionV1.Review candidate=
            MailboxProductionSignerTrustAdmissionV1
                .reviewUntrustedCandidate(
                    historical,scoped,candidateKeyDer,
                    candidateKeyFingerprint,detachedSignatureDer,
                    proposedPinAccount,proposedPinFingerprint);
        if(candidate.status!=
            MailboxProductionSignerTrustAdmissionV1.Status
                .REVIEWED_UNTRUSTED_SIGNATURE_AND_PIN_NO_AUTHORITY||
           !candidate.detachedSignatureMathematicallyVerified||
           candidate.productionSignerRootEnrolled||
           candidate.independentlyAuthenticatedTrustAnchor||
           candidate.historicalSourceAuthenticated||
           candidate.atomicRecoveryEpochProved||
           candidate.cleanupAuthorized||candidate.grantAuthorized)
            throw veto("CANDIDATE_REVIEW_AUTHORITY_CONTRADICTION");
        return new Review(
            Status.MATCHED_UNTRUSTED_CANDIDATE_REVIEW_NO_AUTHORITY,
            observed.changedSources,observed.uncertainSources,true);
    }

    /**
     * A closed, non-mutating production admission check.
     * Mismatched/uncertain evidence is checked-vetoed BEFORE trust.
     * For matched evidence, G21.159 requires an enrolled production
     * signer root; there are ZERO. This method has NO success path
     * and never returns an authority-bearing token.
     */
    static void requireProductionRecoveryAdmission(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        String namedAccount,byte[] candidatePublicKeyDer
    )throws IOException{
        String scoped=account(namedAccount);
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report observed=
            delta(historical,current);
        if(observed.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .CHANGED_OBSERVED_METADATA_NO_AUTHORITY)
            throw veto("CHANGED_RECOVERY_EVIDENCE");
        if(observed.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .UNCERTAIN_MISSING_FILE_KEY_NO_AUTHORITY)
            throw veto("UNCERTAIN_RECOVERY_EVIDENCE");
        if(observed.status!=
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY||
           !observed.changedSources.isEmpty()||
           !observed.uncertainSources.isEmpty())
            throw veto("UNSAFE_RECOVERY_EVIDENCE");
        MailboxProductionSignerTrustAdmissionV1.requireProductionSignerRoot(
            scoped,candidatePublicKeyDer);
        // The above API always vetoes, and there is NO external trust
        // proof or atomic epoch even if future code changes its
        // implementation. Make the denial explicit as defense-depth.
        throw veto("PRODUCTION_RECOVERY_AUTHORITY_NOT_IMPLEMENTED");
    }

    private MailboxHistoricalRecoveryAdmissionV1(){}
}
