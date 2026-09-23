package spk.local;

import java.util.*;

/**
 * Exact-v308 definition-authoring boundary.
 *
 * Clone source selection is a distinct pre-pass. Unknown/no-op keys are rejected rather
 * than silently serialized into a cache override.
 */
final class CustomDefinitionOverlayPolicy {
    enum Kind { ITEM, NPC }

    private static final Set<String> ITEM_FIELDS = Collections.unmodifiableSet(
        new LinkedHashSet<>(Arrays.asList(
            "name", "clone", "fullClone", "osrs", "modelId", "actions",
            "maleModels", "femaleModels", "srcColors", "destColors", "retextures",
            "zoom", "rotations", "offsets", "resize", "ambient", "contrast",
            "hover", "icon", "iconX", "iconY", "stackable", "textureInvAnim",
            "fullTexture", "broken", "zan2d"
        ))
    );

    private static final Set<String> NPC_FIELDS = Collections.unmodifiableSet(
        new LinkedHashSet<>(Arrays.asList(
            "name", "clone", "osrs", "models", "standAnim", "walkAnim",
            "rotateAnim", "rotateAnim180", "rotateAnim90CW", "rotateAnim90CCW",
            "pet", "size", "scaleHeight", "scaleWidth", "srcColors", "destColors",
            "retextures", "actions", "minimap", "combatLevel", "chatHeadModels",
            "ambient", "contrast", "priorityRender", "clickable", "renderIdle",
            "rotationSpeed"
        ))
    );

    private static final Set<String> INTEGER_FIELDS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "clone", "fullClone", "modelId", "zoom", "ambient", "contrast",
            "icon", "iconX", "iconY", "fullTexture", "zan2d",
            "standAnim", "walkAnim", "rotateAnim", "rotateAnim180",
            "rotateAnim90CW", "rotateAnim90CCW", "size", "scaleHeight",
            "scaleWidth", "combatLevel", "rotationSpeed"
        ))
    );

    private static final Set<String> BOOLEAN_FIELDS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "osrs", "stackable", "textureInvAnim", "broken", "pet", "minimap",
            "priorityRender", "clickable", "renderIdle"
        ))
    );

    private static final Set<String> INTEGER_LIST_FIELDS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "models", "maleModels", "femaleModels", "srcColors", "destColors",
            "retextures", "resize", "rotations", "offsets", "chatHeadModels"
        ))
    );

    private CustomDefinitionOverlayPolicy() {}

    static void validateField(Kind kind, String field, String value) {
        if (field == null || field.isEmpty())
            throw new IllegalArgumentException("definition field");
        if (value == null)
            throw new IllegalArgumentException("definition value");

        if (field.startsWith("param_"))
            throw new IllegalArgumentException(
                "exact v308 skips param_* before active item-field handling: " + field
            );
        if (kind == Kind.ITEM && ("equipClone".equals(field) || "cloneEquip".equals(field)))
            throw new IllegalArgumentException(
                "exact v308 does not use " + field + " as an item clone pre-pass key"
            );

        Set<String> allowed = kind == Kind.ITEM ? ITEM_FIELDS : NPC_FIELDS;
        if (!allowed.contains(field))
            throw new IllegalArgumentException(
                "unknown or unsupported exact-v308 " + kind + " definition field: " + field
            );

        if (INTEGER_FIELDS.contains(field)) {
            int parsed = parseInt(value, field);
            if (("clone".equals(field) || "fullClone".equals(field)) && parsed < 0)
                throw new IllegalArgumentException(field + " source id must be non-negative");
        } else if (BOOLEAN_FIELDS.contains(field)) {
            if (!"true".equals(value) && !"false".equals(value))
                throw new IllegalArgumentException(field + " must be true/false");
        } else if (INTEGER_LIST_FIELDS.contains(field)) {
            parseIntegerList(value, field);
        } else if ("actions".equals(field)) {
            parseActionList(value);
        }
    }

    static int itemCloneSource(Map<String,String> fields) {
        if (fields.containsKey("fullClone"))
            return parseInt(fields.get("fullClone"), "fullClone");
        if (fields.containsKey("clone"))
            return parseInt(fields.get("clone"), "clone");
        return -1;
    }

    static int npcCloneSource(Map<String,String> fields) {
        if (fields.containsKey("clone"))
            return parseInt(fields.get("clone"), "clone");
        return -1;
    }

    static boolean itemEffectiveOsrs(Map<String,String> fields, boolean sourceOsrs) {
        String explicit = fields.get("osrs");
        return explicit == null ? sourceOsrs : Boolean.parseBoolean(explicit);
    }

    static List<String> exactApplicationPhases(Kind kind, Map<String,String> fields) {
        ArrayList<String> phases = new ArrayList<>();
        int source = kind == Kind.ITEM ? itemCloneSource(fields) : npcCloneSource(fields);
        if (source >= 0)
            phases.add("RESOLVE_SOURCE:" + source);
        phases.add("COPY_SOURCE_THEN_RESTORE_TARGET_ID");
        phases.add("APPLY_CURRENT_FIELDS");
        phases.add("LOADER_POST_PROCESSING");
        return Collections.unmodifiableList(phases);
    }

    private static int parseInt(String value, String field) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(field + " must be integer: " + value, e);
        }
    }

    private static void parseIntegerList(String value, String field) {
        String trimmed = value.trim();
        if (trimmed.isEmpty())
            throw new IllegalArgumentException(field + " integer list is empty");
        String[] parts = trimmed.split(",");
        for (String part : parts)
            parseInt(part, field);
    }

    private static void parseActionList(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 5)
            throw new IllegalArgumentException(
                "actions must contain exactly five inventory slots"
            );
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty())
                throw new IllegalArgumentException("actions slot must be null or text");
            if ("null".equals(token)) continue;
            if (token.indexOf(',') >= 0 || token.indexOf('\t') >= 0 ||
                token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0)
                throw new IllegalArgumentException("invalid actions token: " + token);
        }
    }
}
