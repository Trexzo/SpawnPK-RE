package spk.local;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerPvpDelayedHitServiceTest {
    public static void main(String[] args) throws Exception {
        delayedLethalDelivery();
        staleTargetCancelsDamage();
        staleAttackerCancelsDamage();
        explicitCancellation();
        domainBoundary();

        System.out.println(
            "PLAYER_PVP_DELAYED_HIT_SERVICE_PASS " +
            "damageResolvedOnce=true " +
            "hpUnchangedBeforeDue=true " +
            "dueDelivery=true " +
            "lethalOutcomes=2 " +
            "staleAttackerNoDamage=true " +
            "staleTargetNoDamage=true " +
            "explicitCancel=true " +
            "generationFenced=true " +
            "deathAttribution=true " +
            "protocolIndependent=true"
        );
    }

    private static void delayedLethalDelivery()
        throws Exception {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker =
            new WorldPlayer();
        WorldPlayer target =
            new WorldPlayer();
        long attackerGeneration =
            world.registerPlayer(
                attacker,
                "delayed-pvp-attacker"
            );
        long targetGeneration =
            world.registerPlayer(
                target,
                "delayed-pvp-target"
            );

        List<CombatOutcome> outcomes =
            new ArrayList<>();
        int[] deliveries = {0};

        try {
            target.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,
                10
            );

            PlayerPvpDelayedHitService service =
                new PlayerPvpDelayedHitService(
                    world,
                    "CUSTOM_LOCALLAB",
                    PlayerPvpDelayedHitService
                        .REQUIRE_CURRENT_PLAYER_GENERATIONS,
                    outcomes::add,
                    (deliveredTarget, result) -> {
                        if (deliveredTarget != target) {
                            throw new AssertionError(
                                "delivery target changed"
                            );
                        }
                        deliveries[0]++;
                    }
                );

            long scheduledFrom =
                world.clock().tick();

            PlayerPvpDelayedHitService.Snapshot
                scheduled =
                    service.scheduleAtExpectedTick(
                        attacker,
                        attackerGeneration,
                        target,
                        targetGeneration,
                        10,
                        2,
                        "CUSTOM_LOCALLAB",
                        "TEST_FIXED_DAMAGE",
                        scheduledFrom
                    );

            require(
                scheduled.state ==
                    PlayerPvpDelayedHitService
                        .State.SCHEDULED &&
                scheduled.scheduledFromTick ==
                    scheduledFrom &&
                scheduled.dueTick ==
                    scheduledFrom + 2L &&
                target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    ) == 10 &&
                !target.lifecycle().dead() &&
                world.events().size() == 1,
                "schedule mutated target"
            );

            world.events().runDue(
                scheduled.dueTick - 1L
            );

            require(
                target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    ) == 10 &&
                !target.lifecycle().dead() &&
                deliveries[0] == 0 &&
                outcomes.isEmpty(),
                "hit delivered before due tick"
            );

            world.events().runDue(
                scheduled.dueTick
            );

            PlayerPvpDelayedHitService.Snapshot
                delivered =
                    service.get(
                        scheduled.hitId
                    );

            require(
                delivered != null &&
                delivered.state ==
                    PlayerPvpDelayedHitService
                        .State.DELIVERED &&
                delivered.appliedDamage == 10 &&
                delivered.hitpointsBefore == 10 &&
                delivered.hitpointsAfter == 0 &&
                delivered.died &&
                !delivered.ignoredDead &&
                target.lifecycle().dead() &&
                target.lifecycle().deathTick() ==
                    scheduled.dueTick &&
                target.lifecycle().deathAttribution()!=null &&
                attacker.id().equals(
                    target.lifecycle()
                        .deathAttribution()
                        .attackerId
                ) &&
                target.lifecycle()
                    .deathAttribution()
                    .attackerGeneration==
                        attackerGeneration &&
                "PLAYER_PVP".equals(
                    target.lifecycle()
                        .deathAttribution()
                        .context
                ) &&
                deliveries[0] == 1,
                "due delivery mismatch"
            );

            require(
                outcomes.size() == 2 &&
                outcomes.get(0).type() ==
                    CombatOutcomeType.PLAYER_KILL &&
                outcomes.get(1).type() ==
                    CombatOutcomeType.PLAYER_DEATH &&
                outcomes.get(0).worldTick() ==
                    scheduled.dueTick &&
                outcomes.get(1).worldTick() ==
                    scheduled.dueTick,
                "lethal outcomes mismatch"
            );

            require(
                service.retireTerminal(
                    scheduled.hitId
                ) &&
                service.size() == 0,
                "terminal retirement"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void staleTargetCancelsDamage()
        throws Exception {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker =
            new WorldPlayer();
        WorldPlayer target =
            new WorldPlayer();
        long attackerGeneration =
            world.registerPlayer(
                attacker,
                "stale-target-attacker"
            );
        long targetGeneration =
            world.registerPlayer(
                target,
                "stale-target"
            );

        try {
            target.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,
                20
            );

            PlayerPvpDelayedHitService service =
                new PlayerPvpDelayedHitService(
                    world,
                    "CUSTOM_LOCALLAB",
                    PlayerPvpDelayedHitService
                        .REQUIRE_CURRENT_PLAYER_GENERATIONS
                );

            PlayerPvpDelayedHitService.Snapshot
                scheduled =
                    service.scheduleAtExpectedTick(
                        attacker,
                        attackerGeneration,
                        target,
                        targetGeneration,
                        7,
                        2,
                        "CUSTOM_LOCALLAB",
                        "TEST_FIXED_DAMAGE",
                        world.clock().tick()
                    );

            world.unregisterPlayer(
                target
            );

            world.events().runDue(
                scheduled.dueTick
            );

            PlayerPvpDelayedHitService.Snapshot
                terminal =
                    service.get(
                        scheduled.hitId
                    );

            require(
                terminal != null &&
                terminal.state ==
                    PlayerPvpDelayedHitService
                        .State.STALE_TARGET &&
                terminal.appliedDamage == -1 &&
                target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    ) == 20 &&
                !target.lifecycle().dead(),
                "stale target took damage"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void staleAttackerCancelsDamage()
        throws Exception {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker =
            new WorldPlayer();
        WorldPlayer target =
            new WorldPlayer();
        long attackerGeneration =
            world.registerPlayer(
                attacker,
                "stale-attacker"
            );
        long targetGeneration =
            world.registerPlayer(
                target,
                "stale-attacker-target"
            );

        try {
            target.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,
                20
            );

            PlayerPvpDelayedHitService service =
                new PlayerPvpDelayedHitService(
                    world,
                    "CUSTOM_LOCALLAB",
                    PlayerPvpDelayedHitService
                        .REQUIRE_CURRENT_PLAYER_GENERATIONS
                );

            PlayerPvpDelayedHitService.Snapshot
                scheduled =
                    service.scheduleAtExpectedTick(
                        attacker,
                        attackerGeneration,
                        target,
                        targetGeneration,
                        7,
                        2,
                        "CUSTOM_LOCALLAB",
                        "TEST_FIXED_DAMAGE",
                        world.clock().tick()
                    );

            world.unregisterPlayer(
                attacker
            );

            world.events().runDue(
                scheduled.dueTick
            );

            PlayerPvpDelayedHitService.Snapshot
                terminal =
                    service.get(
                        scheduled.hitId
                    );

            require(
                terminal != null &&
                terminal.state ==
                    PlayerPvpDelayedHitService
                        .State.STALE_ATTACKER &&
                terminal.appliedDamage == -1 &&
                target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    ) == 20 &&
                !target.lifecycle().dead(),
                "stale attacker delivered damage"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void explicitCancellation()
        throws Exception {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker =
            new WorldPlayer();
        WorldPlayer target =
            new WorldPlayer();
        long attackerGeneration =
            world.registerPlayer(
                attacker,
                "cancel-attacker"
            );
        long targetGeneration =
            world.registerPlayer(
                target,
                "cancel-target"
            );

        try {
            target.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,
                20
            );

            PlayerPvpDelayedHitService service =
                new PlayerPvpDelayedHitService(
                    world,
                    "CUSTOM_LOCALLAB",
                    PlayerPvpDelayedHitService
                        .REQUIRE_CURRENT_PLAYER_GENERATIONS
                );

            PlayerPvpDelayedHitService.Snapshot
                scheduled =
                    service.scheduleAtExpectedTick(
                        attacker,
                        attackerGeneration,
                        target,
                        targetGeneration,
                        7,
                        2,
                        "CUSTOM_LOCALLAB",
                        "TEST_FIXED_DAMAGE",
                        world.clock().tick()
                    );

            require(
                service.cancel(
                    scheduled.hitId
                ),
                "cancel rejected"
            );

            world.events().runDue(
                scheduled.dueTick
            );

            PlayerPvpDelayedHitService.Snapshot
                terminal =
                    service.get(
                        scheduled.hitId
                    );

            require(
                terminal != null &&
                terminal.state ==
                    PlayerPvpDelayedHitService
                        .State.CANCELLED &&
                target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    ) == 20,
                "cancelled hit delivered"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void domainBoundary() {
        for (Class<?> type :
                new Class<?>[]{
                    PlayerPvpDelayedHitService.class,
                    PlayerPvpDelayedHitService
                        .Snapshot.class
                }) {
            for (Field field :
                    type.getDeclaredFields()) {
                String name =
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if (name.contains("packet") ||
                    name.contains("opcode") ||
                    name.contains("widget") ||
                    name.contains("sceneindex") ||
                    name.contains("clientindex") ||
                    name.contains("reward") ||
                    name.contains("projectile") ||
                    name.contains("gfx") ||
                    name.contains("hitsplat")) {
                    throw new AssertionError(
                        "presentation/protocol policy leaked " +
                        type.getSimpleName() +
                        "." +
                        field.getName()
                    );
                }
            }
        }
    }

    private static void cleanup(
        World world
    ) {
        for (WorldPlayer player :
                world.players().snapshot()) {
            world.unregisterPlayer(
                player
            );
        }

        world.close();
    }

    private static void require(
        boolean condition,
        String label
    ) {
        if (!condition) {
            throw new AssertionError(
                label
            );
        }
    }

    private PlayerPvpDelayedHitServiceTest() {}
}
