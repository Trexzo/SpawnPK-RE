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
        WorldPlayer attacker=new WorldPlayer();
        long attackerGeneration=world.registerPlayer(
            attacker,
            "respawn-fence-attacker"
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

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(
                    player
                );

            synchronized(player.mutationLock()){
                BankState.Stack[] bank=
                    player.bank().bankSnapshot();
                BankState.Stack[] inventory=
                    player.bank().inventorySnapshot();
                inventory[0]=new BankState.Stack(995,25);
                player.bank().restoreAccountState(
                    bank,
                    inventory,
                    player.bank().isOpen()
                );
            }

            PlayerLifecycleService.DamageResult lethal=
                lifecycle.applyDamage(
                    500,
                    100L,
                    "RESPAWN_FENCE_TEST",
                    0L
                );

            world.playerDeathAttributions().record(
                attacker,
                attackerGeneration,
                player,
                generation
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
               !coordinator.deferredPvpDeathSettlementEligible()||
               player.bank().inventoryAt(0)==null||
               player.bank().inventoryAt(0).itemId!=995||
               player.bank().inventoryAt(0).qty!=25||
               world.groundItems().size()!=0||
               !player.lifecycle().dead()||
               movement.x()!=transientX||
               movement.y()!=transientY||
               !movement.transientRegion())
                throw new AssertionError(
                    "respawn did not remain prepared after outer commit"
                );

            drain(queue);

            SceneUpdatePublisher beforePublisher=
                regionBridge.publisher;

            coordinator.settleDeferredPvpDeathAfterWorldTick(
                "[respawn-fence-death-settle] "
            );

            GroundItem deathCoins=
                world.groundItems().findVisible(
                    995,
                    transientX,
                    transientY,
                    0,
                    attacker.username()
                );

            if(coordinator.deferredPvpDeathSettlementEligible()||
               player.bank().inventoryAt(0)!=null||
               deathCoins==null||
               deathCoins.amount!=25||
               !attacker.username().equals(deathCoins.owner)||
               tickBridge.saveCalls!=1||
               !"PLAYER_PVP_DEATH_SETTLEMENT".equals(
                    tickBridge.lastSaveReason))
                throw new AssertionError(
                    "PvP death settlement did not commit before respawn"
                );

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
               !"PLAYER_PVP_DEATH_SETTLEMENT".equals(
                    tickBridge.lastSaveReason)||
               queue.queuedBytes()!=0)
                throw new AssertionError(
                    "failed respawn publication changed exact preimage"
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
               coordinator.deferredPvpDeathSettlementEligible()||
               !player.lifecycle().dead()||
               world.groundItems().findVisible(
                    995,
                    transientX,
                    transientY,
                    0,
                    attacker.username()
               ).amount!=25)
                throw new AssertionError(
                    "failed respawn was not re-prepared without duplicate death loot"
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
               queue.queuedBytes()<=0)
                throw new AssertionError(
                    "same-writer respawn retry did not settle exactly once"
                );

            assertStalePreparedRespawnRejected();

            System.out.println(
                "PLAYER_RESPAWN_COMMIT_FENCE_PASS "+
                "pvpDeathSettlementPreparedNoMutation=true "+
                "pvpDeathSettlementBeforeRespawn=true "+
                "pvpDeathReplayNoDuplicate=true "+
                "outerCommitDefersSemantic=true "+
                "admissionFailureRetainsDeath=true "+
                "admissionFailureRestoresRegion=true "+
                "sameWriterRetryCommitsOnce=true "+
                "stalePreparedRejected=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );
            if(attacker.registered())
                world.unregisterPlayer(
                    attacker,
                    attackerGeneration
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
