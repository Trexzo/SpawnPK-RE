package spk.local;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * World-process-local forensic observation of the DEFAULT sequential
 * post-settlement G4/G14/G15/G16/G18 progression callback.
 *
 * A recorded RETURNED is NOT evidence that a reward was credited (a
 * consumer may have no active task). THREW may mean partial mutation.
 * NOT_REACHED means the sequential callback stopped before that stage.
 *
 * No persistence, grant, replay, reconnect, "ACK", recovery or original
 * SpawnPK server-side kill-credit authority is implemented here.
 */
final class LocalLabSequentialTerminalStageTrace {
    static final int MAX_REPORTS=128;

    enum Stage { G4, G14, G15, G16, G18 }
    enum Outcome {
        RETURNED,
        THREW,
        NOT_REACHED,
        SKIPPED_NO_CURRENT_RECIPIENT
    }

    // G1 executes before the five derived progression consumers.
    // RETURNED means the G1 callback returned, NOT that its receipt was
    // durably acknowledged. THREW may be after a partial G1 mutation.
    enum G1Outcome { NOT_OBSERVED, RETURNED, THREW }

    static final class Report {
        final EntityId npcId;
        final int definitionId;
        final Tile deathTile;
        final long deathTick;
        final String recipientRef;
        final LocalLabSettledDeathEnvelope.OriginStatus originStatus;
        final long spawnRecipientGeneration;
        // Observation ONLY. A false match means the current same-name
        // WorldPlayer is not the immutable NPC spawn-time player/epoch.
        // This does not authorize blocking or replaying any grant.
        final boolean spawnOriginMatchedAtDispatch;
        final long observedCurrentGeneration;
        final G1Outcome g1Outcome;
        final String g1FailureType;
        final Map<Stage,Outcome> outcomes;
        final Map<Stage,String> failureTypes;

        private Report(Capture capture){
            LocalLabSettledDeathEnvelope death=capture.envelope;
            npcId=death.npcId;
            definitionId=death.definitionId;
            deathTile=death.deathTile;
            deathTick=death.deathTick;
            recipientRef=death.recipientRef;
            spawnRecipientGeneration=death.spawnRecipientGeneration;
            originStatus=death.originStatus;
            spawnOriginMatchedAtDispatch=
                capture.spawnOriginMatchedAtDispatch;
            observedCurrentGeneration=capture.observedCurrentGeneration;
            g1Outcome=capture.g1Outcome;
            g1FailureType=capture.g1FailureType;
            outcomes=Collections.unmodifiableMap(
                new EnumMap<Stage,Outcome>(capture.outcomes)
            );
            failureTypes=Collections.unmodifiableMap(
                new EnumMap<Stage,String>(capture.failureTypes)
            );
        }

        Outcome outcome(Stage stage){
            return outcomes.get(
                Objects.requireNonNull(stage,"stage")
            );
        }

        String failureType(Stage stage){
            return failureTypes.get(
                Objects.requireNonNull(stage,"stage")
            );
        }
    }

    static final class Snapshot {
        final long observed;
        final long evicted;
        final List<Report> recent;
        final Map<Stage,Map<Outcome,Long>> outcomeCounts;
        final Map<G1Outcome,Long> g1OutcomeCounts;
        final Map<LocalLabSettledDeathEnvelope.OriginStatus,Long> originCounts;
        final LocalLabOriginConsumerOutcomeTally.Snapshot originConsumerOutcomes;
        final LocalLabTerminalFailureBoundaryTally.Snapshot failureBoundaries;

        long originCount(LocalLabSettledDeathEnvelope.OriginStatus origin){
            return originCounts.get(Objects.requireNonNull(origin,"origin"));
        }

        long g1OutcomeCount(G1Outcome outcome){
            return g1OutcomeCounts.get(
                Objects.requireNonNull(outcome,"g1Outcome")
            );
        }

        long outcomeCount(Stage stage,Outcome outcome){
            return outcomeCounts.get(Objects.requireNonNull(stage,"stage"))
                .get(Objects.requireNonNull(outcome,"outcome"));
        }

