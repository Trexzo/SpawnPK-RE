package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

public final class G132GoodwillWellLiveFailClosedIntegrationTest {
    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalGoodwillWellUiHandler goodwill=
            new LocalGoodwillWellUiHandler();
        int actionCalls;
        LocalGoodwillWellUiHandler.Result lastResult;

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

        @Override public boolean retireGoodwillWellRoot(){
            return goodwill.close();
        }

        @Override public boolean openGoodwillWell(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            goodwill.open(writer);
            return true;
        }

        @Override public LocalGoodwillWellUiHandler.Result
            handleGoodwillWellWidget(
                GoodwillWellPresentation.Input input,
                String tag
            )throws IOException{
            actionCalls++;
            lastResult=
                goodwill.handle(input);
            return lastResult;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean customCommand=false;
        boolean root51150=false;
        boolean exactTextChannels=false;
        boolean truthfulUnconfiguredProjection=false;
        boolean donate51163Disabled=false;
        boolean closedUiNoop=false;
        boolean interfaceCloseRetires=false;
        boolean competingRootRetires=false;
        boolean campaignInvented=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g132-player"
        );

        try{
            Bridge bridge=new Bridge();
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
                        new int[]{91,92,93,94}
                    )
                );

            customCommand=
                LocalCommandDispatcher
                    .isGoodwillWellRoute(
                        new String[]{"goodwill"}
                    )&&
                LocalCommandDispatcher
                    .isGoodwillWellRoute(
                        new String[]{"well"}
                    )&&
                !LocalCommandDispatcher
                    .isGoodwillWellRoute(
                        new String[]{"well","invent"}
                    );

            require(
                customCommand,
                "Goodwill LocalLab command route"
            );

            String opened=
                ui.replaceMonsterSpawnerWithGoodwillWellRoot(
                    ()->{
                        bridge.openGoodwillWell(
                            writer,
                            "[g132-open] "
                        );
                        return "GOODWILL_WELL_ROOT_OPENED";
                    }
                );

            root51150=
                "GOODWILL_WELL_ROOT_OPENED"
                    .equals(opened)&&
                GoodwillWellPresentation.ROOT==51150&&
                bridge.goodwill.isOpen()&&
                wire.size()>0;

            require(
                root51150,
                "Goodwill root open"
            );

            exactTextChannels=
                GoodwillWellPresentation
                    .CONTRIBUTION_ITEM_TEXT_WIDGET==
                    51154&&
                GoodwillWellPresentation
                    .PROGRESS_TEXT_WIDGET==
                    51158&&
                GoodwillWellPresentation
                    .SERVER_REWARD_TEXT_WIDGET==
                    51162&&
                GoodwillWellPresentation
                    .INDIVIDUAL_REWARD_TEXT_WIDGET==
                    51169;

            truthfulUnconfiguredProjection=
                LocalGoodwillWellUiHandler
                    .CONTRIBUTION_TEXT
                    .contains("not configured")&&
                LocalGoodwillWellUiHandler
                    .PROGRESS_TEXT
                    .contains("No LocalLab campaign")&&
                LocalGoodwillWellUiHandler
                    .SERVER_REWARD_TEXT
                    .contains("not configured")&&
                LocalGoodwillWellUiHandler
                    .INDIVIDUAL_REWARD_TEXT
                    .contains("not configured");

            require(
                exactTextChannels&&
                truthfulUnconfiguredProjection,
                "Goodwill exact truthful projection"
            );

            GoodwillWellPresentation.Input donate=
                GoodwillWellPresentation
                    .resolveWidget(
                        GoodwillWellPresentation
                            .DONATE_WIDGET
                    );

            require(
                donate!=null&&
                donate.kind==
                    GoodwillWellPresentation
                        .InputKind.DONATE,
                "Goodwill Donate exact input"
            );

            ui.handleWidget(
                GoodwillWellPresentation
                    .DONATE_WIDGET,
                writer,
                "[g132-donate] "
            );

            donate51163Disabled=
                bridge.actionCalls==1&&
                bridge.lastResult!=null&&
                bridge.lastResult.kind==
                    GoodwillWellPresentation
                        .InputKind.DONATE&&
                !bridge.lastResult.succeeded&&
                "DISABLED_NO_GAMEPLAY_AUTHORITY"
                    .equals(
                        bridge.lastResult.status
                    );

            require(
                donate51163Disabled,
                "Goodwill Donate did not fail closed"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g132-close] "
            );

            interfaceCloseRetires=
                !bridge.goodwill.isOpen();

            require(
                interfaceCloseRetires,
                "interface close did not retire Goodwill"
            );

            int callsBeforeClosed=
                bridge.actionCalls;

            ui.handleWidget(
                GoodwillWellPresentation
                    .DONATE_WIDGET,
                writer,
                "[g132-closed-donate] "
            );

            closedUiNoop=
                bridge.actionCalls==
                    callsBeforeClosed;

            require(
                closedUiNoop,
                "closed Goodwill Donate escaped root fence"
            );

            ui.replaceMonsterSpawnerWithGoodwillWellRoot(
                ()->{
                    bridge.openGoodwillWell(
                        writer,
                        "[g132-reopen] "
                    );
                    return "GOODWILL_WELL_ROOT_OPENED";
                }
            );

            require(
                bridge.goodwill.isOpen(),
                "Goodwill reopen precondition"
            );

            ui.replaceMonsterSpawnerWithEventChestRoot(
                ()->"EVENT_CHEST_ROOT_OPENED"
            );

            competingRootRetires=
                !bridge.goodwill.isOpen();

            require(
                competingRootRetires,
                "competing root did not retire Goodwill"
            );

            campaignInvented=false;

            for(Field field:
                    LocalGoodwillWellUiHandler.class
                        .getDeclaredFields())
                if(field.getType()==
                        GoodwillWellService.class)
                    campaignInvented=true;

            require(
                !campaignInvented,
                "Goodwill live shell invented campaign state"
            );

            System.out.println(
                "G132_GOODWILL_WELL_LIVE_FAIL_CLOSED_PASS"+
                " customCommand="+customCommand+
                " root51150="+root51150+
                " exactTextChannels="+
                    exactTextChannels+
                " truthfulUnconfiguredProjection="+
                    truthfulUnconfiguredProjection+
                " donate51163Disabled="+
                    donate51163Disabled+
                " closedUiNoop="+closedUiNoop+
                " interfaceCloseRetires="+
                    interfaceCloseRetires+
                " competingRootRetires="+
                    competingRootRetires+
                " campaignInvented="+campaignInvented+
                " contributionAssetClaim=false"+
                " goalClaim=false"+
                " rewardClaim=false"+
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

    private G132GoodwillWellLiveFailClosedIntegrationTest(){}
}
