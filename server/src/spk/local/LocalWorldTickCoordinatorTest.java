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

        @Override public void publishPlayerAppearanceSnapshot(
            int[] appearanceItems,
            ServerPacketWriter writer
        ){
            if(appearanceItems==null||
               appearanceItems.length!=
                    EquipmentState.APPEARANCE_SLOTS)
                throw new AssertionError(
                    "invalid respawn appearance snapshot"
                );
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
                    movement,
                    null,
                    player,
                    equipment
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

            int deadX=respawning.movement.x();
            int deadY=respawning.movement.y();

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

            respawning.writer.beginBatch();

            coordinator.tick(
                15L,
                3_000L,
                respawning.writer,
                "[tick-respawn-abort] "
            );

            if(!respawning.player.lifecycle().dead()||
               respawning.player.playerState().currentLevel(
                    PlayerState.HITPOINTS)!=0||
               respawning.movement.x()!=deadX||
               respawning.movement.y()!=deadY||
               !coordinator.deferredRespawnEligible())
                throw new AssertionError(
                    "respawn committed inside outer world-tick batch"
                );

            respawning.writer.abortBatch();
            coordinator.abortRegionStreamBatch();
            coordinator.abortHomePresentationBatch();
            coordinator.abortGroundPresentationBatch();
            coordinator.abortDeferredRespawnAfterWorldTick();
            coordinator.abortDeferredBankInteractionsAfterWorldTick();
            coordinator.abortDeferredMakeoverInteractionsAfterWorldTick();
            coordinator.abortDeferredGroundTakeAfterWorldTick();
            coordinator.abortDeferredPetPickupAfterWorldTick();
            coordinator.abortDeferredPetEffectTimeoutAfterWorldTick();

            if(!respawning.player.lifecycle().dead()||
               respawning.player.playerState().currentLevel(
                    PlayerState.HITPOINTS)!=0||
               respawning.movement.x()!=deadX||
               respawning.movement.y()!=deadY||
               coordinator.deferredRespawnEligible()||
               respawning.wire.size()!=before)
                throw new AssertionError(
                    "outer abort changed prepared respawn preimage"
                );

            respawning.writer.beginBatch();

            coordinator.tick(
                16L,
                3_600L,
                respawning.writer,
                "[tick-respawn-commit] "
            );

            LocalSession.endWorldTickBatch(
                respawning.writer
            );
            coordinator.commitRegionStreamBatch();
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                3_600L
            );

            if(!respawning.player.lifecycle().dead()||
               respawning.player.playerState().currentLevel(
                    PlayerState.HITPOINTS)!=0||
               respawning.movement.x()!=deadX||
               respawning.movement.y()!=deadY||
               !coordinator.deferredRespawnEligible())
                throw new AssertionError(
                    "respawn settled before post-commit hook"
                );

            coordinator.settleDeferredRespawnAfterWorldTick(
                respawning.writer,
                "[tick-respawn-commit] "
            );

            if(!respawning.player.lifecycle().alive()||
               !respawning.player.playerState().alive()||
               respawning.player.playerState().currentLevel(
                    PlayerState.HITPOINTS)!=99)
                throw new AssertionError(
                    "post-commit respawn HP/lifecycle mismatch"
                );

            if(respawning.movement.x()!=
                    MovementState.INITIAL_X||
               respawning.movement.y()!=
                    MovementState.INITIAL_Y||
               !respawning.movement.inHomeWindow())
                throw new AssertionError(
                    "post-commit respawn did not restore HOME"
                );

            if(!"PLAYER_RESPAWN".equals(
                    bridge.lastSaveReason))
                throw new AssertionError(
                    "respawn save boundary missing: "+
                    bridge.lastSaveReason
                );

            if(respawning.wire.size()<=before)
                throw new AssertionError(
                    "post-commit respawn emitted no client packets"
                );
        }

        try(Fixture deferredBank=new Fixture()){
            TickBridge bridge=new TickBridge();
            LocalWorldTickCoordinator coordinator=
                deferredBank.coordinator(false,bridge);

            int targetX=
                deferredBank.movement.x()+2;
            int targetY=
                deferredBank.movement.y();

            String spawned=
                deferredBank.npcs.devSpawnNpc(
                    7605,
                    2,
                    0,
                    deferredBank.movement,
                    deferredBank.writer
                );

            if(!spawned.startsWith(
                    "DEV_NPC_SPAWN_OK"))
                throw new AssertionError(
                    "deferred bank coordinator banker spawn="+
                    spawned
                );

            NpcEntity banker=null;

            for(NpcEntity npc:
                    deferredBank.npcs.snapshot())
                if(npc.definitionId==7605&&
                   npc.x==targetX&&
                   npc.y==targetY){
                    banker=npc;
                    break;
                }

            if(banker==null)
                throw new AssertionError(
                    "deferred bank coordinator banker missing"
                );

            String queued=
                deferredBank.routedNpcs.handle(
                    new NpcAction(
                        17,
                        banker.sceneIndex
                    ),
                    banker,
                    deferredBank.writer
                );

            if(queued==null||
               !queued.contains(
                   "DEFERRED_UNTIL_ADJACENT")||
               !deferredBank.routedNpcs.hasPendingBank()||
               deferredBank.routedNpcs.pendingBankNpc()!=banker)
                throw new AssertionError(
                    "deferred bank coordinator request not queued result="+
                    queued
                );

            if(deferredBank.movement.advance()==null)
                throw new AssertionError(
                    "deferred bank coordinator did not approach"
                );

            deferredBank.writer.beginBatch();

            coordinator.tick(
                15L,
                4_000L,
                deferredBank.writer,
                "[tick-bank-abort] "
            );

            if(deferredBank.bank.isOpen()||
               !deferredBank.routedNpcs.hasPendingBank()||
               deferredBank.routedNpcs.pendingBankNpc()!=banker||
               !coordinator.deferredBankInteractionEligible())
                throw new AssertionError(
                    "deferred Bank opened inside outer world-tick batch"
                );

            deferredBank.writer.abortBatch();
            coordinator.abortHomePresentationBatch();
            coordinator.abortGroundPresentationBatch();
            coordinator.abortDeferredBankInteractionsAfterWorldTick();
            coordinator.abortDeferredGroundTakeAfterWorldTick();
            coordinator.abortDeferredPetPickupAfterWorldTick();

            if(deferredBank.bank.isOpen()||
               !deferredBank.routedNpcs.hasPendingBank()||
               deferredBank.routedNpcs.pendingBankNpc()!=banker||
               coordinator.deferredBankInteractionEligible())
                throw new AssertionError(
                    "outer world-tick abort changed deferred Bank state"
                );

            deferredBank.writer.beginBatch();

            coordinator.tick(
                16L,
                4_600L,
                deferredBank.writer,
                "[tick-bank-commit] "
            );

            LocalSession.endWorldTickBatch(
                deferredBank.writer
            );
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                4_600L
            );

            if(deferredBank.bank.isOpen()||
               !deferredBank.routedNpcs.hasPendingBank())
                throw new AssertionError(
                    "deferred Bank settled before post-commit hook"
                );

            coordinator.settleDeferredBankInteractionsAfterWorldTick(
                4_600L,
                deferredBank.writer,
                "[tick-bank-commit] "
            );

            if(!deferredBank.bank.isOpen()||
               deferredBank.routedNpcs.hasPendingBank())
                throw new AssertionError(
                    "post-commit deferred Bank settlement failed"
                );
        }

        try(Fixture deferredMakeover=new Fixture()){
            TickBridge bridge=new TickBridge();
            LocalWorldTickCoordinator coordinator=
                deferredMakeover.coordinator(true,bridge);
            LocalMakeoverMageHandler makeover=
                deferredMakeover.routedNpcs.makeoverMage();

            if(makeover==null)
                throw new AssertionError(
                    "deferred Make-over fixture handler missing"
                );

            String spawned=
                deferredMakeover.npcs.devSpawnNpc(
                    LocalMakeoverMageHandler.NPC_ID,
                    2,
                    0,
                    deferredMakeover.movement,
                    deferredMakeover.writer
                );

            if(!spawned.startsWith(
                    "DEV_NPC_SPAWN_OK"))
                throw new AssertionError(
                    "deferred Make-over mage spawn failed: "+
                    spawned
                );

            NpcEntity mage=null;

            for(NpcEntity npc:
                    deferredMakeover.npcs.snapshot())
                if(npc.definitionId==
                        LocalMakeoverMageHandler.NPC_ID&&
                   npc.x==
                        deferredMakeover.movement.x()+2&&
                   npc.y==
                        deferredMakeover.movement.y()){
                    mage=npc;
                    break;
                }

            if(mage==null)
                throw new AssertionError(
                    "deferred Make-over mage missing"
                );

            if(!makeover.beginIfSupported(
                    new NpcAction(
                        155,
                        mage.sceneIndex
                    ),
                    mage,
                    deferredMakeover.writer,
                    "[tick-makeover-fixture] "
                )||
               !makeover.pending()||
               makeover.active())
                throw new AssertionError(
                    "deferred Make-over request not queued"
                );

            deferredMakeover.writer.beginBatch();

            coordinator.tick(
                18L,
                4_800L,
                deferredMakeover.writer,
                "[tick-makeover-abort] "
            );

            if(!makeover.pending()||
               makeover.active()||
               !coordinator.deferredMovementEligible()||
               coordinator
                    .deferredMakeoverInteractionEligible())
                throw new AssertionError(
                    "movement-dependent Make-over tail ran before movement commit"
                );

            deferredMakeover.writer.abortBatch();
            coordinator.abortDeferredMovementAfterWorldTick();
            coordinator.abortHomePresentationBatch();
            coordinator.abortGroundPresentationBatch();
            coordinator.abortDeferredBankInteractionsAfterWorldTick();
            coordinator.abortDeferredMakeoverInteractionsAfterWorldTick();
            coordinator.abortDeferredGroundTakeAfterWorldTick();
            coordinator.abortDeferredPetPickupAfterWorldTick();

            if(!makeover.pending()||
               makeover.active()||
               coordinator.deferredMovementEligible()||
               coordinator
                    .deferredMakeoverInteractionEligible())
                throw new AssertionError(
                    "outer world-tick abort changed deferred Make-over state"
                );

            deferredMakeover.writer.beginBatch();

            coordinator.tick(
                19L,
                5_400L,
                deferredMakeover.writer,
                "[tick-makeover-commit] "
            );

            LocalSession.endWorldTickBatch(
                deferredMakeover.writer
            );

            if(!coordinator.deferredMovementEligible()||
               coordinator
                    .deferredMakeoverInteractionEligible())
                throw new AssertionError(
                    "movement tail became eligible before movement settlement"
                );

            coordinator.settleDeferredMovementAfterWorldTick(
                deferredMakeover.writer,
                "[tick-makeover-commit] "
            );

            if(coordinator.deferredMovementEligible()||
               !coordinator
                    .deferredMakeoverInteractionEligible())
                throw new AssertionError(
                    "committed movement did not release Make-over tail"
                );

            coordinator.settleDeferredBankInteractionsAfterWorldTick(
                5_400L,
                deferredMakeover.writer,
                "[tick-makeover-commit] "
            );

            if(!makeover.pending()||
               makeover.active())
                throw new AssertionError(
                    "deferred Make-over settled before post-commit hook"
                );

            coordinator
                .settleDeferredMakeoverInteractionsAfterWorldTick(
                    5_400L,
                    deferredMakeover.writer,
                    "[tick-makeover-commit] "
                );

            if(makeover.pending()||
               !makeover.active())
                throw new AssertionError(
                    "post-commit deferred Make-over settlement failed"
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
               !coordinator.deferredMovementEligible()||
               coordinator.deferredGroundTakeEligible())
                throw new AssertionError(
                    "movement-dependent Take tail ran before movement commit"
                );

            deferredTake.writer.abortBatch();
            coordinator.abortDeferredMovementAfterWorldTick();
            coordinator.abortHomePresentationBatch();
            coordinator.abortGroundPresentationBatch();
            coordinator.abortDeferredBankInteractionsAfterWorldTick();
            coordinator.abortDeferredGroundTakeAfterWorldTick();

            if(deferredTake.bank.inventoryCount(995)!=0||
               deferredTake.world.groundItems().byId(
                    ground.id
                )!=ground||
               !deferredTake.groundItems.hasPendingTake()||
               coordinator.deferredMovementEligible()||
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

            if(!coordinator.deferredMovementEligible()||
               coordinator.deferredGroundTakeEligible())
                throw new AssertionError(
                    "Take tail became eligible before movement settlement"
                );

            coordinator.settleDeferredMovementAfterWorldTick(
                deferredTake.writer,
                "[tick-ground-take-commit] "
            );

            if(coordinator.deferredMovementEligible()||
               !coordinator.deferredGroundTakeEligible())
                throw new AssertionError(
                    "committed movement did not release Take tail"
                );

            coordinator.settleDeferredBankInteractionsAfterWorldTick(
                5_600L,
                deferredTake.writer,
                "[tick-ground-take-commit] "
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

        try(Fixture deferredPetPickup=new Fixture()){
            TickBridge bridge=new TickBridge();
            LocalWorldTickCoordinator coordinator=
                deferredPetPickup.coordinator(false,bridge);

            PetDefinitionRepository.Def def=
                PetDefinitionRepository.get(24019);

            if(def==null)
                throw new AssertionError(
                    "deferred pet pickup fixture definition missing"
                );

            String spawn=
                deferredPetPickup.npcs.spawnPet(
                    def,
                    deferredPetPickup.movement,
                    deferredPetPickup.writer
                );

            if(!spawn.startsWith("PET_SPAWN_OK"))
                throw new AssertionError(
                    "deferred pet pickup spawn failed: "+
                    spawn
                );

            deferredPetPickup.petState.activate(
                def
            );

            String follow=
                deferredPetPickup.npcs.tickFollow(
                    deferredPetPickup.movement,
                    deferredPetPickup.writer
                );

            NpcEntity pet=
                deferredPetPickup.npcs.pet();

            if(follow==null||
               !follow.contains("movement=WALK")||
               pet==null||
               Math.abs(
                   pet.x-
                   deferredPetPickup.movement.x()
               )+
               Math.abs(
                   pet.y-
                   deferredPetPickup.movement.y()
               )!=1)
                throw new AssertionError(
                    "deferred pet pickup fixture not cardinal adjacent"
                );

            if(!deferredPetPickup.petDropPickup
                    .handlePickupNpcAction(
                        new NpcAction(
                            155,
                            pet.sceneIndex
                        ),
                        deferredPetPickup.writer,
                        "[tick-pet-pickup-fixture] "
                    )||
               !deferredPetPickup.petDropPickup
                    .pickupPending())
                throw new AssertionError(
                    "deferred pet pickup was not queued"
                );

            deferredPetPickup.writer.beginBatch();

            coordinator.tick(
                30L,
                6_000L,
                deferredPetPickup.writer,
                "[tick-pet-pickup-abort] "
            );

            if(deferredPetPickup.npcs.pet()!=pet||
               !deferredPetPickup.petState.active()||
               deferredPetPickup.bank.inventoryCount(
                    def.itemId
               )!=0||
               !deferredPetPickup.petDropPickup.pickupPending()||
               !coordinator.deferredPetPickupEligible())
                throw new AssertionError(
                    "pet pickup committed inside outer world-tick batch"
                );

            deferredPetPickup.writer.abortBatch();
            coordinator.abortHomePresentationBatch();
            coordinator.abortGroundPresentationBatch();
            coordinator.abortDeferredBankInteractionsAfterWorldTick();
            coordinator.abortDeferredGroundTakeAfterWorldTick();
            coordinator.abortDeferredPetPickupAfterWorldTick();

            if(deferredPetPickup.npcs.pet()!=pet||
               !deferredPetPickup.petState.active()||
               deferredPetPickup.bank.inventoryCount(
                    def.itemId
               )!=0||
               !deferredPetPickup.petDropPickup.pickupPending()||
               coordinator.deferredPetPickupEligible())
                throw new AssertionError(
                    "outer world-tick abort changed pet pickup state"
                );

            deferredPetPickup.writer.beginBatch();

            coordinator.tick(
                31L,
                6_600L,
                deferredPetPickup.writer,
                "[tick-pet-pickup-commit] "
            );

            LocalSession.endWorldTickBatch(
                deferredPetPickup.writer
            );
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                6_600L
            );
            coordinator.settleDeferredBankInteractionsAfterWorldTick(
                6_600L,
                deferredPetPickup.writer,
                "[tick-pet-pickup-commit] "
            );

            if(deferredPetPickup.npcs.pet()!=pet||
               !deferredPetPickup.petState.active()||
               deferredPetPickup.bank.inventoryCount(
                    def.itemId
               )!=0||
               !deferredPetPickup.petDropPickup.pickupPending())
                throw new AssertionError(
                    "pet pickup settled before post-commit hook"
                );

            coordinator.settleDeferredGroundTakeAfterWorldTick(
                6_600L,
                deferredPetPickup.writer,
                "[tick-pet-pickup-commit] "
            );
            coordinator.settleDeferredPetPickupAfterWorldTick(
                deferredPetPickup.writer,
                "[tick-pet-pickup-commit] ",
                6_600L
            );

            if(deferredPetPickup.npcs.pet()!=null||
               deferredPetPickup.petState.active()||
               deferredPetPickup.bank.inventoryCount(
                    def.itemId
               )!=1||
               deferredPetPickup.petDropPickup.pickupPending())
                throw new AssertionError(
                    "post-commit pet pickup settlement failed"
                );
        }

        try(Fixture petTimeout=new Fixture()){
            TickBridge bridge=new TickBridge();
            LocalWorldTickCoordinator coordinator=
                petTimeout.coordinator(false,bridge);

            PetDefinitionRepository.Def def=
                PetDefinitionRepository.get(24019);

            if(def==null)
                throw new AssertionError(
                    "pet timeout fixture definition missing"
                );

            String spawn=
                petTimeout.npcs.spawnPet(
                    def,
                    petTimeout.movement,
                    petTimeout.writer
                );

            if(!spawn.startsWith("PET_SPAWN_OK"))
                throw new AssertionError(
                    "pet timeout fixture spawn failed: "+
                    spawn
                );

            petTimeout.petState.activate(def);
            petTimeout.petEffects.onPetChanged(
                def.itemId,
                def.npcId
            );
            petTimeout.petEffects.forceCharge(
                1,
                10_000L
            );

            String charged=
                petTimeout.npcs.setPetNativeState(
                    1,
                    petTimeout.writer
                );

            if(!charged.startsWith(
                    "PET_NATIVE_STATE_OK")||
               petTimeout.petEffects.charge()!=1||
               petTimeout.npcs.petNativeState()!=1)
                throw new AssertionError(
                    "pet timeout fixture charge not armed"
                );

            long timeoutAt=
                10_000L+
                petTimeout.petEffects.resetMs();

            petTimeout.writer.beginBatch();

            coordinator.tick(
                40L,
                timeoutAt,
                petTimeout.writer,
                "[tick-pet-timeout-abort] "
            );

            if(petTimeout.petEffects.charge()!=1||
               petTimeout.petEffects.lastDamageAtMs()!=10_000L||
               petTimeout.npcs.petNativeState()!=1||
               !coordinator
                    .deferredPetEffectTimeoutEligible())
                throw new AssertionError(
                    "pet timeout committed inside outer world-tick batch"
                );

            petTimeout.writer.abortBatch();
            coordinator.abortRegionStreamBatch();
            coordinator.abortHomePresentationBatch();
            coordinator.abortGroundPresentationBatch();
            coordinator.abortDeferredBankInteractionsAfterWorldTick();
            coordinator.abortDeferredMakeoverInteractionsAfterWorldTick();
            coordinator.abortDeferredGroundTakeAfterWorldTick();
            coordinator.abortDeferredPetPickupAfterWorldTick();
            coordinator.abortDeferredPetEffectTimeoutAfterWorldTick();

            if(petTimeout.petEffects.charge()!=1||
               petTimeout.petEffects.lastDamageAtMs()!=10_000L||
               petTimeout.npcs.petNativeState()!=1||
               coordinator
                    .deferredPetEffectTimeoutEligible())
                throw new AssertionError(
                    "outer world-tick abort changed pet timeout state"
                );

            petTimeout.writer.beginBatch();

            coordinator.tick(
                41L,
                timeoutAt+600L,
                petTimeout.writer,
                "[tick-pet-timeout-commit] "
            );

            LocalSession.endWorldTickBatch(
                petTimeout.writer
            );
            coordinator.commitRegionStreamBatch();
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                timeoutAt+600L
            );

            if(petTimeout.petEffects.charge()!=1||
               petTimeout.npcs.petNativeState()!=1||
               !coordinator
                    .deferredPetEffectTimeoutEligible())
                throw new AssertionError(
                    "pet timeout settled before post-commit hook"
                );

            coordinator
                .settleDeferredPetEffectTimeoutAfterWorldTick(
                    petTimeout.writer,
                    "[tick-pet-timeout-commit] ",
                    41L
                );

            if(petTimeout.petEffects.charge()!=0||
               petTimeout.petEffects.accumulatedDamage()!=0||
               petTimeout.petEffects.lastDamageAtMs()!=0L||
               petTimeout.npcs.petNativeState()!=0||
               coordinator
                    .deferredPetEffectTimeoutEligible())
                throw new AssertionError(
                    "post-commit pet timeout settlement failed"
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
            "respawnOuterAbortPreservesDead=true "+
            "respawnPostCommitSettles=true "+
            "movementTailAfterCommit=true "+
            "movementAbortRestoresPreimage=true "+
            "transientMovementSave=false "+
            "deferredBankOuterAbortPreservesState=true "+
            "deferredBankPostCommitSettles=true "+
            "deferredMakeoverOuterAbortPreservesState=true "+
            "deferredMakeoverPostCommitSettles=true "+
            "deferredTakeOuterAbortPreservesState=true "+
            "deferredTakePostCommitSettles=true "+
            "deferredPetPickupOuterAbortPreservesState=true "+
            "deferredPetPickupPostCommitSettles=true "+
            "petEffectTimeoutOuterAbortPreservesState=true "+
            "petEffectTimeoutPostCommitSettles=true "+
            "sharedHomeClockReconnect=true"
        );
    }
}
