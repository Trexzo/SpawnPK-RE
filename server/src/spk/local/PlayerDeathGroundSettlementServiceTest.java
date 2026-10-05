package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Regression for player-death carried loss -> owner-scoped ground settlement. */
public final class PlayerDeathGroundSettlementServiceTest {
    public static void main(String[] args){
        riskLossSettlesExactlyOnce();
        safeKeepAllCreatesNoGround();
        overflowFailureLeavesCarriedUntouched();
        groundRollbackRestoresPostimage();

        System.out.println(
            "PLAYER_DEATH_GROUND_SETTLEMENT_PASS "+
            "partialLoss=true "+
            "equipmentLoss=true "+
            "ownerScoped=true "+
            "idempotent=true "+
            "safeKeepAll=true "+
            "overflowAtomic=true "+
            "groundRollback=true "+
            "authority="+
            PlayerDeathGroundSettlementService.SETTLEMENT_AUTHORITY
        );
    }

    private static void riskLossSettlesExactlyOnce(){
        World world=World.isolatedForTest(600L);
        WorldPlayer player=configuredPlayer();
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(player);

        require(
            lifecycle.applyDamage(
                500,
                500L,
                "PVP_RISK",
                5L
            ).died,
            "risk death"
        );

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                "CUSTOM_LOCALLAB_RISK_TEST"
            );
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolver.previewCurrentDeath();

