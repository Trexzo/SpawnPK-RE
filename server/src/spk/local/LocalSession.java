package spk.local;

import java.io.*;
import java.net.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

final class LocalSession implements Runnable {
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
    /** Engine R7 one-stop in-game developer control center. */
    private final DevControlCenter devPanel = new DevControlCenter();
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
    private ObjectInteraction pendingBankInteraction;
    private Integer pendingBankNpcScene;
    private long pendingBankNpcDeadlineMs;
    private GroundItemInteraction pendingGroundTake;
    private long pendingGroundTakeDeadlineMs;
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
    private long pendingBankDeadlineMs;
    private String username = AccountStore.CANONICAL_USERNAME;
    private String loginAlias = "localtest";
    private boolean persistentAccount;
    private boolean compCapeCustomizeOpen;
    private int pendingPetColorSlot=-1;
    private int[] pendingPetColorItems;
    private String pendingPetColorFamily;
    private int pendingMiniConfigureSlot=-1,pendingMiniConfigureItem=-1;
    private int pendingPetAccessorySlot=-1,pendingPetAccessoryItem=-1;
    /** Persisted semantic global pet accessory. 0 means none. Visual selector mapping remains evidence-gated. */
    private int activePetAccessoryItem;
    /** Engine R3 per-view remote-player synchronization context. */
    private Player81WorldSync.Context player81Sync;
    private EntityId activePlayerFollow;
    private EntityId activePlayerAttack;
    private EntityId activePlayerTrade;
    private long nextPlayerAttackTick;
    private int petTestSequenceStep=-1;
    private long petTestSequenceAt=Long.MAX_VALUE;
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
        if (movementEnabled && !bootstrap) throw new IllegalArgumentException("movement requires bootstrap");
    }

    @Override public void run() {
        String tag = "[session " + socket.getRemoteSocketAddress() + "] ";
        try (socket; InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            LoginFrame frame = LocalLoginTransport.readLogin(socket,in,out,tag);
            loginAlias = frame.username == null || frame.username.isEmpty() ? "localtest" : frame.username;

            // v5.12.3: two persistent localhost profiles without requiring a second
            // client configuration. The first canonical/localtest login is opensrc;
            // while opensrc is online, the next identical login becomes src. An
            // explicitly supplied src login also selects that profile directly.
            username = LocalAccountProfiles.chooseForLogin(world,loginAlias);
            persistentAccount = LocalAccountProfiles.isPersistent(username);
            if(!username.equalsIgnoreCase(loginAlias) && !loginAlias.equalsIgnoreCase("localtest"))
                System.out.println(tag+"V5123_LOCAL_PROFILE_ALIAS loginAlias="+loginAlias+" selected="+username+" reason="+(username.equalsIgnoreCase(LocalAccountProfiles.SECONDARY)?"PRIMARY_ALREADY_ONLINE":"CANONICAL_ALIAS"));
            else if(username.equalsIgnoreCase(LocalAccountProfiles.SECONDARY))
                System.out.println(tag+"V5123_LOCAL_PROFILE_ALIAS loginAlias="+loginAlias+" selected=src reason=PRIMARY_ALREADY_ONLINE");
            if (persistentAccount) {
                try {
                    System.out.println(tag + "V5123_ACCOUNT " + LocalAccountProfiles.load(username,bank,equipment,movement,petState,playerState));
                    int persistedAccessory=PetAccessoryPersistence.load(username);
                    activePetAccessoryItem=isPetAccessoryItem(persistedAccessory)?persistedAccessory:0;
                    System.out.println(tag+"V5131_PET_ACCESSORY_PERSIST_LOAD item="+(activePetAccessoryItem==0?"NONE":activePetAccessoryItem)+" authority=ACCOUNT_SEMANTIC_STATE");
                }
                catch (Throwable e) {
                    System.err.println(tag + "V5123_ACCOUNT_LOAD_FAILED file="+LocalAccountProfiles.accountFile(username)+" profile="+username+" error="+e+" action=KEEP_DEFAULTS");
                }
            }

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

            LocalLoginTransport.Ciphers loginCiphers=LocalLoginTransport.ciphers(frame);
            outboundPackets = new OutboundPacketQueue();
            ServerPacketWriter serverPackets = new ServerPacketWriter(outboundPackets, loginCiphers.serverToClient);
            sessionPackets=serverPackets;
            scenePublisher = new SceneUpdatePublisher(serverPackets,new SceneCoordinateContext(MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y,0));
            ClientPacketProbe clientPackets = new ClientPacketProbe(in, loginCiphers.clientToServer, tag);
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
                    if(petState.active() && activePetAccessoryItem!=0){
                        Integer selector=petAccessorySelector(activePetAccessoryItem);
                        String visual=npcs.devSetParticleSelector(selector,movement,serverPackets);
                        System.out.println(tag+"V5131_PET_ACCESSORY_PERSIST_RESTORE item="+activePetAccessoryItem+" selector="+selector+" visual={"+visual+"} authority=ACCOUNT_SEMANTIC_STATE");
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
            if(pendingPetColorItems!=null || pendingMiniConfigureItem>=0 || pendingPetAccessoryItem>=0 || devPanel.isOpen()) clearDialogNumberKeys();
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
            preparePlayerInteractionTick(worldTick);
            MovementState.Tick mt = movementEnabled ? movement.advance() : null;
            Integer measuredApproachTarget = mt==null ? null : combat.consumeApproachFacingTargetForMovement();
            Integer playerApproachTarget = mt==null ? null : playerInteractionTargetForMovement();
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
            if(mt!=null)tryDispatchPendingPlayerTradeAfterMovement(tag);
            tickPlayerAttack(worldTick,sessionPackets,tag);
            tryOpenDeferredBank(sessionPackets, tag, now);
            tryOpenDeferredNpcBank(sessionPackets, tag, now);
            tryTakeDeferredGround(sessionPackets, tag, now);
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
                applyPetDamage(dealt,now,sessionPackets,tag,"COMBAT_M2");
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
            TradeService.cancelIfActive(worldPlayer,"AUTO_REGION_REBASE");activePlayerFollow=null;activePlayerAttack=null;activePlayerTrade=null;combat.cancelForManualMovement();
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
        if(!bootstrap||petTestRealtimeScheduled||petTestSequenceStep<0||sessionPackets==null)return;
        long at=Math.max(now,petTestSequenceAt==Long.MAX_VALUE?now:petTestSequenceAt);
        petTestRealtimeScheduled=true;
        world.realtime().schedule(at,worldPlayer,()->{
            petTestRealtimeScheduled=false;
            if(petTestSequenceStep<0)return;
            long when=System.currentTimeMillis();
            try{tickPetTestSequence(when,sessionPackets,"[session "+socket.getRemoteSocketAddress()+"] ");}
            catch(Throwable t){petTestSequenceStep=-1;petTestSequenceAt=Long.MAX_VALUE;System.err.println("[world player="+worldPlayer.id()+"] pet-test sequence failed: "+t);}
            if(petTestSequenceStep>=0)ensurePetTestSequenceScheduled(when);
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
        boolean compWasOpen = compCapeCustomizeOpen;
        boolean petColorWasOpen=pendingPetColorItems!=null;
        boolean miniConfigWasOpen=pendingMiniConfigureItem>=0;
        boolean petAccessoryWasOpen=pendingPetAccessoryItem>=0;
        if(petColorWasOpen)clearPetColorDialog();
        if(miniConfigWasOpen)clearMiniConfigureDialog();
        if(petAccessoryWasOpen)clearPetAccessoryDialog();
        compCapeCustomizeOpen = false;
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

        PrayerDefinitionRepository.Def prayer=PrayerDefinitionRepository.byWidget(widget);
        if(prayer!=null){
            String result=prayers.click(prayer,playerState,serverPackets);
            System.out.println(tag+"V510_PRAYER_WIDGET widget="+widget+" result="+result+" state={"+prayers.summary()+"}");
            return;
        }

        int currentCombatRoot=CombatInterfaceRepository.forWeapon(equipment.weapon());
        CombatStyleRepository.Style style=CombatStyleRepository.byWidget(currentCombatRoot,widget);
        if(style!=null){
            String result=combatStyles.click(currentCombatRoot,widget,serverPackets);
            System.out.println(tag+"V510_COMBAT_STYLE widget="+widget+" weapon="+equipment.weapon()+" result="+result);
            return;
        }

        MagicState.Check directSpell=magic.direct(widget,bank,equipment,playerState);
        if(directSpell.handled){
            System.out.println(tag+"V510_MAGIC_DIRECT widget="+widget+" result="+directSpell.message+" state={"+magic.summary()+"}");
            return;
        }

        if(pendingMiniConfigureItem>=0 && (widget==2482||widget==2483||widget==2484||widget==2485||widget==54195)){
            if(widget==54195||widget==2484||widget==2485){
                serverPackets.fixed(219,new byte[0]);
                System.out.println(tag+"V5127_MINIPET_CONFIGURE_DIALOG item="+pendingMiniConfigureItem+" action=CANCEL widget="+widget);
                clearMiniConfigureDialog();
                return;
            }
            int item=pendingMiniConfigureItem;
            if(widget==2482){
                String result=miniPets.configure(item,petState,npcs,movement,serverPackets);
                saveAccountQuiet(tag,"MINIPET_CONFIGURE");
                serverPackets.fixed(219,new byte[0]);
                System.out.println(tag+"V5127_MINIPET_CONFIGURE_DIALOG item="+item+" action=ACTIVATE result="+result+" actorRequiresMainPet=true");
                clearMiniConfigureDialog();
                return;
            }
            if(widget==2483){
                String result=miniPets.off(petState,npcs,serverPackets);
                saveAccountQuiet(tag,"MINIPET_DISABLE");
                serverPackets.fixed(219,new byte[0]);
                System.out.println(tag+"V5127_MINIPET_CONFIGURE_DIALOG item="+item+" action=DISABLE result="+result);
                clearMiniConfigureDialog();
                return;
            }
        }

        if(pendingPetAccessoryItem>=0 && (widget==54195 || (widget>=2482 && widget<=2485))){
            int item=pendingPetAccessoryItem;
            if(widget==54195 || widget==2485){
                serverPackets.fixed(219,new byte[0]);
                System.out.println(tag+"V5130_PET_ACCESSORY_DIALOG item="+item+" action=CLOSE widget="+widget);
                clearPetAccessoryDialog();
                return;
            }
            if(widget==2482){
                BankState.Stack st=bank.inventoryAt(pendingPetAccessorySlot);
                if(st==null || st.itemId!=item || st.qty<=0){
                    serverPackets.fixed(219,new byte[0]);
                    System.out.println(tag+"V5130_PET_ACCESSORY_DIALOG item="+item+" action=ACTIVATE result=REJECTED_ITEM_MOVED");
                    clearPetAccessoryDialog(); return;
                }
                activePetAccessoryItem=item;
                Integer selector=petAccessorySelector(item);
                String visual=npcs.devSetParticleSelector(selector,movement,serverPackets);
                serverPackets.fixed(219,new byte[0]);
                saveAccountQuiet(tag,"PET_ACCESSORY_ACTIVATE");
                System.out.println(tag+"V5130_PET_ACCESSORY_DIALOG item="+item+" action=ACTIVATE selector="+selector+
                    " visual={"+visual+"} wording=RECONSTRUCTED_FROM_OFFICIAL_TOGGLE_SEMANTIC provenance="+petAccessorySelectorAuthority(item));
                clearPetAccessoryDialog(); return;
            }
            if(widget==2483){
                activePetAccessoryItem=0;
                String visual=npcs.devSetParticleSelector(null,movement,serverPackets);
                serverPackets.fixed(219,new byte[0]);
                saveAccountQuiet(tag,"PET_ACCESSORY_DETACH");
                System.out.println(tag+"V5130_PET_ACCESSORY_DIALOG item="+item+" action=DETACH visual={"+visual+"} wording=RECONSTRUCTED_FROM_OFFICIAL_TOGGLE_SEMANTIC");
                clearPetAccessoryDialog(); return;
            }
            if(widget==2484){
                serverPackets.fixed(219,new byte[0]);
                System.out.println(tag+"V5130_PET_ACCESSORY_DIALOG item="+item+" action=CANCEL");
                clearPetAccessoryDialog(); return;
            }
        }

        if(pendingPetColorItems!=null && (widget==54195 || (widget>=2482 && widget<=2485))){
            if(widget==54195){
                serverPackets.fixed(219,new byte[0]);
                System.out.println(tag+"V5127_PET_COLOR_DIALOG family="+pendingPetColorFamily+" action=CLOSE_WINDOW widget=54195");
                clearPetColorDialog();
                return;
            }
            if(widget==2485 && !"SCOOBY_BEHEMOTH".equals(pendingPetColorFamily)){
                serverPackets.fixed(219,new byte[0]);
                System.out.println(tag+"V57_PET_COLOR_DIALOG family="+pendingPetColorFamily+" action=CANCEL");
                clearPetColorDialog();
                return;
            }
            int choice=widget-2482;
            if(choice>=0 && choice<pendingPetColorItems.length){
                BankState.Stack st=bank.inventoryAt(pendingPetColorSlot);
                int current=st==null?-1:st.itemId;
                if(st==null || !petColorCurrentAllowed(pendingPetColorFamily,current,pendingPetColorItems)){
                    serverPackets.fixed(219,new byte[0]);
                    System.out.println(tag+"V57_PET_COLOR_DIALOG family="+pendingPetColorFamily+" result=REJECTED_ITEM_MOVED");
                    clearPetColorDialog();
                    return;
                }
                int replacement=pendingPetColorItems[choice];
                String result=current==replacement?"INVENTORY_TRANSFORM_NOOP":bank.transformInventoryOne(pendingPetColorSlot,current,replacement,serverPackets);
                serverPackets.fixed(219,new byte[0]);
                if(result.startsWith("INVENTORY_TRANSFORM_OK")||result.equals("INVENTORY_TRANSFORM_NOOP"))saveAccountQuiet(tag,"PET_SWITCH_COLOR");
                System.out.println(tag+"V57_PET_COLOR_DIALOG family="+pendingPetColorFamily+" choice="+(choice+1)+" result="+result+" item="+current+"->"+replacement);
                clearPetColorDialog();
                return;
            }
        }
        if (widget == 63027 || widget == 63031) {
            if (!compCapeCustomizeOpen) {
                System.out.println(tag + "V55_COMP_CAPE_WIDGET widget="+widget+" result=IGNORED_NOT_OPEN");
                return;
            }
            boolean confirm = widget == 63027;
            serverPackets.fixed(219, new byte[0]);
            compCapeCustomizeOpen = false;
            System.out.println(tag + "V55_COMP_CAPE_WIDGET widget="+widget+" action="+(confirm?"CONFIRM":"CANCEL")+" result=CLOSED_NATIVE_ROOT_63036 selectors="+playerState.compSelectorSummary());
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

    private void acceptPendingGenericInteraction(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        for (GenericInteractionEvent e; (e=R85GenericC2SBridge.take(clientPackets))!=null; ) {
            switch (e.family) {
                case OBJECT_OPTION:
                    System.out.println(tag+"V5185_GENERIC_OBJECT_ACTION "+e+" result=DECODED_FAIL_CLOSED authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN");
                    break;
                case WIDGET_ITEM_OPTION:
                    System.out.println(tag+"V5185_WIDGET_ITEM_ACTION "+e+" result=DECODED_FAIL_CLOSED authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN");
                    break;
                case ITEM_ON_PLAYER:
                    System.out.println(tag+"V5185_ITEM_ON_PLAYER "+e+" result=DECODED_FAIL_CLOSED authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN");
                    break;
                case ITEM_ON_GROUND_ITEM:
                    System.out.println(tag+"V5185_ITEM_ON_GROUND "+e+" result=DECODED_FAIL_CLOSED authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN");
                    break;
                case ITEM_ON_OBJECT:
                    System.out.println(tag+"V5185_ITEM_ON_OBJECT "+e+" result=DECODED_FAIL_CLOSED authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN");
                    break;
                default:
                    System.out.println(tag+"V5185_GENERIC_INTERACTION "+e+" result=DECODED_FAIL_CLOSED");
            }
        }
    }

    private void acceptPendingObjectInteraction(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        ObjectInteraction r = clientPackets.takeObjectInteraction();
        if (r == null) return;
        if (r.objectId == BankState.BANK_OBJECT_ID) {
            if (adjacentTo(r.worldX, r.worldY)) {
                pendingBankInteraction=null;
                openBankNow(r, serverPackets, tag, "OPENED_ADJACENT_IMMEDIATE");
            } else {
                pendingBankInteraction=r;
                pendingBankDeadlineMs=System.currentTimeMillis()+10_000L;
                System.out.println(tag + "V5_BANK_INTERACTION " + r
                                 + " authorityWorld="+movement.x()+","+movement.y()
                                 + " distance="+chebyshev(movement.x(),movement.y(),r.worldX,r.worldY)
                                 + " action=DEFERRED_UNTIL_ADJACENT");
            }
            return;
        }
        pendingBankInteraction=null;
        System.out.println(tag + "OBJECT_INTERACTION " + r
                         + " action=DECODED_NOT_IMPLEMENTED decoderAligned=true");
    }

    private void tryOpenDeferredBank(ServerPacketWriter serverPackets, String tag, long now) throws IOException {
        ObjectInteraction r=pendingBankInteraction;
        if (r==null) return;
        if (now > pendingBankDeadlineMs) {
            pendingBankInteraction=null;
            System.out.println(tag + "V5_BANK_INTERACTION " + r
                             + " authorityWorld="+movement.x()+","+movement.y()
                             + " action=CANCELLED_TIMEOUT_NOT_ADJACENT");
            return;
        }
        if (!adjacentTo(r.worldX,r.worldY)) {
            if (movement.queued()==0) {
                pendingBankInteraction=null;
                System.out.println(tag + "V5_BANK_INTERACTION " + r
                                 + " authorityWorld="+movement.x()+","+movement.y()
                                 + " action=CANCELLED_PATH_ENDED_NOT_ADJACENT");
            }
            return;
        }
        pendingBankInteraction=null;
        movement.clearQueuedPath();
        openBankNow(r,serverPackets,tag,"OPENED_AFTER_AUTHORITATIVE_ARRIVAL");
    }

    private void openBankNow(ObjectInteraction r, ServerPacketWriter serverPackets, String tag, String reason) throws IOException {
        bank.open(serverPackets);
        System.out.println(tag + "V5_BANK_OPEN " + r
                         + " authorityWorld="+movement.x()+","+movement.y()
                         + " distance="+chebyshev(movement.x(),movement.y(),r.worldX,r.worldY)
                         + " root="+BankState.BANK_ROOT+" bankContainer="+BankState.BANK_CONTAINER
                         + " bankInventoryRoot="+BankState.BANK_INVENTORY_ROOT
                         + " inventoryContainer="+BankState.BANK_INVENTORY_CONTAINER
                         + " bankOccupied="+bank.bankSlots()+"/"+bank.bankCapacity()
                         + " inventoryOccupied="+bank.inventorySlots()+"/"+bank.inventoryCapacity()
                         + " placeholders="+bank.placeholdersEnabled()
                         + " action="+reason);
    }

    private void tryOpenDeferredNpcBank(ServerPacketWriter serverPackets,String tag,long now)throws IOException {
        Integer scene=pendingBankNpcScene;if(scene==null)return; NpcEntity n=npcs.scene(scene);
        if(n==null||now>pendingBankNpcDeadlineMs){pendingBankNpcScene=null;System.out.println(tag+"V511_NPC_BANK scene="+scene+" action=CANCELLED_MISSING_OR_TIMEOUT");return;}
        if(!adjacentTo(n.x,n.y)){if(movement.queued()==0){pendingBankNpcScene=null;System.out.println(tag+"V511_NPC_BANK scene="+scene+" action=CANCELLED_PATH_ENDED_NOT_ADJACENT");}return;}
        pendingBankNpcScene=null; movement.clearQueuedPath(); NpcAction synthetic=new NpcAction(17,scene); NpcInteractionRouter.Route route=NpcInteractionRouter.resolve(synthetic,n);
        openBankFromNpc(n,synthetic,route,serverPackets,tag,"OPENED_AFTER_AUTHORITATIVE_ARRIVAL");
    }

    private void openBankFromNpc(NpcEntity n,NpcAction req,NpcInteractionRouter.Route route,ServerPacketWriter serverPackets,String tag,String reason)throws IOException{
        bank.open(serverPackets);
        System.out.println(tag+"V511_BANK_OPEN_NPC npc="+n.definitionId+" scene="+n.sceneIndex+" world="+n.x+","+n.y+" request="+req+" route="+route+
            " authorityWorld="+movement.x()+","+movement.y()+" distance="+chebyshev(movement.x(),movement.y(),n.x,n.y)+" root="+BankState.BANK_ROOT+" action="+reason);
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

        // v5.18.4.2: inventory opcodes are generic option transports.  Resolve the
        // clicked option through the exact current item-definition action array before
        // assigning semantics.  This is required because option 3 (C2S16) is Defuse
        // on the infernal capes but Override on staff/rank partyhats.
        if (a.widgetId == BankState.NORMAL_INVENTORY_CONTAINER) {
            InventoryActionRouter.Resolution route = InventoryActionRouter.resolve(a);
            if (route.is("Override")) {
                BankState.Stack st = bank.inventoryAt(a.slot);
                if (st == null || st.itemId != a.itemId || st.qty <= 0) {
                    System.out.println(tag+"V51842_OVERRIDE "+a+" route="+route+" result=REJECTED_INVENTORY_MISMATCH");
                    return;
                }
                String result = CosmeticOverrideService.apply(bank, a.slot, a.itemId, playerState.cosmetic(), serverPackets);
                boolean changed = result.startsWith("COSMETIC_OVERRIDE_OK");
                if (changed) {
                    playerState.syncEquipmentPresentation(equipment);
                    bank.sendCosmetic(serverPackets, playerState.cosmetic());
                    playerPresentation.refresh(username, equipment, playerState, serverPackets);
                    saveAccountQuiet(tag, "COSMETIC_OVERRIDE");
                }
                System.out.println(tag+"V51842_OVERRIDE "+a+" route="+route+" result="+result+
                    " bs="+playerState.cosmetic().itemId()+" underlyingEquipmentUnchanged=true ammo="+equipment.itemAt(EquipmentSlot.AMMO)+
                    " appearanceRefresh="+changed+" authority=EXACT_CLIENT_ACTION_PLUS_EXISTING_BS_CHANNEL");
                return;
            }
            if (route.is("Defuse")) {
                BankState.Stack st = bank.inventoryAt(a.slot);
                if (st == null || st.itemId != a.itemId || st.qty <= 0) {
                    System.out.println(tag+"V51842_DEFUSE "+a+" route="+route+" result=REJECTED_INVENTORY_MISMATCH");
                    return;
                }
                System.out.println(tag+"V51842_DEFUSE "+a+" route="+route+
                    " result=FAIL_CLOSED_NO_MUTATION authority=UNKNOWN_SERVER_AUTHORITY");
                return;
            }
        }

        // Exact mini-pet inventory action: Configure is option 1 -> C2S122. The
        // production dialogue text is server-authored and unavailable statically, so
        // LocalLab exposes a clearly reconstructed Activate/Disable/Cancel chatbox.
        if(a.opcode==122 && a.widgetId==BankState.NORMAL_INVENTORY_CONTAINER && MiniPetDefinitionRepository.isMiniPetItem(a.itemId)){
            BankState.Stack st=bank.inventoryAt(a.slot);
            if(st==null||st.itemId!=a.itemId||st.qty<=0){System.out.println(tag+"V5127_MINIPET_CONFIGURE "+a+" result=REJECTED_INVENTORY_MISMATCH");return;}
            String semantic=ItemActionResolver.inventoryOption1Semantic(a.itemId);
            if(!"Configure".equalsIgnoreCase(semantic)){System.out.println(tag+"V5127_MINIPET_CONFIGURE "+a+" result=REJECTED_ACTION_SEMANTIC semantic="+semantic);return;}
            openMiniConfigureDialog(a.slot,a.itemId,serverPackets);
            System.out.println(tag+"V5127_MINIPET_CONFIGURE "+a+" result=DIALOG_OPEN authority=EXACT_ACTION_RECONSTRUCTED_SERVER_WORDING");
            return;
        }

        // Pet accessories are reusable global toggles. The exact client only proves
        // that Read is C2S122; the response is server-authored. Official SpawnPK update
        // text describes the items as "toggled", infinite-use particle accessories, so
        // LocalLab opens a native chatbox with reconstructed management wording instead
        // of silently toggling on Read.
        if(a.opcode==122 && a.widgetId==BankState.NORMAL_INVENTORY_CONTAINER && isPetAccessoryItem(a.itemId)){
            BankState.Stack st=bank.inventoryAt(a.slot);
            if(st==null||st.itemId!=a.itemId||st.qty<=0){System.out.println(tag+"V5130_PET_ACCESSORY "+a+" result=REJECTED_INVENTORY_MISMATCH");return;}
            String semantic=ItemActionResolver.inventoryOption1Semantic(a.itemId);
            if(!"Read".equalsIgnoreCase(semantic)){System.out.println(tag+"V5130_PET_ACCESSORY "+a+" result=REJECTED_ACTION_SEMANTIC semantic="+semantic);return;}
            openPetAccessoryDialog(a.slot,a.itemId,serverPackets);
            System.out.println(tag+"V5130_PET_ACCESSORY_READ item="+a.itemId+" name="+petAccessoryName(a.itemId)+
                " result=DIALOG_OPEN wording=RECONSTRUCTED_SERVER_RESPONSE officialSemantics=TOGGLE_INFINITE_USE currentActive="+
                (activePetAccessoryItem==0?"NONE":activePetAccessoryItem));
            return;
        }

        // Native dyed Doppelganger pet exposes Remove-dye as inventory option index 3
        // (menu 493 -> opcode 75).  The current item catalogue gives an exact base/dyed
        // pair: 28807 Doppelganger pet (dyed) -> 3241 Doppelganger pet.  This implements
        // only the item-state transition; the special dyed NPC presentation remains
        // evidence-blocked and is not guessed here.
        if (a.opcode == 75 && a.widgetId == BankState.NORMAL_INVENTORY_CONTAINER && a.itemId == 28807) {
            String result=bank.splitInventoryOne(a.slot,28807,3241,28824,serverPackets);
            if(result.startsWith("INVENTORY_SPLIT_OK")) saveAccountQuiet(tag,"DOPPELGANGER_REMOVE_DYE");
            System.out.println(tag+"V57_DOPPELGANGER_REMOVE_DYE "+a+" result="+result+" baseItem=3241 returnedDye=28824 decoderAligned=true");
            return;
        }

        // Resvano and Scooby Behemoth use a native choice dialogue rather than a
        // blind cycle. Widget family 2481..2485 is the current classic option UI;
        // labels are deliberately generic because production colour names are not
        // present in the recovered item config.
        if (a.opcode == 75 && a.widgetId == BankState.NORMAL_INVENTORY_CONTAINER) {
            int[] family=petColorFamily(a.itemId);
            if(family!=null){
                openPetColorDialog(a.slot,family,petColorFamilyName(a.itemId),serverPackets);
                System.out.println(tag+"V59_PET_COLOR_DIALOG_OPEN "+a+" family="+pendingPetColorFamily+" choices="+java.util.Arrays.toString(family)+" chatboxRoot=2480 transport=S2C164");
                return;
            }
        }

        // Native Grand completionist cape Customize inventory action (menu 493 -> opcode 75).
        // The exact current client initializes its six-colour selector UI when root 63036
        // arrives through packet 97. No replacement LocalLab UI is invented.
        if (a.opcode == 75 && a.widgetId == BankState.NORMAL_INVENTORY_CONTAINER && isSpecialCompCape(a.itemId)) {
            BankState.Stack st=bank.inventoryAt(a.slot);
            if(st==null || st.itemId!=a.itemId || st.qty<=0){
                System.out.println(tag+"V54_COMP_CAPE_CUSTOMIZE_OPEN "+a+" result=REJECTED_INVENTORY_MISMATCH");
                return;
            }
            serverPackets.fixed(97, BootstrapPackets.interface97(63036));
            compCapeCustomizeOpen = true;
            System.out.println(tag+"V55_COMP_CAPE_CUSTOMIZE_OPEN "+a+" result=OPENED_NATIVE_ROOT_63036 selectors="+playerState.compSelectorSummary());
            return;
        }

        // Native icon-family items use the dedicated COSMETIC/bs channel, never AMMO slot 13.
        if(a.opcode==41 && a.widgetId==BankState.NORMAL_INVENTORY_CONTAINER && ItemCatalog.isNativePlayerIcon(a.itemId)){
            String result=bank.equipCosmeticFromInventory(a.slot,a.itemId,playerState.cosmetic(),serverPackets);
            boolean changed=result.startsWith("COSMETIC_EQUIP_OK");
            if(changed){playerState.syncEquipmentPresentation(equipment);bank.sendCosmetic(serverPackets,playerState.cosmetic());playerPresentation.refresh(username,equipment,playerState,serverPackets);saveAccountQuiet(tag,"COSMETIC_EQUIP");}
            System.out.println(tag+"V511_COSMETIC_EQUIP "+a+" result="+result+" nativeBs="+playerState.nativeIconItemId()+" ammo="+equipment.itemAt(EquipmentSlot.AMMO)+" appearanceRefresh="+changed);
            return;
        }

        // The pinned client maps inventory action text containing Wear/Wield/Equip
        // to menu action 454 -> opcode 41. Handle that one path generically for
        // every slot resolved by EquipmentMetadataRepository.
        if (a.opcode == 41 && a.widgetId == BankState.NORMAL_INVENTORY_CONTAINER) {
            String result = bank.equipFromInventory(a.slot, a.itemId, equipment, serverPackets);
            boolean changed = result.startsWith("EQUIP_OK");
            if (changed) {
                playerState.syncEquipmentPresentation(equipment);
                playerPresentation.refresh(username,equipment,playerState,serverPackets);
                int root=CombatInterfaceRepository.forWeapon(equipment.weapon());
                serverPackets.fixed(71, BootstrapPackets.sidebar71(root, CombatInterfaceRepository.TAB_INDEX));
                System.out.println(tag+"V510_STYLE_EQUIP_RECONCILE "+combatStyles.reconcileRoot(root,serverPackets));
            }
            saveAccountQuiet(tag, "EQUIP_FROM_INVENTORY");
            System.out.println(tag + "V522_EQUIPMENT_ITEM_ACTION " + a + " result=" + result
                             + " weapon="+equipment.weapon()+" appearanceRefresh="+changed
                             + " decoderAligned=true");
            return;
        }

        // Exact current client builds a dedicated one-slot cosmetic widget 27701
        // at the bottom-center of equipment root 1644. Its Remove option uses the
        // normal widget-item option transport. Keep this independent from AMMO/1688.
        if(a.opcode==145 && a.widgetId==BankState.COSMETIC_WIDGET){
            int active=playerState.cosmetic().itemId();
            if(a.slot!=0 || active<0 || a.itemId!=active){
                System.out.println(tag+"V5124_COSMETIC_WIDGET_REMOVE "+a+" result=REJECTED_EXPECTED slot0_item="+active); return;
            }
            String result=bank.unequipCosmeticToInventory(playerState.cosmetic(),serverPackets);
            boolean changed=result.startsWith("COSMETIC_UNEQUIP_OK");
            if(changed){
                playerState.syncEquipmentPresentation(equipment);
                bank.sendCosmetic(serverPackets,playerState.cosmetic());
                playerPresentation.refresh(username,equipment,playerState,serverPackets);
                saveAccountQuiet(tag,"COSMETIC_WIDGET_REMOVE");
            }
            System.out.println(tag+"V5124_COSMETIC_WIDGET_REMOVE "+a+" result="+result+" nativeBs="+playerState.nativeIconItemId()+" ammo="+equipment.itemAt(EquipmentSlot.AMMO));
            return;
        }

        // Classic equipment widget 1688 uses the normal first item-container action
        // (opcode 145) for Remove. This returns the item to inventory, refreshes the
        // 14-slot equipment container, then refreshes packet-81 appearance.
        if (a.opcode == 145 && a.widgetId == EquipmentState.EQUIPMENT_WIDGET) {
            String result = bank.unequipToInventory(a.slot, a.itemId, equipment, serverPackets);
            boolean changed = result.startsWith("UNEQUIP_OK");
            if (changed) {
                playerState.syncEquipmentPresentation(equipment);
                playerPresentation.refresh(username,equipment,playerState,serverPackets);
                int root=CombatInterfaceRepository.forWeapon(equipment.weapon());
                serverPackets.fixed(71, BootstrapPackets.sidebar71(root, CombatInterfaceRepository.TAB_INDEX));
                System.out.println(tag+"V510_STYLE_UNEQUIP_RECONCILE "+combatStyles.reconcileRoot(root,serverPackets));
            }
            saveAccountQuiet(tag, "UNEQUIP_TO_INVENTORY");
            System.out.println(tag + "V522_UNEQUIP_ITEM_ACTION " + a + " result=" + result
                             + " appearanceRefresh="+changed+" decoderAligned=true");
            return;
        }

        String result = bank.apply(a, serverPackets);
        saveAccountQuiet(tag, "BANK_ITEM_ACTION");
        System.out.println(tag + "V522_BANK_ITEM_ACTION " + a + " result=" + result + " decoderAligned=true");
    }

    private void acceptPendingItemOnItem(ClientPacketProbe clientPackets,ServerPacketWriter serverPackets,String tag)throws IOException{
        ItemOnItemAction a=clientPackets.takeItemOnItem();
        if(a==null)return;
        if(a.selectedWidget!=BankState.NORMAL_INVENTORY_CONTAINER || a.targetWidget!=BankState.NORMAL_INVENTORY_CONTAINER){
            System.out.println(tag+"V57_ITEM_ON_ITEM "+a+" result=DECODED_UNSUPPORTED_WIDGET");
            return;
        }
        boolean doppelPair=(a.selectedItemId==28824&&a.targetItemId==3241)||(a.selectedItemId==3241&&a.targetItemId==28824);
        if(doppelPair){
            String result=bank.combineInventoryOne(a.selectedSlot,a.selectedItemId,a.targetSlot,a.targetItemId,28807,serverPackets);
            if(result.startsWith("INVENTORY_COMBINE_OK"))saveAccountQuiet(tag,"DOPPELGANGER_APPLY_DYE");
            System.out.println(tag+"V57_DOPPELGANGER_APPLY_DYE "+a+" result="+result+" resultItem=28807");
            return;
        }
        System.out.println(tag+"V57_ITEM_ON_ITEM "+a+" result=DECODED_NO_SEMANTIC_HANDLER");
    }

    private void acceptPendingItemOnNpc(ClientPacketProbe clientPackets,ServerPacketWriter serverPackets,String tag)throws IOException{
        ItemOnNpcAction a=clientPackets.takeItemOnNpc();
        if(a==null)return;
        NpcEntity target=npcs.scene(a.targetNpcIndex);
        BankState.Stack st=a.widgetId==BankState.NORMAL_INVENTORY_CONTAINER?bank.inventoryAt(a.slot):null;
        if(st==null||st.itemId!=a.itemId){System.out.println(tag+"V5128_ITEM_ON_NPC "+a+" result=REJECTED_SOURCE_INVENTORY_MISMATCH");return;}
        if(isPetAccessoryItem(a.itemId) && target!=null && target==npcs.pet()){
            Integer selector=petAccessorySelector(a.itemId);
            activePetAccessoryItem=a.itemId;
            String visual=npcs.devSetParticleSelector(selector,movement,serverPackets);
            saveAccountQuiet(tag,"PET_ACCESSORY_USE_ON_PET");
            System.out.println(tag+"V5129_PET_ACCESSORY_USE_ON_PET "+a+" targetDef="+target.definitionId+
                " result=ATTACHED selector="+selector+" visual={"+visual+"} provenance="+petAccessorySelectorAuthority(a.itemId));
            return;
        }
        System.out.println(tag+"V5128_ITEM_ON_NPC "+a+" target="+target+" result=DECODED_NO_SEMANTIC_HANDLER");
    }

    private void acceptPendingSpellTarget(ClientPacketProbe clientPackets,ServerPacketWriter serverPackets,String tag)throws IOException{
        SpellTargetRequest req=clientPackets.takeSpellTarget();
        if(req==null)return;
        MagicState.Check check=magic.target(req,bank,equipment,playerState);
        if(!check.accepted){
            System.out.println(tag+"V510_MAGIC_TARGET "+req+" result="+check.message+" state={"+magic.summary()+"}");
            return;
        }

        String effect="ROUTER_ACCEPTED_EFFECT_UNIMPLEMENTED";
        if(req.kind==SpellTargetRequest.Kind.NPC){
            NpcEntity target=npcs.scene(req.targetIndex);
            if(target==null) effect="REJECTED_TARGET_NPC_NOT_VISIBLE scene="+req.targetIndex;
            else if(CombatTargetRepository.isCombatDummy(target.definitionId))
                effect=combat.magicFixtureHit(req.targetIndex,check.spell,npcs,serverPackets);
            else effect="TARGET_NPC_VISIBLE def="+target.definitionId+" effect=UNIMPLEMENTED_SERVER_AUTHORITY";
        } else if(req.kind==SpellTargetRequest.Kind.INVENTORY_ITEM){
            BankState.Stack at=bank.inventoryAt(req.targetSlot);
            if(req.targetWidget==BankState.NORMAL_INVENTORY_CONTAINER && (at==null||at.itemId!=req.targetId))
                effect="REJECTED_TARGET_INVENTORY_MISMATCH";
            else effect="TARGET_ITEM_ROUTED effect=UNIMPLEMENTED_SERVER_AUTHORITY";
        } else if(req.kind==SpellTargetRequest.Kind.PLAYER){
            effect="TARGET_PLAYER_ROUTED index="+req.targetIndex+" effect=UNIMPLEMENTED_SERVER_AUTHORITY";
        } else if(req.kind==SpellTargetRequest.Kind.OBJECT){
            effect="TARGET_OBJECT_ROUTED id="+req.targetId+" world="+req.worldX+","+req.worldY+" effect=UNIMPLEMENTED_SERVER_AUTHORITY";
        } else if(req.kind==SpellTargetRequest.Kind.GROUND_ITEM){
            effect="TARGET_GROUND_ITEM_ROUTED id="+req.targetId+" world="+req.worldX+","+req.worldY+" effect=UNIMPLEMENTED_SERVER_AUTHORITY";
        }
        System.out.println(tag+"V510_MAGIC_TARGET "+req+" spell="+check.spell+" validation="+check.message+" result="+effect);
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
        if(activePetAccessoryItem!=0){
            Integer selector=petAccessorySelector(activePetAccessoryItem);
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
                         +" accessory="+(activePetAccessoryItem==0?"NONE":activePetAccessoryItem+"/selector"+petAccessorySelector(activePetAccessoryItem)+"/"+accessorySpawn)
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
        GroundItemInteraction a=clientPackets.takeGroundItemInteraction(); if(a==null)return;
        GroundItem g=world.groundItems().find(a.itemId,a.worldX,a.worldY,0);
        if(g==null || (g.owner!=null&&!g.owner.equalsIgnoreCase(username))){System.out.println(tag+"V511_GROUND_ACTION "+a+" result=REJECTED_NOT_VISIBLE_OR_MISSING");return;}
        String action=GroundItemActionRepository.action(a.itemId,a.option);
        if(action==null){System.out.println(tag+"V511_GROUND_ACTION "+a+" result=EMPTY_ACTION_SLOT authority=EXACT_CLIENT_DEF");return;}
        if(!"Take".equalsIgnoreCase(action)){System.out.println(tag+"V511_GROUND_ACTION "+a+" action="+action+" result=DECODED_CONTENT_SEMANTIC_UNIMPLEMENTED");return;}
        if(!bank.canAddInventoryAmount(g.itemId,g.amount)){System.out.println(tag+"V511_GROUND_TAKE "+a+" result=REJECTED_INVENTORY_FULL amount="+g.amount);return;}
        if(onTile(g.tile.x,g.tile.y)){takeGroundNow(g,serverPackets,tag,"TAKE_ON_TILE_IMMEDIATE");return;}
        pendingGroundTake=a; pendingGroundTakeDeadlineMs=System.currentTimeMillis()+10_000L;
        System.out.println(tag+"V5122_GROUND_TAKE "+a+" result=DEFERRED_UNTIL_EXACT_TILE distance="+chebyshev(movement.x(),movement.y(),g.tile.x,g.tile.y));
    }

    private void tryTakeDeferredGround(ServerPacketWriter serverPackets,String tag,long now)throws IOException{
        GroundItemInteraction a=pendingGroundTake;if(a==null)return;
        GroundItem g=world.groundItems().find(a.itemId,a.worldX,a.worldY,0);
        if(g==null||now>pendingGroundTakeDeadlineMs){pendingGroundTake=null;System.out.println(tag+"V511_GROUND_TAKE "+a+" result=CANCELLED_MISSING_OR_TIMEOUT");return;}
        if(!onTile(g.tile.x,g.tile.y)){if(movement.queued()==0){pendingGroundTake=null;System.out.println(tag+"V5122_GROUND_TAKE "+a+" result=CANCELLED_PATH_ENDED_NOT_ON_TILE");}return;}
        pendingGroundTake=null; movement.clearQueuedPath(); takeGroundNow(g,serverPackets,tag,"TAKE_AFTER_EXACT_TILE_ARRIVAL");
    }

    private void takeGroundNow(GroundItem g,ServerPacketWriter serverPackets,String tag,String reason)throws IOException{
        if(!bank.canAddInventoryAmount(g.itemId,g.amount)){System.out.println(tag+"V511_GROUND_TAKE id="+g.id+" result=REJECTED_INVENTORY_FULL");return;}
        int dst=bank.addInventoryAmount(g.itemId,g.amount,serverPackets); if(dst<0)return;
        world.groundItems().remove(g.id); scenePublisher.groundRemove(g); saveAccountQuiet(tag,"GROUND_TAKE");
        System.out.println(tag+"V511_GROUND_TAKE id="+g.id+" item="+g.itemId+" amount="+g.amount+" dst="+dst+" world="+g.tile+" result="+reason);
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
        if(player81Sync==null){System.out.println(tag+"V5131_PLAYER_ACTION "+a+" result=REJECTED_SYNC_NOT_READY");return;}
        WorldPlayer target=player81Sync.resolveVisible(a.playerIndex);
        if(target==null||!target.registered()){
            System.out.println(tag+"V5131_PLAYER_ACTION "+a+" result=REJECTED_STALE_OR_NOT_VISIBLE");return;
        }
        if(combat.active()){
            boolean cancelled=combat.cancelForManualMovement();
            if(cancelled)clearOpponentOverlay(serverPackets,tag,"PLAYER_INTERACTION_REPLACES_NPC_COMBAT");
        }
        if(a.optionSlot==1){
            activePlayerTrade=null;activePlayerFollow=null;activePlayerAttack=target.id();nextPlayerAttackTick=0;movement.clearQueuedPath();
            System.out.println(tag+"V5131_PLAYER_ATTACK_REQUEST "+a+" target="+target.username()+" world="+target.movement().x()+","+target.movement().y()+
                " clickFacing=false facingAuthority=FIRST_AUTHORITATIVE_MOVEMENT damage=DEFERRED_SERVER_FORMULA_AUTHORITY");
            return;
        }
        if(a.optionSlot==2){
            activePlayerTrade=null;activePlayerAttack=null;activePlayerFollow=target.id();nextPlayerAttackTick=0;movement.clearQueuedPath();
            System.out.println(tag+"V5131_PLAYER_FOLLOW_REQUEST "+a+" target="+target.username()+" world="+target.movement().x()+","+target.movement().y()+" authority=SERVER_ROUTE");
            return;
        }
        if(a.optionSlot==3){
            activePlayerFollow=null;activePlayerAttack=null;nextPlayerAttackTick=0;movement.clearQueuedPath();
            activePlayerTrade=target.id();
            int dx=Math.abs(target.movement().x()-movement.x()),dy=Math.abs(target.movement().y()-movement.y());
            if(dx+dy==1){
                dispatchPendingPlayerTrade(target,tag,"ALREADY_ADJACENT");
            }else{
                System.out.println(tag+"V5141_PLAYER_TRADE_APPROACH "+a+" source="+username+" target="+target.username()+
                    " distanceChebyshev="+Math.max(dx,dy)+" distanceManhattan="+(dx+dy)+" action=DEFERRED_UNTIL_CARDINAL_ADJACENT authority=SERVER_ROUTE");
            }
            return;
        }
        System.out.println(tag+"V5131_PLAYER_ACTION "+a+" result=DECODED_HIDDEN_OPTION_FAIL_CLOSED");
    }

    /** Re-plan active player Follow/Attack/Trade against the target's current authoritative tile. */
    private void preparePlayerInteractionTick(long worldTick)throws IOException{
        EntityId id=activePlayerAttack!=null?activePlayerAttack:(activePlayerFollow!=null?activePlayerFollow:activePlayerTrade);
        if(id==null||player81Sync==null)return;
        WorldPlayer target=world.players().byId(id);
        if(target==null||!target.registered()||target.movement().plane()!=movement.plane()||player81Sync.clientIndexFor(target)<0){
            activePlayerAttack=null;activePlayerFollow=null;activePlayerTrade=null;nextPlayerAttackTick=0;movement.clearQueuedPath();return;
        }
        int range=activePlayerAttack!=null?playerAttackRange():1;
        int dx=Math.abs(target.movement().x()-movement.x()),dy=Math.abs(target.movement().y()-movement.y());
        int dist=Math.max(dx,dy);
        boolean inRange=range==1?dx+dy==1:dist<=range && !(dx==0&&dy==0);
        if(inRange){
            movement.clearQueuedPath();
            if(activePlayerTrade!=null)dispatchPendingPlayerTrade(target,"[world player="+worldPlayer.id()+"] ","ARRIVED_ADJACENT_PRE_TICK");
            return;
        }
        java.util.List<int[]> route=HomeCombatPathfinder.route(movement.x(),movement.y(),target.movement().x(),target.movement().y(),range);
        if(route==null||route.isEmpty())return;
        int n=Math.min(route.size(),MovementState.MAX_QUEUED_STEPS);
        int[] xs=new int[n],ys=new int[n];for(int i=0;i<n;i++){xs[i]=route.get(i)[0];ys[i]=route.get(i)[1];}
        movement.clearQueuedPath();
        String result=movement.accept(new MovementRequest(164,movement.persistentRun(),xs,ys,new byte[0]));
        if(!result.startsWith("ACCEPTED")){
            System.out.println("[world player="+worldPlayer.id()+"] V5141_PLAYER_ROUTE result="+result+" target="+target.username()+" range="+range+" steps="+n+
                " interaction="+(activePlayerTrade!=null?"TRADE":activePlayerAttack!=null?"ATTACK":"FOLLOW")+" worldTick="+worldTick);
        }
    }

    private void dispatchPendingPlayerTrade(WorldPlayer target,String tag,String reason)throws IOException{
        if(activePlayerTrade==null||target==null||!activePlayerTrade.equals(target.id()))return;
        int dx=Math.abs(target.movement().x()-movement.x()),dy=Math.abs(target.movement().y()-movement.y());
        if(dx+dy!=1)return;
        activePlayerTrade=null;movement.clearQueuedPath();
        String result=player81Sync.requestTrade(target,System.currentTimeMillis());
        if(result.startsWith("TRADE_MUTUAL_ACCEPTED")){
            String ui=TradeService.start(world,worldPlayer,target);
            result=result+" "+ui;
        }
        System.out.println(tag+"V5141_PLAYER_TRADE_DISPATCH source="+username+" target="+target.username()+" reason="+reason+
            " adjacency=CARDINAL_1 result="+result);
    }

    private void tryDispatchPendingPlayerTradeAfterMovement(String tag)throws IOException{
        if(activePlayerTrade==null||player81Sync==null)return;
        WorldPlayer target=world.players().byId(activePlayerTrade);
        if(target==null||!target.registered()){activePlayerTrade=null;return;}
        dispatchPendingPlayerTrade(target,tag,"ARRIVED_ADJACENT_AFTER_MOVEMENT");
    }

    private Integer playerInteractionTargetForMovement(){
        if(player81Sync==null)return null;
        EntityId id=activePlayerAttack!=null?activePlayerAttack:activePlayerFollow;if(id==null)return null;
        WorldPlayer target=world.players().byId(id);if(target==null||!target.registered())return null;
        int value=player81Sync.interactionTargetFor(target);return value<0?null:Integer.valueOf(value);
    }

    private int playerAttackRange(){
        CombatWeaponProfile p=CombatWeaponRepository.resolve(equipment.weapon());
        if(p==null||p.attackRange<=0)return 1;
        return Math.max(1,Math.min(10,p.attackRange));
    }

    private void tickPlayerAttack(long worldTick,ServerPacketWriter serverPackets,String tag)throws IOException{
        if(activePlayerAttack==null||player81Sync==null)return;
        WorldPlayer target=world.players().byId(activePlayerAttack);
        if(target==null||!target.registered()){activePlayerAttack=null;nextPlayerAttackTick=0;return;}
        int targetValue=player81Sync.interactionTargetFor(target);
        if(targetValue<0){activePlayerAttack=null;nextPlayerAttackTick=0;return;}
        int range=playerAttackRange();int dx=Math.abs(target.movement().x()-movement.x()),dy=Math.abs(target.movement().y()-movement.y());
        boolean inRange=range==1?dx+dy==1:(Math.max(dx,dy)<=range&&!(dx==0&&dy==0));
        if(!inRange||worldTick<nextPlayerAttackTick)return;
        CombatWeaponProfile p=CombatWeaponRepository.resolve(equipment.weapon());
        int speed=p==null||p.attackSpeedTicks<=0?4:p.attackSpeedTicks;
        int anim=p==null?-1:p.attackAnimation;
        if(anim>=0)serverPackets.varShort(81,CombatSync.player81AnimationAndInteraction(anim,targetValue));
        else serverPackets.varShort(81,CombatSync.player81InteractionOnly(targetValue));
        nextPlayerAttackTick=worldTick+Math.max(1,speed);
        System.out.println(tag+"V5131_PLAYER_ATTACK_PRESENTATION target="+target.username()+" clientTarget="+targetValue+" distance="+Math.max(dx,dy)+
            " range="+range+" weapon="+equipment.weapon()+" attackAnim="+(anim>=0?anim:"DEFERRED")+" speedTicks="+speed+
            " damage=DEFERRED_FORMULA_AUTHORITY remoteMaskRelay=true nextAttackTick="+nextPlayerAttackTick);
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

        NpcInteractionRouter.Route route=NpcInteractionRouter.resolve(a,clicked);
        if(route.service==NpcInteractionRouter.Service.BANK && clicked!=null){
            if(adjacentTo(clicked.x,clicked.y)){ pendingBankNpcScene=null; openBankFromNpc(clicked,a,route,serverPackets,tag,"OPENED_ADJACENT_IMMEDIATE"); }
            else { pendingBankNpcScene=clicked.sceneIndex; pendingBankNpcDeadlineMs=System.currentTimeMillis()+10_000L;
                System.out.println(tag+"V511_NPC_BANK "+a+" clicked="+clicked+" route="+route+" distance="+chebyshev(movement.x(),movement.y(),clicked.x,clicked.y)+" action=DEFERRED_UNTIL_ADJACENT"); }
            return;
        }
        System.out.println(tag+"V511_NPC_ACTION "+a+" route="+route+" result=DECODED_SEMANTIC_"+route.service+" clicked="+clicked+" petScene="+(pet==null?-1:pet.sceneIndex));
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
        String trade=TradeService.handleAmount(worldPlayer,amount);
        if(trade!=null){
            System.out.println(tag+"V5140_TRADE_AMOUNT opcode=208 amount="+amount+" result="+trade+" decoderAligned="+clientPackets.isAligned());
            return;
        }
        String result = bank.applyAmount(amount, serverPackets);
        saveAccountQuiet(tag, "BANK_AMOUNT");
        System.out.println(tag + "V522_BANK_AMOUNT opcode=208 amount="+amount+" result="+result+" decoderAligned="+clientPackets.isAligned());
    }

    private void acceptPendingContainerDrag(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        ContainerDrag d = clientPackets.takeContainerDrag();
        if (d == null) return;
        String result = bank.applyDrag(d, serverPackets);
        saveAccountQuiet(tag, d.widgetId==BankState.NORMAL_INVENTORY_CONTAINER ? "INVENTORY_DRAG" : "BANK_DRAG");
        System.out.println(tag + "V561_CONTAINER_DRAG " + d + " result="+result+" decoderAligned="+clientPackets.isAligned());
    }

    private void acceptPendingCommand(ClientPacketProbe clientPackets, ServerPacketWriter serverPackets, String tag) throws IOException {
        String command = clientPackets.takeCommand();
        if (command == null) return;
        String result = bank.applyCommand(command, serverPackets);
        if (!"IGNORED_NON_BANK_COMMAND".equals(result)) {
            saveAccountQuiet(tag, "BANK_COMMAND");
            System.out.println(tag + "V522_BANK_COMMAND command="+command+" result="+result+" decoderAligned="+clientPackets.isAligned());
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
        if(p.length>=1 && p[0].equalsIgnoreCase("authority")){
            int item=p.length>=2?parseInt(p[1],equipment.weapon()):equipment.weapon();
            System.out.println(tag+"V5170_CONTENT_AUTHORITY "+ContentAuthorityRepository.summary()+" current={"+ContentAuthorityRepository.itemSummary(item)+"}");
            return;
        }

        if(p.length>=1 && p[0].equalsIgnoreCase("equipstr")){
            int item=p.length>=2?parseInt(p[1],-1):-1;
            ItemAuthorityRepository.Entry e=ItemAuthorityRepository.get(item);
            // Exact current client Ctrl-hover requests equipstr <id> and then waits
            // for server-fed numeric key24 data. R8.1 refuses to invent those 14
            // numbers, but it also must not leave the client stuck on Loading.
            serverPackets.varShort(126,new PacketPayloadWriter().putStringNl("RESET_HOVER_EQUIPMENT").putU16BELowAdd128(0).toByteArray());
            String relation=e==null?"":e.relationSummary;
            String mechanics=e==null?"":e.mechanicsSummary;
            System.out.println(tag+"V5181_EQUIPSTR_FAIL_CLOSED item="+item+" known="+(e!=null)+
                " resetHover=true numeric14=UNRESOLVED_SERVER_AUTHORITY relation=["+clip(relation,100)+"] mechanics=["+clip(mechanics,100)+"]");
            return;
        }

        if(p.length>=1 && p[0].equalsIgnoreCase("igsearch")){
            String q=joinTokens(p,1);
            String r=itemLibrary.searchExact(serverPackets,q);
            System.out.println(tag+"V5150_ITEM_LIBRARY_IGSEARCH query=\""+q+"\" result="+r+" route=EXACT_CURRENT_C2S103");
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("itemlib")){
            int item=equipment.weapon();
            if(p.length>=2){
                int parsed=parseInt(p[1],Integer.MIN_VALUE);
                if(parsed!=Integer.MIN_VALUE)item=parsed;
                else { ItemAuthorityRepository.Entry e=ItemAuthorityRepository.byExactName(joinTokens(p,1)); item=e==null?-1:e.itemId; }
            }
            if(item<0 || ItemAuthorityRepository.get(item)==null){System.out.println(tag+"V5150_ITEM_LIBRARY_DEV_OPEN result=REJECTED_UNKNOWN_ITEM syntax=::itemlib <itemId|exact name>");return;}
            String r=itemLibrary.open(serverPackets,item);
            System.out.println(tag+"V5150_ITEM_LIBRARY_DEV_OPEN result="+r+" opener=LOCAL_DEV_ONLY nativeRoot=47500 normalRequest=igsearch");
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("worldauth")){
            int rid=p.length>=2?parseInt(p[1],-1):(((movement.x()>>6)<<8)|(movement.y()>>6));
            WorldRegionAuthorityRepository.Region r=WorldRegionAuthorityRepository.get(rid);
            System.out.println(tag+"V5150_WORLD_AUTHORITY region="+rid+" result="+(r==null?"UNKNOWN":r.toString())+
                " repositoryRegions="+WorldRegionAuthorityRepository.count()+" decoded="+WorldRegionAuthorityRepository.fullyDecodedCount()+
                " productionConfirmed="+WorldRegionAuthorityRepository.productionConfirmedCount()+" behavior=DATA_ONLY_NO_TELEPORT");
            return;
        }
        if(p.length>=1 && (p[0].equalsIgnoreCase("regionload")||p[0].equalsIgnoreCase("worldload"))){
            if(p.length<2){System.out.println(tag+"V5160_REGION_LOAD result=REJECTED syntax=::regionload <regionId> [plane] | ::regionhome");return;}
            int rid=parseInt(p[1],-1), plane=p.length>=3?parseInt(p[2],0):0;
            System.out.println(tag+"V5160_REGION_LOAD "+enterTransientRegionDev(rid,plane,serverPackets,tag));
            return;
        }
        if(p.length>=1 && (p[0].equalsIgnoreCase("regionhome")||p[0].equalsIgnoreCase("worldhome"))){
            System.out.println(tag+"V5160_REGION_HOME "+returnHomeFromTransientRegion(serverPackets,tag));
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("collisionauth")){
            int x=p.length>=3?parseInt(p[1],movement.x()):movement.x(), y=p.length>=3?parseInt(p[2],movement.y()):movement.y(), pl=p.length>=4?parseInt(p[3],movement.plane()):movement.plane();
            int rid=((x>>6)<<8)|(y>>6);
            System.out.println(tag+"V5160_COLLISION_AUTH world="+x+","+y+","+pl+" region="+rid+" mask="+WorldCollisionAuthority.maskAt(x,y,pl)+" blocked="+WorldCollisionAuthority.blockedTile(x,y,pl)+" repositoryRegions="+WorldCollisionAuthority.regionCount()+" entries="+WorldCollisionAuthority.entryCount());
            return;
        }

        if(p.length>=2 && p[0].equalsIgnoreCase("prayerbook")){
            String r=prayers.switchBook(p[1],serverPackets);
            System.out.println(tag+"V510_PRAYER_BOOK command="+clean+" result="+r);
            return;
        }
        if(p.length>=2 && p[0].equalsIgnoreCase("spellbook")){
            String r=magic.switchBook(p[1],serverPackets);
            System.out.println(tag+"V510_SPELL_BOOK command="+clean+" result="+r);
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("prayeroff")){
            String r=prayers.deactivateAll(serverPackets);
            System.out.println(tag+"V510_PRAYER_OFF result="+r);
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("prayerinfo")){
            System.out.println(tag+"V510_PRAYER_INFO "+prayers.summary()+" definitions="+PrayerDefinitionRepository.count());
            return;
        }
        if(p.length>=2 && p[0].equalsIgnoreCase("prayericon")){
            int icon=parseInt(p[1],-999);
            String r=prayers.publishManualHeadIcon(icon,serverPackets);
            System.out.println(tag+"V510_PRAYER_ICON result="+r);
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("magicinfo")){
            System.out.println(tag+"V510_MAGIC_INFO "+magic.summary()+" definitions="+SpellDefinitionRepository.count());
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("styleinfo")){
            int root=CombatInterfaceRepository.forWeapon(equipment.weapon());
            System.out.println(tag+"V510_STYLE_INFO weapon="+equipment.weapon()+" "+combatStyles.summary(root)+" roots="+CombatStyleRepository.rootCount()+" styles="+CombatStyleRepository.countStyles());
            return;
        }

        if(p.length>=1 && p[0].equalsIgnoreCase("engine")){
            System.out.println(tag+"V5123_ENGINE "+BuildInfo.summary()+" account="+username+" loginAlias="+loginAlias+" persistent="+persistentAccount+" "+world.summary()+" metrics="+world.metrics()+" sceneBase="+(scenePublisher==null?"none":scenePublisher.context().currentChunkX()+","+scenePublisher.context().currentChunkY())+
                " npcDefinitions="+EffectiveNpcDefinitionRepository.count()+" groundActionExceptions="+GroundItemActionRepository.exceptionCount()+" miniDefinitions="+MiniPetDefinitionRepository.count()+
                " itemAuthority="+ItemAuthorityRepository.count()+" worldRegions="+WorldRegionAuthorityRepository.count()+" worldAuthorityMode=DATA_ONLY");
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("authority")){
            System.out.println(tag+"V5124_AUTHORITY "+AuthorityR16R25Publisher.status()+
                " bankWrapperExact="+BankState.BANK_WRAPPER_ROOT+" bankRuntimeRoot="+BankState.BANK_ROOT+
                " combatProfiles="+CombatStyleRepository.rootCount()+" combatStyles="+CombatStyleRepository.countStyles()+
                " note=R25_core_17_59_plus_independent_exact_staff328_3; unproven_server_mechanics_remain_fail_closed");
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("minipet")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"status";
            if(sub.equals("status")||sub.equals("info")){System.out.println(tag+"V511_"+miniPets.status(petState,npcs));return;}
            if(sub.equals("off")||sub.equals("disable")){String r=miniPets.off(petState,npcs,serverPackets);saveAccountQuiet(tag,"MINIPET_OFF");System.out.println(tag+"V511_"+r);return;}
            if(sub.equals("set")&&p.length>=3){int item=parseInt(p[2],-1);String r=miniPets.configure(item,petState,npcs,movement,serverPackets);if(r.startsWith("MINIPET_CONFIGURED"))saveAccountQuiet(tag,"MINIPET_SET_DEV");System.out.println(tag+"V511_"+r+" commandAuthority=LOCAL_DEV");return;}
            System.out.println(tag+"V511_MINIPET_HELP commands=status | set <itemId> | off nativeInventoryAction=Configure/C2S122");return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("cosmetic")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"info";
            if(sub.equals("info")||sub.equals("status")){System.out.println(tag+"V5124_COSMETIC_INFO item="+playerState.cosmetic().itemId()+" nativeBs="+playerState.nativeIconItemId()+" ammo="+equipment.itemAt(EquipmentSlot.AMMO)+" authority=PLAYER_APPEARANCE_BS ui1688=NORMAL_EQUIPMENT cosmeticWidget="+BankState.COSMETIC_WIDGET+" cosmeticWidgetPublished=true");return;}
            if(sub.equals("off")||sub.equals("remove")){String r=bank.unequipCosmeticToInventory(playerState.cosmetic(),serverPackets);if(r.startsWith("COSMETIC_UNEQUIP_OK")){playerState.syncEquipmentPresentation(equipment);bank.sendCosmetic(serverPackets,playerState.cosmetic());playerPresentation.refresh(username,equipment,playerState,serverPackets);saveAccountQuiet(tag,"COSMETIC_OFF");}System.out.println(tag+"V5124_"+r+" nativeBs="+playerState.nativeIconItemId()+" ammo="+equipment.itemAt(EquipmentSlot.AMMO)+" cosmeticWidget="+BankState.COSMETIC_WIDGET);return;}
            System.out.println(tag+"V511_COSMETIC_HELP commands=info | off equip=normal_inventory_Wear/Wield_opcode41_on_native_icon_item");return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("devworld")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"info";
            if(sub.equals("info")){System.out.println(tag+"V5121_DEV_WORLD "+world.summary()+" metrics="+world.metrics()+" sceneChunk="+scenePublisher.context().currentChunkX()+","+scenePublisher.context().currentChunkY());return;}
            if(sub.equals("ground")&&p.length>=4){
                int item=parseInt(p[2],-1),amount=parseInt(p[3],-1),dx=p.length>=5?parseInt(p[4],0):0,dy=p.length>=6?parseInt(p[5],0):0;
                if(!ItemCatalog.exists(item)||amount<=0||amount>65535||dx<-16||dx>16||dy<-16||dy>16){System.out.println(tag+"V511_DEV_WORLD_GROUND result=REJECTED syntax=::devworld ground <item> <1..65535> [dx] [dy]");return;}
                Tile t=new Tile(movement.x()+dx,movement.y()+dy,0); GroundItem before=world.groundItems().findOwned(item,t.x,t.y,0,username);int old=before==null?0:before.amount;
                if((long)old+amount>65535){System.out.println(tag+"V511_DEV_WORLD_GROUND result=REJECTED_AMOUNT_OVERFLOW");return;}
                GroundItem g=world.groundItems().add(item,amount,t,username,sessionWorldTick,true);if(old>0)scenePublisher.groundAmount(g,old);else scenePublisher.groundSpawn(g);
                System.out.println(tag+"V511_DEV_WORLD_GROUND result=OK "+g);return;
            }
            if(sub.equals("groundclear")){int n=0;for(GroundItem g:world.groundItems().removeDevOwned()){try{scenePublisher.groundRemove(g);n++;}catch(IllegalArgumentException ignored){}}System.out.println(tag+"V511_DEV_WORLD_GROUNDCLEAR removed="+n);return;}
            if(sub.equals("object")&&p.length>=7){int id=parseInt(p[2],-1),dx=parseInt(p[3],0),dy=parseInt(p[4],0),shape=parseInt(p[5],-1),rot=parseInt(p[6],-1);Tile t=new Tile(movement.x()+dx,movement.y()+dy,0);try{WorldObject o=world.objects().put(id,t,shape,rot,true);scenePublisher.objectAdd(id,t,shape,rot);System.out.println(tag+"V511_DEV_WORLD_OBJECT result=OK "+o);}catch(Exception e){System.out.println(tag+"V511_DEV_WORLD_OBJECT result=REJECTED "+e.getMessage());}return;}
            if(sub.equals("objremove")&&p.length>=6){int dx=parseInt(p[2],0),dy=parseInt(p[3],0),shape=parseInt(p[4],-1),rot=parseInt(p[5],-1);Tile t=new Tile(movement.x()+dx,movement.y()+dy,0);try{WorldObject old=world.objects().removeAt(t,shape);scenePublisher.objectRemove(t,shape,rot);System.out.println(tag+"V511_DEV_WORLD_OBJREMOVE result=OK old="+old);}catch(Exception e){System.out.println(tag+"V511_DEV_WORLD_OBJREMOVE result=REJECTED "+e.getMessage());}return;}
            if(sub.equals("objanim")&&p.length>=7){int anim=parseInt(p[2],-1),dx=parseInt(p[3],0),dy=parseInt(p[4],0),shape=parseInt(p[5],-1),rot=parseInt(p[6],-1);try{scenePublisher.objectAnimation(anim,new Tile(movement.x()+dx,movement.y()+dy,0),shape,rot);System.out.println(tag+"V511_DEV_WORLD_OBJANIM result=OK anim="+anim);}catch(Exception e){System.out.println(tag+"V511_DEV_WORLD_OBJANIM result=REJECTED "+e.getMessage());}return;}
            if(sub.equals("gfx")&&p.length>=5){int gfx=parseInt(p[2],-1),dx=parseInt(p[3],0),dy=parseInt(p[4],0),h=p.length>=6?parseInt(p[5],0):0,d=p.length>=7?parseInt(p[6],0):0;try{scenePublisher.spotGraphic(gfx,new Tile(movement.x()+dx,movement.y()+dy,0),h,d);System.out.println(tag+"V511_DEV_WORLD_GFX result=OK gfx="+gfx);}catch(Exception e){System.out.println(tag+"V511_DEV_WORLD_GFX result=REJECTED "+e.getMessage());}return;}
            if(sub.equals("sound")&&p.length>=3){int id=parseInt(p[2],-1),delay=p.length>=4?parseInt(p[3],0):0,loops=p.length>=5?parseInt(p[4],0):0;try{scenePublisher.soundEffect(id,delay,loops);System.out.println(tag+"V511_DEV_WORLD_SOUND result=OK id="+id);}catch(Exception e){System.out.println(tag+"V511_DEV_WORLD_SOUND result=REJECTED "+e.getMessage());}return;}
            System.out.println(tag+"V511_DEV_WORLD_HELP ground <item> <amount> [dx] [dy] | groundclear | object <id> <dx> <dy> <shape> <rot> | objremove <dx> <dy> <shape> <rot> | objanim <anim> <dx> <dy> <shape> <rot> | gfx <gfx> <dx> <dy> [height] [delay] | sound <id> [delay] [loops] | info");return;
        }

        if(dev.trace().enabled() && p.length>0 && p[0].toLowerCase(java.util.Locale.ROOT).startsWith("dev"))
            dev.trace().record("DEV_COMMAND_REQUEST","C2S103 command=\""+clean+"\" -> router="+p[0],"EXACT_C2S103_TRANSPORT/LOCAL_DEV_ROUTE");

        // v5.9.1 session-only Dev Authority Workbench. These routes never persist
        // experimental presentation values into opensrc.properties.
        if(p.length>=1 && p[0].equalsIgnoreCase("dev")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"info";
            if(sub.equals("reset")){
                boolean hadMorph=dev.playerNpcTransformId()!=null;
                String npcr=npcs.devRemoveAllNpcs(serverPackets);
                String worldr=resetDevWorld(serverPackets);
                dev.resetAll();
                if(hadMorph) playerPresentation.refresh(username,equipment,playerState,serverPackets);
                String petReset="none";
                if(npcs.pet()!=null && petState.active()) petReset=npcs.previewPetDefinition(petState.npcId(),movement,serverPackets);
                String rr=bank.restoreDevInventoryPreview(serverPackets);
                System.out.println(tag+"V511_DEV_RESET workbench="+dev.summary()+" inventory="+rr+" pet="+petReset+" devNpcs="+npcr+" devWorld="+worldr+" playerRefresh="+hadMorph+" persisted=false");
            } else System.out.println(tag+"V592_DEV_INFO "+dev.summary());
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("devpet")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"info";
            String r;
            if(sub.equals("info")){ System.out.println(tag+"V591_"+npcs.devInfo(movement)); return; }
            if(sub.equals("fx")){
                String v=p.length>=3?p[2].toLowerCase(java.util.Locale.ROOT):"auto";
                Integer sel;
                if(v.equals("auto")||v.equals("reset")) sel=null;
                else if(v.equals("next")) sel=dev.petParticleSelector()==null?0:((dev.petParticleSelector()+1)&255);
                else if(v.equals("prev")) sel=dev.petParticleSelector()==null?255:((dev.petParticleSelector()+255)&255);
                else { int x=parseInt(v,-1); if(x<0||x>255){System.out.println(tag+"V591_DEV_PET_FX result=REJECTED expected=auto|next|prev|0..255");return;} sel=x; }
                r=npcs.devSetParticleSelector(sel,movement,serverPackets); System.out.println(tag+"V591_"+r); return;
            }
            if(sub.equals("npc")||sub.equals("preview")){
                int npc=p.length>=3?parseInt(p[2],-1):-1;
                r=npcs.previewPetDefinition(npc,movement,serverPackets); System.out.println(tag+"V591_DEV_PET_NPC "+r); return;
            }
            if(sub.equals("map")){
                String kind=p.length>=3?p[2].toLowerCase(java.util.Locale.ROOT):"show";
                if(kind.equals("show")||kind.equals("info")){
                    System.out.println(tag+"V593_DEV_PET_MAP npc="+dev.petNpcBindings()+" sprite="+dev.petSpriteBindings()+" persisted=false"); return;
                }
                if(kind.equals("clear")){
                    if(p.length>=4){ int item=parseInt(p[3],-1); dev.clearPetBinding(item); } else dev.clearPetBindings();
                    System.out.println(tag+"V593_DEV_PET_MAP_CLEAR npc="+dev.petNpcBindings()+" sprite="+dev.petSpriteBindings()); return;
                }
                if(kind.equals("npc")){
                    int item=p.length>=4?parseInt(p[3],-1):-1, npc=p.length>=5?parseInt(p[4],-1):-1;
                    if(item<0||npc<0||npc>16383){System.out.println(tag+"V593_DEV_PET_MAP_NPC result=REJECTED syntax=::devpet map npc <itemId> <npcId>");return;}
                    dev.setPetNpcBinding(item,npc);
                    String live="inactive";
                    if(npcs.pet()!=null&&npcs.pet().petItemId==item) live=npcs.previewPetDefinition(npc,movement,serverPackets);
                    System.out.println(tag+"V593_DEV_PET_MAP_NPC item="+item+" npc="+npc+" live="+live+" persisted=false"); return;
                }
                if(kind.equals("sprite")){
                    int item=p.length>=4?parseInt(p[3],-1):-1, preview=p.length>=5?parseInt(p[4],-1):-1;
                    if(item<0||preview<0||!ItemCatalog.exists(preview)){System.out.println(tag+"V593_DEV_PET_MAP_SPRITE result=REJECTED syntax=::devpet map sprite <itemId> <previewItemId>");return;}
                    dev.setPetSpriteBinding(item,preview);
                    System.out.println(tag+"V593_"+bank.sendDevInventoryVariantPreview(dev.petSpriteBindings(),serverPackets)); return;
                }
                if(kind.equals("applysprites")){ System.out.println(tag+"V593_"+bank.sendDevInventoryVariantPreview(dev.petSpriteBindings(),serverPackets)); return; }
                System.out.println(tag+"V593_DEV_PET_MAP_HELP npc <itemId> <npcId> | sprite <itemId> <previewItemId> | applysprites | show | clear [itemId]"); return;
            }
            if(sub.equals("visual")){
                String op=p.length>=3?p[2].toLowerCase(java.util.Locale.ROOT):"info";
                int active=npcs.pet()==null?-1:npcs.pet().definitionId;
                if(op.equals("info")){ int npc=p.length>=4?parseInt(p[3],active):active; System.out.println(tag+"V5124_"+SpecialPetVisualLab.info(npc,dev.petParticleSelector())+" "+LocalDevVisualOverrideStore.summary()); return; }
                if(op.equals("npc")){ int npc=p.length>=4?parseInt(p[3],-1):-1; System.out.println(tag+"V593_"+npcs.previewPetDefinition(npc,movement,serverPackets)); return; }
                if(op.equals("owner")){
                    String v=p.length>=4?p[3].toLowerCase(java.util.Locale.ROOT):"player";
                    int raw=v.equals("player")?(32768+NpcRegistry.LOCAL_PLAYER_INDEX):parseInt(v,-1);
                    if(v.equals("raw")&&p.length>=5)raw=parseInt(p[4],-1);
                    System.out.println(tag+"V593_"+npcs.devPetInteractionTarget(raw,serverPackets)); return;
                }
                if(op.equals("fx")){
                    String v=p.length>=4?p[3].toLowerCase(java.util.Locale.ROOT):"auto"; Integer sel=v.equals("auto")?null:parseInt(v,-1);
                    if(sel!=null&&(sel<0||sel>255)){System.out.println(tag+"V593_DEV_PET_VISUAL_FX result=REJECTED");return;}
                    System.out.println(tag+"V593_"+npcs.devSetParticleSelector(sel,movement,serverPackets)); return;
                }
                if(op.equals("alpha")){
                    String v=p.length>=4?p[3].toLowerCase(java.util.Locale.ROOT):"info";
                    if(v.equals("info")){System.out.println(tag+"V5124_"+SpecialPetVisualLab.inspectOnly(op,active)+" "+LocalDevVisualOverrideStore.summary());return;}
                    if(v.equals("auto")||v.equals("reset")){System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.set("alpha",null));return;}
                    if(v.equals("off"))v="0"; int x=parseInt(v,-1); if(x<0||x>255){System.out.println(tag+"V5124_DEV_PET_VISUAL_ALPHA result=REJECTED expected=auto|off|0..255");return;}
                    System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.set("alpha",Integer.toString(x))); return;
                }
                if(op.equals("ai")){
                    String v=p.length>=4?p[3].toLowerCase(java.util.Locale.ROOT):"info";
                    if(v.equals("info")){System.out.println(tag+"V5124_"+SpecialPetVisualLab.inspectOnly(op,active)+" "+LocalDevVisualOverrideStore.summary());return;}
                    if(v.equals("auto")||v.equals("reset")){System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.set("ai",null));return;}
                    if(v.equals("off"))v="0"; try{Integer.parseInt(v);}catch(Exception e){System.out.println(tag+"V5124_DEV_PET_VISUAL_AI result=REJECTED expected=auto|off|signedInt");return;}
                    System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.set("ai",v)); return;
                }
                if(op.equals("tint")){
                    String v=p.length>=4?p[3]:"info"; String lv=v.toLowerCase(java.util.Locale.ROOT);
                    if(lv.equals("info")){System.out.println(tag+"V5124_"+SpecialPetVisualLab.inspectOnly(op,active)+" "+LocalDevVisualOverrideStore.summary());return;}
                    if(lv.equals("auto")||lv.equals("reset")){System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.set("tint",null));return;}
                    if(lv.equals("off")){System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.set("tint","off"));return;}
                    if(lv.equals("raw")&&p.length>=5){try{Integer.parseInt(p[4]);}catch(Exception e){System.out.println(tag+"V5124_DEV_PET_VISUAL_TINT result=REJECTED raw_signedInt");return;}System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.set("tint","raw:"+p[4]));return;}
                    String rgb=lv.startsWith("#")?lv.substring(1):(lv.startsWith("0x")?lv.substring(2):lv);
                    try{int n=Integer.parseInt(rgb,16);if(n<0||n>0xffffff)throw new Exception();System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.set("tint","rgb:"+String.format("%06x",n)));}catch(Exception e){System.out.println(tag+"V5124_DEV_PET_VISUAL_TINT result=REJECTED expected=auto|off|#RRGGBB|0xRRGGBB|raw <signedInt>");}return;
                }
                if(op.equals("bodycycle")){
                    String v=p.length>=4?p[3].toLowerCase(java.util.Locale.ROOT):"info";
                    if(v.equals("info")){System.out.println(tag+"V5124_"+SpecialPetVisualLab.inspectOnly(op,active)+" "+LocalDevVisualOverrideStore.summary());return;}
                    if(v.equals("auto")||v.equals("reset")){System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.set("bodycycle",null));return;}
                    if(v.equals("off")){System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.set("bodycycle","off"));return;}
                    int phase=parseInt(v,-1);if(phase<0||phase>66){System.out.println(tag+"V5124_DEV_PET_VISUAL_BODYCYCLE result=REJECTED expected=auto|off|0..66");return;}
                    System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.set("bodycycle",Integer.toString(phase)));return;
                }
                if(op.equals("intrinsicfx")){
                    String v=p.length>=4?p[3].toLowerCase(java.util.Locale.ROOT):"info";
                    if(v.equals("info")){System.out.println(tag+"V5125_DEV_PET_VISUAL_INTRINSICFX npc="+active+" default="+((active==1334||active==8210)?"OFF_CORRECTED_PROFILE":"NATIVE_ON")+" "+LocalDevVisualOverrideStore.summary());return;}
                    if(v.equals("auto")||v.equals("reset")||v.equals("default")){System.out.println(tag+"V5125_"+LocalDevVisualOverrideStore.set("intrinsicfx",null));return;}
                    if(v.equals("on")||v.equals("native")||v.equals("true")){System.out.println(tag+"V5125_"+LocalDevVisualOverrideStore.set("intrinsicfx","on"));return;}
                    if(v.equals("off")||v.equals("false")){System.out.println(tag+"V5125_"+LocalDevVisualOverrideStore.set("intrinsicfx","off"));return;}
                    System.out.println(tag+"V5125_DEV_PET_VISUAL_INTRINSICFX result=REJECTED expected=auto|on|off");return;
                }
                if(op.equals("state")){int st=p.length>=4?parseInt(p[3],-1):-1;if(st<0||st>3){System.out.println(tag+"V5124_DEV_PET_VISUAL_STATE result=REJECTED expected=0..3");return;}System.out.println(tag+"V5124_"+npcs.setPetNativeState(st,serverPackets));return;}
                if(op.equals("text")){if(p.length<4){System.out.println(tag+"V5124_DEV_PET_VISUAL_TEXT result=REJECTED expected=<text>");return;}System.out.println(tag+"V5124_"+npcs.forcePetText(joinTokens(p,3),serverPackets));return;}
                if(op.equals("anim")){int anim=p.length>=4?parseInt(p[3],-999):-999,delay=p.length>=5?parseInt(p[4],0):0;System.out.println(tag+"V5124_"+npcs.animatePet(anim,delay,serverPackets));return;}
                if(op.equals("gfx")){int gfx=p.length>=4?parseInt(p[3],-999):-999,h=p.length>=5?parseInt(p[4],0):0,d=p.length>=6?parseInt(p[5],0):0;System.out.println(tag+"V5124_"+npcs.gfxPet(gfx,h,d,serverPackets));return;}
                if(op.equals("animfx")){int anim=p.length>=4?parseInt(p[3],-999):-999,gfx=p.length>=5?parseInt(p[4],-999):-999,h=p.length>=6?parseInt(p[5],0):0,d=p.length>=7?parseInt(p[6],0):0;System.out.println(tag+"V5124_"+npcs.animationAndGfxPet(anim,0,gfx,h,d,serverPackets));return;}
                if(op.equals("owneranim")){int anim=p.length>=4?parseInt(p[3],-999):-999;if(anim<-1||anim>65535){System.out.println(tag+"V5124_DEV_PET_VISUAL_OWNERANIM result=REJECTED_RANGE");return;}serverPackets.varShort(81,CombatSync.player81AnimationOnly(anim));System.out.println(tag+"V5124_DEV_PET_VISUAL_OWNERANIM anim="+anim+" authority=LOCAL_DEV_EXPERIMENT");return;}
                if(op.equals("ownergfx")){int gfx=p.length>=4?parseInt(p[3],-999):-999,h=p.length>=5?parseInt(p[4],0):0,d=p.length>=6?parseInt(p[5],0):0;try{serverPackets.varShort(81,CombatSync.player81GfxOnly(gfx,h,d));System.out.println(tag+"V5124_DEV_PET_VISUAL_OWNERGFX gfx="+gfx+" height="+h+" delay="+d+" authority=LOCAL_DEV_EXPERIMENT");}catch(IllegalArgumentException e){System.out.println(tag+"V5124_DEV_PET_VISUAL_OWNERGFX result=REJECTED "+e.getMessage());}return;}
                if(op.equals("owneranimfx")){int anim=p.length>=4?parseInt(p[3],-999):-999,gfx=p.length>=5?parseInt(p[4],-999):-999,h=p.length>=6?parseInt(p[5],0):0,d=p.length>=7?parseInt(p[6],0):0;try{serverPackets.varShort(81,CombatSync.player81AnimationAndGfx(anim,gfx,h,d));System.out.println(tag+"V5124_DEV_PET_VISUAL_OWNERANIMFX anim="+anim+" gfx="+gfx+" height="+h+" delay="+d+" authority=LOCAL_DEV_EXPERIMENT");}catch(IllegalArgumentException e){System.out.println(tag+"V5124_DEV_PET_VISUAL_OWNERANIMFX result=REJECTED "+e.getMessage());}return;}
                if(op.equals("reset")||op.equals("clear")){System.out.println(tag+"V5124_"+LocalDevVisualOverrideStore.clear());System.out.println(tag+"V5124_"+npcs.devSetParticleSelector(null,movement,serverPackets));return;}
                System.out.println(tag+"V5124_DEV_PET_VISUAL_HELP info [npcId] | npc <id> (model/body via definition) | owner player|raw <target> | fx auto|0..255 | intrinsicfx auto|on|off | alpha auto|off|0..255 | ai auto|off|<int> | tint auto|off|#RRGGBB|raw <int> | bodycycle auto|off|0..66 | state 0..3 | text <text> | anim <id> [delay] | gfx <id> [height] [delay] | animfx <anim> <gfx> [height] [delay] | owneranim <id> | ownergfx <id> [height] [delay] | owneranimfx <anim> <gfx> [height] [delay] | reset ; client fields=LOCAL_DEV_EXPERIMENT"); return;
            }
            if(sub.equals("anim")){
                int anim=p.length>=3?parseInt(p[2],-999):-999, delay=p.length>=4?parseInt(p[3],0):0;
                r=npcs.animatePet(anim,delay,serverPackets); System.out.println(tag+"V591_DEV_PET_ANIM "+r); return;
            }
            if(sub.equals("gfx")){
                int gfx=p.length>=3?parseInt(p[2],-999):-999, h=p.length>=4?parseInt(p[3],0):0, d=p.length>=5?parseInt(p[4],0):0;
                r=npcs.gfxPet(gfx,h,d,serverPackets); System.out.println(tag+"V591_DEV_PET_GFX "+r); return;
            }
            if(sub.equals("animfx")){
                int anim=p.length>=3?parseInt(p[2],-999):-999, gfx=p.length>=4?parseInt(p[3],-999):-999, h=p.length>=5?parseInt(p[4],0):0, d=p.length>=6?parseInt(p[5],0):0;
                r=npcs.animationAndGfxPet(anim,0,gfx,h,d,serverPackets); System.out.println(tag+"V591_DEV_PET_ANIMFX "+r); return;
            }
            if(sub.equals("follow")){
                String op=p.length>=3?p[2].toLowerCase(java.util.Locale.ROOT):"info";
                if(op.equals("freeze")) r=npcs.devFollowFreeze(true);
                else if(op.equals("resume")) r=npcs.devFollowFreeze(false);
                else if(op.equals("step")) r=npcs.devFollowStep(movement,serverPackets);
                else if(op.equals("snap")) r=npcs.devSnapToOwner(movement,serverPackets);
                else if(op.equals("normal")||op.equals("reset")) r=npcs.devFollowDelay(null);
                else if(op.equals("delay")){
                    long ms=p.length>=4?parseLong(p[3],-1L):-1L;
                    try { r=npcs.devFollowDelay(ms<0?null:ms); } catch(IllegalArgumentException e){ r="REJECTED "+e.getMessage(); }
                } else r=npcs.devInfo(movement);
                System.out.println(tag+"V591_DEV_PET_FOLLOW "+r); return;
            }
            System.out.println(tag+"V593_DEV_PET_HELP commands=info | fx auto|next|prev|0..255 | npc <npcId> | map npc|sprite|applysprites|show|clear | visual info|npc|owner|fx|intrinsicfx|alpha|tint|ai|bodycycle|state|anim|gfx|animfx|reset | anim <id> [delay] | gfx <id> [height] [delay] | animfx <anim> <gfx> [height] [delay] | follow freeze|resume|step|snap|normal|delay <ms>");
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("devplayer")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"help";
            if(sub.equals("anim")){
                int anim=p.length>=3?parseInt(p[2],-999):-999;
                if(anim<-1||anim>65535){System.out.println(tag+"V591_DEV_PLAYER_ANIM result=REJECTED_RANGE");return;}
                serverPackets.varShort(81,CombatSync.player81AnimationOnly(anim));
                System.out.println(tag+"V591_DEV_PLAYER_ANIM anim="+anim+" gfx=NONE authority=TEMPORARY_VISUAL_PROBE"); return;
            }
            if(sub.equals("gfx")){
                int gfx=p.length>=3?parseInt(p[2],-999):-999, h=p.length>=4?parseInt(p[3],0):0, d=p.length>=5?parseInt(p[4],0):0;
                try { serverPackets.varShort(81,CombatSync.player81GfxOnly(gfx,h,d)); System.out.println(tag+"V591_DEV_PLAYER_GFX gfx="+gfx+" height="+h+" delay="+d+" anim=NONE authority=TEMPORARY_VISUAL_PROBE"); }
                catch(IllegalArgumentException e){ System.out.println(tag+"V591_DEV_PLAYER_GFX result=REJECTED "+e.getMessage()); }
                return;
            }
            if(sub.equals("animfx")){
                int anim=p.length>=3?parseInt(p[2],-999):-999, gfx=p.length>=4?parseInt(p[3],-999):-999, h=p.length>=5?parseInt(p[4],0):0, d=p.length>=6?parseInt(p[5],0):0;
                try { serverPackets.varShort(81,CombatSync.player81AnimationAndGfx(anim,gfx,h,d)); System.out.println(tag+"V591_DEV_PLAYER_ANIMFX anim="+anim+" gfx="+gfx+" height="+h+" delay="+d+" authority=TEMPORARY_VISUAL_PROBE"); }
                catch(IllegalArgumentException e){ System.out.println(tag+"V591_DEV_PLAYER_ANIMFX result=REJECTED "+e.getMessage()); }
                return;
            }
            if(sub.equals("morph")||sub.equals("npc")){
                int npc=p.length>=3?parseInt(p[2],-1):-1;
                if(npc<0||npc>16383){System.out.println(tag+"V592_DEV_PLAYER_MORPH result=REJECTED expected=npcId_0..16383");return;}
                try { System.out.println(tag+"V592_"+playerPresentation.morph(npc,username,equipment,playerState,serverPackets)); }
                catch(IllegalArgumentException e){ System.out.println(tag+"V592_DEV_PLAYER_MORPH result=REJECTED "+e.getMessage()); }
                return;
            }
            if(sub.equals("clear")||sub.equals("normal")||sub.equals("unmorph")){
                System.out.println(tag+"V592_"+playerPresentation.clear(username,equipment,playerState,serverPackets)); return;
            }
            if(sub.equals("info")){ System.out.println(tag+"V592_"+playerPresentation.info()); return; }
            System.out.println(tag+"V592_DEV_PLAYER_HELP commands=info | morph <npcId> | clear | anim <id> | gfx <id> [height] [delay] | animfx <anim> <gfx> [height] [delay] nurseIsolation='anim 10184' vs 'gfx 1310' vs 'animfx 10184 1310'"); return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("devnpc")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"help";
            String r;
            if(sub.equals("list")){ int limit=p.length>=3?parseInt(p[2],20):20; System.out.println(tag+"V592_"+npcs.devNpcList(limit)); return; }
            if(sub.equals("info")){ int scene=p.length>=3?parseInt(p[2],-1):-1; System.out.println(tag+"V592_"+npcs.devNpcInfo(scene,movement)); return; }
            if(sub.equals("spawn")){
                int npc=p.length>=3?parseInt(p[2],-1):-1, dx=p.length>=4?parseInt(p[3],1):1, dy=p.length>=5?parseInt(p[4],0):0;
                System.out.println(tag+"V592_"+npcs.devSpawnNpc(npc,dx,dy,movement,serverPackets)); return;
            }
            if(sub.equals("remove")){ int scene=p.length>=3?parseInt(p[2],-1):-1; System.out.println(tag+"V592_"+npcs.devRemoveNpc(scene,serverPackets)); return; }
            if(sub.equals("clear")){ System.out.println(tag+"V592_"+npcs.devRemoveAllNpcs(serverPackets)); return; }
            if(sub.equals("anim")){
                int scene=p.length>=3?parseInt(p[2],-1):-1, anim=p.length>=4?parseInt(p[3],-999):-999, delay=p.length>=5?parseInt(p[4],0):0;
                System.out.println(tag+"V592_"+npcs.devNpcAnimation(scene,anim,delay,serverPackets)); return;
            }
            if(sub.equals("gfx")){
                int scene=p.length>=3?parseInt(p[2],-1):-1, gfx=p.length>=4?parseInt(p[3],-999):-999, h=p.length>=5?parseInt(p[4],0):0, d=p.length>=6?parseInt(p[5],0):0;
                System.out.println(tag+"V592_"+npcs.devNpcGfx(scene,gfx,h,d,serverPackets)); return;
            }
            if(sub.equals("text")){
                int scene=p.length>=3?parseInt(p[2],-1):-1;
                String text=p.length>=4?joinTokens(p,3):"";
                System.out.println(tag+"V592_"+npcs.devNpcText(scene,text,serverPackets)); return;
            }
            if(sub.equals("target")){
                int scene=p.length>=3?parseInt(p[2],-1):-1;
                String tv=p.length>=4?p[3].toLowerCase(java.util.Locale.ROOT):"";
                int target=tv.equals("player")?(32768+NpcRegistry.LOCAL_PLAYER_INDEX):parseInt(tv,-1);
                System.out.println(tag+"V592_"+npcs.devNpcTarget(scene,target,serverPackets)); return;
            }
            if(sub.equals("hit")){
                int scene=p.length>=3?parseInt(p[2],-1):-1, damage=p.length>=4?parseInt(p[3],0):0;
                int max=p.length>=6?parseInt(p[5],100):100, cur=p.length>=5?parseInt(p[4],Math.max(0,max-damage)):Math.max(0,max-damage);
                System.out.println(tag+"V592_"+npcs.devNpcHit(scene,damage,cur,max,serverPackets)); return;
            }
            System.out.println(tag+"V592_DEV_NPC_HELP commands=list [limit] | info <scene> | spawn <npcId> [dx] [dy] | remove <scene> | clear | anim <scene> <anim> [delay] | gfx <scene> <gfx> [height] [delay] | text <scene> <text...> | target <scene> player|<raw0..65535> | hit <scene> <damage> [currentHp] [maxHp]"); return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("devtrace")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"info";
            if(sub.equals("on")){ dev.trace().setEnabled(true); System.out.println(tag+"V592_DEV_TRACE "+dev.trace().summary()); return; }
            if(sub.equals("off")){ dev.trace().setEnabled(false); System.out.println(tag+"V592_DEV_TRACE "+dev.trace().summary()); return; }
            if(sub.equals("clear")){ dev.trace().clear(); System.out.println(tag+"V592_DEV_TRACE_CLEAR "+dev.trace().summary()); return; }
            if(sub.equals("show")){
                int limit=p.length>=3?parseInt(p[2],20):20;
                java.util.List<String> rows=dev.trace().snapshot(limit);
                System.out.println(tag+"V592_DEV_TRACE_SHOW "+dev.trace().summary());
                for(String row:rows) System.out.println(tag+"V592_TRACE "+row);
                return;
            }
            System.out.println(tag+"V592_DEV_TRACE_INFO "+dev.trace().summary()+" commands=on|off|show_[n]|clear"); return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("devasset")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"help";
            if(sub.equals("item")){ int id=p.length>=3?parseInt(p[2],-1):-1; System.out.println(tag+"V592_"+DevAssetBrowser.item(id)); return; }
            if(sub.equals("pet")){ int id=p.length>=3?parseInt(p[2],-1):-1; System.out.println(tag+"V592_"+DevAssetBrowser.pet(id)); return; }
            if(sub.equals("find")){
                if(p.length<3){System.out.println(tag+"V592_DEV_ASSET_FIND result=REJECTED_EMPTY");return;}
                String q=joinTokens(p,2);
                java.util.List<String> rows=DevAssetBrowser.findItems(q,25);
                System.out.println(tag+"V592_DEV_ASSET_FIND query=\""+q+"\" count="+rows.size());
                for(String row:rows) System.out.println(tag+"V592_ASSET "+row);
                return;
            }
            System.out.println(tag+"V592_DEV_ASSET_HELP commands=item <id> | pet <itemId> | find <nameTerm>"); return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("devitem")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"help";
            if(sub.equals("sprite")){
                int slot=p.length>=3?parseInt(p[2],-1):-1, item=p.length>=4?parseInt(p[3],-1):-1;
                System.out.println(tag+"V591_"+bank.sendDevInventorySpritePreview(slot,item,serverPackets)); return;
            }
            if(sub.equals("gallery")){
                int start=p.length>=3?parseInt(p[2],0):0;
                if(p.length<4){System.out.println(tag+"V591_DEV_ITEM_GALLERY result=REJECTED_NEED_ITEM_IDS");return;}
                int[] ids=new int[p.length-3]; for(int i=3;i<p.length;i++)ids[i-3]=parseInt(p[i],-1);
                System.out.println(tag+"V591_"+bank.sendDevInventorySpriteGallery(start,ids,serverPackets)); return;
            }
            if(sub.equals("restore")||sub.equals("reset")){System.out.println(tag+"V591_"+bank.restoreDevInventoryPreview(serverPackets));return;}
            System.out.println(tag+"V591_DEV_ITEM_HELP commands=sprite <slot0..27> <itemId> | gallery <startSlot> <itemId...> | restore note=preview_is_client_container_only_do_not_click_it"); return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("devcombat")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"info";
            int weapon=equipment.weapon();
            if(sub.equals("anim")){
                String v=p.length>=3?p[2].toLowerCase(java.util.Locale.ROOT):"auto";
                try {
                    if(v.equals("auto")||v.equals("reset")) dev.setCombatAnimationOverride(weapon,null);
                    else if(v.equals("off")||v.equals("none")) dev.setCombatAnimationOverride(weapon,-1);
                    else { int a=parseInt(v,-999); if(a<-1||a>65535)throw new IllegalArgumentException("animation -1..65535"); dev.setCombatAnimationOverride(weapon,a); }
                    System.out.println(tag+"V591_DEV_COMBAT_ANIM weapon="+weapon+" override="+(dev.hasCombatAnimationOverride(weapon)?dev.combatAnimationOverride(weapon):"AUTO")+" authority=TEMPORARY_OVERRIDE");
                } catch(IllegalArgumentException e){System.out.println(tag+"V591_DEV_COMBAT_ANIM result=REJECTED "+e.getMessage());}
                return;
            }
            System.out.println(tag+"V591_DEV_COMBAT_INFO weapon="+weapon+" profile="+CombatWeaponRepository.resolve(weapon)+" animOverride="+(dev.hasCombatAnimationOverride(weapon)?dev.combatAnimationOverride(weapon):"AUTO")+" scorchingNormalPolicy=UNBOUND_ANIMATION_SUPPRESSED_AFTER_LIVE_CRASH"); return;
        }

        if (p.length>=1 && p[0].equalsIgnoreCase("nurse")) {
            int changed=playerState.restoreNurse();
            movement.setRunEnergy(100);
            changed |= playerState.syncScopesightMaintenance(scopesightActive());
            publishSkillMask(changed,serverPackets);
            serverPackets.fixed(110,BootstrapPackets.runEnergy110(100));
            serverPackets.varShort(126,BootstrapPackets.widgetText126(149,"100%"));
            serverPackets.varShort(81,CombatSync.player81AnimationAndGfx(10184,1310,0,0));
            saveAccountQuiet(tag,"NURSE");
            System.out.println(tag+"V58_NURSE command="+command+" hp="+playerState.currentLevel(PlayerState.HITPOINTS)
                             +" prayer="+playerState.currentLevel(PlayerState.PRAYER)+" ranged="+playerState.currentLevel(PlayerState.RANGED)
                             +" magic="+playerState.currentLevel(PlayerState.MAGIC)+" special="+playerState.specialEnergy()
                             +" run="+movement.runEnergy()+" poison="+playerState.poison()+" venom="+playerState.venom()+" sicken="+playerState.sicken()
                             +" anim=10184 gfx=1310 gfxHeight=0 gfxDelay=0 scopesightActive="+scopesightActive());
            return;
        }
        if (p.length>=1 && p[0].equalsIgnoreCase("appfixture")) {
            String which=p.length>=2?p[1]:"help";
            String appResult=ApplicationUiFixtureService.run(which,serverPackets);
            System.out.println(tag+"R85_APP_FIXTURE "+appResult+" authority=LOCAL_DEV_FIXTURE clientProtocol=EXACT_CURRENT");
            return;
        }
        if (p.length>=1 && (p[0].equalsIgnoreCase("voidglass3")||p[0].equalsIgnoreCase("voidglass2"))) {
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"status";
            if(sub.equals("give")||sub.equals("item")){System.out.println(tag+"CUSTOM_PET_R3_VOIDGLASS "+giveVoidglassR3(serverPackets,tag));return;}
            if(sub.equals("candidate")||sub.equals("c")){
                int idx=p.length>=3?parseInt(p[2],-1):-1;
                System.out.println(tag+"CUSTOM_PET_R3_VOIDGLASS "+selectVoidglassR3Candidate(idx,serverPackets));return;
            }
            if(sub.equals("next")){System.out.println(tag+"CUSTOM_PET_R3_VOIDGLASS "+cycleVoidglassR3Candidate(serverPackets));return;}
            if(sub.equals("proc")){System.out.println(tag+"CUSTOM_PET_R3_VOIDGLASS "+triggerVoidglassR3Proc(serverPackets));return;}
            if(sub.equals("status")||sub.equals("info")){System.out.println(tag+"CUSTOM_PET_R3_VOIDGLASS "+voidglassR3Status());return;}
            if(sub.equals("reset")){
                if(!VoidglassR3CustomContent.active(petState,npcs.pet())){System.out.println(tag+"CUSTOM_PET_R3_VOIDGLASS_RESET result=REJECTED_ACTIVE_PET_NOT_VOIDGLASS_R3");return;}
                String a=npcs.previewPetDefinition(VoidglassR3CustomContent.DEFAULT_NPC_ID,movement,serverPackets), b=npcs.setPetNativeState(0,serverPackets), c=npcs.devSetParticleSelector(null,movement,serverPackets);
                System.out.println(tag+"CUSTOM_PET_R3_VOIDGLASS_RESET candidate={"+a+"} native={"+b+"} particles={"+c+"}");return;
            }
            System.out.println(tag+"CUSTOM_PET_R3_VOIDGLASS_HELP usage=::voidglass3 give|candidate <1..4>|next|proc|status|reset item=29999 note=voidglass2_alias_migrated_to_R3 customAuthority=LOCAL_DEV_ONLY");return;
        }

        if (p.length>=1 && p[0].equalsIgnoreCase("voidglass")) {
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"status";
            NpcEntity activePet=npcs.pet();
            if(sub.equals("help")){
                System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS_HELP usage=::item 22960 -> Drop -> ::voidglass on | fx <6|8|auto> | proc | status | off note=R1_reuses_Vasa_client_definition_session_only");
                return;
            }
            if(sub.equals("on")||sub.equals("enable")){
                if(voidglass.active()){
                    System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS result=ALREADY_ACTIVE "+voidglass.summary(dev.petParticleSelector()));
                    return;
                }
                if(!VoidglassPetProfile.matches(petState,activePet)){
                    System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS result=REJECTED_NEED_BASE_VASA expected="+VoidglassPetProfile.BASE_ITEM_ID+"->"+VoidglassPetProfile.BASE_NPC_ID+
                        " active="+(petState.active()?petState.itemId()+"->"+petState.npcId():"none")+
                        " instructions=::item_22960_then_Drop");
                    return;
                }
                Integer previous=dev.petParticleSelector();
                voidglass.activate(previous);
                String fx=npcs.devSetParticleSelector(VoidglassPetProfile.DEFAULT_PARTICLE_SELECTOR,movement,serverPackets);
                String text=npcs.forcePetText("VOIDGLASS",serverPackets);
                System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS result=ENABLED content="+VoidglassPetProfile.DISPLAY_NAME+
                    " baseItem="+VoidglassPetProfile.BASE_ITEM_ID+" baseNpc="+VoidglassPetProfile.BASE_NPC_ID+
                    " model="+VoidglassPetProfile.WORLD_MODEL_ID+" stand="+VoidglassPetProfile.STAND_ANIM+" walkTurn="+VoidglassPetProfile.WALK_TURN_ANIM+
                    " fx="+fx+" identityText="+text+" "+voidglass.summary(dev.petParticleSelector()));
                return;
            }
            if(sub.equals("off")||sub.equals("disable")){
                if(!voidglass.active()){
                    System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS result=ALREADY_OFF");
                    return;
                }
                Integer restore=voidglass.clearAndRestoreSelector();
                String fx=npcs.devSetParticleSelector(restore,movement,serverPackets);
                System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS result=DISABLED restoredFx="+(restore==null?"AUTO":restore)+" transport="+fx);
                return;
            }
            if(sub.equals("fx")){
                if(!voidglass.active() || !VoidglassPetProfile.matches(petState,activePet)){
                    System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS_FX result=REJECTED_NOT_ACTIVE");
                    return;
                }
                String v=p.length>=3?p[2].toLowerCase(java.util.Locale.ROOT):"auto";
                int selector=v.equals("auto")||v.equals("reset")?VoidglassPetProfile.DEFAULT_PARTICLE_SELECTOR:parseInt(v,-1);
                if(!VoidglassPetProfile.allowedSelector(selector)){
                    System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS_FX result=REJECTED selector=6_or_8_or_auto");
                    return;
                }
                voidglass.selectParticle(selector);
                String fx=npcs.devSetParticleSelector(selector,movement,serverPackets);
                System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS_FX result=OK selector="+selector+" transport="+fx+
                    " visual="+(selector==6?"MAGENTA":"CYAN_PINK_ALTERNATING"));
                return;
            }
            if(sub.equals("proc")){
                if(!voidglass.active() || !VoidglassPetProfile.matches(petState,activePet)){
                    System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS_PROC result=REJECTED_NOT_ACTIVE");
                    return;
                }
                voidglass.recordProc();
                String text=npcs.forcePetText(VoidglassPetProfile.PROC_TEXT,serverPackets);
                serverPackets.varShort(81,CombatSync.player81GfxOnly(VoidglassPetProfile.OWNER_PROC_GFX,0,0));
                System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS_PROC result=OK text="+text+" ownerAnim=NONE ownerGfx="+VoidglassPetProfile.OWNER_PROC_GFX+
                    " procCount="+voidglass.procCount()+" mechanic=PRESENTATION_ONLY_R1 combatAccuracyHook=DEFERRED");
                return;
            }
            if(sub.equals("status")||sub.equals("info")){
                System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS_STATUS "+voidglass.summary(dev.petParticleSelector())+
                    " visiblePet="+(activePet==null?"none":activePet.petItemId+"->"+activePet.definitionId)+
                    " customNameServerSide=Voidglass_Nistirio clientDefinitionName=Vasa_nistirio_pet limitation=NEW_CLIENT_DEFINITION_NOT_PACKED_IN_R1");
                return;
            }
            System.out.println(tag+"CUSTOM_PET_R1_VOIDGLASS_HELP usage=on | fx <6|8|auto> | proc | status | off");
            return;
        }

        if (p.length>=1 && p[0].equalsIgnoreCase("petboost")) {
            serverPackets.varShort(81,CombatSync.player81GfxOnly(1310,0,0));
            System.out.println(tag+"V593_PET_BOOST_FIXTURE result=PLAYER_PRESENTATION anim=NONE gfx=1310 productionNormalPetEvidence=LIVE_COMPONENT_ISOLATION");
            return;
        }
        if (p.length>=1 && p[0].equalsIgnoreCase("scopesnipe")) {
            if(!scopesightActive() || npcs.pet()==null || npcs.pet().definitionId!=ScopesightPetProfile.NPC_ID){
                System.out.println(tag+"V58_SCOPESIGHT_SNIPE result=REJECTED_NO_ACTIVE_SCOPESIGHT activePet="+(petState.active()?petState.itemId()+"->"+petState.npcId():"none"));
                return;
            }
            String r=npcs.forcePetText(ScopesightPetProfile.NATIVE_TRIGGER_TEXT,serverPackets);
            System.out.println(tag+"V58_SCOPESIGHT_SNIPE result="+r+" nativeClientTrigger=true");
            return;
        }
        if (p.length>=1 && p[0].equalsIgnoreCase("petproc")) {
            serverPackets.varShort(81,CombatSync.player81GfxOnly(1310,0,0));
            String snipe="NOT_SCOPESIGHT";
            if(scopesightActive() && npcs.pet()!=null && npcs.pet().definitionId==ScopesightPetProfile.NPC_ID)
                snipe=npcs.forcePetText(ScopesightPetProfile.NATIVE_TRIGGER_TEXT,serverPackets);
            System.out.println(tag+"V511_PET_PROC_FIXTURE playerAnim=NONE playerGfx=1310 scopesight="+snipe
                             +" semantics=PRODUCTION_NORMAL_PET_BOOST_PRESENTATION");
            return;
        }

        if (p.length>=1 && p[0].equalsIgnoreCase("petstatus")) {
            NpcEntity pet=npcs.pet();
            System.out.println(tag+"V59_PET_STATUS active="+(pet!=null)+" petState="+(petState.active()?petState.itemId()+"->"+petState.npcId():"none")+
                " visibleNpc="+(pet==null?"none":pet.definitionId)+" nativeFamily="+(pet==null?"NONE":PetPresentationProfile.nativeStateFamily(pet.definitionId))+
                " effectState={"+petEffects.summary()+"} followOwnerRunning="+npcs.recentOwnerRunning());
            return;
        }
        if (p.length>=1 && p[0].equalsIgnoreCase("pettestall")) {
            petTestSequenceStep=0; petTestSequenceAt=System.currentTimeMillis();
            System.out.println(tag+"V59_PET_TEST_ALL_ARMED activePet="+(npcs.pet()==null?"none":npcs.pet().petItemId+"->"+npcs.pet().definitionId)+
                " sequence=owner827,playerAnim10184_ONLY,playerGfx1310_ONLY,combined10184+1310,state1,state2,state3,state0,specific intervalMs=1800");
            return;
        }
        if (p.length>=1 && p[0].equalsIgnoreCase("pettest")) {
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"help";
            String r;
            if(sub.equals("help")){
                System.out.println(tag+"V59_PET_TEST_HELP commands=::pettestall | ::pettest all | profile | lifecycle | boost | state <0..3> | charge <0..3> | damage <amount> | reset | anim <id> [delay] | gfx <id> [height] [delay] | animfx <anim> <gfx> [height] [delay] | preview <npcId> | snipe | tempoross [state]");
                return;
            }
            if(sub.equals("all")){
                petTestSequenceStep=0; petTestSequenceAt=System.currentTimeMillis();
                System.out.println(tag+"V59_PET_TEST_ALL_ARMED alias=pettest_all activePet="+(npcs.pet()==null?"none":npcs.pet().petItemId+"->"+npcs.pet().definitionId)+" intervalMs=1800");
                return;
            }
            if(sub.equals("profile")){
                NpcEntity active=npcs.pet();
                System.out.println(tag+"V59_PET_TEST_PROFILE active="+(active!=null)+" pet="+(active==null?"none":active.petItemId+"->"+active.definitionId)+
                    " nativeFamily="+(active==null?"NONE":PetPresentationProfile.nativeStateFamily(active.definitionId))+" stateVisuals="+
                    (active==null?"none":PetPresentationProfile.nativeStateVisual(active.definitionId,1)+","+PetPresentationProfile.nativeStateVisual(active.definitionId,2)+","+PetPresentationProfile.nativeStateVisual(active.definitionId,3))+
                    " lifecycle=owner827_noGfx boost=owner10184+gfx1310 effectState={"+petEffects.summary()+"}");
                return;
            }
            if(sub.equals("lifecycle")){
                serverPackets.varShort(81,CombatSync.player81AnimationOnly(PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION));
                System.out.println(tag+"V59_PET_TEST lifecycle ownerAnim=827 ownerGfx=NONE evidence=V908_PRODUCTION_CERTIFIED");
                return;
            }
            if(sub.equals("boost")){
                serverPackets.varShort(81,CombatSync.player81GfxOnly(PetPresentationProfile.OWNER_BOOST_GFX,0,0));
                System.out.println(tag+"V593_PET_TEST boost ownerAnim=NONE ownerGfx=1310");
                return;
            }
            if(sub.equals("state")){
                int state=p.length>=3?parseInt(p[2],-1):-1;
                r=npcs.setPetNativeState(state,serverPackets);
                System.out.println(tag+"V59_PET_TEST state result="+r);
                return;
            }
            if(sub.equals("charge")){
                int charge=p.length>=3?parseInt(p[2],-1):-1;
                NpcEntity active=npcs.pet();
                if(active==null || !PetPresentationProfile.isChargePet(active.petItemId,active.definitionId) || charge<0 || charge>3){
                    System.out.println(tag+"V59_PET_TEST charge result=REJECTED expected=active_charge_pet_and_0..3"); return;
                }
                petEffects.forceCharge(charge,System.currentTimeMillis());
                r=npcs.setPetNativeState(charge,serverPackets);
                System.out.println(tag+"V59_PET_TEST charge result="+r+" effectState={"+petEffects.summary()+"}"); return;
            }
            if(sub.equals("damage")){
                int damage=p.length>=3?parseInt(p[2],0):0;
                applyPetDamage(damage,System.currentTimeMillis(),serverPackets,tag,"PETTEST_DAMAGE"); return;
            }
            if(sub.equals("reset")){
                NpcEntity active=npcs.pet();
                if(active!=null && PetPresentationProfile.supportsNativeState(active.definitionId)) r=npcs.setPetNativeState(0,serverPackets); else r="NO_NATIVE_STATE_TO_CLEAR";
                petEffects.forceCharge(0,System.currentTimeMillis());
                System.out.println(tag+"V59_PET_TEST reset result="+r+" effectState={"+petEffects.summary()+"}"); return;
            }
            if(sub.equals("anim")){
                int anim=p.length>=3?parseInt(p[2],-999):-999; int delay=p.length>=4?parseInt(p[3],0):0;
                r=npcs.animatePet(anim,delay,serverPackets);
                System.out.println(tag+"V59_PET_TEST anim result="+r+" semantics=RAW_LOCALHOST_VISUAL_PROBE");
                return;
            }
            if(sub.equals("gfx")){
                int gfx=p.length>=3?parseInt(p[2],-999):-999; int h=p.length>=4?parseInt(p[3],0):0; int d=p.length>=5?parseInt(p[4],0):0;
                r=npcs.gfxPet(gfx,h,d,serverPackets);
                System.out.println(tag+"V59_PET_TEST gfx result="+r+" codec=EXACT_PACKET65_MASK_0x80");
                return;
            }
            if(sub.equals("animfx")){
                int anim=p.length>=3?parseInt(p[2],-999):-999; int gfx=p.length>=4?parseInt(p[3],-999):-999; int h=p.length>=5?parseInt(p[4],0):0; int d=p.length>=6?parseInt(p[5],0):0;
                r=npcs.animationAndGfxPet(anim,0,gfx,h,d,serverPackets);
                System.out.println(tag+"V59_PET_TEST animfx result="+r+" semantics=RAW_LOCALHOST_VISUAL_PROBE");
                return;
            }
            if(sub.equals("preview")){
                int npc=p.length>=3?parseInt(p[2],-1):-1;
                r=npcs.previewPetDefinition(npc,movement,serverPackets);
                System.out.println(tag+"V59_PET_TEST preview result="+r+" note=NOT_PERSISTED_USE_TO_IDENTIFY_SCOOBY_COLOR_MAPPING");
                return;
            }
            if(sub.equals("snipe")){
                r=npcs.pet()!=null&&npcs.pet().definitionId==8330?npcs.forcePetText("SNIPE",serverPackets):"REJECTED_ACTIVE_PET_NOT_SCOPESIGHT";
                System.out.println(tag+"V59_PET_TEST snipe result="+r); return;
            }
            if(sub.equals("tempoross")){
                int state=p.length>=3?parseInt(p[2],1):1;
                if(npcs.pet()==null || PetPresentationProfile.nativeStateFamily(npcs.pet().definitionId)!=PetPresentationProfile.NativeStateFamily.TEMPOROSS_DEBUFF){
                    System.out.println(tag+"V59_PET_TEST tempoross result=REJECTED_ACTIVE_PET_NOT_TEMPOROSS"); return;
                }
                String a=npcs.animatePet(PetPresentationProfile.TEMPOROSS_ACTIVATION_ANIMATION_CANDIDATE,0,serverPackets);
                String st=npcs.setPetNativeState(state,serverPackets);
                System.out.println(tag+"V59_PET_TEST tempoross anim="+a+" state="+st+" animationEvidence=EXACT_CACHE_NAME_CANDIDATE_NOT_RUNTIME_BOUND");
                return;
            }
            System.out.println(tag+"V59_PET_TEST result=UNKNOWN_SUBCOMMAND sub="+sub+" use=::pettest_help");
            return;
        }
        if (p.length>=1 && (p[0].equalsIgnoreCase("behemothcharge") || p[0].equalsIgnoreCase("petcharge"))) {
            int charge=p.length>=2?parseInt(p[1],-1):-1;
            NpcEntity pet=npcs.pet();
            if(pet==null || !PetPresentationProfile.isChargePet(pet.petItemId,pet.definitionId)){
                System.out.println(tag+"V59_BEHEMOTH_CHARGE result=REJECTED_ACTIVE_PET_NOT_CHARGE_FAMILY"); return;
            }
            if(charge<0||charge>3){ System.out.println(tag+"V59_BEHEMOTH_CHARGE result=REJECTED_RANGE expected=0..3"); return; }
            petEffects.forceCharge(charge,System.currentTimeMillis());
            String state=npcs.setPetNativeState(charge,serverPackets);
            System.out.println(tag+"V59_BEHEMOTH_CHARGE result="+state+" effectState={"+petEffects.summary()+"}");
            return;
        }
        if (p.length>=1 && (p[0].equalsIgnoreCase("behemothhit") || p[0].equalsIgnoreCase("petdamage"))) {
            int damage=p.length>=2?parseInt(p[1],0):0;
            applyPetDamage(damage,System.currentTimeMillis(),serverPackets,tag,"MANUAL_BEHEMOTH_HIT");
            return;
        }
        if (p.length>=1 && p[0].equalsIgnoreCase("evilwolperproc")) {
            NpcEntity pet=npcs.pet();
            if(pet==null || !(pet.definitionId==6991||(pet.definitionId>=8124&&pet.definitionId<=8126))){
                System.out.println(tag+"V59_EVIL_WOLPER_PROC result=REJECTED_ACTIVE_PET_NOT_EVIL_WOLPER"); return;
            }
            int state=p.length>=2?parseInt(p[1],1):1; if(state<1||state>3)state=1;
            String st=npcs.setPetNativeState(state,serverPackets);
            serverPackets.varShort(81,CombatSync.player81GfxOnly(PetPresentationProfile.OWNER_BOOST_GFX,0,0));
            System.out.println(tag+"V593_EVIL_WOLPER_PROC state="+st+" ownerBoost=GFX1310_ONLY nativeIcon=sprite53 physicalBodyAnimation=UNRESOLVED_USE_pettest_anim");
            return;
        }
        if (p.length>=1 && p[0].equalsIgnoreCase("temporossproc")) {
            int state=p.length>=2?parseInt(p[1],1):1;
            NpcEntity pet=npcs.pet();
            if(pet==null || PetPresentationProfile.nativeStateFamily(pet.definitionId)!=PetPresentationProfile.NativeStateFamily.TEMPOROSS_DEBUFF){
                System.out.println(tag+"V59_TEMPOROSS_PROC result=REJECTED_ACTIVE_PET_NOT_TEMPOROSS"); return;
            }
            String a=npcs.animatePet(PetPresentationProfile.TEMPOROSS_ACTIVATION_ANIMATION_CANDIDATE,0,serverPackets);
            String st=npcs.setPetNativeState(state,serverPackets);
            System.out.println(tag+"V59_TEMPOROSS_PROC anim="+a+" state="+st+" anim15562Evidence=EXACT_CACHE_NAME_CANDIDATE nativeStateRenderer=EXACT_CLIENT");
            return;
        }
        if (p.length>=2 && p[0].equalsIgnoreCase("petnpc")) {
            int npc=parseInt(p[1],-1);
            String r=npcs.previewPetDefinition(npc,movement,serverPackets);
            System.out.println(tag+"V59_PET_NPC_PREVIEW result="+r+" recommendedScoobyCandidates=5159,5160,5162,6650");
            return;
        }

        if (p.length==7 && p[0].equalsIgnoreCase("compcolors")) {
            int[] selectors=new int[6];
            boolean valid=true;
            for(int i=0;i<6;i++){ selectors[i]=parseInt(p[i+1],-1); if(selectors[i]<0 || selectors[i]>19) valid=false; }
            if(!valid || !playerState.setCompSelectors(selectors)){
                System.out.println(tag+"V54_COMP_COLORS command="+command+" result=REJECTED_SELECTOR_RANGE expected=0..19");
                return;
            }
            boolean equipped=BootstrapPackets.hasSpecialCompletionistCape(equipment.appearanceItems());
            if(equipped) playerPresentation.refresh(username,equipment,playerState,serverPackets);
            saveAccountQuiet(tag,"COMP_COLORS");
            System.out.println(tag+"V55_COMP_COLORS command="+command+" result=APPLIED selectors="+playerState.compSelectorSummary()+" capeEquipped="+equipped+" appearanceRefresh="+equipped);
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("devhit")){
            String r=combat.devHitCommand(p);
            System.out.println(tag+"V5128_"+r);
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("petaccessory")){
            String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"status";
            if(sub.equals("off")||sub.equals("none")||sub.equals("disable")){activePetAccessoryItem=0;String visual=npcs.devSetParticleSelector(null,movement,serverPackets);saveAccountQuiet(tag,"PET_ACCESSORY_DEV_OFF");System.out.println(tag+"V5128_PET_ACCESSORY active=NONE visual={"+visual+"}");return;}
            System.out.println(tag+"V5128_PET_ACCESSORY active="+(activePetAccessoryItem==0?"NONE":activePetAccessoryItem+"/"+petAccessoryName(activePetAccessoryItem))+" visualSelectorMapping=UNRESOLVED_FAIL_CLOSED");
            return;
        }
        if(p.length>=1 && p[0].equalsIgnoreCase("petswitchcolor")){
            int requested=p.length>=2?parseInt(p[1],-1):-1;
            int slot=-1,current=-1;
            for(int i=0;i<bank.inventoryCapacity();i++){
                BankState.Stack st=bank.inventoryAt(i);
                if(st==null) continue;
                if(st.itemId>=24016 && st.itemId<=24019 && (requested<0 || st.itemId==requested)){slot=i;current=st.itemId;break;}
            }
            if(slot<0){System.out.println(tag+"V5128_SCOOBY_SWITCH_COLOR result=REJECTED_NO_VARIANT_IN_INVENTORY requested="+requested);return;}
            int[] family=petColorFamily(current);
            openPetColorDialog(slot,family,petColorFamilyName(current),serverPackets);
            System.out.println(tag+"V5128_SCOOBY_SWITCH_COLOR result=DIALOG_OPEN current="+current+" slot="+slot+" choices="+java.util.Arrays.toString(family)+" authority=LOCAL_COMPAT_EXTENSION native24019MenuAbsent=true");
            return;
        }

        if (p.length>=1 && p[0].equalsIgnoreCase("combatprobe")) {
            CombatWeaponProfile profile=CombatWeaponRepository.resolve(equipment.weapon());
            int combatRoot=CombatInterfaceRepository.forWeapon(equipment.weapon());
            System.out.println(tag+"V56_COMBAT_PROBE weapon="+equipment.weapon()+" profile="+profile
                             +" style={"+combatStyles.summary(combatRoot)+"}"
                             +" targetScene="+combat.state().targetSceneIndex+" targetDef="+combat.state().targetDefinitionId
                             +" context="+combat.state().context+" formula=UNRESOLVED_NO_DAMAGE_GUESS");
            return;
        }
        if (p.length>=1 && p[0].equalsIgnoreCase("combatfixture")) {
            int damage=p.length>=2?parseInt(p[1],0):0;
            String fixture=combat.fixtureHit(damage,npcs,serverPackets);
            int dealt=combat.consumeLastDamage();
            if(dealt>0) applyPetDamage(dealt,System.currentTimeMillis(),serverPackets,tag,"COMBAT_FIXTURE");
            System.out.println(tag+"V59_COMBAT_FIXTURE command="+command+" result="+fixture);
            return;
        }
        if (p.length>=2 && (p[0].equalsIgnoreCase("item") || p[0].equalsIgnoreCase("tabitem"))) {
            int id=parseInt(p[1],-1);
            int amount=p.length>=3?parseAmount(p[2],1):1;
            String spawn=bank.spawnItem(id,amount,serverPackets);
            saveAccountQuiet(tag, "ITEM_SPAWN");
            System.out.println(tag + "V522_ITEM_COMMAND source="+p[0].toLowerCase(java.util.Locale.ROOT)
                             + " command="+command+" result="+spawn
                             + " decoderAligned="+clientPackets.isAligned());
        }
    }


    private String giveVoidglassR3(ServerPacketWriter w,String tag)throws IOException{
        PetDefinitionRepository.Def d=PetDefinitionRepository.get(VoidglassR3CustomContent.ITEM_ID);
        if(d==null||d.npcId!=VoidglassR3CustomContent.DEFAULT_NPC_ID)return "VOIDGLASS_R3_GIVE_REJECTED serverDefinitionMissing=true";
        String r=bank.spawnItem(VoidglassR3CustomContent.ITEM_ID,1,w);saveAccountQuiet(tag,"CUSTOM_VOIDGLASS_R3_GIVE");
        return "VOIDGLASS_R3_GIVE "+r+" next=inventory_Drop normalLifecycle=true correctedClientRange=true legacy32760Retired=true";
    }
    private String selectVoidglassR3Candidate(int index,ServerPacketWriter w)throws IOException{
        if(!VoidglassR3CustomContent.active(petState,npcs.pet()))return "VOIDGLASS_R3_CANDIDATE_REJECTED activePetMustBe=item29999";
        VoidglassR3CustomContent.Candidate c=VoidglassR3CustomContent.candidate(index);if(c==null)return "VOIDGLASS_R3_CANDIDATE_REJECTED expected=1..4";
        String r=npcs.previewPetDefinition(c.npcId,movement,w);return "VOIDGLASS_R3_CANDIDATE_OK "+c.summary()+" transport={"+r+"} persistence=DEFAULT_CANDIDATE_ON_RELOGIN";
    }
    private String cycleVoidglassR3Candidate(ServerPacketWriter w)throws IOException{
        if(!VoidglassR3CustomContent.active(petState,npcs.pet()))return "VOIDGLASS_R3_CANDIDATE_REJECTED activePetMustBe=item29999";
        VoidglassR3CustomContent.Candidate cur=VoidglassR3CustomContent.candidateByNpc(npcs.pet().definitionId);int next=cur==null?1:(cur.index%VoidglassR3CustomContent.CANDIDATES.length)+1;return selectVoidglassR3Candidate(next,w);
    }
    private String triggerVoidglassR3Proc(ServerPacketWriter w)throws IOException{
        if(!VoidglassR3CustomContent.active(petState,npcs.pet()))return "VOIDGLASS_R3_PROC_REJECTED activePetMustBe=item29999 candidateNpc12000..12003";
        VoidglassR3CustomContent.Candidate c=VoidglassR3CustomContent.candidateByNpc(npcs.pet().definitionId);if(c==null)c=VoidglassR3CustomContent.defaultCandidate();
        String fx=npcs.animationAndGfxPet(c.stand,0,VoidglassR3CustomContent.PROC_GFX,0,0,w);String text=npcs.forcePetText(VoidglassR3CustomContent.PROC_TEXT,w);
        return "VOIDGLASS_R3_PROC_OK candidate="+c.index+" anim="+c.stand+" gfx="+VoidglassR3CustomContent.PROC_GFX+" text={"+text+"} visual={"+fx+"} hydraAssets=false gameplayModifier=NONE";
    }
    private String voidglassR3Status(){
        NpcEntity p=npcs.pet();VoidglassR3CustomContent.Candidate c=p==null?null:VoidglassR3CustomContent.candidateByNpc(p.definitionId);
        return VoidglassR3CustomContent.profile()+" active="+VoidglassR3CustomContent.active(petState,p)+" candidate="+(c==null?"none":c.summary())+
            " currentPet="+(p==null?"none":"item="+p.petItemId+" npc="+p.definitionId+" world="+p.x+","+p.y)+" currentMovementAuthority=R8.4/V9.12-derived-follow pickupFix=R8.1";
    }

    private String resetDevWorld(ServerPacketWriter serverPackets)throws IOException{
        int ground=0,objects=0;
        for(GroundItem g:world.groundItems().removeDevOwned()){try{scenePublisher.groundRemove(g);ground++;}catch(IllegalArgumentException ignored){}}
        for(WorldObject o:world.objects().removeDevOwned()){try{scenePublisher.objectRemove(o.tile,o.shape,o.rotation);objects++;}catch(IllegalArgumentException ignored){}}
        return "DEV_WORLD_RESET ground="+ground+" objects="+objects;
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

    private void applyPetDamage(int damage,long now,ServerPacketWriter serverPackets,String tag,String source)throws IOException{
        if(damage<=0){ System.out.println(tag+"V59_PET_DAMAGE source="+source+" result=IGNORED_NONPOSITIVE damage="+damage); return; }
        NpcEntity pet=npcs.pet();
        if(pet==null || !PetPresentationProfile.isChargePet(pet.petItemId,pet.definitionId)){
            System.out.println(tag+"V59_PET_DAMAGE source="+source+" damage="+damage+" result=NO_ACTIVE_CHARGE_PET"); return;
        }
        boolean changed=petEffects.recordDamage(damage,now);
        String presentation="UNCHANGED";
        if(changed) presentation=npcs.setPetNativeState(petEffects.charge(),serverPackets);
        System.out.println(tag+"V59_PET_DAMAGE source="+source+" damage="+damage+" chargeChanged="+changed+" presentation="+presentation+
            " effectState={"+petEffects.summary()+"} modifiersRecordedOnly=true combatM2FormulaStillFixture=true");
    }

    private void tickPetTestSequence(long now,ServerPacketWriter serverPackets,String tag)throws IOException{
        NpcEntity pet=npcs.pet();
        switch(petTestSequenceStep){
            case 0:
                serverPackets.varShort(81,CombatSync.player81AnimationOnly(PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION));
                System.out.println(tag+"V591_PET_TEST_ALL step=1/9 ownerLifecycleAnim=827 gfx=NONE"); break;
            case 1:
                serverPackets.varShort(81,CombatSync.player81AnimationOnly(PetPresentationProfile.OWNER_BOOST_ANIMATION));
                System.out.println(tag+"V591_PET_TEST_ALL step=2/9 playerAnim10184_ONLY gfx=NONE purpose=NURSE_COMPONENT_ISOLATION"); break;
            case 2:
                serverPackets.varShort(81,CombatSync.player81GfxOnly(PetPresentationProfile.OWNER_BOOST_GFX,0,0));
                System.out.println(tag+"V591_PET_TEST_ALL step=3/9 playerGfx1310_ONLY anim=NONE purpose=NURSE_COMPONENT_ISOLATION"); break;
            case 3:
                serverPackets.varShort(81,CombatSync.player81AnimationAndGfx(PetPresentationProfile.OWNER_BOOST_ANIMATION,PetPresentationProfile.OWNER_BOOST_GFX,0,0));
                System.out.println(tag+"V591_PET_TEST_ALL step=4/9 combined=10184+1310 purpose=REFERENCE_ONLY"); break;
            case 4: case 5: case 6:
                int state=petTestSequenceStep-3;
                String sr=pet==null?"SKIP_NO_ACTIVE_PET":npcs.setPetNativeState(state,serverPackets);
                System.out.println(tag+"V591_PET_TEST_ALL step="+(petTestSequenceStep+1)+"/9 state="+state+" result="+sr); break;
            case 7:
                String reset=pet==null?"SKIP_NO_ACTIVE_PET":npcs.setPetNativeState(0,serverPackets);
                System.out.println(tag+"V591_PET_TEST_ALL step=8/9 state=0 result="+reset); break;
            case 8:
                String specific="NONE_FOR_THIS_PET";
                if(pet!=null && pet.definitionId==8330) specific=npcs.forcePetText("SNIPE",serverPackets);
                else if(pet!=null && PetPresentationProfile.nativeStateFamily(pet.definitionId)==PetPresentationProfile.NativeStateFamily.TEMPOROSS_DEBUFF){
                    specific=npcs.animatePet(PetPresentationProfile.TEMPOROSS_ACTIVATION_ANIMATION_CANDIDATE,0,serverPackets)+"; "+npcs.setPetNativeState(1,serverPackets);
                } else if(pet!=null && PetPresentationProfile.nativeStateFamily(pet.definitionId)==PetPresentationProfile.NativeStateFamily.WOLPER_KRAMP_ACTIVE){
                    specific=npcs.setPetNativeState(1,serverPackets);
                }
                System.out.println(tag+"V591_PET_TEST_ALL step=9/9 specific="+specific);
                petTestSequenceStep=-1; petTestSequenceAt=Long.MAX_VALUE; return;
            default:
                petTestSequenceStep=-1; petTestSequenceAt=Long.MAX_VALUE; return;
        }
        petTestSequenceStep++;
        petTestSequenceAt=now+1800L;
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

    private String enterTransientRegionDev(int regionId,int plane,ServerPacketWriter w,String tag) throws IOException {
        if(world.players().size()!=1)return "REJECTED_MULTIPLAYER members="+world.players().size()+" reason=PER_VIEW_REGION_MEMBERSHIP_NOT_YET_PROMOTED";
        WorldRegionAuthorityRepository.Region r=WorldRegionAuthorityRepository.get(regionId);
        if(r==null)return "REJECTED_UNKNOWN_REGION id="+regionId;
        if(!r.mapPresent||!r.terrainParseOk)return "REJECTED_TERRAIN_NOT_DECODED id="+regionId+" mapPresent="+r.mapPresent+" terrainParseOk="+r.terrainParseOk;
        if(!WorldCollisionAuthority.hasRegion(regionId))return "REJECTED_COLLISION_AUTHORITY_MISSING id="+regionId;
        if(plane<0||plane>3)return "REJECTED_PLANE expected=0..3";
        Tile tile=WorldCollisionAuthority.safeTile(regionId,plane);
        if(tile==null)return "REJECTED_NO_SAFE_STATIC_TILE id="+regionId+" plane="+plane;
        TradeService.cancelIfActive(worldPlayer,"REGION_DEV_LOAD"); activePlayerFollow=null;activePlayerAttack=null;activePlayerTrade=null;combat.cancelForManualMovement();
        nextPetFollowAt=Long.MAX_VALUE; petFollowRealtimeScheduled=false;
        int chunkX=tile.x>>3, chunkY=tile.y>>3, baseX=(chunkX-6)<<3, baseY=(chunkY-6)<<3;
        // Explicitly remove the HOME NPC view before changing region. The server
        // registry itself stays intact so ::regionhome can republish the exact same
        // certified semantic actors without reconstructing or duplicating them.
        java.util.List<NpcEntity> oldNpcs=npcs.snapshot();
        if(!oldNpcs.isEmpty()){
            java.util.ArrayList<NpcSyncEncoder.Update> removals=new java.util.ArrayList<>();
            for(NpcEntity n:oldNpcs)removals.add(NpcSyncEncoder.Update.remove(n));
            w.varShort(65,NpcSyncEncoder.encode(removals,java.util.Collections.emptyList(),movement.x(),movement.y()));
        }
        movement.enterTransientRegion(tile.x,tile.y,plane,baseX,baseY);
        w.fixed(219,new byte[0]);
        w.fixed(73,BootstrapPackets.region73(chunkX,chunkY));
        w.varShort(81,BootstrapPackets.player81TeleportNoAppearance(plane,tile.y-baseY,tile.x-baseX));
        scenePublisher=new SceneUpdatePublisher(w,new SceneCoordinateContext(baseX,baseY,plane));
        saveAccountQuiet(tag,"REGION_DEV_LOAD_NONPERSISTENT");
        return "OK region="+regionId+" name=["+r.name+"] group=["+r.group+"] landing="+tile.x+","+tile.y+","+plane+" base="+baseX+","+baseY+" packet73="+chunkX+","+chunkY+" collision=EXACT_CURRENT_STATIC removedHomeNpcView="+oldNpcs.size()+" arrivalAuthority=LOCAL_DEV_SAFE_TILE_NOT_PRODUCTION persistence=HOME_FALLBACK";
    }

    private String returnHomeFromTransientRegion(ServerPacketWriter w,String tag) throws IOException {
        if(!movement.transientRegion())return "ALREADY_HOME world="+movement.x()+","+movement.y();
        if(world.players().size()!=1)return "REJECTED_MULTIPLAYER members="+world.players().size();
        movement.returnHome();
        w.fixed(219,new byte[0]);
        w.fixed(73,BootstrapPackets.region73(385,436));
        w.varShort(81,BootstrapPackets.player81TeleportNoAppearance(0,55,55));
        scenePublisher=new SceneUpdatePublisher(w,new SceneCoordinateContext(MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y,0));
        HomeObjectOverlayReplayer.Stats scene=homeWorld.replayScene(w,MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y);
        scenePublisher.context().invalidate();
        // Registry identity/state survived the transient projection. Republish it
        // as an initial HOME NPC view instead of calling bootstrapHome(), which
        // would risk duplicating semantic actors in the server registry.
        java.util.List<NpcEntity> homeNpcs=npcs.snapshot();
        if(!homeNpcs.isEmpty())w.varShort(65,NpcSyncEncoder.initial(homeNpcs,movement.x(),movement.y()));
        int replay=0;for(GroundItem g:world.groundItems().snapshot())if(g.owner==null||g.owner.equalsIgnoreCase(username)){scenePublisher.groundSpawn(g);replay++;}
        saveAccountQuiet(tag,"REGION_DEV_RETURN_HOME");
        return "OK world="+movement.x()+","+movement.y()+" packet73=385,436 scene={"+scene+"} groundReplay="+replay+" npcRepublish="+homeNpcs.size()+" pet="+petState.active()+"";
    }

    private void saveAccountQuiet(String tag, String reason) {
        if (!persistentAccount) return;
        try {
            String result=LocalAccountProfiles.save(username,bank,equipment,movement,petState,playerState);
            PetAccessoryPersistence.save(username,activePetAccessoryItem);
            System.out.println(tag + "V5123_ACCOUNT_SAVE reason="+reason+" " + result + " petAccessory="+(activePetAccessoryItem==0?"NONE":activePetAccessoryItem));
        } catch (Throwable e) {
            System.err.println(tag + "V5123_ACCOUNT_SAVE_FAILED reason="+reason+" file="+LocalAccountProfiles.accountFile(username)+" profile="+username+" error="+e);
        }
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
        clearMiniConfigureDialog();clearPetColorDialog();clearPetAccessoryDialog();
        w.fixed(219,new byte[0]);
        devPanel.open(page);
        renderDevPanel(w);
    }

    private void renderDevPanel(ServerPacketWriter w)throws IOException{
        if(!devPanel.isOpen())return;
        String title="LocalLab Dev Control Center";
        String[] o={"","","",""};
        switch(devPanel.page()){
            case MAIN:
                title="LocalLab Dev Control Center | "+BuildInfo.VERSION;
                o[0]="Combat & weapons";o[1]="Pets";o[2]="Magic & prayer";o[3]="More systems...";break;
            case MORE:
                title="More systems";
                o[0]="World & collision";o[1]="Items & native UI";o[2]="Player / NPC lab";o[3]="Diagnostics";break;
            case COMBAT:{
                int wid=equipment.weapon(); CombatStyleRepository.Style st=combatStyles.current(CombatInterfaceRepository.forWeapon(wid));
                title="Combat | "+wid+" "+devItemName(wid);
                o[0]="Attack animation...";o[1]="Hitsplat lab...";o[2]="Cycle style ["+(st==null?"?":st.label)+"]";o[3]="More combat...";break;}
            case COMBAT_MORE:{
                int wid=equipment.weapon();V913WeaponRuntimeAuthority.Profile rp=V913WeaponRuntimeAuthority.resolve(wid);
                title="Combat systems | runtime="+(rp==null?"none":wid);
                o[0]="Runtime weapon lab...";o[1]="Current authority summary";o[2]="Back to main";o[3]="Back to combat";break;}
            case COMBAT_RUNTIME:{
                V913WeaponRuntimeAuthority.Profile rp=selectedRuntimeWeaponProfile();
                title="Runtime weapon lab | "+(rp==null?"none":rp.itemId+" "+rp.name);
                o[0]="Use equipped weapon";o[1]="Browse runtime item ID...";o[2]="Preview safe presentation";o[3]="Back";break;}
            case COMBAT_ANIM:{
                int wid=equipment.weapon();String ov=dev.hasCombatAnimationOverride(wid)?String.valueOf(dev.combatAnimationOverride(wid)):"AUTO";
                title="Attack animation | weapon "+wid+" | "+ov;
                o[0]="Set animation ID...";o[1]="Reset to authority";o[2]="Play current now";o[3]="Back";break;}
            case COMBAT_HIT:
                title="Hitsplat lab | "+clip(combat.devHitSummary(),58);
                o[0]="Set damage...";o[1]="Set type...";o[2]="Auto normal/max";o[3]="Back";break;
            case PETS:{
                NpcEntity pet=npcs.pet();
                title="Pets | "+(pet==null?"none":"item "+pet.petItemId+" npc "+pet.definitionId);
                o[0]="Particle FX...";o[1]="Follow controls...";o[2]="Presentation...";o[3]="Back";break;}
            case PET_FX:
                title="Pet particle selector | "+(dev.petParticleSelector()==null?"AUTO":dev.petParticleSelector());
                o[0]="Next selector";o[1]="Auto / clear";o[2]="Set selector ID...";o[3]="Back";break;
            case PET_FOLLOW:
                title="Pet follow | frozen="+npcs.followFrozen()+" delay="+(dev.petFollowDelayMs()==null?"AUTO":dev.petFollowDelayMs()+"ms");
                o[0]=npcs.followFrozen()?"Resume follow":"Freeze follow";o[1]="Step once";o[2]="Snap to owner";o[3]="Back";break;
            case PET_PRESENT:
                title="Pet presentation | native state="+npcs.petNativeState();
                o[0]="Play animation ID...";o[1]="Play GFX ID...";o[2]="Cycle native state";o[3]="More presentation...";break;
            case PET_PRESENT_MORE:
                title="Pet presentation / custom | "+(npcs.pet()==null?"no active pet":"npc "+npcs.pet().definitionId);
                o[0]="Custom content...";o[1]="Show pet info";o[2]="R1 Voidglass status";o[3]="Back";break;
            case CUSTOM_CONTENT:
                title="Custom content | LOCAL DEV - not production authority";
                o[0]="Voidglass Nistirio R3...";o[1]="Boundary / provenance";o[2]="R1 prototype reference";o[3]="Back";break;
            case CUSTOM_VOIDGLASS:
                title="Voidglass R3 | "+(VoidglassR3CustomContent.active(petState,npcs.pet())?"ACTIVE":"inactive")+" | item 29999";
                o[0]="Give item 29999";o[1]="Next visual candidate";o[2]="Trigger VOIDGLASS RIFT";o[3]="Back";break;
            case MAGIC_PRAYER:
                title="Magic / Prayer | spells="+SpellDefinitionRepository.count()+" prayers="+PrayerDefinitionRepository.count();
                o[0]="Magic controls...";o[1]="Prayer controls...";o[2]="Deactivate all prayers";o[3]="Back";break;
            case MAGIC:
                title="Magic book | "+magic.book()+" | root="+magic.root();
                o[0]="Modern";o[1]="Ancient";o[2]="Lunar";o[3]="Back";break;
            case PRAYER:
                title="Prayer | "+prayers.book()+" active="+prayers.activeCount()+" icon="+prayers.manualHeadIcon();
                o[0]="Toggle prayer book";o[1]="Toggle widget ID...";o[2]="Manual head icon...";o[3]="Back";break;
            case WORLD:{
                int rid=((movement.x()>>6)<<8)|(movement.y()>>6);
                title="World | "+movement.x()+","+movement.y()+","+movement.plane()+" region="+rid;
                o[0]="Load region ID...";o[1]="Return HOME";o[2]="Inspect collision";o[3]="Back";break;}
            case ITEMS:
                title="Items | weapon "+equipment.weapon()+" "+devItemName(equipment.weapon());
                o[0]="Open Item Library ID...";o[1]="Equipment Stats";o[2]="Items Kept on Death";o[3]="Back";break;
            case PLAYER_NPC:
                title="Player / NPC lab | visible NPCs="+npcs.visibleCount();
                o[0]="Player presentation...";o[1]="NPC sandbox...";o[2]="Authority census";o[3]="Back";break;
            case PLAYER:
                title="Player | "+playerPresentation.info();
                o[0]="Morph to NPC ID...";o[1]="Clear morph";o[2]="Presentation...";o[3]="Back";break;
            case PLAYER_PRESENT:
                title="Player presentation probe";
                o[0]="Play animation ID...";o[1]="Play GFX ID...";o[2]="Refresh appearance";o[3]="Back";break;
            case NPC:
                title="NPC sandbox | visible="+npcs.visibleCount();
                o[0]="Spawn NPC ID...";o[1]="Clear dev NPCs";o[2]="List NPCs to log";o[3]="Back";break;
            case DIAG:
                title="Diagnostics | trace="+dev.trace().enabled()+" | "+clip(ContentAuthorityRepository.summary(),42);
                o[0]=dev.trace().enabled()?"Disable protocol trace":"Enable protocol trace";o[1]="Authority browser...";o[2]="Reset dev overrides...";o[3]="Back";break;
            case AUTHORITY:
                title="Authority browser | read-only | "+ClientAssetAlignmentAuthority.shortStatus();
                o[0]="Magic / prayer authority...";o[1]="World / item authority...";o[2]="Research closure...";o[3]="Back";break;
            case AUTH_MAGIC:
                title="Magic authority | "+clip(selectedMagicAuthoritySummary(),58);
                o[0]="Browse spell widget ID...";o[1]="Browse prayer widget ID...";o[2]="Counts / boundary";o[3]="Back";break;
            case AUTH_WORLD_ITEM:
                title="World/item authority | "+clip(selectedWorldItemAuthoritySummary(),54);
                o[0]="Browse item ID...";o[1]="Browse region ID...";o[2]="Use current region";o[3]="Back";break;
            case RESEARCH:
                title="Research closure | "+clip(ResearchExhaustionAuthority.summary(),58);
                o[0]="Equipment/static stats...";o[1]="Pet proc/presentation...";o[2]="World transitions...";o[3]="Client discovery...";break;
            case RESEARCH_EQUIP:{
                int id=devPanel.selectedResearchEquipItemId();if(id<0)id=equipment.weapon();
                title="Equipment research | "+clip(EquipmentResearchAuthority.itemSummary(id),55);
                o[0]="Browse item ID...";o[1]="Use equipped weapon";o[2]="Schema / server boundary";o[3]="Back";break;}
            case RESEARCH_PET:{
                PetProcResearchAuthority.Row row=selectedPetProcResearchRow();
                title="Pet research | "+clip(PetProcResearchAuthority.summary(row)+" | "+PetMovementResearchAuthority.summary(),55);
                o[0]="Use active pet";o[1]="Browse research row #...";o[2]="Preview first candidate";o[3]="Back";break;}
            case RESEARCH_WORLD:{
                int rid=devPanel.selectedResearchTransitionRegion();if(rid<0)rid=((movement.x()>>6)<<8)|(movement.y()>>6);
                int iid=devPanel.selectedResearchTeleportItemId();
                title="World transitions | "+clip((iid<0?"tele:none":WorldTransitionResearchAuthority.teleportSummary(iid))+" | "+WorldTransitionResearchAuthority.regionSummary(rid),55);
                o[0]="Browse teleport item ID...";o[1]="Use current region";o[2]="Service/static contracts...";o[3]="Back";break;}
            case RESEARCH_SERVICE:
                title="NPC / world / shop static | "+clip(ServiceResearchAuthority.summary(),55);
                o[0]="Interaction router atlas";o[1]="Blood / enchantment contracts";o[2]="Shop framework / boundaries";o[3]="Back";break;
            case RESEARCH_DISCOVERY:
                title="Client discovery | "+clip(ClientDiscoveryAuthority.summary(),58);
                o[0]="Tasks / achievements";o[1]="Magic / construction";o[2]="UI / controls / minigames";o[3]="Asset/model closure...";break;
            case RESEARCH_ASSET:
                title="Asset/model closure | "+clip(AssetRuntimeResearchAuthority.archiveSummary(),55);
                o[0]="Client/assets alignment...";o[1]="Asset/model summaries";o[2]="Application protocols...";o[3]="Back";break;
            case RESEARCH_PROTOCOL:
                title="Application protocols | "+clip(ClientApplicationProtocolAuthority.summary(),55);
                o[0]="S2C250 application bus";o[1]="S2C126 / VM / settings";o[2]="C2S / world / events";o[3]="Back";break;
            case ALIGNMENT:
                title="Client/assets alignment | "+ClientAssetAlignmentAuthority.shortStatus();
                o[0]="Show pinned hashes";o[1]="Authority census";o[2]="Root 328 reconciliation";o[3]="Back";break;
            case RESET_CONFIRM:
                title="Reset ALL temporary dev overrides?";
                o[0]="CONFIRM reset all";o[1]="Cancel";o[2]="Close panel";o[3]="Back";break;
            default: break;
        }
        w.varShort(126,BootstrapPackets.widgetText126(2481,clip(title,80)));
        for(int i=0;i<4;i++)w.varShort(126,BootstrapPackets.widgetText126(2482+i,clip(o[i],76)));
        w.fixed(164,BootstrapPackets.chatboxInterface164(2480));
        publishDialogNumberKeys(2482,2483,2484,2485);
    }

    private void handleDevPanelWidget(int widget,ServerPacketWriter w,String tag)throws IOException{
        if(widget==54195){w.fixed(219,new byte[0]);devPanel.close();clearDialogNumberKeys();System.out.println(tag+"V5171_DEV_PANEL_CLOSE widget=54195");return;}
        int choice=widget-2482;if(choice<0||choice>3)return;
        String result="";
        switch(devPanel.page()){
            case MAIN:
                devPanel.setPage(choice==0?DevControlCenter.Page.COMBAT:choice==1?DevControlCenter.Page.PETS:choice==2?DevControlCenter.Page.MAGIC_PRAYER:DevControlCenter.Page.MORE);break;
            case MORE:
                devPanel.setPage(choice==0?DevControlCenter.Page.WORLD:choice==1?DevControlCenter.Page.ITEMS:choice==2?DevControlCenter.Page.PLAYER_NPC:DevControlCenter.Page.DIAG);break;
            case COMBAT:
                if(choice==0)devPanel.setPage(DevControlCenter.Page.COMBAT_ANIM);
                else if(choice==1)devPanel.setPage(DevControlCenter.Page.COMBAT_HIT);
                else if(choice==2)result=cycleCombatStyleFromPanel(w);
                else devPanel.setPage(DevControlCenter.Page.COMBAT_MORE);break;
            case COMBAT_MORE:
                if(choice==0)devPanel.setPage(DevControlCenter.Page.COMBAT_RUNTIME);
                else if(choice==1)result=runtimeWeaponAuthoritySummary(equipment.weapon());
                else if(choice==2)devPanel.setPage(DevControlCenter.Page.MAIN);
                else devPanel.setPage(DevControlCenter.Page.COMBAT);break;
            case COMBAT_RUNTIME:
                if(choice==0){V913WeaponRuntimeAuthority.Profile rp=V913WeaponRuntimeAuthority.resolve(equipment.weapon());if(rp==null)result="REJECTED equipped weapon has no V9.13 runtime profile: "+equipment.weapon();else{devPanel.selectRuntimeWeaponItemId(equipment.weapon());result=runtimeWeaponAuthoritySummary(equipment.weapon());}}
                else if(choice==1){promptDevPanelAmount(DevControlCenter.PendingAmount.RUNTIME_WEAPON_ITEM,w);return;}
                else if(choice==2){V913WeaponRuntimeAuthority.Profile rp=selectedRuntimeWeaponProfile();result=RuntimeWeaponPresentationLab.preview(rp,npcs,movement,scenePublisher,w);}
                else devPanel.setPage(DevControlCenter.Page.COMBAT_MORE);break;
            case COMBAT_ANIM:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.COMBAT_ANIM,w);return;}
                else if(choice==1){dev.setCombatAnimationOverride(equipment.weapon(),null);result="combat animation reset to authority";}
                else if(choice==2)result=playCurrentAttackAnimation(w);
                else devPanel.setPage(DevControlCenter.Page.COMBAT);break;
            case COMBAT_HIT:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.HIT_DAMAGE,w);return;}
                else if(choice==1){promptDevPanelAmount(DevControlCenter.PendingAmount.HIT_TYPE,w);return;}
                else if(choice==2)result=combat.devHitCommand(new String[]{"devhit","variant","auto"});
                else devPanel.setPage(DevControlCenter.Page.COMBAT);break;
            case PETS:
                devPanel.setPage(choice==0?DevControlCenter.Page.PET_FX:choice==1?DevControlCenter.Page.PET_FOLLOW:choice==2?DevControlCenter.Page.PET_PRESENT:DevControlCenter.Page.MAIN);break;
            case PET_FX:
                if(choice==0){Integer cur=dev.petParticleSelector();Integer next=cur==null?0:(cur>=255?null:cur+1);result=npcs.devSetParticleSelector(next,movement,w);}
                else if(choice==1)result=npcs.devSetParticleSelector(null,movement,w);
                else if(choice==2){promptDevPanelAmount(DevControlCenter.PendingAmount.PET_FX,w);return;}
                else devPanel.setPage(DevControlCenter.Page.PETS);break;
            case PET_FOLLOW:
                if(choice==0)result=npcs.devFollowFreeze(!npcs.followFrozen());
                else if(choice==1)result=npcs.devFollowStep(movement,w);
                else if(choice==2)result=npcs.devSnapToOwner(movement,w);
                else devPanel.setPage(DevControlCenter.Page.PETS);break;
            case PET_PRESENT:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.PET_ANIM,w);return;}
                else if(choice==1){promptDevPanelAmount(DevControlCenter.PendingAmount.PET_GFX,w);return;}
                else if(choice==2){int n=(npcs.petNativeState()+1)&3;result=npcs.setPetNativeState(n,w);}
                else devPanel.setPage(DevControlCenter.Page.PET_PRESENT_MORE);break;
            case PET_PRESENT_MORE:
                if(choice==0)devPanel.setPage(DevControlCenter.Page.CUSTOM_CONTENT);
                else if(choice==1)result=npcs.devInfo(movement);
                else if(choice==2)result="R1 reference: "+voidglass.summary(dev.petParticleSelector())+" | R3 item29999 + candidate NPC12000..12003";
                else devPanel.setPage(DevControlCenter.Page.PET_PRESENT);break;
            case CUSTOM_CONTENT:
                if(choice==0)devPanel.setPage(DevControlCenter.Page.CUSTOM_VOIDGLASS);
                else if(choice==1)result=VoidglassR3CustomContent.boundary();
                else if(choice==2)result="R1 prototype: item22960 Vasa -> npc3701; session-only overlay. Kept for regression/reference only.";
                else devPanel.setPage(DevControlCenter.Page.PET_PRESENT_MORE);break;
            case CUSTOM_VOIDGLASS:
                if(choice==0)result=giveVoidglassR3(w,tag);
                else if(choice==1)result=cycleVoidglassR3Candidate(w);
                else if(choice==2)result=triggerVoidglassR3Proc(w);
                else devPanel.setPage(DevControlCenter.Page.CUSTOM_CONTENT);break;
            case MAGIC_PRAYER:
                if(choice==0)devPanel.setPage(DevControlCenter.Page.MAGIC);
                else if(choice==1)devPanel.setPage(DevControlCenter.Page.PRAYER);
                else if(choice==2)result=prayers.deactivateAll(w);
                else devPanel.setPage(DevControlCenter.Page.MAIN);break;
            case MAGIC:
                if(choice==0)result=magic.switchBook("modern",w);
                else if(choice==1)result=magic.switchBook("ancient",w);
                else if(choice==2)result=magic.switchBook("lunar",w);
                else devPanel.setPage(DevControlCenter.Page.MAGIC_PRAYER);break;
            case PRAYER:
                if(choice==0){result=prayers.switchBook(prayers.book()==PrayerDefinitionRepository.Book.NORMAL?"curses":"normal",w);}
                else if(choice==1){promptDevPanelAmount(DevControlCenter.PendingAmount.PRAYER_WIDGET,w);return;}
                else if(choice==2){promptDevPanelAmount(DevControlCenter.PendingAmount.PRAYER_ICON,w);return;}
                else devPanel.setPage(DevControlCenter.Page.MAGIC_PRAYER);break;
            case WORLD:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.REGION_ID,w);return;}
                else if(choice==1)result=returnHomeFromTransientRegion(w,tag);
                else if(choice==2){int mask=WorldCollisionAuthority.maskAt(movement.x(),movement.y(),movement.plane());result="collision world="+movement.x()+","+movement.y()+","+movement.plane()+" mask="+mask+" blocked="+WorldCollisionAuthority.blockedTile(movement.x(),movement.y(),movement.plane());}
                else devPanel.setPage(DevControlCenter.Page.MORE);break;
            case ITEMS:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.ITEM_LIBRARY_ID,w);return;}
                else if(choice==1){w.fixed(219,new byte[0]);devPanel.close();clearDialogNumberKeys();result=NativeEquipmentDeathUi.openEquipmentStats(w,equipment);System.out.println(tag+"V5171_DEV_PANEL action=EQUIPMENT_STATS result="+result);return;}
                else if(choice==2){w.fixed(219,new byte[0]);devPanel.close();clearDialogNumberKeys();result=NativeEquipmentDeathUi.openDeathPreview(w,bank,equipment);System.out.println(tag+"V5171_DEV_PANEL action=DEATH_PREVIEW result="+result);return;}
                else devPanel.setPage(DevControlCenter.Page.MORE);break;
            case PLAYER_NPC:
                if(choice==0)devPanel.setPage(DevControlCenter.Page.PLAYER);
                else if(choice==1)devPanel.setPage(DevControlCenter.Page.NPC);
                else if(choice==2)result=ContentAuthorityRepository.summary()+" | "+ContentAuthorityRepository.itemSummary(equipment.weapon());
                else devPanel.setPage(DevControlCenter.Page.MORE);break;
            case PLAYER:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.PLAYER_MORPH,w);return;}
                else if(choice==1)result=playerPresentation.clear(username,equipment,playerState,w);
                else if(choice==2)devPanel.setPage(DevControlCenter.Page.PLAYER_PRESENT);
                else devPanel.setPage(DevControlCenter.Page.PLAYER_NPC);break;
            case PLAYER_PRESENT:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.PLAYER_ANIM,w);return;}
                else if(choice==1){promptDevPanelAmount(DevControlCenter.PendingAmount.PLAYER_GFX,w);return;}
                else if(choice==2){playerPresentation.refresh(username,equipment,playerState,w);result="player appearance refreshed";}
                else devPanel.setPage(DevControlCenter.Page.PLAYER);break;
            case NPC:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.NPC_SPAWN,w);return;}
                else if(choice==1)result=npcs.devRemoveAllNpcs(w);
                else if(choice==2)result=npcs.devNpcList(20);
                else devPanel.setPage(DevControlCenter.Page.PLAYER_NPC);break;
            case DIAG:
                if(choice==0){dev.trace().setEnabled(!dev.trace().enabled());result=dev.trace().summary();}
                else if(choice==1)devPanel.setPage(DevControlCenter.Page.AUTHORITY);
                else if(choice==2)devPanel.setPage(DevControlCenter.Page.RESET_CONFIRM);
                else devPanel.setPage(DevControlCenter.Page.MORE);break;
            case AUTHORITY:
                if(choice==0)devPanel.setPage(DevControlCenter.Page.AUTH_MAGIC);
                else if(choice==1)devPanel.setPage(DevControlCenter.Page.AUTH_WORLD_ITEM);
                else if(choice==2)devPanel.setPage(DevControlCenter.Page.RESEARCH);
                else devPanel.setPage(DevControlCenter.Page.DIAG);break;
            case AUTH_MAGIC:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.AUTH_SPELL_WIDGET,w);return;}
                else if(choice==1){promptDevPanelAmount(DevControlCenter.PendingAmount.AUTH_PRAYER_WIDGET,w);return;}
                else if(choice==2)result="read-only authority: spells="+SpellDefinitionRepository.count()+" prayers="+PrayerDefinitionRepository.count()+"; server-owned costs/effects remain evidence-gated";
                else devPanel.setPage(DevControlCenter.Page.AUTHORITY);break;
            case AUTH_WORLD_ITEM:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.AUTH_ITEM_ID,w);return;}
                else if(choice==1){promptDevPanelAmount(DevControlCenter.PendingAmount.AUTH_REGION_ID,w);return;}
                else if(choice==2){int rid=((movement.x()>>6)<<8)|(movement.y()>>6);devPanel.selectRegionId(rid);result=regionAuthoritySummary(rid);}
                else devPanel.setPage(DevControlCenter.Page.AUTHORITY);break;
            case RESEARCH:
                if(choice==0)devPanel.setPage(DevControlCenter.Page.RESEARCH_EQUIP);
                else if(choice==1)devPanel.setPage(DevControlCenter.Page.RESEARCH_PET);
                else if(choice==2)devPanel.setPage(DevControlCenter.Page.RESEARCH_WORLD);
                else devPanel.setPage(DevControlCenter.Page.RESEARCH_DISCOVERY);break;
            case RESEARCH_EQUIP:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.RESEARCH_EQUIP_ITEM,w);return;}
                else if(choice==1){int id=equipment.weapon();devPanel.selectResearchEquipItemId(id);result=EquipmentResearchAuthority.itemSummary(id);}
                else if(choice==2)result="fields="+java.util.Arrays.toString(EquipmentResearchAuthority.BASE_PROFILE_FIELDS)+" | "+EquipmentResearchAuthority.QUERY_EQUIPSTR+" | "+EquipmentResearchAuthority.boundary();
                else devPanel.setPage(DevControlCenter.Page.RESEARCH);break;
            case RESEARCH_PET:
                if(choice==0){NpcEntity pet=npcs.pet();if(pet==null)result="REJECTED no active main pet";else{PetProcResearchAuthority.Row row=PetProcResearchAuthority.findForPetItem(pet.petItemId);if(row==null)result="activePet item="+pet.petItemId+" has no actual Item+Pet R4 proc tuple";else{devPanel.selectResearchPetRow(row.index);result=PetProcResearchAuthority.summary(row);}}}
                else if(choice==1){promptDevPanelAmount(DevControlCenter.PendingAmount.RESEARCH_PET_ROW,w);return;}
                else if(choice==2)result=previewSelectedPetResearchCandidate(w);
                else devPanel.setPage(DevControlCenter.Page.RESEARCH);break;
            case RESEARCH_WORLD:
                if(choice==0){promptDevPanelAmount(DevControlCenter.PendingAmount.RESEARCH_TELE_ITEM,w);return;}
                else if(choice==1){int rid=((movement.x()>>6)<<8)|(movement.y()>>6);devPanel.selectResearchTransitionRegion(rid);result=WorldTransitionResearchAuthority.regionSummary(rid)+" | "+WorldFullResearchAuthority.regionSummary(rid);}
                else if(choice==2)devPanel.setPage(DevControlCenter.Page.RESEARCH_SERVICE);
                else devPanel.setPage(DevControlCenter.Page.RESEARCH);break;
            case RESEARCH_SERVICE:
                if(choice==0)result=ServiceResearchAuthority.routerSummary();
                else if(choice==1)result=ServiceResearchAuthority.bloodSummary();
                else if(choice==2)result=ServiceResearchAuthority.shopSummary();
                else devPanel.setPage(DevControlCenter.Page.RESEARCH_WORLD);break;
            case RESEARCH_DISCOVERY:
                if(choice==0)result=ClientDiscoveryAuthority.taskAchievementSummary()+" | "+ClientDiscoveryAuthority.boundary();
                else if(choice==1)result=ClientDiscoveryAuthority.magicConstructionSummary()+" | "+ClientDiscoveryAuthority.boundary();
                else if(choice==2)result=ClientDiscoveryAuthority.uiControlSummary()+" | "+ClientDiscoveryAuthority.minigameSummary()+" | "+ClientDiscoveryAuthority.boundary();
                else devPanel.setPage(DevControlCenter.Page.RESEARCH_ASSET);break;
            case RESEARCH_ASSET:
                if(choice==0)devPanel.setPage(DevControlCenter.Page.ALIGNMENT);
                else if(choice==1)result=AssetRuntimeResearchAuthority.mayaSummary()+" | "+AssetRuntimeResearchAuthority.objectRawSummary()+" | "+AssetRuntimeResearchAuthority.updaterBoundary();
                else if(choice==2)devPanel.setPage(DevControlCenter.Page.RESEARCH_PROTOCOL);
                else devPanel.setPage(DevControlCenter.Page.RESEARCH_DISCOVERY);break;
            case RESEARCH_PROTOCOL:
                if(choice==0)result=ClientApplicationProtocolAuthority.applicationSummary()+" | "+ClientApplicationProtocolAuthority.boundary();
                else if(choice==1)result=ClientApplicationProtocolAuthority.controlSummary()+" | "+ClientApplicationProtocolAuthority.boundary();
                else if(choice==2)result=ClientApplicationProtocolAuthority.interactionSummary()+" | "+ClientApplicationProtocolAuthority.boundary();
                else devPanel.setPage(DevControlCenter.Page.RESEARCH_ASSET);break;
            case ALIGNMENT:
                if(choice==0)result="client="+ClientAssetAlignmentAuthority.CLIENT_SHA256+" assets="+ClientAssetAlignmentAuthority.SPAWNPK_ASSET_BUNDLE_SHA256+" authority="+ClientAssetAlignmentAuthority.SPAWNPK_AUTHORITY_SHA256;
                else if(choice==1)result=ContentAuthorityRepository.summary()+" collisionRegions="+WorldCollisionAuthority.regionCount()+" worldPlacements="+ClientAssetAlignmentAuthority.STATIC_WORLD_PLACEMENTS;
                else if(choice==2)result=root328AlignmentSummary();
                else devPanel.setPage(DevControlCenter.Page.AUTHORITY);break;
            case RESET_CONFIRM:
                if(choice==0){result=resetAllDevOverridesFromPanel(w);devPanel.setPage(DevControlCenter.Page.DIAG);}
                else if(choice==1||choice==3)devPanel.setPage(DevControlCenter.Page.DIAG);
                else {w.fixed(219,new byte[0]);devPanel.close();clearDialogNumberKeys();System.out.println(tag+"V5171_DEV_PANEL_CLOSE reason=RESET_PAGE_CLOSE");return;}
                break;
            default: devPanel.setPage(DevControlCenter.Page.MAIN);break;
        }
        if(result!=null&&!result.isEmpty())System.out.println(tag+"V5171_DEV_PANEL page="+devPanel.page()+" choice="+(choice+1)+" result={"+result+"}");
        renderDevPanel(w);
    }

    private void promptDevPanelAmount(DevControlCenter.PendingAmount pending,ServerPacketWriter w)throws IOException{
        w.fixed(219,new byte[0]);clearDialogNumberKeys();devPanel.prompt(pending);w.fixed(27,new byte[0]);
    }

    private void handleDevPanelAmount(int value,ServerPacketWriter w,String tag)throws IOException{
        DevControlCenter.PendingAmount p=devPanel.pending();boolean reopen=true;String result;
        try{
            switch(p){
                case COMBAT_ANIM:
                    if(value<0||value>65535)result="REJECTED animation 0..65535";else{dev.setCombatAnimationOverride(equipment.weapon(),value);result="weapon="+equipment.weapon()+" animationOverride="+value;}break;
                case HIT_DAMAGE:
                    if(value<0||value>255)result="REJECTED damage 0..255";else result=combat.devHitCommand(new String[]{"devhit","damage",String.valueOf(value)});break;
                case HIT_TYPE:
                    if(value<0||value>255)result="REJECTED type 0..255";else result=combat.devHitCommand(new String[]{"devhit","type",String.valueOf(value)});break;
                case PET_FX:
                    if(value<0||value>255)result="REJECTED selector 0..255";else result=npcs.devSetParticleSelector(value,movement,w);break;
                case PET_ANIM:
                    if(value<0||value>65535)result="REJECTED animation 0..65535";else result=npcs.animatePet(value,0,w);break;
                case PET_GFX:
                    if(value<0||value>65535)result="REJECTED gfx 0..65535";else result=npcs.gfxPet(value,0,0,w);break;
                case PET_NATIVE_STATE:
                    if(value<0||value>3)result="REJECTED state 0..3";else result=npcs.setPetNativeState(value,w);break;
                case REGION_ID:
                    result=enterTransientRegionDev(value,0,w,tag);reopen=false;break;
                case ITEM_LIBRARY_ID:
                    if(ItemAuthorityRepository.get(value)==null)result="REJECTED unknown item "+value;
                    else{devPanel.close();clearDialogNumberKeys();result=itemLibrary.open(w,value);reopen=false;}break;
                case PLAYER_MORPH:
                    if(value<0||value>16383)result="REJECTED npc 0..16383";else result=playerPresentation.morph(value,username,equipment,playerState,w);break;
                case PLAYER_ANIM:
                    if(value<0||value>65535)result="REJECTED animation 0..65535";else{w.varShort(81,CombatSync.player81AnimationOnly(value));result="player anim="+value;}break;
                case PLAYER_GFX:
                    if(value<0||value>65535)result="REJECTED gfx 0..65535";else{w.varShort(81,CombatSync.player81GfxOnly(value,0,0));result="player gfx="+value;}break;
                case NPC_SPAWN:
                    if(value<0||value>16383)result="REJECTED npc 0..16383";else result=npcs.devSpawnNpc(value,1,0,movement,w);break;
                case PRAYER_WIDGET:{
                    PrayerDefinitionRepository.Def d=PrayerDefinitionRepository.byWidget(value);
                    if(d==null)result="REJECTED unknown prayer widget "+value;
                    else if(d.book!=prayers.book())result="REJECTED prayer belongs to "+d.book+" current="+prayers.book()+" name="+d.name;
                    else result=prayers.click(d,playerState,w);
                    break;}
                case PRAYER_ICON:
                    result=prayers.publishManualHeadIcon(value,w);break;
                case AUTH_SPELL_WIDGET:{
                    SpellDefinitionRepository.Spell d=SpellDefinitionRepository.byWidget(value);
                    if(d==null)result="REJECTED unknown spell widget "+value;else{devPanel.selectSpellWidget(value);result=spellAuthoritySummary(value);}
                    break;}
                case AUTH_PRAYER_WIDGET:{
                    PrayerDefinitionRepository.Def d=PrayerDefinitionRepository.byWidget(value);
                    if(d==null)result="REJECTED unknown prayer widget "+value;else{devPanel.selectPrayerWidget(value);result=prayerAuthoritySummary(value);}
                    break;}
                case AUTH_ITEM_ID:
                    if(ItemAuthorityRepository.get(value)==null)result="REJECTED unknown item "+value;else{devPanel.selectItemId(value);result=itemAuthorityBrowserSummary(value);}break;
                case AUTH_REGION_ID:
                    if(WorldRegionAuthorityRepository.get(value)==null)result="REJECTED unknown region "+value;else{devPanel.selectRegionId(value);result=regionAuthoritySummary(value);}break;
                case RUNTIME_WEAPON_ITEM:{
                    V913WeaponRuntimeAuthority.Profile rp=V913WeaponRuntimeAuthority.resolve(value);
                    if(rp==null)result="REJECTED item has no V9.13 runtime weapon profile: "+value;
                    else{devPanel.selectRuntimeWeaponItemId(value);result=runtimeWeaponAuthoritySummary(value);}
                    break;}
                case RESEARCH_EQUIP_ITEM:
                    if(ItemAuthorityRepository.get(value)==null)result="REJECTED unknown item "+value;else{devPanel.selectResearchEquipItemId(value);result=EquipmentResearchAuthority.itemSummary(value);}break;
                case RESEARCH_PET_ROW:{
                    PetProcResearchAuthority.Row row=PetProcResearchAuthority.byIndex(value);
                    if(row==null)result="REJECTED pet research row must be 1.."+PetProcResearchAuthority.count();else{devPanel.selectResearchPetRow(value);result=PetProcResearchAuthority.summary(row);}
                    break;}
                case RESEARCH_TELE_ITEM:{
                    WorldTransitionResearchAuthority.TeleportItem t=WorldTransitionResearchAuthority.teleport(value);
                    if(t==null)result="REJECTED item has no static teleport-candidate row: "+value;else{devPanel.selectResearchTeleportItemId(value);result=WorldTransitionResearchAuthority.teleportSummary(value);}
                    break;}
                case RESEARCH_TRANSITION_REGION:
                    if(WorldRegionAuthorityRepository.get(value)==null)result="REJECTED unknown region "+value;else{devPanel.selectResearchTransitionRegion(value);result=WorldTransitionResearchAuthority.regionSummary(value);}break;
                default: result="IGNORED no pending developer amount";break;
            }
        }catch(IllegalArgumentException ex){result="REJECTED "+ex.getMessage();}
        System.out.println(tag+"V5171_DEV_PANEL_AMOUNT kind="+p+" value="+value+" result={"+result+"}");
        if(reopen){devPanel.finishPrompt();renderDevPanel(w);}else devPanel.cancelPending();
    }

    private PetProcResearchAuthority.Row selectedPetProcResearchRow(){
        int idx=devPanel.selectedResearchPetRow();
        if(idx>0)return PetProcResearchAuthority.byIndex(idx);
        NpcEntity pet=npcs.pet();
        return pet==null?null:PetProcResearchAuthority.findForPetItem(pet.petItemId);
    }

    private String previewSelectedPetResearchCandidate(ServerPacketWriter w)throws IOException{
        PetProcResearchAuthority.Row row=selectedPetProcResearchRow();
        if(row==null)return "REJECTED no pet research row selected / active pet not mapped";
        NpcEntity pet=npcs.pet();if(pet==null)return "REJECTED no active main pet";
        if(!row.previewable())return "REJECTED row has no previewable animation/GFX; "+row.confidence+" binding="+row.bindingStatus+" runtimeNeeded="+row.minimalRuntimeNeeded;
        int anim=row.firstAnimation(),gfx=row.firstGfx();String a="NONE",g="NONE";
        if(anim>=0)a=npcs.animatePet(anim,0,w);
        if(gfx>=0)g=npcs.gfxPet(gfx,0,0,w);
        return "OK presentationOnly=true row="+row.index+" family="+row.family+" confidence="+row.confidence+" binding="+row.bindingStatus+" anim="+anim+" gfx="+gfx+" animResult={"+a+"} gfxResult={"+g+"} mechanics=NONE projectile=NONE impact=NONE; candidate preview does not promote production binding";
    }

    private V913WeaponRuntimeAuthority.Profile selectedRuntimeWeaponProfile(){
        int id=devPanel.selectedRuntimeWeaponItemId();
        if(id<0)id=equipment.weapon();
        return V913WeaponRuntimeAuthority.resolve(id);
    }

    private String runtimeWeaponAuthoritySummary(int itemId){
        V913WeaponRuntimeAuthority.Profile p=V913WeaponRuntimeAuthority.resolve(itemId);
        return RuntimeWeaponPresentationLab.summary(p)+" previewSafe="+RuntimeWeaponPresentationLab.previewSafe(p)+
            " projectilePolicy="+RuntimeWeaponPresentationLab.PROJECTILE_POLICY+
            " preAnimationPolicy="+RuntimeWeaponPresentationLab.PRE_ANIMATION_POLICY;
    }

    private String selectedMagicAuthoritySummary(){
        String a=devPanel.selectedSpellWidget()<0?"spell:none":clip(spellAuthoritySummary(devPanel.selectedSpellWidget()),30);
        String b=devPanel.selectedPrayerWidget()<0?"prayer:none":clip(prayerAuthoritySummary(devPanel.selectedPrayerWidget()),30);
        return a+" | "+b;
    }

    private String selectedWorldItemAuthoritySummary(){
        String a=devPanel.selectedItemId()<0?"item:none":clip(itemAuthorityBrowserSummary(devPanel.selectedItemId()),28);
        int rid=devPanel.selectedRegionId();
        if(rid<0)rid=((movement.x()>>6)<<8)|(movement.y()>>6);
        return a+" | "+clip(regionAuthoritySummary(rid),28);
    }

    private String spellAuthoritySummary(int widget){
        SpellDefinitionRepository.Spell d=SpellDefinitionRepository.byWidget(widget);
        if(d==null)return "spell widget="+widget+" UNKNOWN";
        return "spell widget="+widget+" "+d.name+" book="+d.book+" lvl="+d.level+" target="+d.targeted+" resources="+(d.resources==null?0:d.resources.length);
    }

    private String prayerAuthoritySummary(int widget){
        PrayerDefinitionRepository.Def d=PrayerDefinitionRepository.byWidget(widget);
        if(d==null)return "prayer widget="+widget+" UNKNOWN";
        return "prayer widget="+widget+" "+d.name+" book="+d.book+" lvl="+d.level+" varp="+d.varp;
    }

    private String itemAuthorityBrowserSummary(int itemId){
        ItemAuthorityRepository.Entry e=ItemAuthorityRepository.get(itemId);
        if(e==null)return "item="+itemId+" UNKNOWN";
        return ContentAuthorityRepository.itemSummary(itemId)+" equip="+e.equippable()+" effectText="+(e.effectText!=null&&!e.effectText.trim().isEmpty());
    }

    private String regionAuthoritySummary(int regionId){
        WorldRegionAuthorityRepository.Region r=WorldRegionAuthorityRepository.get(regionId);
        if(r==null)return "region="+regionId+" UNKNOWN";
        return "region="+regionId+" "+(r.name==null||r.name.isEmpty()?"unnamed":r.name)+" bounds="+r.x0+","+r.y0+".."+r.x1+","+r.y1+" decoded="+(r.mapPresent&&r.landPresent&&r.terrainParseOk&&r.objectParseOk)+" placements="+r.placements+" usage="+r.usageStatus;
    }

    private String root328AlignmentSummary(){
        CombatStyleRepository.Style a=CombatStyleRepository.byValue(328,0),b=CombatStyleRepository.byValue(328,1),c=CombatStyleRepository.byValue(328,2);
        return "root328="+(ClientAssetAlignmentAuthority.root328Aligned()?"PASS":"FAIL")+" styles="+(a==null?"?":a.label)+"/"+(b==null?"?":b.label)+"/"+(c==null?"?":c.label)+" totalRoots="+CombatStyleRepository.rootCount()+" totalStyles="+CombatStyleRepository.countStyles();
    }

    private String cycleCombatStyleFromPanel(ServerPacketWriter w)throws IOException{
        int root=CombatInterfaceRepository.forWeapon(equipment.weapon()), cur=combatStyles.value();
        for(int i=1;i<=4;i++){int v=(cur+i)&3;CombatStyleRepository.Style s=CombatStyleRepository.byValue(root,v);if(s!=null)return combatStyles.click(root,s.widget,w);}
        return "NO_ALTERNATE_STYLE root="+root;
    }

    private String playCurrentAttackAnimation(ServerPacketWriter w)throws IOException{
        int weapon=equipment.weapon();Integer anim=null;String authority="";
        if(dev.hasCombatAnimationOverride(weapon)){Integer x=dev.combatAnimationOverride(weapon);if(x!=null&&x>=0){anim=x;authority="TEMPORARY_OVERRIDE";}}
        if(anim==null){V913WeaponRuntimeAuthority.Profile p=V913WeaponRuntimeAuthority.resolve(weapon);if(p!=null&&p.attackAnimation>=0){anim=p.attackAnimation;authority="PRODUCTION_RUNTIME_V913";}}
        if(anim==null){WeaponAttackAuthorityRepository.Row r=WeaponAttackAuthorityRepository.resolve(weapon);if(r!=null&&r.attackAnimation>=0){anim=r.attackAnimation;authority=r.attackAuthority;}}
        if(anim==null)return "NO_RESOLVED_ATTACK_ANIMATION weapon="+weapon;
        w.varShort(81,CombatSync.player81AnimationOnly(anim));return "PLAY_ATTACK_ANIMATION weapon="+weapon+" anim="+anim+" authority="+authority;
    }

    private String resetAllDevOverridesFromPanel(ServerPacketWriter w)throws IOException{
        boolean hadMorph=dev.playerNpcTransformId()!=null;String npcr=npcs.devRemoveAllNpcs(w);String worldr=resetDevWorld(w);dev.resetAll();LocalDevVisualOverrideStore.clear();
        if(hadMorph)playerPresentation.refresh(username,equipment,playerState,w);
        String petReset="none";if(npcs.pet()!=null&&petState.active())petReset=npcs.previewPetDefinition(petState.npcId(),movement,w);
        String inv=bank.restoreDevInventoryPreview(w);
        return "workbench="+dev.summary()+" inventory="+inv+" pet="+petReset+" devNpcs="+npcr+" devWorld="+worldr+" playerRefresh="+hadMorph+" persisted=false";
    }

    private String devItemName(int itemId){ItemAuthorityRepository.Entry e=ItemAuthorityRepository.get(itemId);return e==null?"unknown":clip(ItemAuthorityRepository.stripTags(e.name),30);}
    private static String clip(String s,int n){if(s==null)return "";String x=s.replace('\n',' ').replace('\r',' ').replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,Math.max(0,n-3))+"...";}

    private void openMiniConfigureDialog(int slot,int itemId,ServerPacketWriter w)throws IOException{
        pendingMiniConfigureSlot=slot;pendingMiniConfigureItem=itemId;
        w.varShort(126,BootstrapPackets.widgetText126(2481,"Configure mini-pet"));
        w.varShort(126,BootstrapPackets.widgetText126(2482,"Activate this mini-pet"));
        w.varShort(126,BootstrapPackets.widgetText126(2483,"Disable current mini-pet"));
        w.varShort(126,BootstrapPackets.widgetText126(2484,"Cancel"));
        w.varShort(126,BootstrapPackets.widgetText126(2485,"Close"));
        w.fixed(164,BootstrapPackets.chatboxInterface164(2480));
        publishDialogNumberKeys(2482,2483,2484,2485);
    }
    private void clearMiniConfigureDialog(){pendingMiniConfigureSlot=-1;pendingMiniConfigureItem=-1;clearDialogNumberKeys();}

    private void openPetAccessoryDialog(int slot,int itemId,ServerPacketWriter w)throws IOException{
        pendingPetAccessorySlot=slot; pendingPetAccessoryItem=itemId;
        w.varShort(126,BootstrapPackets.widgetText126(2481,"Pet accessory"));
        w.varShort(126,BootstrapPackets.widgetText126(2482,"Activate "+petAccessoryName(itemId)));
        w.varShort(126,BootstrapPackets.widgetText126(2483,"Remove active pet accessory"));
        w.varShort(126,BootstrapPackets.widgetText126(2484,"Cancel"));
        w.varShort(126,BootstrapPackets.widgetText126(2485,"Close"));
        w.fixed(164,BootstrapPackets.chatboxInterface164(2480));
        publishDialogNumberKeys(2482,2483,2484,2485);
    }
    private void clearPetAccessoryDialog(){pendingPetAccessorySlot=-1;pendingPetAccessoryItem=-1;clearDialogNumberKeys();}

    private void openPetColorDialog(int slot,int[] family,String name,ServerPacketWriter w)throws IOException{
        pendingPetColorSlot=slot; pendingPetColorItems=family.clone(); pendingPetColorFamily=name;
        w.varShort(126,BootstrapPackets.widgetText126(2481,"Select a color"));
        if("SCOOBY_BEHEMOTH".equals(name) && family.length==4){
            w.varShort(126,BootstrapPackets.widgetText126(2482,"Black / white"));
            w.varShort(126,BootstrapPackets.widgetText126(2483,"Black / orange"));
            w.varShort(126,BootstrapPackets.widgetText126(2484,"White / blue"));
            w.varShort(126,BootstrapPackets.widgetText126(2485,"Green / black"));
        } else {
            w.varShort(126,BootstrapPackets.widgetText126(2482,"Color 1"));
            w.varShort(126,BootstrapPackets.widgetText126(2483,"Color 2"));
            w.varShort(126,BootstrapPackets.widgetText126(2484,"Color 3"));
            w.varShort(126,BootstrapPackets.widgetText126(2485,"Cancel"));
        }
        w.fixed(164,BootstrapPackets.chatboxInterface164(2480));
        publishDialogNumberKeys(2482,2483,2484,2485);
    }
    private void clearPetColorDialog(){pendingPetColorSlot=-1;pendingPetColorItems=null;pendingPetColorFamily=null;clearDialogNumberKeys();}
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

    private static boolean contains(int[] xs,int v){for(int x:xs)if(x==v)return true;return false;}
    private static int[] petColorFamily(int itemId){
        if(itemId>=27340&&itemId<=27342)return new int[]{27340,27341,27342};
        if(itemId>=27343&&itemId<=27345)return new int[]{27343,27344,27345};
        if(itemId>=24016&&itemId<=24019)return new int[]{24016,24017,24018,24019};
        return null;
    }
    private static String petColorFamilyName(int itemId){
        if(itemId>=27340&&itemId<=27342)return "RESVANO_EVIL_WOLPER";
        if(itemId>=27343&&itemId<=27345)return "RESVANO_ETHEREAL";
        if(itemId>=24016&&itemId<=24019)return "SCOOBY_BEHEMOTH";
        return "UNKNOWN";
    }

    private static boolean petColorCurrentAllowed(String family,int current,int[] choices){
        if("SCOOBY_BEHEMOTH".equals(family))return current>=24016&&current<=24019;
        return contains(choices,current);
    }
    private static boolean isPetAccessoryItem(int itemId){
        return (itemId>=20542&&itemId<=20546)||itemId==20699||itemId==21068;
    }
    private static String petAccessoryName(int itemId){
        switch(itemId){
            case 20542:return "White pet accessory";
            case 20543:return "Red pet accessory";
            case 20544:return "Green pet accessory";
            case 20545:return "Blue pet accessory";
            case 20546:return "Gold pet accessory";
            case 20699:return "Enchanted pet accessory";
            case 21068:return "Easter pet accessory";
            default:return "Unknown pet accessory";
        }
    }
    private static Integer petAccessorySelector(int itemId){
        switch(itemId){
            // R2.10 runtime authority: these values are the selectors that the
            // current LocalLab client actually rendered as the labelled accessory
            // colors during the user's live A/B pass.
            case 20542:return 1; // White item -> live white presentation
            case 20543:return 2; // Red item -> live red presentation
            case 20544:return 3; // Green item -> live light-green presentation
            case 20545:return 4; // Blue item -> live light-blue presentation
            case 20546:return 5; // Gold item -> live yellow/gold presentation
            case 20699:return 7; // strong behavior/name mapping: cycles 1..5
            case 21068:return 8; // strong behavior/name mapping: cyan/pink alternation
            default:return null;
        }
    }
    private static String petAccessorySelectorAuthority(int itemId){
        return itemId>=20542&&itemId<=20546?"USER_RUNTIME_CERTIFIED_LABEL_TO_SELECTOR_R2_10":
               itemId==20699?"STRONG_ENCHANTED_CYCLE_BEHAVIOR_NAME_MAPPING":
               itemId==21068?"STRONG_EASTER_CYAN_MAGENTA_BEHAVIOR_NAME_MAPPING":"UNKNOWN";
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

    private static boolean isSpecialCompCape(int itemId){
        return itemId==23063 || itemId==21963 || itemId==21964;
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
        if(pendingMiniConfigureItem>=0 || pendingPetColorItems!=null || pendingPetAccessoryItem>=0 || devPanel.isOpen()){
            serverPackets.fixed(219,new byte[0]);
            boolean mini=pendingMiniConfigureItem>=0, color=pendingPetColorItems!=null, accessory=pendingPetAccessoryItem>=0, panel=devPanel.isOpen();
            clearMiniConfigureDialog(); clearPetColorDialog(); clearPetAccessoryDialog(); devPanel.close(); clearDialogNumberKeys();
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
        if(activePlayerFollow!=null||activePlayerAttack!=null||activePlayerTrade!=null){
            boolean hadFacingInteraction=activePlayerFollow!=null||activePlayerAttack!=null;
            boolean hadTrade=activePlayerTrade!=null;
            activePlayerFollow=null;activePlayerAttack=null;activePlayerTrade=null;nextPlayerAttackTick=0;
            if(hadFacingInteraction)serverPackets.varShort(81,CombatSync.player81InteractionOnly(-1));
            System.out.println(tag+"V5141_PLAYER_INTERACTION_CANCEL reason=MANUAL_MOVEMENT clientInteractionTarget="+(hadFacingInteraction?"CLEAR":"UNCHANGED")+" pendingTrade="+hadTrade);
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
