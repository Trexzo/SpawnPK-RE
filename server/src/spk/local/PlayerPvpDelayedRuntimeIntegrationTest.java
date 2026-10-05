package spk.local;

import java.io.ByteArrayOutputStream;

public final class PlayerPvpDelayedRuntimeIntegrationTest {
    public static void main(String[] args)
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
                "delayed-runtime-attacker"
            );
        world.registerPlayer(
            target,
            "delayed-runtime-target"
        );

        ByteArrayOutputStream attackerWire =
            new ByteArrayOutputStream();
        ByteArrayOutputStream targetWire =
            new ByteArrayOutputStream();

        ServerPacketWriter attackerWriter =
            new ServerPacketWriter(
                attackerWire,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );
        ServerPacketWriter targetWriter =
            new ServerPacketWriter(
                targetWire,
                new IsaacCipher(
                    new int[]{5,6,7,8}
                )
            );

        try {
            String move =
                target.movement().accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{
                            attacker.movement().x() + 1
                        },
                        new int[]{
                            attacker.movement().y()
                        },
                        new byte[0]
                    )
                );

            require(
                move.startsWith("ACCEPTED"),
                "target adjacency"
            );
            target.movement().advance();

            attacker.equipment().setWeapon(
                4151
            );
            target.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,
                9
            );

            Player81WorldSync.Context attackerSync =
                Player81WorldSync.register(
                    attackerWriter,
                    world,
                    attacker,
                    new DevAuthorityWorkbench()
                );
            Player81WorldSync.Context targetSync =
                Player81WorldSync.register(
                    targetWriter,
                    world,
                    target,
                    new DevAuthorityWorkbench()
                );

            Player81WorldSync.transformForTest(
                attackerSync,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                targetSync,
                BootstrapPackets.player81Idle()
            );

            int targetIndex =
                attackerSync.clientIndexFor(
                    target
                );
            require(
                targetIndex >= 0,
                "target visible"
            );

            CombatAttackTimingRules delayedTiming =
                request ->
                    new CombatAttackTimingRules.Result(
                        4,
                        2,
                        "CUSTOM_LOCALLAB_TEST_CADENCE",
                        "CUSTOM_LOCALLAB_TEST_DELAY",
                        "TEST_DELAY_2_TICKS"
                    );

            LocalPlayerInteractionHandler interactions =
                new LocalPlayerInteractionHandler(
                    world,
                    attacker,
                    attacker.movement(),
                    attacker.equipment(),
                    attacker.combatStyles(),
                    CombatDamageRules.localLabFallback(),
                    delayedTiming,
                    CombatSystemHooks.forPlayer(
                        attacker
                    ),
                    () -> attackerGeneration
                );

            String request =
                interactions.handleResolved(
                    new PlayerAction(
                        128,
                        1,
                        targetIndex,
                        "Attack"
                    ),
                    target,
                    attackerSync
                );

            require(
                request != null &&
                request.contains(
                    "PLAYER_ATTACK_REQUEST"
                ),
                "attack request"
            );

            int targetBytesBefore =
                targetWire.size();

            String scheduled =
                interactions.tickAttack(
                    world.clock().tick(),
                    attackerWriter,
                    attackerSync
                );

            require(
                scheduled != null &&
                scheduled.contains(
                    "PLAYER_ATTACK_SCHEDULED"
                ) &&
                scheduled.contains(
                    "hitDelayTicks=2"
                ) &&
                scheduled.contains(
                    "targetHpPacket134=DEFERRED_UNTIL_DUE"
                ),
                "runtime did not schedule hit"
            );

            require(
                target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    ) == 9 &&
                !target.lifecycle().dead() &&
                interactions.nextAttackTick() == 4L &&
                targetWire.size() ==
                    targetBytesBefore,
                "attack tick leaked delayed damage"
            );

            world.events().runDue(
                1L
            );

            require(
                target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    ) == 9 &&
                !target.lifecycle().dead() &&
                targetWire.size() ==
                    targetBytesBefore,
                "pre-due tick leaked damage"
            );

            world.events().runDue(
                2L
            );

            require(
                target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    ) == 0 &&
                target.lifecycle().dead() &&
                target.lifecycle().deathTick() == 2L &&
                targetWire.size() >
                    targetBytesBefore,
                "due hit did not reach live target"
            );

            String cancelled =
                interactions.prepareTick(
                    2L,
                    attackerSync
                );

            require(
                interactions.activeAttack() ==
                    null &&
                cancelled != null &&
                cancelled.contains(
                    "PLAYER_ATTACK_CANCELLED"
                ),
                "dead target remained active"
            );

            System.out.println(
                "PLAYER_PVP_DELAYED_RUNTIME_INTEGRATION_PASS " +
                "attackTickDamage=false " +
                "dueTick=2 " +
                "dueDamage=9 " +
                "hpPacket134AtDue=true " +
                "cadenceTick=4 " +
                "deadTargetCancelled=true " +
                "authority=CUSTOM_LOCALLAB"
            );
        } finally {
            try {
                Player81WorldSync.unregister(
                    attackerWriter
                );
            } catch (Exception ignored) {}
            try {
                Player81WorldSync.unregister(
                    targetWriter
                );
            } catch (Exception ignored) {}

            for (WorldPlayer player :
                    world.players().snapshot()) {
                world.unregisterPlayer(
                    player
                );
            }

            world.close();
        }
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

    private PlayerPvpDelayedRuntimeIntegrationTest() {}
}
