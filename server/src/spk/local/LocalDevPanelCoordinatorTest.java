package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalDevPanelCoordinatorTest {
    private static final class Bridge
        implements LocalDevPanelCoordinator.SessionBridge
    {
        SceneUpdatePublisher publisher;
        String saveReason;
        int rootReplacingAmounts;

        @Override public String username(){
            return "opensrc";
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }

        @Override public void replaceScenePublisher(
            SceneUpdatePublisher replacement
        ){
            publisher=replacement;
        }

        @Override public void saveAccount(
            String tag,
            String reason
        ){
            saveReason=reason;
        }

        @Override public LocalDevPanelAmountHandler.Outcome
            handleRootReplacingAmount(
                LocalDevPanelCoordinator
                    .RootReplacingAmountAction action
            )throws java.io.IOException{
            rootReplacingAmounts++;
            return action.handle();
        }
    }

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        Path tempDir=Files.createTempDirectory(
            "locallab-dev-panel-test-"
        );

        try{
            WorldPlayer player=new WorldPlayer();
            world.registerPlayer(player,"opensrc");

            MovementState movement=player.movement();
            BankState bank=player.bank();
            EquipmentState equipment=player.equipment();
            PetState petState=player.petState();
            PlayerState playerState=player.playerState();
            PrayerState prayers=player.prayers();
            MagicState magic=player.magic();
            CombatStyleState combatStyles=
                player.combatStyles();

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            PlayerPresentationService presentation=
                new PlayerPresentationService(dev);
            NpcRegistry npcs=new NpcRegistry(dev);
            HomeWorldRuntimePlan homeWorld=
                new HomeWorldRuntimePlan();
            CombatEngine combat=new CombatEngine(dev);
            NativeItemLibraryService itemLibrary=
                new NativeItemLibraryService();
            VoidglassPetState voidglass=
                new VoidglassPetState();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(new int[]{1,2,3,4})
                );

            Bridge bridge=new Bridge();
            bridge.publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalPlayerInteractionHandler playerInteractions=
                new LocalPlayerInteractionHandler(
                    world,
                    player,
                    movement,
                    equipment
                );

            LocalRegionDevCommandHandler regionDev=
                new LocalRegionDevCommandHandler(
                    world,
                    player,
                    movement,
                    playerInteractions,
                    combat,
                    npcs,
                    petState,
                    homeWorld,
                    ()->{}
                );

            LocalDevSessionCommandHandler devSession=
                new LocalDevSessionCommandHandler(
                    world,
                    dev,
                    npcs,
                    presentation,
                    equipment,
                    playerState,
                    bank,
                    petState,
                    movement
                );

            LocalVoidglassCommandHandler voidglassCommands=
                new LocalVoidglassCommandHandler(
                    bank,
                    petState,
                    npcs,
                    movement,
                    dev,
                    voidglass
                );

            LocalPetInventoryDialogHandler petDialogs=
                new LocalPetInventoryDialogHandler(
                    bank,
                    player.miniPets(),
                    petState,
                    npcs,
                    movement,
                    new PetAccessoryState()
                );

            DevControlCenter panel=
                new DevControlCenter();

            LocalDevPanelRenderer renderer=
                new LocalDevPanelRenderer(
                    panel,
                    equipment,
                    combatStyles,
                    dev,
                    combat,
                    npcs,
                    petState,
                    magic,
                    prayers,
                    movement,
                    presentation
                );

            LocalDialogNumberKeyState keys=
                new LocalDialogNumberKeyState(
                    tempDir.resolve(
                        "dialog-keys.properties"
                    )
                );

            LocalDevPanelAmountHandler amounts=
                new LocalDevPanelAmountHandler(
                    panel,
                    dev,
                    equipment,
                    combat,
                    npcs,
                    movement,
                    regionDev,
                    itemLibrary,
                    presentation,
                    playerState,
                    prayers,
                    renderer,
                    keys::clear
                );

            LocalDevPanelCoordinator[] holder=
                new LocalDevPanelCoordinator[1];

            LocalDevPanelWidgetHandler widgets=
                new LocalDevPanelWidgetHandler(
                    panel,
                    equipment,
                    combatStyles,
                    dev,
                    combat,
                    npcs,
                    movement,
                    voidglass,
                    voidglassCommands,
                    prayers,
                    magic,
                    regionDev,
                    bank,
                    presentation,
                    playerState,
                    devSession,
                    renderer,
                    (pending,w)->
                        holder[0].promptAmount(
                            pending,
                            w
                        ),
                    keys::clear
                );

            LocalDevPanelCoordinator coordinator=
                new LocalDevPanelCoordinator(
                    panel,
                    player,
                    bank,
                    itemLibrary,
                    petDialogs,
                    renderer,
                    amounts,
                    widgets,
                    keys,
                    bridge
                );
            holder[0]=coordinator;

            int before=wire.size();

            coordinator.open(
                DevControlCenter.Page.MAIN,
                writer
            );

            if(!panel.isOpen()||
               panel.page()!=DevControlCenter.Page.MAIN){
                throw new AssertionError(
                    "panel did not open on MAIN"
                );
            }

            if(wire.size()<=before)
                throw new AssertionError(
                    "panel open emitted no packets"
                );

            String active=
                Files.readString(
                    keys.file(),
                    StandardCharsets.UTF_8
                );

            if(!active.contains("active=true")||
               !active.contains(
                   "widgets=2482,2483,2484,2485"
               )){
                throw new AssertionError(
                    "dialog key publication changed: "+
                    active
                );
            }

            WorldPlayer tradePeer=
                new WorldPlayer();
            world.registerPlayer(
                tradePeer,
                "dev-panel-peer"
            );
            OutboundPacketQueue tradePeerQueue=
                new OutboundPacketQueue();
            ServerPacketWriter tradePeerWriter=
                new ServerPacketWriter(
                    tradePeerQueue,
                    new IsaacCipher(
                        new int[]{9,10,11,12}
                    )
                );

            TradeService.register(
                world,
                player,
                player.generation(),
                bank,
                writer,
                ()->{}
            );
            TradeService.register(
                world,
                tradePeer,
                tradePeer.generation(),
                tradePeer.bank(),
                tradePeerWriter,
                ()->{}
            );
            if(!TradeService.start(
                    world,
                    player,
                    tradePeer
                ).contains("TRADE_UI_OPEN"))
                throw new AssertionError(
                    "Dev Panel failure fixture Trade did not open"
                );

            bank.open(writer);
            itemLibrary.open(
                writer,
                28860
            );
            coordinator.promptAmount(
                DevControlCenter.PendingAmount.HIT_DAMAGE,
                writer
            );

            OutboundPacketQueue failedPanelQueue=
                new OutboundPacketQueue(1024);
            failedPanelQueue.offer(
                new byte[900]
            );
            ServerPacketWriter failedPanelWriter=
                new ServerPacketWriter(
                    failedPanelQueue,
                    new IsaacCipher(
                        new int[]{13,14,15,16}
                    )
                );

            boolean panelOpenFailed=false;
            try{
                coordinator.open(
                    DevControlCenter.Page.MORE,
                    failedPanelWriter
                );
            }catch(java.io.IOException expected){
                panelOpenFailed=true;
            }

            if(!panelOpenFailed||
               panel.isOpen()||
               !panel.hasPending()||
               panel.pending()!=
                    DevControlCenter.PendingAmount.HIT_DAMAGE)
                throw new AssertionError(
                    "failed Dev Panel target did not restore prior panel state"
                );

            if(!bank.isOpen()||
               !itemLibrary.isOpen()||
               itemLibrary.selectedItem()!=28860||
               !TradeService.active(player)||
               !TradeService.active(tradePeer))
                throw new AssertionError(
                    "failed Dev Panel target retired an existing owner"
                );

            TradeService.cancelIfActive(
                player,
                "DEV_PANEL_FAILURE_FIXTURE_CLEANUP"
            );
            TradeService.unregister(
                tradePeer
            );
            world.unregisterPlayer(
                tradePeer
            );
            bank.clientClosed();
            itemLibrary.close();

            coordinator.promptAmount(
                DevControlCenter.PendingAmount.ITEM_LIBRARY_ID,
                writer
            );
            int rootReplacingBeforeInvalid=
                bridge.rootReplacingAmounts;

            coordinator.handleAmount(
                -1,
                writer,
                "[dev-panel-test] "
            );

            if(bridge.rootReplacingAmounts!=
                    rootReplacingBeforeInvalid||
               itemLibrary.isOpen())
                throw new AssertionError(
                    "invalid Item Library amount entered root replacement"
                );

            coordinator.promptAmount(
                DevControlCenter.PendingAmount.ITEM_LIBRARY_ID,
                writer
            );

            coordinator.handleAmount(
                28860,
                writer,
                "[dev-panel-test] "
            );

            if(bridge.rootReplacingAmounts!=
                    rootReplacingBeforeInvalid+1||
               !itemLibrary.isOpen()||
               itemLibrary.selectedItem()!=28860)
                throw new AssertionError(
                    "valid Item Library amount bypassed root replacement"
                );

            itemLibrary.close();
            coordinator.open(
                DevControlCenter.Page.MAIN,
                writer
            );

            coordinator.promptAmount(
                DevControlCenter.PendingAmount.HIT_DAMAGE,
                writer
            );

            if(!panel.hasPending()||
               panel.pending()!=
                   DevControlCenter.PendingAmount.HIT_DAMAGE){
                throw new AssertionError(
                    "numeric prompt state changed"
                );
            }

            String prompted=
                Files.readString(
                    keys.file(),
                    StandardCharsets.UTF_8
                );

            if(!prompted.startsWith(
                "active=false\nwidgets=\n"
            )){
                throw new AssertionError(
                    "prompt did not clear dialog keys: "+
                    prompted
                );
            }

            coordinator.open(
                DevControlCenter.Page.MORE,
                writer
            );

            coordinator.closeSession();

            if(panel.isOpen()||panel.hasPending())
                throw new AssertionError(
                    "session close did not close panel"
                );

            String closed=
                Files.readString(
                    keys.file(),
                    StandardCharsets.UTF_8
                );

            if(!closed.startsWith(
                "active=false\nwidgets=\n"
            )){
                throw new AssertionError(
                    "session close did not clear keys"
                );
            }

            System.out.println(
                "LOCAL_DEV_PANEL_COORDINATOR_PASS "+
                "openRender=true keyPublication=true "+
                "itemLibraryRootReplacement=true "+
                "invalidItemLibraryPreserved=true "+
                "numericPrompt=true sessionClose=true "+
                "targetFailureAtomic=true "+
                "failedTargetPreservesBank=true "+
                "failedTargetPreservesItemLibrary=true "+
                "failedTargetPreservesTrade=true"
            );
        }finally{
            world.close();
            try{
                Files.deleteIfExists(
                    tempDir.resolve(
                        "dialog-keys.properties.tmp"
                    )
                );
                Files.deleteIfExists(
                    tempDir.resolve(
                        "dialog-keys.properties"
                    )
                );
                Files.deleteIfExists(tempDir);
            }catch(Throwable ignored){}
        }
    }
}
