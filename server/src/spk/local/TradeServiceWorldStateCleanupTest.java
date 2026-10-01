package spk.local;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
            closeBoundaryOperationFence(
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
                "closeBoundaryOperationsFenced=true "+
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

    private static void closeBoundaryOperationFence(
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
            "trade-close-boundary-a"
        );
        world.registerPlayer(
            b,
            "trade-close-boundary-b"
        );

        OutboundPacketQueue qa=
            new OutboundPacketQueue();
        OutboundPacketQueue qb=
            new OutboundPacketQueue();
        ServerPacketWriter wa=
            new ServerPacketWriter(
                qa,
                new IsaacCipher(
                    new int[]{17,18,19,20}
                )
            );
        ServerPacketWriter wb=
            new ServerPacketWriter(
                qb,
                new IsaacCipher(
                    new int[]{21,22,23,24}
                )
            );

        CountDownLatch lifecycleHeld=
            new CountDownLatch(1);
        CountDownLatch releaseLifecycle=
            new CountDownLatch(1);
        Throwable[] blockerFailure={null};
        Throwable[] closeFailure={null};

        try{
            TradeService.register(
                world,
                a,
                a.bank(),
                wa,
                ()->{}
            );
            TradeService.register(
                world,
                b,
                b.bank(),
                wb,
                ()->{}
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
                ))
                throw new AssertionError(
                    "close-boundary trade fixture did not open"
                );

            int packetsA=
                qa.queuedPackets();
            int packetsB=
                qb.queuedPackets();
            int bytesA=
                qa.queuedBytes();
            int bytesB=
                qb.queuedBytes();

            Thread blocker=
                new Thread(
                    ()->{
                        try{
                            world.withOpenLifecycleOwnership(
                                ()->{
                                    lifecycleHeld.countDown();

                                    if(!releaseLifecycle.await(
                                            5L,
                                            TimeUnit.SECONDS))
                                        throw new AssertionError(
                                            "close-boundary lifecycle release timeout"
                                        );
                                }
                            );
                        }catch(Throwable failure){
                            blockerFailure[0]=failure;
                        }
                    },
                    "trade-close-boundary-blocker"
                );

            blocker.start();

            if(!lifecycleHeld.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "close-boundary lifecycle blocker did not enter"
                );

            Thread closer=
                new Thread(
                    ()->{
                        try{
                            world.close();
                        }catch(Throwable failure){
                            closeFailure[0]=failure;
                        }
                    },
                    "trade-close-boundary-closer"
                );

            closer.start();

            long deadline=
                System.nanoTime()+
                TimeUnit.SECONDS.toNanos(
                    5L
                );

            while(!world.closed()&&
                  System.nanoTime()<deadline)
                Thread.yield();

            if(!world.closed())
                throw new AssertionError(
                    "World close flag was not published"
                );

            if(trackedWorlds()!=baseline+1)
                throw new AssertionError(
                    "TradeService state swept before lifecycle barrier release"
                );

            String reopened=
                TradeService.start(
                    world,
                    a,
                    b
                );

            if(!"TRADE_UI_REJECTED_WORLD_CLOSED"
                    .equals(reopened))
                throw new AssertionError(
                    "post-close-boundary trade start was admitted: "+
                    reopened
                );

            String widget=
                TradeService.handleWidget(
                    a,
                    TradeService.FIRST_ACCEPT
                );

            if(widget!=null)
                throw new AssertionError(
                    "post-close-boundary trade widget was admitted: "+
                    widget
                );

            if(TradeService.active(a)||
               TradeService.active(b))
                throw new AssertionError(
                    "closed World still exposed active trade"
                );

            if(qa.queuedPackets()!=packetsA||
               qb.queuedPackets()!=packetsB||
               qa.queuedBytes()!=bytesA||
               qb.queuedBytes()!=bytesB)
                throw new AssertionError(
                    "post-close-boundary trade operation emitted packet I/O"
                );

            releaseLifecycle.countDown();

            blocker.join(
                5_000L
            );
            closer.join(
                5_000L
            );

            if(blocker.isAlive()||
               closer.isAlive())
                throw new AssertionError(
                    "close-boundary threads did not terminate"
                );

            if(blockerFailure[0]!=null)
                throw new AssertionError(
                    "lifecycle blocker failed",
                    blockerFailure[0]
                );

            if(closeFailure[0]!=null)
                throw new AssertionError(
                    "World close failed",
                    closeFailure[0]
                );

            if(trackedWorlds()!=baseline)
                throw new AssertionError(
                    "terminal trade state not released after lifecycle barrier"
                );
        }finally{
            releaseLifecycle.countDown();
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