package spk.local;

import java.util.Objects;

/**
 * G21.30: correlate a successful G21.23 strict file-operation receipt
 * with the immutable G21.25 hypothetical account bytes after the G21.28
 * reserved FIFO observation. This evidence is explicitly NON-GRANTING.
 *
 * The strict writer is still opt-in and not authorized to bypass the
 * WorldPlayerPersistence FIFO for live item settlement. Receipts are
 * in-memory and not independently restart-reconstructible.
 */
final class MailboxBoundStrictReceiptEvidence {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2130_SNAPSHOT_BOUND_FILE_OPERATION_NO_GRANT";

    enum State {
        BOUND_HYPOTHETICAL_FILE_OPERATION,
        EXACT_PREPARED_NO_GRANT,
        HYPOTHETICAL_MISSING_RECEIPT,
        HYPOTHETICAL_RECEIPT_FOR_DIFFERENT_SNAPSHOT,
        REJECTED_OR_UNTRUSTED_OBSERVATION,
        RESTART_QUARANTINE
    }

    static final class Evidence {
        final State state;
        final String account;
        final String intentKey;
        final String authority=AUTHORITY;
        final boolean strictOperationSnapshotBound;
        final boolean restartRecoveryAuthorized=false;
        final boolean liveGrantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean liveReconciliationAuthorized=false;
        final boolean clientSuccessAuthorized=false;

        private Evidence(
            State state,MailboxSettlementPostimagePlanner.Proposal proposal,
            boolean bound
        ){
            this.state=Objects.requireNonNull(state,"state");
            this.account=proposal.account;
            this.intentKey=proposal.idempotencyKey;
            this.strictOperationSnapshotBound=bound;
        }
    }

    static Evidence inspect(
        WorldPlayerPersistence.PreparedAccountReservation token,
        MailboxSettlementPostimagePlanner.Proposal proposal,
        WorldPlayerPersistence.PreparedDrainObservation disk,
        StrictDurablePlayerSnapshotWriter.Receipt receipt
    ){
        Objects.requireNonNull(proposal,"proposal");
        if(token==null)
            return evidence(State.RESTART_QUARANTINE,proposal,false);

        MailboxPreparedRecoveryDecision.Decision earlier=
            MailboxPreparedRecoveryDecision.assess(
                token,proposal,disk,receipt
            );

        if(earlier.state==
                MailboxPreparedRecoveryDecision.State
                    .EXACT_PREPARED_UNCLAIMED)
            return evidence(State.EXACT_PREPARED_NO_GRANT,proposal,false);

        if(earlier.state==
                MailboxPreparedRecoveryDecision.State
                    .HYPOTHETICAL_VISIBLE_NO_RECEIPT)
            return evidence(
                State.HYPOTHETICAL_MISSING_RECEIPT,proposal,false
            );

        if(earlier.state==
                MailboxPreparedRecoveryDecision.State
                    .HYPOTHETICAL_VISIBLE_UNBOUND_RECEIPT){
            if(receipt!=null&&
               receipt.matchesSnapshot(proposal.hypotheticalPostimage))
                return evidence(
                    State.BOUND_HYPOTHETICAL_FILE_OPERATION,
                    proposal,true
                );
            return evidence(
                State.HYPOTHETICAL_RECEIPT_FOR_DIFFERENT_SNAPSHOT,
                proposal,false
            );
        }

        return evidence(
            State.REJECTED_OR_UNTRUSTED_OBSERVATION,proposal,false
        );
    }

    private static Evidence evidence(
        State state,MailboxSettlementPostimagePlanner.Proposal proposal,
        boolean bound
    ){
        return new Evidence(state,proposal,bound);
    }

    private MailboxBoundStrictReceiptEvidence(){}
}
