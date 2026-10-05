package spk.local;

import java.io.*;
import java.net.*;
import java.time.Instant;

final class LocalSession implements Runnable {
    @FunctionalInterface
    interface MonsterSpawnerUiFactory {
        LocalMonsterSpawnerUiHandler create(
            World world,
            WorldPlayer player,
            String canonicalUsername
        ) throws Exception;

        default void onCommittedResult(
            World world,
            WorldPlayer player,
            String canonicalUsername,
            LocalMonsterSpawnerUiHandler.Result result,
            ServerPacketWriter writer,
            String tag
        ) throws Exception{}

        default void onSessionClosed(
            World world,
            WorldPlayer player,
            long expectedGeneration,
            String canonicalUsername
        ) throws Exception{}
    }

    @FunctionalInterface
    interface MonsterSpawnerOpenAction {
        boolean open() throws IOException;
    }

    @FunctionalInterface
    interface WorldTickGateAction {
        void run() throws Exception;
    }

    static final class WorldTickGate {
        private boolean active;

        synchronized void activate(){
            active=true;
        }

        synchronized void disableAndAwait(){
            active=false;
        }

        synchronized boolean runIfActive(
            WorldTickGateAction action
        )throws Exception{
            if(action==null)
                throw new NullPointerException(
                    "world tick gate action"
                );

            if(!active)
                return false;

            action.run();
            return true;
        }

        synchronized boolean runIfActiveAndWriterLive(
            ServerPacketWriter writer,
            WorldTickGateAction action
        )throws Exception{
            if(action==null)
                throw new NullPointerException(
                    "world command gate action"
                );

            if(!active)
                return false;

            if(writer==null)
                return false;

            if(writer.terminal())
                throw new IOException(
                    "terminal session packet writer"
                );

            action.run();
            return true;
        }

        synchronized boolean runRunnableIfActive(
            Runnable action
        ){
            if(action==null)
                throw new NullPointerException(
                    "world callback gate action"
                );

            if(!active)
                return false;

            action.run();
            return true;
        }

        synchronized boolean
            runRunnableIfActiveAndWriterLive(
                ServerPacketWriter writer,
                Runnable action
            )
        {
            if(action==null)
                throw new NullPointerException(
                    "world callback gate action"
                );

            if(!active||
               writer==null||
               writer.terminal())
                return false;

            action.run();
            return true;
        }

        synchronized boolean active(){
            return active;
        }
    }

    private final Socket socket;
    private final boolean bootstrap;
    private final boolean movementEnabled;
    private final World world;
    private final MonsterSpawnerUiFactory monsterSpawnerUiFactory;
    private final WorldPlayer worldPlayer;
    private final MovementState movement;
    private final BankState bank;
    private final EquipmentState equipment;
    private final PetState petState;
    private final PlayerState playerState;
    private final PlayerStatusService statuses;
    private final PrayerState prayers;
    private final MagicState magic;
    private final CombatStyleState combatStyles;
    private final PetEffectState petEffects;
    private final MiniPetService miniPets;
    private final VoidglassPetState voidglass = new VoidglassPetState();
    private final DevAuthorityWorkbench dev = new DevAuthorityWorkbench();
    private final PlayerPresentationService playerPresentation;
    private final NpcRegistry npcs;
    private final HomeWorldRuntimePlan homeWorld;
    private final CombatEngine combat;
    /** Engine R5 native Item Library server-side authority projection. */
    private final NativeItemLibraryService itemLibrary = new NativeItemLibraryService();
    private final LocalDiagnosticCommandHandler diagnosticCommands;
    private final LocalPrayerMagicCommandHandler prayerMagicCommands;
    private final LocalDevWorldCommandHandler devWorldCommands;
    private final LocalMiniPetCommandHandler miniPetCommands;
    private final LocalCosmeticCommandHandler cosmeticCommands;
    private final LocalCompColorsCommandHandler compColorsCommands;
    private final LocalBankRequestHandler bankRequests;
    private final LocalItemOnItemHandler itemOnItemHandler;
    private final LocalSpellTargetHandler spellTargetHandler;
    private final LocalGroundItemInteractionHandler groundItemHandler;
    private final LocalItemOnNpcHandler itemOnNpcHandler;
    private final LocalGameplayWidgetHandler gameplayWidgetHandler;
    private final LocalBankObjectInteractionHandler bankObjectHandler;
    private final LocalRoutedNpcInteractionHandler routedNpcHandler;
    private final LocalGenericInteractionHandler genericInteractionHandler;
    private final LocalPlayerInteractionHandler playerInteractions;
    private final LocalCanonicalNpcAttackHandler canonicalNpcAttack;
    private final LocalEquipmentItemActionHandler equipmentItemActions;
    private final LocalPetInventoryDialogHandler petDialogs;
    private final LocalCompCapeCustomizeHandler compCapeCustomize;
    private final LocalDevPetCommandHandler devPetCommands;
    private final LocalDevPlayerCommandHandler devPlayerCommands;
    private final LocalDevNpcCommandHandler devNpcCommands;
    private final LocalDevToolCommandHandler devToolCommands;
    private final LocalVoidglassCommandHandler voidglassCommands;
    private final LocalPetRuntimeCommandHandler petRuntimeCommands;
    private final LocalCombatCommandHandler combatCommands;
    private final LocalRegionDevCommandHandler regionDevCommands;
    private final LocalDevSessionCommandHandler devSessionCommands;
    private final LocalPetCompatibilityCommandHandler petCompatibilityCommands;
    /** Engine R7 one-stop in-game developer control center. */
    private final DevControlCenter devPanel = new DevControlCenter();
    private final LocalDialogNumberKeyState dialogNumberKeys = new LocalDialogNumberKeyState();
    private final LocalDevPanelCoordinator devPanelCoordinator;
    private final LocalCommandDispatcher commandDispatcher;
    private final LocalSessionUiActionHandler uiActions;
    private final LocalPetDropPickupHandler petDropPickup;
    private final LocalPetRealtimeScheduler petRealtime;
    private final LocalMovementRequestHandler movementRequests;
    private final RegionLoadLifecycle regionLoads;
    private final LocalRegionStreamHandler regionStreams;
    private final LocalWorldTickCoordinator worldTicks;
    private final LocalPendingRequestDispatcher pendingRequests;
    private final LocalSessionBootstrapPublisher bootstrapPublisher;
    private final LocalSessionPlayerInitializer playerInitializer;
    private final LocalSessionRuntimeBindings runtimeBindings;
    private SceneUpdatePublisher scenePublisher;
    private ServerPacketWriter sessionPackets;
    private OutboundPacketQueue outboundPackets;
    private long sessionWorldTick;
    private boolean worldRegistered;
    private long worldPlayerGeneration;
    private boolean worldTickAttached;
    private final WorldTickGate worldTickGate=
        new WorldTickGate();
    private final WorldTickGate worldCommandGate=
        new WorldTickGate();
    private String username = AccountStore.CANONICAL_USERNAME;
    private String loginAlias = "localtest";
    private boolean persistentAccount;
    /** Persisted semantic global pet accessory. 0 means none. */
    private final PetAccessoryState petAccessoryState;
    private volatile boolean logoutRequested;

    LocalSession(Socket socket, boolean bootstrap) {
        this(socket,bootstrap,false,World.shared(),null);
    }

    LocalSession(
        Socket socket,
        boolean bootstrap,
        boolean movementEnabled
    ){
        this(
            socket,
            bootstrap,
            movementEnabled,
            World.shared(),
            null
        );
    }

    LocalSession(
        Socket socket,
        boolean bootstrap,
        boolean movementEnabled,
        World world
    ){
        this(
            socket,
            bootstrap,
            movementEnabled,
            world,
            null
        );
    }

