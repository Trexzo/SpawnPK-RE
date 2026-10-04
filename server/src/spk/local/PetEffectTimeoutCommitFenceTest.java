package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PetEffectTimeoutCommitFenceTest {
    private static final int QUEUE_CAPACITY=1<<20;

    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;
        @Override public String username(){return "pet-timeout";}
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
        @Override public String username(){return "pet-timeout";}
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
        @Override public long petFollowDeadline(){return Long.MAX_VALUE;}
        @Override public void setPetFollowDeadline(long value){}
        @Override public void ensurePetFollowScheduled(long now){}
        @Override public void ensurePetTestSequenceScheduled(long now){}
    }

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,"pet-timeout");

        try{
            MovementState movement=player.movement();
            PetState petState=player.petState();
            PetEffectState effects=player.petEffects();
            EquipmentState equipment=player.equipment();
            CombatStyleState combatStyles=player.combatStyles();
            DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);
            HomeWorldRuntimePlan home=new HomeWorldRuntimePlan();
            CombatEngine combat=new CombatEngine(dev);
            OutboundPacketQueue queue=new OutboundPacketQueue(QUEUE_CAPACITY);
            ServerPacketWriter writer=new ServerPacketWriter(
                queue,
                new IsaacCipher(new int[]{901,902,903,904})
            );
            SceneUpdatePublisher publisher=new SceneUpdatePublisher(
                writer,
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0
                )
            );

            npcs.bootstrapHome(writer,movement,petState,home);
            drain(queue);

            PetDefinitionRepository.Def definition=
                PetDefinitionRepository.get(24019);

            if(definition==null||
               !PetPresentationProfile.isChargePet(
                    definition.itemId,
                    definition.npcId))
                throw new AssertionError(
                    "charge-pet fixture missing"
                );

            String spawned=npcs.spawnPet(
                definition,
                movement,
                writer
            );

            if(spawned==null||
               !spawned.startsWith("PET_SPAWN_OK"))
                throw new AssertionError(
                    "charge pet spawn failed result="+spawned
                );

            petState.activate(definition);
            effects.onPetChanged(
                definition.itemId,
                definition.npcId
            );

            String nativeTwo=npcs.setPetNativeState(
                2,
                writer
            );

            if(nativeTwo==null||
               !nativeTwo.contains("state=2"))
                throw new AssertionError(
                    "native-state fixture failed result="+nativeTwo
                );

            drain(queue);

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
                    effects,
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
                    effects,
                    npcs,
                    movement
                );

            RegionBridge regionBridge=new RegionBridge();
            regionBridge.publisher=publisher;
            LocalRegionStreamHandler regionStreams=
                new LocalRegionStreamHandler(
                    false,
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
                    false,
                    world,
                    player,
                    movement,
                    equipment,
                    combatStyles,
                    effects,
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

            long armedAt=10_000L;
            long timeoutAt=
                armedAt+
                effects.resetMs()+
                1L;

            effects.forceCharge(2,armedAt);

            writer.beginBatch();
            coordinator.tick(
                1L,
                timeoutAt,
                writer,
                "[pet-timeout-abort] "
            );

            if(effects.charge()!=2||
               npcs.petNativeState()!=2||
               !coordinator.deferredPetEffectTimeoutEligible())
                throw new AssertionError(
                    "timeout reset committed inside provisional outer batch"
                );

            writer.abortBatch();
            coordinator.abortHomePresentationBatch();
            coordinator.abortGroundPresentationBatch();
            coordinator.abortDeferredPetEffectTimeoutAfterWorldTick();

            if(effects.charge()!=2||
               effects.lastDamageAtMs()!=armedAt||
               npcs.petNativeState()!=2)
                throw new AssertionError(
                    "outer abort changed timeout preimage"
                );

            writer.beginBatch();
            coordinator.tick(
                2L,
                timeoutAt,
                writer,
                "[pet-timeout-commit] "
            );
            LocalSession.endWorldTickBatch(writer);
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(timeoutAt);

            if(effects.charge()!=2||
               npcs.petNativeState()!=2)
                throw new AssertionError(
                    "outer commit settled timeout before post-commit hook"
                );

            coordinator.settleDeferredPetEffectTimeoutAfterWorldTick(
                writer,
                "[pet-timeout-commit] "
            );

            if(effects.charge()!=0||
               effects.accumulatedDamage()!=0||
               effects.lastDamageAtMs()!=0L||
               npcs.petNativeState()!=0)
                throw new AssertionError(
                    "post-commit timeout reset did not settle"
                );

            if(queue.queuedBytes()<=0)
                throw new AssertionError(
                    "post-commit timeout reset emitted no bytes"
                );

            drain(queue);

            effects.forceCharge(2,armedAt);
            npcs.setPetNativeState(2,writer);
            drain(queue);

            writer.beginBatch();
            coordinator.tick(
                3L,
                timeoutAt,
                writer,
                "[pet-timeout-failure] "
            );
            LocalSession.endWorldTickBatch(writer);
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(timeoutAt);
            drain(queue);

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    queue,
                    QUEUE_CAPACITY
                );

            boolean failed=false;
            try{
                coordinator.settleDeferredPetEffectTimeoutAfterWorldTick(
                    writer,
                    "[pet-timeout-failure] "
                );
            }catch(IOException expected){
                failed=true;
            }finally{
                pressure.release();
            }

            if(!failed)
                throw new AssertionError(
                    "forced timeout reset publication failure did not escape"
                );

            if(writer.terminal()||
               effects.charge()!=2||
               effects.lastDamageAtMs()!=armedAt||
               npcs.petNativeState()!=2||
               !coordinator.deferredPetEffectTimeoutEligible())
                throw new AssertionError(
                    "failed standalone timeout reset changed retryable state"
                );

            coordinator.settleDeferredPetEffectTimeoutAfterWorldTick(
                writer,
                "[pet-timeout-retry] "
            );

            if(effects.charge()!=0||
               npcs.petNativeState()!=0||
               coordinator.deferredPetEffectTimeoutEligible())
                throw new AssertionError(
                    "same-writer timeout reset retry did not commit"
                );

            System.out.println(
                "PET_EFFECT_TIMEOUT_COMMIT_FENCE_PASS "+
                "outerAbortPreservesCharge=true "+
                "retryResetCommitsOnce=true "+
                "resetFailureRetainsCharge=true "+
                "nativeStateSettlesAfterBytes=true"
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

    private PetEffectTimeoutCommitFenceTest(){}
}
