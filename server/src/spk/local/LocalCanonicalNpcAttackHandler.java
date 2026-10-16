package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * One-click LocalLab bridge from a viewer-local NPC scene handle to canonical
 * World-owned PvM damage.
 *
 * This owns only the per-session server-side cadence gate for exact canonical
 * PvM clicks. Chase, auto-repeat, rewards and death finalization remain outside
 * this handler. The certified combat-dummy path remains in CombatEngine and is
 * routed before this handler.
 */
final class LocalCanonicalNpcAttackHandler {
    enum Status {
        LEGACY_DUMMY,
        NONCANONICAL_SCENE,
        STALE_SCENE,
        WORLD_CLOSED,
        STALE_CANONICAL,
        DEFINITION_MISMATCH,
        LIFECYCLE_MISSING,
        STALE_LIFECYCLE,
        TARGET_DEAD,
        STALE_PLAYER,
        OUT_OF_RANGE,
        PRESENTATION_UNREPRESENTABLE,
        CADENCE_BLOCKED,
        HIT
    }

    /**
     * Exact World + WorldPlayer/generation accepted by the canonical
     * PvM click resolver when this immediate hit actually mutated the
     * NPC lifecycle. This is a LOCAL evidence witness, not original
     * SpawnPK last-hit attribution or a reward/loot authorization.
     *
     * Unlike NpcLifecycleService.DamageResult.sourceAuthority (NPC
     * lifecycle registration), this witness identifies the validated
     * player of the successful canonical click. It is never created
     * for a failed/stale/out-of-range click, or direct anonymous
     * NpcLifecycleService.applyDamage calls.
     */
    static final class AcceptedHitWitness {
        final World world;
        final WorldPlayer attacker;
        final long generation;
        final EntityId attackerId;
        final EntityId targetNpcId;
        final long worldTick;
        final int appliedDamage;
        final boolean newlyDied;

        private AcceptedHitWitness(
            World world,
            WorldPlayer attacker,
            long generation,
            EntityId attackerId,
            EntityId targetNpcId,
            long worldTick,
            int appliedDamage,
            boolean newlyDied
        ){
            this.world=Objects.requireNonNull(world,"world");
            this.attacker=Objects.requireNonNull(attacker,"attacker");
            this.attackerId=Objects.requireNonNull(attackerId,"attackerId");
            this.targetNpcId=Objects.requireNonNull(targetNpcId,"targetNpcId");
            if(generation<0L||worldTick<0L||appliedDamage<0)
                throw new IllegalArgumentException(
                    "invalid accepted-hit evidence generation/tick/damage");
            if(!attacker.id().equals(attackerId))
                throw new IllegalArgumentException(
                    "accepted click attacker identity mismatch");
            this.generation=generation;
            this.worldTick=worldTick;
            this.appliedDamage=appliedDamage;
            this.newlyDied=newlyDied;
        }

        boolean stillCurrentIn(World checkedWorld){
            return world==checkedWorld&&
                world.players().owns(attacker,generation);
        }
    }

    static final class Result {
        final Status status;
        final EntityId canonicalId;
        final int sceneIndex;
        final int definitionId;
        final int distance;
        final int legalRange;
        final int appliedDamage;
        final int hitpointsAfter;
        final int maxHitpoints;
        final boolean newlyDied;
        final String presentationAuthority;
        final AcceptedHitWitness acceptedHit;