        List<PlayerDeathItemResolutionService.Decision> decisions=
            new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried){
            int keep=line.quantity;

            if(line.itemId==995)
                keep=40;
            else if(line.itemId==4151)
                keep=0;

            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    keep
                )
            );
        }

        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                decisions
            );

        Tile deathTile=
            new Tile(
                3210,
                3210,
                0
            );

        PlayerDeathGroundSettlementService settlement=
            new PlayerDeathGroundSettlementService(
                world,
                player
            );

        PlayerDeathGroundSettlementService.Receipt first=
            settlement.settle(
                resolution,
                deathTile,
                "attacker"
            );

        PlayerDeathGroundSettlementService.Receipt replay=
            settlement.settle(
                resolution,
                deathTile,
                "attacker"
            );

        require(
            first==replay&&
            settlement.size()==1&&
            first.keptQuantity==40&&
            first.lostQuantity==61,
            "risk receipt"
        );

        BankState.Stack coins=
            player.bank()
                .inventoryAt(0);

        require(
            coins!=null&&
            coins.itemId==995&&
            coins.qty==40,
            "partial inventory loss"
        );
        require(
            player.equipment()
                .itemAt(
                    EquipmentSlot.WEAPON
                        .equipmentIndex
                )<0&&
            player.equipment()
                .quantityAt(
                    EquipmentSlot.WEAPON
                        .equipmentIndex
                )==0,
            "equipment loss"
        );

        GroundItem groundCoins=
            world.groundItems()
                .findOwned(
                    995,
                    deathTile.x,
                    deathTile.y,
                    deathTile.plane,
                    "attacker"
                );
        GroundItem groundWhip=
            world.groundItems()
                .findOwned(
                    4151,
                    deathTile.x,
                    deathTile.y,
                    deathTile.plane,
                    "attacker"
                );

        require(
            groundCoins!=null&&
            groundCoins.amount==60&&
            groundWhip!=null&&
            groundWhip.amount==1,
            "owner-scoped ground loss"
        );

        expect(
            IllegalStateException.class,
            ()->settlement.settle(
                resolution,
                new Tile(
                    3211,
                    3210,
                    0
                ),
                "attacker"
            ),
            "conflicting replay"
        );
    }

    private static void safeKeepAllCreatesNoGround(){
        World world=World.isolatedForTest(600L);
        WorldPlayer player=configuredPlayer();
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(player);

        require(
            lifecycle.applyDamage(
                500,
                600L,
                "SAFE",
                5L
            ).died,
            "safe death"
        );

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                "CUSTOM_LOCALLAB_SAFE_TEST"
            );
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolver.previewCurrentDeath();

        ArrayList<PlayerDeathItemResolutionService.Decision> decisions=
            new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried)
            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    line.quantity
                )
            );

        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                decisions
            );

        PlayerDeathGroundSettlementService.Receipt receipt=
            new PlayerDeathGroundSettlementService(
                world,
                player
            ).settle(
                resolution,
                new Tile(
                    3200,
                    3200,
                    0
                ),
                "self"
            );

        require(
            receipt.lostQuantity==0&&
            receipt.groundMutations.isEmpty()&&
            world.groundItems().size()==0&&
            player.bank().inventoryAt(0).qty==100&&
            player.equipment().itemAt(
                EquipmentSlot.WEAPON
                    .equipmentIndex
            )==4151,
            "safe keep-all settlement"
        );
    }

    private static void overflowFailureLeavesCarriedUntouched(){
        World world=World.isolatedForTest(600L);
        WorldPlayer player=configuredPlayer();
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(player);
        Tile tile=
            new Tile(
                3300,
                3300,
                0
            );

        world.groundItems().add(
            995,
            Integer.MAX_VALUE,
            tile,
            "attacker",
            699L,
            false
        );

        require(
            lifecycle.applyDamage(
                500,
                700L,
                "OVERFLOW",
                5L
            ).died,
            "overflow death"
        );

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                "CUSTOM_LOCALLAB_OVERFLOW_TEST"
            );
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolver.previewCurrentDeath();

        ArrayList<PlayerDeathItemResolutionService.Decision> decisions=
            new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried)
            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    line.itemId==995
                        ?0
                        :line.quantity
                )
            );

        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                decisions
            );

        PlayerDeathGroundSettlementService settlement=
            new PlayerDeathGroundSettlementService(
                world,
                player
            );

        expect(
            IllegalStateException.class,
            ()->settlement.settle(
                resolution,
                tile,
                "attacker"
            ),
            "ground overflow"
        );

        require(
            settlement.size()==0&&
            player.bank().inventoryAt(0)!=null&&
            player.bank().inventoryAt(0).qty==100&&
            player.equipment().itemAt(
                EquipmentSlot.WEAPON
                    .equipmentIndex
            )==4151&&
            world.groundItems()
                .findOwned(
                    995,
                    tile.x,
                    tile.y,
                    tile.plane,
                    "attacker"
                ).amount==
                    Integer.MAX_VALUE,
            "overflow failure atomicity"
        );
    }

    private static void groundRollbackRestoresPostimage(){
        GroundItemRegistry ground=
            new GroundItemRegistry();
        Tile tile=
            new Tile(
                3400,
                3400,
                0
            );

        GroundItem existing=
            ground.add(
                995,
                25,
                tile,
                "owner",
                1L,
                false
            );

        List<GroundItemRegistry.BatchMutation> mutations=
            ground.addBatchDetailed(
                Arrays.asList(
                    new GroundItemRegistry.AddRequest(
                        995,
                        5,
                        tile,
                        "owner",
                        2L,
                        false
                    ),
                    new GroundItemRegistry.AddRequest(
                        4151,
                        1,
                        tile,
                        "owner",
                        2L,
                        false
                    )
                )
            );

        require(
            existing.amount==30&&
            ground.findOwned(
                4151,
                tile.x,
                tile.y,
                tile.plane,
                "owner"
            )!=null,
            "ground rollback fixture"
        );

        ground.rollbackBatchDetailed(
            mutations
        );

        require(
            existing.amount==25&&
            ground.findOwned(
                4151,
                tile.x,
                tile.y,
                tile.plane,
                "owner"
            )==null,
            "ground rollback restoration"
        );
    }

    private static WorldPlayer configuredPlayer(){
        WorldPlayer player=
            new WorldPlayer();

        synchronized(player.mutationLock()){
            BankState.Stack[] bank=
                new BankState.Stack[
                    BankState.BANK_CAPACITY
                ];
            BankState.Stack[] inventory=
                new BankState.Stack[
                    BankState.INVENTORY_CAPACITY
                ];

            inventory[0]=
                new BankState.Stack(
                    995,
                    100
                );

            player.bank().restoreAccountState(
                bank,
                inventory,
                false
            );

            int[] items=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            int[] quantities=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            Arrays.fill(
                items,
                -1
            );

            items[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=4151;
            quantities[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=1;

            player.equipment().restoreAccountState(
                items,
                quantities
            );
        }

        return player;
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

    private PlayerDeathGroundSettlementServiceTest(){}
}
