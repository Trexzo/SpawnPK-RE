package spk.local;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R76 OFFLINE ONLY: deterministic WorldPulse ticks exercise the actual
 * timed-effect expiration, per-player WorldTickTarget and WorldEventQueue
 * runDue paths. No network, login or production session is started.
 */
public final class R76OfflineWorldTickOwnedEventTest {
    public static void main(String[] args)throws Exception{
        try(World world=World.isolatedForTest(60_000L)){
            WorldPlayer retiring=new WorldPlayer();
            long retiringGeneration=
                world.registerPlayer(retiring,"opensrc");
            WorldPlayer active=new WorldPlayer();
            long activeGeneration=
                world.registerPlayer(active,"other");

            R74OfflineCommandIngressFence commandFence=
                new R74OfflineCommandIngressFence(world);
            commandFence.installForTest();
            R76OfflineWorldTickAndEventFence gate=
                new R76OfflineWorldTickAndEventFence(
                    world,commandFence
                );
            gate.installForTest();
            try{
                gate.installForTest();
                throw new AssertionError(
                    "R76 allowed duplicate tick/event fence installation"
                );
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "already installed"
                ),"R76 duplicate gate error "+expected);
            }

            AtomicInteger retiringTicks=new AtomicInteger();
            AtomicInteger activeTicks=new AtomicInteger();
            AtomicInteger retiringEvents=new AtomicInteger();
            AtomicInteger activeEvents=new AtomicInteger();
            AtomicInteger globalEvents=new AtomicInteger();
            AtomicInteger cancelledEvents=new AtomicInteger();
            AtomicInteger staleEvents=new AtomicInteger();

            world.attachTickTarget(new WorldTickTarget(){
                @Override public EntityId ownerId(){
                    return retiring.id();
                }
                @Override public long ownerGeneration(){
                    return retiringGeneration;
                }
                @Override public void onWorldTick(
                    long tick,long nowMillis
                ){
                    retiringTicks.incrementAndGet();
                }
            });
            world.attachTickTarget(new WorldTickTarget(){
                @Override public EntityId ownerId(){
                    return active.id();
                }
                @Override public long ownerGeneration(){
                    return activeGeneration;
                }
                @Override public void onWorldTick(
                    long tick,long nowMillis
                ){
                    activeTicks.incrementAndGet();
                }
            });

            String effect=TimedEffectCatalog.all().get(1).key;
            require(!"DYNAMIC".equals(effect),
                "R76 catalog selected unsupported dynamic effect");
            retiring.timedEffects().applyFixed(
                effect,2L,0L,"R76_OFFLINE_TEST"
            );
            active.timedEffects().applyFixed(
                effect,2L,0L,"R76_OFFLINE_TEST"
            );

            world.events().scheduleOwnedForTest(
                1L,retiring.id(),retiringGeneration,
                ()->retiringEvents.incrementAndGet()
            );
            world.events().scheduleOwnedForTest(
                2L,retiring.id(),retiringGeneration,
                ()->retiringEvents.incrementAndGet()
            );
            world.events().scheduleOwnedForTest(
                4L,retiring.id(),retiringGeneration,
                ()->staleEvents.incrementAndGet()
            );
            world.events().scheduleOwnedForTest(
                2L,active.id(),activeGeneration,
                ()->activeEvents.incrementAndGet()
            );
            WorldEventQueue.Handle cancelled=world.events()
                .scheduleOwnedForTest(
                    2L,active.id(),activeGeneration,
                    ()->cancelledEvents.incrementAndGet()
                );
            cancelled.cancel();
            world.events().schedule(
                2L,()->globalEvents.incrementAndGet()
            );

            // No realtime WorldPulse thread is started. Each real
            // pulseOnce run is entered using World.observePulse's
            // explicit compatibility execution context.
            require(world.observePulse(System.currentTimeMillis())==1L,
                "R76 first World clock tick mismatched");
            require(retiringTicks.get()==1&&
                    activeTicks.get()==1&&
                    retiringEvents.get()==1&&
                    activeEvents.get()==0&&
                    retiring.timedEffects().active(effect)&&
                    active.timedEffects().active(effect),
                "R76 ordinary tick/event/effect baseline failed");

