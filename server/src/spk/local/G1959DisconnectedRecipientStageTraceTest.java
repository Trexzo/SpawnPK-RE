package spk.local;

/**
 * G19.5.9/60: real installed sequential trace cannot mistake an NPC's
 * settled drop for a delivered recipient reward after player retirement,
 * and cannot accept a terminal from a foreign World.
 */
public final class G1959DisconnectedRecipientStageTraceTest {
    static void run(boolean crossWorld)throws Exception{
        World world=World.isolatedForTest(60_000L);
        World foreign=crossWorld?World.isolatedForTest(60_000L):null;
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        final String username=crossWorld
            ?"g1960-foreign-terminal"
            :"g1959-disconnected-recipient";
        try{
            generation=world.registerPlayer(player,username);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);

            // Explicitly select default sequential dispatch instead of
            // inheriting the process-wide diagnostic experiment property.
            LocalMonsterSpawnerActivationRuntime graph=
                LocalLabMonsterSpawnerProvisioning.create(world,null);
            MonsterSpawnerService spawner=graph.service();
            spawner.openSession(
                username,
                LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(username,0);
            spawner.activate(username,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime().spawnAndBind(
                    username,username,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            check(npc!=null&&
                    spawned.runtime.recipientOrigin!=null&&
                    spawned.runtime.recipientOrigin.player==player&&
                    spawned.runtime.recipientOrigin.generation==generation,
                "real canonical death retains immutable spawn recipient witness");

            if(!crossWorld){
                check(world.unregisterPlayer(player,generation),
                    "retire exact recipient BEFORE real death finalization");
            }
            NpcLifecycleService.DamageResult lethal=
                world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick());
            check(lethal.newlyDied&&lethal.hitpointsAfter==0,
                "actual canonical lethal lifecycle");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                graph.runtime().finalizeIfOwned(npc);
            check(terminal!=null&&
                    terminal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.sourceWorld==world&&
                    terminal.finalization!=null&&
                    terminal.settlement!=null&&
                    graph.runtime().get(npc.id)==null&&
                    world.npcs().byId(npc.id)==null&&
                    world.npcLifecycle().get(npc.id)==null,
                "exact terminal finalized and retired in source World");

            LocalLabSettledDeathEnvelope envelope=
                LocalLabSettledDeathEnvelope.observe(world,terminal);
            check(envelope.npcId.equals(npc.id)&&
                    envelope.deathTick==terminal.finalization.deathTick&&
                    envelope.spawnRecipientGeneration==generation&&
                    envelope.observedCurrentGeneration==
                        (crossWorld?generation:-1L)&&
                    envelope.originStatus==
                        (crossWorld
                            ?LocalLabSettledDeathEnvelope.OriginStatus
                                .CURRENT_SPAWN_RECIPIENT
                            :LocalLabSettledDeathEnvelope.OriginStatus
                                .NO_CURRENT_RECIPIENT)&&
                    envelope.spawnRecipientStillCurrent()==crossWorld,
                "settled envelope separates connected and absent original recipient");

            GroundItem ground=world.groundItems().findOwned(
                995,3088,3495,0,username);
            check(ground!=null&&ground.amount==1,
                "settled drop remains attributed to configured recipient name");

            LocalLabSequentialTerminalStageTrace.Snapshot actual=
                graph.sequentialTerminalDiagnosticSnapshot();
            check(actual!=null&&
                    actual.observed==1&&actual.evicted==0&&
                    actual.recent.size()==1,
                "one real installed default-stage trace");
            LocalLabOriginFirstFailureBoundaryTally.Snapshot originBoundary=
                actual.originFailureBoundaries;
            LocalLabSettledDeathEnvelope.OriginStatus firstOrigin=
                crossWorld
                    ?LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT
                    :LocalLabSettledDeathEnvelope.OriginStatus.NO_CURRENT_RECIPIENT;
            LocalLabTerminalFailureBoundaryTally.Boundary first=
                crossWorld
                    ?LocalLabTerminalFailureBoundaryTally.Boundary.NO_THROW_RECORDED
                    :LocalLabTerminalFailureBoundaryTally.Boundary.NO_CURRENT_RECIPIENT;
            check(originBoundary.count(firstOrigin,first)==1L&&
                !originBoundary.hasDurableGrantAcknowledgement()&&
                !originBoundary.safeToAutomaticallyReplay(),
                "actual disconnected or current origin retains correct first blocking stage");
            LocalLabOriginConsumerOutcomeTally.Snapshot joint=
                actual.originConsumerOutcomes;
            check(joint.originCount(envelope.originStatus)==1L&&
                    joint.count(envelope.originStatus,
                        LocalLabTerminalDeliveryAssessment.Consumer.G1,
                        LocalLabTerminalDeliveryAssessment.Evidence.RETURNED_UNACKNOWLEDGED)
                        ==1L&&
                    !joint.hasDurableGrantAcknowledgement()&&
                    !joint.safeToAutomaticallyReplay(),
                "exact installed recipient origin separated from G1 callback outcome");
            if(!crossWorld)
                for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                        LocalLabTerminalDeliveryAssessment.Consumer.values())
                    if(consumer!=LocalLabTerminalDeliveryAssessment.Consumer.G1)
                        check(joint.count(
                            LocalLabSettledDeathEnvelope.OriginStatus.NO_CURRENT_RECIPIENT,
                            consumer,
                            LocalLabTerminalDeliveryAssessment.Evidence
                                .SKIPPED_NO_CURRENT_RECIPIENT)==1L,
                            "disconnected origin never upgrades skipped consumer "+consumer);
            LocalLabTerminalEvidenceConsistencyAudit.Result audit=
                LocalLabTerminalEvidenceConsistencyAudit.inspect(actual);
            check(audit.status==
                    LocalLabTerminalEvidenceConsistencyAudit.Status.CONSISTENT&&
                    audit.observedTerminals==1L&&
                    audit.findings.isEmpty()&&
                    !audit.hasDurableGrantAcknowledgement()&&
                    !audit.safeToAutomaticallyReplay(),
                "disconnected or exact recipient audit consistent and strictly non-granting");
            LocalLabTerminalDeliverySummary summary=
                LocalLabTerminalDeliverySummary.from(actual);
            check(summary.observedTerminals==1L&&
                    summary.originCount(envelope.originStatus)==1L&&
                    summary.count(
                        LocalLabTerminalDeliveryAssessment.Consumer.G1,
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED)==1L&&
                    !summary.hasDurableGrantAcknowledgement()&&
                    !summary.safeToAutomaticallyReplay(),
                "installed one-event summary is neither reward nor replay ACK");
            if(!crossWorld)
                for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                        LocalLabTerminalDeliveryAssessment.Consumer.values())
                    if(consumer!=LocalLabTerminalDeliveryAssessment.Consumer.G1)
                        check(summary.count(consumer,
                            LocalLabTerminalDeliveryAssessment.Evidence
                                .SKIPPED_NO_CURRENT_RECIPIENT)==1L,
                            "disconnected recipient remains an explicit skip "+consumer);
            check(summary.firstBoundaryCount(
                    crossWorld
                        ?LocalLabTerminalFailureBoundaryTally.Boundary
                            .NO_THROW_RECORDED
                        :LocalLabTerminalFailureBoundaryTally.Boundary
                            .NO_CURRENT_RECIPIENT)==1L,
                "disconnection must never be labeled a consumer exception");
            LocalLabSequentialTerminalStageTrace.Report report=
                actual.recent.get(0);
            check(actual.originCount(envelope.originStatus)==1L&&
                actual.originCount(
                    crossWorld
                        ?LocalLabSettledDeathEnvelope.OriginStatus.NO_CURRENT_RECIPIENT
                        :LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT)
                    ==0L,
                "no-current and exact-current origin totals stay disjoint");
            LocalLabTerminalDeliveryAssessment assessment=
                LocalLabTerminalDeliveryAssessment.from(report);
            check(!assessment.hasDurableGrantAcknowledgement()&&
                    !assessment.safeToAutomaticallyReplay()&&
                    assessment.originStatus==envelope.originStatus,
                "origin observations never authorize restart-safe grants");
            if(!crossWorld){
                for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                        LocalLabTerminalDeliveryAssessment.Consumer.values())
                    if(consumer!=LocalLabTerminalDeliveryAssessment.Consumer.G1)
                        check(assessment.evidence(consumer)==
                            LocalLabTerminalDeliveryAssessment.Evidence
                                .SKIPPED_NO_CURRENT_RECIPIENT,
                            "no current recipient skips derived consumer "+consumer);
            }
            check(report.npcId.equals(npc.id)&&
                    report.originStatus==envelope.originStatus&&
                    report.deathTile.equals(envelope.deathTile)&&
                    report.definitionId==envelope.definitionId&&
                    report.deathTick==terminal.finalization.deathTick&&
                    report.recipientRef.equals(username)&&
                    report.spawnRecipientGeneration==generation&&
                    report.spawnOriginMatchedAtDispatch==crossWorld&&
                    report.observedCurrentGeneration==
                        (crossWorld?generation:-1L),
                "trace separates current matching origin from missing recipient");

