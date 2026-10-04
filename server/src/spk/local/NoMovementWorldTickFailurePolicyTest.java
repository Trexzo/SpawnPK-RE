package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class NoMovementWorldTickFailurePolicyTest {
    private static final int QUEUE_CAPACITY=1<<20;

    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;
        @Override public String username(){return "no-move-fence";}
        @Override public boolean persistentAccount(){return false;}
        @Override public long sessionWorldTick(){return 1L;}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
        @Override public void saveAccount(String tag,String reason){}
        @Override public int syncScopesightPassive(ServerPacketWriter writer){return 0;}
        @Override public void resetPetFollowDeadline(){}
        @Override public void ensurePetFollowScheduled(long now){}
    }

    private static final class RegionBridge
        implements LocalRegionStreamHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;
        @Override public String username(){return "no-move-fence";}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
        @Override public void replaceScenePublisher(SceneUpdatePublisher replacement){
            publisher=replacement;
        }
        @Override public void resetPetFollowRuntime(){}
    }

    private static final class TickBridge
        implements LocalWorldTickCoordinator.SessionBridge
    {
        SceneUpdatePublisher publisher;
        int saveCalls;
        int followScheduleCalls;
        int testScheduleCalls;
        boolean failTestSchedule;
        long petDeadline=Long.MAX_VALUE;

        @Override public Player81WorldSync.Context player81Sync(){return null;}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
        @Override public void saveAccount(String tag,String reason){saveCalls++;}
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
        @Override public long petFollowDeadline(){return petDeadline;}
        @Override public void setPetFollowDeadline(long value){petDeadline=value;}
        @Override public void ensurePetFollowScheduled(long now){followScheduleCalls++;}
        @Override public void ensurePetTestSequenceScheduled(long now){
            testScheduleCalls++;
            if(failTestSchedule)
                throw new IllegalStateException(
                    "injected late no-movement tail failure"
                );
        }
    }

    private static final class Fixture implements AutoCloseable {
        final World world=World.isolatedForTest(60_000L);
        final WorldPlayer player=new WorldPlayer();
        final long generation;
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
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{991,992,993,994}
                )
            );
        final SceneUpdatePublisher publisher;
        final LocalPlayerInteractionHandler playerInteractions;
        final LocalWorldTickCoordinator coordinator;
        final TickBridge tickBridge=new TickBridge();

        Fixture(boolean movementEnabled)throws Exception{
            generation=
                world.registerPlayer(
                    player,
                    "no-move-fence"
                );
            movement=player.movement();
            equipment=player.equipment();
            petEffects=player.petEffects();
            petState=player.petState();
            combatStyles=player.combatStyles();

            npcs.bootstrapHome(
                writer,
                movement,
                petState,
                home
            );
            drain(queue);

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
                    player,
                    movement,
                    equipment
                );

            LocalBankObjectInteractionHandler bankObjects=
                new LocalBankObjectInteractionHandler(
                    player.bank(),
                    movement
                );

            LocalRoutedNpcInteractionHandler routedNpcs=
                new LocalRoutedNpcInteractionHandler(
                    npcs,
                    player.bank(),
                    movement,
                    null,
                    player,
                    equipment
                );

            LocalGroundItemInteractionHandler groundItems=
                new LocalGroundItemInteractionHandler(
                    world,
                    player.bank(),
                    movement
                );

            PetBridge petBridge=new PetBridge();
            petBridge.publisher=publisher;

            LocalPetDropPickupHandler petDropPickup=
                new LocalPetDropPickupHandler(
                    world,
                    player.bank(),
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
                    movementEnabled,
                    world,
                    player,
                    movement,
                    home,
                    npcs,
                    playerInteractions,
                    combat,
                    regionBridge
                );

            tickBridge.publisher=publisher;

            coordinator=
                new LocalWorldTickCoordinator(
                    movementEnabled,
                    world,
                    player,
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

        @Override public void close(){
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        noMovementTailFailureRetires();
        tickBodyFailureAbortsAndRetires();
        transientTailBodyFailureRetires();
        transientTailAdmissionFailureRetires();
        transientMovementSourceFailureRemainsRetryable();
        movementSourceFailureRemainsRetryable();

        System.out.println(
            "NO_MOVEMENT_WORLD_TICK_FAILURE_POLICY_PASS "+
            "semanticTailExecuted=true "+
            "admissionFailureRetiresWriter=true "+
            "tickBodyFailed=true "+
            "outerBatchAborted=true "+
            "partialBatchNotCommitted=true "+
            "noMovementTailFailureRetiresWriter=true "+
            "transientTailBodyFailureRetiresWriter=true "+
            "transientTailAdmissionFailureRetiresWriter=true "+
            "transientMovementPolicyUnaffected=true "+
            "movementRollbackPolicyUnaffected=true "+
            "laterPublicationRejected=true"
        );
    }

    private static void noMovementTailFailureRetires()
        throws Exception{
        try(Fixture f=new Fixture(false)){
            SceneCoordinateContext.Snapshot sceneBefore=
                f.publisher.context().snapshot();

            f.writer.beginBatch();

            f.coordinator.tick(
                80L,
                30_000L,
                f.writer,
                "[no-move-tail-failure] "
            );

            if(!f.coordinator
                    .noMovementSemanticTailCompleted()||
               f.coordinator.deferredMovementEligible()||
               f.coordinator.legacyTickCount()!=1L||
               f.coordinator.movementTickCount()!=0L||
               f.tickBridge.followScheduleCalls!=1||
               f.tickBridge.testScheduleCalls!=1)
                throw new AssertionError(
                    "no-movement semantic tail did not complete under provisional batch"
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    f.queue,
                    QUEUE_CAPACITY
                );

            boolean failed=false;
            try{
                try{
                    LocalSession.endWorldTickBatch(
                        f.writer
                    );
                }catch(IOException expected){
                    failed=true;
                }

                if(!failed)
                    throw new AssertionError(
                        "forced no-movement final admission failure did not escape"
                    );

                f.coordinator
                    .abortDeferredMovementAfterWorldTick();
                f.coordinator
                    .abortRegionStreamBatch();
                f.publisher.context().restore(
                    sceneBefore
                );
                f.coordinator
                    .abortHomePresentationBatch();
                f.coordinator
                    .abortGroundPresentationBatch();
                f.coordinator
                    .abortDeferredRespawnAfterWorldTick();
                f.coordinator
                    .abortDeferredBankInteractionsAfterWorldTick();
                f.coordinator
                    .abortDeferredMakeoverInteractionsAfterWorldTick();
                f.coordinator
                    .abortDeferredGroundTakeAfterWorldTick();
                f.coordinator
                    .abortDeferredPetPickupAfterWorldTick();
                f.coordinator
                    .abortDeferredPetEffectTimeoutAfterWorldTick();
                f.coordinator
                    .abortDeferredPetChargeIncrementAfterWorldTick(
                        f.writer
                    );

                if(!f.coordinator
                        .retireAfterFailedNoMovementSemanticTail(
                            f.writer
                        ))
                    throw new AssertionError(
                        "completed no-movement tail did not request retirement"
                    );
            }finally{
                pressure.release();
            }

            if(!f.writer.terminal()||
               f.coordinator
                    .noMovementSemanticTailCompleted()||
               f.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "failed no-movement semantic tick remained live"
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
                    "terminal no-movement writer accepted later publication"
                );
        }
    }

    private static void tickBodyFailureAbortsAndRetires()
        throws Exception{
        try(Fixture f=new Fixture(false)){
            f.tickBridge.failTestSchedule=true;

            SceneCoordinateContext.Snapshot sceneBefore=
                f.publisher.context().snapshot();

            f.writer.beginBatch();

            IllegalStateException bodyFailure=null;
            try{
                f.coordinator.tick(
                    82L,
                    31_200L,
                    f.writer,
                    "[no-move-body-failure] "
                );
            }catch(IllegalStateException expected){
                bodyFailure=expected;
                LocalSession
                    .abortWorldTickBatchAfterTickFailure(
                        f.writer,
                        expected
                    );
            }

            if(bodyFailure==null)
                throw new AssertionError(
                    "late no-movement tick-body failure did not escape"
                );

            if(!f.coordinator
                    .noMovementSemanticTailEntered()||
               f.coordinator
                    .noMovementSemanticTailCompleted()||
               f.tickBridge.followScheduleCalls!=1||
               f.tickBridge.testScheduleCalls!=1)
                throw new AssertionError(
                    "tick-body failure did not occur after semantic-tail entry"
                );

            f.coordinator
                .abortDeferredMovementAfterWorldTick();
            f.coordinator
                .abortRegionStreamBatch();
            f.publisher.context().restore(
                sceneBefore
            );
            f.coordinator
                .abortHomePresentationBatch();
            f.coordinator
                .abortGroundPresentationBatch();
            f.coordinator
                .abortDeferredRespawnAfterWorldTick();
            f.coordinator
                .abortDeferredBankInteractionsAfterWorldTick();
            f.coordinator
                .abortDeferredMakeoverInteractionsAfterWorldTick();
            f.coordinator
                .abortDeferredGroundTakeAfterWorldTick();
            f.coordinator
                .abortDeferredPetPickupAfterWorldTick();
            f.coordinator
                .abortDeferredPetEffectTimeoutAfterWorldTick();
            f.coordinator
                .abortDeferredPetChargeIncrementAfterWorldTick(
                    f.writer
                );

            if(!f.coordinator
                    .retireAfterFailedNoMovementSemanticTail(
                        f.writer
                    ))
                throw new AssertionError(
                    "entered failed no-movement tail did not request retirement"
                );

            if(!f.writer.terminal()||
               f.coordinator
                    .noMovementSemanticTailEntered()||
               f.coordinator
                    .noMovementSemanticTailCompleted()||
               f.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "failed tick body committed partial batch or remained live"
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
                    "failed tick-body writer accepted later publication"
                );
        }
    }

    private static void transientTailBodyFailureRetires()
        throws Exception{
        try(Fixture f=new Fixture(false)){
            f.movement.enterTransientRegion(
                MovementState.INITIAL_X,
                MovementState.INITIAL_Y,
                0,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );
            f.tickBridge.failTestSchedule=true;

            SceneCoordinateContext.Snapshot sceneBefore=
                f.publisher.context().snapshot();

            f.writer.beginBatch();

            IllegalStateException bodyFailure=null;
            try{
                f.coordinator.tick(
                    83L,
                    31_800L,
                    f.writer,
                    "[transient-no-move-body-failure] "
                );
            }catch(IllegalStateException expected){
                bodyFailure=expected;
                LocalSession
                    .abortWorldTickBatchAfterTickFailure(
                        f.writer,
                        expected
                    );
            }

            if(bodyFailure==null||
               !f.coordinator
                    .noMovementSemanticTailEntered()||
               f.coordinator
                    .noMovementSemanticTailCompleted()||
               f.tickBridge.followScheduleCalls!=1||
               f.tickBridge.testScheduleCalls!=1)
                throw new AssertionError(
                    "transient tick-body failure did not occur after semantic-tail entry"
                );

            cleanupFailedTick(
                f,
                sceneBefore
            );

            if(!f.coordinator
                    .retireAfterFailedNoMovementSemanticTail(
                        f.writer
                    ))
                throw new AssertionError(
                    "failed transient no-movement tail did not request retirement"
                );

            if(!f.writer.terminal()||
               f.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "failed transient no-movement body remained live"
                );
        }
    }

    private static void transientTailAdmissionFailureRetires()
        throws Exception{
        try(Fixture f=new Fixture(false)){
            f.movement.enterTransientRegion(
                MovementState.INITIAL_X,
                MovementState.INITIAL_Y,
                0,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

            SceneCoordinateContext.Snapshot sceneBefore=
                f.publisher.context().snapshot();

            f.writer.beginBatch();

            f.coordinator.tick(
                84L,
                32_400L,
                f.writer,
                "[transient-no-move-admission-failure] "
            );

            if(!f.coordinator
                    .noMovementSemanticTailCompleted()||
               f.coordinator.deferredMovementEligible()||
               f.coordinator.movementTickCount()!=0L||
               f.tickBridge.followScheduleCalls!=1||
               f.tickBridge.testScheduleCalls!=1)
                throw new AssertionError(
                    "transient no-movement semantic tail did not complete"
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    f.queue,
                    QUEUE_CAPACITY
                );

            try{
                boolean failed=false;
                try{
                    LocalSession.endWorldTickBatch(
                        f.writer
                    );
                }catch(IOException expected){
                    failed=true;
                }

                if(!failed)
                    throw new AssertionError(
                        "transient no-movement final admission failure did not escape"
                    );

                cleanupFailedTick(
                    f,
                    sceneBefore
                );

                if(!f.coordinator
                        .retireAfterFailedNoMovementSemanticTail(
                            f.writer
                        ))
                    throw new AssertionError(
                        "completed transient no-movement tail did not request retirement"
                    );
            }finally{
                pressure.release();
            }

            if(!f.writer.terminal()||
               f.coordinator
                    .noMovementSemanticTailCompleted()||
               f.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "failed transient no-movement admission remained live"
                );
        }
    }

    private static void cleanupFailedTick(
        Fixture f,
        SceneCoordinateContext.Snapshot sceneBefore
    ){
        f.coordinator
            .abortDeferredMovementAfterWorldTick();
        f.coordinator
            .abortRegionStreamBatch();
        f.publisher.context().restore(
            sceneBefore
        );
        f.coordinator
            .abortHomePresentationBatch();
        f.coordinator
            .abortGroundPresentationBatch();
        f.coordinator
            .abortDeferredRespawnAfterWorldTick();
        f.coordinator
            .abortDeferredBankInteractionsAfterWorldTick();
        f.coordinator
            .abortDeferredMakeoverInteractionsAfterWorldTick();
        f.coordinator
            .abortDeferredGroundTakeAfterWorldTick();
        f.coordinator
            .abortDeferredPetPickupAfterWorldTick();
        f.coordinator
            .abortDeferredPetEffectTimeoutAfterWorldTick();
        f.coordinator
            .abortDeferredPetChargeIncrementAfterWorldTick(
                f.writer
            );
    }

    private static void transientMovementSourceFailureRemainsRetryable()
        throws Exception{
        try(Fixture f=new Fixture(true)){
            f.movement.enterTransientRegion(
                MovementState.INITIAL_X,
                MovementState.INITIAL_Y,
                0,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

            int startX=f.movement.x();
            int startY=f.movement.y();

            String accepted=
                f.movement.accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{startX+1},
                        new int[]{startY},
                        new byte[0]
                    )
                );

            if(!accepted.startsWith("ACCEPTED")||
               f.movement.queued()!=1)
                throw new AssertionError(
                    "transient movement-policy fixture rejected: "+
                    accepted
                );

            f.writer.beginBatch();

            f.coordinator.tick(
                85L,
                33_000L,
                f.writer,
                "[transient-movement-policy-control] "
            );

            if(!f.coordinator.deferredMovementEligible()||
               f.coordinator
                    .noMovementSemanticTailEntered()||
               f.coordinator
                    .noMovementSemanticTailCompleted()||
               f.movement.x()!=startX+1||
               f.movement.queued()!=0)
                throw new AssertionError(
                    "transient movement source unexpectedly entered no-movement policy"
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    f.queue,
                    QUEUE_CAPACITY
                );

            try{
                boolean failed=false;
                try{
                    LocalSession.endWorldTickBatch(
                        f.writer
                    );
                }catch(IOException expected){
                    failed=true;
                }

                if(!failed)
                    throw new AssertionError(
                        "forced transient movement source admission failure did not escape"
                    );

                if(!f.coordinator
                        .abortDeferredMovementAfterWorldTick())
                    throw new AssertionError(
                        "transient movement rollback token missing"
                    );

                if(f.coordinator
                        .retireAfterFailedNoMovementSemanticTail(
                            f.writer
                        ))
                    throw new AssertionError(
                        "transient movement-source failure invoked no-movement retirement"
                    );
            }finally{
                pressure.release();
            }

            if(f.writer.terminal()||
               f.movement.x()!=startX||
               f.movement.y()!=startY||
               f.movement.queued()!=1||
               f.coordinator.deferredMovementEligible())
                throw new AssertionError(
                    "transient movement source rollback/retry policy changed"
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
                    "transient movement-source writer was not reusable"
                );
        }
    }

    private static void movementSourceFailureRemainsRetryable()
        throws Exception{
        try(Fixture f=new Fixture(true)){
            int startX=f.movement.x();
            int startY=f.movement.y();

            String accepted=
                f.movement.accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{startX+1},
                        new int[]{startY},
                        new byte[0]
                    )
                );

            if(!accepted.startsWith("ACCEPTED")||
               f.movement.queued()!=1)
                throw new AssertionError(
                    "movement-policy fixture rejected: "+
                    accepted
                );

            f.writer.beginBatch();

            f.coordinator.tick(
                81L,
                30_600L,
                f.writer,
                "[movement-policy-control] "
            );

            if(!f.coordinator.deferredMovementEligible()||
               f.coordinator
                    .noMovementSemanticTailCompleted()||
               f.movement.x()!=startX+1||
               f.movement.queued()!=0)
                throw new AssertionError(
                    "movement source unexpectedly entered semantic-tail policy"
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    f.queue,
                    QUEUE_CAPACITY
                );

            boolean failed=false;
            try{
                try{
                    LocalSession.endWorldTickBatch(
                        f.writer
                    );
                }catch(IOException expected){
                    failed=true;
                }

                if(!failed)
                    throw new AssertionError(
                        "forced movement source admission failure did not escape"
                    );

                if(!f.coordinator
                        .abortDeferredMovementAfterWorldTick())
                    throw new AssertionError(
                        "movement rollback token missing"
                    );

                if(f.coordinator
                        .retireAfterFailedNoMovementSemanticTail(
                            f.writer
                        ))
                    throw new AssertionError(
                        "movement-source failure invoked no-movement retirement"
                    );
            }finally{
                pressure.release();
            }

            if(f.writer.terminal()||
               f.movement.x()!=startX||
               f.movement.y()!=startY||
               f.movement.queued()!=1||
               f.coordinator.deferredMovementEligible())
                throw new AssertionError(
                    "movement source rollback/retry policy changed"
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
                    "movement-source writer was not reusable"
                );
        }
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

    private NoMovementWorldTickFailurePolicyTest(){}
}
