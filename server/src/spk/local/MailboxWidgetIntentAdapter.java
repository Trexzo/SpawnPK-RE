package spk.local;

import java.util.Objects;

/**
 * Exact-v308 C2S185 Mailbox widget intent boundary.
 *
 * This adapter is deliberately pure. The caller must bind its Mailbox
 * service to the authenticated player and maintain its own selected message
 * identity. Returned intents are NOT authorization to mutate inventory,
 * bank, mail, or rewards. Revalidate ownership/state at the transaction
 * boundary before ever implementing settlement or deletion.
 */
final class MailboxWidgetIntentAdapter {
    static final int DEPOSIT_BANK_WIDGET=32178;
    static final int DEPOSIT_INVENTORY_WIDGET=32181;
    static final int DELETE_MESSAGE_WIDGET=32184;
    static final int REFRESH_INBOX_WIDGET=32185;
    static final int EXACT_WIDGET_OPCODE=185;

    enum Kind {
        DEPOSIT_TO_BANK,
        DEPOSIT_TO_INVENTORY,
        DELETE_MESSAGE,
        REFRESH_INBOX
    }

    static final class Intent {
        final Kind kind;
        final String messageId;
        final MailboxRewardDeliveryService.ClaimState observedClaimState;

        private Intent(
            Kind kind,
            String messageId,
            MailboxRewardDeliveryService.ClaimState observedClaimState
        ){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.messageId=messageId;
            this.observedClaimState=observedClaimState;
        }
    }

    static Intent resolve(
        WidgetActionClientRequest request,
        MailboxRewardDeliveryService mailbox,
        String selectedMessageId
    ){
        WidgetActionClientRequest checked=
            Objects.requireNonNull(request,"request");
        int widget=checked.widgetId();

        Kind kind;
        switch(widget){
            case DEPOSIT_BANK_WIDGET:
                kind=Kind.DEPOSIT_TO_BANK;
                break;
            case DEPOSIT_INVENTORY_WIDGET:
                kind=Kind.DEPOSIT_TO_INVENTORY;
                break;
            case DELETE_MESSAGE_WIDGET:
                kind=Kind.DELETE_MESSAGE;
                break;
            case REFRESH_INBOX_WIDGET:
                kind=Kind.REFRESH_INBOX;
                break;
            default:
                return null;
        }

        ClientRequestMetadata metadata=
            Objects.requireNonNull(
                checked.metadata(),
                "metadata"
            );

        if(metadata.opcode!=EXACT_WIDGET_OPCODE||
            metadata.provenance!=
                ClientRequestProvenance.EXACT_CURRENT_CLIENT)
            throw new IllegalArgumentException(
                "Mailbox requires exact-current C2S185 request"
            );

        MailboxRewardDeliveryService checkedMailbox=
            Objects.requireNonNull(mailbox,"mailbox");

        if(kind==Kind.REFRESH_INBOX)
            return new Intent(
                kind,
                null,
                null
            );

        if(selectedMessageId==null)
            throw new IllegalStateException(
                "Mailbox action without selected message"
            );

        String normalized=
            RewardDeliveryMessage.normalizeMessageId(
                selectedMessageId
            );

        MailboxRewardDeliveryService.Snapshot selected=
            checkedMailbox.get(normalized);

        if(selected==null)
            throw new IllegalStateException(
                "stale or foreign Mailbox selection"
            );

        MailboxRewardDeliveryService.ClaimState state=
            Objects.requireNonNull(
                selected.claimState,
                "selected claim state"
            );

        if((kind==Kind.DEPOSIT_TO_BANK||
            kind==Kind.DEPOSIT_TO_INVENTORY)&&
            state!=MailboxRewardDeliveryService.ClaimState.UNCLAIMED)
            throw new IllegalStateException(
                "Mailbox attachments not claimable"
            );

        return new Intent(
            kind,
            selected.message.messageId,
            state
        );
    }

    private MailboxWidgetIntentAdapter(){}
}
