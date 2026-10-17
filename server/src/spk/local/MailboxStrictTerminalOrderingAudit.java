package spk.local;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * G21.80: distinguish a strict terminal ACCOUNT FILE operation from
 * the unimplemented positive durable MAILBOX TRANSACTION commit.
 *
 * Pure, ephemeral classification only. A Receipt is never a portable
 * credential. Inspecting an account cannot make it safe to replay.
 */
final class MailboxStrictTerminalOrderingAudit {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2180_STRICT_ORDERING_AUDIT_NO_GRANT";

    enum State {
        PREPARED_BEFORE_REPLACE_NO_GRANT,
        NEGATIVE_MARKER_MANUAL_REVIEW_NO_GRANT,
        TERMINAL_VISIBLE_NO_RECEIPT_QUARANTINE,
        STRICT_FILE_OPERATION_ONLY_NO_COMMIT,
        CONFLICTING_RECEIPT_QUARANTINE,
        INVALID_OR_MISSING_QUARANTINE
    }

    static final class Result {
        final State state;
        final String account;
        final boolean strictOperationReceiptMatched;
        final Set<MailboxSettlementCutoverReadiness.MissingProof>
            missingPositiveProofs;
        final boolean transactionCommitted=false;
        final boolean durabilityConfirmed=false;
        final boolean liveApplied=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        Result(String account,State state,boolean matched){
            this.account=account;
            this.state=state;
            this.strictOperationReceiptMatched=matched;
            missingPositiveProofs=Collections.unmodifiableSet(
                EnumSet.allOf(
                    MailboxSettlementCutoverReadiness.MissingProof.class
                )
            );
        }
    }

    /**
     * The caller passes a *fresh* G21.71 forensic observation. Even that
     * freshness is point-in-time and MUST NOT be turned into a grant.
     * A receipt is accepted for comparison only when it exactly names the
     * intended account path and terminal snapshot. A process cannot
     * reconstruct or trust the receipt from account-file bytes.
     */
    static Result inspect(
        MailboxSettlementPostimagePlanner.Proposal proposal,
        PlayerSnapshot terminal,
        Path accountPath,
        StrictDurablePlayerSnapshotWriter.Receipt receipt,
        FilePlayerRepository.RestartRecoveryEvidence observed
    ){
        Objects.requireNonNull(proposal,"proposal");
        Objects.requireNonNull(terminal,"terminal");
        Objects.requireNonNull(accountPath,"account path");
        Objects.requireNonNull(observed,"restart observation");

        if(!proposal.account.equals(observed.account)||
           !proposal.account.equals(terminal.username())||
           !MailboxAtomicTerminalSnapshot.compose(proposal)
               .values().equals(terminal.values()))
            throw new IllegalArgumentException(
                "G21.80 terminal/account intent identity mismatch");

        MailboxSettlementCutoverReadiness.assess(
            proposal.account,observed
        );

        final Path pinned=accountPath.toAbsolutePath().normalize();
        if(!pinned.isAbsolute()||pinned.getParent()==null)
            throw new IllegalArgumentException(
                "G21.80 invalid expected account path");

        boolean matched=receipt!=null;
        if(matched&&(
           !proposal.account.equals(receipt.account)||
           !pinned.equals(receipt.file)||
           !StrictDurablePlayerSnapshotWriter.AUTHORITY.equals(
               receipt.authority)||
           !receipt.matchesSnapshot(terminal)))
            throw new IllegalArgumentException(
                "G21.80 foreign or stale strict receipt NO_GRANT");

        State state;
        switch(observed.state){
            case DURABLE_REVIEW_MARKER:
            case UNCERTAIN_COMMIT_MARKER:
            case STRANDED_WRITE_INTENT_MARKER:
                state=State.NEGATIVE_MARKER_MANUAL_REVIEW_NO_GRANT;
                break;
            case COHERENT_TERMINAL_QUARANTINE:
                state=matched
                    ?State.STRICT_FILE_OPERATION_ONLY_NO_COMMIT
                    :State.TERMINAL_VISIBLE_NO_RECEIPT_QUARANTINE;
                break;
            case PREPARED_UNCLAIMED_NO_REPLAY:
                state=matched
                    ?State.CONFLICTING_RECEIPT_QUARANTINE
                    :State.PREPARED_BEFORE_REPLACE_NO_GRANT;
                break;
            default:
                state=State.INVALID_OR_MISSING_QUARANTINE;
        }

        return new Result(proposal.account,state,matched);
    }

    private MailboxStrictTerminalOrderingAudit(){}
}
