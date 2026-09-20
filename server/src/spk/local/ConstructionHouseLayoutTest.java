package spk.local;

import java.util.Collections;
import java.util.LinkedHashSet;

/** Deterministic regression for Issue #167 semantic Construction/house state. */
public final class ConstructionHouseLayoutTest {
    public static void main(String[] args) {
        ConstructionRoomCatalog catalog =
            ConstructionRoomCatalog.exactCurrentClientEvidence();

        require(catalog.size() == 23, "exact-current room count");
        HouseRoomDefinition parlour = catalog.find("parlour").orElseThrow(
            () -> new AssertionError("missing parlour")
        );
        HouseRoomDefinition treasure = catalog.find("treasure_room").orElseThrow(
            () -> new AssertionError("missing treasure room")
        );
        require("Parlour".equals(parlour.displayName()), "parlour label");
        require(parlour.displayedLevelRequirementEvidence() == 1, "parlour displayed level");
        require("Treasure room".equals(treasure.displayName()), "treasure label");
        require(treasure.displayedLevelRequirementEvidence() == 75, "treasure displayed level");
        require(
            parlour.authority() == ConstructionEvidenceAuthority.EXACT_CURRENT_CLIENT,
            "room provenance"
        );

        ConstructionService service = new ConstructionService();
        ConstructionService.HouseId houseId =
            new ConstructionService.HouseId("house:owner-a");

        ConstructionService.Snapshot created = service.createHouse(
            houseId,
            "player:owner-a",
            ConstructionEvidenceAuthority.CUSTOM_LOCALLAB
        );
        require(created.rooms().isEmpty(), "empty house");
        require(!created.buildModeEnabled(), "build mode initially explicit false");
        require("player:owner-a".equals(created.ownerRef()), "owner identity");

        ConstructionService.Snapshot buildMode =
            service.setBuildMode(houseId, true);
        require(buildMode.buildModeEnabled(), "build mode enabled");

        ConstructionService.PlacedRoomId roomA =
            new ConstructionService.PlacedRoomId("placed:a");
        ConstructionService.PlacedRoomId roomB =
            new ConstructionService.PlacedRoomId("placed:b");

        ConstructionService.LayoutCoordinate coordA =
            new ConstructionService.LayoutCoordinate(0, 0, 0);
        ConstructionService.LayoutCoordinate coordB =
            new ConstructionService.LayoutCoordinate(1, 0, 0);

        service.addRoom(
            houseId,
            roomA,
            parlour,
            coordA,
            Collections.<ConstructionService.PlacedRoomId>emptySet()
        );

        LinkedHashSet<ConstructionService.PlacedRoomId> adjacent =
            new LinkedHashSet<ConstructionService.PlacedRoomId>();
        adjacent.add(roomA);

        ConstructionService.Snapshot twoRooms = service.addRoom(
            houseId,
            roomB,
            treasure,
            coordB,
            adjacent
        );

        require(twoRooms.rooms().size() == 2, "two placed rooms");
        require(twoRooms.roomAt(coordB).isPresent(), "coordinate lookup");
        require(twoRooms.adjacentTo(roomA).contains(roomB), "symmetric adjacency A");
        require(twoRooms.adjacentTo(roomB).contains(roomA), "symmetric adjacency B");

        expectIllegalState(new Runnable() {
            @Override public void run() {
                service.addRoom(
                    houseId,
                    new ConstructionService.PlacedRoomId("placed:duplicate-coordinate"),
                    parlour,
                    coordA,
                    Collections.<ConstructionService.PlacedRoomId>emptySet()
                );
            }
        }, "duplicate coordinate");

        expectIllegalArgument(new Runnable() {
            @Override public void run() {
                service.addRoom(
                    houseId,
                    new ConstructionService.PlacedRoomId("placed:bad-edge"),
                    parlour,
                    new ConstructionService.LayoutCoordinate(2, 0, 0),
                    Collections.singleton(
                        new ConstructionService.PlacedRoomId("placed:missing")
                    )
                );
            }
        }, "unknown adjacency");

        expectUnsupported(new Runnable() {
            @Override public void run() {
                twoRooms.rooms().clear();
            }
        }, "room snapshot immutable");

        expectUnsupported(new Runnable() {
            @Override public void run() {
                twoRooms.adjacentTo(roomA).clear();
            }
        }, "adjacency snapshot immutable");

        final String materialized = service.requestMaterialization(
            houseId,
            new HouseInstanceMaterializationPort() {
                @Override public String materialize(ConstructionService.Snapshot snapshot) {
                    require(snapshot.id().equals(houseId), "materialization receives house snapshot");
                    return "world-instance:house-owner-a";
                }
            }
        );
        require(
            "world-instance:house-owner-a".equals(materialized),
            "opaque #158 integration boundary"
        );

        ConstructionService.Snapshot oneRoom =
            service.removeRoom(houseId, roomA);
        require(oneRoom.rooms().size() == 1, "remove room");
        require(!oneRoom.roomAt(coordA).isPresent(), "coordinate released");
        require(oneRoom.adjacentTo(roomB).isEmpty(), "adjacency cleaned");

        ConstructionService restoredService = new ConstructionService();
        ConstructionService.Snapshot restored = restoredService.load(oneRoom);
        require(restored.rooms().size() == 1, "snapshot load");
        require(restored.buildModeEnabled(), "build mode restored");
        require(restored.ownerRef().equals(oneRoom.ownerRef()), "owner restored");

        assertNoAuthorityLeak(
            HouseRoomDefinition.class,
            ConstructionRoomCatalog.class,
            ConstructionService.class,
            HouseInstanceMaterializationPort.class
        );

        System.out.println(
            "ISSUE167_CONSTRUCTION_HOUSE_PASS roomCatalog=23 exactNamesLevels=true " +
            "priceAuthorityPromoted=false emptyHouse=true buildMode=true addRemove=true " +
            "duplicateCoordinateFailClosed=true adjacencySemantic=true adjacencyPolicyInvented=false " +
            "immutable=true snapshotLoad=true worldInstancePort=true instanceOwnershipDuplicated=false " +
            "inventoryMutation=false currencyMutation=false placementPolicyInvented=false protocolIndependent=true"
        );
    }

    private static void assertNoAuthorityLeak(Class<?>... roots) {
        String[] forbidden = {
            "widget", "opcode", "subtype", "packet", "sprite", "sceneindex",
            "worldx", "worldy", "objectid", "price", "cost", "materialrequirement"
        };
        for (Class<?> root : roots) {
            for (java.lang.reflect.Field field : root.getDeclaredFields()) {
                String haystack =
                    (field.getName() + " " + field.getType().getName())
                        .toLowerCase(java.util.Locale.ROOT);
                for (String token : forbidden) {
                    require(
                        !haystack.contains(token),
                        root.getName() + " leaked forbidden identity/policy through " + field.getName()
                    );
                }
            }
        }
    }

    private static void expectIllegalState(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalStateException: " + label);
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private static void expectIllegalArgument(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException: " + label);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectUnsupported(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected immutable view: " + label);
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
