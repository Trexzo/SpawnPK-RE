package spk.local;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Read-only semantic NPC catalog query boundary. */
final class NpcCatalogQueryService {
    static final class NpcSummary {
        private final int npcId;
        private final String name;
        private final Integer combatLevel;
        private final Integer size;
        private final List<String> actions;
        private final CatalogEvidenceAuthority authority;

        NpcSummary(
            int npcId,
            String name,
            Integer combatLevel,
            Integer size,
            List<String> actions,
            CatalogEvidenceAuthority authority
        ) {
            if (npcId < 0) throw new IllegalArgumentException("npcId must be non-negative");
            this.npcId = npcId;
            this.name = requireText(name, "name");
            if (combatLevel != null && combatLevel.intValue() < 0) {
                throw new IllegalArgumentException("combatLevel must be non-negative");
            }
            if (size != null && size.intValue() <= 0) {
                throw new IllegalArgumentException("size must be positive");
            }
            this.combatLevel = combatLevel;
            this.size = size;
            Objects.requireNonNull(actions, "actions");
            ArrayList<String> actionCopy = new ArrayList<String>();
            for (String action : actions) {
                actionCopy.add(requireText(action, "action"));
            }
            this.actions = Collections.unmodifiableList(actionCopy);
            this.authority = Objects.requireNonNull(authority, "authority");
        }

        int npcId() { return npcId; }
        String name() { return name; }
        Optional<Integer> combatLevel() { return Optional.ofNullable(combatLevel); }
        Optional<Integer> size() { return Optional.ofNullable(size); }
        List<String> actions() { return actions; }
        CatalogEvidenceAuthority authority() { return authority; }
    }

    private final Map<Integer, NpcSummary> byId;

    NpcCatalogQueryService(Collection<NpcSummary> summaries) {
        Objects.requireNonNull(summaries, "summaries");
        LinkedHashMap<Integer, NpcSummary> copy = new LinkedHashMap<Integer, NpcSummary>();
        ArrayList<NpcSummary> ordered = new ArrayList<NpcSummary>();
        for (NpcSummary summary : summaries) {
            ordered.add(Objects.requireNonNull(summary, "summary"));
        }
        ordered.sort(Comparator.comparingInt(NpcSummary::npcId));
        for (NpcSummary summary : ordered) {
            if (copy.put(Integer.valueOf(summary.npcId()), summary) != null) {
                throw new IllegalArgumentException("Duplicate NPC id: " + summary.npcId());
            }
        }
        this.byId = Collections.unmodifiableMap(copy);
    }

    Optional<NpcSummary> findById(int npcId) {
        return Optional.ofNullable(byId.get(Integer.valueOf(npcId)));
    }

    QueryPage<NpcSummary> searchByName(String substring, int offset, int limit) {
        String needle = requireText(substring, "substring").toLowerCase(Locale.ROOT);
        ArrayList<NpcSummary> matches = new ArrayList<NpcSummary>();
        for (NpcSummary summary : byId.values()) {
            if (summary.name().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(summary);
            }
        }
        return QueryPage.slice(matches, offset, limit);
    }

    private static String requireText(String value, String label) {
        if (value == null) throw new NullPointerException(label);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return normalized;
    }
}
