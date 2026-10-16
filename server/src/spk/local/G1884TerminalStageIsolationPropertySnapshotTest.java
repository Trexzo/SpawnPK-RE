package spk.local;

import java.util.Collections;

/**
 * G18.8.4 — test the REAL public create(World) property boundary, not
 * the separate explicit diagnostic injection overload. The opt-in
 * experiment must be captured at composition time. Flipping the
 * JVM property afterward cannot change an existing World graph.
 *
 * Both cases use genuine NPC death, installed G1, G14 malformed
 * test-only persistence, healthy activated G15/G16/G18 consumers
 * and actual ground drop settlement. No original SpawnPK policy.
 */
public final class G1884TerminalStageIsolationPropertySnapshotTest {
    private static final String USER="g1884-property-composition";
    private static final String PROPERTY=
        LocalLabMonsterSpawnerProvisioning
            .ISOLATE_TERMINAL_STAGES_PROPERTY;

    public static void main(String[] args)throws Exception {
        String prior=System.getProperty(PROPERTY);
        try {
            runCase(false,2901L);
            runCase(true,3001L);
            System.out.println(
                "G1884_TERMINAL_ISOLATION_PROPERTY_COMPOSITION_SNAPSHOT_PASS"+
                " absentAtCreateRetainsSequentialFailure=true"+
                " enabledAtCreateContainsG14Exception=true"+
                " postCreatePropertyFlipIgnoredBothWays=true"+
                " installedObserversActualNpcDeathsTwo=true"+
                " g1ReceiptsEachOne=true"+
                " groundSettlementsEachOne=true"+
                " originalSpawnpkPolicy=false"+
                " durableOutbox=false");
        }finally {
            if(prior==null)
                System.clearProperty(PROPERTY);
            else
                System.setProperty(PROPERTY,prior);
        }
    }