    LocalSession(
        Socket socket,
        boolean bootstrap,
        boolean movementEnabled,
        World world,
        MonsterSpawnerUiFactory monsterSpawnerUiFactory
    ){
        VoidglassR3CustomContent.ensureRuntimePetMapping();
        this.socket = socket;
        this.bootstrap = bootstrap;
        this.movementEnabled = movementEnabled;
        this.world = java.util.Objects.requireNonNull(world,"world");
        this.monsterSpawnerUiFactory=monsterSpawnerUiFactory;
        this.playerPresentation =
            new PlayerPresentationService(
                this.world,
                dev
            );
        this.regionLoads = new RegionLoadLifecycle();
        this.homeWorld = new HomeWorldRuntimePlan(this.world.homeNpcs());
        this.worldPlayer = new WorldPlayer();
        this.petAccessoryState=worldPlayer.petAccessoryState();
        this.movement = worldPlayer.movement();
        this.bank = worldPlayer.bank();
        this.equipment = worldPlayer.equipment();
        this.petState = worldPlayer.petState();
        this.playerState = worldPlayer.playerState();
        this.statuses = new PlayerStatusService(worldPlayer);
        this.prayers = worldPlayer.prayers();
        this.magic = worldPlayer.magic();
        this.combatStyles = worldPlayer.combatStyles();
        this.petEffects = worldPlayer.petEffects();
        this.miniPets = worldPlayer.miniPets();
        this.combat = new CombatEngine(
            worldPlayer.combatState(),
            dev,
            CombatDamageRules.localLabFallback(),
            CombatSystemHooks.forPlayer(
                worldPlayer
            )
        );
        this.npcs = new NpcRegistry(
            dev,
            this.world.petNpcs(),
            worldPlayer.id()
        );
        this.diagnosticCommands = new LocalDiagnosticCommandHandler(
            world,itemLibrary);
        this.prayerMagicCommands = new LocalPrayerMagicCommandHandler(prayers);
        this.devWorldCommands = new LocalDevWorldCommandHandler(world,movement);
        this.miniPetCommands = new LocalMiniPetCommandHandler(miniPets,petState,npcs,movement);
        this.cosmeticCommands = new LocalCosmeticCommandHandler(bank,equipment,playerState,playerPresentation);
        this.compColorsCommands = new LocalCompColorsCommandHandler(playerState,equipment,playerPresentation);
        this.bankRequests = new LocalBankRequestHandler(worldPlayer,bank);
        this.itemOnItemHandler = new LocalItemOnItemHandler(bank);
        this.spellTargetHandler = new LocalSpellTargetHandler(
            magic,bank,equipment,playerState,npcs,combat);
        this.groundItemHandler = new LocalGroundItemInteractionHandler(
            world,bank,movement);
        this.itemOnNpcHandler = new LocalItemOnNpcHandler(
            bank,npcs,movement,petAccessoryState);
        this.gameplayWidgetHandler = new LocalGameplayWidgetHandler(
            prayers,playerState,equipment,combatStyles,magic,bank);
        this.bankObjectHandler = new LocalBankObjectInteractionHandler(
            bank,
            movement,
            world.content()
        );
        this.routedNpcHandler = new LocalRoutedNpcInteractionHandler(
            npcs,
            bank,
            movement,
            world.content(),
            worldPlayer,
            equipment
        );
        this.genericInteractionHandler =
            new LocalGenericInteractionHandler(
                world.content()
            );
        this.playerInteractions = new LocalPlayerInteractionHandler(
            world,
            worldPlayer,
            movement,
            equipment,
            ()->LocalSession.this.worldPlayerGeneration
        );
        this.canonicalNpcAttack=
            new LocalCanonicalNpcAttackHandler(
                world,
                worldPlayer,
                ()->LocalSession.this.worldPlayerGeneration,
                equipment,
                combatStyles,
                npcs,
                (target,generation)->{},
                target->
                    LocalSession.this.world
                        .finalizeMonsterSpawnerPvmIfOwned(
                            target
                        ),
                target->
                    LocalSession.this.world
                        .retryMonsterSpawnerPvmFinalizationIfPending(
                            target
                        )
            );
        this.equipmentItemActions = new LocalEquipmentItemActionHandler(
            bank,equipment,playerState,playerPresentation,combatStyles);
        this.petDialogs = new LocalPetInventoryDialogHandler(
            bank,miniPets,petState,npcs,movement,petAccessoryState);
        this.compCapeCustomize = new LocalCompCapeCustomizeHandler(
            bank,playerState);
        this.devPetCommands = new LocalDevPetCommandHandler(
            dev,npcs,movement,bank);
        this.devPlayerCommands = new LocalDevPlayerCommandHandler(
            playerPresentation,equipment,playerState);
        this.devNpcCommands = new LocalDevNpcCommandHandler(
            npcs,movement);
        this.devToolCommands = new LocalDevToolCommandHandler(
            dev,bank,equipment);
        this.voidglassCommands = new LocalVoidglassCommandHandler(
            bank,petState,npcs,movement,dev,voidglass);
        this.petRuntimeCommands = new LocalPetRuntimeCommandHandler(
            petState,petEffects,npcs,movement);
        this.combatCommands = new LocalCombatCommandHandler(
            combat,npcs,petRuntimeCommands);
        this.regionDevCommands = new LocalRegionDevCommandHandler(
            world,
            worldPlayer,
            movement,
            playerInteractions,
            combat,
            npcs,
            petState,
            homeWorld,
            regionLoads,
            ()->resetPetFollowRuntime());
        this.devSessionCommands = new LocalDevSessionCommandHandler(
            world,
            dev,
            npcs,
            playerPresentation,
            equipment,
            playerState,
            bank,
            petState,
            movement);
        this.petCompatibilityCommands = new LocalPetCompatibilityCommandHandler(
            petAccessoryState,npcs,movement,petDialogs);
        LocalDevPanelRenderer devPanelRenderer = new LocalDevPanelRenderer(
            devPanel,
            equipment,
            combatStyles,
            dev,
            combat,
            npcs,
            petState,
            magic,
            prayers,
            movement,
            playerPresentation);
        LocalDevPanelAmountHandler devPanelAmounts = new LocalDevPanelAmountHandler(
            devPanel,
            dev,
            equipment,
            combat,
            npcs,
            movement,
            regionDevCommands,
            itemLibrary,
            playerPresentation,
            playerState,
            prayers,
            devPanelRenderer,
            ()->dialogNumberKeys.clear());
        LocalDevPanelWidgetHandler devPanelWidgets = new LocalDevPanelWidgetHandler(
            devPanel,
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
            regionDevCommands,
            bank,
            playerPresentation,
            playerState,
            devSessionCommands,
            devPanelRenderer,
            (pending,writer)->promptDevPanelAmount(pending,writer),
            ()->dialogNumberKeys.clear());
        this.devPanelCoordinator = new LocalDevPanelCoordinator(
            devPanel,
            worldPlayer,
            bank,
            itemLibrary,
            petDialogs,
            devPanelRenderer,
            devPanelAmounts,
            devPanelWidgets,
            dialogNumberKeys,
            new LocalDevPanelCoordinator.SessionBridge(){
                @Override public String username(){
                    return LocalSession.this.username;
                }

                @Override public SceneUpdatePublisher scenePublisher(){
                    return LocalSession.this.scenePublisher;
                }

                @Override public void replaceScenePublisher(
                    SceneUpdatePublisher replacement
                ){
                    LocalSession.this.scenePublisher=replacement;
                }

                @Override public void saveAccount(
                    String tag,
                    String reason
                ){
                    LocalSession.this.saveAccountQuiet(tag,reason);
                }

                @Override public LocalDevPanelAmountHandler.Outcome
                    handleRootReplacingAmount(
                        LocalDevPanelCoordinator
                            .RootReplacingAmountAction action
                    )throws IOException{
                    final LocalDevPanelAmountHandler.Outcome[]
                        outcome={null};

                    String result=
                        LocalSession.this.uiActions
                            .replaceMonsterSpawnerWithItemLibraryRootCommand(
                                ()->{
                                    outcome[0]=
                                        action.handle();
                                    return true;
                                }
                            );

                    return result==null
                        ?null
                        :outcome[0];
                }
            });
        this.commandDispatcher = new LocalCommandDispatcher(
            bankRequests,
            diagnosticCommands,
            regionDevCommands,
            prayerMagicCommands,
            miniPetCommands,
            cosmeticCommands,
            devWorldCommands,
            dev,
            devSessionCommands,
            devPetCommands,
            devPlayerCommands,
            devNpcCommands,
            devToolCommands,
            voidglassCommands,
            petRuntimeCommands,
            compColorsCommands,
            combatCommands,
            petCompatibilityCommands,
            world.content(),
            worldPlayer,
            new LocalCommandDispatcher.SessionBridge(){
                @Override public SceneUpdatePublisher scenePublisher(){
                    return LocalSession.this.scenePublisher;
                }

                @Override public void replaceScenePublisher(
                    SceneUpdatePublisher replacement
                ){
                    LocalSession.this.scenePublisher=replacement;
                }

                @Override public void saveAccount(
                    String tag,
                    String reason
                ){
                    LocalSession.this.saveAccountQuiet(tag,reason);
                }

                @Override public void openDevPanel(
                    ServerPacketWriter writer
                )throws IOException{
                    openDevPanelForCurrentSession(
                        LocalSession.this.world,
                        LocalSession.this.worldPlayer,
                        LocalSession.this.worldPlayerGeneration,
                        LocalSession.this.uiActions,
                        ()->{
                            LocalSession.this.devPanelCoordinator.open(
                                DevControlCenter.Page.MAIN,
                                writer
                            );
                            return "DEV_PANEL_ROOT_OPENED";
                        }
                    );
                }

                @Override public boolean openMonsterSpawner(
                    ServerPacketWriter writer
                )throws IOException{
                    return openMonsterSpawnerForCurrentSession(
                        LocalSession.this.world,
                        LocalSession.this.worldPlayer,
                        LocalSession.this.worldPlayerGeneration,
                        ()->
                            LocalSession.this.uiActions
                                .openMonsterSpawnerIfConfigured(
                                    writer
                                )
                    );
                }

                @Override public LocalCommandDispatcher
                    .RootReplacingCommandDispatch
                    handleRootReplacingCommand(
                        LocalCommandDispatcher
                            .RootReplacingCommandAction action
                    )throws IOException{
                    String result=
                        LocalSession.this.uiActions
                            .replaceMonsterSpawnerWithItemLibraryRootCommand(
                                ()->
                                    action.handle()
                            );

                    if(result==null)
                        return LocalCommandDispatcher
                            .RootReplacingCommandDispatch
                            .rejected();

                    if("ROOT_COMMAND_HANDLED".equals(
                            result))
                        return LocalCommandDispatcher
                            .RootReplacingCommandDispatch
                            .admitted(
                                true
                            );

                    if("ROOT_COMMAND_NOT_HANDLED".equals(
                            result))
                        return LocalCommandDispatcher
                            .RootReplacingCommandDispatch
                            .admitted(
                                false
                            );

                    throw new IllegalStateException(
                        "Unexpected root command result="+
                        result
                    );
                }

                @Override public void applyPetDialog(
                    LocalPetInventoryDialogHandler.Result result,
                    String tag
                ){
                    LocalSession.this.applyPetDialogResult(result,tag);
                }
            });
        this.uiActions = new LocalSessionUiActionHandler(
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
            new LocalSessionUiActionHandler.SessionBridge(){
                @Override public void saveAccount(
                    String tag,
                    String reason
                ){
                    LocalSession.this.saveAccountQuiet(tag,reason);
                }

                @Override public void clearDialogNumberKeys(){
                    LocalSession.this.dialogNumberKeys.clear();
                }

                @Override public void handleDevPanelWidget(
                    int widget,
                    ServerPacketWriter writer,
                    String tag
                )throws IOException{
                    LocalSession.this.devPanelCoordinator.handleWidget(
                        widget,
                        writer,
                        tag
                    );
                }

                @Override public void applyPetDialog(
                    LocalPetInventoryDialogHandler.Result result,
                    String tag
                ){
                    LocalSession.this.applyPetDialogResult(
                        result,
                        tag
                    );
                }

                @Override public void handleHomeTeleport(
                    ServerPacketWriter writer,
                    String tag
                )throws IOException{
                    LocalSession.this.handleHomeTeleportFromWidget(
                        writer,
                        tag
                    );
                }

                @Override public LocalSessionUiActionHandler
                    .MonsterSpawnerDispatch handleMonsterSpawnerWidget(
                        LocalMonsterSpawnerUiHandler handler,
                        int widget,
                        ServerPacketWriter writer,
                        String tag
                    )throws IOException{
                    return dispatchMonsterSpawnerWidgetForCurrentSession(
                        LocalSession.this.monsterSpawnerUiFactory,
                        LocalSession.this.world,
                        LocalSession.this.worldPlayer,
                        LocalSession.this.worldPlayerGeneration,
                        LocalSession.this.username,
                        handler,
                        widget,
                        writer,
                        tag
                    );
                }

                @Override public LocalSessionUiActionHandler
                    .MonsterSpawnerDispatch handleMonsterSpawnerWidget(
                        LocalMonsterSpawnerUiHandler handler,
                        int widget,
                        ServerPacketWriter writer,
                        String tag,
                        java.util.function.BooleanSupplier uiOpen
                    )throws IOException{
                    return dispatchMonsterSpawnerWidgetForCurrentSession(
                        LocalSession.this.monsterSpawnerUiFactory,
                        LocalSession.this.world,
                        LocalSession.this.worldPlayer,
                        LocalSession.this.worldPlayerGeneration,
                        LocalSession.this.username,
                        handler,
                        widget,
                        writer,
                        tag,
                        uiOpen
                    );
                }

                @Override public boolean closeMonsterSpawnerUi(
                    java.util.function.BooleanSupplier closeAction
                )throws IOException{
                    return closeMonsterSpawnerUiForCurrentSession(
                        LocalSession.this.world,
                        LocalSession.this.worldPlayer,
                        LocalSession.this.worldPlayerGeneration,
                        closeAction
                    );
                }

                @Override public String replaceMonsterSpawnerRoot(
                    LocalSessionUiActionHandler.RootInterfaceAction action
                )throws IOException{
                    return replaceMonsterSpawnerRootForCurrentSession(
                        LocalSession.this.world,
                        LocalSession.this.worldPlayer,
                        LocalSession.this.worldPlayerGeneration,
                        action
                    );
                }

                @Override public boolean retireMakeoverDesignerRoot(){
                    LocalMakeoverMageHandler makeover=
                        LocalSession.this.routedNpcHandler
                            .makeoverMage();

                    return makeover!=null&&
                        makeover.retireDesignerRoot();
                }

                @Override public void handleMonsterSpawnerResult(
                    LocalMonsterSpawnerUiHandler.Result result,
                    ServerPacketWriter writer,
                    String tag
                )throws IOException{
                    forwardMonsterSpawnerUiResult(
                        LocalSession.this.monsterSpawnerUiFactory,
                        LocalSession.this.world,
                        LocalSession.this.worldPlayer,
                        LocalSession.this.worldPlayerGeneration,
                        LocalSession.this.username,
                        result,
                        writer,
                        tag
                    );
                }

                @Override public void requestLogout(){
                    LocalSession.this.logoutRequested=true;
                }
            });

        this.bankObjectHandler.installRootOwner(
            action->
                this.uiActions.replaceMonsterSpawnerWithBankRoot(
                    ()->action.open()
                )
        );

        this.routedNpcHandler.installBankRootOwner(
            action->
                this.uiActions.replaceMonsterSpawnerWithBankRoot(
                    ()->action.open()
                )
        );

        this.petDropPickup = new LocalPetDropPickupHandler(
            world,
            bank,
            movement,
            petState,
            petEffects,
            miniPets,
            npcs,
            voidglass,
            petAccessoryState,
            dev,
            new LocalPetDropPickupHandler.SessionBridge(){
                @Override public String username(){
                    return LocalSession.this.username;
                }

                @Override public boolean persistentAccount(){
                    return LocalSession.this.persistentAccount;
                }

                @Override public long sessionWorldTick(){
                    return LocalSession.this.sessionWorldTick;
                }

                @Override public SceneUpdatePublisher scenePublisher(){
                    return LocalSession.this.scenePublisher;
                }


                @Override public void saveAccount(
                    String tag,
                    String reason
                ){
                    LocalSession.this.saveAccountQuiet(tag,reason);
                }

                @Override public PlayerState.PreparedScopesightMaintenance
                    prepareScopesightPassive(
                        boolean active
                    ){
                    return playerState
                        .prepareScopesightMaintenance(
                            active
                        );
                }

                @Override public void publishScopesightPassive(
                    PlayerState.PreparedScopesightMaintenance prepared,
                    ServerPacketWriter writer
                )throws IOException{
                    if(prepared==null)
                        throw new NullPointerException(
                            "prepared"
                        );

                    for(int skill=0;
                        skill<PlayerState.COMBAT_SKILL_COUNT;
                        skill++){
                        if((prepared.changedMask&
                            (1<<skill))==0)
                            continue;

                        writer.fixed(
                            134,
                            BootstrapPackets.skill134(
                                skill,
                                playerState.xp(skill),
                                prepared.levelForSkill(
                                    skill
                                )
                            )
                        );
                    }
                }

                @Override public void commitScopesightPassive(
                    PlayerState.PreparedScopesightMaintenance prepared
                ){
                    playerState
                        .commitScopesightMaintenance(
                            prepared
                        );
                }

                @Override public void resetPetFollowDeadline(){
                    LocalSession.this.resetPetFollowDeadline();
                }

                @Override public void ensurePetFollowScheduled(
                    long now
                ){
                    LocalSession.this.ensurePetFollowScheduled(now);
                }
            });
        this.petRealtime = new LocalPetRealtimeScheduler(
            bootstrap,
            world,
            worldPlayer,
            movement,
            npcs,
            petDropPickup,
            petRuntimeCommands,
            ()->LocalSession.this.worldPlayerGeneration,
            new LocalPetRealtimeScheduler.SessionBridge(){
                @Override public ServerPacketWriter sessionPackets(){
                    return LocalSession.this.sessionPackets;
                }

                @Override public String sessionTag(){
                    return "[session "+
                        LocalSession.this.socket.getRemoteSocketAddress()+
                        "] ";
                }

                @Override public boolean
                    runIfSessionWorldCallbackActive(
                        Runnable action
                    )
                {
                    return LocalSession.this
                        .worldTickGate
                        .runRunnableIfActiveAndWriterLive(
                            LocalSession.this.sessionPackets,
                            action
                        );
                }
            });
        this.movementRequests = new LocalMovementRequestHandler(
            movementEnabled,
            bank,
            petDialogs,
            devPanel,
            movement,
            combat,
            equipment,
            playerInteractions,
            petDropPickup,
            npcs,
            new LocalMovementRequestHandler.SessionBridge(){
                @Override public void clearDialogNumberKeys(){
                    LocalSession.this.dialogNumberKeys.clear();
                }

                @Override public void cancelActiveDialogue(
                    ServerPacketWriter writer,
                    String tag,
                    String reason
                )throws IOException{
                    LocalMakeoverMageHandler handler=
                        LocalSession.this.routedNpcHandler
                            .makeoverMage();

                    if(handler!=null)
                        handler.cancelForManualMovement(
                            writer,
                            tag
                        );
                }

                @Override public void clearOpponentOverlay(
                    ServerPacketWriter writer,
                    String tag,
                    String reason
                )throws IOException{
                    LocalSession.this.clearOpponentOverlay(
                        writer,
                        tag,
                        reason
                    );
                }
            });
        this.regionStreams = new LocalRegionStreamHandler(
            movementEnabled,
            world,
            worldPlayer,
            movement,
            homeWorld,
            npcs,
            playerInteractions,
            combat,
            regionLoads,
            new LocalRegionStreamHandler.SessionBridge(){
                @Override public String username(){
                    return LocalSession.this.username;
                }

                @Override public SceneUpdatePublisher scenePublisher(){
                    return LocalSession.this.scenePublisher;
                }

                @Override public void replaceScenePublisher(
                    SceneUpdatePublisher replacement
                ){
                    LocalSession.this.scenePublisher=replacement;
                }

                @Override public void resetPetFollowRuntime(){
                    LocalSession.this.resetPetFollowRuntime();
                }
            });
        this.worldTicks = new LocalWorldTickCoordinator(
            movementEnabled,
            world,
            worldPlayer,
            movement,
            equipment,
            combatStyles,
            petEffects,
            statuses,
            npcs,
            homeWorld,
            combat,
            regionStreams,
            playerInteractions,
            bankObjectHandler,
            routedNpcHandler,
            groundItemHandler,
            petDropPickup,
            petRuntimeCommands,
            new LocalWorldTickCoordinator.SessionBridge(){
                @Override public Player81WorldSync.Context player81Sync(){
                    return LocalSession.this.runtimeBindings.context();
                }

                @Override public SceneUpdatePublisher scenePublisher(){
                    return LocalSession.this.scenePublisher;
                }

                @Override public void publishPlayerAppearanceSnapshot(
                    int[] appearanceItems,
                    ServerPacketWriter writer
                )throws IOException{
                    LocalSession.this.playerPresentation
                        .refreshSnapshot(
                            LocalSession.this.username,
                            appearanceItems,
                            LocalSession.this.playerState,
                            writer
                        );
                }


                @Override public void saveAccount(
                    String tag,
                    String reason
                ){
                    LocalSession.this.saveAccountQuiet(tag,reason);
                }

                @Override public void publishOpponentOverlay(
                    NpcEntity target,
                    ServerPacketWriter writer,
                    String tag,
                    String reason
                )throws IOException{
                    LocalSession.this.publishOpponentOverlay(
                        target,
                        writer,
                        tag,
                        reason
                    );
                }

                @Override public void clearOpponentOverlay(
                    ServerPacketWriter writer,
                    String tag,
                    String reason
                )throws IOException{
                    LocalSession.this.clearOpponentOverlay(
                        writer,
                        tag,
                        reason
                    );
                }

                @Override public long petFollowDeadline(){
                    return LocalSession.this.petFollowDeadline();
                }

                @Override public void setPetFollowDeadline(
                    long value
                ){
                    LocalSession.this.setPetFollowDeadline(value);
                }

                @Override public void ensurePetFollowScheduled(
                    long now
                ){
                    LocalSession.this.ensurePetFollowScheduled(now);
                }

                @Override public void ensurePetTestSequenceScheduled(
                    long now
                ){
                    LocalSession.this.ensurePetTestSequenceScheduled(now);
                }
            });
        this.pendingRequests = new LocalPendingRequestDispatcher(
            worldPlayer,
            bank,
            equipment,
            combatStyles,
            movement,
            npcs,
            combat,
            devPanel,
            uiActions,
            commandDispatcher,
            bankObjectHandler,
            genericInteractionHandler,
            equipmentItemActions,
            petDialogs,
            compCapeCustomize,
            itemOnItemHandler,
            itemOnNpcHandler,
            spellTargetHandler,
            petDropPickup,
            groundItemHandler,
            playerInteractions,
            routedNpcHandler,
            bankRequests,
            movementRequests,
            petRealtime,
            new LocalPendingRequestDispatcher.SessionBridge(){
                @Override public String username(){
                    return LocalSession.this.username;
                }

                @Override public String loginAlias(){
                    return LocalSession.this.loginAlias;
                }

                @Override public boolean persistentAccount(){
                    return LocalSession.this.persistentAccount;
                }

                @Override public long sessionWorldTick(){
                    return LocalSession.this.sessionWorldTick;
                }

                @Override public SceneUpdatePublisher scenePublisher(){
                    return LocalSession.this.scenePublisher;
                }

                @Override public Player81WorldSync.Context player81Sync(){
                    return LocalSession.this.runtimeBindings.context();
                }

                @Override public void refreshPlayerAppearance(
                    ServerPacketWriter writer
                )throws IOException{
                    LocalSession.this.playerPresentation.refresh(
                        LocalSession.this.username,
                        LocalSession.this.equipment,
                        LocalSession.this.playerState,
                        writer
                    );
                }

                @Override public void saveAccount(
                    String tag,
                    String reason
                ){
                    LocalSession.this.saveAccountQuiet(tag,reason);
                }

                @Override public void applyPetDialogResult(
                    LocalPetInventoryDialogHandler.Result result,
                    String tag
                ){
                    LocalSession.this.applyPetDialogResult(
                        result,
                        tag
                    );
                }

                @Override public void clearOpponentOverlay(
                    ServerPacketWriter writer,
                    String tag,
                    String reason
                )throws IOException{
                    LocalSession.this.clearOpponentOverlay(
                        writer,
                        tag,
                        reason
                    );
                }

                @Override public void handleRegionLoadAck(
                    String tag
                ){
                    LocalSession.this.handleRegionLoadAck(
                        tag
                    );
                }

                @Override public LocalCanonicalNpcAttackHandler.Result
                    handleCanonicalNpcAttack(
                        NpcAction action,
                        NpcEntity clicked,
                        ServerPacketWriter writer
                    )throws IOException{
                    return LocalSession.this
                        .canonicalNpcAttack
                        .handle(
                            action,
                            clicked,
                            writer
                        );
                }

                @Override public void handleDevPanelAmount(
                    int value,
                    ServerPacketWriter writer,
                    String tag
                )throws IOException{
                    LocalSession.this.devPanelCoordinator.handleAmount(
                        value,
                        writer,
                        tag
                    );
                }
            });
        this.bootstrapPublisher = new LocalSessionBootstrapPublisher(
            world,
            equipment,
            movement,
            playerState,
            prayers,
            magic,
            bank,
            homeWorld,
            npcs,
            petState,
            petAccessoryState,
            miniPets,
            movementEnabled,
            (tag,reason)->LocalSession.this.saveAccountQuiet(
                tag,
                reason
            ));
        this.playerInitializer = new LocalSessionPlayerInitializer(
            world,
            worldPlayer,
            bank,
            equipment,
            movement,
            petState,
            playerState,
            petEffects,
            petAccessoryState
        );
        this.runtimeBindings = new LocalSessionRuntimeBindings(
            world,
            worldPlayer,
            dev,
            npcs,
            movement,
            bank,
            new LocalSessionRuntimeBindings.SessionBridge(){
                @Override public void saveAccount(
                    String tag,
                    String reason
                ){
                    LocalSession.this.saveAccountQuiet(
                        tag,
                        reason
                    );
                }

                @Override public void publishTradeRoot(
                    TradeService.RootPublication action
                )throws IOException{
                    LocalSession.this.uiActions
                        .publishTradeRootForOwnedSession(
                            ()->{
                                action.publish();
                                return "TRADE_ROOT_PUBLISHED";
                            }
                        );
                }
            }
        );
        if (movementEnabled && !bootstrap) throw new IllegalArgumentException("movement requires bootstrap");
    }

