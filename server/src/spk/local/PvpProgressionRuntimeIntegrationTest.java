package spk.local;

/**
 * Playability regression: a lethal live-world PvP resolution must persist
 * attacker/victim progression instead of discarding CombatOutcome facts.
 */
public final class PvpProgressionRuntimeIntegrationTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer victim=new WorldPlayer();

        long attackerGeneration=
            world.registerPlayer(attacker,"pvp-attacker");
        long victimGeneration=
            world.registerPlayer(victim,"pvp-victim");

        try{
            attacker.equipment().setWeapon(4151);

            if(!victim.playerState().setCurrentLevel(
                    PlayerState.HITPOINTS,
                    9))
                throw new AssertionError(
                    "victim HP fixture did not change"
                );

            CombatOutcomeObserver observer=
                new PvpProgressionCombatOutcomeObserver(
                    world
                );

            PlayerCombatResolutionService combat=
                new PlayerCombatResolutionService(
                    attacker,
                    CombatDamageRules.localLabFallback(),
                    CombatAttackTimingRules.recoveredCompatibility(),
                    CombatSystemHooks.forPlayer(attacker),
                    observer
                );

            CombatStyleRepository.Style style=
                attacker.combatStyles().current(
                    CombatInterfaceRepository.forWeapon(
                        attacker.equipment().weapon()
                    )
                );

            PlayerCombatResolutionService.Result result=
                combat.resolveImmediateOwned(
                    world,
                    attackerGeneration,
                    victim,
                    victimGeneration,
                    attacker.equipment().weapon(),
                    style,
                    40L
                );

            if(!result.lifecycle.died)
                throw new AssertionError(
                    "lethal PvP fixture did not die"
                );

            PvpProgressionService.Snapshot attackerStats=
                new PvpProgressionService(attacker).snapshot();
            PvpProgressionService.Snapshot victimStats=
                new PvpProgressionService(victim).snapshot();

            if(attackerStats.kills!=1L||
               attackerStats.deaths!=0L||
               attackerStats.revision!=1L)
                throw new AssertionError(
                    "attacker progression mismatch "+
                    attackerStats
                );

            if(victimStats.kills!=0L||
               victimStats.deaths!=1L||
               victimStats.revision!=1L)
                throw new AssertionError(
                    "victim progression mismatch "+
                    victimStats
                );

            observer.onCombatOutcome(
                new CombatOutcome(
                    attacker.id().toString(),
                    victim.id().toString(),
                    CombatOutcomeType.NPC_KILL,
                    CombatOutcomeContext.PLAYER_PVP,
                    41L,
                    "TEST_NONMATCHING"
                )
            );

            attackerStats=
                new PvpProgressionService(attacker).snapshot();
            victimStats=
                new PvpProgressionService(victim).snapshot();

            if(attackerStats.kills!=1L||
               victimStats.deaths!=1L)
                throw new AssertionError(
                    "nonmatching combat fact mutated PvP progression"
                );

            PlayerSnapshot attackerSnapshot=
                PlayerSnapshotCodec.capture(
                    "pvp-attacker",
                    attacker
                );
            PlayerSnapshot victimSnapshot=
                PlayerSnapshotCodec.capture(
                    "pvp-victim",
                    victim
                );

            WorldPlayer restoredAttacker=
                new WorldPlayer();
            WorldPlayer restoredVictim=
                new WorldPlayer();

            PlayerSnapshotCodec.applyValidated(
                attackerSnapshot,
                restoredAttacker
            );
            PlayerSnapshotCodec.applyValidated(
                victimSnapshot,
                restoredVictim
            );

            PvpProgressionService.Snapshot restoredAttackerStats=
                new PvpProgressionService(
                    restoredAttacker
                ).snapshot();
            PvpProgressionService.Snapshot restoredVictimStats=
                new PvpProgressionService(
                    restoredVictim
                ).snapshot();

            if(restoredAttackerStats.kills!=1L||
               restoredAttackerStats.deaths!=0L||
               restoredAttackerStats.revision!=1L)
                throw new AssertionError(
                    "attacker persistence mismatch "+
                    restoredAttackerStats
                );

            if(restoredVictimStats.kills!=0L||
               restoredVictimStats.deaths!=1L||
               restoredVictimStats.revision!=1L)
                throw new AssertionError(
                    "victim persistence mismatch "+
                    restoredVictimStats
                );

            System.out.println(
                "PVP_PROGRESSION_RUNTIME_PASS "+
                "attackerKills=1 victimDeaths=1 "+
                "snapshotRoundTrip=true "+
                "namespace="+
                PvpProgressionService.NAMESPACE+
                " authority="+
                PvpProgressionService.AUTHORITY
            );
        }finally{
            world.unregisterPlayer(attacker);
            world.unregisterPlayer(victim);
            world.close();
        }
    }
}
