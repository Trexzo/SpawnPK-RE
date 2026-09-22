package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class PlayerPrivilegeServiceTest {
    private static final AtomicTransactionService.SourceAuthority
        POLICY=
            AtomicTransactionService
                .SourceAuthority
                .CUSTOM_LOCALLAB;

    public static void main(String[] args){
        authorityFence();

        PlayerPrivilegeService service=
            new PlayerPrivilegeService(
                POLICY
            );

        PlayerPrivilegeService.Definition alpha=
            new PlayerPrivilegeService.Definition(
                "privilege:alpha",
                "Alpha Privilege",
                POLICY
            );

        PlayerPrivilegeService.Definition beta=
            new PlayerPrivilegeService.Definition(
                "privilege:beta",
                "Beta Privilege",
                POLICY
            );

        service.registerDefinition(alpha);
        service.registerDefinition(beta);

        require(
            service.definitionCount()==2&&
            service.catalog().definitions
                .equals(
                    Arrays.asList(
                        alpha,
                        beta
                    )
                ),
            "privilege catalog registration order"
        );

        expect(
            UnsupportedOperationException.class,
            ()->service.catalog()
                .definitions.clear(),
            "privilege catalog mutable"
        );

        expect(
            IllegalStateException.class,
            ()->service.registerDefinition(
                new PlayerPrivilegeService
                    .Definition(
                        "PRIVILEGE:ALPHA",
                        "Duplicate",
                        POLICY
                    )
            ),
            "duplicate privilege definition"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.registerDefinition(
                new PlayerPrivilegeService
                    .Definition(
                        "privilege:wrong",
                        "Wrong",
                        AtomicTransactionService
                            .SourceAuthority
                            .EXACT_CURRENT_CLIENT
                    )
            ),
            "exact-client privilege definition used as gameplay policy"
        );

        PlayerPrivilegeService.PlayerSnapshot
            empty=
                service.snapshot(
                    " Player:Alice "
                );

        require(
            "player:alice".equals(
                empty.playerRef
            )&&
            !empty.assigned()&&
            empty.revision==0L&&
            service.playerStateCount()==0,
            "read-only privilege snapshot"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.assign(
                "player:alice",
                "privilege:missing"
            ),
            "unknown privilege assignment"
        );

        require(
            service.playerStateCount()==0,
            "failed privilege assignment created player state"
        );

        PlayerPrivilegeService.PlayerSnapshot
            first=
                service.assign(
                    "PLAYER:ALICE",
                    "PRIVILEGE:ALPHA"
                );

        require(
            first.assigned()&&
            first.privilege==alpha&&
            first.revision==1L&&
            first.policyAuthority==POLICY&&
            PlayerPrivilegeService
                .PRESENTATION_AUTHORITY
                .equals(
                    first
                        .presentationAuthority
                ),
            "first privilege assignment"
        );

        PlayerPrivilegeService.PlayerSnapshot
            retry=
                service.assign(
                    "player:alice",
                    "privilege:alpha"
                );

        require(
            retry.privilege==alpha&&
            retry.revision==1L,
            "same privilege assignment idempotent"
        );

        PlayerPrivilegeService.PlayerSnapshot
            changed=
                service.assign(
                    "player:alice",
                    "privilege:beta"
                );

        require(
            changed.privilege==beta&&
            changed.revision==2L,
            "privilege assignment change"
        );

        PlayerPrivilegeService.PlayerSnapshot
            bob=
                service.assign(
                    "player:bob",
                    "privilege:alpha"
                );

        require(
            bob.privilege==alpha&&
            bob.revision==1L&&
            service.snapshot(
                "player:alice"
            ).privilege==beta&&
            service.playerStateCount()==2,
            "privilege player isolation"
        );

        PlayerPrivilegeService.PlayerSnapshot
            cleared=
                service.clear(
                    "player:alice"
                );

        require(
            !cleared.assigned()&&
            cleared.revision==3L,
            "privilege clear"
        );

        PlayerPrivilegeService.PlayerSnapshot
            clearRetry=
                service.clear(
                    "player:alice"
                );

        require(
            !clearRetry.assigned()&&
            clearRetry.revision==3L,
            "privilege clear idempotent"
        );

        PlayerPrivilegeService.PlayerSnapshot
            absentClear=
                service.clear(
                    "player:charlie"
                );

        require(
            !absentClear.assigned()&&
            absentClear.revision==0L&&
            service.playerStateCount()==2,
            "absent privilege clear no state creation"
        );

        clientMappingBoundary();
        stateBoundary();

        System.out.println(
            "PLAYER_PRIVILEGE_SERVICE_PASS "+
            "semanticDefinitions=true "+
            "normalizedPlayers=true "+
            "onePrivilegePerPlayer=true "+
            "revisionedAssignment=true "+
            "sameAssignmentIdempotent=true "+
            "clearIdempotent=true "+
            "playerIsolation=true "+
            "numericAcMappingOwned=false "+
            "namedSpawnPkRankMappingOwned=false "+
            "permissionPolicyOwned=false "+
            "packet81Owned=false "+
            "cosmeticBsOwned=false "+
            "equipmentOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void authorityFence(){
        expect(
            IllegalArgumentException.class,
            ()->new PlayerPrivilegeService(
                AtomicTransactionService
                    .SourceAuthority
                    .EXACT_CURRENT_CLIENT
            ),
            "exact client used as privilege gameplay policy"
        );

        expect(
            IllegalArgumentException.class,
            ()->new PlayerPrivilegeService(
                AtomicTransactionService
                    .SourceAuthority
                    .UNKNOWN_SERVER_AUTHORITY
            ),
            "unknown authority used as privilege gameplay policy"
        );
    }

    private static void clientMappingBoundary(){
        for(Class<?> type:new Class<?>[]{
                PlayerPrivilegeService.class,
                PlayerPrivilegeService
                    .Definition.class,
                PlayerPrivilegeService
                    .PlayerSnapshot.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.equals("ac")||
                   name.contains("clientcode")||
                   name.contains("numericrank")||
                   name.contains("sprite")||
                   name.contains("iconid")||
                   name.contains("itemid")||
                   name.contains("widget")||
                   name.contains("opcode")||
                   name.contains("packet"))
                    throw new AssertionError(
                        "client mapping leaked into privilege domain "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void stateBoundary(){
        for(Method method:
                PlayerPrivilegeService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("serialize")||
               name.contains("publish")||
               name.contains("equip")||
               name.contains("cosmetic")||
               name.contains("permission")||
               name.contains("persist"))
                throw new AssertionError(
                    "presentation/permission behavior leaked into privilege service "+
                    method.getName()
                );
        }
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
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private PlayerPrivilegeServiceTest(){}
}