    static boolean openMonsterSpawnerForCurrentSession(
        World world,
        WorldPlayer player,
        long expectedGeneration,
        MonsterSpawnerOpenAction action
    )throws IOException{
        World checkedWorld=
            java.util.Objects.requireNonNull(
                world,
                "world"
            );
        WorldPlayer checkedPlayer=
            java.util.Objects.requireNonNull(
                player,
                "player"
            );
        MonsterSpawnerOpenAction checkedAction=
            java.util.Objects.requireNonNull(
                action,
                "action"
            );
        final boolean[] opened={false};
        final boolean[] published={false};

        try{
            boolean worldOpen=
                checkedWorld
                    .withOpenLifecycleOwnership(
                        ()->
                            published[0]=
                                TradeService.publishCompetingRoot(
                                    checkedPlayer,
                                    ()->{
                                        boolean delivered=
                                            checkedWorld
                                                .withOpenPlayerMutationOwnershipIfCurrent(
                                                    checkedPlayer,
                                                    expectedGeneration,
                                                    ()->
                                                        opened[0]=
                                                            checkedAction.open()
                                                );

                                        return delivered&&
                                            opened[0];
                                    }
                                )
                    );

            return worldOpen&&
                published[0]&&
                opened[0];
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IOException(
                "Monster Spawner UI open failed",
                failure
            );
        }
    }

