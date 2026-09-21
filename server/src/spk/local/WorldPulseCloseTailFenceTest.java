package spk.local;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldPulseCloseTailFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                10L
            );

        WorldPlayer first=
            new WorldPlayer();
        WorldPlayer second=
            new WorldPlayer();

        CountDownLatch firstEntered=
            new CountDownLatch(1);
        CountDownLatch releaseFirst=
            new CountDownLatch(1);
        CountDownLatch firstExited=
            new CountDownLatch(1);

        AtomicInteger firstInterrupts=
            new AtomicInteger();
        AtomicInteger secondRuns=
            new AtomicInteger();
        AtomicLong observedTick=
            new AtomicLong(-1L);

        ExecutorService closer=
            Executors.newSingleThreadExecutor();

        try{
            for(int i=0;i<99;i++)
                world.observePulse(
                    System.currentTimeMillis()
                );

            if(world.clock().tick()!=99L)
                throw new AssertionError(
                    "precondition tick="+
                    world.clock().tick()
                );

            long firstGeneration=
                world.registerPlayer(
                    first,
                    LocalAccountProfiles.PRIMARY
                );
            long secondGeneration=
                world.registerPlayer(
                    second,
                    "pulse-tail-peer"
                );

            world.attachTickTarget(
                new WorldTickTarget(){
                    @Override public EntityId ownerId(){
                        return first.id();
                    }

                    @Override public long ownerGeneration(){
                        return firstGeneration;
                    }

                    @Override public void onWorldTick(
                        long tick,
                        long nowMillis
                    ){
                        observedTick.set(tick);
                        firstEntered.countDown();

                        try{
                            while(true){
                                try{
                                    releaseFirst.await();
                                    return;
                                }catch(InterruptedException ignored){
                                    firstInterrupts.incrementAndGet();
                                }
                            }
                        }finally{
                            firstExited.countDown();
                        }
                    }
                }
            );

            world.attachTickTarget(
                new WorldTickTarget(){
                    @Override public EntityId ownerId(){
                        return second.id();
                    }

                    @Override public long ownerGeneration(){
                        return secondGeneration;
                    }

                    @Override public void onWorldTick(
                        long tick,
                        long nowMillis
                    ){
                        secondRuns.incrementAndGet();
                    }
                }
            );

            world.start();

            if(!firstEntered.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "blocking tick target never entered"
                );

            if(observedTick.get()!=100L)
                throw new AssertionError(
                    "expected blocking target at tick 100, got "+
                    observedTick.get()
                );

            Future<?> closeFuture=
                closer.submit(
                    world::close
                );

            try{
                closeFuture.get(
                    4,
                    TimeUnit.SECONDS
                );
            }catch(TimeoutException timeout){
                releaseFirst.countDown();
                throw new AssertionError(
                    "World.close did not return after bounded pulse join",
                    timeout
                );
            }

            if(firstExited.getCount()==0L)
                throw new AssertionError(
                    "blocking target unexpectedly exited before release"
                );

            if(firstInterrupts.get()==0)
                throw new AssertionError(
                    "pulse close did not interrupt active target"
                );

            if(secondRuns.get()!=0)
                throw new AssertionError(
                    "later target ran before blocked target release"
                );

            if(world.persistence()
                    .checkpointCapturedCount()!=0L)
                throw new AssertionError(
                    "checkpoint captured before blocked target release "+
                    world.persistence().metrics()
                );

            releaseFirst.countDown();

            if(!firstExited.await(
                    3,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "blocking target did not exit after release"
                );

            Thread pulseThread=
                world.pulse().thread();

            if(pulseThread!=null)
                pulseThread.join(
                    3_000L
                );

            if(pulseThread!=null&&
               pulseThread.isAlive())
                throw new AssertionError(
                    "pulse thread remained alive after target release"
                );

            if(secondRuns.get()!=0)
                throw new AssertionError(
                    "later tick target executed after World close"
                );

            if(world.persistence()
                    .checkpointCapturedCount()!=0L||
               world.persistence()
                    .checkpointWrittenCount()!=0L||
               world.persistence()
                    .checkpointRejectedCount()!=0L||
               world.persistence()
                    .failedCount()!=0L)
                throw new AssertionError(
                    "post-close pulse tail touched persistence "+
                    world.persistence().metrics()
                );

            if(!world.unregisterPlayer(first)||
               !world.unregisterPlayer(second))
                throw new AssertionError(
                    "post-close player cleanup failed"
                );

            System.out.println(
                "WORLD_PULSE_CLOSE_TAIL_FENCE_PASS "+
                "blockedTick=100 "+
                "interruptIgnored=true "+
                "laterTargetSuppressed=true "+
                "postCloseCheckpointSuppressed=true "+
                "pulseTerminated=true "+
                "postCloseCleanup=true"
            );
        }finally{
            releaseFirst.countDown();

            closer.shutdownNow();
            closer.awaitTermination(
                2,
                TimeUnit.SECONDS
            );

            if(first.registered())
                world.unregisterPlayer(first);

            if(second.registered())
                world.unregisterPlayer(second);

            world.close();
        }
    }

    private WorldPulseCloseTailFenceTest(){}
}
