package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PlayerDeathGroundSettlementServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_DEATH_SETTLEMENT_TEST";

    public static void main(String[] args){
        exactSettlementAndReplay();
        staleCarriedStateFailsClosed();
        crossPlayerResolutionRejected();
        postRespawnReplayRejected();
        authorityGuards();

        System.out.println(
            "PLAYER_DEATH_GROUND_SETTLEMENT_PASS "+
            "exactDeathTile=true "+
            "inventoryLoss=true "+
            "equipmentLoss=true "+
            "partialKeep=true "+
            "groundBatch=true "+
            "lootOwnerCallerOwned=true "+
            "sameDeathReplayIdempotent=true "+
            "staleStateRejected=true "+
            "crossPlayerRejected=true "+
            "postRespawnReplayRejected=true "+
            "policyInvented=false "+
            "packetPublication=false"
        );
    }

    private static void exactSettlementAndReplay(){
        WorldPlayer player=configuredPlayer();
        World world=World.isolatedForTest(600L);
        GroundItemRegistry ground=
            world.groundItems();

        kill(player,77L,"PVP_TEST",5L);

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                "CUSTOM_LOCALLAB_DEATH_POLICY_TEST"
            );

        PlayerDeathItemResolutionService.DeathPreview preview=
            resolver.previewCurrentDeath();

        Tile expectedTile=
            new Tile(
                MovementState.INITIAL_X,
                MovementState.INITIAL_Y,
                0
            );

        require(
            expectedTile.equals(preview.deathTile),
            "death preview tile"
        );

        List<PlayerDeathItemResolutionService.Decision>
            decisions=
                new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried){
            final int kept;
            if(line.itemId==995)
                kept=40;
            else if(line.itemId==15272)
                kept=line.quantity;
            else if(line.itemId==4151)
                kept=line.quantity;
            else if(line.itemId==892)
                kept=20;
            else
                throw new AssertionError(
                    "unexpected carried item "+
                    line.itemId
                );

            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    kept
                )
            );
        }

        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                decisions
            );

        require(
            expectedTile.equals(
                resolution.deathTile
            ),
            "resolution death tile"
        );

        PlayerDeathGroundSettlementService settlementService=
            new PlayerDeathGroundSettlementService(
                world,
                player,
                AUTHORITY
            );

        PlayerDeathGroundSettlementService.Settlement settlement=
            settlementService.settle(
                resolution,
                "killer"
            );

        require(
            settlement.deathSequence==
                resolution.deathSequence&&
            settlement.deathTick==
                resolution.deathTick&&
            expectedTile.equals(
                settlement.deathTile
            )&&
            "killer".equals(
                settlement.lootOwner
            )&&
            settlement.keptTotalQuantity==63&&
            settlement.lostTotalQuantity==90,
            "settlement identity/totals"
        );

        require(
            player.bank().inventoryCount(995)==40&&
            player.bank().inventoryCount(15272)==2,
            "inventory postimage"
        );

        require(
            player.equipment().itemAt(
                EquipmentSlot.WEAPON
            )==4151&&
            player.equipment().quantityAt(
                EquipmentSlot.WEAPON
            )==1&&
            player.equipment().itemAt(
                EquipmentSlot.AMMO
            )==892&&
            player.equipment().quantityAt(
                EquipmentSlot.AMMO
            )==20,
            "equipment postimage"
        );

        GroundItem coins=
            ground.findOwned(
                995,
                expectedTile.x,
                expectedTile.y,
                expectedTile.plane,
                "killer"
            );
        GroundItem ammo=
            ground.findOwned(
                892,
                expectedTile.x,
                expectedTile.y,
                expectedTile.plane,
                "killer"
            );

        require(
            coins!=null&&
            coins.amount==60&&
            ammo!=null&&
            ammo.amount==30&&
            ground.size()==2,
            "ground loss postimage"
        );

        PlayerDeathGroundSettlementService.Settlement replay=
            settlementService.settle(
                resolution,
                "different-owner-must-not-reapply"
            );

        require(
            replay==settlement&&
            ground.size()==2&&
            ground.findOwned(
                995,
                expectedTile.x,
                expectedTile.y,
                expectedTile.plane,
                "killer"
            ).amount==60&&
            player.bank().inventoryCount(995)==40&&
            player.equipment().quantityAt(
                EquipmentSlot.AMMO
            )==20,
            "same-death replay"
        );
    }

    private static void staleCarriedStateFailsClosed(){
        WorldPlayer player=configuredPlayer();
        World world=World.isolatedForTest(600L);
        GroundItemRegistry ground=
            world.groundItems();

        kill(player,88L,"STALE_TEST",5L);

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                "CUSTOM_LOCALLAB_DEATH_POLICY_TEST"
            );
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolver.previewCurrentDeath();
        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                keepNone(preview.carried)
            );

        synchronized(player.mutationLock()){
            player.bank()
                .consumeInventoryAmountSemantic(
                    0,
                    995,
                    1
                );
        }

        PlayerDeathGroundSettlementService service=
            new PlayerDeathGroundSettlementService(
                world,
                player,
                AUTHORITY
            );

        expect(
            IllegalStateException.class,
            ()->service.settle(
                resolution,
                "killer"
            ),
            "stale carried state"
        );

        require(
            ground.size()==0&&
            player.bank().inventoryCount(995)==99&&
            service.size()==0,
            "stale failure mutation"
        );
    }

    private static void crossPlayerResolutionRejected(){
        WorldPlayer first=configuredPlayer();
        WorldPlayer second=configuredPlayer();

        kill(first,99L,"FIRST",5L);
        kill(second,99L,"SECOND",5L);

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                first,
                "CUSTOM_LOCALLAB_DEATH_POLICY_TEST"
            );
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolver.previewCurrentDeath();
        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                keepNone(preview.carried)
            );

        World world=World.isolatedForTest(600L);
        GroundItemRegistry ground=
            world.groundItems();
        PlayerDeathGroundSettlementService service=
            new PlayerDeathGroundSettlementService(
                world,
                second,
                AUTHORITY
            );

        expect(
            IllegalArgumentException.class,
            ()->service.settle(
                resolution,
                "killer"
            ),
            "cross-player resolution"
        );

        require(
            ground.size()==0,
            "cross-player ground mutation"
        );
    }

    private static void postRespawnReplayRejected(){
        WorldPlayer player=configuredPlayer();
        World world=World.isolatedForTest(600L);
        GroundItemRegistry ground=
            world.groundItems();

        kill(player,111L,"RESPAWN_REPLAY",0L);

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                "CUSTOM_LOCALLAB_DEATH_POLICY_TEST"
            );
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolver.previewCurrentDeath();
        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                keepAll(preview.carried)
            );

        PlayerDeathGroundSettlementService service=
            new PlayerDeathGroundSettlementService(
                world,
                player,
                AUTHORITY
            );
        service.settle(
            resolution,
            null
        );

        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            );
        PlayerLifecycleService.PreparedRespawn prepared=
            lifecycle.prepareRespawn(111L);

        require(
            prepared!=null,
            "prepared respawn"
        );
        lifecycle.commitPreparedRespawn(
            prepared
        );

        expect(
            IllegalStateException.class,
            ()->service.settle(
                resolution,
                null
            ),
            "post-respawn replay"
        );

        require(
            ground.size()==0,
            "post-respawn replay mutation"
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
            inventory[1]=
                new BankState.Stack(
                    15272,
                    2
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
            items[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ]=892;
            quantities[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ]=50;

            player.equipment()
                .restoreAccountState(
                    items,
                    quantities
                );
        }

        return player;
    }

    private static void kill(
        WorldPlayer player,
        long tick,
        String cause,
        long respawnDelay
    ){
        PlayerLifecycleService.DamageResult result=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            ).applyDamage(
                500,
                tick,
                cause,
                respawnDelay
            );

        require(
            result.died&&
            player.lifecycle().dead(),
            "death fixture"
        );
    }

    private static List<PlayerDeathItemResolutionService.Decision>
        keepNone(
            List<PlayerDeathItemResolutionService.CarriedLine>
                lines
        ){
        ArrayList<PlayerDeathItemResolutionService.Decision>
            out=new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                lines)
            out.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    0
                )
            );

        return out;
    }

    private static List<PlayerDeathItemResolutionService.Decision>
        keepAll(
            List<PlayerDeathItemResolutionService.CarriedLine>
                lines
        ){
        ArrayList<PlayerDeathItemResolutionService.Decision>
            out=new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                lines)
            out.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    line.quantity
                )
            );

        return out;
    }

    private static void authorityGuards(){
        WorldPlayer player=configuredPlayer();
        World world=World.isolatedForTest(600L);
        GroundItemRegistry ground=
            world.groundItems();

        expect(
            IllegalArgumentException.class,
            ()->new PlayerDeathGroundSettlementService(
                world,
                player,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority"
        );
        expect(
            IllegalArgumentException.class,
            ()->new PlayerDeathGroundSettlementService(
                world,
                player,
                "EXACT_CURRENT_CLIENT"
            ),
            "client authority"
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
            throw new AssertionError(
                label
            );
    }

    private PlayerDeathGroundSettlementServiceTest(){}
}
