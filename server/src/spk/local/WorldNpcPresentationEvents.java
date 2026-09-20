package spk.local;

import java.util.*;

/**
 * World-owned semantic NPC presentation event queue.
 *
 * This owns cross-viewer event identity, recipient state, duplicate suppression
 * and expiry. Viewer packet scene translation remains in SharedNpcWorldRelay.
 */
final class WorldNpcPresentationEvents {
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
        final HashSet<EntityId> delivered=
            new HashSet<>();

        Event(
            long sequence,
            long createdAt,
            EntityId sourceId,
            Target target,
            NpcSyncEncoder.Mask mask,
            long playerBarrierSequence,
            LinkedHashSet<EntityId> recipients
        ){
            this.sequence=sequence;
            this.createdAt=createdAt;
            this.sourceId=sourceId;
            this.target=target;
            this.mask=mask;
            this.playerBarrierSequence=
                playerBarrierSequence;
            this.recipients=recipients;
        }
    }

    private final ArrayDeque<Event> events=
        new ArrayDeque<>();
    private long sequence;

    synchronized boolean enqueue(
        long now,
        EntityId sourceId,
        Target target,
        NpcSyncEncoder.Mask mask,
        long playerBarrierSequence,
        Collection<EntityId> recipients
    ){
        if(sourceId==null||
           target==null||
           mask==null)
            throw new NullPointerException(
                "sourceId/target/mask"
            );

        LinkedHashSet<EntityId> unique=
            new LinkedHashSet<>();

        if(recipients!=null)
            for(EntityId recipient:recipients)
                if(recipient!=null&&
                   !recipient.equals(sourceId))
                    unique.add(recipient);

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
           sameMask(last.mask,mask))
            return false;

        events.addLast(
            new Event(
                ++sequence,
                now,
                sourceId,
                target,
                mask,
                playerBarrierSequence,
                unique
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
        if(viewerId==null)
            return Collections.emptyList();

        pruneExpired(now);

        ArrayList<Event> out=new ArrayList<>();
        for(Event event:events)
            if(event.recipients.contains(viewerId)&&
               !event.delivered.contains(viewerId))
                out.add(event);

        return Collections.unmodifiableList(out);
    }

    synchronized void markDelivered(
        long eventSequence,
        EntityId viewerId,
        long now
    ){
        if(viewerId==null)return;

        for(Event event:events)
            if(event.sequence==eventSequence){
                event.delivered.add(viewerId);
                break;
            }

        pruneDelivered(now);
    }

    synchronized void retainRecipients(
        Collection<EntityId> liveRecipients,
        long now
    ){
        HashSet<EntityId> live=
            liveRecipients==null
                ?new HashSet<>()
                :new HashSet<>(liveRecipients);

        for(Event event:events)
            event.recipients.retainAll(live);

        pruneDelivered(now);
    }

    synchronized int size(){
        return events.size();
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
