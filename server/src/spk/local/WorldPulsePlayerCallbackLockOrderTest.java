package spk.local;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldPulsePlayerCallbackLockOrderTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "worldpulse-lock-order"
            );

        CountDownLatch callbackEntered=
            new CountDownLatch(1);
        CountDownLatch allowLifecycleReentry=
            new CountDownLatch(1);
        CountDownLatch lifecycleReentered=
            new CountDownLatch(1);
        CountDownLatch releaseCallback=
            new CountDownLatch(1);

        AtomicBoolean lifecycleReentryWorked=
            new AtomicBoolean();
        AtomicBoolean unregisterResult=
            new AtomicBoolean();
        AtomicInteger callbackCount=
            new AtomicInteger();
        AtomicReference<Throwable> pulseFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> unregisterFailure=
            new AtomicReference<>();

        world.attachTickTarget(
            new WorldTickTarget(){
                @Override public EntityId ownerId(){
                    return player.id();
                }

                @Override public long ownerGeneration(){
                    return generation;
                }

                @Override public void onWorldTick(
                    long worldTick,
                    long nowMillis
                )throws Exception{
                    callbackCount.incrementAndGet();
                    callbackEntered.countDown();

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
                            "World unexpectedly closed during callback"
                        );

                    lifecycleReentered.countDown();

                    if(!releaseCallback.await(
                            5L,
                            TimeUnit.SECONDS))
                        throw new AssertionError(
                            "callback release timeout"
                        );
                }
            }
        );

        Thread pulseThread=
            new Thread(
                ()->{
                    try{
                        world.pulse().pulseOnce(
                            System.currentTimeMillis()
                        );
                    }catch(Throwable failure){
                        pulseFailure.set(failure);
                    }
                },
                "worldpulse-lock-order-pulse"
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
                "worldpulse-lock-order-unregister"
            );

        pulseThread.setDaemon(true);
        unregisterThread.setDaemon(true);

        try{
            pulseThread.start();

            if(!callbackEntered.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "tick target callback did not enter"
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
                    "unregister completed while callback was active"
                );

            allowLifecycleReentry.countDown();

            if(!lifecycleReentered.await(
                    2L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "callback could not re-enter World lifecycle; lock inversion remains"
                );

            if(!lifecycleReentryWorked.get())
                throw new AssertionError(
                    "lifecycle reentry action did not execute"
                );

            if(!unregisterThread.isAlive())
                throw new AssertionError(
                    "unregister crossed active callback after lifecycle reentry"
                );

            if(!world.players().owns(
                    player,
                    generation
                ))
                throw new AssertionError(
                    "player ownership disappeared during callback"
                );

            releaseCallback.countDown();

            pulseThread.join(5_000L);

            if(pulseThread.isAlive())
                throw new AssertionError(
                    "WorldPulse callback did not finish"
                );

            if(pulseFailure.get()!=null)
                throw new AssertionError(
                    "WorldPulse callback failed",
                    pulseFailure.get()
                );

            unregisterThread.join(5_000L);

            if(unregisterThread.isAlive())
                throw new AssertionError(
                    "unregister did not finish after callback"
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

            if(player.registered())
                throw new AssertionError(
                    "player remained registered"
                );

            int beforeStalePulse=
                callbackCount.get();

            world.pulse().pulseOnce(
                System.currentTimeMillis()
            );

            if(callbackCount.get()!=
                    beforeStalePulse)
                throw new AssertionError(
                    "stale tick target executed after unregister"
                );

            System.out.println(
                "WORLD_PULSE_PLAYER_CALLBACK_LOCK_ORDER_PASS "+
                "callbackEntered=true "+
                "lifecycleReentryWorked=true "+
                "unregisterBlockedDuringCallback=true "+
                "unregisterCompletedAfterCallback=true "+
                "staleTargetRejected=true"
            );
        }finally{
            allowLifecycleReentry.countDown();
            releaseCallback.countDown();

            pulseThread.join(1_000L);
            unregisterThread.join(1_000L);

            boolean threadsStopped=
                !pulseThread.isAlive()&&
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

    private WorldPulsePlayerCallbackLockOrderTest(){}
}