    // G1953/54: independent focused entry point for the actual installed
    // LocalLab observer, restoring the JVM property after its test case.
    static void runObserverDiagnosticCase(
        boolean isolate,
        long deathTick
    )throws Exception{
        String prior=System.getProperty(PROPERTY);
        try{
            runCase(isolate,deathTick);
        }finally{
            if(prior==null)System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY,prior);
        }
    }

    static void runSequentialStageTraceCase(
        boolean malformedG14,
        long deathTick
    )throws Exception{
        String prior=System.getProperty(PROPERTY);
        try{
            runCase(false,deathTick,malformedG14);
        }finally{
            if(prior==null)System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY,prior);
        }
    }

    private static void runCase(
        boolean optInAtComposition,
        long deathTick
    )throws Exception{
        runCase(optInAtComposition,deathTick,true);
    }

    private static void runCase(
        boolean optInAtComposition,
        long deathTick,
        boolean malformedG14
    )throws Exception{
        if(optInAtComposition)
            System.setProperty(PROPERTY,"true");
        else
            System.clearProperty(PROPERTY);

        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try {
            generation=world.registerPlayer(player,USER);

            LocalLabTaskScrollRuntime g15=
                world.localLabTaskScrolls();
            LocalLabTaskScrollRuntime.AssignmentResult scroll=
                g15.assign(USER,world.clock().tick());
            check(scroll!=null&&scroll.assignedNow&&
                    scroll.task.objective.progress==0L,
                "genuine G15 active objective before terminal");

            LocalLabAdventureRuntime g16=world.localLabAdventures();
            LocalLabAdventureRuntime.ActivationResult adv=
                g16.activate(USER);
            check(adv!=null&&adv.activatedNow&&
                    g16.objective(USER)!=null&&
                    g16.objective(USER).progress==0L,
                "genuine G16 active objective before terminal");

            LocalLabDailyMoneyMakingRuntime g18=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult dmm=
                g18.activateEasyIfCurrent(USER,player,generation);
            check(dmm!=null&&dmm.activatedNow&&
                    g18.snapshot(USER).objective.progress==0L,
                "genuine G18 active objective before terminal");

            // Keep G14 unhydrated before injecting invalid TEST-ONLY
            // state. No TCP or account-file corruption is modeled.
            if(malformedG14)
                player.snapshotExtensions().replaceNamespace(
                    LocalLabDailyChallengePersistence.NAMESPACE,
                    Collections.singletonMap(
                        "version","invalid-g1884-test-only"));

            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);

            // Deliberate opposite property post-construction.
            // The existing observer must NEVER live-reconfigure.
            if(optInAtComposition)
                System.clearProperty(PROPERTY);
            else
                System.setProperty(PROPERTY,"true");

            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawn=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npc=spawn.spawn.combat.spawn.npc;
            check(spawn.runtime.recipientOrigin!=null&&
                    spawn.runtime.recipientOrigin.player==player&&
                    spawn.runtime.recipientOrigin.generation==generation&&
                    world.npcLifecycle().applyDamage(
                        npc.id,99,deathTick).newlyDied,
                "actual NPC captures exact owner and dies");

            MonsterSpawnerPvmRuntime.FinalizeResult finished=
                installed.runtime().finalizeIfOwned(npc);
            int expected=(optInAtComposition||!malformedG14)?1:0;
            check(finished!=null&&
                    finished.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    finished.settlement!=null&&
                    finished.settlement.groundItems.size()==1&&
                    finished.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    world.pvmRecords().receiptCount()==1&&
                    world.pvmRecords().snapshot(player).kills==1L&&
                    g15.active(USER).objective.progress==expected&&
                    g16.objective(USER).progress==expected&&
                    g18.snapshot(USER).objective.progress==expected&&
                    installed.runtime().get(npc.id)==null,
                "post-composition toggle cannot alter delivery optIn="+
                    optInAtComposition);

            String invalid=null;
            try{
                world.localLabDailyChallenges().status(USER);
            }catch(IllegalStateException caught){
                invalid=caught.getMessage();
            }
            check(malformedG14
                    ?invalid!=null&&invalid.contains(
                        "Daily Challenge persistence invalid")
                    :invalid==null,
                "G14 validation matches requested test-only fixture");

            // Inspect the REAL installed terminal-observer execution
            // after G1 and actual ground settlement. This overall outcome
            // is distinct from per-consumer G14 failure/credit.
            MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics observer=
                installed.runtime().terminalObserverDiagnostics();
            check(observer.totalAttempts==1L&&
                    observer.failedAttempts==
                        (optInAtComposition||!malformedG14?0L:1L)&&
                    observer.evictedAttempts==0L&&
                    observer.recentAttempts.size()==1,
                "one installed observer attempt after ground commit");
            MonsterSpawnerPvmRuntime.TerminalObserverAttempt attempt=
                observer.recentAttempts.get(0);
            check(attempt.npcId.equals(npc.id)&&
                    attempt.deathTick==deathTick&&
                    USER.equals(attempt.recipientRef)&&
                    attempt.spawnRecipientGeneration==generation&&
                    attempt.outcome==
                        (optInAtComposition||!malformedG14
                            ?MonsterSpawnerPvmRuntime
                                .TerminalObserverOutcome.RETURNED
                            :MonsterSpawnerPvmRuntime
                                .TerminalObserverOutcome.THREW)&&
                    (optInAtComposition||!malformedG14
                        ?attempt.failureType==null
                        :IllegalStateException.class.getName().equals(
                            attempt.failureType)),
                "overall observer outcome captures isolated vs sequential failure");

            LocalLabTerminalStageIsolation.DiagnosticSnapshot stages=
                installed.terminalDiagnosticSnapshot();
            if(optInAtComposition){
                check(stages!=null&&
                        stages.observedCount==1L&&
                        stages.recentReports.size()==1&&
                        stages.recentReports.get(0).outcome(
                            LocalLabTerminalStageIsolation.Stage.G14)==
                            LocalLabTerminalStageIsolation.Outcome.THREW&&
                        stages.recentReports.get(0).outcome(
                            LocalLabTerminalStageIsolation.Stage.G15)==
                            LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                        stages.recentReports.get(0).outcome(
                            LocalLabTerminalStageIsolation.Stage.G16)==
                            LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                        stages.recentReports.get(0).outcome(
                            LocalLabTerminalStageIsolation.Stage.G18)==
                            LocalLabTerminalStageIsolation.Outcome.RETURNED,
                    "overall RETURNED must not hide failed individual G14");
            }else
                check(stages==null,
                    "sequential mode does not claim isolated stage diagnostics");

            LocalLabSequentialTerminalStageTrace.Snapshot trace=
                installed.sequentialTerminalDiagnosticSnapshot();
            check(trace!=null,
                "production composition owns World-local trace instance");
            if(optInAtComposition)
                check(trace.observed==0L&&trace.evicted==0L&&
                        trace.recent.isEmpty(),
                    "isolation mode does not fabricate sequential stages");
            else{
                check(trace.observed==1L&&trace.evicted==0L&&
                        trace.recent.size()==1,
                    "default mode records one exact sequential trace");
                LocalLabOriginFirstFailureBoundaryTally.Snapshot originBoundary=
                    trace.originFailureBoundaries;
                check(originBoundary.count(
                        LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT,
                        malformedG14
                            ?LocalLabTerminalFailureBoundaryTally.Boundary.G14
                            :LocalLabTerminalFailureBoundaryTally.Boundary.NO_THROW_RECORDED)
                        ==1L&&
                    !originBoundary.hasDurableGrantAcknowledgement()&&
                    !originBoundary.safeToAutomaticallyReplay(),
                    "actual G14 failing/healthy origin-boundary cross-observation");
                LocalLabSequentialTerminalStageTrace.Report stage=
                    trace.recent.get(0);
                check(stage.npcId.equals(npc.id)&&
                        stage.deathTick==deathTick&&
                        USER.equals(stage.recipientRef)&&
                        stage.spawnRecipientGeneration==generation&&
                        stage.observedCurrentGeneration==generation&&
                        stage.spawnOriginMatchedAtDispatch&&
                        stage.outcome(
                            LocalLabSequentialTerminalStageTrace.Stage.G4)==
                            LocalLabSequentialTerminalStageTrace
                                .Outcome.RETURNED&&
                        stage.outcome(
                            LocalLabSequentialTerminalStageTrace.Stage.G14)==
                            (malformedG14
                                ?LocalLabSequentialTerminalStageTrace
                                    .Outcome.THREW
                                :LocalLabSequentialTerminalStageTrace
                                    .Outcome.RETURNED)&&
                        (malformedG14
                            ?IllegalStateException.class.getName().equals(
                                stage.failureType(
                                    LocalLabSequentialTerminalStageTrace.Stage.G14))
                            :stage.failureType(
                                LocalLabSequentialTerminalStageTrace.Stage.G14)
                                ==null),
                    "sequential G4/G14 exact stage outcome");
                for(LocalLabSequentialTerminalStageTrace.Stage after:
                    new LocalLabSequentialTerminalStageTrace.Stage[]{
                        LocalLabSequentialTerminalStageTrace.Stage.G15,
                        LocalLabSequentialTerminalStageTrace.Stage.G16,
                        LocalLabSequentialTerminalStageTrace.Stage.G18
                    })
                    check(stage.outcome(after)==
                            (malformedG14
                                ?LocalLabSequentialTerminalStageTrace
                                    .Outcome.NOT_REACHED
                                :LocalLabSequentialTerminalStageTrace
                                    .Outcome.RETURNED)&&
                            stage.failureType(after)==null,
                        "remaining stages reflect real fail-fast boundary");

                LocalLabOriginConsumerOutcomeTally.Snapshot joint=
                    trace.originConsumerOutcomes;
                LocalLabSettledDeathEnvelope.OriginStatus currentOrigin=
                    LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT;
                check(joint.originCount(currentOrigin)==1L&&
                    joint.count(currentOrigin,
                        LocalLabTerminalDeliveryAssessment.Consumer.G14,
                        malformedG14
                            ?LocalLabTerminalDeliveryAssessment.Evidence
                                .THREW_MUTATION_UNCERTAIN
                            :LocalLabTerminalDeliveryAssessment.Evidence
                                .RETURNED_UNACKNOWLEDGED)==1L&&
                    joint.count(currentOrigin,
                        LocalLabTerminalDeliveryAssessment.Consumer.G15,
                        malformedG14
                            ?LocalLabTerminalDeliveryAssessment.Evidence.NOT_REACHED
                            :LocalLabTerminalDeliveryAssessment.Evidence
                                .RETURNED_UNACKNOWLEDGED)==1L&&
                    !joint.hasDurableGrantAcknowledgement()&&
                    !joint.safeToAutomaticallyReplay(),
                    "exact matching-origin G14 exception retains downstream delivery boundary");
                LocalLabTerminalEvidenceConsistencyAudit.Result audit=
                    LocalLabTerminalEvidenceConsistencyAudit.inspect(trace);
                check(audit.status==
                        LocalLabTerminalEvidenceConsistencyAudit.Status.CONSISTENT&&
                        audit.observedTerminals==1L&&
                        audit.findings.isEmpty()&&
                        !audit.safeToAutomaticallyReplay(),
                    "real G14 fail-fast or healthy callback has coherent detached diagnostics");
                LocalLabTerminalDeliverySummary summary=
                    LocalLabTerminalDeliverySummary.from(trace);
                check(summary.observedTerminals==1L&&
                    summary.count(
                        LocalLabTerminalDeliveryAssessment.Consumer.G1,
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED)==1L&&
                    summary.count(
                        LocalLabTerminalDeliveryAssessment.Consumer.G4,
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED)==1L&&
                    summary.count(
                        LocalLabTerminalDeliveryAssessment.Consumer.G14,
                        malformedG14
                            ?LocalLabTerminalDeliveryAssessment.Evidence
                                .THREW_MUTATION_UNCERTAIN
                            :LocalLabTerminalDeliveryAssessment.Evidence
                                .RETURNED_UNACKNOWLEDGED)==1L&&
                    summary.hasObservedMutationUncertainty()==malformedG14&&
                    !summary.hasDurableGrantAcknowledgement()&&
                    !summary.safeToAutomaticallyReplay(),
                    "actual G14 fail-fast count accurately describes six consumers");
                for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                        new LocalLabTerminalDeliveryAssessment.Consumer[]{
                            LocalLabTerminalDeliveryAssessment.Consumer.G15,
                            LocalLabTerminalDeliveryAssessment.Consumer.G16,
                            LocalLabTerminalDeliveryAssessment.Consumer.G18
                        })
                    check(summary.count(consumer,
                            malformedG14
                                ?LocalLabTerminalDeliveryAssessment.Evidence.NOT_REACHED
                                :LocalLabTerminalDeliveryAssessment.Evidence
                                    .RETURNED_UNACKNOWLEDGED)==1L,
                        "aggregate downstream progression respects fail-fast "+consumer);
                check(summary.firstBoundaryCount(
                        malformedG14
                            ?LocalLabTerminalFailureBoundaryTally.Boundary.G14
                            :LocalLabTerminalFailureBoundaryTally.Boundary
                                .NO_THROW_RECORDED)==1L&&
                    summary.firstBoundaryCount(
                        LocalLabTerminalFailureBoundaryTally.Boundary.G1)==0L,
                    "genuine G14 fail-fast is the first observed blocking stage");
                LocalLabTerminalDeliveryAssessment assessment=
                    LocalLabTerminalDeliveryAssessment.from(stage);
                check(assessment.evidence(
                        LocalLabTerminalDeliveryAssessment.Consumer.G1)==
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED&&
                    assessment.evidence(
                        LocalLabTerminalDeliveryAssessment.Consumer.G4)==
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED&&
                    assessment.evidence(
                        LocalLabTerminalDeliveryAssessment.Consumer.G14)==
                        (malformedG14
                            ?LocalLabTerminalDeliveryAssessment.Evidence
                                .THREW_MUTATION_UNCERTAIN
                            :LocalLabTerminalDeliveryAssessment.Evidence
                                .RETURNED_UNACKNOWLEDGED)&&
                    assessment.hasMutationUncertainty()==malformedG14&&
                    !assessment.hasDurableGrantAcknowledgement()&&
                    !assessment.safeToAutomaticallyReplay(),
                    "G14 failure/healthy triage is not proof of grant");
                for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                        new LocalLabTerminalDeliveryAssessment.Consumer[]{
                            LocalLabTerminalDeliveryAssessment.Consumer.G15,
                            LocalLabTerminalDeliveryAssessment.Consumer.G16,
                            LocalLabTerminalDeliveryAssessment.Consumer.G18
                        })
                    check(assessment.evidence(consumer)==
                            (malformedG14
                                ?LocalLabTerminalDeliveryAssessment.Evidence.NOT_REACHED
                                :LocalLabTerminalDeliveryAssessment.Evidence
                                    .RETURNED_UNACKNOWLEDGED),
                        "consumer triage respects fail-fast after G14 "+consumer);
                boolean immutableMap=false;
                try{
                    assessment.consumers.clear();
                }catch(UnsupportedOperationException readonly){
                    immutableMap=true;
                }
                check(immutableMap&&
                    assessment.consumers.size()==
                        LocalLabTerminalDeliveryAssessment.Consumer.values().length,
                    "per-consumer snapshot immutable and complete");

                boolean immutable=false;
                try{
                    trace.recent.clear();
                }catch(UnsupportedOperationException expectedReadonly){
                    immutable=true;
                }
                check(immutable,
                    "sequential trace snapshot is read-only");
            }

            check(installed.runtime().finalizeIfOwned(npc).status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    world.pvmRecords().receiptCount()==1&&
                    installed.runtime().terminalObserverDiagnostics()
                        .totalAttempts==1L,
                "one real canonical terminal and observer no replay");
        }finally {
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError("G18.8.4 "+message);
    }

    private G1884TerminalStageIsolationPropertySnapshotTest(){}
}
