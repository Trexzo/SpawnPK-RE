package spk.local;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * World-process-local cross-tabulation of settled NPC terminal observations:
 * recipient origin at dispatch × each of six consumer callback outcomes.
 *
 * Unlike separately retained origin and consumer marginal totals, a cross-tab
 * preserves aggregate relationships after 128 detailed reports are evicted.
 * It does NOT preserve which individual evicted NPC had which outcome, nor
 * retain WorldPlayer, Throwable or mutable settlement references.
 *
 * Observation ONLY: no durable ACK, replay, persisted ID, or original
 * SpawnPK killer/credit policy is recovered or authorized here.
 */
final class LocalLabOriginConsumerOutcomeTally {
    private final EnumMap<
        LocalLabSettledDeathEnvelope.OriginStatus,
        EnumMap<
            LocalLabTerminalDeliveryAssessment.Consumer,
            EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long>
        >
    > counts=new EnumMap<
        LocalLabSettledDeathEnvelope.OriginStatus,
        EnumMap<
            LocalLabTerminalDeliveryAssessment.Consumer,
            EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long>
        >
    >(LocalLabSettledDeathEnvelope.OriginStatus.class);

    private final EnumMap<LocalLabSettledDeathEnvelope.OriginStatus,Long>
        originTotals=new EnumMap<LocalLabSettledDeathEnvelope.OriginStatus,Long>(
            LocalLabSettledDeathEnvelope.OriginStatus.class
        );

