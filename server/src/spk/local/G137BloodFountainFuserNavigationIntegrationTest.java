package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

public final class G137BloodFountainFuserNavigationIntegrationTest {
    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalBloodFountainUiHandler blood=
            new LocalBloodFountainUiHandler();
        int hubCalls;
        int fuserCalls;
        LocalBloodFountainUiHandler.HubResult lastHub;
        LocalBloodFountainUiHandler.FuserResult lastFuser;

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

        @Override public LocalBloodFountainUiHandler.FuserResult
            handleBloodDiamondFuserWidget(
                BloodDiamondFuserPresentation.Input input,
                String tag
            )throws IOException{
            fuserCalls++;
            lastFuser=
                blood.handleFuser(input);
            return lastFuser;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean hubRoot3320=false;
        boolean fuser60004=false;
        boolean fuserRoot318=false;
        boolean fuseRowsExact=false;
        boolean cycleWire216=false;
        boolean fuseRowsDisabled=false;
        boolean cycleDisabled=false;
        boolean salvage60006StillWorks=false;
        boolean wrongSurfaceNoop=false;
        boolean interfaceCloseRetires=false;
        boolean competingRootRetires=false;
        boolean fuserServiceCreated=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g137-player"
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
                        new int[]{141,142,143,144}
                    )
                );

            String opened=
                ui.replaceMonsterSpawnerWithBloodFountainRoot(
                    ()->{
                        bridge.openBloodFountainHub(
                            writer,
                            "[g137-open] "
                        );
                        return "BLOOD_FOUNTAIN_ROOT_OPENED";
                    }
                );

            hubRoot3320=
                "BLOOD_FOUNTAIN_ROOT_OPENED"
                    .equals(opened)&&
                BloodFountainHubPresentation
                    .ROOT==3320&&
                bridge.blood.surface()==
                    LocalBloodFountainUiHandler
                        .Surface.HUB;

            require(
                hubRoot3320,
                "Blood Fountain hub precondition"
            );

            int[] fuseWidgets={
                65406,
                65410,
                65414
            };

            fuseRowsExact=true;

            for(int i=0;i<fuseWidgets.length;i++){
                BloodDiamondFuserPresentation.Input input=
                    BloodDiamondFuserPresentation
                        .resolveWidget(
                            fuseWidgets[i]
                        );

                fuseRowsExact&=
                    input!=null&&
                    input.kind==
                        BloodDiamondFuserPresentation
                            .InputKind.FUSE_ROW&&
                    input.rowIndex==i&&
                    BloodDiamondFuserPresentation
                        .fuseWidget(i)==
                        fuseWidgets[i];
            }

            BloodDiamondFuserPresentation.Input cycle=
                BloodDiamondFuserPresentation
                    .resolveWidget(
                        BloodDiamondFuserPresentation
                            .CYCLE_WIRE_WIDGET
                    );

            cycleWire216=
                BloodDiamondFuserPresentation
                    .CYCLE_WIDGET==65752&&
                BloodDiamondFuserPresentation
                    .CYCLE_WIRE_WIDGET==216&&
                cycle!=null&&
                cycle.kind==
                    BloodDiamondFuserPresentation
                        .InputKind.CYCLE_ITEMS;

            require(
                fuseRowsExact&&cycleWire216,
                "Blood Diamond Fuser exact inputs"
            );

            ui.handleWidget(
                BloodDiamondFuserPresentation
                    .fuseWidget(0),
                writer,
                "[g137-hub-fuse] "
            );

            wrongSurfaceNoop=
                bridge.fuserCalls==1&&
                bridge.lastFuser!=null&&
                "WRONG_SURFACE_NOOP"
                    .equals(
                        bridge.lastFuser.status
                    );

            require(
                wrongSurfaceNoop,
                "Fuser action escaped HUB surface"
            );

            int beforeFuser=
                wire.size();

            ui.handleWidget(
                BloodFountainHubPresentation
                    .BLOOD_DIAMOND_FUSER_WIDGET,
                writer,
                "[g137-fuser-nav] "
            );

            fuser60004=
                bridge.hubCalls==1&&
                bridge.lastHub!=null&&
                bridge.lastHub.intent==
                    BloodFountainHubService
                        .Intent.BLOOD_DIAMOND_FUSER&&
                "NAVIGATED_TO_FUSER"
                    .equals(
                        bridge.lastHub.status
                    )&&
                bridge.lastHub.navigated;

            fuserRoot318=
                BloodDiamondFuserPresentation
                    .ROOT==318&&
                bridge.blood.surface()==
                    LocalBloodFountainUiHandler
                        .Surface.FUSER&&
                wire.size()>beforeFuser;

            require(
                fuser60004&&fuserRoot318,
                "exact Blood Fountain to Fuser navigation"
            );

            for(int i=0;i<fuseWidgets.length;i++){
                int callsBefore=
                    bridge.fuserCalls;

                ui.handleWidget(
                    fuseWidgets[i],
                    writer,
                    "[g137-fuse-row-"+i+"] "
                );

                fuseRowsDisabled|=
                    bridge.fuserCalls==
                        callsBefore+1&&
                    bridge.lastFuser!=null&&
                    bridge.lastFuser.input.kind==
                        BloodDiamondFuserPresentation
                            .InputKind.FUSE_ROW&&
                    bridge.lastFuser.input.rowIndex==i&&
                    "DISABLED_NO_GAMEPLAY_AUTHORITY"
                        .equals(
                            bridge.lastFuser.status
                        )&&
                    !bridge.lastFuser.succeeded;

                require(
                    bridge.fuserCalls==
                        callsBefore+1&&
                    "DISABLED_NO_GAMEPLAY_AUTHORITY"
                        .equals(
                            bridge.lastFuser.status
                        ),
                    "Fuser row did not fail closed "+
                    i
                );
            }

            fuseRowsDisabled&=
                bridge.fuserCalls==4;

            int beforeCycleCalls=
                bridge.fuserCalls;

            ui.handleWidget(
                BloodDiamondFuserPresentation
                    .CYCLE_WIRE_WIDGET,
                writer,
                "[g137-cycle] "
            );

            cycleDisabled=
                bridge.fuserCalls==
                    beforeCycleCalls+1&&
                bridge.lastFuser!=null&&
                bridge.lastFuser.input.kind==
                    BloodDiamondFuserPresentation
                        .InputKind.CYCLE_ITEMS&&
                "DISABLED_NO_GAMEPLAY_AUTHORITY"
                    .equals(
                        bridge.lastFuser.status
                    )&&
                !bridge.lastFuser.succeeded;

            require(
                fuseRowsDisabled&&
                cycleDisabled,
                "Fuser controls did not fail closed"
            );

            ui.handleWidget(
                BloodFountainHubPresentation
                    .BLOOD_SHARD_SALVAGING_WIDGET,
                writer,
                "[g137-fuser-hub-input] "
            );

            wrongSurfaceNoop&=
                bridge.hubCalls==2&&
                bridge.lastHub!=null&&
                "WRONG_SURFACE_NOOP"
                    .equals(
                        bridge.lastHub.status
                    );

            require(
                wrongSurfaceNoop,
                "hub input escaped FUSER surface"
            );

            ui.replaceMonsterSpawnerWithBloodFountainRoot(
                ()->{
                    bridge.openBloodFountainHub(
                        writer,
                        "[g137-reopen-hub] "
                    );
                    return "BLOOD_FOUNTAIN_ROOT_OPENED";
                }
            );

            ui.handleWidget(
                BloodFountainHubPresentation
                    .BLOOD_SHARD_SALVAGING_WIDGET,
                writer,
                "[g137-salvage-regression] "
            );

            salvage60006StillWorks=
                bridge.lastHub!=null&&
                bridge.lastHub.intent==
                    BloodFountainHubService
                        .Intent.BLOOD_SHARD_SALVAGING&&
                bridge.lastHub.navigated&&
                "NAVIGATED_TO_SALVAGE"
                    .equals(
                        bridge.lastHub.status
                    )&&
                bridge.blood.surface()==
                    LocalBloodFountainUiHandler
                        .Surface.SALVAGE;

            require(
                salvage60006StillWorks,
                "G13.6 Salvage navigation regressed"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g137-close] "
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
                        "[g137-reopen-fuser] "
                    );
                    return "BLOOD_FOUNTAIN_ROOT_OPENED";
                }
            );

            ui.handleWidget(
                BloodFountainHubPresentation
                    .BLOOD_DIAMOND_FUSER_WIDGET,
                writer,
                "[g137-reopen-fuser-nav] "
            );

            require(
                bridge.blood.surface()==
                    LocalBloodFountainUiHandler
                        .Surface.FUSER,
                "Fuser reopen precondition"
            );

            ui.replaceMonsterSpawnerWithUnclaimedCofferRoot(
                ()->"UNCLAIMED_COFFER_ROOT_OPENED"
            );

            competingRootRetires=
                !bridge.blood.isOpen();

            require(
                competingRootRetires,
                "competing root did not retire Fuser owner"
            );

            for(Field field:
                    LocalBloodFountainUiHandler
                        .class
                        .getDeclaredFields())
                if(field.getType()==
                        BloodDiamondFuserService.class)
                    fuserServiceCreated=true;

            require(
                !fuserServiceCreated,
                "Blood Fountain live owner created Fuser service state"
            );

            System.out.println(
                "G137_BLOOD_FOUNTAIN_FUSER_NAV_PASS"+
                " hubRoot3320="+hubRoot3320+
                " fuser60004="+fuser60004+
                " fuserRoot318="+fuserRoot318+
                " fuseRowsExact="+fuseRowsExact+
                " cycleWire216="+cycleWire216+
                " fuseRowsDisabled="+fuseRowsDisabled+
                " cycleDisabled="+cycleDisabled+
                " salvage60006StillWorks="+
                    salvage60006StillWorks+
                " wrongSurfaceNoop="+
                    wrongSurfaceNoop+
                " interfaceCloseRetires="+
                    interfaceCloseRetires+
                " competingRootRetires="+
                    competingRootRetires+
                " fuserServiceCreated="+
                    fuserServiceCreated+
                " recipeBindingClaim=false"+
                " recipeVisualClaim=false"+
                " costClaim=false"+
                " cyclePolicyClaim=false"+
                " outcomeClaim=false"+
                " persistenceClaim=false"+
                " originalHubTransportClaim=false"+
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

    private G137BloodFountainFuserNavigationIntegrationTest(){}
}
