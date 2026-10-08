package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Semantic Mailbox -> exact-v308 subtype-31 presentation adapter.
 *
 * This maps only recovered read/claim enum state. It does not perform item
 * settlement, choose deposit destinations, generate rewards, schedule expiry
 * or define persistence policy.
 */
final class MailboxPresentationAdapter {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static void clear(
        ServerPacketWriter writer
    )throws IOException{
        ApplicationUiService.mailboxClear(
            Objects.requireNonNull(
                writer,
                "writer"
            )
        );
    }

    static void append(
        ServerPacketWriter writer,
        MailboxRewardDeliveryService.Snapshot
            snapshot
    )throws IOException{
        MailboxRewardDeliveryService.Snapshot checked=
            Objects.requireNonNull(
                snapshot,
                "snapshot"
            );

        ApplicationUiService.mailboxAppend(
            Objects.requireNonNull(
                writer,
                "writer"
            ),
            readStateCode(
                checked.readState
            ),
            checked.message.subject
        );
    }

    static void readState(
        ServerPacketWriter writer,
        int row,
        MailboxRewardDeliveryService.ReadState
            state
    )throws IOException{
        ApplicationUiService.mailboxReadState(
            Objects.requireNonNull(
                writer,
                "writer"
            ),
            row,
            readStateCode(state)
        );
    }

    static void claimState(
        ServerPacketWriter writer,
        MailboxRewardDeliveryService.ClaimState
            state
    )throws IOException{
        ApplicationUiService.mailboxClaimState(
            Objects.requireNonNull(
                writer,
                "writer"
            ),
            claimStateCode(state)
        );
    }

    static void detailMode(
        ServerPacketWriter writer,
        int mode
    )throws IOException{
        ApplicationUiService.mailboxDetailMode(
            Objects.requireNonNull(
                writer,
                "writer"
            ),
            mode
        );
    }

    static void finalizeRows(
        ServerPacketWriter writer
    )throws IOException{
        ApplicationUiService.mailboxFinalize(
            Objects.requireNonNull(
                writer,
                "writer"
            )
        );
    }

    static void attention(
        ServerPacketWriter writer
    )throws IOException{
        ApplicationUiService.mailboxAttention(
            Objects.requireNonNull(
                writer,
                "writer"
            )
        );
    }

    static void select(
        ServerPacketWriter writer,
        int row
    )throws IOException{
        ApplicationUiService.mailboxSelect(
            Objects.requireNonNull(
                writer,
                "writer"
            ),
            row
        );
    }

    static int readStateCode(
        MailboxRewardDeliveryService.ReadState
            state
    ){
        switch(Objects.requireNonNull(
                state,
                "state"
            )){
            case UNREAD:
                return 0;
            case READ:
                return 1;
            default:
                throw new IllegalStateException(
                    "unknown Mailbox read state "+
                    state
                );
        }
    }

    static int claimStateCode(
        MailboxRewardDeliveryService.ClaimState
            state
    ){
        switch(Objects.requireNonNull(
                state,
                "state"
            )){
            case EMPTY:
                return 0;
            case UNCLAIMED:
                return 1;
            case CLAIMED:
                return 2;
            default:
                throw new IllegalStateException(
                    "unknown Mailbox claim state "+
                    state
                );
        }
    }

    private MailboxPresentationAdapter(){}
}
