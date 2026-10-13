package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * G21.162: re-inspect the REAL account and negative marker files
 * through the original G21.71 cooperating publication lock before
 * any hypothetical cutover admission. No retained Decision is accepted
 * as an input to requireFreshProductionCutover().
 *
 * Historical source provenance and non-cooperating RAW/default/future
 * writer exclusion remain UNPROVED, and fresh inspection is NOT an
 * atomic crash-safe epoch. No existing live World/settlement caller
 * is modified or authorized by this read-only facade.
 */
final class MailboxFreshFileCutoverAdmissionV1 {
    enum Status {
        FRESH_FILE_FORENSIC_REVIEW_NO_AUTHORITY
    }

    static final class Review {
        final Status status=Status.FRESH_FILE_FORENSIC_REVIEW_NO_AUTHORITY;
        final String account;
        final FilePlayerRepository.RestartRecoveryEvidence.State state;
        final MailboxSettlementCutoverReadiness.Disposition disposition;
        final boolean originalObjectAndBytesObserved;
        final Set<MailboxSettlementCutoverReadiness.MissingProof> missingProofs;
        final boolean snapshotAdmitted=false;
        final boolean historicalEvidenceAuthenticated=false;
        final boolean independentlyEnrolledSignerRoot=false;
        final boolean rawDefaultOrFutureWritersFenced=false;
        final boolean atomicRecoveryEpochProved=false;
        final boolean durabilityConfirmed=false;
        final boolean transactionCommitted=false;
        final boolean cleanupAuthorized=false;
        final boolean rolloverAuthorized=false;
        final boolean pruningAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        private Review(
            FilePlayerRepository.RestartRecoveryEvidence original,
            MailboxSettlementCutoverReadiness.Decision cutover
        ){
            account=cutover.account;
            state=original.state;
            disposition=cutover.disposition;
            originalObjectAndBytesObserved=
                original.exactObjectAndBytesObserved;
            missingProofs=Collections.unmodifiableSet(
                EnumSet.copyOf(cutover.missingProofs));
        }
    }

    private static IOException veto(String reason){
        return new IOException(
            "G21.162 FRESH_CUTOVER_UNSAFE_NO_AUTHORITY "+reason+
            " provenance=false writerFence=false epoch=false"+
            " cleanup=false grant=false");
    }

    private static String account(String account)throws IOException{
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw veto("INVALID_FRESH_CUTOVER_ACCOUNT");
        return account;
    }

    private static MailboxSettlementCutoverReadiness.Decision
        requireNoAuthority(
            String account,
            FilePlayerRepository.RestartRecoveryEvidence original
        )throws IOException{
        final MailboxSettlementCutoverReadiness.Decision value=
            MailboxSettlementCutoverReadiness.assess(account,original);
        if(value.snapshotAdmitted||value.transactionCommitted||
           value.liveApplied||value.durabilityConfirmed||
           value.grantAuthorized||value.replayAuthorized||
           value.rollbackAuthorized||value.releaseAuthorized||
           value.clientAckAuthorized||
           value.independentlyEnrolledSignerRootObserved||
           value.historicalEvidenceCustodyAuthenticated||
           value.rawDefaultAndHistoricalWritersExcluded||
           value.futureWriterFenceProved||
           value.atomicRecoveryEpochProved||
           !value.missingProofs.containsAll(EnumSet.allOf(
               MailboxSettlementCutoverReadiness.MissingProof.class)))
            throw veto("CUTOVER_CONTRACT_CLAIMS_AUTHORITY");
        return value;
    }

    /**
     * A new call observes disk afresh via G21.71's read-only
     * locked NOFOLLOW checks. The resulting Review is NEVER an
     * authentication or approval credential, and can be stale
     * as soon as the cooperating publication lock is released.
     */
    static Review inspectFreshReadOnly(
        FilePlayerRepository repository,String requestedAccount
    )throws IOException{
        Objects.requireNonNull(repository,"file repository");
        String scoped=account(requestedAccount);
        FilePlayerRepository.RestartRecoveryEvidence original=
            repository.inspectRestartRecoveryReadOnly(scoped);
        return new Review(original,requireNoAuthority(scoped,original));
    }

    /**
     * No retained Decision is accepted. G21.163 keeps the original
     * G21.71 file inspection and deny-only disposition/root checks
     * under ONE G21.39 cooperating publication lock. A coherent
     * terminal is still UNCOMMITTED, raw writers remain unfenced and
     * no production recovery/settlement caller is newly connected.
     */
    static void requireFreshProductionCutover(
        FilePlayerRepository repository,String requestedAccount,
        byte[] candidatePublicKeyDer
    )throws IOException{
        Objects.requireNonNull(repository,"file repository");
        String scoped=account(requestedAccount);
        // G21.163: the actual locked G21.71 source inspection and
        // this deny-only cutover evaluation now share ONE cooperating
        // publication critical section. Do not reuse any prior Review.
        repository.requireFreshCutoverUnderPublicationLock(
            scoped,candidatePublicKeyDer);
        throw veto("LOCKED_CUTOVER_MUST_DENY");
    }

    /**
     * Called solely from FilePlayerRepository's G21.163 method WHILE
     * the exact account's original cooperating lock is still held.
     * Never exposes an approval result or unlock-to-continue token.
     */
    static void requireObservedCutoverInsidePublicationLock(
        String scoped,
        FilePlayerRepository.RestartRecoveryEvidence original,
        byte[] candidatePublicKeyDer
    )throws IOException{
        MailboxSettlementCutoverReadiness.Decision observed=
            requireNoAuthority(scoped,original);
        if(observed.disposition!=
             MailboxSettlementCutoverReadiness.Disposition
                 .COHERENT_TERMINAL_NOT_COMMITTED)
            throw veto("NO_POSITIVE_CUTOVER_STATE_"+observed.disposition);
        // Even a coherent G21.71 terminal is NEVER a durable commit.
        // The existing G21.161 guard rejects unknown signer roots.
        MailboxSettlementCutoverReadiness.requireProductionSignerBeforeCutover(
            observed,candidatePublicKeyDer);
        throw veto("ATOMIC_CUTOVER_AUTHORITY_NOT_IMPLEMENTED");
    }

    private MailboxFreshFileCutoverAdmissionV1(){}
}
