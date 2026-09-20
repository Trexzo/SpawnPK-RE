package spk.local;

import java.io.*;
import java.net.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

final class LocalSession implements Runnable {
    private static final long SERVER_SEED = 0x0123456789ABCDEFL;
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
    private final PrayerState prayers;
    private final MagicState magic;
    private final CombatStyleState combatStyles;
    private final PetEffectState petEffects;
    private final MiniPetService miniPets;
    private final VoidglassPetState voidglass = new VoidglassPetState();
    private final DevAuthorityWorkbench dev = new DevAuthorityWorkbench();
    private final PlayerPresentationService playerPresentation = new PlayerPresentationService(dev);
    private final NpcRegistry npcs = new NpcRegistry(dev);
    private final HomeWorldRuntimePlan homeWorld = new HomeWorldRuntimePlan();
    private final CombatEngine combat = new CombatEngine(dev);
    /** Engine R5 native Item Library server-side authority projection. */
    private final NativeItemLibraryService itemLibrary = new NativeItemLibraryService();
    private final LocalDiagnosticCommandHandler diagnosticCommands;
    private final LocalPrayerMagicCommandHandler prayerMagicCommands;
    private final LocalDevWorldCommandHandler devWorldCommands;
    private final LocalMiniPetCommandHandler miniPetCommands;
    private final LocalCosmeticCommandHandler cosmeticCommands;
    private final LocalCompColorsCommandHandler compColorsCommands;
    private final LocalItemSpawnCommandHandler itemSpawnCommands;
    private final LocalNurseCommandHandler nurseCommands;
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
    private final LocalRegionStreamHandler regionStreams;
    private final LocalWorldTickCoordinator worldTicks;
    private final LocalPendingRequestDispatcher pendingRequests;
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
    private final PetAccessoryState petAccessoryState = new PetAccessoryState();
    /** Engine R3 per-view remote-player synchronization context. */
    private Player81WorldSync.Context player81Sync;
    private volatile boolean logoutRequested;

