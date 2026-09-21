package spk.local;

import java.util.concurrent.atomic.AtomicInteger;

public final class WorldRealtimeGenerationOwnershipFenceTest {
    public static void main(String[] args){
        World world=
            World.isolatedForTest(60_000L);
        WorldPlayer player=
            new WorldPlayer();
        AtomicInteger ran=
            new AtomicInteger();

        try{
            long firstGeneration=
                world.registerPlayer(
                    player,
                    "realtime-generation"
                );

            world.realtime().schedule(
                0L,
                player,
                firstGeneration,
                ran::incrementAndGet
            );

            if(world.realtime().runDue(
                    Long.MAX_VALUE)!=1)
                throw new AssertionError(
                    "first owner task did not drain"
                );

            if(ran.get()!=1)
                throw new AssertionError(
                    "first owner execution count="+
                    ran.get()
                );

            if(!world.unregisterPlayer(
                    player,
                    firstGeneration
                ))
                throw new AssertionError(
                    "first owner unregister failed"
                );

            long replacementGeneration=
                world.registerPlayer(
                    player,
                    "realtime-generation"
                );

            if(replacementGeneration<=
                    firstGeneration)
                throw new AssertionError(
                    "generation did not advance"
                );

            boolean staleRejected=false;

            try{
                world.realtime().schedule(
                    0L,
                    player,
                    firstGeneration,
                    ran::incrementAndGet
                );
            }catch(IllegalStateException expected){
                staleRejected=
                    expected.getMessage()
                        .contains(
                            "generation changed"
                        );
            }

            if(!staleRejected)
                throw new AssertionError(
                    "stale realtime owner accepted"
                );

            if(world.realtime().size()!=0)
                throw new AssertionError(
                    "stale realtime schedule grew queue"
                );

            world.realtime().schedule(
                0L,
                player,
                replacementGeneration,
                ran::incrementAndGet
            );

            if(world.realtime().runDue(
                    Long.MAX_VALUE)!=1)
                throw new AssertionError(
                    "replacement owner task did not drain"
                );

            if(ran.get()!=2)
                throw new AssertionError(
                    "replacement execution count="+
                    ran.get()
                );

            System.out.println(
                "WORLD_REALTIME_GENERATION_OWNERSHIP_FENCE_PASS "+
                "firstOwnerExecuted=true "+
                "staleScheduleRejected=true "+
                "queueUnchangedOnStale=true "+
                "replacementOwnerExecuted=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );

            world.close();
        }
    }

    private WorldRealtimeGenerationOwnershipFenceTest(){}
}