        private Snapshot(
            long observed,
            long evicted,
            ArrayDeque<Report> reports,
            EnumMap<Stage,EnumMap<Outcome,Long>> totals,
            EnumMap<G1Outcome,Long> g1Totals,
            EnumMap<LocalLabSettledDeathEnvelope.OriginStatus,Long> originTotals,
            LocalLabOriginConsumerOutcomeTally.Snapshot jointEvidence,
            LocalLabTerminalFailureBoundaryTally.Snapshot boundaries
        ){
            this.observed=observed;
            this.evicted=evicted;
            this.recent=Collections.unmodifiableList(
                new ArrayList<Report>(reports)
            );
            EnumMap<Stage,Map<Outcome,Long>> detached=
                new EnumMap<Stage,Map<Outcome,Long>>(Stage.class);
            for(Stage stage:Stage.values())
                detached.put(stage,Collections.unmodifiableMap(
                    new EnumMap<Outcome,Long>(totals.get(stage))
                ));
            outcomeCounts=Collections.unmodifiableMap(detached);
            g1OutcomeCounts=Collections.unmodifiableMap(
                new EnumMap<G1Outcome,Long>(g1Totals)
            );
            originCounts=Collections.unmodifiableMap(
                new EnumMap<LocalLabSettledDeathEnvelope.OriginStatus,Long>(
                    originTotals
                )
            );
            originConsumerOutcomes=Objects.requireNonNull(
                jointEvidence,"jointEvidence");
            failureBoundaries=Objects.requireNonNull(
                boundaries,"boundaries");
        }
    }

    /**
     * An invocation-local scratchpad. Captures which callbacks returned
     * and which were NOT invoked when fail-fast propagation stopped the
     * sequential chain; this is never a reward/credit dedupe key.
     */
    static final class Capture {
        final MonsterSpawnerPvmRuntime.FinalizeResult terminal;
        final LocalLabSettledDeathEnvelope envelope;
        private final LocalLabSequentialTerminalStageTrace owner;
        final boolean spawnOriginMatchedAtDispatch;
        final long observedCurrentGeneration;
        final EnumMap<Stage,Outcome> outcomes=
            new EnumMap<Stage,Outcome>(Stage.class);
        final EnumMap<Stage,String> failureTypes=
            new EnumMap<Stage,String>(Stage.class);
        private boolean recorded;
        private G1Outcome g1Outcome=G1Outcome.NOT_OBSERVED;
        private String g1FailureType;

        private void claimG1(G1Outcome outcome,Throwable failure){
            if(recorded||g1Outcome!=G1Outcome.NOT_OBSERVED)
                throw new IllegalStateException(
                    "SEQUENTIAL_TERMINAL_G1_ALREADY_OBSERVED"
                );
            g1Outcome=Objects.requireNonNull(outcome,"g1Outcome");
            g1FailureType=failure==null
                ?null:failure.getClass().getName();
        }

        void g1Returned(){
            claimG1(G1Outcome.RETURNED,null);
        }

        void g1Threw(Throwable failure){
            claimG1(G1Outcome.THREW,Objects.requireNonNull(failure,"failure"));
        }

        private Capture(
            LocalLabSequentialTerminalStageTrace owner,
            MonsterSpawnerPvmRuntime.FinalizeResult terminal,
            World world
        ){
            this.owner=owner;
            this.terminal=terminal;
            envelope=LocalLabSettledDeathEnvelope.observe(
                world,terminal
            );
            observedCurrentGeneration=envelope.observedCurrentGeneration;
            spawnOriginMatchedAtDispatch=
                envelope.spawnRecipientStillCurrent();
            for(Stage stage:Stage.values())
                outcomes.put(stage,Outcome.NOT_REACHED);
        }

        void run(
            Stage stage,
            Runnable callback
        ){
            Stage checked=Objects.requireNonNull(stage,"stage");
            Runnable action=Objects.requireNonNull(
                callback,"callback"
            );
            if(recorded||outcomes.get(checked)!=Outcome.NOT_REACHED)
                throw new IllegalStateException(
                    "sequential stage attempt already completed stage="+
                    checked
                );
            try{
                action.run();
                outcomes.put(checked,Outcome.RETURNED);
            }catch(RuntimeException|Error failure){
                outcomes.put(checked,Outcome.THREW);
                // Only retain type name, not throwable, payload,
                // user state or stack trace.
                failureTypes.put(
                    checked,failure.getClass().getName()
                );
                throw failure;
            }
        }

        void skipNoCurrentRecipient(){
            if(recorded)
                throw new IllegalStateException(
                    "sequential stage capture already recorded"
                );
            for(Stage stage:Stage.values()){
                if(outcomes.get(stage)!=Outcome.NOT_REACHED)
                    throw new IllegalStateException(
                        "cannot mark started stage skipped"
                    );
                outcomes.put(
                    stage,Outcome.SKIPPED_NO_CURRENT_RECIPIENT
                );
            }
        }
    }

