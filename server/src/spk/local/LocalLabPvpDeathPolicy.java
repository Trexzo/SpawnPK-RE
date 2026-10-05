package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Explicit LocalLab PvP death policy.
 *
 * Recovered item data still leaves ordinary risk/value ordering unresolved.
 * This policy therefore makes one deliberate server choice: standard
 * unresolved carried items are lost. It is not a claim about original
 * SpawnPK behavior.
 */
final class LocalLabPvpDeathPolicy {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB";
    static final String POLICY_ID=
        "DROP_STANDARD_V1";

    static final class Result {
        final List<PlayerDeathItemResolutionService.Decision>
            decisions;
        final int autoKeptLines;
        final int autoLostLines;
        final int standardLostLines;

        Result(
            List<PlayerDeathItemResolutionService.Decision>
                decisions,
            int autoKeptLines,
            int autoLostLines,
            int standardLostLines
        ){
            this.decisions=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        Objects.requireNonNull(
                            decisions,
                            "decisions"
                        )
                    )
                );
            this.autoKeptLines=autoKeptLines;
            this.autoLostLines=autoLostLines;
            this.standardLostLines=
                standardLostLines;
        }
    }

    Result decide(
        PlayerDeathItemResolutionService.DeathPreview
            preview
    ){
        PlayerDeathItemResolutionService.DeathPreview
            checked=
                Objects.requireNonNull(
                    preview,
                    "preview"
                );

        ArrayList<PlayerDeathItemResolutionService.Decision>
            decisions=
                new ArrayList<>();
        int autoKept=0;
        int autoLost=0;
        int standardLost=0;

        for(PlayerDeathItemResolutionService.CarriedLine
                line:
                checked.carried){
            DeathPolicyRepository.Policy policy=
                DeathPolicyRepository.get(
                    line.itemId
                );

            int keepAmount;

            switch(policy.kind){
                case AUTO_KEEP_EXPLICIT:
                    keepAmount=line.quantity;
                    autoKept++;
                    break;
                case AUTO_LOSS_EXPLICIT:
                    keepAmount=0;
                    autoLost++;
                    break;
                case STANDARD_UNRESOLVED:
                default:
                    keepAmount=0;
                    standardLost++;
                    break;
            }

            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    keepAmount
                )
            );
        }

        return new Result(
            decisions,
            autoKept,
            autoLost,
            standardLost
        );
    }
}
