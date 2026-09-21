package spk.local;

/**
 * Semantic integration port for #154 objective/progression state.
 *
 * Objective state is explicitly scoped by the owning hunter. Bounty Hunter
 * references objective identity and completion only; it does not own or
 * duplicate objective progress.
 */
interface BountyObjectivePort {
    boolean exists(
        BountyHunterService.PlayerId hunter,
        BountyHunterService.ObjectiveReference objective
    );

    boolean isComplete(
        BountyHunterService.PlayerId hunter,
        BountyHunterService.ObjectiveReference objective
    );
}
