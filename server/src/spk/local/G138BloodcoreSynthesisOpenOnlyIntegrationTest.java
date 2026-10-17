package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class G138BloodcoreSynthesisOpenOnlyIntegrationTest {
    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalBloodFountainUiHandler blood=
            new LocalBloodFountainUiHandler();

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

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean customCommand=false;
        boolean root61078=false;
        boolean status61096_61097=false;
        boolean truthfulUnconfiguredStatus=false;
        boolean controlFamilies4=false;
        boolean noRawWidgetDispatch=false;
        boolean input61099=false;
        boolean second61100Unowned=false;
        boolean rootReplacementLifecycle=false;
        boolean synthesisServiceCreated=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g138-player"
        );

        try{
            Bridge bridge=
                new Bridge();
            LocalSessionUiActionHandler ui=
                uiHandler(
                    player,
                    bridge
                );
            LocalBloodcoreSynthesisUiHandler synthesis=
                new LocalBloodcoreSynthesisUiHandler();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{151,152,153,154}
                    )
                );

            customCommand=
                LocalCommandDispatcher
                    .isBloodcoreSynthesisRoute(
                        new String[]{"bloodcoresynthesis"}
                    )&&
                LocalCommandDispatcher
                    .isBloodcoreSynthesisRoute(
                        new String[]{"bcsynthesis"}
                    )&&
                !LocalCommandDispatcher
                    .isBloodcoreSynthesisRoute(
                        new String[]{"bcsynthesis","invent"}
                    );

            require(
                customCommand,
                "Bloodcore Synthesis LocalLab route"
            );

            controlFamilies4=
                BloodcoreSynthesisPresentation
                    .controls()
                    .size()==4;

            status61096_61097=
                BloodcoreSynthesisPresentation
                    .STATUS_WIDGET_A==61096&&
                BloodcoreSynthesisPresentation
                    .STATUS_WIDGET_B==61097;

            input61099=
                BloodcoreSynthesisPresentation
                    .INPUT_ITEM_WIDGET==61099;

            second61100Unowned=
                BloodcoreSynthesisPresentation
                    .SECOND_ITEM_WIDGET==61100;

            require(
                controlFamilies4&&
                status61096_61097&&
                input61099&&
                second61100Unowned,
                "Bloodcore Synthesis exact surface"
            );

            boolean resolverFound=false;
            boolean handlerFound=false;

            for(Method method:
                    BloodcoreSynthesisPresentation
                        .class
                        .getDeclaredMethods()){
                String name=
                    method.getName()
                        .toLowerCase(
                            java.util.Locale.ROOT
                        );

                if(name.contains("resolvewidget"))
                    resolverFound=true;

                if(name.contains("handleclick"))
                    handlerFound=true;
            }

            noRawWidgetDispatch=
                !resolverFound&&!handlerFound;

            require(
                noRawWidgetDispatch,
                "Bloodcore Synthesis raw control dispatch regressed"
            );

            truthfulUnconfiguredStatus=
                LocalBloodcoreSynthesisUiHandler
                    .STATUS_A
                    .equals(
                        "No LocalLab synthesis recipe configured"
                    )&&
                LocalBloodcoreSynthesisUiHandler
                    .STATUS_B
                    .equals(
                        "Controls disabled until transport authority is closed"
                    );

            require(
                truthfulUnconfiguredStatus,
                "Bloodcore Synthesis status text"
            );

            ui.replaceMonsterSpawnerWithBloodFountainRoot(
                ()->{
                    bridge.blood.openHub(
                        writer
                    );
                    return "BLOOD_FOUNTAIN_ROOT_OPENED";
                }
            );

            require(
                bridge.blood.isOpen(),
                "Bloodcore Synthesis lifecycle precondition"
            );

            int beforeSynthesis=
                wire.size();

            String opened=
                ui.replaceMonsterSpawnerRoot(
                    ()->{
                        synthesis.open(
                            writer
                        );
                        return "BLOODCORE_SYNTHESIS_ROOT_OPENED";
                    }
                );

            root61078=
                "BLOODCORE_SYNTHESIS_ROOT_OPENED"
                    .equals(opened)&&
                BloodcoreSynthesisPresentation
                    .ROOT==61078&&
                wire.size()>beforeSynthesis;

            rootReplacementLifecycle=
                !bridge.blood.isOpen();

            require(
                root61078&&
                rootReplacementLifecycle,
                "Bloodcore Synthesis root replacement"
            );

            for(Field field:
                    LocalBloodcoreSynthesisUiHandler
                        .class
                        .getDeclaredFields())
                if(field.getType()==
                        BloodcoreSynthesisService.class)
                    synthesisServiceCreated=true;

            require(
                !synthesisServiceCreated,
                "open-only Synthesis adapter created service state"
            );

            System.out.println(
                "G138_BLOODCORE_SYNTHESIS_OPEN_ONLY_PASS"+
                " customCommand="+customCommand+
                " root61078="+root61078+
                " status61096_61097="+
                    status61096_61097+
                " truthfulUnconfiguredStatus="+
                    truthfulUnconfiguredStatus+
                " controlFamilies4="+
                    controlFamilies4+
                " noRawWidgetDispatch="+
                    noRawWidgetDispatch+
                " input61099="+input61099+
                " second61100Unowned="+
                    second61100Unowned+
                " rootReplacementLifecycle="+
                    rootReplacementLifecycle+
                " synthesisServiceCreated="+
                    synthesisServiceCreated+
                " recipeClaim=false"+
                " secondSurfaceClaim=false"+
                " controlTransportClaim=false"+
                " outputClaim=false"+
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

    private G138BloodcoreSynthesisOpenOnlyIntegrationTest(){}
}
