package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class MovementWorldTickCommitFenceTest {
    private static final int QUEUE_CAPACITY=1<<20;

    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;
        @Override public String username(){return "movement-fence";}
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
        @Override public String username(){return "movement-fence";}
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
        String lastSaveReason;
        int saveCalls;
        int followScheduleCalls;
        int testScheduleCalls;
        long petDeadline=Long.MAX_VALUE;

        @Override public Player81WorldSync.Context player81Sync(){return null;}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
        @Override public void saveAccount(String tag,String reason){
            lastSaveReason=reason;
            saveCalls++;
        }
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
        @Override public void ensurePetTestSequenceScheduled(long now){testScheduleCalls++;}
    }

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(
            player,
            "movement-fence"
        );

        try{
            MovementState movement=player.movement();
            EquipmentState equipment=player.equipment();
            CombatStyleState combatStyles=player.combatStyles();
            PetEffectState petEffects=player.petEffects();
            PetState petState=player.petState();
            DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);
            HomeWorldRuntimePlan home=new HomeWorldRuntimePlan();
            CombatEngine combat=new CombatEngine(dev);

            OutboundPacketQueue queue=
                new OutboundPacketQueue(
                    QUEUE_CAPACITY
                );
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(
                        new int[]{971,972,973,974}
                    )
                );

            npcs.bootstrapHome(
                writer,
                movement,
                petState,
                home
            );
            drain(queue);

            SceneUpdatePublisher publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalPlayerInteractionHandler playerInteractions=
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
                    true,
                    world,
                    player,
                    movement,
                    home,
                    npcs,
                    playerInteractions,
                    combat,
                    regionBridge
                );

            TickBridge tickBridge=new TickBridge();
            tickBridge.publisher=publisher;

            LocalWorldTickCoordinator coordinator=
                new LocalWorldTickCoordinator(
                    true,
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

            int startX=movement.x();
            int startY=movement.y();
            int startPlane=movement.plane();

            String accepted=
                movement.accept(
                    new MovementRequest(
                        164,
                        true,
                        new int[]{startX+2},
                        new int[]{startY},
                        new byte[0]
                    )
                );

            if(!accepted.startsWith("ACCEPTED")||
               movement.queued()!=2)
                throw new AssertionError(
                    "movement fence route fixture failed: "+
                    accepted
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    queue,
                    QUEUE_CAPACITY
                );

            boolean failed=false;
            try{
                writer.beginBatch();

                coordinator.tick(
                    70L,
                    20_000L,
                    writer,
                    "[movement-fence-abort] "
                );

                if(!coordinator.deferredMovementEligible()||
                   movement.x()!=startX+2||
                   movement.y()!=startY||
                   movement.queued()!=0||
                   tickBridge.saveCalls!=0||
                   coordinator.legacyTickCount()!=0L||
                   coordinator.movementTickCount()!=0L||
                   tickBridge.followScheduleCalls!=0||
                   tickBridge.testScheduleCalls!=0||
                   coordinator.deferredBankInteractionEligible()||
                   coordinator.deferredGroundTakeEligible()||
                   coordinator.deferredPetPickupEligible())
                    throw new AssertionError(
                        "movement tail ran before packet commit"
                    );

                try{
                    LocalSession.endWorldTickBatch(
                        writer
                    );
                }catch(IOException expected){
                    failed=true;
                }

                if(!failed)
                    throw new AssertionError(
                        "forced movement batch admission failure did not escape"
                    );

                if(!coordinator.abortDeferredMovementAfterWorldTick())
                    throw new AssertionError(
                        "movement rollback token missing"
                    );
            }finally{
                pressure.release();
            }

            if(writer.terminal()||
               queue.queuedBytes()!=0||
               movement.x()!=startX||
               movement.y()!=startY||
               movement.plane()!=startPlane||
               movement.queued()!=2||
               coordinator.deferredMovementEligible()||
               tickBridge.saveCalls!=0||
               tickBridge.lastSaveReason!=null||
               coordinator.legacyTickCount()!=0L||
               coordinator.movementTickCount()!=0L||
               tickBridge.followScheduleCalls!=0||
               tickBridge.testScheduleCalls!=0)
                throw new AssertionError(
                    "aborted movement did not restore exact preimage"
                );

            writer.beginBatch();

            coordinator.tick(
                71L,
                20_600L,
                writer,
                "[movement-fence-retry] "
            );

            if(!coordinator.deferredMovementEligible()||
               movement.x()!=startX+2||
               movement.y()!=startY||
               movement.queued()!=0||
               tickBridge.saveCalls!=0||
               coordinator.legacyTickCount()!=0L||
               coordinator.movementTickCount()!=0L)
                throw new AssertionError(
                    "retry movement was not staged exactly once"
                );

            LocalSession.endWorldTickBatch(
                writer
            );

            coordinator.commitRegionStreamBatch();
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                20_600L
            );

            coordinator.settleDeferredMovementAfterWorldTick(
                writer,
                "[movement-fence-retry] "
            );

            if(coordinator.deferredMovementEligible()||
               movement.x()!=startX+2||
               movement.y()!=startY||
               movement.plane()!=startPlane||
               movement.queued()!=0||
               coordinator.movementTickCount()!=1L||
               coordinator.legacyTickCount()!=1L||
               tickBridge.saveCalls!=1||
               !"POSITION_TICK".equals(
                    tickBridge.lastSaveReason)||
               tickBridge.followScheduleCalls!=1||
               tickBridge.testScheduleCalls!=1||
               queue.queuedBytes()<=0)
                throw new AssertionError(
                    "same-writer movement retry did not commit once"
                );

            drain(queue);

            int tailStartX=movement.x();
            int tailStartY=movement.y();

            String tailAccepted=
                movement.accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{tailStartX+1},
                        new int[]{tailStartY},
                        new byte[0]
                    )
                );

            if(!tailAccepted.startsWith("ACCEPTED")||
               movement.queued()!=1)
                throw new AssertionError(
                    "tail-failure route fixture failed: "+
                    tailAccepted
                );

            writer.beginBatch();

            coordinator.tick(
                72L,
                21_200L,
                writer,
                "[movement-tail-terminal] "
            );

            if(!coordinator.deferredMovementEligible()||
               movement.x()!=tailStartX+1||
               movement.y()!=tailStartY)
                throw new AssertionError(
                    "tail-failure movement was not staged"
                );

            LocalSession.endWorldTickBatch(
                writer
            );

            int committedMovementBytes=
                queue.queuedBytes();

            if(committedMovementBytes<=0)
                throw new AssertionError(
                    "movement transport did not commit before tail pressure"
                );

            OutboundPacketQueue.BatchReservation tailPressure=
                OutboundPacketQueue.reserveBatch(
                    queue,
                    QUEUE_CAPACITY-
                        committedMovementBytes
                );

            boolean tailFailed=false;
            try{
                try{
                    coordinator
                        .settleDeferredMovementAfterWorldTick(
                            writer,
                            "[movement-tail-terminal] "
                        );
                }catch(IOException expected){
                    tailFailed=true;
                }

                if(!tailFailed)
                    throw new AssertionError(
                        "forced post-movement tail admission failure did not escape"
                    );
            }finally{
                tailPressure.release();
            }

            if(!writer.terminal()||
               coordinator.deferredMovementEligible()||
               movement.x()!=tailStartX+1||
               movement.y()!=tailStartY||
               queue.queuedBytes()!=
                   committedMovementBytes)
                throw new AssertionError(
                    "tail failure did not retire writer while preserving committed movement"
                );

            boolean terminalRejected=false;
            try{
                writer.fixed(
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
                    "retired writer accepted post-failure publication"
                );

            System.out.println(
                "MOVEMENT_WORLD_TICK_COMMIT_FENCE_PASS "+
                "abortRestoresPosition=true "+
                "abortRestoresPath=true "+
                "abortSuppressesSave=true "+
                "abortSuppressesTail=true "+
                "sameWriterRetryCommitsOnce=true "+
                "runTwoStepPreserved=true "+
                "movementTransportCommitted=true "+
                "tailAdmissionFailureRetiresWriter=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );
            world.close();
        }
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        queue.drainTo(
            out,
            1<<20
        );
    }

    private MovementWorldTickCommitFenceTest(){}
}
