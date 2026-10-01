package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Owns the exact current interface-close and widget-action routing that was
 * previously embedded in LocalSession.
 *
 * Session-only lifecycle effects stay behind a narrow bridge so this handler
 * does not own sockets, persistence implementation, or logout loop control.
 */
final class LocalSessionUiActionHandler {
    interface SessionBridge {
        void saveAccount(String tag,String reason);
        void clearDialogNumberKeys();
        void handleDevPanelWidget(
            int widget,
            ServerPacketWriter serverPackets,
            String tag
        )throws IOException;
        void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        );
        default void handleHomeTeleport(
            ServerPacketWriter serverPackets,
            String tag
        )throws IOException{}
        default void handleMonsterSpawnerResult(
            LocalMonsterSpawnerUiHandler.Result result,
            ServerPacketWriter serverPackets,
            String tag
        )throws IOException{}
        void requestLogout();
    }

    private final WorldPlayer worldPlayer;
    private final NativeItemLibraryService itemLibrary;
    private final DevControlCenter devPanel;
    private final BankState bank;
    private final LocalCompCapeCustomizeHandler compCapeCustomize;
    private final LocalPetInventoryDialogHandler petDialogs;
    private final LocalGameplayWidgetHandler gameplayWidgetHandler;
    private volatile LocalMonsterSpawnerUiHandler monsterSpawnerUiHandler;
    private final MovementState movement;
    private final boolean movementEnabled;
    private final EquipmentState equipment;
    private final SessionBridge bridge;

    LocalSessionUiActionHandler(
        WorldPlayer worldPlayer,
        NativeItemLibraryService itemLibrary,
        DevControlCenter devPanel,
        BankState bank,
        LocalCompCapeCustomizeHandler compCapeCustomize,
        LocalPetInventoryDialogHandler petDialogs,
        LocalGameplayWidgetHandler gameplayWidgetHandler,
        MovementState movement,
        boolean movementEnabled,
        EquipmentState equipment,
        SessionBridge bridge
    ){
        this(
            worldPlayer,
            itemLibrary,
            devPanel,
            bank,
            compCapeCustomize,
            petDialogs,
            gameplayWidgetHandler,
            movement,
            movementEnabled,
            equipment,
            null,
            bridge
        );
    }

    LocalSessionUiActionHandler(
        WorldPlayer worldPlayer,
        NativeItemLibraryService itemLibrary,
        DevControlCenter devPanel,
        BankState bank,
        LocalCompCapeCustomizeHandler compCapeCustomize,
        LocalPetInventoryDialogHandler petDialogs,
        LocalGameplayWidgetHandler gameplayWidgetHandler,
        MovementState movement,
        boolean movementEnabled,
        EquipmentState equipment,
        LocalMonsterSpawnerUiHandler monsterSpawnerUiHandler,
        SessionBridge bridge
    ){
        this.worldPlayer=Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.itemLibrary=Objects.requireNonNull(itemLibrary,"itemLibrary");
        this.devPanel=Objects.requireNonNull(devPanel,"devPanel");
        this.bank=Objects.requireNonNull(bank,"bank");
        this.compCapeCustomize=Objects.requireNonNull(
            compCapeCustomize,"compCapeCustomize");
        this.petDialogs=Objects.requireNonNull(petDialogs,"petDialogs");
        this.gameplayWidgetHandler=Objects.requireNonNull(
            gameplayWidgetHandler,"gameplayWidgetHandler");
        this.monsterSpawnerUiHandler=monsterSpawnerUiHandler;
        this.movement=Objects.requireNonNull(movement,"movement");
        this.movementEnabled=movementEnabled;
        this.equipment=Objects.requireNonNull(equipment,"equipment");
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    synchronized void installMonsterSpawnerUiHandler(
        LocalMonsterSpawnerUiHandler adapter
    ){
        LocalMonsterSpawnerUiHandler checked=
            Objects.requireNonNull(
                adapter,
                "adapter"
            );

        if(monsterSpawnerUiHandler==null){
            monsterSpawnerUiHandler=checked;
            return;
        }

        if(monsterSpawnerUiHandler!=checked)
            throw new IllegalStateException(
                "Monster Spawner UI adapter already installed"
            );
    }

    boolean openMonsterSpawnerIfConfigured(
        ServerPacketWriter serverPackets
    )throws IOException{
        if(monsterSpawnerUiHandler==null)
            return false;

        monsterSpawnerUiHandler.open(
            Objects.requireNonNull(
                serverPackets,
                "serverPackets"
            )
        );
        return true;
    }

    void handleInterfaceClose(
        boolean decoderAligned,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        boolean tradeWasOpen=
            TradeService.cancelIfActive(
                worldPlayer,
                "CLIENT_INTERFACE_CLOSE"
            );
        boolean itemLibraryWasOpen=itemLibrary.isOpen();
        itemLibrary.close();

        boolean devPanelWasOpen=
            devPanel.isOpen()||devPanel.hasPending();
        devPanel.close();
        bridge.clearDialogNumberKeys();

        boolean wasOpen=bank.clientClosed();
        boolean compWasOpen=compCapeCustomize.close();

        LocalPetInventoryDialogHandler.CloseState petDialogClose=
            petDialogs.clearAll();
        boolean petColorWasOpen=petDialogClose.petColorWasOpen;
        boolean miniConfigWasOpen=petDialogClose.miniConfigWasOpen;
        boolean petAccessoryWasOpen=
            petDialogClose.petAccessoryWasOpen;

        // Bank overlay inventory is widget 5064; once the overlay closes the
        // normal inventory is widget 3214. Re-send 3214 so withdrawals remain
        // visible.
        if(wasOpen)
            bank.sendNormalInventory(serverPackets);

        bridge.saveAccount(tag,"INTERFACE_CLOSE");

        System.out.println(
            tag+"V522_INTERFACE_CLOSE opcode=130 bankWasOpen="+wasOpen+
            " bankOpen=false normalInventory3214Refresh="+wasOpen+
            " compCapeWasOpen="+compWasOpen+
            " tradeWasOpen="+tradeWasOpen+
            " itemLibraryWasOpen="+itemLibraryWasOpen+
            " devPanelWasOpen="+devPanelWasOpen+
            " petColorWasOpen="+petColorWasOpen+
            " miniConfigWasOpen="+miniConfigWasOpen+
            " petAccessoryWasOpen="+petAccessoryWasOpen+
            " decoderAligned="+decoderAligned
        );
    }

    void handleWidget(
        int widget,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(widget==2458){
            bridge.saveAccount(tag,"LOGOUT_BUTTON");
            serverPackets.fixed(109,new byte[0]);
            bridge.requestLogout();
            System.out.println(
                tag+
                "V5124_LOGOUT widget=2458 result=S2C109_LOGOUT_DISCONNECT save=true"
            );
            return;
        }

        if(devPanel.isOpen()&&isDevPanelWidget(widget)){
            bridge.handleDevPanelWidget(
                widget,
                serverPackets,
                tag
            );
            return;
        }

        if(widget==NativeEquipmentDeathUi.EQUIPMENT_STATS_BUTTON){
            String result=
                NativeEquipmentDeathUi.openEquipmentStats(
                    serverPackets,
                    equipment
                );
            System.out.println(
                tag+"V5140_EQUIPMENT_STATS widget="+widget+
                " result="+result
            );
            return;
        }

        if(widget==NativeEquipmentDeathUi.DEATH_BUTTON){
            String result=
                NativeEquipmentDeathUi.openDeathPreview(
                    serverPackets,
                    bank,
                    equipment
                );
            System.out.println(
                tag+"V5140_DEATH_PREVIEW widget="+widget+
                " result="+result
            );
            return;
        }

        String itemLibraryWidget=
            itemLibrary.handleWidget(serverPackets,widget);
        if(itemLibraryWidget!=null){
            System.out.println(
                tag+"V5150_ITEM_LIBRARY_WIDGET widget="+widget+
                " result="+itemLibraryWidget
            );
            return;
        }

        String tradeWidget=
            TradeService.handleWidget(worldPlayer,widget);
        if(tradeWidget!=null){
            System.out.println(
                tag+"V5140_TRADE_WIDGET widget="+widget+
                " result="+tradeWidget
            );
            return;
        }

        LocalMonsterSpawnerUiHandler configuredMonsterSpawner=
            monsterSpawnerUiHandler;

        if(configuredMonsterSpawner!=null){
            boolean monsterSpawnerWidget=
                configuredMonsterSpawner.ownsWidget(
                    widget
                );
            LocalMonsterSpawnerUiHandler.Result monsterSpawner=
                configuredMonsterSpawner.handle(
                    widget,
                    serverPackets
                );

            if(monsterSpawner!=null){
                System.out.println(
                    tag+
                    "MONSTER_SPAWNER_UI widget="+
                    widget+
                    " status="+
                    monsterSpawner.status+
                    " row="+
                    monsterSpawner.rowIndex+
                    " budget="+
                    monsterSpawner.activationBudget
                );

                bridge.handleMonsterSpawnerResult(
                    monsterSpawner,
                    serverPackets,
                    tag
                );
                return;
            }

            if(monsterSpawnerWidget){
                System.out.println(
                    tag+
                    "MONSTER_SPAWNER_UI widget="+
                    widget+
                    " status=UNCONFIGURED_ROW_NOOP"
                );
                return;
            }
        }

        String gameplayWidget=
            gameplayWidgetHandler.handle(widget,serverPackets);
        if(gameplayWidget!=null){
            System.out.println(tag+gameplayWidget);

            if(gameplayWidgetHandler.consumeAcceptedHomeTeleport())
                bridge.handleHomeTeleport(
                    serverPackets,
                    tag
                );

            return;
        }

        LocalPetInventoryDialogHandler.Result petDialogWidget=
            petDialogs.handleWidget(widget,serverPackets);
        if(petDialogWidget!=null){
            bridge.applyPetDialog(petDialogWidget,tag);
            return;
        }

        String compCapeWidget=
            compCapeCustomize.handleWidget(widget,serverPackets);
        if(compCapeWidget!=null){
            System.out.println(tag+compCapeWidget);
            return;
        }

        if(widget==152){
            if(!movementEnabled){
                System.out.println(
                    tag+
                    "M5_RUN_TOGGLE widget=152 action=OBSERVE_ONLY movementAuthority=false"
                );
                return;
            }

            boolean enabled=movement.togglePersistentRun();
            serverPackets.fixed(
                36,
                BootstrapPackets.config36(
                    173,
                    enabled?1:0
                )
            );
            bridge.saveAccount(tag,"RUN_TOGGLE");
            System.out.println(
                tag+"M5_RUN_TOGGLE widget=152 enabled="+enabled+
                " opcode=36 setting=173 value="+(enabled?1:0)+
                " authority=PERSISTENT_ACCOUNT_TOGGLE"
            );
            return;
        }

        if(widget==BankState.DEPOSIT_INVENTORY_WIDGET){
            String result=
                bank.depositInventory(serverPackets);
            bridge.saveAccount(
                tag,
                "BANK_DEPOSIT_INVENTORY"
            );
            System.out.println(
                tag+"V4_BANK_WIDGET widget="+widget+
                " action=DEPOSIT_INVENTORY result="+result
            );
            return;
        }

        if(widget==BankState.TOGGLE_PLACEHOLDERS_WIDGET){
            String result=
                bank.togglePlaceholders(serverPackets);
            bridge.saveAccount(tag,"BANK_PLACEHOLDERS");
            System.out.println(
                tag+"V4_BANK_WIDGET widget="+widget+
                " action=TOGGLE_PLACEHOLDERS result="+result
            );
            return;
        }

        if(widget==5384||widget==5380){
            bank.close(serverPackets);
            System.out.println(
                tag+"V4_BANK_WIDGET widget="+widget+
                " action=CLOSE_BANK bankOpen="+bank.isOpen()
            );
        }
    }

    static boolean isDevPanelWidget(int widget){
        return widget==54195||
            (widget>=2482&&widget<=2485);
    }
}
