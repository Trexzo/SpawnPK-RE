package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Owns the exact current interface-close and widget-action routing that was
 * previously embedded in LocalSession.
 *
 * Session-only lifecycle effects stay behind a narrow bridge so this handler
 * does not own sockets, persistence implementation, or logout loop control.
 */
final class LocalSessionUiActionHandler {
    static final class MonsterSpawnerDispatch {
        final boolean admitted;
        final boolean closedUi;
        final LocalMonsterSpawnerUiHandler.Result result;

        private MonsterSpawnerDispatch(
            boolean admitted,
            boolean closedUi,
            LocalMonsterSpawnerUiHandler.Result result
        ){
            this.admitted=admitted;
            this.closedUi=closedUi;
            this.result=result;
        }

        static MonsterSpawnerDispatch admitted(
            LocalMonsterSpawnerUiHandler.Result result
        ){
            return new MonsterSpawnerDispatch(
                true,
                false,
                result
            );
        }

        static MonsterSpawnerDispatch closedUi(){
            return new MonsterSpawnerDispatch(
                true,
                true,
                null
            );
        }

        static MonsterSpawnerDispatch rejected(){
            return new MonsterSpawnerDispatch(
                false,
                false,
                null
            );
        }
    }

    interface RootInterfaceAction {
        String publish() throws IOException;
    }

    interface RootInterfaceBooleanAction {
        boolean publish() throws IOException;
    }

    interface RootCommitAction {
        void commit();
    }

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
        default void handleTeleportNavigation(
            TeleportNavigationService.EntryKind kind,
            ServerPacketWriter serverPackets,
            String tag
        )throws IOException{}
        default void handleMonsterSpawnerResult(
            LocalMonsterSpawnerUiHandler.Result result,
            ServerPacketWriter serverPackets,
            String tag
        )throws IOException{}

        default MonsterSpawnerDispatch handleMonsterSpawnerWidget(
            LocalMonsterSpawnerUiHandler handler,
            int widget,
            ServerPacketWriter serverPackets,
            String tag
        )throws IOException{
            LocalMonsterSpawnerUiHandler.Result result=
                Objects.requireNonNull(
                    handler,
                    "handler"
                ).handle(
                    widget,
                    serverPackets
                );

            if(result!=null)
                handleMonsterSpawnerResult(
                    result,
                    serverPackets,
                    tag
                );

            return MonsterSpawnerDispatch.admitted(
                result
            );
        }

        default MonsterSpawnerDispatch handleMonsterSpawnerWidget(
            LocalMonsterSpawnerUiHandler handler,
            int widget,
            ServerPacketWriter serverPackets,
            String tag,
            BooleanSupplier uiOpen
        )throws IOException{
            if(!Objects.requireNonNull(
                    uiOpen,
                    "uiOpen"
                ).getAsBoolean())
                return MonsterSpawnerDispatch.closedUi();

            return handleMonsterSpawnerWidget(
                handler,
                widget,
                serverPackets,
                tag
            );
        }

        default boolean closeMonsterSpawnerUi(
            BooleanSupplier closeAction
        )throws IOException{
            return Objects.requireNonNull(
                closeAction,
                "closeAction"
            ).getAsBoolean();
        }

        default String replaceMonsterSpawnerRoot(
            RootInterfaceAction action
        )throws IOException{
            return Objects.requireNonNull(
                action,
                "action"
            ).publish();
        }

        default boolean retireMakeoverDesignerRoot(){
            return false;
        }

        default boolean retireLootingBagRoot(){
            return false;
        }

        default boolean retireTournamentRoot(){
            return false;
        }

        default LocalTournamentUiHandler.Result
            handleTournamentWidget(
                TournamentPresentation.Input input,
                String tag
            )throws IOException{
            return null;
        }

        default LocalBloodSlayerUiHandler.Result
            handleBloodSlayerWidget(
                BloodSlayerPresentation.Input input,
                ServerPacketWriter serverPackets,
                String tag
            )throws IOException{
            return null;
        }

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
    private volatile boolean monsterSpawnerUiOpen;
    private volatile boolean bloodSlayerUiOpen;
    private final LocalBossTeleportUiHandler bossTeleportUiHandler;
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
            monsterSpawnerUiHandler,
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
        LocalBossTeleportUiHandler bossTeleportUiHandler,
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
        this.bossTeleportUiHandler=bossTeleportUiHandler;
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
        LocalMonsterSpawnerUiHandler configured=
            monsterSpawnerUiHandler;

        if(configured==null)
            return false;