        private Result(
            Status status,
            NpcEntity clicked,
            EntityId canonicalId,
            int distance,
            int legalRange,
            int appliedDamage,
            int hitpointsAfter,
            int maxHitpoints,
            boolean newlyDied,
            AcceptedHitWitness acceptedHit
        ){
            this.status=Objects.requireNonNull(status,"status");
            if(status==Status.HIT&&acceptedHit==null)
                throw new IllegalArgumentException(
                    "HIT must retain verified attacker witness");
            if(status!=Status.HIT&&acceptedHit!=null)
                throw new IllegalArgumentException(
                    "rejected canonical click cannot carry attacker witness");
            if(acceptedHit!=null&&
                    (!acceptedHit.targetNpcId.equals(canonicalId)||
                     acceptedHit.appliedDamage!=appliedDamage||
                     acceptedHit.newlyDied!=newlyDied))
                throw new IllegalArgumentException(
                    "accepted hit witness does not match result");
            this.acceptedHit=acceptedHit;
            this.canonicalId=canonicalId;
            this.sceneIndex=clicked==null?-1:clicked.sceneIndex;
            this.definitionId=clicked==null?-1:clicked.definitionId;
            this.distance=distance;
            this.legalRange=legalRange;
            this.appliedDamage=appliedDamage;
            this.hitpointsAfter=hitpointsAfter;
            this.maxHitpoints=maxHitpoints;
            this.newlyDied=newlyDied;
            this.presentationAuthority=
                "CUSTOM_LOCALLAB_PACKET65_SINGLE_HIT_TYPE_1";
        }

        boolean hit(){
            return status==Status.HIT;
        }

        @Override public String toString(){
            return "CanonicalNpcAttack{status="+status+
                ",scene="+sceneIndex+
                ",definition="+definitionId+
                ",canonical="+canonicalId+
                ",distance="+distance+
                ",range="+legalRange+
                ",damage="+appliedDamage+
                ",hp="+hitpointsAfter+
                "/"+maxHitpoints+
                ",newlyDied="+newlyDied+
                ",presentationAuthority="+
                presentationAuthority+
                "}";
        }
    }

    private static final int BASIC_HIT_TYPE=1;

    @FunctionalInterface
    interface BeforeResolutionHook {
        void run(
            WorldNpc target,
            long expectedGeneration
        );
    }

    @FunctionalInterface
    interface LethalFinalizer {
        void run(
            WorldNpc target
        ) throws Exception;
    }

    @FunctionalInterface
    interface DeadFinalizationRetry {
        void run(
            WorldNpc target
        ) throws Exception;
    }

    private final World world;
    private final WorldPlayer player;
    private final LongSupplier generationSupplier;
    private final EquipmentState equipment;
    private final CombatStyleState combatStyles;
    private final NpcRegistry npcs;
    private final NpcCombatResolutionService resolution;
    private final BeforeResolutionHook beforeResolution;
    private final LethalFinalizer lethalFinalizer;
    private final DeadFinalizationRetry deadFinalizationRetry;

    LocalCanonicalNpcAttackHandler(
        World world,
        WorldPlayer player,
        LongSupplier generationSupplier,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        NpcRegistry npcs
    ){
        this(
            world,
            player,
            generationSupplier,
            equipment,
            combatStyles,
            npcs,
            (target,generation)->{},
            target->{},
            target->{}
        );
    }

    LocalCanonicalNpcAttackHandler(
        World world,
        WorldPlayer player,
        LongSupplier generationSupplier,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        NpcRegistry npcs,
        BeforeResolutionHook beforeResolution
    ){
        this(
            world,
            player,
            generationSupplier,
            equipment,
            combatStyles,
            npcs,
            beforeResolution,
            target->{},
            target->{}
        );
    }

    LocalCanonicalNpcAttackHandler(
        World world,
        WorldPlayer player,
        LongSupplier generationSupplier,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        NpcRegistry npcs,
        BeforeResolutionHook beforeResolution,
        LethalFinalizer lethalFinalizer
    ){
        this(
            world,
            player,
            generationSupplier,
            equipment,
            combatStyles,
            npcs,
            beforeResolution,
            lethalFinalizer,
            target->{}
        );
    }

