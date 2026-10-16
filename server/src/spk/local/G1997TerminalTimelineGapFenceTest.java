package spk.local;

/**
 * G19.9.7–G20.0.0: real canonical settled NPC, separate read-only
 * test collector, and deliberately impossible/valid sequential
 * diagnostic stage prefixes. Never runs real grant consumers twice.
 */
public final class G1997TerminalTimelineGapFenceTest {
    static void run(int scenario)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        final String name="g1997-timeline-case-"+scenario;
        long generation=-1L;
        try{
            generation=world.registerPlayer(player,name);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            LocalMonsterSpawnerActivationRuntime graph=
                LocalLabMonsterSpawnerProvisioning.create(world,null);
            MonsterSpawnerService spawner=graph.service();
            spawner.openSession(name,
                LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(name,0);
            spawner.activate(name,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime().spawnAndBind(name,name,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            check(npc!=null&&world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick()).newlyDied,
                "genuine canonical NPC lethal event");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                graph.runtime().finalizeIfOwned(npc);
            check(terminal.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                terminal.settlement!=null&&
                graph.sequentialTerminalDiagnosticSnapshot().observed==1L,
                "actual installed G1 and stage callbacks already completed");
            GroundItem drop=world.groundItems().findOwned(
                995,3088,3495,0,name);
            check(drop!=null&&drop.amount==1&&
                world.pvmRecords().receiptCount()==1L,
                "one genuine settled drop and no duplicate G1 credit");

            // Isolated diagnostic collector: the following run callbacks
            // are empty TEST observations; they grant no rewards.
            LocalLabSequentialTerminalStageTrace observer=
                new LocalLabSequentialTerminalStageTrace(world);
            LocalLabSequentialTerminalStageTrace.Capture capture=
                observer.begin(terminal);
            String expectedFailure=null;
            if(scenario==1){
                // Impossible: G14 reached while G4 was unattempted.
                capture.run(
                    LocalLabSequentialTerminalStageTrace.Stage.G14,()->{});
                expectedFailure=
                    "TERMINAL_FAILURE_BOUNDARY_REACHED_AFTER_GAP_G14";
            }else if(scenario==2){
                // Impossible: G16 threw while G14/G15 unattempted.
                capture.run(
                    LocalLabSequentialTerminalStageTrace.Stage.G4,()->{});
                try{
                    capture.run(
                        LocalLabSequentialTerminalStageTrace.Stage.G16,
                        ()->{throw new IllegalStateException(
                            "test-only no-op stage failure");});
                }catch(IllegalStateException expected){
                    check(expected.getMessage().contains("test-only"),
                        "synthetic no-op callback throw consumed by fixture");
                }
                expectedFailure=
                    "TERMINAL_FAILURE_BOUNDARY_REACHED_AFTER_GAP_G16";
            }else if(scenario==3){
                // A test-only partial prefix is legitimate evidence: the
                // observer did not record a G1 call or later callbacks.
                capture.run(
                    LocalLabSequentialTerminalStageTrace.Stage.G4,()->{});
                capture.run(
                    LocalLabSequentialTerminalStageTrace.Stage.G14,()->{});
            }else if(scenario==4){
                // Legitimate empty test collector; all stages NOT_REACHED
                // with G1 NOT_OBSERVED, not evidence of reward failure.
            }else throw new IllegalArgumentException("scenario="+scenario);

            String rejected=null;
            try{
                observer.record(capture);
            }catch(IllegalStateException violation){
                rejected=violation.getMessage();
            }
            if(expectedFailure!=null){
                check(expectedFailure.equals(rejected),
                    "exact gap failure stage="+scenario+" got="+rejected);
                check(observer.snapshot().observed==0L&&
                    observer.snapshot().recent.isEmpty()&&
                    observer.snapshot().g1OutcomeCount(
                        LocalLabSequentialTerminalStageTrace.G1Outcome.NOT_OBSERVED)
                        ==0L&&
                    observer.snapshot().failureBoundaries.count(
                        LocalLabTerminalFailureBoundaryTally.Boundary.NO_THROW_RECORDED)
                        ==0L&&
                    observer.snapshot().originConsumerOutcomes.originCount(
                        LocalLabSettledDeathEnvelope.OriginStatus
                            .CURRENT_SPAWN_RECIPIENT)==0L,
                    "invalid chronology rejected before ALL counters and ring");
                LocalLabTerminalEvidenceConsistencyAudit.Result audit=
                    LocalLabTerminalEvidenceConsistencyAudit.inspect(
                        observer.snapshot());
                check(audit.status==
                    LocalLabTerminalEvidenceConsistencyAudit.Status.CONSISTENT&&
                    audit.findings.isEmpty(),
                    "rejected synthetic chronology cannot corrupt audit");
            }else{
                check(rejected==null&&
                    observer.snapshot().observed==1L&&
                    observer.snapshot().recent.size()==1,
                    "valid partial diagnostic prefix remains accepted");
                LocalLabSequentialTerminalStageTrace.Report detail=
                    observer.snapshot().recent.get(0);
                check(detail.g1Outcome==
                    LocalLabSequentialTerminalStageTrace.G1Outcome.NOT_OBSERVED,
                    "synthetic capture does not forge real G1 outcome");
                if(scenario==3)
                    check(detail.outcome(
                        LocalLabSequentialTerminalStageTrace.Stage.G14)==
                            LocalLabSequentialTerminalStageTrace.Outcome.RETURNED&&
                        detail.outcome(
                        LocalLabSequentialTerminalStageTrace.Stage.G15)==
                            LocalLabSequentialTerminalStageTrace.Outcome.NOT_REACHED,
                        "valid G4/G14 prefix and unattempted trailing stages");
                LocalLabTerminalEvidenceConsistencyAudit.Result audit=
                    LocalLabTerminalEvidenceConsistencyAudit.inspect(
                        observer.snapshot());
                check(audit.status==
                    LocalLabTerminalEvidenceConsistencyAudit.Status.CONSISTENT&&
                    !audit.safeToAutomaticallyReplay()&&
                    !audit.hasDurableGrantAcknowledgement(),
                    "accepted test-only prefix remains purely diagnostic");
            }
            check(graph.runtime().finalizeIfOwned(npc).status==
                MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                world.groundItems().findOwned(
                    995,3088,3495,0,name).amount==1&&
                world.pvmRecords().receiptCount()==1L,
                "neither rejected nor accepted diagnostic resettles or grants");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(1);
        System.out.println("G1997_TERMINAL_TIMELINE_G14_GAP_FENCE_PASS"+
            " realNpcGroundSettlement=true"+
            " G14AfterUnreachedG4Rejected=true"+
            " zeroPartialCollectorMutation=true"+
            " noGrantReplay=true");
    }

    static void check(boolean ok,String description){
        if(!ok)throw new AssertionError("G19.9.7–G20.0.0 "+description);
    }
    private G1997TerminalTimelineGapFenceTest(){}
}
