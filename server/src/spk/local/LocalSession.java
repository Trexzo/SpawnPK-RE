package spk.local;

import java.io.*;
import java.net.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

final class LocalSession implements Runnable {
    private static final long SERVER_SEED = 0x0123456789ABCDEFL;
    private static final long PET_PICKUP_REMOVE_DELAY_MS=0L;
    private static final long PET_PICKUP_FACING_CLEAR_DELAY_MS=450L;
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
    private SceneUpdatePublisher scenePublisher;
    private ServerPacketWriter sessionPackets;
    private OutboundPacketQueue outboundPackets;
    private long sessionWorldTick;
    private long legacyTickCount;
    private long movementTickCount;
    private long nextPetFollowAt=Long.MAX_VALUE;
    private boolean petFollowRealtimeScheduled;
    private boolean petTestRealtimeScheduled;
    private boolean worldRegistered;
    private long worldPlayerGeneration;
    private boolean worldTickAttached;
    private Integer pendingPetPickupScene;
    private long pendingPetPickupDeadlineMs;
    /** Legacy R2.12 field retained for binary/test compatibility; R2.13 pickup uses Q/R and never arms it. */
    private long pendingPetFacingClearAtMs=Long.MAX_VALUE;
    /** Pick-up completion is synchronized with animation 827 + Q/R turn-to-tile on a world pulse. */
    private long pendingPetPickupCompleteAtMs=Long.MAX_VALUE;
    private int pendingPetPickupItem=-1,pendingPetPickupNpc=-1,pendingPetPickupCompleteScene=-1;
    private String pendingPetPickupCompleteReason;
    /** R8.1 owns a temporary follow freeze while the player approaches a Pick-up target. */
    private boolean petPickupOwnedFollowFreeze;
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
    private void onWorldTick(long worldTick,long now)throws Exception{
        sessionWorldTick=worldTick;
        if(!bootstrap||sessionPackets==null)return;
        sessionPackets.beginBatch();
        try{
            String tag="[session "+socket.getRemoteSocketAddress()+"] ";
            if(maybeAutoStreamRegion(sessionPackets,tag)){
                legacyTickCount++;
                return;
            }
            if(movement.transientRegion()){
                MovementState.Tick mt = movementEnabled ? movement.advance() : null;
                if(mt==null) sessionPackets.varShort(81,BootstrapPackets.player81Idle());
                else if(mt.running) sessionPackets.varShort(81,BootstrapPackets.player81RunSteps(mt.dir1,mt.dir2));
                else sessionPackets.varShort(81,BootstrapPackets.player81WalkStep(mt.dir1));
                if(mt!=null){movementTickCount++;saveAccountQuiet("[session "+socket.getRemoteSocketAddress()+"] ","TRANSIENT_REGION_POSITION_NONPERSISTENT");}
                legacyTickCount++;
                if(legacyTickCount==1||legacyTickCount%25==0)System.out.println("[session "+socket.getRemoteSocketAddress()+"] V5160_TRANSIENT_REGION_PULSE tick="+worldTick+" world="+movement.x()+","+movement.y()+","+movement.plane()+" base="+movement.loadedBaseX()+","+movement.loadedBaseY()+" queued="+movement.queued()+" staticCollision=true homeSystemsSuspended=true");
                return;
            }
            String playerInteractionPrep=playerInteractions.prepareTick(worldTick,player81Sync);
            if(playerInteractionPrep!=null)System.out.println(playerInteractionPrep);
            MovementState.Tick mt = movementEnabled ? movement.advance() : null;
            Integer measuredApproachTarget = mt==null ? null : combat.consumeApproachFacingTargetForMovement();
            Integer playerApproachTarget = mt==null ? null : playerInteractions.movementInteractionTarget(player81Sync);
            Integer movementFacingTarget = measuredApproachTarget!=null?measuredApproachTarget:playerApproachTarget;
            if (mt == null) {
                sessionPackets.varShort(81, BootstrapPackets.player81Idle());
            } else if (mt.running) {
                sessionPackets.varShort(81, movementFacingTarget==null
                    ? BootstrapPackets.player81RunSteps(mt.dir1, mt.dir2)
                    : Player81MeasuredSync.runStepsAndInteraction(mt.dir1,mt.dir2,movementFacingTarget.intValue()));
                movementTickCount++;
                System.out.println("[world player="+worldPlayer.id()+"] M5_AUTHORITATIVE_TICK mode=RUN tiles=2 from="+mt.fromX+","+mt.fromY
                                 + " to="+mt.toX+","+mt.toY+" dirs="+mt.dir1+","+mt.dir2
                                 + " combatFacing="+(movementFacingTarget==null?"NONE":movementFacingTarget)
                                 + " remaining="+mt.remaining+" movementTick="+movementTickCount+" worldTick="+worldTick);
            } else {
                sessionPackets.varShort(81, movementFacingTarget==null
                    ? BootstrapPackets.player81WalkStep(mt.dir1)
                    : Player81MeasuredSync.walkStepAndInteraction(mt.dir1,movementFacingTarget.intValue()));
                movementTickCount++;
                System.out.println("[world player="+worldPlayer.id()+"] M5_AUTHORITATIVE_TICK mode=WALK tiles=1 from="+mt.fromX+","+mt.fromY
                                 + " to="+mt.toX+","+mt.toY+" dir="+mt.dir1
                                 + " combatFacing="+(movementFacingTarget==null?"NONE":movementFacingTarget)
                                 + " remaining="+mt.remaining+" movementTick="+movementTickCount+" worldTick="+worldTick);
            }
            if(mt!=null)saveAccountQuiet(tag,"POSITION_TICK");
            if(mt!=null){
                String playerTradeTick=playerInteractions.afterMovement(player81Sync);
                if(playerTradeTick!=null)System.out.println(tag+playerTradeTick);
            }
            String playerAttackTick=playerInteractions.tickAttack(worldTick,sessionPackets,player81Sync);
            if(playerAttackTick!=null)System.out.println(tag+playerAttackTick);
            String bankObjectTick=bankObjectHandler.tick(now,sessionPackets);
            if(bankObjectTick!=null)System.out.println(tag+bankObjectTick);
            String routedNpcTick=routedNpcHandler.tick(now,sessionPackets);
            if(routedNpcTick!=null)System.out.println(tag+routedNpcTick);
            applyGroundItemResult(
                groundItemHandler.tick(now,scenePublisher,sessionPackets),tag);
            tryPickupDeferredPet(sessionPackets, tag, now);
            tryCompletePetPickup(sessionPackets, tag, now);
            if (mt != null) {
                if(pendingPetPickupScene==null){
                    npcs.queueOwnerMovement(mt);
                    // Broken/empty breadcrumb recovery is still active follow work.
                    if(npcs.needsFollow(movement) && nextPetFollowAt==Long.MAX_VALUE) nextPetFollowAt = now + 200L;
                } else {
                    // R8.1 interaction ownership: once Pick-up is clicked the pet must
                    // stop chasing the owner, otherwise the owner approaches a moving
                    // target and the deferred interaction can terminate at a stale tile.
                    nextPetFollowAt=Long.MAX_VALUE;
                }
            }
            legacyTickCount++;
            String npcPulse=npcs.tickHome(movement,sessionPackets,homeWorld,legacyTickCount);
            SharedNpcWorldRelay.syncRemotePets(sessionPackets);
            if(npcPulse!=null && (legacyTickCount==1 || legacyTickCount%25==0 || !npcPulse.contains("worldAdd=0 worldRemove=0 worldWalk=0")))
                System.out.println(tag+"WORLD_R7_"+npcPulse+" sharedWorldTick="+worldTick);
            String combatTick=combat.tick(movement,npcs,equipment,sessionPackets,legacyTickCount,
                combatStyles.current(CombatInterfaceRepository.forWeapon(equipment.weapon())),scenePublisher);
            if(combatTick!=null) {
                System.out.println(tag+"V56_COMBAT "+combatTick+" sharedWorldTick="+worldTick);
                if(combatTick.startsWith("TARGET_CLEARED")) clearOpponentOverlay(sessionPackets,tag,"COMBAT_TARGET_CLEARED");
            }
            int dealt=combat.consumeLastDamage();
            if(dealt>0){
                NpcEntity overlayTarget=npcs.scene(combat.state().targetSceneIndex);
                if(overlayTarget!=null){
                    publishOpponentOverlay(overlayTarget,sessionPackets,tag,"HIT_UPDATE");
                    int baseline=combat.state().context==CombatContext.PLAYER_PVP?100:200;
                    int remoteHitType=dealt>=baseline?6:1;
                }
                String petDamage=petRuntimeCommands.applyDamage(
                    dealt,now,sessionPackets,"COMBAT_M2");
                if(petDamage!=null)System.out.println(tag+petDamage);
            }
            if(petEffects.tick(now) && npcs.pet()!=null && PetPresentationProfile.supportsNativeState(npcs.pet().definitionId)){
                String reset=npcs.setPetNativeState(0,sessionPackets);
                System.out.println(tag+"V59_PET_CHARGE_TIMEOUT_RESET "+reset+" state="+petEffects.summary()+" sharedWorldTick="+worldTick);
            }
            ensurePetFollowScheduled(now);
            ensurePetTestSequenceScheduled(now);
            if (legacyTickCount == 1 || legacyTickCount % 25 == 0) {
                System.out.println(tag + "V5121_WORLD_PULSE tick=" + worldTick + " playerAgeTicks="+legacyTickCount
                                 + " playerId="+worldPlayer.id()+" world="+movement.x()+","+movement.y()
                                 + " queued="+movement.queued()+" members="+world.players().size()
                                 + " certification=M4_CERTIFIED M5_WEAPONS_ACTIVE ENGINE_R2_WORLD_PULSE");
            }
        } finally {
            sessionPackets.endBatch();
        }
    }

