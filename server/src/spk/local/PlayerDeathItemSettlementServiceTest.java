package spk.local;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PlayerDeathItemSettlementServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_DEATH_SETTLEMENT_TEST";

    public static void main(String[] args){
        exactSettlementAndReplay();
        stateDriftRejected();
        staleDeathRejected();
        crossPlayerResolutionRejected();
        emptyCarriedSettles();
        authorityAndBoundary();

        System.out.println(
            "PLAYER_DEATH_ITEM_SETTLEMENT_PASS "+
            "exactDeathIdentity=true "+
            "fullCarriedPreimage=true "+
            "partialStackKeep=true "+
            "equipmentLoss=true "+
            "lostLinesReturned=true "+
            "replayIdempotent=true "+
            "stateDriftRejected=true "+
            "staleDeathRejected=true "+
            "crossPlayerRejected=true "+
            "emptyCarried=true "+
            "groundMutation=false "+
            "lootRecipientOwned=false "+
            "protectionPolicyOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void exactSettlementAndReplay(){
        WorldPlayer player=
            configuredPlayer();

        PlayerDeathItemResolutionService.Resolution
            resolution=
                resolveDeath(
                    player,
                    77L,
                    "pvp-test"
                );

        PlayerDeathItemSettlementService service=
            new PlayerDeathItemSettlementService(
                player,
                AUTHORITY
            );

        PlayerDeathItemSettlementService.Receipt
            receipt=
                service.settle(
                    resolution
                );

        require(
            receipt.playerId.equals(
                player.id()
            )&&
            receipt.deathTick==77L&&
            receipt.deathSequence==1L&&
            "pvp-test".equals(
                receipt.deathCause
            )&&
            receipt.keptTotalQuantity==44&&
            receipt.lostTotalQuantity==110&&
            AUTHORITY.equals(
                receipt.policyAuthority
            ),
            "receipt identity/totals"
        );

        require(
            receipt.lostLines.size()==2,
            "lost line count"
        );

        PlayerDeathItemSettlementService.LostLine
            coins=
                findLost(
                    receipt.lostLines,
                    995
                );
        PlayerDeathItemSettlementService.LostLine
            ammo=
                findLost(
                    receipt.lostLines,
                    892
                );

        require(
            coins.quantity==60&&
            coins.source==
                PlayerDeathItemResolutionService
                    .Source.INVENTORY&&
            coins.sourceIndex==0&&
            coins.equipmentSlot==null,
            "coin lost line"
        );

        require(
            ammo.quantity==50&&
            ammo.source==
                PlayerDeathItemResolutionService
                    .Source.EQUIPMENT&&
            ammo.equipmentSlot==
                EquipmentSlot.AMMO,
            "ammo lost line"
        );

        expect(
            UnsupportedOperationException.class,
            ()->receipt.lostLines.clear(),
            "lost lines mutable"
        );

        BankState.Stack keptCoins=
            player.bank().inventoryAt(0);
        BankState.Stack keptFood=
            player.bank().inventoryAt(1);

        require(
            keptCoins!=null&&
            keptCoins.itemId==995&&
            keptCoins.qty==40&&
            keptFood!=null&&
            keptFood.itemId==15272&&
            keptFood.qty==3,
            "inventory kept postimage"
        );

        require(
            player.equipment()
                .itemAt(
                    EquipmentSlot.WEAPON
                )==4151&&
            player.equipment()
                .quantityAt(
                    EquipmentSlot.WEAPON
                )==1&&
            player.equipment()
                .itemAt(
                    EquipmentSlot.AMMO
                )==-1&&
            player.equipment()
                .quantityAt(
                    EquipmentSlot.AMMO
                )==0,
            "equipment kept/lost postimage"
        );

        PlayerDeathItemSettlementService.Receipt
            replay=
                service.settle(
                    resolution
                );

        require(
            replay==receipt&&
            service.size()==1,
            "settlement replay"
        );
    }

    private static void stateDriftRejected(){
        WorldPlayer player=
            configuredPlayer();

        PlayerDeathItemResolutionService.Resolution
            resolution=
                resolveDeath(
                    player,
                    81L,
                    "drift"
                );

        synchronized(player.mutationLock()){
            BankState.Stack coins=
                player.bank()
                    .inventoryAt(0);
            coins.qty=99;
        }

        PlayerDeathItemSettlementService service=
            new PlayerDeathItemSettlementService(
                player,
                AUTHORITY
            );

        expect(
            IllegalStateException.class,
            ()->service.settle(
                resolution
            ),
            "state drift"
        );

        require(
            player.bank()
                .inventoryAt(0)
                .qty==99&&
            player.equipment()
                .itemAt(
                    EquipmentSlot.AMMO
                )==892&&
            service.size()==0,
            "state drift failure mutated carried state"
        );
    }

    private static void staleDeathRejected(){
        WorldPlayer player=
            configuredPlayer();
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            );

        PlayerLifecycleService.DamageResult firstDeath=
            lifecycle.applyDamage(
                500,
                90L,
                "first",
                0L
            );
        require(
            firstDeath.died,
            "first death fixture"
        );

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolver.previewCurrentDeath();
        PlayerDeathItemResolutionService.Resolution old=
            resolver.resolveCurrentDeath(
                preview,
                decisions(preview)
            );

        require(
            lifecycle.tick(
                90L,
                99
            )==
                PlayerLifecycleService.TickResult
                    .RESPAWNED,
            "zero-delay respawn fixture"
        );

        require(
            lifecycle.applyDamage(
                500,
                91L,
                "second",
                5L
            ).died,
            "replacement death fixture"
        );

        PlayerDeathItemSettlementService service=
            new PlayerDeathItemSettlementService(
                player,
                AUTHORITY
            );

        expect(
            IllegalStateException.class,
            ()->service.settle(old),
            "stale death resolution"
        );

        require(
            service.size()==0&&
            player.lifecycle()
                .deathSequence()==2L,
            "stale death failure changed replacement death"
        );
    }

    private static void crossPlayerResolutionRejected(){
        WorldPlayer first=
            configuredPlayer();
        WorldPlayer second=
            configuredPlayer();

        PlayerDeathItemResolutionService.Resolution
            resolution=
                resolveDeath(
                    first,
                    100L,
                    "first"
                );

        PlayerDeathItemSettlementService secondService=
            new PlayerDeathItemSettlementService(
                second,
                AUTHORITY
            );

        expect(
            IllegalArgumentException.class,
            ()->secondService.settle(
                resolution
            ),
            "cross-player resolution"
        );

        require(
            second.bank()
                .inventoryAt(0)
                .qty==100&&
            second.equipment()
                .itemAt(
                    EquipmentSlot.AMMO
                )==892,
            "cross-player rejection mutated target"
        );
    }

    private static void emptyCarriedSettles(){
        WorldPlayer player=
            new WorldPlayer();

        synchronized(player.mutationLock()){
            player.bank()
                .replaceInventorySemantic(
                    emptyInventoryItems(),
                    new int[
                        BankState
                            .INVENTORY_CAPACITY
                    ]
                );

            int[] equipmentItems=
                new int[
                    EquipmentState
                        .EQUIPMENT_SLOTS
                ];
            Arrays.fill(
                equipmentItems,
                -1
            );

            player.equipment()
                .restoreAccountState(
                    equipmentItems,
                    new int[
                        EquipmentState
                            .EQUIPMENT_SLOTS
                    ]
                );
        }

        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            );
        require(
            lifecycle.applyDamage(
                500,
                120L,
                "empty",
                5L
            ).died,
            "empty death fixture"
        );

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolver.previewCurrentDeath();

        require(
            preview.carried.isEmpty(),
            "empty carried preview"
        );

        PlayerDeathItemResolutionService.Resolution resolution=
            resolver.resolveCurrentDeath(
                preview,
                Collections.emptyList()
            );

        PlayerDeathItemSettlementService service=
            new PlayerDeathItemSettlementService(
                player,
                AUTHORITY
            );
        PlayerDeathItemSettlementService.Receipt receipt=
            service.settle(
                resolution
            );

        require(
            receipt.lostLines.isEmpty()&&
            receipt.keptTotalQuantity==0&&
            receipt.lostTotalQuantity==0&&
            service.size()==1,
            "empty carried settlement"
        );
    }

    private static void authorityAndBoundary(){
        WorldPlayer player=
            new WorldPlayer();

        expect(
            IllegalArgumentException.class,
            ()->new PlayerDeathItemSettlementService(
                player,
                "EXACT_CURRENT_CLIENT"
            ),
            "client authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new PlayerDeathItemSettlementService(
                player,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority"
        );

        for(Class<?> type:new Class<?>[]{
                PlayerDeathItemSettlementService.class,
                PlayerDeathItemSettlementService.Receipt.class,
                PlayerDeathItemSettlementService.LostLine.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );
                String typeName=
                    field.getType()
                        .getName();

                for(String forbidden:
                        new String[]{
                            "packet",
                            "widget",
                            "opcode",
                            "socket",
                            "killer",
                            "recipient",
                            "grounditem"
                        })
                    require(
                        !name.contains(
                            forbidden
                        ),
                        "unowned identity leaked "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );

                require(
                    !typeName.contains(
                        "ServerPacketWriter")&&
                    !typeName.contains(
                        "GroundItemRegistry"),
                    "transport/ground dependency leaked "+
                    type.getSimpleName()+
                    "."+
                    field.getName()
                );
            }
        }
    }

    private static WorldPlayer configuredPlayer(){
        WorldPlayer player=
            new WorldPlayer();

        synchronized(player.mutationLock()){
            int[] inventoryItems=
                emptyInventoryItems();
            int[] inventoryQuantities=
                new int[
                    BankState
                        .INVENTORY_CAPACITY
                ];

            inventoryItems[0]=995;
            inventoryQuantities[0]=100;
            inventoryItems[1]=15272;
            inventoryQuantities[1]=3;

            player.bank()
                .replaceInventorySemantic(
                    inventoryItems,
                    inventoryQuantities
                );

            int[] equipmentItems=
                new int[
                    EquipmentState
                        .EQUIPMENT_SLOTS
                ];
            int[] equipmentQuantities=
                new int[
                    EquipmentState
                        .EQUIPMENT_SLOTS
                ];
            Arrays.fill(
                equipmentItems,
                -1
            );

            equipmentItems[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=4151;
            equipmentQuantities[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=1;
            equipmentItems[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ]=892;
            equipmentQuantities[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ]=50;

            player.equipment()
                .restoreAccountState(
                    equipmentItems,
                    equipmentQuantities
                );
        }

        return player;
    }

    private static PlayerDeathItemResolutionService.Resolution
        resolveDeath(
            WorldPlayer player,
            long tick,
            String cause
        ){
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            );

        require(
            lifecycle.applyDamage(
                500,
                tick,
                cause,
                5L
            ).died,
            "death fixture "+cause
        );

        PlayerDeathItemResolutionService resolver=
            new PlayerDeathItemResolutionService(
                player,
                AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolver.previewCurrentDeath();

        return resolver.resolveCurrentDeath(
            preview,
            decisions(preview)
        );
    }

    private static List<PlayerDeathItemResolutionService.Decision>
        decisions(
            PlayerDeathItemResolutionService.DeathPreview
                preview
        ){
        ArrayList<PlayerDeathItemResolutionService.Decision>
            out=
                new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine
                line:
                preview.carried){
            int keep;

            if(line.itemId==995)
                keep=40;
            else if(line.itemId==892)
                keep=0;
            else
                keep=line.quantity;

            out.add(
                new PlayerDeathItemResolutionService
                    .Decision(
                        line.lineId,
                        keep
                    )
            );
        }

        return out;
    }

    private static PlayerDeathItemSettlementService.LostLine
        findLost(
            List<PlayerDeathItemSettlementService.LostLine>
                lines,
            int itemId
        ){
        for(PlayerDeathItemSettlementService.LostLine line:
                lines)
            if(line.itemId==itemId)
                return line;

        throw new AssertionError(
            "missing lost item="+itemId
        );
    }

    private static int[] emptyInventoryItems(){
        int[] out=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        Arrays.fill(out,-1);
        return out;
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

    private PlayerDeathItemSettlementServiceTest(){}
}
