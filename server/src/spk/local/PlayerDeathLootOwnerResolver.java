package spk.local;

import java.util.Objects;

/**
 * Resolves one already-recorded death attribution into the owner string used
 * by GroundItemRegistry.
 *
 * EntityId + generation remain the lethal-attribution authority. A username
 * captured while that exact generation is proven current may survive a later
 * disconnect only for ground-loot ownership; it does not authorize rewards or
 * stale-player mutation.
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

        String capturedUsername=
            attribution.attackerUsername;

        WorldPlayer attacker=
            checkedWorld.players().byId(
                attribution.attackerId
            );

        if(attacker==null||
           !checkedWorld.players().owns(
                attacker,
                attribution.attackerGeneration
            )){
            if(capturedUsername!=null)
                return new Result(
                    capturedUsername,
                    attribution.attackerId,
                    attribution.attackerGeneration,
                    "KILLER_CAPTURED_IDENTITY"
                );

            return new Result(
                null,
                attribution.attackerId,
                attribution.attackerGeneration,
                "PUBLIC_STALE_ATTACKER_GENERATION"
            );
        }

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

        username=username.trim();

        if(capturedUsername!=null&&
           !capturedUsername.equals(username))
            return new Result(
                null,
                attribution.attackerId,
                attribution.attackerGeneration,
                "PUBLIC_ATTACKER_IDENTITY_MISMATCH"
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
