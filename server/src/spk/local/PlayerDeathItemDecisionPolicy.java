package spk.local;

import java.util.List;

/**
 * Server-owned carried-item keep/loss policy for one already-canonical death
 * preview. Implementations must return exactly one decision per carried line.
 */
interface PlayerDeathItemDecisionPolicy {
    List<PlayerDeathItemResolutionService.Decision> decide(
        PlayerDeathItemResolutionService.DeathPreview preview
    );

    String authority();
}
