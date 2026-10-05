package spk.local;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class PlayerDeathGroundSettlementServiceTest {
    private static final String DEATH_AUTHORITY =
        "CUSTOM_LOCALLAB_DEATH_ITEMS_TEST";
    private static final String SETTLEMENT_AUTHORITY =
        "CUSTOM_LOCALLAB_PLAYER_DEATH_GROUND_SETTLEMENT_TEST";

    public static void main(String[] args)throws Exception{
        mixedLossSettlementAndReplay();
        staleCarriedStateRejected();
        authorityGuards();
        boundaryGuard();

        System.out.println(
            "PLAYER_DEATH_GROUND_SETTLEMENT_PASS "+
            "mixedInventoryEquipment=true "+
            "partialStackKeep=true "+
            "capturedDeathTile=true "+
            "ownerScopedLoot=true "+
            "carriedPostimage=true "+
            "liveGroundPresentation=true "+
            "replayIdempotent=true "+
            "staleCarriedRejected=true "+
            "keepPolicyOwned=false "+
            "killerSelectionOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void mixedLossSettlementAndReplay()
        throws Exception
    {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer victim =
            new WorldPlayer();
        WorldPlayer killer =
            new WorldPlayer();

        long victimGeneration =
            world.registerPlayer(
                victim,
                "victim"
            );
        long killerGeneration =
            world.registerPlayer(
                killer,
                "killer"
            );

        try{
            configureCarried(
                victim
            );

            victim.movement()
                .restoreAccountState(
                    false,
                    100,
                    3094,
                    3497,
                    0
                );

            Tile deathTile =
                new Tile(
                    victim.movement().x(),
                    victim.movement().y(),
                    victim.movement().plane()
                );

            PlayerLifecycleService lifecycle =
                new PlayerLifecycleService(
                    victim,
                    DEATH_AUTHORITY
                );

            require(
                lifecycle.applyDamage(
                    500,
                    77L,
                    "pvp-test",
                    5L
                ).died,
                "victim death fixture"
            );

            PlayerDeathItemResolutionService deaths =
                new PlayerDeathItemResolutionService(
                    victim,
                    DEATH_AUTHORITY
                );

            PlayerDeathItemResolutionService.DeathPreview preview =
                deaths.previewCurrentDeath();

            require(
                deathTile.equals(
                    preview.deathTile
                ),
                "death tile not captured"
            );

            List<PlayerDeathItemResolutionService.Decision>
                decisions =
                    decisionsFor(
                        preview
                    );

            PlayerDeathItemResolutionService.Resolution resolution =
                deaths.resolveCurrentDeath(
                    preview,
                    decisions
                );

            require(
                deathTile.equals(
                    resolution.deathTile
                ),
                "resolution lost death tile"
            );

            /*
             * Prove settlement uses the captured death tile rather than the
             * player's later movement state.
             */
            victim.movement()
                .returnHome();

            PlayerDeathGroundSettlementService settlement =
                new PlayerDeathGroundSettlementService(
                    world,
                    victim,
                    SETTLEMENT_AUTHORITY,
                    PlayerDeathGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            PlayerDeathGroundSettlementService.Receipt receipt =
                settlement.settle(
                    resolution,
                    "killer"
                );

            require(
                receipt.playerId.equals(
                    victim.id()
                )&&
                receipt.deathTick == 77L&&
                receipt.deathSequence == 1L&&
                receipt.deathTile.equals(
                    deathTile
                )&&
                "killer".equals(
                    receipt.recipientRef
                )&&
                receipt.keptTotalQuantity == 43&&
                receipt.lostTotalQuantity == 110,
                "settlement receipt identity"
            );

            BankState.Stack coins =
                victim.bank()
                    .inventoryAt(0);
            BankState.Stack food =
                victim.bank()
                    .inventoryAt(1);

            require(
                coins != null&&
                coins.itemId == 995&&
                coins.qty == 40&&
                food != null&&
                food.itemId == 15272&&
                food.qty == 2,
                "inventory carried postimage"
            );

            require(
                victim.equipment()
                    .itemAt(
                        EquipmentSlot.WEAPON
                    ) == 4151&&
                victim.equipment()
                    .quantityAt(
                        EquipmentSlot.WEAPON
                    ) == 1&&
                victim.equipment()
                    .itemAt(
                        EquipmentSlot.AMMO
                    ) == -1&&
                victim.equipment()
                    .quantityAt(
                        EquipmentSlot.AMMO
                    ) == 0,
                "equipment carried postimage"
            );

            GroundItem lostCoins =
                world.groundItems()
                    .findOwned(
                        995,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "killer"
                    );
            GroundItem lostAmmo =
                world.groundItems()
                    .findOwned(
                        892,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "killer"
                    );

            require(
                lostCoins != null&&
                lostCoins.amount == 60&&
                lostAmmo != null&&
                lostAmmo.amount == 50,
                "lost items not materialized"
            );

            require(
                world.groundItems()
                    .findOwned(
                        995,
                        victim.movement().x(),
                        victim.movement().y(),
                        victim.movement().plane(),
                        "killer"
                    ) == null,
                "settlement used later/home tile"
            );

            List<WorldGroundItemPresentationEvents.Event>
                pending =
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            killer.id(),
                            killerGeneration,
                            System.currentTimeMillis()
                        );

            require(
                pending.size() == 2,
                "killer live ground presentation count="+
                pending.size()
            );

            PlayerDeathGroundSettlementService.Receipt replay =
                settlement.settle(
                    resolution,
                    "killer"
                );

            require(
                replay == receipt&&
                settlement.size() == 1&&
                lostCoins.amount == 60&&
                lostAmmo.amount == 50&&
                world.groundItemPresentationEvents()
                    .pendingFor(
                        killer.id(),
                        killerGeneration,
                        System.currentTimeMillis()
                    ).size() == 2,
                "replay duplicated settlement"
            );
        }finally{
            if(world.players().owns(
                    victim,
                    victimGeneration
                ))
                world.unregisterPlayer(
                    victim,
                    victimGeneration
                );

            if(world.players().owns(
                    killer,
                    killerGeneration
                ))
                world.unregisterPlayer(
                    killer,
                    killerGeneration
                );

            world.close();
        }
    }

    private static void staleCarriedStateRejected(){
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer victim =
            new WorldPlayer();

        long generation =
            world.registerPlayer(
                victim,
                "victim-stale"
            );

        try{
            configureCarried(
                victim
            );

            require(
                new PlayerLifecycleService(
                    victim,
                    DEATH_AUTHORITY
                ).applyDamage(
                    500,
                    90L,
                    "stale-test",
                    5L
                ).died,
                "stale death fixture"
            );

            PlayerDeathItemResolutionService deaths =
                new PlayerDeathItemResolutionService(
                    victim,
                    DEATH_AUTHORITY
                );

            PlayerDeathItemResolutionService.DeathPreview preview =
                deaths.previewCurrentDeath();
            PlayerDeathItemResolutionService.Resolution resolution =
                deaths.resolveCurrentDeath(
                    preview,
                    decisionsFor(
                        preview
                    )
                );

            victim.equipment()
                .setStack(
                    EquipmentSlot.AMMO,
                    892,
                    49
                );

            PlayerDeathGroundSettlementService settlement =
                new PlayerDeathGroundSettlementService(
                    world,
                    victim,
                    SETTLEMENT_AUTHORITY,
                    PlayerDeathGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            expect(
                IllegalStateException.class,
                ()->settlement.settle(
                    resolution,
                    "killer"
                ),
                "stale carried state"
            );

            require(
                settlement.size() == 0&&
                world.groundItems().size() == 0&&
                victim.bank()
                    .inventoryAt(0).qty == 100&&
                victim.equipment()
                    .quantityAt(
                        EquipmentSlot.AMMO
                    ) == 49,
                "stale settlement mutated state"
            );
        }finally{
            if(world.players().owns(
                    victim,
                    generation
                ))
                world.unregisterPlayer(
                    victim,
                    generation
                );

            world.close();
        }
    }

    private static List<PlayerDeathItemResolutionService.Decision>
        decisionsFor(
            PlayerDeathItemResolutionService.DeathPreview preview
        ){
        ArrayList<PlayerDeathItemResolutionService.Decision> out =
            new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried){
            int keep;

            if(line.itemId == 995)
                keep = 40;
            else if(line.itemId == 15272)
                keep = line.quantity;
            else if(line.itemId == 4151)
                keep = line.quantity;
            else if(line.itemId == 892)
                keep = 0;
            else
                throw new AssertionError(
                    "unexpected carried fixture item="+
                    line.itemId
                );

            out.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    keep
                )
            );
        }

        return out;
    }

    private static void configureCarried(
        WorldPlayer player
    ){
        synchronized(player.mutationLock()){
            BankState.Stack[] bank =
                new BankState.Stack[
                    BankState.BANK_CAPACITY
                ];
            BankState.Stack[] inventory =
                new BankState.Stack[
                    BankState.INVENTORY_CAPACITY
                ];

            inventory[0] =
                new BankState.Stack(
                    995,
                    100
                );
            inventory[1] =
                new BankState.Stack(
                    15272,
                    2
                );

            player.bank()
                .restoreAccountState(
                    bank,
                    inventory,
                    false
                );

            int[] items =
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            int[] quantities =
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
            ] = 4151;
            quantities[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ] = 1;
            items[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ] = 892;
            quantities[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ] = 50;

            player.equipment()
                .restoreAccountState(
                    items,
                    quantities
                );
        }
    }

    private static void authorityGuards(){
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer player =
            new WorldPlayer();

        try{
            expect(
                IllegalArgumentException.class,
                ()->new PlayerDeathGroundSettlementService(
                    world,
                    player,
                    "EXACT_CURRENT_CLIENT",
                    PlayerDeathGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                ),
                "client settlement authority"
            );

            expect(
                IllegalArgumentException.class,
                ()->new PlayerDeathGroundSettlementService(
                    world,
                    player,
                    "UNKNOWN_SERVER_AUTHORITY",
                    PlayerDeathGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                ),
                "unknown settlement authority"
            );
        }finally{
            world.close();
        }
    }

    private static void boundaryGuard(){
        for(Field field:
                PlayerDeathGroundSettlementService.class
                    .getDeclaredFields()){
            String name =
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            for(String forbidden:
                    new String[]{
                        "packet",
                        "widget",
                        "opcode",
                        "sceneindex",
                        "itemvalue",
                        "protectitem",
                        "skull"
                    })
                require(
                    !name.contains(
                        forbidden
                    ),
                    "protocol/unowned policy leaked through "+
                    field.getName()
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
            if(type.isInstance(
                    failure
                ))
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
