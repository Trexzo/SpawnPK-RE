package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Protocol-independent exact-current Looting Bag state.
 *
 * Exact v308 proves a 28-slot bag plus per-stack and bulk deposit-to-bank
 * intents. Item admission, Wilderness/death rules, actual bank mutation,
 * persistence and anti-abuse remain external authority.
 */
final class LootingBagService {
    static final int CAPACITY=28;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum SettlementState {
        RESERVED,
        SETTLED,
        CANCELLED
    }

    static final class StackSpec {
        final int itemId;
        final long amount;

        StackSpec(
            int itemId,
            long amount
        ){
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId="+itemId
                );

            if(amount<=0L)
                throw new IllegalArgumentException(
                    "amount="+amount
                );

            this.itemId=itemId;
            this.amount=amount;
        }

        String fingerprint(){
            return itemId+"x"+amount;
        }
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
            return "looting-bag-slot-"+
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
            return "looting-bag-settlement-"+
                Long.toUnsignedString(value);
        }
    }

    static final class SlotSnapshot {
        final SlotId slotId;
        final int itemId;
        final long amount;
        final long reserved;
        final long available;
        final String sourceAuthority;
        final String receiptKey;

        SlotSnapshot(Slot slot){
            this.slotId=slot.id;
            this.itemId=slot.itemId;
            this.amount=slot.amount;
            this.reserved=slot.reserved;
            this.available=
                slot.amount-
                    slot.reserved;
            this.sourceAuthority=
                slot.sourceAuthority;
            this.receiptKey=
                slot.receiptKey;
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
        final SettlementState state;
        final List<SettlementLineSnapshot> lines;

        SettlementSnapshot(
            Settlement settlement
        ){
            this.settlementId=
                settlement.id;
            this.ownerRef=
                settlement.ownerRef;
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
    }

    static final class Snapshot {
        final String ownerRef;
        final int capacity;
        final List<SlotSnapshot> slots;
        final List<SettlementSnapshot> settlements;
        final String presentationAuthority;

        Snapshot(
            Bag bag,
            Collection<Settlement> settlements
        ){
            this.ownerRef=
                bag.ownerRef;
            this.capacity=CAPACITY;

            ArrayList<SlotSnapshot>
                slotSnapshots=
                    new ArrayList<>();

            for(Slot slot:
                    bag.slots.values())
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
                if(bag.ownerRef.equals(
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

    static final class DepositResult {
        final boolean changed;
        final Snapshot bag;

        DepositResult(
            boolean changed,
            Snapshot bag
        ){
            this.changed=changed;
            this.bag=
                Objects.requireNonNull(
                    bag,
                    "bag"
                );
        }
    }

    private static final class Receipt {
        final String key;
        final String fingerprint;

        Receipt(
            String key,
            String fingerprint
        ){
            this.key=key;
            this.fingerprint=
                fingerprint;
        }
    }

    private static final class Slot {
        final SlotId id;
        final int itemId;
        final String sourceAuthority;
        final String receiptKey;

        long amount;
        long reserved;

        Slot(
            SlotId id,
            StackSpec stack,
            String sourceAuthority,
            String receiptKey
        ){
            this.id=id;
            this.itemId=stack.itemId;
            this.amount=stack.amount;
            this.sourceAuthority=
                sourceAuthority;
            this.receiptKey=
                receiptKey;
        }
    }

    private static final class Bag {
        final String ownerRef;

        final LinkedHashMap<SlotId,Slot>
            slots=
                new LinkedHashMap<>();

        final HashMap<String,Receipt>
            receipts=
                new HashMap<>();

        Bag(String ownerRef){
            this.ownerRef=ownerRef;
        }
    }

    private static final class Settlement {
        final SettlementId id;
        final String ownerRef;
        final LinkedHashMap<SlotId,Long>
            lines=
                new LinkedHashMap<>();

        SettlementState state=
            SettlementState.RESERVED;

        Settlement(
            SettlementId id,
            String ownerRef
        ){
            this.id=id;
            this.ownerRef=ownerRef;
        }
    }

    private final AtomicLong slotSequence=
        new AtomicLong();

    private final AtomicLong settlementSequence=
        new AtomicLong();

    private final LinkedHashMap<String,Bag>
        bags=
            new LinkedHashMap<>();

    private final LinkedHashMap<SettlementId,Settlement>
        settlements=
            new LinkedHashMap<>();

    /**
     * Call only after the external source-side item removal/deposit operation
     * has succeeded. This service intentionally does not decide how items are
     * admitted into the Looting Bag.
     */
    synchronized DepositResult confirmExternalDeposit(
        String ownerRef,
        String receiptKey,
        Collection<StackSpec> stacks,
        String sourceAuthority
    ){
        String owner=
            normalizeOwner(
                ownerRef
            );
        String receipt=
            normalizeKey(
                receiptKey,
                "receiptKey"
            );
        String authority=
            requireText(
                sourceAuthority,
                "sourceAuthority"
            );

        Objects.requireNonNull(
            stacks,
            "stacks"
        );

        if(stacks.isEmpty())
            throw new IllegalArgumentException(
                "Looting Bag deposit stacks empty"
            );

        ArrayList<StackSpec> checked=
            new ArrayList<>();

        for(StackSpec stack:stacks)
            checked.add(
                Objects.requireNonNull(
                    stack,
                    "stack"
                )
            );

        String fingerprint=
            fingerprint(
                checked,
                authority
            );

        Bag existing=
            bags.get(owner);

        if(existing!=null){
            Receipt prior=
                existing.receipts.get(
                    receipt
                );

            if(prior!=null){
                if(!prior.fingerprint.equals(
                        fingerprint))
                    throw new IllegalStateException(
                        "Looting Bag receipt replay mismatch "+
                        receipt+
                        " owner="+owner
                    );

                return new DepositResult(
                    false,
                    snapshotOf(existing)
                );
            }
        }

        int current=
            existing==null
                ?0
                :existing.slots.size();

        if(checked.size()>
                CAPACITY-current)
            throw new IllegalStateException(
                "Looting Bag capacity exceeded owner="+
                owner+
                " current="+current+
                " incoming="+checked.size()+
                " capacity="+CAPACITY
            );

        ArrayList<Slot> pending=
            new ArrayList<>();

        for(StackSpec stack:checked)
            pending.add(
                new Slot(
                    nextSlotId(),
                    stack,
                    authority,
                    receipt
                )
            );

        Bag bag=
            existing;

        if(bag==null){
            bag=new Bag(owner);
            bags.put(
                owner,
                bag
            );
        }

        bag.receipts.put(
            receipt,
            new Receipt(
                receipt,
                fingerprint
            )
        );

        // Explicit LocalLab policy: no implicit stack merge.
        for(Slot slot:pending)
            bag.slots.put(
                slot.id,
                slot
            );

        return new DepositResult(
            true,
            snapshotOf(bag)
        );
    }

    synchronized SettlementSnapshot beginBankDeposit(
        String ownerRef,
        SlotId slotId,
        long amount
    ){
        if(amount<=0L)
            throw new IllegalArgumentException(
                "amount="+amount
            );

        Bag bag=
            requireBag(
                ownerRef
            );

        Slot slot=
            requireSlot(
                bag,
                slotId
            );

        long available=
            slot.amount-
                slot.reserved;

        if(amount>available)
            throw new IllegalStateException(
                "Looting Bag amount unavailable slot="+
                slot.id+
                " requested="+amount+
                " available="+available
            );

        Settlement settlement=
            new Settlement(
                nextSettlementId(),
                bag.ownerRef
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

    synchronized SettlementSnapshot beginBankDepositAll(
        String ownerRef
    ){
        Bag bag=
            requireBag(
                ownerRef
            );

        if(bag.slots.isEmpty())
            throw new IllegalStateException(
                "Looting Bag empty owner="+
                bag.ownerRef
            );

        for(Slot slot:
                bag.slots.values())
            if(slot.reserved!=0L)
                throw new IllegalStateException(
                    "Looting Bag bulk bank deposit requires no active reservation owner="+
                    bag.ownerRef
                );

        Settlement settlement=
            new Settlement(
                nextSettlementId(),
                bag.ownerRef
            );

        for(Slot slot:
                bag.slots.values())
            settlement.lines.put(
                slot.id,
                slot.amount
            );

        for(Map.Entry<SlotId,Long> line:
                settlement.lines.entrySet()){
            Slot slot=
                bag.slots.get(
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
     * Confirm only after external bank mutation succeeds.
     */
    synchronized boolean confirmBankDeposit(
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
                "cancelled Looting Bag settlement cannot confirm "+
                settlement.id
            );

        Bag bag=
            requireBag(
                settlement.ownerRef
            );

        preflightReservation(
            bag,
            settlement
        );

        ArrayList<SlotId> emptied=
            new ArrayList<>();

        for(Map.Entry<SlotId,Long> line:
                settlement.lines.entrySet()){
            Slot slot=
                bag.slots.get(
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
            bag.slots.remove(
                slotId
            );

        settlement.state=
            SettlementState.SETTLED;

        return true;
    }

    synchronized boolean cancelBankDeposit(
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
                "settled Looting Bag settlement cannot cancel "+
                settlement.id
            );

        Bag bag=
            requireBag(
                settlement.ownerRef
            );

        preflightReservation(
            bag,
            settlement
        );

        for(Map.Entry<SlotId,Long> line:
                settlement.lines.entrySet()){
            Slot slot=
                bag.slots.get(
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
        Bag bag=
            bags.get(
                normalizeOwner(
                    ownerRef
                )
            );

        return bag==null
            ?null
            :snapshotOf(bag);
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
        return bags.size();
    }

    private Snapshot snapshotOf(
        Bag bag
    ){
        return new Snapshot(
            bag,
            settlements.values()
        );
    }

    private Bag requireBag(
        String ownerRef
    ){
        String owner=
            normalizeOwner(
                ownerRef
            );

        Bag bag=
            bags.get(owner);

        if(bag==null)
            throw new IllegalArgumentException(
                "unknown Looting Bag owner="+
                owner
            );

        return bag;
    }

    private static Slot requireSlot(
        Bag bag,
        SlotId slotId
    ){
        SlotId id=
            Objects.requireNonNull(
                slotId,
                "slotId"
            );

        Slot slot=
            bag.slots.get(id);

        if(slot==null)
            throw new IllegalArgumentException(
                "unknown Looting Bag slot "+
                id+
                " owner="+bag.ownerRef
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
                "unknown Looting Bag settlement "+
                id
            );

        return settlement;
    }

    private static void preflightReservation(
        Bag bag,
        Settlement settlement
    ){
        for(Map.Entry<SlotId,Long> line:
                settlement.lines.entrySet()){
            Slot slot=
                bag.slots.get(
                    line.getKey()
                );

            if(slot==null)
                throw new IllegalStateException(
                    "reserved Looting Bag slot disappeared "+
                    line.getKey()
                );

            long amount=
                line.getValue();

            if(amount<=0L||
               slot.reserved<amount||
               slot.amount<amount)
                throw new IllegalStateException(
                    "Looting Bag reservation drift slot="+
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
                "Looting Bag slot sequence exhausted"
            );

        return new SlotId(value);
    }

    private SettlementId nextSettlementId(){
        long value=
            settlementSequence
                .incrementAndGet();

        if(value<=0L)
            throw new IllegalStateException(
                "Looting Bag settlement sequence exhausted"
            );

        return new SettlementId(value);
    }

    private static String fingerprint(
        Collection<StackSpec> stacks,
        String authority
    ){
        StringBuilder out=
            new StringBuilder(
                authority
            );

        for(StackSpec stack:stacks)
            out.append('|')
                .append(
                    stack.fingerprint()
                );

        return out.toString();
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

    private static String normalizeKey(
        String value,
        String field
    ){
        String normalized=
            requireText(
                value,
                field
            ).toLowerCase(
                Locale.ROOT
            );

        for(int i=0;i<
                normalized.length();i++){
            char c=
                normalized.charAt(i);

            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||
                c=='_'||
                c=='-'||
                c==':';

            if(!ok)
                throw new IllegalArgumentException(
                    field+" invalid="+
                    value
                );
        }

        return normalized;
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(
                field
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }
}
