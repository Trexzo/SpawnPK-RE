package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Exact-current client room label + displayed-level evidence.
 *
 * Deliberately omits both conflicting client price representations. Actual
 * server build cost/material/prerequisite policy remains unknown authority.
 */
final class ConstructionRoomCatalog {
    private final Map<String, HouseRoomDefinition> byKey;
    private final List<HouseRoomDefinition> ordered;

    private ConstructionRoomCatalog(List<HouseRoomDefinition> definitions) {
        LinkedHashMap<String, HouseRoomDefinition> copy =
            new LinkedHashMap<String, HouseRoomDefinition>();
        ArrayList<HouseRoomDefinition> order =
            new ArrayList<HouseRoomDefinition>();

        for (HouseRoomDefinition definition : definitions) {
            if (copy.put(definition.key(), definition) != null) {
                throw new IllegalArgumentException("duplicate room key: " + definition.key());
            }
            order.add(definition);
        }

        this.byKey = Collections.unmodifiableMap(copy);
        this.ordered = Collections.unmodifiableList(order);
    }

    static ConstructionRoomCatalog exactCurrentClientEvidence() {
        ArrayList<HouseRoomDefinition> rooms = new ArrayList<HouseRoomDefinition>();
        add(rooms, "parlour", "Parlour", 1);
        add(rooms, "garden", "Garden", 1);
        add(rooms, "kitchen", "Kitchen", 5);
        add(rooms, "dining_room", "Dining room", 10);
        add(rooms, "workshop", "Workshop", 15);
        add(rooms, "bedroom", "Bedroom", 20);
        add(rooms, "hall_skill_trophies", "Hall - Skill Trophies", 25);
        add(rooms, "games_room", "Games Room", 30);
        add(rooms, "combat_room", "Combat room", 32);
        add(rooms, "hall_quest_trophies", "Hall - Quest trophies", 35);
        add(rooms, "menagerie", "Menagerie", 37);
        add(rooms, "study", "Study", 40);
        add(rooms, "costume_room", "Costume room", 42);
        add(rooms, "chapel", "Chapel", 45);
        add(rooms, "boss_portal_room", "Boss portal room", 50);
        add(rooms, "formal_garden", "Formal garden", 55);
        add(rooms, "throne_room", "Throne room", 60);
        add(rooms, "superior_garden", "Superior garden", 65);
        add(rooms, "dungeon_corridor", "Dungeon - corridor", 70);
        add(rooms, "dungeon_junction", "Dungeon - junction", 70);
        add(rooms, "dungeon_stairs", "Dungeon - stairs", 70);
        add(rooms, "dungeon_pit", "Dungeon - pit", 70);
        add(rooms, "treasure_room", "Treasure room", 75);
        return new ConstructionRoomCatalog(rooms);
    }

    List<HouseRoomDefinition> definitions() {
        return ordered;
    }

    Optional<HouseRoomDefinition> find(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(
            byKey.get(key.trim().toLowerCase(java.util.Locale.ROOT))
        );
    }

    int size() {
        return ordered.size();
    }

    private static void add(
        List<HouseRoomDefinition> out,
        String key,
        String name,
        int displayedLevel
    ) {
        out.add(
            new HouseRoomDefinition(
                key,
                name,
                displayedLevel,
                ConstructionEvidenceAuthority.EXACT_CURRENT_CLIENT
            )
        );
    }
}
