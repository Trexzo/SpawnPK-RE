package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

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
                    itemLibrary
                );
            LocalPrayerMagicCommandHandler prayerMagicCommands=
                new LocalPrayerMagicCommandHandler(
                    prayers
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
                    voidglassCommands,
                    petRuntimeCommands,
                    compColorsCommands,
                    combatCommands,
                    petCompatibilityCommands,
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

            final int[] rootReplacements={0};

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
                        @Override public String replaceMonsterSpawnerRoot(
                            LocalSessionUiActionHandler.RootInterfaceAction action
                        )throws IOException{
                            rootReplacements[0]++;
                            return action.publish();
                        }
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
                        @Override public void refreshPlayerAppearance(
                            ServerPacketWriter writer
                        )throws IOException{}
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
                (53+typedEncoder.nextInt())&255
            );
            // Item-on-item: unsupported widgets/items all zero.
            // Exact opcode-53 transforms still travel through the typed FIFO;
            // the existing semantic handler must consume and fail closed.
            typedWire.write(
                new byte[]{
                    0,0,
                    0,(byte)128,
                    (byte)128,0,
                    0,0,
                    0,0,
                    0,0
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

            typedWire.write(
                (236+typedEncoder.nextInt())&255
            );
            // Ground option 3: worldY LE, item BE, worldX LE.
            // Missing item id 0 at tile 0,0 must be consumed and fail closed.
            typedWire.write(
                new byte[]{0,0,0,0,0,0}
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
                    "typed item-on-item fixture decode failed"
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
                    "typed ground item fixture decode failed"
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

            MonsterSpawnerService rootSpawner=
                new MonsterSpawnerService(
                    world.npcs()
                );
            rootSpawner.replaceCatalog(
                java.util.Collections.singletonList(
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "pending-comp-root",
                        1530
                    )
                ),
                "CUSTOM_LOCALLAB_PENDING_COMP_ROOT_CATALOG"
            );
            rootSpawner.openSession(
                "opensrc",
                "CUSTOM_LOCALLAB_PENDING_COMP_ROOT_POLICY"
            );

            LocalMonsterSpawnerUiHandler rootUi=
                new LocalMonsterSpawnerUiHandler(
                    rootSpawner,
                    "opensrc",
                    new LocalMonsterSpawnerUiHandler.ActivationBudgetResolver(){
                        @Override public int spawnBudget(
                            LocalMonsterSpawnerUiHandler.Context context
                        ){
                            return 1;
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB_PENDING_COMP_ROOT_POLICY";
                        }
                    },
                    new LocalMonsterSpawnerUiHandler.SelectedNpcLabelResolver(){
                        @Override public String label(
                            MonsterSpawnerService.CatalogEntry entry
                        ){
                            return "NPC-"+entry.definitionId;
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB_PENDING_COMP_ROOT_CATALOG";
                        }
                    }
                );

            uiActions.installMonsterSpawnerUiHandler(
                rootUi
            );
            uiActions.openMonsterSpawnerIfConfigured(
                writer
            );

            bank.spawnItem(
                23063,
                1,
                writer
            );

            int compSlot=-1;
            for(int i=0;
                i<bank.inventoryCapacity();
                i++){
                BankState.Stack stack=
                    bank.inventoryAt(
                        i
                    );
                if(stack!=null&&
                   stack.itemId==23063&&
                   stack.qty>0){
                    compSlot=i;
                    break;
                }
            }

            if(compSlot<0)
                throw new AssertionError(
                    "comp cape root integration fixture missing item"
                );

            Method routeItemAction=
                LocalPendingRequestDispatcher.class
                    .getDeclaredMethod(
                        "routeItemAction",
                        ItemContainerAction.class,
                        ServerPacketWriter.class,
                        String.class
                    );
            routeItemAction.setAccessible(
                true
            );

            int rootBeforeInvalid=
                rootReplacements[0];

            routeItemAction.invoke(
                dispatcher,
                new ItemContainerAction(
                    75,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    compSlot,
                    21963,
                    0,
                    "INVENTORY_OPTION_3"
                ),
                writer,
                "[pending-test] "
            );

            if(rootReplacements[0]!=
                    rootBeforeInvalid)
                throw new AssertionError(
                    "invalid comp cape action revoked Monster Spawner"
                );

            routeItemAction.invoke(
                dispatcher,
                new ItemContainerAction(
                    75,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    compSlot,
                    23063,
                    0,
                    "INVENTORY_OPTION_3"
                ),
                writer,
                "[pending-test] "
            );

            if(rootReplacements[0]!=
                    rootBeforeInvalid+1||
               !compCape.isOpen())
                throw new AssertionError(
                    "valid comp cape root bypassed Monster ownership"
                );

            int postCompRootWire=
                wire.size();

            uiActions.handleWidget(
                MonsterSpawnerPresentation.TOGGLE_WIDGET,
                writer,
                "[pending-test] "
            );

            if(wire.size()!=
                    postCompRootWire)
                throw new AssertionError(
                    "comp cape root left Monster Spawner gate open"
                );

            System.out.println(
                "LOCAL_PENDING_REQUEST_DISPATCHER_PASS "+
                "widgetConsumed=true dropConsumed=true "+
                "itemOnNpcConsumed=true itemOnItemConsumed=true "+
                "objectInteractionConsumed=true "+
                "playerActionConsumed=true npcActionConsumed=true "+
                "spellTargetConsumed=true genericInteractionConsumed=true "+
                "itemActionConsumed=true groundItemConsumed=true "+
                "movementConsumed=true runToggleBeforeMovement=true "+
                "classifierCompatibility=true "+
                "compCapeRootRevokesMonsterSpawner=true "+
                "invalidCompCapePreservesMonsterSpawner=true"
            );
        }finally{
            world.close();
        }
    }
}
