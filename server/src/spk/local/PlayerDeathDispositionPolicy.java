package spk.local;

import java.util.Collection;

/**
 * Caller-owned gameplay policy for one exact player-death carried-item preview.
 *
 * Implementations decide only kept amounts. They do not mutate player state,
 * publish packets, create ground items, or claim recovered original-server
 * economics.
 */
interface PlayerDeathDispositionPolicy {
    String authority();

    Collection<PlayerDeathItemResolutionService.Decision> decide(
        PlayerDeathItemResolutionService.DeathPreview preview
    );
}
