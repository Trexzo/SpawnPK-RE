package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;

public final class G1310LegendaryPetFusionLiveFailClosedIntegrationTest {
    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalLegendaryPetFusionUiHandler fusion=
            new LocalLegendaryPetFusionUiHandler();
        int actionCalls;
        LocalLegendaryPetFusionUiHandler.Result lastResult;

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

        @Override public boolean retireLegendaryPetFusionRoot(){
            return fusion.close();
        }

        @Override public boolean openLegendaryPetFusion(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            fusion.open(writer);
            return true;
        }

        @Override public LocalLegendaryPetFusionUiHandler.Result
            handleLegendaryPetFusionWidget(
                LegendaryPetFusionPresentation.Input input,
                String tag
            )throws IOException{
            actionCalls++;
            lastResult=
                fusion.handle(input);
            return lastResult;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean customCommand=false;
        boolean root18547=false;
        boolean emptyIngredient18548=false;
        boolean emptyCost18549=false;
        boolean emptyResult18550=false;
        boolean truthfulAvailability18552=false;
        boolean fuseWire23Disabled=false;
        boolean close65418Retires=false;
        boolean closedUiNoop=false;
        boolean interfaceCloseRetires=false;
        boolean competingRootRetires=false;
        boolean fusionServiceCreated=false;
        boolean legacyDefaultsUsed=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g1310-player"
        );

        try{
            customCommand=
                LocalCommandDispatcher
                    .isLegendaryPetFusionRoute(
                        new String[]{"petfusion"}
                    )&&
                LocalCommandDispatcher
                    .isLegendaryPetFusionRoute(
                        new String[]{"legendaryfusion"}
                    )&&
                !LocalCommandDispatcher
                    .isLegendaryPetFusionRoute(
                        new String[]{"petfusion","invent"}
                    );

            require(
                customCommand,
                "Legendary Pet Fusion LocalLab route"
            );

            emptyIngredient18548=
                emptyContainer(
                    LegendaryPetFusionPresentation
                        .INGREDIENT_WIDGET
                );
            emptyCost18549=
                emptyContainer(
                    LegendaryPetFusionPresentation
                        .COST_WIDGET
                );
            emptyResult18550=
                emptyContainer(
                    LegendaryPetFusionPresentation
                        .RESULT_WIDGET
                );

            truthfulAvailability18552=
                LegendaryPetFusionPresentation
                    .AVAILABILITY_TEXT_WIDGET==
                    18552&&
                LocalLegendaryPetFusionUiHandler
                    .AVAILABILITY_TEXT
                    .equals(
                        "No LocalLab fusion offering configured"
                    )&&
                !LocalLegendaryPetFusionUiHandler
                    .AVAILABILITY_TEXT
                    .equals(
                        LegendaryPetFusionPresentation
                            .LegacyEvidence
                            .AVAILABILITY_TEXT
                    );

            legacyDefaultsUsed=
                !emptyIngredient18548||
                !emptyCost18549||
                !emptyResult18550||
                !truthfulAvailability18552;

            require(
                !legacyDefaultsUsed,
                "legacy defaults leaked into live projection"
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
                        new int[]{171,172,173,174}
                    )
                );

            String opened=
                ui.replaceMonsterSpawnerWithLegendaryPetFusionRoot(
                    ()->{
                        bridge.openLegendaryPetFusion(
                            writer,
                            "[g1310-open] "
                        );
                        return "LEGENDARY_PET_FUSION_ROOT_OPENED";
                    }
                );

            root18547=
                "LEGENDARY_PET_FUSION_ROOT_OPENED"
                    .equals(opened)&&
                LegendaryPetFusionPresentation
                    .ROOT==18547&&
                bridge.fusion.isOpen()&&
                wire.size()>0;

            require(
                root18547,
                "Legendary Pet Fusion root"
            );

            LegendaryPetFusionPresentation.Input fuse=
                LegendaryPetFusionPresentation
                    .resolveWidget(
                        LegendaryPetFusionPresentation
                            .ROOT,
                        LegendaryPetFusionPresentation
                            .FUSE_WIRE_WIDGET
                    );

            require(
                fuse!=null&&
                fuse.kind==
                    LegendaryPetFusionPresentation
                        .InputKind.FUSE&&
                LegendaryPetFusionPresentation
                    .FUSE_WIRE_WIDGET==23,
                "Legendary Pet Fusion exact Fuse alias"
            );

            ui.handleWidget(
                LegendaryPetFusionPresentation
                    .FUSE_WIRE_WIDGET,
                writer,
                "[g1310-fuse] "
            );

            fuseWire23Disabled=
                bridge.actionCalls==1&&
                bridge.lastResult!=null&&
                bridge.lastResult.kind==
                    LegendaryPetFusionPresentation
                        .InputKind.FUSE&&
                !bridge.lastResult.succeeded&&
                "DISABLED_NO_GAMEPLAY_AUTHORITY"
                    .equals(
                        bridge.lastResult.status
                    );

