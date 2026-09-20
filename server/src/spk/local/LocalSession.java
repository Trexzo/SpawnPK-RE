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
    private final LocalDevPanelRenderer devPanelRenderer;
    private final LocalDevPanelAmountHandler devPanelAmounts;
    private final LocalDevPanelWidgetHandler devPanelWidgets;
    private final LocalCommandDispatcher commandDispatcher;
    private final LocalSessionUiActionHandler uiActions;
    private final LocalPetDropPickupHandler petDropPickup;
    private final LocalMovementRequestHandler movementRequests;
    private final LocalRegionStreamHandler regionStreams;
    private final LocalWorldTickCoordinator worldTicks;
    private SceneUpdatePublisher scenePublisher;
    private ServerPacketWriter sessionPackets;
    private OutboundPacketQueue outboundPackets;
    private long sessionWorldTick;
    private long nextPetFollowAt=Long.MAX_VALUE;
    private boolean petFollowRealtimeScheduled;
    private boolean petTestRealtimeScheduled;
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
            ()->{
                nextPetFollowAt=Long.MAX_VALUE;
                petFollowRealtimeScheduled=false;
            });
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
        this.devPanelRenderer = new LocalDevPanelRenderer(
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
        this.devPanelAmounts = new LocalDevPanelAmountHandler(
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
            ()->clearDialogNumberKeys());
        this.devPanelWidgets = new LocalDevPanelWidgetHandler(
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
            ()->clearDialogNumberKeys());
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
                    LocalSession.this.openDevPanel(
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
                    LocalSession.this.clearDialogNumberKeys();
                }

                @Override public void handleDevPanelWidget(
                    int widget,
                    ServerPacketWriter writer,
                    String tag
                )throws IOException{
                    LocalSession.this.handleDevPanelWidget(
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
                    LocalSession.this.nextPetFollowAt=Long.MAX_VALUE;
                }

                @Override public void ensurePetFollowScheduled(
                    long now
                ){
                    LocalSession.this.ensurePetFollowScheduled(now);
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
                    LocalSession.this.clearDialogNumberKeys();
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
                    LocalSession.this.nextPetFollowAt=Long.MAX_VALUE;
                    LocalSession.this.petFollowRealtimeScheduled=false;
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
                    return LocalSession.this.nextPetFollowAt;
                }

                @Override public void setPetFollowDeadline(
                    long value
                ){
                    LocalSession.this.nextPetFollowAt=value;
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
            if(petDialogs.hasAnyOpen() || devPanel.isOpen()) clearDialogNumberKeys();
            devPanel.close();
            synchronized(worldPlayer.mutationLock()){saveAccountQuiet(tag, "SESSION_END");}
        }
    }

    private void processPendingOnWorld(ClientPacketProbe clientPackets,ServerPacketWriter serverPackets,String tag) throws Exception {
        world.submitAndWait(worldPlayer,()->{
            acceptPendingInterfaceClose(clientPackets, serverPackets, tag);
            acceptPendingActions(clientPackets, serverPackets, tag);
            acceptPendingCommand(clientPackets, serverPackets, tag);
            acceptPendingObjectInteraction(clientPackets, serverPackets, tag);
            acceptPendingGenericInteraction(clientPackets, serverPackets, tag);
            acceptPendingItemAction(clientPackets, serverPackets, tag);
            acceptPendingItemOnItem(clientPackets, serverPackets, tag);
            acceptPendingItemOnNpc(clientPackets, serverPackets, tag);
            acceptPendingSpellTarget(clientPackets, serverPackets, tag);
            acceptPendingDropItem(clientPackets, serverPackets, tag);
            acceptPendingGroundItemInteraction(clientPackets, serverPackets, tag);
            acceptPendingPlayerAction(clientPackets, serverPackets, tag);
            acceptPendingNpcAction(clientPackets, serverPackets, tag);
            acceptPendingAmount(clientPackets, serverPackets, tag);
            acceptPendingContainerDrag(clientPackets, serverPackets, tag);
            acceptPendingMovement(clientPackets, serverPackets, tag);
            long now=System.currentTimeMillis();
            ensurePetFollowScheduled(now);
            ensurePetTestSequenceScheduled(now);
        },5_000L);
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

    private void ensurePetFollowScheduled(long now){
        if(!bootstrap||movement.transientRegion()||petDropPickup.pendingPickupBlocksPetFollow()||petFollowRealtimeScheduled||npcs.followFrozen()||!npcs.needsFollow(movement))return;
        if(nextPetFollowAt==Long.MAX_VALUE)nextPetFollowAt=now+200L;
        long at=Math.max(now,nextPetFollowAt);
        petFollowRealtimeScheduled=true;
        world.realtime().schedule(at,worldPlayer,()->runPetFollowRealtime());
    }

    private void runPetFollowRealtime(){
        petFollowRealtimeScheduled=false;
        if(!bootstrap||movement.transientRegion()||petDropPickup.pendingPickupBlocksPetFollow()||sessionPackets==null||npcs.followFrozen()||!npcs.needsFollow(movement)){nextPetFollowAt=Long.MAX_VALUE;return;}
        long now=System.currentTimeMillis();
        try{
            String tag="[session "+socket.getRemoteSocketAddress()+"] ";
            String petFollow=npcs.tickFollow(movement,sessionPackets);
            long nextDelay=npcs.needsFollow(movement)?npcs.followDelayMs(movement):Long.MAX_VALUE;
            if(petFollow!=null)System.out.println(tag+"V512_"+petFollow+" execution=SHARED_WORLD_THREAD initialReactionMs=200 nextDelayMs="+(nextDelay==Long.MAX_VALUE?"idle":nextDelay)+" ownerRunning="+npcs.recentOwnerRunning());
            nextPetFollowAt=nextDelay==Long.MAX_VALUE?Long.MAX_VALUE:now+nextDelay;
            if(nextDelay!=Long.MAX_VALUE)ensurePetFollowScheduled(now);
        }catch(Throwable t){System.err.println("[world player="+worldPlayer.id()+"] pet-follow presentation failed: "+t);}
    }

    private void ensurePetTestSequenceScheduled(long now){
        if(!bootstrap||petTestRealtimeScheduled||!petRuntimeCommands.sequenceActive()||sessionPackets==null)return;
        long due=petRuntimeCommands.sequenceAt();
        long at=Math.max(now,due==Long.MAX_VALUE?now:due);
        petTestRealtimeScheduled=true;
        world.realtime().schedule(at,worldPlayer,()->{
            petTestRealtimeScheduled=false;
            if(!petRuntimeCommands.sequenceActive())return;
            long when=System.currentTimeMillis();
            try{
                String line=petRuntimeCommands.tickSequence(when,sessionPackets);
                if(line!=null)System.out.println("[session "+socket.getRemoteSocketAddress()+"] "+line);
            }
            catch(Throwable t){
                petRuntimeCommands.failSequence();
                System.err.println("[world player="+worldPlayer.id()+"] pet-test sequence failed: "+t);
            }
            if(petRuntimeCommands.sequenceActive())ensurePetTestSequenceScheduled(when);
        });
    }

    private void acceptPendingInterfaceClose(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(!clientPackets.takeInterfaceClose())return;
        uiActions.handleInterfaceClose(
            clientPackets.isAligned(),
            serverPackets,
            tag
        );
    }

    private void acceptPendingActions(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        Integer widget=clientPackets.takeWidgetAction();
        if(widget==null)return;
        uiActions.handleWidget(
            widget.intValue(),
            serverPackets,
            tag
        );
    }

    private void applyPetDialogResult(
        LocalPetInventoryDialogHandler.Result result,
        String tag
    ){
        if(result==null)return;
        if(result.keyAction==LocalPetInventoryDialogHandler.KeyAction.PUBLISH_2482_2485)
            publishDialogNumberKeys(2482,2483,2484,2485);
        if(result.saveReason!=null)saveAccountQuiet(tag,result.saveReason);
        if(result.logText!=null)System.out.println(tag+result.logText);
        if(result.keyAction==LocalPetInventoryDialogHandler.KeyAction.CLEAR_AFTER_LOG)
            clearDialogNumberKeys();
    }

    private void acceptPendingGenericInteraction(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        for (GenericInteractionEvent e; (e=R85GenericC2SBridge.take(clientPackets))!=null; ) {
            String result=genericInteractionHandler.handle(e);
            if(result!=null)System.out.println(tag+result);
        }
    }

    private void acceptPendingObjectInteraction(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        ObjectInteraction request=clientPackets.takeObjectInteraction();
        if(request==null)return;
        String result=bankObjectHandler.handle(request,serverPackets);
        if(result!=null)System.out.println(tag+result);
    }

    private boolean adjacentTo(int x,int y) {
        return chebyshev(movement.x(),movement.y(),x,y) <= 1;
    }
    private boolean onTile(int x,int y) {
        return movement.x()==x && movement.y()==y;
    }

    static int chebyshev(int x0,int y0,int x1,int y1) {
        return LocalMovementRequestHandler.chebyshev(
            x0,
            y0,
            x1,
            y1
        );
    }

    private void acceptPendingItemAction(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        ItemContainerAction a = clientPackets.takeItemAction();
        if (a == null) return;

        String tradeItem=TradeService.handleItemAction(worldPlayer,a);
        if(tradeItem!=null){
            System.out.println(tag+"V5140_TRADE_ITEM "+a+" result="+tradeItem);
            return;
        }

        LocalEquipmentItemActionHandler.Result equipmentAction=
            equipmentItemActions.handle(a,username,serverPackets);
        if(equipmentAction!=null){
            for(String line:equipmentAction.beforeSaveLogs)System.out.println(tag+line);
            if(equipmentAction.saveReason!=null)saveAccountQuiet(tag,equipmentAction.saveReason);
            for(String line:equipmentAction.afterSaveLogs)System.out.println(tag+line);
            return;
        }

        LocalPetInventoryDialogHandler.Result petDialogItem=
            petDialogs.handleItemAction(a,serverPackets);
        if(petDialogItem!=null){
            applyPetDialogResult(petDialogItem,tag);
            return;
        }

        String compCapeItem=
            compCapeCustomize.handleItemAction(a,serverPackets);
        if(compCapeItem!=null){
            System.out.println(tag+compCapeItem);
            return;
        }

        String result = bank.apply(a, serverPackets);
        saveAccountQuiet(tag, "BANK_ITEM_ACTION");
        System.out.println(tag + "V522_BANK_ITEM_ACTION " + a + " result=" + result + " decoderAligned=true");
    }

    private void acceptPendingItemOnItem(ClientPacketProbe clientPackets,ServerPacketWriter serverPackets,String tag)throws IOException{
        ItemOnItemAction a=clientPackets.takeItemOnItem();
        if(a==null)return;
        LocalItemOnItemHandler.Result result=itemOnItemHandler.handle(a,serverPackets);
        if(result.saveReason!=null)saveAccountQuiet(tag,result.saveReason);
        System.out.println(tag+result.logText);
    }

    private void acceptPendingItemOnNpc(ClientPacketProbe clientPackets,ServerPacketWriter serverPackets,String tag)throws IOException{
        ItemOnNpcAction action=clientPackets.takeItemOnNpc();
        if(action==null)return;
        LocalItemOnNpcHandler.Result result=itemOnNpcHandler.handle(action,serverPackets);
        if(result.saveReason!=null)saveAccountQuiet(tag,result.saveReason);
        System.out.println(tag+result.logText);
    }

    private void acceptPendingSpellTarget(ClientPacketProbe clientPackets,ServerPacketWriter serverPackets,String tag)throws IOException{
        SpellTargetRequest req=clientPackets.takeSpellTarget();
        if(req==null)return;
        System.out.println(tag+spellTargetHandler.handle(req,serverPackets));
    }

    private void acceptPendingDropItem(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        DropItemAction action=clientPackets.takeDropItem();
        if(action==null)return;
        petDropPickup.handleDrop(action,serverPackets,tag);
    }

    private void acceptPendingGroundItemInteraction(ClientPacketProbe clientPackets,ServerPacketWriter serverPackets,String tag)throws IOException{
        GroundItemInteraction action=clientPackets.takeGroundItemInteraction();
        if(action==null)return;
        applyGroundItemResult(
            groundItemHandler.handle(action,username,scenePublisher,serverPackets),
            tag
        );
    }

    private void applyGroundItemResult(LocalGroundItemInteractionHandler.Result result,String tag){
        if(result==null)return;
        if(result.saveReason!=null)saveAccountQuiet(tag,result.saveReason);
        System.out.println(tag+result.logText);
    }

    private void acceptPendingPlayerAction(ClientPacketProbe clientPackets,ServerPacketWriter serverPackets,String tag)throws IOException{
        PlayerAction a=clientPackets.takePlayerAction();
        if(a==null)return;
        if(player81Sync==null){
            System.out.println(tag+"V5131_PLAYER_ACTION "+a+" result=REJECTED_SYNC_NOT_READY");
            return;
        }
        WorldPlayer target=player81Sync.resolveVisible(a.playerIndex);
        if(target==null||!target.registered()){
            System.out.println(tag+"V5131_PLAYER_ACTION "+a+" result=REJECTED_STALE_OR_NOT_VISIBLE");
            return;
        }
        if(combat.active()){
            boolean cancelled=combat.cancelForManualMovement();
            if(cancelled)clearOpponentOverlay(serverPackets,tag,"PLAYER_INTERACTION_REPLACES_NPC_COMBAT");
        }
        String result=playerInteractions.handleResolved(a,target,player81Sync);
        if(result!=null)System.out.println(tag+result);
    }

    private void acceptPendingNpcAction(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        NpcAction a=clientPackets.takeNpcAction();
        if(a==null) return;
        NpcEntity clicked=npcs.scene(a.sceneIndex);
        NpcEntity pet=npcs.pet();

        // MAINLINE pet Pick-up is exact current-client opcode155 only.
        // Attack opcode72 must never despawn a follower merely because the scene
        // index happens to match the active pet.
        if(petDropPickup.handlePickupNpcAction(
            a,
            serverPackets,
            tag
        ))return;

        // Any different NPC interaction supersedes a deferred pet pickup before
        // a new combat/bank interaction target is assigned.
        petDropPickup.cancelDeferredForNewNpcAction(a,tag);

        // Exact client exposes Yoshiganger NPC option 3 as Switch-effect, but the production
        // gameplay transition (Doppel-like vs Yoshi-like functionality) is not recovered.
        // Fail closed: never synthesize an accessory/intrinsic particle layer as a substitute.
        if(clicked!=null && clicked==pet && clicked.definitionId==1334 && a.opcode==17){
            String rr=LocalDevVisualOverrideStore.set("intrinsicfx",null);
            System.out.println(tag+"V5128_YOSHIGANGER_SWITCH_EFFECT scene="+clicked.sceneIndex+
                " result=PENDING_FUNCTIONAL_MODE_RECONSTRUCTION visualAccessoryInvented=false bodyGreenPreserved=true overrideReset="+rr);
            return;
        }

        // Exact current-client combat entry point is opcode72. opcode155 remains the
        // ordinary first NPC option (e.g. pet Pick-up). The target definition is
        // still checked fail-closed, so only the production max-hit dummies enter
        // the M1 combat harness.
        if(isCombatAttackAction(a,clicked)){
            int combatRoot=CombatInterfaceRepository.forWeapon(equipment.weapon());
            long now=System.currentTimeMillis();
            String result=combat.request(clicked,movement,equipment.weapon(),now,combatStyles.current(combatRoot),serverPackets);
            String approach="NONE";
            if(result.contains("TARGET_DEFERRED_RANGE"))
                approach=combat.beginServerOwnedApproach(clicked,movement,equipment.weapon(),now);
            System.out.println(tag+"V5123_COMBAT_REQUEST "+a+" semantic=NPC_ATTACK clicked="+clicked+" result="+result+" approach="+approach);
            return;
        }

        String routed=routedNpcHandler.handle(a,clicked,serverPackets);
        if(routed!=null)System.out.println(tag+routed);
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

    static boolean isCombatAttackAction(NpcAction a,NpcEntity clicked){
        return a!=null && a.opcode==72 && clicked!=null && CombatTargetRepository.isCombatDummy(clicked.definitionId);
    }

    private void acceptPendingAmount(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        Integer amount = clientPackets.takeAmount();
        if (amount == null) return;
        if(devPanel.hasPending()){
            handleDevPanelAmount(amount.intValue(),serverPackets,tag);
            return;
        }
        LocalBankRequestHandler.Result result=bankRequests.handleAmount(amount.intValue(),serverPackets);
        if(result.saveReason!=null)saveAccountQuiet(tag,result.saveReason);
        System.out.println(tag+result.logText+" decoderAligned="+clientPackets.isAligned());
    }

    private void acceptPendingContainerDrag(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        ContainerDrag d = clientPackets.takeContainerDrag();
        if (d == null) return;
        LocalBankRequestHandler.Result result=bankRequests.handleDrag(d,serverPackets);
        if(result.saveReason!=null)saveAccountQuiet(tag,result.saveReason);
        System.out.println(tag+result.logText+" decoderAligned="+clientPackets.isAligned());
    }

    private void acceptPendingCommand(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        String command=clientPackets.takeCommand();
        if(command==null)return;

        commandDispatcher.handle(
            command,
            clientPackets.isAligned(),
            username,
            loginAlias,
            persistentAccount,
            sessionWorldTick,
            serverPackets,
            tag
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



    // ---------------------------------------------------------------------
    // Engine R7 — one-stop in-game developer control center.
    // Uses the exact current clickable four-choice chatbox (2480) and native
    // numeric prompt (S2C27 -> C2S208). All overrides are session-local.
    // ---------------------------------------------------------------------
    private void openDevPanel(DevControlCenter.Page page,ServerPacketWriter w)throws IOException{
        if(bank.isOpen())bank.close(w);
        TradeService.cancelIfActive(worldPlayer,"DEV_PANEL_OPEN");
        itemLibrary.close();
        petDialogs.clearAll();
        clearDialogNumberKeys();
        w.fixed(219,new byte[0]);
        devPanel.open(page);
        renderDevPanel(w);
    }

    private void renderDevPanel(ServerPacketWriter w)throws IOException{
        if(devPanelRenderer.render(w))
            publishDialogNumberKeys(2482,2483,2484,2485);
    }

    private void handleDevPanelWidget(int widget,ServerPacketWriter w,String tag)throws IOException{
        LocalDevPanelWidgetHandler.Outcome outcome=
            devPanelWidgets.handle(
                widget,username,scenePublisher,w);

        if(outcome==null)return;

        if(outcome.scenePublisher!=null)
            scenePublisher=outcome.scenePublisher;

        if(outcome.saveReason!=null)
            saveAccountQuiet(tag,outcome.saveReason);

        if(outcome.directLogText!=null){
            System.out.println(tag+outcome.directLogText);
            return;
        }

        if(!outcome.renderAfter)return;

        if(outcome.resultText!=null&&!outcome.resultText.isEmpty()){
            System.out.println(
                tag+"V5171_DEV_PANEL page="+
                devPanel.page()+
                " choice="+(outcome.choice+1)+
                " result={"+outcome.resultText+"}");
        }

        renderDevPanel(w);
    }

    private void promptDevPanelAmount(DevControlCenter.PendingAmount pending,ServerPacketWriter w)throws IOException{
        w.fixed(219,new byte[0]);clearDialogNumberKeys();devPanel.prompt(pending);w.fixed(27,new byte[0]);
    }

    private void handleDevPanelAmount(int value,ServerPacketWriter w,String tag)throws IOException{
        LocalDevPanelAmountHandler.Outcome outcome=
            devPanelAmounts.handle(
                value,username,scenePublisher,w);

        if(outcome.scenePublisher!=null)
            scenePublisher=outcome.scenePublisher;

        if(outcome.saveReason!=null)
            saveAccountQuiet(tag,outcome.saveReason);

        System.out.println(
            tag+"V5171_DEV_PANEL_AMOUNT kind="+
            outcome.pending+
            " value="+value+
            " result={"+outcome.resultText+"}");

        if(outcome.reopen){
            devPanel.finishPrompt();
            renderDevPanel(w);
        }else{
            devPanel.cancelPending();
        }
    }

    /**
     * Generic LocalLab classic-dialog keyboard contract. The exact client key
     * queue returns ASCII digits; the client helper translates 1..9 into the
     * ordered widget ids published here and sends the same opcode185 packet as
     * a mouse click. This file is runtime state only and never shipped as data.
     */
    private void publishDialogNumberKeys(int... widgets){
        try{
            Path f=Paths.get("server","data","locallab_dialog_keys.properties");
            Path parent=f.getParent(); if(parent!=null)Files.createDirectories(parent);
            StringBuilder ids=new StringBuilder();
            for(int i=0;i<widgets.length&&i<9;i++){if(i>0)ids.append(',');ids.append(widgets[i]);}
            String body="active=true\nwidgets="+ids+"\nupdated="+System.currentTimeMillis()+"\n";
            Path tmp=f.resolveSibling(f.getFileName().toString()+".tmp");
            Files.write(tmp,body.getBytes(StandardCharsets.UTF_8));
            try{Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Throwable t){System.err.println("LOCALLAB_DIALOG_NUMBER_KEYS_STATE_WRITE_FAILED "+t);}
    }
    private void clearDialogNumberKeys(){
        try{
            Path f=Paths.get("server","data","locallab_dialog_keys.properties");
            if(Files.exists(f))Files.write(f,"active=false\nwidgets=\n".getBytes(StandardCharsets.UTF_8));
        }catch(Throwable t){System.err.println("LOCALLAB_DIALOG_NUMBER_KEYS_STATE_CLEAR_FAILED "+t);}
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

    private void acceptPendingMovement(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        MovementRequest request=clientPackets.takeMovement();
        if(request==null)return;
        movementRequests.handle(
            request,
            serverPackets,
            tag
        );
    }

}
