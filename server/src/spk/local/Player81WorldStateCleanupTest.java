package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class Player81WorldStateCleanupTest {
    public static void main(String[] args)throws Exception{
        int baseline=trackedWorlds();
        int writerBaseline=trackedWriters();

        World world=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();

        world.registerPlayer(a,"player81-cleanup-a");
        world.registerPlayer(b,"player81-cleanup-b");

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

        try{
            Player81WorldSync.register(
                wa,
                world,
                a,
                new DevAuthorityWorkbench()
            );
            Player81WorldSync.register(
                wb,
                world,
                b,
                new DevAuthorityWorkbench()
            );

            if(trackedWorlds()!=baseline+1)
                throw new AssertionError(
                    "world state not created baseline="+baseline+
                    " tracked="+trackedWorlds()
                );

            Player81WorldSync.unregister(wa);

            if(trackedWorlds()!=baseline+1)
                throw new AssertionError(
                    "world state removed while peer context remained"
                );

            if(world.players().size()!=2)
                throw new AssertionError(
                    "test requires players to remain registered before final sync unregister"
                );

            Player81WorldSync.unregister(wb);

            if(world.players().size()!=2)
                throw new AssertionError(
                    "Player81 unregister unexpectedly changed world membership"
                );

            if(trackedWorlds()!=baseline)
                throw new AssertionError(
                    "empty Player81 world state retained baseline="+baseline+
                    " tracked="+trackedWorlds()
                );

            worldCloseCleanup(
                baseline,
                writerBaseline
            );
            closeWindowContextFence(
                baseline,
                writerBaseline
            );

            System.out.println(
                "PLAYER81_WORLD_STATE_CLEANUP_PASS "+
                "peerKeepsState=true "+
                "finalContextReleasedBeforeWorldUnregister=true "+
                "playersStillRegistered=2 "+
                "worldCloseReleasedState=true "+
                "worldCloseReleasedWriters=true "+
                "worldCloseContextsClosed=true "+
                "postCloseRegisterRejected=true "+
                "noStateResurrection=true "+
                "postCloseUnregisterIdempotent=true "+
                "closeWindowContextStale=true "+
                "closeWindowTradeRejected=true "+
                "closeWindowClientIndexRejected=true "+
                "closeWindowSkillNoop=true "+
                "closeWindowOptionsNoop=true "+
                "baseline="+baseline
            );
        }finally{
            Player81WorldSync.unregister(wa);
            Player81WorldSync.unregister(wb);
            world.unregisterPlayer(a);
            world.unregisterPlayer(b);
            world.close();
        }
    }

    private static void worldCloseCleanup(
        int worldBaseline,
        int writerBaseline
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
            "player81-close-a"
        );
        world.registerPlayer(
            b,
            "player81-close-b"
        );

        ServerPacketWriter wa=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{9,10,11,12}
                )
            );
        ServerPacketWriter wb=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{13,14,15,16}
                )
            );

        Player81WorldSync.Context ca=null;
        Player81WorldSync.Context cb=null;

        try{
            ca=
                Player81WorldSync.register(
                    wa,
                    world,
                    a,
                    new DevAuthorityWorkbench()
                );
            cb=
                Player81WorldSync.register(
                    wb,
                    world,
                    b,
                    new DevAuthorityWorkbench()
                );

            if(trackedWorlds()!=
                    worldBaseline+1||
               trackedWriters()!=
                    writerBaseline+2)
                throw new AssertionError(
                    "World-close Player81 fixture not retained"
                );

            world.close();

            if(trackedWorlds()!=
                    worldBaseline||
               trackedWriters()!=
                    writerBaseline)
                throw new AssertionError(
                    "World close retained Player81 static state"
                );

            if(ca==null||
               cb==null||
               !ca.closed||
               !cb.closed)
                throw new AssertionError(
                    "World close did not terminalize Player81 contexts"
                );

            expect(
                IllegalStateException.class,
                ()->Player81WorldSync.register(
                    wa,
                    world,
                    a,
                    new DevAuthorityWorkbench()
                ),
                "post-close Player81 registration"
            );

            if(trackedWorlds()!=
                    worldBaseline||
               trackedWriters()!=
                    writerBaseline)
                throw new AssertionError(
                    "post-close Player81 registration resurrected terminal state"
                );

            Player81WorldSync.unregister(
                wa
            );
            Player81WorldSync.unregister(
                wb
            );

            if(trackedWorlds()!=
                    worldBaseline||
               trackedWriters()!=
                    writerBaseline)
                throw new AssertionError(
                    "post-close Player81 unregister changed baseline"
                );
        }finally{
            Player81WorldSync.unregister(
                wa
            );
            Player81WorldSync.unregister(
                wb
            );

            if(a.registered())
                world.unregisterPlayer(a);
            if(b.registered())
                world.unregisterPlayer(b);

            world.close();
        }
    }

    private static void closeWindowContextFence(
        int worldBaseline,
        int writerBaseline
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
            "player81-close-window-a"
        );
        world.registerPlayer(
            b,
            "player81-close-window-b"
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

        Player81WorldSync.Context ca=null;
        Player81WorldSync.Context cb=null;
        CountDownLatch lifecycleEntered=
            new CountDownLatch(1);
        CountDownLatch releaseLifecycle=
            new CountDownLatch(1);
        AtomicReference<Throwable> blockerFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> closeFailure=
            new AtomicReference<>();
        Thread blocker=null;
        Thread closer=null;

        try{
            ca=
                Player81WorldSync.register(
                    wa,
                    world,
                    a,
                    new DevAuthorityWorkbench()
                );
            cb=
                Player81WorldSync.register(
                    wb,
                    world,
                    b,
                    new DevAuthorityWorkbench()
                );

            if(!ca.ownerCurrent()||
               !cb.ownerCurrent())
                throw new AssertionError(
                    "close-window contexts were not current before close"
                );

            if(trackedWorlds()!=
                    worldBaseline+1||
               trackedWriters()!=
                    writerBaseline+2)
                throw new AssertionError(
                    "close-window Player81 fixture not retained"
                );

            int packetsA=
                qa.queuedPackets();
            int packetsB=
                qb.queuedPackets();
            int bytesA=
                qa.queuedBytes();
            int bytesB=
                qb.queuedBytes();

            blocker=
                new Thread(
                    ()->{
                        try{
                            boolean accepted=
                                world.withOpenLifecycleOwnership(
                                    ()->{
                                        lifecycleEntered.countDown();

                                        boolean interrupted=false;
                                        while(releaseLifecycle.getCount()>0L){
                                            try{
                                                releaseLifecycle.await(
                                                    10L,
                                                    TimeUnit.MILLISECONDS
                                                );
                                            }catch(InterruptedException ignored){
                                                interrupted=true;
                                            }
                                        }

                                        if(interrupted)
                                            Thread.currentThread()
                                                .interrupt();
                                    }
                                );

                            if(!accepted)
                                throw new AssertionError(
                                    "Player81 lifecycle blocker was not admitted"
                                );
                        }catch(Throwable failure){
                            blockerFailure.set(
                                failure
                            );
                        }
                    },
                    "player81-close-window-lifecycle-owner"
                );
            blocker.start();

            if(!lifecycleEntered.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "Player81 lifecycle blocker did not enter"
                );

            closer=
                new Thread(
                    ()->{
                        try{
                            world.close();
                        }catch(Throwable failure){
                            closeFailure.set(
                                failure
                            );
                        }
                    },
                    "player81-close-window-close-owner"
                );
            closer.start();

            long deadline=
                System.nanoTime()+
                TimeUnit.SECONDS.toNanos(
                    2L
                );

            while(!world.closed()&&
                  System.nanoTime()<deadline)
                Thread.sleep(1L);

            if(!world.closed())
                throw new AssertionError(
                    "Player81 World close boundary was not published"
                );

            if(!closer.isAlive())
                throw new AssertionError(
                    "Player81 close owner did not remain behind lifecycle barrier"
                );

            if(trackedWorlds()!=
                    worldBaseline+1||
               trackedWriters()!=
                    writerBaseline+2)
                throw new AssertionError(
                    "Player81 state swept before lifecycle barrier release"
                );

            if(ca.ownerCurrent()||
               cb.ownerCurrent())
                throw new AssertionError(
                    "closed World contexts still reported current"
                );

            String trade=
                ca.requestTrade(
                    b,
                    System.currentTimeMillis()
                );

            if(!"TRADE_REJECTED_STALE_OWNER".equals(
                    trade))
                throw new AssertionError(
                    "closed World Player81 trade was admitted: "+
                    trade
                );

            if(Player81WorldSync.clientIndexFor(
                    wa,
                    b
                )!=-1)
                throw new AssertionError(
                    "closed World Player81 client index remained visible"
                );

            boolean skillSent=
                Player81WorldSync.sendSkillUpdate(
                    world,
                    a,
                    0,
                    1,
                    1
                );

            if(skillSent)
                throw new AssertionError(
                    "closed World Player81 skill update was admitted"
                );

            Player81WorldSync
                .sendPlayerOptionsIfMultiplayer(
                    world
                );

            if(qa.queuedPackets()!=packetsA||
               qb.queuedPackets()!=packetsB||
               qa.queuedBytes()!=bytesA||
               qb.queuedBytes()!=bytesB)
                throw new AssertionError(
                    "closed World Player81 operation emitted packet I/O"
                );

            releaseLifecycle.countDown();

            blocker.join(5_000L);
            closer.join(5_000L);

            if(blocker.isAlive()||
               closer.isAlive())
                throw new AssertionError(
                    "Player81 close-window threads did not terminate"
                );

            if(blockerFailure.get()!=null)
                throw new AssertionError(
                    "Player81 lifecycle blocker failed",
                    blockerFailure.get()
                );

            if(closeFailure.get()!=null)
                throw new AssertionError(
                    "Player81 World close failed",
                    closeFailure.get()
                );

            if(trackedWorlds()!=
                    worldBaseline||
               trackedWriters()!=
                    writerBaseline)
                throw new AssertionError(
                    "Player81 terminal sweep did not restore baseline"
                );
        }finally{
            releaseLifecycle.countDown();

            if(blocker!=null&&
               blocker.isAlive())
                blocker.join(5_000L);
            if(closer!=null&&
               closer.isAlive())
                closer.join(5_000L);

            Player81WorldSync.unregister(
                wa
            );
            Player81WorldSync.unregister(
                wb
            );

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

    private static int trackedWriters()
        throws Exception{
        Field field=
            Player81WorldSync.class
                .getDeclaredField(
                    "BY_WRITER"
                );
        field.setAccessible(true);

        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<ServerPacketWriter,?>
                writers=
                    (IdentityHashMap<ServerPacketWriter,?>)
                        field.get(null);

            return writers.size();
        }
    }

    private static int trackedWorlds()throws Exception{
        Field field=Player81WorldSync.class.getDeclaredField("BY_WORLD");
        field.setAccessible(true);
        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<World,?> states=
                (IdentityHashMap<World,?>)field.get(null);
            return states.size();
        }
    }
}