    LocalCanonicalNpcAttackHandler(
        World world,
        WorldPlayer player,
        LongSupplier generationSupplier,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        NpcRegistry npcs,
        BeforeResolutionHook beforeResolution,
        LethalFinalizer lethalFinalizer,
        DeadFinalizationRetry deadFinalizationRetry
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.player=Objects.requireNonNull(player,"player");
        this.generationSupplier=
            Objects.requireNonNull(
                generationSupplier,
                "generationSupplier"
            );
        this.equipment=Objects.requireNonNull(equipment,"equipment");
        this.combatStyles=
            Objects.requireNonNull(
                combatStyles,
                "combatStyles"
            );
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.beforeResolution=
            Objects.requireNonNull(
                beforeResolution,
                "beforeResolution"
            );
        this.lethalFinalizer=
            Objects.requireNonNull(
                lethalFinalizer,
                "lethalFinalizer"
            );
        this.deadFinalizationRetry=
            Objects.requireNonNull(
                deadFinalizationRetry,
                "deadFinalizationRetry"
            );

        if(player.equipment()!=equipment||
           player.combatStyles()!=combatStyles)
            throw new IllegalArgumentException(
                "canonical NPC attack state must belong to exact WorldPlayer"
            );

        this.resolution=
            new NpcCombatResolutionService(
                player,
                world.npcLifecycle(),
                CombatDamageRules.localLabFallback(),
                CombatAttackTimingRules
                    .recoveredCompatibility(),
                CombatSystemHooks.forPlayer(player)
            );

        if(!resolution.isBoundToLifecycle(
                world.npcLifecycle()
            ))
            throw new IllegalStateException(
                "canonical NPC attack resolver is not bound to World lifecycle"
            );
    }

    /**
     * Returns null only when this is not an exact semantic NPC ATTACK and the
     * residual interaction router should continue.
     */
    // All canonical NPC click handlers bound to this player share a
    // monitor. Also fence SAME-THREAD callback reentry: Java synchronized
    // alone permits it before the outer attack has published cooldown.
    // Non-attack interactions and combat dummies keep their normal route.
    Result handle(
        NpcAction action,
        NpcEntity clicked,
        ServerPacketWriter writer
    )throws IOException{
        synchronized(player.canonicalNpcAttackLock()){
            if(!player.tryEnterCanonicalNpcAttack()){
                NpcInteractionRouter.Route nestedRoute=
                    NpcInteractionRouter.resolve(action,clicked);
                if(nestedRoute.service!=
                        NpcInteractionRouter.Service.ATTACK||
                   CombatTargetRepository.isCombatDummy(
                       clicked.definitionId
                   ))
                    return handleUnderPlayerCadence(
                        action,clicked,writer
                    );
                return result(
                    Status.CADENCE_BLOCKED,
                    clicked,
                    clicked.canonicalId(),
                    -1,-1,0,-1,-1,false
                );
            }
            try{
                return handleUnderPlayerCadence(
                    action,clicked,writer
                );
            }finally{
                // Also release on IOException, callback failure, stale
                // ownership or early status return. Never reset cooldown.
                player.leaveCanonicalNpcAttack();
            }
        }
    }

