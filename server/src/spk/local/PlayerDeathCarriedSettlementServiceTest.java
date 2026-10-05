package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public final class PlayerDeathCarriedSettlementServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G1_DEATH_POLICY_TEST";

    public static void main(String[] args){
        atomicCarriedPostimageAndReplay();
        policyStateDriftFailsClosed();
        unknownAuthorityRejected();

        System.out.println(
            "PLAYER_DEATH_CARRIED_SETTLEMENT_PASS "+
            "inventoryPostimage=true "+
            "equipmentPostimage=true "+
            "partialStackKeep=true "+
            "lostRows=true "+
            "replayIdempotent=true "+
            "stateDriftFailsClosed=true "+
            "groundMutation=false "+
            "policyAuthorityExplicit=true"
        );
    }

    private static void atomicCarriedPostimageAndReplay(){
        WorldPlayer player=configuredPlayer();
        kill(player,200L,"g1-pvp");

        PlayerDeathDispositionPolicy policy=
            new PlayerDeathDispositionPolicy(){
                @Override public String authority(){
                    return AUTHORITY;
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
                        else if(line.itemId==15272)
                            kept=line.quantity;
                        else if(line.itemId==4151)
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

        PlayerDeathCarriedSettlementService service=
            new PlayerDeathCarriedSettlementService(
                player,
                policy
            );

        PlayerDeathCarriedSettlementService.Receipt receipt=
            service.settleCurrentDeath();

        BankState.Stack coins=
            player.bank().inventoryAt(0);
        BankState.Stack food=
            player.bank().inventoryAt(1);

        require(
            coins!=null&&
            coins.itemId==995&&
            coins.qty==40,
            "coin postimage"
        );
        require(
            food!=null&&
            food.itemId==15272&&
            food.qty==2,
            "kept food postimage"
        );
        require(
            player.equipment().itemAt(
                EquipmentSlot.WEAPON)==4151&&
            player.equipment().quantityAt(
                EquipmentSlot.WEAPON)==1,
            "kept weapon postimage"
        );
        require(
            player.equipment().itemAt(
                EquipmentSlot.AMMO)==-1&&
            player.equipment().quantityAt(
                EquipmentSlot.AMMO)==0,
            "lost ammo postimage"
        );

        require(
            receipt.deathTick==200L&&
            receipt.deathSequence==1L&&
            receipt.keptTotalQuantity==43&&
            receipt.lostTotalQuantity==110&&
            receipt.lost.size()==2&&
            AUTHORITY.equals(receipt.policyAuthority),
            "receipt identity/totals"
        );

        require(
            lost(receipt,995)==60&&
            lost(receipt,892)==50,
            "lost rows"
        );

        PlayerDeathCarriedSettlementService.Receipt replay=
            service.settleCurrentDeath();

        require(
            replay==receipt&&
            service.size()==1&&
            player.bank().inventoryAt(0).qty==40&&
            player.equipment().itemAt(
                EquipmentSlot.AMMO)==-1,
            "replay idempotency"
        );
    }

    private static void policyStateDriftFailsClosed(){
        WorldPlayer player=configuredPlayer();
        kill(player,201L,"g1-drift");

        PlayerDeathDispositionPolicy policy=
            new PlayerDeathDispositionPolicy(){
                @Override public String authority(){
                    return AUTHORITY;
                }

                @Override
                public Collection<PlayerDeathItemResolutionService.Decision>
                    decide(
                        PlayerDeathItemResolutionService.DeathPreview preview
                    )
                {
                    /*
                     * Deliberately violate the preview contract between
                     * preview and resolution. The semantic resolver must catch
                     * this before the carried postimage service commits.
                     */
                    synchronized(player.mutationLock()){
                        player.equipment().setStack(
                            EquipmentSlot.WEAPON,
                            4151,
                            2
                        );
                    }

                    ArrayList<PlayerDeathItemResolutionService.Decision> out=
                        new ArrayList<>();
                    for(PlayerDeathItemResolutionService.CarriedLine line:
                            preview.carried)
                        out.add(
                            new PlayerDeathItemResolutionService.Decision(
                                line.lineId,
                                0
                            )
                        );
                    return out;
                }
            };

        PlayerDeathCarriedSettlementService service=
            new PlayerDeathCarriedSettlementService(
                player,
                policy
            );

        expect(
            IllegalStateException.class,
            service::settleCurrentDeath,
            "policy state drift"
        );

        require(
            service.size()==0&&
            player.bank().inventoryAt(0).qty==100&&
            player.bank().inventoryAt(1).qty==2&&
            player.equipment().itemAt(
                EquipmentSlot.WEAPON)==4151&&
            player.equipment().quantityAt(
                EquipmentSlot.WEAPON)==2&&
            player.equipment().itemAt(
                EquipmentSlot.AMMO)==892,
            "drift failure committed unrelated postimage"
        );
    }

    private static void unknownAuthorityRejected(){
        WorldPlayer player=configuredPlayer();

        expect(
            IllegalArgumentException.class,
            ()->new PlayerDeathCarriedSettlementService(
                player,
                new PlayerDeathDispositionPolicy(){
                    @Override public String authority(){
                        return "UNKNOWN_SERVER_AUTHORITY";
                    }

                    @Override
                    public Collection<PlayerDeathItemResolutionService.Decision>
                        decide(
                            PlayerDeathItemResolutionService.DeathPreview preview
                        )
                    {
                        throw new AssertionError(
                            "unknown authority policy executed"
                        );
                    }
                }
            ),
            "unknown authority"
        );
    }

    private static int lost(
        PlayerDeathCarriedSettlementService.Receipt receipt,
        int itemId
    ){
        for(PlayerDeathCarriedSettlementService.LostLine line:
                receipt.lost)
            if(line.itemId==itemId)
                return line.amount;

        return 0;
    }

    private static WorldPlayer configuredPlayer(){
        WorldPlayer player=new WorldPlayer();

        synchronized(player.mutationLock()){
            BankState.Stack[] bank=
                new BankState.Stack[BankState.BANK_CAPACITY];
            BankState.Stack[] inventory=
                new BankState.Stack[BankState.INVENTORY_CAPACITY];

            inventory[0]=new BankState.Stack(995,100);
            inventory[1]=new BankState.Stack(15272,2);

            player.bank().restoreAccountState(
                bank,
                inventory,
                false
            );

            int[] items=
                new int[EquipmentState.EQUIPMENT_SLOTS];
            int[] quantities=
                new int[EquipmentState.EQUIPMENT_SLOTS];
            Arrays.fill(items,-1);

            items[EquipmentSlot.WEAPON.equipmentIndex]=4151;
            quantities[EquipmentSlot.WEAPON.equipmentIndex]=1;
            items[EquipmentSlot.AMMO.equipmentIndex]=892;
            quantities[EquipmentSlot.AMMO.equipmentIndex]=50;

            player.equipment().restoreAccountState(
                items,
                quantities
            );
        }

        return player;
    }

    private static void kill(
        WorldPlayer player,
        long tick,
        String cause
    ){
        PlayerLifecycleService.DamageResult result=
            new PlayerLifecycleService(
                player,
                AUTHORITY
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

    private PlayerDeathCarriedSettlementServiceTest(){}
}
