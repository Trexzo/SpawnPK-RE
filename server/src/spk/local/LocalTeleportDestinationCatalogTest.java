package spk.local;

import java.util.EnumSet;

/**
 * Locks the first LocalLab playable teleport destination policy to recovered
 * named region identity plus deterministic static-collision-safe landing.
 */
public final class LocalTeleportDestinationCatalogTest {
    public static void main(String[] args){
        EnumSet<TeleportNavigationService.EntryKind> configured=
            EnumSet.of(
                TeleportNavigationService.EntryKind.MONEY,
                TeleportNavigationService.EntryKind.TRAINING,
                TeleportNavigationService.EntryKind.BOSS,
                TeleportNavigationService.EntryKind.PK,
                TeleportNavigationService.EntryKind.MINIGAME,
                TeleportNavigationService.EntryKind.BOUNTY
            );

        require(
            LocalTeleportDestinationCatalog.configuredCount()==
                configured.size(),
            "configured destination count"
        );

        for(TeleportNavigationService.EntryKind kind:
                TeleportNavigationService.EntryKind.values()){
            LocalTeleportDestinationCatalog.Destination destination=
                LocalTeleportDestinationCatalog.get(
                    kind
                );

            if(!configured.contains(kind)){
                require(
                    destination==null,
                    "unexpected configured destination "+kind
                );
                continue;
            }

            require(
                destination!=null&&
                destination.kind==kind,
                "missing destination "+kind
            );

            WorldRegionAuthorityRepository.Region region=
                WorldRegionAuthorityRepository.get(
                    destination.regionId
                );

            require(
                region!=null&&
                destination.expectedName.equals(
                    region.name
                )&&
                destination.expectedGroup.equals(
                    region.group
                )&&
                region.mapPresent&&
                region.terrainParseOk,
                "recovered region authority "+kind
            );

            require(
                WorldCollisionAuthority.hasRegion(
                    destination.regionId
                ),
                "collision authority "+kind
            );

            Tile safe=
                WorldCollisionAuthority.safeTile(
                    destination.regionId,
                    destination.plane
                );

            require(
                safe!=null&&
                !WorldCollisionAuthority.blockedTile(
                    safe.x,
                    safe.y,
                    safe.plane
                )&&
                safe.x>=region.x0&&
                safe.x<=region.x1&&
                safe.y>=region.y0&&
                safe.y<=region.y1,
                "safe landing "+kind
            );
        }

        require(
            LocalTeleportDestinationCatalog.get(
                TeleportNavigationService.EntryKind.HOME
            )==null,
            "HOME must remain canonical HOME runtime"
        );
        require(
            LocalTeleportDestinationCatalog.get(
                TeleportNavigationService.EntryKind.HOUSE
            )==null,
            "HOUSE must remain explicit unconfigured instance runtime"
        );
        require(
            LocalTeleportDestinationCatalog.POLICY_AUTHORITY.startsWith(
                "LOCAL_LAB_POLICY_"
            ),
            "explicit LocalLab policy authority"
        );

        System.out.println(
            "LOCAL_PLAYABLE_TELEPORT_DESTINATIONS_PASS "+
            "configured=6 "+
            "homeCanonical=true "+
            "houseUnconfigured=true "+
            "namedRegionAuthority=true "+
            "collisionSafeLanding=true "+
            "originalSpawnPkCoordinatesClaimed=false "+
            "policy="+
            LocalTeleportDestinationCatalog.POLICY_AUTHORITY
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private LocalTeleportDestinationCatalogTest(){}
}
