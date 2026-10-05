package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class RegionStreamTickAccountingCommitFenceTest {
    private static final int QUEUE_CAPACITY=1<<20;

    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;

        @Override public String username(){return "region-clock";}
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

        @Override public String username(){return "region-clock";}
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
        long petDeadline=Long.MAX_VALUE;

        @Override public Player81WorldSync.Context player81Sync(){return null;}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
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
        @Override public long petFollowDeadline(){return petDeadline;}
        @Override public void setPetFollowDeadline(long value){petDeadline=value;}
        @Override public void ensurePetFollowScheduled(long now){}
        @Override public void ensurePetTestSequenceScheduled(long now){}
    }

    private static final class Fixture implements AutoCloseable {
        final World world=World.isolatedForTest(60_000L);
        final WorldPlayer player=new WorldPlayer();
        final long generation;

        final MovementState movement;
        final EquipmentState equipment;
        final PetState petState;
        final PetEffectState petEffects;
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
                    new int[]{1101,1102,1103,1104}
                )
            );

        final SceneUpdatePublisher publisher;
        final LocalWorldTickCoordinator coordinator;

        Fixture()throws Exception{
            generation=
                world.registerPlayer(
                    player,
                    "region-clock"
                );

            movement=player.movement();
            equipment=player.equipment();
            petState=player.petState();
            petEffects=player.petEffects();
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

            coordinator=
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

            movement.enterTransientRegion(
                4064,
                4192,
                0,
                4064,
                4192
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
        failedRegionAdmissionPreservesClock();
        unbatchedCompatibilityAdvancesImmediately();

        System.out.println(
            "REGION_STREAM_TICK_ACCOUNTING_COMMIT_FENCE_PASS "+
            "regionTickAbortPreservesCombatClock=true "+
            "regionTickRetryCommitsOnce=true "+
            "combatTickUnaffectedByFailedRegionAdmission=true "+
            "unbatchedCompatibility=true"
        );
    }

    private static void failedRegionAdmissionPreservesClock()
        throws Exception
    {
        try(Fixture f=new Fixture()){
            int oldBaseX=f.movement.loadedBaseX();
            int oldBaseY=f.movement.loadedBaseY();

            f.writer.beginBatch();

            f.coordinator.tick(
                100L,
                50_000L,
                f.writer,
                "[region-clock-abort] "
            );

            if(f.coordinator.legacyTickCount()!=0L||
               !f.coordinator.deferredRegionTickAccounting()||
               !f.coordinator.regionStreamBatchStaged())
                throw new AssertionError(
                    "region tick accounting committed before source transport"
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
            }finally{
                pressure.release();
            }

            if(!failed)
                throw new AssertionError(
                    "forced region source admission failure did not escape"
                );

            if(!f.coordinator.abortRegionStreamBatch())
                throw new AssertionError(
                    "failed region transaction had no rollback snapshot"
                );

            if(f.coordinator.legacyTickCount()!=0L||
               f.coordinator.deferredRegionTickAccounting()||
               f.movement.loadedBaseX()!=oldBaseX||
               f.movement.loadedBaseY()!=oldBaseY||
               f.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "failed region admission advanced combat clock or leaked state"
                );

            f.writer.beginBatch();

            f.coordinator.tick(
                101L,
                50_600L,
                f.writer,
                "[region-clock-retry] "
            );

            if(f.coordinator.legacyTickCount()!=0L||
               !f.coordinator.deferredRegionTickAccounting())
                throw new AssertionError(
                    "region retry committed clock before transport"
                );

            LocalSession.endWorldTickBatch(
                f.writer
            );

            if(!f.coordinator.commitRegionStreamBatch())
                throw new AssertionError(
                    "successful region retry did not commit staged transaction"
                );

            if(f.coordinator.legacyTickCount()!=1L||
               f.coordinator.deferredRegionTickAccounting())
                throw new AssertionError(
                    "successful region retry did not commit combat clock exactly once"
                );

            if(f.queue.queuedBytes()<=0)
                throw new AssertionError(
                    "successful region retry emitted no source bytes"
                );
        }
    }

    private static void unbatchedCompatibilityAdvancesImmediately()
        throws Exception
    {
        try(Fixture f=new Fixture()){
            f.coordinator.tick(
                110L,
                60_000L,
                f.writer,
                "[region-clock-unbatched] "
            );

            if(f.coordinator.legacyTickCount()!=1L||
               f.coordinator.deferredRegionTickAccounting()||
               f.coordinator.regionStreamBatchStaged())
                throw new AssertionError(
                    "unbatched region accounting compatibility changed"
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

    private RegionStreamTickAccountingCommitFenceTest(){}
}
