package spk.local;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Read-only exact-v308 Mailbox attachment widget projection (S2C53/32175).
 *
 * The client data contract establishes widget identity and item-container
 * encoding, not mailbox-opening, item ownership, claiming or settlement.
 * Clearing claimed/empty attachments is a defensive CUSTOM_LOCALLAB view
 * policy. Domain snapshots remain unchanged.
 */
final class MailboxAttachmentProjection {
    static final int ATTACHMENT_CONTAINER_WIDGET=32175;

    static void publishContainer(
        ServerPacketWriter writer,
        MailboxRewardDeliveryService.Snapshot snapshot
    )throws IOException{
        Objects.requireNonNull(writer,"writer");
        byte[] body=containerBody(snapshot);
        writer.varShort(53,body);
    }

    /**
     * Preflight the complete exact S2C53 body without advancing an outbound
     * stream or ISAAC cipher. Shared by the G21.3 direct publisher and
     * higher-level read-only detail composition.
     */
    static byte[] containerBody(
        MailboxRewardDeliveryService.Snapshot snapshot
    )throws IOException{
        MailboxRewardDeliveryService.Snapshot checked=
            Objects.requireNonNull(snapshot,"snapshot");

        RewardDeliveryMessage message=
            Objects.requireNonNull(
                checked.message,
                "mailbox message"
            );

        MailboxRewardDeliveryService.ClaimState state=
            Objects.requireNonNull(
                checked.claimState,
                "claimState"
            );

        List<RewardDeliveryMessage.Attachment> attachments=
            Objects.requireNonNull(
                message.attachments,
                "attachments"
            );

        if(state==MailboxRewardDeliveryService.ClaimState.EMPTY&&
            !attachments.isEmpty())
            throw new IllegalStateException(
                "EMPTY mailbox message still has attachments"
            );

        if(state==MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
            attachments.isEmpty())
            throw new IllegalStateException(
                "UNCLAIMED mailbox message has no attachments"
            );

        // The original semantic envelope retains its attachments after
        // settlement. Expose no actionable items once explicitly CLAIMED.
        boolean visible=
            state==MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

        int count=visible?attachments.size():0;

        if(count>0xffff)
            throw new IllegalArgumentException(
                "client Mailbox attachment slot overflow"
            );

        int[] itemIds=new int[count];
        int[] quantities=new int[count];

        for(int i=0;i<count;i++){
            RewardDeliveryMessage.Attachment attachment=
                Objects.requireNonNull(
                    attachments.get(i),
                    "mailbox attachment"
                );

            if(attachment.itemId<0||
                attachment.itemId>=0xffff)
                throw new IllegalArgumentException(
                    "client Mailbox itemId="+attachment.itemId
                );

            if(attachment.amount<=0||
                attachment.amount>Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                    "client Mailbox quantity="+attachment.amount
                );

            itemIds[i]=attachment.itemId;
            quantities[i]=(int)attachment.amount;
        }

        // Build the complete exact client packet before writing any bytes.
        byte[] body=BootstrapPackets.itemContainer53(
            ATTACHMENT_CONTAINER_WIDGET,
            itemIds,
            quantities
        );

        if(body.length>0xffff)
            throw new IllegalArgumentException(
                "client Mailbox S2C53 payload too large: "+
                body.length
            );

        return body;
    }

    private MailboxAttachmentProjection(){}
}
