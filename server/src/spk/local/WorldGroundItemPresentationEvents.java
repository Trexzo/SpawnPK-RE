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
        AMOUNT
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
            GroundItem item,
            int oldAmount,
            EntityId recipientId,
            long recipientGeneration
        ){
            this.sequence=sequence;
            this.createdAt=createdAt;
            this.kind=Objects.requireNonNull(kind,"kind");
            this.groundItemId=item.id;
            this.itemId=item.itemId;
            this.tile=item.tile;
            this.oldAmount=oldAmount;
            this.newAmount=item.amount;
            this.owner=item.owner;
            this.spawnedTick=item.spawnedTick;
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
        GroundItem item,
        WorldPlayer recipient,
        long generation
    ){
        return enqueue(
            now,
            Kind.SPAWN,
            item,
            -1,
            recipient,
            generation
        );
    }

    synchronized boolean enqueueAmount(
        long now,
        GroundItem item,
        int oldAmount,
        WorldPlayer recipient,
        long generation
    ){
        if(oldAmount<=0||
           oldAmount>=item.amount)
            throw new IllegalArgumentException(
                "oldAmount="+
                oldAmount+
                " newAmount="+
                item.amount
            );

        return enqueue(
            now,
            Kind.AMOUNT,
            item,
            oldAmount,
            recipient,
            generation
        );
    }

    private boolean enqueue(
        long now,
        Kind kind,
        GroundItem item,
        int oldAmount,
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
                oldAmount,
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
