package spk.local;

/**
 * G19.6.7/68: real, installed DEFAULT G1 terminal callback can fail
 * after canonical ground settlement but BEFORE G4/G14/G15/G16/G18.
 * The new World-local report must preserve that failure boundary;
 * its presence does not authorize retry or durable reward credit.
 */
public final class G1967G1PreStageFailureDiagnosticTest {
    static void run(boolean provokeSecondDeath)throws Exception {
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        final String name=provokeSecondDeath
            ?"g1967-g1-pending-respawn"
            :"g1968-g1-healthy";
        try {
            generation=world.registerPlayer(player,name);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world,null);
            MonsterSpawnerService service=installed.service();
            int cycles=provokeSecondDeath?2:1;
            EntityId lastId=null;
            for(int n=0;n<cycles;n++){
                // Genuine G1 teardown may retire the spawner session.
                // Re-open only when absent, preserving its lifecycle.
                if(service.getSession(name)==null){
                    service.openSession(name,
                        LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
                    service.selectRow(name,0);
                }
                service.activate(name,1);
                MonsterSpawnerPvmRuntime.SpawnResult spawned=
                    installed.runtime().spawnAndBind(name,name,3088,3495,0);
                WorldNpc npc=spawned.spawn.combat.spawn.npc;
                check(npc!=null&&(lastId==null||!npc.id.equals(lastId)),
                    "distinct genuine canonical NPC");
                lastId=npc.id;
                check(world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick()).newlyDied,
                    "genuine canonical death");
                MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                    installed.runtime().finalizeIfOwned(npc);
                check(terminal!=null&&terminal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.settlement!=null&&
                    world.npcs().byId(npc.id)==null&&
                    installed.runtime().get(npc.id)==null,
                    "real NPC finalization and one ground settlement");
                check(installed.runtime().finalizeIfOwned(npc).status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                    "terminal cannot be finalized twice");
            }
            GroundItem ground=world.groundItems().findOwned(
                995,3088,3495,0,name);
            check(ground!=null&&ground.amount==cycles,
                "exactly one ground item per genuine settled NPC death");

            LocalLabSequentialTerminalStageTrace.Snapshot stage=
                installed.sequentialTerminalDiagnosticSnapshot();
            check(stage!=null&&stage.observed==cycles&&
                stage.evicted==0L&&stage.recent.size()==cycles,
                "one forensic diagnostic report per real terminal event");

            LocalLabSequentialTerminalStageTrace.Report first=
                stage.recent.get(0);
            check(first.g1Outcome==
                    LocalLabSequentialTerminalStageTrace.G1Outcome.RETURNED&&
                first.g1FailureType==null&&
                first.spawnOriginMatchedAtDispatch,
                "first installed G1 callback returned for current exact origin");
            for(LocalLabSequentialTerminalStageTrace.Stage consumer:
                    LocalLabSequentialTerminalStageTrace.Stage.values()){
                check(first.outcome(consumer)==
                    LocalLabSequentialTerminalStageTrace.Outcome.RETURNED,
                    "healthy actual consumer callback reached "+consumer);
            }

            check(stage.g1OutcomeCount(
                    LocalLabSequentialTerminalStageTrace.G1Outcome.RETURNED)==1L&&
                  stage.g1OutcomeCount(
                    LocalLabSequentialTerminalStageTrace.G1Outcome.THREW)==
                        (provokeSecondDeath?1L:0L)&&
                  stage.g1OutcomeCount(
                    LocalLabSequentialTerminalStageTrace.G1Outcome.NOT_OBSERVED)==0L,
                "G1 observed return/failure totals are separate from derived stages");

            check(stage.originCount(
                    LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT)
                    ==cycles,
                "real G1 return and post-settlement exception keep the same exact origin");
            LocalLabTerminalDeliveryAssessment firstAssessment=
                LocalLabTerminalDeliveryAssessment.from(first);
            check(!firstAssessment.hasMutationUncertainty()&&
                    !firstAssessment.safeToAutomaticallyReplay()&&
                    !firstAssessment.hasDurableGrantAcknowledgement(),
                "successful callbacks never become durable credit receipts");
            for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                    LocalLabTerminalDeliveryAssessment.Consumer.values())
                check(firstAssessment.evidence(consumer)==
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED,
                    "first genuine terminal returned but was not durably acknowledged "+consumer);

