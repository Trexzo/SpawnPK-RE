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
    static final String RISK_AUTHORITY=
        "LOCAL_LAB_POLICY_PK_REGION_RISK_V1";
    static final String STANDARD_POLICY_SAFE=
        "STANDARD_UNRESOLVED_KEEP";
    static final String STANDARD_POLICY_PK=
        "STANDARD_UNRESOLVED_LOSE";

    enum RiskClass {
        SAFE_OR_UNCLASSIFIED,
        PK_RISK
    }

    static final class Plan {
        final List<PlayerDeathItemResolutionService.Decision>
            decisions;
        final int autoKeptLines;
        final int explicitLostLines;
        final int standardKeptLines;
        final int standardLostLines;
        final RiskClass riskClass;
        final int deathRegionId;
        final String standardPolicy;
        final String riskAuthority;

        private Plan(
            List<PlayerDeathItemResolutionService.Decision>
                decisions,
            int autoKeptLines,
            int explicitLostLines,
            int standardKeptLines,
            int standardLostLines,
            RiskClass riskClass,
            int deathRegionId,
            String standardPolicy
        ){
            this.decisions=
                Collections.unmodifiableList(
                    new ArrayList<>(decisions)
                );
            this.autoKeptLines=autoKeptLines;
            this.explicitLostLines=explicitLostLines;
            this.standardKeptLines=standardKeptLines;
            this.standardLostLines=standardLostLines;
            this.riskClass=Objects.requireNonNull(
                riskClass,
                "riskClass"
            );
            this.deathRegionId=deathRegionId;
            this.standardPolicy=Objects.requireNonNull(
                standardPolicy,
                "standardPolicy"
            );
            this.riskAuthority=RISK_AUTHORITY;
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

        WorldRegionAuthorityRepository.Region deathRegion=
            WorldRegionAuthorityRepository.forTile(
                checked.deathTile.x,
                checked.deathTile.y
            );

        int deathRegionId=
            deathRegion==null
                ?-1
                :deathRegion.regionId;

        LocalTeleportDestinationCatalog.Destination pk=
            LocalTeleportDestinationCatalog.get(
                TeleportNavigationService.EntryKind.PK
            );

        if(pk==null)
            throw new IllegalStateException(
                "LocalLab PK risk destination is not configured"
            );

        RiskClass riskClass=
            deathRegionId==pk.regionId
                ?RiskClass.PK_RISK
                :RiskClass.SAFE_OR_UNCLASSIFIED;

        String standardPolicy=
            riskClass==RiskClass.PK_RISK
                ?STANDARD_POLICY_PK
                :STANDARD_POLICY_SAFE;

        ArrayList<PlayerDeathItemResolutionService.Decision>
            decisions=
                new ArrayList<>(
                    checked.carried.size()
                );

        int autoKept=0;
        int explicitLost=0;
        int standardKept=0;
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
                    if(riskClass==
                            RiskClass.PK_RISK){
                        kept=0;
                        standardLost++;
                    }else{
                        kept=line.quantity;
                        standardKept++;
                    }
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
            standardKept,
            standardLost,
            riskClass,
            deathRegionId,
            standardPolicy
        );
    }
}
