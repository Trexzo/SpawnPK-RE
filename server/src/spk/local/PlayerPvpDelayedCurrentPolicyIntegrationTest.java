package spk.local;

public final class PlayerPvpDelayedCurrentPolicyIntegrationTest {
    public static void main(String[] args)throws Exception{
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
                "g51-delayed-attacker"
            );
        long targetGeneration=
            world.registerPlayer(
                target,
                "g51-delayed-target"
            );

        OutboundPacketQueue attackerQueue=
            new OutboundPacketQueue(
                1<<20
            );
        OutboundPacketQueue targetQueue=
            new OutboundPacketQueue(
                1<<20
            );
        ServerPacketWriter attackerWriter=
            new ServerPacketWriter(
                attackerQueue,
                new IsaacCipher(
                    new int[]{31,32,33,34}
                )
            );
        ServerPacketWriter targetWriter=
            new ServerPacketWriter(
                targetQueue,
                new IsaacCipher(
                    new int[]{35,36,37,38}
                )
            );

        try{
            LocalTeleportDestinationCatalog.Destination pk=
                LocalTeleportDestinationCatalog.get(
                    TeleportNavigationService.EntryKind.PK
                );
            Tile pkTile=
                WorldCollisionAuthority.safeTile(
                    pk.regionId,
                    pk.plane
                );
            WorldRegionAuthorityRepository.Region pkRegion=
                WorldRegionAuthorityRepository.get(
                    pk.regionId
                );

            enter(
                attacker,
                pkTile,
                pkRegion
            );
            target.movement().enterTransientRegion(
                pkTile.x+1,
                pkTile.y,
                pkTile.plane,
                pkRegion.x0,
                pkRegion.y0
            );

            attacker.equipment().setWeapon(
                4151
            );
            target.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,
                10
            );

            Player81WorldSync.Context attackerSync=
                Player81WorldSync.register(
                    attackerWriter,
                    world,
                    attacker,
                    new DevAuthorityWorkbench()
                );
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

            int targetIndex=
                attackerSync.clientIndexFor(
                    target
                );
            require(
                targetIndex>=0,
                "PK target not visible"
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
                "initial PK attack request"
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
                target.playerState().currentLevel(
                    PlayerState.HITPOINTS
                )==10&&
                !target.lifecycle().dead()&&
                interactions.nextAttackTick()==4L&&
                targetQueue.queuedBytes()==
                    targetBytesBefore,
                "initial delayed attack scheduling"
            );

            target.movement().returnHome();

            long tick1=world.clock().advance();
            require(
                tick1==1L&&
                world.events().runDue(
                    tick1
                )==0&&
                target.playerState().currentLevel(
                    PlayerState.HITPOINTS
                )==10,
                "pre-due relocation"
            );

            long tick2=world.clock().advance();
            require(
                tick2==2L&&
                world.events().runDue(
                    tick2
                )==1&&
                target.playerState().currentLevel(
                    PlayerState.HITPOINTS
                )==10&&
                !target.lifecycle().dead()&&
                targetQueue.queuedBytes()==
                    targetBytesBefore,
                "PK exit did not cancel delayed delivery"
            );

            String cancelled=
                interactions.tickAttack(
                    tick2,
                    attackerWriter,
                    attackerSync
                );
            require(
                cancelled!=null&&
                cancelled.contains(
                    "reason=PVP_REGION_POLICY"
                )&&
                interactions.activeAttack()==null,
                "handler did not retire attack after PK exit"
            );

            target.movement().enterTransientRegion(
                pkTile.x+1,
                pkTile.y,
                pkTile.plane,
                pkRegion.x0,
                pkRegion.y0
            );

            Player81WorldSync.unregister(
                attackerWriter
            );
            Player81WorldSync.unregister(
                targetWriter
            );

            attackerSync=
                Player81WorldSync.register(
                    attackerWriter,
                    world,
                    attacker,
                    new DevAuthorityWorkbench()
                );
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

            targetIndex=
                attackerSync.clientIndexFor(
                    target
                );
            require(
                targetIndex>=0,
                "second PK target not visible"
            );

            request=
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
                "second PK attack request"
            );

            targetBytesBefore=
                targetQueue.queuedBytes();

            String scheduledAgain=
                interactions.tickAttack(
                    world.clock().tick(),
                    attackerWriter,
                    attackerSync
                );
            require(
                scheduledAgain!=null&&
                scheduledAgain.contains(
                    "PLAYER_ATTACK_SCHEDULED"
                )&&
                target.playerState().currentLevel(
                    PlayerState.HITPOINTS
                )==10&&
                interactions.nextAttackTick()==6L,
                "second delayed attack scheduling"
            );

            long tick3=world.clock().advance();
            require(
                tick3==3L&&
                world.events().runDue(
                    tick3
                )==0&&
                target.playerState().currentLevel(
                    PlayerState.HITPOINTS
                )==10,
                "second pre-due mutation"
            );

            long tick4=world.clock().advance();
            require(
                tick4==4L&&
                world.events().runDue(
                    tick4
                )==1&&
                target.playerState().currentLevel(
                    PlayerState.HITPOINTS
                )==0&&
                target.lifecycle().dead()&&
                target.lifecycle().deathTick()==4L&&
                targetQueue.queuedBytes()>
                    targetBytesBefore,
                "due delayed hit did not land"
            );

            PlayerLifecycleState.DeathAttribution attribution=
                target.lifecycle().deathAttribution();

            require(
                attribution!=null&&
                attribution.attackerId.equals(
                    attacker.id()
                )&&
                attribution.attackerGeneration==
                    attackerGeneration&&
                "PLAYER_PVP".equals(
                    attribution.context
                ),
                "delayed lethal attribution mismatch"
            );

            System.out.println(
                "G5_DELAYED_PVP_CURRENT_POLICY_PASS "+
                "pkSchedule=true "+
                "noEarlyHpMutation=true "+
                "pkExitCancelsDueHit=true "+
                "pkExitNoHpPacket=true "+
                "reenterPkSchedule=true "+
                "dueTickDamage=true "+
                "dueTickDeath=true "+
                "killerAttribution=true "+
                "dueTickHpPacket=true "+
                "cadenceIndependent=true "+
                "generationFenced=true "+
                "authority=CUSTOM_LOCALLAB"
            );
        }finally{
            try{
                Player81WorldSync.unregister(
                    attackerWriter
                );
            }catch(Exception ignored){}
            try{
                Player81WorldSync.unregister(
                    targetWriter
                );
            }catch(Exception ignored){}

            if(attacker.registered())
                world.unregisterPlayer(
                    attacker,
                    attackerGeneration
                );
            if(target.registered())
                world.unregisterPlayer(
                    target,
                    targetGeneration
                );
            world.close();
        }
    }

    private static void enter(
        WorldPlayer player,
        Tile tile,
        WorldRegionAuthorityRepository.Region region
    ){
        player.movement().enterTransientRegion(
            tile.x,
            tile.y,
            tile.plane,
            region.x0,
            region.y0
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private PlayerPvpDelayedCurrentPolicyIntegrationTest(){}
}
