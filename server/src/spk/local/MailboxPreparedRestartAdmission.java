package spk.local;

import java.util.Objects;

/**
 * G21.31: inspect a saved PREPARED account on a detached player BEFORE
 * the normal persistence load returns it to the live-session hydrator.
 *
 * A staged G21.22 journal must still refer to its original UNCLAIMED
 * envelope and full 28-slot inventory BEFORE image. In particular,
 * G21.25's hypothetical inventory+CLAIMED postimage is NOT a valid
 * restart authorization, even when its file was atomically replaced.
 *
 * This policy never mutates the loaded snapshot, filesystem, inventory,
 * mailbox, or live player. It never grants, rolls back or replays.
 */
final class MailboxPreparedRestartAdmission {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2131_RESTART_PREPARED_NO_GRANT";

    enum State {
        NO_JOURNAL,
        VALID_PREPARED_UNCLAIMED,
        QUARANTINE_INVALID_JOURNAL,
        QUARANTINE_MISSING_OR_REPLACED_MESSAGE,
        QUARANTINE_CLAIMED_OR_EMPTY_MESSAGE,
        QUARANTINE_ATTACHMENT_MISMATCH,
        QUARANTINE_INVENTORY_PREIMAGE_MISMATCH,
        QUARANTINE_INVALID_ACCOUNT_SNAPSHOT,
        QUARANTINE_TERMINAL_NO_GRANT,
        QUARANTINE_INVALID_TERMINAL_SNAPSHOT
    }

    static final class Decision {
        final State state;
        final boolean admissionAllowed;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final String authority=AUTHORITY;

        private Decision(State state,boolean allowed){
            this.state=Objects.requireNonNull(state,"state");
            this.admissionAllowed=allowed;
        }
    }

    static Decision inspect(PlayerSnapshot account){
        PlayerSnapshot snapshot=Objects.requireNonNull(
            account,"account"
        );

        // G21.64: terminal transaction evidence never bypasses the
        // no-journal fast path, including malformed or orphaned records.
        MailboxAtomicTerminalSnapshot.Observation terminal=
            MailboxAtomicTerminalSnapshot.inspect(snapshot);
        if(terminal.state!=MailboxAtomicTerminalSnapshot.State.ABSENT)
            return deny(terminal.state==
                MailboxAtomicTerminalSnapshot.State.COHERENT_TERMINAL_NO_GRANT
                ?State.QUARANTINE_TERMINAL_NO_GRANT
                :State.QUARANTINE_INVALID_TERMINAL_SNAPSHOT);

        // Leave every historical/non-G21.22 account untouched.
        final String namespacePrefix=
            PlayerSnapshotExtensionState.PREFIX+
            MailboxPreparedClaimJournal.NAMESPACE+".";
        boolean marked=false;
        for(String key:snapshot.values().keySet()){
            if(key.startsWith(namespacePrefix)){
                marked=true;
                break;
            }
        }
        if(!marked)
            return new Decision(State.NO_JOURNAL,true);

        final PlayerSnapshot normalized;
        final WorldPlayer staged=new WorldPlayer();
        try{
            normalized=PlayerSnapshotCodec.applyValidated(
                snapshot,staged
            );
        }catch(RuntimeException corrupted){
            return deny(State.QUARANTINE_INVALID_ACCOUNT_SNAPSHOT);
        }

        if(!normalized.values().equals(snapshot.values())||
           !normalized.username().equals(snapshot.username())||
           normalized.version()!=PlayerSnapshot.CURRENT_VERSION)
            return deny(State.QUARANTINE_INVALID_ACCOUNT_SNAPSHOT);

        final MailboxPreparedClaimJournal.Intent intent;
        try{
            intent=MailboxPreparedClaimJournal.inspectPrepared(
                staged
            );
        }catch(RuntimeException corrupted){
            return deny(State.QUARANTINE_INVALID_JOURNAL);
        }

        if(intent==null||
           !intent.account.equals(snapshot.username()))
            return deny(State.QUARANTINE_INVALID_JOURNAL);

        MailboxRewardDeliveryService.Snapshot selected=
            staged.mailbox().get(intent.messageId);
        if(selected==null||
           !selected.message.messageId.equals(intent.messageId))
            return deny(State.QUARANTINE_MISSING_OR_REPLACED_MESSAGE);

        if(selected.claimState!=
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED)
            return deny(State.QUARANTINE_CLAIMED_OR_EMPTY_MESSAGE);

        StringBuilder attachments=new StringBuilder();
        for(RewardDeliveryMessage.Attachment item:
                selected.message.attachments){
            if(attachments.length()!=0)
                attachments.append(',');
            attachments.append(item.itemId).append(':')
                .append(item.amount);
        }
        if(!intent.attachmentFingerprint.equals(
                attachments.toString()))
            return deny(State.QUARANTINE_ATTACHMENT_MISMATCH);

        int[] beforeIds=intent.expectedItemIds();
        int[] beforeQuantities=intent.expectedQuantities();
        if(beforeIds.length!=BankState.INVENTORY_CAPACITY||
           beforeQuantities.length!=BankState.INVENTORY_CAPACITY)
            return deny(State.QUARANTINE_INVENTORY_PREIMAGE_MISMATCH);

        for(int i=0;i<BankState.INVENTORY_CAPACITY;i++){
            BankState.InventorySlotSnapshot slot=
                staged.bank().inventorySlotSnapshot(i);
            int id=slot.occupied?slot.itemId:-1;
            int quantity=slot.occupied?slot.quantity:0;
            if(id!=beforeIds[i]||quantity!=beforeQuantities[i])
                return deny(
                    State.QUARANTINE_INVENTORY_PREIMAGE_MISMATCH
                );
        }

        return new Decision(State.VALID_PREPARED_UNCLAIMED,true);
    }

    private static Decision deny(State reason){
        return new Decision(reason,false);
    }

    private MailboxPreparedRestartAdmission(){}
}
