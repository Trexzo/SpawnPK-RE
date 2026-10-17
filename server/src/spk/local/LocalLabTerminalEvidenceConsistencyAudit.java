package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

        void retained(long detail,long total,boolean exact,String label){
            nonnegative(total,"RETAINED_TOTAL_"+label);
            if(total==Long.MAX_VALUE){
                saturated=true;
                return;
            }
            if(detail>total||(exact&&detail!=total))
                issue("DETAIL_DRIFT_"+label);
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
        return inspectComponents(s,derived,matrix,s.originFailureBoundaries);
    }

    /**
     * Compare an externally supplied origin x first-boundary snapshot
     * against same-epoch marginals. This remains a pure negative audit
     * for mixed-epoch or corrupt diagnostic data, never a grant gate.
     */
    static Result inspectComponents(
        LocalLabSequentialTerminalStageTrace.Snapshot source,
        LocalLabTerminalDeliverySummary summary,
        LocalLabOriginConsumerOutcomeTally.Snapshot joint,
        LocalLabOriginFirstFailureBoundaryTally.Snapshot originBoundaries
    ){
        LocalLabSequentialTerminalStageTrace.Snapshot s=
            Objects.requireNonNull(source,"source");
        LocalLabTerminalDeliverySummary derived=
            Objects.requireNonNull(summary,"summary");
        LocalLabOriginConsumerOutcomeTally.Snapshot matrix=
            Objects.requireNonNull(joint,"joint");
        return inspectComponents(
            s,derived,matrix,crossedOrThrow(originBoundaries),
            s.originFirstFailureKinds
        );
    }

    private static LocalLabOriginFirstFailureBoundaryTally.Snapshot crossedOrThrow(
        LocalLabOriginFirstFailureBoundaryTally.Snapshot crossed
    ){
        return Objects.requireNonNull(crossed,"originBoundaries");
    }

    /** Read-only five-component consistency witness for mixed-epoch tests. */
    static Result inspectComponents(
        LocalLabSequentialTerminalStageTrace.Snapshot source,
        LocalLabTerminalDeliverySummary summary,
        LocalLabOriginConsumerOutcomeTally.Snapshot joint,
        LocalLabOriginFirstFailureBoundaryTally.Snapshot originBoundaries,
        LocalLabOriginFirstFailureKindTally.Snapshot originKinds
    ){
        LocalLabSequentialTerminalStageTrace.Snapshot s=
            Objects.requireNonNull(source,"source");
        LocalLabTerminalDeliverySummary derived=
            Objects.requireNonNull(summary,"summary");
        LocalLabOriginConsumerOutcomeTally.Snapshot matrix=
            Objects.requireNonNull(joint,"joint");
        LocalLabOriginFirstFailureBoundaryTally.Snapshot crossed=
            Objects.requireNonNull(originBoundaries,"originBoundaries");
        LocalLabOriginFirstFailureKindTally.Snapshot types=
            Objects.requireNonNull(originKinds,"originKinds");
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
        for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                LocalLabTerminalFailureBoundaryTally.Boundary.values()){
            long byKind=0L;
            for(LocalLabTerminalFirstFailureTypeTally.Kind kind:
                    LocalLabTerminalFirstFailureTypeTally.Kind.values()){
                long n=s.firstFailureTypes.count(boundary,kind);
                byKind=c.add(byKind,n,"FIRST_TYPE_TOTAL_"+boundary);
                boolean noThrow=boundary==
                    LocalLabTerminalFailureBoundaryTally.Boundary.NO_THROW_RECORDED||
                    boundary==LocalLabTerminalFailureBoundaryTally.Boundary
                        .NO_CURRENT_RECIPIENT;
                if(noThrow!=(kind==LocalLabTerminalFirstFailureTypeTally.Kind
                        .NO_EXCEPTION)&&n>0L)
                    c.issue("FIRST_TYPE_IMPOSSIBLE_"+boundary+"_"+kind);
            }
            c.equal(byKind,s.failureBoundaries.count(boundary),
                "FIRST_TYPE_VS_BOUNDARY_"+boundary);
        }
        for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                LocalLabTerminalFailureBoundaryTally.Boundary.values()){
            long sum=0L;
            for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                    LocalLabSettledDeathEnvelope.OriginStatus.values())
                sum=c.add(sum,crossed.count(origin,boundary),
                    "ORIGIN_FIRST_COLUMN_"+boundary);
            c.equal(sum,s.failureBoundaries.count(boundary),
                "ORIGIN_FIRST_VS_BOUNDARY_"+boundary);
            for(LocalLabTerminalFirstFailureTypeTally.Kind kind:
                    LocalLabTerminalFirstFailureTypeTally.Kind.values()){
                long typedOriginSum=0L;
                for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                        LocalLabSettledDeathEnvelope.OriginStatus.values()){
                    long value=types.count(origin,boundary,kind);
                    typedOriginSum=c.add(typedOriginSum,value,
                        "ORIGIN_FIRST_KIND_COLUMN_"+boundary+"_"+kind);
                    boolean noThrow=boundary==
                        LocalLabTerminalFailureBoundaryTally.Boundary
                            .NO_THROW_RECORDED||
                        boundary==LocalLabTerminalFailureBoundaryTally.Boundary
                            .NO_CURRENT_RECIPIENT;
                    if(noThrow!=(kind==LocalLabTerminalFirstFailureTypeTally
                            .Kind.NO_EXCEPTION)&&value>0L)
                        c.issue("ORIGIN_FIRST_KIND_IMPOSSIBLE_"+
                            origin+"_"+boundary+"_"+kind);
                }
                c.equal(typedOriginSum,
                    s.firstFailureTypes.count(boundary,kind),
                    "ORIGIN_FIRST_KIND_VS_TYPE_"+boundary+"_"+kind);
            }
        }
        for(LocalLabSettledDeathEnvelope.OriginStatus origin:
                LocalLabSettledDeathEnvelope.OriginStatus.values())
            for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                    LocalLabTerminalFailureBoundaryTally.Boundary.values()){
                long typedSum=0L;
                for(LocalLabTerminalFirstFailureTypeTally.Kind kind:
                        LocalLabTerminalFirstFailureTypeTally.Kind.values())
                    typedSum=c.add(typedSum,types.count(origin,boundary,kind),
                        "ORIGIN_FIRST_KIND_ROW_"+origin+"_"+boundary);
                c.equal(typedSum,crossed.count(origin,boundary),
                    "ORIGIN_FIRST_KIND_VS_ORIGIN_BOUNDARY_"+origin+"_"+boundary);
            }

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
            long firstForOrigin=0L;
            for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                    LocalLabTerminalFailureBoundaryTally.Boundary.values())
                firstForOrigin=c.add(firstForOrigin,
                    crossed.count(origin,boundary),"ORIGIN_FIRST_ROW_"+origin);
            c.equal(firstForOrigin,originCount,
                "ORIGIN_FIRST_VS_ORIGIN_"+origin);
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
        inspectRetainedDetails(s,derived,matrix,crossed,types,c);
        return c.finish(observed);
    }

    /**
     * Retained report evidence is at most a SUBSET after ring eviction.
     * Before the first eviction, it MUST reproduce every marginal/joint
     * outcome and first-blocker count exactly. No evicted per-death detail
     * or grant acknowledgement can be reconstructed from these totals.
     */
    private static void inspectRetainedDetails(
        LocalLabSequentialTerminalStageTrace.Snapshot source,
        LocalLabTerminalDeliverySummary summary,
        LocalLabOriginConsumerOutcomeTally.Snapshot matrix,
        LocalLabOriginFirstFailureBoundaryTally.Snapshot cross,
        LocalLabOriginFirstFailureKindTally.Snapshot typed,
        Checker c
    ){
        LocalLabSettledDeathEnvelope.OriginStatus[] origins=
            LocalLabSettledDeathEnvelope.OriginStatus.values();
        LocalLabTerminalDeliveryAssessment.Consumer[] consumers=
            LocalLabTerminalDeliveryAssessment.Consumer.values();
        LocalLabTerminalDeliveryAssessment.Evidence[] evidences=
            LocalLabTerminalDeliveryAssessment.Evidence.values();
        LocalLabTerminalFailureBoundaryTally.Boundary[] boundaries=
            LocalLabTerminalFailureBoundaryTally.Boundary.values();
        long[] originCount=new long[origins.length];
        long[] boundaryCount=new long[boundaries.length];
        long[][] originBoundaryCount=
            new long[origins.length][boundaries.length];
        long[][] kindCount=new long[boundaries.length][
            LocalLabTerminalFirstFailureTypeTally.Kind.values().length];
        long[][][] originKindCount=new long[origins.length][boundaries.length][
            LocalLabTerminalFirstFailureTypeTally.Kind.values().length];
        long[][] consumerCount=new long[consumers.length][evidences.length];
        long[][][] jointCount=
            new long[origins.length][consumers.length][evidences.length];
        Set<EntityId> seenNpcIds=new HashSet<EntityId>();

        for(LocalLabSequentialTerminalStageTrace.Report report:
                source.recent){
            if(report==null){
                c.issue("DETAIL_NULL_REPORT");
                continue;
            }
            if(report.npcId==null||report.deathTile==null||
               report.recipientRef==null||report.originStatus==null){
                c.issue("DETAIL_MISSING_DEATH_IDENTITY");
                continue;
            }
            if(!seenNpcIds.add(report.npcId))
                c.issue("DETAIL_DUPLICATE_NPC_ID");
            if(report.spawnOriginMatchedAtDispatch!=
                    (report.originStatus==
                        LocalLabSettledDeathEnvelope.OriginStatus
                            .CURRENT_SPAWN_RECIPIENT))
                c.issue("DETAIL_ORIGIN_MATCH_FLAG_CONTRADICTION");
            if(report.originStatus==
                    LocalLabSettledDeathEnvelope.OriginStatus.NO_CURRENT_RECIPIENT&&
               report.observedCurrentGeneration!=-1L)
                c.issue("DETAIL_DISCONNECTED_GENERATION_CONTRADICTION");
            if(report.originStatus==
                    LocalLabSettledDeathEnvelope.OriginStatus.MISSING_SPAWN_ORIGIN&&
               report.spawnRecipientGeneration!=-1L)
                c.issue("DETAIL_MISSING_ORIGIN_GENERATION_CONTRADICTION");

            boolean g1Threw=report.g1Outcome==
                LocalLabSequentialTerminalStageTrace.G1Outcome.THREW;
            if(g1Threw!=(report.g1FailureType!=null))
                c.issue("DETAIL_G1_FAILURE_TYPE_CONTRADICTION");
            for(LocalLabSequentialTerminalStageTrace.Stage stage:
                    LocalLabSequentialTerminalStageTrace.Stage.values()){
                if((report.outcome(stage)==
                        LocalLabSequentialTerminalStageTrace.Outcome.THREW)!=
                   (report.failureType(stage)!=null))
                    c.issue("DETAIL_STAGE_FAILURE_TYPE_"+stage);
            }

            try{
                LocalLabTerminalFailureBoundaryTally.Boundary boundary=
                    LocalLabTerminalFailureBoundaryTally.classify(report);
                LocalLabTerminalDeliveryAssessment assessment=
                    LocalLabTerminalDeliveryAssessment.from(report);
                originCount[report.originStatus.ordinal()]++;
                boundaryCount[boundary.ordinal()]++;
                LocalLabTerminalFirstFailureTypeTally.Kind kind=
                    LocalLabTerminalFirstFailureTypeTally.classify(
                        report,boundary
                    );
                kindCount[boundary.ordinal()][kind.ordinal()]++;
                originKindCount[report.originStatus.ordinal()]
                    [boundary.ordinal()][kind.ordinal()]++;
                originBoundaryCount[report.originStatus.ordinal()]
                    [boundary.ordinal()]++;
                for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                        consumers){
                    LocalLabTerminalDeliveryAssessment.Evidence evidence=
                        assessment.evidence(consumer);
                    if(evidence==null){
                        c.issue("DETAIL_MISSING_CONSUMER_"+consumer);
                        continue;
                    }
                    consumerCount[consumer.ordinal()][evidence.ordinal()]++;
                    jointCount[report.originStatus.ordinal()]
                        [consumer.ordinal()][evidence.ordinal()]++;
                }
            }catch(RuntimeException invalid){
                // Never let a corrupt diagnostic record authorize gameplay
                // mutation, and do not retain the exception object itself.
                c.issue("DETAIL_INVALID_STAGE_SEQUENCE");
            }
        }

        boolean exact=source.evicted==0L;
        for(LocalLabSettledDeathEnvelope.OriginStatus origin:origins)
            c.retained(originCount[origin.ordinal()],
                source.originCount(origin),exact,"ORIGIN_"+origin);
        for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                boundaries)
            c.retained(boundaryCount[boundary.ordinal()],
                source.failureBoundaries.count(boundary),exact,
                "FIRST_"+boundary);
        for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:boundaries)
            for(LocalLabTerminalFirstFailureTypeTally.Kind kind:
                    LocalLabTerminalFirstFailureTypeTally.Kind.values())
                c.retained(kindCount[boundary.ordinal()][kind.ordinal()],
                    source.firstFailureTypes.count(boundary,kind),exact,
                    "FIRST_TYPE_"+boundary+"_"+kind);
        for(LocalLabSettledDeathEnvelope.OriginStatus origin:origins)
            for(LocalLabTerminalFailureBoundaryTally.Boundary boundary:
                    boundaries){
                c.retained(
                    originBoundaryCount[origin.ordinal()][boundary.ordinal()],
                    cross.count(origin,boundary),exact,
                    "ORIGIN_FIRST_"+origin+"_"+boundary);
                for(LocalLabTerminalFirstFailureTypeTally.Kind kind:
                        LocalLabTerminalFirstFailureTypeTally.Kind.values())
                    c.retained(
                        originKindCount[origin.ordinal()][boundary.ordinal()]
                            [kind.ordinal()],
                        typed.count(origin,boundary,kind),exact,
                        "ORIGIN_FIRST_KIND_"+origin+"_"+boundary+"_"+kind);
            }
        for(LocalLabTerminalDeliveryAssessment.Consumer consumer:consumers)
            for(LocalLabTerminalDeliveryAssessment.Evidence evidence:evidences){
                c.retained(consumerCount[consumer.ordinal()][evidence.ordinal()],
                    summary.count(consumer,evidence),exact,
                    "CONSUMER_"+consumer+"_"+evidence);
                for(LocalLabSettledDeathEnvelope.OriginStatus origin:origins)
                    c.retained(jointCount[origin.ordinal()]
                            [consumer.ordinal()][evidence.ordinal()],
                        matrix.count(origin,consumer,evidence),exact,
                        "JOINT_"+origin+"_"+consumer+"_"+evidence);
            }
    }

    private LocalLabTerminalEvidenceConsistencyAudit(){}
}