    /**
     * R8.1 automatic packet-73 scene streaming. The client owns terrain/object
     * decoding from its current cache; LocalLab only recenters the 104x104 scene
     * before the player reaches its black/unloaded edge. Dynamic SpawnPK overlays
     * outside HOME remain deliberately absent.
     */
    private boolean maybeAutoStreamRegion(ServerPacketWriter w,String tag)throws IOException{
        if(!movementEnabled||world.players().size()!=1)return false;

        // When an exploration window walks well back into HOME, reconnect to the
        // certified HOME view without teleporting the authoritative world position.
        if(movement.transientRegion()&&movement.insideHomeInnerCore(24)){
            movement.restoreHomeWindowAtCurrentPosition();
            w.fixed(219,new byte[0]);
            w.fixed(73,BootstrapPackets.region73(385,436));
            w.varShort(81,BootstrapPackets.player81TeleportNoAppearance(0,movement.y()-MovementState.REGION_BASE_Y,movement.x()-MovementState.REGION_BASE_X));
            scenePublisher=new SceneUpdatePublisher(w,new SceneCoordinateContext(MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y,0));
            HomeObjectOverlayReplayer.Stats scene=homeWorld.replayScene(w,MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y);
            scenePublisher.context().invalidate();
            java.util.List<NpcEntity> homeNpcs=npcs.snapshot();
            if(!homeNpcs.isEmpty())w.varShort(65,NpcSyncEncoder.initial(homeNpcs,movement.x(),movement.y()));
            int replay=0;for(GroundItem g:world.groundItems().snapshot())if(g.owner==null||g.owner.equalsIgnoreCase(username)){scenePublisher.groundSpawn(g);replay++;}
            nextPetFollowAt=Long.MAX_VALUE;petFollowRealtimeScheduled=false;
            System.out.println(tag+"V5181_WORLD_AUTO_HOME_REATTACH world="+movement.x()+","+movement.y()+",0 base="+MovementState.REGION_BASE_X+","+MovementState.REGION_BASE_Y+
                " packet73=385,436 scene={"+scene+"} npcRepublish="+homeNpcs.size()+" groundReplay="+replay+" dynamicOutsideHome=false");
            return true;
        }

        if(!movement.nearLoadedEdge(16))return false;
        int rid=((movement.x()>>6)<<8)|(movement.y()>>6);
        WorldRegionAuthorityRepository.Region r=WorldRegionAuthorityRepository.get(rid);
        if(r==null||!r.mapPresent||!r.terrainParseOk||!WorldCollisionAuthority.hasRegion(rid)){
            System.out.println(tag+"V5181_WORLD_AUTO_REBASE result=FAIL_CLOSED region="+rid+" world="+movement.x()+","+movement.y()+","+movement.plane()+
                " authority="+(r==null?"UNKNOWN":("map="+r.mapPresent+" terrain="+r.terrainParseOk+" collision="+WorldCollisionAuthority.hasRegion(rid))));
            return false;
        }
        int chunkX=movement.x()>>3,chunkY=movement.y()>>3;
        int baseX=(chunkX-6)<<3,baseY=(chunkY-6)<<3;
        if(baseX==movement.loadedBaseX()&&baseY==movement.loadedBaseY())return false;

        boolean leavingHome=!movement.transientRegion();
        int removed=0;
        if(leavingHome){
            java.util.List<NpcEntity> old=npcs.snapshot();removed=old.size();
            if(!old.isEmpty()){
                java.util.ArrayList<NpcSyncEncoder.Update> removals=new java.util.ArrayList<>();
                for(NpcEntity n:old)removals.add(NpcSyncEncoder.Update.remove(n));
                w.varShort(65,NpcSyncEncoder.encode(removals,java.util.Collections.emptyList(),movement.x(),movement.y()));
            }
            nextPetFollowAt=Long.MAX_VALUE;petFollowRealtimeScheduled=false;
            TradeService.cancelIfActive(worldPlayer,"AUTO_REGION_REBASE");playerInteractions.clearTargets();combat.cancelForManualMovement();
        }
        movement.rebaseLoadedWindow(baseX,baseY,true);
        w.fixed(219,new byte[0]);
        w.fixed(73,BootstrapPackets.region73(chunkX,chunkY));
        w.varShort(81,BootstrapPackets.player81TeleportNoAppearance(movement.plane(),movement.y()-baseY,movement.x()-baseX));
        scenePublisher=new SceneUpdatePublisher(w,new SceneCoordinateContext(baseX,baseY,movement.plane()));
        System.out.println(tag+"V5181_WORLD_AUTO_REBASE result=OK region="+rid+" name=["+r.name+"] world="+movement.x()+","+movement.y()+","+movement.plane()+
            " base="+baseX+","+baseY+" packet73="+chunkX+","+chunkY+" removedHomeNpcView="+removed+
            " terrain=CLIENT_CACHE collision=EXACT_CURRENT_STATIC dynamicOverlays=UNRESOLVED_SERVER_AUTHORITY");
        return true;
    }

