package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Protocol-independent semantic Construction/house aggregate.
 *
 * This service owns identity, build-mode state, placed rooms and caller-supplied
 * adjacency. It deliberately does not own prices, materials, placement legality,
 * orientation, furnishing rules, inventory mutation, or WorldInstance lifecycle.
 */
final class ConstructionService {
    static final class HouseId {
        private final String value;

        HouseId(String value) {
            this.value = requireId(value, "house id");
        }

        String value() { return value; }

        @Override public boolean equals(Object other) {
            return other instanceof HouseId && value.equals(((HouseId) other).value);
        }

        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    static final class PlacedRoomId {
        private final String value;

        PlacedRoomId(String value) {
            this.value = requireId(value, "placed room id");
        }

        String value() { return value; }

        @Override public boolean equals(Object other) {
            return other instanceof PlacedRoomId && value.equals(((PlacedRoomId) other).value);
        }

        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    static final class LayoutCoordinate {
        private final int x;
        private final int y;
        private final int level;

        LayoutCoordinate(int x, int y, int level) {
            this.x = x;
            this.y = y;
            this.level = level;
        }

        int x() { return x; }
        int y() { return y; }
        int level() { return level; }

        @Override public boolean equals(Object other) {
            if (!(other instanceof LayoutCoordinate)) return false;
            LayoutCoordinate value = (LayoutCoordinate) other;
            return x == value.x && y == value.y && level == value.level;
        }

        @Override public int hashCode() {
            int result = Integer.hashCode(x);
            result = 31 * result + Integer.hashCode(y);
            result = 31 * result + Integer.hashCode(level);
            return result;
        }

        @Override public String toString() {
            return x + ":" + y + ":" + level;
        }
    }

    static final class PlacedRoomSnapshot {
        private final PlacedRoomId id;
        private final HouseRoomDefinition definition;
        private final LayoutCoordinate coordinate;

        private PlacedRoomSnapshot(
            PlacedRoomId id,
            HouseRoomDefinition definition,
            LayoutCoordinate coordinate
        ) {
            this.id = id;
            this.definition = definition;
            this.coordinate = coordinate;
        }

        PlacedRoomId id() { return id; }
        HouseRoomDefinition definition() { return definition; }
        LayoutCoordinate coordinate() { return coordinate; }
    }

    static final class Snapshot {
        private final HouseId id;
        private final String ownerRef;
        private final boolean buildModeEnabled;
        private final ConstructionEvidenceAuthority authority;
        private final Map<PlacedRoomId, PlacedRoomSnapshot> rooms;
        private final Map<PlacedRoomId, Set<PlacedRoomId>> adjacency;

        private Snapshot(House house) {
            this.id = house.id;
            this.ownerRef = house.ownerRef;
            this.buildModeEnabled = house.buildModeEnabled;
            this.authority = house.authority;

            LinkedHashMap<PlacedRoomId, PlacedRoomSnapshot> roomCopy =
                new LinkedHashMap<PlacedRoomId, PlacedRoomSnapshot>();
            for (PlacedRoom room : house.rooms.values()) {
                roomCopy.put(
                    room.id,
                    new PlacedRoomSnapshot(room.id, room.definition, room.coordinate)
                );
            }
            this.rooms = Collections.unmodifiableMap(roomCopy);

            LinkedHashMap<PlacedRoomId, Set<PlacedRoomId>> adjacencyCopy =
                new LinkedHashMap<PlacedRoomId, Set<PlacedRoomId>>();
            for (Map.Entry<PlacedRoomId, LinkedHashSet<PlacedRoomId>> entry :
                    house.adjacency.entrySet()) {
                adjacencyCopy.put(
                    entry.getKey(),
                    Collections.unmodifiableSet(
                        new LinkedHashSet<PlacedRoomId>(entry.getValue())
                    )
                );
            }
            this.adjacency = Collections.unmodifiableMap(adjacencyCopy);
        }

        HouseId id() { return id; }
        String ownerRef() { return ownerRef; }
        boolean buildModeEnabled() { return buildModeEnabled; }
        ConstructionEvidenceAuthority authority() { return authority; }
        Map<PlacedRoomId, PlacedRoomSnapshot> rooms() { return rooms; }
        Map<PlacedRoomId, Set<PlacedRoomId>> adjacency() { return adjacency; }

        Optional<PlacedRoomSnapshot> roomAt(LayoutCoordinate coordinate) {
            Objects.requireNonNull(coordinate, "coordinate");
            for (PlacedRoomSnapshot room : rooms.values()) {
                if (room.coordinate().equals(coordinate)) {
                    return Optional.of(room);
                }
            }
            return Optional.empty();
        }

        Set<PlacedRoomId> adjacentTo(PlacedRoomId roomId) {
            Set<PlacedRoomId> values = adjacency.get(
                Objects.requireNonNull(roomId, "roomId")
            );
            return values == null
                ? Collections.<PlacedRoomId>emptySet()
                : values;
        }
    }

    private static final class PlacedRoom {
        private final PlacedRoomId id;
        private final HouseRoomDefinition definition;
        private final LayoutCoordinate coordinate;

        private PlacedRoom(
            PlacedRoomId id,
            HouseRoomDefinition definition,
            LayoutCoordinate coordinate
        ) {
            this.id = id;
            this.definition = definition;
            this.coordinate = coordinate;
        }
    }

    private static final class House {
        private final HouseId id;
        private final String ownerRef;
        private final ConstructionEvidenceAuthority authority;
        private boolean buildModeEnabled;
        private final LinkedHashMap<PlacedRoomId, PlacedRoom> rooms =
            new LinkedHashMap<PlacedRoomId, PlacedRoom>();
        private final LinkedHashMap<LayoutCoordinate, PlacedRoomId> occupied =
            new LinkedHashMap<LayoutCoordinate, PlacedRoomId>();
        private final LinkedHashMap<PlacedRoomId, LinkedHashSet<PlacedRoomId>> adjacency =
            new LinkedHashMap<PlacedRoomId, LinkedHashSet<PlacedRoomId>>();

        private House(
            HouseId id,
            String ownerRef,
            ConstructionEvidenceAuthority authority
        ) {
            this.id = id;
            this.ownerRef = ownerRef;
            this.authority = authority;
        }
    }

    private final Map<HouseId, House> houses =
        new LinkedHashMap<HouseId, House>();

    Snapshot createHouse(
        HouseId id,
        String ownerRef,
        ConstructionEvidenceAuthority authority
    ) {
        Objects.requireNonNull(id, "id");
        if (houses.containsKey(id)) {
            throw new IllegalStateException("House already exists: " + id);
        }

        House house = new House(
            id,
            requireRef(ownerRef),
            Objects.requireNonNull(authority, "authority")
        );
        houses.put(id, house);
        return new Snapshot(house);
    }

    Snapshot load(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (houses.containsKey(snapshot.id())) {
            throw new IllegalStateException("House already loaded: " + snapshot.id());
        }

        House house = new House(
            snapshot.id(),
            snapshot.ownerRef(),
            snapshot.authority()
        );
        house.buildModeEnabled = snapshot.buildModeEnabled();

        for (PlacedRoomSnapshot room : snapshot.rooms().values()) {
            addLoadedRoom(house, room);
        }

        for (Map.Entry<PlacedRoomId, Set<PlacedRoomId>> edge :
                snapshot.adjacency().entrySet()) {
            if (!house.rooms.containsKey(edge.getKey())) {
                throw new IllegalArgumentException("Adjacency references missing room: " + edge.getKey());
            }
            for (PlacedRoomId neighbor : edge.getValue()) {
                if (!house.rooms.containsKey(neighbor)) {
                    throw new IllegalArgumentException("Adjacency references missing room: " + neighbor);
                }
                if (edge.getKey().equals(neighbor)) {
                    throw new IllegalArgumentException("Room cannot be adjacent to itself");
                }
            }
        }

        for (Map.Entry<PlacedRoomId, Set<PlacedRoomId>> edge :
                snapshot.adjacency().entrySet()) {
            LinkedHashSet<PlacedRoomId> target = house.adjacency.get(edge.getKey());
            target.addAll(edge.getValue());
        }

        validateSymmetricAdjacency(house);
        houses.put(house.id, house);
        return new Snapshot(house);
    }

    Snapshot setBuildMode(HouseId id, boolean enabled) {
        House house = requireHouse(id);
        house.buildModeEnabled = enabled;
        return new Snapshot(house);
    }

    Snapshot addRoom(
        HouseId houseId,
        PlacedRoomId placedRoomId,
        HouseRoomDefinition definition,
        LayoutCoordinate coordinate,
        Set<PlacedRoomId> adjacentTo
    ) {
        House house = requireHouse(houseId);
        Objects.requireNonNull(placedRoomId, "placedRoomId");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(adjacentTo, "adjacentTo");

        if (house.rooms.containsKey(placedRoomId)) {
            throw new IllegalStateException("Placed room id already exists: " + placedRoomId);
        }
        if (house.occupied.containsKey(coordinate)) {
            throw new IllegalStateException("Layout coordinate already occupied: " + coordinate);
        }

        LinkedHashSet<PlacedRoomId> neighbors =
            new LinkedHashSet<PlacedRoomId>();
        for (PlacedRoomId neighbor : adjacentTo) {
            Objects.requireNonNull(neighbor, "adjacent room");
            if (placedRoomId.equals(neighbor)) {
                throw new IllegalArgumentException("Room cannot be adjacent to itself");
            }
            if (!house.rooms.containsKey(neighbor)) {
                throw new IllegalArgumentException("Adjacent room does not exist: " + neighbor);
            }
            neighbors.add(neighbor);
        }

        PlacedRoom room = new PlacedRoom(
            placedRoomId,
            definition,
            coordinate
        );
        house.rooms.put(placedRoomId, room);
        house.occupied.put(coordinate, placedRoomId);
        house.adjacency.put(placedRoomId, new LinkedHashSet<PlacedRoomId>());

        for (PlacedRoomId neighbor : neighbors) {
            house.adjacency.get(placedRoomId).add(neighbor);
            house.adjacency.get(neighbor).add(placedRoomId);
        }

        return new Snapshot(house);
    }

    Snapshot removeRoom(HouseId houseId, PlacedRoomId placedRoomId) {
        House house = requireHouse(houseId);
        Objects.requireNonNull(placedRoomId, "placedRoomId");
        PlacedRoom removed = house.rooms.remove(placedRoomId);
        if (removed == null) {
            throw new IllegalArgumentException("Unknown placed room: " + placedRoomId);
        }

        house.occupied.remove(removed.coordinate);
        house.adjacency.remove(placedRoomId);
        for (LinkedHashSet<PlacedRoomId> neighbors : house.adjacency.values()) {
            neighbors.remove(placedRoomId);
        }

        return new Snapshot(house);
    }

    Snapshot snapshot(HouseId id) {
        return new Snapshot(requireHouse(id));
    }

    String requestMaterialization(
        HouseId id,
        HouseInstanceMaterializationPort materializationPort
    ) {
        String instanceRef = Objects.requireNonNull(
            Objects.requireNonNull(materializationPort, "materializationPort")
                .materialize(snapshot(id)),
            "materialization result"
        ).trim();

        if (instanceRef.isEmpty()) {
            throw new IllegalStateException("materialization returned blank instance reference");
        }
        return instanceRef;
    }

    private static void addLoadedRoom(House house, PlacedRoomSnapshot snapshot) {
        if (house.rooms.containsKey(snapshot.id())) {
            throw new IllegalArgumentException("Duplicate placed room id: " + snapshot.id());
        }
        if (house.occupied.containsKey(snapshot.coordinate())) {
            throw new IllegalArgumentException("Duplicate occupied coordinate: " + snapshot.coordinate());
        }
        PlacedRoom room = new PlacedRoom(
            snapshot.id(),
            snapshot.definition(),
            snapshot.coordinate()
        );
        house.rooms.put(room.id, room);
        house.occupied.put(room.coordinate, room.id);
        house.adjacency.put(room.id, new LinkedHashSet<PlacedRoomId>());
    }

    private static void validateSymmetricAdjacency(House house) {
        for (Map.Entry<PlacedRoomId, LinkedHashSet<PlacedRoomId>> edge :
                house.adjacency.entrySet()) {
            for (PlacedRoomId neighbor : edge.getValue()) {
                Set<PlacedRoomId> reverse = house.adjacency.get(neighbor);
                if (reverse == null || !reverse.contains(edge.getKey())) {
                    throw new IllegalArgumentException(
                        "Adjacency graph is not symmetric: " + edge.getKey() + " -> " + neighbor
                    );
                }
            }
        }
    }

    private House requireHouse(HouseId id) {
        Objects.requireNonNull(id, "id");
        House house = houses.get(id);
        if (house == null) {
            throw new IllegalArgumentException("Unknown house: " + id);
        }
        return house;
    }

    private static String requireId(String value, String label) {
        if (value == null) throw new NullPointerException(label);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return normalized;
    }

    private static String requireRef(String value) {
        return requireId(value, "ownerRef");
    }
}
