package spk.local;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * G21.81: detached recovery identity reconstructed from a *loaded*
 * G21.64 terminal account snapshot with a separate G21.71 observation.
 *
 * Neither input is an atomic combined read. This is DIAGNOSTICS ONLY:
 * an identity fingerprint is not a signed COMMIT, a receipt, an
 * admission decision, or cross-restart replay authority.
 */
final class MailboxRestartTerminalIdentity {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2181_TERMINAL_IDENTITY_NO_GRANT";
    private static final String PREFIX=
        "extension."+MailboxAtomicTerminalSnapshot.NAMESPACE+".";

    enum State {
        MISSING_ACCOUNT_NO_IDENTITY,
        PREPARED_OR_LEGACY_NO_TERMINAL,
        VALID_TERMINAL_QUARANTINED,
        NEGATIVE_MARKER_MANUAL_REVIEW,
        INVALID_OR_CONFLICTING_QUARANTINE
    }

    static final class Result {
        final State state;
        final String account;
        final String messageId;
        final String intentKey;
        final String preparedSha256;
        final String hypotheticalSha256;
        final String terminalSnapshotSha256;
        final String identityFingerprint;
        final Set<MailboxSettlementCutoverReadiness.MissingProof>
            missingPositiveProofs;
        final boolean identityValidated;
        final boolean durabilityConfirmed=false;
        final boolean transactionCommitted=false;
        final boolean liveApplied=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        private Result(
            State state,String account,PlayerSnapshot terminal
        ){
            this.state=state;
            this.account=account;
            missingPositiveProofs=Collections.unmodifiableSet(
                EnumSet.allOf(
                    MailboxSettlementCutoverReadiness.MissingProof.class
                )
            );
            identityValidated=terminal!=null;
            if(terminal==null){
                messageId=null;
                intentKey=null;
                preparedSha256=null;
                hypotheticalSha256=null;
                terminalSnapshotSha256=null;
                identityFingerprint=null;
            }else{
                messageId=terminal.value(PREFIX+"message");
                intentKey=terminal.value(PREFIX+"key");
                preparedSha256=terminal.value(PREFIX+"before");
                hypotheticalSha256=terminal.value(PREFIX+"after");
                terminalSnapshotSha256=StrictDurablePlayerSnapshotWriter
                    .canonicalSnapshotSha256(terminal);
                identityFingerprint=sha256(
                    "SPK.G2181.TERMINAL.IDENTITY.V1|"+
                    account+"|"+messageId+"|"+intentKey+"|"+
                    preparedSha256+"|"+hypotheticalSha256+"|"+
                    terminal.value(PREFIX+"checksum")+"|"+
                    terminalSnapshotSha256
                );
            }
        }
    }

    /**
     * Snapshot comes from a NON-ADMITTING account read; evidence comes
     * from G21.71's read-only recovery inspection. They are deliberately
     * NOT a joint transaction, and must never enable live mutation.
     *
     * Negative marker precedence wins even when terminal identity is
     * structurally coherent. Identity is provided solely for human
     * diagnostics and duplicate-identification research.
     */
    static Result inspect(
        String expectedAccount,PlayerSnapshot disk,
        FilePlayerRepository.RestartRecoveryEvidence observed
    ){
        Objects.requireNonNull(observed,"recovery evidence");
        if(expectedAccount==null||
           !expectedAccount.matches("[a-z0-9_-]{1,64}")||
           !expectedAccount.equals(observed.account)||
           (disk!=null&&!expectedAccount.equals(disk.username())))
            throw new IllegalArgumentException(
                "G21.81 foreign or noncanonical recovery identity");
        MailboxSettlementCutoverReadiness.assess(
            expectedAccount,observed
        );

        final FilePlayerRepository.RestartRecoveryEvidence.State state=
            observed.state;
        if(state==FilePlayerRepository.RestartRecoveryEvidence.State
                .MISSING_ACCOUNT_NO_REPLAY)
            return new Result(
                disk==null?State.MISSING_ACCOUNT_NO_IDENTITY
                    :State.INVALID_OR_CONFLICTING_QUARANTINE,
                expectedAccount,null
            );
        if(disk==null)
            return new Result(
                State.INVALID_OR_CONFLICTING_QUARANTINE,
                expectedAccount,null
            );

        boolean coherent=MailboxAtomicTerminalSnapshot.inspect(disk)
            .state==MailboxAtomicTerminalSnapshot.State
                .COHERENT_TERMINAL_NO_GRANT;
        boolean marked=
            state==FilePlayerRepository.RestartRecoveryEvidence.State
                .DURABLE_REVIEW_MARKER||
            state==FilePlayerRepository.RestartRecoveryEvidence.State
                .UNCERTAIN_COMMIT_MARKER||
            state==FilePlayerRepository.RestartRecoveryEvidence.State
                .STRANDED_WRITE_INTENT_MARKER;

        if(marked)
            return new Result(
                State.NEGATIVE_MARKER_MANUAL_REVIEW,
                expectedAccount,coherent?disk:null
            );
        if(state==FilePlayerRepository.RestartRecoveryEvidence.State
                .COHERENT_TERMINAL_QUARANTINE&&coherent)
            return new Result(
                State.VALID_TERMINAL_QUARANTINED,
                expectedAccount,disk
            );
        if(!coherent&&(
           state==FilePlayerRepository.RestartRecoveryEvidence.State
                .PREPARED_UNCLAIMED_NO_REPLAY||
           state==FilePlayerRepository.RestartRecoveryEvidence.State
                .LEGACY_NO_JOURNAL_NON_ADMITTING))
            return new Result(
                State.PREPARED_OR_LEGACY_NO_TERMINAL,
                expectedAccount,null
            );
        return new Result(
            State.INVALID_OR_CONFLICTING_QUARANTINE,
            expectedAccount,null
        );
    }

    private static String sha256(String value){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            char[] out=new char[bytes.length*2];
            char[] hex="0123456789abcdef".toCharArray();
            for(int i=0;i<bytes.length;i++){
                out[2*i]=hex[(bytes[i]&255)>>>4];
                out[2*i+1]=hex[bytes[i]&15];
            }
            return new String(out);
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException(
                "G21.81 SHA-256 unavailable",impossible
            );
        }
    }

    private MailboxRestartTerminalIdentity(){}
}
