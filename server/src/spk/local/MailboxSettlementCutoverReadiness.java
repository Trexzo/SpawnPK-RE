package spk.local;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * G21.79: explain the exact missing positive-settlement proofs after
 * G21.71-78 read-only filesystem observations.
 *
 * This is a non-admitting, non-granting CUTOVER CONTRACT only. Neither
 * the coherent terminal snapshot nor an in-memory World freshness check
 * makes a durable, exactly-once COMMIT or a restart replay credential.
 */
final class MailboxSettlementCutoverReadiness {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2179_CUTOVER_CONTRACT_NO_GRANT";

    enum Disposition {
        PREPARED_ONLY_NO_SETTLEMENT,
        COHERENT_TERMINAL_NOT_COMMITTED,
        REVIEW_MARKER_MANUAL_HOLD,
        MISSING_ACCOUNT_QUARANTINE,
        INVALID_ACCOUNT_QUARANTINE,
        LEGACY_NOT_MAILBOX_TRANSACTION
    }

    enum MissingProof {
        DURABLE_POSITIVE_COMMIT_AND_ORDERING,
        IDEMPOTENT_CROSS_RESTART_RECONCILIATION,
        ATOMIC_WORLD_OWNED_LIVE_APPLY,
        POSTCOMMIT_RESERVATION_RELEASE_AND_ACK
    }

    static final class Decision {
        final String account;
        final FilePlayerRepository.RestartRecoveryEvidence.State observed;
        final Disposition disposition;
        final Set<MissingProof> missingProofs;
        final boolean snapshotAdmitted=false;
        final boolean durabilityConfirmed=false;
        final boolean transactionCommitted=false;
        final boolean liveApplied=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        private Decision(
            String account,
            FilePlayerRepository.RestartRecoveryEvidence.State observed,
            Disposition disposition
        ){
            this.account=account;
            this.observed=observed;
            this.disposition=disposition;
            this.missingProofs=Collections.unmodifiableSet(
                EnumSet.allOf(MissingProof.class)
            );
        }
    }

    /**
     * Takes only an EXISTING G21.71 observation. It does not read disk,
     * mutate a WorldPlayer, publish receipts, or alter restart admission.
     * A read-only observation may be stale immediately after return.
     */
    static Decision assess(
        String expectedAccount,
        FilePlayerRepository.RestartRecoveryEvidence observed
    ){
        Objects.requireNonNull(observed,"recovery evidence");
        if(expectedAccount==null||
           !expectedAccount.matches("[a-z0-9_-]{1,64}")||
           !expectedAccount.equals(observed.account))
            throw new IllegalArgumentException(
                "G21.79 foreign or noncanonical recovery account");
        if(observed.restartAdmissionAuthorized||
           observed.durabilityConfirmed||
           observed.transactionCommitted||
           observed.liveApplied||
           observed.grantAuthorized||
           observed.replayAuthorized||
           observed.rollbackAuthorized||
           observed.releaseAuthorized||
           observed.clientAckAuthorized)
            throw new IllegalArgumentException(
                "G21.79 positive authority must not enter cutover assessment");

        final Disposition disposition;
        switch(observed.state){
            case PREPARED_UNCLAIMED_NO_REPLAY:
                disposition=Disposition.PREPARED_ONLY_NO_SETTLEMENT;
                break;
            case COHERENT_TERMINAL_QUARANTINE:
                disposition=Disposition.COHERENT_TERMINAL_NOT_COMMITTED;
                break;
            case DURABLE_REVIEW_MARKER:
            case UNCERTAIN_COMMIT_MARKER:
            case STRANDED_WRITE_INTENT_MARKER:
                disposition=Disposition.REVIEW_MARKER_MANUAL_HOLD;
                break;
            case MISSING_ACCOUNT_NO_REPLAY:
                disposition=Disposition.MISSING_ACCOUNT_QUARANTINE;
                break;
            case INVALID_TERMINAL_QUARANTINE:
            case INVALID_PREPARED_QUARANTINE:
                disposition=Disposition.INVALID_ACCOUNT_QUARANTINE;
                break;
            case LEGACY_NO_JOURNAL_NON_ADMITTING:
                disposition=Disposition.LEGACY_NOT_MAILBOX_TRANSACTION;
                break;
            default:
                throw new IllegalStateException(
                    "G21.79 unmapped recovery state");
        }
        return new Decision(expectedAccount,observed.state,disposition);
    }

    private MailboxSettlementCutoverReadiness(){}
}
