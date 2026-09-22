package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Player-scoped Coffer of Unclaimed Rewards & Prizes.
 *
 * The exact client proves a 70-slot item container with partial remove actions
 * and bulk inventory/bank intents. Actual inventory/bank mutation, persistence,
 * expiry and reward generation remain external.
 */
final class UnclaimedRewardCofferService {
    static final int CAPACITY=70;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum Destination {
        INVENTORY,
        BANK
    }

    enum SettlementState {
        RESERVED,
        SETTLED,
        CANCELLED
    }

    static final class SlotId
        implements Comparable<SlotId> {

        private final long value;

        SlotId(long value){
            if(value<=0L)
                throw new IllegalArgumentException(
                    "slotId="+value
                );
            this.value=value;
        }

        long value(){
            return value;
        }

        @Override public int compareTo(
            SlotId other
        ){
            return Long.compare(
                value,
                Objects.requireNonNull(
                    other,
                    "other"
                ).value
            );
        }

        @Override public boolean equals(
            Object other
        ){
            return other instanceof SlotId&&
                value==
                    ((SlotId)other).value;
        }

        @Override public int hashCode(){
            return Long.hashCode(value);
        }

        @Override public String toString(){
            return "coffer-slot-"+
                Long.toUnsignedString(value);
        }
    }

    static final class SettlementId
        implements Comparable<SettlementId> {

        private final long value;

        SettlementId(long value){
            if(value<=0L)
                throw new IllegalArgumentException(
                    "settlementId="+value
                );
            this.value=value;
        }

        @Override public int compareTo(
            SettlementId other
        ){
            return Long.compare(
                value,
                Objects.requireNonNull(
                    other,
                    "other"
                ).value
            );
        }

        @Override public boolean equals(
            Object other
        ){
            return other instanceof SettlementId&&
                value==
                    ((SettlementId)other).value;
        }

        @Override public int hashCode(){
            return Long.hashCode(value);
        }

        @Override public String toString(){
            return "coffer-settlement-"+
                Long.toUnsignedString(value);
        }
    }

    static final class SlotSnapshot {
        final SlotId slotId;
        final int itemId;
        final long amount;
        final long reserved;
        final long available;
        final String deliveryKey;
        final String sourceAuthority;

        SlotSnapshot(Slot slot){
            this.slotId=slot.id;
            this.itemId=slot.itemId;
            this.amount=slot.amount;
            this.reserved=slot.reserved;
            this.available=
                slot.amount-
                    slot.reserved;
            this.deliveryKey=
                slot.deliveryKey;
            this.sourceAuthority=
                slot.sourceAuthority;
        }
    }

    static final class SettlementLineSnapshot {
        final SlotId slotId;
        final long amount;

        SettlementLineSnapshot(
            SlotId slotId,
            long amount
        ){
            this.slotId=slotId;
            this.amount=amount;
        }
    }

    static final class SettlementSnapshot {
        final SettlementId settlementId;
        final String ownerRef;
        final Destination destination;
        final SettlementState state;
        final List<SettlementLineSnapshot> lines;

        SettlementSnapshot(
            Settlement settlement
        ){
            this.settlementId=
                settlement.id;
            this.ownerRef=
                settlement.ownerRef;
            this.destination=
                settlement.destination;
            this.state=
                settlement.state;

            ArrayList<SettlementLineSnapshot>
                copy=
                    new ArrayList<>();

            for(Map.Entry<SlotId,Long> line:
                    settlement.lines.entrySet())
                copy.add(
                    new SettlementLineSnapshot(
                        line.getKey(),
                        line.getValue()
                    )
                );

            this.lines=
                Collections.unmodifiableList(
                    copy
                );
        }

        boolean terminal(){
            return state!=
                SettlementState.RESERVED;
        }
    }

    static final class Snapshot {
        final String ownerRef;
        final int capacity;
        final List<SlotSnapshot> slots;
        final List<SettlementSnapshot> settlements;
        final String presentationAuthority;

