package spk.local;

import java.util.Objects;

/**
 * Resolves one already-recorded death attribution into the owner string used
 * by GroundItemRegistry.
 *
 * EntityId + generation are the authority. Username is read only after the
 * exact attacker generation is proven current because the ground-item layer's
 * visibility contract is still username based.
 */
final class PlayerDeathLootOwnerResolver {
    static final class Result {
        final String lootOwner;
        final EntityId attackerId;
        final long attackerGeneration;
        final String reason;

        private Result(
            String lootOwner,
            EntityId attackerId,
            long attackerGeneration,
            String reason
        ){
            this.lootOwner=lootOwner;
            this.attackerId=attackerId;
            this.attackerGeneration=attackerGeneration;
            this.reason=reason;
        }

        boolean killerScoped(){
            return lootOwner!=null;
        }
    }

    Result resolve(
        World world,
        WorldPlayer victim,
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        World checkedWorld=
            Objects.requireNonNull(
                world,
                "world"
            );
        WorldPlayer checkedVictim=
            Objects.requireNonNull(
                victim,
                "victim"
            );
        PlayerDeathItemResolutionService.Resolution checkedResolution=
            Objects.requireNonNull(
                resolution,
                "resolution"
            );

        if(!checkedVictim.id().equals(
                checkedResolution.playerId))
            throw new IllegalArgumentException(
                "death resolution belongs to another victim expected="+
                checkedVictim.id()+
                " actual="+
                checkedResolution.playerId
            );

        PlayerLifecycleState.DeathAttribution attribution=
            checkedVictim.lifecycle()
                .deathAttribution();

        if(attribution==null)
            return publicResult(
                "PUBLIC_NO_ATTRIBUTION"
            );

        if(attribution.deathSequence!=
                checkedResolution.deathSequence)
            return publicResult(
                "PUBLIC_STALE_DEATH_ATTRIBUTION"
            );

        if(!"PLAYER_PVP".equals(
                attribution.context))
            return publicResult(
                "PUBLIC_UNSUPPORTED_ATTRIBUTION_CONTEXT"
            );

        WorldPlayer attacker=
            checkedWorld.players().byId(
                attribution.attackerId
            );

        if(attacker==null||
           !checkedWorld.players().owns(
                attacker,
                attribution.attackerGeneration
            ))
            return new Result(
                null,
                attribution.attackerId,
                attribution.attackerGeneration,
                "PUBLIC_STALE_ATTACKER_GENERATION"
            );

        String username=
            attacker.username();

        if(username==null||
           username.trim().isEmpty())
            return new Result(
                null,
                attribution.attackerId,
                attribution.attackerGeneration,
                "PUBLIC_ATTACKER_USERNAME_UNAVAILABLE"
            );

        return new Result(
            username,
            attribution.attackerId,
            attribution.attackerGeneration,
            "KILLER_CURRENT_GENERATION"
        );
    }

    private static Result publicResult(
        String reason
    ){
        return new Result(
            null,
            null,
            0L,
            reason
        );
    }
}
