package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * G21.33 read-only triage of a G21.32 negative review fence using the
 * existing WorldPlayerPersistence FIFO forensic load. All outcomes are
 * NON-AUTHORIZING: no session admission, grant, replay, rollback, or
 * negative-marker clearing can be inferred from this observation.
 *
 * Account file and fence are separate files, so double-reading the fence
 * only detects some races; it does NOT make these files an atomic snapshot.
 */
final class MailboxFencedRestartForensics {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2133_FENCED_ACCOUNT_FORENSICS_NO_GRANT";

    enum State {
        NO_FENCE_NO_AUTHORITY,
        INVALID_OR_UNREADABLE_FENCE,
        FENCE_DISAPPEARED_OR_CHANGED,
        ACCOUNT_READ_FAILED,
        MISSING_ACCOUNT,
        INVALID_OR_DIVERGENT_ACCOUNT,
        EXACT_PREPARED_UNCLAIMED,
        EXACT_HYPOTHETICAL_CLAIMED,
        OTHER_ACCOUNT_POSTIMAGE
    }

    static final class Report {
        final State state;
        final String account;
        final String observedSnapshotSha256;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean releaseFenceAuthorized=false;
        final boolean sessionAdmissionAuthorized=false;
        final boolean fileDurabilityConfirmed=false;
        final boolean automaticRecoveryAuthorized=false;
        final String authority=AUTHORITY;

        private Report(
            State state,String account,String snapshotSha256
        ){
            this.state=Objects.requireNonNull(state,"state");
            this.account=account;
            this.observedSnapshotSha256=snapshotSha256;
        }
    }

    static Report inspect(
        WorldPlayerPersistence persistence,
        MailboxDurableReviewFence fence,
        String account
    ){
        Objects.requireNonNull(persistence,"persistence");
        Objects.requireNonNull(fence,"fence");
        Objects.requireNonNull(account,"account");

        final MailboxDurableReviewFence.Record marker;
        try{
            if(!fence.present(account))
                return result(State.NO_FENCE_NO_AUTHORITY,account,null);
            marker=fence.inspect(account);
        }catch(IOException|RuntimeException unreadable){
            return result(State.INVALID_OR_UNREADABLE_FENCE,account,null);
        }

        final Optional<PlayerSnapshot> snapshot;
        try{
            // G21.31 forensic FIFO explicitly avoids session hydration.
            snapshot=persistence.observeUntrustedMailboxAccount(account);
        }catch(IOException|RuntimeException readFailure){
            return result(State.ACCOUNT_READ_FAILED,account,null);
        }

        // Post-read check prevents a detected marker swap/disappearance
        // from being presented as stable evidence. This does NOT provide
        // transaction-wide atomicity across two independent files.
        try{
            if(!fence.present(account))
                return result(
                    State.FENCE_DISAPPEARED_OR_CHANGED,account,null
                );
            MailboxDurableReviewFence.Record rechecked=
                fence.inspect(account);
            if(!sameMarker(marker,rechecked))
                return result(
                    State.FENCE_DISAPPEARED_OR_CHANGED,account,null
                );
        }catch(IOException|RuntimeException mismatch){
            return result(State.FENCE_DISAPPEARED_OR_CHANGED,account,null);
        }

        if(!snapshot.isPresent())
            return result(State.MISSING_ACCOUNT,account,null);

        PlayerSnapshot stored=snapshot.get();
        if(!account.equals(stored.username())||
           stored.version()!=PlayerSnapshot.CURRENT_VERSION)
            return result(
                State.INVALID_OR_DIVERGENT_ACCOUNT,account,null
            );

        final PlayerSnapshot canonical;
        try{
            canonical=PlayerSnapshotCodec.validateAndNormalize(stored);
        }catch(RuntimeException damaged){
            return result(
                State.INVALID_OR_DIVERGENT_ACCOUNT,account,null
            );
        }
        if(!canonical.values().equals(stored.values()))
            return result(
                State.INVALID_OR_DIVERGENT_ACCOUNT,account,null
            );

        String hash=StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(canonical);
        if(hash.equals(marker.preparedSha256)){
            MailboxPreparedRestartAdmission.Decision prepared=
                MailboxPreparedRestartAdmission.inspect(canonical);
            if(prepared.state==
                    MailboxPreparedRestartAdmission.State
                        .VALID_PREPARED_UNCLAIMED&&
               matchesIntentIdentity(canonical,marker))
                return result(
                    State.EXACT_PREPARED_UNCLAIMED,account,hash
                );
            return result(State.OTHER_ACCOUNT_POSTIMAGE,account,hash);
        }

        if(hash.equals(marker.hypotheticalSha256)){
            if(exactClaimedPostimage(canonical,marker))
                return result(
                    State.EXACT_HYPOTHETICAL_CLAIMED,account,hash
                );
            return result(State.OTHER_ACCOUNT_POSTIMAGE,account,hash);
        }

        return result(State.OTHER_ACCOUNT_POSTIMAGE,account,hash);
    }

