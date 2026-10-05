package spk.local;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runtime composition for one LocalLab player-death carried-item decision.
 *
 * This deliberately does not claim recovered SpawnPK death economics. Until a
 * separately owned wilderness/risk policy exists, LocalLab keeps every carried
 * inventory/equipment quantity and merely makes that decision explicit before
 * respawn can settle.
 */
final class LocalDeathLoopService {
    static final String POLICY_AUTHORITY=
        "CUSTOM_LOCALLAB_KEEP_ALL_DEATH_ITEMS";

    private final PlayerDeathItemResolutionService resolutions;

    LocalDeathLoopService(
        WorldPlayer player
    ){
        this.resolutions=
            new PlayerDeathItemResolutionService(
                Objects.requireNonNull(
                    player,
                    "player"
                ),
                POLICY_AUTHORITY
            );
    }

    PlayerDeathItemResolutionService.Resolution
        ensureCurrentDeathResolved()
    {
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolutions.previewCurrentDeath();

        PlayerDeathItemResolutionService.Resolution existing=
            resolutions.get(
                preview.deathSequence
            );

        if(existing!=null){
            requireResolutionMatches(
                existing,
                preview.deathSequence,
                preview.deathTick
            );
            return existing;
        }

        List<PlayerDeathItemResolutionService.Decision> decisions=
            new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried)
            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    line.quantity
                )
            );

        PlayerDeathItemResolutionService.Resolution resolved=
            resolutions.resolveCurrentDeath(
                preview,
                decisions
            );

        requireResolutionMatches(
            resolved,
            preview.deathSequence,
            preview.deathTick
        );

        return resolved;
    }

    void requireResolvedForRespawn(
        PlayerLifecycleService.PreparedRespawn prepared
    ){
        Objects.requireNonNull(
            prepared,
            "prepared"
        );

        PlayerDeathItemResolutionService.Resolution resolution=
            resolutions.get(
                prepared.deathSequence
            );

        if(resolution==null)
            throw new IllegalStateException(
                "respawn has no carried-item death resolution deathSequence="+
                prepared.deathSequence
            );

        requireResolutionMatches(
            resolution,
            prepared.deathSequence,
            prepared.deathTick
        );
    }

    int resolutionCount(){
        return resolutions.size();
    }

    PlayerDeathItemResolutionService.Resolution resolution(
        long deathSequence
    ){
        return resolutions.get(
            deathSequence
        );
    }

    private static void requireResolutionMatches(
        PlayerDeathItemResolutionService.Resolution resolution,
        long deathSequence,
        long deathTick
    ){
        if(resolution.deathSequence!=deathSequence||
           resolution.deathTick!=deathTick||
           !POLICY_AUTHORITY.equals(
                resolution.policyAuthority))
            throw new IllegalStateException(
                "death resolution identity/policy mismatch sequence="+
                deathSequence
            );

        for(PlayerDeathItemResolutionService.Disposition disposition:
                resolution.dispositions)
            if(disposition.keptAmount!=
                    disposition.line.quantity||
               disposition.lostAmount!=0)
                throw new IllegalStateException(
                    "LocalLab keep-all death policy drift lineId="+
                    disposition.line.lineId+
                    " kept="+
                    disposition.keptAmount+
                    " quantity="+
                    disposition.line.quantity+
                    " lost="+
                    disposition.lostAmount
                );
    }
}