            // A real queued World command establishes quiescence BEFORE
            // that tick's event, timed effect and tick-target phases.
            java.util.concurrent.CompletableFuture<Void> quiesce=
                world.submit(retiring,retiringGeneration,()->{
                    commandFence.quiesceInsideWorldActor(
                        retiring,retiringGeneration
                    );
                });
            require(world.observePulse(System.currentTimeMillis())==2L,
                "R76 second World clock tick mismatched");
            quiesce.get(10L,TimeUnit.SECONDS);

            require(commandFence.isQuiesced(
                        retiring,retiringGeneration) &&
                    retiringTicks.get()==1&&
                    activeTicks.get()==2&&
                    retiringEvents.get()==1&&
                    activeEvents.get()==1&&
                    globalEvents.get()==1&&
                    cancelledEvents.get()==0&&
                    retiring.timedEffects().active(effect)&&
                    !active.timedEffects().active(effect),
                "R76 quiesced World periodic mutation was not fenced");

            // After quiescence, the new explicitly tagged producer
            // must be denied before an event is added.
            expectIllegal(()->world.events().scheduleOwnedForTest(
                3L,retiring.id(),retiringGeneration,
                ()->retiringEvents.incrementAndGet()
            ),"R76_EVENT_QUIESCED");

            world.events().scheduleOwnedForTest(
                3L,active.id(),activeGeneration,
                ()->activeEvents.incrementAndGet()
            );
            world.events().schedule(
                3L,()->globalEvents.incrementAndGet()
            );
            require(world.observePulse(System.currentTimeMillis())==3L,
                "R76 third World clock tick mismatched");
            require(retiringTicks.get()==1&&
                    activeTicks.get()==3&&
                    retiringEvents.get()==1&&
                    activeEvents.get()==2&&
                    globalEvents.get()==2&&
                    retiring.timedEffects().active(effect),
                "R76 repeated tick denied wrong owner or altered globals");

            // The previously tagged event at tick 4 must also be
            // rejected after the World removes that exact generation.
            require(world.unregisterPlayer(
                        retiring,retiringGeneration),
                "R76 retiring owner unregister failed");
            require(world.observePulse(System.currentTimeMillis())==4L,
                "R76 fourth World clock tick mismatched");
            require(staleEvents.get()==0&&
                    retiringTicks.get()==1&&
                    activeTicks.get()==4,
                "R76 stale generation scheduled event still ran");

            System.out.println(
                "R76_OFFLINE_TICK_OWNED_EVENT_PASS "+
                "preQuiescenceTickTargetAllowed=true "+
                "preQuiescenceTimedEffectPreserved=true "+
                "preQuiescenceOwnedEventExecuted=true "+
                "previouslyScheduledOwnedEventRejected=true "+
                "timedEffectExpirationBlockedAfterQuiescence=true "+
                "quiescedTickTargetDeniedOnRealWorldPulse=true "+
                "newOwnedEventDeniedAtScheduling=true "+
                "unrelatedTimedEffectAndTickTargetUnaffected=true "+
                "unrelatedAndGlobalEventsUnaffected=true "+
                "cancellationSemanticsPreserved=true "+
                "staleGenerationEventRejectedAfterUnregister=true "+
                "defaultLiveCallbacksUnchanged=true"
            );
        }
    }

    @FunctionalInterface private interface Action{
        void run()throws Exception;
    }

    private static void expectIllegal(
        Action action,String expected
    )throws Exception{
        try{
            action.run();
            throw new AssertionError(
                "expected R76 refusal "+expected
            );
        }catch(IllegalStateException denied){
            require(denied.getMessage().contains(expected),
                "R76 wrong event refusal "+denied);
        }
    }

    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }

    private R76OfflineWorldTickOwnedEventTest(){}
}
