package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Per-player authoritative world-pulse gameplay coordinator.
 *
 * LocalSession remains responsible for the network/session shell and packet
 * batch boundary. This coordinator owns the established per-pulse gameplay
 * ordering that runs only on the shared WorldPulse execution context.
 */
final class LocalWorldTickCoordinator {
    interface SessionBridge {
        Player81WorldSync.Context player81Sync();
        SceneUpdatePublisher scenePublisher();
        void saveAccount(String tag,String reason);
        void publishOpponentOverlay(
            NpcEntity target,
            ServerPacketWriter writer,
            String tag,
            String reason
        )throws IOException;
        void clearOpponentOverlay(
            ServerPacketWriter writer,
            String tag,
            String reason
        )throws IOException;
        long petFollowDeadline();
        void setPetFollowDeadline(long value);
        void ensurePetFollowScheduled(long now);
        void ensurePetTestSequenceScheduled(long now);
    }

    private final boolean movementEnabled;
    private final World world;
    private final WorldPlayer worldPlayer;
    private final MovementState movement;
    private final EquipmentState equipment;
    private final CombatStyleState combatStyles;
    private final PetEffectState petEffects;
    private final PlayerStatusService statuses;
    private final PlayerLifecycleService lifecycle;
    private final PlayerDeathItemResolutionService deathItemResolution;
    private final PlayerDeathGroundSettlementService deathGroundSettlement;
    private final LocalLabDeathDispositionPolicy deathDispositionPolicy;
    private final NpcRegistry npcs;
    private final HomeWorldRuntimePlan homeWorld;
    private final CombatEngine combat;
    private final LocalRegionStreamHandler regionStreams;
    private final LocalPlayerInteractionHandler playerInteractions;
    private final LocalBankObjectInteractionHandler bankObjectHandler;
    private final LocalRoutedNpcInteractionHandler routedNpcHandler;
    private final LocalGroundItemInteractionHandler groundItemHandler;
    private final LocalGroundItemPresentationRelay groundItemPresentationRelay;
    private final LocalPetDropPickupHandler petDropPickup;
    private final LocalPetRuntimeCommandHandler petRuntimeCommands;
    private final SessionBridge bridge;

    private long legacyTickCount;
    private long movementTickCount;
    private boolean deferredRegionLegacyTickIncrement;
    private boolean deferredBankInteractionEligible;
    private boolean deferredMakeoverInteractionEligible;
    private boolean deferredGroundTakeEligible;
    private boolean deferredPetPickupEligible;
    private PetEffectState.PreparedTimeoutReset deferredPetEffectTimeout;
    private NpcEntity deferredPetEffectPet;
    private int deferredPetEffectNativeState;
    private PlayerLifecycleService.PreparedRespawn deferredRespawn;
    private PlayerDeathItemResolutionService.Resolution deferredDeathResolution;
    private LocalLabDeathDispositionPolicy.Plan deferredDeathPlan;
    private MovementState.Snapshot deferredMovementPreimage;
    private LocalPlayerInteractionHandler.Snapshot deferredMovementInteractionPreimage;
    private CombatEngine.MovementFacingSnapshot deferredMovementFacingPreimage;
    private MovementState.Tick deferredMovementTick;
    private Player81WorldSync.Context deferredMovementPlayerSync;
    private Integer deferredMovementFacingTarget;
    private long deferredMovementWorldTick;
    private long deferredMovementNow;
    private boolean deferredMovementTransient;
    private boolean noMovementSemanticTailEntered;
    private boolean noMovementSemanticTailCompleted;

    LocalWorldTickCoordinator(
        boolean movementEnabled,
        World world,
        WorldPlayer worldPlayer,
        MovementState movement,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        PetEffectState petEffects,
        NpcRegistry npcs,
        HomeWorldRuntimePlan homeWorld,
        CombatEngine combat,
        LocalRegionStreamHandler regionStreams,
        LocalPlayerInteractionHandler playerInteractions,
        LocalBankObjectInteractionHandler bankObjectHandler,
        LocalRoutedNpcInteractionHandler routedNpcHandler,
        LocalGroundItemInteractionHandler groundItemHandler,
        LocalPetDropPickupHandler petDropPickup,
        LocalPetRuntimeCommandHandler petRuntimeCommands,
        SessionBridge bridge
    ){
        this(
            movementEnabled,
            world,
            worldPlayer,
            movement,
            equipment,
            combatStyles,
            petEffects,
            new PlayerStatusService(worldPlayer),
            npcs,
            homeWorld,
            combat,
            regionStreams,
            playerInteractions,
            bankObjectHandler,
            routedNpcHandler,
            groundItemHandler,
            petDropPickup,
            petRuntimeCommands,
            bridge
        );
    }

