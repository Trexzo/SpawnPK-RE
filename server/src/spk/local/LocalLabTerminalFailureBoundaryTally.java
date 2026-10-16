package spk.local;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * World-process-local, bounded-memory distribution of the FIRST observed
 * point that blocked DEFAULT sequential post-settlement delivery.
 *
 * Pure diagnostic evidence. A callback returning is not a grant ACK, a
 * throwing callback may have already mutated account/objective state,
 * and even a complete successful chain cannot authorize replay.
 *
 * This tally preserves aggregate first-boundary counts after the 128
 * detailed terminal reports are evicted; it is NOT a durable outbox.
 */
final class LocalLabTerminalFailureBoundaryTally {
    enum Boundary {
        NO_THROW_RECORDED,
        NO_CURRENT_RECIPIENT,
        G1, G4, G14, G15, G16, G18
    }

    private final EnumMap<Boundary,Long> totals=
        new EnumMap<Boundary,Long>(Boundary.class);

    LocalLabTerminalFailureBoundaryTally(){
        for(Boundary boundary:Boundary.values())totals.put(boundary,0L);
    }

    /**
     * Validates mutually exclusive fail-fast outcomes BEFORE any collector
     * counters are changed. Only the earliest throwing consumer is evidence
     * of the failure boundary; all derived consumers must be NOT_REACHED
     * after a G1 or later-stage exception.
     */
    static Boundary classify(LocalLabSequentialTerminalStageTrace.Report report){
        LocalLabSequentialTerminalStageTrace.Report r=
            Objects.requireNonNull(report,"report");
        boolean afterFailure=
            r.g1Outcome==LocalLabSequentialTerminalStageTrace.G1Outcome.THREW;
        Boundary firstFailure=afterFailure?Boundary.G1:null;
        int skipped=0;
        // A sequential observer cannot reach a later stage after an
        // earlier NOT_REACHED stage. A partial diagnostic prefix may
        // legitimately end in NOT_REACHED, but cannot then resume.
        // Validate before any World-local count or report is recorded.
        boolean stoppedBeforeStage=false;
        for(LocalLabSequentialTerminalStageTrace.Stage stage:
                LocalLabSequentialTerminalStageTrace.Stage.values()){
            LocalLabSequentialTerminalStageTrace.Outcome outcome=
                Objects.requireNonNull(r.outcome(stage),"stageOutcome");
            if(!afterFailure){
                if(outcome==LocalLabSequentialTerminalStageTrace.Outcome.NOT_REACHED)
                    stoppedBeforeStage=true;
                else if(stoppedBeforeStage)
                    throw new IllegalStateException(
                        "TERMINAL_FAILURE_BOUNDARY_REACHED_AFTER_GAP_"+stage);
            }
            if(afterFailure){
                if(outcome!=LocalLabSequentialTerminalStageTrace.Outcome.NOT_REACHED)
                    throw new IllegalStateException(
                        "TERMINAL_FAILURE_BOUNDARY_POST_THROW_STAGE_"+stage);
            }else if(outcome==
                LocalLabSequentialTerminalStageTrace.Outcome.THREW){
                afterFailure=true;
                firstFailure=Boundary.valueOf(stage.name());
            }else if(outcome==
                LocalLabSequentialTerminalStageTrace.Outcome.SKIPPED_NO_CURRENT_RECIPIENT){
                skipped++;
            }
        }
        if(skipped!=0&&skipped!=
                LocalLabSequentialTerminalStageTrace.Stage.values().length)
            throw new IllegalStateException(
                "TERMINAL_FAILURE_BOUNDARY_PARTIAL_RECIPIENT_SKIP");
        if(skipped!=0&&firstFailure!=null)
            throw new IllegalStateException(
                "TERMINAL_FAILURE_BOUNDARY_SKIP_AND_THROW");
        if(firstFailure!=null)return firstFailure;
        if(skipped!=0)return Boundary.NO_CURRENT_RECIPIENT;
        return Boundary.NO_THROW_RECORDED;
    }

    void acceptValidated(Boundary boundary){
        Boundary checked=Objects.requireNonNull(boundary,"boundary");
        long old=totals.get(checked);
        if(old<Long.MAX_VALUE)totals.put(checked,old+1L);
    }

    Snapshot snapshot(){return new Snapshot(totals);}

    static final class Snapshot {
        final Map<Boundary,Long> boundaryCounts;

        private Snapshot(EnumMap<Boundary,Long> source){
            boundaryCounts=Collections.unmodifiableMap(
                new EnumMap<Boundary,Long>(source)
            );
        }

        long count(Boundary boundary){
            return boundaryCounts.get(
                Objects.requireNonNull(boundary,"boundary")
            );
        }

        boolean hasDurableGrantAcknowledgement(){return false;}
        boolean safeToAutomaticallyReplay(){return false;}
    }
}
