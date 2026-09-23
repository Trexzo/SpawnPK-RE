package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class PlayerAppearanceRoleProjectionTest {
    private static final AtomicTransactionService.SourceAuthority POLICY=
        AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB;

    public static void main(String[] args){
        authorityFence();

        PlayerPrivilegeService privileges=
            new PlayerPrivilegeService(POLICY);
        PlayerPrivilegeService.Definition alpha=
            privileges.registerDefinition(
                new PlayerPrivilegeService.Definition(
                    "privilege:alpha",
                    "Alpha Privilege",
                    POLICY
                )
            );

        PlayerAppearanceRoleProjection projection=
            new PlayerAppearanceRoleProjection(POLICY);

        require(
            projection.project(
                privileges.snapshot("player:alice")
            )==0,
            "unassigned role default"
        );

        privileges.assign(
            "player:alice",
            alpha.privilegeKey
        );

        require(
            projection.project(
                privileges.snapshot("player:alice")
            )==0,
            "unmapped semantic privilege default"
        );

        projection.register(
            new PlayerAppearanceRoleProjection.Mapping(
                alpha.privilegeKey,
                45,
                POLICY
            )
        );

        require(
            projection.project(
                privileges.snapshot("player:alice")
            )==45,
            "mapped appearance role"
        );

        expect(
            IllegalStateException.class,
            ()->projection.register(
                new PlayerAppearanceRoleProjection.Mapping(
                    "PRIVILEGE:ALPHA",
                    46,
                    POLICY
                )
            ),
            "duplicate normalized mapping"
        );

        expect(
            IllegalArgumentException.class,
            ()->new PlayerAppearanceRoleProjection.Mapping(
                "privilege:too-high",
                32768,
                POLICY
            ),
            "role above signed-short range"
        );
        expect(
            IllegalArgumentException.class,
            ()->new PlayerAppearanceRoleProjection.Mapping(
                "privilege:too-low",
                -32769,
                POLICY
            ),
            "role below signed-short range"
        );

        projectionBoundary();

        System.out.println(
            "PLAYER_APPEARANCE_ROLE_PROJECTION_PASS "+
            "semanticPrivilegeInput=true "+
            "unassignedZero=true "+
            "unmappedZero=true "+
            "explicitCustomMapping=true "+
            "signedShortRole=true "+
            "namedSpawnPkMappingOwned=false "+
            "loginPrivilegeOwned=false "+
            "itemGateOwned=false "+
            "spriteOwned=false "+
            "packetOwned=false"
        );
    }

    private static void authorityFence(){
        expect(
            IllegalArgumentException.class,
            ()->new PlayerAppearanceRoleProjection(
                AtomicTransactionService.SourceAuthority.EXACT_CURRENT_CLIENT
            ),
            "exact client used as role-mapping policy"
        );
        expect(
            IllegalArgumentException.class,
            ()->new PlayerAppearanceRoleProjection(
                AtomicTransactionService.SourceAuthority.UNKNOWN_SERVER_AUTHORITY
            ),
            "unknown authority used as role-mapping policy"
        );

        PlayerAppearanceRoleProjection projection=
            new PlayerAppearanceRoleProjection(POLICY);
        expect(
            IllegalArgumentException.class,
            ()->projection.register(
                new PlayerAppearanceRoleProjection.Mapping(
                    "privilege:wrong-authority",
                    1,
                    AtomicTransactionService.SourceAuthority.EXACT_CURRENT_CLIENT
                )
            ),
            "mapping authority mismatch"
        );
    }

    private static void projectionBoundary(){
        for(Field field:
                PlayerAppearanceRoleProjection.class
                    .getDeclaredFields()){
            String name=
                field.getName().toLowerCase(Locale.ROOT);
            if(name.contains("ct")||
               name.contains("loginprivilege")||
               name.contains("itemid")||
               name.contains("partyhat")||
               name.contains("sprite")||
               name.contains("iconid"))
                throw new AssertionError(
                    "forbidden authority leaked into projection field "+
                    field.getName()
                );
        }

        require(
            "UNKNOWN_SERVER_AUTHORITY".equals(
                PlayerAppearanceRoleProjection
                    .NAMED_SPAWNPK_MAPPING_AUTHORITY
            ),
            "named mapping authority boundary"
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;
            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }
        throw new AssertionError(label+" did not fail");
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private PlayerAppearanceRoleProjectionTest(){}
}
