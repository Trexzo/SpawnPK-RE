package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class PlayerDeathItemSettlementServiceTest {
    private static final String RESOLUTION_AUTHORITY=
        "CUSTOM_LOCALLAB_DEATH_RESOLUTION_TEST";
    private static final String SETTLEMENT_AUTHORITY=
        "CUSTOM_LOCALLAB_DEATH_SETTLEMENT_TEST";

    public static void main(String[] args){
        exactAtomicSettlement();
        replayIsIdempotent();
        carriedDriftRejectsWithoutSettlementMutation();
        crossPlayerResolutionRejected();
        authorityGuards();
        boundaryGuard();

        System.out.println(
            "PLAYER_DEATH_ITEM_SETTLEMENT_PASS "+
            "inventoryMutation=true "+
            "equipmentMutation=true "+
            "partialStackKeep=true "+
            "fullLossClears=true "+
            "fullKeepPreserves=true "+
            "lostLinesReturned=true "+
            "deathSequenceBound=true "+
            "replayIdempotent=true "+
            "driftAtomic=true "+
            "crossPlayerRejected=true "+
            "itemValueOwned=false "+
            "protectItemOwned=false "+
            "lootRecipientOwned=false "+
            "groundMutation=false "+
            "packetOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void exactAtomicSettlement(){
        WorldPlayer player=configuredPlayer();
        kill(player,100L);

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                RESOLUTION_AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview
            preview=resolver.previewCurrentDeath();

        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                Arrays.asList(
                    decision(preview,995,40),
                    decision(preview,15272,2),
                    decision(preview,4151,0),
                    decision(preview,892,10)
                )
            );

        PlayerDeathItemSettlementService service=
            new PlayerDeathItemSettlementService(
                player,
                SETTLEMENT_AUTHORITY
            );

        PlayerDeathItemSettlementService.Settlement result=
            service.settle(resolution);

        BankState.InventorySlotSnapshot coins=
            player.bank().inventorySlotSnapshot(0);
        BankState.InventorySlotSnapshot food=
            player.bank().inventorySlotSnapshot(1);

        require(
            coins.occupied&&
            coins.itemId==995&&
            coins.quantity==40,
            "partial inventory keep"
        );
        require(
            food.occupied&&
            food.itemId==15272&&
            food.quantity==2,
            "full inventory keep"
        );

        require(
            player.equipment().itemAt(
                EquipmentSlot.WEAPON
            )==-1&&
            player.equipment().quantityAt(
                EquipmentSlot.WEAPON
            )==0,
            "full equipment loss"
        );
        require(
            player.equipment().itemAt(
                EquipmentSlot.AMMO
            )==892&&
            player.equipment().quantityAt(
                EquipmentSlot.AMMO
            )==10,
            "partial equipment keep"
        );

        require(
            result.playerId.equals(
                player.id()
            )&&
            result.deathTick==100L&&
            result.deathSequence==1L&&
            "pvp-settlement-test".equals(
                result.deathCause
            )&&
            result.keptTotalQuantity==52&&
            result.lostTotalQuantity==101&&
            result.lost.size()==3&&
            RESOLUTION_AUTHORITY.equals(
                result.resolutionAuthority
            )&&
            SETTLEMENT_AUTHORITY.equals(
                result.settlementAuthority
            ),
            "settlement identity/totals"
        );

        requireLost(
            result.lost,
            PlayerDeathItemResolutionService.Source.INVENTORY,
            0,
            null,
            995,
            60
        );
        requireLost(
            result.lost,
            PlayerDeathItemResolutionService.Source.EQUIPMENT,
            EquipmentSlot.WEAPON.equipmentIndex,
            EquipmentSlot.WEAPON,
            4151,
            1
        );
        requireLost(
            result.lost,
            PlayerDeathItemResolutionService.Source.EQUIPMENT,
            EquipmentSlot.AMMO.equipmentIndex,
            EquipmentSlot.AMMO,
            892,
            40
        );

        expect(
            UnsupportedOperationException.class,
            ()->result.lost.clear(),
            "lost result mutable"
        );
    }

    private static void replayIsIdempotent(){
        WorldPlayer player=configuredPlayer();
        kill(player,200L);

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                RESOLUTION_AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview
            preview=resolver.previewCurrentDeath();
        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                Arrays.asList(
                    decision(preview,995,40),
                    decision(preview,15272,2),
                    decision(preview,4151,0),
                    decision(preview,892,10)
                )
            );

        PlayerDeathItemSettlementService service=
            new PlayerDeathItemSettlementService(
                player,
                SETTLEMENT_AUTHORITY
            );

        PlayerDeathItemSettlementService.Settlement first=
            service.settle(resolution);

        int coinsBefore=
            player.bank()
                .inventorySlotSnapshot(0)
                .quantity;
        int ammoBefore=
            player.equipment()
                .quantityAt(
                    EquipmentSlot.AMMO
                );

        PlayerDeathItemSettlementService.Settlement second=
            service.settle(resolution);

        require(
            first==second&&
            service.size()==1&&
            service.get(
                resolution.deathSequence
            )==first,
            "replay result identity"
        );
        require(
            player.bank()
                .inventorySlotSnapshot(0)
                .quantity==coinsBefore&&
            player.equipment()
                .quantityAt(
                    EquipmentSlot.AMMO
                )==ammoBefore,
            "replay mutated carried items"
        );
    }

    private static void carriedDriftRejectsWithoutSettlementMutation(){
        WorldPlayer player=configuredPlayer();
        kill(player,300L);

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                RESOLUTION_AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview
            preview=resolver.previewCurrentDeath();
        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                Arrays.asList(
                    decision(preview,995,40),
                    decision(preview,15272,2),
                    decision(preview,4151,0),
                    decision(preview,892,10)
                )
            );

        /*
         * Simulate a competing carried-state mutation after resolution but
         * before settlement. Settlement must reject before applying any of its
         * own postimage.
         */
        player.bank()
            .consumeInventoryAmountSemantic(
                0,
                995,
                1
            );

        int[] beforeItems=
            equipmentItems(player);
        int[] beforeQuantities=
            equipmentQuantities(player);
        BankState.InventorySlotSnapshot beforeCoins=
            player.bank()
                .inventorySlotSnapshot(0);
        BankState.InventorySlotSnapshot beforeFood=
            player.bank()
                .inventorySlotSnapshot(1);

        PlayerDeathItemSettlementService service=
            new PlayerDeathItemSettlementService(
                player,
                SETTLEMENT_AUTHORITY
            );

        expect(
            IllegalStateException.class,
            ()->service.settle(
                resolution
            ),
            "drifted carried state"
        );

        BankState.InventorySlotSnapshot afterCoins=
            player.bank()
                .inventorySlotSnapshot(0);
        BankState.InventorySlotSnapshot afterFood=
            player.bank()
                .inventorySlotSnapshot(1);

        require(
            afterCoins.itemId==
                beforeCoins.itemId&&
            afterCoins.quantity==
                beforeCoins.quantity&&
            afterFood.itemId==
                beforeFood.itemId&&
            afterFood.quantity==
                beforeFood.quantity&&
            Arrays.equals(
                beforeItems,
                equipmentItems(player)
            )&&
            Arrays.equals(
                beforeQuantities,
                equipmentQuantities(player)
            )&&
            service.size()==0,
            "settlement changed state after drift rejection"
        );
    }

    private static void crossPlayerResolutionRejected(){
        WorldPlayer a=configuredPlayer();
        WorldPlayer b=configuredPlayer();
        kill(a,400L);
        kill(b,400L);

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                a,
                RESOLUTION_AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview
            preview=resolver.previewCurrentDeath();
        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                Arrays.asList(
                    decision(preview,995,100),
                    decision(preview,15272,2),
                    decision(preview,4151,1),
                    decision(preview,892,50)
                )
            );

        PlayerDeathItemSettlementService service=
            new PlayerDeathItemSettlementService(
                b,
                SETTLEMENT_AUTHORITY
            );

        expect(
            IllegalArgumentException.class,
            ()->service.settle(
                resolution
            ),
            "cross-player death resolution"
        );
    }

    private static void authorityGuards(){
        WorldPlayer player=new WorldPlayer();

        for(String authority:
                new String[]{
                    "EXACT_CURRENT_CLIENT",
                    "UNKNOWN_SERVER_AUTHORITY",
                    " "
                })
            expect(
                IllegalArgumentException.class,
                ()->new PlayerDeathItemSettlementService(
                    player,
                    authority
                ),
                "invalid settlement authority "+
                authority
            );

        expect(
            NullPointerException.class,
            ()->new PlayerDeathItemSettlementService(
                player,
                null
            ),
            "null settlement authority"
        );
    }

    private static void boundaryGuard(){
        String className=
            PlayerDeathItemSettlementService.class
                .getName()
                .toLowerCase(
                    Locale.ROOT
                );

        require(
            !className.contains("packet")&&
            !className.contains("widget")&&
            !className.contains("socket"),
            "transport identity leaked through settlement type"
        );
    }

    private static WorldPlayer configuredPlayer(){
        WorldPlayer player=new WorldPlayer();

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

        return player;
    }

    private static void kill(
        WorldPlayer player,
        long tick
    ){
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(
                player,
                "CUSTOM_LOCALLAB_DEATH_SETTLEMENT_LIFECYCLE"
            );

        PlayerLifecycleService.DamageResult result=
            lifecycle.applyDamage(
                500,
                tick,
                "pvp-settlement-test",
                5L
            );

        require(
            result.died&&
            player.lifecycle().dead(),
            "test death setup"
        );
    }

    private static PlayerDeathItemResolutionService.Decision
        decision(
            PlayerDeathItemResolutionService.DeathPreview preview,
            int itemId,
            int keptAmount
        )
    {
        for(
            PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried
        )
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

    private static int[] equipmentItems(
        WorldPlayer player
    ){
        int[] out=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];

        for(int i=0;i<out.length;i++)
            out[i]=
                player.equipment()
                    .itemAt(i);

        return out;
    }

    private static int[] equipmentQuantities(
        WorldPlayer player
    ){
        int[] out=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];

        for(int i=0;i<out.length;i++)
            out[i]=
                player.equipment()
                    .quantityAt(i);

        return out;
    }

    private static void requireLost(
        List<PlayerDeathItemSettlementService.LostLine> lost,
        PlayerDeathItemResolutionService.Source source,
        int sourceIndex,
        EquipmentSlot equipmentSlot,
        int itemId,
        int quantity
    ){
        for(PlayerDeathItemSettlementService.LostLine line:
                lost)
            if(line.source==source&&
               line.sourceIndex==sourceIndex&&
               line.equipmentSlot==equipmentSlot&&
               line.itemId==itemId&&
               line.quantity==quantity)
                return;

        throw new AssertionError(
            "missing lost line item="+
            itemId+
            " qty="+
            quantity
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
            throw new AssertionError(
                label
            );
    }

    private PlayerDeathItemSettlementServiceTest(){}
}
