package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalWorldTickCoordinatorTest {
    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;

        @Override public String username(){return "opensrc";}
        @Override public boolean persistentAccount(){return true;}
        @Override public long sessionWorldTick(){return 1L;}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
        @Override public void saveAccount(String tag,String reason){}
        @Override public int syncScopesightPassive(
            ServerPacketWriter serverPackets
        ){return 0;}
        @Override public void resetPetFollowDeadline(){}
        @Override public void ensurePetFollowScheduled(long now){}
    }

    private static final class RegionBridge
        implements LocalRegionStreamHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;

        @Override public String username(){return "opensrc";}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
        @Override public void replaceScenePublisher(
            SceneUpdatePublisher replacement
        ){
            publisher=replacement;
        }
        @Override public void resetPetFollowRuntime(){}
    }

    private static final class TickBridge
        implements LocalWorldTickCoordinator.SessionBridge
    {
        SceneUpdatePublisher publisher;
        String lastSaveReason;
        int saveCalls;
        long petDeadline=Long.MAX_VALUE;
        int followScheduleCalls;
        int testSequenceScheduleCalls;
        int overlayPublishCalls;
        int overlayClearCalls;

        @Override public Player81WorldSync.Context player81Sync(){
            return null;
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }

        @Override public void saveAccount(
            String tag,
            String reason
        ){
            lastSaveReason=reason;
            saveCalls++;
        }

        @Override public void publishOpponentOverlay(
            NpcEntity target,
            ServerPacketWriter writer,
            String tag,
            String reason
        ){
            overlayPublishCalls++;
        }

        @Override public void clearOpponentOverlay(
            ServerPacketWriter writer,
            String tag,
            String reason
        ){
            overlayClearCalls++;
        }

        @Override public long petFollowDeadline(){
            return petDeadline;
        }

        @Override public void setPetFollowDeadline(long value){
            petDeadline=value;
        }

        @Override public void ensurePetFollowScheduled(long now){
            followScheduleCalls++;
        }

        @Override public void ensurePetTestSequenceScheduled(long now){
            testSequenceScheduleCalls++;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final World world=World.isolatedForTest(50L);
        final WorldPlayer player=new WorldPlayer();
        final MovementState movement=player.movement();
        final BankState bank=player.bank();
        final EquipmentState equipment=player.equipment();
        final PetState petState=player.petState();
        final PetEffectState petEffects=player.petEffects();
        final CombatStyleState combatStyles=player.combatStyles();
        final DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        final NpcRegistry npcs=new NpcRegistry(dev);
        final HomeWorldRuntimePlan homeWorld=new HomeWorldRuntimePlan();
        final CombatEngine combat=new CombatEngine(dev);
        final ByteArrayOutputStream wire=new ByteArrayOutputStream();
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(new int[]{1,2,3,4})
            );
        final SceneUpdatePublisher publisher=
            new SceneUpdatePublisher(
                writer,
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0
                )
            );
        final LocalPlayerInteractionHandler playerInteractions;
        final LocalBankObjectInteractionHandler bankObjects;
        final LocalRoutedNpcInteractionHandler routedNpcs;
        final LocalGroundItemInteractionHandler groundItems;
        final LocalPetDropPickupHandler petDropPickup;
        final LocalPetRuntimeCommandHandler petRuntime;
        final RegionBridge regionBridge=new RegionBridge();

        Fixture()throws Exception{
            world.registerPlayer(player,"opensrc");
            npcs.bootstrapHome(
                writer,
                movement,
                petState,
                homeWorld
            );

            playerInteractions=
                new LocalPlayerInteractionHandler(
                    world,
                    player,
                    movement,
                    equipment
                );

            bankObjects=
                new LocalBankObjectInteractionHandler(
                    bank,
                    movement
                );

            routedNpcs=
                new LocalRoutedNpcInteractionHandler(
                    npcs,
                    bank,
                    movement
                );

            groundItems=
                new LocalGroundItemInteractionHandler(
                    world,
                    bank,
                    movement
                );

            PetBridge petBridge=new PetBridge();
            petBridge.publisher=publisher;

            petDropPickup=
                new LocalPetDropPickupHandler(
                    world,
                    bank,
                    movement,
                    petState,
                    petEffects,
                    player.miniPets(),
                    npcs,
                    new VoidglassPetState(),
                    new PetAccessoryState(),
                    dev,
                    petBridge
                );

            petRuntime=
                new LocalPetRuntimeCommandHandler(
                    petState,
                    petEffects,
                    npcs,
                    movement
                );

            regionBridge.publisher=publisher;
        }

        LocalWorldTickCoordinator coordinator(
            boolean movementEnabled,
            TickBridge tickBridge
        ){
            LocalRegionStreamHandler regionStreams=
                new LocalRegionStreamHandler(
                    movementEnabled,
                    world,
                    player,
                    movement,
                    homeWorld,
                    npcs,
                    playerInteractions,
                    combat,
                    regionBridge
                );

            tickBridge.publisher=publisher;

            return new LocalWorldTickCoordinator(
                movementEnabled,
                world,
                player,
                movement,
                equipment,
                combatStyles,
                petEffects,
                npcs,
                homeWorld,
                combat,
                regionStreams,
                playerInteractions,
                bankObjects,
                routedNpcs,
                groundItems,
                petDropPickup,
                petRuntime,
                tickBridge
            );
        }

        @Override public void close(){
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        try(Fixture idle=new Fixture()){
            TickBridge bridge=new TickBridge();
            LocalWorldTickCoordinator coordinator=
                idle.coordinator(false,bridge);

            int before=idle.wire.size();

            coordinator.tick(
                1L,
                1_000L,
                idle.writer,
                "[tick-test] "
            );

            if(coordinator.legacyTickCount()!=1L)
                throw new AssertionError(
                    "idle legacy tick count changed"
                );

            if(coordinator.movementTickCount()!=0L)
                throw new AssertionError(
                    "idle pulse advanced movement"
                );

            if(bridge.followScheduleCalls!=1||
               bridge.testSequenceScheduleCalls!=1){
                throw new AssertionError(
                    "post-tick scheduler hooks changed"
                );
            }

            if(idle.wire.size()<=before)
                throw new AssertionError(
                    "idle HOME pulse emitted no packets"
                );
        }

        try(Fixture moving=new Fixture()){
            TickBridge bridge=new TickBridge();
            LocalWorldTickCoordinator coordinator=
                moving.coordinator(true,bridge);

            String accepted=
                moving.movement.accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{
                            MovementState.INITIAL_X+1
                        },
                        new int[]{
                            MovementState.INITIAL_Y
                        },
                        new byte[0]
                    )
                );

            if(!accepted.startsWith("ACCEPTED"))
                throw new AssertionError(
                    "movement setup rejected: "+accepted
                );

            coordinator.tick(
                1L,
                2_000L,
                moving.writer,
                "[tick-test] "
            );

            if(coordinator.legacyTickCount()!=1L)
                throw new AssertionError(
                    "moving legacy tick count changed"
                );

            if(coordinator.movementTickCount()!=1L)
                throw new AssertionError(
                    "queued movement did not advance exactly once"
                );

            if(bridge.saveCalls!=1)
                throw new AssertionError(
                    "HOME movement save count="+
                    bridge.saveCalls
                );

            if(!"POSITION_TICK".equals(
                bridge.lastSaveReason
            )){
                throw new AssertionError(
                    "movement save boundary changed: "+
                    bridge.lastSaveReason
                );
            }

            if(moving.movement.x()!=
               MovementState.INITIAL_X+1){
                throw new AssertionError(
                    "authoritative movement position changed"
                );
            }
        }

        try(Fixture transientMove=new Fixture()){
            TickBridge bridge=new TickBridge();
            LocalWorldTickCoordinator coordinator=
                transientMove.coordinator(true,bridge);

            Tile start=
                WorldCollisionAuthority.safeTile(
                    16193,
                    0
                );

            if(start==null)
                throw new AssertionError(
                    "transient safe-tile fixture missing"
                );

            int targetX=-1;
            int targetY=-1;
            for(int dx=-1;dx<=1&&targetX<0;dx++){
                for(int dy=-1;dy<=1;dy++){
                    if(dx==0&&dy==0)
                        continue;

                    int nx=start.x+dx;
                    int ny=start.y+dy;

                    if(CollisionStepAuthority.canStep(
                            CollisionStepAuthority.Policy.WORLD_STATIC,
                            start.x,
                            start.y,
                            0,
                            nx,
                            ny)){
                        targetX=nx;
                        targetY=ny;
                        break;
                    }
                }
            }

            if(targetX<0)
                throw new AssertionError(
                    "transient safe tile has no traversable neighbor "+
                    start
                );

            int chunkX=start.x>>3;
            int chunkY=start.y>>3;
            int baseX=(chunkX-6)<<3;
            int baseY=(chunkY-6)<<3;

            transientMove.movement.enterTransientRegion(
                start.x,
                start.y,
                0,
                baseX,
                baseY
            );

            String accepted=
                transientMove.movement.accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{targetX},
                        new int[]{targetY},
                        new byte[0]
                    )
                );

            if(!accepted.startsWith("ACCEPTED"))
                throw new AssertionError(
                    "transient movement rejected: "+
                    accepted
                );

            coordinator.tick(
                5L,
                2_500L,
                transientMove.writer,
                "[tick-transient-test] "
            );

            if(coordinator.movementTickCount()!=1L)
                throw new AssertionError(
                    "transient movement did not advance"
                );

            if(bridge.saveCalls!=0||
               bridge.lastSaveReason!=null)
                throw new AssertionError(
                    "nonpersistent transient movement saved account calls="+
                    bridge.saveCalls+
                    " reason="+bridge.lastSaveReason
                );
        }

        try(Fixture respawning=new Fixture()){
            TickBridge bridge=new TickBridge();
            LocalWorldTickCoordinator coordinator=
                respawning.coordinator(true,bridge);

            String accepted=
                respawning.movement.accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{
                            MovementState.INITIAL_X+1
                        },
                        new int[]{
                            MovementState.INITIAL_Y
                        },
                        new byte[0]
                    )
                );

            if(!accepted.startsWith("ACCEPTED"))
                throw new AssertionError(
                    "respawn movement setup rejected: "+
                    accepted
                );

            respawning.movement.advance();

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(
                    respawning.player
                );

            PlayerLifecycleService.DamageResult lethal=
                lifecycle.applyDamage(
                    500,
                    10L,
                    "WORLD_TICK_RESPAWN_TEST"
                );

            if(!lethal.died||
               !respawning.player.lifecycle().dead())
                throw new AssertionError(
                    "respawn fixture did not die "+
                    lethal
                );

            int before=respawning.wire.size();

            coordinator.tick(
                15L,
                3_000L,
                respawning.writer,
                "[tick-test] "
            );

            if(respawning.player.lifecycle().dead())
                throw new AssertionError(
                    "world tick did not respawn player"
                );

            if(respawning.player.playerState().currentLevel(
                    PlayerState.HITPOINTS)!=99)
                throw new AssertionError(
                    "world tick did not restore HP"
                );

            if(respawning.movement.x()!=
                    MovementState.INITIAL_X||
               respawning.movement.y()!=
                    MovementState.INITIAL_Y||
               !respawning.movement.inHomeWindow())
                throw new AssertionError(
                    "world tick did not restore HOME"
                );

            if(!"PLAYER_RESPAWN".equals(
                    bridge.lastSaveReason))
                throw new AssertionError(
                    "respawn save boundary missing: "+
                    bridge.lastSaveReason
                );

            if(respawning.wire.size()<=before)
                throw new AssertionError(
                    "respawn emitted no client packets"
                );
        }

        try(Fixture deferredTake=new Fixture()){
            TickBridge bridge=new TickBridge();
            LocalWorldTickCoordinator coordinator=
                deferredTake.coordinator(true,bridge);

            Tile target=
                new Tile(
                    MovementState.INITIAL_X+1,
                    MovementState.INITIAL_Y,
                    0
                );

            GroundItem ground=
                deferredTake.world.groundItems().add(
                    995,
                    25,
                    target,
                    "opensrc",
                    30L,
                    false
                );

            LocalGroundItemInteractionHandler.Result queued=
                deferredTake.groundItems.handle(
                    new GroundItemInteraction(
                        236,
                        3,
                        995,
                        target.x,
                        target.y
                    ),
                    "opensrc",
                    deferredTake.publisher,
                    deferredTake.writer
                );

            if(queued==null||
               !queued.logText.contains(
                   "DEFERRED_UNTIL_EXACT_TILE")||
               !deferredTake.groundItems.hasPendingTake())
                throw new AssertionError(
                    "deferred Take fixture was not retained"
                );

            String accepted=
                deferredTake.movement.accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{target.x},
                        new int[]{target.y},
                        new byte[0]
                    )
                );

            if(!accepted.startsWith("ACCEPTED"))
                throw new AssertionError(
                    "deferred Take movement rejected: "+
                    accepted
                );

            deferredTake.writer.beginBatch();

            coordinator.tick(
                20L,
                5_000L,
                deferredTake.writer,
                "[tick-ground-take-abort] "
            );

            if(deferredTake.bank.inventoryCount(995)!=0||
               deferredTake.world.groundItems().byId(
                    ground.id
                )!=ground||
               !deferredTake.groundItems.hasPendingTake()||
               !coordinator.deferredGroundTakeEligible())
                throw new AssertionError(
                    "deferred Take committed inside outer world-tick batch"
                );

            deferredTake.writer.abortBatch();
            coordinator.abortHomePresentationBatch();
            coordinator.abortGroundPresentationBatch();
            coordinator.abortDeferredGroundTakeAfterWorldTick();

            if(deferredTake.bank.inventoryCount(995)!=0||
               deferredTake.world.groundItems().byId(
                    ground.id
                )!=ground||
               !deferredTake.groundItems.hasPendingTake()||
               coordinator.deferredGroundTakeEligible())
                throw new AssertionError(
                    "outer world-tick abort changed deferred Take state"
                );

            deferredTake.writer.beginBatch();

            coordinator.tick(
                21L,
                5_600L,
                deferredTake.writer,
                "[tick-ground-take-commit] "
            );

            LocalSession.endWorldTickBatch(
                deferredTake.writer
            );
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                5_600L
            );

            if(deferredTake.bank.inventoryCount(995)!=0||
               deferredTake.world.groundItems().byId(
                    ground.id
                )!=ground||
               !deferredTake.groundItems.hasPendingTake())
                throw new AssertionError(
                    "deferred Take settled before post-commit hook"
                );

            coordinator.settleDeferredGroundTakeAfterWorldTick(
                5_600L,
                deferredTake.writer,
                "[tick-ground-take-commit] "
            );

            if(deferredTake.bank.inventoryCount(995)!=25||
               deferredTake.world.groundItems().byId(
                    ground.id
                )!=null||
               deferredTake.groundItems.hasPendingTake()||
               !"GROUND_TAKE".equals(
                    bridge.lastSaveReason
               ))
                throw new AssertionError(
                    "post-commit deferred Take settlement failed"
                );
        }

        try(Fixture deferredPet=new Fixture()){
            TickBridge bridge=new TickBridge();
            LocalWorldTickCoordinator coordinator=
                deferredPet.coordinator(
                    true,
                    bridge
                );

            PetDefinitionRepository.Def petDef=
                PetDefinitionRepository.get(
                    24019
                );

            if(petDef==null)
                throw new AssertionError(
                    "deferred pet fixture missing"
                );

            String spawned=
                deferredPet.npcs.spawnPet(
                    petDef,
                    deferredPet.movement,
                    deferredPet.writer
                );

            if(spawned==null||
               !spawned.startsWith(
                   "PET_SPAWN_OK"))
                throw new AssertionError(
                    "deferred pet spawn failed result="+
                    spawned
                );

            deferredPet.petState.activate(
                petDef
            );

            NpcEntity pet=
                deferredPet.npcs.pet();

            if(pet==null)
                throw new AssertionError(
                    "deferred pet actor missing"
                );

            pet.x=
                deferredPet.movement.x()+1;
            pet.y=
                deferredPet.movement.y();

            if(!deferredPet.petDropPickup
                    .handlePickupNpcAction(
                        new NpcAction(
                            155,
                            pet.sceneIndex
                        ),
                        deferredPet.writer,
                        "[tick-pet-pickup-arm] "
                    )||
               !deferredPet.petDropPickup
                    .pickupPending())
                throw new AssertionError(
                    "deferred pet pickup did not arm"
                );

            int inventoryBefore=
                deferredPet.bank.inventoryCount(
                    petDef.itemId
                );

            deferredPet.writer.beginBatch();

            coordinator.tick(
                30L,
                6_000L,
                deferredPet.writer,
                "[tick-pet-pickup-abort] "
            );

            if(!deferredPet.petState.active()||
               deferredPet.npcs.pet()!=pet||
               deferredPet.bank.inventoryCount(
                    petDef.itemId
                )!=inventoryBefore||
               !deferredPet.petDropPickup
                    .pickupPending()||
               !coordinator
                    .deferredPetPickupEligible())
                throw new AssertionError(
                    "deferred pet pickup committed inside outer world-tick batch"
                );

            deferredPet.writer.abortBatch();
            coordinator.abortHomePresentationBatch();
            coordinator.abortGroundPresentationBatch();
            coordinator.abortDeferredGroundTakeAfterWorldTick();
            coordinator.abortDeferredPetPickupAfterWorldTick();

            if(!deferredPet.petState.active()||
               deferredPet.npcs.pet()!=pet||
               deferredPet.bank.inventoryCount(
                    petDef.itemId
                )!=inventoryBefore||
               !deferredPet.petDropPickup
                    .pickupPending()||
               coordinator
                    .deferredPetPickupEligible())
                throw new AssertionError(
                    "outer world-tick abort changed deferred pet pickup state"
                );

            deferredPet.writer.beginBatch();

            coordinator.tick(
                31L,
                6_600L,
                deferredPet.writer,
                "[tick-pet-pickup-commit] "
            );

            LocalSession.endWorldTickBatch(
                deferredPet.writer
            );
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                6_600L
            );

            if(!deferredPet.petState.active()||
               deferredPet.npcs.pet()!=pet||
               deferredPet.bank.inventoryCount(
                    petDef.itemId
               )!=inventoryBefore||
               !deferredPet.petDropPickup
                    .pickupPending())
                throw new AssertionError(
                    "deferred pet pickup settled before post-commit hook"
                );

            coordinator.settleDeferredGroundTakeAfterWorldTick(
                6_600L,
                deferredPet.writer,
                "[tick-pet-pickup-commit] "
            );

            coordinator.settleDeferredPetPickupAfterWorldTick(
                6_600L,
                deferredPet.writer,
                "[tick-pet-pickup-commit] "
            );

            if(deferredPet.petState.active()||
               deferredPet.npcs.pet()!=null||
               deferredPet.bank.inventoryCount(
                    petDef.itemId
                )!=inventoryBefore+1||
               deferredPet.petDropPickup
                    .pickupPending()||
               coordinator
                    .deferredPetPickupEligible())
                throw new AssertionError(
                    "post-commit deferred pet pickup settlement failed"
                );
        }

        try(Fixture reconnect=new Fixture()){
            reconnect.npcs.tickHome(
                reconnect.movement,
                reconnect.writer,
                reconnect.homeWorld,
                69L
            );

            TickBridge bridge=new TickBridge();
            LocalWorldTickCoordinator freshCoordinator=
                reconnect.coordinator(false,bridge);

            freshCoordinator.tick(
                70L,
                4_000L,
                reconnect.writer,
                "[tick-reconnect-test] "
            );

            if(freshCoordinator.legacyTickCount()!=1L)
                throw new AssertionError(
                    "fresh session legacy counter changed"
                );
        }

        System.out.println(
            "LOCAL_WORLD_TICK_COORDINATOR_PASS "+
            "idlePulse=true authoritativeMove=true "+
            "tickCountersOwned=true schedulerHooks=true "+
            "respawnLifecycle=true "+
            "transientMovementSave=false "+
            "deferredTakeOuterAbortPreservesState=true "+
            "deferredTakePostCommitSettles=true "+
            "deferredPetPickupOuterAbortPreservesState=true "+
            "deferredPetPickupPostCommitSettles=true "+
            "sharedHomeClockReconnect=true"
        );
    }
}