    LocalSession(Socket socket, boolean bootstrap) { this(socket, bootstrap, false, World.shared()); }
    LocalSession(Socket socket, boolean bootstrap, boolean movementEnabled) { this(socket, bootstrap, movementEnabled, World.shared()); }
    LocalSession(Socket socket, boolean bootstrap, boolean movementEnabled, World world) {
        VoidglassR3CustomContent.ensureRuntimePetMapping();
        this.socket = socket;
        this.bootstrap = bootstrap;
        this.movementEnabled = movementEnabled;
        this.world = java.util.Objects.requireNonNull(world,"world");
        this.worldPlayer = new WorldPlayer();
        this.movement = worldPlayer.movement();
        this.bank = worldPlayer.bank();
        this.equipment = worldPlayer.equipment();
        this.petState = worldPlayer.petState();
        this.playerState = worldPlayer.playerState();
        this.prayers = worldPlayer.prayers();
        this.magic = worldPlayer.magic();
        this.combatStyles = worldPlayer.combatStyles();
        this.petEffects = worldPlayer.petEffects();
        this.miniPets = worldPlayer.miniPets();
        this.diagnosticCommands = new LocalDiagnosticCommandHandler(
            world,equipment,movement,prayers,magic,combatStyles,itemLibrary);
        this.prayerMagicCommands = new LocalPrayerMagicCommandHandler(prayers,magic);
        this.devWorldCommands = new LocalDevWorldCommandHandler(world,movement);
        this.miniPetCommands = new LocalMiniPetCommandHandler(miniPets,petState,npcs,movement);
        this.cosmeticCommands = new LocalCosmeticCommandHandler(bank,equipment,playerState,playerPresentation);
        this.compColorsCommands = new LocalCompColorsCommandHandler(playerState,equipment,playerPresentation);
        this.itemSpawnCommands = new LocalItemSpawnCommandHandler(bank);
        this.nurseCommands = new LocalNurseCommandHandler(playerState,movement);
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
        this.bankObjectHandler = new LocalBankObjectInteractionHandler(bank,movement);
        this.routedNpcHandler = new LocalRoutedNpcInteractionHandler(
            npcs,bank,movement);
        this.genericInteractionHandler = new LocalGenericInteractionHandler();
        this.playerInteractions = new LocalPlayerInteractionHandler(
            world,worldPlayer,movement,equipment);
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
            combat,equipment,combatStyles,npcs,petRuntimeCommands);
        this.regionDevCommands = new LocalRegionDevCommandHandler(
            world,
            worldPlayer,
            movement,
            playerInteractions,
            combat,
            npcs,
            petState,
            homeWorld,
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
            nurseCommands,
            voidglassCommands,
            petRuntimeCommands,
            compColorsCommands,
            combatCommands,
            petCompatibilityCommands,
            itemSpawnCommands,
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

                @Override public boolean scopesightActive(){
                    return LocalSession.this.scopesightActive();
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
                    return LocalSession.this.player81Sync;
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
                    return LocalSession.this.player81Sync;
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
        if (movementEnabled && !bootstrap) throw new IllegalArgumentException("movement requires bootstrap");
    }

    @Override public void run() {
        String tag = "[session " + socket.getRemoteSocketAddress() + "] ";
        try (socket; InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            if (!socket.getInetAddress().isLoopbackAddress()) throw new SecurityException("non-loopback peer refused");
            socket.setSoTimeout(30_000);

            byte[] pre = Binary.readExactly(in, 2);
            int requestType = pre[0] & 0xff;
            int userHash5 = pre[1] & 0xff;
            if (requestType != 14) throw new IOException("expected prelogin 14, got " + requestType);
            System.out.println(tag + "prelogin ok type=14 userHash5=" + userHash5);

            out.write(new byte[8]);
            out.write(0);
            Binary.put64(out, SERVER_SEED);
            out.flush();

            int loginType = in.read();
            int outerLength = in.read();
            if (loginType < 0 || outerLength < 0) throw new EOFException("login header EOF");
            byte[] payload = Binary.readExactly(in, outerLength);
            LoginFrame frame = LoginFrame.parse(loginType, payload);
            loginAlias = frame.username == null || frame.username.isEmpty() ? "localtest" : frame.username;
            System.out.println(tag + frame);
            if (frame.revision != 317) throw new IOException("expected protocol revision 317, got " + frame.revision);

            // v5.12.3 two-profile selection/load remains byte/state compatible,
            // but filesystem/profile orchestration now lives outside the socket session.
            LocalAccountLifecycle.Selection account=LocalAccountLifecycle.select(world,loginAlias,tag);
            username=account.username;
            persistentAccount=account.persistent;
            LocalAccountLifecycle.LoadResult accountLoad=LocalAccountLifecycle.load(
                account,bank,equipment,movement,petState,playerState,PetAccessoryAuthority::isAccessory,tag);
            petAccessoryState.setActiveItem(accountLoad.accessoryItem);

            // One-time migration from the superseded LocalLab bug that stored native icons in AMMO.
            if(!playerState.cosmetic().active() && ItemCatalog.isNativePlayerIcon(equipment.itemAt(EquipmentSlot.AMMO))){
                int legacyIcon=equipment.unequip(EquipmentSlot.AMMO); playerState.cosmetic().set(legacyIcon);
                System.out.println(tag+"V511_COSMETIC_MIGRATION legacyAmmoIcon="+legacyIcon+" -> dedicatedBs cosmetic=true ammoCleared=true");
            }
            playerState.syncEquipmentPresentation(equipment);
            if(petState.active()) {
                // Mapping data is authority; reconcile persisted item->NPC pairs so a
                // corrected color/pet definition takes effect immediately after upgrade.
                PetDefinitionRepository.Def persistedDef=PetDefinitionRepository.get(petState.itemId());
                if(persistedDef!=null && persistedDef.npcId!=petState.npcId()){
                    int oldNpc=petState.npcId(); petState.activate(persistedDef);
                    System.out.println(tag+"V59_PET_MAPPING_RECONCILE item="+petState.itemId()+" npc="+oldNpc+"->"+petState.npcId()+" provenance="+persistedDef.provenance);
                }
                petEffects.onPetChanged(petState.itemId(),petState.npcId());
            }
            // Scopesight is a maintained-stat pet. Apply its baseline before the
            // initial packet-134 skill publication so login starts coherent.
            playerState.syncScopesightMaintenance(scopesightActive());

            worldPlayerGeneration=world.registerPlayer(worldPlayer,username);
            worldRegistered=true;
            world.start();
            System.out.println(tag+"V512_WORLD_REGISTER playerId="+worldPlayer.id()+" generation="+worldPlayerGeneration+" username="+username+" members="+world.players().size()+" worldIdentity="+System.identityHashCode(world));

            int[] outboundSeeds = frame.isaacSeeds.clone();
            int[] inboundSeeds = frame.isaacSeeds.clone();
            for (int i = 0; i < inboundSeeds.length; i++) inboundSeeds[i] += 50;
            IsaacCipher clientToServer = new IsaacCipher(outboundSeeds);
            IsaacCipher serverToClient = new IsaacCipher(inboundSeeds);
            outboundPackets = new OutboundPacketQueue();
            ServerPacketWriter serverPackets = new ServerPacketWriter(outboundPackets, serverToClient);
            sessionPackets=serverPackets;
            scenePublisher = new SceneUpdatePublisher(serverPackets,new SceneCoordinateContext(MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y,0));
            ClientPacketProbe clientPackets = new ClientPacketProbe(in, clientToServer, tag);
            System.out.println(tag+"BUILD "+BuildInfo.summary()+" world="+world.summary()+" npcDefinitions="+EffectiveNpcDefinitionRepository.count()+" miniPetDefinitions="+MiniPetDefinitionRepository.count());

            // Login response 2 is followed by the exact bytes consumed as Client.cT
            // and the client boolean flag. 205 passes both current privileged gate families
            // used by the native Spawn Tab/debug surfaces; server authority remains LOCAL only.
            out.write(2); out.write(205); out.write(0); out.flush();
            System.out.println(tag + "LOGIN_SUCCESS_LOCAL rank=205 localDevAuthority=true flag=false account="+username+" loginAlias="+loginAlias+" persistent="+persistentAccount+" at " + Instant.now());

            socket.setSoTimeout(5_000);
            try {
                int first = clientPackets.readFirst185();
                if (first == 185 && bootstrap) {
                    BootstrapPackets.send(serverPackets, username, equipment.appearanceItems(), movement.runEnergy(), playerState);
                    // R2.13 account-position restore. BootstrapPackets retains the certified
                    // HOME spawn bootstrap; an immediate no-appearance teleport in the same
                    // outbound batch projects a persisted authoritative tile without changing
                    // the pinned bootstrap implementation or appearance semantics.
                    if(movement.x()!=MovementState.INITIAL_X || movement.y()!=MovementState.INITIAL_Y || movement.plane()!=0){
                        int localX=movement.x()-MovementState.REGION_BASE_X;
                        int localY=movement.y()-MovementState.REGION_BASE_Y;
                        serverPackets.varShort(81,BootstrapPackets.player81TeleportNoAppearance(movement.plane(),localY,localX));
                        System.out.println(tag+"V51214_POSITION_RESTORE world="+movement.x()+","+movement.y()+","+movement.plane()+
                            " localX="+localX+" localY="+localY+" wireOrder=Y_THEN_X authority=ACCOUNT_MOVEMENT_STATE");
                    }
                    // v5 native sidebar bootstrap. These are current-client roots; packet 71 with
                    // interfaceId=0 is an exact client shortcut to the native Spawn Tab root 67027.
                    // We deliberately do not force-select any of these tabs.
                    int combatRoot = CombatInterfaceRepository.forWeapon(equipment.weapon());
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(combatRoot, CombatInterfaceRepository.TAB_INDEX));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(BootstrapPackets.SKILLS_ROOT, 1));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(BootstrapPackets.ACHIEVEMENTS_ROOT, BootstrapPackets.ACHIEVEMENTS_INDEX));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(BootstrapPackets.INVENTORY_ROOT, 3));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(BootstrapPackets.EQUIPMENT_ROOT, 4));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(prayers.root(), 5));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(magic.root(), 6));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(BootstrapPackets.CLAN_CHAT_ROOT, 7));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(BootstrapPackets.FRIENDS_ROOT, 8));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(BootstrapPackets.IGNORE_ROOT, 9));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(BootstrapPackets.LOGOUT_ROOT, 10));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(BootstrapPackets.OPTIONS_ROOT, 11));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(BootstrapPackets.EMOTES_ROOT, 12));
                    serverPackets.fixed(71, BootstrapPackets.sidebar71(BootstrapPackets.SPAWN_TAB_SHORTCUT_ROOT, BootstrapPackets.SPAWN_TAB_INDEX));
                    // v5.4 exact current-client packet-134 skill bootstrap. The native HP/Prayer
                    // orbs and skill surfaces consume the same server-owned combat-skill state.
                    for (int skill=0; skill<PlayerState.COMBAT_SKILL_COUNT; skill++) {
                        serverPackets.fixed(134, BootstrapPackets.skill134(skill, playerState.xp(skill), playerState.currentLevel(skill)));
                    }
                    bank.sendNormalInventory(serverPackets);
                    bank.sendEquipment(serverPackets, equipment);
                    // Empty cosmetic slot is already client-native. Publish widget 27701
                    // only when a persisted cosmetic is active so default bootstrap
                    // packet order stays byte-for-byte compatible with older fixtures.
                    if(playerState.cosmetic().active()) bank.sendCosmetic(serverPackets, playerState.cosmetic());
                    if (movement.persistentRun()) {
                        serverPackets.fixed(36, BootstrapPackets.config36(173, 1));
                    }
                    // WORLD-R7 production HOME parity rebased onto MAINLINE v5.5.
                    // Reproject semantic world coordinates against LocalLab's actual runtime
                    // scene base before emitting packet 85/101/151, then bootstrap HOME actors
                    // through MAINLINE's single NpcRegistry / packet-65 owner.
                    HomeObjectOverlayReplayer.Stats homeScene=homeWorld.replayScene(serverPackets,MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y);
                    // HOME replay owns packet85 internally; invalidate our persistent context so the next standalone scene event re-establishes it.
                    scenePublisher.context().invalidate();
                    npcs.bootstrapHome(serverPackets,movement,petState,homeWorld);
                    if(petState.active() && petAccessoryState.activeItem()!=0){
                        Integer selector=PetAccessoryAuthority.selector(petAccessoryState.activeItem());
                        String visual=npcs.devSetParticleSelector(selector,movement,serverPackets);
                        System.out.println(tag+"V5131_PET_ACCESSORY_PERSIST_RESTORE item="+petAccessoryState.activeItem()+" selector="+selector+" visual={"+visual+"} authority=ACCOUNT_SEMANTIC_STATE");
                    }
                    if(petState.active() && petState.miniConfigured())
                        System.out.println(tag+"V511_MINIPET_BOOTSTRAP "+miniPets.onMainPetSpawn(petState,npcs,movement,serverPackets));
                    int groundReplay=0;
                    for(GroundItem g:world.groundItems().snapshot()) if(g.owner==null||g.owner.equalsIgnoreCase(username)){ scenePublisher.groundSpawn(g); groundReplay++; }
                    System.out.println(tag + "WORLD_R7_HOME_BOOTSTRAP scene="+homeScene
                                     + " npcVisible="+npcs.visibleCount()
                                     + " trackedHomeNpcs="+homeWorld.trackedWorldNpcCount()
                                     + " bloodFountain=1799@3079,3492"
                                     + " dummies=5x1488+1x1489 productionPositions=true"
                                     + " pet="+(petState.active()?(petState.itemId()+"->"+petState.npcId()):"none")
                                     + " miniConfigured="+(petState.miniConfigured()?petState.miniItemId():-1)+" groundReplay="+groundReplay);
                    byte[] appearance = BootstrapPackets.appearanceBlock(username, equipment.appearanceItems(), playerState);
                    System.out.println(tag + "M4_BOOTSTRAP_SENT packets=249,73,81 region=385,436 local=55,55"
                                     + " appearanceMask=0x10 appearanceBytes=" + appearance.length
                                     + " status=M4_CERTIFIED");
                    System.out.println(tag + "M5_RUN_ENERGY_SENT opcode=110 value="+movement.runEnergy()+" schema=STATIC_EXACT_FIXED1 persistentAccount="+persistentAccount);
                    System.out.println(tag + "M5_RUN_ORB_WIDGET_SENT opcode=126 widget=149 text=\""+movement.runEnergy()+"%\" schema=STATIC_EXACT_VARSHORT");
                    EquipmentMetadataRepository.Meta bootMeta = EquipmentMetadataRepository.resolveKnownSlot(equipment.weapon(), EquipmentSlot.WEAPON);
                    EquipmentPoseProfile bootPose = EquipmentPoseRepository.forAppearance(equipment.appearanceItems());
                    System.out.println(tag + "V522_EQUIPMENT_BOOTSTRAP weaponSlot="+EquipmentState.WEAPON_SLOT+" itemId="+equipment.weapon()
                                     + " item=Scythe_of_bloodrend appearanceValue="+(512+equipment.weapon())
                                     + " pose="+bootPose.name+" pose7="+java.util.Arrays.toString(bootPose.toArray())
                                     + " weaponSpecificMask=0x"+Integer.toHexString(bootPose.weaponSpecificMask)
                                     + " weaponSpecificComplete="+bootPose.weaponSpecificComplete()
                                     + " twoHanded="+(bootMeta!=null && bootMeta.twoHanded)
                                     + " inventoryRoot=3213 inventoryWidget=3214");
                    System.out.println(tag + "V57_NATIVE_SIDEBAR combat="+combatRoot+"@0 skills=3917@1 achievements=44100@2 inventory=3213@3 equipment=1644@4 prayer=5608@5 magic=1151@6"
                                     + " clan=18128@7 friends=5065@8 ignore=5715@9 logout=2449@10 options=904@11 emotes=147@12"
                                     + " spawnShortcut=0@"+BootstrapPackets.SPAWN_TAB_INDEX+"->"+BootstrapPackets.SPAWN_TAB_ROOT
                                     + " equipmentContainer="+EquipmentState.EQUIPMENT_WIDGET+" localRank=205");
                    System.out.println(tag + "V5_ITEM_REPOSITORY loaded="+ItemDefinitionRepository.count()
                                     + " genericCommands=::item|::tabitem_<id>_[amount] source=items.json+SpawnPK_i.bin");
                    System.out.println(tag + "V57_PET_REPOSITORY mapped="+PetDefinitionRepository.count()
                                     + " ambiguous="+PetDefinitionRepository.ambiguousCount()
                                     + " lifecycle=opcode87_DROP->packet65_FOLLOWER->opcode155_PICKUP"
                                     + " ownerTargetBase=32768 localPlayerIndex="+NpcRegistry.LOCAL_PLAYER_INDEX);
                    System.out.println(tag + "V54_PLAYER_STATE skills=7 hp="+playerState.currentLevel(PlayerState.HITPOINTS)
                                     + " prayer="+playerState.currentLevel(PlayerState.PRAYER)
                                     + " combat="+playerState.combatLevel()+" xp99="+PlayerState.XP_99
                                     + " skillPacket=134 compSelectors="+playerState.compSelectorSummary());
                    System.out.println(tag + "V54_WEAPON_POSE_REPOSITORY directObserved="+WeaponPoseRepository.directCount()
                                     + " canonicalFamilies="+WeaponPoseRepository.familyCount()
                                     + " conflicts="+WeaponPoseRepository.conflictingFamilyCount());
                    System.out.println(tag + "V57_COMBAT_M2 weaponSlotResolved="+CombatWeaponRepository.count()+" currentEquipActionWeapons="+CombatWeaponRepository.currentEquipActionWeaponCount()
                                     + " productionPoseProfiles="+CombatWeaponRepository.withProductionPose()
                                     + " inferredPresentationProfiles="+CombatWeaponRepository.withInferredPresentation()
                                     + " mechanicsResolved="+CombatWeaponRepository.mechanicsResolved()
                                     + " targets=1488:PLAYER_PVP,1489:NPC_PVM"
                                     + " normalDamage=DISABLED_UNTIL_FORMULA_EVIDENCE"
                                     + " npcAttackOpcode72=STATIC_EXACT_BE_SHORT_A"
                                     + " packet81AnimationMask=0x08 packet65NpcAnimMask=0x10 packet65SingleHitMask=0x40");
                    System.out.println(tag + "V523_EQUIPMENT_RECONCILIATION currentActionCandidates="+EquipmentMetadataRepository.currentActionCandidateCount()
                                     + " resolved="+EquipmentMetadataRepository.currentActionResolvedCount()
                                     + " productionAppearanceIds="+EquipmentMetadataRepository.productionObservedCount()
                                     + " clientOpcode41BaseFallback=true provenanceAware=true");
                    if (movementEnabled) {
                        System.out.println(tag + "M5_AUTHORITY_ENABLED world="+movement.x()+","+movement.y()
                                         + " tickMs=600 collision=CLIENT_SUBMITTED_PATH regionPolicy=CURRENT_104x104"
                                         + " runEnergy="+movement.runEnergy()+" runToggle="+(movement.persistentRun()?1:0)+" runToggleWidget=152 config173=ACK minimap248=ACCEPT_PATH_CORE");
                    }
                    saveAccountQuiet(tag, "BOOTSTRAP");
                }
                if(bootstrap && player81Sync==null){
                    player81Sync=Player81WorldSync.register(serverPackets,world,worldPlayer,dev);
                    SharedNpcWorldRelay.register(serverPackets,world,worldPlayer,npcs,movement);
                    TradeService.register(world,worldPlayer,bank,serverPackets,()->saveAccountQuiet("[session "+socket.getRemoteSocketAddress()+"] ","TRADE_COMMIT"));
                    Player81WorldSync.sendPlayerOptionsIfMultiplayer(world);
                    System.out.println(tag+"V5131_ENGINE_R3_PLAYER_SYNC_REGISTER playerId="+worldPlayer.id()+" members="+world.players().size()+" "+player81Sync.summary()+
                        " options="+(world.players().size()>1?"Attack/Follow/TradeWith":"DEFERRED_UNTIL_MULTIPLAYER")+" authority=EXACT_CLIENT_S2C104_C2S128_153_73");
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
            if(worldTickAttached){world.detachTickTarget(worldPlayer.id());worldTickAttached=false;}
            if(sessionPackets!=null){TradeService.unregister(worldPlayer);SharedNpcWorldRelay.unregister(sessionPackets);Player81WorldSync.unregister(sessionPackets);player81Sync=null;}
            if(worldRegistered){
                boolean removed=world.unregisterPlayer(worldPlayer);worldRegistered=false;
                System.out.println(tag+"V512_WORLD_UNREGISTER playerId="+worldPlayer.id()+" removed="+removed+" members="+world.players().size()+" queuedCommands="+world.commands().size());
            }
            devPanelCoordinator.closeSession();
            synchronized(worldPlayer.mutationLock()){saveAccountQuiet(tag, "SESSION_END");}
        }
    }

    private void processPendingOnWorld(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws Exception{
        world.submitAndWait(
            worldPlayer,
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

    private void saveAccountQuiet(String tag, String reason) {
        LocalAccountLifecycle.saveQuiet(
            username,persistentAccount,bank,equipment,movement,petState,playerState,
            petAccessoryState.activeItem(),tag,reason);
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

    private static String joinTokens(String[] p,int start){
        if(p==null||start>=p.length) return "";
        StringBuilder b=new StringBuilder();
        for(int i=start;i<p.length;i++){ if(i>start)b.append(' '); b.append(p[i]); }
        return b.toString();
    }

    private static long parseLong(String s,long fallback) {
        try{return Long.parseLong(s);}catch(Exception e){return fallback;}
    }

    private static int parseInt(String s,int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }

    private static int parseAmount(String s,int fallback) {
        if (s==null) return fallback;
        String t=s.trim().toLowerCase(java.util.Locale.ROOT).replace(",", "");
        long mul=1L;
        if (t.endsWith("k")) { mul=1_000L; t=t.substring(0,t.length()-1); }
        else if (t.endsWith("m")) { mul=1_000_000L; t=t.substring(0,t.length()-1); }
        else if (t.endsWith("b")) { mul=1_000_000_000L; t=t.substring(0,t.length()-1); }
        try {
            long base=Long.parseLong(t);
            long v=Math.max(1L,Math.min(1_000_000_000L,base*mul));
            return (int)v;
        } catch (Exception e) { return fallback; }
    }

}
