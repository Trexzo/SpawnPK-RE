package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded, read-only projection of semantic Mailbox rows to the exact-v308
 * subtype-31 inbox-list protocol. This class does not open a client root,
 * select/read/delete messages, settle attachments or persist mail.
 *
 * All validation completes before the first packet is sent. Transport I/O
 * failures can still interrupt a publication; this is not a transaction.
 */
final class MailboxInboxProjection {
    static final int CLIENT_VISIBLE_ROW_LIMIT=35;

    static void publish(
        ServerPacketWriter writer,
        List<MailboxRewardDeliveryService.Snapshot> snapshots
    )throws IOException{
        ServerPacketWriter checkedWriter=
            Objects.requireNonNull(writer,"writer");

        List<MailboxRewardDeliveryService.Snapshot> rows=
            new ArrayList<>(
                Objects.requireNonNull(snapshots,"snapshots")
            );

        if(rows.size()>CLIENT_VISIBLE_ROW_LIMIT)
            throw new IllegalArgumentException(
                "client Mailbox row limit "+
                CLIENT_VISIBLE_ROW_LIMIT+
                " exceeded by "+rows.size()
            );

        Set<String> seenMessageIds=new HashSet<>();

        for(MailboxRewardDeliveryService.Snapshot row:rows){
            MailboxRewardDeliveryService.Snapshot checkedRow=
                Objects.requireNonNull(row,"mailbox row");

            RewardDeliveryMessage message=
                Objects.requireNonNull(
                    checkedRow.message,
                    "mailbox message"
                );

            if(!seenMessageIds.add(
                    RewardDeliveryMessage.normalizeMessageId(
                        message.messageId
                    )
                ))
                throw new IllegalArgumentException(
                    "duplicate mailbox messageId "+
                    message.messageId
                );

            // Validate semantic state before writing CLEAR.
            MailboxPresentationAdapter.readStateCode(
                checkedRow.readState
            );

            validateSubject(message.subject);
        }

        MailboxPresentationAdapter.clear(checkedWriter);

        for(MailboxRewardDeliveryService.Snapshot row:rows)
            MailboxPresentationAdapter.append(
                checkedWriter,
                row
            );

        MailboxPresentationAdapter.finalizeRows(
            checkedWriter
        );
    }

    /**
     * S2C250 var-byte frame has a 255-byte payload ceiling.
     * subtype(2) + operation(1) + state(1) + subject + LF(1)
     * requires subject length <= 250 Latin-1 bytes.
     *
     * LF is the client string terminator; control characters and text that
     * would undergo lossy charset replacement are never published.
     */
    private static void validateSubject(String subject){
        Objects.requireNonNull(subject,"subject");

        if(subject.isEmpty()||subject.length()>250)
            throw new IllegalArgumentException(
                "Mailbox subject length outside 1..250"
            );

        for(int i=0;i<subject.length();i++){
            char c=subject.charAt(i);

            if(c<32||c==127||(c>=128&&c<160)||c>255)
                throw new IllegalArgumentException(
                    "Mailbox subject is not single-line printable Latin-1"
                );
        }
    }

    private MailboxInboxProjection(){}
}
