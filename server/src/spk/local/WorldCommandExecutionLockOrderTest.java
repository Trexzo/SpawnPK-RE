package spk.local;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldCommandExecutionLockOrderTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "command-lock-order"
            );

        CountDownLatch commandEntered=
            new CountDownLatch(1);
        CountDownLatch allowLifecycleReentry=
            new CountDownLatch(1);
        CountDownLatch lifecycleReentered=
            new CountDownLatch(1);
        CountDownLatch releaseCommand=
            new CountDownLatch(1);

        AtomicBoolean lifecycleReentryWorked=
            new AtomicBoolean();
        AtomicBoolean unregisterResult=
            new AtomicBoolean();
        AtomicBoolean staleExecuted=
            new AtomicBoolean();
        AtomicReference<Throwable> drainFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> unregisterFailure=
            new AtomicReference<>();

        CompletableFuture<Void> commandFuture=
            world.submit(
                player,
                generation,
                ()->{
                    commandEntered.countDown();

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
                            "World unexpectedly closed during command"
                        );

                    lifecycleReentered.countDown();

                    if(!releaseCommand.await(
                            5L,
                            TimeUnit.SECONDS))
                        throw new AssertionError(
                            "command release timeout"
                        );
                }
            );

        Thread drainThread=
            new Thread(
                ()->{
                    try{
                        world.commands().drain(
                            1,
                            1
                        );
                    }catch(Throwable failure){
                        drainFailure.set(
                            failure
                        );
                    }
                },
                "command-lock-order-drain"
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
                "command-lock-order-unregister"
            );

        drainThread.setDaemon(true);
        unregisterThread.setDaemon(true);

        try{
            drainThread.start();

            if(!commandEntered.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "World command did not enter"
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
                    "unregister completed while command was active"
                );

            allowLifecycleReentry.countDown();

            if(!lifecycleReentered.await(
                    2L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "command could not re-enter World lifecycle; lock inversion remains"
                );

            if(!lifecycleReentryWorked.get())
                throw new AssertionError(
                    "lifecycle reentry action did not execute"
                );

            if(!unregisterThread.isAlive())
                throw new AssertionError(
                    "unregister crossed active command"
                );

            if(!world.players().owns(
                    player,
                    generation
                ))
                throw new AssertionError(
                    "player ownership disappeared during command"
                );

            releaseCommand.countDown();

            drainThread.join(5_000L);

            if(drainThread.isAlive())
                throw new AssertionError(
                    "command drain did not finish"
                );

            if(drainFailure.get()!=null)
                throw new AssertionError(
                    "command drain failed",
                    drainFailure.get()
                );

            commandFuture.get(
                5L,
                TimeUnit.SECONDS
            );

            unregisterThread.join(5_000L);

            if(unregisterThread.isAlive())
                throw new AssertionError(
                    "unregister did not finish after command"
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

            CompletableFuture<Void> stale=
                world.submit(
                    player,
                    generation,
                    ()->staleExecuted.set(true)
                );

            boolean staleRejected=false;

            try{
                stale.join();
            }catch(CompletionException failure){
                staleRejected=
                    failure.getCause() instanceof
                        java.util.concurrent.CancellationException;
            }catch(java.util.concurrent.CancellationException failure){
                staleRejected=true;
            }

            if(!staleRejected)
                throw new AssertionError(
                    "stale generation command was not rejected"
                );

            if(staleExecuted.get())
                throw new AssertionError(
                    "stale generation command executed"
                );

            System.out.println(
                "WORLD_COMMAND_EXECUTION_LOCK_ORDER_PASS "+
                "commandEntered=true "+
                "lifecycleReentryWorked=true "+
                "unregisterBlockedDuringCommand=true "+
                "commandCompletedBeforeUnregister=true "+
                "staleCommandRejected=true"
            );
        }finally{
            allowLifecycleReentry.countDown();
            releaseCommand.countDown();

            drainThread.join(1_000L);
            unregisterThread.join(1_000L);

            boolean threadsStopped=
                !drainThread.isAlive()&&
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

    private WorldCommandExecutionLockOrderTest(){}
}
