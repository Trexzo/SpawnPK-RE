package spk.local;

import java.util.Arrays;

/** Regression for the first explicit post-R24 LocalLab player death loop. */
public final class LocalDeathLoopServiceTest {
    public static void main(String[] args){
        carriedDeathIsResolvedExactlyOnce();
        unresolvedRespawnIsRejected();
        emptyCarriedDeathIsValid();

        System.out.println(
            "LOCAL_DEATH_LOOP_RUNTIME_PASS "+
            "keepAll=true "+
            "deathSequenceBound=true "+
            "retryIdempotent=true "+
            "unresolvedRespawnRejected=true "+
            "emptyCarried=true "+
            "authority="+
            LocalDeathLoopService.POLICY_AUTHORITY
        );
    }

    private static void carriedDeathIsResolvedExactlyOnce(){
        WorldPlayer player=configuredPlayer();
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(player);
        LocalDeathLoopService deathLoop=
            new LocalDeathLoopService(player);

        PlayerLifecycleService.DamageResult death=
            lifecycle.applyDamage(
                500,
                100L,
                "GAMEPLAY_LOOP_TEST",
                0L
            );

        require(
            death.died&&
            player.lifecycle().deathSequence()==1L,
            "first death"
        );

        PlayerLifecycleService.PreparedRespawn prepared=
            lifecycle.prepareRespawn(100L);

        require(
            prepared!=null,
            "first prepared respawn"
        );

        PlayerDeathItemResolutionService.Resolution first=
            deathLoop.ensureCurrentDeathResolved();
        PlayerDeathItemResolutionService.Resolution replay=
            deathLoop.ensureCurrentDeathResolved();

        require(
            first==replay&&
            deathLoop.resolutionCount()==1,
            "death resolution replay"
        );
        require(
            first.deathSequence==prepared.deathSequence&&
            first.deathTick==prepared.deathTick&&
            first.dispositions.size()==2&&
            first.keptTotalQuantity()==101&&
            first.lostTotalQuantity()==0&&
            LocalDeathLoopService.POLICY_AUTHORITY.equals(
                first.policyAuthority
            ),
            "first death disposition"
        );

        require(
            player.bank().inventoryAt(0).itemId==995&&
            player.bank().inventoryAt(0).qty==100&&
            player.equipment().itemAt(
                EquipmentSlot.WEAPON
                    .equipmentIndex
            )==4151&&
            player.equipment().quantityAt(
                EquipmentSlot.WEAPON
                    .equipmentIndex
            )==1,
            "keep-all mutated carried items"
        );

        deathLoop.requireResolvedForRespawn(
            prepared
        );
        lifecycle.commitPreparedRespawn(
            prepared
        );

        PlayerLifecycleService.DamageResult secondDeath=
            lifecycle.applyDamage(
                500,
                100L,
                "SECOND_SAME_TICK",
                0L
            );

        require(
            secondDeath.died&&
            player.lifecycle().deathSequence()==2L,
            "second same-tick death"
        );

        PlayerDeathItemResolutionService.Resolution second=
            deathLoop.ensureCurrentDeathResolved();

        require(
            second.deathSequence==2L&&
            second!=first&&
            deathLoop.resolutionCount()==2,
            "distinct death sequence"
        );
    }

    private static void unresolvedRespawnIsRejected(){
        WorldPlayer player=configuredPlayer();
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(player);
        LocalDeathLoopService deathLoop=
            new LocalDeathLoopService(player);

        require(
            lifecycle.applyDamage(
                500,
                200L,
                "UNRESOLVED",
                0L
            ).died,
            "unresolved death"
        );

        PlayerLifecycleService.PreparedRespawn prepared=
            lifecycle.prepareRespawn(200L);

        expect(
            IllegalStateException.class,
            ()->deathLoop.requireResolvedForRespawn(
                prepared
            ),
            "unresolved respawn"
        );

        require(
            player.lifecycle().dead()&&
            deathLoop.resolutionCount()==0,
            "unresolved respawn mutated lifecycle"
        );
    }

    private static void emptyCarriedDeathIsValid(){
        WorldPlayer player=new WorldPlayer();

        synchronized(player.mutationLock()){
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
            Arrays.fill(
                items,
                -1
            );

            player.equipment().restoreAccountState(
                items,
                quantities
            );
        }

        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(player);
        LocalDeathLoopService deathLoop=
            new LocalDeathLoopService(player);

        require(
            lifecycle.applyDamage(
                500,
                300L,
                "EMPTY",
                0L
            ).died,
            "empty death"
        );

        PlayerDeathItemResolutionService.Resolution resolution=
            deathLoop.ensureCurrentDeathResolved();

        require(
            resolution.dispositions.isEmpty()&&
            resolution.keptTotalQuantity()==0&&
            resolution.lostTotalQuantity()==0&&
            deathLoop.resolutionCount()==1,
            "empty carried resolution"
        );

        deathLoop.requireResolvedForRespawn(
            lifecycle.prepareRespawn(300L)
        );
    }

    private static WorldPlayer configuredPlayer(){
        WorldPlayer player=new WorldPlayer();

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

            player.equipment().restoreAccountState(
                items,
                quantities
            );
        }

        return player;
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
            throw new AssertionError(label);
    }

    private LocalDeathLoopServiceTest(){}
}
