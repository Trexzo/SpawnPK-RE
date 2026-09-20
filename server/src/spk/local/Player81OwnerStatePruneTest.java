package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Map;

public final class Player81OwnerStatePruneTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();

        world.registerPlayer(a,"player81-prune-a");
        world.registerPlayer(b,"player81-prune-b");

        ServerPacketWriter wa=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{1,2,3,4})
            );
        ServerPacketWriter wb=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{5,6,7,8})
            );

        Player81WorldSync.Context ca=
            Player81WorldSync.register(
                wa,
                world,
                a,
                new DevAuthorityWorkbench()
            );
        Player81WorldSync.Context cb=
            Player81WorldSync.register(
                wb,
                world,
                b,
                new DevAuthorityWorkbench()
            );

        try{
            Player81WorldSync.transformForTest(
                ca,
                BootstrapPackets.player81WalkStep(4)
            );
            Player81WorldSync.transformForTest(
                ca,
                CombatSync.player81AnimationAndGfx(
                    827,
                    1310,
                    0,
                    0
                )
            );

            Player81WorldSync.transformForTest(
                cb,
                BootstrapPackets.player81WalkStep(6)
            );
            Player81WorldSync.transformForTest(
                cb,
                CombatSync.player81AnimationAndGfx(
                    828,
                    1311,
                    0,
                    0
                )
            );

            String first=ca.requestTrade(b,1_000L);
            String second=cb.requestTrade(a,40_000L);

            if(first==null||second==null)
                throw new AssertionError(
                    "trade request setup failed"
                );

            Object state=stateFor(world);
            Map<?,?> contexts=map(state,"contexts");
            Map<?,?> motions=map(state,"motions");
            Map<?,?> events=map(state,"events");
            Map<?,?> requests=map(state,"tradeRequests");

            if(!contexts.containsKey(a.id())||
               !contexts.containsKey(b.id()))
                throw new AssertionError(
                    "context setup incomplete"
                );

            if(!motions.containsKey(a.id())||
               !motions.containsKey(b.id()))
                throw new AssertionError(
                    "motion setup incomplete"
                );

            if(!events.containsKey(a.id())||
               !events.containsKey(b.id()))
                throw new AssertionError(
                    "event setup incomplete"
                );

            if(!requestMentions(requests,a.id()))
                throw new AssertionError(
                    "trade request setup missing departed owner"
                );

            Player81WorldSync.unregister(wa);

            state=stateFor(world);
            if(state==null)
                throw new AssertionError(
                    "WorldState removed while peer context remained"
                );

            contexts=map(state,"contexts");
            motions=map(state,"motions");
            events=map(state,"events");
            requests=map(state,"tradeRequests");

            if(contexts.containsKey(a.id()))
                throw new AssertionError(
                    "departed context retained"
                );
            if(motions.containsKey(a.id()))
                throw new AssertionError(
                    "departed motion retained"
                );
            if(events.containsKey(a.id()))
                throw new AssertionError(
                    "departed event queue retained"
                );
            if(requestMentions(requests,a.id()))
                throw new AssertionError(
                    "departed trade request retained"
                );

            if(!contexts.containsKey(b.id()))
                throw new AssertionError(
                    "surviving peer context removed"
                );
            if(!motions.containsKey(b.id()))
                throw new AssertionError(
                    "surviving peer motion removed"
                );
            if(!events.containsKey(b.id()))
                throw new AssertionError(
                    "surviving peer event queue removed"
                );

            if(world.players().size()!=2)
                throw new AssertionError(
                    "Player81 cleanup changed World membership"
                );

            System.out.println(
                "PLAYER81_OWNER_STATE_PRUNE_PASS "+
                "departedContext=true "+
                "departedMotion=true "+
                "departedEvents=true "+
                "departedTradeRequests=true "+
                "peerStatePreserved=true "+
                "playersStillRegistered=2"
            );
        }finally{
            Player81WorldSync.unregister(wa);
            Player81WorldSync.unregister(wb);
            world.unregisterPlayer(a);
            world.unregisterPlayer(b);
            world.close();
        }
    }

    private static Object stateFor(World world)throws Exception{
        Field field=
            Player81WorldSync.class.getDeclaredField(
                "BY_WORLD"
            );
        field.setAccessible(true);
        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            Map<World,?> states=
                (Map<World,?>)field.get(null);
            return states.get(world);
        }
    }

    private static Map<?,?> map(
        Object state,
        String name
    )throws Exception{
        if(state==null)
            throw new AssertionError(
                "missing Player81 WorldState"
            );

        Field field=
            state.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (Map<?,?>)field.get(state);
    }

    private static boolean requestMentions(
        Map<?,?> requests,
        EntityId id
    ){
        String prefix=id+">";
        String suffix=">"+id;
        for(Object keyObject:requests.keySet()){
            String key=String.valueOf(keyObject);
            if(key.startsWith(prefix)||
               key.endsWith(suffix))
                return true;
        }
        return false;
    }
}