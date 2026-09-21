package spk.local;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

public final class TradeServiceContextReplacementCleanupTest {
    public static void main(String[] args)throws Exception{
        int baseline=trackedWorlds();

        sameWorldReplacement(baseline);
        crossWorldReplacement(baseline);

        if(trackedWorlds()!=baseline)
            throw new AssertionError(
                "final tracked worlds="+trackedWorlds()+
                " baseline="+baseline
            );

        System.out.println(
            "TRADE_CONTEXT_REPLACEMENT_CLEANUP_PASS "+
            "sameWorldTradeCancelled=true "+
            "oldWriterReleased=true "+
            "staleUnregisterFenced=true "+
            "replacementCanRetrade=true "+
            "crossWorldUniqueContext=true "+
            "finalBaseline="+baseline
        );
    }

    private static void sameWorldReplacement(
        int baseline
    )throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();

        world.registerPlayer(a,"trade-replace-a");
        world.registerPlayer(b,"trade-replace-b");

        ServerPacketWriter writerA1=writer(1);
        ServerPacketWriter writerA2=writer(5);
        ServerPacketWriter writerB=writer(9);

        try{
            TradeService.register(
                world,
                a,
                a.bank(),
                writerA1,
                ()->{}
            );
            TradeService.register(
                world,
                b,
                b.bank(),
                writerB,
                ()->{}
            );

            String first=
                TradeService.start(world,a,b);
            if(first==null||
               !first.contains("TRADE_UI_OPEN"))
                throw new AssertionError(
                    "first trade="+first
                );

            if(!TradeService.active(a)||
               !TradeService.active(b))
                throw new AssertionError(
                    "first trade not active"
                );

            TradeService.register(
                world,
                a,
                a.bank(),
                writerA2,
                ()->{}
            );

            if(TradeService.active(a)||
               TradeService.active(b))
                throw new AssertionError(
                    "replacement did not cancel stale trade"
                );

            if(tradeEntries(world)!=0)
                throw new AssertionError(
                    "stale trade entries="+
                    tradeEntries(world)
                );

            if(countPlayerContexts(a)!=1)
                throw new AssertionError(
                    "A context count="+
                    countPlayerContexts(a)
                );

            if(writerFor(world,a)!=writerA2)
                throw new AssertionError(
                    "replacement writer not installed"
                );

            if(writerRetained(writerA1))
                throw new AssertionError(
                    "old A writer retained"
                );

            String second=
                TradeService.start(world,a,b);
            if(second==null||
               !second.contains("TRADE_UI_OPEN"))
                throw new AssertionError(
                    "replacement trade="+second
                );

            if(!TradeService.active(a)||
               !TradeService.active(b))
                throw new AssertionError(
                    "replacement trade not active"
                );

            TradeService.unregister(
                a,
                writerA1
            );

            if(!TradeService.active(a)||
               !TradeService.active(b))
                throw new AssertionError(
                    "stale writer unregister cancelled replacement trade"
                );

            if(writerFor(world,a)!=writerA2)
                throw new AssertionError(
                    "stale writer unregister removed replacement context"
                );

            TradeService.unregister(
                a,
                writerA2
            );

            if(TradeService.active(a)||
               TradeService.active(b))
                throw new AssertionError(
                    "replacement unregister did not cancel"
                );

            TradeService.unregister(b);

            if(trackedWorlds()!=baseline)
                throw new AssertionError(
                    "same-world state retained baseline="+
                    baseline+
                    " tracked="+trackedWorlds()
                );
        }finally{
            TradeService.unregister(a);
            TradeService.unregister(b);
            world.unregisterPlayer(a);
            world.unregisterPlayer(b);
            world.close();
        }
    }

    private static void crossWorldReplacement(
        int baseline
    )throws Exception{
        World first=World.isolatedForTest(600L);
        World second=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();

        first.registerPlayer(
            player,
            "trade-cross-world"
        );

        ServerPacketWriter writerFirst=writer(13);
        ServerPacketWriter writerSecond=writer(17);

        try{
            TradeService.register(
                first,
                player,
                player.bank(),
                writerFirst,
                ()->{}
            );

            if(countPlayerContexts(player)!=1)
                throw new AssertionError(
                    "initial cross-world contexts="+
                    countPlayerContexts(player)
                );

            if(trackedWorlds()!=baseline+1)
                throw new AssertionError(
                    "initial world tracking baseline="+
                    baseline+
                    " tracked="+trackedWorlds()
                );

            TradeService.register(
                second,
                player,
                player.bank(),
                writerSecond,
                ()->{}
            );

            if(countPlayerContexts(player)!=1)
                throw new AssertionError(
                    "duplicate cross-world contexts="+
                    countPlayerContexts(player)
                );

            if(writerFor(first,player)!=null)
                throw new AssertionError(
                    "old world still owns context"
                );

            if(writerFor(second,player)!=writerSecond)
                throw new AssertionError(
                    "new world writer missing"
                );

            if(writerRetained(writerFirst))
                throw new AssertionError(
                    "old cross-world writer retained"
                );

            if(trackedWorlds()!=baseline+1)
                throw new AssertionError(
                    "replacement should own exactly one world baseline="+
                    baseline+
                    " tracked="+trackedWorlds()
                );

            TradeService.unregister(
                player,
                writerFirst
            );

            if(countPlayerContexts(player)!=1||
               writerFor(second,player)!=writerSecond)
                throw new AssertionError(
                    "stale cross-world writer unregister removed current context"
                );

            TradeService.unregister(
                player,
                writerSecond
            );

            if(countPlayerContexts(player)!=0)
                throw new AssertionError(
                    "unregister left contexts="+
                    countPlayerContexts(player)
                );

            if(trackedWorlds()!=baseline)
                throw new AssertionError(
                    "cross-world final state retained baseline="+
                    baseline+
                    " tracked="+trackedWorlds()
                );
        }finally{
            TradeService.unregister(player);
            first.unregisterPlayer(player);
            first.close();
            second.close();
        }
    }

    private static ServerPacketWriter writer(
        int seed
    ){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),
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

    private static int trackedWorlds()
        throws Exception{
        synchronized(TradeService.class){
            return states().size();
        }
    }

    private static int countPlayerContexts(
        WorldPlayer player
    )throws Exception{
        synchronized(TradeService.class){
            int count=0;

            for(Object state:states().values()){
                Map<?,?> contexts=contexts(state);
                if(contexts.containsKey(player.id()))
                    count++;
            }

            return count;
        }
    }

    private static int tradeEntries(
        World world
    )throws Exception{
        synchronized(TradeService.class){
            Object state=states().get(world);
            if(state==null)
                return 0;

            Field field=
                state.getClass()
                    .getDeclaredField("trades");
            field.setAccessible(true);

            return ((Map<?,?>)field.get(state))
                .size();
        }
    }

    private static ServerPacketWriter writerFor(
        World world,
        WorldPlayer player
    )throws Exception{
        synchronized(TradeService.class){
            Object state=states().get(world);
            if(state==null)
                return null;

            Object context=
                contexts(state).get(player.id());

            if(context==null)
                return null;

            Field writer=
                context.getClass()
                    .getDeclaredField("writer");
            writer.setAccessible(true);
            return (ServerPacketWriter)
                writer.get(context);
        }
    }

    private static boolean writerRetained(
        ServerPacketWriter target
    )throws Exception{
        synchronized(TradeService.class){
            for(Object state:states().values()){
                for(Object context:
                        contexts(state).values()){
                    Field writer=
                        context.getClass()
                            .getDeclaredField("writer");
                    writer.setAccessible(true);

                    if(writer.get(context)==target)
                        return true;
                }
            }

            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static IdentityHashMap<World,Object>
        states()throws Exception{
        Field field=
            TradeService.class
                .getDeclaredField("STATES");
        field.setAccessible(true);
        return (IdentityHashMap<World,Object>)
            field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object,Object> contexts(
        Object state
    )throws Exception{
        Field field=
            state.getClass()
                .getDeclaredField("contexts");
        field.setAccessible(true);
        return (Map<Object,Object>)
            field.get(state);
    }
}
