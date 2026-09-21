package spk.local;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorldCloseScheduledWorkFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );

        WorldPlayer player=
            new WorldPlayer();

        AtomicInteger eventRuns=
            new AtomicInteger();
        AtomicInteger realtimeRuns=
            new AtomicInteger();

        try{
            world.registerPlayer(
                player,
                "world-close-fence"
            );

            WorldEventQueue.Handle handle=
                world.events().schedule(
                    1L,
                    eventRuns::incrementAndGet
                );

            world.realtime().schedule(
                0L,
                player,
                realtimeRuns::incrementAndGet
            );

            if(world.events().size()!=1||
               world.realtime().size()!=1)
                throw new AssertionError(
                    "scheduled fixtures missing events="+
                    world.events().size()+
                    " realtime="+
                    world.realtime().size()
                );

            world.close();

            if(world.events().size()!=0||
               world.realtime().size()!=0)
                throw new AssertionError(
                    "world close retained scheduled work events="+
                    world.events().size()+
                    " realtime="+
                    world.realtime().size()
                );

            if(!handle.cancelled())
                throw new AssertionError(
                    "dropped event handle not cancelled"
                );

            if(handleOwner(handle)!=null)
                throw new AssertionError(
                    "dropped event handle retained queue owner"
                );

            if(world.events().runDue(
                    Long.MAX_VALUE)!=0)
                throw new AssertionError(
                    "closed event queue executed work"
                );

            if(world.realtime().runDue(
                    Long.MAX_VALUE)!=0)
                throw new AssertionError(
                    "closed realtime queue executed work"
                );

            if(eventRuns.get()!=0||
               realtimeRuns.get()!=0)
                throw new AssertionError(
                    "post-close task execution event="+
                    eventRuns.get()+
                    " realtime="+
                    realtimeRuns.get()
                );

            assertRejected(
                ()->world.events().schedule(
                    2L,
                    eventRuns::incrementAndGet
                ),
                "post-close event schedule"
            );

            assertRejected(
                ()->world.realtime().schedule(
                    1L,
                    player,
                    realtimeRuns::incrementAndGet
                ),
                "post-close realtime schedule"
            );

            assertRejected(
                world::start,
                "post-close World.start"
            );

            assertRejected(
                ()->world.observePulse(
                    System.currentTimeMillis()
                ),
                "post-close observePulse"
            );

            world.close();

            if(eventRuns.get()!=0||
               realtimeRuns.get()!=0)
                throw new AssertionError(
                    "idempotent close executed work"
                );

            System.out.println(
                "WORLD_CLOSE_SCHEDULED_WORK_FENCE_PASS "+
                "eventsCleared=true "+
                "realtimeCleared=true "+
                "handleCancelled=true "+
                "scheduleRejected=true "+
                "restartRejected=true "+
                "observeRejected=true "+
                "idempotent=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );

            world.close();
        }
    }

    private static Object handleOwner(
        WorldEventQueue.Handle handle
    )throws Exception{
        Field owner=
            WorldEventQueue.Handle.class
                .getDeclaredField(
                    "owner"
                );

        owner.setAccessible(true);
        return owner.get(handle);
    }

    private static void assertRejected(
        Runnable action,
        String label
    ){
        boolean rejected=false;

        try{
            action.run();
        }catch(IllegalStateException expected){
            rejected=true;
        }

        if(!rejected)
            throw new AssertionError(
                label+" was accepted"
            );
    }

    private WorldCloseScheduledWorkFenceTest(){}
}
