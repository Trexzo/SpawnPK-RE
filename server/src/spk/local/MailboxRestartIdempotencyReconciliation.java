package spk.local;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * G21.82: compare TWO detached G21.81 restart observations without
 * treating either fingerprint as a durable claim, transaction log,
 * ownership token, or exactly-once grant credential.
 *
 * A pairwise snapshot comparison is NON-ATOMIC across observations,
 * including if two reads happen to match. All outcomes are NO_GRANT.
 */
final class MailboxRestartIdempotencyReconciliation {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2182_PAIRWISE_RECONCILIATION_NO_GRANT";

    enum State {
        REPEATED_TERMINAL_IDENTITY_QUARANTINED,
        CONFLICTING_TERMINAL_IDENTITIES_QUARANTINED,
        PREPARED_TO_TERMINAL_UNCONFIRMED,
        TERMINAL_TO_PREPARED_REGRESSION_QUARANTINED,
        UNSETTLED_NO_TERMINAL_RECORD,
        NEGATIVE_MARKER_MANUAL_REVIEW,
        MISSING_ACCOUNT_QUARANTINE,
        INVALID_OR_CONFLICTING_QUARANTINE
    }

    static final class Decision {
        final String account;
        final State state;
        final boolean sameTerminalIdentity;
        final Set<MailboxSettlementCutoverReadiness.MissingProof>
            missingPositiveProofs;
        final boolean durabilityConfirmed=false;
        final boolean transactionCommitted=false;
        final boolean liveApplied=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        private Decision(String account,State state,boolean identical){
            this.account=account;
            this.state=state;
            sameTerminalIdentity=identical;
            missingPositiveProofs=Collections.unmodifiableSet(
                EnumSet.allOf(
                    MailboxSettlementCutoverReadiness.MissingProof.class
                )
            );
        }
    }

    static Decision compare(
        MailboxRestartTerminalIdentity.Result before,
        MailboxRestartTerminalIdentity.Result after
    ){
        Objects.requireNonNull(before,"previous restart observation");
        Objects.requireNonNull(after,"current restart observation");
        if(before.account==null||
           !before.account.matches("[a-z0-9_-]{1,64}")||
           !before.account.equals(after.account))
            throw new IllegalArgumentException(
                "G21.82 foreign or noncanonical account comparison");
        requireNoAuthority(before);
        requireNoAuthority(after);
        final String account=before.account;

        if(marked(before)||marked(after))
            return new Decision(account,
                State.NEGATIVE_MARKER_MANUAL_REVIEW,false);
        if(invalid(before)||invalid(after))
            return new Decision(account,
                State.INVALID_OR_CONFLICTING_QUARANTINE,false);
        if(missing(before)||missing(after))
            return new Decision(account,
                State.MISSING_ACCOUNT_QUARANTINE,false);

        boolean beforeTerminal=terminal(before);
        boolean afterTerminal=terminal(after);
        if(beforeTerminal&&afterTerminal){
            boolean same=identityEquals(before,after);
            return new Decision(account,
                same?State.REPEATED_TERMINAL_IDENTITY_QUARANTINED
                    :State.CONFLICTING_TERMINAL_IDENTITIES_QUARANTINED,
                same);
        }
        if(!beforeTerminal&&afterTerminal)
            return new Decision(account,
                State.PREPARED_TO_TERMINAL_UNCONFIRMED,false);
        if(beforeTerminal)
            return new Decision(account,
                State.TERMINAL_TO_PREPARED_REGRESSION_QUARANTINED,false);
        if(before.state==MailboxRestartTerminalIdentity.State
                .PREPARED_OR_LEGACY_NO_TERMINAL&&
           after.state==MailboxRestartTerminalIdentity.State
                .PREPARED_OR_LEGACY_NO_TERMINAL)
            return new Decision(account,
                State.UNSETTLED_NO_TERMINAL_RECORD,false);

        return new Decision(account,
            State.INVALID_OR_CONFLICTING_QUARANTINE,false);
    }

    private static boolean marked(
        MailboxRestartTerminalIdentity.Result value
    ){
        return value.state==MailboxRestartTerminalIdentity.State
            .NEGATIVE_MARKER_MANUAL_REVIEW;
    }

    private static boolean invalid(
        MailboxRestartTerminalIdentity.Result value
    ){
        return value.state==MailboxRestartTerminalIdentity.State
            .INVALID_OR_CONFLICTING_QUARANTINE;
    }

    private static boolean missing(
        MailboxRestartTerminalIdentity.Result value
    ){
        return value.state==MailboxRestartTerminalIdentity.State
            .MISSING_ACCOUNT_NO_IDENTITY;
    }

    private static boolean terminal(
        MailboxRestartTerminalIdentity.Result value
    ){
        return value.state==MailboxRestartTerminalIdentity.State
                .VALID_TERMINAL_QUARANTINED&&
            value.identityValidated&&
            value.identityFingerprint!=null&&
            value.identityFingerprint.matches("[0-9a-f]{64}")&&
            value.terminalSnapshotSha256!=null&&
            value.terminalSnapshotSha256.matches("[0-9a-f]{64}");
    }

    private static boolean identityEquals(
        MailboxRestartTerminalIdentity.Result first,
        MailboxRestartTerminalIdentity.Result second
    ){
        return Objects.equals(first.identityFingerprint,
                    second.identityFingerprint)&&
            Objects.equals(first.intentKey,second.intentKey)&&
            Objects.equals(first.messageId,second.messageId)&&
            Objects.equals(first.preparedSha256,
                second.preparedSha256)&&
            Objects.equals(first.hypotheticalSha256,
                second.hypotheticalSha256)&&
            Objects.equals(first.terminalSnapshotSha256,
                second.terminalSnapshotSha256);
    }

    private static void requireNoAuthority(
        MailboxRestartTerminalIdentity.Result value
    ){
        if(value.durabilityConfirmed||value.transactionCommitted||
           value.liveApplied||value.grantAuthorized||
           value.replayAuthorized||value.rollbackAuthorized||
           value.restartAdmissionAuthorized||
           value.releaseAuthorized||value.clientAckAuthorized||
           value.missingPositiveProofs==null||
           !value.missingPositiveProofs.containsAll(
               EnumSet.allOf(
                   MailboxSettlementCutoverReadiness.MissingProof.class
               )))
            throw new IllegalArgumentException(
                "G21.82 prior evidence claims unsupported authority");
    }

    private MailboxRestartIdempotencyReconciliation(){}
}
