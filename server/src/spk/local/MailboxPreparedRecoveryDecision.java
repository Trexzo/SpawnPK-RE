package spk.local;

import java.util.Objects;

/**
 * G21.29: a read-only, fail-closed crash-recovery decision boundary.
 *
 * An observed postimage is NOT evidence of successful strict commit. The
 * G21.23 Receipt contains account, path and writer authority only; it does
 * not bind the canonical snapshot, Mailbox intent key or owner generation.
 * Even a genuine matching-account Receipt cannot authorize live settlement.
 *
 * No branch of this classifier grants inventory, marks Mailbox CLAIMED,
 * replays a transaction, releases a reservation, or sends client packets.
 */
final class MailboxPreparedRecoveryDecision {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2129_RECOVERY_DECISION_NO_GRANT";

    enum State {
        EXACT_PREPARED_UNCLAIMED,
        HYPOTHETICAL_VISIBLE_NO_RECEIPT,
        HYPOTHETICAL_VISIBLE_UNBOUND_RECEIPT,
        RECEIPT_IDENTITY_MISMATCH,
        MISSING_ACCOUNT_QUARANTINE,
        DIVERGENT_ACCOUNT_QUARANTINE,
        STALE_OR_UNBOUND_RESERVATION,
        UNTRUSTED_OBSERVATION,
        RESTART_REQUIRES_MANUAL_RECONCILIATION
    }

    static final class Decision {
        final State state;
        final String account;
        final String intentKey;
        final long generation;
        final String authority=AUTHORITY;
        final boolean durabilityConfirmed=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean safeReleaseAuthorized=false;
        final boolean clientSuccessAuthorized=false;

        private Decision(
            State state,String account,String key,long generation
        ){
            this.state=Objects.requireNonNull(state,"state");
            this.account=account;
            this.intentKey=key;
            this.generation=generation;
        }

        boolean needsManualReconciliation(){
            return state!=State.EXACT_PREPARED_UNCLAIMED;
        }
    }

    /**
     * A restart cannot reconstruct G21.27's in-memory exclusive token.
     * Absence of a token always quarantines; do not redo/undo a prepared
     * transfer merely because a file or idempotency key survives.
     */
    static Decision afterRestart(
        MailboxSettlementPostimagePlanner.Proposal proposal
    ){
        Objects.requireNonNull(proposal,"proposal");
        return new Decision(
            State.RESTART_REQUIRES_MANUAL_RECONCILIATION,
            proposal.account,proposal.idempotencyKey,
            proposal.ownerGeneration
        );
    }

    /**
     * Requires a previously completed G21.28 same-FIFO disk observation.
     * An observed EXACT_PREPARED record is informational only: the caller
     * must use the G21.28 independently guarded cancellation path, not
     * this Decision, to release an unchanged reservation.
     */
    static Decision assess(
        WorldPlayerPersistence.PreparedAccountReservation token,
        MailboxSettlementPostimagePlanner.Proposal proposal,
        WorldPlayerPersistence.PreparedDrainObservation disk,
        StrictDurablePlayerSnapshotWriter.Receipt receipt
    ){
        Objects.requireNonNull(proposal,"proposal");
        if(token==null)
            return afterRestart(proposal);
        if(disk==null)
            return decision(
                State.UNTRUSTED_OBSERVATION,proposal
            );

        // Snapshot and envelope authority are rechecked. Do not
        // classify a retired generation, recycled message identity or
        // changed inventory against an old proposed postimage.
        synchronized(token.owner.mutationLock()){
            if(!token.isActive()||
               !token.owner.accepts(token.generation)||
               token.generation!=proposal.ownerGeneration||
               !token.account.equals(proposal.account)||
               !token.intentKey.equals(proposal.idempotencyKey)||
               !token.exactPreimage.values().equals(
                   proposal.preparedPreimage.values()))
                return decision(
                    State.STALE_OR_UNBOUND_RESERVATION,proposal
                );

            MailboxRewardDeliveryService.Snapshot current=
                token.owner.mailbox().get(proposal.messageId);
            if(current==null||current.message!=token.envelopeIdentity)
                return decision(
                    State.STALE_OR_UNBOUND_RESERVATION,proposal
                );
            try{
                MailboxSettlementPostimagePlanner.Proposal fresh=
                    MailboxSettlementPostimagePlanner.plan(
                        token.owner,token.generation,current
                    );
                if(!fresh.idempotencyKey.equals(proposal.idempotencyKey)||
                   !fresh.preparedPreimage.values().equals(
                       proposal.preparedPreimage.values())||
                   !fresh.hypotheticalPostimage.values().equals(
                       proposal.hypotheticalPostimage.values()))
                    return decision(
                        State.STALE_OR_UNBOUND_RESERVATION,proposal
                    );
            }catch(IllegalArgumentException|
                    IllegalStateException stale){
                return decision(
                    State.STALE_OR_UNBOUND_RESERVATION,proposal
                );
            }
        }

        // The observation is not a persisted receipt, nor can a caller
        // claim it was one by setting a flag in a forged transport object.
        if(!disk.workerQuiescentAtRead||
           disk.durabilityReceipt||disk.grantAuthorized||
           !proposal.account.equals(disk.account)||
           !proposal.idempotencyKey.equals(disk.intentKey)||
           proposal.ownerGeneration!=disk.generation)
            return decision(State.UNTRUSTED_OBSERVATION,proposal);

        // G21.23 Receipt is genuine only as a file-operation result.
        // It has NO snapshot fingerprint, claim key or generation, so
        // account/path similarity cannot upgrade it into grant proof.
        if(receipt!=null&&
           (!proposal.account.equals(receipt.account)||
            !StrictDurablePlayerSnapshotWriter.AUTHORITY.equals(
                receipt.authority)))
            return decision(State.RECEIPT_IDENTITY_MISMATCH,proposal);

        switch(disk.state){
            case EXACT_PREPARED:
                return decision(State.EXACT_PREPARED_UNCLAIMED,proposal);
            case EXACT_HYPOTHETICAL:
                return decision(
                    receipt==null?
                        State.HYPOTHETICAL_VISIBLE_NO_RECEIPT:
                        State.HYPOTHETICAL_VISIBLE_UNBOUND_RECEIPT,
                    proposal
                );
            case MISSING_ACCOUNT:
                return decision(
                    State.MISSING_ACCOUNT_QUARANTINE,proposal
                );
            case DIVERGENT:
            default:
                return decision(
                    State.DIVERGENT_ACCOUNT_QUARANTINE,proposal
                );
        }
    }

    private static Decision decision(
        State state,MailboxSettlementPostimagePlanner.Proposal proposal
    ){
        return new Decision(
            state,proposal.account,proposal.idempotencyKey,
            proposal.ownerGeneration
        );
    }

    private MailboxPreparedRecoveryDecision(){}
}