            require(
                fuseWire23Disabled,
                "Legendary Pet Fusion Fuse did not fail closed"
            );

            ui.handleWidget(
                LegendaryPetFusionPresentation
                    .CLOSE_WIDGET,
                writer,
                "[g1310-close-widget] "
            );

            close65418Retires=
                bridge.actionCalls==2&&
                bridge.lastResult!=null&&
                bridge.lastResult.kind==
                    LegendaryPetFusionPresentation
                        .InputKind.CLOSE&&
                bridge.lastResult.succeeded&&
                "CLOSED".equals(
                    bridge.lastResult.status
                )&&
                !bridge.fusion.isOpen();

            require(
                close65418Retires,
                "Legendary Pet Fusion Close did not retire root"
            );

            int closedCalls=
                bridge.actionCalls;

            ui.handleWidget(
                LegendaryPetFusionPresentation
                    .FUSE_WIRE_WIDGET,
                writer,
                "[g1310-closed-fuse] "
            );

            closedUiNoop=
                bridge.actionCalls==
                    closedCalls;

            require(
                closedUiNoop,
                "closed Fuse alias escaped root gate"
            );

            LocalLegendaryPetFusionUiHandler.Result
                directClosed=
                    bridge.fusion.handle(
                        fuse
                    );

            closedUiNoop&=
                "CLOSED_UI_NOOP".equals(
                    directClosed.status
                )&&
                !directClosed.succeeded;

            require(
                closedUiNoop,
                "closed Fusion handler did not fail closed"
            );

            ui.replaceMonsterSpawnerWithLegendaryPetFusionRoot(
                ()->{
                    bridge.openLegendaryPetFusion(
                        writer,
                        "[g1310-reopen-interface] "
                    );
                    return "LEGENDARY_PET_FUSION_ROOT_OPENED";
                }
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g1310-interface-close] "
            );

            interfaceCloseRetires=
                !bridge.fusion.isOpen();

            require(
                interfaceCloseRetires,
                "interface close did not retire Fusion"
            );

            ui.replaceMonsterSpawnerWithLegendaryPetFusionRoot(
                ()->{
                    bridge.openLegendaryPetFusion(
                        writer,
                        "[g1310-reopen-competing] "
                    );
                    return "LEGENDARY_PET_FUSION_ROOT_OPENED";
                }
            );

            require(
                bridge.fusion.isOpen(),
                "Fusion competing-root precondition"
            );

            ui.replaceMonsterSpawnerWithBloodFountainRoot(
                ()->"BLOOD_FOUNTAIN_ROOT_OPENED"
            );

            competingRootRetires=
                !bridge.fusion.isOpen();

            require(
                competingRootRetires,
                "competing root did not retire Fusion"
            );

            for(Field field:
                    LocalLegendaryPetFusionUiHandler
                        .class
                        .getDeclaredFields())
                if(field.getType()==
                        LegendaryPetFusionService.class)
                    fusionServiceCreated=true;

            require(
                !fusionServiceCreated,
                "live Fusion shell created semantic service state"
            );

            System.out.println(
                "G1310_LEGENDARY_PET_FUSION_LIVE_FAIL_CLOSED_PASS"+
                " customCommand="+customCommand+
                " root18547="+root18547+
                " emptyIngredient18548="+
                    emptyIngredient18548+
                " emptyCost18549="+
                    emptyCost18549+
                " emptyResult18550="+
                    emptyResult18550+
                " truthfulAvailability18552="+
                    truthfulAvailability18552+
                " fuseWire23Disabled="+
                    fuseWire23Disabled+
                " close65418Retires="+
                    close65418Retires+
                " closedUiNoop="+closedUiNoop+
                " interfaceCloseRetires="+
                    interfaceCloseRetires+
                " competingRootRetires="+
                    competingRootRetires+
                " fusionServiceCreated="+
                    fusionServiceCreated+
                " legacyDefaultsUsed="+
                    legacyDefaultsUsed+
                " offeringClaim=false"+
                " stateWireClaim=false"+
                " rngClaim=false"+
                " persistenceClaim=false"+
                " originalNavigationClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static boolean emptyContainer(
        int widget
    )throws Exception{
        byte[] body=
            LegendaryPetFusionPresentation
                .itemContainer(
                    widget,
                    Collections.emptyList()
                );

        return body.length==4&&
            (body[0]&255)==
                (widget>>>8)&&
            (body[1]&255)==
                (widget&255)&&
            (body[2]&255)==0&&
            (body[3]&255)==0;
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

    private G1310LegendaryPetFusionLiveFailClosedIntegrationTest(){}
}
