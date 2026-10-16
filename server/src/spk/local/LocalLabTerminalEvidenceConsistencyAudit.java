package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure, read-only integrity check of a single DEFAULT World-local terminal
 * diagnostic snapshot and its derived six-consumer/origin projections.
 *
 * CONSISTENT means these process-local counters reconcile mathematically;
 * it does NOT mean the original NPC attribution was right, rewards were
 * applied, accounts saved, or any consumer may be replayed.
 */
final class LocalLabTerminalEvidenceConsistencyAudit {
    enum Status { CONSISTENT, DRIFT, INDETERMINATE_SATURATED }
    static final int MAX_FINDINGS=64;

    static final class Result {
        final Status status;
        final long observedTerminals;
        final List<String> findings;

        private Result(Status status,long observed,List<String> findings){
            this.status=status;
            observedTerminals=observed;
            this.findings=Collections.unmodifiableList(
                new ArrayList<String>(findings)
            );
        }

        boolean hasDurableGrantAcknowledgement(){return false;}
        boolean safeToAutomaticallyReplay(){return false;}
    }

    private static final class Checker {
        final List<String> findings=new ArrayList<>();
        boolean saturated;

        void issue(String label){
            if(findings.size()<MAX_FINDINGS)findings.add(label);
        }

        void nonnegative(long value,String label){
            if(value<0L)issue("NEGATIVE_"+label);
            if(value==Long.MAX_VALUE)saturated=true;
        }

        long add(long left,long right,String label){
            nonnegative(left,label+"_LEFT");
            nonnegative(right,label+"_RIGHT");
            if(left<0L||right<0L)return 0L;
            if(left>Long.MAX_VALUE-right){
                saturated=true;
                return Long.MAX_VALUE;
            }
            return left+right;
        }

        void equal(long actual,long expected,String label){
            nonnegative(actual,label+"_ACTUAL");
            nonnegative(expected,label+"_EXPECTED");
            if(actual==Long.MAX_VALUE||expected==Long.MAX_VALUE){
                // Counter saturation loses exact cardinality evidence.
                saturated=true;
                return;
            }
            if(actual!=expected)issue("DRIFT_"+label);
        }

        Result finish(long observed){
            Status status=!findings.isEmpty()
                ?Status.DRIFT
                :(saturated?Status.INDETERMINATE_SATURATED:Status.CONSISTENT);
            return new Result(status,observed,findings);
        }
    }

    static Result inspect(LocalLabSequentialTerminalStageTrace.Snapshot source){
        LocalLabSequentialTerminalStageTrace.Snapshot s=
            Objects.requireNonNull(source,"source");
        return inspectComponents(
            s,LocalLabTerminalDeliverySummary.from(s),
            s.originConsumerOutcomes
        );
    }

    /**
     * Diagnostic-only component comparison, useful for detecting accidental
     * mixed-time snapshots. Production callers should prefer inspect(source)
     * so all projections derive from the SAME detached observation.
     */
    static Result inspectComponents(
        LocalLabSequentialTerminalStageTrace.Snapshot source,
        LocalLabTerminalDeliverySummary summary,
        LocalLabOriginConsumerOutcomeTally.Snapshot joint
    ){
        LocalLabSequentialTerminalStageTrace.Snapshot s=
            Objects.requireNonNull(source,"source");
        LocalLabTerminalDeliverySummary derived=
            Objects.requireNonNull(summary,"summary");
        LocalLabOriginConsumerOutcomeTally.Snapshot matrix=
            Objects.requireNonNull(joint,"joint");
        Checker c=new Checker();
        long observed=s.observed;

        c.equal(derived.observedTerminals,observed,"SUMMARY_OBSERVED");
        c.equal(derived.evictedDetailReports,s.evicted,"SUMMARY_EVICTED");
        c.equal(derived.retainedDetailReports,s.recent.size(),
            "SUMMARY_RETAINED");
        c.equal(c.add(s.evicted,s.recent.size(),"DETAIL_RING_SUM"),observed,
            "DETAIL_RING_OBSERVED");

        long boundaryTotal=0L;
        for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                LocalLabTerminalFailureBoundaryTally.Boundary.values()){
            long count=s.failureBoundaries.count(boundary);
            c.equal(derived.firstBoundaryCount(boundary),count,
                "SUMMARY_FIRST_"+boundary);
            boundaryTotal=c.add(boundaryTotal,count,"BOUNDARY_TOTAL");
        }
        c.equal(boundaryTotal,observed,"FIRST_BOUNDARIES_OBSERVED");

        for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                LocalLabTerminalDeliveryAssessment.Consumer.values()){
            long totalConsumer=0L;
            for(LocalLabTerminalDeliveryAssessment.Evidence evidence:
                    LocalLabTerminalDeliveryAssessment.Evidence.values()){
                long originSum=0L;
                for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                        LocalLabSettledDeathEnvelope.OriginStatus.values()){
                    originSum=c.add(originSum,
                        matrix.count(origin,consumer,evidence),
                        "JOINT_CONSUMER_"+consumer+"_"+evidence);
                }
                long marginal=derived.count(consumer,evidence);
                c.equal(originSum,marginal,
                    "JOINT_VS_CONSUMER_"+consumer+"_"+evidence);
                totalConsumer=c.add(totalConsumer,marginal,
                    "CONSUMER_TOTAL_"+consumer);
            }
            c.equal(totalConsumer,observed,
                "CONSUMER_VS_OBSERVED_"+consumer);
            LocalLabTerminalFailureBoundaryTally.Boundary boundary=
                LocalLabTerminalFailureBoundaryTally.Boundary.valueOf(
                    consumer.name());
            c.equal(
                derived.firstBoundaryCount(boundary),
                derived.count(consumer,
                    LocalLabTerminalDeliveryAssessment.Evidence
                        .THREW_MUTATION_UNCERTAIN),
                "FIRST_THROW_"+consumer
            );
        }

        long totalOrigin=0L;
        for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                LocalLabSettledDeathEnvelope.OriginStatus.values()){
            long originCount=s.originCount(origin);
            c.equal(matrix.originCount(origin),originCount,
                "JOINT_ORIGIN_"+origin);
            c.equal(derived.originCount(origin),originCount,
                "SUMMARY_ORIGIN_"+origin);
            totalOrigin=c.add(totalOrigin,originCount,
                "ORIGINS_TOTAL");
            for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                    LocalLabTerminalDeliveryAssessment.Consumer.values()){
                long row=0L;
                for(LocalLabTerminalDeliveryAssessment.Evidence evidence:
                        LocalLabTerminalDeliveryAssessment.Evidence.values())
                    row=c.add(row,matrix.count(origin,consumer,evidence),
                        "JOINT_ROW_"+origin+"_"+consumer);
                c.equal(row,originCount,
                    "ORIGIN_CONSUMER_ROW_"+origin+"_"+consumer);
            }
        }
        c.equal(totalOrigin,observed,"ORIGINS_OBSERVED");
        c.equal(
            derived.firstBoundaryCount(
                LocalLabTerminalFailureBoundaryTally.Boundary
                    .NO_CURRENT_RECIPIENT),
            derived.count(
                LocalLabTerminalDeliveryAssessment.Consumer.G4,
                LocalLabTerminalDeliveryAssessment.Evidence
                    .SKIPPED_NO_CURRENT_RECIPIENT),
            "FIRST_NO_CURRENT_VS_G4_SKIP"
        );
        return c.finish(observed);
    }

    private LocalLabTerminalEvidenceConsistencyAudit(){}
}
