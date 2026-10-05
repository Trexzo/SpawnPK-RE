package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public final class PlayerDeathWorldSettlementServiceTest {
    private static final String ITEM_AUTHORITY=
        "CUSTOM_LOCALLAB_G1_DEATH_ITEMS_TEST";
    private static final String GROUND_AUTHORITY=
        "CUSTOM_LOCALLAB_G1_DEATH_GROUND_TEST";

    public static void main(String[] args){
        pvpDeathSettlesCarriedAndGroundExactlyOnce();
        missingAttributionFailsBeforeMutation();
        attackerLogoutStillRetainsDropOwner();
        authorityGuards();

        System.out.println(
            "PLAYER_DEATH_WORLD_SETTLEMENT_PASS "+
            "typedAttribution=true "+
            "frozenDeathTile=true "+
            "inventoryEquipmentPostimage=true "+
            "killerOwnedGround=true "+
            "groundBatch=true "+
            "replayNoDuplicate=true "+
            "missingAttributionFailsClosed=true "+
            "attackerLogoutOwnerStable=true "+
            "originalServerEconomicsClaimed=false"
        );
    }

    private static void pvpDeathSettlesCarriedAndGroundExactlyOnce(){
        Fixture fixture=new Fixture();

        try{
            configureVictim(fixture.victim);
            kill(fixture.victim,300L,"pvp-g1");
            fixture.world.playerDeathAttributions().record(
                fixture.attacker,
                fixture.attacker.generation(),
                fixture.victim,
                fixture.victim.generation()
            );

            PlayerDeathWorldSettlementService service=
                service(fixture);

            PlayerDeathWorldSettlementService.Receipt receipt=
                service.settleCurrentPvpDeath();

            require(
                receipt.deathTick==300L&&
                receipt.deathSequence==1L&&
                receipt.attackerId.equals(
                    fixture.attacker.id())&&
                receipt.victimId.equals(
                    fixture.victim.id())&&
                receipt.deathTile.equals(
                    tile(fixture.victim))&&
                receipt.ground.size()==2,
                "world settlement receipt"
            );

            assertCarriedPostimage(
                fixture.victim
            );
            assertGround(
                fixture,
                995,
                60
            );
            assertGround(
                fixture,
                892,
                50
            );

            PlayerDeathWorldSettlementService.Receipt replay=
                service.settleCurrentPvpDeath();

            require(
                replay==receipt&&
                service.size()==1,
                "world settlement replay identity"
            );

            assertGround(
                fixture,
                995,
                60
            );
            assertGround(
                fixture,
                892,
                50
            );
        }finally{
            fixture.close();
        }
    }

    private static void missingAttributionFailsBeforeMutation(){
        Fixture fixture=new Fixture();

        try{
            configureVictim(fixture.victim);
            kill(fixture.victim,301L,"missing-attribution");

            PlayerDeathWorldSettlementService service=
                service(fixture);

            expect(
                IllegalStateException.class,
                service::settleCurrentPvpDeath,
                "missing attribution"
            );

            require(
                fixture.victim.bank().inventoryAt(0).qty==100&&
                fixture.victim.equipment().itemAt(
                    EquipmentSlot.AMMO)==892&&
                fixture.world.groundItems().size()==0,
                "missing attribution mutated state"
            );
        }finally{
            fixture.close();
        }
    }

    private static void attackerLogoutStillRetainsDropOwner(){
        Fixture fixture=new Fixture();

        try{
            configureVictim(fixture.victim);
            kill(fixture.victim,302L,"attacker-logout");

            PlayerDeathAttributionRegistry.Attribution attribution=
                fixture.world.playerDeathAttributions().record(
                    fixture.attacker,
                    fixture.attacker.generation(),
                    fixture.victim,
                    fixture.victim.generation()
                );

            fixture.world.unregisterPlayer(
                fixture.attacker
            );

            PlayerDeathWorldSettlementService.Receipt receipt=
                service(fixture)
                    .settleCurrentPvpDeath();

            require(
                receipt.attackerId.equals(
                    attribution.attackerId)&&
                receipt.ground.size()==2,
                "logout attribution lost"
            );

            GroundItem coins=
                fixture.world.groundItems()
                    .findVisible(
                        995,
                        attribution.deathTile.x,
                        attribution.deathTile.y,
                        attribution.deathTile.plane,
                        attribution.attackerRef
                    );

            require(
                coins!=null&&
                coins.amount==60&&
                attribution.attackerRef.equals(
                    coins.owner),
                "logout drop owner drift"
            );
        }finally{
            fixture.close();
        }
    }

    private static void authorityGuards(){
        Fixture fixture=new Fixture();

        try{
            expect(
                IllegalArgumentException.class,
                ()->new PlayerDeathWorldSettlementService(
                    fixture.world,
                    fixture.victim,
                    dispositionPolicy(),
                    new PlayerDeathGroundDropPolicy(){
                        @Override public String authority(){
                            return "UNKNOWN_SERVER_AUTHORITY";
                        }

                        @Override public String ownerRef(
                            PlayerDeathAttributionRegistry.Attribution attribution,
                            PlayerDeathCarriedSettlementService.Receipt carried
                        ){
                            return attribution.attackerRef;
                        }

                        @Override public boolean devOwned(){
                            return false;
                        }
                    }
                ),
                "unknown ground authority"
            );
        }finally{
            fixture.close();
        }
    }

    private static PlayerDeathWorldSettlementService service(
        Fixture fixture
    ){
        return new PlayerDeathWorldSettlementService(
            fixture.world,
            fixture.victim,
            dispositionPolicy(),
            new PlayerDeathGroundDropPolicy(){
                @Override public String authority(){
                    return GROUND_AUTHORITY;
                }

                @Override public String ownerRef(
                    PlayerDeathAttributionRegistry.Attribution attribution,
                    PlayerDeathCarriedSettlementService.Receipt carried
                ){
                    return attribution.attackerRef;
                }

                @Override public boolean devOwned(){
                    return false;
                }
            }
        );
    }

    private static PlayerDeathDispositionPolicy dispositionPolicy(){
        return new PlayerDeathDispositionPolicy(){
            @Override public String authority(){
                return ITEM_AUTHORITY;
            }

            @Override
            public Collection<PlayerDeathItemResolutionService.Decision>
                decide(
                    PlayerDeathItemResolutionService.DeathPreview preview
                )
            {
                ArrayList<PlayerDeathItemResolutionService.Decision> out=
                    new ArrayList<>();

                for(PlayerDeathItemResolutionService.CarriedLine line:
                        preview.carried){
                    int kept;

                    if(line.itemId==995)
                        kept=40;
                    else if(line.itemId==15272||
                            line.itemId==4151)
                        kept=line.quantity;
                    else if(line.itemId==892)
                        kept=0;
                    else
                        throw new AssertionError(
                            "unexpected fixture item "+line.itemId
                        );

                    out.add(
                        new PlayerDeathItemResolutionService.Decision(
                            line.lineId,
                            kept
                        )
                    );
                }

                return out;
            }
        };
    }

    private static void assertCarriedPostimage(
        WorldPlayer victim
    ){
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
            food.qty==2&&
            victim.equipment().itemAt(
                EquipmentSlot.WEAPON)==4151&&
            victim.equipment().quantityAt(
                EquipmentSlot.WEAPON)==1&&
            victim.equipment().itemAt(
                EquipmentSlot.AMMO)==-1,
            "carried postimage"
        );
    }

    private static void assertGround(
        Fixture fixture,
        int itemId,
        int amount
    ){
        Tile death=tile(fixture.victim);

        GroundItem item=
            fixture.world.groundItems()
                .findVisible(
                    itemId,
                    death.x,
                    death.y,
                    death.plane,
                    fixture.attacker.username()
                );

        require(
            item!=null&&
            item.amount==amount&&
            fixture.attacker.username()
                .equals(item.owner),
            "ground item "+itemId
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

    private static void configureVictim(
        WorldPlayer player
    ){
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
            Arrays.fill(items,-1);

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
    }

    private static void kill(
        WorldPlayer player,
        long tick,
        String cause
    ){
        PlayerLifecycleService.DamageResult result=
            new PlayerLifecycleService(
                player,
                ITEM_AUTHORITY
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
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private static final class Fixture {
        final World world=World.isolatedForTest(900L);
        final WorldPlayer attacker=new WorldPlayer();
        final WorldPlayer victim=new WorldPlayer();

        Fixture(){
            world.registerPlayer(
                attacker,
                "attacker"
            );
            world.registerPlayer(
                victim,
                "victim"
            );
        }

        void close(){
            try{
                if(world.players().owns(
                        attacker,
                        attacker.generation()))
                    world.unregisterPlayer(
                        attacker
                    );
            }catch(Exception ignored){}

            try{
                if(world.players().owns(
                        victim,
                        victim.generation()))
                    world.unregisterPlayer(
                        victim
                    );
            }catch(Exception ignored){}

            world.close();
        }
    }

    private PlayerDeathWorldSettlementServiceTest(){}
}