    LocalLabOriginConsumerOutcomeTally(){
        for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                LocalLabSettledDeathEnvelope.OriginStatus.values()){
            EnumMap<
                LocalLabTerminalDeliveryAssessment.Consumer,
                EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long>
            > perConsumer=new EnumMap<
                LocalLabTerminalDeliveryAssessment.Consumer,
                EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long>
            >(LocalLabTerminalDeliveryAssessment.Consumer.class);

            for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                    LocalLabTerminalDeliveryAssessment.Consumer.values()){
                EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long> row=
                    new EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long>(
                        LocalLabTerminalDeliveryAssessment.Evidence.class
                    );
                for(LocalLabTerminalDeliveryAssessment.Evidence evidence:
                        LocalLabTerminalDeliveryAssessment.Evidence.values())
                    row.put(evidence,0L);
                perConsumer.put(consumer,row);
            }
            counts.put(origin,perConsumer);
            originTotals.put(origin,0L);
        }
    }

    private static long incrementSaturating(long previous){
        return previous==Long.MAX_VALUE?previous:previous+1L;
    }

    // Called only by an exact World-local trace AFTER all same-World,
    // same-collector and one-shot capture provenance checks pass.
    void accept(
        LocalLabSequentialTerminalStageTrace.Report report,
        LocalLabTerminalDeliveryAssessment assessment
    ){
        LocalLabSequentialTerminalStageTrace.Report checked=
            Objects.requireNonNull(report,"report");
        LocalLabTerminalDeliveryAssessment delivery=
            Objects.requireNonNull(assessment,"assessment");
        LocalLabSettledDeathEnvelope.OriginStatus origin=
            Objects.requireNonNull(checked.originStatus,"originStatus");
        if(delivery.originStatus!=origin||
           !delivery.npcId.equals(checked.npcId)||
           delivery.deathTick!=checked.deathTick)
            throw new IllegalArgumentException(
                "ORIGIN_CONSUMER_OUTCOME_ASSESSMENT_MISMATCH");

        EnumMap<
            LocalLabTerminalDeliveryAssessment.Consumer,
            EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long>
        > perConsumer=counts.get(origin);
        if(perConsumer==null)
            throw new IllegalArgumentException(
                "ORIGIN_CONSUMER_OUTCOME_UNKNOWN_ORIGIN");

        // All rows are exhaustively initialized and the assessment always
        // contains all six consumers. Do not permit partial cross-tab writes.
        for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                LocalLabTerminalDeliveryAssessment.Consumer.values())
            Objects.requireNonNull(
                delivery.evidence(consumer),"consumerEvidence"
            );

        for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                LocalLabTerminalDeliveryAssessment.Consumer.values()){
            LocalLabTerminalDeliveryAssessment.Evidence evidence=
                delivery.evidence(consumer);
            EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long> row=
                perConsumer.get(consumer);
            row.put(evidence,incrementSaturating(row.get(evidence)));
        }
        originTotals.put(origin,
            incrementSaturating(originTotals.get(origin)));
    }

    Snapshot snapshot(){return new Snapshot(counts,originTotals);}

    static final class Snapshot {
        final Map<
            LocalLabSettledDeathEnvelope.OriginStatus,
            Map<
                LocalLabTerminalDeliveryAssessment.Consumer,
                Map<LocalLabTerminalDeliveryAssessment.Evidence,Long>
            >
        > crossCounts;
        final Map<LocalLabSettledDeathEnvelope.OriginStatus,Long> originCounts;

        private Snapshot(
            EnumMap<
                LocalLabSettledDeathEnvelope.OriginStatus,
                EnumMap<
                    LocalLabTerminalDeliveryAssessment.Consumer,
                    EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long>
                >
            > source,
            EnumMap<LocalLabSettledDeathEnvelope.OriginStatus,Long> origins
        ){
            EnumMap<
                LocalLabSettledDeathEnvelope.OriginStatus,
                Map<
                    LocalLabTerminalDeliveryAssessment.Consumer,
                    Map<LocalLabTerminalDeliveryAssessment.Evidence,Long>
                >
            > immutable=new EnumMap<
                LocalLabSettledDeathEnvelope.OriginStatus,
                Map<
                    LocalLabTerminalDeliveryAssessment.Consumer,
                    Map<LocalLabTerminalDeliveryAssessment.Evidence,Long>
                >
            >(LocalLabSettledDeathEnvelope.OriginStatus.class);
            for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                    LocalLabSettledDeathEnvelope.OriginStatus.values()){
                EnumMap<
                    LocalLabTerminalDeliveryAssessment.Consumer,
                    Map<LocalLabTerminalDeliveryAssessment.Evidence,Long>
                > rows=new EnumMap<
                    LocalLabTerminalDeliveryAssessment.Consumer,
                    Map<LocalLabTerminalDeliveryAssessment.Evidence,Long>
                >(LocalLabTerminalDeliveryAssessment.Consumer.class);
                for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                        LocalLabTerminalDeliveryAssessment.Consumer.values())
                    rows.put(consumer,Collections.unmodifiableMap(
                        new EnumMap<LocalLabTerminalDeliveryAssessment.Evidence,Long>(
                            source.get(origin).get(consumer)
                        )
                    ));
                immutable.put(origin,Collections.unmodifiableMap(rows));
            }
            crossCounts=Collections.unmodifiableMap(immutable);
            originCounts=Collections.unmodifiableMap(
                new EnumMap<LocalLabSettledDeathEnvelope.OriginStatus,Long>(
                    origins
                )
            );
        }

        long count(
            LocalLabSettledDeathEnvelope.OriginStatus origin,
            LocalLabTerminalDeliveryAssessment.Consumer consumer,
            LocalLabTerminalDeliveryAssessment.Evidence evidence
        ){
            return crossCounts.get(Objects.requireNonNull(origin,"origin"))
                .get(Objects.requireNonNull(consumer,"consumer"))
                .get(Objects.requireNonNull(evidence,"evidence"));
        }

        long originCount(
            LocalLabSettledDeathEnvelope.OriginStatus origin
        ){
            return originCounts.get(Objects.requireNonNull(origin,"origin"));
        }

        boolean hasDurableGrantAcknowledgement(){return false;}
        boolean safeToAutomaticallyReplay(){return false;}
    }
}