    LocalWorldTickCoordinator(
        boolean movementEnabled,
        World world,
        WorldPlayer worldPlayer,
        MovementState movement,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        PetEffectState petEffects,
        PlayerStatusService statuses,
        NpcRegistry npcs,
        HomeWorldRuntimePlan homeWorld,
        CombatEngine combat,
        LocalRegionStreamHandler regionStreams,
        LocalPlayerInteractionHandler playerInteractions,
        LocalBankObjectInteractionHandler bankObjectHandler,
        LocalRoutedNpcInteractionHandler routedNpcHandler,
        LocalGroundItemInteractionHandler groundItemHandler,
        LocalPetDropPickupHandler petDropPickup,
        LocalPetRuntimeCommandHandler petRuntimeCommands,
        SessionBridge bridge
    ){
        this.movementEnabled=movementEnabled;
        this.world=Objects.requireNonNull(world,"world");
        this.worldPlayer=Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.equipment=Objects.requireNonNull(equipment,"equipment");
        this.combatStyles=Objects.requireNonNull(combatStyles,"combatStyles");
        this.petEffects=Objects.requireNonNull(petEffects,"petEffects");
        this.statuses=Objects.requireNonNull(statuses,"statuses");
        this.lifecycle=new PlayerLifecycleService(worldPlayer);
        this.deathItemResolution=
            new PlayerDeathItemResolutionService(
                worldPlayer,
                LocalLabDeathDispositionPolicy.AUTHORITY
            );
        this.deathGroundSettlement=
            new PlayerDeathGroundSettlementService(
                world,
                worldPlayer,
                LocalLabDeathDispositionPolicy.AUTHORITY
            );
        this.deathDispositionPolicy=
            new LocalLabDeathDispositionPolicy();
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.homeWorld=Objects.requireNonNull(homeWorld,"homeWorld");
        this.combat=Objects.requireNonNull(combat,"combat");
        this.regionStreams=Objects.requireNonNull(regionStreams,"regionStreams");
        this.playerInteractions=Objects.requireNonNull(
            playerInteractions,"playerInteractions");
        this.bankObjectHandler=Objects.requireNonNull(
            bankObjectHandler,"bankObjectHandler");
        this.routedNpcHandler=Objects.requireNonNull(
            routedNpcHandler,"routedNpcHandler");
        this.groundItemHandler=Objects.requireNonNull(
            groundItemHandler,"groundItemHandler");
        this.groundItemPresentationRelay=
            new LocalGroundItemPresentationRelay(
                this.world,
                this.worldPlayer,
                this.movement
            );
        this.petDropPickup=Objects.requireNonNull(
            petDropPickup,"petDropPickup");
        this.petRuntimeCommands=Objects.requireNonNull(
            petRuntimeCommands,"petRuntimeCommands");
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    void tick(
        long worldTick,
        long now,
        ServerPacketWriter writer,
        String tag
    )throws Exception{
        if(deferredMovementTick!=null)
            throw new IllegalStateException(
                "deferred movement settlement still active"
            );

        deferredBankInteractionEligible=false;
        deferredMakeoverInteractionEligible=false;
        deferredGroundTakeEligible=false;
        deferredPetPickupEligible=false;
        deferredPetEffectTimeout=null;
        deferredPetEffectPet=null;
        deferredPetEffectNativeState=0;
        deferredRespawn=null;
        noMovementSemanticTailEntered=false;
        noMovementSemanticTailCompleted=false;

        PlayerStatusService.TickResult statusTick=
            statuses.tick(worldTick);

        if(statusTick.changed()){
            System.out.println(
                tag+
                "PLAYER_STATUS_EXPIRY "+
                statusTick+
                " sharedWorldTick="+worldTick
            );
        }

        prepareDeathSettlementIfNeeded();

        PlayerLifecycleService.PreparedRespawn preparedRespawn=
            lifecycle.prepareRespawn(
                worldTick
            );

        if(preparedRespawn!=null){
            deferredRespawn=
                preparedRespawn;
            return;
        }

        if(regionStreams.maybeStream(writer,tag)){
            if(writer.batchActive()){
                if(deferredRegionLegacyTickIncrement)
                    throw new IllegalStateException(
                        "deferred region tick accounting still active"
                    );
                deferredRegionLegacyTickIncrement=true;
            }else{
                legacyTickCount++;
            }
            return;
        }

        if(movement.transientRegion()){
            tickTransientRegion(
                worldTick,
                now,
                writer,
                tag
            );
            return;
        }

        boolean movementFence=
            writer.batchActive();

        MovementState.Snapshot movementBefore=
            movementFence
                ?movement.snapshot()
                :null;
        LocalPlayerInteractionHandler.Snapshot
            interactionsBefore=
                movementFence
                    ?playerInteractions.snapshot()
                    :null;
        CombatEngine.MovementFacingSnapshot
            combatFacingBefore=
                movementFence
                    ?combat.snapshotMovementFacing()
                    :null;

        Player81WorldSync.Context playerSync=
            bridge.player81Sync();

        String playerInteractionPrep=
            playerInteractions.prepareTick(
                worldTick,
                playerSync
            );
        if(playerInteractionPrep!=null)
            System.out.println(playerInteractionPrep);

        MovementState.Tick movementTick=
            movementEnabled
                ?movement.advance()
                :null;

        Integer measuredApproachTarget=
            movementTick==null
                ?null
                :combat.consumeApproachFacingTargetForMovement();

        Integer playerApproachTarget=
            movementTick==null
                ?null
                :playerInteractions.movementInteractionTarget(
                    playerSync
                );

        Integer movementFacingTarget=
            measuredApproachTarget!=null
                ?measuredApproachTarget
                :playerApproachTarget;

        try{
            publishMovement(
                movementTick,
                movementFacingTarget,
                writer
            );
        }catch(IOException failure){
            handlePreparedMovementPublicationFailure(
                movementTick,
                movementFence,
                movementBefore,
                interactionsBefore,
                combatFacingBefore,
                writer
            );
            throw failure;
        }catch(RuntimeException failure){
            handlePreparedMovementPublicationFailure(
                movementTick,
                movementFence,
                movementBefore,
                interactionsBefore,
                combatFacingBefore,
                writer
            );
            throw failure;
        }catch(Error failure){
            handlePreparedMovementPublicationFailure(
                movementTick,
                movementFence,
                movementBefore,
                interactionsBefore,
                combatFacingBefore,
                writer
            );
            throw failure;
        }

        if(movementTick!=null&&movementFence){
            stageDeferredMovement(
                movementBefore,
                interactionsBefore,
                combatFacingBefore,
                movementTick,
                playerSync,
                movementFacingTarget,
                worldTick,
                now,
                false
            );
            return;
        }

        if(movementTick!=null)
            commitHomeMovementAccounting(
                movementTick,
                movementFacingTarget,
                worldTick
            );

        if(movementTick==null&&movementFence)
            noMovementSemanticTailEntered=true;

        runHomeTickTail(
            worldTick,
            now,
            writer,
            tag,
            playerSync,
            movementTick
        );

        if(movementTick==null&&movementFence)
            noMovementSemanticTailCompleted=true;
    }

    private void tickTransientRegion(
        long worldTick,
        long now,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        boolean movementFence=
            writer.batchActive();
        MovementState.Snapshot movementBefore=
            movementFence
                ?movement.snapshot()
                :null;

        MovementState.Tick movementTick=
            movementEnabled
                ?movement.advance()
                :null;

        try{
            publishTransientMovement(
                movementTick,
                writer
            );
        }catch(IOException failure){
            if(movementTick!=null&&
               movementFence)
                movement.restore(
                    movementBefore
                );
            throw failure;
        }catch(RuntimeException failure){
            if(movementTick!=null&&
               movementFence)
                movement.restore(
                    movementBefore
                );
            throw failure;
        }catch(Error failure){
            if(movementTick!=null&&
               movementFence)
                movement.restore(
                    movementBefore
                );
            throw failure;
        }

        if(movementTick!=null&&movementFence){
            stageDeferredMovement(
                movementBefore,
                null,
                null,
                movementTick,
                null,
                null,
                worldTick,
                now,
                true
            );
            return;
        }

        if(movementTick!=null)
            movementTickCount++;

        if(movementTick==null&&movementFence)
            noMovementSemanticTailEntered=true;

        runTransientTickTail(
            worldTick,
            now,
            writer,
            tag,
            movementTick
        );

        if(movementTick==null&&movementFence)
            noMovementSemanticTailCompleted=true;
    }

    void completeRegionLoad(
        RegionLoadLifecycle.Completion completion,
        ServerPacketWriter writer,
        String tag,
        long now
    )throws IOException{
        boolean groundSnapshotPublished=
            regionStreams.completeRegionLoad(
                completion,
                writer,
                tag
            );

        groundItemPresentationRelay
            .consumeSnapshotCoveredAfterSnapshot(
                now,
                groundSnapshotPublished
            );
    }

    private void publishMovement(
        MovementState.Tick movementTick,
        Integer movementFacingTarget,
        ServerPacketWriter writer
    )throws IOException{
        if(movementTick==null){
            writer.varShort(
                81,
                BootstrapPackets.player81Idle()
            );
            return;
        }

        if(movementTick.running){
            writer.varShort(
                81,
                movementFacingTarget==null
                    ?BootstrapPackets.player81RunSteps(
                        movementTick.dir1,
                        movementTick.dir2
                    )
                    :Player81MeasuredSync.runStepsAndInteraction(
                        movementTick.dir1,
                        movementTick.dir2,
                        movementFacingTarget.intValue()
                    )
            );
            return;
        }

        writer.varShort(
            81,
            movementFacingTarget==null
                ?BootstrapPackets.player81WalkStep(
                    movementTick.dir1
                )
                :Player81MeasuredSync.walkStepAndInteraction(
                    movementTick.dir1,
                    movementFacingTarget.intValue()
                )
        );
    }

    private void publishTransientMovement(
        MovementState.Tick movementTick,
        ServerPacketWriter writer
    )throws IOException{
        if(movementTick==null){
            writer.varShort(
                81,
                BootstrapPackets.player81Idle()
            );
        }else if(movementTick.running){
            writer.varShort(
                81,
                BootstrapPackets.player81RunSteps(
                    movementTick.dir1,
                    movementTick.dir2
                )
            );
        }else{
            writer.varShort(
                81,
                BootstrapPackets.player81WalkStep(
                    movementTick.dir1
                )
            );
        }
    }

    private void commitHomeMovementAccounting(
        MovementState.Tick movementTick,
        Integer movementFacingTarget,
        long worldTick
    ){
        movementTickCount++;

        System.out.println(
            "[world player="+worldPlayer.id()+
            "] M5_AUTHORITATIVE_TICK mode="+
            (movementTick.running
                ?"RUN tiles=2"
                :"WALK tiles=1")+
            " from="+
            movementTick.fromX+","+
            movementTick.fromY+
            " to="+
            movementTick.toX+","+
            movementTick.toY+
            (movementTick.running
                ?" dirs="+movementTick.dir1+
                    ","+movementTick.dir2
                :" dir="+movementTick.dir1)+
            " combatFacing="+
            (movementFacingTarget==null
                ?"NONE"
                :movementFacingTarget)+
            " remaining="+movementTick.remaining+
            " movementTick="+movementTickCount+
            " worldTick="+worldTick
        );
    }

    private void updatePetFollowAfterOwnerMovement(
        MovementState.Tick movementTick,
        long now
    ){
        if(!petDropPickup.pickupPending()){
            npcs.queueOwnerMovement(movementTick);

            if(npcs.needsFollow(movement)&&
               bridge.petFollowDeadline()==Long.MAX_VALUE){
                bridge.setPetFollowDeadline(
                    now+200L
                );
            }
            return;
        }

        bridge.setPetFollowDeadline(
            Long.MAX_VALUE
        );
    }

    private void runHomeTickTail(
        long worldTick,
        long now,
        ServerPacketWriter writer,
        String tag,
        Player81WorldSync.Context playerSync,
        MovementState.Tick movementTick
    )throws IOException{
        if(movementTick!=null)
            bridge.saveAccount(
                tag,
                "POSITION_TICK"
            );

        if(movementTick!=null){
            String playerTradeTick=
                playerInteractions.afterMovement(
                    playerSync
                );
            if(playerTradeTick!=null)
                System.out.println(
                    tag+playerTradeTick
                );
        }

        String playerAttackTick=
            playerInteractions.tickAttack(
                worldTick,
                writer,
                playerSync
            );
        if(playerAttackTick!=null)
            System.out.println(
                tag+playerAttackTick
            );

        deferredBankInteractionEligible=true;
        deferredMakeoverInteractionEligible=true;

        groundItemPresentationRelay.publishPendingIfSceneReady(
            now,
            bridge.scenePublisher(),
            regionStreams.regionLoadPending()
        );

        deferredGroundTakeEligible=true;
        deferredPetPickupEligible=true;

        if(movementTick!=null)
            updatePetFollowAfterOwnerMovement(
                movementTick,
                now
            );

        legacyTickCount++;

        npcs.beginHomePresentationBatch(
            homeWorld
        );

        String npcPulse=
            npcs.tickHome(
                movement,
                writer,
                homeWorld,
                worldTick
            );

        SharedNpcWorldRelay.syncRemotePets(
            writer
        );

        if(npcPulse!=null&&
           (
               legacyTickCount==1||
               legacyTickCount%25==0||
               !npcPulse.contains(
                   "worldAdd=0 worldRemove=0 worldWalk=0"
               )
           ))
            System.out.println(
                tag+"WORLD_R7_"+
                npcPulse+
                " sharedWorldTick="+
                worldTick
            );

        SceneUpdatePublisher scenePublisher=
            bridge.scenePublisher();

        String combatTick=
            combat.tick(
                movement,
                npcs,
                equipment,
                writer,
                legacyTickCount,
                combatStyles.current(
                    CombatInterfaceRepository.forWeapon(
                        equipment.weapon()
                    )
                ),
                scenePublisher
            );

        if(combatTick!=null){
            System.out.println(
                tag+
                "V56_COMBAT "+
                combatTick+
                " sharedWorldTick="+
                worldTick
            );

            if(combatTick.startsWith(
                    "TARGET_CLEARED"))
                bridge.clearOpponentOverlay(
                    writer,
                    tag,
                    "COMBAT_TARGET_CLEARED"
                );
        }

        int dealt=
            combat.consumeLastDamage();

        if(dealt>0){
            NpcEntity overlayTarget=
                npcs.scene(
                    combat.state()
                        .targetSceneIndex
                );

            if(overlayTarget!=null)
                bridge.publishOpponentOverlay(
                    overlayTarget,
                    writer,
                    tag,
                    "HIT_UPDATE"
                );

            String petDamage=
                petRuntimeCommands.applyDamage(
                    dealt,
                    now,
                    writer,
                    "COMBAT_M2"
                );

            if(petDamage!=null)
                System.out.println(
                    tag+petDamage
                );
        }

        if(!petRuntimeCommands
                .preparedCombatDamagePending()){
            PetEffectState.PreparedTimeoutReset
                petEffectTimeout=
                    petEffects.prepareTimeoutReset(
                        now
                    );

            if(petEffectTimeout!=null){
                NpcEntity effectPet=
                    npcs.pet();

                if(effectPet!=null&&
                   PetPresentationProfile.supportsNativeState(
                       effectPet.definitionId
                   )){
                    deferredPetEffectTimeout=
                        petEffectTimeout;
                    deferredPetEffectPet=
                        effectPet;
                    deferredPetEffectNativeState=
                        npcs.petNativeState();
                }else{
                    petEffects.commitTimeoutReset(
                        petEffectTimeout
                    );
                }
            }
        }

        bridge.ensurePetFollowScheduled(
            now
        );
        bridge.ensurePetTestSequenceScheduled(
            now
        );

        if(legacyTickCount==1||
           legacyTickCount%25==0)
            System.out.println(
                tag+
                "V5121_WORLD_PULSE tick="+
                worldTick+
                " playerAgeTicks="+
                legacyTickCount+
                " playerId="+
                worldPlayer.id()+
                " world="+
                movement.x()+","+
                movement.y()+
                " queued="+
                movement.queued()+
                " members="+
                world.players().size()+
                " certification=M4_CERTIFIED M5_WEAPONS_ACTIVE ENGINE_R2_WORLD_PULSE"
            );
    }

    private void runTransientTickTail(
        long worldTick,
        long now,
        ServerPacketWriter writer,
        String tag,
        MovementState.Tick movementTick
    )throws IOException{
        groundItemPresentationRelay.publishPendingIfSceneReady(
            now,
            bridge.scenePublisher(),
            regionStreams.regionLoadPending()
        );

        deferredPetPickupEligible=true;

        if(movementTick!=null)
            updatePetFollowAfterOwnerMovement(
                movementTick,
                now
            );

        bridge.ensurePetFollowScheduled(
            now
        );
        bridge.ensurePetTestSequenceScheduled(
            now
        );

        legacyTickCount++;

        if(legacyTickCount==1||
           legacyTickCount%25==0)
            System.out.println(
                tag+
                "V5160_TRANSIENT_REGION_PULSE tick="+
                worldTick+
                " world="+
                movement.x()+","+
                movement.y()+","+
                movement.plane()+
                " base="+
                movement.loadedBaseX()+","+
                movement.loadedBaseY()+
                " queued="+
                movement.queued()+
                " staticCollision=true homeWorldNpcSystemsSuspended=true petLifecycleActive=true transientPositionSave=false"
            );
    }

    private void handlePreparedMovementPublicationFailure(
        MovementState.Tick movementTick,
        boolean movementFence,
        MovementState.Snapshot movementBefore,
        LocalPlayerInteractionHandler.Snapshot
            interactionsBefore,
        CombatEngine.MovementFacingSnapshot
            combatFacingBefore,
        ServerPacketWriter writer
    ){
        restorePreparedMovement(
            movementTick,
            movementFence,
            movementBefore,
            interactionsBefore,
            combatFacingBefore
        );

        /*
         * prepareTick(...) runs before S2C81 publication and can dispatch
         * shared Trade semantics when a deferred target becomes adjacent.
         * With no actual movement there is no prepared-movement token whose
         * local rollback is sufficient to reconstruct that shared authority.
         * If idle S2C81 publication now fails, fail closed: LocalSession will
         * abort the open packet batch, while this terminal latch prevents the
         * same live client from continuing after pre-tail interaction state
         * may already have advanced.
         *
         * Actual movement keeps #1812's exact snapshot rollback/retry policy.
         */
        if(movementTick==null&&movementFence)
            writer.markTerminal();
    }

    private void restorePreparedMovement(
        MovementState.Tick movementTick,
        boolean movementFence,
        MovementState.Snapshot movementBefore,
        LocalPlayerInteractionHandler.Snapshot
            interactionsBefore,
        CombatEngine.MovementFacingSnapshot
            combatFacingBefore
    ){
        if(movementTick==null||
           !movementFence)
            return;

        movement.restore(
            movementBefore
        );
        playerInteractions.restore(
            interactionsBefore
        );
        combat.restoreMovementFacing(
            combatFacingBefore
        );
    }

    private void stageDeferredMovement(
        MovementState.Snapshot movementBefore,
        LocalPlayerInteractionHandler.Snapshot
            interactionsBefore,
        CombatEngine.MovementFacingSnapshot
            combatFacingBefore,
        MovementState.Tick movementTick,
        Player81WorldSync.Context playerSync,
        Integer movementFacingTarget,
        long worldTick,
        long now,
        boolean transientRegion
    ){
        if(deferredMovementTick!=null)
            throw new IllegalStateException(
                "deferred movement already staged"
            );

        deferredMovementPreimage=
            Objects.requireNonNull(
                movementBefore,
                "movementBefore"
            );
        deferredMovementInteractionPreimage=
            interactionsBefore;
        deferredMovementFacingPreimage=
            combatFacingBefore;
        deferredMovementTick=
            Objects.requireNonNull(
                movementTick,
                "movementTick"
            );
        deferredMovementPlayerSync=
            playerSync;
        deferredMovementFacingTarget=
            movementFacingTarget;
        deferredMovementWorldTick=
            worldTick;
        deferredMovementNow=
            now;
        deferredMovementTransient=
            transientRegion;
    }

    void settleDeferredMovementAfterWorldTick(
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        MovementState.Tick movementTick=
            deferredMovementTick;

        if(movementTick==null)
            return;

        Player81WorldSync.Context playerSync=
            deferredMovementPlayerSync;
        Integer movementFacingTarget=
            deferredMovementFacingTarget;
        long worldTick=
            deferredMovementWorldTick;
        long now=
            deferredMovementNow;
        boolean transientRegion=
            deferredMovementTransient;

        clearDeferredMovementToken();

        if(transientRegion)
            movementTickCount++;
        else
            commitHomeMovementAccounting(
                movementTick,
                movementFacingTarget,
                worldTick
            );

        SceneUpdatePublisher tailPublisher=
            bridge.scenePublisher();
        SceneCoordinateContext.Snapshot
            tailSceneContext=
                tailPublisher==null
                    ?null
                    :tailPublisher.context()
                        .snapshot();

        boolean writerBatchActive=false;
        boolean relayBatchActive=false;
        boolean tailCommitted=false;

        try{
            writer.beginBatch();
            writerBatchActive=true;

            relayBatchActive=
                SharedNpcWorldRelay.beginSourceMaskBatch(
                    writer
                );

            if(transientRegion)
                runTransientTickTail(
                    worldTick,
                    now,
                    writer,
                    tag,
                    movementTick
                );
            else
                runHomeTickTail(
                    worldTick,
                    now,
                    writer,
                    tag,
                    playerSync,
                    movementTick
                );

            writer.endBatch();
            writerBatchActive=false;
            tailCommitted=true;

            if(relayBatchActive)
                SharedNpcWorldRelay
                    .commitSourceMaskBatch(
                        writer
                    );

            commitHomePresentationBatch();
            commitGroundPresentationBatch(
                System.currentTimeMillis()
            );
        }catch(IOException failure){
            if(!tailCommitted)
                abortMovementTail(
                    writer,
                    writerBatchActive,
                    relayBatchActive,
                    tailPublisher,
                    tailSceneContext,
                    failure
                );
            throw failure;
        }catch(RuntimeException failure){
            if(!tailCommitted)
                abortMovementTail(
                    writer,
                    writerBatchActive,
                    relayBatchActive,
                    tailPublisher,
                    tailSceneContext,
                    failure
                );
            throw failure;
        }catch(Error failure){
            if(!tailCommitted)
                abortMovementTail(
                    writer,
                    writerBatchActive,
                    relayBatchActive,
                    tailPublisher,
                    tailSceneContext,
                    failure
                );
            throw failure;
        }
    }

    private void abortMovementTail(
        ServerPacketWriter writer,
        boolean writerBatchActive,
        boolean relayBatchActive,
        SceneUpdatePublisher tailPublisher,
        SceneCoordinateContext.Snapshot
            tailSceneContext,
        Throwable primary
    ){
        if(writerBatchActive)
            try{
                writer.abortBatch();
            }catch(Throwable abortFailure){
                primary.addSuppressed(
                    abortFailure
                );
            }

        if(relayBatchActive)
            try{
                SharedNpcWorldRelay
                    .abortSourceMaskBatch(
                        writer
                    );
            }catch(Throwable relayFailure){
                primary.addSuppressed(
                    relayFailure
                );
            }

        if(tailPublisher!=null&&
           tailSceneContext!=null)
            try{
                tailPublisher.context()
                    .restore(
                        tailSceneContext
                    );
            }catch(Throwable sceneFailure){
                primary.addSuppressed(
                    sceneFailure
                );
            }

        try{
            abortHomePresentationBatch();
        }catch(Throwable homeFailure){
            primary.addSuppressed(
                homeFailure
            );
        }

        try{
            abortGroundPresentationBatch();
        }catch(Throwable groundFailure){
            primary.addSuppressed(
                groundFailure
            );
        }

        abortDeferredBankInteractionsAfterWorldTick();
        abortDeferredMakeoverInteractionsAfterWorldTick();
        abortDeferredGroundTakeAfterWorldTick();
        abortDeferredPetPickupAfterWorldTick();
        abortDeferredPetEffectTimeoutAfterWorldTick();
        abortDeferredPetChargeIncrementAfterWorldTick(
            writer
        );

        /*
         * The source movement packet has already committed before this
         * standalone tail transaction begins.  Tail code can mutate several
         * gameplay authorities before its final queue admission, so a
         * recoverable admission failure cannot safely resume this live
         * session without either replaying or rolling back every one of
         * those authorities.  Retire the writer instead: teardown may persist
         * the resulting authoritative state, but no live client can continue
         * after missing the corresponding tail presentation.
         */
        writer.markTerminal();
    }

    boolean abortDeferredMovementAfterWorldTick(){
        if(deferredMovementTick==null)
            return false;

        movement.restore(
            deferredMovementPreimage
        );

        if(deferredMovementInteractionPreimage!=null)
            playerInteractions.restore(
                deferredMovementInteractionPreimage
            );

        if(deferredMovementFacingPreimage!=null)
            combat.restoreMovementFacing(
                deferredMovementFacingPreimage
            );

        clearDeferredMovementToken();
        return true;
    }

    boolean deferredMovementEligible(){
        return deferredMovementTick!=null;
    }

    boolean noMovementSemanticTailEntered(){
        return noMovementSemanticTailEntered;
    }

    boolean noMovementSemanticTailCompleted(){
        return noMovementSemanticTailCompleted;
    }

    void clearNoMovementSemanticTailAfterCommit(){
        noMovementSemanticTailEntered=false;
        noMovementSemanticTailCompleted=false;
    }

    boolean retireAfterFailedNoMovementSemanticTail(
        ServerPacketWriter writer
    ){
        if(!noMovementSemanticTailEntered)
            return false;

        noMovementSemanticTailEntered=false;
        noMovementSemanticTailCompleted=false;
        writer.markTerminal();
        return true;
    }

    private void clearDeferredMovementToken(){
        deferredMovementPreimage=null;
        deferredMovementInteractionPreimage=null;
        deferredMovementFacingPreimage=null;
        deferredMovementTick=null;
        deferredMovementPlayerSync=null;
        deferredMovementFacingTarget=null;
        deferredMovementWorldTick=0L;
        deferredMovementNow=0L;
        deferredMovementTransient=false;
    }

    private void applyGroundItemResult(
        LocalGroundItemInteractionHandler.Result result,
        String tag
    ){
        if(result==null)return;

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+result.logText
        );
    }

