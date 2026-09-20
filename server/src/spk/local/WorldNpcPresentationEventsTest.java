package spk.local;

import java.util.*;

public final class WorldNpcPresentationEventsTest {
    public static void main(String[] args){
        WorldNpcPresentationEvents events=
            new WorldNpcPresentationEvents();

        EntityId source=EntityId.next();
        EntityId viewerA=EntityId.next();
        EntityId viewerB=EntityId.next();
        EntityId canonicalPet=EntityId.next();

        WorldNpcPresentationEvents.Target target=
            WorldNpcPresentationEvents.Target.pet(
                canonicalPet,
                8330
            );

        NpcSyncEncoder.Mask mask=
            NpcSyncEncoder.Mask.forceText("2");

        long now=1_000L;

        boolean first=events.enqueue(
            now,
            source,
            target,
            mask,
            7L,
            Arrays.asList(viewerA,viewerB)
        );

        if(!first||events.size()!=1)
            throw new AssertionError(
                "first event was not queued"
            );

        boolean duplicate=events.enqueue(
            now+20L,
            source,
            target,
            NpcSyncEncoder.Mask.forceText("2"),
            7L,
            Arrays.asList(viewerA,viewerB)
        );

        if(duplicate||events.size()!=1)
            throw new AssertionError(
                "same-tick semantic duplicate was not suppressed"
            );

        boolean newBarrier=events.enqueue(
            now+30L,
            source,
            target,
            NpcSyncEncoder.Mask.forceText("2"),
            8L,
            Arrays.asList(viewerA,viewerB)
        );

        if(!newBarrier||events.size()!=2)
            throw new AssertionError(
                "new player barrier was incorrectly deduplicated"
            );

        List<WorldNpcPresentationEvents.Event> aPending=
            events.pendingFor(
                viewerA,
                now+40L
            );

        if(aPending.size()!=2)
            throw new AssertionError(
                "viewer A pending count="+
                aPending.size()
            );

        events.markDelivered(
            aPending.get(0).sequence,
            viewerA,
            now+50L
        );

        if(events.pendingFor(
                viewerA,
                now+60L
            ).size()!=1)
            throw new AssertionError(
                "per-viewer delivery state not retained"
            );

        if(events.pendingFor(
                viewerB,
                now+60L
            ).size()!=2)
            throw new AssertionError(
                "viewer A delivery leaked to viewer B"
            );

        events.retainRecipients(
            Collections.singleton(viewerB),
            now+70L
        );

        if(events.pendingFor(
                viewerA,
                now+80L
            ).size()!=0)
            throw new AssertionError(
                "dead recipient was not pruned"
            );

        List<WorldNpcPresentationEvents.Event> bPending=
            events.pendingFor(
                viewerB,
                now+80L
            );

        for(WorldNpcPresentationEvents.Event event:
            bPending)
            events.markDelivered(
                event.sequence,
                viewerB,
                now+90L
            );

        if(events.size()!=0)
            throw new AssertionError(
                "fully delivered events not pruned"
            );

        events.enqueue(
            now+100L,
            source,
            target,
            mask,
            9L,
            Collections.singleton(viewerB)
        );

        if(!events.pendingFor(
                viewerB,
                now+100L+
                    WorldNpcPresentationEvents.EVENT_TTL_MS+
                    1L
            ).isEmpty())
            throw new AssertionError(
                "expired event remained pending"
            );

        if(events.size()!=0)
            throw new AssertionError(
                "expired event remained queued"
            );

        System.out.println(
            "WORLD_NPC_PRESENTATION_EVENTS_PASS "+
            "dedupe=true"+
            " barrierDistinct=true"+
            " recipientDelivery=true"+
            " deadRecipientPrune=true"+
            " ttl=true"
        );
    }
}