        configured.open(
            Objects.requireNonNull(
                serverPackets,
                "serverPackets"
            )
        );
        itemLibrary.close();
        bridge.retireMakeoverDesignerRoot();
        bank.clientClosed();
        compCapeCustomize.close();
        devPanel.close();
        bridge.clearDialogNumberKeys();
        closeBossTeleportUi();
        bridge.retireLootingBagRoot();
        bridge.retireTournamentRoot();
        bloodSlayerUiOpen=false;
        monsterSpawnerUiOpen=true;
        return true;
    }

    boolean openBossTeleportIfConfigured(
        ServerPacketWriter serverPackets
    )throws IOException{
        LocalBossTeleportUiHandler configured=
            bossTeleportUiHandler;

        if(configured==null)
            return false;

        String result=
            bridge.replaceMonsterSpawnerRoot(
                ()->
                    publishBossTeleportRootForOwnedSession(
                        ()->
                            configured
                                .open(
                                    Objects.requireNonNull(
                                        serverPackets,
                                        "serverPackets"
                                    )
                                )
                                .detail
                    )
            );

        return result!=null;
    }

    boolean openBloodSlayer(
        ServerPacketWriter serverPackets
    )throws IOException{
        String result=
            replaceMonsterSpawnerRoot(
                ()->{
                    BloodSlayerPresentation.open(
                        Objects.requireNonNull(serverPackets,"serverPackets")
                    );
                    return "BLOOD_SLAYER_ROOT_OPENED";
                }
            );

        if(result==null)
            return false;

        bloodSlayerUiOpen=true;
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

        boolean monsterSpawnerWasOpen=
            bridge.closeMonsterSpawnerUi(
                ()->{
                    boolean wasOpen=
                        monsterSpawnerUiOpen;
                    monsterSpawnerUiOpen=false;
                    return wasOpen;
                }
            );

        boolean bossTeleportWasOpen=
            closeBossTeleportUi();

        boolean bloodSlayerWasOpen=bloodSlayerUiOpen;
        bloodSlayerUiOpen=false;

        boolean lootingBagWasOpen=
            bridge.retireLootingBagRoot();

        boolean tournamentWasOpen=
            bridge.retireTournamentRoot();

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
            " monsterSpawnerWasOpen="+monsterSpawnerWasOpen+
            " bossTeleportWasOpen="+bossTeleportWasOpen+
            " bloodSlayerWasOpen="+bloodSlayerWasOpen+
            " lootingBagWasOpen="+lootingBagWasOpen+
            " tournamentWasOpen="+tournamentWasOpen+
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
            if(isDevPanelRootReplacementWidget(
                    widget
                )){
                String replaced=
                    replaceMonsterSpawnerRoot(
                        ()->{
                            bridge.handleDevPanelWidget(
                                widget,
                                serverPackets,
                                tag
                            );
                            return "DEV_PANEL_ROOT_REPLACED";
                        }
                    );

                if(replaced==null)
                    System.out.println(
                        tag+
                        "V5171_DEV_PANEL widget="+
                        widget+
                        " result=LIFECYCLE_REJECTED"
                    );

                return;
            }

            bridge.handleDevPanelWidget(
                widget,
                serverPackets,
                tag
            );
            return;
        }

        if(widget==NativeEquipmentDeathUi.EQUIPMENT_STATS_BUTTON){
            String result=
                replaceMonsterSpawnerRoot(
                    ()->
                        NativeEquipmentDeathUi.openEquipmentStats(
                            serverPackets,
                            equipment
                        )
                );

            if(result==null){
                System.out.println(
                    tag+
                    "V5140_EQUIPMENT_STATS widget="+
                    widget+
                    " result=LIFECYCLE_REJECTED"
                );
                return;
            }

            System.out.println(
                tag+"V5140_EQUIPMENT_STATS widget="+widget+
                " result="+result
            );
            return;
        }

        if(widget==NativeEquipmentDeathUi.DEATH_BUTTON){
            String result=
                replaceMonsterSpawnerRoot(
                    ()->
                        NativeEquipmentDeathUi.openDeathPreview(
                            serverPackets,
                            bank,
                            equipment
                        )
                );

            if(result==null){
                System.out.println(
                    tag+
                    "V5140_DEATH_PREVIEW widget="+
                    widget+
                    " result=LIFECYCLE_REJECTED"
                );
                return;
            }

            System.out.println(
                tag+"V5140_DEATH_PREVIEW widget="+widget+
                " result="+result
            );
            return;
        }

        BloodSlayerPresentation.Input bloodSlayerInput=
            BloodSlayerPresentation.resolveWidget(widget);

        if(bloodSlayerInput!=null){
            if(!bloodSlayerUiOpen){
                System.out.println(
                    tag+"G4_BLOOD_SLAYER_UI widget="+widget+
                    " status=CLOSED_UI_NOOP"
                );
                return;
            }

            LocalBloodSlayerUiHandler.Result result=
                bridge.handleBloodSlayerWidget(
                    bloodSlayerInput,
                    serverPackets,
                    tag
                );

            if(result==null){
                System.out.println(
                    tag+"G4_BLOOD_SLAYER_UI widget="+widget+
                    " status=UNCONFIGURED_HANDLER_NOOP"
                );
                return;
            }

            System.out.println(
                tag+"G4_BLOOD_SLAYER_UI widget="+widget+
                " status="+result.status+
                " mode="+result.mode+
                " c2s="+BloodSlayerPresentation.WIDGET_ACTION_OPCODE+
                " authority="+LocalBloodSlayerUiHandler.AUTHORITY
            );
            return;
        }

        TournamentPresentation.Input tournamentInput=
            TournamentPresentation.resolveWidget(
                widget
            );

        if(tournamentInput!=null&&
           tournamentInput.kind!=
                TournamentPresentation.InputKind
                    .LEADERBOARD_FILTER){
            LocalTournamentUiHandler.Result
                tournament=
                    bridge.handleTournamentWidget(
                        tournamentInput,
                        tag
                    );

            if(tournament==null){
                System.out.println(
                    tag+
                    "G91_TOURNAMENT_UI widget="+
                    widget+
                    " status=UNCONFIGURED_HANDLER_NOOP"
                );
                return;
            }

            System.out.println(
                tag+
                "G91_TOURNAMENT_UI widget="+
                widget+
                " status="+
                tournament.status+
                " input="+
                tournament.inputKind+
                " entrants="+
                tournament.entrantCount+
                " detail=["+
                tournament.detail+
                "] c2s="+
                TournamentPresentation
                    .WIDGET_ACTION_OPCODE+
                " presentationAuthority="+
                TournamentPresentation
                    .PRESENTATION_AUTHORITY+
                " gameplayAuthority="+
                LocalLabTournamentRuntime
                    .AUTHORITY
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

        if(configuredMonsterSpawner!=null&&
           configuredMonsterSpawner.ownsWidget(
                widget
           )){
            MonsterSpawnerDispatch dispatch=
                bridge.handleMonsterSpawnerWidget(
                    configuredMonsterSpawner,
                    widget,
                    serverPackets,
                    tag,
                    ()->monsterSpawnerUiOpen
                );

            if(!dispatch.admitted){
                System.out.println(
                    tag+
                    "MONSTER_SPAWNER_UI widget="+
                    widget+
                    " status=LIFECYCLE_REJECTED"
                );
                return;
            }

            if(dispatch.closedUi){
                System.out.println(
                    tag+
                    "MONSTER_SPAWNER_UI widget="+
                    widget+
                    " status=CLOSED_UI_NOOP"
                );
                return;
            }

            LocalMonsterSpawnerUiHandler.Result monsterSpawner=
                dispatch.result;

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
                return;
            }

            System.out.println(
                tag+
                "MONSTER_SPAWNER_UI widget="+
                widget+
                " status=UNCONFIGURED_ROW_NOOP"
            );
            return;
        }

        LocalBossTeleportUiHandler configuredBossTeleport=
            bossTeleportUiHandler;

        if(configuredBossTeleport!=null&&
           configuredBossTeleport.ownsWidget(
                widget
           )){
            LocalBossTeleportUiHandler.Result boss=
                configuredBossTeleport.handleWidget(
                    widget,
                    serverPackets,
                    tag
                );

            boolean monsterSpawnerHandoff=false;

            if(boss.status==
                    LocalBossTeleportUiHandler.Status.TELEPORTED&&
               boss.teleportSucceeded)
                monsterSpawnerHandoff=
                    openMonsterSpawnerIfConfigured(
                        serverPackets
                    );

            System.out.println(
                tag+
                "G3_BOSS_TELEPORT_UI widget="+
                widget+
                " status="+
                boss.status+
                " row="+
                boss.rowIndex+
                " success="+
                boss.teleportSucceeded+
                " monsterSpawnerHandoff="+
                monsterSpawnerHandoff+
                " detail=["+
                boss.detail+
                "] authority="+
                LocalBossTeleportUiHandler.POLICY_AUTHORITY
            );
            return;
        }

        TeleportNavigationService.EntryKind navigationKind=
            TeleportNavigationWidgetAdapter.resolve(
                widget
            );

        if(navigationKind!=null&&
           navigationKind!=TeleportNavigationService.EntryKind.HOME){
            if(navigationKind==
                    TeleportNavigationService.EntryKind.BOSS&&
               bossTeleportUiHandler!=null){
                boolean opened=
                    openBossTeleportIfConfigured(
                        serverPackets
                    );

                System.out.println(
                    tag+
                    "G3_BOSS_TELEPORT_OPEN topLevelWidget="+
                    widget+
                    " opened="+
                    opened+
                    " root="+
                    BossTeleportPresentation.ROOT+
                    " directRelocation=false"+
                    " authority="+
                    LocalBossTeleportUiHandler.POLICY_AUTHORITY
                );
                return;
            }

            bridge.handleTeleportNavigation(
                navigationKind,
                serverPackets,
                tag
            );
            return;
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

    String replaceMonsterSpawnerRootCommand(
        RootInterfaceBooleanAction publisher
    )throws IOException{
        RootInterfaceBooleanAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        return replaceMonsterSpawnerRoot(
            ()->
                checked.publish()
                    ?"ROOT_COMMAND_HANDLED"
                    :"ROOT_COMMAND_NOT_HANDLED"
        );
    }

    String replaceMonsterSpawnerWithItemLibraryRootCommand(
        RootInterfaceBooleanAction publisher
    )throws IOException{
        RootInterfaceBooleanAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        return replaceMonsterSpawnerWithItemLibraryRoot(
            ()->
                checked.publish()
                    ?"ROOT_COMMAND_HANDLED"
                    :"ROOT_COMMAND_NOT_HANDLED"
        );
    }

    String publishDevPanelRootForOwnedSession(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        String result=
            checked.publish();

        monsterSpawnerUiOpen=false;
        bloodSlayerUiOpen=false;
        itemLibrary.close();

        bridge.retireMakeoverDesignerRoot();
        bank.clientClosed();
        compCapeCustomize.close();
        closeBossTeleportUi();
        bridge.retireLootingBagRoot();
        bridge.retireTournamentRoot();
        return result;
    }

    String publishBankRootForOwnedSession(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        String result=
            checked.publish();

        monsterSpawnerUiOpen=false;
        bloodSlayerUiOpen=false;
        itemLibrary.close();

        bridge.retireMakeoverDesignerRoot();
        compCapeCustomize.close();
        devPanel.close();
        bridge.clearDialogNumberKeys();
        closeBossTeleportUi();
        bridge.retireLootingBagRoot();
        bridge.retireTournamentRoot();
        return result;
    }

    String publishCompCapeRootForOwnedSession(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        String result=
            checked.publish();

        monsterSpawnerUiOpen=false;
        bloodSlayerUiOpen=false;
        itemLibrary.close();

        bridge.retireMakeoverDesignerRoot();
        bank.clientClosed();
        devPanel.close();
        bridge.clearDialogNumberKeys();
        closeBossTeleportUi();
        bridge.retireLootingBagRoot();
        bridge.retireTournamentRoot();
        return result;
    }

    String publishMakeoverRootForOwnedSession(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        String result=
            checked.publish();

        monsterSpawnerUiOpen=false;
        bloodSlayerUiOpen=false;
        itemLibrary.close();

        bank.clientClosed();
        compCapeCustomize.close();
        devPanel.close();
        bridge.clearDialogNumberKeys();
        closeBossTeleportUi();
        bridge.retireLootingBagRoot();
        bridge.retireTournamentRoot();
        return result;
    }

    String publishTradeRootForOwnedSession(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        String result=
            checked.publish();

        monsterSpawnerUiOpen=false;
        bloodSlayerUiOpen=false;
        itemLibrary.close();

        bridge.retireMakeoverDesignerRoot();
        bank.clientClosed();
        compCapeCustomize.close();
        devPanel.close();
        bridge.clearDialogNumberKeys();
        closeBossTeleportUi();
        bridge.retireLootingBagRoot();
        bridge.retireTournamentRoot();
        return result;
    }

    String publishItemLibraryRootForOwnedSession(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        String result=
            checked.publish();

        monsterSpawnerUiOpen=false;
        bloodSlayerUiOpen=false;
        bridge.retireMakeoverDesignerRoot();
        bank.clientClosed();
        compCapeCustomize.close();
        devPanel.close();
        bridge.clearDialogNumberKeys();
        closeBossTeleportUi();
        bridge.retireLootingBagRoot();
        bridge.retireTournamentRoot();
        return result;
    }

    String publishCompetingRootForOwnedSession(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        String result=
            checked.publish();

        monsterSpawnerUiOpen=false;
        bloodSlayerUiOpen=false;
        itemLibrary.close();

        bridge.retireMakeoverDesignerRoot();
        bank.clientClosed();
        compCapeCustomize.close();
        devPanel.close();
        bridge.clearDialogNumberKeys();
        closeBossTeleportUi();
        bridge.retireLootingBagRoot();
        bridge.retireTournamentRoot();
        return result;
    }

    String publishLootingBagRootForOwnedSession(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        String result=
            checked.publish();

        monsterSpawnerUiOpen=false;
        bloodSlayerUiOpen=false;
        itemLibrary.close();

        bridge.retireMakeoverDesignerRoot();
        bank.clientClosed();
        compCapeCustomize.close();
        devPanel.close();
        bridge.clearDialogNumberKeys();
        closeBossTeleportUi();
        bridge.retireTournamentRoot();

        return result;
    }

    String publishTournamentRootForOwnedSession(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        String result=
            checked.publish();

        monsterSpawnerUiOpen=false;
        bloodSlayerUiOpen=false;
        itemLibrary.close();

        bridge.retireMakeoverDesignerRoot();
        bank.clientClosed();
        compCapeCustomize.close();
        devPanel.close();
        bridge.clearDialogNumberKeys();
        closeBossTeleportUi();
        bridge.retireLootingBagRoot();

        return result;
    }

    String publishBossTeleportRootForOwnedSession(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        String result=
            checked.publish();

        monsterSpawnerUiOpen=false;
        bloodSlayerUiOpen=false;
        itemLibrary.close();

        bridge.retireMakeoverDesignerRoot();
        bank.clientClosed();
        compCapeCustomize.close();
        devPanel.close();
        bridge.clearDialogNumberKeys();

        bridge.retireLootingBagRoot();
        bridge.retireTournamentRoot();
        return result;
    }

    private boolean closeBossTeleportUi(){
        LocalBossTeleportUiHandler configured=
            bossTeleportUiHandler;

        return configured!=null&&
            configured.close();
    }

    String replaceMonsterSpawnerWithItemLibraryRoot(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        return bridge.replaceMonsterSpawnerRoot(
            ()->
                publishItemLibraryRootForOwnedSession(
                    checked
                )
        );
    }

    String replaceMonsterSpawnerWithMakeoverRoot(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        return bridge.replaceMonsterSpawnerRoot(
            ()->
                publishMakeoverRootForOwnedSession(
                    checked
                )
        );
    }

    String replaceMonsterSpawnerWithMakeoverRoot(
        RootInterfaceAction publisher,
        RootCommitAction commit
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );
        RootCommitAction checkedCommit=
            Objects.requireNonNull(
                commit,
                "commit"
            );

        return bridge.replaceMonsterSpawnerRoot(
            ()->{
                String result=
                    publishMakeoverRootForOwnedSession(
                        checked
                    );
                checkedCommit.commit();
                return result;
            }
        );
    }

    String replaceMonsterSpawnerWithCompCapeRoot(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        return bridge.replaceMonsterSpawnerRoot(
            ()->
                publishCompCapeRootForOwnedSession(
                    checked
                )
        );
    }

    String replaceMonsterSpawnerWithBankRoot(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        return bridge.replaceMonsterSpawnerRoot(
            ()->
                publishBankRootForOwnedSession(
                    checked
                )
        );
    }

    String replaceMonsterSpawnerWithLootingBagRoot(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        return bridge.replaceMonsterSpawnerRoot(
            ()->
                publishLootingBagRootForOwnedSession(
                    checked
                )
        );
    }

    String replaceMonsterSpawnerWithTournamentRoot(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        return bridge.replaceMonsterSpawnerRoot(
            ()->
                publishTournamentRootForOwnedSession(
                    checked
                )
        );
    }

    String replaceMonsterSpawnerRoot(
        RootInterfaceAction publisher
    )throws IOException{
        RootInterfaceAction checked=
            Objects.requireNonNull(
                publisher,
                "publisher"
            );

        return bridge.replaceMonsterSpawnerRoot(
            ()->
                publishCompetingRootForOwnedSession(
                    checked
                )
        );
    }

    private boolean isDevPanelRootReplacementWidget(
        int widget
    ){
        if(devPanel.page()!=
                DevControlCenter.Page.ITEMS)
            return false;

        int choice=widget-2482;

        return choice==1||
            choice==2;
    }

    static boolean isDevPanelWidget(int widget){
        return widget==54195||
            (widget>=2482&&widget<=2485);
    }
}