    private void prepareDeathSettlementIfNeeded(){
        if(!worldPlayer.lifecycle().dead())
            return;

        long deathSequence=
            worldPlayer.lifecycle().deathSequence();

        if(deathGroundSettlement.get(
                deathSequence)!=null)
            return;

        if(deferredDeathResolution!=null){
            if(deferredDeathResolution.deathSequence!=
                    deathSequence)
                throw new IllegalStateException(
                    "deferred death settlement belongs to stale death sequence expected="+
                    deathSequence+
                    " actual="+
                    deferredDeathResolution.deathSequence
                );
            return;
        }

        PlayerDeathItemResolutionService.DeathPreview preview=
            deathItemResolution.previewCurrentDeath();
        LocalLabDeathDispositionPolicy.Plan plan=
            deathDispositionPolicy.plan(
                preview
            );

        deferredDeathResolution=
            deathItemResolution.resolveCurrentDeath(
                preview,
                plan.decisions
            );
        deferredDeathPlan=plan;
    }

    void settleCurrentDeathForSessionTeardown(
        String tag
    ){
        prepareDeathSettlementIfNeeded();
        settleDeferredDeathSettlementAfterWorldTick(
            tag
        );
    }

    void settleDeferredDeathSettlementAfterWorldTick(
        String tag
    ){
        PlayerDeathItemResolutionService.Resolution resolution=
            deferredDeathResolution;
        LocalLabDeathDispositionPolicy.Plan plan=
            deferredDeathPlan;

        deferredDeathResolution=null;
        deferredDeathPlan=null;

        if(resolution==null)
            return;

        if(plan==null)
            throw new IllegalStateException(
                "death settlement missing disposition plan"
            );

        PlayerDeathGroundSettlementService.Settlement settlement=
            deathGroundSettlement.settle(
                resolution,
                null
            );

        bridge.saveAccount(
            tag,
            "PLAYER_DEATH_SETTLEMENT"
        );

        System.out.println(
            tag+
            "PLAYER_DEATH_GROUND_SETTLED deathSequence="+
            settlement.deathSequence+
            " tile="+
            settlement.deathTile+
            " keptQty="+
            settlement.keptTotalQuantity+
            " lostQty="+
            settlement.lostTotalQuantity+
            " groundStacks="+
            settlement.drops.size()+
            " autoKeptLines="+
            plan.autoKeptLines+
            " explicitLostLines="+
            plan.explicitLostLines+
            " standardLostLines="+
            plan.standardLostLines+
            " standardPolicy="+
            LocalLabDeathDispositionPolicy.STANDARD_POLICY+
            " lootOwner=PUBLIC"+
            " authority="+
            LocalLabDeathDispositionPolicy.AUTHORITY
        );
    }

