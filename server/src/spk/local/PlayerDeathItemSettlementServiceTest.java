package spk.local;

import java.util.*;

public final class PlayerDeathItemSettlementServiceTest {
    private static final String POLICY=
        "CUSTOM_LOCALLAB_DEATH_SETTLEMENT_TEST";

    public static void main(String[] args)
        throws Exception{
        exactSettlementAndReplay();
        groundPreflightFailureAtomic();
        staleDeathRejected();

        System.out.println(
            "PLAYER_DEATH_ITEM_SETTLEMENT_PASS "+
            "inventoryExact=true "+
            "equipmentExact=true "+
            "lostToGround=true "+
            "ownerScoped=true "+
            "presentationQueued=true "+
            "replayIdempotent=true "+
            "conflictingReplayFailClosed=true "+
            "groundPreflightFailureAtomic=true "+
            "staleDeathFailClosed=true "+
            "keepPolicyOwned=false "+
            "valuePolicyOwned=false "+
            "authority="+
            PlayerDeathItemSettlementService.AUTHORITY
        );
    }

    private static void exactSettlementAndReplay()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer victim=
            configuredPlayer(
                100,
                2,
                true,
                50
            );
        WorldPlayer killer=
            emptyPlayer();

        world.registerPlayer(victim,"victim");
        world.registerPlayer(killer,"killer");