    static boolean closeMonsterSpawnerUiForCurrentSession(
        World world,
        WorldPlayer player,
        long expectedGeneration,
        java.util.function.BooleanSupplier closeAction
    )throws IOException{
        World checkedWorld=
            java.util.Objects.requireNonNull(
                world,
                "world"
            );
        WorldPlayer checkedPlayer=
            java.util.Objects.requireNonNull(
                player,
                "player"
            );
        java.util.function.BooleanSupplier checkedAction=
            java.util.Objects.requireNonNull(
                closeAction,
                "closeAction"
            );
        final boolean[] wasOpen={false};

        try{
            boolean delivered=
                checkedWorld
                    .withOpenPlayerMutationOwnershipIfCurrent(
                        checkedPlayer,
                        expectedGeneration,
                        ()->
                            wasOpen[0]=
                                checkedAction.getAsBoolean()
                    );

            return delivered&&
                wasOpen[0];
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IOException(
                "Monster Spawner UI close failed",
                failure
            );
        }
    }

    static String openDevPanelForCurrentSession(
        World world,
        WorldPlayer player,
        long expectedGeneration,
        LocalSessionUiActionHandler uiActions,
        LocalSessionUiActionHandler.RootInterfaceAction action
    )throws IOException{
        LocalSessionUiActionHandler checkedUi=
            java.util.Objects.requireNonNull(
                uiActions,
                "uiActions"
            );
        LocalSessionUiActionHandler.RootInterfaceAction checkedAction=
            java.util.Objects.requireNonNull(
                action,
                "action"
            );

        return replaceMonsterSpawnerRootForCurrentSession(
            world,
            player,
            expectedGeneration,
            ()->
                checkedUi.publishDevPanelRootForOwnedSession(
                    checkedAction
                )
        );
    }

    static String replaceMonsterSpawnerRootForCurrentSession(
        World world,
        WorldPlayer player,
        long expectedGeneration,
        LocalSessionUiActionHandler.RootInterfaceAction action
    )throws IOException{
        World checkedWorld=
            java.util.Objects.requireNonNull(
                world,
                "world"
            );
        WorldPlayer checkedPlayer=
            java.util.Objects.requireNonNull(
                player,
                "player"
            );
        LocalSessionUiActionHandler.RootInterfaceAction checkedAction=
            java.util.Objects.requireNonNull(
                action,
                "action"
            );
        final String[] result={null};
        final boolean[] published={false};

        try{
            boolean worldOpen=
                checkedWorld
                    .withOpenLifecycleOwnership(
                        ()->
                            published[0]=
                                TradeService.publishCompetingRoot(
                                    checkedPlayer,
                                    ()->
                                        checkedWorld
                                            .withOpenPlayerMutationOwnershipIfCurrent(
                                                checkedPlayer,
                                                expectedGeneration,
                                                ()->
                                                    result[0]=
                                                        checkedAction.publish()
                                            )
                                )
                    );

            return worldOpen&&
                published[0]
                ?result[0]
                :null;
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IOException(
                "Monster Spawner root replacement failed",
                failure
            );
        }
    }

    static LocalSessionUiActionHandler.MonsterSpawnerDispatch
        dispatchMonsterSpawnerWidgetForCurrentSession(
            MonsterSpawnerUiFactory factory,
            World world,
            WorldPlayer player,
            long expectedGeneration,
            String canonicalUsername,
            LocalMonsterSpawnerUiHandler handler,
            int widget,
            ServerPacketWriter writer,
            String tag
        )throws IOException{
        return dispatchMonsterSpawnerWidgetForCurrentSession(
            factory,
            world,
            player,
            expectedGeneration,
            canonicalUsername,
            handler,
            widget,
            writer,
            tag,
            ()->true
        );
    }

