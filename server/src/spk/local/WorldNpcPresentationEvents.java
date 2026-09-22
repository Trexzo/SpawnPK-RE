package spk.local;

import java.util.*;

/**
 * World-owned semantic NPC presentation event queue.
 *
 * This owns cross-viewer event identity, recipient state, duplicate suppression
 * and expiry. Viewer packet scene translation remains in SharedNpcWorldRelay.
 */
final class WorldNpcPresentationEvents
    implements AutoCloseable {
    static final long EVENT_TTL_MS=5000L;

    static final class Target {
        static final int SCENE=0;
        static final int PET=1;
        static final int MINI=2;
        static final int CANONICAL=3;

        final int kind;
        final int scene;
        final int definition;
        final EntityId canonicalId;

        private Target(
            int kind,
            int scene,
            int definition,
            EntityId canonicalId
        ){
            this.kind=kind;
            this.scene=scene;
            this.definition=definition;
            this.canonicalId=canonicalId;
        }

        static Target scene(
            int scene,
            int definition
        ){
            return new Target(
                SCENE,
                scene,
                definition,
                null
            );
        }

        static Target pet(
            EntityId canonicalId,
            int definition
        ){
            return new Target(
                PET,
                -1,
                definition,
                canonicalId
            );
        }

        static Target mini(
            EntityId canonicalId,
            int definition
        ){
            return new Target(
                MINI,
                -1,
                definition,
                canonicalId
            );
        }

        static Target canonical(
            EntityId canonicalId,
            int definition
        ){
            if(canonicalId==null)
                throw new NullPointerException(
                    "canonicalId"
                );
            return new Target(
                CANONICAL,
                -1,
                definition,
                canonicalId
            );
        }
    }

    static final class Event {
        final long sequence;
        final long createdAt;
        final long playerBarrierSequence;
        final EntityId sourceId;
        final Target target;
        final NpcSyncEncoder.Mask mask;
        final LinkedHashSet<EntityId> recipients;
        final HashMap<EntityId,Long>
            recipientGenerations;
        final HashSet<EntityId> delivered=
            new HashSet<>();

        Event(
            long sequence,
            long createdAt,
            EntityId sourceId,
            Target target,
            NpcSyncEncoder.Mask mask,
            long playerBarrierSequence,
            LinkedHashSet<EntityId> recipients,
            HashMap<EntityId,Long> recipientGenerations
        ){
            this.sequence=sequence;
            this.createdAt=createdAt;
            this.sourceId=sourceId;
            this.target=target;
            this.mask=mask;
            this.playerBarrierSequence=
                playerBarrierSequence;
            this.recipients=recipients;
            this.recipientGenerations=
                recipientGenerations;
        }
    }

    private final ArrayDeque<Event> events=
        new ArrayDeque<>();
    private long sequence;
    private boolean closed;

    synchronized boolean enqueue(
        long now,
        EntityId sourceId,
        Target target,
        NpcSyncEncoder.Mask mask,
        long playerBarrierSequence,
        Collection<EntityId> recipients
    ){
        LinkedHashMap<EntityId,Long> owned=
            new LinkedHashMap<>();

        if(recipients!=null)
            for(EntityId recipient:recipients)
                if(recipient!=null)
                    owned.put(
                        recipient,
                        -1L
                    );

        return enqueueOwned(
            now,
            sourceId,
            target,
            mask,
            playerBarrierSequence,
            owned
        );
    }

    synchronized boolean enqueueOwned(
        long now,
        EntityId sourceId,
        Target target,
        NpcSyncEncoder.Mask mask,
        long playerBarrierSequence,
        Map<EntityId,Long> recipients
    ){
        if(closed)
            return false;

        if(sourceId==null||
           target==null||
           mask==null)
            throw new NullPointerException(
                "sourceId/target/mask"
            );

        LinkedHashSet<EntityId> unique=
            new LinkedHashSet<>();
        HashMap<EntityId,Long> generations=
            new HashMap<>();

        if(recipients!=null)
            for(Map.Entry<EntityId,Long> entry:
                    recipients.entrySet()){
                EntityId recipient=
                    entry.getKey();
                Long generation=
                    entry.getValue();

                if(recipient==null||
                   recipient.equals(sourceId))
                    continue;

                unique.add(recipient);
                generations.put(
                    recipient,
                    generation==null
                        ?-1L
                        :generation.longValue()
                );
            }

        if(unique.isEmpty())
            return false;

        pruneExpired(now);

        Event last=events.peekLast();
        if(last!=null&&
           now-last.createdAt<=100L&&
           last.playerBarrierSequence==
                playerBarrierSequence&&
           last.sourceId.equals(sourceId)&&
           sameTarget(last.target,target)&&
           sameMask(last.mask,mask)&&
           sameRecipients(
               last,
               unique,
               generations
           ))
            return false;

        events.addLast(
            new Event(
                ++sequence,
                now,
                sourceId,
                target,
                mask,
                playerBarrierSequence,
                unique,
                generations
            )
        );

        while(events.size()>256)
            events.removeFirst();

        return true;
    }

    synchronized List<Event> pendingFor(
        EntityId viewerId,
        long now
    ){
        if(closed||viewerId==null)
            return Collections.emptyList();

        pruneExpired(now);

        ArrayList<Event> out=new ArrayList<>();
        for(Event event:events)
            if(event.recipients.contains(viewerId)&&
               !event.delivered.contains(viewerId))
                out.add(event);

        return Collections.unmodifiableList(out);
    }

    synchronized List<Event> pendingFor(
        EntityId viewerId,
        long viewerGeneration,
        long now
    ){
        if(closed||viewerId==null)
            return Collections.emptyList();

        pruneExpired(now);

        ArrayList<Event> out=
            new ArrayList<>();

        for(Event event:events){
            Long expected=
                event.recipientGenerations.get(
                    viewerId
                );

            if(expected==null)
                continue;

            if(expected.longValue()>=0L&&
               expected.longValue()!=viewerGeneration)
                continue;

            if(!event.delivered.contains(
                    viewerId))
                out.add(event);
        }

        return Collections.unmodifiableList(
            out
        );
    }

    synchronized void markDelivered(
        long eventSequence,
        EntityId viewerId,
        long now
    ){
        if(closed||viewerId==null)
            return;

        for(Event event:events)
            if(event.sequence==eventSequence){
                event.delivered.add(viewerId);
                break;
            }

        pruneDelivered(now);
    }

    synchronized int removeSource(
        EntityId sourceId,
        long now
    ){
        if(closed||sourceId==null)
            return 0;

        int removed=0;

        for(Iterator<Event> iterator=
                events.iterator();
                iterator.hasNext();){
            Event event=iterator.next();

            if(event.sourceId.equals(sourceId)){
                iterator.remove();
                removed++;
            }
        }

        pruneExpired(now);
        return removed;
    }

    synchronized void retainRecipients(
        Collection<EntityId> liveRecipients,
        long now
    ){
        if(closed)
            return;

        HashSet<EntityId> live=
            liveRecipients==null
                ?new HashSet<>()
                :new HashSet<>(liveRecipients);

        for(Event event:events){
            event.recipients.retainAll(live);
            event.recipientGenerations
                .keySet()
                .retainAll(live);
        }

        pruneDelivered(now);
    }

    synchronized void retainRecipientsOwned(
        Map<EntityId,Long> liveRecipients,
        long now
    ){
        if(closed)
            return;

        for(Event event:events){
            for(Iterator<EntityId> it=
                    event.recipients.iterator();
                    it.hasNext();){
                EntityId recipient=
                    it.next();
                Long expected=
                    event.recipientGenerations.get(
                        recipient
                    );
                Long current=
                    liveRecipients==null
                        ?null
                        :liveRecipients.get(
                            recipient
                        );

                boolean keep=
                    current!=null&&
                    (expected==null||
                     expected.longValue()<0L||
                     expected.longValue()==
                        current.longValue());

                if(!keep){
                    it.remove();
                    event.recipientGenerations
                        .remove(recipient);
                    event.delivered
                        .remove(recipient);
                }
            }
        }

        pruneDelivered(now);
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
        Iterator<Event> it=events.iterator();
        while(it.hasNext())
            if(now-it.next().createdAt>
                    EVENT_TTL_MS)
                it.remove();
    }

    private void pruneDelivered(long now){
        Iterator<Event> it=events.iterator();
        while(it.hasNext()){
            Event event=it.next();
            if(now-event.createdAt>
                    EVENT_TTL_MS||
               event.recipients.isEmpty()||
               event.delivered.containsAll(
                   event.recipients
               ))
                it.remove();
        }
    }

    private static boolean sameRecipients(
        Event event,
        LinkedHashSet<EntityId> recipients,
        HashMap<EntityId,Long> generations
    ){
        return event.recipients.equals(
                recipients
            )&&
            event.recipientGenerations.equals(
                generations
            );
    }

    private static boolean sameTarget(
        Target a,
        Target b
    ){
        return a!=null&&b!=null&&
            a.kind==b.kind&&
            a.scene==b.scene&&
            a.definition==b.definition&&
            Objects.equals(
                a.canonicalId,
                b.canonicalId
            );
    }

    private static boolean sameMask(
        NpcSyncEncoder.Mask a,
        NpcSyncEncoder.Mask b
    ){
        if(a==b)return true;
        if(a==null||b==null)return false;

        return Objects.equals(
                a.animationId,
                b.animationId
            )&&
            a.animationDelay==b.animationDelay&&
            Objects.equals(
                a.interactionTarget,
                b.interactionTarget
            )&&
            Objects.equals(
                a.hitDamage,
                b.hitDamage
            )&&
            Objects.equals(
                a.gfxId,
                b.gfxId
            )&&
            a.gfxHeight==b.gfxHeight&&
            a.gfxDelay==b.gfxDelay&&
            Objects.equals(
                a.forceText,
                b.forceText
            )&&
            a.hitType==b.hitType&&
            a.hitCycle==b.hitCycle&&
            a.currentHp==b.currentHp&&
            a.maxHp==b.maxHp;
    }
}
