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

    /**
     * Fully decoded, untrusted persistence input. Restore validates and
     * commits all rows atomically into an empty semantic service.
     */
    static final class RestoredEntry {
        final RewardDeliveryMessage message;
        final ReadState readState;
        final ClaimState claimState;

        RestoredEntry(
            RewardDeliveryMessage message,
            ReadState readState,
            ClaimState claimState
        ){
            this.message=Objects.requireNonNull(message,"message");
            this.readState=Objects.requireNonNull(readState,"readState");
            this.claimState=Objects.requireNonNull(claimState,"claimState");
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

    /**
     * Restore already-validated LocalLab account envelopes. Never performs
     * item delivery/settlement. The service must be empty and remains intact
     * on any malformed row, capacity overflow or duplicate message identity.
     */
    synchronized void restore(List<RestoredEntry> restored){
        Objects.requireNonNull(restored,"restored");

        if(!entries.isEmpty())
            throw new IllegalStateException(
                "Mailbox restore requires empty service"
            );

        if(restored.size()>capacity)
            throw new IllegalArgumentException(
                "Mailbox restored rows exceed capacity"
            );

        LinkedHashMap<String,Entry> next=
            new LinkedHashMap<>();

        for(RestoredEntry input:restored){
            RestoredEntry row=
                Objects.requireNonNull(input,"restored row");
            RewardDeliveryMessage message=
                Objects.requireNonNull(
                    row.message,"restored message"
                );
            ReadState read=
                Objects.requireNonNull(
                    row.readState,"restored readState"
                );
            ClaimState claim=
                Objects.requireNonNull(
                    row.claimState,"restored claimState"
                );

            if(message.hasAttachments()
                ?claim==ClaimState.EMPTY
                :claim!=ClaimState.EMPTY)
                throw new IllegalArgumentException(
                    "Mailbox claim state contradicts attachments"
                );

            if(next.containsKey(message.messageId))
                throw new IllegalArgumentException(
                    "duplicate restored Mailbox message "+
                    message.messageId
                );

            Entry entry=new Entry(message);
            entry.readState=read;
            entry.claimState=claim;
            next.put(message.messageId,entry);
        }

        entries.putAll(next);
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
