package spk.local;

import java.util.Objects;

interface PlayerPvpEligibilityPolicy {
    final class Result {
        final boolean eligible;
        final String detail;

        Result(
            boolean eligible,
            String detail
        ){
            this.eligible=eligible;
            this.detail=Objects.requireNonNull(
                detail,
                "detail"
            );
        }
    }

    Result evaluate(
        WorldPlayer attacker,
        WorldPlayer target
    );

    static PlayerPvpEligibilityPolicy allowAllTestSeam(){
        return (attacker,target)->
            new Result(
                true,
                "ALLOW_ALL_TEST_SEAM"
            );
    }
}

final class LocalLabPvpRegionPolicy
    implements PlayerPvpEligibilityPolicy {

    static final LocalLabPvpRegionPolicy INSTANCE=
        new LocalLabPvpRegionPolicy();

    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_PK_REGION_PVP_V1";

    @Override public Result evaluate(
        WorldPlayer attacker,
        WorldPlayer target
    ){
        WorldPlayer checkedAttacker=
            Objects.requireNonNull(
                attacker,
                "attacker"
            );
        WorldPlayer checkedTarget=
            Objects.requireNonNull(
                target,
                "target"
            );

        LocalTeleportDestinationCatalog.Destination pk=
            LocalTeleportDestinationCatalog.get(
                TeleportNavigationService.EntryKind.PK
            );

        if(pk==null)
            throw new IllegalStateException(
                "LocalLab PK destination is not configured"
            );

        MovementState attackerMovement=
            checkedAttacker.movement();
        MovementState targetMovement=
            checkedTarget.movement();

        int attackerRegion=
            regionId(
                attackerMovement.x(),
                attackerMovement.y()
            );
        int targetRegion=
            regionId(
                targetMovement.x(),
                targetMovement.y()
            );

        boolean attackerEligible=
            attackerRegion==pk.regionId&&
            attackerMovement.plane()==pk.plane;

        boolean targetEligible=
            targetRegion==pk.regionId&&
            targetMovement.plane()==pk.plane;

        boolean eligible=
            attackerEligible&&targetEligible;

        return new Result(
            eligible,
            "authority="+AUTHORITY+
            " requiredRegion="+pk.regionId+
            " requiredPlane="+pk.plane+
            " attackerRegion="+attackerRegion+
            " attackerPlane="+attackerMovement.plane()+
            " targetRegion="+targetRegion+
            " targetPlane="+targetMovement.plane()
        );
    }

    private static int regionId(
        int x,
        int y
    ){
        WorldRegionAuthorityRepository.Region region=
            WorldRegionAuthorityRepository.forTile(
                x,
                y
            );

        return region==null
            ?-1
            :region.regionId;
    }

    private LocalLabPvpRegionPolicy(){}
}
