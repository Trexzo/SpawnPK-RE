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

        System.out.println(
            "LOCAL_WORLD_TICK_COORDINATOR_PASS "+
            "idlePulse=true authoritativeMove=true "+
            "tickCountersOwned=true schedulerHooks=true "+
            "respawnLifecycle=true"
        );
    }
}
