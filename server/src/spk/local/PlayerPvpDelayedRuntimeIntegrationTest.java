package spk.local;

public final class PlayerPvpDelayedRuntimeIntegrationTest {
    public static void main(String[] args)
        throws Exception {
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer attacker=
            new WorldPlayer();
        WorldPlayer target=
            new WorldPlayer();

        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "delayed-runtime-attacker"
            );
        world.registerPlayer(
            target,
            "delayed-runtime-target"
        );

        OutboundPacketQueue attackerQueue=
            new OutboundPacketQueue(
                1 << 20
            );
        OutboundPacketQueue targetQueue=
            new OutboundPacketQueue(
                1 << 20
            );

        ServerPacketWriter attackerWriter=
            new ServerPacketWriter(
                attackerQueue,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );
        ServerPacketWriter targetWriter=
            new ServerPacketWriter(
                targetQueue,
                new IsaacCipher(
                    new int[]{5,6,7,8}
                )
            );

        try {
            LocalTeleportDestinationCatalog.Destination
                pkDestination=
                    LocalTeleportDestinationCatalog.get(
                        TeleportNavigationService.EntryKind.PK
                    );
            Tile pkTile=
                WorldCollisionAuthority.safeTile(
                    pkDestination.regionId,
                    pkDestination.plane
                );

            require(
                pkTile!=null,
                "PK destination safe tile"
            );

            int targetX=pkTile.x;
            int targetY=pkTile.y;
            int attackerX=targetX-1;
            int attackerY=targetY;
            int chunkX=targetX>>3;
            int chunkY=targetY>>3;
            int baseX=(chunkX-6)<<3;
            int baseY=(chunkY-6)<<3;

            require(
                WorldCollisionAuthority.canStep(
                    attackerX,
                    attackerY,
                    pkDestination.plane,
                    targetX,
                    targetY
                ),
                "PK fixture cardinal step"
            );

            attacker.movement().enterTransientRegion(
                attackerX,
                attackerY,
                pkDestination.plane,
                baseX,
                baseY
            );
            target.movement().enterTransientRegion(
                targetX,
                targetY,
                pkDestination.plane,
                baseX,
                baseY
            );

            attacker.equipment().setWeapon(
                4151
            );
            target.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,
                9
            );

            Player81WorldSync.Context attackerSync=
                Player81WorldSync.register(
                    attackerWriter,
                    world,
                    attacker,
                    new DevAuthorityWorkbench()
                );
            Player81WorldSync.Context targetSync=
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

            int targetIndex=
                attackerSync.clientIndexFor(
                    target
                );
            require(
                targetIndex>=0,
                "target visible"
            );

            PlayerPvpEligibilityPolicy.Result eligibility=
                LocalLabPvpRegionPolicy.INSTANCE
                    .evaluate(
                        attacker,
                        target
                    );
            require(
                eligibility.eligible,
                "current PK-region gate rejected delayed fixture "+
                eligibility.detail
            );

            CombatAttackTimingRules delayedTiming=
                request->
                    new CombatAttackTimingRules.Result(
                        4,
                        2,
                        "CUSTOM_LOCALLAB_TEST_CADENCE",
                        "CUSTOM_LOCALLAB_TEST_DELAY",
                        "TEST_DELAY_2_TICKS"
                    );

            LocalPlayerInteractionHandler interactions=
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
                    ()->attackerGeneration,
                    LocalLabPvpRegionPolicy.INSTANCE
                );

            String request=
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
                request!=null&&
                request.contains(
                    "PLAYER_ATTACK_REQUEST"
                ),
                "attack request"
            );

            int targetBytesBefore=
                targetQueue.queuedBytes();

            String scheduled=
                interactions.tickAttack(
                    world.clock().tick(),
                    attackerWriter,
                    attackerSync
                );

            require(
                scheduled!=null&&
                scheduled.contains(
                    "PLAYER_ATTACK_SCHEDULED"
                )&&
                scheduled.contains(
                    "hitDelayTicks=2"
                )&&
                scheduled.contains(
                    "targetHpPacket134=DEFERRED_UNTIL_DUE"
                ),
                "runtime did not schedule hit "+
                scheduled
            );

            require(
                target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    )==9&&
                !target.lifecycle().dead()&&
                interactions.nextAttackTick()==4L&&
                targetQueue.queuedBytes()==
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
                    )==9&&
                !target.lifecycle().dead()&&
                targetQueue.queuedBytes()==
                    targetBytesBefore,
                "pre-due tick leaked damage"
            );

            world.events().runDue(
                2L
            );

            PlayerLifecycleState.DeathAttribution attribution=
                target.lifecycle()
                    .deathAttribution();

            require(
                target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    )==0&&
                target.lifecycle().dead()&&
                target.lifecycle().deathTick()==2L&&
                attribution!=null&&
                attacker.id().equals(
                    attribution.attackerId
                )&&
                attribution.attackerGeneration==
                    attackerGeneration&&
                "PLAYER_PVP".equals(
                    attribution.context
                )&&
                targetQueue.queuedBytes()>
                    targetBytesBefore,
                "due hit did not reach clean-R25 live target"
            );

            String cancelled=
                interactions.prepareTick(
                    2L,
                    attackerSync
                );

            require(
                interactions.activeAttack()==
                    null&&
                cancelled!=null&&
                cancelled.contains(
                    "PLAYER_ATTACK_CANCELLED"
                ),
                "dead target remained active"
            );

            System.out.println(
                "PLAYER_PVP_DELAYED_RUNTIME_INTEGRATION_PASS "+
                "pkRegionGate=true "+
                "attackTickDamage=false "+
                "dueTick=2 "+
                "dueDamage=9 "+
                "deathAttribution=true "+
                "hpPacket134AtDue=true "+
                "cadenceTick=4 "+
                "deadTargetCancelled=true "+
                "authority=CUSTOM_LOCALLAB_G5_DELAYED_PVP_HIT_V1"
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
