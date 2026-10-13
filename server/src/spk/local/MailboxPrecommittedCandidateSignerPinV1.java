package spk.local;

import java.io.IOException;

/**
 * G21.158: negative-only separation between a candidate-signature
 * public-key fingerprint and an EXPECTED precommitted fingerprint.
 *
 * The precommitment is supplied separately, but the caller may still
 * control both values. NO trusted keystore, enrolled signer, custody,
 * original filesystem provenance, atomic epoch or reward authorization
 * is established here. This never writes, reads disk or acquires locks.
 */
final class MailboxPrecommittedCandidateSignerPinV1 {
    enum Status {
        MATCHED_UNTRUSTED_PRECOMMITMENT_NO_AUTHORITY
    }

    static final class Report {
        final Status status=
            Status.MATCHED_UNTRUSTED_PRECOMMITMENT_NO_AUTHORITY;
        final boolean candidatePublicKeyMatchedPrecommitment=true;
        final boolean detachedCandidateSignatureMathematicallyVerified=true;
        final boolean precommitmentIndependentlyProvisioned=false;
        final boolean externalSignerTrustAuthenticated=false;
        final boolean historicalEvidenceSourceAuthenticated=false;
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
        private Report(){}
    }

    private static IOException veto(String reason){
        return new IOException(
            "G21.158 PRECOMMITTED_SIGNER_UNSAFE_NO_AUTHORITY "+
            reason+" trust=false epoch=false cleanup=false grant=false");
    }

    /**
     * The expected fingerprint/account is a DISTINCT input, not the
     * digest computed from the supplied candidate public key.
     * This enables later real out-of-band precommitment provisioning
     * but DOES NOT prove that such provisioning has occurred.
     */
    static Report verifyAgainstSeparatePrecommitment(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        String account,
        byte[] candidatePublicKeyDer,String candidateFingerprint,
        byte[] detachedSignatureDer,
        String precommittedAccount,String expectedPrecommittedFingerprint
    )throws IOException{
        if(account==null||!account.matches("[a-z0-9_-]{1,64}")||
           precommittedAccount==null||
           !precommittedAccount.matches("[a-z0-9_-]{1,64}"))
            throw veto("INVALID_ACCOUNT_SCOPE");
        if(!account.equals(precommittedAccount))
            throw veto("PRECOMMITMENT_ACCOUNT_SCOPE_MISMATCH");
        if(expectedPrecommittedFingerprint==null||
           !expectedPrecommittedFingerprint.matches("[0-9a-f]{64}"))
            throw veto("MISSING_OR_MALFORMED_PRECOMMITTED_FINGERPRINT");
        if(candidateFingerprint==null||
           !candidateFingerprint.matches("[0-9a-f]{64}"))
            throw veto("MALFORMED_CANDIDATE_FINGERPRINT");
        if(!expectedPrecommittedFingerprint.equals(candidateFingerprint))
            throw veto("CANDIDATE_KEY_NOT_PRECOMMITTED");
        // G21.157 independently recomputes the candidate-key digest,
        // revalidates the complete retained historical snapshot, and
        // checks the detached P256 ECDSA math. Neither result
        // authenticates the caller's untrusted precommitment source.
        MailboxDetachedHistoricalCandidateSignatureV1.Report candidate=
            MailboxDetachedHistoricalCandidateSignatureV1.verifyCandidate(
                historical,account,candidatePublicKeyDer,
                candidateFingerprint,detachedSignatureDer);
        if(candidate.status!=
              MailboxDetachedHistoricalCandidateSignatureV1.Status
                .VALID_SIGNATURE_UNTRUSTED_KEY_NO_AUTHORITY||
           !candidate.candidateSignatureMathematicallyVerified||
           candidate.independentTrustAnchorAuthenticated||
           candidate.historicalSourceAuthenticated||
           candidate.cleanupAuthorized||candidate.grantAuthorized)
            throw veto("UNSAFE_CANDIDATE_VERIFIER_RESULT");
        return new Report();
    }

    private MailboxPrecommittedCandidateSignerPinV1(){}
}
