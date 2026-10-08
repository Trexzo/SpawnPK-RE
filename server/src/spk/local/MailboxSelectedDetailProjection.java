package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Rootless, read-only selected Mailbox detail publisher.
 *
 * Only exact recovered widgets 32168 (subject) and 32175 (attachments),
 * plus subtype-31 op4 claim visibility, are owned. Body/sent/expiry fields,
 * root navigation, input actions and item settlement are not inferred.
 */
final class MailboxSelectedDetailProjection {
    static final int SUBJECT_WIDGET=32168;

    static void publish(
        ServerPacketWriter writer,
        MailboxRewardDeliveryService.Snapshot snapshot
    )throws IOException{
        ServerPacketWriter checkedWriter=
            Objects.requireNonNull(writer,"writer");
        MailboxRewardDeliveryService.Snapshot checked=
            Objects.requireNonNull(snapshot,"snapshot");

        RewardDeliveryMessage message=
            Objects.requireNonNull(
                checked.message,"mailbox message"
            );

        MailboxInboxProjection.validateSubject(
            message.subject
        );

        // Complete every semantic and packet-length preflight before
        // the first S2C53 write. This does not make transport I/O atomic.
        MailboxPresentationAdapter.claimStateCode(
            checked.claimState
        );
        byte[] containerBody=
            MailboxAttachmentProjection.containerBody(
                checked
            );
        byte[] textBody=
            BootstrapPackets.widgetText126(
                SUBJECT_WIDGET,
                message.subject
            );

        if(textBody.length>0xffff)
            throw new IllegalArgumentException(
                "Mailbox subject S2C126 frame too large"
            );

        // CUSTOM_LOCALLAB ordering, not claimed as original server
        // ordering: content before claim visibility/action affordances.
        checkedWriter.varShort(
            53,
            containerBody
        );
        ApplicationBus126Publisher.send(
            checkedWriter,
            SUBJECT_WIDGET,
            message.subject
        );
        MailboxPresentationAdapter.claimState(
            checkedWriter,
            checked.claimState
        );
    }

    private MailboxSelectedDetailProjection(){}
}
