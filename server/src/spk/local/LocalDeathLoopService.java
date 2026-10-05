package spk.local;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runtime composition for LocalLab player-death carried-item policy.
 *
 * This is explicit LocalLab gameplay authority. It does not claim recovered
 * original SpawnPK wilderness, keep-count, protect-item or killer economics.
 */
final class LocalDeathLoopService {
    static final String POLICY_AUTHORITY=
        "CUSTOM_LOCALLAB_DEATH_RISK_R1";

    private final World world;
    private final WorldPlayer player;
    private final PlayerDeathItemResolutionService resolutions;
    private final PlayerDeathGroundSettlementService groundSettlement;

    LocalDeathLoopService(
        WorldPlayer player
    ){
        this(
            null,
            player
        );
    }

    LocalDeathLoopService(
        World world,
        WorldPlayer player
    ){
        this.world=world;
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.resolutions=
            new PlayerDeathItemResolutionService(
                this.player,
                POLICY_AUTHORITY
            );
        this.groundSettlement=
            world==null
                ?null
                :new PlayerDeathGroundSettlementService(
                    world,
                    this.player
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
            settleDropResolutionIfRequired(
                existing
            );
            return existing;
        }

        LiveKiller killer=
            resolveLiveKiller(
                preview
            );
        boolean dropAll=
            preview.riskAtDeath&&
            killer!=null&&
            preview.deathTile!=null;

        List<PlayerDeathItemResolutionService.Decision> decisions=
            new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried)
            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    dropAll
                        ?0
                        :line.quantity
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

        if(dropAll)
            settleDropResolution(
                resolved,
                killer
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

        settleDropResolutionIfRequired(
            resolution
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

    PlayerDeathGroundSettlementService.Receipt settlement(
        long deathSequence
    ){
        return groundSettlement==null
            ?null
            :groundSettlement.get(
                deathSequence
            );
    }

    private void settleDropResolutionIfRequired(
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        if(resolution.lostTotalQuantity()==0)
            return;

        LiveKiller killer=
            resolveLiveKiller(
                resolution
            );

        if(killer==null)
            throw new IllegalStateException(
                "DROP_ALL death lost its live killer before settlement sequence="+
                resolution.deathSequence
            );

        settleDropResolution(
            resolution,
            killer
        );
    }

    private void settleDropResolution(
        PlayerDeathItemResolutionService.Resolution resolution,
        LiveKiller killer
    ){
        if(groundSettlement==null)
            throw new IllegalStateException(
                "DROP_ALL death requires World ground settlement"
            );
        if(resolution.deathTile==null)
            throw new IllegalStateException(
                "DROP_ALL death missing canonical death tile sequence="+
                resolution.deathSequence
            );

        if(!world.players().owns(
                killer.player,
                killer.generation))
            throw new IllegalStateException(
                "DROP_ALL killer ownership changed before settlement killer="+
                killer.player.id()
            );

        groundSettlement.settle(
            resolution,
            resolution.deathTile,
            killer.username
        );
    }

    private LiveKiller resolveLiveKiller(
        PlayerDeathItemResolutionService.DeathPreview preview
    ){
        if(world==null||
           !preview.riskAtDeath||
           preview.responsiblePlayerId==null)
            return null;

        return resolveLiveKiller(
            preview.responsiblePlayerId
        );
    }

    private LiveKiller resolveLiveKiller(
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        if(world==null||
           !resolution.riskAtDeath||
           resolution.responsiblePlayerId==null)
            return null;

        return resolveLiveKiller(
            resolution.responsiblePlayerId
        );
    }

    private LiveKiller resolveLiveKiller(
        EntityId killerId
    ){
        WorldPlayer killer=
            world.players().byId(
                killerId
            );

        if(killer==null||
           killer==player)
            return null;

        long generation=
            killer.generation();

        if(!world.players().owns(
                killer,
                generation))
            return null;

        String username=
            killer.username();

        if(username==null||
           username.trim().isEmpty())
            return null;

        return new LiveKiller(
            killer,
            generation,
            username
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
            if(disposition.keptAmount<0||
               disposition.lostAmount<0||
               disposition.keptAmount+
                    disposition.lostAmount!=
                    disposition.line.quantity)
                throw new IllegalStateException(
                    "LocalLab death disposition quantity drift lineId="+
                    disposition.line.lineId
                );
    }

    private static final class LiveKiller {
        final WorldPlayer player;
        final long generation;
        final String username;

        LiveKiller(
            WorldPlayer player,
            long generation,
            String username
        ){
            this.player=player;
            this.generation=generation;
            this.username=username;
        }
    }
}
