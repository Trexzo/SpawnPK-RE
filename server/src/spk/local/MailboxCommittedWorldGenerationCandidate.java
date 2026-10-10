package spk.local;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * G21.90: opt-in, read-only ownership proof for a separately recovered
 * COMMIT-backed terminal snapshot. This MUST NOT be used as session
 * admission, application, replay or settlement authority.
 *
 * No filesystem I/O, mutation, packets, or reward operations execute
 * under World lifecycle / player mutation ownership.
 */
final class MailboxCommittedWorldGenerationCandidate {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2190_WORLD_GENERATION_CANDIDATE_NO_ADMISSION";

    enum Decision {
        CANDIDATE_ONLY_NO_ADMISSION,
        REJECT_NOT_OWNED,
        REJECT_ACCOUNT_MISMATCH,
        REJECT_UNTRUSTED_RECOVERY,
        REJECT_NONFRESH_RECEIVER
    }

    static Decision inspect(
        World world,WorldPlayer receiver,long expectedGeneration,
        MailboxCommittedDetachedRestartRecovery.Result recovered
    )throws Exception{
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(receiver,"receiver");
        Objects.requireNonNull(recovered,"recovered");

        AtomicReference<Decision> decision=new AtomicReference<>(
            Decision.REJECT_NOT_OWNED
        );
        boolean owned=world.withOpenPlayerMutationOwnershipIfCurrent(
            receiver,expectedGeneration,()->{
                if(!recovered.account.equals(receiver.username())){
                    decision.set(Decision.REJECT_ACCOUNT_MISMATCH);
                    return;
                }

                // Immutable metadata from a prior G21.89 independently
                // verified disk recovery; checking this does NOT revalidate
                // any subsequent disk change or authorize live restoration.
                if(!recovered.claimedBeforeAnyReplay||
                   !recovered.detachedRoundTrip||
                   recovered.transactionCommitted||
                   recovered.liveApplied||
                   recovered.grantAuthorized||
                   recovered.replayAuthorized||
                   recovered.rollbackAuthorized||
                   recovered.restartAdmissionAuthorized||
                   recovered.releaseAuthorized||
                   recovered.clientAckAuthorized||
                   !recovered.account.equals(
                       recovered.exactRestoredSnapshot.username())||
                   !recovered.terminalSha256.equals(
                       StrictDurablePlayerSnapshotWriter
                           .canonicalSnapshotSha256(
                               recovered.exactRestoredSnapshot))||
                   MailboxAtomicTerminalSnapshot.inspect(
                       recovered.exactRestoredSnapshot).state!=
                       MailboxAtomicTerminalSnapshot.State
                           .COHERENT_TERMINAL_NO_GRANT){
                    decision.set(Decision.REJECT_UNTRUSTED_RECOVERY);
                    return;
                }

                // A receiver with any restored Mailbox, occupied inventory
                // or changed gameplay snapshot is NOT a fresh hydration
                // receiver. This read-only snapshot comparison also covers
                // non-inventory modifications without relying on one field.
                if(receiver.mailboxSnapshotKnown()||
                   receiver.mailbox().size()!=0||
                   receiver.bank().inventorySlots()!=0){
                    decision.set(Decision.REJECT_NONFRESH_RECEIVER);
                    return;
                }
                PlayerSnapshot actual=PlayerSnapshotCodec.capture(
                    recovered.account,receiver
                );
                PlayerSnapshot blank=PlayerSnapshotCodec.capture(
                    recovered.account,new WorldPlayer()
                );
                if(!actual.values().equals(blank.values())){
                    decision.set(Decision.REJECT_NONFRESH_RECEIVER);
                    return;
                }
                decision.set(Decision.CANDIDATE_ONLY_NO_ADMISSION);
            }
        );
        return owned?decision.get():Decision.REJECT_NOT_OWNED;
    }

    private MailboxCommittedWorldGenerationCandidate(){}
}
