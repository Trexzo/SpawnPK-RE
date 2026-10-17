package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PetEffectTimeoutFailureAtomicityTest {
    private static final int QUEUE_CAPACITY=1<<20;

    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;
        @Override public String username(){return "pet-timeout-failure";}
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
        @Override public String username(){return "pet-timeout-failure";}
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
        long generation=world.registerPlayer(
            player,
            "pet-timeout-failure"
        );

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

            OutboundPacketQueue queue=
                new OutboundPacketQueue(
                    QUEUE_CAPACITY
                );
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(
                        new int[]{951,952,953,954}
                    )
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

            npcs.bootstrapHome(
                writer,
                movement,
                petState,
                home
            );
            drain(queue);

            PetDefinitionRepository.Def def=
                PetDefinitionRepository.get(
                    24019
                );

            if(def==null||
               !PetPresentationProfile.isChargePet(
                    def.itemId,
                    def.npcId))
                throw new AssertionError(
                    "charge-pet fixture missing"
                );

            String spawn=npcs.spawnPet(
                def,
                movement,
                writer
            );
            if(spawn==null||
               !spawn.startsWith(
                   "PET_SPAWN_OK"))
                throw new AssertionError(
                    "charge-pet spawn failed result="+
                    spawn
                );

            petState.activate(def);
            effects.onPetChanged(
                def.itemId,
                def.npcId
            );
            effects.forceCharge(
                2,
                10_000L
            );

            String nativeTwo=
                npcs.setPetNativeState(
                    2,
                    writer
                );

            if(nativeTwo==null||
               !nativeTwo.contains(
                   "state=2"))
                throw new AssertionError(
                    "native-state fixture failed result="+
                    nativeTwo
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

            long timeoutAt=
                10_000L+
                effects.resetMs()+
                1L;

            writer.beginBatch();
            coordinator.tick(
                50L,
                timeoutAt,
                writer,
                "[pet-timeout-failure] "
            );
            LocalSession.endWorldTickBatch(
                writer
            );
            coordinator.commitRegionStreamBatch();
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                timeoutAt
            );

            if(effects.charge()!=2||
               npcs.petNativeState()!=2||
               !coordinator
                    .deferredPetEffectTimeoutEligible())
                throw new AssertionError(
                    "timeout was not preserved until post-commit settlement"
                );

            drain(queue);

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    queue,
                    QUEUE_CAPACITY
                );

            boolean failed=false;
            try{
                coordinator
                    .settleDeferredPetEffectTimeoutAfterWorldTick(
                        writer,
                        "[pet-timeout-failure] ",
                        50L
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
               effects.lastDamageAtMs()!=10_000L||
               npcs.petNativeState()!=2)
                throw new AssertionError(
                    "failed timeout reset changed semantic preimage"
                );

            /*
             * #1805 intentionally drops only the failed prepared token. The
             * unchanged exact charge/native preimage must let the next world
             * tick re-prepare the same timeout on this same reusable writer.
             */
            writer.beginBatch();
            coordinator.tick(
                51L,
                timeoutAt+600L,
                writer,
                "[pet-timeout-retry] "
            );
            LocalSession.endWorldTickBatch(
                writer
            );
            coordinator.commitRegionStreamBatch();
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                timeoutAt+600L
            );

            if(effects.charge()!=2||
               npcs.petNativeState()!=2||
               !coordinator
                    .deferredPetEffectTimeoutEligible())
                throw new AssertionError(
                    "failed timeout reset was not retryable on same writer"
                );

            coordinator
                .settleDeferredPetEffectTimeoutAfterWorldTick(
                    writer,
                    "[pet-timeout-retry] ",
                    51L
                );

            if(effects.charge()!=0||
               effects.accumulatedDamage()!=0||
               effects.lastDamageAtMs()!=0L||
               npcs.petNativeState()!=0||
               coordinator
                    .deferredPetEffectTimeoutEligible())
                throw new AssertionError(
                    "same-writer timeout reset retry did not settle"
                );

            if(queue.queuedBytes()<=0)
                throw new AssertionError(
                    "timeout retry committed no reset bytes"
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

    private PetEffectTimeoutFailureAtomicityTest(){}
}
