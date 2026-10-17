package spk.local;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * LocalLab-owned playable destinations for the recovered exact-v308 top-level
 * teleport/navigation concepts.
 *
 * Region identities are recovered cache authority. Category->region selection
 * is explicitly LocalLab gameplay policy, not recovered original SpawnPK
 * destination policy. Landing coordinates are selected separately by static
 * collision authority.
 */
final class LocalTeleportDestinationCatalog {
    static final String POLICY_AUTHORITY=
        "LOCAL_LAB_POLICY_PLAYABLE_TELEPORTS_R1";

    static final class Destination {
        final TeleportNavigationService.EntryKind kind;
        final int regionId;
        final int plane;
        final String expectedName;
        final String expectedGroup;

        Destination(
            TeleportNavigationService.EntryKind kind,
            int regionId,
            int plane,
            String expectedName,
            String expectedGroup
        ){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.regionId=regionId;
            this.plane=plane;
            this.expectedName=requireText(expectedName,"expectedName");
            this.expectedGroup=requireText(expectedGroup,"expectedGroup");
        }
    }

    private static final Map<TeleportNavigationService.EntryKind,Destination>
        BY_KIND=build();

    static Destination get(
        TeleportNavigationService.EntryKind kind
    ){
        return BY_KIND.get(
            Objects.requireNonNull(
                kind,
                "kind"
            )
        );
    }

    static boolean configured(
        TeleportNavigationService.EntryKind kind
    ){
        return get(kind)!=null;
    }

    static int configuredCount(){
        return BY_KIND.size();
    }

    private static Map<TeleportNavigationService.EntryKind,Destination> build(){
        EnumMap<TeleportNavigationService.EntryKind,Destination> out=
            new EnumMap<>(
                TeleportNavigationService.EntryKind.class
            );

        // LocalLab progression/content routing. These are not claims that the
        // original server used these top-level categories for these regions.
        put(
            out,
            new Destination(
                TeleportNavigationService.EntryKind.MONEY,
                16177,
                0,
                "Regular Dzone",
                "dzone"
            )
        );
        put(
            out,
            new Destination(
                TeleportNavigationService.EntryKind.TRAINING,
                12946,
                0,
                "Blood slayer cave",
                "bs_cave"
            )
        );
        put(
            out,
            new Destination(
                TeleportNavigationService.EntryKind.BOSS,
                16168,
                0,
                "Vetion's Rest",
                "vetion"
            )
        );
        put(
            out,
            new Destination(
                TeleportNavigationService.EntryKind.PK,
                13641,
                0,
                "cwars8a",
                "clan_wars_8"
            )
        );
        put(
            out,
            new Destination(
                TeleportNavigationService.EntryKind.MINIGAME,
                16185,
                0,
                "Gamble",
                "gamble"
            )
        );
        put(
            out,
            new Destination(
                TeleportNavigationService.EntryKind.BOUNTY,
                13642,
                0,
                "cwars8b",
                "clan_wars_8"
            )
        );

        // HOUSE intentionally remains unconfigured until Construction's house
        // instance materialization has a legitimate live session entrypoint.
        if(out.containsKey(
                TeleportNavigationService.EntryKind.HOME)||
           out.containsKey(
                TeleportNavigationService.EntryKind.HOUSE))
            throw new IllegalStateException(
                "HOME/HOUSE must not be region-catalog aliases"
            );

        for(Destination destination:out.values())
            validateRecoveredRegion(
                destination
            );

        return Collections.unmodifiableMap(
            out
        );
    }

    private static void put(
        EnumMap<TeleportNavigationService.EntryKind,Destination> out,
        Destination destination
    ){
        if(out.put(
                destination.kind,
                destination)!=null)
            throw new IllegalStateException(
                "duplicate LocalLab teleport destination "+
                destination.kind
            );
    }

    private static void validateRecoveredRegion(
        Destination destination
    ){
        WorldRegionAuthorityRepository.Region region=
            WorldRegionAuthorityRepository.get(
                destination.regionId
            );

        if(region==null)
            throw new IllegalStateException(
                "missing teleport region "+
                destination.regionId
            );

        if(!destination.expectedName.equals(
                region.name)||
           !destination.expectedGroup.equals(
                region.group))
            throw new IllegalStateException(
                "teleport region metadata drift kind="+
                destination.kind+
                " id="+
                destination.regionId+
                " name="+region.name+
                " group="+region.group
            );

        if(!region.mapPresent||
           !region.terrainParseOk||
           !WorldCollisionAuthority.hasRegion(
                destination.regionId))
            throw new IllegalStateException(
                "teleport region lacks map/collision authority kind="+
                destination.kind+
                " id="+
                destination.regionId
            );

        Tile safe=
            WorldCollisionAuthority.safeTile(
                destination.regionId,
                destination.plane
            );

        if(safe==null)
            throw new IllegalStateException(
                "teleport region has no collision-safe landing kind="+
                destination.kind+
                " id="+
                destination.regionId
            );
    }

    private static String requireText(
        String value,
        String label
    ){
        if(value==null)
            throw new NullPointerException(label);

        String clean=value.trim();
        if(clean.isEmpty())
            throw new IllegalArgumentException(
                label+" blank"
            );

        return clean;
    }

    private LocalTeleportDestinationCatalog(){}
}