        Snapshot(
            Coffer coffer,
            Collection<Settlement> settlements
        ){
            this.ownerRef=
                coffer.ownerRef;
            this.capacity=CAPACITY;

            ArrayList<SlotSnapshot>
                slotSnapshots=
                    new ArrayList<>();

            for(Slot slot:
                    coffer.slots.values())
                slotSnapshots.add(
                    new SlotSnapshot(slot)
                );

            this.slots=
                Collections.unmodifiableList(
                    slotSnapshots
                );

            ArrayList<SettlementSnapshot>
                settlementSnapshots=
                    new ArrayList<>();

            for(Settlement settlement:
                    settlements)
                if(coffer.ownerRef.equals(
                        settlement.ownerRef))
                    settlementSnapshots.add(
                        new SettlementSnapshot(
                            settlement
                        )
                    );

            settlementSnapshots.sort(
                Comparator.comparing(
                    value->
                        value.settlementId
                )
            );

            this.settlements=
                Collections.unmodifiableList(
                    settlementSnapshots
                );
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        SlotSnapshot slot(
            SlotId slotId
        ){
            SlotId id=
                Objects.requireNonNull(
                    slotId,
                    "slotId"
                );

            for(SlotSnapshot slot:slots)
                if(slot.slotId.equals(id))
                    return slot;

            return null;
        }

        int size(){
            return slots.size();
        }

        boolean full(){
            return slots.size()>=capacity;
        }
    }

    private static final class Slot {
        final SlotId id;
        final int itemId;
        final String deliveryKey;
        final String sourceAuthority;

        long amount;
        long reserved;

        Slot(
            SlotId id,
            RewardDeliveryMessage.Attachment
                attachment,
            String deliveryKey,
            String sourceAuthority
        ){
            this.id=id;
            this.itemId=
                attachment.itemId;
            this.amount=
                attachment.amount;
            this.deliveryKey=
                deliveryKey;
            this.sourceAuthority=
                sourceAuthority;
        }
    }

    private static final class Coffer {
        final String ownerRef;

        final LinkedHashMap<SlotId,Slot>
            slots=
                new LinkedHashMap<>();

        final HashSet<String>
            deliveryKeys=
                new HashSet<>();

        Coffer(String ownerRef){
            this.ownerRef=ownerRef;
        }
    }

    private static final class Settlement {
        final SettlementId id;
        final String ownerRef;
        final Destination destination;
        final LinkedHashMap<SlotId,Long>
            lines=
                new LinkedHashMap<>();

        SettlementState state=
            SettlementState.RESERVED;

        Settlement(
            SettlementId id,
            String ownerRef,
            Destination destination
        ){
            this.id=id;
            this.ownerRef=ownerRef;
            this.destination=
                destination;
        }
    }

    private final AtomicLong slotSequence=
        new AtomicLong();

    private final AtomicLong settlementSequence=
        new AtomicLong();

    private final LinkedHashMap<String,Coffer>
        coffers=
            new LinkedHashMap<>();

    private final LinkedHashMap<SettlementId,Settlement>
        settlements=
            new LinkedHashMap<>();

    synchronized Snapshot deliver(
        String ownerRef,
        String deliveryKey,
        Collection<
            RewardDeliveryMessage.Attachment
        > attachments,
        String sourceAuthority
    ){
        String owner=
            normalizeOwner(
                ownerRef
            );
        String delivery=
            RewardDeliveryMessage
                .normalizeMessageId(
                    deliveryKey
                );
        String authority=
            MatchRules.requireText(
                sourceAuthority,
                "sourceAuthority"
            );

        Objects.requireNonNull(
            attachments,
            "attachments"
        );

        if(attachments.isEmpty())
            throw new IllegalArgumentException(
                "coffer delivery attachments empty"
            );

        ArrayList<
            RewardDeliveryMessage.Attachment
        > checked=
            new ArrayList<>();

        for(RewardDeliveryMessage.Attachment
                attachment:attachments)
            checked.add(
                Objects.requireNonNull(
                    attachment,
                    "attachment"
                )
            );

        Coffer existing=
            coffers.get(owner);

        if(existing!=null&&
           existing.deliveryKeys.contains(
                delivery))
            throw new IllegalStateException(
                "duplicate coffer deliveryKey "+
                delivery+
                " owner="+owner
            );

        int current=
            existing==null
                ?0
                :existing.slots.size();

        if(checked.size()>
                CAPACITY-current)
            throw new IllegalStateException(
                "coffer capacity exceeded owner="+
                owner+
                " current="+current+
                " incoming="+checked.size()+
                " capacity="+CAPACITY
            );

        ArrayList<Slot> pending=
            new ArrayList<>();

        for(RewardDeliveryMessage.Attachment
                attachment:checked)
            pending.add(
                new Slot(
                    nextSlotId(),
                    attachment,
                    delivery,
                    authority
                )
            );

        Coffer coffer=
            existing;

        if(coffer==null){
            coffer=
                new Coffer(owner);
            coffers.put(
                owner,
                coffer
            );
        }

        coffer.deliveryKeys.add(
            delivery
        );

        for(Slot slot:pending)
            coffer.slots.put(
                slot.id,
                slot
            );

        return snapshotOf(coffer);
    }

