package spk.local;

import java.util.*;

/**
 * World-owned exact-generation carried-state presentation events.
 *
 * Gameplay authority remains in BankState/EquipmentState. Events retain one
 * immutable postimage so presentation can be retried without reading a later
 * carried state.
 */
final class WorldPlayerCarriedPresentationEvents
    implements AutoCloseable {
    static final long EVENT_TTL_MS=10000L;

    static final class Event {
        final long sequence;
        final long createdAt;
        final EntityId recipientId;
        final long recipientGeneration;
        final long deathSequence;
        final int[] inventoryItems;
        final int[] inventoryQuantities;
        final int[] equipmentItems;
        final int[] equipmentQuantities;
        final int[] appearanceItems;

        private Event(
            long sequence,
            long createdAt,
            WorldPlayer recipient,
            long recipientGeneration,
            long deathSequence
        ){
            this.sequence=sequence;
            this.createdAt=createdAt;
            this.recipientId=recipient.id();
            this.recipientGeneration=recipientGeneration;
            this.deathSequence=deathSequence;

            this.inventoryItems=
                new int[BankState.INVENTORY_CAPACITY];
            this.inventoryQuantities=
                new int[BankState.INVENTORY_CAPACITY];
            Arrays.fill(
                this.inventoryItems,
                -1
            );

            BankState bank=recipient.bank();
            for(int slot=0;
                slot<BankState.INVENTORY_CAPACITY;
                slot++){
                BankState.Stack stack=
                    bank.inventoryAt(slot);
                if(stack==null)
                    continue;

                inventoryItems[slot]=stack.itemId;
                inventoryQuantities[slot]=stack.qty;
            }

            this.equipmentItems=
                recipient.equipment()
                    .containerItems();
            this.equipmentQuantities=
                recipient.equipment()
                    .containerQuantities();
            this.appearanceItems=
                recipient.equipment()
                    .appearanceItems();
        }
    }

    private static final class LastDeath {
        final long generation;
        final long deathSequence;

        LastDeath(
            long generation,
            long deathSequence
        ){
            this.generation=generation;
            this.deathSequence=deathSequence;
        }
    }

    private final ArrayDeque<Event> events=
        new ArrayDeque<>();
    private final HashMap<EntityId,LastDeath> lastDeaths=
        new HashMap<>();
    private long sequence;
    private boolean closed;

    synchronized boolean enqueueDeathPostimage(
        long now,
        WorldPlayer recipient,
        long generation,
        long deathSequence
    ){
        if(closed)
            return false;

        WorldPlayer checked=
            Objects.requireNonNull(
                recipient,
                "recipient"
            );

        if(deathSequence<=0L)
            throw new IllegalArgumentException(
                "deathSequence="+deathSequence
            );

        pruneExpired(now);

        LastDeath last=
            lastDeaths.get(
                checked.id()
            );

        if(last!=null&&
           last.generation==generation&&
           last.deathSequence==deathSequence)
            return false;

        if(last!=null&&
           last.generation==generation&&
           last.deathSequence>deathSequence)
            throw new IllegalStateException(
                "carried presentation death sequence regressed player="+
                checked.id()+
                " last="+last.deathSequence+
                " next="+deathSequence
            );

        events.addLast(
            new Event(
                ++sequence,
                now,
                checked,
                generation,
                deathSequence
            )
        );

        lastDeaths.put(
            checked.id(),
            new LastDeath(
                generation,
                deathSequence
            )
        );

        while(events.size()>256)
            events.removeFirst();

        return true;
    }

    synchronized List<Event> pendingFor(
        EntityId recipientId,
        long generation,
        long now
    ){
        if(closed||recipientId==null)
            return Collections.emptyList();

        pruneExpired(now);

        ArrayList<Event> out=
            new ArrayList<>();

        for(Event event:events)
            if(event.recipientId.equals(
                    recipientId
                )&&
               event.recipientGeneration==generation)
                out.add(event);

        return Collections.unmodifiableList(
            out
        );
    }

    synchronized boolean markDelivered(
        long eventSequence,
        EntityId recipientId,
        long generation,
        long now
    ){
        if(closed||recipientId==null)
            return false;

        for(Iterator<Event> it=
                events.iterator();
                it.hasNext();){
            Event event=it.next();

            if(event.sequence!=eventSequence)
                continue;

            if(!event.recipientId.equals(
                    recipientId
                )||
               event.recipientGeneration!=generation)
                return false;

            it.remove();
            pruneExpired(now);
            return true;
        }

        pruneExpired(now);
        return false;
    }

    synchronized int size(){
        return events.size();
    }

    synchronized boolean closed(){
        return closed;
    }

    @Override public synchronized void close(){
        if(closed)
            return;

        closed=true;
        events.clear();
        lastDeaths.clear();
    }

    private void pruneExpired(
        long now
    ){
        for(Iterator<Event> it=
                events.iterator();
                it.hasNext();)
            if(now-it.next().createdAt>
                    EVENT_TTL_MS)
                it.remove();
    }
}
