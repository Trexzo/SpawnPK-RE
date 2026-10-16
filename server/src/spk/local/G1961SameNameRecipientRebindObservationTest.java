package spk.local;

/**
 * G19.6.1/62 — characterize EXISTING LocalLab same-name World progression
 * without changing attribution. A historical G1847 contract intentionally
 * lets a replacement WorldPlayer of the same name complete World-local
 * tasks from a previously spawned NPC. This test must preserve that
 * contract while exposing the origin mismatch in read-only evidence.
 */
public final class G1961SameNameRecipientRebindObservationTest {
    static void run(boolean isolated)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=new WorldPlayer();
        final String user=isolated
            ?"g1962-observe-rebind":"g1961-observe-rebind";
        long originalGeneration=-1L;
        long replacementGeneration=-1L;
        try{
            originalGeneration=world.registerPlayer(original,user);
            original.movement().restoreAccountState(
                false,100,3087,3495,0);

            // Existing G1847/G1863 compatibility: one World-name-keyed
            // active G4 task is begun by the old player at spawn time.
            LocalLabSlayerRuntime slayer=world.localLabSlayer();
            LocalLabSlayerRuntime.StartResult started=
                slayer.startMonsterHunter(user,world.clock().tick());
            require(started!=null&&started.created&&
                    started.status.task.objective.progress==0L,
                "original World-owned G4 task was active");

            LocalLabTerminalStageIsolation isolation=
                isolated?new LocalLabTerminalStageIsolation():null;
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world,isolation);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                user,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(user,0);
            spawner.activate(user,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawn=
                installed.runtime().spawnAndBind(
                    user,user,3088,3495,0);
            WorldNpc npc=spawn.spawn.combat.spawn.npc;
            MonsterSpawnerPvmRuntime.RecipientOrigin origin=
                spawn.runtime.recipientOrigin;
            require(npc!=null&&origin!=null&&
                    origin.player==original&&
                    origin.generation==originalGeneration&&
                    origin.stillCurrentIn(world),
                "exact original NPC origin at spawn");

            require(world.unregisterPlayer(original,originalGeneration),
                "original player retired");
            replacementGeneration=world.registerPlayer(replacement,user);
            replacement.movement().restoreAccountState(
                false,100,3087,3495,0);
            require(replacement!=original&&
                    world.players().byName(user)==replacement&&
                    !origin.stillCurrentIn(world),
                "same-name replacement is a different WorldPlayer");

            // Activate separate objectives on the replacement. The
            // established LocalLab policy currently credits these through
            // username-based World state; do not silently alter that.
            LocalLabTaskScrollRuntime g15=world.localLabTaskScrolls();
            require(g15.assign(user,world.clock().tick()).assignedNow&&
                    g15.active(user).objective.progress==0L,
                "replacement G15 active");
            LocalLabAdventureRuntime g16=world.localLabAdventures();
            require(g16.activate(user).activatedNow&&
                    g16.objective(user).progress==0L,
                "replacement G16 active");
            LocalLabDailyMoneyMakingRuntime g18=
                world.localLabDailyMoneyMaking();
            require(g18.activateEasyIfCurrent(
                        user,replacement,replacementGeneration
                    ).activatedNow&&
                    g18.snapshot(user).objective.progress==0L,
                "replacement G18 active");

            require(world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick()).newlyDied,
                "real canonical lethal NPC");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                installed.runtime().finalizeIfOwned(npc);
            require(terminal!=null&&
                    terminal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.recipientOrigin==origin&&
                    terminal.settlement!=null&&
                    installed.runtime().get(npc.id)==null,
                "real finalized NPC retains immutable old spawn origin");

            LocalLabSettledDeathEnvelope envelope=
                LocalLabSettledDeathEnvelope.observe(world,terminal);
            require(envelope.originStatus==
                    LocalLabSettledDeathEnvelope.OriginStatus.SAME_NAME_REBOUND&&
                    envelope.spawnRecipientGeneration==originalGeneration&&
                    envelope.observedCurrentGeneration==replacementGeneration&&
                    !envelope.spawnRecipientStillCurrent()&&
                    envelope.npcId.equals(npc.id)&&
                    envelope.deathTick==terminal.finalization.deathTick,
                "settled death envelope retains old origin separate from replacement player");

            GroundItem drop=world.groundItems().findOwned(
                995,3088,3495,0,user);
            require(drop!=null&&drop.amount==1&&
                    world.pvmRecords().receiptCount()==1L&&
                    world.pvmRecords().snapshot(original).kills==0L&&
                    world.pvmRecords().snapshot(replacement).kills==1L&&
                    g15.active(user).objective.progress==1L&&
                    g16.objective(user).progress==1L&&
                    g18.snapshot(user).objective.progress==1L,
                "preexisting name-keyed LocalLab credits are preserved");

            MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics outer=
                installed.runtime().terminalObserverDiagnostics();
            require(outer.totalAttempts==1L&&outer.failedAttempts==0L&&
                    outer.recentAttempts.size()==1&&
                    outer.recentAttempts.get(0).outcome==
                        MonsterSpawnerPvmRuntime.TerminalObserverOutcome
                            .RETURNED,
                "one returning post-settlement observer");
            if(isolated){
                LocalLabTerminalStageIsolation.DiagnosticSnapshot stages=
                    installed.terminalDiagnosticSnapshot();
                require(stages!=null&&stages.observedCount==1L&&
                        stages.recentReports.size()==1,
                    "actual opt-in isolated stage report");
                LocalLabTerminalStageIsolation.Report report=
                    stages.recentReports.get(0);
                require(!report.spawnOriginMatchedAtDispatch,
                    "existing isolated evidence flags mismatch");
                for(LocalLabTerminalStageIsolation.Stage stage:
                    LocalLabTerminalStageIsolation.Stage.values())
                    require(report.outcome(stage)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED,
                        "isolated current behavior preserved stage="+stage);
            }else{
                LocalLabSequentialTerminalStageTrace.Snapshot stages=
                    installed.sequentialTerminalDiagnosticSnapshot();
                require(stages!=null&&stages.observed==1L&&
                        stages.recent.size()==1,
                    "one default sequential stage report");
                LocalLabSequentialTerminalStageTrace.Report report=
                    stages.recent.get(0);
                require(stages.originCount(
                        LocalLabSettledDeathEnvelope.OriginStatus.SAME_NAME_REBOUND)
                        ==1L&&
                    stages.originCount(
                        LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT)
                        ==0L,
                    "historical owner rebound is aggregated without credit policy changes");
                LocalLabOriginConsumerOutcomeTally.Snapshot joint=
                    stages.originConsumerOutcomes;
                require(joint.originCount(
                        LocalLabSettledDeathEnvelope.OriginStatus.SAME_NAME_REBOUND)==1L&&
                    joint.count(
                        LocalLabSettledDeathEnvelope.OriginStatus.SAME_NAME_REBOUND,
                        LocalLabTerminalDeliveryAssessment.Consumer.G1,
                        LocalLabTerminalDeliveryAssessment.Evidence.RETURNED_UNACKNOWLEDGED)
                        ==1L&&
                    joint.count(
                        LocalLabSettledDeathEnvelope.OriginStatus.SAME_NAME_REBOUND,
                        LocalLabTerminalDeliveryAssessment.Consumer.G15,
                        LocalLabTerminalDeliveryAssessment.Evidence.RETURNED_UNACKNOWLEDGED)
                        ==1L&&
                    joint.count(
                        LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT,
                        LocalLabTerminalDeliveryAssessment.Consumer.G15,
                        LocalLabTerminalDeliveryAssessment.Evidence.RETURNED_UNACKNOWLEDGED)
                        ==0L&&
                    !joint.safeToAutomaticallyReplay(),
                    "historical same-name credit remains a rebound origin observation");
                LocalLabTerminalDeliveryAssessment assessment=
                    LocalLabTerminalDeliveryAssessment.from(report);
                require(assessment.originStatus==
                        LocalLabSettledDeathEnvelope.OriginStatus.SAME_NAME_REBOUND&&
                    assessment.evidence(
                        LocalLabTerminalDeliveryAssessment.Consumer.G1)==
                        LocalLabTerminalDeliveryAssessment.Evidence.RETURNED_UNACKNOWLEDGED&&
                    !assessment.safeToAutomaticallyReplay()&&
                    !assessment.hasDurableGrantAcknowledgement(),
                    "same-name credits remain observable, never retroactively authoritative");
                require(!report.spawnOriginMatchedAtDispatch&&
                        report.originStatus==
                            LocalLabSettledDeathEnvelope.OriginStatus.SAME_NAME_REBOUND&&
                        report.definitionId==terminal.finalization.definitionId&&
                        report.deathTile.equals(terminal.finalization.deathTile)&&
                        report.spawnRecipientGeneration==
                            originalGeneration&&
                        report.observedCurrentGeneration==
                            replacementGeneration&&
                        report.npcId.equals(npc.id)&&
                        report.deathTick==terminal.finalization.deathTick,
                    "default trace flags precise old and current generation");
                for(LocalLabSequentialTerminalStageTrace.Stage stage:
                    LocalLabSequentialTerminalStageTrace.Stage.values())
                    require(report.outcome(stage)==
                        LocalLabSequentialTerminalStageTrace.Outcome.RETURNED,
                        "default current behavior preserved stage="+stage);
            }

            require(installed.runtime().finalizeIfOwned(npc).status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    world.pvmRecords().receiptCount()==1L&&
                    world.groundItems().findOwned(
                        995,3088,3495,0,user).amount==1,
                "no duplicate credit or ground settlement on retry");
        }finally{
            if(replacementGeneration>=0L&&
               world.players().owns(replacement,replacementGeneration))
                world.unregisterPlayer(replacement,replacementGeneration);
            if(originalGeneration>=0L&&
               world.players().owns(original,originalGeneration))
                world.unregisterPlayer(original,originalGeneration);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(false);
        System.out.println(
            "G1961_SAME_NAME_RECIPIENT_REBIND_OBSERVATION_PASS"+
            " oldSpawnPlayerWitness=true"+
            " replacementNameCreditPolicyPreserved=true"+
            " defaultStageOriginMismatchVisible=true"+
            " oldAndObservedGenerationCaptured=true"+
            " noDuplicateSettlement=true");
    }

    private static void require(boolean ok,String detail){
        if(!ok)throw new AssertionError("G19.6.1/62 "+detail);
    }
    private G1961SameNameRecipientRebindObservationTest(){}
}