        try{
            kill(victim,77L,5L);

            PlayerDeathItemResolutionService resolver=
                new PlayerDeathItemResolutionService(
                    victim,
                    POLICY
                );
            PlayerDeathItemResolutionService.DeathPreview
                preview=
                    resolver.previewCurrentDeath();

            ArrayList<PlayerDeathItemResolutionService.Decision>
                decisions=
                    new ArrayList<>();

            for(PlayerDeathItemResolutionService.CarriedLine line:
                    preview.carried){
                int keep;

                if(line.itemId==995)
                    keep=40;
                else if(line.itemId==892)
                    keep=0;
                else
                    keep=line.quantity;

                decisions.add(
                    new PlayerDeathItemResolutionService.Decision(
                        line.lineId,
                        keep
                    )
                );
            }

            PlayerDeathItemResolutionService.Resolution
                resolution=
                    resolver.resolveCurrentDeath(
                        preview,
                        decisions
                    );

            PlayerDeathItemSettlementService settlement=
                new PlayerDeathItemSettlementService(
                    world,
                    victim
                );

            PlayerDeathItemSettlementService.Receipt
                receipt=
                    settlement.settle(
                        resolution,
                        "killer"
                    );

            require(
                receipt.playerId.equals(victim.id())&&
                receipt.deathSequence==1L&&
                receipt.deathTick==77L&&
                receipt.lootOwner.equals("killer")&&
                receipt.keptTotalQuantity==43&&
                receipt.lostTotalQuantity==110&&
                receipt.groundItems.size()==2,
                "settlement receipt"
            );

            BankState.Stack coins=
                victim.bank().inventoryAt(0);
            BankState.Stack food=
                victim.bank().inventoryAt(1);

            require(
                coins!=null&&
                coins.itemId==995&&
                coins.qty==40&&
                food!=null&&
                food.itemId==15272&&
                food.qty==2,
                "inventory postimage"
            );

            require(
                victim.equipment().itemAt(
                    EquipmentSlot.WEAPON
                )==4151&&
                victim.equipment().quantityAt(
                    EquipmentSlot.WEAPON
                )==1&&
                victim.equipment().itemAt(
                    EquipmentSlot.AMMO
                )<0&&
                victim.equipment().quantityAt(
                    EquipmentSlot.AMMO
                )==0,
                "equipment postimage"
            );

            GroundItem coinsGround=
                world.groundItems().findOwned(
                    995,
                    receipt.deathTile.x,
                    receipt.deathTile.y,
                    receipt.deathTile.plane,
                    "killer"
                );
            GroundItem ammoGround=
                world.groundItems().findOwned(
                    892,
                    receipt.deathTile.x,
                    receipt.deathTile.y,
                    receipt.deathTile.plane,
                    "killer"
                );

            require(
                coinsGround!=null&&
                coinsGround.amount==60&&
                ammoGround!=null&&
                ammoGround.amount==50,
                "ground loss bundle"
            );

            int eventsBeforeReplay=
                world.groundItemPresentationEvents()
                    .size();

            require(
                eventsBeforeReplay==2,
                "ground presentation events="+
                eventsBeforeReplay
            );

            PlayerDeathItemSettlementService.Receipt
                replay=
                    settlement.settle(
                        resolution,
                        "killer"
                    );

            require(
                replay==receipt&&
                settlement.size()==1&&
                world.groundItems()
                    .snapshot()
                    .size()==2&&
                world.groundItemPresentationEvents()
                    .size()==eventsBeforeReplay,
                "identical replay"
            );

            boolean conflicting=false;
            try{
                settlement.settle(
                    resolution,
                    "other-owner"
                );
            }catch(IllegalStateException expected){
                conflicting=true;
            }

            require(
                conflicting&&
                world.groundItems()
                    .snapshot()
                    .size()==2&&
                victim.bank()
                    .inventoryAt(0)
                    .qty==40,
                "conflicting replay"
            );
        }finally{
            world.unregisterPlayer(victim);
            world.unregisterPlayer(killer);
            world.close();
        }
    }

    private static void groundPreflightFailureAtomic()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer victim=
            configuredPlayer(
                1,
                0,
                false,
                0
            );
        WorldPlayer killer=
            emptyPlayer();

        world.registerPlayer(victim,"victim-overflow");
        world.registerPlayer(killer,"killer-overflow");

        try{
            kill(victim,88L,5L);

            Tile tile=
                new Tile(
                    victim.movement().x(),
                    victim.movement().y(),
                    victim.movement().plane()
                );

            world.groundItems().add(
                995,
                Integer.MAX_VALUE,
                tile,
                "killer-overflow",
                1L,
                false
            );

            PlayerDeathItemResolutionService resolver=
                new PlayerDeathItemResolutionService(
                    victim,
                    POLICY
                );
            PlayerDeathItemResolutionService.DeathPreview
                preview=
                    resolver.previewCurrentDeath();

            PlayerDeathItemResolutionService.Resolution
                resolution=
                    resolver.resolveCurrentDeath(
                        preview,
                        keepNone(preview.carried)
                    );

            PlayerDeathItemSettlementService settlement=
                new PlayerDeathItemSettlementService(
                    world,
                    victim
                );

            boolean failed=false;
            try{
                settlement.settle(
                    resolution,
                    "killer-overflow"
                );
            }catch(IllegalStateException expected){
                failed=true;
            }

            BankState.Stack stillCarried=
                victim.bank().inventoryAt(0);

            require(
                failed&&
                stillCarried!=null&&
                stillCarried.itemId==995&&
                stillCarried.qty==1&&
                world.groundItems()
                    .findOwned(
                        995,
                        tile.x,
                        tile.y,
                        tile.plane,
                        "killer-overflow"
                    ).amount==Integer.MAX_VALUE&&
                settlement.size()==0,
                "ground preflight failure changed state"
            );
        }finally{
            world.unregisterPlayer(victim);
            world.unregisterPlayer(killer);
            world.close();
        }
    }

    private static void staleDeathRejected()
        throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer victim=
            configuredPlayer(
                1,
                0,
                false,
                0
            );

        world.registerPlayer(victim,"victim-stale");

        try{
            kill(victim,99L,0L);

            PlayerDeathItemResolutionService resolver=
                new PlayerDeathItemResolutionService(
                    victim,
                    POLICY
                );
            PlayerDeathItemResolutionService.DeathPreview
                preview=
                    resolver.previewCurrentDeath();
            PlayerDeathItemResolutionService.Resolution
                resolution=
                    resolver.resolveCurrentDeath(
                        preview,
                        keepNone(preview.carried)
                    );

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(
                    victim,
                    POLICY
                );

            require(
                lifecycle.tick(99L,99)==
                    PlayerLifecycleService.TickResult.RESPAWNED,
                "stale fixture respawn"
            );

            PlayerDeathItemSettlementService settlement=
                new PlayerDeathItemSettlementService(
                    world,
                    victim
                );

            boolean rejected=false;
            try{
                settlement.settle(
                    resolution,
                    "nobody"
                );
            }catch(IllegalStateException expected){
                rejected=true;
            }

            require(
                rejected&&
                victim.bank()
                    .inventoryAt(0)!=null&&
                settlement.size()==0&&
                world.groundItems()
                    .snapshot()
                    .isEmpty(),
                "stale death settlement"
            );
        }finally{
            world.unregisterPlayer(victim);
            world.close();
        }
    }

    private static WorldPlayer configuredPlayer(
        int coins,
        int food,
        boolean weapon,
        int ammo
    ){
        WorldPlayer player=emptyPlayer();

        BankState.Stack[] bank=
            new BankState.Stack[
                BankState.BANK_CAPACITY
            ];
        BankState.Stack[] inventory=
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ];

        if(coins>0)
            inventory[0]=
                new BankState.Stack(
                    995,
                    coins
                );
        if(food>0)
            inventory[1]=
                new BankState.Stack(
                    15272,
                    food
                );

        player.bank().restoreAccountState(
            bank,
            inventory,
            false
        );

        int[] items=
            player.equipment().containerItems();
        int[] quantities=
            player.equipment()
                .containerQuantities();

        if(weapon){
            items[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=4151;
            quantities[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=1;
        }

        if(ammo>0){
            items[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ]=892;
            quantities[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ]=ammo;
        }

        player.equipment()
            .restoreAccountState(
                items,
                quantities
            );

        return player;
    }

    private static WorldPlayer emptyPlayer(){
        WorldPlayer player=
            new WorldPlayer();

        player.bank().restoreAccountState(
            new BankState.Stack[
                BankState.BANK_CAPACITY
            ],
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ],
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
        Arrays.fill(items,-1);

        player.equipment()
            .restoreAccountState(
                items,
                quantities
            );
        player.playerState()
            .syncEquipmentPresentation(
                player.equipment()
            );

        return player;
    }

    private static void kill(
        WorldPlayer player,
        long tick,
        long respawnDelay
    ){
        PlayerLifecycleService.DamageResult result=
            new PlayerLifecycleService(
                player,
                POLICY
            ).applyDamage(
                500,
                tick,
                "PVP_TEST",
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
            out=
                new ArrayList<>();

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

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private PlayerDeathItemSettlementServiceTest(){}
}