    void abortDeferredDeathSettlementAfterWorldTick(){
        deferredDeathResolution=null;
        deferredDeathPlan=null;
    }

    boolean deferredDeathSettlementEligible(){
        return deferredDeathResolution!=null;
    }

    void settleDeferredRespawnAfterWorldTick(
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        PlayerLifecycleService.PreparedRespawn prepared=
            deferredRespawn;

        deferredRespawn=null;

        if(prepared==null)
            return;

        settleDeferredDeathSettlementAfterWorldTick(
            tag
        );

        lifecycle.requirePreparedRespawnCurrent(
            prepared
        );

        boolean writerBatchActive=false;
        boolean packetCommitted=false;

        try{
            writer.beginBatch();
            writerBatchActive=true;

            writer.fixed(
                134,
                BootstrapPackets.skill134(
                    PlayerState.HITPOINTS,
                    worldPlayer.playerState().xp(
                        PlayerState.HITPOINTS
                    ),
                    prepared.restoredHitpoints
                )
            );

            regionStreams.stageHomeForPreparedRespawn(
                writer,
                tag
            );

            writer.endBatch();
            writerBatchActive=false;
            packetCommitted=true;

            regionStreams.commitRegionStreamBatch();
            lifecycle.commitPreparedRespawn(
                prepared
            );

            playerInteractions.clearTargets();
            TradeService.cancelIfActive(
                worldPlayer,
                "PLAYER_RESPAWN"
            );

            bridge.saveAccount(
                tag,
                "PLAYER_RESPAWN"
            );

            legacyTickCount++;

            System.out.println(
                tag+
                "PLAYER_RESPAWN_APPLIED tick="+
                prepared.worldTick+
                " hp="+
                worldPlayer.playerState().currentLevel(
                    PlayerState.HITPOINTS
                )+
                " world="+
                movement.x()+","+
                movement.y()+","+
                movement.plane()+
                " authority="+
                PlayerLifecycleService.AUTHORITY
            );
        }catch(IOException failure){
            if(!packetCommitted)
                abortPreparedRespawnPacket(
                    writer,
                    writerBatchActive,
                    failure
                );
            throw failure;
        }catch(RuntimeException failure){
            if(!packetCommitted)
                abortPreparedRespawnPacket(
                    writer,
                    writerBatchActive,
                    failure
                );
            throw failure;
        }catch(Error failure){
            if(!packetCommitted)
                abortPreparedRespawnPacket(
                    writer,
                    writerBatchActive,
                    failure
                );
            throw failure;
        }
    }

