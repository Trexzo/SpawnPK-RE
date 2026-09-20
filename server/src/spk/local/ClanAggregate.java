package spk.local;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Protocol-independent durable clan aggregate.
 *
 * This models only semantic membership/rank/permission state proven necessary by
 * the exact-current clan surfaces. It deliberately does not define who is
 * authorized to mutate ranks or permissions; those production rules remain
 * server authority.
 */
final class ClanAggregate {
    enum Rank {
        RECRUIT,
        CORPORAL,
        SERGEANT,
        LIEUTENANT,
        CAPTAIN,
        GENERAL,
        OWNER
    }

    enum Permission {
        ENTER_CHAT,
        TALK_CHAT,
        KICK_OR_MUTE,
        BAN_CHAT
    }

    enum PermissionThreshold {
        ANYONE,
        FRIENDS,
        RECRUIT_PLUS,
        CORPORAL_PLUS,
        SERGEANT_PLUS,
        LIEUTENANT_PLUS,
        CAPTAIN_PLUS,
        GENERAL_PLUS,
        OWNER_ONLY
    }

    static final class ClanId {
        private final String value;

        ClanId(String value) {
            this.value = requireSemanticId(value, "clan id");
        }

        String value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ClanId && value.equals(((ClanId) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    static final class MemberId {
        private final String value;

        MemberId(String value) {
            this.value = requireSemanticId(value, "member id");
        }

        String value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MemberId && value.equals(((MemberId) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    static final class Snapshot {
        private final ClanId id;
        private final String name;
        private final MemberId owner;
        private final Map<MemberId, Rank> members;
        private final Map<Permission, PermissionThreshold> permissions;
        private final boolean generalHasCoOwnerPrivileges;
        private final ClanEvidenceAuthority definitionAuthority;

        private Snapshot(
            ClanId id,
            String name,
            MemberId owner,
            Map<MemberId, Rank> members,
            Map<Permission, PermissionThreshold> permissions,
            boolean generalHasCoOwnerPrivileges,
            ClanEvidenceAuthority definitionAuthority
        ) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.members = Collections.unmodifiableMap(new LinkedHashMap<MemberId, Rank>(members));
            this.permissions = Collections.unmodifiableMap(new EnumMap<Permission, PermissionThreshold>(permissions));
            this.generalHasCoOwnerPrivileges = generalHasCoOwnerPrivileges;
            this.definitionAuthority = definitionAuthority;
        }

        ClanId id() { return id; }
        String name() { return name; }
        MemberId owner() { return owner; }
        Map<MemberId, Rank> members() { return members; }
        Map<Permission, PermissionThreshold> permissions() { return permissions; }
        boolean generalHasCoOwnerPrivileges() { return generalHasCoOwnerPrivileges; }
        ClanEvidenceAuthority definitionAuthority() { return definitionAuthority; }
    }

    private final ClanId id;
    private String name;
    private final MemberId owner;
    private final LinkedHashMap<MemberId, Rank> members = new LinkedHashMap<MemberId, Rank>();
    private final EnumMap<Permission, PermissionThreshold> permissions =
        new EnumMap<Permission, PermissionThreshold>(Permission.class);
    private boolean generalHasCoOwnerPrivileges;
    private final ClanEvidenceAuthority definitionAuthority;

    ClanAggregate(
        ClanId id,
        String name,
        MemberId owner,
        Map<Permission, PermissionThreshold> initialPermissions,
        boolean generalHasCoOwnerPrivileges,
        ClanEvidenceAuthority definitionAuthority
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = requireName(name);
        this.owner = Objects.requireNonNull(owner, "owner");
        this.definitionAuthority = Objects.requireNonNull(definitionAuthority, "definitionAuthority");
        Objects.requireNonNull(initialPermissions, "initialPermissions");

        for (Permission permission : Permission.values()) {
            PermissionThreshold threshold = initialPermissions.get(permission);
            if (threshold == null) {
                throw new IllegalArgumentException("Missing permission threshold: " + permission);
            }
            permissions.put(permission, threshold);
        }

        if (initialPermissions.size() != Permission.values().length) {
            throw new IllegalArgumentException("Unexpected permission dimensions");
        }

        this.generalHasCoOwnerPrivileges = generalHasCoOwnerPrivileges;
        members.put(owner, Rank.OWNER);
    }

    ClanId id() {
        return id;
    }

    void rename(String nextName) {
        name = requireName(nextName);
    }

    void putMember(MemberId memberId, Rank rank) {
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(rank, "rank");
        if (owner.equals(memberId) && rank != Rank.OWNER) {
            throw new IllegalStateException("Clan owner rank cannot be downgraded");
        }
        if (!owner.equals(memberId) && rank == Rank.OWNER) {
            throw new IllegalArgumentException("Additional OWNER rank is not represented by this foundation");
        }
        members.put(memberId, rank);
    }

    void removeMember(MemberId memberId) {
        Objects.requireNonNull(memberId, "memberId");
        if (owner.equals(memberId)) {
            throw new IllegalStateException("Clan owner cannot be removed");
        }
        members.remove(memberId);
    }

    void setPermission(Permission permission, PermissionThreshold threshold) {
        permissions.put(
            Objects.requireNonNull(permission, "permission"),
            Objects.requireNonNull(threshold, "threshold")
        );
    }

    void setGeneralHasCoOwnerPrivileges(boolean enabled) {
        generalHasCoOwnerPrivileges = enabled;
    }

    Snapshot snapshot() {
        return new Snapshot(
            id,
            name,
            owner,
            members,
            permissions,
            generalHasCoOwnerPrivileges,
            definitionAuthority
        );
    }

    private static String requireSemanticId(String value, String label) {
        if (value == null) {
            throw new NullPointerException(label);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        return normalized;
    }

    private static String requireName(String value) {
        if (value == null) {
            throw new NullPointerException("name");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        return normalized;
    }
}
