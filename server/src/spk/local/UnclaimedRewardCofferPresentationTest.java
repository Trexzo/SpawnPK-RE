package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public final class UnclaimedRewardCofferPresentationTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_REWARD_COFFER_PRESENTATION";

    public static void main(String[] args)
        throws Exception
    {
        UnclaimedRewardCofferService service=
            new UnclaimedRewardCofferService();

        UnclaimedRewardCofferService.Snapshot coffer=
            service.deliver(
                "player:alice",
                "delivery:one",
                Arrays.asList(
                    new RewardDeliveryMessage.Attachment(
                        100,
                        10L
                    ),
                    new RewardDeliveryMessage.Attachment(
                        200,
                        20L
                    )
                ),
                POLICY
            );

        UnclaimedRewardCofferPresentation.Projection
            projection=
                UnclaimedRewardCofferPresentation
                    .project(coffer);

        exactProjection(projection);
        exactPerItemInputs(projection);
        exactBulkInputs();
        staleIdentityFence(service,projection);
        failClosedBoundaries(service);
        authorityBoundary();

        System.out.println(
            "REWARD_COFFER_PRESENTATION_PASS "+
            "root42100=true "+
            "container42101=true "+
            "capacity70=true "+
            "s2c53=true "+
            "remove1Opcode145=true "+
            "remove5Opcode117=true "+
            "remove10Opcode43=true "+
            "removeAllOpcode129=true "+
            "bulkInventory42104=true "+
            "bulkBank42108=true "+
            "hoverWidgetsNonAction=true "+
            "slotIdentityFence=true "+
            "perItemDestinationOwned=false "+
            "persistenceOwned=false "+
            "offlineAtomicityOwned=false"
        );
    }

    private static void exactProjection(
        UnclaimedRewardCofferPresentation
            .Projection projection
    )throws Exception{
        require(
            projection.slots.size()==2&&
            projection.slots.get(0).clientSlot==0&&
            projection.slots.get(0).itemId==100&&
            projection.slots.get(0).quantity==10&&
            projection.slots.get(1).clientSlot==1&&
            projection.slots.get(1).itemId==200&&
            projection.slots.get(1).quantity==20,
            "ordered 70-slot projection"
        );

        byte[] body=
            projection.containerBody();

        require(
            body.length>=10&&
            (body[0]&255)==0xa4&&
            (body[1]&255)==0x75&&
            (body[2]&255)==0x00&&
            (body[3]&255)==0x46,
            "S2C53 coffer header"
        );

        require(
            (body[4]&255)==10&&
            (body[5]&255)==0xe5&&
            (body[6]&255)==0x00,
            "first coffer stack wire"
        );

        byte[] root=
            BootstrapPackets.interface97(
                UnclaimedRewardCofferPresentation
                    .ROOT
            );

        require(
            root.length==2&&
            (root[0]&255)==0xa4&&
            (root[1]&255)==0x74,
            "root 42100 S2C97 body"
        );
    }

    private static void exactPerItemInputs(
        UnclaimedRewardCofferPresentation
            .Projection projection
    ){
        requireRemove(
            projection,
            145,
            0,
            100,
            UnclaimedRewardCofferPresentation
                .AmountMode.ONE
        );
        requireRemove(
            projection,
            117,
            0,
            100,
            UnclaimedRewardCofferPresentation
                .AmountMode.FIVE
        );
        requireRemove(
            projection,
            43,
            1,
            200,
            UnclaimedRewardCofferPresentation
                .AmountMode.TEN
        );
        requireRemove(
            projection,
            129,
            1,
            200,
            UnclaimedRewardCofferPresentation
                .AmountMode.ALL
        );

        require(
            UnclaimedRewardCofferPresentation
                .resolveItemAction(
                    new ItemContainerAction(
                        145,
                        3214,
                        0,
                        100,
                        0,
                        "ITEM_ACTION_1"
                    ),
                    projection
                )==null,
            "unrelated container ignored"
        );

        expect(
            IllegalArgumentException.class,
            ()->UnclaimedRewardCofferPresentation
                .resolveItemAction(
                    new ItemContainerAction(
                        135,
                        42101,
                        0,
                        100,
                        0,
                        "ITEM_ACTION_X"
                    ),
                    projection
                ),
            "unconfigured option-X rejected"
        );
    }

    private static void exactBulkInputs(){
        UnclaimedRewardCofferPresentation.BulkIntent
            inventory=
                UnclaimedRewardCofferPresentation
                    .resolveWidget(42104);

        UnclaimedRewardCofferPresentation.BulkIntent
            bank=
                UnclaimedRewardCofferPresentation
                    .resolveWidget(42108);

        require(
            inventory!=null&&
            inventory.destination==
                UnclaimedRewardCofferService
                    .Destination
                    .INVENTORY,
            "42104 bulk inventory"
        );

        require(
            bank!=null&&
            bank.destination==
                UnclaimedRewardCofferService
                    .Destination
                    .BANK,
            "42108 bulk bank"
        );

        require(
            UnclaimedRewardCofferPresentation
                .resolveWidget(42105)==null&&
            UnclaimedRewardCofferPresentation
                .resolveWidget(42109)==null,
            "hover ids not actions"
        );
    }

    private static void staleIdentityFence(
        UnclaimedRewardCofferService service,
        UnclaimedRewardCofferPresentation
            .Projection before
    ){
        UnclaimedRewardCofferService.SlotId first=
            before.slots.get(0).slotId;

        UnclaimedRewardCofferService.SettlementSnapshot
            reservation=
                service.beginSettlement(
                    "player:alice",
                    first,
                    10L,
                    UnclaimedRewardCofferService
                        .Destination
                        .INVENTORY
                );

        service.confirmSettlement(
            reservation.settlementId
        );

        UnclaimedRewardCofferPresentation.Projection
            after=
                UnclaimedRewardCofferPresentation
                    .project(
                        service.get(
                            "player:alice"
                        )
                    );

        require(
            after.slots.size()==1&&
            after.slots.get(0).itemId==200,
            "post-settlement projection compacted"
        );

        expect(
            IllegalStateException.class,
            ()->UnclaimedRewardCofferPresentation
                .resolveItemAction(
                    new ItemContainerAction(
                        145,
                        42101,
                        0,
                        100,
                        0,
                        "ITEM_ACTION_1"
                    ),
                    after
                ),
            "stale coffer slot/item identity accepted"
        );
    }

    private static void failClosedBoundaries(
        UnclaimedRewardCofferService service
    ){
        UnclaimedRewardCofferPresentation.Projection
            projection=
                UnclaimedRewardCofferPresentation
                    .project(
                        service.get(
                            "player:alice"
                        )
                    );

        expect(
            IllegalStateException.class,
            ()->UnclaimedRewardCofferPresentation
                .resolveItemAction(
                    new ItemContainerAction(
                        145,
                        42101,
                        69,
                        200,
                        0,
                        "ITEM_ACTION_1"
                    ),
                    projection
                ),
            "empty coffer client slot accepted"
        );

        expect(
            IllegalArgumentException.class,
            ()->UnclaimedRewardCofferPresentation
                .resolveItemAction(
                    new ItemContainerAction(
                        145,
                        42101,
                        0,
                        200,
                        1,
                        "ITEM_ACTION_1"
                    ),
                    projection
                ),
            "unexpected coffer item extra accepted"
        );

        UnclaimedRewardCofferService large=
            new UnclaimedRewardCofferService();

        UnclaimedRewardCofferService.Snapshot
            tooLarge=
                large.deliver(
                    "player:large",
                    "delivery:large",
                    Collections.singletonList(
                        new RewardDeliveryMessage
                            .Attachment(
                                300,
                                ((long)Integer.MAX_VALUE)+1L
                            )
                    ),
                    POLICY
                );

        expect(
            IllegalArgumentException.class,
            ()->UnclaimedRewardCofferPresentation
                .project(tooLarge),
            "coffer quantity beyond S2C53 i32"
        );

        expect(
            IllegalArgumentException.class,
            ()->UnclaimedRewardCofferPresentation
                .resolveWidget(-1),
            "negative widget"
        );
    }

    private static void requireRemove(
        UnclaimedRewardCofferPresentation
            .Projection projection,
        int opcode,
        int clientSlot,
        int itemId,
        UnclaimedRewardCofferPresentation
            .AmountMode amountMode
    ){
        UnclaimedRewardCofferPresentation.RemoveIntent
            intent=
                UnclaimedRewardCofferPresentation
                    .resolveItemAction(
                        new ItemContainerAction(
                            opcode,
                            42101,
                            clientSlot,
                            itemId,
                            0,
                            "ignored"
                        ),
                        projection
                    );

        require(
            intent!=null&&
            intent.clientSlot==clientSlot&&
            intent.itemId==itemId&&
            intent.amountMode==amountMode&&
            intent.slotId.equals(
                projection.slots
                    .get(clientSlot)
                    .slotId
            ),
            "coffer item action "+
            opcode+
            " -> "+
            amountMode
        );
    }

    private static void authorityBoundary(){
        require(
            UnclaimedRewardCofferPresentation
                .CAPACITY==
            UnclaimedRewardCofferService
                .CAPACITY,
            "coffer capacity parity"
        );

        for(Field field:
                UnclaimedRewardCofferPresentation
                    .class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("expiry")||
               name.contains("fallback")||
               name.contains("persist")||
               name.contains("duplicatepolicy")||
               name.contains("overflowpolicy"))
                throw new AssertionError(
                    "unowned reward-coffer policy field "+
                    field.getName()
                );
        }

        for(Method method:
                UnclaimedRewardCofferPresentation
                    .class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("settle")||
               name.contains("enqueue")||
               name.contains("expire")||
               name.contains("persist")||
               name.contains("fallback"))
                throw new AssertionError(
                    "unowned reward-coffer behavior method "+
                    method.getName()
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

    private UnclaimedRewardCofferPresentationTest(){}
}
