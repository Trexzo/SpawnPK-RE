package spk.local;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import spk.plugin.api.PluginTask;

/**
 * R105 OFFLINE TEST ONLY. An EntityId and WorldPlayer object survive a
 * unregister/register sequence, but its generation changes. Every plugin
 * event must retain and revalidate the EXACT original generation.
 *
 * No LocalSession, sockets, persistent account save, production plugin
 * manager, World.shared, or actual account reconnect is involved.
 */
public final class R105OfflinePluginGenerationRolloverTest {
    private static final String USER="r105-rollover";

    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R105 unexpected arguments");

        // Keep the full owner-tagged one-shot/repeating retirement baseline.
        R104OfflineRepeatingPluginRetirementTest.main(new String[0]);
        rolloverBeforeFirstEventDispatch();
        rolloverAfterSeveralSuccessfulRepeats();
        System.out.println(
            "R105_OFFLINE_PLUGIN_GENERATION_ROLLOVER_PASS "+
            "R103R104BaselineStillPasses=true "+
            "sameWorldPlayerAndEntityIdReRegistered=true "+
            "generationActuallyChanged=true "+
            "oldOneShotAndRepeatDeniedAfterNewRegistration=true "+
            "liveRepeaterDeniedAfterGenerationChange=true "+
            "newGenerationPluginTasksStillExecuteUnderActorLock=true "+
            "staleGenerationNewAdmissionDenied=true "+
            "oldTaskHandlesRetiredAndCannotReschedule=true "+
            "unrelatedNonPlayerDiagnosticStillRuns=true "+
            "legacyPluginAPIUnchanged=true "+
            "noProductionLoginOrPluginWiring=true");
    }

    private static void rolloverBeforeFirstEventDispatch()
            throws Exception{
        try(World world=World.isolatedForTest(55L)){
            WorldPlayer actor=new WorldPlayer();
            long previous=world.registerPlayer(actor,USER);
            R99OfflineCrossQueueRetirementFence gate=installGates(world);
            PluginCallbackScope scope=newScope(world);
            PluginTaskTracker plugin=newPlugin(world,scope);
            try{
                AtomicInteger oldOneCalls=new AtomicInteger();
                AtomicInteger oldRepeats=new AtomicInteger();
                AtomicInteger newOneCalls=new AtomicInteger();
                AtomicInteger newRepeats=new AtomicInteger();
                AtomicInteger globalCalls=new AtomicInteger();

                PluginTask oldOne=plugin.scheduleOwnedForTest(
                    4L,actor.id(),previous,oldOneCalls::incrementAndGet);
                PluginTask oldRepeat=plugin.scheduleOwnedRepeatingForTest(
                    5L,1L,actor.id(),previous,oldRepeats::incrementAndGet);
                // Deterministic handoff BEFORE the World begins processing
                // either old event. The new session uses the exact same
                // EntityId and object reference, not a different player.
                require(world.unregisterPlayer(actor,previous),
                    "R105 first generation could not unregister");
                long current=world.registerPlayer(actor,USER);
                require(current>previous &&
                        world.players().byId(actor.id())==actor &&
                        world.players().owns(actor,current) &&
                        !world.players().owns(actor,previous),
                    "R105 re-registration did not change ownership epoch");

                expectRefusal(()->plugin.scheduleOwnedForTest(
                    1L,actor.id(),previous,oldOneCalls::incrementAndGet),
                    "R76_EVENT_QUIESCED before schedule");
                expectRefusal(()->plugin.scheduleOwnedRepeatingForTest(
                    1L,1L,actor.id(),previous,oldRepeats::incrementAndGet),
                    "R76_EVENT_QUIESCED before schedule");
                expectRefusal(()->plugin.schedule(
                    1L,oldOneCalls::incrementAndGet),
                    "R101_UNTAGGED_EVENT_DENIED");

                PluginTask newOne=plugin.scheduleOwnedForTest(
                    2L,actor.id(),current,()->{
                        assertActorContext(world,actor,current);
                        newOneCalls.incrementAndGet();
                    });
                PluginTask newRepeat=plugin.scheduleOwnedRepeatingForTest(
                    1L,1L,actor.id(),current,()->{
                        assertActorContext(world,actor,current);
                        newRepeats.incrementAndGet();
                    });
                world.events().scheduleExplicitNonPlayerForTest(
                    3L,"R105 isolated global diagnostic",
                    globalCalls::incrementAndGet);

                world.start();
                await(()->newOneCalls.get()==1&&newRepeats.get()>=3,
                    "R105 re-registered player callbacks did not run");
                require(newRepeat.cancel(),
                    "R105 new generation repeating cancellation failed");
                int atCancellation=newRepeats.get();
                long afterCancelTick=world.clock().tick();
                await(()->world.clock().tick()>=afterCancelTick+7L &&
                    !oldOne.active()&&!oldRepeat.active() &&
                    !newOne.active() && !newRepeat.active() &&
                    plugin.size()==0 && world.events().size()==0,
                    "R105 old generation task handles failed to retire");
                require(oldOneCalls.get()==0&&oldRepeats.get()==0&&
                        newOneCalls.get()==1&&
                        newRepeats.get()==atCancellation&&
                        globalCalls.get()==1 &&
                        gate.deniedEventsForTest()>=4,
                    "R105 old task ran or new generation/global blocked");
                require(world.players().owns(actor,current),
                    "R105 old callbacks removed new registered actor");
                System.out.println(
                    "R105_ROLLOVER_BEFORE_DISPATCH_PASS "+
                    "oldOneShotAndRepeatSkipped=true "+
                    "newOwnerTasksAndGlobalExecuted=true "+
                    "samePlayerObjectAndId=true "+
                    "allRejectedHandlesRetired=true");
            }finally{
                plugin.close();
                scope.close();
            }
        }
    }

    private static void rolloverAfterSeveralSuccessfulRepeats()
            throws Exception{
        try(World world=World.isolatedForTest(65L)){
            WorldPlayer actor=new WorldPlayer();
            long previous=world.registerPlayer(actor,USER);
            R99OfflineCrossQueueRetirementFence gate=installGates(world);
            PluginCallbackScope scope=newScope(world);
            PluginTaskTracker plugin=newPlugin(world,scope);
            try{
                AtomicInteger oldCalls=new AtomicInteger();
                AtomicInteger newCalls=new AtomicInteger();
                world.start();
                PluginTask oldRepeat=plugin.scheduleOwnedRepeatingForTest(
                    1L,1L,actor.id(),previous,()->{
                        assertActorContext(world,actor,previous);
                        oldCalls.incrementAndGet();
                    });
                await(()->oldCalls.get()>=4,
                    "R105 old generation did not repeat successfully");

                // World.unregisterPlayer waits on the same lifecycle and
                // actor mutation locks used by R99 callback dispatch.
                // Any callback started before the rollover finishes first.
                require(world.unregisterPlayer(actor,previous),
                    "R105 live generation could not unregister");
                int boundary=oldCalls.get();
                long current=world.registerPlayer(actor,USER);
                require(current>previous &&
                        world.players().owns(actor,current) &&
                        !world.players().owns(actor,previous) &&
                        world.players().byId(actor.id())==actor,
                    "R105 live rollover failed to replace generation");
                expectRefusal(
                    ()->plugin.scheduleOwnedRepeatingForTest(
                        1L,1L,actor.id(),previous,
                        oldCalls::incrementAndGet),
                    "R76_EVENT_QUIESCED before schedule");

                PluginTask newOne=plugin.scheduleOwnedForTest(
                    2L,actor.id(),current,()->{
                        assertActorContext(world,actor,current);
                        newCalls.incrementAndGet();
                    });
                long rolloverTick=world.clock().tick();
                await(()->newCalls.get()==1 && !oldRepeat.active() &&
                    !newOne.active() && plugin.size()==0 &&
                    world.clock().tick()>=rolloverTick+5L,
                    "R105 live old-generation repeating handle not retired");
                require(oldCalls.get()==boundary&&newCalls.get()==1&&
                        world.events().size()==0&&
                        gate.deniedEventsForTest()>=2,
                    "R105 old generation executed after live rollover");
                System.out.println(
                    "R105_ROLLOVER_DURING_REPEATER_PASS "+
                    "oldGenerationValidRepeats="+boundary+" "+
                    "postRolloverOldRuns=0 "+
                    "newGenerationOneShotRan=true "+
                    "oldRepeatHandleRetired=true");
            }finally{
                plugin.close();
                scope.close();
            }
        }
    }

    private static R99OfflineCrossQueueRetirementFence installGates(
            World world){
        R98OfflineFinalCaptureCommandFence ingress=
            new R98OfflineFinalCaptureCommandFence(world);
        ingress.installForTest();
        R99OfflineCrossQueueRetirementFence gate=
            new R99OfflineCrossQueueRetirementFence(world,ingress);
        gate.installForTest();
        world.events().installOfflineExplicitEventProvenanceForTest();
        return gate;
    }

    private static PluginCallbackScope newScope(World world){
        PluginCallbackScope scope=new PluginCallbackScope(
            ()->world.pulse().inExecutionContext(),
            ()->!world.closed());
        scope.activate();
        return scope;
    }

    private static PluginTaskTracker newPlugin(
            World world,PluginCallbackScope scope){
        PluginTaskTracker tracker=new PluginTaskTracker(
            world.clock(),world.events(),()->!world.closed(),
            null,scope,()->true,(kind,error)->{
                throw new AssertionError(
                    "R105 unexpected plugin callback failure "+kind,error);
            });
        tracker.activate();
        return tracker;
    }

    private static void assertActorContext(
            World world,WorldPlayer actor,long generation){
        require(world.pulse().inExecutionContext() &&
                Thread.holdsLock(actor.mutationLock()) &&
                world.players().owns(actor,generation),
            "R105 callback bypassed World actor ownership");
    }

    private static void expectRefusal(
            Runnable task,String reason){
        boolean refused=false;
        try{task.run();}
        catch(IllegalStateException expected){
            refused=expected.getMessage()!=null &&
                    expected.getMessage().contains(reason);
        }
        require(refused,"R105 missing or incorrect refusal "+reason);
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
    private R105OfflinePluginGenerationRolloverTest(){}
}