    private boolean pendingPickupBlocksPetFollow(){
        if(pendingPetPickupScene==null)return false;
        NpcEntity pet=npcs.pet();
        // A freshly dropped pet initially shares the owner tile. Production lets the
        // pet perform its one-tile lifecycle egress before Pick-up completes. That
        // single overlap state is the only pending-Pick-up case allowed to follow.
        return pet==null||pet.sceneIndex!=pendingPetPickupScene.intValue()||pet.x!=movement.x()||pet.y!=movement.y();
    }

    private void ensurePetFollowScheduled(long now){
        if(!bootstrap||movement.transientRegion()||pendingPickupBlocksPetFollow()||petFollowRealtimeScheduled||npcs.followFrozen()||!npcs.needsFollow(movement))return;
        if(nextPetFollowAt==Long.MAX_VALUE)nextPetFollowAt=now+200L;
        long at=Math.max(now,nextPetFollowAt);
        petFollowRealtimeScheduled=true;
        world.realtime().schedule(at,worldPlayer,()->runPetFollowRealtime());
    }

    private void runPetFollowRealtime(){
        petFollowRealtimeScheduled=false;
        if(!bootstrap||movement.transientRegion()||pendingPickupBlocksPetFollow()||sessionPackets==null||npcs.followFrozen()||!npcs.needsFollow(movement)){nextPetFollowAt=Long.MAX_VALUE;return;}
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

    private void acceptPendingInterfaceClose(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        if (!clientPackets.takeInterfaceClose()) return;
        boolean tradeWasOpen=TradeService.cancelIfActive(worldPlayer,"CLIENT_INTERFACE_CLOSE");
        boolean itemLibraryWasOpen=itemLibrary.isOpen();
        itemLibrary.close();
        boolean devPanelWasOpen=devPanel.isOpen()||devPanel.hasPending();
        devPanel.close();
        clearDialogNumberKeys();
        boolean wasOpen = bank.clientClosed();
        boolean compWasOpen = compCapeCustomize.close();
        LocalPetInventoryDialogHandler.CloseState petDialogClose=
            petDialogs.clearAll();
        boolean petColorWasOpen=petDialogClose.petColorWasOpen;
        boolean miniConfigWasOpen=petDialogClose.miniConfigWasOpen;
        boolean petAccessoryWasOpen=petDialogClose.petAccessoryWasOpen;
        // Bank overlay inventory is widget 5064; once the overlay closes the normal
        // inventory is widget 3214. Re-send 3214 so withdrawals remain visible.
        if (wasOpen) bank.sendNormalInventory(serverPackets);
        saveAccountQuiet(tag, "INTERFACE_CLOSE");
        System.out.println(tag + "V522_INTERFACE_CLOSE opcode=130 bankWasOpen="+wasOpen
                         + " bankOpen=false normalInventory3214Refresh="+wasOpen
                         + " compCapeWasOpen="+compWasOpen+" tradeWasOpen="+tradeWasOpen+" itemLibraryWasOpen="+itemLibraryWasOpen+" devPanelWasOpen="+devPanelWasOpen
                         + " petColorWasOpen="+petColorWasOpen+" miniConfigWasOpen="+miniConfigWasOpen+" petAccessoryWasOpen="+petAccessoryWasOpen
                         + " decoderAligned="+clientPackets.isAligned());
    }

    private void acceptPendingActions(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        Integer widget = clientPackets.takeWidgetAction();
        if (widget == null) return;

        if(widget==2458){
            saveAccountQuiet(tag,"LOGOUT_BUTTON");
            serverPackets.fixed(109,new byte[0]);
            logoutRequested=true;
            System.out.println(tag+"V5124_LOGOUT widget=2458 result=S2C109_LOGOUT_DISCONNECT save=true");
            return;
        }

        if(devPanel.isOpen() && (widget==54195 || (widget>=2482 && widget<=2485))){
            handleDevPanelWidget(widget,serverPackets,tag);
            return;
        }

        if(widget==NativeEquipmentDeathUi.EQUIPMENT_STATS_BUTTON){
            String result=NativeEquipmentDeathUi.openEquipmentStats(serverPackets,equipment);
            System.out.println(tag+"V5140_EQUIPMENT_STATS widget="+widget+" result="+result);
            return;
        }
        if(widget==NativeEquipmentDeathUi.DEATH_BUTTON){
            String result=NativeEquipmentDeathUi.openDeathPreview(serverPackets,bank,equipment);
            System.out.println(tag+"V5140_DEATH_PREVIEW widget="+widget+" result="+result);
            return;
        }
        String itemLibraryWidget=itemLibrary.handleWidget(serverPackets,widget);
        if(itemLibraryWidget!=null){
            System.out.println(tag+"V5150_ITEM_LIBRARY_WIDGET widget="+widget+" result="+itemLibraryWidget);
            return;
        }
        String tradeWidget=TradeService.handleWidget(worldPlayer,widget);
        if(tradeWidget!=null){
            System.out.println(tag+"V5140_TRADE_WIDGET widget="+widget+" result="+tradeWidget);
            return;
        }

        String gameplayWidget=gameplayWidgetHandler.handle(widget,serverPackets);
        if(gameplayWidget!=null){
            System.out.println(tag+gameplayWidget);
            return;
        }

        LocalPetInventoryDialogHandler.Result petDialogWidget=
            petDialogs.handleWidget(widget,serverPackets);
        if(petDialogWidget!=null){
            applyPetDialogResult(petDialogWidget,tag);
            return;
        }

        String compCapeWidget=
            compCapeCustomize.handleWidget(widget,serverPackets);
        if(compCapeWidget!=null){
            System.out.println(tag+compCapeWidget);
            return;
        }
        if (widget == 152) {
            if (!movementEnabled) {
                System.out.println(tag + "M5_RUN_TOGGLE widget=152 action=OBSERVE_ONLY movementAuthority=false");
                return;
            }
            boolean enabled = movement.togglePersistentRun();
            serverPackets.fixed(36, BootstrapPackets.config36(173, enabled ? 1 : 0));
            saveAccountQuiet(tag, "RUN_TOGGLE");
            System.out.println(tag + "M5_RUN_TOGGLE widget=152 enabled=" + enabled
                             + " opcode=36 setting=173 value=" + (enabled ? 1 : 0)
                             + " authority=PERSISTENT_ACCOUNT_TOGGLE");
            return;
        }
        if (widget == BankState.DEPOSIT_INVENTORY_WIDGET) {
            String result = bank.depositInventory(serverPackets);
            saveAccountQuiet(tag, "BANK_DEPOSIT_INVENTORY");
            System.out.println(tag + "V4_BANK_WIDGET widget="+widget+" action=DEPOSIT_INVENTORY result="+result);
            return;
        }
        if (widget == BankState.TOGGLE_PLACEHOLDERS_WIDGET) {
            String result = bank.togglePlaceholders(serverPackets);
            saveAccountQuiet(tag, "BANK_PLACEHOLDERS");
            System.out.println(tag + "V4_BANK_WIDGET widget="+widget+" action=TOGGLE_PLACEHOLDERS result="+result);
            return;
        }
        if (widget == 5384 || widget == 5380) {
            bank.close(serverPackets);
            System.out.println(tag + "V4_BANK_WIDGET widget="+widget+" action=CLOSE_BANK bankOpen="+bank.isOpen());
        }
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
        return Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
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

    private void acceptPendingDropItem(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        DropItemAction a=clientPackets.takeDropItem();
        if(a==null) return;
        if(a.widgetId!=BankState.NORMAL_INVENTORY_CONTAINER){
            System.out.println(tag+"V53_ITEM_DROP "+a+" result=OBSERVED_UNSUPPORTED_WIDGET itemRetained=true");
            return;
        }

        String option5=ItemActionResolver.inventoryOption5Semantic(a.itemId);
        if(!"Drop".equalsIgnoreCase(option5)){
            System.out.println(tag+"V511_INVENTORY_OPTION5 "+a+" semantic="+option5+" result=DECODED_NOT_DROP itemRetained=true authority=EXACT_ITEM_ACTION");
            return;
        }

        PetDefinitionRepository.Def def=resolvePetDefinitionForDrop(a.itemId);
        if(def==null){
            if(PetDefinitionRepository.isAmbiguous(a.itemId)){
                System.out.println(tag+"V591_PET_DROP "+a+" result=REJECTED_AMBIGUOUS_MAPPING itemRetained=true evidence="+PetDefinitionRepository.ambiguousEvidence(a.itemId)+
                    " hint=use_::devpet_map_<itemId>_<npcId>_for_session_only_visual_binding");
            } else {
                dropOrdinaryGround(a,serverPackets,tag);
            }
            return;
        }
        BankState.Stack st=bank.inventoryAt(a.slot);
        if(st==null || st.itemId!=a.itemId || st.qty<=0){
            System.out.println(tag+"V53_PET_DROP "+a+" result=REJECTED_INVENTORY_MISMATCH itemRetained=true");
            return;
        }

        boolean replacing=petState.active() || npcs.pet()!=null;
        int oldItem=replacing?petState.itemId():-1;
        int oldNpc=replacing?petState.npcId():-1;
        if(replacing && (!petState.active() || npcs.pet()==null)){
            // Persistent/runtime disagreement must never consume a new pet item.
            System.out.println(tag+"V56_PET_REPLACE "+a+" result=REJECTED_ACTIVE_STATE_MISMATCH itemRetained=true");
            return;
        }

        String inv=bank.consumeInventoryOne(a.slot,a.itemId,serverPackets);
        if(!inv.startsWith("INVENTORY_CONSUME_OK")){
            System.out.println(tag+"V56_PET_DROP "+a+" result="+inv+" itemRetained=true");
            return;
        }

        int restoredOldSlot=-1;
        if(replacing){
            String despawn=npcs.removePet(serverPackets);
            if(voidglass.active()){
                Integer restore=voidglass.clearAndRestoreSelector();
                npcs.devSetParticleSelector(restore,movement,serverPackets);
                System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS state=CLEARED reason=PET_REPLACE restoredFx="+(restore==null?"AUTO":restore));
            }
            restoredOldSlot=bank.addInventoryOnePreferred(oldItem,a.slot,serverPackets);
            if(restoredOldSlot<0){
                int rollbackNew=bank.addInventoryOne(a.itemId,serverPackets);
                throw new IllegalStateException("pet replacement could not restore old item="+oldItem+" despawn="+despawn+" rollbackNew="+rollbackNew);
            }
            petState.clear();
        }

        // A previous Pick-up may have temporarily targeted scene 4 so the player
        // could face the pet during animation 827. Clear that target BEFORE a new
        // scene-4 pet is added, otherwise the client keeps facing the new pet forever.
        clearPetPickupFacingNow(serverPackets,tag,"PET_DROP_PRESPAWN");
        if(def.npcId==1334 || def.npcId==8210){
            String resetFx=LocalDevVisualOverrideStore.set("intrinsicfx",null);
            System.out.println(tag+"V5128_SPECIAL_PET_DEFAULT_ACCESSORY_NONE npc="+def.npcId+" item="+def.itemId+" result="+resetFx+
                " bodyTreatmentPreserved=true accessoryLayer=NONE");
        }
        String spawn=npcs.spawnPet(def,movement,serverPackets);
        if(!spawn.startsWith("PET_SPAWN_OK")){
            int rollbackNew=bank.addInventoryOne(a.itemId,serverPackets);
            // spawnPet has no ordinary failure after preflight once the previous pet
            // has been removed. Keep a loud diagnostic instead of risking item loss.
            System.out.println(tag+"V56_PET_DROP "+a+" result="+spawn+" rollbackNewSlot="+rollbackNew+" oldPetItemAlreadyRestoredSlot="+restoredOldSlot);
            return;
        }
        petState.activate(def);
        petEffects.onPetChanged(def.itemId,def.npcId);
        String accessorySpawn="NONE";
        if(petAccessoryState.activeItem()!=0){
            Integer selector=PetAccessoryAuthority.selector(petAccessoryState.activeItem());
            accessorySpawn=npcs.devSetParticleSelector(selector,movement,serverPackets);
        }
        String miniSpawn=petState.miniConfigured()?miniPets.onMainPetSpawn(petState,npcs,movement,serverPackets):"MINIPET_NONE_CONFIGURED";
        int passiveChanged=syncScopesightPassive(serverPackets);
        // V9.08 production capture: every successful pet Drop uses owner animation 827, no GFX.
        serverPackets.varShort(81,CombatSync.player81AnimationOnly(PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION));
        saveAccountQuiet(tag,replacing?"PET_REPLACE":"PET_DROP_SUMMON");
        System.out.println(tag+(replacing?"V56_PET_REPLACE ":"V56_PET_DROP ")+a+" result="+spawn
                         +" inventoryMutation="+inv
                         +(replacing?" replaced="+oldItem+"->"+oldNpc+" restoredOldItemSlot="+restoredOldSlot:"")
                         +" scopesightSkillMask=0x"+Integer.toHexString(passiveChanged)
                         +" ownerAnim="+PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION+" ownerGfx=NONE"
                         +" accessory="+(petAccessoryState.activeItem()==0?"NONE":petAccessoryState.activeItem()+"/selector"+PetAccessoryAuthority.selector(petAccessoryState.activeItem())+"/"+accessorySpawn)
                         +" mini="+miniSpawn+" persistent="+persistentAccount);
    }

    private void dropOrdinaryGround(DropItemAction a,ServerPacketWriter serverPackets,String tag)throws IOException {
        BankState.Stack st=bank.inventoryAt(a.slot);
        if(st==null||st.itemId!=a.itemId||st.qty<=0){System.out.println(tag+"V511_GROUND_DROP "+a+" result=REJECTED_INVENTORY_MISMATCH itemRetained=true");return;}
        if(st.qty>0xffff){System.out.println(tag+"V511_GROUND_DROP "+a+" result=REJECTED_WIRE_AMOUNT_GT_65535 qty="+st.qty+" itemRetained=true authority=S2C44_U16_AMOUNT");return;}
        Tile tile=new Tile(movement.x(),movement.y(),0);
        GroundItem before=world.groundItems().findOwned(a.itemId,tile.x,tile.y,tile.plane,username);
        int oldAmount=before==null?0:before.amount;
        if((long)oldAmount+st.qty>0xffff){System.out.println(tag+"V511_GROUND_DROP "+a+" result=REJECTED_MERGED_WIRE_AMOUNT_GT_65535 existing="+oldAmount+" qty="+st.qty+" itemRetained=true");return;}
        int qty=bank.consumeInventoryAll(a.slot,a.itemId,serverPackets); if(qty<=0){System.out.println(tag+"V511_GROUND_DROP "+a+" result=REJECTED_CONSUME_FAILED itemRetained=true");return;}
        GroundItem g=world.groundItems().add(a.itemId,qty,tile,username,sessionWorldTick,false);
        if(oldAmount>0)scenePublisher.groundAmount(g,oldAmount); else scenePublisher.groundSpawn(g);
        saveAccountQuiet(tag,"GROUND_DROP");
        System.out.println(tag+"V511_GROUND_DROP "+a+" result=DROP_OK amount="+qty+" world="+tile+" registryId="+g.id+" policy=LOCAL_PERSIST_UNTIL_PICKED owner="+username);
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

    private boolean cardinalAdjacentTo(int x,int y){
        return Math.abs(x-movement.x())+Math.abs(y-movement.y())==1;
    }

    private void freezePetFollowForPickup(String tag){
        if(petPickupOwnedFollowFreeze||npcs.followFrozen())return;
        npcs.devFollowFreeze(true);petPickupOwnedFollowFreeze=true;nextPetFollowAt=Long.MAX_VALUE;
        System.out.println(tag+"V5181_PET_PICKUP_FOLLOW_FREEZE owned=true reason=APPROACH_TARGET_STABILITY");
    }

    private void releasePetFollowAfterPickup(String tag,String reason){
        if(!petPickupOwnedFollowFreeze)return;
        npcs.devFollowFreeze(false);petPickupOwnedFollowFreeze=false;nextPetFollowAt=Long.MAX_VALUE;
        if(npcs.pet()!=null&&npcs.needsFollow(movement))ensurePetFollowScheduled(System.currentTimeMillis());
        System.out.println(tag+"V5181_PET_PICKUP_FOLLOW_FREEZE owned=false reason="+reason);
    }

    private void tryPickupDeferredPet(ServerPacketWriter serverPackets,String tag,long now)throws IOException{
        Integer scene=pendingPetPickupScene;
        if(scene==null)return;
        if(pendingPetPickupCompleteAtMs!=Long.MAX_VALUE)return;
        NpcEntity pet=npcs.pet();
        if(pet==null || pet.sceneIndex!=scene || !petState.active() || now>pendingPetPickupDeadlineMs){
            pendingPetPickupScene=null;releasePetFollowAfterPickup(tag,"MISSING_OR_TIMEOUT");
            System.out.println(tag+"V5126_PET_PICKUP scene="+scene+" action=CANCELLED_MISSING_OR_TIMEOUT");
            return;
        }
        if(!cardinalAdjacentTo(pet.x,pet.y)){
            // Immediately after Drop the pet is deliberately introduced on the
            // owner's exact tile and then takes its one-tile lifecycle egress WALK.
            // A fast Pick-up click during that brief overlap must wait for that
            // egress rather than being treated as a failed approach route.
            if(pet.x==movement.x() && pet.y==movement.y()) return;
            if(movement.queued()==0){
                pendingPetPickupScene=null;releasePetFollowAfterPickup(tag,"PATH_ENDED_NOT_ADJACENT");
                System.out.println(tag+"V5126_PET_PICKUP scene="+scene+" action=CANCELLED_PATH_ENDED_NOT_CARDINAL_ADJACENT owner="+
                    movement.x()+","+movement.y()+" pet="+pet.x+","+pet.y);
            }
            return;
        }
        movement.clearQueuedPath();
        executePetPickupNow(new NpcAction(155,scene),serverPackets,tag,now,"PICKUP_AFTER_CARDINAL_ARRIVAL");
    }

    private void executePetPickupNow(NpcAction a,ServerPacketWriter serverPackets,String tag,long now,String reason)throws IOException{
        NpcEntity pet=npcs.pet();
        if(pet==null || !petState.active() || a==null || a.sceneIndex!=pet.sceneIndex){
            pendingPetPickupScene=null;releasePetFollowAfterPickup(tag,"STALE_TARGET");
            System.out.println(tag+"V5126_PET_PICKUP "+a+" result=CANCELLED_STALE_TARGET reason="+reason);
            return;
        }
        if(!cardinalAdjacentTo(pet.x,pet.y)){
            System.out.println(tag+"V5126_PET_PICKUP "+a+" result=REJECTED_NOT_CARDINAL_ADJACENT reason="+reason);
            return;
        }
        if(!bank.canAddInventoryOne(petState.itemId())){
            pendingPetPickupScene=null;releasePetFollowAfterPickup(tag,"INVENTORY_FULL");
            System.out.println(tag+"V56_PET_PICKUP "+a+" result=REJECTED_INVENTORY_FULL petRemains=true reason="+reason);
            return;
        }
        int item=petState.itemId(),npc=petState.npcId();
        // V9.12 production measurement: pickup facing is NOT interaction-target m.
        // S2C81 carries animation 827 + one-shot turn-to-tile Q/R where
        // Q=2*petWorldX+1 and R=2*petWorldY+1. Pet removal then follows immediately.
        serverPackets.varShort(81,Player81MeasuredSync.animationAndTurnToTile(
            PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION,pet.x,pet.y));
        int q=pet.x*2+1,r=pet.y*2+1;
        pendingPetPickupItem=item; pendingPetPickupNpc=npc; pendingPetPickupCompleteScene=pet.sceneIndex;
        pendingPetPickupCompleteReason=reason; pendingPetPickupCompleteAtMs=now+PET_PICKUP_REMOVE_DELAY_MS;
        pendingPetFacingClearAtMs=Long.MAX_VALUE;
        System.out.println(tag+"V51213_PET_PICKUP "+a+" result=TURN_TILE_ANIM_AND_REMOVE_SYNCHRONIZED item="+item+" npc="+npc+
                         " turnQ="+q+" turnR="+r+" petTile="+pet.x+","+pet.y+
                         " ownerAnim="+PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION+" ownerGfx=NONE"+
                         " interactionTargetUsed=false removeAfterMs=0 sameWorldTick=true reason="+reason);
        tryCompletePetPickup(serverPackets,tag,now);
    }

    private void tryCompletePetPickup(ServerPacketWriter serverPackets,String tag,long now)throws IOException {
        if(pendingPetPickupCompleteAtMs==Long.MAX_VALUE || now<pendingPetPickupCompleteAtMs)return;
        NpcEntity pet=npcs.pet(); int scene=pendingPetPickupCompleteScene;
        if(pet==null || pet.sceneIndex!=scene || !petState.active()){
            pendingPetPickupCompleteAtMs=Long.MAX_VALUE; pendingPetPickupScene=null;releasePetFollowAfterPickup(tag,"COMPLETE_STALE");
            System.out.println(tag+"V5127_PET_PICKUP_COMPLETE scene="+scene+" result=CANCELLED_STALE"); return;
        }
        int item=pendingPetPickupItem,npc=pendingPetPickupNpc; String reason=pendingPetPickupCompleteReason;
        String despawn=npcs.removePet(serverPackets);
        if(voidglass.active()){Integer restore=voidglass.clearAndRestoreSelector();npcs.devSetParticleSelector(restore,movement,serverPackets);System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS state=CLEARED reason=PET_PICKUP restoredFx="+(restore==null?"AUTO":restore));}
        int dst=bank.addInventoryOne(item,serverPackets); if(dst<0)throw new IllegalStateException("pet pickup inventory preflight mismatch");
        petState.clear();petEffects.clear();pendingPetPickupScene=null;
        pendingPetPickupCompleteAtMs=Long.MAX_VALUE;pendingPetPickupItem=-1;pendingPetPickupNpc=-1;pendingPetPickupCompleteScene=-1;pendingPetPickupCompleteReason=null;
        releasePetFollowAfterPickup(tag,"PICKUP_COMPLETE");
        int passiveChanged=syncScopesightPassive(serverPackets);saveAccountQuiet(tag,"PET_PICKUP");
        System.out.println(tag+"V5127_PET_PICKUP_COMPLETE result="+despawn+" restoredItem="+item+" inventorySlot="+dst+" npc="+npc+
            " scopesightSkillMask=0x"+Integer.toHexString(passiveChanged)+" reason="+reason+" persistent="+persistentAccount);
    }

    private void tryClearPetPickupFacing(ServerPacketWriter serverPackets,String tag,long now)throws IOException {
        if(pendingPetFacingClearAtMs==Long.MAX_VALUE || now<pendingPetFacingClearAtMs)return;
        serverPackets.varShort(81,CombatSync.player81InteractionOnly(-1));
        pendingPetFacingClearAtMs=Long.MAX_VALUE;
        System.out.println(tag+"V5126_PET_PICKUP_FACING_CLEAR target=-1 reason=POST_PICKUP_PRESENTATION");
    }

    private void clearPetPickupFacingNow(ServerPacketWriter serverPackets,String tag,String reason)throws IOException {
        if(pendingPetFacingClearAtMs==Long.MAX_VALUE)return;
        serverPackets.varShort(81,CombatSync.player81InteractionOnly(-1));
        pendingPetFacingClearAtMs=Long.MAX_VALUE;
        System.out.println(tag+"V5126_PET_PICKUP_FACING_CLEAR target=-1 reason="+reason);
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
        if(isPetPickupAction(a,pet,petState)){
            if(!bank.canAddInventoryOne(petState.itemId())){
                System.out.println(tag+"V56_PET_PICKUP "+a+" result=REJECTED_INVENTORY_FULL petRemains=true");
                return;
            }
            pendingPetPickupScene=pet.sceneIndex;
            pendingPetPickupDeadlineMs=System.currentTimeMillis()+10_000L;
            // Freeze ordinary follower advancement while the interaction route owns
            // this pet. Existing breadcrumbs must not make the target walk away.
            if(!cardinalAdjacentTo(pet.x,pet.y) && !(pet.x==movement.x()&&pet.y==movement.y()))freezePetFollowForPickup(tag);
            nextPetFollowAt=Long.MAX_VALUE;
            if(cardinalAdjacentTo(pet.x,pet.y)){
                System.out.println(tag+"V51213_PET_PICKUP "+a+" result=QUEUED_FOR_NEXT_AUTHORITATIVE_WORLD_TICK"+
                    " owner="+movement.x()+","+movement.y()+" pet="+pet.x+","+pet.y);
            } else {
                System.out.println(tag+"V51213_PET_PICKUP "+a+" result=DEFERRED_UNTIL_CARDINAL_ADJACENT distanceCheb="+
                    chebyshev(movement.x(),movement.y(),pet.x,pet.y)+" owner="+movement.x()+","+movement.y()+" pet="+pet.x+","+pet.y);
            }
            return;
        }

        // Any different NPC interaction supersedes a deferred pet pickup and the
        // temporary post-pickup facing target. Clear the stale scene target BEFORE
        // assigning a new combat/bank interaction target so scene-index reuse cannot
        // wipe the new target on the following world tick.
        if(pendingPetPickupScene!=null && pendingPetPickupCompleteAtMs==Long.MAX_VALUE){
            Integer cancelled=pendingPetPickupScene; pendingPetPickupScene=null;releasePetFollowAfterPickup(tag,"NEW_NPC_INTERACTION");
            System.out.println(tag+"V5127_PET_PICKUP scene="+cancelled+" action=CANCELLED_BY_NEW_NPC_INTERACTION new="+a);
        }

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


    static boolean isPetPickupAction(NpcAction a,NpcEntity pet,PetState petState){
        return a!=null && a.opcode==155 && pet!=null && petState!=null && petState.active() && a.sceneIndex==pet.sceneIndex;
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

    private void acceptPendingCommand(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        String command = clientPackets.takeCommand();
        if (command == null) return;
        LocalBankRequestHandler.Result bankCommand=bankRequests.handleCommand(command,serverPackets);
        if(bankCommand!=null){
            if(bankCommand.saveReason!=null)saveAccountQuiet(tag,bankCommand.saveReason);
            System.out.println(tag+bankCommand.logText+" decoderAligned="+clientPackets.isAligned());
            return;
        }

        String clean=command.trim();
        if (clean.startsWith("::")) clean=clean.substring(2);
        String[] p=clean.split("\\s+");

        if(p.length>=1 && (p[0].equalsIgnoreCase("devpanel")||p[0].equalsIgnoreCase("devui")||p[0].equalsIgnoreCase("lab")||(p[0].equalsIgnoreCase("dev")&&p.length>=2&&p[1].equalsIgnoreCase("panel")))){
            openDevPanel(DevControlCenter.Page.MAIN,serverPackets);
            System.out.println(tag+"V5172_DEV_PANEL_OPEN route="+p[0]+" authority="+ContentAuthorityRepository.summary()+" runtimeWeaponProfiles="+V913WeaponRuntimeAuthority.count());
            return;
        }

        if(diagnosticCommands.handle(
            p,serverPackets,tag,username,loginAlias,persistentAccount,scenePublisher))return;
        LocalRegionDevCommandHandler.Result regionDevCommand=
            regionDevCommands.handle(p,username,scenePublisher,serverPackets);
        if(regionDevCommand!=null){
            if(regionDevCommand.scenePublisher!=null)
                scenePublisher=regionDevCommand.scenePublisher;
            if(regionDevCommand.saveReason!=null)
                saveAccountQuiet(tag,regionDevCommand.saveReason);
            System.out.println(tag+regionDevCommand.logText);
            return;
        }
        if(prayerMagicCommands.handle(p,clean,serverPackets,tag))return;
        if(p.length>=1 && p[0].equalsIgnoreCase("authority")){
            System.out.println(tag+"V5124_AUTHORITY "+AuthorityR16R25Publisher.status()+
                " bankWrapperExact="+BankState.BANK_WRAPPER_ROOT+" bankRuntimeRoot="+BankState.BANK_ROOT+
                " combatProfiles="+CombatStyleRepository.rootCount()+" combatStyles="+CombatStyleRepository.countStyles()+
                " note=R25_core_17_59_plus_independent_exact_staff328_3; unproven_server_mechanics_remain_fail_closed");
            return;
        }
        LocalMiniPetCommandHandler.Result miniPetCommand=miniPetCommands.handle(p,serverPackets);
        if(miniPetCommand!=null){
            if(miniPetCommand.saveReason!=null)saveAccountQuiet(tag,miniPetCommand.saveReason);
            System.out.println(tag+miniPetCommand.logText);
            return;
        }
        LocalCosmeticCommandHandler.Result cosmeticCommand=
            cosmeticCommands.handle(p,username,serverPackets);
        if(cosmeticCommand!=null){
            if(cosmeticCommand.saveReason!=null)saveAccountQuiet(tag,cosmeticCommand.saveReason);
            System.out.println(tag+cosmeticCommand.logText);
            return;
        }
        if(devWorldCommands.handle(p,scenePublisher,username,sessionWorldTick,tag))return;

        if(dev.trace().enabled() && p.length>0 && p[0].toLowerCase(java.util.Locale.ROOT).startsWith("dev"))
            dev.trace().record("DEV_COMMAND_REQUEST","C2S103 command=\""+clean+"\" -> router="+p[0],"EXACT_C2S103_TRANSPORT/LOCAL_DEV_ROUTE");

        // v5.9.1 session-only Dev Authority Workbench. These routes never persist
        // experimental presentation values into opensrc.properties.
        String devSessionCommand=
            devSessionCommands.handle(
                p,username,scenePublisher,serverPackets);
        if(devSessionCommand!=null){
            System.out.println(tag+devSessionCommand);
            return;
        }
        java.util.List<String> devPetCommand=
            devPetCommands.handle(p,serverPackets);
        if(devPetCommand!=null){
            for(String line:devPetCommand)System.out.println(tag+line);
            return;
        }
        java.util.List<String> devPlayerCommand=
            devPlayerCommands.handle(p,username,serverPackets);
        if(devPlayerCommand!=null){
            for(String line:devPlayerCommand)System.out.println(tag+line);
            return;
        }
        java.util.List<String> devNpcCommand=
            devNpcCommands.handle(p,serverPackets);
        if(devNpcCommand!=null){
            for(String line:devNpcCommand)System.out.println(tag+line);
            return;
        }
        java.util.List<String> devToolCommand=
            devToolCommands.handle(p,serverPackets);
        if(devToolCommand!=null){
            for(String line:devToolCommand)System.out.println(tag+line);
            return;
        }
        LocalNurseCommandHandler.Result nurseCommand=
            nurseCommands.handle(p,command,scopesightActive(),serverPackets);
        if(nurseCommand!=null){
            if(nurseCommand.saveReason!=null)saveAccountQuiet(tag,nurseCommand.saveReason);
            System.out.println(tag+nurseCommand.logText);
            return;
        }
        if (p.length>=1 && p[0].equalsIgnoreCase("appfixture")) {
            String which=p.length>=2?p[1]:"help";
            String appResult=ApplicationUiFixtureService.run(which,serverPackets);
            System.out.println(tag+"R85_APP_FIXTURE "+appResult+" authority=LOCAL_DEV_FIXTURE clientProtocol=EXACT_CURRENT");
            return;
        }
        LocalVoidglassCommandHandler.Outcome voidglassCommand=
            voidglassCommands.handle(p,serverPackets);
        if(voidglassCommand!=null){
            if(voidglassCommand.saveReason!=null)
                saveAccountQuiet(tag,voidglassCommand.saveReason);
            System.out.println(tag+voidglassCommand.text);
            return;
        }

        java.util.List<String> petRuntimeCommand=
            petRuntimeCommands.handle(p,serverPackets);
        if(petRuntimeCommand!=null){
            for(String line:petRuntimeCommand)System.out.println(tag+line);
            return;
        }

        LocalCompColorsCommandHandler.Result compColorsCommand=
            compColorsCommands.handle(p,command,username,serverPackets);
        if(compColorsCommand!=null){
            if(compColorsCommand.saveReason!=null)saveAccountQuiet(tag,compColorsCommand.saveReason);
            System.out.println(tag+compColorsCommand.logText);
            return;
        }
        java.util.List<String> combatCommand=
            combatCommands.handle(p,command,serverPackets);
        if(combatCommand!=null){
            for(String line:combatCommand)System.out.println(tag+line);
            return;
        }
        LocalPetCompatibilityCommandHandler.Outcome petCompatibilityCommand=
            petCompatibilityCommands.handle(p,serverPackets);
        if(petCompatibilityCommand!=null){
            if(petCompatibilityCommand.dialogResult!=null){
                applyPetDialogResult(
                    petCompatibilityCommand.dialogResult,
                    tag);
            }else{
                if(petCompatibilityCommand.saveReason!=null)
                    saveAccountQuiet(tag,petCompatibilityCommand.saveReason);
                if(petCompatibilityCommand.logText!=null)
                    System.out.println(tag+petCompatibilityCommand.logText);
            }
            return;
        }

        LocalItemSpawnCommandHandler.Result itemSpawnCommand=
            itemSpawnCommands.handle(p,command,serverPackets);
        if(itemSpawnCommand!=null){
            if(itemSpawnCommand.saveReason!=null)saveAccountQuiet(tag,itemSpawnCommand.saveReason);
            System.out.println(tag+itemSpawnCommand.logText+
                " decoderAligned="+clientPackets.isAligned());
        }
    }


    private PetDefinitionRepository.Def resolvePetDefinitionForDrop(int itemId){
        Integer override=dev.petNpcBinding(itemId);
        PetDefinitionRepository.Def base=PetDefinitionRepository.get(itemId);
        if(override==null) return base;
        if(base==null){
            // Behemoth-family workbench can deliberately bind an otherwise ambiguous
            // inventory item without persisting the experimental association.
            PetDefinitionRepository.Def template=PetDefinitionRepository.get(24019);
            if(template==null)return null;
            return new PetDefinitionRepository.Def(itemId,override,ItemCatalog.name(itemId),"DEV NPC "+override,
                template.standAnim,template.walkAnim,template.turn180Anim,template.turn90CWAnim,template.turn90CCWAnim,template.size,template.models,
                "V593_SESSION_DEV_NPC_BINDING_NOT_PERSISTED");
        }
        return new PetDefinitionRepository.Def(base.itemId,override,base.itemName,"DEV NPC "+override,
            base.standAnim,base.walkAnim,base.turn180Anim,base.turn90CWAnim,base.turn90CCWAnim,base.size,base.models,
            base.provenance+"+V593_SESSION_DEV_NPC_BINDING");
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

    private void acceptPendingMovement(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        MovementRequest req = clientPackets.takeMovement();
        if (req == null) return;
        if (!movementEnabled) {
            System.out.println(tag + "M5_MOVEMENT_REQUEST " + req + " action=OBSERVE_ONLY");
            return;
        }
        boolean bankWasOpen=bank.isOpen();
        if(bankWasOpen){
            bank.close(serverPackets);
            System.out.println(tag+"V5122_BANK_CLOSE_ON_MOVEMENT opcode="+req.opcode+" final="+req.finalX()+","+req.finalY()+" normalInventory3214Refresh=true");
        }
        if(petDialogs.hasAnyOpen() || devPanel.isOpen()){
            serverPackets.fixed(219,new byte[0]);
            LocalPetInventoryDialogHandler.CloseState petDialogClose=
                petDialogs.clearAll();
            boolean mini=petDialogClose.miniConfigWasOpen;
            boolean color=petDialogClose.petColorWasOpen;
            boolean accessory=petDialogClose.petAccessoryWasOpen;
            boolean panel=devPanel.isOpen();
            devPanel.close();
            clearDialogNumberKeys();
            System.out.println(tag+"V5170_DIALOG_CLOSE_ON_MOVEMENT mini="+mini+" petColor="+color+" petAccessory="+accessory+" devPanel="+panel+" opcode="+req.opcode);
        }
        int clientStartX=req.waypointCount()>0?req.x[0]:movement.x();
        int clientStartY=req.waypointCount()>0?req.y[0]:movement.y();
        int startDrift=chebyshev(movement.x(),movement.y(),clientStartX,clientStartY);
        if(startDrift>1)
            System.out.println(tag+"V5123_MOVEMENT_START_DRIFT observeOnly=true clientStart="+clientStartX+","+clientStartY+" authority="+movement.x()+","+movement.y()+" chebyshev="+startDrift+" queuedBefore="+movement.queued()+" final="+req.finalX()+","+req.finalY());
        long now=System.currentTimeMillis();
        if(combat.consumeImmediateApproachEcho(req,now)){
            // The server-owned combat route is already active. Do not let the
            // stock client's immediate interaction-route echo replace it.
            System.out.println(tag+"V5123_COMBAT_APPROACH_ECHO_IGNORED "+combat.approachEchoSummary(req)+" weapon="+equipment.weapon()+" authorityWorld="+movement.x()+","+movement.y());
            return;
        }
        if(combat.active()){
            boolean cancelled=combat.cancelForManualMovement();
            if(cancelled){
                // Clear the client-side interaction target as well as server state.
                // This prevents stale target/facing state from trying to resurrect
                // the dummy after the player deliberately clicks the ground.
                serverPackets.varShort(81,CombatSync.player81InteractionOnly(-1));
                clearOpponentOverlay(serverPackets,tag,"MANUAL_MOVEMENT");
                System.out.println(tag+"V5123_COMBAT_CANCEL_ON_MANUAL_MOVEMENT final="+req.finalX()+","+req.finalY()+" weapon="+equipment.weapon()+" clientInteractionTarget=CLEAR");
            }
        }
        LocalPlayerInteractionHandler.Cancellation playerCancel=playerInteractions.cancelActive();
        if(playerCancel.hadAnything()){
            if(playerCancel.hadFacingInteraction)serverPackets.varShort(81,CombatSync.player81InteractionOnly(-1));
            System.out.println(tag+"V5141_PLAYER_INTERACTION_CANCEL reason=MANUAL_MOVEMENT clientInteractionTarget="+
                (playerCancel.hadFacingInteraction?"CLEAR":"UNCHANGED")+" pendingTrade="+playerCancel.hadTrade);
        }
        if(pendingPetPickupCompleteAtMs!=Long.MAX_VALUE){
            pendingPetPickupCompleteAtMs=Long.MAX_VALUE;pendingPetPickupItem=-1;pendingPetPickupNpc=-1;pendingPetPickupCompleteScene=-1;pendingPetPickupCompleteReason=null;pendingPetPickupScene=null;
            releasePetFollowAfterPickup(tag,"MOVEMENT_AFTER_PICKUP_ANIMATION");
            System.out.println(tag+"V5127_PET_PICKUP_COMPLETE action=CANCELLED_BY_MOVEMENT petRemains=true");
        }
        clearPetPickupFacingNow(serverPackets,tag,"NEW_MOVEMENT_INTENT");
        boolean replacingLiveRoute = npcs.pet()!=null && (movement.queued()>0 || npcs.needsFollow(movement));
        String result = movement.accept(req);
        String petRouteReset="NONE";
        if(result.startsWith("ACCEPTED") && replacingLiveRoute)
            petRouteReset=npcs.onOwnerRouteReplaced();
        System.out.println(tag + "M5_MOVEMENT_REQUEST " + req + " action=" + result
                         + " authorityWorld="+movement.x()+","+movement.y()+" petRoute="+petRouteReset);
    }
}
