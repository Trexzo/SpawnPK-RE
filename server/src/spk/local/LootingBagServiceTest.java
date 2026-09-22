package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class LootingBagServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_LOOTING_BAG";

    public static void main(String[] args){
        LootingBagService service=
            new LootingBagService();

        LootingBagService.DepositResult alice=
            service.confirmExternalDeposit(
                " Player:Alice ",
                "receipt:alice:1",
                Arrays.asList(
                    new LootingBagService
                        .StackSpec(
                            100,
                            10L
                        ),
                    new LootingBagService
                        .StackSpec(
                            100,
                            5L
                        )
                ),
                POLICY
            );

        require(
            alice.changed&&
            "player:alice".equals(
                alice.bag.ownerRef
            )&&
            alice.bag.capacity==
                LootingBagService.CAPACITY&&
            alice.bag.size()==2&&
            alice.bag.slots.get(0)
                .itemId==100&&
            alice.bag.slots.get(1)
                .itemId==100&&
            LootingBagService
                .PRESENTATION_AUTHORITY
                .equals(
                    alice.bag
                        .presentationAuthority
                ),
            "Looting Bag external deposit/no-merge"
        );

        LootingBagService.DepositResult replay=
            service.confirmExternalDeposit(
                "player:alice",
                "RECEIPT:ALICE:1",
                Arrays.asList(
                    new LootingBagService
                        .StackSpec(
                            100,
                            10L
                        ),
                    new LootingBagService
                        .StackSpec(
                            100,
                            5L
                        )
                ),
                POLICY
            );

        require(
            !replay.changed&&
            replay.bag.size()==2,
            "Looting Bag receipt idempotency"
        );

        expect(
            IllegalStateException.class,
            ()->service
                .confirmExternalDeposit(
                    "player:alice",
                    "receipt:alice:1",
                    Collections.singletonList(
                        new LootingBagService
                            .StackSpec(
                                999,
                                1L
                            )
                    ),
                    POLICY
                ),
            "Looting Bag receipt replay mismatch"
        );

        service.confirmExternalDeposit(
            "player:bob",
            "receipt:bob:1",
            Collections.singletonList(
                new LootingBagService
                    .StackSpec(
                        200,
                        3L
                    )
            ),
            POLICY
        );

        require(
            service.get(
                "player:alice"
            ).size()==2&&
            service.get(
                "player:bob"
            ).size()==1,
            "Looting Bag player isolation"
        );

        partialBankSettlement(service);
        bulkBankSettlement(service);
        capacityAtomicity(service);
        immutableSnapshots(service);
        protocolBoundary();

        System.out.println(
            "LOOTING_BAG_SERVICE_PASS "+
            "exactCapacity28=true "+
            "normalizedPlayerIdentity=true "+
            "playerIsolation=true "+
            "batchIntakeAtomic=true "+
            "runtimeReceiptIdempotent=true "+
            "noImplicitStackMerge=true "+
            "partialBankReservation=true "+
            "doubleReservationPrevented=true "+
            "cancelRestoresAvailability=true "+
            "confirmAfterExternalBankSettlement=true "+
            "emptySlotRemoved=true "+
            "bulkDepositAll=true "+
            "bulkReservationFence=true "+
            "terminalSettlementIdempotent=true "+
            "acceptedItemPolicyOwned=false "+
            "wildernessPolicyOwned=false "+
            "deathPolicyOwned=false "+
            "bankMutation=false "+
            "inventoryMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void partialBankSettlement(
        LootingBagService service
    ){
        LootingBagService.Snapshot before=
            service.get(
                "player:alice"
            );

        LootingBagService.SlotId slot=
            before.slots.get(0)
                .slotId;

        LootingBagService.SettlementSnapshot
            reservation=
                service.beginBankDeposit(
                    "player:alice",
                    slot,
                    4L
                );

        LootingBagService.SlotSnapshot
            reserved=
                service.get(
                    "player:alice"
                ).slot(slot);

        require(
            reserved.amount==10L&&
            reserved.reserved==4L&&
            reserved.available==6L&&
            reservation.lines.size()==1,
            "Looting Bag partial bank reservation"
        );

        expect(
            IllegalStateException.class,
            ()->service.beginBankDeposit(
                "player:alice",
                slot,
                7L
            ),
            "Looting Bag double reservation"
        );

        require(
            service.cancelBankDeposit(
                reservation.settlementId
            )&&
            !service.cancelBankDeposit(
                reservation.settlementId
            ),
            "Looting Bag bank cancellation"
        );

        LootingBagService.SlotSnapshot restored=
            service.get(
                "player:alice"
            ).slot(slot);

        require(
            restored.amount==10L&&
            restored.reserved==0L&&
            restored.available==10L,
            "Looting Bag cancel availability"
        );

        LootingBagService.SettlementSnapshot
            settled=
                service.beginBankDeposit(
                    "player:alice",
                    slot,
                    4L
                );

        require(
            service.confirmBankDeposit(
                settled.settlementId
            )&&
            !service.confirmBankDeposit(
                settled.settlementId
            ),
            "Looting Bag bank settlement acknowledgement"
        );

        LootingBagService.SlotSnapshot after=
            service.get(
                "player:alice"
            ).slot(slot);

        require(
            after.amount==6L&&
            after.reserved==0L&&
            after.available==6L,
            "Looting Bag decrement after bank confirm"
        );

        LootingBagService.SettlementSnapshot
            remainder=
                service.beginBankDeposit(
                    "player:alice",
                    slot,
                    6L
                );

        service.confirmBankDeposit(
            remainder.settlementId
        );

        require(
            service.get(
                "player:alice"
            ).slot(slot)==null,
            "Looting Bag empty slot retained"
        );
    }

    private static void bulkBankSettlement(
        LootingBagService service
    ){
        service.confirmExternalDeposit(
            "player:alice",
            "receipt:alice:bulk",
            Arrays.asList(
                new LootingBagService
                    .StackSpec(
                        300,
                        3L
                    ),
                new LootingBagService
                    .StackSpec(
                        301,
                        4L
                    )
            ),
            POLICY
        );

        LootingBagService.Snapshot before=
            service.get(
                "player:alice"
            );

        LootingBagService.SlotId first=
            before.slots.get(0)
                .slotId;

        LootingBagService.SettlementSnapshot
            partial=
                service.beginBankDeposit(
                    "player:alice",
                    first,
                    1L
                );

        expect(
            IllegalStateException.class,
            ()->service.beginBankDepositAll(
                "player:alice"
            ),
            "Looting Bag bulk with active reservation"
        );

        service.cancelBankDeposit(
            partial.settlementId
        );

        LootingBagService.SettlementSnapshot
            all=
                service.beginBankDepositAll(
                    "player:alice"
                );

        require(
            all.lines.size()==
                service.get(
                    "player:alice"
                ).size(),
            "Looting Bag deposit-all reservation"
        );

        service.confirmBankDeposit(
            all.settlementId
        );

        require(
            service.get(
                "player:alice"
            ).size()==0,
            "Looting Bag deposit-all did not empty bag"
        );
    }

    private static void capacityAtomicity(
        LootingBagService service
    ){
        ArrayList<LootingBagService.StackSpec>
            twentySeven=
                new ArrayList<>();

        for(int i=0;i<27;i++)
            twentySeven.add(
                new LootingBagService
                    .StackSpec(
                        1000+i,
                        1L
                    )
            );

        service.confirmExternalDeposit(
            "player:capacity",
            "receipt:capacity:27",
            twentySeven,
            POLICY
        );

        expect(
            IllegalStateException.class,
            ()->service
                .confirmExternalDeposit(
                    "player:capacity",
                    "receipt:capacity:overflow",
                    Arrays.asList(
                        new LootingBagService
                            .StackSpec(
                                2000,
                                1L
                            ),
                        new LootingBagService
                            .StackSpec(
                                2001,
                                1L
                            )
                    ),
                    POLICY
                ),
            "Looting Bag capacity overflow"
        );

        require(
            service.get(
                "player:capacity"
            ).size()==27,
            "failed Looting Bag batch mutated contents"
        );

        service.confirmExternalDeposit(
            "player:capacity",
            "receipt:capacity:last",
            Collections.singletonList(
                new LootingBagService
                    .StackSpec(
                        3000,
                        1L
                    )
            ),
            POLICY
        );

        require(
            service.get(
                "player:capacity"
            ).full()&&
            service.get(
                "player:capacity"
            ).size()==28,
            "exact Looting Bag capacity"
        );
    }

    private static void immutableSnapshots(
        LootingBagService service
    ){
        boolean slotsImmutable=false;

        try{
            service.get(
                "player:capacity"
            ).slots.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            slotsImmutable=true;
        }

        require(
            slotsImmutable,
            "Looting Bag slots mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                LootingBagService.class,
                LootingBagService.Snapshot.class,
                LootingBagService.SlotSnapshot.class,
                LootingBagService.SettlementSnapshot.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("interface")||
                   name.contains("wilderness")||
                   name.contains("deathloss")||
                   name.contains("fee")||
                   name.contains("cooldown"))
                    throw new AssertionError(
                        "protocol/unknown-policy identity leaked into Looting Bag "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );

                String typeName=
                    field.getType()
                        .getName();

                if(typeName.contains(
                        "BankState")||
                   typeName.contains(
                        "Inventory"))
                    throw new AssertionError(
                        "runtime bank/inventory dependency leaked into Looting Bag "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
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

    private LootingBagServiceTest(){}
}
