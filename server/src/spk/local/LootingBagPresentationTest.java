package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public final class LootingBagPresentationTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_LOOTING_BAG_PRESENTATION";

    public static void main(String[] args)
        throws Exception
    {
        LootingBagService service=
            new LootingBagService();

        LootingBagService.Snapshot bag=
            service.confirmExternalDeposit(
                "player:alice",
                "receipt:1",
                Arrays.asList(
                    new LootingBagService.StackSpec(
                        100,
                        10L
                    ),
                    new LootingBagService.StackSpec(
                        200,
                        20L
                    )
                ),
                POLICY
            ).bag;

        LootingBagPresentation.Projection projection=
            LootingBagPresentation.project(bag);

        exactProjection(projection);
        exactPerSlotInputs(projection);
        staleIdentityFence(service,projection);
        failClosedBoundaries(service);
        authorityBoundary();

        System.out.println(
            "LOOTING_BAG_PRESENTATION_PASS "+
            "root26700=true "+
            "container26706=true "+
            "capacity28=true "+
            "s2c53=true "+
            "deposit1Opcode145=true "+
            "deposit5Opcode117=true "+
            "deposit10Opcode43=true "+
            "depositAllOpcode129=true "+
            "slotIdentityFence=true "+
            "bulkWidget26708Visible=true "+
            "bulkWidgetTransportOwned=false "+
            "wildernessPolicyOwned=false "+
            "deathPolicyOwned=false "+
            "bankSettlementOwned=false"
        );
    }

    private static void exactProjection(
        LootingBagPresentation.Projection projection
    )throws Exception{
        require(
            projection.slots.size()==2&&
            projection.slots.get(0).clientSlot==0&&
            projection.slots.get(0).itemId==100&&
            projection.slots.get(0).quantity==10&&
            projection.slots.get(1).clientSlot==1&&
            projection.slots.get(1).itemId==200&&
            projection.slots.get(1).quantity==20,
            "ordered 28-slot projection"
        );

        byte[] body=projection.containerBody();

        require(
            body.length>=10&&
            (body[0]&255)==0x68&&
            (body[1]&255)==0x52&&
            (body[2]&255)==0x00&&
            (body[3]&255)==0x1c,
            "S2C53 widget/capacity header"
        );

        require(
            (body[4]&255)==10&&
            (body[5]&255)==0xe5&&
            (body[6]&255)==0x00,
            "first projected stack wire"
        );

        require(
            LootingBagPresentation.ROOT==26700&&
            LootingBagPresentation
                .CONTAINER_WIDGET==26706&&
            LootingBagPresentation
                .EMPTY_TEXT_WIDGET==26707&&
            LootingBagPresentation
                .BULK_DEPOSIT_WIDGET==26708,
            "exact Looting Bag widgets"
        );

        byte[] root=
            BootstrapPackets.interface97(
                LootingBagPresentation.ROOT
            );

        require(
            root.length==2&&
            (root[0]&255)==0x68&&
            (root[1]&255)==0x4c,
            "root 26700 S2C97 body"
        );
    }

    private static void exactPerSlotInputs(
        LootingBagPresentation.Projection projection
    ){
        requireIntent(
            projection,
            145,
            0,
            100,
            LootingBagPresentation
                .AmountMode.ONE
        );
        requireIntent(
            projection,
            117,
            0,
            100,
            LootingBagPresentation
                .AmountMode.FIVE
        );
        requireIntent(
            projection,
            43,
            1,
            200,
            LootingBagPresentation
                .AmountMode.TEN
        );
        requireIntent(
            projection,
            129,
            1,
            200,
            LootingBagPresentation
                .AmountMode.ALL
        );

        require(
            LootingBagPresentation
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
            ()->LootingBagPresentation
                .resolveItemAction(
                    new ItemContainerAction(
                        135,
                        26706,
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

    private static void staleIdentityFence(
        LootingBagService service,
        LootingBagPresentation.Projection before
    ){
        LootingBagService.SlotId first=
            before.slots.get(0).slotId;

        LootingBagService.SettlementSnapshot
            reservation=
                service.beginBankDeposit(
                    "player:alice",
                    first,
                    10L
                );

        service.confirmBankDeposit(
            reservation.settlementId
        );

        LootingBagPresentation.Projection after=
            LootingBagPresentation.project(
                service.get("player:alice")
            );

        require(
            after.slots.size()==1&&
            after.slots.get(0).itemId==200,
            "post-settlement projection compacts semantic order"
        );

        expect(
            IllegalStateException.class,
            ()->LootingBagPresentation
                .resolveItemAction(
                    new ItemContainerAction(
                        145,
                        26706,
                        0,
                        100,
                        0,
                        "ITEM_ACTION_1"
                    ),
                    after
                ),
            "stale client slot/item identity accepted"
        );
    }

    private static void failClosedBoundaries(
        LootingBagService service
    ){
        LootingBagPresentation.Projection projection=
            LootingBagPresentation.project(
                service.get("player:alice")
            );

        expect(
            IllegalStateException.class,
            ()->LootingBagPresentation
                .resolveItemAction(
                    new ItemContainerAction(
                        145,
                        26706,
                        27,
                        200,
                        0,
                        "ITEM_ACTION_1"
                    ),
                    projection
                ),
            "empty client slot accepted"
        );

        expect(
            IllegalArgumentException.class,
            ()->LootingBagPresentation
                .resolveItemAction(
                    new ItemContainerAction(
                        145,
                        26706,
                        0,
                        200,
                        1,
                        "ITEM_ACTION_1"
                    ),
                    projection
                ),
            "unexpected item action extra accepted"
        );

        LootingBagService large=
            new LootingBagService();

        LootingBagService.Snapshot tooLarge=
            large.confirmExternalDeposit(
                "player:large",
                "receipt:large",
                Collections.singletonList(
                    new LootingBagService.StackSpec(
                        300,
                        ((long)Integer.MAX_VALUE)+1L
                    )
                ),
                POLICY
            ).bag;

        expect(
            IllegalArgumentException.class,
            ()->LootingBagPresentation
                .project(tooLarge),
            "quantity beyond S2C53 signed i32"
        );
    }

    private static void requireIntent(
        LootingBagPresentation.Projection projection,
        int opcode,
        int clientSlot,
        int itemId,
        LootingBagPresentation.AmountMode amountMode
    ){
        LootingBagPresentation.DepositIntent intent=
            LootingBagPresentation
                .resolveItemAction(
                    new ItemContainerAction(
                        opcode,
                        26706,
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
            "item action "+
            opcode+
            " -> "+
            amountMode
        );
    }

    private static void authorityBoundary(){
        require(
            LootingBagPresentation.CAPACITY==
                LootingBagService.CAPACITY,
            "capacity parity"
        );

        for(Field field:
                LootingBagPresentation.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("wilderness")||
               name.contains("deathloss")||
               name.contains("fee")||
               name.contains("cooldown")||
               name.contains("itemadmission"))
                throw new AssertionError(
                    "unowned Looting Bag policy field "+
                    field.getName()
                );
        }

        for(Method method:
                LootingBagPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("resolvebulk")||
               name.contains("depositallwidget")||
               name.contains("applybank")||
               name.contains("persist")||
               name.contains("ondeath"))
                throw new AssertionError(
                    "unowned Looting Bag behavior method "+
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

    private LootingBagPresentationTest(){}
}
