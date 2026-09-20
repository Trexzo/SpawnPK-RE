package spk.local;

/**
 * Semantic integration port for #154 objective/progression state.
 *
 * Bounty Hunter references objective identity and completion only; it does not
 * own or duplicate objective progress.
 */
interface BountyObjectivePort {
    boolean exists(BountyHunterService.ObjectiveReference objective);
    boolean isComplete(BountyHunterService.ObjectiveReference objective);
}
