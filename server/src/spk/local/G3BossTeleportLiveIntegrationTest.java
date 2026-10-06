package spk.local;

import java.io.ByteArrayOutputStream;

public final class G3BossTeleportLiveIntegrationTest {
    private static final int[] SEED={91,92,93,94};

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        int navigationRequests;
        TeleportNavigationService.EntryKind lastNavigationKind;
        String saveReason;

        @Override public void saveAccount(
            String tag,
            String reason
        ){
            saveReason=reason;
        }

        @Override public void clearDialogNumberKeys(){}

        @Override public void handleDevPanelWidget(
            int widget,
            ServerPacketWriter packets,
            String tag
        ){}

        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        ){}

        @Override public void handleTeleportNavigation(
            TeleportNavigationService.EntryKind kind,
            ServerPacketWriter packets,
            String tag
        ){
            navigationRequests++;
            lastNavigationKind=kind;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "g3-boss-live"
            );

        try{
            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(dev);
            CombatEngine combat=
                new CombatEngine(dev);
            LocalPlayerInteractionHandler interactions=
                new LocalPlayerInteractionHandler(
                    world,
                    player,
                    player.movement(),
                    player.equipment()
                );

            LocalRegionDevCommandHandler relocation=
                new LocalRegionDevCommandHandler(
                    world,
                    player,
                    player.movement(),
                    interactions,
                    combat,
                    npcs,
                    player.petState(),
                    new HomeWorldRuntimePlan(),
                    ()->{}
                );

            LocalTeleportNavigationRuntime navigation=
                new LocalTeleportNavigationRuntime(
                    relocation
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        SEED.clone()
                    )
                );

            final SceneUpdatePublisher[] scene={
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                )
            };

            LocalBossTeleportUiHandler bossUi=
                new LocalBossTeleportUiHandler(
                    ()->"g3-boss-live",
                    (packets,tag)->{
                        LocalTeleportNavigationRuntime.Result result=
                            navigation.request(
                                "g3-boss-live",
                                TeleportNavigationService
                                    .EntryKind.BOSS,
                                scene[0],
                                packets
                            );

                        if(result.relocation!=null&&
                           result.relocation.scenePublisher!=null)
                            scene[0]=
                                result.relocation.scenePublisher;

                        return result.succeeded();
                    }
                );

            BankState bank=player.bank();
            EquipmentState equipment=player.equipment();
            MovementState movement=player.movement();
            PlayerState playerState=player.playerState();
            PetState petState=player.petState();
            MiniPetService miniPets=player.miniPets();

            LocalPetInventoryDialogHandler petDialogs=
                new LocalPetInventoryDialogHandler(
                    bank,
                    miniPets,
                    petState,
                    npcs,
                    movement,
                    new PetAccessoryState()
                );

            LocalGameplayWidgetHandler gameplay=
                new LocalGameplayWidgetHandler(
                    player.prayers(),
                    playerState,
                    equipment,
                    player.combatStyles(),
                    player.magic(),
                    bank
                );

            LocalCompCapeCustomizeHandler compCape=
                new LocalCompCapeCustomizeHandler(
                    bank,
                    playerState
                );

            Bridge bridge=new Bridge();

            LocalSessionUiActionHandler ui=
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
                    null,
                    bossUi,
                    bridge
                );

            LocalTeleportDestinationCatalog.Destination
                destination=
                    LocalTeleportDestinationCatalog.get(
                        TeleportNavigationService
                            .EntryKind.BOSS
                    );

            require(
                destination!=null&&
                destination.regionId==
                    LocalBossTeleportUiHandler.REGION_ID&&
                destination.regionId==16168&&
                "Vetion's Rest".equals(
                    destination.expectedName
                )&&
                "vetion".equals(
                    destination.expectedGroup
                ),
                "LocalLab BOSS destination authority drift"
            );

            Tile expectedLanding=
                WorldCollisionAuthority.safeTile(
                    destination.regionId,
                    destination.plane
                );

            int initialX=movement.x();
            int initialY=movement.y();

            require(
                TeleportNavigationWidgetAdapter
                    .WIDGET_ACTION_OPCODE==185&&
                TeleportNavigationWidgetAdapter
                    .resolve(1170)==
                        TeleportNavigationService
                            .EntryKind.BOSS,
                "top-level BOSS widget authority drift"
            );

            int openWire=wire.size();
            ui.handleWidget(
                1170,
                writer,
                "[g3-boss] "
            );

            require(
                bossUi.isOpen()&&
                wire.size()>openWire&&
                bridge.navigationRequests==0&&
                movement.x()==initialX&&
                movement.y()==initialY&&
                navigation.snapshot(
                    "g3-boss-live"
                ).successful(
                    TeleportNavigationService
                        .EntryKind.BOSS
                )==0L,
                "top-level Boss Teleports did not open exact root without relocating"
            );

            BossTeleportService.Snapshot catalog=
                bossUi.serviceSnapshot();

            require(
                catalog.entries.size()==1&&
                LocalBossTeleportUiHandler.BOSS_KEY
                    .equals(
                        catalog.entries.get(0).bossKey
                    )&&
                BossTeleportPresentation.ROOT==18616&&
                BossTeleportPresentation.MAX_ROWS==13,
                "live Boss Teleport catalog/root mismatch"
            );

            for(int row=1;
                row<BossTeleportPresentation.MAX_ROWS;
                row++){
                int before=wire.size();

                ui.handleWidget(
                    BossTeleportPresentation
                        .rowWidget(row),
                    writer,
                    "[g3-boss] "
                );

                BossTeleportService.PlayerSnapshot snapshot=
                    bossUi.snapshot();

                require(
                    (snapshot==null||
                     !snapshot.hasSelection())&&
                    wire.size()==before,
                    "unconfigured Boss row gained authority row="+row
                );
            }

            int selectionWire=wire.size();

            ui.handleWidget(
                BossTeleportPresentation
                    .rowWidget(
                        LocalBossTeleportUiHandler
                            .CONFIGURED_ROW
                    ),
                writer,
                "[g3-boss] "
            );

            BossTeleportService.PlayerSnapshot selected=
                bossUi.snapshot();

            require(
                selected!=null&&
                selected.hasSelection()&&
                LocalBossTeleportUiHandler.BOSS_KEY
                    .equals(
                        selected.selectedBossKey
                    )&&
                wire.size()>selectionWire,
                "configured LocalLab Boss row did not publish/select"
            );

            long bossCountBeforeDrop=
                navigation.snapshot(
                    "g3-boss-live"
                ).successful(
                    TeleportNavigationService
                        .EntryKind.BOSS
                );

            int dropWire=wire.size();

            ui.handleWidget(
                BossTeleportPresentation
                    .VIEW_FULL_DROP_TABLE_WIDGET,
                writer,
                "[g3-boss] "
            );

            require(
                navigation.snapshot(
                    "g3-boss-live"
                ).successful(
                    TeleportNavigationService
                        .EntryKind.BOSS
                )==bossCountBeforeDrop&&
                wire.size()==dropWire&&
                bossUi.snapshot()!=null&&
                bossUi.snapshot().hasSelection(),
                "unowned full drop table escaped fail-closed boundary"
            );

            ui.handleWidget(
                BossTeleportPresentation
                    .TELEPORT_WIDGET,
                writer,
                "[g3-boss] "
            );

            require(
                !bossUi.isOpen()&&
                movement.transientRegion()&&
                movement.x()==expectedLanding.x&&
                movement.y()==expectedLanding.y&&
                movement.plane()==expectedLanding.plane&&
                navigation.snapshot(
                    "g3-boss-live"
                ).successful(
                    TeleportNavigationService
                        .EntryKind.BOSS
                )==1L&&
                bossUi.snapshot()!=null&&
                bossUi.snapshot()
                    .successfulTeleports==1L,
                "exact Boss Teleport action did not commit live region relocation exactly once"
            );

            ui.handleWidget(
                BossTeleportPresentation
                    .TELEPORT_WIDGET,
                writer,
                "[g3-boss] "
            );

            require(
                navigation.snapshot(
                    "g3-boss-live"
                ).successful(
                    TeleportNavigationService
                        .EntryKind.BOSS
                )==1L&&
                bossUi.snapshot()
                    .successfulTeleports==1L,
                "stale closed-root Boss teleport duplicated relocation"
            );

            ui.handleWidget(
                13053,
                writer,
                "[g3-boss] "
            );

            require(
                bossUi.isOpen(),
                "second exact top-level BOSS alias did not reopen root"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g3-boss] "
            );

            require(
                !bossUi.isOpen()&&
                "INTERFACE_CLOSE".equals(
                    bridge.saveReason
                ),
                "interface close did not retire Boss Teleport ownership"
            );

            ui.handleWidget(
                30083,
                writer,
                "[g3-boss] "
            );

            require(
                bossUi.isOpen(),
                "third exact top-level BOSS alias did not reopen root"
            );

            String bankRoot=
                ui.replaceMonsterSpawnerWithBankRoot(
                    ()->{
                        bank.open(writer);
                        return "BANK_AFTER_BOSS";
                    }
                );

            require(
                "BANK_AFTER_BOSS".equals(bankRoot)&&
                bank.isOpen()&&
                !bossUi.isOpen(),
                "competing Bank root did not retire Boss Teleport ownership"
            );

            bank.close(writer);

            ui.handleWidget(
                1164,
                writer,
                "[g3-boss] "
            );

            require(
                bridge.navigationRequests==1&&
                bridge.lastNavigationKind==
                    TeleportNavigationService
                        .EntryKind.MONEY,
                "non-BOSS teleport navigation changed"
            );

            System.out.println(
                "G3_BOSS_TELEPORT_LIVE_PASS"+
                " topLevelBossOpensRoot=true"+
                " root18616=true"+
                " rows13=true"+
                " row0CustomLocalLab=true"+
                " otherRowsFailClosed=true"+
                " selectionPublished=true"+
                " teleport60448=true"+
                " liveBossNavigation=true"+
                " region16168=true"+
                " collisionSafeLanding=true"+
                " relocationSuccessCount=true"+
                " staleTeleportRejected=true"+
                " viewFullDropTableFailClosed=true"+
                " interfaceCloseRetires=true"+
                " competingRootRetires=true"+
                " otherTeleportKindsUnchanged=true"+
                " originalRowMappingClaim=false"+
                " originalSpawnpkDestinationClaim=false"+
                " dropAuthority=false"+
                " authority="+
                    LocalBossTeleportUiHandler.POLICY_AUTHORITY
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );
            world.close();
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G3BossTeleportLiveIntegrationTest(){}
}
