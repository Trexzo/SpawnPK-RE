package spk.local;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * G19.6.6: the DEFAULT installed progression observer processes 131
 * actual canonical NPC deaths, retaining per-stage outcome totals even
 * after the detailed 128-entry diagnostic ring evicts three reports.
 * RETURNED is only callback status, NEVER a reward-applied ACK.
 */
public final class G1966SequentialStageOutcomeEvictionTotalsTest {
    public static void main(String[] args)throws Exception {
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        final String name="g1966-actual-stage-outcome-evictions";
        // The unregistered session owner suppresses unrelated G1 auto-respawn
        // scheduling while preserving a real connected recipient and all five
        // installed default progression callbacks across 131 manual spawns.
        final String sessionOwner=name+"-unregistered-spawner-owner";
        try {
            generation=world.registerPlayer(player,name);
            player.movement().restoreAccountState(false,100,3087,3495,0);
            LocalMonsterSpawnerActivationRuntime graph=
                LocalLabMonsterSpawnerProvisioning.create(world,null);
            MonsterSpawnerService service=graph.service();
            service.openSession(sessionOwner,
                LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            service.selectRow(sessionOwner,0);
            service.activate(sessionOwner,1);

            int cycles=LocalLabSequentialTerminalStageTrace.MAX_REPORTS+3;
            ArrayList<EntityId> ids=new ArrayList<EntityId>();
            HashSet<EntityId> unique=new HashSet<EntityId>();
            LocalLabSequentialTerminalStageTrace.Snapshot early=null;
            for(int n=0;n<cycles;n++){
                if(n>0){
                    // The real G1 teardown may retire the owner session
                    // after each finalized NPC. Re-establish the exact
                    // LocalLab catalog selection before another genuine
                    // spawn instead of bypassing session lifecycle.
                    if(service.getSession(sessionOwner)==null){
                        service.openSession(
                            sessionOwner,
                            LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
                        );
                        service.selectRow(sessionOwner,0);
                    }
                    service.activate(sessionOwner,1);
                }
                MonsterSpawnerPvmRuntime.SpawnResult spawned=
                    graph.runtime().spawnAndBind(sessionOwner,name,3088,3495,0);
                WorldNpc npc=spawned.spawn.combat.spawn.npc;
                check(npc!=null&&unique.add(npc.id),
                    "each cycle uses a distinct real NPC identity");
                ids.add(npc.id);
                check(world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick()).newlyDied,
                    "genuine lifecycle lethal damage "+n);
                MonsterSpawnerPvmRuntime.FinalizeResult result=
                    graph.runtime().finalizeIfOwned(npc);
                check(result!=null&&
                        result.status==
                            MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                        result.settlement!=null&&
                        graph.runtime().get(npc.id)==null&&
                        world.npcs().byId(npc.id)==null,
                    "canonical death and ground settlement exactly once "+n);
                if(n==0)early=graph.sequentialTerminalDiagnosticSnapshot();
            }

            LocalLabSequentialTerminalStageTrace.Snapshot after=
                graph.sequentialTerminalDiagnosticSnapshot();
            check(early!=null&&early.observed==1L&&
                    early.evicted==0L&&early.recent.size()==1&&
                    after.observed==cycles&&after.evicted==3L&&
                    after.recent.size()==128&&unique.size()==cycles,
                "bounded detail and monotonic aggregate observations");
            check(after.recent.get(0).npcId.equals(ids.get(3))&&
                    after.recent.get(127).npcId.equals(ids.get(cycles-1)),
                "oldest three genuine death reports evicted first");
            for(LocalLabSequentialTerminalStageTrace.Stage stage:
                    LocalLabSequentialTerminalStageTrace.Stage.values()){
                check(early.outcomeCount(stage,
                        LocalLabSequentialTerminalStageTrace.Outcome.RETURNED)
                        ==1L&&
                        after.outcomeCount(stage,
                            LocalLabSequentialTerminalStageTrace.Outcome.RETURNED)
                            ==cycles,
                    "returned callbacks persist in detached totals "+stage);
                for(LocalLabSequentialTerminalStageTrace.Outcome outcome:
                        LocalLabSequentialTerminalStageTrace.Outcome.values())
                    if(outcome!=
                            LocalLabSequentialTerminalStageTrace.Outcome.RETURNED)
                        check(after.outcomeCount(stage,outcome)==0L,
                            "no fabricated failure/skip/reach outcomes "+stage);
            }
            LocalLabSequentialTerminalStageTrace.G1Outcome returned=
                LocalLabSequentialTerminalStageTrace.G1Outcome.RETURNED;
            check(early.g1OutcomeCount(returned)==1L&&
                    after.g1OutcomeCount(returned)==cycles&&
                    after.g1OutcomeCount(
                        LocalLabSequentialTerminalStageTrace.G1Outcome.THREW)==0L&&
                    after.g1OutcomeCount(
                        LocalLabSequentialTerminalStageTrace.G1Outcome.NOT_OBSERVED)==0L,
                "131 real installed G1 callbacks survive detail eviction");
            LocalLabSettledDeathEnvelope.OriginStatus current=
                LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT;
            check(early.originCount(current)==1L&&
                    after.originCount(current)==cycles,
                "131 genuine current spawn origins retained after three evictions");
            for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                    LocalLabSettledDeathEnvelope.OriginStatus.values())
                if(origin!=current)
                    check(after.originCount(origin)==0L,
                        "no invented stale or disconnected origins "+origin);
            boolean originImmutable=false;
            try{
                after.originCounts.put(current,0L);
            }catch(UnsupportedOperationException expected){
                originImmutable=true;
            }
            check(originImmutable&&early.originCount(current)==1L,
                "historic origin aggregate snapshot detached and immutable");
            boolean g1Readonly=false;
            try{
                after.g1OutcomeCounts.put(returned,0L);
            }catch(UnsupportedOperationException expected){
                g1Readonly=true;
            }
            check(g1Readonly&&early.g1OutcomeCount(returned)==1L,
                "G1 aggregate snapshot deeply detached and immutable");
            LocalLabOriginConsumerOutcomeTally.Snapshot firstJoint=
                early.originConsumerOutcomes;
            LocalLabOriginConsumerOutcomeTally.Snapshot finalJoint=
                after.originConsumerOutcomes;
            LocalLabSettledDeathEnvelope.OriginStatus currentOrigin=
                LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT;
            check(firstJoint.originCount(currentOrigin)==1L&&
                    finalJoint.originCount(currentOrigin)==cycles&&
                    !finalJoint.hasDurableGrantAcknowledgement()&&
                    !finalJoint.safeToAutomaticallyReplay(),
                "retained exact current-origin joint count after 131 real deaths");
            for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                    LocalLabTerminalDeliveryAssessment.Consumer.values()){
                check(firstJoint.count(currentOrigin,consumer,
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED)==1L&&
                    finalJoint.count(currentOrigin,consumer,
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED)==cycles,
                    "current-origin six-consumer correlation survives eviction "+consumer);
                for(LocalLabTerminalDeliveryAssessment.Evidence e:
                        LocalLabTerminalDeliveryAssessment.Evidence.values())
                    if(e!=LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED)
                        check(finalJoint.count(currentOrigin,consumer,e)==0L,
                            "no fabricated origin/consumer failure "+consumer);
            }
            boolean jointOuterImmutable=false;
            boolean jointInnerImmutable=false;
            try{
                finalJoint.crossCounts.clear();
            }catch(UnsupportedOperationException expected){
                jointOuterImmutable=true;
            }
            try{
                finalJoint.crossCounts.get(currentOrigin).get(
                    LocalLabTerminalDeliveryAssessment.Consumer.G1).put(
                    LocalLabTerminalDeliveryAssessment.Evidence.RETURNED_UNACKNOWLEDGED,
                    0L);
            }catch(UnsupportedOperationException expected){
                jointInnerImmutable=true;
            }
            check(jointOuterImmutable&&jointInnerImmutable&&
                    firstJoint.originCount(currentOrigin)==1L,
                "joint origin by consumer history deeply detached and immutable");
            LocalLabTerminalDeliverySummary firstSummary=
                LocalLabTerminalDeliverySummary.from(early);
            LocalLabTerminalDeliverySummary finalSummary=
                LocalLabTerminalDeliverySummary.from(after);
            check(firstSummary.observedTerminals==1L&&
                    finalSummary.observedTerminals==cycles&&
                    finalSummary.evictedDetailReports==3L&&
                    finalSummary.retainedDetailReports==128&&
                    !finalSummary.hasObservedMutationUncertainty()&&
                    !finalSummary.hasDurableGrantAcknowledgement()&&
                    !finalSummary.safeToAutomaticallyReplay(),
                "aggregate summary after 131 canonical settled NPC deaths");
            for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                    LocalLabTerminalDeliveryAssessment.Consumer.values()){
                check(firstSummary.count(consumer,
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED)==1L&&
                    finalSummary.count(consumer,
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED)==cycles&&
                    finalSummary.totalObservationsFor(consumer)==cycles,
                    "six consumer counts retained beyond ring eviction "+consumer);
                for(LocalLabTerminalDeliveryAssessment.Evidence evidence:
                        LocalLabTerminalDeliveryAssessment.Evidence.values())
                    if(evidence!=LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED)
                        check(finalSummary.count(consumer,evidence)==0L,
                            "no fabricated consumer failure or skipped state "+consumer);
            }
            boolean summaryImmutable=false;
            boolean summaryNestedImmutable=false;
            try{
                finalSummary.consumerCounts.clear();
            }catch(UnsupportedOperationException expected){
                summaryImmutable=true;
            }
            try{
                finalSummary.consumerCounts.get(
                    LocalLabTerminalDeliveryAssessment.Consumer.G1).put(
                        LocalLabTerminalDeliveryAssessment.Evidence.RETURNED_UNACKNOWLEDGED,
                        0L);
            }catch(UnsupportedOperationException expected){
                summaryNestedImmutable=true;
            }
            check(summaryImmutable&&summaryNestedImmutable&&
                    firstSummary.count(
                        LocalLabTerminalDeliveryAssessment.Consumer.G1,
                        LocalLabTerminalDeliveryAssessment.Evidence
                            .RETURNED_UNACKNOWLEDGED)==1L,
                "detached six-consumer historical map deeply immutable");
            LocalLabTerminalFailureBoundaryTally.Boundary noThrow=
                LocalLabTerminalFailureBoundaryTally.Boundary.NO_THROW_RECORDED;
            check(early.failureBoundaries.count(noThrow)==1L&&
                    after.failureBoundaries.count(noThrow)==cycles&&
                    firstSummary.firstBoundaryCount(noThrow)==1L&&
                    finalSummary.firstBoundaryCount(noThrow)==cycles,
                "131 no-throw traces retained beyond 3 detail evictions");
            for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                    LocalLabTerminalFailureBoundaryTally.Boundary.values())
                if(boundary!=noThrow)
                    check(after.failureBoundaries.count(boundary)==0L,
                        "no synthetic first failure "+boundary);
            boolean boundaryReadonly=false;
            try{
                after.failureBoundaries.boundaryCounts.put(noThrow,0L);
            }catch(UnsupportedOperationException expected){
                boundaryReadonly=true;
            }
            check(boundaryReadonly&&
                    early.failureBoundaries.count(noThrow)==1L,
                "first-failure historical snapshots detached and immutable");
            boolean outerReadonly=false;
            boolean innerReadonly=false;
            try{
                after.outcomeCounts.clear();
            }catch(UnsupportedOperationException expected){
                outerReadonly=true;
            }
            try{
                after.outcomeCounts.get(
                    LocalLabSequentialTerminalStageTrace.Stage.G4).put(
                        LocalLabSequentialTerminalStageTrace.Outcome.RETURNED,
                        0L);
            }catch(UnsupportedOperationException expected){
                innerReadonly=true;
            }
            check(outerReadonly&&innerReadonly&&
                    early.outcomeCount(
                        LocalLabSequentialTerminalStageTrace.Stage.G4,
                        LocalLabSequentialTerminalStageTrace.Outcome.RETURNED)
                        ==1L,
                "historical snapshot deep immutable and detached");
            GroundItem item=world.groundItems().findOwned(
                995,3088,3495,0,name);
            check(item!=null&&item.amount==cycles&&
                    graph.runtime().terminalObserverDiagnostics()
                        .totalAttempts==cycles,
                "actual ground settlement and observer count one per death");
            System.out.println(
                "G1966_SEQUENTIAL_STAGE_OUTCOME_EVICTION_TOTALS_PASS"+
                " actualCanonicalNpcDeaths=131"+
                " boundedReports=128"+
                " preservedPerStageReturns=131"+
                " immutableDetachedCounters=true"+
                " durableRewardAck=false");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G19.6.6 "+why);
    }
    private G1966SequentialStageOutcomeEvictionTotalsTest(){}
}