    static LocalSessionUiActionHandler.MonsterSpawnerDispatch
        dispatchMonsterSpawnerWidgetForCurrentSession(
            MonsterSpawnerUiFactory factory,
            World world,
            WorldPlayer player,
            long expectedGeneration,
            String canonicalUsername,
            LocalMonsterSpawnerUiHandler handler,
            int widget,
            ServerPacketWriter writer,
            String tag,
            java.util.function.BooleanSupplier uiOpen
        )throws IOException{
        World checkedWorld=
            java.util.Objects.requireNonNull(
                world,
                "world"
            );
        WorldPlayer checkedPlayer=
            java.util.Objects.requireNonNull(
                player,
                "player"
            );
        LocalMonsterSpawnerUiHandler checkedHandler=
            java.util.Objects.requireNonNull(
                handler,
                "handler"
            );
        String username=
            PartyService.requireRef(
                canonicalUsername
            );
        java.util.Objects.requireNonNull(
            writer,
            "writer"
        );
        java.util.Objects.requireNonNull(
            tag,
            "tag"
        );
        java.util.function.BooleanSupplier checkedUiOpen=
            java.util.Objects.requireNonNull(
                uiOpen,
                "uiOpen"
            );

        if(!checkedHandler.isBoundToOwner(
                username
            )||
           !checkedHandler.isBoundTo(
                checkedWorld
            ))
            throw new IllegalArgumentException(
                "Monster Spawner widget handler differs from exact session context owner="+
                username
            );

        final LocalMonsterSpawnerUiHandler.Result[]
            committed={null};
        final boolean[] closedUi={false};

        try{
            boolean delivered=
                checkedWorld
                    .withOpenPlayerMutationOwnershipIfCurrent(
                        checkedPlayer,
                        expectedGeneration,
                        ()->{
                            if(!checkedUiOpen.getAsBoolean()){
                                closedUi[0]=true;
                                return;
                            }

                            committed[0]=
                                checkedHandler.handle(
                                    widget,
                                    writer
                                );

                            if(committed[0]!=null)
                                invokeMonsterSpawnerCommittedResult(
                                    factory,
                                    checkedWorld,
                                    checkedPlayer,
                                    username,
                                    committed[0],
                                    writer,
                                    tag
                                );
                        }
                    );

            if(!delivered)
                return LocalSessionUiActionHandler
                    .MonsterSpawnerDispatch
                    .rejected();

            if(closedUi[0])
                return LocalSessionUiActionHandler
                    .MonsterSpawnerDispatch
                    .closedUi();

            return LocalSessionUiActionHandler
                .MonsterSpawnerDispatch
                .admitted(
                    committed[0]
                );
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Monster Spawner widget transaction failed owner="+
                username+
                " widget="+
                widget,
                failure
            );
        }
    }

    static void forwardMonsterSpawnerUiResult(
        MonsterSpawnerUiFactory factory,
        World world,
        WorldPlayer player,
        long expectedGeneration,
        String canonicalUsername,
        LocalMonsterSpawnerUiHandler.Result result,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        if(factory==null)
            return;

        World checkedWorld=
            java.util.Objects.requireNonNull(
                world,
                "world"
            );
        WorldPlayer checkedPlayer=
            java.util.Objects.requireNonNull(
                player,
                "player"
            );
        String username=
            PartyService.requireRef(
                canonicalUsername
            );

        try{
            boolean delivered=
                checkedWorld
                    .withOpenPlayerMutationOwnershipIfCurrent(
                        checkedPlayer,
                        expectedGeneration,
                        ()->invokeMonsterSpawnerCommittedResult(
                            factory,
                            checkedWorld,
                            checkedPlayer,
                            username,
                            result,
                            writer,
                            tag
                        )
                    );

            if(!delivered)
                throw new IllegalStateException(
                    "Monster Spawner callback rejected by World/player ownership fence owner="+
                    username+
                    " expectedGeneration="+
                    expectedGeneration
                );
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Monster Spawner post-commit callback failed owner="+
                username+
                " status="+
                java.util.Objects.requireNonNull(
                    result,
                    "result"
                ).status,
                failure
            );
        }
    }

    private static void invokeMonsterSpawnerCommittedResult(
        MonsterSpawnerUiFactory factory,
        World world,
        WorldPlayer player,
        String canonicalUsername,
        LocalMonsterSpawnerUiHandler.Result result,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        if(factory==null)
            return;

        String username=
            PartyService.requireRef(
                canonicalUsername
            );
        LocalMonsterSpawnerUiHandler.Result checkedResult=
            java.util.Objects.requireNonNull(
                result,
                "result"
            );
        java.util.Objects.requireNonNull(
            writer,
            "writer"
        );
        java.util.Objects.requireNonNull(
            tag,
            "tag"
        );

        if(!checkedResult.session.ownerRef.equals(
                username
            ))
            throw new IllegalArgumentException(
                "Monster Spawner committed result owner differs from canonical session account expected="+
                username+
                " actual="+
                checkedResult.session.ownerRef
            );

        try{
            factory.onCommittedResult(
                java.util.Objects.requireNonNull(
                    world,
                    "world"
                ),
                java.util.Objects.requireNonNull(
                    player,
                    "player"
                ),
                username,
                checkedResult,
                writer,
                tag
            );
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Monster Spawner post-commit callback failed owner="+
                username+
                " status="+
                checkedResult.status,
                failure
            );
        }
    }

    static void notifyMonsterSpawnerSessionClosed(
        MonsterSpawnerUiFactory factory,
        World world,
        WorldPlayer player,
        long expectedGeneration,
        String canonicalUsername
    )throws Exception{
        if(factory==null)
            return;

        World checkedWorld=
            java.util.Objects.requireNonNull(
                world,
                "world"
            );
        WorldPlayer checkedPlayer=
            java.util.Objects.requireNonNull(
                player,
                "player"
            );
        String username=
            PartyService.requireRef(
                canonicalUsername
            );

        boolean delivered=
            checkedWorld
                .withOpenPlayerMutationOwnershipIfCurrent(
                    checkedPlayer,
                    expectedGeneration,
                    ()->factory.onSessionClosed(
                        checkedWorld,
                        checkedPlayer,
                        expectedGeneration,
                        username
                    )
                );

        if(!delivered)
            throw new IllegalStateException(
                "Monster Spawner session-close callback rejected by World/player ownership fence owner="+
                username+
                " expectedGeneration="+
                expectedGeneration
            );
    }

    static LocalMonsterSpawnerUiHandler
        resolveMonsterSpawnerUiAfterLogin(
            MonsterSpawnerUiFactory factory,
            World world,
            WorldPlayer player,
            String canonicalUsername
        )throws Exception{
        WorldPlayer checkedPlayer=
            java.util.Objects.requireNonNull(
                player,
                "player"
            );

        return resolveMonsterSpawnerUiAfterLogin(
            factory,
            world,
            checkedPlayer,
            checkedPlayer.generation(),
            canonicalUsername
        );
    }

    static LocalMonsterSpawnerUiHandler
        resolveMonsterSpawnerUiAfterLogin(
            MonsterSpawnerUiFactory factory,
            World world,
            WorldPlayer player,
            long expectedGeneration,
            String canonicalUsername
        )throws Exception{
        if(factory==null)
            return null;

        World checkedWorld=
            java.util.Objects.requireNonNull(
                world,
                "world"
            );
        WorldPlayer checkedPlayer=
            java.util.Objects.requireNonNull(
                player,
                "player"
            );
        String username=
            PartyService.requireRef(
                canonicalUsername
            );
        final LocalMonsterSpawnerUiHandler[] resolved={
            null
        };

        boolean delivered=
            checkedWorld
                .withOpenPlayerMutationOwnershipIfCurrent(
                    checkedPlayer,
                    expectedGeneration,
                    ()->{
                        LocalMonsterSpawnerUiHandler adapter=
                            factory.create(
                                checkedWorld,
                                checkedPlayer,
                                username
                            );

                        if(adapter==null)
                            return;

                        if(!adapter.isBoundToOwner(
                                username
                            ))
                            throw new IllegalArgumentException(
                                "Monster Spawner UI owner differs from canonical session account "+
                                username
                            );

                        if(!adapter.isBoundTo(
                                checkedWorld
                            ))
                            throw new IllegalArgumentException(
                                "Monster Spawner UI service belongs to another World account="+
                                username
                            );

                        resolved[0]=adapter;
                    }
                );

        if(!delivered)
            throw new IllegalStateException(
                "Monster Spawner UI factory rejected by World/player ownership fence owner="+
                username+
                " expectedGeneration="+
                expectedGeneration
            );

        return resolved[0];
    }

    @Override public void run() {
        String tag = "[session " + socket.getRemoteSocketAddress() + "] ";
        try (socket; InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            LoginFrame frame=LocalLoginTransport.readLogin(
                socket,
                in,
                out,
                tag
            );
            loginAlias = frame.username == null || frame.username.isEmpty() ? "localtest" : frame.username;
            System.out.println(tag + frame);
            if (frame.revision != 317) throw new IOException("expected protocol revision 317, got " + frame.revision);

            LocalSessionPlayerInitializer.Result playerInit=
                playerInitializer.initialize(loginAlias,tag);
            username=playerInit.username;
            persistentAccount=playerInit.persistentAccount;
            worldPlayerGeneration=playerInit.worldPlayerGeneration;
            worldRegistered=true;

            /*
             * Session-owned World commands are valid before the regular tick
             * target is attached, so they have an independent callback
             * lifetime. Activate immediately after exact player registration.
             */
            worldCommandGate.activate();

            LocalMonsterSpawnerUiHandler monsterSpawnerUi=
                resolveMonsterSpawnerUiAfterLogin(
                    monsterSpawnerUiFactory,
                    world,
                    worldPlayer,
                    worldPlayerGeneration,
                    username
                );

            if(monsterSpawnerUi!=null)
                uiActions.installMonsterSpawnerUiHandler(
                    monsterSpawnerUi
                );

            LocalLoginTransport.Ciphers loginCiphers=
                LocalLoginTransport.ciphers(frame);
            outboundPackets = new OutboundPacketQueue();
            ServerPacketWriter serverPackets =
                new ServerPacketWriter(
                    outboundPackets,
                    loginCiphers.serverToClient
                );
            sessionPackets=serverPackets;
            scenePublisher = new SceneUpdatePublisher(serverPackets,new SceneCoordinateContext(MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y,0));
            ClientPacketProbe clientPackets =
                new ClientPacketProbe(
                    in,
                    loginCiphers.clientToServer,
                    tag
                );
            System.out.println(tag+"BUILD "+BuildInfo.summary()+" world="+world.summary()+" npcDefinitions="+EffectiveNpcDefinitionRepository.count()+" miniPetDefinitions="+MiniPetDefinitionRepository.count());

            // Login response 2 is followed by the exact bytes consumed as Client.cT
            // and the client boolean flag. 205 passes both current privileged gate families
            // used by the native Spawn Tab/debug surfaces; server authority remains LOCAL only.
            LocalLoginTransport.writeLoginSuccess(out);
            System.out.println(tag + "LOGIN_SUCCESS_LOCAL rank=205 localDevAuthority=true flag=false account="+username+" loginAlias="+loginAlias+" persistent="+persistentAccount+" at " + Instant.now());

            socket.setSoTimeout(5_000);
            try {
                int first = clientPackets.readFirst185();
                if (first == 185 && bootstrap) {
                    bootstrapPublisher.publish(
                        serverPackets,
                        scenePublisher,
                        username,
                        persistentAccount,
                        tag
                    );
                    logRegionLoadBegin(
                        regionLoads.begin(
                            385,
                            436,
                            MovementState.REGION_BASE_X,
                            MovementState.REGION_BASE_Y,
                            "LOGIN_BOOTSTRAP"
                        ),
                        385,
                        436,
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        "LOGIN_BOOTSTRAP",
                        tag
                    );
                }
                if(bootstrap && runtimeBindings.context()==null){
                    runtimeBindings.register(
                        serverPackets,
                        tag,
                        worldPlayerGeneration
                    );
                }

                try {
                    socket.setSoTimeout(250);
                    while (in.available() > 0 && clientPackets.isAligned()) {
                        if (!clientPackets.readNextKnownPacket()) break;
                        processPendingOnWorld(clientPackets,serverPackets,tag);
                        if(logoutRequested) break;
                    }
                } catch (SocketTimeoutException ignored) {}
            } catch (SocketTimeoutException ignored) {
                System.out.println(tag + "no post-login packet within 5s; keeping local session open");
            }

            if(logoutRequested){
                requireLiveSessionWriter(
                    serverPackets
                );
                serverPackets.flush();
                drainOutbound(out);
                System.out.println(tag+"V5124_LOGOUT_SOCKET_END requested=true phase=PRE_TICK_ATTACH");
                return;
            }

            final long attachedGeneration=worldPlayerGeneration;

            /*
             * Activate before publication into World.tickTargets so there is
             * no attached-but-inactive window which could drop the first
             * legitimate callback. If attachment itself fails, roll the
             * session-local gate back immediately.
             */
            worldTickGate.activate();
            try{
                world.attachTickTarget(new WorldTickTarget(){
                    public EntityId ownerId(){return worldPlayer.id();}
                    public long ownerGeneration(){return attachedGeneration;}
                    public void onWorldTick(long tick,long nowMillis)throws Exception{LocalSession.this.onWorldTick(tick,nowMillis);}
                });
                worldTickAttached=true;
            }catch(Throwable attachFailure){
                worldTickGate.disableAndAwait();
                throw attachFailure;
            }
            requireLiveSessionWriter(
                serverPackets
            );
            serverPackets.flush();
            drainOutbound(out);
            System.out.println(tag+"V512_WORLD_TICK_ATTACH playerId="+worldPlayer.id()+" generation="+attachedGeneration+" worldTick="+world.clock().tick()+" members="+world.players().size());

            socket.setSoTimeout(100);
            while (true) {
                long now = System.currentTimeMillis();
                requireLiveSessionWriter(
                    serverPackets
                );
                if(outboundPackets.overflowed())throw new IOException("outbound packet queue overflowed");
                drainOutbound(out);

                try {
                    if (clientPackets.isAligned()) {

                        if (!clientPackets.readNextKnownPacket()) {
                            if (!clientPackets.isAligned()) continue;
                            break;
                        }
                        processPendingOnWorld(clientPackets,serverPackets,tag);
                        drainOutbound(out);
                        if(logoutRequested){ System.out.println(tag+"V5124_LOGOUT_SOCKET_END requested=true"); break; }
                    } else {
                        int b = in.read();
                        if (b < 0) break;
                        ByteArrayOutputStream raw = new ByteArrayOutputStream();
                        raw.write(b);
                        while (in.available() > 0 && raw.size() < 256) raw.write(in.read());
                        byte[] data = raw.toByteArray();
                        System.out.println(tag + "CLIENT_RAW_UNFRAMED bytes=" + data.length
                                         + " hex=" + ClientPacketProbe.hex(data, 96)
                                         + " reason=decoder-paused-after-unknown-opcode");
                    }
                } catch (SocketTimeoutException ignored) {}
            }
        } catch (Throwable t) {
            System.err.println(tag + "closed: " + t);
        } finally {
            /*
             * Stop session-owned World commands first. A command which is
             * already active keeps this session-local monitor until its whole
             * gameplay action exits; a queued/dequeued command entering later
             * observes inactive and becomes a no-op. No World/service lock is
             * held while teardown waits here.
             */
            worldCommandGate.disableAndAwait();

            if(worldTickAttached){
                /*
                 * Quiesce the LocalSession callback lifetime before any
                 * runtime binding is cleared. disableAndAwait() acquires only
                 * the session-local gate: it waits for an active callback to
                 * leave, then makes already-snapshotted late callbacks return
                 * without touching runtime/gameplay state. Release this gate
                 * before entering World/service teardown.
                 */
                worldTickGate.disableAndAwait();

                LocalSessionTeardown.run(
                    tag,
                    "DETACH_TICK_TARGET",
                    ()->{
                        try{
                            world.detachTickTarget(
                                worldPlayer.id(),
                                worldPlayerGeneration
                            );
                        }finally{
                            worldTickAttached=false;
                        }
                    }
                );
            }

            if(monsterSpawnerUiFactory!=null&&
               worldRegistered){
                LocalSessionTeardown.run(
                    tag,
                    "MONSTER_SPAWNER_SESSION_CLOSE",
                    ()->{
                        try{
                            notifyMonsterSpawnerSessionClosed(
                                monsterSpawnerUiFactory,
                                world,
                                worldPlayer,
                                worldPlayerGeneration,
                                username
                            );
                        }catch(RuntimeException failure){
                            throw failure;
                        }catch(Error failure){
                            throw failure;
                        }catch(Exception failure){
                            throw new IllegalStateException(
                                "Monster Spawner session-close callback failed owner="+
                                username,
                                failure
                            );
                        }
                    }
                );
            }

            LocalSessionTeardown.run(
                tag,
                "RUNTIME_BINDINGS_UNREGISTER",
                runtimeBindings::unregister
            );

            LocalSessionTeardown.run(
                tag,
                "DEV_PANEL_CLOSE",
                devPanelCoordinator::closeSession
            );

            LocalSessionTeardown.run(
                tag,
                "FINAL_ACCOUNT_SAVE",
                ()->saveAccountFinal(
                    tag,
                    "SESSION_END"
                )
            );

            if(worldRegistered){
                LocalSessionTeardown.run(
                    tag,
                    "WORLD_UNREGISTER",
                    ()->{
                        try{
                            boolean removed=
                                world.unregisterPlayer(
                                    worldPlayer,
                                    worldPlayerGeneration
                                );

                            System.out.println(
                                tag+
                                "V512_WORLD_UNREGISTER playerId="+
                                worldPlayer.id()+
                                " removed="+removed+
                                " members="+
                                world.players().size()+
                                " queuedCommands="+
                                world.commands().size()
                            );
                        }finally{
                            worldRegistered=false;
                        }
                    }
                );
            }
        }
    }

    private void handleHomeTeleportFromWidget(
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        LocalRegionDevCommandHandler.Result result=
            regionDevCommands.teleportHomeFromMagic(
                username,
                scenePublisher,
                writer
            );

        if(result.scenePublisher!=null)
            scenePublisher=result.scenePublisher;

        if(result.saveReason!=null)
            saveAccountQuiet(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+
            result.logText
        );
    }

    private void logRegionLoadBegin(
        RegionLoadLifecycle.Begin begin,
        int centerX,
        int centerY,
        int baseX,
        int baseY,
        String reason,
        String tag
    ){
        System.out.println(
            tag+
            "V5182_REGION_LOAD_BEGIN seq="+
            begin.sequence+
            " center="+centerX+","+centerY+
            " base="+baseX+","+baseY+
            " reason="+reason+
            " supersededPending="+
            begin.superseded+
            (begin.superseded
                ?" supersededSeq="+
                    begin.supersededSequence
                :"")+
            " authority=V308_RUNTIME_PROBE_PACKET73_LIFECYCLE"
        );
    }

    private void handleRegionLoadAck(
        String tag
    ){
        RegionLoadLifecycle.Completion completion=
            regionLoads.prepareComplete();

        if(!completion.matched){
            System.out.println(
                tag+
                "V5182_REGION_LOAD_ACK_UNMATCHED opcode=121"+
                " stateMutation=false"+
                " authority=V308_RUNTIME_PROBE_RS_CLIENT_BW"
            );
            return;
        }

        long now=
            System.currentTimeMillis();
        boolean packetCommitted=false;

        sessionPackets.beginBatch();

        try{
            worldTicks.completeRegionLoad(
                completion,
                sessionPackets,
                tag,
                now
            );

            sessionPackets.endBatch();
            packetCommitted=true;

            worldTicks
                .commitRegionStreamBatch();
            worldTicks
                .commitGroundPresentationBatch(
                    now
                );

            if(!regionLoads.commitCompletion(
                    completion
                ))
                throw new IllegalStateException(
                    "region ACK completion identity changed seq="+
                    completion.sequence
                );

            System.out.println(
                tag+
                "V5182_REGION_LOAD_COMPLETE seq="+
                completion.sequence+
                " center="+
                completion.centerX+","+
                completion.centerY+
                " base="+
                completion.baseX+","+
                completion.baseY+
                " reason="+
                completion.reason+
                " opcode=121"+
                " authority=V308_RUNTIME_PROBE_RS_CLIENT_BW"
            );
        }catch(IOException failure){
            if(!packetCommitted)
                abortRegionAckReplay(
                    failure
                );
            throw new IllegalStateException(
                "post-region-load scene replay failed seq="+
                completion.sequence+
                " reason="+completion.reason,
                failure
            );
        }catch(RuntimeException failure){
            if(!packetCommitted)
                abortRegionAckReplay(
                    failure
                );
            throw failure;
        }catch(Error failure){
            if(!packetCommitted)
                abortRegionAckReplay(
                    failure
                );
            throw failure;
        }
    }

    private void abortRegionAckReplay(
        Throwable primary
    ){
        try{
            sessionPackets.abortBatch();
        }catch(Throwable abortFailure){
            primary.addSuppressed(
                abortFailure
            );
        }

        try{
            worldTicks.abortRegionStreamBatch();
        }catch(Throwable restoreFailure){
            primary.addSuppressed(
                restoreFailure
            );
        }

        try{
            worldTicks.abortGroundPresentationBatch();
        }catch(Throwable groundFailure){
            primary.addSuppressed(
                groundFailure
            );
        }
    }

    private void processPendingOnWorld(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws Exception{
        world.submitAndWait(
            worldPlayer,
            worldPlayerGeneration,
            ()->worldCommandGate
                .runIfActiveAndWriterLive(
                    serverPackets,
                    ()->pendingRequests.drain(
                        clientPackets,
                        serverPackets,
                        tag
                    )
                ),
            5_000L
        );
    }

    static void requireLiveSessionWriter(
        ServerPacketWriter writer
    )throws IOException{
        if(writer!=null&&writer.terminal())
            throw new IOException(
                "terminal session packet writer"
            );
    }

    private void drainOutbound(OutputStream out)throws IOException{
        drainSessionOutbound(
            outboundPackets,
            sessionPackets,
            out,
            256*1024
        );
    }

    static int drainSessionOutbound(
        OutboundPacketQueue packets,
        ServerPacketWriter writer,
        OutputStream out,
        int maxBytesPerDrain
    )throws IOException{
        if(packets==null)
            return 0;

        try{
            return packets.drainTo(
                out,
                maxBytesPerDrain
            );
        }catch(IOException failure){
            /*
             * The queue -> socket handoff is no longer retractable once the
             * network OutputStream rejects a drain. Latch the exact session
             * writer before the failure escapes so an already-snapshotted
             * WorldPulse target stops at onWorldTick's terminal gate.
             * Normal LocalSession finally teardown still owns runtime/world
             * cleanup.
             */
            if(writer!=null)
                writer.markTerminal();
            throw failure;
        }
    }

    /** Existing certified per-player gameplay tick, now invoked only by the one shared WorldPulse. */
    private void onWorldTick(
        long worldTick,
        long now
    )throws Exception{
        worldTickGate.runIfActive(
            ()->onWorldTickActive(
                worldTick,
                now
            )
        );
    }

    private void onWorldTickActive(
        long worldTick,
        long now
    )throws Exception{
        sessionWorldTick=worldTick;
        if(!bootstrap||
           sessionPackets==null||
           sessionPackets.terminal())
            return;

        String tickTag=
            "[session "+socket.getRemoteSocketAddress()+"] ";

        ScenePublisherBatchSnapshot sceneBatch=
            ScenePublisherBatchSnapshot.capture(
                scenePublisher
            );

        boolean relayMaskBatchActive=
            SharedNpcWorldRelay.beginSourceMaskBatch(
                sessionPackets
            );
        boolean batchCommitted=false;
        boolean tickCompleted=false;

        try{
            sessionPackets.beginBatch();

            try{
                worldTicks.tick(
                    worldTick,
                    now,
                    sessionPackets,
                    tickTag
                );
                tickCompleted=true;
            }catch(Exception failure){
                abortWorldTickBatchAfterTickFailure(
                    sessionPackets,
                    failure
                );
                throw failure;
            }catch(Error failure){
                abortWorldTickBatchAfterTickFailure(
                    sessionPackets,
                    failure
                );
                throw failure;
            }

            endWorldTickBatch(
                sessionPackets
            );
            batchCommitted=true;
        }finally{
            if(batchCommitted){
                worldTicks
                    .clearNoMovementSemanticTailAfterCommit();

                worldTicks
                    .commitRegionStreamBatch();

                if(relayMaskBatchActive)
                    SharedNpcWorldRelay
                        .commitSourceMaskBatch(
                            sessionPackets
                        );

                worldTicks
                    .commitHomePresentationBatch();
                worldTicks
                    .commitGroundPresentationBatch(
                        System.currentTimeMillis()
                    );

                worldTicks
                    .commitCarriedPresentationBatch(
                        System.currentTimeMillis()
                    );

                if(tickCompleted){
                    worldTicks
                        .settleDeferredMovementAfterWorldTick(
                            sessionPackets,
                            tickTag
                        );
                    worldTicks
                        .settleDeferredPetChargeIncrementAfterWorldTick(
                            sessionPackets,
                            tickTag
                        );
                    worldTicks
                        .settleDeferredRespawnAfterWorldTick(
                            sessionPackets,
                            tickTag
                        );
                    worldTicks
                        .settleDeferredBankInteractionsAfterWorldTick(
                            System.currentTimeMillis(),
                            sessionPackets,
                            tickTag
                        );
                    worldTicks
                        .settleDeferredMakeoverInteractionsAfterWorldTick(
                            System.currentTimeMillis(),
                            sessionPackets,
                            tickTag
                        );
                    worldTicks
                        .settleDeferredGroundTakeAfterWorldTick(
                            System.currentTimeMillis(),
                            sessionPackets,
                            tickTag
                        );
                    worldTicks
                        .settleDeferredPetPickupAfterWorldTick(
                            sessionPackets,
                            tickTag,
                            System.currentTimeMillis()
                        );
                    worldTicks
                        .settleDeferredPetEffectTimeoutAfterWorldTick(
                            sessionPackets,
                            tickTag,
                            worldTick
                        );
                }else{
                    worldTicks
                        .abortDeferredMovementAfterWorldTick();
                    worldTicks
                        .abortDeferredRespawnAfterWorldTick();
                    worldTicks
                        .abortDeferredBankInteractionsAfterWorldTick();
                    worldTicks
                        .abortDeferredMakeoverInteractionsAfterWorldTick();
                    worldTicks
                        .abortDeferredGroundTakeAfterWorldTick();
                    worldTicks
                        .abortDeferredPetPickupAfterWorldTick();
                    worldTicks
                        .abortDeferredPetEffectTimeoutAfterWorldTick();
                    worldTicks
                        .abortDeferredPetChargeIncrementAfterWorldTick(
                            sessionPackets
                        );
                }
            }else{
                if(relayMaskBatchActive)
                    SharedNpcWorldRelay
                        .abortSourceMaskBatch(
                            sessionPackets
                        );

                worldTicks
                    .abortDeferredMovementAfterWorldTick();

                worldTicks
                    .abortRegionStreamBatch();

                scenePublisher=
                    sceneBatch.abortAndRestore();

                worldTicks
                    .abortHomePresentationBatch();
                worldTicks
                    .abortGroundPresentationBatch();

                worldTicks
                    .abortCarriedPresentationBatch();
                worldTicks
                    .abortDeferredRespawnAfterWorldTick();
                worldTicks
                    .abortDeferredBankInteractionsAfterWorldTick();
                worldTicks
                    .abortDeferredMakeoverInteractionsAfterWorldTick();
                worldTicks
                    .abortDeferredGroundTakeAfterWorldTick();
                worldTicks
                    .abortDeferredPetPickupAfterWorldTick();
                worldTicks
                    .abortDeferredPetEffectTimeoutAfterWorldTick();
                worldTicks
                    .abortDeferredPetChargeIncrementAfterWorldTick(
                        sessionPackets
                    );

                worldTicks
                    .retireAfterFailedNoMovementSemanticTail(
                        sessionPackets
                    );
            }
        }
    }

    static final class ScenePublisherBatchSnapshot {
        private final SceneUpdatePublisher publisher;
        private final SceneCoordinateContext.Snapshot context;

        private ScenePublisherBatchSnapshot(
            SceneUpdatePublisher publisher,
            SceneCoordinateContext.Snapshot context
        ){
            this.publisher=publisher;
            this.context=context;
        }

        static ScenePublisherBatchSnapshot capture(
            SceneUpdatePublisher publisher
        ){
            return new ScenePublisherBatchSnapshot(
                publisher,
                publisher==null
                    ?null
                    :publisher.context().snapshot()
            );
        }

        SceneUpdatePublisher abortAndRestore(){
            if(publisher!=null)
                publisher.context().restore(
                    context
                );

            return publisher;
        }
    }

    static void endWorldTickBatch(
        ServerPacketWriter writer
    )throws IOException{
        try{
            writer.endBatch();
        }catch(IOException failure){
            abortWorldTickBatchAfterCommitFailure(
                writer,
                failure
            );
            throw failure;
        }catch(RuntimeException failure){
            abortWorldTickBatchAfterCommitFailure(
                writer,
                failure
            );
            throw failure;
        }catch(Error failure){
            abortWorldTickBatchAfterCommitFailure(
                writer,
                failure
            );
            throw failure;
        }
    }

    static void abortWorldTickBatchAfterTickFailure(
        ServerPacketWriter writer,
        Throwable primary
    ){
        try{
            writer.abortBatch();
        }catch(Throwable abortFailure){
            primary.addSuppressed(
                abortFailure
            );
        }
    }

    private static void abortWorldTickBatchAfterCommitFailure(
        ServerPacketWriter writer,
        Throwable primary
    ){
        try{
            writer.abortBatch();
        }catch(Throwable abortFailure){
            primary.addSuppressed(
                abortFailure
            );
        }
    }

    private void resetPetFollowRuntime(){
        petRealtime.resetFollowRuntime();
    }

    private void resetPetFollowDeadline(){
        petRealtime.resetFollowDeadline();
    }

    private long petFollowDeadline(){
        return petRealtime.followDeadline();
    }

    private void setPetFollowDeadline(long value){
        petRealtime.setFollowDeadline(value);
    }

    private void ensurePetFollowScheduled(long now){
        petRealtime.ensureFollowScheduled(now);
    }

    private void ensurePetTestSequenceScheduled(long now){
        petRealtime.ensureTestSequenceScheduled(now);
    }

    private void applyPetDialogResult(
        LocalPetInventoryDialogHandler.Result result,
        String tag
    ){
        if(result==null)return;
        if(result.keyAction==LocalPetInventoryDialogHandler.KeyAction.PUBLISH_2482_2485)
            dialogNumberKeys.publish(2482,2483,2484,2485);
        if(result.saveReason!=null)saveAccountQuiet(tag,result.saveReason);
        if(result.logText!=null)System.out.println(tag+result.logText);
        if(result.keyAction==LocalPetInventoryDialogHandler.KeyAction.CLEAR_AFTER_LOG)
            dialogNumberKeys.clear();
    }

    static int chebyshev(int x0,int y0,int x1,int y1) {
        return LocalMovementRequestHandler.chebyshev(
            x0,
            y0,
            x1,
            y1
        );
    }

    /** Legacy package-level test seam; ownership now lives in LocalPetDropPickupHandler. */
    static boolean isPetPickupAction(
        NpcAction a,
        NpcEntity pet,
        PetState petState
    ){
        return LocalPetDropPickupHandler.isPetPickupAction(
            a,
            pet,
            petState
        );
    }

    static boolean isCombatAttackAction(
        NpcAction action,
        NpcEntity clicked
    ){
        return LocalPendingRequestDispatcher.isCombatAttackAction(
            action,
            clicked
        );
    }

    private boolean scopesightActive(){
        return petState.active() && petState.itemId()==ScopesightPetProfile.ITEM_ID && petState.npcId()==ScopesightPetProfile.NPC_ID;
    }

    /** Apply/unwind only the maintained Scopesight fixture values and publish changed skills. */
    private int syncScopesightPassive(ServerPacketWriter serverPackets) throws IOException {
        int changed=playerState.syncScopesightMaintenance(scopesightActive());
        publishSkillMask(changed,serverPackets);
        return changed;
    }

    private void publishSkillMask(int mask,ServerPacketWriter serverPackets) throws IOException {
        for(int skill=0;skill<PlayerState.COMBAT_SKILL_COUNT;skill++)
            if((mask&(1<<skill))!=0)
                serverPackets.fixed(134,BootstrapPackets.skill134(skill,playerState.xp(skill),playerState.currentLevel(skill)));
    }

    private WorldPlayerPersistence.SaveTicket requestAccountSave(
        String tag,
        String reason
    )throws Exception{
        if(!persistentAccount)
            return null;

        if(world.pulse().inExecutionContext())
            return world.persistence().captureAndSave(
                username,
                worldPlayer,
                worldPlayerGeneration,
                petAccessoryState.activeItem(),
                tag,
                reason
            );

        final java.util.concurrent.atomic.AtomicReference<
            WorldPlayerPersistence.SaveTicket
        > ticket=
            new java.util.concurrent.atomic.AtomicReference<>();

        world.submitAndWait(
            worldPlayer,
            worldPlayerGeneration,
            ()->ticket.set(
                world.persistence().captureAndSave(
                    username,
                    worldPlayer,
                    worldPlayerGeneration,
                    petAccessoryState.activeItem(),
                    tag,
                    reason
                )
            ),
            5_000L
        );

        WorldPlayerPersistence.SaveTicket result=
            ticket.get();

        if(result==null)
            throw new IllegalStateException(
                "world save capture produced no ticket"
            );

        return result;
    }

    private WorldPlayerPersistence.SaveTicket
        captureFinalSaveAndUnregister(
            String tag,
            String reason,
            WorldPlayerPersistence.FinalSaveReservation
                reservation
        )throws Exception{
        if(!persistentAccount||
           !worldRegistered)
            return null;

        if(reservation==null)
            throw new NullPointerException(
                "final save reservation"
            );

        if(world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "final session teardown cannot run on World execution context"
            );

        final java.util.concurrent.atomic.AtomicReference<
            WorldPlayerPersistence.SaveTicket
        > ticket=
            new java.util.concurrent.atomic.AtomicReference<>();

        final java.util.concurrent.atomic.AtomicBoolean
            removed=
                new java.util.concurrent.atomic.AtomicBoolean();

        java.util.concurrent.CompletableFuture<Void>
            finalWorldAction=
                world.submit(
                    worldPlayer,
                    worldPlayerGeneration,
                    ()->{
                        WorldPlayerPersistence.CapturedSave
                            finalCapture=
                                world.persistence()
                                    .captureDeferredFinalSave(
                                        username,
                                        worldPlayer,
                                        worldPlayerGeneration,
                                        petAccessoryState.activeItem(),
                                        tag,
                                        reason
                                    );

                        boolean unregistered=
                            world.unregisterPlayer(
                                worldPlayer,
                                worldPlayerGeneration
                            );

                        if(!unregistered){
                            world.persistence()
                                .releaseCheckpointSuppression(
                                    worldPlayer.id(),
                                    worldPlayerGeneration
                                );

                            throw new IllegalStateException(
                                "final save capture could not unregister exact player generation id="+
                                worldPlayer.id()+
                                " generation="+
                                worldPlayerGeneration
                            );
                        }

                        removed.set(
                            true
                        );

                        /*
                         * Publish while the exact World command still owns the
                         * capture/retirement transition. A caller-side timeout
                         * therefore cannot invalidate a snapshot that this
                         * already-started action may still commit.
                         */
                        ticket.set(
                            reservation.publish(
                                finalCapture
                            )
                        );
                    }
                );

        /*
         * Once submitted, the command future owns reservation failure
         * settlement. If outer teardown unregisters first, command cancellation
         * aborts the reserved worker slot. If this action wins, it publishes the
         * final snapshot before completing successfully.
         */
        finalWorldAction.whenComplete(
            (ignored,failure)->{
                if(failure!=null)
                    reservation.abort(
                        failure
                    );
            }
        );

        try{
            finalWorldAction.get(
                5,
                java.util.concurrent.TimeUnit.SECONDS
            );
        }catch(java.util.concurrent.TimeoutException pending){
            /*
             * Do not abort here. submit/get timeout does not cancel an already
             * dequeued World command. Outer teardown will either unregister
             * first (causing command failure -> reservation abort) or wait for
             * this action to retire+publish the exact generation.
             */
            System.err.println(
                tag+
                "V5123_ACCOUNT_FINAL_WORLD_ACTION_PENDING reason="+
                reason+
                " playerId="+
                worldPlayer.id()+
                " generation="+
                worldPlayerGeneration
            );
            return null;
        }catch(java.util.concurrent.ExecutionException failure){
            Throwable cause=
                failure.getCause();

            if(cause instanceof Exception)
                throw (Exception)cause;
            if(cause instanceof Error)
                throw (Error)cause;

            throw new RuntimeException(
                cause
            );
        }

        if(!removed.get())
            throw new IllegalStateException(
                "world final-save teardown did not remove player generation"
            );

        WorldPlayerPersistence.SaveTicket result=
            ticket.get();

        if(result==null)
            throw new IllegalStateException(
                "world final-save teardown produced no published save ticket"
            );

        /*
         * World ownership ended and the captured final save was published in
         * the same exact-player action. The outer finally must not attempt a
         * second unregister.
         */
        worldRegistered=false;

        return result;
    }

    private void saveAccountQuiet(
        String tag,
        String reason
    ){
        if(!persistentAccount)
            return;

        try{
            requestAccountSave(
                tag,
                reason
            );
        }catch(Throwable e){
            System.err.println(
                tag+
                "V5123_ACCOUNT_SAVE_FAILED reason="+
                reason+
                " profile="+username+
                " repository="+
                world.persistence().repositoryName()+
                " stage=WORLD_CAPTURE_OR_ENQUEUE"+
                " error="+e
            );
        }
    }

    private void saveAccountFinal(
        String tag,
        String reason
    ){
        if(!persistentAccount||
           !worldRegistered)
            return;

        WorldPlayerPersistence.FinalSaveReservation
            reservation=null;

        try{
            /*
             * Reserve persistence FIFO position before final capture. Queue
             * saturation may wait here, off the World thread, while the player
             * remains live so every later mutation is included in the eventual
             * snapshot.
             */
            reservation=
                world.persistence()
                    .reserveFinalSaveWithBackpressure(
                        worldPlayer,
                        worldPlayerGeneration,
                        5_000L
                    );

            /*
             * Capture, exact-generation unregister and reservation publication
             * are one World-owned action. A bounded caller wait may time out,
             * but reservation settlement then remains owned by that command
             * future rather than being aborted underneath an in-flight action.
             */
            WorldPlayerPersistence.SaveTicket ticket=
                captureFinalSaveAndUnregister(
                    tag,
                    reason,
                    reservation
                );

            if(ticket==null)
                return;

            ticket.completion.get(
                5,
                java.util.concurrent.TimeUnit.SECONDS
            );
        }catch(Throwable e){
            if(reservation!=null)
                reservation.abort(
                    e
                );

            System.err.println(
                tag+
                "V5123_ACCOUNT_FINAL_SAVE_WAIT_FAILED reason="+
                reason+
                " profile="+username+
                " repository="+
                world.persistence().repositoryName()+
                " error="+e
            );
        }
    }



    // LocalDevPanelWidgetHandler is constructed before the coordinator so its
    // numeric-prompt callback enters through this one deferred forwarding seam.
    private void promptDevPanelAmount(
        DevControlCenter.PendingAmount pending,
        ServerPacketWriter writer
    )throws IOException{
        devPanelCoordinator.promptAmount(
            pending,
            writer
        );
    }

    private void publishOpponentOverlay(NpcEntity target,ServerPacketWriter w,String tag,String reason)throws IOException{
        if(target==null)return;
        EffectiveNpcDefinitionRepository.Def d=EffectiveNpcDefinitionRepository.get(target.definitionId);
        String name=d==null||d.name==null||d.name.trim().isEmpty()?"NPC "+target.definitionId:d.name.replace("@gre@","").replace("@red@","");
        String payload=name+"/255/255";
        w.varShort(126,BootstrapPackets.widgetText126(25,payload));
        System.out.println(tag+"V5128_OPPONENT_OVERLAY key=25 value=\""+payload+"\" reason="+reason+" authority=EXACT_CLIENT_KEY25 hp=IMMORTAL_DUMMY_FIXTURE");
    }
    private void clearOpponentOverlay(ServerPacketWriter w,String tag,String reason)throws IOException{
        // R2.8 sent key25 with an empty string. Live runtime showed the client
        // disconnecting immediately after that malformed clear. Key25's exact
        // payload grammar is name/currentHp/maxHp, so suppress the unsafe empty
        // clear until an exact production clear sentinel is recovered. A new target
        // safely overwrites key25.
        System.out.println(tag+"V5129_OPPONENT_OVERLAY_CLEAR_SUPPRESSED key=25 reason="+reason+
            " guard=EMPTY_PAYLOAD_CLIENT_DISCONNECT exactClearSentinel=UNRESOLVED");
    }


}
