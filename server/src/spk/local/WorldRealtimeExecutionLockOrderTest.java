package spk.local;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldRealtimeExecutionLockOrderTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "realtime-lock-order"
            );

        CountDownLatch taskEntered=
            new CountDownLatch(1);
        CountDownLatch allowLifecycleReentry=
            new CountDownLatch(1);
        CountDownLatch lifecycleReentered=
            new CountDownLatch(1);
        CountDownLatch releaseTask=
            new CountDownLatch(1);

        AtomicBoolean lifecycleReentryWorked=
            new AtomicBoolean();
        AtomicBoolean unregisterResult=
            new AtomicBoolean();
        AtomicBoolean staleExecuted=
            new AtomicBoolean();
        AtomicReference<Throwable> runFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> unregisterFailure=
            new AtomicReference<>();

        world.scheduleRealtime(
            System.currentTimeMillis(),
            player,
            generation,
            ()->{
                taskEntered.countDown();

                try{
                    if(!allowLifecycleReentry.await(
                            5L,
                            TimeUnit.SECONDS))
                        throw new AssertionError(
                            "lifecycle reentry release timeout"
                        );

                    boolean open=
                        world.runIfOpen(
                            ()->lifecycleReentryWorked
                                .set(true)
                        );

                    if(!open)
                        throw new AssertionError(
                            "World unexpectedly closed during realtime task"
                        );

                    lifecycleReentered.countDown();

                    if(!releaseTask.await(
                            5L,
                            TimeUnit.SECONDS))
                        throw new AssertionError(
                            "realtime task release timeout"
                        );
                }catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new AssertionError(
                        "realtime task interrupted",
                        interrupted
                    );
                }
            }
        );

        Thread runThread=
            new Thread(
                ()->{
                    try{
                        world.realtime().runDue(
                            System.currentTimeMillis()+
                            1_000L
                        );
                    }catch(Throwable failure){
                        runFailure.set(failure);
                    }
                },
                "realtime-lock-order-run"
            );

        Thread unregisterThread=
            new Thread(
                ()->{
                    try{
                        unregisterResult.set(
                            world.unregisterPlayer(
                                player,
                                generation
                            )
                        );
                    }catch(Throwable failure){
                        unregisterFailure.set(
                            failure
                        );
                    }
                },
                "realtime-lock-order-unregister"
            );

        runThread.setDaemon(true);
        unregisterThread.setDaemon(true);

        try{
            runThread.start();

            if(!taskEntered.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "realtime task did not enter"
                );

            unregisterThread.start();

            long blockedDeadline=
                System.nanoTime()+
                TimeUnit.SECONDS.toNanos(2L);

            while(unregisterThread.getState()!=
                    Thread.State.BLOCKED&&
                  unregisterThread.isAlive()&&
                  System.nanoTime()<blockedDeadline)
                Thread.sleep(2L);

            if(!unregisterThread.isAlive())
                throw new AssertionError(
                    "unregister completed while realtime task was active"
                );

            allowLifecycleReentry.countDown();

            if(!lifecycleReentered.await(
                    2L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "realtime task could not re-enter World lifecycle; lock inversion remains"
                );

            if(!lifecycleReentryWorked.get())
                throw new AssertionError(
                    "realtime lifecycle reentry action did not execute"
                );

            if(!unregisterThread.isAlive())
                throw new AssertionError(
                    "unregister crossed active realtime task"
                );

            if(!world.players().owns(
                    player,
                    generation
                ))
                throw new AssertionError(
                    "player ownership disappeared during realtime task"
                );

            releaseTask.countDown();

            runThread.join(5_000L);

            if(runThread.isAlive())
                throw new AssertionError(
                    "runDue did not finish"
                );

            if(runFailure.get()!=null)
                throw new AssertionError(
                    "runDue failed",
                    runFailure.get()
                );

            unregisterThread.join(5_000L);

            if(unregisterThread.isAlive())
                throw new AssertionError(
                    "unregister did not finish after realtime task"
                );

            if(unregisterFailure.get()!=null)
                throw new AssertionError(
                    "unregister failed",
                    unregisterFailure.get()
                );

            if(!unregisterResult.get())
                throw new AssertionError(
                    "unregister returned false"
                );

            boolean staleRejected=false;

            try{
                world.scheduleRealtime(
                    System.currentTimeMillis(),
                    player,
                    generation,
                    ()->staleExecuted.set(true)
                );
            }catch(IllegalStateException expected){
                staleRejected=true;
            }

            if(!staleRejected)
                throw new AssertionError(
                    "stale generation realtime scheduling was not rejected"
                );

            world.realtime().runDue(
                System.currentTimeMillis()+
                2_000L
            );

            if(staleExecuted.get())
                throw new AssertionError(
                    "stale realtime task executed"
                );

            System.out.println(
                "WORLD_REALTIME_EXECUTION_LOCK_ORDER_PASS "+
                "taskEntered=true "+
                "lifecycleReentryWorked=true "+
                "unregisterBlockedDuringTask=true "+
                "taskCompletedBeforeUnregister=true "+
                "staleTaskRejected=true"
            );
        }finally{
            allowLifecycleReentry.countDown();
            releaseTask.countDown();

            runThread.join(1_000L);
            unregisterThread.join(1_000L);

            boolean threadsStopped=
                !runThread.isAlive()&&
                !unregisterThread.isAlive();

            if(threadsStopped){
                if(player.registered())
                    world.unregisterPlayer(
                        player,
                        player.generation()
                    );

                world.close();
            }
        }
    }

    private WorldRealtimeExecutionLockOrderTest(){}
}