    private void abortPreparedRespawnPacket(
        ServerPacketWriter writer,
        boolean writerBatchActive,
        Throwable primary
    ){
        if(writerBatchActive)
            try{
                writer.abortBatch();
            }catch(Throwable abortFailure){
                primary.addSuppressed(
                    abortFailure
                );
            }

        try{
            regionStreams.abortRegionStreamBatch();
        }catch(Throwable restoreFailure){
            primary.addSuppressed(
                restoreFailure
            );
        }
    }

    void abortDeferredRespawnAfterWorldTick(){
        deferredRespawn=null;
    }

    boolean deferredRespawnEligible(){
        return deferredRespawn!=null;
    }

    void settleDeferredBankInteractionsAfterWorldTick(
        long now,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        if(!deferredBankInteractionEligible)
            return;

        deferredBankInteractionEligible=false;

        String bankObjectTick=
            bankObjectHandler.tick(
                now,
                writer
            );
        if(bankObjectTick!=null)
            System.out.println(
                tag+bankObjectTick
            );

        String routedNpcTick=
            routedNpcHandler.tick(
                now,
                writer
            );
        if(routedNpcTick!=null)
            System.out.println(
                tag+routedNpcTick
            );
    }

    void abortDeferredBankInteractionsAfterWorldTick(){
        deferredBankInteractionEligible=false;
    }

