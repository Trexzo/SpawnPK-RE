package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public final class LocalSessionUiActionHandlerTest {
    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        String saveReason;
        boolean clearedKeys;
        boolean logoutRequested;
        int devPanelWidgets;
        int petDialogResults;
        int homeTeleportRequests;
        int monsterSpawnerResults;
        int monsterSpawnerWidgetTransactions;
        int monsterSpawnerRootReplacements;
        LocalMonsterSpawnerUiHandler.Result lastMonsterSpawnerResult;

        @Override public void saveAccount(
            String tag,
            String reason
        ){
            saveReason=reason;
        }

        @Override public void clearDialogNumberKeys(){
            clearedKeys=true;
        }

        @Override public void handleDevPanelWidget(
            int widget,
            ServerPacketWriter serverPackets,
            String tag
        ){
            devPanelWidgets++;
        }

        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        ){
            petDialogResults++;
        }

        @Override public void handleHomeTeleport(
            ServerPacketWriter serverPackets,
            String tag
        ){
            homeTeleportRequests++;
        }

        @Override public LocalSessionUiActionHandler
            .MonsterSpawnerDispatch handleMonsterSpawnerWidget(
                LocalMonsterSpawnerUiHandler handler,
                int widget,
                ServerPacketWriter serverPackets,
                String tag
            )throws java.io.IOException{
            monsterSpawnerWidgetTransactions++;
            return LocalSessionUiActionHandler.SessionBridge.super
                .handleMonsterSpawnerWidget(
                    handler,
                    widget,
                    serverPackets,
                    tag
                );
        }

        @Override public String replaceMonsterSpawnerRoot(
            LocalSessionUiActionHandler.RootInterfaceAction action
        )throws java.io.IOException{
            monsterSpawnerRootReplacements++;
            return LocalSessionUiActionHandler.SessionBridge.super
                .replaceMonsterSpawnerRoot(
                    action
                );
        }

        @Override public void handleMonsterSpawnerResult(
            LocalMonsterSpawnerUiHandler.Result result,
            ServerPacketWriter serverPackets,
            String tag
        ){
            monsterSpawnerResults++;
            lastMonsterSpawnerResult=result;
        }

        @Override public void requestLogout(){
            logoutRequested=true;
        }
    }

    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        EquipmentState equipment=player.equipment();
        MovementState movement=player.movement();
        PetState petState=player.petState();
        PlayerState playerState=player.playerState();
        PrayerState prayers=player.prayers();
        MagicState magic=player.magic();
        CombatStyleState combatStyles=player.combatStyles();
        MiniPetService miniPets=player.miniPets();

        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        NpcRegistry npcs=new NpcRegistry(dev);
        PetAccessoryState accessory=new PetAccessoryState();

        LocalPetInventoryDialogHandler petDialogs=
            new LocalPetInventoryDialogHandler(
                bank,
                miniPets,
                petState,
                npcs,
                movement,
                accessory
            );

        LocalGameplayWidgetHandler gameplay=
            new LocalGameplayWidgetHandler(
                prayers,
                playerState,
                equipment,
                combatStyles,
                magic,
                bank
            );

        LocalCompCapeCustomizeHandler compCape=
            new LocalCompCapeCustomizeHandler(
                bank,
                playerState
            );

        Bridge bridge=new Bridge();
        LocalSessionUiActionHandler h=
            new LocalSessionUiActionHandler(
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

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter w=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(new int[]{1,2,3,4})
            );

        int absentSpawnerOpenBefore=
            wire.size();
        if(h.openMonsterSpawnerIfConfigured(
                w
            ))
            throw new AssertionError(
                "unconfigured Monster Spawner UI reported open"
            );
        if(wire.size()!=absentSpawnerOpenBefore)
            throw new AssertionError(
                "unconfigured Monster Spawner UI emitted packet"
            );

        int before=wire.size();
        h.handleWidget(2458,w,"[ui-test] ");
        if(!bridge.logoutRequested)
            throw new AssertionError("logout callback not invoked");
        if(!"LOGOUT_BUTTON".equals(bridge.saveReason))
            throw new AssertionError("logout save reason changed");
        if(wire.size()<=before)
            throw new AssertionError("logout packet not emitted");

        bridge.saveReason=null;
        int runBefore=wire.size();
        h.handleWidget(152,w,"[ui-test] ");
        if(!"RUN_TOGGLE".equals(bridge.saveReason))
            throw new AssertionError("run-toggle save reason changed");
        if(wire.size()<=runBefore)
            throw new AssertionError("run-toggle config packet not emitted");

        int homeBefore=wire.size();
        h.handleWidget(1195,w,"[ui-test] ");
        if(bridge.homeTeleportRequests!=1)
            throw new AssertionError(
                "Home Teleport bridge count="+
                bridge.homeTeleportRequests
            );
        if(wire.size()!=homeBefore)
            throw new AssertionError(
                "routing-only test unexpectedly emitted Home Teleport packets"
            );

        int absentSpawnerBefore=wire.size();
        h.handleWidget(
            MonsterSpawnerPresentation.rowWidget(0),
            w,
            "[ui-test] "
        );
        if(wire.size()!=absentSpawnerBefore)
            throw new AssertionError(
                "legacy UI route unexpectedly handled Monster Spawner row"
            );

        MonsterSpawnerService spawner=
            new MonsterSpawnerService(
                new WorldNpcRegistry()
            );
        spawner.replaceCatalog(
            Collections.singletonList(
                new MonsterSpawnerService.CatalogEntry(
                    0,
                    "session-ui-route",
                    1700
                )
            ),
            "CUSTOM_LOCALLAB_SESSION_UI_CATALOG"
        );
        spawner.openSession(
            "session-ui-owner",
            "CUSTOM_LOCALLAB_SESSION_UI_POLICY"
        );

        LocalMonsterSpawnerUiHandler monsterSpawnerUi=
            new LocalMonsterSpawnerUiHandler(
                spawner,
                "session-ui-owner",
                new LocalMonsterSpawnerUiHandler
                    .ActivationBudgetResolver(){
                    @Override public int spawnBudget(
                        LocalMonsterSpawnerUiHandler.Context context
                    ){
                        return 2;
                    }

                    @Override public String authority(){
                        return "CUSTOM_LOCALLAB_SESSION_UI_POLICY";
                    }
                },
                new LocalMonsterSpawnerUiHandler
                    .SelectedNpcLabelResolver(){
                    @Override public String label(
                        MonsterSpawnerService.CatalogEntry entry
                    ){
                        return "NPC-"+entry.definitionId;
                    }

                    @Override public String authority(){
                        return "CUSTOM_LOCALLAB_SESSION_UI_CATALOG";
                    }
                }
            );

        DevControlCenter routedDevPanel=
            new DevControlCenter();

        LocalSessionUiActionHandler routed=
            new LocalSessionUiActionHandler(
                player,
                new NativeItemLibraryService(),
                routedDevPanel,
                bank,
                compCape,
                petDialogs,
                gameplay,
                movement,
                true,
                equipment,
                monsterSpawnerUi,
                bridge
            );

        int configuredPreOpenWire=
            wire.size();
        int configuredPreOpenResults=
            bridge.monsterSpawnerResults;
        int configuredPreOpenTransactions=
            bridge.monsterSpawnerWidgetTransactions;

        routed.handleWidget(
            MonsterSpawnerPresentation.rowWidget(0),
            w,
            "[ui-test] "
        );

        MonsterSpawnerService.SessionSnapshot
            configuredPreOpenSession=
                spawner.getSession(
                    "session-ui-owner"
                );

        if(configuredPreOpenSession.selectedRowIndex!=null||
           wire.size()!=configuredPreOpenWire||
           bridge.monsterSpawnerResults!=
                configuredPreOpenResults||
           bridge.monsterSpawnerWidgetTransactions!=
                configuredPreOpenTransactions)
            throw new AssertionError(
                "closed Monster Spawner UI accepted pre-open row action"
            );

        int configuredSpawnerOpenBefore=
            wire.size();
        if(!routed.openMonsterSpawnerIfConfigured(
                w
            ))
            throw new AssertionError(
                "configured Monster Spawner UI did not report open"
            );
        if(wire.size()<=configuredSpawnerOpenBefore)
            throw new AssertionError(
                "configured Monster Spawner UI open packet missing"
            );

        int spawnerTextBefore=wire.size();
        int homeRequestsBeforeSpawner=
            bridge.homeTeleportRequests;
        int resultsBeforeUnconfigured=
            bridge.monsterSpawnerResults;

        routed.handleWidget(
            MonsterSpawnerPresentation.rowWidget(1),
            w,
            "[ui-test] "
        );

        MonsterSpawnerService.SessionSnapshot
            afterUnconfigured=
                spawner.getSession(
                    "session-ui-owner"
                );

        if(afterUnconfigured.selectedRowIndex!=null||
           wire.size()!=spawnerTextBefore||
           bridge.homeTeleportRequests!=
                homeRequestsBeforeSpawner||
           bridge.monsterSpawnerResults!=
                resultsBeforeUnconfigured)
            throw new AssertionError(
                "unconfigured exact Monster Spawner row was not bounded/consumed"
            );

        routed.handleWidget(
            MonsterSpawnerPresentation.rowWidget(0),
            w,
            "[ui-test] "
        );

        MonsterSpawnerService.SessionSnapshot selected=
            spawner.getSession(
                "session-ui-owner"
            );

        if(selected.selectedRowIndex==null||
           selected.selectedRowIndex.intValue()!=0||
           selected.selectedDefinitionId==null||
           selected.selectedDefinitionId.intValue()!=1700)
            throw new AssertionError(
                "Monster Spawner row did not route through configured adapter"
            );
        if(wire.size()<=spawnerTextBefore)
            throw new AssertionError(
                "Monster Spawner selected-text packet missing"
            );
        if(bridge.homeTeleportRequests!=
                homeRequestsBeforeSpawner)
            throw new AssertionError(
                "Monster Spawner row fell through to unrelated widget route"
            );

        if(bridge.monsterSpawnerResults!=1||
           bridge.lastMonsterSpawnerResult==null||
           bridge.lastMonsterSpawnerResult.status!=
                LocalMonsterSpawnerUiHandler.Status.ROW_SELECTED||
           bridge.lastMonsterSpawnerResult.session.selectedRowIndex==null||
           bridge.lastMonsterSpawnerResult.session.selectedRowIndex.intValue()!=0||
           bridge.lastMonsterSpawnerResult.session.selectedDefinitionId==null||
           bridge.lastMonsterSpawnerResult.session.selectedDefinitionId.intValue()!=1700||
           bridge.lastMonsterSpawnerResult.session.active)
            throw new AssertionError(
                "ROW_SELECTED result was not forwarded exactly once after commit"
            );

        int retainedReopenBefore=
            wire.size();
        if(!routed.openMonsterSpawnerIfConfigured(
                w
            ))
            throw new AssertionError(
                "retained Monster Spawner UI did not reopen"
            );

        byte[] retainedReopenWire=
            wire.toByteArray();
        String retainedReopenPayload=
            new String(
                retainedReopenWire,
                retainedReopenBefore,
                retainedReopenWire.length-retainedReopenBefore,
                StandardCharsets.ISO_8859_1
            );

        if(!retainedReopenPayload.contains(
                "You have selected: @yel@NPC-1700"
            ))
            throw new AssertionError(
                "retained Monster Spawner selection was not restored on reopen"
            );

        routed.handleWidget(
            MonsterSpawnerPresentation.TOGGLE_WIDGET,
            w,
            "[ui-test] "
        );

        MonsterSpawnerService.SessionSnapshot activated=
            spawner.getSession(
                "session-ui-owner"
            );

        if(!activated.active||
           activated.remainingSpawnBudget!=2)
            throw new AssertionError(
                "Monster Spawner toggle did not route caller-owned budget"
            );

        if(bridge.monsterSpawnerResults!=2||
           bridge.lastMonsterSpawnerResult==null||
           bridge.lastMonsterSpawnerResult.status!=
                LocalMonsterSpawnerUiHandler.Status.ACTIVATED||
           bridge.lastMonsterSpawnerResult.activationBudget!=2||
           !bridge.lastMonsterSpawnerResult.session.active||
           bridge.lastMonsterSpawnerResult.session.remainingSpawnBudget!=2)
            throw new AssertionError(
                "ACTIVATED result was not forwarded exactly once with committed budget"
            );

        routed.handleWidget(
            MonsterSpawnerPresentation.TOGGLE_WIDGET,
            w,
            "[ui-test] "
        );

        MonsterSpawnerService.SessionSnapshot deactivated=
            spawner.getSession(
                "session-ui-owner"
            );

        if(deactivated.active||
           bridge.monsterSpawnerResults!=3||
           bridge.lastMonsterSpawnerResult==null||
           bridge.lastMonsterSpawnerResult.status!=
                LocalMonsterSpawnerUiHandler.Status.DEACTIVATED||
           bridge.lastMonsterSpawnerResult.session.active||
           bridge.lastMonsterSpawnerResult.session.remainingSpawnBudget!=0)
            throw new AssertionError(
                "DEACTIVATED result was not forwarded exactly once after commit"
            );

        if(bridge.homeTeleportRequests!=
                homeRequestsBeforeSpawner)
            throw new AssertionError(
                "Monster Spawner toggle fell through to unrelated widget route"
            );

        MonsterSpawnerService.SessionSnapshot
            beforeEquipmentRoot=
                spawner.getSession(
                    "session-ui-owner"
                );
        int equipmentRootWireBefore=
            wire.size();

        routed.handleWidget(
            NativeEquipmentDeathUi.EQUIPMENT_STATS_BUTTON,
            w,
            "[ui-test] "
        );

        int equipmentRootWireAfter=
            wire.size();
        int equipmentRootResults=
            bridge.monsterSpawnerResults;
        int equipmentRootTransactions=
            bridge.monsterSpawnerWidgetTransactions;

        if(equipmentRootWireAfter<=equipmentRootWireBefore)
            throw new AssertionError(
                "equipment-stats root replacement emitted no packets"
            );

        routed.handleWidget(
            MonsterSpawnerPresentation.TOGGLE_WIDGET,
            w,
            "[ui-test] "
        );

        MonsterSpawnerService.SessionSnapshot
            afterEquipmentClosedWidget=
                spawner.getSession(
                    "session-ui-owner"
                );

        if(afterEquipmentClosedWidget.active!=
                beforeEquipmentRoot.active||
           afterEquipmentClosedWidget.remainingSpawnBudget!=
                beforeEquipmentRoot.remainingSpawnBudget||
           !afterEquipmentClosedWidget.selectedRowIndex.equals(
                beforeEquipmentRoot.selectedRowIndex
            )||
           wire.size()!=equipmentRootWireAfter||
           bridge.monsterSpawnerResults!=
                equipmentRootResults||
           bridge.monsterSpawnerWidgetTransactions!=
                equipmentRootTransactions)
            throw new AssertionError(
                "equipment-stats root replacement left Monster Spawner gate open"
            );

        if(!routed.openMonsterSpawnerIfConfigured(
                w
            ))
            throw new AssertionError(
                "Monster Spawner did not reopen before death-root replacement"
            );

        MonsterSpawnerService.SessionSnapshot
            beforeDeathRoot=
                spawner.getSession(
                    "session-ui-owner"
                );
        int deathRootWireBefore=
            wire.size();

        routed.handleWidget(
            NativeEquipmentDeathUi.DEATH_BUTTON,
            w,
            "[ui-test] "
        );

        int deathRootWireAfter=
            wire.size();
        int deathRootResults=
            bridge.monsterSpawnerResults;
        int deathRootTransactions=
            bridge.monsterSpawnerWidgetTransactions;

        if(deathRootWireAfter<=deathRootWireBefore)
            throw new AssertionError(
                "death-preview root replacement emitted no packets"
            );

        routed.handleWidget(
            MonsterSpawnerPresentation.TOGGLE_WIDGET,
            w,
            "[ui-test] "
        );

        MonsterSpawnerService.SessionSnapshot
            afterDeathClosedWidget=
                spawner.getSession(
                    "session-ui-owner"
                );

        if(afterDeathClosedWidget.active!=
                beforeDeathRoot.active||
           afterDeathClosedWidget.remainingSpawnBudget!=
                beforeDeathRoot.remainingSpawnBudget||
           !afterDeathClosedWidget.selectedRowIndex.equals(
                beforeDeathRoot.selectedRowIndex
            )||
           wire.size()!=deathRootWireAfter||
           bridge.monsterSpawnerResults!=
                deathRootResults||
           bridge.monsterSpawnerWidgetTransactions!=
                deathRootTransactions)
            throw new AssertionError(
                "death-preview root replacement left Monster Spawner gate open"
            );

        if(!routed.openMonsterSpawnerIfConfigured(
                w
            ))
            throw new AssertionError(
                "Monster Spawner did not reopen before Dev Panel root composition"
            );

        routedDevPanel.open(
            DevControlCenter.Page.ITEMS
        );

        int rootReplacementsBeforeNavigation=
            bridge.monsterSpawnerRootReplacements;
        int devPanelWidgetsBeforeNavigation=
            bridge.devPanelWidgets;

        routed.handleWidget(
            2482,
            w,
            "[ui-test] "
        );

        if(bridge.devPanelWidgets!=
                devPanelWidgetsBeforeNavigation+1||
           bridge.monsterSpawnerRootReplacements!=
                rootReplacementsBeforeNavigation)
            throw new AssertionError(
                "ordinary Dev Panel ITEMS navigation entered root replacement"
            );

        MonsterSpawnerService.SessionSnapshot
            beforeDevEquipment=
                spawner.getSession(
                    "session-ui-owner"
                );
        int devEquipmentWireBefore=
            wire.size();
        int devEquipmentResultsBefore=
            bridge.monsterSpawnerResults;
        int devEquipmentTransactionsBefore=
            bridge.monsterSpawnerWidgetTransactions;

        routed.handleWidget(
            2483,
            w,
            "[ui-test] "
        );

        if(bridge.monsterSpawnerRootReplacements!=
                rootReplacementsBeforeNavigation+1||
           bridge.devPanelWidgets!=
                devPanelWidgetsBeforeNavigation+2)
            throw new AssertionError(
                "Dev Panel equipment terminal action missed root replacement"
            );

        routed.handleWidget(
            MonsterSpawnerPresentation.TOGGLE_WIDGET,
            w,
            "[ui-test] "
        );

        MonsterSpawnerService.SessionSnapshot
            afterDevEquipmentClosedWidget=
                spawner.getSession(
                    "session-ui-owner"
                );

        if(afterDevEquipmentClosedWidget.active!=
                beforeDevEquipment.active||
           afterDevEquipmentClosedWidget.remainingSpawnBudget!=
                beforeDevEquipment.remainingSpawnBudget||
           !afterDevEquipmentClosedWidget.selectedRowIndex.equals(
                beforeDevEquipment.selectedRowIndex
            )||
           wire.size()!=devEquipmentWireBefore||
           bridge.monsterSpawnerResults!=
                devEquipmentResultsBefore||
           bridge.monsterSpawnerWidgetTransactions!=
                devEquipmentTransactionsBefore)
            throw new AssertionError(
                "Dev Panel equipment terminal root left Monster Spawner gate open"
            );

        if(!routed.openMonsterSpawnerIfConfigured(
                w
            ))
            throw new AssertionError(
                "Monster Spawner did not reopen before Dev Panel death root"
            );

        int devDeathWireBefore=
            wire.size();
        int devDeathResultsBefore=
            bridge.monsterSpawnerResults;
        int devDeathTransactionsBefore=
            bridge.monsterSpawnerWidgetTransactions;

        routed.handleWidget(
            2484,
            w,
            "[ui-test] "
        );

        if(bridge.monsterSpawnerRootReplacements!=
                rootReplacementsBeforeNavigation+2||
           bridge.devPanelWidgets!=
                devPanelWidgetsBeforeNavigation+3)
            throw new AssertionError(
                "Dev Panel death terminal action missed root replacement"
            );

        routed.handleWidget(
            MonsterSpawnerPresentation.TOGGLE_WIDGET,
            w,
            "[ui-test] "
        );

        MonsterSpawnerService.SessionSnapshot
            afterDevDeathClosedWidget=
                spawner.getSession(
                    "session-ui-owner"
                );

        if(afterDevDeathClosedWidget.active!=
                beforeDevEquipment.active||
           afterDevDeathClosedWidget.remainingSpawnBudget!=
                beforeDevEquipment.remainingSpawnBudget||
           wire.size()!=devDeathWireBefore||
           bridge.monsterSpawnerResults!=
                devDeathResultsBefore||
           bridge.monsterSpawnerWidgetTransactions!=
                devDeathTransactionsBefore)
            throw new AssertionError(
                "Dev Panel death terminal root left Monster Spawner gate open"
            );

        routedDevPanel.close();

        int configuredHomeBefore=wire.size();
        int monsterTransactionsBeforeHome=
            bridge.monsterSpawnerWidgetTransactions;
        routed.handleWidget(
            1195,
            w,
            "[ui-test] "
        );
        if(bridge.homeTeleportRequests!=
                homeRequestsBeforeSpawner+1)
            throw new AssertionError(
                "configured Monster Spawner route captured Home Teleport"
            );
        if(wire.size()!=configuredHomeBefore)
            throw new AssertionError(
                "configured routing unexpectedly emitted Home Teleport packets"
            );
        if(bridge.monsterSpawnerWidgetTransactions!=
                monsterTransactionsBeforeHome)
            throw new AssertionError(
                "unrelated Home Teleport entered Monster Spawner transaction bridge"
            );

        routed.handleInterfaceClose(
            true,
            w,
            "[ui-test] "
        );

        int postCloseWire=
            wire.size();
        int postCloseResults=
            bridge.monsterSpawnerResults;
        int postCloseTransactions=
            bridge.monsterSpawnerWidgetTransactions;
        MonsterSpawnerService.SessionSnapshot
            beforeClosedToggle=
                spawner.getSession(
                    "session-ui-owner"
                );

        routed.handleWidget(
            MonsterSpawnerPresentation.TOGGLE_WIDGET,
            w,
            "[ui-test] "
        );

        MonsterSpawnerService.SessionSnapshot
            afterClosedToggle=
                spawner.getSession(
                    "session-ui-owner"
                );

        if(afterClosedToggle.active||
           beforeClosedToggle.active!=
                afterClosedToggle.active||
           beforeClosedToggle.remainingSpawnBudget!=
                afterClosedToggle.remainingSpawnBudget||
           wire.size()!=postCloseWire||
           bridge.monsterSpawnerResults!=
                postCloseResults||
           bridge.monsterSpawnerWidgetTransactions!=
                postCloseTransactions)
            throw new AssertionError(
                "closed Monster Spawner UI accepted post-close toggle action"
            );

        // Prove an initially unconfigured routing owner becomes live only after explicit open.
        LocalSessionUiActionHandler lateBound=
            new LocalSessionUiActionHandler(
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

        int lateTextBefore=wire.size();

        lateBound.installMonsterSpawnerUiHandler(
            monsterSpawnerUi
        );

        int lateClosedResults=
            bridge.monsterSpawnerResults;
        int lateClosedTransactions=
            bridge.monsterSpawnerWidgetTransactions;

        lateBound.handleWidget(
            MonsterSpawnerPresentation.rowWidget(0),
            w,
            "[ui-test] "
        );

        if(wire.size()!=lateTextBefore||
           bridge.monsterSpawnerResults!=
                lateClosedResults||
           bridge.monsterSpawnerWidgetTransactions!=
                lateClosedTransactions)
            throw new AssertionError(
                "late-installed Monster Spawner accepted action before explicit open"
            );

        if(!lateBound.openMonsterSpawnerIfConfigured(
                w
            ))
            throw new AssertionError(
                "late-installed Monster Spawner did not explicitly open"
            );

        int lateOpenWire=
            wire.size();

        lateBound.handleWidget(
            MonsterSpawnerPresentation.rowWidget(0),
            w,
            "[ui-test] "
        );

        if(wire.size()<=lateOpenWire)
            throw new AssertionError(
                "opened late-installed Monster Spawner route did not publish selected text"
            );

        lateBound.handleWidget(
            MonsterSpawnerPresentation.TOGGLE_WIDGET,
            w,
            "[ui-test] "
        );

        MonsterSpawnerService.SessionSnapshot lateActivated=
            spawner.getSession(
                "session-ui-owner"
            );

        if(!lateActivated.active||
           lateActivated.remainingSpawnBudget!=2)
            throw new AssertionError(
                "late-installed Monster Spawner toggle did not reach adapter"
            );

        // Same instance is idempotent; a distinct replacement is rejected.
        lateBound.installMonsterSpawnerUiHandler(
            monsterSpawnerUi
        );

        LocalMonsterSpawnerUiHandler replacementUi=
            new LocalMonsterSpawnerUiHandler(
                spawner,
                "session-ui-owner",
                new LocalMonsterSpawnerUiHandler
                    .ActivationBudgetResolver(){
                    @Override public int spawnBudget(
                        LocalMonsterSpawnerUiHandler.Context context
                    ){
                        return 2;
                    }

                    @Override public String authority(){
                        return "CUSTOM_LOCALLAB_SESSION_UI_POLICY";
                    }
                },
                new LocalMonsterSpawnerUiHandler
                    .SelectedNpcLabelResolver(){
                    @Override public String label(
                        MonsterSpawnerService.CatalogEntry entry
                    ){
                        return "NPC-"+entry.definitionId;
                    }

                    @Override public String authority(){
                        return "CUSTOM_LOCALLAB_SESSION_UI_CATALOG";
                    }
                }
            );

        boolean replacementRejected=false;
        try{
            lateBound.installMonsterSpawnerUiHandler(
                replacementUi
            );
        }catch(IllegalStateException expected){
            replacementRejected=true;
        }

        if(!replacementRejected)
            throw new AssertionError(
                "Monster Spawner UI replacement was not rejected"
            );

        World lateWorld=
            World.isolatedForTest(
                600L
            );
        WorldPlayer latePlayer=
            new WorldPlayer();
        long lateGeneration=
            lateWorld.registerPlayer(
                latePlayer,
                "session-ui-owner"
            );
        boolean[] factoryContext={false};
        boolean[] callbackContext={false};
        int[] callbackCalls={0};
        boolean[] closeContext={false};
        int[] closeCalls={0};
        LocalMonsterSpawnerUiHandler lateWorldUi=
            configuredSpawnerUi(
                lateWorld.npcs(),
                "session-ui-owner"
            );

        try{
            LocalSession.MonsterSpawnerUiFactory callbackFactory=
                new LocalSession.MonsterSpawnerUiFactory(){
                    @Override public LocalMonsterSpawnerUiHandler create(
                        World factoryWorld,
                        WorldPlayer factoryPlayer,
                        String canonicalUsername
                    ){
                        factoryContext[0]=
                            factoryWorld==lateWorld&&
                            factoryPlayer==latePlayer&&
                            "session-ui-owner".equals(
                                canonicalUsername
                            );
                        return lateWorldUi;
                    }

                    @Override public void onCommittedResult(
                        World callbackWorld,
                        WorldPlayer callbackPlayer,
                        String canonicalUsername,
                        LocalMonsterSpawnerUiHandler.Result result,
                        ServerPacketWriter callbackWriter,
                        String callbackTag
                    ){
                        callbackCalls[0]++;
                        callbackContext[0]=
                            callbackWorld==lateWorld&&
                            callbackPlayer==latePlayer&&
                            "session-ui-owner".equals(
                                canonicalUsername
                            )&&
                            result==bridge.lastMonsterSpawnerResult&&
                            callbackWriter==w&&
                            "[callback-test] ".equals(
                                callbackTag
                            );
                    }

                    @Override public void onSessionClosed(
                        World closeWorld,
                        WorldPlayer closePlayer,
                        long expectedGeneration,
                        String canonicalUsername
                    ){
                        closeCalls[0]++;
                        closeContext[0]=
                            closeWorld==lateWorld&&
                            closePlayer==latePlayer&&
                            expectedGeneration==
                                latePlayer.generation()&&
                            "session-ui-owner".equals(
                                canonicalUsername
                            );
                    }
                };

            LocalMonsterSpawnerUiHandler resolved=
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    callbackFactory,
                    lateWorld,
                    latePlayer,
                    lateGeneration,
                    "session-ui-owner"
                );

            if(resolved!=lateWorldUi||
               !factoryContext[0]||
               !resolved.isBoundToOwner(
                    "session-ui-owner"
                )||
               !resolved.isBoundTo(
                    lateWorld
                ))
                throw new AssertionError(
                    "late Monster Spawner UI factory context"
                );

            World foreignWorld=
                World.isolatedForTest(
                    600L
                );
            try{
                LocalMonsterSpawnerUiHandler foreignWorldUi=
                    configuredSpawnerUi(
                        foreignWorld.npcs(),
                        "session-ui-owner"
                    );

                boolean foreignWorldRejected=false;
                try{
                    LocalSession.resolveMonsterSpawnerUiAfterLogin(
                        (factoryWorld,factoryPlayer,canonicalUsername)->
                            foreignWorldUi,
                        lateWorld,
                        latePlayer,
                        lateGeneration,
                        "session-ui-owner"
                    );
                }catch(IllegalArgumentException expected){
                    foreignWorldRejected=true;
                }

                if(!foreignWorldRejected)
                    throw new AssertionError(
                        "foreign-World Monster Spawner UI was accepted"
                    );
            }finally{
                foreignWorld.close();
            }

            LocalMonsterSpawnerUiHandler detachedUi=
                configuredSpawnerUi(
                    new WorldNpcRegistry(),
                    "session-ui-owner"
                );

            boolean detachedRejected=false;
            try{
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    (factoryWorld,factoryPlayer,canonicalUsername)->
                        detachedUi,
                    lateWorld,
                    latePlayer,
                    "session-ui-owner"
                );
            }catch(IllegalArgumentException expected){
                detachedRejected=true;
            }

            if(!detachedRejected)
                throw new AssertionError(
                    "detached-registry Monster Spawner UI was accepted"
                );

            if(LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    (factoryWorld,factoryPlayer,canonicalUsername)->null,
                    lateWorld,
                    latePlayer,
                    lateGeneration,
                    "session-ui-owner"
                )!=null)
                throw new AssertionError(
                    "null late Monster Spawner UI factory result changed behavior"
                );

            boolean ownerMismatchRejected=false;
            try{
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    (factoryWorld,factoryPlayer,canonicalUsername)->
                        lateWorldUi,
                    lateWorld,
                    latePlayer,
                    lateGeneration,
                    "different-owner"
                );
            }catch(IllegalArgumentException expected){
                ownerMismatchRejected=true;
            }

            if(!ownerMismatchRejected)
                throw new AssertionError(
                    "late Monster Spawner UI owner mismatch was accepted"
                );

            LocalSession.forwardMonsterSpawnerUiResult(
                callbackFactory,
                lateWorld,
                latePlayer,
                lateGeneration,
                "session-ui-owner",
                bridge.lastMonsterSpawnerResult,
                w,
                "[callback-test] "
            );

            if(!callbackContext[0]||
               callbackCalls[0]!=1)
                throw new AssertionError(
                    "Monster Spawner callback exact LocalSession context"
                );

            LocalSession.forwardMonsterSpawnerUiResult(
                null,
                lateWorld,
                latePlayer,
                lateGeneration,
                "session-ui-owner",
                bridge.lastMonsterSpawnerResult,
                w,
                "[callback-test] "
            );

            if(callbackCalls[0]!=1)
                throw new AssertionError(
                    "null Monster Spawner callback factory invoked policy"
                );

            boolean callbackOwnerMismatchRejected=false;
            try{
                LocalSession.forwardMonsterSpawnerUiResult(
                    callbackFactory,
                    lateWorld,
                    latePlayer,
                    lateGeneration,
                    "different-owner",
                    bridge.lastMonsterSpawnerResult,
                    w,
                    "[callback-test] "
                );
            }catch(IllegalArgumentException expected){
                callbackOwnerMismatchRejected=true;
            }

            if(!callbackOwnerMismatchRejected||
               callbackCalls[0]!=1)
                throw new AssertionError(
                    "Monster Spawner committed-result owner mismatch was accepted"
                );

            if(!lateWorld.unregisterPlayer(
                    latePlayer,
                    lateGeneration
                ))
                throw new AssertionError(
                    "late callback fixture did not unregister original generation"
                );

            long replacementGeneration=
                lateWorld.registerPlayer(
                    latePlayer,
                    "session-ui-owner"
                );

            boolean staleGenerationRejected=false;
            try{
                LocalSession.forwardMonsterSpawnerUiResult(
                    callbackFactory,
                    lateWorld,
                    latePlayer,
                    lateGeneration,
                    "session-ui-owner",
                    bridge.lastMonsterSpawnerResult,
                    w,
                    "[callback-test] "
                );
            }catch(IllegalStateException expected){
                staleGenerationRejected=true;
            }

            if(!staleGenerationRejected||
               callbackCalls[0]!=1)
                throw new AssertionError(
                    "stale LocalSession generation invoked Monster Spawner callback"
                );

            LocalSession.forwardMonsterSpawnerUiResult(
                callbackFactory,
                lateWorld,
                latePlayer,
                replacementGeneration,
                "session-ui-owner",
                bridge.lastMonsterSpawnerResult,
                w,
                "[callback-test] "
            );

            if(callbackCalls[0]!=2)
                throw new AssertionError(
                    "replacement generation did not invoke explicit current callback"
                );

            LocalSession.notifyMonsterSpawnerSessionClosed(
                callbackFactory,
                lateWorld,
                latePlayer,
                replacementGeneration,
                "session-ui-owner"
            );

            if(closeCalls[0]!=1||
               !closeContext[0])
                throw new AssertionError(
                    "Monster Spawner session-close callback exact context"
                );

            LocalSession.notifyMonsterSpawnerSessionClosed(
                null,
                lateWorld,
                latePlayer,
                replacementGeneration,
                "session-ui-owner"
            );

            if(closeCalls[0]!=1)
                throw new AssertionError(
                    "null Monster Spawner session-close factory invoked policy"
                );

            boolean staleCloseRejected=false;
            try{
                LocalSession.notifyMonsterSpawnerSessionClosed(
                    callbackFactory,
                    lateWorld,
                    latePlayer,
                    lateGeneration,
                    "session-ui-owner"
                );
            }catch(IllegalStateException expected){
                staleCloseRejected=true;
            }

            if(!staleCloseRejected||
               closeCalls[0]!=1)
                throw new AssertionError(
                    "stale generation invoked Monster Spawner session-close callback"
                );

            lateWorld.close();

            boolean terminalWorldRejected=false;
            try{
                LocalSession.forwardMonsterSpawnerUiResult(
                    callbackFactory,
                    lateWorld,
                    latePlayer,
                    replacementGeneration,
                    "session-ui-owner",
                    bridge.lastMonsterSpawnerResult,
                    w,
                    "[callback-test] "
                );
            }catch(IllegalStateException expected){
                terminalWorldRejected=true;
            }

            if(!terminalWorldRejected||
               callbackCalls[0]!=2)
                throw new AssertionError(
                    "closed World admitted Monster Spawner callback policy"
                );

            if(LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    null,
                    lateWorld,
                    latePlayer,
                    "session-ui-owner"
                )!=null)
                throw new AssertionError(
                    "absent late Monster Spawner UI factory changed behavior"
                );

            boolean terminalFactoryRejected=false;
            try{
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    (factoryWorld,factoryPlayer,canonicalUsername)->null,
                    lateWorld,
                    latePlayer,
                    replacementGeneration,
                    "session-ui-owner"
                );
            }catch(IllegalStateException expected){
                terminalFactoryRejected=true;
            }

            if(!terminalFactoryRejected)
                throw new AssertionError(
                    "closed World admitted Monster Spawner post-login factory"
                );
        }finally{
            lateWorld.close();
        }

        bridge.saveReason=null;
        bridge.clearedKeys=false;
        h.handleInterfaceClose(true,w,"[ui-test] ");
        if(!"INTERFACE_CLOSE".equals(bridge.saveReason))
            throw new AssertionError("interface-close save reason changed");
        if(!bridge.clearedKeys)
            throw new AssertionError("interface-close key clear missing");

        if(!LocalSessionUiActionHandler.isDevPanelWidget(54195))
            throw new AssertionError("panel close widget lost");
        if(!LocalSessionUiActionHandler.isDevPanelWidget(2482))
            throw new AssertionError("panel choice widget lost");
        if(LocalSessionUiActionHandler.isDevPanelWidget(152))
            throw new AssertionError("run toggle captured as panel widget");

        System.out.println(
            "LOCAL_SESSION_UI_ACTION_HANDLER_PASS "+
            "logout=true runToggle=true homeTeleport=true interfaceClose=true "+
            "panelBoundary=true monsterSpawnerRoute=true "+
            "monsterSpawnerAbsentPreserved=true "+
            "monsterSpawnerResultForwarding=true "+
            "monsterSpawnerOpenConfigured=true "+
            "monsterSpawnerOpenAbsentNoop=true "+
            "monsterSpawnerRetainedSelectionReopen=true "+
            "monsterSpawnerUnconfiguredRowBounded=true "+
            "monsterSpawnerUnrelatedBypass=true "+
            "monsterSpawnerPreOpenRejected=true "+
            "monsterSpawnerPostCloseRejected=true "+
            "monsterSpawnerLateInstallClosed=true "+
            "monsterSpawnerEquipmentRootRevokes=true "+
            "monsterSpawnerDeathRootRevokes=true "+
            "monsterSpawnerDevPanelEquipmentRootRevokes=true "+
            "monsterSpawnerDevPanelDeathRootRevokes=true "+
            "monsterSpawnerDevPanelNavigationPreserved=true"
        );

        System.out.println(
            "LOCAL_SESSION_MONSTER_SPAWNER_LATE_BIND_PASS "+
            "factoryAfterIdentity=true "+
            "exactWorldPlayer=true "+
            "ownerFence=true "+
            "nullPreserved=true "+
            "oneTimeInstall=true "+
            "resultCallback=true "+
            "exactCallbackContext=true "+
            "callbackOwnerFence=true "+
            "callbackGenerationFence=true "+
            "callbackWorldCloseFence=true "+
            "sessionCloseHook=true "+
            "sessionCloseGenerationFence=true "+
            "monsterSpawnerWorldFence=true "+
            "loginFactoryWorldCloseFence=true "+
            "factoryStillFunctional=true "+
            "policyNeutral=true"
        );
    }

    private static LocalMonsterSpawnerUiHandler
        configuredSpawnerUi(
            WorldNpcRegistry registry,
            String owner
        ){
        MonsterSpawnerService service=
            new MonsterSpawnerService(
                registry
            );

        service.replaceCatalog(
            Collections.singletonList(
                new MonsterSpawnerService.CatalogEntry(
                    0,
                    "world-fence-row",
                    1700
                )
            ),
            "CUSTOM_LOCALLAB_SESSION_UI_CATALOG"
        );
        service.openSession(
            owner,
            "CUSTOM_LOCALLAB_SESSION_UI_POLICY"
        );

        return new LocalMonsterSpawnerUiHandler(
            service,
            owner,
            new LocalMonsterSpawnerUiHandler
                .ActivationBudgetResolver(){
                @Override public int spawnBudget(
                    LocalMonsterSpawnerUiHandler.Context context
                ){
                    return 2;
                }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_SESSION_UI_POLICY";
                }
            },
            new LocalMonsterSpawnerUiHandler
                .SelectedNpcLabelResolver(){
                @Override public String label(
                    MonsterSpawnerService.CatalogEntry entry
                ){
                    return "NPC-"+entry.definitionId;
                }

                @Override public String authority(){
                    return "CUSTOM_LOCALLAB_SESSION_UI_CATALOG";
                }
            }
        );
    }
}
