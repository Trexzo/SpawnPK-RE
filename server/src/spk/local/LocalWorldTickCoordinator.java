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

        PlayerLifecycleService.TickResult lifecycleTick=
            lifecycle.tick(worldTick);

        if(lifecycleTick==PlayerLifecycleService.TickResult.RESPAWNED){
            playerInteractions.clearTargets();
            TradeService.cancelIfActive(
                worldPlayer,
                "PLAYER_RESPAWN"
            );

            writer.fixed(
                134,
                BootstrapPackets.skill134(
                    PlayerState.HITPOINTS,
                    worldPlayer.playerState().xp(
                        PlayerState.HITPOINTS
                    ),
                    worldPlayer.playerState().currentLevel(
                        PlayerState.HITPOINTS
                    )
                )
            );

            regionStreams.reattachHomeForRespawn(
                writer,
                tag
            );

            bridge.saveAccount(
                tag,
                "PLAYER_RESPAWN"
            );

            legacyTickCount++;

            System.out.println(
                tag+
                "PLAYER_RESPAWN_APPLIED tick="+
                worldTick+
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
            return;
        }

        if(regionStreams.maybeStream(writer,tag)){
            legacyTickCount++;
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

        publishMovement(
            movementTick,
            movementFacingTarget,
            worldTick,
            writer
        );

        if(movementTick!=null)
            bridge.saveAccount(tag,"POSITION_TICK");

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

        groundItemPresentationRelay.publishPendingIfSceneReady(
            now,
            bridge.scenePublisher(),
            regionStreams.regionLoadPending()
        );

        applyGroundItemResult(
            groundItemHandler.tick(
                now,
                bridge.scenePublisher(),
                writer
            ),
            tag
        );

        petDropPickup.tick(
            writer,
            tag,
            now
        );

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

        SharedNpcWorldRelay.syncRemotePets(writer);

        if(npcPulse!=null&&
           (
               legacyTickCount==1||
               legacyTickCount%25==0||
               !npcPulse.contains(
                   "worldAdd=0 worldRemove=0 worldWalk=0"
               )
           )){
            System.out.println(
                tag+"WORLD_R7_"+npcPulse+
                " sharedWorldTick="+worldTick
            );
        }

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
                tag+"V56_COMBAT "+combatTick+
                " sharedWorldTick="+worldTick
            );

            if(combatTick.startsWith("TARGET_CLEARED")){
                bridge.clearOpponentOverlay(
                    writer,
                    tag,
                    "COMBAT_TARGET_CLEARED"
                );
            }
        }

        int dealt=combat.consumeLastDamage();

        if(dealt>0){
            NpcEntity overlayTarget=
                npcs.scene(
                    combat.state().targetSceneIndex
                );

            if(overlayTarget!=null){
                bridge.publishOpponentOverlay(
                    overlayTarget,
                    writer,
                    tag,
                    "HIT_UPDATE"
                );

                int baseline=
                    combat.state().context==
                        CombatContext.PLAYER_PVP
                        ?100
                        :200;

                int remoteHitType=
                    dealt>=baseline
                        ?6
                        :1;
            }

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

        if(petEffects.tick(now)&&
           npcs.pet()!=null&&
           PetPresentationProfile.supportsNativeState(
               npcs.pet().definitionId
           )){
            String reset=
                npcs.setPetNativeState(
                    0,
                    writer
                );

            System.out.println(
                tag+
                "V59_PET_CHARGE_TIMEOUT_RESET "+
                reset+
                " state="+petEffects.summary()+
                " sharedWorldTick="+worldTick
            );
        }

        bridge.ensurePetFollowScheduled(now);
        bridge.ensurePetTestSequenceScheduled(now);

        if(legacyTickCount==1||
           legacyTickCount%25==0){
            System.out.println(
                tag+
                "V5121_WORLD_PULSE tick="+worldTick+
                " playerAgeTicks="+legacyTickCount+
                " playerId="+worldPlayer.id()+
                " world="+
                movement.x()+","+movement.y()+
                " queued="+movement.queued()+
                " members="+world.players().size()+
                " certification=M4_CERTIFIED M5_WEAPONS_ACTIVE ENGINE_R2_WORLD_PULSE"
            );
        }
    }

    private void tickTransientRegion(
        long worldTick,
        long now,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        MovementState.Tick movementTick=
            movementEnabled
                ?movement.advance()
                :null;

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

        if(movementTick!=null){
            movementTickCount++;
        }

        groundItemPresentationRelay.publishPendingIfSceneReady(
            now,
            bridge.scenePublisher(),
            regionStreams.regionLoadPending()
        );

        petDropPickup.tick(
            writer,
            tag,
            now
        );

        if(movementTick!=null)
            updatePetFollowAfterOwnerMovement(
                movementTick,
                now
            );

        bridge.ensurePetFollowScheduled(now);
        bridge.ensurePetTestSequenceScheduled(now);

        legacyTickCount++;

        if(legacyTickCount==1||
           legacyTickCount%25==0){
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
                " queued="+movement.queued()+
                " staticCollision=true homeWorldNpcSystemsSuspended=true petLifecycleActive=true transientPositionSave=false"
            );
        }
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
        long worldTick,
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

            movementTickCount++;

            System.out.println(
                "[world player="+worldPlayer.id()+
                "] M5_AUTHORITATIVE_TICK mode=RUN tiles=2 from="+
                movementTick.fromX+","+
                movementTick.fromY+
                " to="+
                movementTick.toX+","+
                movementTick.toY+
                " dirs="+
                movementTick.dir1+","+
                movementTick.dir2+
                " combatFacing="+
                (movementFacingTarget==null
                    ?"NONE"
                    :movementFacingTarget)+
                " remaining="+movementTick.remaining+
                " movementTick="+movementTickCount+
                " worldTick="+worldTick
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

        movementTickCount++;

        System.out.println(
            "[world player="+worldPlayer.id()+
            "] M5_AUTHORITATIVE_TICK mode=WALK tiles=1 from="+
            movementTick.fromX+","+
            movementTick.fromY+
            " to="+
            movementTick.toX+","+
            movementTick.toY+
            " dir="+movementTick.dir1+
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