            LocalLabOriginConsumerOutcomeTally.Snapshot joint=
                stage.originConsumerOutcomes;
            LocalLabSettledDeathEnvelope.OriginStatus currentOrigin=
                LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT;
            check(joint.originCount(currentOrigin)==cycles&&
                    joint.count(currentOrigin,
                        LocalLabTerminalDeliveryAssessment.Consumer.G1,
                        LocalLabTerminalDeliveryAssessment.Evidence.RETURNED_UNACKNOWLEDGED)
                        ==1L&&
                    joint.count(currentOrigin,
                        LocalLabTerminalDeliveryAssessment.Consumer.G1,
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .THREW_MUTATION_UNCERTAIN)==
                        (provokeSecondDeath?1L:0L)&&
                    !joint.hasDurableGrantAcknowledgement()&&
                    !joint.safeToAutomaticallyReplay(),
                "actual G1 pre-stage exception remains exact-origin mutation uncertainty");
            for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                    LocalLabTerminalDeliveryAssessment.Consumer.values())
                if(consumer!=LocalLabTerminalDeliveryAssessment.Consumer.G1)
                    check(joint.count(currentOrigin,consumer,
                        LocalLabTerminalDeliveryAssessment.Evidence.NOT_REACHED)==
                        (provokeSecondDeath?1L:0L),
                        "post-G1 failure never attempts derived consumer "+consumer);
            LocalLabTerminalEvidenceConsistencyAudit.Result verified=
                LocalLabTerminalEvidenceConsistencyAudit.inspect(stage);
            check(verified.status==
                    LocalLabTerminalEvidenceConsistencyAudit.Status.CONSISTENT&&
                    verified.observedTerminals==cycles&&
                    verified.findings.isEmpty()&&
                    !verified.hasDurableGrantAcknowledgement()&&
                    !verified.safeToAutomaticallyReplay(),
                "real G1 postground exception reconciles first-blocker and six consumer marginals");
            LocalLabTerminalDeliverySummary allConsumers=
                LocalLabTerminalDeliverySummary.from(stage);
            check(allConsumers.observedTerminals==cycles&&
                    allConsumers.count(
                        LocalLabTerminalDeliveryAssessment.Consumer.G1,
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED)==1L&&
                    allConsumers.count(
                        LocalLabTerminalDeliveryAssessment.Consumer.G1,
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .THREW_MUTATION_UNCERTAIN)==
                        (provokeSecondDeath?1L:0L)&&
                    allConsumers.hasObservedMutationUncertainty()==provokeSecondDeath&&
                    !allConsumers.hasDurableGrantAcknowledgement()&&
                    !allConsumers.safeToAutomaticallyReplay(),
                "G1 observed returns/throws never become durable grants");
            for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                    LocalLabTerminalDeliveryAssessment.Consumer.values())
                if(consumer!=LocalLabTerminalDeliveryAssessment.Consumer.G1)
                    check(allConsumers.count(consumer,
                            LocalLabTerminalDeliveryAssessment.Evidence.NOT_REACHED)==
                            (provokeSecondDeath?1L:0L)&&
                        allConsumers.count(consumer,
                            LocalLabTerminalDeliveryAssessment.Evidence
                                .RETURNED_UNACKNOWLEDGED)==1L,
                        "G1 pre-stage exception leaves five derived consumers unattempted "+
                            consumer);
            check(allConsumers.firstBoundaryCount(
                    LocalLabTerminalFailureBoundaryTally.Boundary
                        .NO_THROW_RECORDED)==1L&&
                allConsumers.firstBoundaryCount(
                    LocalLabTerminalFailureBoundaryTally.Boundary.G1)==
                    (provokeSecondDeath?1L:0L)&&
                stage.failureBoundaries.count(
                    LocalLabTerminalFailureBoundaryTally.Boundary.G1)==
                    (provokeSecondDeath?1L:0L),
                "real G1 postground exception is the earliest blocking stage");
            MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics all=
                installed.runtime().terminalObserverDiagnostics();
            check(all.totalAttempts==cycles&&
                all.failedAttempts==(provokeSecondDeath?1L:0L)&&
                all.recentAttempts.size()==cycles,
                "outer observer error count matches original G1 failure");

            if(provokeSecondDeath){
                LocalLabSequentialTerminalStageTrace.Report second=
                    stage.recent.get(1);
                LocalLabTerminalDeliveryAssessment secondAssessment=
                    LocalLabTerminalDeliveryAssessment.from(second);
                check(secondAssessment.hasMutationUncertainty()&&
                    secondAssessment.evidence(
                        LocalLabTerminalDeliveryAssessment.Consumer.G1)==
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .THREW_MUTATION_UNCERTAIN&&
                    !secondAssessment.safeToAutomaticallyReplay()&&
                    !secondAssessment.hasDurableGrantAcknowledgement(),
                    "G1 may mutate before exception; no automatic replay");
                for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                        LocalLabTerminalDeliveryAssessment.Consumer.values())
                    if(consumer!=LocalLabTerminalDeliveryAssessment.Consumer.G1)
                        check(secondAssessment.evidence(consumer)==
                            LocalLabTerminalDeliveryAssessment.Evidence.NOT_REACHED,
                            "G1 failure stopped all downstream consumers "+consumer);

                check(second.g1Outcome==
                        LocalLabSequentialTerminalStageTrace.G1Outcome.THREW&&
                    IllegalStateException.class.getName().equals(
                        second.g1FailureType)&&
                    second.spawnOriginMatchedAtDispatch,
                    "second canonical G1 fails before derived consumers");
                for(LocalLabSequentialTerminalStageTrace.Stage consumer:
                        LocalLabSequentialTerminalStageTrace.Stage.values()){
                    check(second.outcome(consumer)==
                        LocalLabSequentialTerminalStageTrace.Outcome.NOT_REACHED&&
                        second.failureType(consumer)==null&&
                        stage.outcomeCount(consumer,
                            LocalLabSequentialTerminalStageTrace.Outcome.RETURNED)
                            ==1L&&
                        stage.outcomeCount(consumer,
                            LocalLabSequentialTerminalStageTrace.Outcome.NOT_REACHED)
                            ==1L,
                        "g1 exception leaves all derived stages unattempted "+consumer);
                }
                check(all.recentAttempts.get(1).outcome==
                    MonsterSpawnerPvmRuntime.TerminalObserverOutcome.THREW,
                    "G1 duplicate pending-respawn exception remains visible");
            }else {
                for(LocalLabSequentialTerminalStageTrace.Stage consumer:
                        LocalLabSequentialTerminalStageTrace.Stage.values())
                    check(stage.outcomeCount(consumer,
                        LocalLabSequentialTerminalStageTrace.Outcome.RETURNED)==1L&&
                        stage.outcomeCount(consumer,
                            LocalLabSequentialTerminalStageTrace.Outcome.NOT_REACHED)==0L,
                        "healthy G1 marks no phantom unattempted stage");
                check(all.recentAttempts.get(0).outcome==
                    MonsterSpawnerPvmRuntime.TerminalObserverOutcome.RETURNED,
                    "healthy G1 preserves existing terminal outcome");
            }
            check(world.pvmRecords().receiptCount()==cycles,
                "G1 record may already apply before callback throws; NEVER replay");
        }finally {
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception {
        run(true);
        System.out.println(
            "G1967_G1_PRE_STAGE_FAILURE_DIAGNOSTIC_PASS"+
            " actualCanonicalDeaths=2"+
            " G1ReturnedThenThrew=true"+
            " allDerivedStagesNotReachedOnG1Failure=true"+
            " postSettlementNoReplay=true");
    }
    private static void check(boolean condition,String why){
        if(!condition)throw new AssertionError("G19.6.7/68 "+why);
    }
    private G1967G1PreStageFailureDiagnosticTest(){}
}
