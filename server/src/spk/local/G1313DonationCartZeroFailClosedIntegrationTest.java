package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

public final class G1313DonationCartZeroFailClosedIntegrationTest {
    private static final int[] SEED={201,202,203,204};

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalDonationCartUiHandler cart=
            new LocalDonationCartUiHandler();
        int actionCalls;
        LocalDonationCartUiHandler.Result lastResult;

        @Override public void saveAccount(
            String tag,
            String reason
        ){}

        @Override public void clearDialogNumberKeys(){}

        @Override public void handleDevPanelWidget(
            int widget,
            ServerPacketWriter writer,
            String tag
        )throws IOException{}

        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        ){}

        @Override public boolean retireDonationCartRoot(){
            return cart.close();
        }

        @Override public boolean openDonationCart(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            cart.open(writer);
            return true;
        }

        @Override public LocalDonationCartUiHandler.Result
            handleDonationCartInput(
                DonationCartPresentation.Input input,
                String tag
            )throws IOException{
            actionCalls++;
            lastResult=
                cart.handle(input);
            return lastResult;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean customCommand=false;
        boolean root60200=false;
        boolean productSlots8=false;
        boolean quantitiesZero8=false;
        boolean actionWidgets19Exact=false;
        boolean allActionsDisabled=false;
        boolean closedUiNoop=false;
        boolean interfaceCloseRetires=false;
        boolean competingRootRetires=false;
        boolean donationCartServiceCreated=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g1313-player"
        );

        try{
            customCommand=
                LocalCommandDispatcher
                    .isDonationCartRoute(
                        new String[]{"donationcart"}
                    )&&
                LocalCommandDispatcher
                    .isDonationCartRoute(
                        new String[]{"donatecart"}
                    )&&
                !LocalCommandDispatcher
                    .isDonationCartRoute(
                        new String[]{"donationcart","invent"}
                    );

            require(
                customCommand,
                "Donation Cart LocalLab route"
            );

            productSlots8=
                DonationCartPresentation
                    .PRODUCT_SLOTS==8;

            require(
                productSlots8,
                "Donation Cart exact product capacity"
            );

            int[] decrease={
                60227,60233,60239,60245,
                60251,60257,60263,60284
            };
            int[] increase={
                60230,60236,60242,60248,
                60254,60260,60266,60287
            };

            int[] actions=new int[19];
            int p=0;
            for(int widget:decrease)
                actions[p++]=widget;
            for(int widget:increase)
                actions[p++]=widget;
            actions[p++]=
                DonationCartPresentation
                    .CHECKOUT_WIDGET;
            actions[p++]=
                DonationCartPresentation
                    .PAYPAL_WIDGET;
            actions[p++]=
                DonationCartPresentation
                    .OSRS_GP_WIDGET;

            actionWidgets19Exact=
                p==19;

            for(int i=0;i<8;i++){
                DonationCartPresentation.Input down=
                    DonationCartPresentation
                        .resolveWidget(
                            decrease[i]
                        );
                DonationCartPresentation.Input up=
                    DonationCartPresentation
                        .resolveWidget(
                            increase[i]
                        );

                actionWidgets19Exact&=
                    down!=null&&
                    down.kind==
                        DonationCartPresentation
                            .InputKind
                            .ADJUST_QUANTITY&&
                    down.productIndex==i&&
                    down.quantityDelta==-1&&
                    up!=null&&
                    up.kind==
                        DonationCartPresentation
                            .InputKind
                            .ADJUST_QUANTITY&&
                    up.productIndex==i&&
                    up.quantityDelta==1;
            }

            DonationCartPresentation.Input checkout=
                DonationCartPresentation
                    .resolveWidget(
                        DonationCartPresentation
                            .CHECKOUT_WIDGET
                    );
            DonationCartPresentation.Input paypal=
                DonationCartPresentation
                    .resolveWidget(
                        DonationCartPresentation
                            .PAYPAL_WIDGET
                    );
            DonationCartPresentation.Input osrs=
                DonationCartPresentation
                    .resolveWidget(
                        DonationCartPresentation
                            .OSRS_GP_WIDGET
                    );

            actionWidgets19Exact&=
                checkout!=null&&
                checkout.kind==
                    DonationCartPresentation
                        .InputKind
                        .PREPARE_CHECKOUT&&
                paypal!=null&&
                paypal.kind==
                    DonationCartPresentation
                        .InputKind
                        .SELECT_PAYMENT&&
                paypal.paymentMode==
                    DonationCartService
                        .PaymentMode.PAYPAL&&
                osrs!=null&&
                osrs.kind==
                    DonationCartPresentation
                        .InputKind
                        .SELECT_PAYMENT&&
                osrs.paymentMode==
                    DonationCartService
                        .PaymentMode.OSRS_GP;

            require(
                actionWidgets19Exact,
                "Donation Cart exact action map"
            );

            Bridge bridge=
                new Bridge();
            LocalSessionUiActionHandler ui=
                uiHandler(
                    player,
                    bridge
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        SEED.clone()
                    )
                );

            String opened=
                ui.replaceMonsterSpawnerWithDonationCartRoot(
                    ()->{
                        bridge.openDonationCart(
                            writer,
                            "[g1313-open] "
                        );
                        return "DONATION_CART_ROOT_OPENED";
                    }
                );

            byte[] bytes=
                wire.toByteArray();
            IsaacCipher decode=
                new IsaacCipher(
                    SEED.clone()
                );
            int offset=0;

            int rootOpcode=
                ((bytes[offset++]&255)-
                    decode.nextInt())&
                    255;
            int root=
                ((bytes[offset++]&255)<<8)|
                (bytes[offset++]&255);

            root60200=
                "DONATION_CART_ROOT_OPENED"
                    .equals(opened)&&
                bridge.cart.isOpen()&&
                rootOpcode==97&&
                root==
                    DonationCartPresentation
                        .ROOT&&
                root==60200;

            require(
                root60200,
                "Donation Cart exact root"
            );

            quantitiesZero8=true;

            for(int i=0;i<8;i++){
                int opcode=
                    ((bytes[offset++]&255)-
                        decode.nextInt())&
                        255;
                int length=
                    ((bytes[offset++]&255)<<8)|
                    (bytes[offset++]&255);

                quantitiesZero8&=
                    opcode==126&&
                    length==4&&
                    offset+length<=bytes.length;

                if(!quantitiesZero8)
                    break;

                int textByte=
                    bytes[offset++]&255;
                int newline=
                    bytes[offset++]&255;
                int target=
                    ((bytes[offset++]&255)<<8)|
                    (((bytes[offset++]&255)-128)&255);

                quantitiesZero8&=
                    textByte=='0'&&
                    newline==10&&
                    target==
                        DonationCartPresentation
                            .quantityWidget(i);
            }

            quantitiesZero8&=
                offset==bytes.length;

            require(
                quantitiesZero8,
                "Donation Cart zero quantity wire projection"
            );

            allActionsDisabled=true;

            for(int widget:actions){
                int before=
                    bridge.actionCalls;

                ui.handleWidget(
                    widget,
                    writer,
                    "[g1313-action] "
                );

                allActionsDisabled&=
                    bridge.actionCalls==
                        before+1&&
                    bridge.lastResult!=null&&
                    "DISABLED_NO_COMMERCE_AUTHORITY"
                        .equals(
                            bridge.lastResult.status
                        )&&
                    !bridge.lastResult.succeeded&&
                    bridge.cart.isOpen();
            }

            require(
                allActionsDisabled,
                "Donation Cart action escaped fail-closed shell"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g1313-interface-close] "
            );

            interfaceCloseRetires=
                !bridge.cart.isOpen();

            require(
                interfaceCloseRetires,
                "interface close did not retire Donation Cart"
            );

            int closedCalls=
                bridge.actionCalls;

            ui.handleWidget(
                DonationCartPresentation
                    .CHECKOUT_WIDGET,
                writer,
                "[g1313-closed-checkout] "
            );

            closedUiNoop=
                bridge.actionCalls==
                    closedCalls;

            require(
                closedUiNoop,
                "closed Donation Cart widget escaped root gate"
            );

            LocalDonationCartUiHandler.Result directClosed=
                bridge.cart.handle(
                    checkout
                );

            closedUiNoop&=
                "CLOSED_UI_NOOP".equals(
                    directClosed.status
                )&&
                !directClosed.succeeded;

            require(
                closedUiNoop,
                "closed Donation Cart handler did not no-op"
            );

            ui.replaceMonsterSpawnerWithDonationCartRoot(
                ()->{
                    bridge.openDonationCart(
                        writer,
                        "[g1313-reopen] "
                    );
                    return "DONATION_CART_ROOT_OPENED";
                }
            );

            require(
                bridge.cart.isOpen(),
                "competing-root precondition"
            );

            ui.replaceMonsterSpawnerWithDonorPanelRoot(
                ()->"DONOR_PANEL_ROOT_OPENED"
            );

            competingRootRetires=
                !bridge.cart.isOpen();

            require(
                competingRootRetires,
                "competing root did not retire Donation Cart"
            );

            for(Field field:
                    LocalDonationCartUiHandler
                        .class
                        .getDeclaredFields())
                if(field.getType()==
                        DonationCartService.class)
                    donationCartServiceCreated=true;

            require(
                !donationCartServiceCreated,
                "Donation Cart shell created semantic cart service"
            );

            System.out.println(
                "G1313_DONATION_CART_ZERO_FAIL_CLOSED_PASS"+
                " customCommand="+customCommand+
                " root60200="+root60200+
                " productSlots8="+productSlots8+
                " quantitiesZero8="+quantitiesZero8+
                " actionWidgets19Exact="+
                    actionWidgets19Exact+
                " allActionsDisabled="+
                    allActionsDisabled+
                " closedUiNoop="+closedUiNoop+
                " interfaceCloseRetires="+
                    interfaceCloseRetires+
                " competingRootRetires="+
                    competingRootRetires+
                " donationCartServiceCreated="+
                    donationCartServiceCreated+
                " catalogClaim=false"+
                " pricingClaim=false"+
                " checkoutClaim=false"+
                " paymentProcessingClaim=false"+
                " settlementClaim=false"+
                " fulfillmentClaim=false"+
                " persistenceClaim=false"+
                " originalNavigationClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static LocalSessionUiActionHandler uiHandler(
        WorldPlayer player,
        Bridge bridge
    ){
        BankState bank=player.bank();
        EquipmentState equipment=
            player.equipment();
        MovementState movement=
            player.movement();
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        NpcRegistry npcs=
            new NpcRegistry(dev);

        LocalPetInventoryDialogHandler petDialogs=
            new LocalPetInventoryDialogHandler(
                bank,
                player.miniPets(),
                player.petState(),
                npcs,
                movement,
                player.petAccessoryState()
            );

        LocalGameplayWidgetHandler gameplay=
            new LocalGameplayWidgetHandler(
                player.prayers(),
                player.playerState(),
                equipment,
                player.combatStyles(),
                player.magic(),
                bank
            );

        LocalCompCapeCustomizeHandler compCape=
            new LocalCompCapeCustomizeHandler(
                bank,
                player.playerState()
            );

        return new LocalSessionUiActionHandler(
            player,
            new NativeItemLibraryService(),
            new DevControlCenter(),
            bank,
            compCape,
            petDialogs,
            gameplay,
            movement,
            true,
            equipment,
            bridge
        );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G1313DonationCartZeroFailClosedIntegrationTest(){}
}
