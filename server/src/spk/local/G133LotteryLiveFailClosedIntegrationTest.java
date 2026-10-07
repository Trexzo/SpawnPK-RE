package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

public final class G133LotteryLiveFailClosedIntegrationTest {
    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalLotteryUiHandler lottery=
            new LocalLotteryUiHandler();
        int actionCalls;
        LocalLotteryUiHandler.Result lastResult;

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

        @Override public boolean retireLotteryRoot(){
            return lottery.close();
        }

        @Override public boolean openLottery(
            LotteryService.Channel channel,
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            lottery.open(
                channel,
                writer
            );
            return true;
        }

        @Override public LocalLotteryUiHandler.Result
            handleLotteryEntry(
                LotteryService.Channel channel,
                String tag
            )throws IOException{
            actionCalls++;
            lastResult=
                lottery.handleEntry(
                    channel
                );
            return lastResult;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean ordinaryCommand=false;
        boolean bloodcoreCommand=false;
        boolean ordinaryRoot52000=false;
        boolean bloodcoreRoot61150=false;
        boolean exactStatusChannels=false;
        boolean emptyHistory6=false;
        boolean emptyHistory35=false;
        boolean ordinaryEntry52010Disabled=false;
        boolean bloodcoreEntry61196Disabled=false;
        boolean channelMismatchNoop=false;
        boolean closedUiNoop=false;
        boolean interfaceCloseRetires=false;
        boolean competingRootRetires=false;
        boolean roundInvented=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g133-player"
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
                        new int[]{101,102,103,104}
                    )
                );

            ordinaryCommand=
                LocalCommandDispatcher
                    .lotteryRoute(
                        new String[]{"lottery"}
                    )==
                    LotteryService.Channel.ORDINARY&&
                LocalCommandDispatcher
                    .lotteryRoute(
                        new String[]{"lotto"}
                    )==
                    LotteryService.Channel.ORDINARY;

            bloodcoreCommand=
                LocalCommandDispatcher
                    .lotteryRoute(
                        new String[]{"bloodcorelottery"}
                    )==
                    LotteryService.Channel.BLOODCORE&&
                LocalCommandDispatcher
                    .lotteryRoute(
                        new String[]{"bclottery"}
                    )==
                    LotteryService.Channel.BLOODCORE&&
                LocalCommandDispatcher
                    .lotteryRoute(
                        new String[]{"lottery","invent"}
                    )==null;

            require(
                ordinaryCommand&&
                bloodcoreCommand,
                "Lottery LocalLab command routes"
            );

            String ordinaryOpen=
                ui.replaceMonsterSpawnerWithLotteryRoot(
                    ()->{
                        bridge.openLottery(
                            LotteryService
                                .Channel.ORDINARY,
                            writer,
                            "[g133-ordinary-open] "
                        );
                        return "LOTTERY_ROOT_OPENED";
                    }
                );

            ordinaryRoot52000=
                "LOTTERY_ROOT_OPENED"
                    .equals(ordinaryOpen)&&
                LotteryPresentation
                    .ORDINARY_ROOT==52000&&
                bridge.lottery.isOpen()&&
                bridge.lottery.openChannel()==
                    LotteryService.Channel.ORDINARY&&
                wire.size()>0;

            require(
                ordinaryRoot52000,
                "ordinary Lottery root"
            );

            exactStatusChannels=
                LotteryPresentation
                    .ORDINARY_COUNTDOWN_WIDGET==
                    52005&&
                LotteryPresentation
                    .ORDINARY_PARTICIPANTS_WIDGET==
                    52006&&
                LotteryPresentation
                    .BLOODCORE_COUNTDOWN_WIDGET==
                    61156&&
                LotteryPresentation
                    .BLOODCORE_PARTICIPANTS_WIDGET==
                    61157&&
                LocalLotteryUiHandler
                    .COUNTDOWN_TEXT
                    .contains("No LocalLab round")&&
                LocalLotteryUiHandler
                    .PARTICIPANT_TEXT
                    .equals("Participants: 0");

            emptyHistory6=
                LotteryService.Channel
                    .ORDINARY
                    .historyCapacity()==6&&
                LotteryPresentation
                    .ORDINARY_HISTORY_FIRST==
                    52014&&
                LotteryPresentation
                    .ORDINARY_HISTORY_LAST==
                    52019;

            emptyHistory35=
                LotteryService.Channel
                    .BLOODCORE
                    .historyCapacity()==35&&
                LotteryPresentation
                    .BLOODCORE_HISTORY_FIRST==
                    61161&&
                LotteryPresentation
                    .BLOODCORE_HISTORY_LAST==
                    61195;

            require(
                exactStatusChannels&&
                emptyHistory6&&
                emptyHistory35,
                "Lottery exact status/history surface"
            );

            ui.handleWidget(
                LotteryPresentation
                    .ORDINARY_ENTRY_WIDGET,
                writer,
                "[g133-ordinary-entry] "
            );

            ordinaryEntry52010Disabled=
                bridge.actionCalls==1&&
                bridge.lastResult!=null&&
                bridge.lastResult.channel==
                    LotteryService.Channel.ORDINARY&&
                !bridge.lastResult.succeeded&&
                "DISABLED_NO_GAMEPLAY_AUTHORITY"
                    .equals(
                        bridge.lastResult.status
                    );

            require(
                ordinaryEntry52010Disabled,
                "ordinary Lottery entry did not fail closed"
            );

            ui.handleWidget(
                LotteryPresentation
                    .BLOODCORE_ENTRY_WIDGET,
                writer,
                "[g133-cross-channel] "
            );

            channelMismatchNoop=
                bridge.actionCalls==2&&
                bridge.lastResult.channel==
                    LotteryService.Channel.BLOODCORE&&
                !bridge.lastResult.succeeded&&
                "CHANNEL_MISMATCH_NOOP"
                    .equals(
                        bridge.lastResult.status
                    );

            require(
                channelMismatchNoop,
                "Lottery channel mismatch escaped root ownership"
            );

            String bloodcoreOpen=
                ui.replaceMonsterSpawnerWithLotteryRoot(
                    ()->{
                        bridge.openLottery(
                            LotteryService
                                .Channel.BLOODCORE,
                            writer,
                            "[g133-bloodcore-open] "
                        );
                        return "LOTTERY_ROOT_OPENED";
                    }
                );

            bloodcoreRoot61150=
                "LOTTERY_ROOT_OPENED"
                    .equals(bloodcoreOpen)&&
                LotteryPresentation
                    .BLOODCORE_ROOT==61150&&
                bridge.lottery.openChannel()==
                    LotteryService.Channel.BLOODCORE;

            require(
                bloodcoreRoot61150,
                "Bloodcore Lottery root"
            );

            ui.handleWidget(
                LotteryPresentation
                    .BLOODCORE_ENTRY_WIDGET,
                writer,
                "[g133-bloodcore-entry] "
            );

            bloodcoreEntry61196Disabled=
                bridge.actionCalls==3&&
                bridge.lastResult.channel==
                    LotteryService.Channel.BLOODCORE&&
                !bridge.lastResult.succeeded&&
                "DISABLED_NO_GAMEPLAY_AUTHORITY"
                    .equals(
                        bridge.lastResult.status
                    );

            require(
                bloodcoreEntry61196Disabled,
                "Bloodcore Lottery entry did not fail closed"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g133-close] "
            );

            interfaceCloseRetires=
                !bridge.lottery.isOpen();

            require(
                interfaceCloseRetires,
                "interface close did not retire Lottery"
            );

            int callsBeforeClosed=
                bridge.actionCalls;

            ui.handleWidget(
                LotteryPresentation
                    .ORDINARY_ENTRY_WIDGET,
                writer,
                "[g133-closed-entry] "
            );

            closedUiNoop=
                bridge.actionCalls==
                    callsBeforeClosed;

            require(
                closedUiNoop,
                "closed Lottery entry escaped root fence"
            );

            ui.replaceMonsterSpawnerWithLotteryRoot(
                ()->{
                    bridge.openLottery(
                        LotteryService
                            .Channel.ORDINARY,
                        writer,
                        "[g133-reopen] "
                    );
                    return "LOTTERY_ROOT_OPENED";
                }
            );

            require(
                bridge.lottery.isOpen(),
                "Lottery reopen precondition"
            );

            ui.replaceMonsterSpawnerWithGoodwillWellRoot(
                ()->"GOODWILL_ROOT_OPENED"
            );

            competingRootRetires=
                !bridge.lottery.isOpen();

            require(
                competingRootRetires,
                "competing root did not retire Lottery"
            );

            for(Field field:
                    LocalLotteryUiHandler.class
                        .getDeclaredFields())
                if(field.getType()==
                        LotteryService.class)
                    roundInvented=true;

            require(
                !roundInvented,
                "Lottery live shell invented LotteryService round state"
            );

            System.out.println(
                "G133_LOTTERY_LIVE_FAIL_CLOSED_PASS"+
                " ordinaryCommand="+ordinaryCommand+
                " bloodcoreCommand="+bloodcoreCommand+
                " ordinaryRoot52000="+ordinaryRoot52000+
                " bloodcoreRoot61150="+bloodcoreRoot61150+
                " exactStatusChannels="+exactStatusChannels+
                " emptyHistory6="+emptyHistory6+
                " emptyHistory35="+emptyHistory35+
                " ordinaryEntry52010Disabled="+
                    ordinaryEntry52010Disabled+
                " bloodcoreEntry61196Disabled="+
                    bloodcoreEntry61196Disabled+
                " channelMismatchNoop="+
                    channelMismatchNoop+
                " closedUiNoop="+closedUiNoop+
                " interfaceCloseRetires="+
                    interfaceCloseRetires+
                " competingRootRetires="+
                    competingRootRetires+
                " roundInvented="+roundInvented+
                " entryEconomicsClaim=false"+
                " rngClaim=false"+
                " prizeClaim=false"+
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

    private G133LotteryLiveFailClosedIntegrationTest(){}
}
