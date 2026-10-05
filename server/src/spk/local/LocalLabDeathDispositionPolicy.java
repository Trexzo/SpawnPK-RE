package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Explicit LocalLab MVP player-death item policy.
 *
 * This is not recovered SpawnPK server economics. It exists to make the local
 * gameplay loop complete while value/risk/skull/Protect Item authority remains
 * unknown:
 * - exact explicit AUTO_KEEP rows remain kept;
 * - exact explicit AUTO_LOSS rows are lost;
 * - STANDARD_UNRESOLVED rows are lost by LocalLab policy.
 *
 * A later gameplay train may replace STANDARD_UNRESOLVED with a configured
 * risk/value policy without changing the settlement transaction.
 */
final class LocalLabDeathDispositionPolicy {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_DEATH_DISPOSITION_MVP_V1";
    static final String STANDARD_POLICY=
        "STANDARD_UNRESOLVED_LOSE";

    static final class Plan {
        final List<PlayerDeathItemResolutionService.Decision>
            decisions;
        final int autoKeptLines;
        final int explicitLostLines;
        final int standardLostLines;

        private Plan(
            List<PlayerDeathItemResolutionService.Decision>
                decisions,
            int autoKeptLines,
            int explicitLostLines,
            int standardLostLines
        ){
            this.decisions=
                Collections.unmodifiableList(
                    new ArrayList<>(decisions)
                );
            this.autoKeptLines=autoKeptLines;
            this.explicitLostLines=explicitLostLines;
            this.standardLostLines=standardLostLines;
        }
    }

    Plan plan(
        PlayerDeathItemResolutionService.DeathPreview preview
    ){
        PlayerDeathItemResolutionService.DeathPreview checked=
            Objects.requireNonNull(
                preview,
                "preview"
            );

        ArrayList<PlayerDeathItemResolutionService.Decision>
            decisions=
                new ArrayList<>(
                    checked.carried.size()
                );

        int autoKept=0;
        int explicitLost=0;
        int standardLost=0;

        for(PlayerDeathItemResolutionService.CarriedLine line:
                checked.carried){
            DeathPolicyRepository.Policy policy=
                DeathPolicyRepository.get(
                    line.itemId
                );

            int kept;
            switch(policy.kind){
                case AUTO_KEEP_EXPLICIT:
                    kept=line.quantity;
                    autoKept++;
                    break;
                case AUTO_LOSS_EXPLICIT:
                    kept=0;
                    explicitLost++;
                    break;
                case STANDARD_UNRESOLVED:
                default:
                    kept=0;
                    standardLost++;
                    break;
            }

            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    kept
                )
            );
        }

        return new Plan(
            decisions,
            autoKept,
            explicitLost,
            standardLost
        );
    }
}
