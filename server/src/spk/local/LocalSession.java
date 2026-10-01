package spk.local;

import java.io.*;
import java.net.*;
import java.time.Instant;

final class LocalSession implements Runnable {
    private final Socket socket;
    private final boolean bootstrap;
    private final boolean movementEnabled;
    private final World world;
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
    private String username = AccountStore.CANONICAL_USERNAME;
    private String loginAlias = "localtest";
    private boolean persistentAccount;
    /** Persisted semantic global pet accessory. 0 means none. */
    private final PetAccessoryState petAccessoryState;
    private volatile boolean logoutRequested;

    LocalSession(Socket socket, boolean bootstrap) { this(socket, bootstrap, false, World.shared()); }
    LocalSession(Socket socket, boolean bootstrap, boolean movementEnabled) { this(socket, bootstrap, movementEnabled, World.shared()); }
    LocalSession(Socket socket, boolean bootstrap, boolean movementEnabled, World world) {
        VoidglassR3CustomContent.ensureRuntimePetMapping();
        this.socket = socket;
        this.bootstrap = bootstrap;
        this.movementEnabled = movementEnabled;
        this.world = java.util.Objects.requireNonNull(world,"world");
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
                    LocalSession.this.devPanelCoordinator.open(
                        DevControlCenter.Page.MAIN,
                        writer
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

                @Override public void requestLogout(){
                    LocalSession.this.logoutRequested=true;
                }
            });
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

                @Override public int syncScopesightPassive(
                    ServerPacketWriter writer
                )throws IOException{
                    return LocalSession.this.syncScopesightPassive(
                        writer
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
            (tag,reason)->LocalSession.this.saveAccountQuiet(
                tag,
                reason
            )
        );
        if (movementEnabled && !bootstrap) throw new IllegalArgumentException("movement requires bootstrap");
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
                serverPackets.flush();
                drainOutbound(out);
                System.out.println(tag+"V5124_LOGOUT_SOCKET_END requested=true phase=PRE_TICK_ATTACH");
                return;
            }

            final long attachedGeneration=worldPlayerGeneration;
            world.attachTickTarget(new WorldTickTarget(){
                public EntityId ownerId(){return worldPlayer.id();}
                public long ownerGeneration(){return attachedGeneration;}
                public void onWorldTick(long tick,long nowMillis)throws Exception{LocalSession.this.onWorldTick(tick,nowMillis);}
            });
            worldTickAttached=true;
            serverPackets.flush();
            drainOutbound(out);
            System.out.println(tag+"V512_WORLD_TICK_ATTACH playerId="+worldPlayer.id()+" generation="+attachedGeneration+" worldTick="+world.clock().tick()+" members="+world.players().size());

            socket.setSoTimeout(100);
            while (true) {
                long now = System.currentTimeMillis();
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
            if(worldTickAttached){
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
            regionLoads.complete();

        if(!completion.matched){
            System.out.println(
                tag+
                "V5182_REGION_LOAD_ACK_UNMATCHED opcode=121"+
                " stateMutation=false"+
                " authority=V308_RUNTIME_PROBE_RS_CLIENT_BW"
            );
            return;
        }

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

        try{
            worldTicks.completeRegionLoad(
                completion,
                sessionPackets,
                tag,
                System.currentTimeMillis()
            );
        }catch(IOException e){
            throw new IllegalStateException(
                "post-region-load scene replay failed seq="+
                completion.sequence+
                " reason="+completion.reason,
                e
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
            ()->pendingRequests.drain(
                clientPackets,
                serverPackets,
                tag
            ),
            5_000L
        );
    }

    private void drainOutbound(OutputStream out)throws IOException{
        if(outboundPackets!=null)outboundPackets.drainTo(out,256*1024);
    }

    /** Existing certified per-player gameplay tick, now invoked only by the one shared WorldPulse. */
    private void onWorldTick(
        long worldTick,
        long now
    )throws Exception{
        sessionWorldTick=worldTick;
        if(!bootstrap||sessionPackets==null)return;

        sessionPackets.beginBatch();
        try{
            worldTicks.tick(
                worldTick,
                now,
                sessionPackets,
                "[session "+socket.getRemoteSocketAddress()+"] "
            );
        }finally{
            sessionPackets.endBatch();
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

    private WorldPlayerPersistence.CapturedSave
        captureAccountSaveDeferred(
            String tag,
            String reason
        )throws Exception{
        if(!persistentAccount)
            return null;

        if(world.pulse().inExecutionContext())
            return world.persistence()
                .captureDeferredFinalSave(
                    username,
                    worldPlayer,
                    worldPlayerGeneration,
                    petAccessoryState.activeItem(),
                    tag,
                    reason
                );

        final java.util.concurrent.atomic.AtomicReference<
            WorldPlayerPersistence.CapturedSave
        > captured=
            new java.util.concurrent.atomic.AtomicReference<>();

        world.submitAndWait(
            worldPlayer,
            worldPlayerGeneration,
            ()->captured.set(
                world.persistence()
                    .captureDeferredFinalSave(
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

        WorldPlayerPersistence.CapturedSave result=
            captured.get();

        if(result==null)
            throw new IllegalStateException(
                "world final-save capture produced no snapshot"
            );

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

        try{
            WorldPlayerPersistence.CapturedSave captured=
                captureAccountSaveDeferred(
                    tag,
                    reason
                );

            if(captured==null)
                return;

            WorldPlayerPersistence.SaveTicket ticket=
                world.persistence()
                    .submitCapturedWithBackpressure(
                        captured,
                        5_000L
                    );

            ticket.completion.get(
                5,
                java.util.concurrent.TimeUnit.SECONDS
            );
        }catch(Throwable e){
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
