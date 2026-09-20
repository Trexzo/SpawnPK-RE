package spk.local;

import java.util.concurrent.atomic.AtomicInteger;

public final class WorldRealtimeQueueLifecycleFenceTest {
    public static void main(String[] args){
        World world=
            World.isolatedForTest(
                50L
            );

        WorldPlayer player=
            new WorldPlayer();

        AtomicInteger ran=
            new AtomicInteger();

        try{
            assertRejected(
                ()->world.realtime()
                    .schedule(
                        1_000L,
                        player,
                        ran::incrementAndGet
                    )
            );

            if(world.realtime().size()!=0)
                throw new AssertionError(
                    "unregistered schedule grew queue"
                );

            long firstGeneration=
                world.registerPlayer(
                    player,
                    "realtime-owner"
                );

            world.realtime().schedule(
                10_000L,
                player,
                ran::incrementAndGet
            );

            if(world.realtime().size()!=1)
                throw new AssertionError(
                    "registered schedule missing"
                );

            if(!world.unregisterPlayer(player))
                throw new AssertionError(
                    "unregister failed"
                );

            if(world.realtime().size()!=0)
                throw new AssertionError(
                    "logout left realtime task queued"
                );

            assertRejected(
                ()->world.realtime()
                    .schedule(
                        20_000L,
                        player,
                        ran::incrementAndGet
                    )
            );

            if(world.realtime().size()!=0)
                throw new AssertionError(
                    "post-logout schedule reinserted work"
                );

            long secondGeneration=
                world.registerPlayer(
                    player,
                    "realtime-owner"
                );

            if(secondGeneration<=firstGeneration)
                throw new AssertionError(
                    "generation did not advance"
                );

            world.realtime().schedule(
                0L,
                player,
                ran::incrementAndGet
            );

            if(world.realtime().runDue(
                    Long.MAX_VALUE)!=1)
                throw new AssertionError(
                    "fresh generation task did not drain"
                );

            if(ran.get()!=1)
                throw new AssertionError(
                    "unexpected realtime execution count="+
                    ran.get()
                );

            System.out.println(
                "WORLD_REALTIME_QUEUE_LIFECYCLE_FENCE_PASS "+
                "unregisteredRejected=true "+
                "logoutCancelled=true "+
                "postLogoutRejected=true "+
                "reregisterFreshGeneration=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );

            world.close();
        }
    }

    private static void assertRejected(
        Runnable action
    ){
        boolean rejected=false;

        try{
            action.run();
        }catch(IllegalStateException expected){
            rejected=true;
        }

        if(!rejected)
            throw new AssertionError(
                "unregistered realtime schedule accepted"
            );
    }
}