    boolean deferredBankInteractionEligible(){
        return deferredBankInteractionEligible;
    }

    void settleDeferredMakeoverInteractionsAfterWorldTick(
        long now,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        if(!deferredMakeoverInteractionEligible)
            return;

        deferredMakeoverInteractionEligible=false;

        String makeoverTick=
            routedNpcHandler.tickMakeover(
                now,
                writer,
                tag
            );
        if(makeoverTick!=null)
            System.out.println(
                tag+makeoverTick
            );
    }

    void abortDeferredMakeoverInteractionsAfterWorldTick(){
        deferredMakeoverInteractionEligible=false;
    }

    boolean deferredMakeoverInteractionEligible(){
        return deferredMakeoverInteractionEligible;
    }

    void settleDeferredGroundTakeAfterWorldTick(
        long now,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        if(!deferredGroundTakeEligible)
            return;

        deferredGroundTakeEligible=false;

        applyGroundItemResult(
            groundItemHandler.tick(
                now,
                bridge.scenePublisher(),
                writer
            ),
            tag
        );
    }

    void abortDeferredGroundTakeAfterWorldTick(){
        deferredGroundTakeEligible=false;
    }

    boolean deferredGroundTakeEligible(){
        return deferredGroundTakeEligible;
    }

    void settleDeferredPetPickupAfterWorldTick(
        ServerPacketWriter writer,
        String tag,
        long now
    )throws IOException{
        if(!deferredPetPickupEligible)
            return;

        deferredPetPickupEligible=false;
        petDropPickup.tick(
            writer,
            tag,
            now
        );
    }

