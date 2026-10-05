package spk.local;

/**
 * Conservative exact-death settlement regression.
 *
 * Proves that only explicit AUTO_LOSS + destroy=true rows mutate carried state.
 * Ambiguous AUTO_LOSS and standard rules are preserved until their destination
 * / ordering is separately recovered or intentionally defined.
 */
public final class PlayerDeathSettlementServiceTest {
    public static void main(String[] args){
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        EquipmentState equipment=player.equipment();

        BankState.Stack[] inventory=
            bank.inventoryContainerSnapshot();
        inventory[0]=new BankState.Stack(28117,3); // AUTO_LOSS + destroy=true
        inventory[1]=new BankState.Stack(24254,1); // AUTO_LOSS, destination unresolved
        inventory[2]=new BankState.Stack(26113,2); // AUTO_KEEP_EXPLICIT
        inventory[3]=new BankState.Stack(995,500); // STANDARD_UNRESOLVED
        bank.restoreInventoryContainerSnapshot(inventory);

        equipment.setStack(
            EquipmentSlot.WEAPON,
            28124,
            1
        ); // AUTO_LOSS + destroy=true
        equipment.setStack(
            EquipmentSlot.HEAD,
            1163,
            1
        ); // STANDARD_UNRESOLVED

        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(player);

        PlayerLifecycleService.DamageResult death=
            lifecycle.applyDamage(
                Integer.MAX_VALUE,
                100L,
                "PVP_TEST"
            );

        require(death.died,"death fixture");

        PlayerDeathSettlementService settlement=
            new PlayerDeathSettlementService(player);

        PlayerDeathSettlementService.Receipt receipt=
            settlement.settleCurrentDeath();

        require(
            receipt.destroyedLines==2,
            "destroyed lines"
        );
        require(
            receipt.destroyedQuantity==4,
            "destroyed quantity"
        );
        require(
            receipt.explicitKeeps==1,
            "explicit keep count"
        );
        require(
            receipt.deferredAutoLoss==1,
            "deferred auto-loss count"
        );
        require(
            receipt.standardUnresolved>=2,
            "standard unresolved count"
        );

        require(
            bank.inventoryAt(0)==null,
            "explicit destroy inventory line remained"
        );
        require(
            bank.inventoryAt(1)!=null&&
            bank.inventoryAt(1).itemId==24254&&
            bank.inventoryAt(1).qty==1,
            "ambiguous auto-loss was invented as destruction"
        );
        require(
            bank.inventoryAt(2)!=null&&
            bank.inventoryAt(2).itemId==26113&&
            bank.inventoryAt(2).qty==2,
            "explicit auto-keep changed"
        );
        require(
            bank.inventoryAt(3)!=null&&
            bank.inventoryAt(3).itemId==995&&
            bank.inventoryAt(3).qty==500,
            "standard unresolved item changed"
        );

        require(
            equipment.itemAt(
                EquipmentSlot.WEAPON
            )==-1,
            "explicit destroy equipment line remained"
        );
        require(
            equipment.itemAt(
                EquipmentSlot.HEAD
            )==1163,
            "standard unresolved equipment changed"
        );

        require(
            settlement.settleCurrentDeath()==receipt,
            "same death did not return exact receipt"
        );

        require(
            DeathPolicyRepository.get(28117)
                .destroyExplicit,
            "destroy authority missing item 28117"
        );
        require(
            DeathPolicyRepository.get(28124)
                .destroyExplicit,
            "destroy authority missing item 28124"
        );
        require(
            !DeathPolicyRepository.get(24254)
                .destroyExplicit,
            "ambiguous auto-loss destination widened"
        );

        PlayerSnapshot persisted=
            PlayerSnapshotCodec.capture(
                "death-settlement",
                player
            );
        WorldPlayer restored=
            new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(
            persisted,
            restored
        );

        require(
            restored.bank().inventoryAt(0)==null,
            "destroyed inventory item resurrected after persistence"
        );
        require(
            restored.bank().inventoryAt(1)!=null&&
            restored.bank().inventoryAt(1).itemId==24254,
            "deferred auto-loss missing after persistence"
        );
        require(
            restored.bank().inventoryAt(2)!=null&&
            restored.bank().inventoryAt(2).itemId==26113,
            "explicit keep missing after persistence"
        );
        require(
            restored.equipment().itemAt(
                EquipmentSlot.WEAPON
            )==-1,
            "destroyed equipment resurrected after persistence"
        );
        require(
            restored.equipment().itemAt(
                EquipmentSlot.HEAD
            )==1163,
            "standard equipment missing after persistence"
        );

        System.out.println(
            "PLAYER_DEATH_CONSERVATIVE_SETTLEMENT_PASS "+
            "destroyExplicitOnly=true "+
            "destroyedLines=2 destroyedQuantity=4 "+
            "ambiguousAutoLossPreserved=true "+
            "standardUnresolvedPreserved=true "+
            "explicitKeepPreserved=true "+
            "retryIdempotent=true "+
            "snapshotRoundTrip=true "+
            "authority="+
            PlayerDeathSettlementService.POLICY_AUTHORITY
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private PlayerDeathSettlementServiceTest(){}
}
