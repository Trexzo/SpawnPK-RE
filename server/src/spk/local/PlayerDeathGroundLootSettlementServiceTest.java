package spk.local;

import java.util.Arrays;
import java.util.List;

public final class PlayerDeathGroundLootSettlementServiceTest {
    private static final String RESOLUTION_AUTHORITY=
        "CUSTOM_LOCALLAB_PLAYER_DEATH_RESOLUTION";
    private static final String ITEM_SETTLEMENT_AUTHORITY=
        "CUSTOM_LOCALLAB_PLAYER_DEATH_ITEM_SETTLEMENT";
    private static final String GROUND_SETTLEMENT_AUTHORITY=
        "CUSTOM_LOCALLAB_PLAYER_DEATH_GROUND_SETTLEMENT";

    public static void main(String[] args){
        liveRecipientSettlement();
        offlineRecipientHasNoPresentation();
        authorityGuards();

        System.out.println(
            "PLAYER_DEATH_GROUND_LOOT_SETTLEMENT_PASS "+
            "canonicalGroundBatch=true "+
            "inventoryLoss=true "+
            "equipmentLoss=true "+
            "existingStackAmount=true "+
            "spawnEvents=true "+
            "amountEvents=true "+
            "liveRecipientGeneration=true "+
            "offlineRecipientCanonical=true "+
            "replayIdempotent=true "+
            "conflictingReplayRejected=true "+
            "recipientPolicyOwned=false "+
            "publicizationOwned=false "+
            "expiryOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void liveRecipientSettlement(){
        World world=World.isolatedForTest(700L);
        WorldPlayer victim=new WorldPlayer();
        WorldPlayer killer=new WorldPlayer();
        long victimGeneration=
            world.registerPlayer(
                victim,
                "victim"
            );
        long killerGeneration=
            world.registerPlayer(
                killer,
                "killer"
            );

        try{
            configureCarried(victim);
            PlayerDeathItemSettlementService.Settlement
                itemSettlement=
                    settleItems(
                        victim,
                        100L
                    );

            Tile deathTile=
                new Tile(
                    victim.movement().x(),
                    victim.movement().y(),
                    victim.movement().plane()
                );

            GroundItem existingCoins=
                world.groundItems().add(
                    995,
                    10,
                    deathTile,
                    "killer",
                    1L,
                    false
                );

            PlayerDeathGroundLootSettlementService
                service=
                    new PlayerDeathGroundLootSettlementService(
                        world,
                        GROUND_SETTLEMENT_AUTHORITY,
                        PlayerDeathGroundLootSettlementService
                            .OWNER_SCOPED_DEATH_TILE
                    );

            PlayerDeathGroundLootSettlementService.Receipt
                receipt=
                    service.settle(
                        itemSettlement,
                        deathTile,
                        "killer"
                    );

            require(
                receipt.playerId.equals(
                    victim.id()
                )&&
                receipt.deathTick==100L&&
                receipt.deathSequence==1L&&
                receipt.deathTile.equals(
                    deathTile
                )&&
                "killer".equals(
                    receipt.recipientRef
                )&&
                receipt.groundItems.size()==3,
                "receipt identity"
            );

            PlayerDeathGroundLootSettlementService
                .SettledGroundItem coinRow=
                    receiptRow(
                        receipt,
                        995
                    );
            PlayerDeathGroundLootSettlementService
                .SettledGroundItem whipRow=
                    receiptRow(
                        receipt,
                        4151
                    );
            PlayerDeathGroundLootSettlementService
                .SettledGroundItem ammoRow=
                    receiptRow(
                        receipt,
                        892
                    );

            require(
                coinRow.settledAmount==60&&
                coinRow.stackAmountAfter==70&&
                whipRow.settledAmount==1&&
                whipRow.stackAmountAfter==1&&
                ammoRow.settledAmount==40&&
                ammoRow.stackAmountAfter==40,
                "ground settlement amounts"
            );

            GroundItem coins=
                world.groundItems()
                    .findOwned(
                        995,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "killer"
                    );
            GroundItem whip=
                world.groundItems()
                    .findOwned(
                        4151,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "killer"
                    );
            GroundItem ammo=
                world.groundItems()
                    .findOwned(
                        892,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "killer"
                    );

            require(
                coins==existingCoins&&
                coins.amount==70&&
                whip!=null&&
                whip.amount==1&&
                ammo!=null&&
                ammo.amount==40,
                "canonical ground postimage"
            );

            List<WorldGroundItemPresentationEvents.Event>
                pending=
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            killer.id(),
                            killerGeneration,
                            System.currentTimeMillis()
                        );

            require(
                pending.size()==3,
                "live recipient event count"
            );

            requireEvent(
                pending,
                995,
                WorldGroundItemPresentationEvents.Kind.AMOUNT,
                10,
                70
            );
            requireEvent(
                pending,
                4151,
                WorldGroundItemPresentationEvents.Kind.SPAWN,
                0,
                1
            );
            requireEvent(
                pending,
                892,
                WorldGroundItemPresentationEvents.Kind.SPAWN,
                0,
                40
            );

            for(WorldGroundItemPresentationEvents.Event event:
                    pending)
                require(
                    world.groundItemPresentationEvents()
                        .markDelivered(
                            event.sequence,
                            killer.id(),
                            killerGeneration,
                            System.currentTimeMillis()
                        ),
                    "event cleanup"
                );

            require(
                service.settle(
                    itemSettlement,
                    deathTile,
                    "killer"
                )==receipt&&
                world.groundItemPresentationEvents()
                    .size()==0,
                "idempotent replay"
            );

            expect(
                IllegalStateException.class,
                ()->service.settle(
                    itemSettlement,
                    new Tile(
                        deathTile.x+1,
                        deathTile.y,
                        deathTile.plane
                    ),
                    "killer"
                ),
                "conflicting tile replay"
            );

            expect(
                IllegalStateException.class,
                ()->service.settle(
                    itemSettlement,
                    deathTile,
                    "victim"
                ),
                "conflicting recipient replay"
            );

            require(
                service.get(
                    victim.id(),
                    itemSettlement.deathSequence
                )==receipt&&
                service.size()==1,
                "receipt lookup"
            );
        }finally{
            if(world.players().owns(
                    victim,
                    victimGeneration))
                world.unregisterPlayer(
                    victim,
                    victimGeneration
                );
            if(world.players().owns(
                    killer,
                    killerGeneration))
                world.unregisterPlayer(
                    killer,
                    killerGeneration
                );
            world.close();
        }
    }

