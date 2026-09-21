package spk.local;

import java.util.Objects;

/**
 * Read-only Bounty Hunter view over the canonical semantic objective ledger.
 *
 * Bounty Hunter may observe objective existence/completion through this port,
 * but it does not own or mutate objective progress.
 */
final class ObjectiveProgressBountyPort
    implements BountyObjectivePort {

    private final ObjectiveProgressService objectives;

    ObjectiveProgressBountyPort(
        ObjectiveProgressService objectives
    ){
        this.objectives=
            Objects.requireNonNull(
                objectives,
                "objectives"
            );
    }

    @Override public boolean exists(
        BountyHunterService.ObjectiveReference objective
    ){
        Objects.requireNonNull(
            objective,
            "objective"
        );

        return objectives.get(
            objective.value()
        )!=null;
    }

    @Override public boolean isComplete(
        BountyHunterService.ObjectiveReference objective
    ){
        Objects.requireNonNull(
            objective,
            "objective"
        );

        ObjectiveProgressService.Snapshot snapshot=
            objectives.get(
                objective.value()
            );

        return snapshot!=null&&
            snapshot.complete;
    }
}