    synchronized SettlementSnapshot
        beginSettlement(
            String ownerRef,
            SlotId slotId,
            long amount,
            Destination destination
        ){
        if(amount<=0L)
            throw new IllegalArgumentException(
                "amount="+amount
            );

        Coffer coffer=
            requireCoffer(
                ownerRef
            );
        Slot slot=
            requireSlot(
                coffer,
                slotId
            );

        long available=
            slot.amount-
                slot.reserved;

        if(amount>available)
            throw new IllegalStateException(
                "coffer amount unavailable slot="+
                slot.id+
                " requested="+amount+
                " available="+available
            );

        Settlement settlement=
            new Settlement(
                nextSettlementId(),
                coffer.ownerRef,
                Objects.requireNonNull(
                    destination,
                    "destination"
                )
            );

        settlement.lines.put(
            slot.id,
            amount
        );

        slot.reserved=
            Math.addExact(
                slot.reserved,
                amount
            );

        settlements.put(
            settlement.id,
            settlement
        );

        return new SettlementSnapshot(
            settlement
        );
    }

    synchronized SettlementSnapshot
        beginBulkSettlement(
            String ownerRef,
            Destination destination
        ){
        Coffer coffer=
            requireCoffer(
                ownerRef
            );

        if(coffer.slots.isEmpty())
            throw new IllegalStateException(
                "coffer empty owner="+
                coffer.ownerRef
            );

        for(Slot slot:
                coffer.slots.values())
            if(slot.reserved!=0L)
                throw new IllegalStateException(
                    "bulk coffer settlement requires no active reservation owner="+
                    coffer.ownerRef
                );

        Settlement settlement=
            new Settlement(
                nextSettlementId(),
                coffer.ownerRef,
                Objects.requireNonNull(
                    destination,
                    "destination"
                )
            );

        for(Slot slot:
                coffer.slots.values())
            settlement.lines.put(
                slot.id,
                slot.amount
            );

        // Every line has been preflighted before any reservation mutation.
        for(Map.Entry<SlotId,Long> line:
                settlement.lines.entrySet()){
            Slot slot=
                coffer.slots.get(
                    line.getKey()
                );
            slot.reserved=
                line.getValue();
        }

        settlements.put(
            settlement.id,
            settlement
        );

        return new SettlementSnapshot(
            settlement
        );
    }

    /**
     * Confirm only after the external inventory/bank transfer succeeded.
     */
    synchronized boolean confirmSettlement(
        SettlementId settlementId
    ){
        Settlement settlement=
            requireSettlement(
                settlementId
            );

        if(settlement.state==
                SettlementState.SETTLED)
            return false;

        if(settlement.state==
                SettlementState.CANCELLED)
            throw new IllegalStateException(
                "cancelled coffer settlement cannot confirm "+
                settlement.id
            );

        Coffer coffer=
            requireCoffer(
                settlement.ownerRef
            );

        preflightReservation(
            coffer,
            settlement
        );

        ArrayList<SlotId> emptied=
            new ArrayList<>();

        for(Map.Entry<SlotId,Long> line:
                settlement.lines.entrySet()){
            Slot slot=
                coffer.slots.get(
                    line.getKey()
                );
            long amount=
                line.getValue();

            slot.reserved-=amount;
            slot.amount-=amount;

            if(slot.amount==0L)
                emptied.add(
                    slot.id
                );
        }

        for(SlotId slotId:emptied)
            coffer.slots.remove(
                slotId
            );

        settlement.state=
            SettlementState.SETTLED;

        return true;
    }

