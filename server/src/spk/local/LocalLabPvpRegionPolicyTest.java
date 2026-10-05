package spk.local;

public final class LocalLabPvpRegionPolicyTest {
    public static void main(String[] args){
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
            enter(
                target,
                pkTile,
                pkRegion
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

            System.out.println(
                "LOCAL_LAB_PVP_REGION_POLICY_PASS "+
                "homeRejected=true "+
                "nonPkDestinationRejected=true "+
                "bothPkEligible=true "+
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
