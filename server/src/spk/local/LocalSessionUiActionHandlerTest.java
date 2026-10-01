package spk.local;

import java.io.ByteArrayOutputStream;
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

        LocalSessionUiActionHandler routed=
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
                monsterSpawnerUi,
                bridge
            );

        int spawnerTextBefore=wire.size();
        int homeRequestsBeforeSpawner=
            bridge.homeTeleportRequests;

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

        int configuredHomeBefore=wire.size();
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

        // Prove an initially unconfigured routing owner becomes live after one late install.
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

        lateBound.handleWidget(
            MonsterSpawnerPresentation.rowWidget(0),
            w,
            "[ui-test] "
        );

        if(wire.size()<=lateTextBefore)
            throw new AssertionError(
                "late-installed Monster Spawner route did not publish selected text"
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
        boolean[] factoryContext={false};
        boolean[] callbackContext={false};

        lateWorld.registerPlayer(
            latePlayer,
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
                        return monsterSpawnerUi;
                    }

                    @Override public void onCommittedResult(
                        World callbackWorld,
                        WorldPlayer callbackPlayer,
                        String canonicalUsername,
                        LocalMonsterSpawnerUiHandler.Result result,
                        ServerPacketWriter callbackWriter,
                        String callbackTag
                    ){
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
                };

            LocalMonsterSpawnerUiHandler resolved=
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    callbackFactory,
                    lateWorld,
                    latePlayer,
                    "session-ui-owner"
                );

            if(resolved!=monsterSpawnerUi||
               !factoryContext[0]||
               !resolved.isBoundToOwner(
                    "session-ui-owner"
                ))
                throw new AssertionError(
                    "late Monster Spawner UI factory context"
                );

            LocalSession.forwardMonsterSpawnerUiResult(
                callbackFactory,
                lateWorld,
                latePlayer,
                "session-ui-owner",
                bridge.lastMonsterSpawnerResult,
                w,
                "[callback-test] "
            );

            if(!callbackContext[0])
                throw new AssertionError(
                    "Monster Spawner callback exact LocalSession context"
                );

            boolean resultOwnerMismatchRejected=false;
            try{
                LocalSession.forwardMonsterSpawnerUiResult(
                    callbackFactory,
                    lateWorld,
                    latePlayer,
                    "different-owner",
                    bridge.lastMonsterSpawnerResult,
                    w,
                    "[callback-test] "
                );
            }catch(IllegalArgumentException expected){
                resultOwnerMismatchRejected=true;
            }

            if(!resultOwnerMismatchRejected)
                throw new AssertionError(
                    "Monster Spawner callback accepted result/session owner mismatch"
                );

            LocalSession.forwardMonsterSpawnerUiResult(
                null,
                lateWorld,
                latePlayer,
                "session-ui-owner",
                bridge.lastMonsterSpawnerResult,
                w,
                "[callback-test] "
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

            if(LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    (factoryWorld,factoryPlayer,canonicalUsername)->null,
                    lateWorld,
                    latePlayer,
                    "session-ui-owner"
                )!=null)
                throw new AssertionError(
                    "null late Monster Spawner UI factory result changed behavior"
                );

            boolean ownerMismatchRejected=false;
            try{
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    (factoryWorld,factoryPlayer,canonicalUsername)->
                        monsterSpawnerUi,
                    lateWorld,
                    latePlayer,
                    "different-owner"
                );
            }catch(IllegalArgumentException expected){
                ownerMismatchRejected=true;
            }

            if(!ownerMismatchRejected)
                throw new AssertionError(
                    "late Monster Spawner UI owner mismatch was accepted"
                );

            if(!lateWorld.unregisterPlayer(
                    latePlayer
                ))
                throw new AssertionError(
                    "late Monster Spawner callback test player unregister"
                );

            boolean stalePlayerRejected=false;
            try{
                LocalSession.forwardMonsterSpawnerUiResult(
                    callbackFactory,
                    lateWorld,
                    latePlayer,
                    "session-ui-owner",
                    bridge.lastMonsterSpawnerResult,
                    w,
                    "[callback-test] "
                );
            }catch(IllegalStateException expected){
                stalePlayerRejected=true;
            }

            if(!stalePlayerRejected)
                throw new AssertionError(
                    "Monster Spawner callback accepted stale player ownership"
                );
        }finally{
            if(latePlayer.registered())
                lateWorld.unregisterPlayer(
                    latePlayer
                );
            lateWorld.close();
        }

        World closedCallbackWorld=
            World.isolatedForTest(
                600L
            );
        WorldPlayer closedCallbackPlayer=
            new WorldPlayer();

        closedCallbackWorld.registerPlayer(
            closedCallbackPlayer,
            "session-ui-owner"
        );
        closedCallbackWorld.close();

        boolean closedWorldRejected=false;
        try{
            LocalSession.forwardMonsterSpawnerUiResult(
                new LocalSession.MonsterSpawnerUiFactory(){
                    @Override public LocalMonsterSpawnerUiHandler create(
                        World factoryWorld,
                        WorldPlayer factoryPlayer,
                        String canonicalUsername
                    ){
                        return monsterSpawnerUi;
                    }

                    @Override public void onCommittedResult(
                        World callbackWorld,
                        WorldPlayer callbackPlayer,
                        String canonicalUsername,
                        LocalMonsterSpawnerUiHandler.Result result,
                        ServerPacketWriter callbackWriter,
                        String callbackTag
                    ){
                        throw new AssertionError(
                            "closed World callback executed"
                        );
                    }
                },
                closedCallbackWorld,
                closedCallbackPlayer,
                "session-ui-owner",
                bridge.lastMonsterSpawnerResult,
                w,
                "[callback-test] "
            );
        }catch(IllegalStateException expected){
            closedWorldRejected=true;
        }

        if(!closedWorldRejected)
            throw new AssertionError(
                "Monster Spawner callback accepted closed World"
            );

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
            "monsterSpawnerResultForwarding=true"
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
            "resultOwnerFence=true "+
            "currentPlayerFence=true "+
            "closedWorldFence=true "+
            "policyNeutral=true"
        );
    }
}