    synchronized boolean cancelSettlement(
        SettlementId settlementId
    ){
        Settlement settlement=
            requireSettlement(
                settlementId
            );

        if(settlement.state==
                SettlementState.CANCELLED)
            return false;

        if(settlement.state==
                SettlementState.SETTLED)
            throw new IllegalStateException(
                "settled coffer settlement cannot cancel "+
                settlement.id
            );

        Coffer coffer=
            requireCoffer(
                settlement.ownerRef
            );

        preflightReservation(
            coffer,
            settlement
        );

        for(Map.Entry<SlotId,Long> line:
                settlement.lines.entrySet()){
            Slot slot=
                coffer.slots.get(
                    line.getKey()
                );

            slot.reserved-=
                line.getValue();
        }

        settlement.state=
            SettlementState.CANCELLED;

        return true;
    }

    synchronized Snapshot get(
        String ownerRef
    ){
        Coffer coffer=
            coffers.get(
                normalizeOwner(
                    ownerRef
                )
            );

        return coffer==null
            ?null
            :snapshotOf(coffer);
    }

    synchronized SettlementSnapshot settlement(
        SettlementId settlementId
    ){
        Settlement settlement=
            settlements.get(
                Objects.requireNonNull(
                    settlementId,
                    "settlementId"
                )
            );

        return settlement==null
            ?null
            :new SettlementSnapshot(
                settlement
            );
    }

    synchronized int playerCount(){
        return coffers.size();
    }

    private Snapshot snapshotOf(
        Coffer coffer
    ){
        return new Snapshot(
            coffer,
            settlements.values()
        );
    }

    private Coffer requireCoffer(
        String ownerRef
    ){
        String owner=
            normalizeOwner(
                ownerRef
            );

        Coffer coffer=
            coffers.get(owner);

        if(coffer==null)
            throw new IllegalArgumentException(
                "unknown reward coffer owner="+
                owner
            );

        return coffer;
    }

    private static Slot requireSlot(
        Coffer coffer,
        SlotId slotId
    ){
        SlotId id=
            Objects.requireNonNull(
                slotId,
                "slotId"
            );

        Slot slot=
            coffer.slots.get(id);

        if(slot==null)
            throw new IllegalArgumentException(
                "unknown coffer slot "+
                id+
                " owner="+coffer.ownerRef
            );

        return slot;
    }

    private Settlement requireSettlement(
        SettlementId settlementId
    ){
        SettlementId id=
            Objects.requireNonNull(
                settlementId,
                "settlementId"
            );

        Settlement settlement=
            settlements.get(id);

        if(settlement==null)
            throw new IllegalArgumentException(
                "unknown coffer settlement "+
                id
            );

        return settlement;
    }

    private static void preflightReservation(
        Coffer coffer,
        Settlement settlement
    ){
        for(Map.Entry<SlotId,Long> line:
                settlement.lines.entrySet()){
            Slot slot=
                coffer.slots.get(
                    line.getKey()
                );

            if(slot==null)
                throw new IllegalStateException(
                    "reserved coffer slot disappeared "+
                    line.getKey()
                );

            long amount=
                line.getValue();

            if(amount<=0L||
               slot.reserved<amount||
               slot.amount<amount)
                throw new IllegalStateException(
                    "coffer reservation drift slot="+
                    slot.id+
                    " amount="+slot.amount+
                    " reserved="+slot.reserved+
                    " expected="+amount
                );
        }
    }

    private SlotId nextSlotId(){
        long value=
            slotSequence.incrementAndGet();

        if(value<=0L)
            throw new IllegalStateException(
                "coffer slot sequence exhausted"
            );

        return new SlotId(value);
    }

    private SettlementId nextSettlementId(){
        long value=
            settlementSequence
                .incrementAndGet();

        if(value<=0L)
            throw new IllegalStateException(
                "coffer settlement sequence exhausted"
            );

        return new SettlementId(value);
    }

    private static String normalizeOwner(
        String ownerRef
    ){
        if(ownerRef==null)
            throw new NullPointerException(
                "ownerRef"
            );

        String normalized=
            ownerRef.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "ownerRef blank"
            );

        return normalized;
    }
}