    void abortDeferredPetPickupAfterWorldTick(){
        deferredPetPickupEligible=false;
    }

    boolean deferredPetPickupEligible(){
        return deferredPetPickupEligible;
    }

    void settleDeferredPetEffectTimeoutAfterWorldTick(
        ServerPacketWriter writer,
        String tag,
        long worldTick
    )throws IOException{
        PetEffectState.PreparedTimeoutReset prepared=
            deferredPetEffectTimeout;
        NpcEntity expectedPet=
            deferredPetEffectPet;
        int expectedNativeState=
            deferredPetEffectNativeState;

        deferredPetEffectTimeout=null;
        deferredPetEffectPet=null;
        deferredPetEffectNativeState=0;

        if(prepared==null)
            return;

        if(npcs.pet()!=expectedPet||
           npcs.petNativeState()!=expectedNativeState)
            throw new IllegalStateException(
                "pet timeout presentation changed before settlement"
            );

        boolean relayBatchActive=false;
        boolean writerBatchActive=false;
        boolean packetCommitted=false;

        try{
            writer.beginBatch();
            writerBatchActive=true;

            relayBatchActive=
                SharedNpcWorldRelay.beginSourceMaskBatch(
                    writer
                );

            String reset=
                npcs.publishPetNativeState(
                    expectedPet,
                    0,
                    writer
                );

            if(!reset.startsWith(
                    "PET_NATIVE_STATE_OK"))
                throw new IllegalStateException(
                    "pet timeout reset publication rejected: "+
                    reset
                );

            writer.endBatch();
            writerBatchActive=false;
            packetCommitted=true;

            if(relayBatchActive)
                SharedNpcWorldRelay
                    .commitSourceMaskBatch(
                        writer
                    );

            npcs.commitPetNativeState(
                expectedPet,
                expectedNativeState,
                0
            );
            petEffects.commitTimeoutReset(
                prepared
            );

            System.out.println(
                tag+
                "V59_PET_CHARGE_TIMEOUT_RESET "+
                reset+
                " state="+petEffects.summary()+
                " sharedWorldTick="+worldTick
            );
        }catch(IOException failure){
            if(!packetCommitted)
                abortPetEffectTimeoutPacket(
                    writer,
                    writerBatchActive,
                    relayBatchActive,
                    failure
                );
            throw failure;
        }catch(RuntimeException failure){
            if(!packetCommitted)
                abortPetEffectTimeoutPacket(
                    writer,
                    writerBatchActive,
                    relayBatchActive,
                    failure
                );
            throw failure;
        }catch(Error failure){
            if(!packetCommitted)
                abortPetEffectTimeoutPacket(
                    writer,
                    writerBatchActive,
                    relayBatchActive,
                    failure
                );
            throw failure;
        }
    }

