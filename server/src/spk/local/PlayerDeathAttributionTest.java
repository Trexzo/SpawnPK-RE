package spk.local;

import java.util.ArrayList;

/** Regression for typed responsible-player identity bound to one death sequence. */
public final class PlayerDeathAttributionTest {
    public static void main(String[] args){
        WorldPlayer victim=new WorldPlayer();
        WorldPlayer attackerA=new WorldPlayer();
        WorldPlayer attackerB=new WorldPlayer();
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(victim);

        victim.playerState().setCurrentLevel(
            PlayerState.HITPOINTS,
            5
        );

        PlayerLifecycleService.DamageResult first=
            lifecycle.applyDamageFromPlayer(
                5,
                10L,
                "ATTRIBUTED_A",
                attackerA.id()
            );

        require(
            first.died&&
            victim.lifecycle().dead()&&
            attackerA.id().equals(
                victim.lifecycle().responsiblePlayerId()
            ),
            "first player attribution"
        );

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                victim,
                "CUSTOM_LOCALLAB_ATTRIBUTION_TEST"
            );
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolver.previewCurrentDeath();

        require(
            attackerA.id().equals(
                preview.responsiblePlayerId
            ),
            "death preview attribution"
        );

        ArrayList<PlayerDeathItemResolutionService.Decision> decisions=
            new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried)
            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    line.quantity
                )
            );

        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                decisions
            );

        require(
            attackerA.id().equals(
                resolution.responsiblePlayerId
            ),
            "death resolution attribution"
        );

        PlayerLifecycleService.DamageResult ignored=
            lifecycle.applyDamageFromPlayer(
                50,
                11L,
                "SHOULD_NOT_REPLACE",
                attackerB.id()
            );

        require(
            ignored.ignoredDead&&
            attackerA.id().equals(
                victim.lifecycle().responsiblePlayerId()
            ),
            "post-death overwrite"
        );

        require(
            lifecycle.tick(15L)==
                PlayerLifecycleService.TickResult.RESPAWNED&&
            victim.lifecycle().responsiblePlayerId()==null,
            "respawn clears attribution"
        );

        victim.playerState().setCurrentLevel(
            PlayerState.HITPOINTS,
            5
        );

        PlayerLifecycleService.DamageResult environment=
            lifecycle.applyDamage(
                5,
                20L,
                "ENVIRONMENT"
            );

        require(
            environment.died&&
            victim.lifecycle().responsiblePlayerId()==null,
            "environment attribution"
        );

        require(
            lifecycle.tick(25L)==
                PlayerLifecycleService.TickResult.RESPAWNED,
            "environment respawn"
        );

        victim.playerState().setCurrentLevel(
            PlayerState.HITPOINTS,
            10
        );

        PlayerLifecycleService.DamageResult nonlethal=
            lifecycle.applyDamageFromPlayer(
                3,
                30L,
                "NONLETHAL_B",
                attackerB.id()
            );

        require(
            !nonlethal.died&&
            victim.lifecycle().responsiblePlayerId()==null,
            "nonlethal attribution"
        );

        PlayerLifecycleService.DamageResult second=
            lifecycle.applyDamageFromPlayer(
                20,
                31L,
                "ATTRIBUTED_B",
                attackerB.id()
            );

        require(
            second.died&&
            victim.lifecycle().deathSequence()==3L&&
            attackerB.id().equals(
                victim.lifecycle().responsiblePlayerId()
            ),
            "second player attribution"
        );

        System.out.println(
            "PLAYER_DEATH_ATTRIBUTION_PASS "+
            "typedPlayerId=true "+
            "deathSequenceBound=true "+
            "previewBound=true "+
            "resolutionBound=true "+
            "ignoredDeadCannotOverwrite=true "+
            "respawnClears=true "+
            "environmentNull=true "+
            "nonlethalNull=true "+
            "secondKillerRebind=true"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private PlayerDeathAttributionTest(){}
}