    private Result handleUnderPlayerCadence(
        NpcAction action,
        NpcEntity clicked,
        ServerPacketWriter writer
    )throws IOException{
        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(
                action,
                clicked
            );

        if(route.service!=
                NpcInteractionRouter.Service.ATTACK)
            return null;

        if(CombatTargetRepository
                .isCombatDummy(
                    clicked.definitionId
                ))
            return result(
                Status.LEGACY_DUMMY,
                clicked,
                clicked.canonicalId(),
                -1,
                -1,
                0,
                -1,
                -1,
                false
            );

        // The scene index in a client packet is a viewer-local slot,
        // not authority to supply a detached or forged NpcEntity.
        // Resolve the current scene object by identity BEFORE reading
        // canonical NPC ownership or initiating any dead retry/damage.
        // This does not disambiguate a replayed wire index after reuse:
        // packets carry no independent scene-generation token.
        if(action.sceneIndex!=clicked.sceneIndex||
           npcs.scene(action.sceneIndex)!=clicked)
            return result(
                Status.STALE_SCENE,
                clicked,
                clicked.canonicalId(),
                -1,
                -1,
                0,
                -1,
                -1,
                false
            );

        EntityId canonicalId=
            clicked.canonicalId();

        if(canonicalId==null)
            return result(
                Status.NONCANONICAL_SCENE,
                clicked,
                null,
                -1,
                -1,
                0,
                -1,
                -1,
                false
            );

        WorldNpc target=
            world.npcs().byId(
                canonicalId
            );

        if(target==null)
            return result(
                Status.STALE_CANONICAL,
                clicked,
                canonicalId,
                -1,
                -1,
                0,
                -1,
                -1,
                false
            );

        if(target.definitionId!=
                clicked.definitionId)
            return result(
                Status.DEFINITION_MISMATCH,
                clicked,
                canonicalId,
                -1,
                -1,
                0,
                -1,
                -1,
                false
            );

        NpcLifecycleService.Snapshot before=
            world.npcLifecycle().get(
                canonicalId
            );

        if(before==null)
            return result(
                Status.LIFECYCLE_MISSING,
                clicked,
                canonicalId,
                -1,
                -1,
                0,
                -1,
                -1,
                false
            );

        // Prevent terminal callbacks after a World has been closed.
        // Later checks also fence a close during generation sampling.
        // These are entry-time checks, not an atomic close lease.
        if(world.closed())
            return result(
                Status.WORLD_CLOSED,
                clicked,
                canonicalId,
                -1,
                -1,
                0,
                before.hitpoints,
                before.maxHitpoints,
                false
            );

        // Exact session admission must precede the dead-NPC retry too.
        // A stale handler has no authority to trigger settlement retries
        // or observer callbacks via a previously finalized target.
        // This is an entry-time guard, not an atomic logout fence.
        long generation=
            generationSupplier.getAsLong();

        if(!world.players().owns(
                player,
                generation
            ))
            return result(
                Status.STALE_PLAYER,
                clicked,
                canonicalId,
                -1,
                -1,
                0,
                before.hitpoints,
                before.maxHitpoints,
                false
            );

        if(before.dead()){
            // The generation supplier is external to scene ownership.
            // A viewer projection can retire while it is evaluated.
            // Do not allow a dead-target retry from a retired view.
            if(npcs.scene(action.sceneIndex)!=clicked)
                return result(
                    Status.STALE_SCENE,
                    clicked,
                    canonicalId,
                    -1,
                    -1,
                    0,
                    before.hitpoints,
                    before.maxHitpoints,
                    false
                );

            if(world.closed())
                return result(
                    Status.WORLD_CLOSED,
                    clicked,
                    canonicalId,
                    -1,
                    -1,
                    0,
                    before.hitpoints,
                    before.maxHitpoints,
                    false
                );

            // A generation callback may have removed the previously
            // observed canonical NPC or its lifecycle. A snapshot
            // captured before that callback cannot authorize retry.
            // This check does not create an atomic terminal lease.
            if(world.npcs().byId(canonicalId)!=target)
                return result(
                    Status.STALE_CANONICAL,
                    clicked,
                    canonicalId,
                    -1,
                    -1,
                    0,
                    before.hitpoints,
                    before.maxHitpoints,
                    false
                );

            NpcLifecycleService.Snapshot currentDeath=
                world.npcLifecycle().get(canonicalId);

            if(currentDeath==null)
                return result(
                    Status.LIFECYCLE_MISSING,
                    clicked,
                    canonicalId,
                    -1,
                    -1,
                    0,
                    before.hitpoints,
                    before.maxHitpoints,
                    false
                );

            // A replacement lifecycle can die again at the exact same
            // tick with identical HP and source authority. Values alone
            // cannot authenticate its registration lifetime.
            if(currentDeath.registrationIdentity!=
                    before.registrationIdentity||
               !currentDeath.dead()||
               currentDeath.deathTick!=before.deathTick||
               currentDeath.maxHitpoints!=before.maxHitpoints)
                return result(
                    Status.STALE_LIFECYCLE,
                    clicked,
                    canonicalId,
                    -1,
                    -1,
                    0,
                    currentDeath.hitpoints,
                    currentDeath.maxHitpoints,
                    false
                );

            retryDeadFinalization(
                target
            );

            return result(
                Status.TARGET_DEAD,
                clicked,
                canonicalId,
                -1,
                -1,
                0,
                before.hitpoints,
                before.maxHitpoints,
                false
            );
        }

        int playerX;
        int playerY;
        int playerPlane;
        int weaponId;
        CombatStyleRepository.Style style;

        synchronized(player.mutationLock()){
            if(!world.players().owns(
                    player,
                    generation
                ))
                return result(
                    Status.STALE_PLAYER,
                    clicked,
                    canonicalId,
                    -1,
                    -1,
                    0,
                    before.hitpoints,
                    before.maxHitpoints,
                    false
                );

            MovementState movement=
                player.movement();

            playerX=movement.x();
            playerY=movement.y();
            playerPlane=movement.plane();
            weaponId=equipment.weapon();

            int root=
                CombatInterfaceRepository.forWeapon(
                    weaponId
                );

            style=
                combatStyles.current(root);
        }

        Tile targetTile=
            target.tile();

        CombatWeaponProfile profile=
            CombatWeaponRepository.resolve(
                weaponId
            );

        int legalRange=
            profile!=null&&
            profile.attackRange>0
                ?profile.attackRange
                :1;

        int distance=
            playerPlane==targetTile.plane
                ?Math.max(
                    Math.abs(
                        playerX-targetTile.x
                    ),
                    Math.abs(
                        playerY-targetTile.y
                    )
                )
                :Integer.MAX_VALUE;

        if(playerPlane!=targetTile.plane||
           !inLegalRange(
                playerX,
                playerY,
                targetTile.x,
                targetTile.y,
                legalRange
            ))
            return result(
                Status.OUT_OF_RANGE,
                clicked,
                canonicalId,
                distance,
                legalRange,
                0,
                before.hitpoints,
                before.maxHitpoints,
                false
            );

        // Packet-65's recovered single-hit HP fields are bytes. Do not mutate
        // canonical HP when the exact lifecycle state cannot be represented
        // without inventing a scaling rule.
        if(before.hitpoints>255||
           before.maxHitpoints>255)
            return result(
                Status.PRESENTATION_UNREPRESENTABLE,
                clicked,
                canonicalId,
                distance,
                legalRange,
                0,
                before.hitpoints,
                before.maxHitpoints,
                false
            );

        long attackTick=
            world.clock().tick();

        if(attackTick<player.canonicalNpcAttackNextAllowedTick())
            return result(
                Status.CADENCE_BLOCKED,
                clicked,
                canonicalId,
                distance,
                legalRange,
                0,
                before.hitpoints,
                before.maxHitpoints,
                false
            );

        // A missing outbound writer is a deterministic caller error.
        // Reject it before the first callback / lifecycle HP mutation;
        // checking only inside sendMaskLocal allowed a hit with no packet.
        // Retain original early statuses for stale, dead, out-of-range
        // and cadence-blocked requests without requiring an output writer.
        Objects.requireNonNull(writer,"writer");

        NpcCombatResolutionService.Result hit;

        try{
            beforeResolution.run(
                target,
                generation
            );

            // The viewer projection can be retired after the initial
            // action/scene check but before immediate damage admission.
            // Do not apply HP when its identity changed in this window,
            // leaving packet-65 without the exact recipient scene.
            // This is a second admission check, not an atomic lease.
            if(npcs.scene(action.sceneIndex)!=clicked)
                return result(
                    Status.STALE_SCENE,
                    clicked,
                    canonicalId,
                    distance,
                    legalRange,
                    0,
                    before.hitpoints,
                    before.maxHitpoints,
                    false
                );

            hit=
                resolution.resolveImmediateOwnedAdmitted(
                    world,
                    generation,
                    target,
                    weaponId,
                    style,
                    attackTick,
                    (attacker,checkedTarget)->{
                        MovementState currentMovement=
                            attacker.movement();
                        Tile currentTargetTile=
                            checkedTarget.tile();

                        return currentMovement.plane()==
                                currentTargetTile.plane&&
                            inLegalRange(
                                currentMovement.x(),
                                currentMovement.y(),
                                currentTargetTile.x,
                                currentTargetTile.y,
                                legalRange
                            );
                    },
                    before.registrationIdentity
                );
        }catch(
            NpcCombatResolutionService
                .ImmediateDamageAdmissionRejectedException rejected
        ){
            return result(
                Status.OUT_OF_RANGE,
                clicked,
                canonicalId,
                distance,
                legalRange,
                0,
                before.hitpoints,
                before.maxHitpoints,
                false
            );
        }catch(
            NpcCombatResolutionService
                .StaleAttackerOwnershipException stale
        ){
            return result(
                Status.STALE_PLAYER,
                clicked,
                canonicalId,
                distance,
                legalRange,
                0,
                before.hitpoints,
                before.maxHitpoints,
                false
            );
        }catch(
            NpcCombatResolutionService
                .StaleTargetOwnershipException stale
        ){
            return result(
                Status.STALE_CANONICAL,
                clicked,
                canonicalId,
                distance,
                legalRange,
                0,
                before.hitpoints,
                before.maxHitpoints,
                false
            );
        }catch(
            NpcLifecycleService
                .StaleLifecycleRegistrationException replaced
        ){
            return result(
                Status.STALE_LIFECYCLE,
                clicked,
                canonicalId,
                distance,
                legalRange,
                0,
                before.hitpoints,
                before.maxHitpoints,
                false
            );
        }catch(
            NpcLifecycleService
                .LifecycleOwnershipException stale
        ){
            return result(
                Status.LIFECYCLE_MISSING,
                clicked,
                canonicalId,
                distance,
                legalRange,
                0,
                before.hitpoints,
                before.maxHitpoints,
                false
            );
        }

        NpcLifecycleService.DamageResult damage=
            hit.lifecycle;

        if(damage.ignoredDead)
            return result(
                Status.TARGET_DEAD,
                clicked,
                canonicalId,
                distance,
                legalRange,
                0,
                damage.hitpointsAfter,
                before.maxHitpoints,
                false
            );

        NpcLifecycleService.Snapshot after=
            world.npcLifecycle().get(
                canonicalId
            );

        if(after==null||
           after.registrationIdentity!=before.registrationIdentity||
           after.maxHitpoints!=before.maxHitpoints||
           after.hitpoints!=damage.hitpointsAfter)
            throw new IllegalStateException(
                "canonical lifecycle changed during click target="+
                canonicalId
            );

        if(damage.appliedDamage>255||
           after.hitpoints>255||
           after.maxHitpoints>255)
            throw new IllegalStateException(
                "post-damage packet-65 HP representation changed target="+
                canonicalId
            );

        // The input tick was captured BEFORE the pre-resolution hook.
        // If World ticks advance during that callback, publishing cooldown
        // from the old tick would make a committed hit immediately eligible
        // for another current-tick click. Anchor the LocalLab cadence to
        // the later of admission and actual HP-completion observations.
        // This changes neither the damage/death tick witness nor the
        // separate legacy CombatState scheduler.
        long completionTick=world.clock().tick();
        long cadenceAnchorTick=Math.max(attackTick,completionTick);
        player.canonicalNpcAttackNextAllowedTick(
            Math.addExact(
                cadenceAnchorTick,
                hit.nextAttackDelayTicks
            )
        );

        // Type 1 is explicit LocalLab basic-hit compatibility. It is not a
        // SpawnPK max-hit or original damage-family claim.
        //
        // HP and cooldown are already committed. A checked transport/write
        // failure must not suppress a newly lethal NPC's terminal callback:
        // no further client click is guaranteed after a broken connection.
        // Preserve the original packet failure if terminalization succeeds;
        // if both fail, propagate the terminal failure with the presentation
        // exception suppressed. Both checked I/O and unchecked publication
        // failures can occur after HP has already been committed.
        try{
            // Canonical HP lives in World, not one session's viewer scene.
            // Publish locally AND enqueue semantic presentation to registered
            // remote viewers via the existing World-owned relay. The relay
            // translates their independent scene indices after player-81
            // ordering barriers. This is not a damage or reward broadcast.
            npcs.sendMask(
                clicked,
                NpcSyncEncoder.Mask.singleHit(
                    damage.appliedDamage,
                    BASIC_HIT_TYPE,
                    after.hitpoints,
                    after.maxHitpoints
                ),
                writer
            );
        }catch(IOException|RuntimeException presentationFailure){
            if(damage.newlyDied){
                try{
                    finalizeLethal(target);
                }catch(IOException|RuntimeException|Error terminalFailure){
                    if(terminalFailure!=presentationFailure)
                        terminalFailure.addSuppressed(presentationFailure);
                    throw terminalFailure;
                }
            }
            throw presentationFailure;
        }

        if(damage.newlyDied)
            finalizeLethal(
                target
            );

        return result(
            Status.HIT,
            clicked,
            canonicalId,
            distance,
            legalRange,
            damage.appliedDamage,
            after.hitpoints,
            after.maxHitpoints,
            damage.newlyDied,
            new AcceptedHitWitness(
                world,
                player,
                generation,
                hit.attackerId,
                hit.targetId,
                attackTick,
                damage.appliedDamage,
                damage.newlyDied
            )
        );
    }

