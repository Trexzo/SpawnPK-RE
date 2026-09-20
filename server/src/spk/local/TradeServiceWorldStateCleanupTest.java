package spk.local;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;

public final class TradeServiceWorldStateCleanupTest {
    public static void main(String[] args)throws Exception{
        int baseline=trackedWorlds();

        World world=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();
        world.registerPlayer(a,"trade-cleanup-a");
        world.registerPlayer(b,"trade-cleanup-b");

        OutboundPacketQueue qa=new OutboundPacketQueue();
        OutboundPacketQueue qb=new OutboundPacketQueue();
        ServerPacketWriter wa=
            new ServerPacketWriter(
                qa,
                new IsaacCipher(new int[]{1,2,3,4})
            );
        ServerPacketWriter wb=
            new ServerPacketWriter(
                qb,
                new IsaacCipher(new int[]{5,6,7,8})
            );

        try{
            TradeService.register(world,a,a.bank(),wa,()->{});
            TradeService.register(world,b,b.bank(),wb,()->{});

            if(trackedWorlds()!=baseline+1)
                throw new AssertionError(
                    "world state not created baseline="+baseline+
                    " tracked="+trackedWorlds()
                );

            String opened=TradeService.start(world,a,b);
            if(opened==null||!opened.contains("TRADE_UI_OPEN"))
                throw new AssertionError("trade did not open: "+opened);
            if(!TradeService.active(a)||!TradeService.active(b))
                throw new AssertionError("trade not active");

            TradeService.unregister(a);

            if(TradeService.active(a)||TradeService.active(b))
                throw new AssertionError(
                    "unregister did not cancel active trade"
                );
            if(trackedWorlds()!=baseline+1)
                throw new AssertionError(
                    "world state removed while peer context remained"
                );

            TradeService.unregister(b);

            if(trackedWorlds()!=baseline)
                throw new AssertionError(
                    "empty world state retained baseline="+baseline+
                    " tracked="+trackedWorlds()
                );

            System.out.println(
                "TRADE_WORLD_STATE_CLEANUP_PASS activeCancel=true "+
                "peerKeepsState=true finalUnregisterReleased=true "+
                "baseline="+baseline
            );
        }finally{
            TradeService.unregister(a);
            TradeService.unregister(b);
            world.unregisterPlayer(a);
            world.unregisterPlayer(b);
            world.close();
        }
    }

    private static int trackedWorlds()throws Exception{
        Field field=TradeService.class.getDeclaredField("STATES");
        field.setAccessible(true);
        synchronized(TradeService.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<World,?> states=
                (IdentityHashMap<World,?>)field.get(null);
            return states.size();
        }
    }
}
