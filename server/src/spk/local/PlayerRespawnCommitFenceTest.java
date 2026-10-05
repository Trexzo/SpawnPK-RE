package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PlayerRespawnCommitFenceTest {
    private static final int QUEUE_CAPACITY=1<<20;

    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;
        @Override public String username(){return "respawn-fence";}
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
        int resetCalls;

        @Override public String username(){return "respawn-fence";}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
        @Override public void replaceScenePublisher(SceneUpdatePublisher replacement){
            publisher=replacement;
        }
        @Override public void resetPetFollowRuntime(){resetCalls++;}
    }

    private static final class TickBridge
        implements LocalWorldTickCoordinator.SessionBridge
    {
        SceneUpdatePublisher publisher;
        String lastSaveReason;
        int saveCalls;

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
            "respawn-fence"
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
                        new int[]{961,962,963,964}
                    )
                );

            npcs.bootstrapHome(
                writer,
                movement,
                petState,
                home
            );
            drain(queue);

            int transientX=3200;
            int transientY=3200;
            int transientBaseX=3150;
            int transientBaseY=3150;

            movement.enterTransientRegion(
                transientX,
                transientY,
                0,
                transientBaseX,
                transientBaseY
            );

            npcs.detachRegionViewPreservingFollowers(
                writer
            );
            drain(queue);

            SceneUpdatePublisher publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        transientBaseX,
                        transientBaseY,
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

            if(DeathPolicyRepository.get(995).kind!=
                    DeathPolicyRepository.Kind.STANDARD_UNRESOLVED)
                throw new AssertionError(
                    "coin fixture must exercise LocalLab standard-loss policy"
                );

            BankState.PreparedInventoryMutation deathCoins=
                player.bank().prepareAddInventoryAmount(
                    995,
                    10
                );
            if(!deathCoins.accepted())
                throw new AssertionError(
                    "death coin fixture rejected "+
                    deathCoins.result
                );
            player.bank().commitPreparedInventoryMutation(
                deathCoins
            );

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(
                    player
                );

            PlayerLifecycleService.DamageResult lethal=
                lifecycle.applyDamage(
                    500,
                    100L,
                    "RESPAWN_FENCE_TEST",
                    0L
                );

            if(!lethal.died||
               !player.lifecycle().dead()||
               player.playerState().currentLevel(
                    PlayerState.HITPOINTS)!=0)
                throw new AssertionError(
                    "respawn fence death fixture failed"
                );

            long deathSequenceBeforeFailure=
                player.lifecycle().deathSequence();
            long deathTickBeforeFailure=
                player.lifecycle().deathTick();
            long respawnTickBeforeFailure=
                player.lifecycle().respawnTick();

            writer.beginBatch();
            coordinator.tick(
                100L,
                10_000L,
                writer,
                "[respawn-fence-prepare] "
            );
            LocalSession.endWorldTickBatch(
                writer
            );
            coordinator.commitRegionStreamBatch();
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                10_000L
            );

            if(!coordinator.deferredRespawnEligible()||
               !coordinator.deferredDeathSettlementEligible()||
               player.bank().inventoryCount(995)!=10||
               world.groundItems().find(
                    995,
                    transientX,
                    transientY,
                    0
               )!=null||
               !player.lifecycle().dead()||
               movement.x()!=transientX||
               movement.y()!=transientY||
               !movement.transientRegion())
                throw new AssertionError(
                    "respawn/death settlement did not remain prepared after outer commit"
                );

            drain(queue);

            SceneUpdatePublisher beforePublisher=
                regionBridge.publisher;

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    queue,
                    QUEUE_CAPACITY
                );

            boolean failed=false;
            try{
                coordinator
                    .settleDeferredRespawnAfterWorldTick(
                        writer,
                        "[respawn-fence-fail] "
                    );
            }catch(IOException expected){
                failed=true;
            }finally{
                pressure.release();
            }

            if(!failed)
                throw new AssertionError(
                    "forced respawn publication failure did not escape"
                );

            if(writer.terminal()||
               !player.lifecycle().dead()||
               player.lifecycle().deathSequence()!=
                    deathSequenceBeforeFailure||
               player.lifecycle().deathTick()!=
                    deathTickBeforeFailure||
               player.lifecycle().respawnTick()!=
                    respawnTickBeforeFailure||
               player.playerState().currentLevel(
                    PlayerState.HITPOINTS)!=0||
               movement.x()!=transientX||
               movement.y()!=transientY||
               movement.loadedBaseX()!=transientBaseX||
               movement.loadedBaseY()!=transientBaseY||
               !movement.transientRegion()||
               regionStreams.regionLoadPending()||
               regionBridge.publisher!=beforePublisher||
               regionBridge.resetCalls!=0||
               tickBridge.saveCalls!=1||
               !"PLAYER_DEATH_SETTLEMENT".equals(
                    tickBridge.lastSaveReason)||
               queue.queuedBytes()!=0)
                throw new AssertionError(
                    "failed respawn publication changed exact respawn preimage"
                );

            GroundItem settledCoins=
                world.groundItems().find(
                    995,
                    transientX,
                    transientY,
                    0
                );
            if(player.bank().inventoryCount(995)!=0||
               settledCoins==null||
               settledCoins.amount!=10||
               settledCoins.owner!=null||
               coordinator.deferredDeathSettlementEligible())
                throw new AssertionError(
                    "death settlement did not commit exactly once before failed respawn publication"
                );

            writer.beginBatch();
            coordinator.tick(
                101L,
                10_600L,
                writer,
                "[respawn-fence-retry] "
            );
            LocalSession.endWorldTickBatch(
                writer
            );
            coordinator.commitRegionStreamBatch();
            coordinator.commitHomePresentationBatch();
            coordinator.commitGroundPresentationBatch(
                10_600L
            );

            if(!coordinator.deferredRespawnEligible()||
               !player.lifecycle().dead())
                throw new AssertionError(
                    "failed respawn was not re-prepared"
                );

            coordinator
                .settleDeferredRespawnAfterWorldTick(
                    writer,
                    "[respawn-fence-retry] "
                );

            if(!player.lifecycle().alive()||
               player.playerState().currentLevel(
                    PlayerState.HITPOINTS)!=
                        PlayerLifecycleService
                            .LOCALLAB_RESTORED_HITPOINTS||
               movement.x()!=MovementState.INITIAL_X||
               movement.y()!=MovementState.INITIAL_Y||
               !movement.inHomeWindow()||
               !regionStreams.regionLoadPending()||
               regionBridge.publisher==beforePublisher||
               regionBridge.resetCalls!=1||
               !"PLAYER_RESPAWN".equals(
                    tickBridge.lastSaveReason)||
               tickBridge.saveCalls!=2||
               queue.queuedBytes()<=0||
               world.groundItems().find(
                    995,
                    transientX,
                    transientY,
                    0
               )!=settledCoins||
               settledCoins.amount!=10)
                throw new AssertionError(
                    "same-writer respawn retry did not settle exactly once"
                );

            BankState.PreparedInventoryMutation teardownCoins=
                player.bank().prepareAddInventoryAmount(
                    995,
                    5
                );
            if(!teardownCoins.accepted())
                throw new AssertionError(
                    "teardown death coin fixture rejected"
                );
            player.bank().commitPreparedInventoryMutation(
                teardownCoins
            );

            PlayerLifecycleService.DamageResult teardownDeath=
                lifecycle.applyDamage(
                    500,
                    150L,
                    "DISCONNECT_DEATH_SETTLEMENT_TEST",
                    5L
                );
            if(!teardownDeath.died||
               !player.lifecycle().dead())
                throw new AssertionError(
                    "teardown death fixture failed"
                );

            coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[respawn-fence-teardown] "
                );

            GroundItem teardownGround=
                world.groundItems().find(
                    995,
                    MovementState.INITIAL_X,
                    MovementState.INITIAL_Y,
                    0
                );
            if(player.bank().inventoryCount(995)!=0||
               teardownGround==null||
               teardownGround.amount!=5||
               teardownGround.owner!=null||
               coordinator.deferredDeathSettlementEligible()||
               tickBridge.saveCalls!=3||
               !"PLAYER_DEATH_SETTLEMENT".equals(
                    tickBridge.lastSaveReason))
                throw new AssertionError(
                    "synchronous teardown death settlement failed"
                );

            assertStalePreparedRespawnRejected();

            System.out.println(
                "PLAYER_RESPAWN_COMMIT_FENCE_PASS "+
                "outerCommitDefersSemantic=true "+
                "admissionFailureRetainsDeath=true "+
                "admissionFailureRestoresRegion=true "+
                "deathSettlementBeforeRespawn=true "+
                "deathSettlementReplaySafe=true "+
                "deathSettlementCheckpointed=true "+
                "disconnectDeathSettlement=true "+
                "sameWriterRetryCommitsOnce=true "+
                "stalePreparedRejected=true"
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

    private static void assertStalePreparedRespawnRejected(){
        WorldPlayer stalePlayer=
            new WorldPlayer();
        PlayerLifecycleService staleLifecycle=
            new PlayerLifecycleService(
                stalePlayer
            );

        staleLifecycle.applyDamage(
            500,
            200L,
            "RESPAWN_FENCE_STALE_A",
            0L
        );

        PlayerLifecycleService.PreparedRespawn stalePrepared=
            staleLifecycle.prepareRespawn(
                200L
            );

        if(stalePrepared==null)
            throw new AssertionError(
                "stale prepared respawn fixture missing"
            );

        staleLifecycle.commitPreparedRespawn(
            stalePrepared
        );

        staleLifecycle.applyDamage(
            500,
            201L,
            "RESPAWN_FENCE_STALE_B",
            0L
        );

        long replacementSequence=
            stalePlayer.lifecycle().deathSequence();
        long replacementDeathTick=
            stalePlayer.lifecycle().deathTick();
        long replacementRespawnTick=
            stalePlayer.lifecycle().respawnTick();

        boolean rejected=false;
        try{
            staleLifecycle.commitPreparedRespawn(
                stalePrepared
            );
        }catch(IllegalStateException expected){
            rejected=true;
        }

        if(!rejected||
           !stalePlayer.lifecycle().dead()||
           stalePlayer.playerState().currentLevel(
                PlayerState.HITPOINTS)!=0||
           stalePlayer.lifecycle().deathSequence()!=
                replacementSequence||
           replacementSequence==
                stalePrepared.deathSequence||
           stalePlayer.lifecycle().deathTick()!=
                replacementDeathTick||
           stalePlayer.lifecycle().respawnTick()!=
                replacementRespawnTick)
            throw new AssertionError(
                "stale prepared respawn did not fail closed"
            );
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

    private PlayerRespawnCommitFenceTest(){}
}