    long nextAllowedAttackTick(){
        return player.canonicalNpcAttackNextAllowedTick();
    }

    private void finalizeLethal(
        WorldNpc target
    )throws IOException{
        try{
            lethalFinalizer.run(
                Objects.requireNonNull(
                    target,
                    "target"
                )
            );
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "canonical NPC lethal finalizer failed target="+
                target.id,
                failure
            );
        }
    }

    private void retryDeadFinalization(
        WorldNpc target
    )throws IOException{
        try{
            deadFinalizationRetry.run(
                Objects.requireNonNull(
                    target,
                    "target"
                )
            );
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "canonical NPC dead finalization retry failed target="+
                target.id,
                failure
            );
        }
    }

    private static boolean inLegalRange(
        int playerX,
        int playerY,
        int targetX,
        int targetY,
        int range
    ){
        int dx=Math.abs(playerX-targetX);
        int dy=Math.abs(playerY-targetY);

        // Preserve CombatEngine compatibility: range 1 is cardinal adjacency
        // (or same tile), not diagonal touch.
        if(range<=1)
            return dx+dy<=1;

        return Math.max(dx,dy)<=range;
    }

    private static Result result(
        Status status,
        NpcEntity clicked,
        EntityId canonicalId,
        int distance,
        int legalRange,
        int appliedDamage,
        int hitpointsAfter,
        int maxHitpoints,
        boolean newlyDied
    ){
        return result(
            status,clicked,canonicalId,distance,legalRange,
            appliedDamage,hitpointsAfter,maxHitpoints,newlyDied,null
        );
    }

    private static Result result(
        Status status,
        NpcEntity clicked,
        EntityId canonicalId,
        int distance,
        int legalRange,
        int appliedDamage,
        int hitpointsAfter,
        int maxHitpoints,
        boolean newlyDied,
        AcceptedHitWitness witness
    ){
        return new Result(
            status,clicked,canonicalId,distance,legalRange,
            appliedDamage,hitpointsAfter,maxHitpoints,newlyDied,witness
        );
    }
}
