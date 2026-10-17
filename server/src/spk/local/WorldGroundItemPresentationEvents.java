package spk.local;

import java.util.*;

/**
 * World-owned owner-scoped ground-item presentation events.
 *
 * Canonical GroundItemRegistry remains gameplay authority. This queue stores
 * immutable value facts needed by one exact player generation to update its
 * already-loaded scene without retaining mutable GroundItem references.
 */
final class WorldGroundItemPresentationEvents
    implements AutoCloseable {
    static final long EVENT_TTL_MS=5000L;

    enum Kind {
        SPAWN,
        AMOUNT,
        REMOVE
    }

    static final class Event {
        final long sequence;
        final long createdAt;
        final Kind kind;
        final long groundItemId;
        final int itemId;
        final Tile tile;
        final int oldAmount;
        final int newAmount;
        final String owner;
        final long spawnedTick;
        final EntityId recipientId;
        final long recipientGeneration;

        Event(
            long sequence,
            long createdAt,
            Kind kind,
            GroundItemRegistry.BatchMutation mutation,
            EntityId recipientId,
            long recipientGeneration
        ){
            GroundItemRegistry.BatchMutation checked=
                Objects.requireNonNull(
                    mutation,
                    "mutation"
                );

            this.sequence=sequence;
            this.createdAt=createdAt;
            this.kind=Objects.requireNonNull(kind,"kind");
            this.groundItemId=checked.groundItemId;
            this.itemId=checked.itemId;
            this.tile=checked.tile;
            this.oldAmount=checked.oldAmount;
            this.newAmount=checked.newAmount;
            this.owner=checked.owner;
            this.spawnedTick=checked.spawnedTick;
            this.recipientId=Objects.requireNonNull(
                recipientId,
                "recipientId"
            );
            this.recipientGeneration=recipientGeneration;
        }

        Event(
            long sequence,
            long createdAt,
            Kind kind,
            GroundItem item,
            EntityId recipientId,
            long recipientGeneration
        ){
            GroundItem checked=
                Objects.requireNonNull(
                    item,
                    "item"
                );

            this.sequence=sequence;
            this.createdAt=createdAt;
            this.kind=Objects.requireNonNull(kind,"kind");
            this.groundItemId=checked.id;
            this.itemId=checked.itemId;
            this.tile=checked.tile;
            this.oldAmount=checked.amount;
            this.newAmount=checked.amount;
            this.owner=checked.owner;
            this.spawnedTick=checked.spawnedTick;
            this.recipientId=Objects.requireNonNull(
                recipientId,
                "recipientId"
            );
            this.recipientGeneration=recipientGeneration;
        }
    }

    private final ArrayDeque<Event> events=
        new ArrayDeque<>();
    private long sequence;
    private boolean closed;

    synchronized boolean enqueueSpawn(
        long now,
        GroundItemRegistry.BatchMutation mutation,
        WorldPlayer recipient,
        long generation
    ){
        GroundItemRegistry.BatchMutation checked=
            Objects.requireNonNull(
                mutation,
                "mutation"
            );

        if(!checked.created())
            throw new IllegalArgumentException(
                "spawn mutation has oldAmount="+
                checked.oldAmount
            );

        return enqueue(
            now,
            Kind.SPAWN,
            checked,
            recipient,
            generation
        );
    }

    synchronized boolean enqueueAmount(
        long now,
        GroundItemRegistry.BatchMutation mutation,
        WorldPlayer recipient,
        long generation
    ){
        GroundItemRegistry.BatchMutation checked=
            Objects.requireNonNull(
                mutation,
                "mutation"
            );

        if(checked.created()||
           checked.oldAmount>=checked.newAmount)
            throw new IllegalArgumentException(
                "oldAmount="+
                checked.oldAmount+
                " newAmount="+
                checked.newAmount
            );

        return enqueue(
            now,
            Kind.AMOUNT,
            checked,
            recipient,
            generation
        );
    }

    synchronized boolean enqueueSpawnSnapshot(
        long now,
        GroundItem item,
        WorldPlayer recipient,
        long generation
    ){
        return enqueueSnapshot(
            now,
            Kind.SPAWN,
            item,
            recipient,
            generation
        );
    }

    synchronized boolean enqueueRemove(
        long now,
        GroundItem item,
        WorldPlayer recipient,
        long generation
    ){
        return enqueueSnapshot(
            now,
            Kind.REMOVE,
            item,
            recipient,
            generation
        );
    }

    private boolean enqueueSnapshot(
        long now,
        Kind kind,
        GroundItem item,
        WorldPlayer recipient,
        long generation
    ){
        if(closed)
            return false;

        GroundItem checkedItem=
            Objects.requireNonNull(
                item,
                "item"
            );
        WorldPlayer checkedRecipient=
            Objects.requireNonNull(
                recipient,
                "recipient"
            );

        pruneExpired(now);

        events.addLast(
            new Event(
                ++sequence,
                now,
                kind,
                checkedItem,
                checkedRecipient.id(),
                generation
            )
        );

        while(events.size()>256)
            events.removeFirst();

        return true;
    }

    private boolean enqueue(
        long now,
        Kind kind,
        GroundItemRegistry.BatchMutation mutation,
        WorldPlayer recipient,
        long generation
    ){
        if(closed)
            return false;

        GroundItemRegistry.BatchMutation checkedMutation=
            Objects.requireNonNull(
                mutation,
                "mutation"
            );
        WorldPlayer checkedRecipient=
            Objects.requireNonNull(
                recipient,
                "recipient"
            );

        pruneExpired(now);

        events.addLast(
            new Event(
                ++sequence,
                now,
                kind,
                checkedMutation,
                checkedRecipient.id(),
                generation
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
               event.recipientGeneration==
                    generation)
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

        for(Iterator<Event> iterator=
                events.iterator();
                iterator.hasNext();){
            Event event=iterator.next();

            if(event.sequence!=eventSequence)
                continue;

            if(!event.recipientId.equals(
                    recipientId
                )||
               event.recipientGeneration!=
                    generation)
                return false;

            iterator.remove();
            pruneExpired(now);
            return true;
        }

        pruneExpired(now);
        return false;
    }

    synchronized void retainRecipients(
        Map<EntityId,Long> generations,
        long now
    ){
        if(closed)
            return;

        for(Iterator<Event> iterator=
                events.iterator();
                iterator.hasNext();){
            Event event=iterator.next();
            Long current=
                generations==null
                    ?null
                    :generations.get(
                        event.recipientId
                    );

            if(current==null||
               current.longValue()!=
                    event.recipientGeneration)
                iterator.remove();
        }

        pruneExpired(now);
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
    }

    private void pruneExpired(long now){
        for(Iterator<Event> iterator=
                events.iterator();
                iterator.hasNext();)
            if(now-iterator.next().createdAt>
                    EVENT_TTL_MS)
                iterator.remove();
    }
}
