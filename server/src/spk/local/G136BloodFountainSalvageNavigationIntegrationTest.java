package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.EnumMap;

public final class G136BloodFountainSalvageNavigationIntegrationTest {
    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalBloodFountainUiHandler blood=
            new LocalBloodFountainUiHandler();
        int hubCalls;
        int salvageCalls;
        LocalBloodFountainUiHandler.HubResult lastHub;
        LocalBloodFountainUiHandler.SalvageResult lastSalvage;

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

        @Override public boolean retireBloodFountainRoot(){
            return blood.close();
        }

        @Override public boolean openBloodFountainHub(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            blood.openHub(writer);
            return true;
        }

        @Override public LocalBloodFountainUiHandler.HubResult
            handleBloodFountainHubIntent(
                BloodFountainHubService.Intent intent,
                ServerPacketWriter writer,
                String tag
            )throws IOException{
            hubCalls++;
            lastHub=
                blood.handleHubIntent(
                    intent,
                    writer
                );
            return lastHub;
        }

        @Override public LocalBloodFountainUiHandler.SalvageResult
            handleBloodShardSalvageWidget(
                BloodShardSalvagePresentation.Intent intent,
                String tag
            )throws IOException{
            salvageCalls++;
            lastSalvage=
                blood.handleSalvage(
                    intent
                );
            return lastSalvage;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean customCommand=false;
        boolean hubRoot3320=false;
        boolean exactHubWidgets=false;
        boolean salvage60006=false;
        boolean salvageRoot18546=false;
        boolean truthfulStatus=false;
        boolean otherHubIntentsDisabled=false;
        boolean salvage60014Disabled=false;
        boolean guide60019Disabled=false;
        boolean wrongSurfaceNoop=false;
        boolean interfaceCloseRetires=false;
        boolean competingRootRetires=false;
        boolean salvageServiceCreated=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g136-player"
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
                        new int[]{131,132,133,134}
                    )
                );

            customCommand=
                LocalCommandDispatcher
                    .isBloodFountainRoute(
                        new String[]{"bloodfountain"}
                    )&&
                LocalCommandDispatcher
                    .isBloodFountainRoute(
                        new String[]{"bfountain"}
                    )&&
                !LocalCommandDispatcher
                    .isBloodFountainRoute(
                        new String[]{"bloodfountain","invent"}
                    );

            require(
                customCommand,
                "Blood Fountain LocalLab command route"
            );

            EnumMap<
                BloodFountainHubService.Intent,
                Integer
            > exactWidgets=
                new EnumMap<>(
                    BloodFountainHubService
                        .Intent.class
                );

            exactWidgets.put(
                BloodFountainHubService
                    .Intent.PERK_TREE,
                60002
            );
            exactWidgets.put(
                BloodFountainHubService
                    .Intent.BLOOD_POOL_STORE,
                60003
            );
            exactWidgets.put(
                BloodFountainHubService
                    .Intent.BLOOD_DIAMOND_FUSER,
                60004
            );
            exactWidgets.put(
                BloodFountainHubService
                    .Intent.BLOOD_DIAMOND_STORE,
                60005
            );
            exactWidgets.put(
                BloodFountainHubService
                    .Intent.BLOOD_SHARD_SALVAGING,
                60006
            );
            exactWidgets.put(
                BloodFountainHubService
                    .Intent.BLOOD_SHARD_STORE,
                60007
            );

            exactHubWidgets=
                exactWidgets.size()==6;

            for(java.util.Map.Entry<
                    BloodFountainHubService.Intent,
                    Integer
                > entry:
                    exactWidgets.entrySet())
                exactHubWidgets&=
                    BloodFountainHubPresentation
                        .resolveWidget(
                            entry.getValue()
                        )==
                        entry.getKey();

            exactHubWidgets&=
                BloodFountainHubPresentation
                    .resolveWidget(60008)==null&&
                BloodFountainHubPresentation
                    .WIDGET_ACTION_OPCODE==185;

            require(
                exactHubWidgets,
                "Blood Fountain exact hub mapping"
            );

            String opened=
                ui.replaceMonsterSpawnerWithBloodFountainRoot(
                    ()->{
                        bridge.openBloodFountainHub(
                            writer,
                            "[g136-open] "
                        );
                        return "BLOOD_FOUNTAIN_ROOT_OPENED";
                    }
                );

            hubRoot3320=
                "BLOOD_FOUNTAIN_ROOT_OPENED"
                    .equals(opened)&&
                BloodFountainHubPresentation
                    .ROOT==3320&&
                bridge.blood.isOpen()&&
                bridge.blood.surface()==
                    LocalBloodFountainUiHandler
                        .Surface.HUB&&
                wire.size()>0;

            require(
                hubRoot3320,
                "Blood Fountain hub root"
            );

            ui.handleWidget(
                BloodShardSalvagePresentation
                    .SALVAGE_WIDGET,
                writer,
                "[g136-wrong-surface-salvage] "
            );

            wrongSurfaceNoop=
                bridge.salvageCalls==1&&
                bridge.lastSalvage!=null&&
                "WRONG_SURFACE_NOOP"
                    .equals(
                        bridge.lastSalvage.status
                    )&&
                !bridge.lastSalvage.succeeded;

            require(
                wrongSurfaceNoop,
                "Salvage action escaped HUB surface"
            );

            ui.handleWidget(
                BloodFountainHubPresentation
                    .BLOOD_DIAMOND_FUSER_WIDGET,
                writer,
                "[g136-disabled-fuser] "
            );

            otherHubIntentsDisabled=
                bridge.hubCalls==1&&
                bridge.lastHub!=null&&
                bridge.lastHub.intent==
                    BloodFountainHubService
                        .Intent.BLOOD_DIAMOND_FUSER&&
                "DISABLED_NO_RUNTIME_COMPOSITION"
                    .equals(
                        bridge.lastHub.status
                    )&&
                !bridge.lastHub.navigated;

            require(
                otherHubIntentsDisabled,
                "unowned Blood Fountain target did not fail closed"
            );

            int beforeSalvage=
                wire.size();

            ui.handleWidget(
                BloodFountainHubPresentation
                    .BLOOD_SHARD_SALVAGING_WIDGET,
                writer,
                "[g136-salvage-nav] "
            );

            salvage60006=
                bridge.hubCalls==2&&
                bridge.lastHub!=null&&
                bridge.lastHub.intent==
                    BloodFountainHubService
                        .Intent.BLOOD_SHARD_SALVAGING&&
                "NAVIGATED_TO_SALVAGE"
                    .equals(
                        bridge.lastHub.status
                    )&&
                bridge.lastHub.navigated;

            salvageRoot18546=
                BloodShardSalvagePresentation
                    .ROOT==18546&&
                bridge.blood.surface()==
                    LocalBloodFountainUiHandler
                        .Surface.SALVAGE&&
                wire.size()>beforeSalvage;

            truthfulStatus=
                LocalBloodFountainUiHandler
                    .SALVAGE_STATUS
                    .equals(
                        "No LocalLab salvage recipe configured"
                    );

            require(
                salvage60006&&
                salvageRoot18546&&
                truthfulStatus,
                "exact Blood Fountain to Salvage navigation"
            );

            ui.handleWidget(
                BloodFountainHubPresentation
                    .BLOOD_POOL_STORE_WIDGET,
                writer,
                "[g136-wrong-surface-hub] "
            );

            wrongSurfaceNoop&=
                bridge.hubCalls==3&&
                bridge.lastHub!=null&&
                "WRONG_SURFACE_NOOP"
                    .equals(
                        bridge.lastHub.status
                    );

            require(
                wrongSurfaceNoop,
                "hub action escaped SALVAGE surface"
            );

            ui.handleWidget(
                BloodShardSalvagePresentation
                    .SALVAGE_WIDGET,
                writer,
                "[g136-salvage-disabled] "
            );

            salvage60014Disabled=
                bridge.salvageCalls==2&&
                bridge.lastSalvage!=null&&
                bridge.lastSalvage.intent==
                    BloodShardSalvagePresentation
                        .Intent.SALVAGE&&
                "DISABLED_NO_GAMEPLAY_AUTHORITY"
                    .equals(
                        bridge.lastSalvage.status
                    )&&
                !bridge.lastSalvage.succeeded;

            require(
                salvage60014Disabled,
                "Salvage action did not fail closed"
            );

            ui.handleWidget(
                BloodShardSalvagePresentation
                    .GUIDE_WIDGET,
                writer,
                "[g136-guide-disabled] "
            );

            guide60019Disabled=
                bridge.salvageCalls==3&&
                bridge.lastSalvage!=null&&
                bridge.lastSalvage.intent==
                    BloodShardSalvagePresentation
                        .Intent.READ_GUIDE&&
                "DISABLED_NO_GAMEPLAY_AUTHORITY"
                    .equals(
                        bridge.lastSalvage.status
                    )&&
                !bridge.lastSalvage.succeeded;

            require(
                guide60019Disabled,
                "Salvage guide did not fail closed"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g136-close] "
            );

            interfaceCloseRetires=
                !bridge.blood.isOpen();

            require(
                interfaceCloseRetires,
                "interface close did not retire Blood Fountain"
            );

            ui.replaceMonsterSpawnerWithBloodFountainRoot(
                ()->{
                    bridge.openBloodFountainHub(
                        writer,
                        "[g136-reopen] "
                    );
                    return "BLOOD_FOUNTAIN_ROOT_OPENED";
                }
            );

            require(
                bridge.blood.isOpen(),
                "Blood Fountain reopen precondition"
            );

            ui.replaceMonsterSpawnerWithUnclaimedCofferRoot(
                ()->"UNCLAIMED_COFFER_ROOT_OPENED"
            );

            competingRootRetires=
                !bridge.blood.isOpen();

            require(
                competingRootRetires,
                "competing root did not retire Blood Fountain"
            );

            for(Field field:
                    LocalBloodFountainUiHandler
                        .class
                        .getDeclaredFields())
                if(field.getType()==
                        BloodShardSalvageService.class)
                    salvageServiceCreated=true;

            require(
                !salvageServiceCreated,
                "Blood Fountain live owner created salvage service state"
            );

            System.out.println(
                "G136_BLOOD_FOUNTAIN_SALVAGE_NAV_PASS"+
                " customCommand="+customCommand+
                " hubRoot3320="+hubRoot3320+
                " exactHubWidgets="+exactHubWidgets+
                " salvage60006="+salvage60006+
                " salvageRoot18546="+
                    salvageRoot18546+
                " truthfulStatus="+truthfulStatus+
                " otherHubIntentsDisabled="+
                    otherHubIntentsDisabled+
                " salvage60014Disabled="+
                    salvage60014Disabled+
                " guide60019Disabled="+
                    guide60019Disabled+
                " wrongSurfaceNoop="+
                    wrongSurfaceNoop+
                " interfaceCloseRetires="+
                    interfaceCloseRetires+
                " competingRootRetires="+
                    competingRootRetires+
                " salvageServiceCreated="+
                    salvageServiceCreated+
                " recipeClaim=false"+
                " yieldClaim=false"+
                " secondItemClaim=false"+
                " inventoryMutationClaim=false"+
                " persistenceClaim=false"+
                " originalWorldEntryClaim=false"
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

    private G136BloodFountainSalvageNavigationIntegrationTest(){}
}
