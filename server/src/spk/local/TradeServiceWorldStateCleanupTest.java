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

            worldCloseCleanup(
                baseline
            );

            System.out.println(
                "TRADE_WORLD_STATE_CLEANUP_PASS activeCancel=true "+
                "peerKeepsState=true finalUnregisterReleased=true "+
                "activeTradeDetached=true worldStateReleased=true "+
                "packetIo=false saveCallback=false "+
                "postCloseRegisterRejected=true "+
                "postCloseUnregisterIdempotent=true "+
                "noStateResurrection=true "+
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

    private static void worldCloseCleanup(
        int baseline
    )throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer a=
            new WorldPlayer();
        WorldPlayer b=
            new WorldPlayer();

        world.registerPlayer(
            a,
            "trade-world-close-a"
        );
        world.registerPlayer(
            b,
            "trade-world-close-b"
        );

        OutboundPacketQueue qa=
            new OutboundPacketQueue();
        OutboundPacketQueue qb=
            new OutboundPacketQueue();
        ServerPacketWriter wa=
            new ServerPacketWriter(
                qa,
                new IsaacCipher(
                    new int[]{9,10,11,12}
                )
            );
        ServerPacketWriter wb=
            new ServerPacketWriter(
                qb,
                new IsaacCipher(
                    new int[]{13,14,15,16}
                )
            );

        int[] saves={0};

        try{
            TradeService.register(
                world,
                a,
                a.bank(),
                wa,
                ()->saves[0]++
            );
            TradeService.register(
                world,
                b,
                b.bank(),
                wb,
                ()->saves[0]++
            );

            String opened=
                TradeService.start(
                    world,
                    a,
                    b
                );

            if(opened==null||
               !opened.contains(
                    "TRADE_UI_OPEN"
                )||
               !TradeService.active(a)||
               !TradeService.active(b))
                throw new AssertionError(
                    "World-close trade fixture did not become active"
                );

            int packetsA=
                qa.queuedPackets();
            int packetsB=
                qb.queuedPackets();
            int bytesA=
                qa.queuedBytes();
            int bytesB=
                qb.queuedBytes();

            world.close();

            if(trackedWorlds()!=baseline)
                throw new AssertionError(
                    "World close retained TradeService state"
                );

            if(TradeService.active(a)||
               TradeService.active(b))
                throw new AssertionError(
                    "World close retained active trade"
                );

            if(qa.queuedPackets()!=packetsA||
               qb.queuedPackets()!=packetsB||
               qa.queuedBytes()!=bytesA||
               qb.queuedBytes()!=bytesB)
                throw new AssertionError(
                    "TradeService terminal detach performed packet I/O"
                );

            if(saves[0]!=0)
                throw new AssertionError(
                    "TradeService terminal detach invoked save callback"
                );

            expect(
                IllegalStateException.class,
                ()->TradeService.register(
                    world,
                    a,
                    a.bank(),
                    wa,
                    ()->saves[0]++
                ),
                "post-close trade registration"
            );

            if(trackedWorlds()!=baseline)
                throw new AssertionError(
                    "rejected post-close TradeService registration resurrected state"
                );

            TradeService.unregister(a);
            TradeService.unregister(b);

            if(trackedWorlds()!=baseline||
               qa.queuedPackets()!=packetsA||
               qb.queuedPackets()!=packetsB||
               saves[0]!=0)
                throw new AssertionError(
                    "post-close TradeService unregister changed terminal state"
                );
        }finally{
            TradeService.unregister(a);
            TradeService.unregister(b);

            if(a.registered())
                world.unregisterPlayer(a);
            if(b.registered())
                world.unregisterPlayer(b);

            world.close();
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
        String label
    )throws Exception{
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(
                    failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
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