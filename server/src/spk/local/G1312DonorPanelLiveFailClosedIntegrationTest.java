package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class G1312DonorPanelLiveFailClosedIntegrationTest {
    private static final int[] SEED={191,192,193,194};

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalDonorPanelUiHandler donor=
            new LocalDonorPanelUiHandler();
        int actionCalls;
        LocalDonorPanelUiHandler.Result lastResult;

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
            handleDonorPanelIntent(
                DonorPanelPresentation.Intent intent,
                String tag
            )throws IOException{
            actionCalls++;
            lastResult=
                donor.handle(intent);
            return lastResult;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean customCommand=false;
        boolean root60062=false;
        boolean intents7=false;
        boolean exactWidgets=false;
        boolean truthfulPromo60091_60100=false;
        boolean allActionsDisabled=false;
        boolean closedUiNoop=false;
        boolean interfaceCloseRetires=false;
        boolean competingRootRetires=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g1312-player"
        );

        try{
            customCommand=
                LocalCommandDispatcher
                    .isDonorPanelRoute(
                        new String[]{"donorpanel"}
                    )&&
                LocalCommandDispatcher
                    .isDonorPanelRoute(
                        new String[]{"donor"}
                    )&&
                !LocalCommandDispatcher
                    .isDonorPanelRoute(
                        new String[]{"donor","invent"}
                    );

            require(
                customCommand,
                "Donor Panel LocalLab route"
            );

            int[] widgets={
                60073,60074,60075,60076,
                60077,60078,60079
            };

            DonorPanelPresentation.Intent[] intents={
                DonorPanelPresentation.Intent
                    .DONATE_FOR_REWARDS,
                DonorPanelPresentation.Intent
                    .VIEW_DONATOR_PERKS,
                DonorPanelPresentation.Intent
                    .OPEN_DONATOR_SHOP,
                DonorPanelPresentation.Intent
                    .TELEPORT_DONATOR_ZONE,
                DonorPanelPresentation.Intent
                    .TELEPORT_ELITE_DONATOR_ZONE,
                DonorPanelPresentation.Intent
                    .TELEPORT_VIP_DONATOR_ZONE,
                DonorPanelPresentation.Intent
                    .TELEPORT_SPONSOR_DONATOR_ZONE
            };

            intents7=
                DonorPanelPresentation
                    .Intent
                    .values()
                    .length==7&&
                intents.length==7;

            exactWidgets=intents7;

            for(int i=0;i<widgets.length;i++)
                exactWidgets&=
                    DonorPanelPresentation
                        .resolveWidget(
                            widgets[i]
                        )==
                        intents[i];

            require(
                exactWidgets,
                "Donor Panel exact intent map"
            );

            truthfulPromo60091_60100=
                DonorPanelPresentation
                    .PROMOTION_PROGRESS_WIDGET==
                    60091&&
                DonorPanelPresentation
                    .NEXT_PROMOTION_WIDGET==
                    60100&&
                LocalDonorPanelUiHandler
                    .PROMOTION_PROGRESS_TEXT
                    .equals(
                        "No LocalLab donation campaign configured"
                    )&&
                LocalDonorPanelUiHandler
                    .NEXT_PROMOTION_TEXT
                    .equals(
                        "Actions disabled until donor policy is configured"
                    );

            require(
                truthfulPromo60091_60100,
                "Donor Panel truthful promotion text"
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
                ui.replaceMonsterSpawnerWithDonorPanelRoot(
                    ()->{
                        bridge.openDonorPanel(
                            writer,
                            "[g1312-open] "
                        );
                        return "DONOR_PANEL_ROOT_OPENED";
                    }
                );

            byte[] openingWire=
                wire.toByteArray();
            IsaacCipher decode=
                new IsaacCipher(
                    SEED.clone()
                );

            int opcode=
                ((openingWire[0]&255)-
                    decode.nextInt())&
                    255;
            int root=
                ((openingWire[1]&255)<<8)|
                (openingWire[2]&255);

            root60062=
                "DONOR_PANEL_ROOT_OPENED"
                    .equals(opened)&&
                bridge.donor.isOpen()&&
                DonorPanelPresentation
                    .ROOT==60062&&
                opcode==97&&
                root==60062;

            truthfulPromo60091_60100&=
                containsAscii(
                    openingWire,
                    LocalDonorPanelUiHandler
                        .PROMOTION_PROGRESS_TEXT
                )&&
                containsAscii(
                    openingWire,
                    LocalDonorPanelUiHandler
                        .NEXT_PROMOTION_TEXT
                );

            require(
                root60062&&
                truthfulPromo60091_60100,
                "Donor Panel live projection"
            );

            allActionsDisabled=true;

            for(int i=0;i<widgets.length;i++){
                int before=
                    bridge.actionCalls;

                ui.handleWidget(
                    widgets[i],
                    writer,
                    "[g1312-action-"+i+"] "
                );

                allActionsDisabled&=
                    bridge.actionCalls==
                        before+1&&
                    bridge.lastResult!=null&&
                    bridge.lastResult.intent==
                        intents[i]&&
                    "DISABLED_NO_GAMEPLAY_AUTHORITY"
                        .equals(
                            bridge.lastResult.status
                        )&&
                    !bridge.lastResult.succeeded&&
                    bridge.donor.isOpen();
            }

            require(
                allActionsDisabled,
                "Donor Panel action escaped fail-closed shell"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g1312-interface-close] "
            );

            interfaceCloseRetires=
                !bridge.donor.isOpen();

            require(
                interfaceCloseRetires,
                "interface close did not retire Donor Panel"
            );

            int closedCalls=
                bridge.actionCalls;

            ui.handleWidget(
                widgets[0],
                writer,
                "[g1312-closed-action] "
            );

            closedUiNoop=
                bridge.actionCalls==
                    closedCalls;

            require(
                closedUiNoop,
                "closed donor widget escaped root gate"
            );

            LocalDonorPanelUiHandler.Result
                directClosed=
                    bridge.donor.handle(
                        intents[0]
                    );

            closedUiNoop&=
                "CLOSED_UI_NOOP".equals(
                    directClosed.status
                )&&
                !directClosed.succeeded;

            require(
                closedUiNoop,
                "closed Donor Panel handler did not no-op"
            );

            ui.replaceMonsterSpawnerWithDonorPanelRoot(
                ()->{
                    bridge.openDonorPanel(
                        writer,
                        "[g1312-reopen] "
                    );
                    return "DONOR_PANEL_ROOT_OPENED";
                }
            );

            require(
                bridge.donor.isOpen(),
                "competing-root precondition"
            );

            ui.replaceMonsterSpawnerWithItemEnchantmentRoot(
                ()->"ITEM_ENCHANTMENT_CATEGORY_ROOT_OPENED"
            );

            competingRootRetires=
                !bridge.donor.isOpen();

            require(
                competingRootRetires,
                "competing root did not retire Donor Panel"
            );

            System.out.println(
                "G1312_DONOR_PANEL_LIVE_FAIL_CLOSED_PASS"+
                " customCommand="+customCommand+
                " root60062="+root60062+
                " intents7="+intents7+
                " exactWidgets="+exactWidgets+
                " truthfulPromo60091_60100="+
                    truthfulPromo60091_60100+
                " allActionsDisabled="+
                    allActionsDisabled+
                " closedUiNoop="+closedUiNoop+
                " interfaceCloseRetires="+
                    interfaceCloseRetires+
                " competingRootRetires="+
                    competingRootRetires+
                " paymentClaim=false"+
                " entitlementClaim=false"+
                " shopPolicyClaim=false"+
                " promotionPolicyClaim=false"+
                " teleportPolicyClaim=false"+
                " persistenceClaim=false"+
                " originalNavigationClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static boolean containsAscii(
        byte[] data,
        String value
    ){
        byte[] needle=
            value.getBytes(
                StandardCharsets.ISO_8859_1
            );

        outer:
        for(int i=0;
            i+needle.length<=data.length;
            i++){
            for(int j=0;j<needle.length;j++)
                if(data[i+j]!=needle[j])
                    continue outer;
            return true;
        }

        return false;
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

    private G1312DonorPanelLiveFailClosedIntegrationTest(){}
}
