package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * G21.159: NON-ENROLLABLE production root admission. There is
 * currently no operator-authenticated signer key provisioned in
 * this repository. Accordingly, every promotion attempt is vetoed.
 *
 * Read-only candidate review can still check G21.158 mathematical
 * signature and user-supplied precommitment, but can NEVER return
 * an authenticated provenance or cleanup/reward authority token.
 *
 * No dynamic registration hook, filesystem/config lookup, mutable
 * registry or caller-controlled trusted-root argument is provided.
 * Future independent root enrollment requires a separate reviewed
 * custody and key-rotation design; nothing here provisions trust.
 */
final class MailboxProductionSignerTrustAdmissionV1 {
    // Fail-closed by construction: no production signer roots have
    // been independently enrolled. Do NOT seed a test key or copy a
    // user's supplied fingerprint into this immutable map.
    private static final Map<String,String> APPROVED_ACCOUNT_ROOTS=
        Collections.emptyMap();

    enum Status {
        REVIEWED_UNTRUSTED_SIGNATURE_AND_PIN_NO_AUTHORITY
    }

    static final class Review {
        final Status status=
            Status.REVIEWED_UNTRUSTED_SIGNATURE_AND_PIN_NO_AUTHORITY;
        final boolean detachedSignatureMathematicallyVerified=true;
        final boolean callerSuppliedExpectedPinMatched=true;
        final boolean productionSignerRootEnrolled=false;
        final boolean independentlyAuthenticatedTrustAnchor=false;
        final boolean historicalSourceAuthenticated=false;
        final boolean originalFileKeyIdentityAuthenticated=false;
        final boolean historicalWriterCensusProved=false;
        final boolean rawOrDefaultWriterExclusionProved=false;
        final boolean futureWriterFenceProved=false;
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
        private Review(){}
    }

    private static IOException veto(String reason){
        return new IOException(
            "G21.159 PRODUCTION_SIGNER_TRUST_UNSAFE_NO_AUTHORITY "+
            reason+" root=false epoch=false cleanup=false grant=false");
    }

    private static String checked(String account)throws IOException{
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw veto("INVALID_PRODUCTION_ACCOUNT_SCOPE");
        return account;
    }

    static int independentlyEnrolledProductionRoots(){
        return APPROVED_ACCOUNT_ROOTS.size();
    }

    /**
     * Explicitly non-privileged candidate review. The caller still
     * controls BOTH the candidate pin and expected precommitment;
     * mathematical success never proves their source or custody.
     */
    static Review reviewUntrustedCandidate(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        String account,
        byte[] candidatePublicKeyDer,String candidateFingerprint,
        byte[] detachedSignatureDer,
        String proposedPinAccount,String proposedExpectedFingerprint
    )throws IOException{
        checked(account);
        MailboxPrecommittedCandidateSignerPinV1.Report candidate=
            MailboxPrecommittedCandidateSignerPinV1
                .verifyAgainstSeparatePrecommitment(
                    historical,account,candidatePublicKeyDer,
                    candidateFingerprint,detachedSignatureDer,
                    proposedPinAccount,proposedExpectedFingerprint);
        if(candidate.status!=
             MailboxPrecommittedCandidateSignerPinV1.Status
                .MATCHED_UNTRUSTED_PRECOMMITMENT_NO_AUTHORITY||
           !candidate.candidatePublicKeyMatchedPrecommitment||
           !candidate.detachedCandidateSignatureMathematicallyVerified||
           candidate.precommitmentIndependentlyProvisioned||
           candidate.externalSignerTrustAuthenticated||
           candidate.historicalEvidenceSourceAuthenticated||
           candidate.cleanupAuthorized||candidate.grantAuthorized)
            throw veto("UNSAFE_UNTRUSTED_CANDIDATE_REVIEW");
        return new Review();
    }

    /**
     * Production trust admission intentionally accepts NO proposed
     * expected pin or alleged operator root from the request. Only a
     * truly independent provisioned registry could supply one, and
     * this build has no such registry. Never interpret a reviewed
     * candidate or existing test fixture as root admission.
     */
    static void requireProductionSignerRoot(
        String account,byte[] candidatePublicKeyDer
    )throws IOException{
        String scoped=checked(account);
        if(candidatePublicKeyDer==null||
           candidatePublicKeyDer.length<65||
           candidatePublicKeyDer.length>256)
            throw veto("INVALID_PRODUCTION_SIGNER_CANDIDATE");
        String expected=APPROVED_ACCOUNT_ROOTS.get(scoped);
        if(expected==null)
            throw veto("NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
        // Future source changes must NOT turn this lookup into
        // authority. Root enrolment/custody/rotation and epoch proof
        // are still absent. Even a mistakenly populated map MUST
        // remain fail-closed at this boundary.
        throw veto("PRODUCTION_ROOT_AUTHORITY_NOT_IMPLEMENTED");
    }

    private MailboxProductionSignerTrustAdmissionV1(){}
}