    private final World world;
    private final ArrayDeque<Report> reports=new ArrayDeque<>();
    private final LocalLabOriginConsumerOutcomeTally jointTally=
        new LocalLabOriginConsumerOutcomeTally();
    private final LocalLabTerminalFailureBoundaryTally firstFailureTally=
        new LocalLabTerminalFailureBoundaryTally();
    private final EnumMap<Stage,EnumMap<Outcome,Long>> totals=
        new EnumMap<Stage,EnumMap<Outcome,Long>>(Stage.class);
    private final EnumMap<G1Outcome,Long> g1Totals=
        new EnumMap<G1Outcome,Long>(G1Outcome.class);
    private final EnumMap<LocalLabSettledDeathEnvelope.OriginStatus,Long>
        originTotals=new EnumMap<LocalLabSettledDeathEnvelope.OriginStatus,Long>(
            LocalLabSettledDeathEnvelope.OriginStatus.class
        );
    private long observed;
    private long evicted;

    LocalLabSequentialTerminalStageTrace(World world){
        this.world=Objects.requireNonNull(world,"world");
        for(G1Outcome outcome:G1Outcome.values())
            g1Totals.put(outcome,0L);
        for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                LocalLabSettledDeathEnvelope.OriginStatus.values())
            originTotals.put(origin,0L);
        for(Stage stage:Stage.values()){
            EnumMap<Outcome,Long> row=
                new EnumMap<Outcome,Long>(Outcome.class);
            for(Outcome outcome:Outcome.values())
                row.put(outcome,0L);
            totals.put(stage,row);
        }
    }

    Capture begin(
        MonsterSpawnerPvmRuntime.FinalizeResult terminal
    ){
        MonsterSpawnerPvmRuntime.FinalizeResult event=
            Objects.requireNonNull(terminal,"terminal");
        if(event.sourceWorld!=world)
            throw new IllegalStateException(
                "SEQUENTIAL_TERMINAL_STAGE_SOURCE_WORLD_MISMATCH"
            );
        if(event.status!=MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED||
           event.finalization==null||event.settlement==null)
            throw new IllegalArgumentException(
                "sequential stages require a settled FINALIZED event"
            );
        return new Capture(this,event,world);
    }

    synchronized void record(Capture capture){
        Capture checked=Objects.requireNonNull(
            capture,"capture"
        );
        if(checked.terminal.sourceWorld!=world)
            throw new IllegalStateException(
                "SEQUENTIAL_TERMINAL_STAGE_SOURCE_WORLD_MISMATCH"
            );
        if(checked.owner!=this)
            throw new IllegalStateException(
                "SEQUENTIAL_TERMINAL_STAGE_CAPTURE_OWNER_MISMATCH"
            );
        if(checked.recorded)
            throw new IllegalStateException(
                "SEQUENTIAL_TERMINAL_STAGE_CAPTURE_ALREADY_RECORDED"
            );
        // Detached report construction precedes counters/claim mutation.
        Report report=new Report(checked);
        LocalLabTerminalDeliveryAssessment assessment=
            LocalLabTerminalDeliveryAssessment.from(report);
        // Validate the complete fail-fast sequence BEFORE touching any
        // counters; corrupt/replayed captures cannot partially update
        // joint or first-failure aggregates.
        LocalLabTerminalFailureBoundaryTally.Boundary firstBoundary=
            LocalLabTerminalFailureBoundaryTally.classify(report);
        // Exact same-World/collector/one-shot checks above must succeed
        // before recording the joint origin/consumer evidence.
        jointTally.accept(report,assessment);
        firstFailureTally.acceptValidated(firstBoundary);
        checked.recorded=true;
        long originPrior=originTotals.get(report.originStatus);
        if(originPrior<Long.MAX_VALUE)
            originTotals.put(report.originStatus,originPrior+1L);
        long g1Prior=g1Totals.get(report.g1Outcome);
        if(g1Prior<Long.MAX_VALUE)
            g1Totals.put(report.g1Outcome,g1Prior+1L);
        for(Stage stage:Stage.values()){
            EnumMap<Outcome,Long> row=totals.get(stage);
            Outcome result=report.outcome(stage);
            long prior=row.get(result);
            if(prior<Long.MAX_VALUE)
                row.put(result,prior+1L);
        }

        if(observed<Long.MAX_VALUE)
            observed++;
        if(reports.size()==MAX_REPORTS){
            reports.removeFirst();
            if(evicted<Long.MAX_VALUE)
                evicted++;
        }
        reports.addLast(report);
    }

    synchronized Snapshot snapshot(){
        return new Snapshot(
            observed,evicted,reports,totals,g1Totals,originTotals,
            jointTally.snapshot(),firstFailureTally.snapshot()
        );
    }
}
