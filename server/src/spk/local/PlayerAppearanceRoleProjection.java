package spk.local;

import java.util.*;

/**
 * Presentation-only projection from semantic player privilege state to the exact
 * packet-81 signed-short appearance-role field consumed as rs.a.k.aC.
 *
 * The original named SpawnPK rank -> numeric role table remains unknown. This
 * adapter therefore contains no built-in production mappings; callers may
 * register explicit CUSTOM_LOCALLAB mappings without leaking protocol values
 * into PlayerPrivilegeService.
 */
final class PlayerAppearanceRoleProjection {
    static final int DEFAULT_ROLE=0;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";
    static final String NAMED_SPAWNPK_MAPPING_AUTHORITY=
        "UNKNOWN_SERVER_AUTHORITY";

    static final class Mapping {
        final String privilegeKey;
        final int appearanceRole;
        final AtomicTransactionService.SourceAuthority sourceAuthority;

        Mapping(
            String privilegeKey,
            int appearanceRole,
            AtomicTransactionService.SourceAuthority sourceAuthority
        ){
            this.privilegeKey=normalizeKey(privilegeKey);
            if(appearanceRole<Short.MIN_VALUE||
               appearanceRole>Short.MAX_VALUE)
                throw new IllegalArgumentException(
                    "appearanceRole outside signed-short range: "+
                    appearanceRole
                );
            this.appearanceRole=appearanceRole;
            this.sourceAuthority=Objects.requireNonNull(
                sourceAuthority,
                "sourceAuthority"
            );
        }
    }

    private final AtomicTransactionService.SourceAuthority mappingAuthority;
    private final LinkedHashMap<String,Mapping> mappings=
        new LinkedHashMap<>();

    PlayerAppearanceRoleProjection(
        AtomicTransactionService.SourceAuthority mappingAuthority
    ){
        this.mappingAuthority=Objects.requireNonNull(
            mappingAuthority,
            "mappingAuthority"
        );
        if(mappingAuthority!=
                AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)
            throw new IllegalArgumentException(
                "appearance-role mapping requires CUSTOM_LOCALLAB authority actual="+
                mappingAuthority
            );
    }

    synchronized Mapping register(Mapping mapping){
        Mapping checked=Objects.requireNonNull(mapping,"mapping");
        if(checked.sourceAuthority!=mappingAuthority)
            throw new IllegalArgumentException(
                "appearance-role mapping authority mismatch key="+
                checked.privilegeKey+
                " actual="+checked.sourceAuthority+
                " expected="+mappingAuthority
            );
        if(mappings.containsKey(checked.privilegeKey))
            throw new IllegalStateException(
                "duplicate appearance-role mapping "+
                checked.privilegeKey
            );
        mappings.put(checked.privilegeKey,checked);
        return checked;
    }

    synchronized int project(
        PlayerPrivilegeService.PlayerSnapshot privilege
    ){
        if(privilege==null||!privilege.assigned())
            return DEFAULT_ROLE;

        if(privilege.policyAuthority!=mappingAuthority)
            throw new IllegalStateException(
                "privilege/mapping authority mismatch player="+
                privilege.playerRef+
                " actual="+privilege.policyAuthority+
                " expected="+mappingAuthority
            );

        Mapping mapping=
            mappings.get(privilege.privilege.privilegeKey);
        return mapping==null
            ?DEFAULT_ROLE
            :mapping.appearanceRole;
    }

    synchronized int mappingCount(){
        return mappings.size();
    }

    AtomicTransactionService.SourceAuthority mappingAuthority(){
        return mappingAuthority;
    }

    private static String normalizeKey(String value){
        if(value==null)
            throw new NullPointerException("privilegeKey");
        String clean=value.trim().toLowerCase(Locale.ROOT);
        if(clean.isEmpty())
            throw new IllegalArgumentException("privilegeKey blank");
        if(clean.length()>160)
            throw new IllegalArgumentException("privilegeKey too long");
        return clean;
    }
}
