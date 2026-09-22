package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class UnclaimedRewardCofferServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_REWARD_COFFER";

    public static void main(String[] args){
        UnclaimedRewardCofferService service=
            new UnclaimedRewardCofferService();

        UnclaimedRewardCofferService.Snapshot alice=
            service.deliver(
                " Player:Alice ",
                "event:contest:001",
                Arrays.asList(
                    new RewardDeliveryMessage
                        .Attachment(
                            100,
                            10L
                        ),
                    new RewardDeliveryMessage
                        .Attachment(
                            100,
                            5L
                        )
                ),
                POLICY
            );

        require(
            "player:alice".equals(
                alice.ownerRef
            )&&
            alice.capacity==
                UnclaimedRewardCofferService
                    .CAPACITY&&
            alice.size()==2&&
            alice.slots.get(0).itemId==100&&
            alice.slots.get(1).itemId==100,
            "coffer delivery/no-merge"
        );

        service.deliver(
            "player:bob",
            "event:contest:001",
            Collections.singletonList(
                new RewardDeliveryMessage
                    .Attachment(
                        200,
                        7L
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
            "coffer player isolation"
        );

        expect(
            IllegalStateException.class,
            ()->service.deliver(
                "player:alice",
                "event:contest:001",
                Collections.singletonList(
                    new RewardDeliveryMessage
                        .Attachment(
                            999,
                            1L
                        )
                ),
                POLICY
            ),
            "duplicate coffer delivery"
        );

        require(
            service.get(
                "player:alice"
            ).size()==2,
            "duplicate delivery mutated coffer"
        );

        partialSettlement(service);
        bulkSettlement(service);
        deliveryCapacityAtomicity(service);
        runtimeDeliveryIdempotency(service);
        immutableSnapshots(service);
        protocolBoundary();

        System.out.println(
            "UNCLAIMED_REWARD_COFFER_PASS "+
            "exactCapacity70=true "+
            "deliveryBatchAtomic=true "+
            "runtimeDeliveryIdempotent=true "+
            "playerIsolation=true "+
            "noImplicitStackMerge=true "+
            "partialReservation=true "+
            "doubleReservationPrevented=true "+
            "cancelRestoresAvailability=true "+
            "confirmAfterExternalSettlement=true "+
            "emptySlotRemoved=true "+
            "bulkInventoryIntent=true "+
            "bulkBankIntent=true "+
            "bulkReservationFence=true "+
            "terminalSettlementIdempotent=true "+
            "inventoryMutation=false "+
            "bankMutation=false "+
            "rewardGeneration=false "+
            "protocolIndependent=true"
        );
    }

    private static void partialSettlement(
        UnclaimedRewardCofferService service
    ){
        UnclaimedRewardCofferService.Snapshot
            before=
                service.get(
                    "player:alice"
                );

        UnclaimedRewardCofferService.SlotId slot=
            before.slots.get(0)
                .slotId;

        UnclaimedRewardCofferService.SettlementSnapshot
            reservation=
                service.beginSettlement(
                    "player:alice",
                    slot,
                    4L,
                    UnclaimedRewardCofferService
                        .Destination.INVENTORY
                );

        UnclaimedRewardCofferService.SlotSnapshot
            reserved=
                service.get(
                    "player:alice"
                ).slot(slot);

        require(
            reserved.amount==10L&&
            reserved.reserved==4L&&
            reserved.available==6L&&
            reservation.destination==
                UnclaimedRewardCofferService
                    .Destination.INVENTORY,
            "partial coffer reservation"
        );

        expect(
            IllegalStateException.class,
            ()->service.beginSettlement(
                "player:alice",
                slot,
                7L,
                UnclaimedRewardCofferService
                    .Destination.BANK
            ),
            "double reservation over available"
        );

        require(
            service.cancelSettlement(
                reservation.settlementId
            )&&
            !service.cancelSettlement(
                reservation.settlementId
            ),
            "coffer settlement cancellation"
        );

        UnclaimedRewardCofferService.SlotSnapshot
            restored=
                service.get(
                    "player:alice"
                ).slot(slot);

        require(
            restored.amount==10L&&
            restored.reserved==0L&&
            restored.available==10L,
            "cancel did not restore coffer availability"
        );

        UnclaimedRewardCofferService.SettlementSnapshot
            settled=
                service.beginSettlement(
                    "player:alice",
                    slot,
                    4L,
                    UnclaimedRewardCofferService
                        .Destination.INVENTORY
                );

        // Confirm is the post-external-settlement acknowledgement.
        require(
            service.confirmSettlement(
                settled.settlementId
            )&&
            !service.confirmSettlement(
                settled.settlementId
            ),
            "coffer settlement acknowledgement"
        );

        UnclaimedRewardCofferService.SlotSnapshot
            after=
                service.get(
                    "player:alice"
                ).slot(slot);

        require(
            after.amount==6L&&
            after.reserved==0L&&
            after.available==6L,
            "coffer quantity did not decrement after confirm"
        );

        UnclaimedRewardCofferService.SettlementSnapshot
            remainder=
                service.beginSettlement(
                    "player:alice",
                    slot,
                    6L,
                    UnclaimedRewardCofferService
                        .Destination.BANK
                );

        service.confirmSettlement(
            remainder.settlementId
        );

        require(
            service.get(
                "player:alice"
            ).slot(slot)==null,
            "emptied coffer slot retained"
        );
    }

    private static void bulkSettlement(
        UnclaimedRewardCofferService service
    ){
        service.deliver(
            "player:alice",
            "event:bulk:002",
            Arrays.asList(
                new RewardDeliveryMessage
                    .Attachment(
                        300,
                        3L
                    ),
                new RewardDeliveryMessage
                    .Attachment(
                        301,
                        4L
                    )
            ),
            POLICY
        );

        UnclaimedRewardCofferService.Snapshot
            before=
                service.get(
                    "player:alice"
                );

        UnclaimedRewardCofferService.SlotId
            first=
                before.slots.get(0)
                    .slotId;

        UnclaimedRewardCofferService.SettlementSnapshot
            partial=
                service.beginSettlement(
                    "player:alice",
                    first,
                    1L,
                    UnclaimedRewardCofferService
                        .Destination.INVENTORY
                );

        expect(
            IllegalStateException.class,
            ()->service.beginBulkSettlement(
                "player:alice",
                UnclaimedRewardCofferService
                    .Destination.BANK
            ),
            "bulk coffer settlement with active reservation"
        );

        service.cancelSettlement(
            partial.settlementId
        );

        UnclaimedRewardCofferService.SettlementSnapshot
            bank=
                service.beginBulkSettlement(
                    "player:alice",
                    UnclaimedRewardCofferService
                        .Destination.BANK
                );

        require(
            bank.destination==
                UnclaimedRewardCofferService
                    .Destination.BANK&&
            bank.lines.size()==
                service.get(
                    "player:alice"
                ).size(),
            "bulk bank coffer intent"
        );

        service.cancelSettlement(
            bank.settlementId
        );

        UnclaimedRewardCofferService.SettlementSnapshot
            inventory=
                service.beginBulkSettlement(
                    "player:alice",
                    UnclaimedRewardCofferService
                        .Destination.INVENTORY
                );

        require(
            inventory.destination==
                UnclaimedRewardCofferService
                    .Destination.INVENTORY,
            "bulk inventory coffer intent"
        );

        service.confirmSettlement(
            inventory.settlementId
        );

        require(
            service.get(
                "player:alice"
            ).size()==0,
            "bulk coffer confirm did not empty slots"
        );
    }

    private static void deliveryCapacityAtomicity(
        UnclaimedRewardCofferService service
    ){
        ArrayList<
            RewardDeliveryMessage.Attachment
        > sixtyNine=
            new ArrayList<>();

        for(int i=0;i<69;i++)
            sixtyNine.add(
                new RewardDeliveryMessage
                    .Attachment(
                        1000+i,
                        1L
                    )
            );

        service.deliver(
            "player:capacity",
            "delivery:capacity:69",
            sixtyNine,
            POLICY
        );

        expect(
            IllegalStateException.class,
            ()->service.deliver(
                "player:capacity",
                "delivery:capacity:overflow",
                Arrays.asList(
                    new RewardDeliveryMessage
                        .Attachment(
                            2000,
                            1L
                        ),
                    new RewardDeliveryMessage
                        .Attachment(
                            2001,
                            1L
                        )
                ),
                POLICY
            ),
            "coffer capacity overflow"
        );

        require(
            service.get(
                "player:capacity"
            ).size()==69,
            "failed coffer batch mutated capacity state"
        );

        service.deliver(
            "player:capacity",
            "delivery:capacity:last",
            Collections.singletonList(
                new RewardDeliveryMessage
                    .Attachment(
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
            ).size()==70,
            "exact coffer capacity"
        );
    }

    private static void runtimeDeliveryIdempotency(
        UnclaimedRewardCofferService service
    ){
        UnclaimedRewardCofferService.Snapshot
            initial=
                service.deliver(
                    "player:idempotent",
                    "delivery:once",
                    Collections.singletonList(
                        new RewardDeliveryMessage
                            .Attachment(
                                4000,
                                2L
                            )
                    ),
                    POLICY
                );

        UnclaimedRewardCofferService.SettlementSnapshot
            settlement=
                service.beginSettlement(
                    "player:idempotent",
                    initial.slots.get(0)
                        .slotId,
                    2L,
                    UnclaimedRewardCofferService
                        .Destination.INVENTORY
                );

        service.confirmSettlement(
            settlement.settlementId
        );

        require(
            service.get(
                "player:idempotent"
            ).size()==0,
            "idempotent coffer not emptied"
        );

        expect(
            IllegalStateException.class,
            ()->service.deliver(
                "player:idempotent",
                "delivery:once",
                Collections.singletonList(
                    new RewardDeliveryMessage
                        .Attachment(
                            4000,
                            2L
                        )
                ),
                POLICY
            ),
            "consumed delivery key accepted again"
        );
    }

    private static void immutableSnapshots(
        UnclaimedRewardCofferService service
    ){
        boolean immutable=false;

        try{
            service.get(
                "player:capacity"
            ).slots.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "coffer slot snapshot mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                UnclaimedRewardCofferService.class,
                UnclaimedRewardCofferService.Snapshot.class,
                UnclaimedRewardCofferService.SlotSnapshot.class,
                UnclaimedRewardCofferService.SettlementSnapshot.class
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
                   name.contains("rewardgenerator")||
                   name.contains("itemvalue"))
                    throw new AssertionError(
                        "protocol/reward-generation identity leaked into coffer "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );

                String typeName=
                    field.getType()
                        .getName();

                if(typeName.contains(
                        "Inventory")||
                   typeName.contains(
                        "Bank"))
                    throw new AssertionError(
                        "direct inventory/bank mutation dependency "+
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

    private UnclaimedRewardCofferServiceTest(){}
}
