package spk.local;

public final class PlayerPvpDeathLedgerTest {
    public static void main(String[] args) {
        exactAttributionAndReplay();
        staleActorsIgnored();
        wrongTickIgnored();
        conflictingKillerRejected();
        closeClears();

        System.out.println(
            "PLAYER_PVP_DEATH_LEDGER_PASS " +
            "exactDeathSequence=true " +
            "exactGenerations=true " +
            "duplicateIdempotent=true " +
            "staleAttackerIgnored=true " +
            "staleVictimIgnored=true " +
            "wrongTickIgnored=true " +
            "conflictingKillerRejected=true " +
            "playerDeathIgnored=true " +
            "closeClears=true " +
            "rewardPolicy=false"
        );
    }

    private static void exactAttributionAndReplay() {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker =
            new WorldPlayer();
        WorldPlayer victim =
            new WorldPlayer();

        long attackerGeneration =
            world.registerPlayer(
                attacker,
                "ledger-attacker"
            );
        long victimGeneration =
            world.registerPlayer(
                victim,
                "ledger-victim"
            );

        try {
            kill(
                victim,
                10L
            );

            CombatOutcome kill =
                new CombatOutcome(
                    attacker.id().toString(),
                    victim.id().toString(),
                    CombatOutcomeType.PLAYER_KILL,
                    CombatOutcomeContext.PLAYER_PVP,
                    10L,
                    PlayerLifecycleService.AUTHORITY
                );

            world.pvpDeathLedger()
                .onCombatOutcome(
                    kill
                );

            PlayerPvpDeathLedger.Entry entry =
                world.pvpDeathLedger()
                    .get(
                        victim.id(),
                        victim.lifecycle()
                            .deathSequence()
                    );

            require(
                entry != null &&
                entry.attackerId.equals(
                    attacker.id()
                ) &&
                entry.attackerGeneration ==
                    attackerGeneration &&
                "ledger-attacker".equals(
                    entry.attackerUsername
                ) &&
                entry.victimId.equals(
                    victim.id()
                ) &&
                entry.victimGeneration ==
                    victimGeneration &&
                "ledger-victim".equals(
                    entry.victimUsername
                ) &&
                entry.deathSequence ==
                    victim.lifecycle()
                        .deathSequence() &&
                entry.deathTick == 10L,
                "exact attribution"
            );

            world.pvpDeathLedger()
                .onCombatOutcome(
                    kill
                );

            require(
                world.pvpDeathLedger().size() == 1 &&
                world.pvpDeathLedger()
                    .get(
                        victim.id(),
                        victim.lifecycle()
                            .deathSequence()
                    ) == entry,
                "duplicate outcome changed ledger"
            );

            world.pvpDeathLedger()
                .onCombatOutcome(
                    new CombatOutcome(
                        attacker.id().toString(),
                        victim.id().toString(),
                        CombatOutcomeType.PLAYER_DEATH,
                        CombatOutcomeContext.PLAYER_PVP,
                        10L,
                        PlayerLifecycleService.AUTHORITY
                    )
                );

            require(
                world.pvpDeathLedger().size() == 1,
                "PLAYER_DEATH created duplicate attribution"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void staleActorsIgnored() {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer staleAttacker =
            new WorldPlayer();
        WorldPlayer victim =
            new WorldPlayer();

        world.registerPlayer(
            staleAttacker,
            "stale-ledger-attacker"
        );
        world.registerPlayer(
            victim,
            "stale-ledger-victim"
        );

        try {
            kill(
                victim,
                20L
            );

            EntityId attackerId =
                staleAttacker.id();

            world.unregisterPlayer(
                staleAttacker
            );

            world.pvpDeathLedger()
                .onCombatOutcome(
                    new CombatOutcome(
                        attackerId.toString(),
                        victim.id().toString(),
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        20L,
                        PlayerLifecycleService.AUTHORITY
                    )
                );

            require(
                world.pvpDeathLedger().size() == 0,
                "stale attacker attributed"
            );

            WorldPlayer currentAttacker =
                new WorldPlayer();

            world.registerPlayer(
                currentAttacker,
                "current-ledger-attacker"
            );

            EntityId victimId =
                victim.id();

            world.unregisterPlayer(
                victim
            );

            world.pvpDeathLedger()
                .onCombatOutcome(
                    new CombatOutcome(
                        currentAttacker.id().toString(),
                        victimId.toString(),
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        20L,
                        PlayerLifecycleService.AUTHORITY
                    )
                );

            require(
                world.pvpDeathLedger().size() == 0,
                "stale victim attributed"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void wrongTickIgnored() {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker =
            new WorldPlayer();
        WorldPlayer victim =
            new WorldPlayer();

        world.registerPlayer(
            attacker,
            "wrong-tick-attacker"
        );
        world.registerPlayer(
            victim,
            "wrong-tick-victim"
        );

        try {
            kill(
                victim,
                30L
            );

            world.pvpDeathLedger()
                .onCombatOutcome(
                    new CombatOutcome(
                        attacker.id().toString(),
                        victim.id().toString(),
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        31L,
                        PlayerLifecycleService.AUTHORITY
                    )
                );

            require(
                world.pvpDeathLedger().size() == 0,
                "wrong-tick outcome attributed"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void conflictingKillerRejected() {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer attackerA =
            new WorldPlayer();
        WorldPlayer attackerB =
            new WorldPlayer();
        WorldPlayer victim =
            new WorldPlayer();

        world.registerPlayer(
            attackerA,
            "conflict-a"
        );
        world.registerPlayer(
            attackerB,
            "conflict-b"
        );
        world.registerPlayer(
            victim,
            "conflict-victim"
        );

        try {
            kill(
                victim,
                40L
            );

            CombatOutcome first =
                new CombatOutcome(
                    attackerA.id().toString(),
                    victim.id().toString(),
                    CombatOutcomeType.PLAYER_KILL,
                    CombatOutcomeContext.PLAYER_PVP,
                    40L,
                    PlayerLifecycleService.AUTHORITY
                );

            world.pvpDeathLedger()
                .onCombatOutcome(
                    first
                );

            boolean rejected=false;

            try {
                world.pvpDeathLedger()
                    .onCombatOutcome(
                        new CombatOutcome(
                            attackerB.id().toString(),
                            victim.id().toString(),
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            40L,
                            PlayerLifecycleService.AUTHORITY
                        )
                    );
            } catch(IllegalStateException expected) {
                rejected=true;
            }

            require(
                rejected &&
                world.pvpDeathLedger()
                    .get(
                        victim.id(),
                        victim.lifecycle()
                            .deathSequence()
                    ).attackerId.equals(
                        attackerA.id()
                    ),
                "conflicting killer not fail-closed"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void closeClears() {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker =
            new WorldPlayer();
        WorldPlayer victim =
            new WorldPlayer();

        world.registerPlayer(
            attacker,
            "close-ledger-attacker"
        );
        world.registerPlayer(
            victim,
            "close-ledger-victim"
        );

        kill(
            victim,
            50L
        );

        world.pvpDeathLedger()
            .onCombatOutcome(
                new CombatOutcome(
                    attacker.id().toString(),
                    victim.id().toString(),
                    CombatOutcomeType.PLAYER_KILL,
                    CombatOutcomeContext.PLAYER_PVP,
                    50L,
                    PlayerLifecycleService.AUTHORITY
                )
            );

        require(
            world.pvpDeathLedger().size() == 1,
            "close fixture"
        );

        world.close();

        require(
            world.pvpDeathLedger().closed() &&
            world.pvpDeathLedger().size() == 0,
            "world close retained ledger"
        );
    }

    private static void kill(
        WorldPlayer victim,
        long tick
    ) {
        PlayerLifecycleService.DamageResult result =
            new PlayerLifecycleService(
                victim
            ).applyDamage(
                500,
                tick,
                "LEDGER_TEST",
                5L
            );

        require(
            result.died &&
            victim.lifecycle().dead(),
            "death fixture"
        );
    }

    private static void cleanup(
        World world
    ) {
        if(world.closed())
            return;

        for(WorldPlayer player:
                world.players().snapshot())
            world.unregisterPlayer(
                player
            );

        world.close();
    }

    private static void require(
        boolean condition,
        String label
    ) {
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private PlayerPvpDeathLedgerTest() {}
}
