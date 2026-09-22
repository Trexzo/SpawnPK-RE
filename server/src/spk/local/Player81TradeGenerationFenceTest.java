package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Map;

public final class Player81TradeGenerationFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer a=
            new WorldPlayer();
        WorldPlayer b=
            new WorldPlayer();

        long aGeneration1=
            world.registerPlayer(
                a,
                "trade-generation-a"
            );
        long bGeneration1=
            world.registerPlayer(
                b,
                "trade-generation-b"
            );

        ServerPacketWriter writerA1=
            writer(1);
        ServerPacketWriter writerB=
            writer(5);

        Player81WorldSync.Context contextA1=
            Player81WorldSync.register(
                writerA1,
                world,
                a,
                new DevAuthorityWorkbench()
            );
        Player81WorldSync.Context contextB=
            Player81WorldSync.register(
                writerB,
                world,
                b,
                new DevAuthorityWorkbench()
            );

        ServerPacketWriter writerA2=null;

        try{
            String first=
                contextA1.requestTrade(
                    b,
                    1_000L
                );

            if(first.startsWith(
                    "TRADE_MUTUAL_ACCEPTED"))
                throw new AssertionError(
                    "first trade request unexpectedly accepted"
                );

            if(!world.unregisterPlayer(
                    a,
                    aGeneration1
                ))
                throw new AssertionError(
                    "generation A1 unregister failed"
                );

            long aGeneration2=
                world.registerPlayer(
                    a,
                    "trade-generation-a"
                );

            if(aGeneration2==aGeneration1)
                throw new AssertionError(
                    "A generation did not advance"
                );

            String staleReciprocal=
                contextB.requestTrade(
                    a,
                    2_000L
                );

            if(staleReciprocal.startsWith(
                    "TRADE_MUTUAL_ACCEPTED"))
                throw new AssertionError(
                    "B1 request accepted stale A1 reciprocal after A2 replacement"
                );

            writerA2=
                writer(9);

            Player81WorldSync.Context contextA2=
                Player81WorldSync.register(
                    writerA2,
                    world,
                    a,
                    new DevAuthorityWorkbench()
                );

            if(contextA2.ownerGeneration!=
                    aGeneration2)
                throw new AssertionError(
                    "fresh A2 context generation mismatch"
                );

            String freshForward=
                contextA2.requestTrade(
                    b,
                    3_000L
                );

            if(freshForward.startsWith(
                    "TRADE_MUTUAL_ACCEPTED"))
                throw new AssertionError(
                    "fresh A2 first request unexpectedly accepted"
                );

            String freshReciprocal=
                contextB.requestTrade(
                    a,
                    3_100L
                );

            if(!freshReciprocal.startsWith(
                    "TRADE_MUTUAL_ACCEPTED"))
                throw new AssertionError(
                    "fresh A2/B1 reciprocal pair did not accept: "+
                    freshReciprocal
                );

            String cleanupProbe=
                contextA2.requestTrade(
                    b,
                    4_000L
                );

            if(cleanupProbe.startsWith(
                    "TRADE_MUTUAL_ACCEPTED"))
                throw new AssertionError(
                    "cleanup probe unexpectedly accepted"
                );

            if(!tradeRequestMentions(
                    world,
                    a.id()
                ))
                throw new AssertionError(
                    "cleanup probe request missing"
                );

            Player81WorldSync.unregister(
                writerA2
            );
            writerA2=null;

            if(tradeRequestMentions(
                    world,
                    a.id()
                ))
                throw new AssertionError(
                    "owner cleanup retained generation-fenced trade request"
                );

            System.out.println(
                "PLAYER81_TRADE_GENERATION_FENCE_PASS "+
                "staleReciprocalRejected=true "+
                "freshReciprocalAccepted=true "+
                "ownerCleanupPreserved=true"
            );

            world.unregisterPlayer(
                a,
                aGeneration2
            );
            world.unregisterPlayer(
                b,
                bGeneration1
            );
        }finally{
            Player81WorldSync.unregister(
                writerA1
            );
            Player81WorldSync.unregister(
                writerB
            );

            if(writerA2!=null)
                Player81WorldSync.unregister(
                    writerA2
                );

            if(a.registered())
                world.unregisterPlayer(
                    a,
                    a.generation()
                );

            if(b.registered())
                world.unregisterPlayer(
                    b,
                    b.generation()
                );

            world.close();
        }
    }

    private static boolean tradeRequestMentions(
        World world,
        EntityId id
    )throws Exception{
        Object state=
            worldState(world);

        if(state==null)
            return false;

        Field field=
            state.getClass()
                .getDeclaredField(
                    "tradeRequests"
                );
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String,?> requests=
            (Map<String,?>)
                field.get(state);

        String prefix=id+">";
        String suffix=">"+id;

        for(String key:requests.keySet()){
            if(key.startsWith(prefix)||
               key.endsWith(suffix))
                return true;
        }

        return false;
    }

    private static Object worldState(
        World world
    )throws Exception{
        Field field=
            Player81WorldSync.class
                .getDeclaredField(
                    "BY_WORLD"
                );
        field.setAccessible(true);

        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            Map<World,?> states=
                (Map<World,?>)
                    field.get(null);
            return states.get(world);
        }
    }

    private static ServerPacketWriter writer(
        int seed
    ){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    private Player81TradeGenerationFenceTest(){}
}
