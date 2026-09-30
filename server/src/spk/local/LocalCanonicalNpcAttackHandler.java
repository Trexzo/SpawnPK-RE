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
        STALE_CANONICAL,
        DEFINITION_MISMATCH,
        LIFECYCLE_MISSING,
        TARGET_DEAD,
        STALE_PLAYER,
        OUT_OF_RANGE,
        PRESENTATION_UNREPRESENTABLE,
        CADENCE_BLOCKED,
        HIT
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

        private Result(
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
            this.status=Objects.requireNonNull(status,"status");
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

    private final World world;
    private final WorldPlayer player;
    private final LongSupplier generationSupplier;
    private final EquipmentState equipment;
    private final CombatStyleState combatStyles;
    private final NpcRegistry npcs;
    private final NpcCombatResolutionService resolution;
    private final BeforeResolutionHook beforeResolution;
    private long nextAllowedAttackTick;

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
            (target,generation)->{}
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
    Result handle(
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

        if(before.dead())
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

        if(attackTick<nextAllowedAttackTick)
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

        NpcCombatResolutionService.Result hit;

        try{
            beforeResolution.run(
                target,
                generation
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
                    }
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

        nextAllowedAttackTick=
            Math.addExact(
                attackTick,
                hit.nextAttackDelayTicks
            );

        // Type 1 is explicit LocalLab basic-hit compatibility. It is not a
        // SpawnPK max-hit or original damage-family claim.
        npcs.sendMaskLocal(
            clicked,
            NpcSyncEncoder.Mask.singleHit(
                damage.appliedDamage,
                BASIC_HIT_TYPE,
                after.hitpoints,
                after.maxHitpoints
            ),
            Objects.requireNonNull(
                writer,
                "writer"
            )
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
            damage.newlyDied
        );
    }

    long nextAllowedAttackTick(){
        return nextAllowedAttackTick;
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
        return new Result(
            status,
            clicked,
            canonicalId,
            distance,
            legalRange,
            appliedDamage,
            hitpointsAfter,
            maxHitpoints,
            newlyDied
        );
    }
}
