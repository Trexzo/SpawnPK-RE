package spk.local;

import java.util.Objects;

/**
 * Immutable semantic room definition.
 *
 * displayedLevelRequirementEvidence is presentation evidence only. It is not an
 * authoritative build prerequisite and no price/material policy is stored here.
 */
final class HouseRoomDefinition {
    private final String key;
    private final String displayName;
    private final int displayedLevelRequirementEvidence;
    private final ConstructionEvidenceAuthority authority;

    HouseRoomDefinition(
        String key,
        String displayName,
        int displayedLevelRequirementEvidence,
        ConstructionEvidenceAuthority authority
    ) {
        this.key = requireKey(key);
        this.displayName = requireText(displayName, "displayName");
        if (displayedLevelRequirementEvidence < 0) {
            throw new IllegalArgumentException("displayed level must be non-negative");
        }
        this.displayedLevelRequirementEvidence = displayedLevelRequirementEvidence;
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    String key() { return key; }
    String displayName() { return displayName; }
    int displayedLevelRequirementEvidence() { return displayedLevelRequirementEvidence; }
    ConstructionEvidenceAuthority authority() { return authority; }

    private static String requireKey(String value) {
        String key = requireText(value, "key").toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean ok =
                c >= 'a' && c <= 'z' ||
                c >= '0' && c <= '9' ||
                c == '_' || c == '-' || c == ':';
            if (!ok) {
                throw new IllegalArgumentException("invalid semantic room key: " + value);
            }
        }
        return key;
    }

    private static String requireText(String value, String label) {
        if (value == null) throw new NullPointerException(label);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return normalized;
    }
}
