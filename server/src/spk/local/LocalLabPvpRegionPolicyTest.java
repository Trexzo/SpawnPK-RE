package spk.local;

public final class LocalLabPvpRegionPolicyTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer attacker=
            new WorldPlayer();
        WorldPlayer target=
            new WorldPlayer();

        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "attacker"
            );
        long targetGeneration=
            world.registerPlayer(
                target,
                "target"
            );

        try{
            PlayerPvpEligibilityPolicy policy=
                LocalLabPvpRegionPolicy.INSTANCE;

            java.io.ByteArrayOutputStream attackerWire=
                new java.io.ByteArrayOutputStream();
            java.io.ByteArrayOutputStream targetWire=
                new java.io.ByteArrayOutputStream();

            ServerPacketWriter attackerWriter=
                new ServerPacketWriter(
                    attackerWire,
                    new IsaacCipher(
                        new int[]{91,92,93,94}
                    )
                );
            ServerPacketWriter targetWriter=
                new ServerPacketWriter(
                    targetWire,
                    new IsaacCipher(
                        new int[]{95,96,97,98}
                    )
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
                "HOME target fixture not visible"
            );

            LocalPlayerInteractionHandler interactions=
                new LocalPlayerInteractionHandler(
                    world,
                    attacker,
                    attacker.movement(),
                    attacker.equipment(),
                    attacker::generation,
                    policy
                );

            String homeAttack=
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
                homeAttack!=null&&
                homeAttack.contains(
                    "reason=PVP_REGION_POLICY")&&
                interactions.activeAttack()==null,
                "HOME handler attack was not rejected by region policy"
            );

            String homeFollow=
                interactions.handleResolved(
                    new PlayerAction(
                        153,
                        2,
                        targetIndex,
                        "Follow"
                    ),
                    target,
                    attackerSync
                );

            require(
                homeFollow!=null&&
                homeFollow.contains(
                    "PLAYER_FOLLOW_REQUEST")&&
                interactions.activeFollow()!=null,
                "HOME follow was incorrectly blocked"
            );

            interactions.cancelActive();

            PlayerPvpEligibilityPolicy.Result home=
                policy.evaluate(
                    attacker,
                    target
                );

            require(
                !home.eligible&&
                home.detail.contains(
                    LocalLabPvpRegionPolicy.AUTHORITY),
                "HOME PvP unexpectedly eligible"
            );

            LocalTeleportDestinationCatalog.Destination boss=
                LocalTeleportDestinationCatalog.get(
                    TeleportNavigationService.EntryKind.BOSS
                );
            Tile bossTile=
                WorldCollisionAuthority.safeTile(
                    boss.regionId,
                    boss.plane
                );
            WorldRegionAuthorityRepository.Region bossRegion=
                WorldRegionAuthorityRepository.get(
                    boss.regionId
                );

            enter(
                attacker,
                bossTile,
                bossRegion
            );
            enter(
                target,
                bossTile,
                bossRegion
            );

            PlayerPvpEligibilityPolicy.Result nonPk=
                policy.evaluate(
                    attacker,
                    target
                );

            require(
                !nonPk.eligible,
                "non-PK configured destination eligible"
            );

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
                "PK target fixture not visible"
            );

            String pkAttack=
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
                pkAttack!=null&&
                pkAttack.contains(
                    "PLAYER_ATTACK_REQUEST")&&
                interactions.activeAttack()!=null,
                "PK handler attack was not accepted"
            );

            PlayerPvpEligibilityPolicy.Result bothPk=
                policy.evaluate(
                    attacker,
                    target
                );

            require(
                bothPk.eligible&&
                bothPk.detail.contains(
                    "requiredRegion="+pk.regionId),
                "both-PK eligibility missing"
            );

            target.movement().returnHome();

            String cancelledAfterTargetLeave=
                interactions.tickAttack(
                    1L,
                    attackerWriter,
                    attackerSync
                );

            require(
                cancelledAfterTargetLeave!=null&&
                cancelledAfterTargetLeave.contains(
                    "PLAYER_ATTACK_CANCELLED")&&
                interactions.activeAttack()==null,
                "active attack survived target leaving PK"
            );

            PlayerPvpEligibilityPolicy.Result targetLeft=
                policy.evaluate(
                    attacker,
                    target
                );

            require(
                !targetLeft.eligible,
                "target leaving PK remained eligible"
            );

            enter(
                target,
                pkTile,
                pkRegion
            );
            attacker.movement().returnHome();

            PlayerPvpEligibilityPolicy.Result attackerLeft=
                policy.evaluate(
                    attacker,
                    target
                );

            require(
                !attackerLeft.eligible,
                "attacker leaving PK remained eligible"
            );

            Player81WorldSync.unregister(
                attackerWriter
            );
            Player81WorldSync.unregister(
                targetWriter
            );

            System.out.println(
                "LOCAL_LAB_PVP_REGION_POLICY_PASS "+
                "homeRejected=true "+
                "nonPkDestinationRejected=true "+
                "bothPkEligible=true "+
                "handlerHomeRejected=true "+
                "handlerPkAccepted=true "+
                "activeAttackCancelledOnExit=true "+
                "followOutsidePkUnaffected=true "+
                "targetLeaveRejected=true "+
                "attackerLeaveRejected=true "+
                "serverRegionAuthority=true "+
                "clientWidgetAuthority=false "+
                "authority="+
                LocalLabPvpRegionPolicy.AUTHORITY
            );
        }finally{
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
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private LocalLabPvpRegionPolicyTest(){}
}
