package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * First playable LocalLab PvP risk rule.
 *
 * This is not claimed as original SpawnPK behavior. Explicit auto-keep items
 * remain protected; every other carried line is risked in full. Skull,
 * protect-item and value-ranked keep rules are intentionally deferred.
 */
final class LocalLabPvpDeathItemPolicy
    implements PlayerDeathItemDecisionPolicy {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_PVP_RISK_ALL_STANDARD_V1";

    @Override public List<
        PlayerDeathItemResolutionService.Decision
    > decide(
        PlayerDeathItemResolutionService.DeathPreview preview
    ){
        PlayerDeathItemResolutionService.DeathPreview
            checked=
                Objects.requireNonNull(
                    preview,
                    "preview"
                );

        ArrayList<PlayerDeathItemResolutionService.Decision>
            out=
                new ArrayList<>(
                    checked.carried.size()
                );

        for(PlayerDeathItemResolutionService.CarriedLine line:
                checked.carried){
            DeathPolicyRepository.Policy policy=
                DeathPolicyRepository.get(
                    line.itemId
                );

            int kept=
                policy.kind==
                    DeathPolicyRepository.Kind
                        .AUTO_KEEP_EXPLICIT
                    ?line.quantity
                    :0;

            out.add(
                new PlayerDeathItemResolutionService
                    .Decision(
                        line.lineId,
                        kept
                    )
            );
        }

        return Collections.unmodifiableList(
            out
        );
    }

    @Override public String authority(){
        return AUTHORITY;
    }
}
