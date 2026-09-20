package spk.local;

import java.util.*;

/**
 * Protocol-independent mailbox/offline-reward state.
 *
 * This service owns envelope presence, read state and claim acknowledgement.
 * It never moves items, chooses inventory/bank destinations, schedules expiry
 * or generates rewards.
 */
final class MailboxRewardDeliveryService {
    enum ReadState {
        UNREAD,
        READ
    }

    enum ClaimState {
        EMPTY,
        UNCLAIMED,
        CLAIMED
    }

    static final class Snapshot {
        final RewardDeliveryMessage message;
        final ReadState readState;
        final ClaimState claimState;

        Snapshot(
            Entry entry
        ){
            this.message=
                entry.message;
            this.readState=
                entry.readState;
            this.claimState=
                entry.claimState;
        }

        @Override public String toString(){
            return "MailboxSnapshot{"+
                "messageId="+
                    message.messageId+
                ",readState="+readState+
                ",claimState="+claimState+
                "}";
        }
    }

    private static final class Entry {
        final RewardDeliveryMessage message;
        ReadState readState;
        ClaimState claimState;

        Entry(
            RewardDeliveryMessage message
        ){
            this.message=message;
            this.readState=
                ReadState.UNREAD;
            this.claimState=
                message.hasAttachments()
                    ?ClaimState.UNCLAIMED
                    :ClaimState.EMPTY;
        }

        Snapshot snapshot(){
            return new Snapshot(
                this
            );
        }
    }

    private final int capacity;

    private final LinkedHashMap<String,Entry>
        entries=
            new LinkedHashMap<>();

    MailboxRewardDeliveryService(
        int capacity
    ){
        if(capacity<=0)
            throw new IllegalArgumentException(
                "capacity="+capacity
            );

        this.capacity=capacity;
    }

    synchronized int capacity(){
        return capacity;
    }

    synchronized int size(){
        return entries.size();
    }

    synchronized int unreadCount(){
        int count=0;

        for(Entry entry:
                entries.values()){
            if(entry.readState==
                    ReadState.UNREAD)
                count++;
        }

        return count;
    }

    synchronized Snapshot deliver(
        RewardDeliveryMessage message
    ){
        Objects.requireNonNull(
            message,
            "message"
        );

        if(entries.containsKey(
                message.messageId))
            throw new IllegalStateException(
                "duplicate messageId "+
                message.messageId
            );

        if(entries.size()>=capacity)
            throw new IllegalStateException(
                "mailbox capacity reached "+
                capacity
            );

        Entry entry=
            new Entry(
                message
            );

        entries.put(
            message.messageId,
            entry
        );

        return entry.snapshot();
    }

    synchronized Snapshot get(
        String messageId
    ){
        Entry entry=
            entries.get(
                RewardDeliveryMessage
                    .normalizeMessageId(
                        messageId
                    )
            );

        return entry==null
            ?null
            :entry.snapshot();
    }

    synchronized boolean markRead(
        String messageId
    ){
        Entry entry=
            requireEntry(
                messageId
            );

        if(entry.readState==
                ReadState.READ)
            return false;

        entry.readState=
            ReadState.READ;
        return true;
    }

    /**
     * Acknowledge that an external inventory/bank settlement completed.
     *
     * No item mutation occurs here. Call only after the external settlement
     * transaction has succeeded.
     */
    synchronized boolean acknowledgeAttachmentSettlement(
        String messageId
    ){
        Entry entry=
            requireEntry(
                messageId
            );

        if(entry.claimState==
                ClaimState.EMPTY)
            throw new IllegalStateException(
                "message has no attachments "+
                entry.message.messageId
            );

        if(entry.claimState==
                ClaimState.CLAIMED)
            return false;

        entry.claimState=
            ClaimState.CLAIMED;
        return true;
    }

    synchronized Snapshot delete(
        String messageId
    ){
        String normalized=
            RewardDeliveryMessage
                .normalizeMessageId(
                    messageId
                );

        Entry removed=
            entries.remove(
                normalized
            );

        return removed==null
            ?null
            :removed.snapshot();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Entry entry:
                entries.values())
            out.add(
                entry.snapshot()
            );

        return Collections.unmodifiableList(
            out
        );
    }

    private Entry requireEntry(
        String messageId
    ){
        String normalized=
            RewardDeliveryMessage
                .normalizeMessageId(
                    messageId
                );

        Entry entry=
            entries.get(
                normalized
            );

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown messageId="+
                normalized
            );

        return entry;
    }
}