    private static void offlineRecipientHasNoPresentation(){
        World world=World.isolatedForTest(800L);
        WorldPlayer victim=new WorldPlayer();
        long victimGeneration=
            world.registerPlayer(
                victim,
                "offline-victim"
            );

        try{
            configureCarried(victim);
            PlayerDeathItemSettlementService.Settlement
                itemSettlement=
                    settleItems(
                        victim,
                        200L
                    );

            Tile deathTile=
                new Tile(
                    victim.movement().x(),
                    victim.movement().y(),
                    victim.movement().plane()
                );

            PlayerDeathGroundLootSettlementService
                service=
                    new PlayerDeathGroundLootSettlementService(
                        world,
                        GROUND_SETTLEMENT_AUTHORITY,
                        PlayerDeathGroundLootSettlementService
                            .OWNER_SCOPED_DEATH_TILE
                    );

            PlayerDeathGroundLootSettlementService.Receipt
                receipt=
                    service.settle(
                        itemSettlement,
                        deathTile,
                        "not-online"
                    );

            require(
                receipt.groundItems.size()==3&&
                world.groundItemPresentationEvents()
                    .size()==0,
                "offline recipient presentation"
            );

            require(
                world.groundItems()
                    .findOwned(
                        995,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "not-online"
                    )!=null&&
                world.groundItems()
                    .findOwned(
                        4151,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "not-online"
                    )!=null&&
                world.groundItems()
                    .findOwned(
                        892,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "not-online"
                    )!=null,
                "offline canonical ground state"
            );
        }finally{
            if(world.players().owns(
                    victim,
                    victimGeneration))
                world.unregisterPlayer(
                    victim,
                    victimGeneration
                );
            world.close();
        }
    }

