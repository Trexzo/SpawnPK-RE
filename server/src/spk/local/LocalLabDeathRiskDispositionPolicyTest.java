package spk.local;

import java.util.Arrays;

public final class LocalLabDeathRiskDispositionPolicyTest {
    private static final int AUTO_KEEP=20466;
    private static final int AUTO_LOSS=24254;
    private static final int STANDARD=995;

    public static void main(String[] args){
        verifySafeHome();
        verifySafeConfiguredDestination();
        verifyPkRisk();

        System.out.println(
            "LOCAL_LAB_DEATH_RISK_DISPOSITION_PASS "+
            "homeStandardKept=true "+
            "nonPkDestinationStandardKept=true "+
            "pkStandardLost=true "+
            "autoKeepStable=true "+
            "autoLossStable=true "+
            "deathTileRegionAuthority=true "+
            "clientWidgetAuthority=false "+
            "originalSpawnPkRiskClaimed=false "+
            "authority="+
            LocalLabDeathDispositionPolicy.RISK_AUTHORITY
        );
    }

    private static void verifySafeHome(){
        WorldPlayer player=configuredPlayer();

        kill(
            player,
            10L,
            "safe-home"
        );

        PlayerDeathItemResolutionService.DeathPreview preview=
            preview(player);

        LocalLabDeathDispositionPolicy.Plan plan=
            new LocalLabDeathDispositionPolicy()
                .plan(preview);

        require(
            plan.riskClass==
                LocalLabDeathDispositionPolicy
                    .RiskClass
                    .SAFE_OR_UNCLASSIFIED,
            "HOME classified as PK risk"
        );
        require(
            LocalLabDeathDispositionPolicy
                .STANDARD_POLICY_SAFE
                .equals(plan.standardPolicy),
            "HOME standard policy"
        );
        require(
            kept(plan,preview,STANDARD)==100&&
            kept(plan,preview,AUTO_KEEP)==1&&
            kept(plan,preview,AUTO_LOSS)==0,
            "HOME item decisions"
        );
        require(
            plan.standardKeptLines==1&&
            plan.standardLostLines==0&&
            plan.autoKeptLines==1&&
            plan.explicitLostLines==1,
            "HOME counters"
        );
    }

    private static void verifySafeConfiguredDestination(){
        WorldPlayer player=configuredPlayer();

        LocalTeleportDestinationCatalog.Destination boss=
            LocalTeleportDestinationCatalog.get(
                TeleportNavigationService.EntryKind.BOSS
            );

        enterRegion(
            player,
            boss
        );

        kill(
            player,
            20L,
            "safe-boss"
        );

        PlayerDeathItemResolutionService.DeathPreview preview=
            preview(player);
        LocalLabDeathDispositionPolicy.Plan plan=
            new LocalLabDeathDispositionPolicy()
                .plan(preview);

        require(
            plan.deathRegionId==boss.regionId&&
            plan.riskClass==
                LocalLabDeathDispositionPolicy
                    .RiskClass
                    .SAFE_OR_UNCLASSIFIED&&
            kept(plan,preview,STANDARD)==100,
            "non-PK configured destination became risk"
        );
        require(
            kept(plan,preview,AUTO_KEEP)==1&&
            kept(plan,preview,AUTO_LOSS)==0,
            "explicit item policy drifted at BOSS"
        );
    }

    private static void verifyPkRisk(){
        WorldPlayer player=configuredPlayer();

        LocalTeleportDestinationCatalog.Destination pk=
            LocalTeleportDestinationCatalog.get(
                TeleportNavigationService.EntryKind.PK
            );

        enterRegion(
            player,
            pk
        );

        kill(
            player,
            30L,
            "pk-risk"
        );

        PlayerDeathItemResolutionService.DeathPreview preview=
            preview(player);
        LocalLabDeathDispositionPolicy.Plan plan=
            new LocalLabDeathDispositionPolicy()
                .plan(preview);

        require(
            plan.deathRegionId==pk.regionId&&
            WorldRegionAuthorityRepository
                .forTile(
                    preview.deathTile.x,
                    preview.deathTile.y
                ).regionId==pk.regionId,
            "PK death tile region mismatch"
        );
        require(
            plan.riskClass==
                LocalLabDeathDispositionPolicy
                    .RiskClass
                    .PK_RISK&&
            LocalLabDeathDispositionPolicy
                .STANDARD_POLICY_PK
                .equals(plan.standardPolicy),
            "PK risk classification"
        );
        require(
            kept(plan,preview,STANDARD)==0&&
            kept(plan,preview,AUTO_KEEP)==1&&
            kept(plan,preview,AUTO_LOSS)==0,
            "PK item decisions"
        );
        require(
            plan.standardKeptLines==0&&
            plan.standardLostLines==1&&
            plan.autoKeptLines==1&&
            plan.explicitLostLines==1,
            "PK counters"
        );
    }

