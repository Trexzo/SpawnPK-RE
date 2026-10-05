package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;

public final class PreTailPlayerInteractionFailurePolicyTest {
    private static final int QUEUE_CAPACITY=1<<20;

    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;

        @Override public String username(){ return "pre-tail-owner"; }
        @Override public boolean persistentAccount(){ return false; }
        @Override public long sessionWorldTick(){ return 1L; }
        @Override public SceneUpdatePublisher scenePublisher(){ return publisher; }
        @Override public void saveAccount(String tag,String reason){}
        @Override public int syncScopesightPassive(ServerPacketWriter writer){ return 0; }
        @Override public void resetPetFollowDeadline(){}
        @Override public void ensurePetFollowScheduled(long now){}
    }

    private static final class RegionBridge
        implements LocalRegionStreamHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;

        @Override public String username(){ return "pre-tail-owner"; }
        @Override public SceneUpdatePublisher scenePublisher(){ return publisher; }

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
        Player81WorldSync.Context sync;
        SceneUpdatePublisher publisher;
        long petDeadline=Long.MAX_VALUE;

        @Override public Player81WorldSync.Context player81Sync(){ return sync; }
        @Override public SceneUpdatePublisher scenePublisher(){ return publisher; }
        @Override public void saveAccount(String tag,String reason){}

        @Override public void publishOpponentOverlay(
            NpcEntity target,
            ServerPacketWriter writer,
            String tag,
            String reason
        ){}

        @Override public void clearOpponentOverlay(
            ServerPacketWriter writer,
            String tag,
            String reason
        ){}

        @Override public long petFollowDeadline(){ return petDeadline; }
        @Override public void setPetFollowDeadline(long value){ petDeadline=value; }
        @Override public void ensurePetFollowScheduled(long now){}
        @Override public void ensurePetTestSequenceScheduled(long now){}
    }

    private static final class Fixture implements AutoCloseable {
        final World world=World.isolatedForTest(60_000L);
        final WorldPlayer owner=new WorldPlayer();
        final WorldPlayer target=new WorldPlayer();

        final long ownerGeneration;
        final long targetGeneration;

        final MovementState movement;
        final EquipmentState equipment;
        final PetEffectState petEffects;
        final PetState petState;
        final CombatStyleState combatStyles;

        final DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        final NpcRegistry npcs=new NpcRegistry(dev);
        final HomeWorldRuntimePlan home=new HomeWorldRuntimePlan();
        final CombatEngine combat=new CombatEngine(dev);

        final OutboundPacketQueue queue=
            new OutboundPacketQueue(QUEUE_CAPACITY);

        final ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{1001,1002,1003,1004}
                )
            );

        final ByteArrayOutputStream targetWire=
            new ByteArrayOutputStream();

        final ServerPacketWriter targetWriter=
            new ServerPacketWriter(
                targetWire,
                new IsaacCipher(
                    new int[]{1005,1006,1007,1008}
                )
            );

        final Player81WorldSync.Context sync;
        final Player81WorldSync.Context targetSync;
        final int targetIndex;

        final SceneUpdatePublisher publisher;
        final LocalPlayerInteractionHandler playerInteractions;
        final LocalWorldTickCoordinator coordinator;
        final TickBridge tickBridge=new TickBridge();

        Fixture()throws Exception{
            ownerGeneration=
                world.registerPlayer(
                    owner,
                    "pre-tail-owner"
                );
            targetGeneration=
                world.registerPlayer(
                    target,
                    "pre-tail-target"
                );

            movement=owner.movement();
            equipment=owner.equipment();
            petEffects=owner.petEffects();
            petState=owner.petState();
            combatStyles=owner.combatStyles();

            String targetRoute=
                target.movement().accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{
                            MovementState.INITIAL_X+2
                        },
                        new int[]{
                            MovementState.INITIAL_Y
                        },
                        new byte[0]
                    )
                );

            if(!targetRoute.startsWith("ACCEPTED"))
                throw new AssertionError(
                    "target route setup failed: "+
                    targetRoute
                );

            target.movement().advance();
            target.movement().advance();

            npcs.bootstrapHome(
                writer,
                movement,
                petState,
                home
            );
            drain(queue);

            sync=
                Player81WorldSync.register(
                    writer,
                    world,
                    owner,
                    dev
                );
            targetSync=
                Player81WorldSync.register(
                    targetWriter,
                    world,
                    target,
                    dev
                );

            Player81WorldSync.transformForTest(
                sync,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                targetSync,
                BootstrapPackets.player81Idle()
            );

            targetIndex=
                sync.clientIndexFor(
                    target
                );

            if(targetIndex<0)
                throw new AssertionError(
                    "target not visible in owner Player81 mapping"
                );

            publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            playerInteractions=
                new LocalPlayerInteractionHandler(
                    world,
                    owner,
                    movement,
                    equipment
                );

            LocalBankObjectInteractionHandler bankObjects=
                new LocalBankObjectInteractionHandler(
                    owner.bank(),
                    movement
                );

            LocalRoutedNpcInteractionHandler routedNpcs=
                new LocalRoutedNpcInteractionHandler(
                    npcs,
                    owner.bank(),
                    movement,
                    null,
                    owner,
                    equipment
                );

            LocalGroundItemInteractionHandler groundItems=
                new LocalGroundItemInteractionHandler(
                    world,
                    owner.bank(),
                    movement
                );

            PetBridge petBridge=new PetBridge();
            petBridge.publisher=publisher;

            LocalPetDropPickupHandler petDropPickup=
                new LocalPetDropPickupHandler(
                    world,
                    owner.bank(),
                    movement,
                    petState,
                    petEffects,
                    owner.miniPets(),
                    npcs,
                    new VoidglassPetState(),
                    new PetAccessoryState(),
                    dev,
                    petBridge
                );

            LocalPetRuntimeCommandHandler petRuntime=
                new LocalPetRuntimeCommandHandler(
                    petState,
                    petEffects,
                    npcs,
                    movement
                );

            RegionBridge regionBridge=new RegionBridge();
            regionBridge.publisher=publisher;

            LocalRegionStreamHandler regionStreams=
                new LocalRegionStreamHandler(
                    true,
                    world,
                    owner,
                    movement,
                    home,
                    npcs,
                    playerInteractions,
                    combat,
                    regionBridge
                );

            tickBridge.publisher=publisher;
            tickBridge.sync=sync;

            coordinator=
                new LocalWorldTickCoordinator(
                    true,
                    world,
                    owner,
                    movement,
                    equipment,
                    combatStyles,
                    petEffects,
                    npcs,
                    home,
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

        void armDeferredTradeThenMoveAdjacent()
            throws Exception
        {
            PlayerAction trade=
                new PlayerAction(
                    73,
                    3,
                    targetIndex,
                    "Trade with"
                );

            String result=
                playerInteractions
                    .handleResolved(
                        trade,
                        target,
                        sync
                    );

            if(result==null||
               !result.contains(
                    "DEFERRED_UNTIL_CARDINAL_ADJACENT")||
               playerInteractions.activeTrade()==null)
                throw new AssertionError(
                    "deferred Trade fixture did not arm: "+
                    result
                );

            String approach=
                target.movement().accept(
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

            if(!approach.startsWith("ACCEPTED"))
                throw new AssertionError(
                    "target adjacency setup failed: "+
                    approach
                );

            target.movement().advance();

            int dx=
                Math.abs(
                    target.movement().x()-
                    movement.x()
                );
            int dy=
                Math.abs(
                    target.movement().y()-
                    movement.y()
                );

            if(dx+dy!=1)
                throw new AssertionError(
                    "target did not become cardinal-adjacent"
                );
        }

        @Override public void close(){
            Player81WorldSync.unregister(writer);
            Player81WorldSync.unregister(targetWriter);

            if(owner.registered())
                world.unregisterPlayer(
                    owner,
                    ownerGeneration
                );

            if(target.registered())
                world.unregisterPlayer(
                    target,
                    targetGeneration
                );

            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        preTailTradeThenIdlePlayer81FailureRetires();
        actualMovementPlayer81FailureRemainsRetryable();

        System.out.println(
            "PRE_TAIL_PLAYER_INTERACTION_FAILURE_POLICY_PASS "+
            "preTailInteractionAdvanced=true "+
            "idlePlayer81Failed=true "+
            "zeroBytesCommitted=true "+
            "writerRetired=true "+
            "movementRollbackPolicyUnaffected=true"
        );
    }

    private static void
        preTailTradeThenIdlePlayer81FailureRetires()
            throws Exception
    {
        try(Fixture f=new Fixture()){
            f.armDeferredTradeThenMoveAdjacent();

            armMalformedPreparedBatch(
                f.writer
            );

            IOException tickFailure=null;

            try{
                f.coordinator.tick(
                    90L,
                    40_000L,
                    f.writer,
                    "[pre-tail-trade] "
                );
            }catch(IOException expected){
                tickFailure=expected;
            }

            if(tickFailure==null||
               tickFailure.getMessage()==null||
               !tickFailure.getMessage().contains(
                    "prepared player81 transform failed"))
                throw new AssertionError(
                    "idle Player81 prepared transform did not fail as expected"
                );

            if(f.playerInteractions.activeTrade()!=null)
                throw new AssertionError(
                    "prepareTick did not dispatch/consume the adjacent deferred Trade"
                );

            if(f.targetWire.size()<=0)
                throw new AssertionError(
                    "pre-tail Trade request produced no peer-visible notification"
                );

            if(f.coordinator
                    .noMovementSemanticTailEntered())
                throw new AssertionError(
                    "failure incorrectly entered the ordinary no-movement tail"
                );

            LocalSession
                .abortWorldTickBatchAfterTickFailure(
                    f.writer,
                    tickFailure
                );

            if(!f.writer.terminal()||
               f.writer.batchActive()||
               f.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "pre-tail idle Player81 failure did not abort bytes and retire writer"
                );

            boolean terminalRejected=false;
            try{
                f.writer.fixed(
                    134,
                    BootstrapPackets.skill134(
                        PlayerState.HITPOINTS,
                        0,
                        1
                    )
                );
            }catch(IOException expected){
                terminalRejected=true;
            }

            if(!terminalRejected)
                throw new AssertionError(
                    "retired pre-tail writer accepted later publication"
                );
        }
    }

    private static void
        actualMovementPlayer81FailureRemainsRetryable()
            throws Exception
    {
        try(Fixture f=new Fixture()){
            int startX=f.movement.x();
            int startY=f.movement.y();

            String accepted=
                f.movement.accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{
                            startX+1
                        },
                        new int[]{
                            startY
                        },
                        new byte[0]
                    )
                );

            if(!accepted.startsWith("ACCEPTED")||
               f.movement.queued()!=1)
                throw new AssertionError(
                    "movement control route failed: "+
                    accepted
                );

            armMalformedPreparedBatch(
                f.writer
            );

            IOException tickFailure=null;

            try{
                f.coordinator.tick(
                    91L,
                    40_600L,
                    f.writer,
                    "[pre-tail-movement-control] "
                );
            }catch(IOException expected){
                tickFailure=expected;
            }

            if(tickFailure==null)
                throw new AssertionError(
                    "movement Player81 transform failure did not escape"
                );

            LocalSession
                .abortWorldTickBatchAfterTickFailure(
                    f.writer,
                    tickFailure
                );

            if(f.writer.terminal()||
               f.writer.batchActive()||
               f.queue.queuedBytes()!=0||
               f.movement.x()!=startX||
               f.movement.y()!=startY||
               f.movement.queued()!=1)
                throw new AssertionError(
                    "actual movement transform failure lost rollback/retry policy"
                );

            f.writer.fixed(
                134,
                BootstrapPackets.skill134(
                    PlayerState.HITPOINTS,
                    0,
                    1
                )
            );

            if(f.queue.queuedBytes()<=0)
                throw new AssertionError(
                    "movement-control writer was not reusable"
                );
        }
    }

    private static void armMalformedPreparedBatch(
        ServerPacketWriter writer
    )throws Exception{
        writer.beginBatch();

        setField(
            writer,
            "batchPlayer81Initialized",
            true
        );
        setField(
            writer,
            "batchPlayer81",
            malformedPrepared()
        );
    }

    private static Player81WorldSync.PreparedBatch
        malformedPrepared()
    {
        return new Player81WorldSync.PreparedBatch(
            null,
            new LinkedHashMap<>(),
            new HashMap<>()
        );
    }

    private static void setField(
        Object target,
        String name,
        Object value
    )throws Exception{
        Field field=
            target.getClass()
                .getDeclaredField(name);
        field.setAccessible(true);
        field.set(target,value);
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        queue.drainTo(
            out,
            QUEUE_CAPACITY
        );
    }

    private PreTailPlayerInteractionFailurePolicyTest(){}
}
