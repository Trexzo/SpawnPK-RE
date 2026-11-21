package spk.local;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import spk.plugin.api.PluginTask;

/**
 * R106 OFFLINE ONLY: same username does not imply same registered actor.
 * Two different WorldPlayer objects have different EntityIds but can
 * simultaneously have the SAME numeric registration generation.
 *
 * No authenticated reconnect, LocalSession, World.shared, socket, or
 * production plugin path is involved.
 */
public final class R106OfflinePluginActorReplacementTest {
    private static final String USER="r106-returning";

    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R106 unexpected arguments");
        R105OfflinePluginGenerationRolloverTest.main(new String[0]);
        replaceBeforeQueuedTasksDue();
        replaceWhileRepeaterIsActive();
        System.out.println(
            "R106_OFFLINE_PLUGIN_ACTOR_REPLACEMENT_PASS "+
            "R103R104R105InheritedBaseline=true "+
            "sameUsernameDifferentWorldPlayerObjects=true "+
            "newAndOldNumericGenerationsEqual=true "+
            "distinctEntityIdRequiredForOwnerMatch=true "+
            "duplicateUsernameSimultaneousLoginDenied=true "+
            "queuedOldActorTasksSuppressedAfterReplacement=true "+
            "activeOldActorRepeaterSuppressedAfterReplacement=true "+
            "oldPluginHandlesRetiredWithoutRequeue=true "+
            "replacementActorCallbacksRunUnderNewActorLock=true "+
            "unrelatedGlobalEventStillRuns=true "+
            "noProductionLoginOrPluginWiring=true");
    }

    private static void replaceBeforeQueuedTasksDue()throws Exception{
        try(Fixture f=new Fixture(60L)){
            WorldPlayer oldPlayer=new WorldPlayer();
            long oldGeneration=f.world.registerPlayer(oldPlayer,USER);
            AtomicInteger oldOneCalls=new AtomicInteger();
            AtomicInteger oldRepeatCalls=new AtomicInteger();
            AtomicInteger newOneCalls=new AtomicInteger();
            AtomicInteger newRepeatCalls=new AtomicInteger();
            AtomicInteger globalCalls=new AtomicInteger();

            PluginTask oldOne=f.plugin.scheduleOwnedForTest(
                4L,oldPlayer.id(),oldGeneration,
                oldOneCalls::incrementAndGet);
            PluginTask oldRepeat=f.plugin.scheduleOwnedRepeatingForTest(
                5L,1L,oldPlayer.id(),oldGeneration,
                oldRepeatCalls::incrementAndGet);

            WorldPlayer incoming=new WorldPlayer();
            expectRefusal(()->f.world.registerPlayer(incoming,USER),
                "DUPLICATE_LOGIN");
            require(f.world.players().owns(oldPlayer,oldGeneration),
                "R106 duplicate attempt disturbed original owner");
            require(f.world.unregisterPlayer(oldPlayer,oldGeneration),
                "R106 old player could not unregister");
            long incomingGeneration=f.world.registerPlayer(incoming,USER);
            assertReplacement(f,oldPlayer,oldGeneration,
                incoming,incomingGeneration);

            // A new actor often starts at generation=1, exactly the
            // prior actor's generation. EntityId MUST also match.
            expectRefusal(()->f.plugin.scheduleOwnedForTest(
                1L,oldPlayer.id(),incomingGeneration,
                oldOneCalls::incrementAndGet),
                "R76_EVENT_QUIESCED before schedule");
            expectRefusal(()->f.plugin.scheduleOwnedRepeatingForTest(
                1L,1L,oldPlayer.id(),incomingGeneration,
                oldRepeatCalls::incrementAndGet),
                "R76_EVENT_QUIESCED before schedule");
            expectRefusal(()->f.plugin.schedule(
                1L,oldOneCalls::incrementAndGet),
                "R101_UNTAGGED_EVENT_DENIED");

            PluginTask newOne=f.plugin.scheduleOwnedForTest(
                2L,incoming.id(),incomingGeneration,()->{
                    assertWorldOwned(f.world,incoming,incomingGeneration);
                    newOneCalls.incrementAndGet();
                });
            PluginTask newRepeat=f.plugin.scheduleOwnedRepeatingForTest(
                1L,1L,incoming.id(),incomingGeneration,()->{
                    assertWorldOwned(f.world,incoming,incomingGeneration);
                    newRepeatCalls.incrementAndGet();
                });
            f.world.events().scheduleExplicitNonPlayerForTest(
                3L,"R106 isolated global diagnostic",
                globalCalls::incrementAndGet);
            f.world.start();

            await(()->newOneCalls.get()==1&&
                newRepeatCalls.get()>=3&&globalCalls.get()==1,
                "R106 new object/diagnostic not dispatched");
            require(newRepeat.cancel(),
                "R106 new actor repeating cancellation failed");
            int afterCancel=newRepeatCalls.get();
            long cancelTick=f.world.clock().tick();
            await(()->f.world.clock().tick()>=cancelTick+7L &&
                !oldOne.active()&&!oldRepeat.active()&&
                !newOne.active()&&!newRepeat.active()&&
                f.plugin.size()==0&&f.world.events().size()==0,
                "R106 old queued tasks failed to retire after replacement");
            require(oldOneCalls.get()==0&&oldRepeatCalls.get()==0&&
                newOneCalls.get()==1&&newRepeatCalls.get()==afterCancel&&
                globalCalls.get()==1&&f.gate.deniedEventsForTest()>=4&&
                f.world.players().owns(incoming,incomingGeneration),
                "R106 queued old actor mutated or replaced new actor");
            System.out.println(
                "R106_REPLACEMENT_BEFORE_DISPATCH_PASS "+
                "equalNumericGenerationDistinctEntityIds=true "+
                "oldOneShotAndRepeatNeverRan=true "+
                "newActorOneShotAndRepeatsRan=true "+
                "globalDiagnosticRan=true "+
                "oldAndNewHandlesRetired=true");
        }
    }

    private static void replaceWhileRepeaterIsActive()throws Exception{
        try(Fixture f=new Fixture(70L)){
            WorldPlayer oldPlayer=new WorldPlayer();
            long oldGeneration=f.world.registerPlayer(oldPlayer,USER);
            AtomicInteger oldCalls=new AtomicInteger();
            AtomicInteger newCalls=new AtomicInteger();
            AtomicInteger extraOldCalls=new AtomicInteger();
            f.world.start();
            PluginTask oldRepeating=f.plugin.scheduleOwnedRepeatingForTest(
                1L,1L,oldPlayer.id(),oldGeneration,()->{
                    assertWorldOwned(f.world,oldPlayer,oldGeneration);
                    oldCalls.incrementAndGet();
                });
            await(()->oldCalls.get()>=4,
                "R106 old actor repeating callback never ran");

            // World.unregisterPlayer serializes with the actual player
            // mutation lock and R99's due-dispatch ownership check.
            require(f.world.unregisterPlayer(oldPlayer,oldGeneration),
                "R106 active old actor could not unregister");
            int boundary=oldCalls.get();
            WorldPlayer incoming=new WorldPlayer();
            long incomingGeneration=f.world.registerPlayer(incoming,USER);
            assertReplacement(f,oldPlayer,oldGeneration,
                incoming,incomingGeneration);
            expectRefusal(()->f.plugin.scheduleOwnedForTest(
                1L,oldPlayer.id(),incomingGeneration,
                extraOldCalls::incrementAndGet),
                "R76_EVENT_QUIESCED before schedule");
            expectRefusal(()->f.plugin.scheduleOwnedRepeatingForTest(
                1L,1L,oldPlayer.id(),oldGeneration,
                extraOldCalls::incrementAndGet),
                "R76_EVENT_QUIESCED before schedule");
            PluginTask newOne=f.plugin.scheduleOwnedForTest(
                2L,incoming.id(),incomingGeneration,()->{
                    assertWorldOwned(f.world,incoming,incomingGeneration);
                    newCalls.incrementAndGet();
                });
            long handoffTick=f.world.clock().tick();
            await(()->newCalls.get()==1&&!oldRepeating.active()&&
                !newOne.active()&&f.plugin.size()==0&&
                f.world.clock().tick()>=handoffTick+5L,
                "R106 previous actor repeater remained active");
            require(oldCalls.get()==boundary&&newCalls.get()==1&&
                extraOldCalls.get()==0&&f.world.events().size()==0&&
                f.gate.deniedEventsForTest()>=3&&
                f.world.players().owns(incoming,incomingGeneration),
                "R106 previous actor callback ran after new owner appeared");
            System.out.println(
                "R106_REPLACEMENT_DURING_REPEATER_PASS "+
                "oldActorValidRepeats="+boundary+" "+
                "oldActorRunsAfterUnregister=0 "+
                "replacementActorOneShotRan=true "+
                "oldRepeaterHandleRetired=true "+
                "numericGenerationsEqualButIdsDifferent=true");
        }
    }

    private static void assertReplacement(Fixture f,
        WorldPlayer former,long oldGeneration,
        WorldPlayer current,long newGeneration){
        require(former!=current&&
            !former.id().equals(current.id())&&
            oldGeneration==newGeneration&&
            f.world.players().byId(former.id())==null&&
            f.world.players().byId(current.id())==current&&
            f.world.players().byName(USER)==current&&
            !f.world.players().owns(former,oldGeneration)&&
            f.world.players().owns(current,newGeneration),
            "R106 did not establish distinct actors with same generation");
    }

    private static void assertWorldOwned(World world,
        WorldPlayer player,long generation){
        require(world.pulse().inExecutionContext()&&
            Thread.holdsLock(player.mutationLock())&&
            world.players().owns(player,generation),
            "R106 plugin callback did not own the exact World actor");
    }

    private static final class Fixture implements AutoCloseable {
        final World world;
        final R99OfflineCrossQueueRetirementFence gate;
        final PluginCallbackScope scope;
        final PluginTaskTracker plugin;

        Fixture(long tickMillis){
            world=World.isolatedForTest(tickMillis);
            R98OfflineFinalCaptureCommandFence finalCapture=
                new R98OfflineFinalCaptureCommandFence(world);
            finalCapture.installForTest();
            gate=new R99OfflineCrossQueueRetirementFence(
                world,finalCapture);
            gate.installForTest();
            world.events().installOfflineExplicitEventProvenanceForTest();
            scope=new PluginCallbackScope(
                ()->world.pulse().inExecutionContext(),
                ()->!world.closed());
            scope.activate();
            plugin=new PluginTaskTracker(
                world.clock(),world.events(),()->!world.closed(),
                null,scope,()->true,(kind,error)->{
                    throw new AssertionError(
                        "R106 plugin callback failure "+kind,error);
                });
            plugin.activate();
        }

        @Override public void close(){
            try{plugin.close();}
            finally{
                try{scope.close();}
                finally{world.close();}
            }
        }
    }

    private static void expectRefusal(Runnable action,String reason){
        boolean refused=false;
        try{action.run();}
        catch(IllegalStateException expected){
            refused=expected.getMessage()!=null&&
                expected.getMessage().contains(reason);
        }
        require(refused,"R106 missing or incorrect refusal "+reason);
    }

    private static void await(BooleanSupplier condition,String message)
            throws InterruptedException{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12L);
        while(!condition.getAsBoolean()){
            require(System.nanoTime()<deadline,message);
            Thread.sleep(12L);
        }
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private R106OfflinePluginActorReplacementTest(){}
}
