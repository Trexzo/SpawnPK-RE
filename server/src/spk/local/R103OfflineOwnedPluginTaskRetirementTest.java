package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import spk.plugin.api.PluginTask;

/**
 * R98 OFFLINE ONLY: actual World command inbox, queued-before-quiesce
 * dispatch, final immutable capture, R89/R90 commit and R96 retirement.
 */
public final class R103OfflineOwnedPluginTaskRetirementTest {
    private static final String USER="opensrc";

    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R103 unexpected arguments");
        Path scratch=Files.createTempDirectory("spk-r98-final-capture-");
        try{
            finalCaptureAndDurableRetirement(
                scratch.resolve("final-capture"));
            unquiescedSaveDoesNotAuthorizeRetirement(
                scratch.resolve("unquiesced"));
            crossQueueAfterFinalRetirement(
                scratch.resolve("r103-final"),true);
            crossQueueUnquiescedPositiveControl(
                scratch.resolve("r103-unquiesced"));
            System.out.println(
                "R103_OFFLINE_OWNED_PLUGIN_TASK_RETIREMENT_PASS "+
                "taggedPluginTaskUsesRealPluginTaskTracker=true "+
                "R76OwnerGenerationRequiredAtAdmission=true "+
                "R99DeniesQueuedPluginAfterR72FinalCapture=true "+
                "deniedTaskHandleRetiredNotPhantomActive=true "+
                "laterTaggedPluginSubmissionRefused=true "+
                "legacyPluginSchedulingStillExplicitlyUnowned=true "+
                "unquiescedPluginTaskRunsUnderWorldMutationLock=true "+
                "R96WitnessPreservesFinalSnapshot=true "+
                "noPublicPluginApiOrProductionWiring=true");
            System.out.println(
                "R99_OFFLINE_FINAL_CAPTURE_CROSS_QUEUE_QUIESCENCE_PASS "+
                "preCaptureRealtimeRejectedAtDispatch=true "+
                "preCaptureOwnedEventRejectedAtDispatch=true "+
                "postCaptureRealtimeRejectedAtAdmission=true "+
                "postCaptureOwnedEventRejectedAtAdmission=true "+
                "postCapturePeriodicTickCallbacksRefused=true "+
                "unquiescedRealtimeAndEventsStillExecute=true "+
                "R72TerminalAndR89R90DurableFinalCommit=true "+
                "R96RetirementWitnessCommitsOnlyAfterUnregister=true "+
                "untaggedLegacyEventsNotCertified=true "+
                "noProductionWorldOrSessionWiring=true");
            System.out.println(
                "R98_OFFLINE_FINAL_CAPTURE_COMMAND_FENCE_PASS "+
                "R72TerminalCapturePathAndFence=true "+
                "offThreadBackpressureSubmission=true "+
                "prematureRetirementBeforeIoCompletionDenied=true "+
                "queuedBeforeFreezeDeniedAtDispatch=true "+
                "submittedAfterFreezeDeniedAtEnqueue=true "+
                "noPostSnapshotGameplayCommandMutatedPlayer=true "+
                "quiescenceAndSnapshotCapturedInSingleWorldAction=true "+
                "capturedWorldOwnerAndOrdinalBound=true "+
                "R96RequiresCompletedQuiescedFinalSave=true "+
                "wrongCaptureOrdinalRejectedBeforeRetirement=true "+
                "unquiescedSaveCannotAuthorizeRetirement=true "+
                "R89R90FinalSnapshotCommitted=true "+
                "R96RetirementWitnessCommittedAfterUnregister=true "+
                "noProductionWorldOrSessionWiring=true");
        }finally{
            try(Stream<Path> walk=Files.walk(scratch)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException ex){throw new IllegalStateException(
                        "R98 scratch cleanup "+p,ex);}
                });
            }
        }
    }


    /** R104: a real repeating plugin task must be fenced every tick. */
    static void runR104RepeatingForTest()throws Exception{
        Path scratch=Files.createTempDirectory("spk-r104-repeat-");
        try{
            Fixture f=new Fixture(scratch.resolve("repeat-final"));
            R90OfflineProducerContinuity.Session producer=f.initialize();
            try(World world=World.isolatedForTest(
                    90L,forbidRawSave(f.files()))){
                WorldPlayer player=f.player();
                long generation=world.registerPlayer(player,USER);
                R93OfflineNonblockingWorldTurnoverGate ioGate=
                    R93OfflineNonblockingWorldTurnoverGate
                        .installFailureQuarantineForTest(world,producer);
                R98OfflineFinalCaptureCommandFence ingress=
                    new R98OfflineFinalCaptureCommandFence(world);
                ingress.installForTest();
                R99OfflineCrossQueueRetirementFence cross=
                    new R99OfflineCrossQueueRetirementFence(world,ingress);
                cross.installForTest();
                world.events().installOfflineExplicitEventProvenanceForTest();

                PluginCallbackScope pluginScope=new PluginCallbackScope(
                    ()->world.pulse().inExecutionContext(),
                    ()->!world.closed());
                PluginTaskTracker plugin=pluginTrackerForTest(
                    world,pluginScope);
                try{
                    world.start();
                    AtomicInteger repeatRuns=new AtomicInteger();
                    AtomicInteger cancelRuns=new AtomicInteger();
                    PluginTask repeating=plugin.scheduleOwnedRepeatingForTest(
                        1L,1L,player.id(),generation,()->{
                            require(world.pulse().inExecutionContext()&&
                                    Thread.holdsLock(player.mutationLock()),
                                "R104 repeating plugin bypassed World owner");
                            repeatRuns.incrementAndGet();
                            try{spawn(player,560);}
                            catch(IOException error){
                                throw new IllegalStateException(error);
                            }
                        });
                    PluginTask cancelled=plugin.scheduleOwnedRepeatingForTest(
                        1L,1L,player.id(),generation,
                        cancelRuns::incrementAndGet);
                    long deadline=System.nanoTime()+
                        TimeUnit.SECONDS.toNanos(12L);
                    while(repeatRuns.get()<3||cancelRuns.get()<1){
                        require(System.nanoTime()<deadline,
                            "R104 healthy repeating plugin did not run "+
                            repeatRuns.get()+"/"+cancelRuns.get());
                        Thread.sleep(15L);
                    }
                    require(cancelled.cancel()&&!cancelled.active(),
                        "R104 cancelled task handle stayed live");
                    int cancelledAt=cancelRuns.get();
                    long cancelledTick=world.clock().tick();
                    while(world.clock().tick()<cancelledTick+3L){
                        require(System.nanoTime()<deadline,
                            "R104 cancel follow-up tick timeout");
                        Thread.sleep(12L);
                    }
                    require(cancelRuns.get()==cancelledAt&&repeating.active(),
                        "R104 cancellation affected wrong repeating task");

                    // The actor command, not this thread, performs the
                    // sticky final-quiescence transition and R72 capture.
                    AtomicReference<WorldPlayerPersistence.CapturedSave>
                        captured=new AtomicReference<>();
                    AtomicInteger atCapture=new AtomicInteger(-1);
                    java.util.concurrent.atomic.AtomicLong atTick=
                        new java.util.concurrent.atomic.AtomicLong();
                    world.submitAndWait(player,generation,()->{
                        atCapture.set(repeatRuns.get());
                        atTick.set(world.clock().tick());
                        captured.set(
                            ingress.quiesceAndCaptureFinalInsideActorForTest(
                                player,generation,USER,0,
                                "[r104] ","REPEATING_PLUGIN_FINAL"));
                    },10_000L);
                    require(captured.get()!=null&&
                            captured.get().finalSnapshot&&
                            ingress.quiescedForTest(player,generation)&&
                            atCapture.get()>=3,
                        "R104 World actor did not finalize valid repetition");

                    expectScheduleDenied(
                        ()->plugin.scheduleOwnedRepeatingForTest(
                            1L,1L,player.id(),generation,
                            repeatRuns::incrementAndGet),
                        "R76_EVENT_QUIESCED before schedule");
                    expectScheduleDenied(
                        ()->plugin.scheduleOwnedRepeatingForTest(
                            1L,1L,player.id(),generation+1L,
                            repeatRuns::incrementAndGet),
                        "R76_EVENT_QUIESCED before schedule");
                    expectScheduleDenied(
                        ()->plugin.scheduleRepeating(
                            1L,1L,repeatRuns::incrementAndGet),
                        "R101_UNTAGGED_EVENT_DENIED");

                    WorldPlayerPersistence.CapturedSave finalSave=
                        captured.get();
                    WorldPlayerPersistence.SaveTicket ticket=
                        world.persistence().submitCapturedWithBackpressure(
                            finalSave,5_000L);
                    ticket.completion.get(15L,TimeUnit.SECONDS);
                    require(ticket.captureOrdinal==1L &&
                            ticket.provenance!=null &&
                            ticket.provenance.capturedOwner==player &&
                            ticket.provenance.worldPlayerGeneration==generation &&
                            f.producerHighwater()==1L,
                        "R104 R89/R90 final snapshot ordinal not committed");

                    // No extra callback may execute or reschedule past
                    // the captured tick, and the handle must retire.
                    long postTick=atTick.get()+4L;
                    long drainDeadline=System.nanoTime()+
                        TimeUnit.SECONDS.toNanos(10L);
                    while(world.clock().tick()<postTick||
                            repeating.active()||plugin.size()!=0||
                            world.events().size()!=0){
                        require(System.nanoTime()<drainDeadline,
                            "R104 rejected recurring handle did not retire "+
                            "tick="+world.clock().tick()+
                            " active="+repeating.active()+
                            " handles="+plugin.size()+
                            " queued="+world.events().size());
                        Thread.sleep(15L);
                    }
                    require(repeatRuns.get()==atCapture.get()&&
                            cancelRuns.get()==cancelledAt&&
                            !repeating.active()&&!cancelled.active()&&
                            cross.deniedEventsForTest()>=3,
                        "R104 stale repeating plugin mutated after capture");
                    PlayerSnapshot persisted=f.files().load(USER).orElseThrow(
                        ()->new IOException("R104 final account missing"));
                    require(R57TradePairJournal.digest(persisted).equals(
                            R57TradePairJournal.digest(ticket.snapshot)),
                        "R104 final account differs after repeating denial");

                    R96OfflineDurableRetirementWitness witness=f.witness(
                        world,player,generation,1L,ioGate,ingress,producer);
                    witness.prepareForTest();
                    require(!world.unregisterPlayer(player,generation),
                        "R104 R96 PREPARED allowed early unregister");
                    witness.unregisterAndCommitForTest(false);
                    R96OfflineDurableRetirementWitness.Review review=
                        R96OfflineDurableRetirementWitness.inspectForTest(
                            f.journal,f.accounts,f.generations,f.sessions,
                            f.ready());
                    require(review.status==
                            R96OfflineDurableRetirementWitness.Status
                                .COMMITTED_WITNESS_ONLY &&
                            !review.loginAuthorized()&&
                            !world.players().owns(player,generation)&&
                            repeatRuns.get()==atCapture.get(),
                        "R104 R96 retirement or post-commit counter failed");
                    System.out.println(
                        "R104_REPEATING_PLUGIN_WORLD_PASS "+
                        "validRepeatsBeforeCapture="+atCapture.get()+" "+
                        "cancelledRepeatingTaskStayedCancelled=true "+
                        "R72TerminalCaptureOnWorldThread=true "+
                        "R99RecurringQueueDispatchDeniedAfterCapture=true "+
                        "retiredTaskHandleAndNoPhantomRequeue=true "+
                        "R89R90ImmutableFinalSnapshotCommitted=true "+
                        "R96CommittedRetirementWitness=true "+
                        "noProductionPluginOrWorldWiring=true");
                }finally{
                    plugin.close();
                    pluginScope.close();
                }
            }
        }finally{
            try(Stream<Path> walk=Files.walk(scratch)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException error){
                        throw new IllegalStateException(
                            "R104 scratch cleanup "+p,error);
                    }
                });
            }
        }
    }

    private static PluginTaskTracker pluginTrackerForTest(World world,
        PluginCallbackScope scope){
        scope.activate();
        PluginTaskTracker tracker=new PluginTaskTracker(
            world.clock(),world.events(),()->!world.closed(),
            null,scope,()->true,(kind,error)->{
                throw new AssertionError(
                    "R103 unexpected plugin callback failure "+kind,error);
            });
        tracker.activate();
        return tracker;
    }

    /**
     * R99: exercise all three EXISTING World owner-tagged opt-in gates
     * against the exact R98 final-capture bit, not an independently
     * inferred logout state. Pre-capture callbacks may be in the queues
     * when the final World handler runs; neither may mutate afterward.
     */
    private static void crossQueueAfterFinalRetirement(
        Path root,boolean strictR101)throws Exception{
        Fixture f=new Fixture(root);
        R90OfflineProducerContinuity.Session producer=f.initialize();
        try(World world=World.isolatedForTest(
                200L,forbidRawSave(f.files()))){
            WorldPlayer player=f.player();
            long generation=world.registerPlayer(player,USER);
            R93OfflineNonblockingWorldTurnoverGate ioGate=
                R93OfflineNonblockingWorldTurnoverGate
                    .installFailureQuarantineForTest(world,producer);
            R98OfflineFinalCaptureCommandFence ingress=
                new R98OfflineFinalCaptureCommandFence(world);
            ingress.installForTest();
            R99OfflineCrossQueueRetirementFence cross=
                new R99OfflineCrossQueueRetirementFence(world,ingress);
            cross.installForTest();
            if(strictR101){
                world.events().installOfflineExplicitEventProvenanceForTest();
                expectScheduleDenied(
                    ()->world.events().schedule(
                        12L,()->{throw new AssertionError(
                            "R101 untagged World event unexpectedly dispatched");}),
                    "R101_UNTAGGED_EVENT_DENIED");
            }
            PluginCallbackScope pluginScope=new PluginCallbackScope(
                ()->world.pulse().inExecutionContext(),()->!world.closed());
            PluginTaskTracker plugin=pluginTrackerForTest(
                world,pluginScope);
            world.start();

            AtomicInteger pluginMutation=new AtomicInteger();
            AtomicReference<PluginTask> pluginHandle=new AtomicReference<>();
            AtomicInteger realtimeMutation=new AtomicInteger();
            AtomicInteger eventMutation=new AtomicInteger();
            AtomicInteger explicitGlobal=new AtomicInteger();
            AtomicReference<WorldPlayerPersistence.CapturedSave> captured=
                new AtomicReference<>();
            java.util.concurrent.atomic.AtomicLong dueTick=
                new java.util.concurrent.atomic.AtomicLong();

            world.submitAndWait(player,generation,()->{
                long eventTick=world.clock().tick()+3L;
                dueTick.set(eventTick);
                // These are admitted BEFORE the same actor marks final
                // capture; they must later fail at REAL dispatch.
                world.realtime().schedule(
                    System.currentTimeMillis()+650L,
                    player,generation,()->{
                        realtimeMutation.incrementAndGet();
                        try{spawn(player,385);}
                        catch(IOException error){
                            throw new IllegalStateException(error);
                        }
                    });
                world.events().scheduleOwnedForTest(
                    eventTick,player.id(),generation,()->{
                        eventMutation.incrementAndGet();
                        try{spawn(player,565);}
                        catch(IOException error){
                            throw new IllegalStateException(error);
                        }
                    });
                PluginTask owned=plugin.scheduleOwnedForTest(
                    3L,player.id(),generation,()->{
                        require(world.pulse().inExecutionContext()&&
                                Thread.holdsLock(player.mutationLock()),
                            "R103 plugin mutated outside World ownership");
                        pluginMutation.incrementAndGet();
                        try{spawn(player,567);}
                        catch(IOException e){throw new IllegalStateException(e);}
                    });
                pluginHandle.set(owned);
                spawn(player,560);
                captured.set(
                    ingress.quiesceAndCaptureFinalInsideActorForTest(
                        player,generation,USER,0,
                        "[r99] ","CROSS_QUEUE_TERMINAL"));
            },10_000L);

            require(ingress.quiescedForTest(player,generation) &&
                    captured.get()!=null &&
                    captured.get().finalSnapshot,
                "R99 actor failed to establish real R72 final capture");
            WorldPlayerPersistence.SaveTicket ticket=
                captured.get().ticket;
            require(ticket.captureOrdinal==1L &&
                    ticket.provenance!=null &&
                    ticket.provenance.capturedOwner==player &&
                    ticket.provenance.worldPlayerGeneration==generation,
                "R99 final source/generation/ordinal invalid");

            expectScheduleDenied(()->world.realtime().schedule(
                    System.currentTimeMillis()+300L,
                    player,generation,()->realtimeMutation.incrementAndGet()),
                "R75_REALTIME_QUIESCED before schedule");
            expectScheduleDenied(()->world.events().scheduleOwnedForTest(
                    dueTick.get()+2L,player.id(),generation,
                    ()->eventMutation.incrementAndGet()),
                "R76_EVENT_QUIESCED before schedule");
            expectScheduleDenied(
                ()->plugin.scheduleOwnedForTest(
                    1L,player.id(),generation,
                    pluginMutation::incrementAndGet),
                "R76_EVENT_QUIESCED before schedule");
            if(strictR101){
                expectScheduleDenied(
                    ()->plugin.schedule(1L,pluginMutation::incrementAndGet),
                    "R101_UNTAGGED_EVENT_DENIED");
                // This explicit, audited non-player action does not touch
                // the actor; it must remain schedulable after final capture.
                world.events().scheduleExplicitNonPlayerForTest(
                    dueTick.get(),"R101 fixture-owned global diagnostic",
                    explicitGlobal::incrementAndGet);
                expectScheduleDenied(
                    ()->world.events().schedule(
                        dueTick.get(),eventMutation::incrementAndGet),
                    "R101_UNTAGGED_EVENT_DENIED");
            }

            WorldPlayerPersistence.SaveTicket submitted=
                world.persistence().submitCapturedWithBackpressure(
                    captured.get(),5_000L);
            require(submitted==ticket,
                "R99 terminal ticket changed during IO admission");
            ticket.completion.get(15L,TimeUnit.SECONDS);
            require(f.producerHighwater()==1L,
                "R99 R89/R90 final source did not commit");

            // Wait until the real World pulse processes BOTH due queues,
            // rather than accepting a zero mutation count because neither
            // callback has reached dispatch yet.
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10L);
            while(world.clock().tick()<=dueTick.get() ||
                    world.events().size()!=0||
                    world.realtime().size()!=0){
                require(System.nanoTime()<deadline,
                    "R99 timeout before scheduled callbacks were dispatched");
                Thread.sleep(25L);
            }
            require(realtimeMutation.get()==0 &&
                    eventMutation.get()==0 &&
                    cross.deniedRealtimeForTest()>=2 &&
                    cross.deniedEventsForTest()>=3 &&
                    cross.deniedTicksForTest()>=1 &&
                    pluginMutation.get()==0 &&
                    pluginHandle.get()!=null &&
                    !pluginHandle.get().active() && plugin.size()==0 &&
                    (!strictR101||explicitGlobal.get()==1),
                "R99 pre/post-final callbacks bypassed cross-queue gate");
            PlayerSnapshot persisted=f.files().load(USER).orElseThrow(
                ()->new IOException("R99 committed account missing"));
            require(R57TradePairJournal.digest(persisted).equals(
                    R57TradePairJournal.digest(ticket.snapshot)),
                "R99 callback changed canonical final snapshot");

            R96OfflineDurableRetirementWitness witness=f.witness(
                world,player,generation,1L,ioGate,ingress,producer);
            witness.prepareForTest();
            require(!world.unregisterPlayer(player,generation),
                "R99 R96 PREPARED did not fence direct unregister");
            witness.unregisterAndCommitForTest(false);
            R96OfflineDurableRetirementWitness.Review observed=
                R96OfflineDurableRetirementWitness.inspectForTest(
                    f.journal,f.accounts,f.generations,f.sessions,
                    f.ready());
            require(!world.players().owns(player,generation) &&
                    observed.status==
                        R96OfflineDurableRetirementWitness.Status
                            .COMMITTED_WITNESS_ONLY &&
                    !observed.loginAuthorized() &&
                    realtimeMutation.get()==0 && eventMutation.get()==0,
                "R99 post-unregister event or witness inconsistency");
            require(pluginMutation.get()==0&&plugin.size()==0,
                "R103 final retirement left a plugin callback active");
            plugin.close();
            pluginScope.close();
        }
    }

    /** A separate unquiesced actor must retain normal realtime/events. */
    private static void crossQueueUnquiescedPositiveControl(
        Path root)throws Exception{
        Fixture f=new Fixture(root);
        R90OfflineProducerContinuity.Session producer=f.initialize();
        try(World world=World.isolatedForTest(
                150L,forbidRawSave(f.files()))){
            WorldPlayer player=f.player();
            long generation=world.registerPlayer(player,USER);
            R93OfflineNonblockingWorldTurnoverGate
                .installFailureQuarantineForTest(world,producer);
            R98OfflineFinalCaptureCommandFence ingress=
                new R98OfflineFinalCaptureCommandFence(world);
            ingress.installForTest();
            R99OfflineCrossQueueRetirementFence cross=
                new R99OfflineCrossQueueRetirementFence(world,ingress);
            cross.installForTest();
            world.events().installOfflineExplicitEventProvenanceForTest();
            PluginCallbackScope pluginScope=new PluginCallbackScope(
                ()->world.pulse().inExecutionContext(),()->!world.closed());
            PluginTaskTracker plugin=pluginTrackerForTest(
                world,pluginScope);
            world.start();
            AtomicInteger pluginRuns=new AtomicInteger();
            PluginTask owned=plugin.scheduleOwnedForTest(
                3L,player.id(),generation,()->{
                    require(world.pulse().inExecutionContext()&&
                            Thread.holdsLock(player.mutationLock()),
                        "R103 plugin executed outside current player lock");
                    pluginRuns.incrementAndGet();
                });
            expectScheduleDenied(
                ()->plugin.scheduleOwnedForTest(
                    1L,player.id(),generation+1L,
                    pluginRuns::incrementAndGet),
                "R76_EVENT_QUIESCED before schedule");
            expectScheduleDenied(
                ()->plugin.schedule(1L,pluginRuns::incrementAndGet),
                "R101_UNTAGGED_EVENT_DENIED");
            AtomicInteger realtime=new AtomicInteger();
            AtomicInteger ownedEvent=new AtomicInteger();
            long dueTick=world.clock().tick()+3L;
            world.realtime().schedule(
                System.currentTimeMillis()+350L,
                player,generation,realtime::incrementAndGet);
            world.events().scheduleOwnedForTest(
                dueTick,player.id(),generation,ownedEvent::incrementAndGet);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10L);
            // Observe both callback delivery AND the plugin tracker's
            // complete one-shot retirement. The callback increments its
            // counter before execute() finishes retiring the handle.
            while(realtime.get()==0||ownedEvent.get()==0||
                    pluginRuns.get()==0||owned.active()||plugin.size()!=0){
                require(System.nanoTime()<deadline,
                    "R103 unquiesced callbacks or task cleanup incomplete "+
                    "realtime="+realtime.get()+
                    " ownedEvent="+ownedEvent.get()+
                    " pluginRuns="+pluginRuns.get()+
                    " active="+owned.active()+
                    " tasks="+plugin.size());
                Thread.sleep(20L);
            }
            require(realtime.get()==1 && ownedEvent.get()==1 &&
                    pluginRuns.get()==1&&!owned.active()&&
                    plugin.size()==0&&
                    cross.deniedRealtimeForTest()==0 &&
                    // The deliberately submitted wrong-generation task
                    // must count as ONE denied event; valid callbacks
                    // must not cause additional denials.
                    cross.deniedEventsForTest()==1 &&
                    cross.deniedTicksForTest()==0 &&
                    !ingress.quiescedForTest(player,generation),
                "R99 fence blocked valid pre-quiescence execution");
            plugin.close();
            pluginScope.close();
        }
    }

    private static void expectScheduleDenied(
        Checked task,String token)throws Exception{
        try{
            task.run();
            throw new AssertionError(
                "R99 expected scheduled callback refusal missing");
        }catch(IllegalStateException denied){
            require(denied.getMessage().contains(token),
                "R99 wrong scheduled callback refusal "+denied);
        }
    }

    private static void finalCaptureAndDurableRetirement(
        Path root)throws Exception{
        Fixture f=new Fixture(root);
        R90OfflineProducerContinuity.Session producer=f.initialize();
        try(World world=World.isolatedForTest(
                60_000L,forbidRawSave(f.files()))){
            WorldPlayer player=f.player();
            long generation=world.registerPlayer(player,USER);
            R93OfflineNonblockingWorldTurnoverGate ioGate=
                R93OfflineNonblockingWorldTurnoverGate
                    .installFailureQuarantineForTest(world,producer);
            R98OfflineFinalCaptureCommandFence ingress=
                new R98OfflineFinalCaptureCommandFence(world);
            ingress.installForTest();
            world.start();

            AtomicInteger executedAfterFinal=new AtomicInteger();
            AtomicReference<CompletableFuture<Void>> queuedBefore=
                new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.CapturedSave> finalSave=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                // This is enqueued while the current World handler is
                // running, and therefore cannot execute until afterward.
                queuedBefore.set(world.submit(player,generation,()->{
                    executedAfterFinal.incrementAndGet();
                    spawn(player,385);
                }));
                spawn(player,560);
                finalSave.set(
                    ingress.quiesceAndCaptureFinalInsideActorForTest(
                        player,generation,USER,0,
                        "[r98] ","QUIESCED_FINAL"));
            },10_000L);
            require(ingress.quiescedForTest(player,generation) &&
                    queuedBefore.get()!=null&&finalSave.get()!=null,
                "R98 actual World actor did not quiesce/capture");
            expectCommandDenied(queuedBefore.get(),
                "R74_COMMAND_QUIESCED before handler");
            CompletableFuture<Void> after=world.submit(
                player,generation,()->{
                    executedAfterFinal.incrementAndGet();
                    spawn(player,565);
                });
            expectCommandDenied(after,
                "R74_COMMAND_QUIESCED before enqueue");
            require(executedAfterFinal.get()==0,
                "R98 queued mutation executed after final snapshot");
            WorldPlayerPersistence.SaveTicket ticket=
                finalSave.get().ticket;
            require(finalSave.get().finalSnapshot &&
                    ticket.captureOrdinal==1L &&
                    ticket.provenance!=null &&
                    ticket.provenance.capturedOwner==player &&
                    ticket.provenance.worldPlayerGeneration==generation,
                "R98 final save missing real World capture provenance");
            // Before off-thread submission/IO completion, retirement
            // is forbidden even though command ingress is quiesced.
            R96OfflineDurableRetirementWitness premature=f.witness(
                world,player,generation,1L,ioGate,ingress,producer);
            expectIoDenied(premature::prepareForTest,
                "R98_RETIRE_DENIED");
            require(!Files.exists(f.witnessPath()),
                "R98 premature witness was published");
            WorldPlayerPersistence.SaveTicket submitted=
                world.persistence().submitCapturedWithBackpressure(
                    finalSave.get(),5_000L);
            require(submitted==ticket,
                "R98 off-thread terminal submission changed source ticket");
            ticket.completion.get(15L,TimeUnit.SECONDS);
            require(f.producerHighwater()==1L,
                "R98 final save not committed through R89/R90");

            R96OfflineDurableRetirementWitness wrong=f.witness(
                world,player,generation,2L,ioGate,ingress,producer);
            expectIoDenied(wrong::prepareForTest,
                "R98_RETIRE_DENIED");
            require(!Files.exists(f.witnessPath()) &&
                    world.players().owns(player,generation),
                "R98 wrong ordinal changed retirement state");

            R96OfflineDurableRetirementWitness witness=f.witness(
                world,player,generation,1L,ioGate,ingress,producer);
            witness.prepareForTest();
            require(!world.unregisterPlayer(player,generation) &&
                    world.players().owns(player,generation),
                "R98 direct unregister bypassed R96 PREPARED state");
            witness.unregisterAndCommitForTest(false);
            require(!world.players().owns(player,generation),
                "R98 World unregister did not complete");
            R96OfflineDurableRetirementWitness.Review inspected=
                R96OfflineDurableRetirementWitness.inspectForTest(
                    f.journal,f.accounts,f.generations,f.sessions,
                    f.ready());
            require(inspected.status==
                    R96OfflineDurableRetirementWitness.Status
                        .COMMITTED_WITNESS_ONLY &&
                    inspected.ordinal==1L &&
                    !inspected.loginAuthorized()&&
                    executedAfterFinal.get()==0,
                "R98 post-capture mutation or invalid retirement witness");
            PlayerSnapshot persisted=f.files().load(USER).orElseThrow(
                ()->new IOException("R98 committed account missing"));
            require(R57TradePairJournal.digest(persisted).equals(
                    R57TradePairJournal.digest(ticket.snapshot)),
                "R98 persisted canonical snapshot differed from final World capture");
        }
    }

    private static void unquiescedSaveDoesNotAuthorizeRetirement(
        Path root)throws Exception{
        Fixture f=new Fixture(root);
        R90OfflineProducerContinuity.Session producer=f.initialize();
        try(World world=World.isolatedForTest(
                60_000L,forbidRawSave(f.files()))){
            WorldPlayer player=f.player();
            long generation=world.registerPlayer(player,USER);
            R93OfflineNonblockingWorldTurnoverGate ioGate=
                R93OfflineNonblockingWorldTurnoverGate
                    .installFailureQuarantineForTest(world,producer);
            R98OfflineFinalCaptureCommandFence ingress=
                new R98OfflineFinalCaptureCommandFence(world);
            ingress.installForTest();
            world.start();
            AtomicReference<WorldPlayerPersistence.SaveTicket> save=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                spawn(player,560);
                save.set(world.persistence().captureAndSave(
                    USER,player,generation,0,"[r98] ","UNQUIESCED_SAVE"));
            },10_000L);
            save.get().completion.get(15L,TimeUnit.SECONDS);
            require(f.producerHighwater()==1L &&
                    !ingress.quiescedForTest(player,generation),
                "R98 unquiesced fixture not initialized");
            R96OfflineDurableRetirementWitness forged=f.witness(
                world,player,generation,1L,ioGate,ingress,producer);
            expectIoDenied(forged::prepareForTest,
                "R98_RETIRE_DENIED");
            require(!Files.exists(f.witnessPath()) &&
                    world.players().owns(player,generation),
                "R98 arbitrary committed save authorized retirement");
            AtomicInteger mutation=new AtomicInteger();
            world.submitAndWait(player,generation,()->{
                mutation.incrementAndGet();
                spawn(player,385);
            },10_000L);
            require(mutation.get()==1,
                "R98 non-quiesced World command unexpectedly fenced");
        }
    }

    @FunctionalInterface private interface Checked {
        void run()throws Exception;
    }

    private static void expectIoDenied(
        Checked action,String expected)throws Exception{
        try{
            action.run();
            throw new AssertionError("R98 expected refusal missing");
        }catch(IOException denied){
            require(denied.getMessage().contains(expected),
                "R98 wrong witness refusal "+denied);
        }
    }

    private static void expectCommandDenied(
        CompletableFuture<Void> task,String expected)throws Exception{
        try{
            task.get(10L,TimeUnit.SECONDS);
            throw new AssertionError(
                "R98 gameplay command ran despite final capture");
        }catch(ExecutionException|CancellationException denied){
            require(denied.toString().contains(expected),
                "R98 wrong command refusal "+denied);
        }
    }

    private static void spawn(WorldPlayer player,int item)
        throws IOException{
        ServerPacketWriter packets=new ServerPacketWriter(
            new OutboundPacketQueue(),new IsaacCipher(
                new int[]{1,2,3,4}));
        String result=player.bank().spawnItem(item,1,packets);
        require(result.startsWith("ITEM_SPAWN_OK"),
            "R98 item mutation denied "+result);
    }

    private static PlayerRepository forbidRawSave(
        FilePlayerRepository files){
        return new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String username)throws IOException{
                return files.load(username);
            }
            @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
                throw new IOException(
                    "R98_RAW_REPOSITORY_BYPASS_DENIED");
            }
        };
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private static final class Fixture {
        final Path root,journal,accounts,generations,sessions;
        Fixture(Path root)throws IOException{
            this.root=root;
            journal=root.resolve("journal");
            accounts=root.resolve("accounts");
            generations=root.resolve("generations");
            sessions=root.resolve("sessions");
            Files.createDirectories(journal);
            Files.createDirectories(accounts);
            Files.createDirectories(generations);
            Files.createDirectories(sessions);
        }
        FilePlayerRepository files(){
            return new FilePlayerRepository(
                user->accounts.resolve(user+".properties"));
        }
        R90OfflineProducerContinuity.Session initialize()
            throws IOException{
            WorldPlayer seed=new WorldPlayer();
            spawn(seed,995);
            files().save(PlayerSnapshotCodec.capture(USER,seed));
            R60TradePairCoordinator.initializeEmpty(journal);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,USER);
            R64OfflineSessionOwnership.Token owner=
                R64OfflineSessionOwnership.claimFirst(
                    journal,accounts,generations,sessions,USER);
            R89OfflineDurableCaptureAdmission.initialize(
                journal,accounts,generations,sessions,owner);
            return R90OfflineProducerContinuity.initialize(
                journal,accounts,generations,sessions,owner);
        }
        WorldPlayer player()throws IOException{
            WorldPlayer player=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                files().load(USER).orElseThrow(
                    ()->new IOException("R98 missing seed account")),
                player);
            return player;
        }
        R64OfflineSessionOwnership.Token ready(){
            R64OfflineSessionOwnership.Review review=
                R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,USER);
            require(review.status==R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                review.token!=null&&!review.loginAuthorized(),
                "R98 R64 owner was not ready");
            return review.token;
        }
        long producerHighwater()throws IOException{
            return R90OfflineProducerContinuity
                .inspectCommittedOrdinalForTest(
                    journal,accounts,generations,sessions,ready());
        }
        R96OfflineDurableRetirementWitness witness(
            World world,WorldPlayer player,long generation,long ordinal,
            R93OfflineNonblockingWorldTurnoverGate ioGate,
            R98OfflineFinalCaptureCommandFence ingress,
            R90OfflineProducerContinuity.Session producer){
            return R96OfflineDurableRetirementWitness
                .createCommandFencedForTest(
                    world,player,generation,ordinal,ioGate,ingress,
                    ready(),producer.ticket,journal,accounts,
                    generations,sessions);
        }
        Path witnessPath(){
            return R96OfflineDurableRetirementWitness.fileForTest(
                sessions,USER);
        }
    }

    private R103OfflineOwnedPluginTaskRetirementTest(){}
}
