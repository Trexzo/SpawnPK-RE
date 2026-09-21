package spk.local;

import java.util.*;

public final class WorldNpcPresentationEventsCloseTest {
    public static void main(String[] args){
        World world=
            World.isolatedForTest(60_000L);

        WorldNpcPresentationEvents events=
            world.npcPresentationEvents();

        EntityId source=
            EntityId.next();
        EntityId viewer=
            EntityId.next();

        long now=
            System.currentTimeMillis();

        boolean queued=
            events.enqueue(
                now,
                source,
                WorldNpcPresentationEvents
                    .Target.scene(
                        129,
                        1488
                    ),
                NpcSyncEncoder.Mask
                    .forceText(
                        "terminal-fixture"
                    ),
                0L,
                Collections.singleton(
                    viewer
                )
            );

        if(!queued||
           events.size()!=1)
            throw new AssertionError(
                "pre-close event not queued"
            );

        if(events.closed())
            throw new AssertionError(
                "event queue terminal before World close"
            );

        world.close();

        if(!events.closed())
            throw new AssertionError(
                "World close did not close presentation events"
            );

        if(events.size()!=0)
            throw new AssertionError(
                "World close retained presentation events="+
                events.size()
            );

        boolean postCloseQueued=
            events.enqueue(
                now+1L,
                source,
                WorldNpcPresentationEvents
                    .Target.scene(
                        129,
                        1488
                    ),
                NpcSyncEncoder.Mask
                    .forceText(
                        "late"
                    ),
                0L,
                Collections.singleton(
                    viewer
                )
            );

        if(postCloseQueued||
           events.size()!=0)
            throw new AssertionError(
                "closed presentation queue accepted enqueue"
            );

        if(!events.pendingFor(
                viewer,
                now+2L
            ).isEmpty())
            throw new AssertionError(
                "closed presentation queue exposed pending work"
            );

        events.markDelivered(
            1L,
            viewer,
            now+3L
        );

        events.retainRecipients(
            Collections.singleton(
                viewer
            ),
            now+4L
        );

        if(events.removeSource(
                source,
                now+5L
            )!=0)
            throw new AssertionError(
                "closed presentation cleanup reported mutation"
            );

        world.close();

        if(!events.closed()||
           events.size()!=0)
            throw new AssertionError(
                "repeated World close changed terminal state"
            );

        System.out.println(
            "WORLD_NPC_PRESENTATION_EVENTS_CLOSE_PASS "+
            "cleared=true "+
            "enqueueRejected=true "+
            "cleanupSafe=true "+
            "repeatedClose=true"
        );
    }

    private WorldNpcPresentationEventsCloseTest(){}
}
