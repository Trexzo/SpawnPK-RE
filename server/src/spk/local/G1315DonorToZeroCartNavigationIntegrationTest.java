package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class G1315DonorToZeroCartNavigationIntegrationTest {
    private static final int[] SEED={221,222,223,224};

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalDonorPanelUiHandler donor=
            new LocalDonorPanelUiHandler();
        final LocalDonationCartUiHandler cart=
            new LocalDonationCartUiHandler();

        int donorLiveCalls;
        int cartCalls;
        LocalDonorPanelUiHandler.Result lastDonor;
        LocalDonationCartUiHandler.Result lastCart;

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

        @Override public boolean retireDonorPanelRoot(){
            return donor.close();
        }

        @Override public boolean openDonorPanel(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            donor.open(writer);
            return true;
        }

        @Override public LocalDonorPanelUiHandler.Result
            handleDonorPanelLiveIntent(
                DonorPanelPresentation.Intent intent,
                String tag
            )throws IOException{
            donorLiveCalls++;
            lastDonor=
                donor.handleLive(intent);
            return lastDonor;
        }

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
            cartCalls++;
            lastCart=
                cart.handle(input);
            return lastCart;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean donorRoot60062=false;
        boolean donate60073=false;
        boolean donorToCart=false;
        boolean donorRetired=false;
        boolean cartRoot60200=false;
        boolean quantitiesZero8=false;
        boolean cartActionsRemainDisabled=false;
        boolean otherDonorActionsDisabled=false;
        boolean closedDonorWidgetNoop=false;
        boolean compatibilityG1312Preserved=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g1315-player"
        );

        try{
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
                ui.replaceMonsterSpawnerWithDonorPanelRoot(
                    ()->{
                        bridge.openDonorPanel(
                            writer,
                            "[g1315-open-donor] "
                        );
                        return "DONOR_PANEL_ROOT_OPENED";
                    }
                );

            byte[] donorWire=
                wire.toByteArray();
            IsaacCipher decode=
                new IsaacCipher(
                    SEED.clone()
                );
            int offset=0;

            int donorOpcode=
                ((donorWire[offset++]&255)-
                    decode.nextInt())&
                    255;
            int donorRoot=
                ((donorWire[offset++]&255)<<8)|
                (donorWire[offset++]&255);

            donorRoot60062=
                "DONOR_PANEL_ROOT_OPENED"
                    .equals(opened)&&
                donorOpcode==97&&
                donorRoot==
                    DonorPanelPresentation.ROOT&&
                donorRoot==60062&&
                bridge.donor.isOpen()&&
                !bridge.cart.isOpen();

            require(
                donorRoot60062,
                "Donor Panel exact root"
            );

            for(int i=0;i<2;i++){
                int opcode=
                    ((donorWire[offset++]&255)-
                        decode.nextInt())&
                        255;
                int length=
                    ((donorWire[offset++]&255)<<8)|
                    (donorWire[offset++]&255);

                require(
                    opcode==126&&
                    length>=3&&
                    offset+length<=
                        donorWire.length,
                    "Donor Panel promotion packet "+i
                );

                offset+=length;
            }

            require(
                offset==donorWire.length,
                "unexpected Donor Panel opening wire tail"
            );

            compatibilityG1312Preserved=
                "DISABLED_NO_GAMEPLAY_AUTHORITY"
                    .equals(
                        bridge.donor.handle(
                            DonorPanelPresentation
                                .Intent
                                .DONATE_FOR_REWARDS
                        ).status
                    )&&
                bridge.donor.isOpen();

            require(
                compatibilityG1312Preserved,
                "G13.12 compatibility handler changed"
            );

            int[] otherWidgets={
                60074,60075,60076,
                60077,60078,60079
            };

            otherDonorActionsDisabled=true;

            for(int widget:otherWidgets){
                int before=
                    bridge.donorLiveCalls;

                ui.handleWidget(
                    widget,
                    writer,
                    "[g1315-other-donor] "
                );

                otherDonorActionsDisabled&=
                    bridge.donorLiveCalls==
                        before+1&&
                    bridge.lastDonor!=null&&
                    "DISABLED_NO_GAMEPLAY_AUTHORITY"
                        .equals(
                            bridge.lastDonor.status
                        )&&
                    !bridge.lastDonor.succeeded&&
                    bridge.donor.isOpen()&&
                    !bridge.cart.isOpen();
            }

            require(
                otherDonorActionsDisabled,
                "non-Donate donor intent escaped fail-closed policy"
            );

            int beforeCartBytes=
                wire.size();
            int beforeDonateCalls=
                bridge.donorLiveCalls;

            ui.handleWidget(
                DonorPanelPresentation
                    .DONATE_WIDGET,
                writer,
                "[g1315-donate] "
            );

            donate60073=
                DonorPanelPresentation
                    .DONATE_WIDGET==60073&&
                bridge.donorLiveCalls==
                    beforeDonateCalls+1&&
                bridge.lastDonor!=null&&
                bridge.lastDonor.intent==
                    DonorPanelPresentation
                        .Intent.DONATE_FOR_REWARDS&&
                "NAVIGATE_DONATION_CART"
                    .equals(
                        bridge.lastDonor.status
                    )&&
                bridge.lastDonor.succeeded;

            donorRetired=
                !bridge.donor.isOpen();
            donorToCart=
                donate60073&&
                donorRetired&&
                bridge.cart.isOpen();

            require(
                donorToCart,
                "Donate did not transfer root ownership"
            );

            byte[] all=
                wire.toByteArray();
            byte[] cartWire=
                Arrays.copyOfRange(
                    all,
                    beforeCartBytes,
                    all.length
                );

            int cartOffset=0;
            int cartOpcode=
                ((cartWire[cartOffset++]&255)-
                    decode.nextInt())&
                    255;
            int cartRoot=
                ((cartWire[cartOffset++]&255)<<8)|
                (cartWire[cartOffset++]&255);

            cartRoot60200=
                cartOpcode==97&&
                cartRoot==
                    DonationCartPresentation.ROOT&&
                cartRoot==60200;

            require(
                cartRoot60200,
                "Donation Cart exact root after Donate"
            );

            quantitiesZero8=true;

            for(int i=0;i<
                    DonationCartPresentation
                        .PRODUCT_SLOTS;i++){
                int opcode=
                    ((cartWire[cartOffset++]&255)-
                        decode.nextInt())&
                        255;
                int length=
                    ((cartWire[cartOffset++]&255)<<8)|
                    (cartWire[cartOffset++]&255);

                quantitiesZero8&=
                    opcode==126&&
                    length==4&&
                    cartOffset+length<=
                        cartWire.length;

                if(!quantitiesZero8)
                    break;

                int textByte=
                    cartWire[cartOffset++]&255;
                int newline=
                    cartWire[cartOffset++]&255;
                int target=
                    ((cartWire[cartOffset++]&255)<<8)|
                    (((cartWire[cartOffset++]&255)-128)&255);

                quantitiesZero8&=
                    textByte=='0'&&
                    newline==10&&
                    target==
                        DonationCartPresentation
                            .quantityWidget(i);
            }

            quantitiesZero8&=
                cartOffset==
                    cartWire.length;

            require(
                quantitiesZero8,
                "zero-product cart projection after Donate"
            );

            int donorCallsAfterNavigation=
                bridge.donorLiveCalls;

            ui.handleWidget(
                DonorPanelPresentation
                    .VIEW_PERKS_WIDGET,
                writer,
                "[g1315-closed-donor] "
            );

            closedDonorWidgetNoop=
                bridge.donorLiveCalls==
                    donorCallsAfterNavigation&&
                bridge.cart.isOpen();

            require(
                closedDonorWidgetNoop,
                "retired Donor widget escaped root gate"
            );

            int[] cartActions={
                60227,60233,60239,60245,
                60251,60257,60263,60284,
                60230,60236,60242,60248,
                60254,60260,60266,60287,
                60214,60273,60274
            };

            cartActionsRemainDisabled=true;

            for(int widget:cartActions){
                int before=
                    bridge.cartCalls;

                ui.handleWidget(
                    widget,
                    writer,
                    "[g1315-cart-action] "
                );

                cartActionsRemainDisabled&=
                    bridge.cartCalls==
                        before+1&&
                    bridge.lastCart!=null&&
                    "DISABLED_NO_COMMERCE_AUTHORITY"
                        .equals(
                            bridge.lastCart.status
                        )&&
                    !bridge.lastCart.succeeded&&
                    bridge.cart.isOpen();
            }

            require(
                cartActionsRemainDisabled,
                "Donation Cart action escaped commerce fence"
            );

            System.out.println(
                "G1315_DONOR_TO_ZERO_CART_NAV_PASS"+
                " donorRoot60062="+donorRoot60062+
                " donate60073="+donate60073+
                " donorToCart="+donorToCart+
                " donorRetired="+donorRetired+
                " cartRoot60200="+cartRoot60200+
                " quantitiesZero8="+quantitiesZero8+
                " cartActionsRemainDisabled="+
                    cartActionsRemainDisabled+
                " otherDonorActionsDisabled="+
                    otherDonorActionsDisabled+
                " closedDonorWidgetNoop="+
                    closedDonorWidgetNoop+
                " compatibilityG1312Preserved="+
                    compatibilityG1312Preserved+
                " catalogClaim=false"+
                " pricingClaim=false"+
                " paymentProcessingClaim=false"+
                " settlementClaim=false"+
                " entitlementClaim=false"+
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

    private G1315DonorToZeroCartNavigationIntegrationTest(){}
}
