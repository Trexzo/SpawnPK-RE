package spk.local;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldRealtimeCloseAdmissionFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(25L);

        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "realtime-close-owner"
            );

        CountDownLatch realtimeExecuted=
            new CountDownLatch(1);
        CountDownLatch targetEntered=
            new CountDownLatch(1);
        CountDownLatch releaseTarget=
            new CountDownLatch(1);
        AtomicBoolean closeWindowExecuted=
            new AtomicBoolean();
        AtomicReference<Throwable> closeFailure=
            new AtomicReference<>();

        world.scheduleRealtime(
            System.currentTimeMillis(),
            player,
            generation,
            realtimeExecuted::countDown
        );

        world.attachTickTarget(
            new WorldTickTarget(){
                private final AtomicBoolean blocked=
                    new AtomicBoolean();

                @Override public EntityId ownerId(){
                    return player.id();
                }

                @Override public long ownerGeneration(){
                    return generation;
                }

                @Override public void onWorldTick(
                    long worldTick,
                    long nowMillis
                ){
                    if(!blocked.compareAndSet(
                            false,
                            true
                        ))
                        return;

                    targetEntered.countDown();

                    boolean interrupted=false;

                    while(releaseTarget.getCount()>0L){
                        try{
                            releaseTarget.await(
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
            }
        );

        Thread closeThread=null;

        try{
            world.start();

            if(!realtimeExecuted.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "pre-close realtime work did not execute"
                );

            if(!targetEntered.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "blocking tick target did not start"
                );

            closeThread=
                new Thread(
                    ()->{
                        try{
                            world.close();
                        }catch(Throwable error){
                            closeFailure.set(error);
                        }
                    },
                    "world-realtime-close-owner"
                );
            closeThread.start();

            long closedDeadline=
                System.nanoTime()+
                TimeUnit.SECONDS.toNanos(2L);

            while(!world.closed()&&
                  System.nanoTime()<closedDeadline)
                Thread.sleep(1L);

            if(!world.closed())
                throw new AssertionError(
                    "World close boundary was not published"
                );

            if(!closeThread.isAlive())
                throw new AssertionError(
                    "close owner left shutdown window before realtime admission test"
                );

            int sizeBefore=
                world.realtime().size();

            boolean closeWindowRejected=false;

            try{
                world.scheduleRealtime(
                    System.currentTimeMillis()+1_000L,
                    player,
                    generation,
                    ()->closeWindowExecuted.set(true)
                );
            }catch(IllegalStateException expected){
                closeWindowRejected=
                    "world closed".equals(
                        expected.getMessage()
                    );
            }

            if(!closeWindowRejected)
                throw new AssertionError(
                    "close-window realtime scheduling was accepted"
                );

            if(world.realtime().size()!=sizeBefore)
                throw new AssertionError(
                    "close-window realtime scheduling grew queue"
                );

            if(closeWindowExecuted.get())
                throw new AssertionError(
                    "close-window realtime task executed"
                );

            releaseTarget.countDown();

            closeThread.join(5_000L);

            if(closeThread.isAlive())
                throw new AssertionError(
                    "World close did not complete after target release"
                );

            if(closeFailure.get()!=null)
                throw new AssertionError(
                    "World close failed",
                    closeFailure.get()
                );

            if(world.realtime().size()!=0)
                throw new AssertionError(
                    "terminal realtime queue not empty"
                );

            boolean postTerminalRejected=false;

            try{
                world.scheduleRealtime(
                    System.currentTimeMillis(),
                    player,
                    generation,
                    ()->{
                        throw new AssertionError(
                            "post-terminal realtime task executed"
                        );
                    }
                );
            }catch(IllegalStateException expected){
                postTerminalRejected=
                    "world closed".equals(
                        expected.getMessage()
                    );
            }

            if(!postTerminalRejected)
                throw new AssertionError(
                    "post-terminal realtime scheduling was accepted"
                );

            System.out.println(
                "WORLD_REALTIME_CLOSE_ADMISSION_FENCE_PASS "+
                "preCloseExecuted=true "+
                "closeWindowRejected=true "+
                "closeWindowQueueUnchanged=true "+
                "postTerminalRejected=true"
            );
        }finally{
            releaseTarget.countDown();

            if(closeThread!=null&&
               closeThread.isAlive())
                closeThread.join(5_000L);

            world.close();
        }
    }
}
