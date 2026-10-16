package spk.local;

/**
 * G19.6.5: World identity is necessary but not sufficient to accept an
 * invocation-local stage capture. Two collectors for the SAME World must
 * never attribute the other collector's terminal observation as their own.
 * This is diagnostic isolation, never reward/grant authority.
 */
public final class G1965SameWorldSequentialTraceCaptureOwnerTest {
    public static void main(String[] args)throws Exception {
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        final String name="g1965-same-world-trace-owner";
        try {
            generation=world.registerPlayer(player,name);
            player.movement().restoreAccountState(false,100,3087,3495,0);
            LocalMonsterSpawnerActivationRuntime graph=
                LocalLabMonsterSpawnerProvisioning.create(world,null);
            MonsterSpawnerService service=graph.service();
            service.openSession(name,
                LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            service.selectRow(name,0);
            service.activate(name,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime().spawnAndBind(name,name,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            check(world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick()).newlyDied,
                "real canonical NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                graph.runtime().finalizeIfOwned(npc);
            check(terminal.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.settlement!=null&&terminal.sourceWorld==world&&
                    graph.sequentialTerminalDiagnosticSnapshot().observed==1L,
                "real installed observer and actual ground settlement");

            LocalLabSettledDeathEnvelope envelope=
                LocalLabSettledDeathEnvelope.observe(world,terminal);
            check(envelope.npcId.equals(npc.id)&&
                    envelope.definitionId==terminal.finalization.definitionId&&
                    envelope.deathTile.equals(terminal.finalization.deathTile)&&
                    envelope.deathTick==terminal.finalization.deathTick&&
                    name.equals(envelope.recipientRef)&&
                    envelope.spawnRecipientGeneration==generation&&
                    envelope.observedCurrentGeneration==generation&&
                    envelope.spawnRecipientStillCurrent()&&
                    envelope.originStatus==
                        LocalLabSettledDeathEnvelope.OriginStatus
                            .CURRENT_SPAWN_RECIPIENT,
                "real canonical finalization and drop identity reflected in envelope");
            LocalLabSequentialTerminalStageTrace a=
                new LocalLabSequentialTerminalStageTrace(world);
            LocalLabSequentialTerminalStageTrace b=
                new LocalLabSequentialTerminalStageTrace(world);
            LocalLabSequentialTerminalStageTrace.Capture capture=a.begin(terminal);
            check(capture.envelope.npcId.equals(envelope.npcId)&&
                    capture.envelope.deathTick==envelope.deathTick&&
                    capture.envelope.originStatus==envelope.originStatus,
                "installed stage capture consumes validated terminal evidence");
            String rejection=null;
            try {
                b.record(capture);
            }catch(IllegalStateException expected){
                rejection=expected.getMessage();
            }
            check("SEQUENTIAL_TERMINAL_STAGE_CAPTURE_OWNER_MISMATCH"
                    .equals(rejection),
                "same-World foreign collector cannot accept capture");
            check(b.snapshot().observed==0L&&
                    b.snapshot().recent.isEmpty()&&
                    b.snapshot().outcomeCount(
                        LocalLabSequentialTerminalStageTrace.Stage.G4,
                        LocalLabSequentialTerminalStageTrace.Outcome.NOT_REACHED
                    )==0L,
                "rejected foreign capture cannot affect history or totals");
            a.record(capture);
            check(a.snapshot().g1OutcomeCount(
                    LocalLabSequentialTerminalStageTrace.G1Outcome.NOT_OBSERVED)
                    ==1L&&
                b.snapshot().g1OutcomeCount(
                    LocalLabSequentialTerminalStageTrace.G1Outcome.NOT_OBSERVED)
                    ==0L,
                "test collector never fabricates a G1 callback and foreign rejection cannot affect totals");
            check(a.snapshot().observed==1L&&
                    a.snapshot().recent.size()==1&&
                    a.snapshot().outcomeCount(
                        LocalLabSequentialTerminalStageTrace.Stage.G4,
                        LocalLabSequentialTerminalStageTrace.Outcome.NOT_REACHED
                    )==1L,
                "original collector alone consumes its detached diagnostic capture");
            String repeated=null;
            try {
                a.record(capture);
            }catch(IllegalStateException expected){
                repeated=expected.getMessage();
            }
            check("SEQUENTIAL_TERMINAL_STAGE_CAPTURE_ALREADY_RECORDED"
                    .equals(repeated)&&a.snapshot().observed==1L,
                "captured observation cannot be recorded twice");
            // Deliberately construct an inconsistent TEST observation
            // from this already genuinely settled NPC. The original
            // G1 exception must prevent ANY derived G4 callback from
            // being recorded as returned. No gameplay callback executes.
            LocalLabSequentialTerminalStageTrace corrupted=
                new LocalLabSequentialTerminalStageTrace(world);
            LocalLabSequentialTerminalStageTrace.Capture inconsistent=
                corrupted.begin(terminal);
            inconsistent.g1Threw(new IllegalStateException(
                "test-only ambiguous postground callback"));
            inconsistent.run(
                LocalLabSequentialTerminalStageTrace.Stage.G4,()->{}
            );
            String inconsistentRejected=null;
            try{
                corrupted.record(inconsistent);
            }catch(IllegalStateException expected){
                inconsistentRejected=expected.getMessage();
            }
            check("TERMINAL_FAILURE_BOUNDARY_POST_THROW_STAGE_G4"
                    .equals(inconsistentRejected)&&
                    corrupted.snapshot().observed==0L&&
                    corrupted.snapshot().failureBoundaries.count(
                        LocalLabTerminalFailureBoundaryTally.Boundary.G1)==0L&&
                    corrupted.snapshot().originConsumerOutcomes.originCount(
                        LocalLabSettledDeathEnvelope.OriginStatus
                            .CURRENT_SPAWN_RECIPIENT)==0L,
                "invalid post-G1 evidence rejected before all marginal and joint tallies");
            LocalLabTerminalEvidenceConsistencyAudit.Result unchanged=
                LocalLabTerminalEvidenceConsistencyAudit.inspect(
                    corrupted.snapshot()
                );
            check(unchanged.status==
                    LocalLabTerminalEvidenceConsistencyAudit.Status.CONSISTENT&&
                    unchanged.observedTerminals==0L&&
                    unchanged.findings.isEmpty()&&
                    !unchanged.safeToAutomaticallyReplay(),
                "invalid event rejected without leaving inconsistent forensic state");
            // This deliberately constructs two *diagnostic* observations
            // from the SAME genuinely settled canonical death. It never
            // invokes the World terminal observer or grants a reward.
            // Marginal and cross-tab counters are mathematically equal;
            // the new retained-detail check must still reject the
            // duplicate NPC identity as ambiguous evidence.
            LocalLabSequentialTerminalStageTrace duplicated=
                new LocalLabSequentialTerminalStageTrace(world);
            duplicated.record(duplicated.begin(terminal));
            duplicated.record(duplicated.begin(terminal));
            LocalLabSequentialTerminalStageTrace.Snapshot duplicateSnapshot=
                duplicated.snapshot();
            check(duplicateSnapshot.observed==2L&&
                    duplicateSnapshot.recent.size()==2,
                "test-only duplicate evidence shares a real settled death");
            LocalLabTerminalEvidenceConsistencyAudit.Result duplicateAudit=
                LocalLabTerminalEvidenceConsistencyAudit.inspect(
                    duplicateSnapshot);
            check(duplicateAudit.status==
                    LocalLabTerminalEvidenceConsistencyAudit.Status.DRIFT&&
                    duplicateAudit.findings.contains(
                        "DETAIL_DUPLICATE_NPC_ID")&&
                    !duplicateAudit.hasDurableGrantAcknowledgement()&&
                    !duplicateAudit.safeToAutomaticallyReplay(),
                "mathematically balanced duplicate NPC report is flagged as unreliable");

            check(graph.runtime().finalizeIfOwned(npc).status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    world.groundItems().findOwned(
                        995,3088,3495,0,name).amount==1,
                "diagnostics never re-finalize or re-settle");
            System.out.println("G1965_SAME_WORLD_STAGE_CAPTURE_OWNER_PASS"+
                " genuineSettledTerminal=true"+
                " sameWorldCollectorIsolation=true"+
                " failedForeignRecordDoesNotConsumeCapture=true"+
                " noGrantOrReplay=true");
        }finally {
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G19.6.5 "+why);
    }
    private G1965SameWorldSequentialTraceCaptureOwnerTest(){}
}
