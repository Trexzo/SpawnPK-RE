package spk.local;

import java.util.List;
import java.util.Optional;

/**
 * External target-selection policy hook.
 *
 * SpawnPK production matching rules are not known from client evidence and are
 * intentionally not embedded in the Bounty Hunter service.
 */
interface BountyTargetSelectionPolicy {
    Optional<BountyHunterService.PlayerId> select(
        BountyHunterService.PlayerId hunter,
        List<BountyHunterService.PlayerId> candidates
    );
}