    private static boolean matchesIntentIdentity(
        PlayerSnapshot snapshot,MailboxDurableReviewFence.Record marker
    ){
        String prefix="extension."+
            MailboxPreparedClaimJournal.NAMESPACE+".";
        return marker.account.equals(snapshot.username())&&
            marker.messageId.equals(snapshot.value(prefix+"message"))&&
            marker.intentKey.equals(snapshot.value(prefix+"key"));
    }

    private static boolean exactClaimedPostimage(
        PlayerSnapshot snapshot,MailboxDurableReviewFence.Record marker
    ){
        if(!matchesIntentIdentity(snapshot,marker))
            return false;

        try{
            WorldPlayer detached=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(snapshot,detached);
            MailboxPreparedClaimJournal.Intent journal=
                MailboxPreparedClaimJournal.inspectPrepared(detached);
            if(journal==null||
               !marker.intentKey.equals(journal.idempotencyKey)||
               !marker.messageId.equals(journal.messageId)||
               !marker.account.equals(journal.account))
                return false;
            MailboxRewardDeliveryService.Snapshot selected=
                detached.mailbox().get(marker.messageId);
            if(selected==null||
               selected.claimState!=
                   MailboxRewardDeliveryService.ClaimState.CLAIMED)
                return false;

            StringBuilder attachmentFingerprint=new StringBuilder();
            for(RewardDeliveryMessage.Attachment attachment:
                    selected.message.attachments){
                if(attachmentFingerprint.length()>0)
                    attachmentFingerprint.append(',');
                attachmentFingerprint.append(attachment.itemId)
                    .append(':').append(attachment.amount);
            }
            if(!journal.attachmentFingerprint.equals(
                    attachmentFingerprint.toString()))
                return false;

            int[] expectedIds=journal.proposedItemIds();
            int[] expectedQuantities=journal.proposedQuantities();
            if(expectedIds.length!=BankState.INVENTORY_CAPACITY||
               expectedQuantities.length!=BankState.INVENTORY_CAPACITY)
                return false;
            for(int i=0;i<expectedIds.length;i++){
                BankState.InventorySlotSnapshot slot=
                    detached.bank().inventorySlotSnapshot(i);
                int id=slot.occupied?slot.itemId:-1;
                int qty=slot.occupied?slot.quantity:0;
                if(id!=expectedIds[i]||qty!=expectedQuantities[i])
                    return false;
            }
            return true;
        }catch(RuntimeException invalid){
            return false;
        }
    }

    private static boolean sameMarker(
        MailboxDurableReviewFence.Record a,
        MailboxDurableReviewFence.Record b
    ){
        return a.account.equals(b.account)&&
            a.messageId.equals(b.messageId)&&
            a.intentKey.equals(b.intentKey)&&
            a.preparedSha256.equals(b.preparedSha256)&&
            a.hypotheticalSha256.equals(b.hypotheticalSha256);
    }

    private static Report result(
        State state,String account,String digest
    ){
        return new Report(state,account,digest);
    }

    private MailboxFencedRestartForensics(){}
}