            if(!crossWorld){
                for(LocalLabSequentialTerminalStageTrace.Stage stage:
                    LocalLabSequentialTerminalStageTrace.Stage.values())
                    check(report.outcome(stage)==
                            LocalLabSequentialTerminalStageTrace.Outcome
                                .SKIPPED_NO_CURRENT_RECIPIENT&&
                            report.failureType(stage)==null,
                        "no current recipient means NO stage attempted");
                MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics all=
                    graph.runtime().terminalObserverDiagnostics();
                check(all.totalAttempts==1L&&all.failedAttempts==0L&&
                        all.recentAttempts.size()==1&&
                        all.recentAttempts.get(0).outcome==
                            MonsterSpawnerPvmRuntime.TerminalObserverOutcome
                                .RETURNED&&
                        world.pvmRecords().receiptCount()==0L,
                    "successful observer return is NOT proof of recipient credit");
                check(graph.runtime().finalizeIfOwned(npc).status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                    "recipient disconnect does not permit retry grant");
            }else{
                String envelopeRejected=null;
                try{
                    LocalLabSettledDeathEnvelope.observe(foreign,terminal);
                }catch(IllegalStateException expected){
                    envelopeRejected=expected.getMessage();
                }
                check("SETTLED_DEATH_ENVELOPE_SOURCE_WORLD_MISMATCH"
                        .equals(envelopeRejected),
                    "foreign World cannot capture a settled death envelope");
                LocalLabSequentialTerminalStageTrace wrong=
                    new LocalLabSequentialTerminalStageTrace(foreign);
                String rejected=null;
                try{
                    wrong.begin(terminal);
                }catch(IllegalStateException expected){
                    rejected=expected.getMessage();
                }
                check(
                    "SEQUENTIAL_TERMINAL_STAGE_SOURCE_WORLD_MISMATCH"
                        .equals(rejected),
                    "foreign World begin rejects before stage callback");

                LocalLabSequentialTerminalStageTrace source=
                    new LocalLabSequentialTerminalStageTrace(world);
                LocalLabSequentialTerminalStageTrace.Capture capture=
                    source.begin(terminal);
                String recordRejected=null;
                try{
                    wrong.record(capture);
                }catch(IllegalStateException expected){
                    recordRejected=expected.getMessage();
                }
                check(
                    "SEQUENTIAL_TERMINAL_STAGE_SOURCE_WORLD_MISMATCH"
                        .equals(recordRejected)&&
                    wrong.snapshot().observed==0L&&
                    wrong.snapshot().recent.isEmpty(),
                    "foreign World cannot record or mutate its history");
                source.record(capture);
                check(source.snapshot().observed==1L&&
                        source.snapshot().recent.size()==1,
                    "failed foreign attempt never consumes source capture");
            }
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
            if(foreign!=null)foreign.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(false);
        System.out.println(
            "G1959_DISCONNECTED_RECIPIENT_STAGE_TRACE_PASS"+
            " exactCanonicalSpawnAndSettlement=true"+
            " fiveStagesSkippedNoCurrentRecipient=true"+
            " observerReturnedButNoCredit=true"+
            " oneGroundDropNoReplay=true");
    }

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G19.5.9/60 "+why);
    }

    private G1959DisconnectedRecipientStageTraceTest(){}
}
