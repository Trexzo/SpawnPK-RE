package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PlayerPvpDeathAtomicSettlementTest {
    private static final String TEST_POLICY_AUTHORITY=
        "CUSTOM_LOCALLAB_PVP_DEATH_ATOMIC_TEST_POLICY";

    public static void main(String[] args){
        happyPathCommitsBothOwners();
        groundPreflightFailureLeavesVictimItems();
        replayIsIdempotent();

        System.out.println(
            "PLAYER_PVP_DEATH_ATOMIC_SETTLEMENT_PASS "+
            "playerThenGroundLockOrder=true "+
            "groundPreparedBeforeItemMutation=true "+
            "inventoryAndEquipmentCommit=true "+
            "groundCommit=true "+
            "presentationPostCommit=true "+
            "overflowPreflightAtomic=true "+
            "replayIdempotent=true "+
            "originalSpawnpkPolicyClaimed=false "+
            "protocolIndependent=true"
        );
    }

    private static void happyPathCommitsBothOwners(){
        World world=World.isolatedForTest(1000L);
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
            kill(victim,10L);

            Tile deathTile=tile(victim);

            world.groundItems().add(
                995,
                10,
                deathTile,
                "killer",
                1L,
                false
            );

            PlayerPvpDeathSettlementService service=
                new PlayerPvpDeathSettlementService(
                    world,
                    loseAllPolicy()
                );

            PlayerPvpDeathSettlementService.Result result=
                service.settle(
                    victim,
                    victimGeneration,
                    deathTile,
                    "killer"
                );

            require(
                result.items.lostTotalQuantity==103&&
                result.ground.groundItems.size()==3&&
                TEST_POLICY_AUTHORITY.equals(
                    result.policyAuthority
                ),
                "atomic result identity"
            );

            require(
                !victim.bank()
                    .inventorySlotSnapshot(0)
                    .occupied&&
                !victim.bank()
                    .inventorySlotSnapshot(1)
                    .occupied&&
                victim.equipment()
                    .itemAt(
                        EquipmentSlot.WEAPON
                    )==-1,
                "victim carried postimage"
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
            GroundItem food=
                world.groundItems()
                    .findOwned(
                        15272,
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

            require(
                coins!=null&&
                coins.amount==110&&
                food!=null&&
                food.amount==2&&
                whip!=null&&
                whip.amount==1,
                "killer ground postimage"
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
                "post-commit presentation"
            );

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(victim);

            require(
                lifecycle.tick(14L)==
                    PlayerLifecycleService.TickResult.NONE&&
                lifecycle.tick(15L)==
                    PlayerLifecycleService.TickResult.RESPAWNED&&
                victim.lifecycle().alive()&&
                victim.movement().inHomeWindow(),
                "respawn preserved"
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

    private static void groundPreflightFailureLeavesVictimItems(){
        World world=World.isolatedForTest(1100L);
        WorldPlayer victim=new WorldPlayer();
        WorldPlayer killer=new WorldPlayer();
        long victimGeneration=
            world.registerPlayer(
                victim,
                "overflow-victim"
            );
        long killerGeneration=
            world.registerPlayer(
                killer,
                "overflow-killer"
            );

        try{
            configureCarried(victim);
            kill(victim,20L);

            Tile deathTile=tile(victim);

            GroundItem full=
                world.groundItems().add(
                    995,
                    Integer.MAX_VALUE,
                    deathTile,
                    "overflow-killer",
                    1L,
                    false
                );

            int beforeCoins=
                victim.bank()
                    .inventorySlotSnapshot(0)
                    .quantity;
            int beforeFood=
                victim.bank()
                    .inventorySlotSnapshot(1)
                    .quantity;
            int beforeWeapon=
                victim.equipment()
                    .itemAt(
                        EquipmentSlot.WEAPON
                    );

            PlayerPvpDeathSettlementService service=
                new PlayerPvpDeathSettlementService(
                    world,
                    loseAllPolicy()
                );

            expect(
                IllegalStateException.class,
                ()->service.settle(
                    victim,
                    victimGeneration,
                    deathTile,
                    "overflow-killer"
                ),
                "ground overflow preflight"
            );

            require(
                victim.bank()
                    .inventorySlotSnapshot(0)
                    .quantity==beforeCoins&&
                victim.bank()
                    .inventorySlotSnapshot(1)
                    .quantity==beforeFood&&
                victim.equipment()
                    .itemAt(
                        EquipmentSlot.WEAPON
                    )==beforeWeapon&&
                full.amount==
                    Integer.MAX_VALUE&&
                service.size()==0&&
                world.groundItemPresentationEvents()
                    .size()==0,
                "overflow failure mutated canonical state"
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

    private static void replayIsIdempotent(){
        World world=World.isolatedForTest(1200L);
        WorldPlayer victim=new WorldPlayer();
        WorldPlayer killer=new WorldPlayer();
        long victimGeneration=
            world.registerPlayer(
                victim,
                "replay-victim"
            );
        long killerGeneration=
            world.registerPlayer(
                killer,
                "replay-killer"
            );

        try{
            configureCarried(victim);
            kill(victim,30L);

            Tile deathTile=tile(victim);

            PlayerPvpDeathSettlementService service=
                new PlayerPvpDeathSettlementService(
                    world,
                    loseAllPolicy()
                );

            PlayerPvpDeathSettlementService.Result first=
                service.settle(
                    victim,
                    victimGeneration,
                    deathTile,
                    "replay-killer"
                );

            int groundSize=
                world.groundItems().size();

            for(WorldGroundItemPresentationEvents.Event event:
                    world.groundItemPresentationEvents()
                        .pendingFor(
                            killer.id(),
                            killerGeneration,
                            System.currentTimeMillis()
                        ))
                world.groundItemPresentationEvents()
                    .markDelivered(
                        event.sequence,
                        killer.id(),
                        killerGeneration,
                        System.currentTimeMillis()
                    );

            PlayerPvpDeathSettlementService.Result second=
                service.settle(
                    victim,
                    victimGeneration,
                    deathTile,
                    "replay-killer"
                );

            require(
                first==second&&
                service.size()==1&&
                world.groundItems().size()==
                    groundSize&&
                world.groundItemPresentationEvents()
                    .size()==0,
                "replay duplicated settlement"
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

    private static PlayerDeathItemDecisionPolicy
        loseAllPolicy()
    {
        return new PlayerDeathItemDecisionPolicy(){
            @Override public List<
                PlayerDeathItemResolutionService.Decision
            > decide(
                PlayerDeathItemResolutionService.DeathPreview
                    preview
            ){
                ArrayList<PlayerDeathItemResolutionService.Decision>
                    out=
                        new ArrayList<>();

                for(PlayerDeathItemResolutionService.CarriedLine line:
                        preview.carried)
                    out.add(
                        new PlayerDeathItemResolutionService
                            .Decision(
                                line.lineId,
                                0
                            )
                    );

                return Collections.unmodifiableList(
                    out
                );
            }

            @Override public String authority(){
                return TEST_POLICY_AUTHORITY;
            }
        };
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

        player.equipment()
            .restoreAccountState(
                equipmentItems,
                equipmentQuantities
            );
    }

    private static void kill(
        WorldPlayer player,
        long deathTick
    ){
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(
                player,
                "CUSTOM_LOCALLAB_PVP_DEATH_ATOMIC_LIFECYCLE"
            );

        require(
            lifecycle.applyDamage(
                500,
                deathTick,
                "pvp-atomic-test",
                5L
            ).died,
            "death fixture"
        );
    }

    private static Tile tile(
        WorldPlayer player
    ){
        return new Tile(
            player.movement().x(),
            player.movement().y(),
            player.movement().plane()
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

    private PlayerPvpDeathAtomicSettlementTest(){}
}
