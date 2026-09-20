package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class LocalPendingRequestDispatcherTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);

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
            CombatStyleState combatStyles=player.combatStyles();
            PetEffectState petEffects=player.petEffects();
            MiniPetService miniPets=player.miniPets();

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
            PetAccessoryState accessory=
                new PetAccessoryState();
            VoidglassPetState voidglass=
                new VoidglassPetState();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(new int[]{1,2,3,4})
                );
            SceneUpdatePublisher publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalDiagnosticCommandHandler diagnosticCommands=
                new LocalDiagnosticCommandHandler(
                    world,
                    equipment,
                    movement,
                    prayers,
                    magic,
                    combatStyles,
                    itemLibrary
                );
            LocalPrayerMagicCommandHandler prayerMagicCommands=
                new LocalPrayerMagicCommandHandler(
                    prayers,
                    magic
                );
            LocalDevWorldCommandHandler devWorldCommands=
                new LocalDevWorldCommandHandler(
                    world,
                    movement
                );
            LocalMiniPetCommandHandler miniPetCommands=
                new LocalMiniPetCommandHandler(
                    miniPets,
                    petState,
                    npcs,
                    movement
                );
            LocalCosmeticCommandHandler cosmeticCommands=
                new LocalCosmeticCommandHandler(
                    bank,
                    equipment,
                    playerState,
                    presentation
                );
            LocalCompColorsCommandHandler compColorsCommands=
                new LocalCompColorsCommandHandler(
                    playerState,
                    equipment,
                    presentation
                );
            LocalItemSpawnCommandHandler itemSpawnCommands=
                new LocalItemSpawnCommandHandler(bank);
            LocalNurseCommandHandler nurseCommands=
                new LocalNurseCommandHandler(
                    playerState,
                    movement
                );
            LocalBankRequestHandler bankRequests=
                new LocalBankRequestHandler(
                    player,
                    bank
                );
            LocalItemOnItemHandler itemOnItemHandler=
                new LocalItemOnItemHandler(bank);
            LocalSpellTargetHandler spellTargetHandler=
                new LocalSpellTargetHandler(
                    magic,
                    bank,
                    equipment,
                    playerState,
                    npcs,
                    combat
                );
            LocalGroundItemInteractionHandler groundItemHandler=
                new LocalGroundItemInteractionHandler(
                    world,
                    bank,
                    movement
                );
            LocalItemOnNpcHandler itemOnNpcHandler=
                new LocalItemOnNpcHandler(
                    bank,
                    npcs,
                    movement,
                    accessory
                );
            LocalGameplayWidgetHandler gameplayWidgets=
                new LocalGameplayWidgetHandler(
                    prayers,
                    playerState,
                    equipment,
                    combatStyles,
                    magic,
                    bank
                );
            LocalBankObjectInteractionHandler bankObjects=
                new LocalBankObjectInteractionHandler(
                    bank,
                    movement
                );
            LocalRoutedNpcInteractionHandler routedNpcs=
                new LocalRoutedNpcInteractionHandler(
                    npcs,
                    bank,
                    movement
                );
            LocalGenericInteractionHandler genericInteractions=
                new LocalGenericInteractionHandler();
            LocalPlayerInteractionHandler playerInteractions=
                new LocalPlayerInteractionHandler(
                    world,
                    player,
                    movement,
                    equipment
                );
            LocalEquipmentItemActionHandler equipmentActions=
                new LocalEquipmentItemActionHandler(
                    bank,
                    equipment,
                    playerState,
                    presentation,
                    combatStyles
                );
            LocalPetInventoryDialogHandler petDialogs=
                new LocalPetInventoryDialogHandler(
                    bank,
                    miniPets,
                    petState,
                    npcs,
                    movement,
                    accessory
                );
            LocalCompCapeCustomizeHandler compCape=
                new LocalCompCapeCustomizeHandler(
                    bank,
                    playerState
                );
            LocalDevPetCommandHandler devPetCommands=
                new LocalDevPetCommandHandler(
                    dev,
                    npcs,
                    movement,
                    bank
                );
            LocalDevPlayerCommandHandler devPlayerCommands=
                new LocalDevPlayerCommandHandler(
                    presentation,
                    equipment,
                    playerState
                );
            LocalDevNpcCommandHandler devNpcCommands=
                new LocalDevNpcCommandHandler(
                    npcs,
                    movement
                );
            LocalDevToolCommandHandler devToolCommands=
                new LocalDevToolCommandHandler(
                    dev,
                    bank,
                    equipment
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
            LocalPetRuntimeCommandHandler petRuntimeCommands=
                new LocalPetRuntimeCommandHandler(
                    petState,
                    petEffects,
                    npcs,
                    movement
                );
            LocalCombatCommandHandler combatCommands=
                new LocalCombatCommandHandler(
                    combat,
                    equipment,
                    combatStyles,
                    npcs,
                    petRuntimeCommands
                );
            LocalRegionDevCommandHandler regionDevCommands=
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
            LocalDevSessionCommandHandler devSessionCommands=
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
            LocalPetCompatibilityCommandHandler petCompatibilityCommands=
                new LocalPetCompatibilityCommandHandler(
                    accessory,
                    npcs,
                    movement,
                    petDialogs
                );

            LocalCommandDispatcher commandDispatcher=
                new LocalCommandDispatcher(
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
                    world.content(),
                    player,
                    new LocalCommandDispatcher.SessionBridge(){
                        @Override public SceneUpdatePublisher scenePublisher(){
                            return publisher;
                        }
                        @Override public void replaceScenePublisher(
                            SceneUpdatePublisher replacement
                        ){}
                        @Override public void saveAccount(
                            String tag,
                            String reason
                        ){}
                        @Override public boolean scopesightActive(){
                            return false;
                        }
                        @Override public void openDevPanel(
                            ServerPacketWriter serverPackets
                        ){}
                        @Override public void applyPetDialog(
                            LocalPetInventoryDialogHandler.Result result,
                            String tag
                        ){}
                    }
                );

            DevControlCenter devPanel=
                new DevControlCenter();

            LocalSessionUiActionHandler uiActions=
                new LocalSessionUiActionHandler(
                    player,
                    itemLibrary,
                    devPanel,
                    bank,
                    compCape,
                    petDialogs,
                    gameplayWidgets,
                    movement,
                    true,
                    equipment,
                    new LocalSessionUiActionHandler.SessionBridge(){
                        @Override public void saveAccount(
                            String tag,
                            String reason
                        ){}
                        @Override public void clearDialogNumberKeys(){}
                        @Override public void handleDevPanelWidget(
                            int widget,
                            ServerPacketWriter serverPackets,
                            String tag
                        ){}
                        @Override public void applyPetDialog(
                            LocalPetInventoryDialogHandler.Result result,
                            String tag
                        ){}
                        @Override public void requestLogout(){}
                    }
                );

            LocalPetDropPickupHandler petDropPickup=
                new LocalPetDropPickupHandler(
                    world,
                    bank,
                    movement,
                    petState,
                    petEffects,
                    miniPets,
                    npcs,
                    voidglass,
                    accessory,
                    dev,
                    new LocalPetDropPickupHandler.SessionBridge(){
                        @Override public String username(){
                            return "opensrc";
                        }
                        @Override public boolean persistentAccount(){
                            return true;
                        }
                        @Override public long sessionWorldTick(){
                            return 1L;
                        }
                        @Override public SceneUpdatePublisher scenePublisher(){
                            return publisher;
                        }
                        @Override public void saveAccount(
                            String tag,
                            String reason
                        ){}
                        @Override public int syncScopesightPassive(
                            ServerPacketWriter serverPackets
                        ){
                            return 0;
                        }
                        @Override public void resetPetFollowDeadline(){}
                        @Override public void ensurePetFollowScheduled(long now){}
                    }
                );

            LocalMovementRequestHandler movementRequests=
                new LocalMovementRequestHandler(
                    true,
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
                        @Override public void clearDialogNumberKeys(){}
                        @Override public void clearOpponentOverlay(
                            ServerPacketWriter serverPackets,
                            String tag,
                            String reason
                        ){}
                    }
                );

            LocalPetRealtimeScheduler petRealtime=
                new LocalPetRealtimeScheduler(
                    true,
                    world,
                    player,
                    movement,
                    npcs,
                    petDropPickup,
                    petRuntimeCommands,
                    new LocalPetRealtimeScheduler.SessionBridge(){
                        @Override public ServerPacketWriter sessionPackets(){
                            return writer;
                        }
                        @Override public String sessionTag(){
                            return "[pending-test] ";
                        }
                    }
                );

            LocalPendingRequestDispatcher dispatcher=
                new LocalPendingRequestDispatcher(
                    player,
                    bank,
                    equipment,
                    combatStyles,
                    movement,
                    npcs,
                    combat,
                    devPanel,
                    uiActions,
                    commandDispatcher,
                    bankObjects,
                    genericInteractions,
                    equipmentActions,
                    petDialogs,
                    compCape,
                    itemOnItemHandler,
                    itemOnNpcHandler,
                    spellTargetHandler,
                    petDropPickup,
                    groundItemHandler,
                    playerInteractions,
                    routedNpcs,
                    bankRequests,
                    movementRequests,
                    petRealtime,
                    new LocalPendingRequestDispatcher.SessionBridge(){
                        @Override public String username(){
                            return "opensrc";
                        }
                        @Override public String loginAlias(){
                            return "localtest";
                        }
                        @Override public boolean persistentAccount(){
                            return true;
                        }
                        @Override public long sessionWorldTick(){
                            return 1L;
                        }
                        @Override public SceneUpdatePublisher scenePublisher(){
                            return publisher;
                        }
                        @Override public Player81WorldSync.Context player81Sync(){
                            return null;
                        }
                        @Override public void saveAccount(
                            String tag,
                            String reason
                        ){}
                        @Override public void applyPetDialogResult(
                            LocalPetInventoryDialogHandler.Result result,
                            String tag
                        ){}
                        @Override public void clearOpponentOverlay(
                            ServerPacketWriter serverPackets,
                            String tag,
                            String reason
                        ){}
                        @Override public void handleDevPanelAmount(
                            int value,
                            ServerPacketWriter serverPackets,
                            String tag
                        ){}
                    }
                );

            int[] probeSeed={5,6,7,8};
            ByteArrayOutputStream typedWire=
                new ByteArrayOutputStream();
            IsaacCipher typedEncoder=
                new IsaacCipher(
                    probeSeed.clone()
                );
            typedWire.write(
                (185+typedEncoder.nextInt())&255
            );
            typedWire.write(0);
            typedWire.write(152);
            typedWire.write(
                (87+typedEncoder.nextInt())&255
            );
            // item=0 BE-A, unsupported widget=0 BE, slot=0 BE-A.
            typedWire.write(
                new byte[]{
                    0,(byte)128,
                    0,0,
                    0,(byte)128
                }
            );

            typedWire.write(
                (57+typedEncoder.nextInt())&255
            );
            // item=0 BE-A, targetNpc=0 BE-A,
            // slot=0 LE, widget=0 BE-A. The semantic handler
            // must consume it and fail closed on missing inventory source.
            typedWire.write(
                new byte[]{
                    0,(byte)128,
                    0,(byte)128,
                    0,0,
                    0,(byte)128
                }
            );

            typedWire.write(
                (132+typedEncoder.nextInt())&255
            );
            // worldX=0 LE-A, non-bank object=12345 BE,
            // worldY=0 BE-A. The semantic handler must consume it
            // and preserve fail-closed non-bank behavior.
            typedWire.write(
                new byte[]{
                    (byte)128,0,
                    0x30,0x39,
                    0,(byte)128
                }
            );

            typedWire.write(
                (153+typedEncoder.nextInt())&255
            );
            // Player option 2 / Follow, player index 1 LE.
            // The test bridge intentionally has no player sync context,
            // so routing must consume it and fail closed as sync-not-ready.
            typedWire.write(
                new byte[]{1,0}
            );

            typedWire.write(
                (18+typedEncoder.nextInt())&255
            );
            // NPC option 5, scene index 0 LE. No matching scene NPC is
            // required; the typed request must still be consumed and routed
            // through the existing fail-closed NPC domain path.
            typedWire.write(
                new byte[]{0,0}
            );

            typedWire.write(
                (249+typedEncoder.nextInt())&255
            );
            // Spell-on-player: target index 0 BE-A, spell widget 0 LE.
            // The handler must consume the typed request and reject the
            // unknown spell semantically rather than leaving transport state.
            typedWire.write(
                new byte[]{0,(byte)128,0,0}
            );

            typedWire.write(
                (70+typedEncoder.nextInt())&255
            );
            // Promoted object option 3: worldY LE=0x3456,
            // worldX BE=0x2345, objectId LE-A=0x1234.
            // Generic semantics remain deliberately fail-closed.
            typedWire.write(
                new byte[]{
                    0x56,0x34,
                    0x23,0x45,
                    (byte)0xB4,0x12
                }
            );

            typedWire.write(
                (41+typedEncoder.nextInt())&255
            );
            // Benign item option 2 fixture: item 0 BE,
            // slot 0 BE-A, widget 0 BE-A. Existing semantic
            // routing must consume it without transport fallback.
            typedWire.write(
                new byte[]{
                    0,0,
                    0,(byte)128,
                    0,(byte)128
                }
            );

            int movementX=MovementState.INITIAL_X+1;
            int movementY=MovementState.INITIAL_Y;
            typedWire.write(
                (164+typedEncoder.nextInt())&255
            );
            typedWire.write(5);
            typedWire.write((movementX+128)&255);
            typedWire.write((movementX>>>8)&255);
            typedWire.write(movementY&255);
            typedWire.write((movementY>>>8)&255);
            typedWire.write(0);

            ClientPacketProbe probe=
                new ClientPacketProbe(
                    new ByteArrayInputStream(
                        typedWire.toByteArray()
                    ),
                    new IsaacCipher(
                        probeSeed.clone()
                    ),
                    "[pending-test] "
                );

            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "typed widget fixture decode failed"
                );

            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "typed drop fixture decode failed"
                );

            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "typed item-on-npc fixture decode failed"
                );

            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "typed object interaction fixture decode failed"
                );

            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "typed player action fixture decode failed"
                );

            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "typed npc action fixture decode failed"
                );

            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "typed spell target fixture decode failed"
                );

            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "typed generic interaction fixture decode failed"
                );

            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "typed item-container fixture decode failed"
                );

            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "typed movement fixture decode failed"
                );

            dispatcher.drain(
                probe,
                writer,
                "[pending-test] "
            );

            if(!movement.persistentRun())
                throw new AssertionError(
                    "widget action was not routed"
                );

            if(movement.queued()!=1)
                throw new AssertionError(
                    "movement request did not reach authoritative queue"
                );

            if(movement.acceptedPaths()!=1L)
                throw new AssertionError(
                    "movement request consumed more or less than once"
                );

            if(probe.typedRequestCount()!=0)
                throw new AssertionError(
                    "typed widget request was not consumed"
                );

            NpcEntity dummy=
                new NpcEntity(
                    3,
                    1489,
                    MovementState.INITIAL_X+2,
                    MovementState.INITIAL_Y
                );

            NpcAction attack=
                new NpcAction(72,3);

            if(!LocalPendingRequestDispatcher.isCombatAttackAction(
                attack,
                dummy
            )){
                throw new AssertionError(
                    "combat attack classification changed"
                );
            }

            if(!LocalSession.isCombatAttackAction(
                attack,
                dummy
            )){
                throw new AssertionError(
                    "LocalSession combat classifier compatibility seam changed"
                );
            }

            System.out.println(
                "LOCAL_PENDING_REQUEST_DISPATCHER_PASS "+
                "widgetConsumed=true dropConsumed=true "+
                "itemOnNpcConsumed=true objectInteractionConsumed=true "+
                "playerActionConsumed=true npcActionConsumed=true "+
                "spellTargetConsumed=true genericInteractionConsumed=true "+
                "itemActionConsumed=true movementConsumed=true "+
                "runToggleBeforeMovement=true "+
                "classifierCompatibility=true"
            );
        }finally{
            world.close();
        }
    }
}
