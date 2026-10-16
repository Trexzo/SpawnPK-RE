package spk.local;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable aggregate of the DEFAULT LocalLab terminal callback OBSERVATIONS
 * already counted by LocalLabSequentialTerminalStageTrace.
 *
 * Counts survive eviction of the 128 individual reports because they are
 * taken from per-World monotonic diagnostics. The counts do not correlate
 * consumer outcomes for evicted individual deaths, and do not establish
 * grants, durable event IDs, ACKs, restart safety or authority to replay.
 */
final class LocalLabTerminalDeliverySummary {
    final long observedTerminals;
    final long evictedDetailReports;
    final int retainedDetailReports;

    final Map<
        LocalLabTerminalDeliveryAssessment.Consumer,
        Map<LocalLabTerminalDeliveryAssessment.Evidence,Long>
    > consumerCounts;
    final Map<LocalLabSettledDeathEnvelope.OriginStatus,Long> originCounts;
    final Map<LocalLabTerminalFailureBoundaryTally.Boundary,Long>
        firstBoundaryCounts;

    private LocalLabTerminalDeliverySummary(
        LocalLabSequentialTerminalStageTrace.Snapshot source,
        EnumMap<
            LocalLabTerminalDeliveryAssessment.Consumer,
            Map<LocalLabTerminalDeliveryAssessment.Evidence,Long>
        > consumers
    ){
        observedTerminals=source.observed;
        evictedDetailReports=source.evicted;
        retainedDetailReports=source.recent.size();
        consumerCounts=Collections.unmodifiableMap(consumers);
        originCounts=Collections.unmodifiableMap(
            new EnumMap<LocalLabSettledDeathEnvelope.OriginStatus,Long>(
                source.originCounts
            )
        );
        firstBoundaryCounts=Collections.unmodifiableMap(
            new EnumMap<LocalLabTerminalFailureBoundaryTally.Boundary,Long>(
                source.failureBoundaries.boundaryCounts
            )
        );
    }

    long count(
        LocalLabTerminalDeliveryAssessment.Consumer consumer,
        LocalLabTerminalDeliveryAssessment.Evidence evidence
    ){
        return consumerCounts.get(Objects.requireNonNull(consumer,"consumer"))
            .get(Objects.requireNonNull(evidence,"evidence"));
    }

    long originCount(LocalLabSettledDeathEnvelope.OriginStatus status){
        return originCounts.get(Objects.requireNonNull(status,"origin"));
    }

    long firstBoundaryCount(LocalLabTerminalFailureBoundaryTally.Boundary boundary){
        return firstBoundaryCounts.get(Objects.requireNonNull(boundary,"boundary"));
    }

    boolean hasObservedMutationUncertainty(){
        for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                LocalLabTerminalDeliveryAssessment.Consumer.values())
            if(count(
                    consumer,
                    LocalLabTerminalDeliveryAssessment.Evidence.THREW_MUTATION_UNCERTAIN
                )>0L)
                return true;
        return false;
    }

    boolean hasDurableGrantAcknowledgement(){return false;}
    boolean safeToAutomaticallyReplay(){return false;}

    private static long addSaturated(long first,long second){
        if(first>=Long.MAX_VALUE-second)return Long.MAX_VALUE;
        return first+second;
    }

    long totalObservationsFor(
        LocalLabTerminalDeliveryAssessment.Consumer consumer
    ){
        long sum=0L;
        for(LocalLabTerminalDeliveryAssessment.Evidence outcome:
                LocalLabTerminalDeliveryAssessment.Evidence.values())
            sum=addSaturated(sum,count(consumer,outcome));
        return sum;
    }

    static LocalLabTerminalDeliverySummary from(
        LocalLabSequentialTerminalStageTrace.Snapshot snapshot
    ){
        LocalLabSequentialTerminalStageTrace.Snapshot source=
            Objects.requireNonNull(snapshot,"snapshot");
        EnumMap<
            LocalLabTerminalDeliveryAssessment.Consumer,
            Map<LocalLabTerminalDeliveryAssessment.Evidence,Long>
        > counts=new EnumMap<
            LocalLabTerminalDeliveryAssessment.Consumer,
            Map<LocalLabTerminalDeliveryAssessment.Evidence,Long>
        >(LocalLabTerminalDeliveryAssessment.Consumer.class);

        for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                LocalLabTerminalDeliveryAssessment.Consumer.values()){
            EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long> row=
                new EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long>(
                    LocalLabTerminalDeliveryAssessment.Evidence.class);
            for(LocalLabTerminalDeliveryAssessment.Evidence evidence:
                    LocalLabTerminalDeliveryAssessment.Evidence.values())
                row.put(evidence,0L);
            counts.put(consumer,Collections.unmodifiableMap(row));
        }

        EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long> g1=
            new EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long>(
                counts.get(LocalLabTerminalDeliveryAssessment.Consumer.G1));
        g1.put(
            LocalLabTerminalDeliveryAssessment.Evidence.RETURNED_UNACKNOWLEDGED,
            source.g1OutcomeCount(
                LocalLabSequentialTerminalStageTrace.G1Outcome.RETURNED)
        );
        g1.put(
            LocalLabTerminalDeliveryAssessment.Evidence.THREW_MUTATION_UNCERTAIN,
            source.g1OutcomeCount(
                LocalLabSequentialTerminalStageTrace.G1Outcome.THREW)
        );
        g1.put(
            LocalLabTerminalDeliveryAssessment.Evidence.NOT_OBSERVED,
            source.g1OutcomeCount(
                LocalLabSequentialTerminalStageTrace.G1Outcome.NOT_OBSERVED)
        );
        counts.put(
            LocalLabTerminalDeliveryAssessment.Consumer.G1,
            Collections.unmodifiableMap(g1)
        );

        for(LocalLabSequentialTerminalStageTrace.Stage stage:
                LocalLabSequentialTerminalStageTrace.Stage.values()){
            EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long> row=
                new EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long>(
                    counts.get(
                        LocalLabTerminalDeliveryAssessment.Consumer.valueOf(stage.name())
                    ));
            row.put(
                LocalLabTerminalDeliveryAssessment.Evidence.RETURNED_UNACKNOWLEDGED,
                source.outcomeCount(
                    stage,LocalLabSequentialTerminalStageTrace.Outcome.RETURNED)
            );
            row.put(
                LocalLabTerminalDeliveryAssessment.Evidence.THREW_MUTATION_UNCERTAIN,
                source.outcomeCount(
                    stage,LocalLabSequentialTerminalStageTrace.Outcome.THREW)
            );
            row.put(
                LocalLabTerminalDeliveryAssessment.Evidence.NOT_REACHED,
                source.outcomeCount(
                    stage,LocalLabSequentialTerminalStageTrace.Outcome.NOT_REACHED)
            );
            row.put(
                LocalLabTerminalDeliveryAssessment.Evidence.SKIPPED_NO_CURRENT_RECIPIENT,
                source.outcomeCount(
                    stage,
                    LocalLabSequentialTerminalStageTrace.Outcome
                        .SKIPPED_NO_CURRENT_RECIPIENT)
            );
            counts.put(
                LocalLabTerminalDeliveryAssessment.Consumer.valueOf(stage.name()),
                Collections.unmodifiableMap(row)
            );
        }

        return new LocalLabTerminalDeliverySummary(source,counts);
    }
}
