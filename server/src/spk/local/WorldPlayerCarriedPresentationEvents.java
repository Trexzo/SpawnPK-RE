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

        private Event(
            long sequence,
            long createdAt,
            EntityId recipientId,
            long recipientGeneration,
            long deathSequence,
            int[] inventoryItems,
            int[] inventoryQuantities,
            int[] equipmentItems,
            int[] equipmentQuantities,
            int[] appearanceItems
        ){
            this.sequence=sequence;
            this.createdAt=createdAt;
            this.recipientId=recipientId;
            this.recipientGeneration=recipientGeneration;
            this.deathSequence=deathSequence;
            this.inventoryItems=inventoryItems.clone();
            this.inventoryQuantities=inventoryQuantities.clone();
            this.equipmentItems=equipmentItems.clone();
            this.equipmentQuantities=equipmentQuantities.clone();
            this.appearanceItems=appearanceItems.clone();
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

    boolean enqueueDeathPostimage(
        long now,
        WorldPlayer recipient,
        long generation,
        long deathSequence
    ){
        WorldPlayer checked=
            Objects.requireNonNull(
                recipient,
                "recipient"
            );

        if(deathSequence<=0L)
            throw new IllegalArgumentException(
                "deathSequence="+deathSequence
            );

        final Event snapshot;
        synchronized(checked.mutationLock()){
            if(checked.generation()!=generation)
                return false;

            snapshot=
                new Event(
                    -1L,
                    now,
                    checked,
                    generation,
                    deathSequence
                );
        }

        return enqueueSnapshot(
            snapshot
        );
    }

    private synchronized boolean enqueueSnapshot(
        Event snapshot
    ){
        if(closed)
            return false;

        LastDeath last=
            lastDeaths.get(
                snapshot.recipientId
            );

        if(last!=null&&
           last.generation==snapshot.recipientGeneration&&
           last.deathSequence==snapshot.deathSequence)
            return false;

        if(last!=null&&
           last.generation==snapshot.recipientGeneration&&
           last.deathSequence>snapshot.deathSequence)
            throw new IllegalStateException(
                "carried presentation death sequence regressed player="+
                snapshot.recipientId+
                " last="+last.deathSequence+
                " next="+snapshot.deathSequence
            );

        Event committed=
            new Event(
                ++sequence,
                snapshot.createdAt,
                snapshot.recipientId,
                snapshot.recipientGeneration,
                snapshot.deathSequence,
                snapshot.inventoryItems,
                snapshot.inventoryQuantities,
                snapshot.equipmentItems,
                snapshot.equipmentQuantities,
                snapshot.appearanceItems
            );

        events.addLast(committed);

        lastDeaths.put(
            committed.recipientId,
            new LastDeath(
                committed.recipientGeneration,
                committed.deathSequence
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
            return true;
        }

        return false;
    }

    synchronized boolean hasPendingForDeath(
        EntityId recipientId,
        long generation,
        long deathSequence
    ){
        if(closed||recipientId==null)
            return false;

        for(Event event:events)
            if(event.recipientId.equals(recipientId)&&
               event.recipientGeneration==generation&&
               event.deathSequence==deathSequence)
                return true;

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
}
