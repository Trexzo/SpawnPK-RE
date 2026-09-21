package spk.local;

import java.util.Objects;

/**
 * Read-only, hunter-scoped Bounty Hunter view over semantic objective ledgers.
 *
 * Bounty Hunter may observe objective existence/completion through this port,
 * but it does not own or mutate objective progress.
 */
final class ObjectiveProgressBountyPort
    implements BountyObjectivePort {

    @FunctionalInterface
    interface LedgerResolver {
        ObjectiveProgressService resolve(
            BountyHunterService.PlayerId hunter
        );
    }

    private final LedgerResolver resolver;

    ObjectiveProgressBountyPort(
        LedgerResolver resolver
    ){
        this.resolver=
            Objects.requireNonNull(
                resolver,
                "resolver"
            );
    }

    @Override public boolean exists(
        BountyHunterService.PlayerId hunter,
        BountyHunterService.ObjectiveReference objective
    ){
        ObjectiveProgressService objectives=
            resolve(hunter);

        Objects.requireNonNull(
            objective,
            "objective"
        );

        return objectives!=null&&
            objectives.get(
                objective.value()
            )!=null;
    }

    @Override public boolean isComplete(
        BountyHunterService.PlayerId hunter,
        BountyHunterService.ObjectiveReference objective
    ){
        ObjectiveProgressService objectives=
            resolve(hunter);

        Objects.requireNonNull(
            objective,
            "objective"
        );

        if(objectives==null)
            return false;

        ObjectiveProgressService.Snapshot snapshot=
            objectives.get(
                objective.value()
            );

        return snapshot!=null&&
            snapshot.complete;
    }

    private ObjectiveProgressService resolve(
        BountyHunterService.PlayerId hunter
    ){
        Objects.requireNonNull(
            hunter,
            "hunter"
        );

        return resolver.resolve(hunter);
    }
}
