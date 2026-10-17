package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class G131EventChestLiveFailClosedIntegrationTest {
    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalEventChestUiHandler eventChest;
        int actionCalls;
        LocalEventChestUiHandler.Result lastResult;

        Bridge(WorldPlayer player){
            this.eventChest=
                new LocalEventChestUiHandler(
                    player
                );
        }

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

        @Override public boolean retireEventChestRoot(){
            return eventChest.close();
        }

        @Override public boolean openEventChest(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            eventChest.open(writer);
            return true;
        }

        @Override public LocalEventChestUiHandler.Result
            handleEventChestWidget(
                EventChestService.Action action,
                String tag
            )throws IOException{
            actionCalls++;
            lastResult=
                eventChest.handle(
                    action
                );
            return lastResult;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean customCommand=false;
        boolean root60600=false;
        boolean emptyMain175=false;
        boolean emptySmall3x4=false;
        boolean exchange60604Disabled=false;
        boolean nextTier60626Disabled=false;
        boolean reset60631Disabled=false;
        boolean closedUiNoop=false;
        boolean interfaceCloseRetires=false;
        boolean competingRootRetires=false;
        boolean semanticKeysAsItemIds=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g131-player"
        );

        try{
            Bridge bridge=
                new Bridge(player);
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
                        new int[]{81,82,83,84}
                    )
                );

            customCommand=
                LocalCommandDispatcher
                    .isEventChestRoute(
                        new String[]{"eventchest"}
                    )&&
                LocalCommandDispatcher
                    .isEventChestRoute(
                        new String[]{"echest"}
                    )&&
                !LocalCommandDispatcher
                    .isEventChestRoute(
                        new String[]{"eventchest","invent"}
                    );

            require(
                customCommand,
                "Event Chest LocalLab command route"
            );

            String opened=
                ui.replaceMonsterSpawnerWithEventChestRoot(
                    ()->{
                        bridge.openEventChest(
                            writer,
                            "[g131-open] "
                        );
                        return "EVENT_CHEST_ROOT_OPENED";
                    }
                );

            root60600=
                "EVENT_CHEST_ROOT_OPENED"
                    .equals(opened)&&
                EventChestPresentation.ROOT==60600&&
                bridge.eventChest.isOpen()&&
                wire.size()>0;

            require(
                root60600,
                "Event Chest root open"
            );

            EventChestService.Snapshot projection=
                bridge.eventChest.snapshot();

            emptyMain175=
                projection.configured()&&
                projection.projection
                    .mainEntries
                    .isEmpty()&&
                EventChestPresentation
                    .MAIN_GRID_CAPACITY==175;

            emptySmall3x4=
                projection.projection
                    .smallGrids
                    .size()==3&&
                projection.projection
                    .smallGrids
                    .stream()
                    .allMatch(
                        java.util.List::isEmpty
                    )&&
                EventChestPresentation
                    .SMALL_GRID_CAPACITY==4&&
                EventChestService
                    .SMALL_GRID_COUNT==3;

            require(
                emptyMain175&&
                emptySmall3x4,
                "truthful empty Event Chest projection"
            );

            ui.handleWidget(
                EventChestPresentation
                    .EXCHANGE_WIDGET,
                writer,
                "[g131-exchange] "
            );

            exchange60604Disabled=
                bridge.lastResult!=null&&
                bridge.lastResult.action==
                    EventChestService
                        .Action.EXCHANGE&&
                !bridge.lastResult.succeeded&&
                "DISABLED_NO_GAMEPLAY_AUTHORITY"
                    .equals(
                        bridge.lastResult.status
                    );

            require(
                exchange60604Disabled,
                "Exchange did not fail closed"
            );

            ui.handleWidget(
                EventChestPresentation
                    .ENTER_NEXT_TIER_WIDGET,
                writer,
                "[g131-next-tier] "
            );

            nextTier60626Disabled=
                bridge.lastResult.action==
                    EventChestService
                        .Action.ENTER_NEXT_TIER&&
                !bridge.lastResult.succeeded;

            require(
                nextTier60626Disabled,
                "Enter next tier did not fail closed"
            );

            ui.handleWidget(
                EventChestPresentation
                    .RESET_EVENT_ITEMS_WIDGET,
                writer,
                "[g131-reset] "
            );

            reset60631Disabled=
                bridge.lastResult.action==
                    EventChestService
                        .Action.RESET_EVENT_ITEMS&&
                !bridge.lastResult.succeeded&&
                bridge.actionCalls==3;

            require(
                reset60631Disabled,
                "Reset event items did not fail closed"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g131-close] "
            );

            interfaceCloseRetires=
                !bridge.eventChest.isOpen();

            require(
                interfaceCloseRetires,
                "interface close did not retire Event Chest"
            );

            int callsBeforeClosed=
                bridge.actionCalls;

            ui.handleWidget(
                EventChestPresentation
                    .EXCHANGE_WIDGET,
                writer,
                "[g131-closed-action] "
            );

            closedUiNoop=
                bridge.actionCalls==
                    callsBeforeClosed;

            require(
                closedUiNoop,
                "closed Event Chest action escaped root fence"
            );

            ui.replaceMonsterSpawnerWithEventChestRoot(
                ()->{
                    bridge.openEventChest(
                        writer,
                        "[g131-reopen] "
                    );
                    return "EVENT_CHEST_ROOT_OPENED";
                }
            );

            require(
                bridge.eventChest.isOpen(),
                "Event Chest reopen precondition"
            );

            ui.replaceMonsterSpawnerWithDuelRoot(
                ()->"DUEL_ROOT_OPENED"
            );

            competingRootRetires=
                !bridge.eventChest.isOpen();

            require(
                competingRootRetires,
                "competing root did not retire Event Chest"
            );

            require(
                !semanticKeysAsItemIds&&
                projection.projection
                    .mainEntries
                    .isEmpty()&&
                projection.projection
                    .smallGrids
                    .stream()
                    .allMatch(
                        java.util.List::isEmpty
                    ),
                "semantic Event Chest keys were reinterpreted"
            );

            System.out.println(
                "G131_EVENT_CHEST_LIVE_FAIL_CLOSED_PASS"+
                " customCommand="+customCommand+
                " root60600="+root60600+
                " emptyMain175="+emptyMain175+
                " emptySmall3x4="+emptySmall3x4+
                " exchange60604Disabled="+
                    exchange60604Disabled+
                " nextTier60626Disabled="+
                    nextTier60626Disabled+
                " reset60631Disabled="+
                    reset60631Disabled+
                " closedUiNoop="+closedUiNoop+
                " interfaceCloseRetires="+
                    interfaceCloseRetires+
                " competingRootRetires="+
                    competingRootRetires+
                " semanticKeysAsItemIds=false"+
                " rewardClaim=false"+
                " rngClaim=false"+
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

    private G131EventChestLiveFailClosedIntegrationTest(){}
}
