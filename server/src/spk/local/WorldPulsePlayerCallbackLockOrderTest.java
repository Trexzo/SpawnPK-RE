package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldPulsePlayerCallbackLockOrderTest {
    public static void main(String[] args)throws Exception{
        assertLocalSessionTickGateQuiescence();
        assertLocalSessionWorldCommandGateQuiescence();

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
                "staleTargetRejected=true "+
                "localSessionTickQuiescedBeforeTeardown=true "+
                "lateSnapshottedTickRejected=true "+
                "localSessionWorldCommandGate=true"
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

    private static void assertLocalSessionWorldCommandGateQuiescence()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "session-command-gate"
            );

        LocalSession.WorldTickGate gate=
            new LocalSession.WorldTickGate();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{71,72,73,74}
                )
            );

        CountDownLatch activeEntered=
            new CountDownLatch(1);
        CountDownLatch releaseActive=
            new CountDownLatch(1);
        CountDownLatch disableCompleted=
            new CountDownLatch(1);

        AtomicBoolean lateGameplayRan=
            new AtomicBoolean();
        AtomicBoolean terminalGameplayRan=
            new AtomicBoolean();
        AtomicReference<Throwable> drainFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> disableFailure=
            new AtomicReference<>();

        gate.activate();

        CompletableFuture<Void> activeFuture=
            world.submit(
                player,
                generation,
                ()->{
                    boolean accepted=
                        gate.runIfActiveAndWriterLive(
                            writer,
                            ()->{
                                activeEntered.countDown();

                                if(!releaseActive.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "session command active release timeout"
                                    );
                            }
                        );

                    if(!accepted)
                        throw new AssertionError(
                            "active session World command was rejected"
                        );
                }
            );

        CompletableFuture<Void> lateFuture=
            world.submit(
                player,
                generation,
                ()->gate.runIfActiveAndWriterLive(
                    writer,
                    ()->lateGameplayRan.set(
                        true
                    )
                )
            );

        Thread drainThread=
            new Thread(
                ()->{
                    try{
                        int drained=
                            world.commands()
                                .drain(
                                    1,
                                    1
                                );

                        if(drained!=1)
                            throw new AssertionError(
                                "expected one active session command drain, got "+
                                drained
                            );
                    }catch(Throwable failure){
                        drainFailure.set(
                            failure
                        );
                    }
                },
                "localsession-command-gate-active"
            );

        Thread disableThread=
            new Thread(
                ()->{
                    try{
                        gate.disableAndAwait();
                        disableCompleted.countDown();
                    }catch(Throwable failure){
                        disableFailure.set(
                            failure
                        );
                    }
                },
                "localsession-command-gate-disable"
            );

        drainThread.setDaemon(true);
        disableThread.setDaemon(true);

        try{
            drainThread.start();

            if(!activeEntered.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "active session World command did not enter gate"
                );

            disableThread.start();

            if(disableCompleted.await(
                    150L,
                    TimeUnit.MILLISECONDS))
                throw new AssertionError(
                    "session World-command teardown crossed active command"
                );

            releaseActive.countDown();

            drainThread.join(
                5_000L
            );
            disableThread.join(
                5_000L
            );

            if(drainThread.isAlive()||
               disableThread.isAlive())
                throw new AssertionError(
                    "session World-command gate threads did not quiesce"
                );

            if(drainFailure.get()!=null)
                throw new AssertionError(
                    "active session World-command drain failed",
                    drainFailure.get()
                );

            if(disableFailure.get()!=null)
                throw new AssertionError(
                    "session World-command gate disable failed",
                    disableFailure.get()
                );

            activeFuture.get(
                1L,
                TimeUnit.SECONDS
            );

            if(disableCompleted.getCount()!=0L||
               gate.active())
                throw new AssertionError(
                    "session World-command gate did not disable"
                );

            int lateDrained=
                world.commands()
                    .drain(
                        1,
                        1
                    );

            if(lateDrained!=1)
                throw new AssertionError(
                    "late queued session command was not drained count="+
                    lateDrained
                );

            lateFuture.get(
                1L,
                TimeUnit.SECONDS
            );

            if(lateGameplayRan.get())
                throw new AssertionError(
                    "late queued session command ran gameplay after teardown disable"
                );

            gate.activate();
            writer.markTerminal();

            boolean terminalRejected=false;

            try{
                gate.runIfActiveAndWriterLive(
                    writer,
                    ()->terminalGameplayRan.set(
                        true
                    )
                );
            }catch(java.io.IOException expected){
                terminalRejected=true;
            }

            if(!terminalRejected||
               terminalGameplayRan.get())
                throw new AssertionError(
                    "terminal session writer did not fail closed before World-command gameplay"
                );

            System.out.println(
                "LOCAL_SESSION_WORLD_COMMAND_TEARDOWN_GATE_PASS "+
                "activeCommandQuiesced=true "+
                "lateQueuedCommandRejected=true "+
                "terminalWriterRejected=true"
            );
        }finally{
            releaseActive.countDown();

            drainThread.join(
                1_000L
            );
            disableThread.join(
                1_000L
            );

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );

            world.close();
        }
    }

    private static void assertLocalSessionTickGateQuiescence()
        throws Exception
    {
        LocalSession.WorldTickGate gate=
            new LocalSession.WorldTickGate();

        CountDownLatch callbackEntered=
            new CountDownLatch(1);
        CountDownLatch releaseCallback=
            new CountDownLatch(1);
        CountDownLatch disableCompleted=
            new CountDownLatch(1);

        AtomicBoolean lateCallbackRan=
            new AtomicBoolean();
        AtomicReference<Throwable> callbackFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> disableFailure=
            new AtomicReference<>();

        gate.activate();

        Thread callbackThread=
            new Thread(
                ()->{
                    try{
                        boolean ran=
                            gate.runIfActive(
                                ()->{
                                    callbackEntered.countDown();

                                    if(!releaseCallback.await(
                                            5L,
                                            TimeUnit.SECONDS))
                                        throw new AssertionError(
                                            "LocalSession tick gate callback release timeout"
                                        );
                                }
                            );

                        if(!ran)
                            throw new AssertionError(
                                "active LocalSession tick gate rejected callback"
                            );
                    }catch(Throwable failure){
                        callbackFailure.set(
                            failure
                        );
                    }
                },
                "localsession-worldtick-gate-callback"
            );

        Thread disableThread=
            new Thread(
                ()->{
                    try{
                        gate.disableAndAwait();
                        disableCompleted.countDown();
                    }catch(Throwable failure){
                        disableFailure.set(
                            failure
                        );
                    }
                },
                "localsession-worldtick-gate-disable"
            );

        callbackThread.setDaemon(true);
        disableThread.setDaemon(true);

        try{
            callbackThread.start();

            if(!callbackEntered.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "LocalSession tick gate callback did not enter"
                );

            disableThread.start();

            if(disableCompleted.await(
                    150L,
                    TimeUnit.MILLISECONDS))
                throw new AssertionError(
                    "LocalSession tick teardown crossed active callback"
                );

            releaseCallback.countDown();

            callbackThread.join(
                5_000L
            );
            disableThread.join(
                5_000L
            );

            if(callbackThread.isAlive()||
               disableThread.isAlive())
                throw new AssertionError(
                    "LocalSession tick gate threads did not quiesce"
                );

            if(callbackFailure.get()!=null)
                throw new AssertionError(
                    "LocalSession tick callback failed",
                    callbackFailure.get()
                );

            if(disableFailure.get()!=null)
                throw new AssertionError(
                    "LocalSession tick gate disable failed",
                    disableFailure.get()
                );

            if(disableCompleted.getCount()!=0L||
               gate.active())
                throw new AssertionError(
                    "LocalSession tick gate did not disable after callback exit"
                );

            boolean lateAccepted=
                gate.runIfActive(
                    ()->lateCallbackRan.set(
                        true
                    )
                );

            if(lateAccepted||
               lateCallbackRan.get())
                throw new AssertionError(
                    "late snapshotted LocalSession tick ran after teardown gate disabled"
                );
        }finally{
            releaseCallback.countDown();

            callbackThread.join(
                1_000L
            );
            disableThread.join(
                1_000L
            );
        }
    }

    private WorldPulsePlayerCallbackLockOrderTest(){}
}
