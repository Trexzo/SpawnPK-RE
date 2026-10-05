package spk.local;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Provisional Gameplay G1 PvP death policy.
 *
 * Authority is explicitly CUSTOM_LOCALLAB. This is not a claim about the
 * original SpawnPK server economy. G1 intentionally uses a simple drop-all
 * rule so the death -> loot -> respawn -> regear loop is playable before
 * permanent economy balancing is chosen.
 */
final class GameplayG1PvpDeathPolicy
    implements PlayerDeathDispositionPolicy,
               PlayerDeathGroundDropPolicy {

    static final GameplayG1PvpDeathPolicy INSTANCE=
        new GameplayG1PvpDeathPolicy();

    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G1_PVP_DROP_ALL_KILLER_PRIVATE";

    @Override
    public String authority(){
        return AUTHORITY;
    }

    @Override
    public Collection<PlayerDeathItemResolutionService.Decision>
        decide(
            PlayerDeathItemResolutionService.DeathPreview preview
        )
    {
        ArrayList<PlayerDeathItemResolutionService.Decision> decisions=
            new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried)
            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    0
                )
            );

        return decisions;
    }

    @Override
    public String ownerRef(
        PlayerDeathAttributionRegistry.Attribution attribution,
        PlayerDeathCarriedSettlementService.Receipt carried
    ){
        if(attribution==null)
            throw new NullPointerException("attribution");

        if(carried==null)
            throw new NullPointerException("carried");

        if(attribution.deathSequence!=carried.deathSequence||
           attribution.deathTick!=carried.deathTick)
            throw new IllegalStateException(
                "death policy attribution mismatch"
            );

        return attribution.attackerRef;
    }

    @Override
    public boolean devOwned(){
        return false;
    }

    private GameplayG1PvpDeathPolicy(){}
}