    private static void abortPetEffectTimeoutPacket(
        ServerPacketWriter writer,
        boolean writerBatchActive,
        boolean relayBatchActive,
        Throwable primary
    ){
        if(writerBatchActive)
            try{
                writer.abortBatch();
            }catch(Throwable abortFailure){
                primary.addSuppressed(
                    abortFailure
                );
            }

        if(relayBatchActive)
            try{
                SharedNpcWorldRelay
                    .abortSourceMaskBatch(
                        writer
                    );
            }catch(Throwable relayFailure){
                primary.addSuppressed(
                    relayFailure
                );
            }
    }

    void abortDeferredPetEffectTimeoutAfterWorldTick(){
        deferredPetEffectTimeout=null;
        deferredPetEffectPet=null;
        deferredPetEffectNativeState=0;
    }

    boolean deferredPetEffectTimeoutEligible(){
        return deferredPetEffectTimeout!=null;
    }

    void settleDeferredPetChargeIncrementAfterWorldTick(
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        String result=
            petRuntimeCommands
                .settlePreparedCombatDamageAfterSourceCommit(
                    writer
                );

        if(result!=null)
            System.out.println(
                tag+result
            );
    }

    boolean abortDeferredPetChargeIncrementAfterWorldTick(
        ServerPacketWriter writer
    ){
        return petRuntimeCommands
            .abortPreparedCombatDamageAfterSourceFailure(
                writer
            );
    }

    boolean deferredPetChargeIncrementEligible(){
        return petRuntimeCommands
            .preparedCombatDamagePending();
    }

    boolean commitRegionStreamBatch(){
        boolean committed=
            regionStreams
                .commitRegionStreamBatch();

        if(committed&&
           deferredRegionLegacyTickIncrement){
            legacyTickCount++;
            deferredRegionLegacyTickIncrement=false;
        }

        return committed;
    }

    boolean abortRegionStreamBatch(){
        boolean aborted=
            regionStreams
                .abortRegionStreamBatch();

        if(aborted)
            deferredRegionLegacyTickIncrement=false;

        return aborted;
    }

    boolean deferredRegionTickAccounting(){
        return deferredRegionLegacyTickIncrement;
    }

    boolean regionStreamBatchStaged(){
        return regionStreams
            .regionStreamBatchStaged();
    }

    boolean commitHomePresentationBatch(){
        return npcs
            .commitHomePresentationBatch();
    }

    boolean abortHomePresentationBatch(){
        return npcs
            .abortHomePresentationBatch(
                homeWorld
            );
    }

    int commitGroundPresentationBatch(
        long now
    ){
        return groundItemPresentationRelay
            .commitStagedDeliveries(
                now
            );
    }

    int abortGroundPresentationBatch(){
        return groundItemPresentationRelay
            .abortStagedDeliveries();
    }

    int stagedGroundPresentationCount(){
        return groundItemPresentationRelay
            .stagedDeliveryCount();
    }

    long legacyTickCount(){
        return legacyTickCount;
    }

    long movementTickCount(){
        return movementTickCount;
    }
}
