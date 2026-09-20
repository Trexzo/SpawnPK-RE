package spk.local;

import java.util.*;

/**
 * Immutable protocol-independent mailbox/offline-reward envelope.
 *
 * Attachment settlement, expiry scheduling and persistence are intentionally
 * outside this value object.
 */
final class RewardDeliveryMessage {
    static final class Attachment {
        final int itemId;
        final long amount;

        Attachment(
            int itemId,
            long amount
        ){
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId="+itemId
                );

            if(amount<=0)
                throw new IllegalArgumentException(
                    "amount="+amount
                );

            this.itemId=itemId;
            this.amount=amount;
        }

        @Override public String toString(){
            return "Attachment{"+
                "itemId="+itemId+
                ",amount="+amount+
                "}";
        }
    }

    final String messageId;
    final String subject;
    final String body;
    final List<Attachment> attachments;
    final String sourceAuthority;

    RewardDeliveryMessage(
        String messageId,
        String subject,
        String body,
        List<Attachment> attachments,
        String sourceAuthority
    ){
        this.messageId=
            normalizeMessageId(
                messageId
            );

        if(subject==null||
           subject.trim().isEmpty())
            throw new IllegalArgumentException(
                "subject"
            );

        if(body==null)
            throw new NullPointerException(
                "body"
            );

        this.subject=subject;
        this.body=body;

        ArrayList<Attachment> copy=
            new ArrayList<>();

        if(attachments!=null){
            for(Attachment attachment:
                    attachments)
                copy.add(
                    Objects.requireNonNull(
                        attachment,
                        "attachment"
                    )
                );
        }

        this.attachments=
            Collections.unmodifiableList(
                copy
            );

        if(sourceAuthority==null||
           sourceAuthority.trim().isEmpty())
            throw new IllegalArgumentException(
                "sourceAuthority"
            );

        this.sourceAuthority=
            sourceAuthority.trim();
    }

    boolean hasAttachments(){
        return !attachments.isEmpty();
    }

    static String normalizeMessageId(
        String messageId
    ){
        Objects.requireNonNull(
            messageId,
            "messageId"
        );

        String normalized=
            messageId.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "blank messageId"
            );

        if(normalized.length()>128)
            throw new IllegalArgumentException(
                "messageId too long"
            );

        for(int i=0;
            i<normalized.length();
            i++){
            char c=normalized.charAt(i);

            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||
                c=='_'||
                c=='-'||
                c==':';

            if(!ok)
                throw new IllegalArgumentException(
                    "invalid messageId="+
                    messageId
                );
        }

        return normalized;
    }

    @Override public String toString(){
        return "RewardDeliveryMessage{"+
            "messageId="+messageId+
            ",subject="+subject+
            ",attachments="+
                attachments.size()+
            ",sourceAuthority="+
                sourceAuthority+
            "}";
    }
}