    private static WorldPlayer configuredPlayer(){
        WorldPlayer player=new WorldPlayer();

        synchronized(player.mutationLock()){
            BankState.Stack[] bank=
                new BankState.Stack[
                    BankState.BANK_CAPACITY
                ];
            BankState.Stack[] inventory=
                new BankState.Stack[
                    BankState.INVENTORY_CAPACITY
                ];

            inventory[0]=new BankState.Stack(
                STANDARD,
                100
            );
            inventory[1]=new BankState.Stack(
                AUTO_KEEP,
                1
            );
            inventory[2]=new BankState.Stack(
                AUTO_LOSS,
                1
            );

            player.bank().restoreAccountState(
                bank,
                inventory,
                false
            );

            int[] equipmentItems=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            int[] equipmentQuantities=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            Arrays.fill(
                equipmentItems,
                -1
            );

            player.equipment().restoreAccountState(
                equipmentItems,
                equipmentQuantities
            );
        }

        require(
            DeathPolicyRepository.get(
                AUTO_KEEP
            ).kind==
                DeathPolicyRepository.Kind
                    .AUTO_KEEP_EXPLICIT&&
            DeathPolicyRepository.get(
                AUTO_LOSS
            ).kind==
                DeathPolicyRepository.Kind
                    .AUTO_LOSS_EXPLICIT&&
            DeathPolicyRepository.get(
                STANDARD
            ).kind==
                DeathPolicyRepository.Kind
                    .STANDARD_UNRESOLVED,
            "death-policy fixture drift"
        );

        return player;
    }

    private static void enterRegion(
        WorldPlayer player,
        LocalTeleportDestinationCatalog.Destination destination
    ){
        Tile tile=
            WorldCollisionAuthority.safeTile(
                destination.regionId,
                destination.plane
            );

        require(
            tile!=null,
            "destination safe tile missing"
        );

        int chunkX=tile.x>>3;
        int chunkY=tile.y>>3;

        player.movement().enterTransientRegion(
            tile.x,
            tile.y,
            tile.plane,
            (chunkX-6)<<3,
            (chunkY-6)<<3
        );
    }

    private static void kill(
        WorldPlayer player,
        long tick,
        String cause
    ){
        PlayerLifecycleService.DamageResult result=
            new PlayerLifecycleService(
                player,
                LocalLabDeathDispositionPolicy.AUTHORITY
            ).applyDamage(
                500,
                tick,
                cause,
                5L
            );

        require(
            result.died&&
            player.lifecycle().dead(),
            "death fixture"
        );
    }

    private static PlayerDeathItemResolutionService.DeathPreview
        preview(
            WorldPlayer player
        )
    {
        return new PlayerDeathItemResolutionService(
            player,
            LocalLabDeathDispositionPolicy.AUTHORITY
        ).previewCurrentDeath();
    }

    private static int kept(
        LocalLabDeathDispositionPolicy.Plan plan,
        PlayerDeathItemResolutionService.DeathPreview preview,
        int itemId
    ){
        int lineId=-1;

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried)
            if(line.itemId==itemId){
                if(lineId>=0)
                    throw new AssertionError(
                        "duplicate item fixture "+
                        itemId
                    );
                lineId=line.lineId;
            }

        if(lineId<0)
            throw new AssertionError(
                "missing item fixture "+
                itemId
            );

        for(PlayerDeathItemResolutionService.Decision decision:
                plan.decisions)
            if(decision.lineId==lineId)
                return decision.keptAmount;

        throw new AssertionError(
            "missing decision item "+
            itemId
        );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private LocalLabDeathRiskDispositionPolicyTest(){}
}
