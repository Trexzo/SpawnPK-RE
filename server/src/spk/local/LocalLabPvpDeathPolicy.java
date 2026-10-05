package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Explicit LocalLab PvP death policy used only to make the reconstructed
 * runtime playable before recovered server-side item-value/skull/Protect Item
 * authority exists.
 *
 * Exact auto-keep classification is respected. Everything else is lost in v1.
 */
final class LocalLabPvpDeathPolicy {
    static final String AUTHORITY =
        "CUSTOM_LOCALLAB_PVP_DEATH_V1";

    List<PlayerDeathItemResolutionService.Decision> decide(
        PlayerDeathItemResolutionService.DeathPreview preview
    ){
        PlayerDeathItemResolutionService.DeathPreview checked =
            Objects.requireNonNull(
                preview,
                "preview"
            );

        ArrayList<PlayerDeathItemResolutionService.Decision> out =
            new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                checked.carried){
            DeathPolicyRepository.Policy policy =
                DeathPolicyRepository.get(
                    line.itemId
                );

            int keep =
                policy.kind ==
                    DeathPolicyRepository.Kind.AUTO_KEEP_EXPLICIT
                    ? line.quantity
                    : 0;

            out.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    keep
                )
            );
        }

        return Collections.unmodifiableList(
            out
        );
    }
}