    private static void authorityGuards(){
        World world=World.isolatedForTest(900L);

        try{
            for(String authority:
                    new String[]{
                        "EXACT_CURRENT_CLIENT",
                        "UNKNOWN_SERVER_AUTHORITY",
                        " "
                    })
                expect(
                    IllegalArgumentException.class,
                    ()->new PlayerDeathGroundLootSettlementService(
                        world,
                        authority,
                        PlayerDeathGroundLootSettlementService
                            .OWNER_SCOPED_DEATH_TILE
                    ),
                    "invalid ground authority "+
                    authority
                );

            expect(
                IllegalArgumentException.class,
                ()->new PlayerDeathGroundLootSettlementService(
                    world,
                    GROUND_SETTLEMENT_AUTHORITY,
                    "INVENTED_POLICY"
                ),
                "unsupported ground policy"
            );
        }finally{
            world.close();
        }
    }

    private static PlayerDeathItemSettlementService.Settlement
        settleItems(
            WorldPlayer player,
            long deathTick
        )
    {
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(
                player,
                "CUSTOM_LOCALLAB_PLAYER_DEATH_LIFECYCLE"
            );

        require(
            lifecycle.applyDamage(
                500,
                deathTick,
                "pvp-ground-loot-test",
                5L
            ).died,
            "death fixture"
        );

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                RESOLUTION_AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview
            preview=resolver.previewCurrentDeath();

        PlayerDeathItemResolutionService.Resolution
            resolution=
                resolver.resolveCurrentDeath(
                    preview,
                    Arrays.asList(
                        decision(
                            preview,
                            995,
                            40
                        ),
                        decision(
                            preview,
                            15272,
                            2
                        ),
                        decision(
                            preview,
                            4151,
                            0
                        ),
                        decision(
                            preview,
                            892,
                            10
                        )
                    )
                );

        return new PlayerDeathItemSettlementService(
            player,
            ITEM_SETTLEMENT_AUTHORITY
        ).settle(
            resolution
        );
    }

    private static void configureCarried(
        WorldPlayer player
    ){
        int[] inventoryItems=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] inventoryQuantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        Arrays.fill(
            inventoryItems,
            -1
        );

        inventoryItems[0]=995;
        inventoryQuantities[0]=100;
        inventoryItems[1]=15272;
        inventoryQuantities[1]=2;

        player.bank()
            .replaceInventorySemantic(
                inventoryItems,
                inventoryQuantities
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

        equipmentItems[
            EquipmentSlot.WEAPON.equipmentIndex
        ]=4151;
        equipmentQuantities[
            EquipmentSlot.WEAPON.equipmentIndex
        ]=1;
        equipmentItems[
            EquipmentSlot.AMMO.equipmentIndex
        ]=892;
        equipmentQuantities[
            EquipmentSlot.AMMO.equipmentIndex
        ]=50;

        player.equipment()
            .restoreAccountState(
                equipmentItems,
                equipmentQuantities
            );
    }

    private static PlayerDeathItemResolutionService.Decision
        decision(
            PlayerDeathItemResolutionService.DeathPreview preview,
            int itemId,
            int keptAmount
        )
    {
        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried)
            if(line.itemId==itemId)
                return new PlayerDeathItemResolutionService
                    .Decision(
                        line.lineId,
                        keptAmount
                    );

        throw new AssertionError(
            "missing carried item "+
            itemId
        );
    }

    private static PlayerDeathGroundLootSettlementService
        .SettledGroundItem receiptRow(
            PlayerDeathGroundLootSettlementService.Receipt receipt,
            int itemId
        )
    {
        for(PlayerDeathGroundLootSettlementService
                .SettledGroundItem row:
                receipt.groundItems)
            if(row.itemId==itemId)
                return row;

        throw new AssertionError(
            "missing receipt item "+
            itemId
        );
    }

    private static void requireEvent(
        List<WorldGroundItemPresentationEvents.Event> events,
        int itemId,
        WorldGroundItemPresentationEvents.Kind kind,
        int oldAmount,
        int newAmount
    ){
        for(WorldGroundItemPresentationEvents.Event event:
                events)
            if(event.itemId==itemId){
                require(
                    event.kind==kind&&
                    event.oldAmount==oldAmount&&
                    event.newAmount==newAmount,
                    "event facts item="+itemId
                );
                return;
            }

        throw new AssertionError(
            "missing event item "+
            itemId
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
            if(type.isInstance(
                    failure))
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

    private PlayerDeathGroundLootSettlementServiceTest(){}
}
