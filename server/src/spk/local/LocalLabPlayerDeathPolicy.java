package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Conservative first live LocalLab player-death policy.
 *
 * Only explicit repository classifications are acted upon:
 * AUTO_KEEP_EXPLICIT => keep all
 * AUTO_LOSS_EXPLICIT => lose all
 * STANDARD_UNRESOLVED => keep all
 *
 * The last rule is deliberately conservative and must not be read as recovered
 * SpawnPK keep-count/value/risk behavior.
 */
final class LocalLabPlayerDeathPolicy {
    static final String AUTHORITY =
        "CUSTOM_LOCALLAB_CONSERVATIVE_DEATH_POLICY_V1";
    static final String RECIPIENT_POLICY =
        "VICTIM_OWNED_RECLAIM";
    static final String PVP_KILLER_RECIPIENT_POLICY =
        "ATTRIBUTED_PVP_KILLER";

    static final class Plan {
        final PlayerDeathItemResolutionService.DeathPreview preview;
        final List<PlayerDeathItemResolutionService.Decision> decisions;
        final Tile deathTile;
        final String recipientRef;
        final int explicitKeepLines;
        final int explicitLossLines;
        final int unresolvedKeptLines;
        final String authority;
        final String recipientPolicy;

        private Plan(
            PlayerDeathItemResolutionService.DeathPreview preview,
            List<PlayerDeathItemResolutionService.Decision> decisions,
            Tile deathTile,
            String recipientRef,
            int explicitKeepLines,
            int explicitLossLines,
            int unresolvedKeptLines,
            String recipientPolicy
        ){
            this.preview=preview;
            this.decisions=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        decisions
                    )
                );
            this.deathTile=deathTile;
            this.recipientRef=recipientRef;
            this.explicitKeepLines=explicitKeepLines;
            this.explicitLossLines=explicitLossLines;
            this.unresolvedKeptLines=unresolvedKeptLines;
            this.authority=AUTHORITY;
            this.recipientPolicy=
                requireRecipientPolicy(
                    recipientPolicy
                );
        }
    }

    Plan plan(
        WorldPlayer player,
        PlayerDeathItemResolutionService.DeathPreview preview
    ){
        return plan(
            player,
            preview,
            null
        );
    }

    Plan plan(
        WorldPlayer player,
        PlayerDeathItemResolutionService.DeathPreview preview,
        PlayerPvpDeathLedger pvpDeathLedger
    ){
        WorldPlayer checkedPlayer=
            Objects.requireNonNull(
                player,
                "player"
            );
        PlayerDeathItemResolutionService.DeathPreview checkedPreview=
            Objects.requireNonNull(
                preview,
                "preview"
            );

        if(!checkedPlayer.id().equals(
                checkedPreview.playerId))
            throw new IllegalArgumentException(
                "death preview belongs to another player expected="+
                checkedPlayer.id()+
                " actual="+
                checkedPreview.playerId
            );

        if(!checkedPlayer.lifecycle().dead()||
           checkedPlayer.lifecycle().deathTick()!=
                checkedPreview.deathTick||
           checkedPlayer.lifecycle().deathSequence()!=
                checkedPreview.deathSequence)
            throw new IllegalStateException(
                "death preview is not current player death id="+
                checkedPlayer.id()
            );

        ArrayList<PlayerDeathItemResolutionService.Decision>
            decisions=
                new ArrayList<>(
                    checkedPreview.carried.size()
                );

        int explicitKeep=0;
        int explicitLoss=0;
        int unresolvedKeep=0;

        for(PlayerDeathItemResolutionService.CarriedLine line:
                checkedPreview.carried){
            PlayerDeathItemResolutionService.CarriedLine checkedLine=
                Objects.requireNonNull(
                    line,
                    "carried line"
                );

            DeathPolicyRepository.Policy policy=
                DeathPolicyRepository.get(
                    checkedLine.itemId
                );

            final int keepAmount;

            switch(policy.kind){
                case AUTO_KEEP_EXPLICIT:
                    keepAmount=
                        checkedLine.quantity;
                    explicitKeep++;
                    break;
                case AUTO_LOSS_EXPLICIT:
                    keepAmount=0;
                    explicitLoss++;
                    break;
                case STANDARD_UNRESOLVED:
                default:
                    keepAmount=
                        checkedLine.quantity;
                    unresolvedKeep++;
                    break;
            }

            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    checkedLine.lineId,
                    keepAmount
                )
            );
        }

        MovementState movement=
            checkedPlayer.movement();

        Tile deathTile=
            new Tile(
                movement.x(),
                movement.y(),
                movement.plane()
            );

        String victimRef=
            checkedPlayer.username();

        if(victimRef==null||
           victimRef.trim().isEmpty())
            throw new IllegalStateException(
                "live death policy requires registered victim username id="+
                checkedPlayer.id()
            );

        String recipientRef=victimRef;
        String recipientPolicy=RECIPIENT_POLICY;

        if(pvpDeathLedger!=null){
            PlayerPvpDeathLedger.Entry attribution=
                pvpDeathLedger.get(
                    checkedPlayer.id(),
                    checkedPreview.deathSequence
                );

            if(attribution!=null){
                recipientRef=
                    attribution.attackerUsername;
                recipientPolicy=
                    PVP_KILLER_RECIPIENT_POLICY;
            }
        }

        return new Plan(
            checkedPreview,
            decisions,
            deathTile,
            recipientRef,
            explicitKeep,
            explicitLoss,
            unresolvedKeep,
            recipientPolicy
        );
    }

    private static String requireRecipientPolicy(
        String value
    ){
        if(!RECIPIENT_POLICY.equals(value)&&
           !PVP_KILLER_RECIPIENT_POLICY.equals(value))
            throw new IllegalArgumentException(
                "recipientPolicy="+
                value
            );

        return value;
    }
}
