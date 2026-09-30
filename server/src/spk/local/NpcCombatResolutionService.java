package spk.local;

import java.util.Objects;

/**
 * Authoritative player-to-NPC PvM resolution.
 *
 * Caller/world ownership is explicit. Presentation, kill credit, rewards,
 * drops, XP and respawn remain outside this service. Immediate resolution
 * remains source-compatible; delayed delivery is optional and explicit.
 */
final class NpcCombatResolutionService {
    static final class StaleAttackerOwnershipException
        extends IllegalStateException {
        final EntityId attackerId;
        final long expectedGeneration;

        StaleAttackerOwnershipException(
            WorldPlayer attacker,
            long expectedGeneration,
            Throwable cause
        ){
            super(
                "PvM attacker ownership changed attacker="+
                attacker.id()+
                " expectedGeneration="+
                expectedGeneration,
                cause
            );
            this.attackerId=attacker.id();
            this.expectedGeneration=expectedGeneration;
        }
    }

    static final class StaleTargetOwnershipException
        extends IllegalStateException {
        final EntityId targetId;

        StaleTargetOwnershipException(
            WorldNpc target,
            Throwable cause
        ){
            super(
                "PvM target ownership changed target="+
                target.id,
                cause
            );
            this.targetId=target.id;
        }
    }

    enum Delivery {
        IMMEDIATE,
        SCHEDULED
    }

    static final class Result {
        final EntityId attackerId;
        final EntityId targetId;
        final CombatDamageRules.Result damage;
        final CombatAttackTimingRules.Result timing;
        final CombatSystemHooks.Snapshot hooks;
        final NpcLifecycleService.DamageResult lifecycle;
        final int nextAttackDelayTicks;

        Result(
            EntityId attackerId,
            EntityId targetId,
            CombatDamageRules.Result damage,
            CombatAttackTimingRules.Result timing,
            CombatSystemHooks.Snapshot hooks,
            NpcLifecycleService.DamageResult lifecycle,
            int nextAttackDelayTicks
        ){
            this.attackerId=Objects.requireNonNull(attackerId,"attackerId");
            this.targetId=Objects.requireNonNull(targetId,"targetId");
            this.damage=Objects.requireNonNull(damage,"damage");
            this.timing=Objects.requireNonNull(timing,"timing");
            this.hooks=Objects.requireNonNull(hooks,"hooks");
            this.lifecycle=Objects.requireNonNull(lifecycle,"lifecycle");
            if(nextAttackDelayTicks<=0)
                throw new IllegalArgumentException(
                    "nextAttackDelayTicks="+nextAttackDelayTicks
                );
            this.nextAttackDelayTicks=nextAttackDelayTicks;
        }
    }

    static final class AttackResolution {
        final Delivery delivery;
        final EntityId attackerId;
        final EntityId targetId;
        final CombatDamageRules.Result damage;
        final CombatAttackTimingRules.Result timing;
        final CombatSystemHooks.Snapshot hooks;
        final NpcLifecycleService.DamageResult lifecycle;
        final NpcPvmDelayedHitService.Snapshot delayedHit;
        final int nextAttackDelayTicks;

        private AttackResolution(
            Delivery delivery,
            EntityId attackerId,
            EntityId targetId,
            CombatDamageRules.Result damage,
            CombatAttackTimingRules.Result timing,
            CombatSystemHooks.Snapshot hooks,
            NpcLifecycleService.DamageResult lifecycle,
            NpcPvmDelayedHitService.Snapshot delayedHit,
            int nextAttackDelayTicks
        ){
            this.delivery=
                Objects.requireNonNull(
                    delivery,
                    "delivery"
                );
            this.attackerId=
                Objects.requireNonNull(
                    attackerId,
                    "attackerId"
                );
            this.targetId=
                Objects.requireNonNull(
                    targetId,
                    "targetId"
                );
            this.damage=
                Objects.requireNonNull(
                    damage,
                    "damage"
                );
            this.timing=
                Objects.requireNonNull(
                    timing,
                    "timing"
                );
            this.hooks=
                Objects.requireNonNull(
                    hooks,
                    "hooks"
                );

            if(delivery==Delivery.IMMEDIATE){
                this.lifecycle=
                    Objects.requireNonNull(
                        lifecycle,
                        "lifecycle"
                    );
                if(delayedHit!=null)
                    throw new IllegalArgumentException(
                        "immediate resolution has delayed hit"
                    );
                this.delayedHit=null;
            }else{
                if(lifecycle!=null)
                    throw new IllegalArgumentException(
                        "scheduled resolution has immediate lifecycle"
                    );
                this.lifecycle=null;
                this.delayedHit=
                    Objects.requireNonNull(
                        delayedHit,
                        "delayedHit"
                    );
            }

            if(nextAttackDelayTicks<=0)
                throw new IllegalArgumentException(
                    "nextAttackDelayTicks="+
                    nextAttackDelayTicks
                );

            this.nextAttackDelayTicks=
                nextAttackDelayTicks;
        }

        static AttackResolution immediate(
            EntityId attackerId,
            EntityId targetId,
            Prepared prepared,
            NpcLifecycleService.DamageResult lifecycle,
            int nextAttackDelayTicks
        ){
            return new AttackResolution(
                Delivery.IMMEDIATE,
                attackerId,
                targetId,
                prepared.damage,
                prepared.timing,
                prepared.hooks,
                lifecycle,
                null,
                nextAttackDelayTicks
            );
        }

        static AttackResolution scheduled(
            EntityId attackerId,
            EntityId targetId,
            Prepared prepared,
            NpcPvmDelayedHitService.Snapshot delayedHit,
            int nextAttackDelayTicks
        ){
            return new AttackResolution(
                Delivery.SCHEDULED,
                attackerId,
                targetId,
                prepared.damage,
                prepared.timing,
                prepared.hooks,
                null,
                delayedHit,
                nextAttackDelayTicks
            );
        }
    }

    private static final class Prepared {
        final CombatDamageRules.Result damage;
        final CombatAttackTimingRules.Result timing;
        final CombatSystemHooks.Snapshot hooks;

        Prepared(
            CombatDamageRules.Result damage,
            CombatAttackTimingRules.Result timing,
            CombatSystemHooks.Snapshot hooks
        ){
            this.damage=Objects.requireNonNull(damage,"damage");
            this.timing=Objects.requireNonNull(timing,"timing");
            this.hooks=Objects.requireNonNull(hooks,"hooks");
        }
    }

    private final WorldPlayer owner;
    private final NpcLifecycleService lifecycle;
    private final CombatDamageRules damageRules;
    private final CombatAttackTimingRules timingRules;
    private final CombatSystemHooks hooks;

    NpcCombatResolutionService(
        WorldPlayer owner,
        NpcLifecycleService lifecycle,
        CombatDamageRules damageRules,
        CombatAttackTimingRules timingRules,
        CombatSystemHooks hooks
    ){
        this.owner=Objects.requireNonNull(owner,"owner");
        this.lifecycle=Objects.requireNonNull(lifecycle,"lifecycle");
        this.damageRules=Objects.requireNonNull(damageRules,"damageRules");
        this.timingRules=Objects.requireNonNull(timingRules,"timingRules");
        this.hooks=Objects.requireNonNull(hooks,"hooks");
    }

    String damageAuthority(){
        return damageRules.authority();
    }

    String damageFormula(){
        return damageRules.formula();
    }

    Result resolveImmediateOwned(
        World world,
        long expectedAttackerGeneration,
        WorldNpc target,
        int weaponId,
        CombatStyleRepository.Style style,
        long worldTick
    )throws java.io.IOException{
        Objects.requireNonNull(world,"world");
        WorldNpc checkedTarget=
            Objects.requireNonNull(target,"target");

        if(worldTick<0L)
            throw new IllegalArgumentException("worldTick="+worldTick);

        preflightOwnership(
            world,
            expectedAttackerGeneration,
            checkedTarget
        );

        CombatAttackTimingRules.Result timing=
            resolveTiming(
                weaponId
            );

        if(timing.hitDelayTicks!=0)
            throw delayedHitNotWired(
                timing
            );

        Prepared prepared=
            completePreparation(
                weaponId,
                style,
                worldTick,
                timing
            );

        NpcLifecycleService.DamageResult lifecycleResult=
            applyPreparedImmediate(
                world,
                expectedAttackerGeneration,
                checkedTarget,
                prepared,
                worldTick
            );

        return new Result(
            owner.id(),
            checkedTarget.id,
            prepared.damage,
            prepared.timing,
            prepared.hooks,
            lifecycleResult,
            nextAttackDelay(
                prepared.timing
            )
        );
    }

    AttackResolution resolveOwned(
        World world,
        long expectedAttackerGeneration,
        WorldNpc target,
        int weaponId,
        CombatStyleRepository.Style style,
        long worldTick,
        NpcPvmDelayedHitService delayedHits
    )throws java.io.IOException{
        Objects.requireNonNull(world,"world");
        WorldNpc checkedTarget=
            Objects.requireNonNull(target,"target");
        NpcPvmDelayedHitService checkedDelayedHits=
            Objects.requireNonNull(
                delayedHits,
                "delayedHits"
            );

        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        preflightOwnership(
            world,
            expectedAttackerGeneration,
            checkedTarget
        );

        CombatAttackTimingRules.Result timing=
            resolveTiming(
                weaponId
            );

        if(timing.hitDelayTicks>0)
            requireSharedClock(
                world,
                worldTick,
                "before hooks"
            );

        Prepared prepared=
            timing.hitDelayTicks>0
                ?completeDelayedPreparation(
                    world,
                    weaponId,
                    style,
                    worldTick,
                    timing
                )
                :completePreparation(
                    weaponId,
                    style,
                    worldTick,
                    timing
                );

        int nextAttackDelay=
            nextAttackDelay(
                prepared.timing
            );

        if(prepared.timing.hitDelayTicks==0){
            NpcLifecycleService.DamageResult
                lifecycleResult=
                    applyPreparedImmediate(
                        world,
                        expectedAttackerGeneration,
                        checkedTarget,
                        prepared,
                        worldTick
                    );

            return AttackResolution.immediate(
                owner.id(),
                checkedTarget.id,
                prepared,
                lifecycleResult,
                nextAttackDelay
            );
        }

        final NpcPvmDelayedHitService.Snapshot delayed;

        try{
            delayed=
                checkedDelayedHits.schedule(
                    owner,
                    expectedAttackerGeneration,
                    checkedTarget,
                    prepared.damage.damage,
                    prepared.timing.hitDelayTicks,
                    prepared.damage.authority,
                    prepared.damage.formula
                );
        }catch(IllegalStateException error){
            if(!world.players().owns(
                    owner,
                    expectedAttackerGeneration
                ))
                throw new StaleAttackerOwnershipException(
                    owner,
                    expectedAttackerGeneration,
                    error
                );

            if(world.npcs().byId(
                    checkedTarget.id
                )!=checkedTarget)
                throw new StaleTargetOwnershipException(
                    checkedTarget,
                    error
                );

            throw error;
        }catch(RuntimeException error){
            throw error;
        }catch(Error error){
            throw error;
        }catch(Exception error){
            throw new IllegalStateException(
                "unexpected delayed PvM scheduling failure",
                error
            );
        }

        return AttackResolution.scheduled(
            owner.id(),
            checkedTarget.id,
            prepared,
            delayed,
            nextAttackDelay
        );
    }

    private CombatAttackTimingRules.Result resolveTiming(
        int weaponId
    ){
        CombatWeaponProfile profile=
            CombatWeaponRepository.resolve(
                weaponId
            );
        V913WeaponRuntimeAuthority.Profile runtime=
            V913WeaponRuntimeAuthority.resolve(
                weaponId
            );

        return Objects.requireNonNull(
            timingRules.resolve(
                new CombatAttackTimingRules.Request(
                    weaponId,
                    profile,
                    runtime
                )
            ),
            "timing result"
        );
    }

    private Prepared completeDelayedPreparation(
        World world,
        int weaponId,
        CombatStyleRepository.Style style,
        long worldTick,
        CombatAttackTimingRules.Result timing
    ){
        CombatSystemHooks.Snapshot hookSnapshot=
            Objects.requireNonNull(
                hooks.beforeDamage(
                    CombatContext.NPC_PVM,
                    weaponId,
                    worldTick
                ),
                "hook snapshot"
            );

        requireSharedClock(
            world,
            worldTick,
            "after hooks"
        );

        CombatDamageRules.Result damage=
            Objects.requireNonNull(
                damageRules.calculate(
                    new CombatDamageRules.Request(
                        CombatContext.NPC_PVM,
                        weaponId,
                        style,
                        worldTick
                    )
                ),
                "damage result"
            );

        requireSharedClock(
            world,
            worldTick,
            "after damage"
        );

        return new Prepared(
            damage,
            timing,
            hookSnapshot
        );
    }

    private Prepared completePreparation(
        int weaponId,
        CombatStyleRepository.Style style,
        long worldTick,
        CombatAttackTimingRules.Result timing
    ){
        CombatSystemHooks.Snapshot hookSnapshot=
            Objects.requireNonNull(
                hooks.beforeDamage(
                    CombatContext.NPC_PVM,
                    weaponId,
                    worldTick
                ),
                "hook snapshot"
            );

        CombatDamageRules.Result damage=
            Objects.requireNonNull(
                damageRules.calculate(
                    new CombatDamageRules.Request(
                        CombatContext.NPC_PVM,
                        weaponId,
                        style,
                        worldTick
                    )
                ),
                "damage result"
            );

        return new Prepared(
            damage,
            timing,
            hookSnapshot
        );
    }

    private NpcLifecycleService.DamageResult
        applyPreparedImmediate(
            World world,
            long expectedAttackerGeneration,
            WorldNpc checkedTarget,
            Prepared prepared,
            long worldTick
        )throws java.io.IOException{
        final NpcLifecycleService.DamageResult[]
            lifecycleResult=
                new NpcLifecycleService.DamageResult[1];

        try{
            world.withOpenPlayerOwnership(
                owner,
                expectedAttackerGeneration,
                ()->{
                    requireTarget(
                        world,
                        checkedTarget,
                        null
                    );

                    lifecycleResult[0]=
                        lifecycle.applyDamage(
                            checkedTarget.id,
                            prepared.damage.damage,
                            worldTick
                        );
                }
            );
        }catch(IllegalStateException error){
            if(!world.players().owns(
                    owner,
                    expectedAttackerGeneration
                ))
                throw new StaleAttackerOwnershipException(
                    owner,
                    expectedAttackerGeneration,
                    error
                );

            if(world.npcs().byId(
                    checkedTarget.id
                )!=checkedTarget)
                throw new StaleTargetOwnershipException(
                    checkedTarget,
                    error
                );

            throw error;
        }

        return Objects.requireNonNull(
            lifecycleResult[0],
            "lifecycleResult"
        );
    }

    private static void requireSharedClock(
        World world,
        long worldTick,
        String phase
    ){
        long current=
            world.clock().tick();

        if(worldTick!=current)
            throw new IllegalStateException(
                "delayed PvM attack tick is not shared World clock tick phase="+
                phase+
                " supplied="+
                worldTick+
                " world="+
                current
            );
    }

    private static int nextAttackDelay(
        CombatAttackTimingRules.Result timing
    ){
        int delay=
            timing.attackSpeedTicks>0
                ?timing.attackSpeedTicks
                :4;

        return Math.max(
            1,
            delay
        );
    }

    private static IllegalStateException delayedHitNotWired(
        CombatAttackTimingRules.Result timing
    ){
        return new IllegalStateException(
            "PvM delayed-hit scheduler not yet wired hitDelayTicks="+
            timing.hitDelayTicks+
            " authority="+
            timing.hitDelayAuthority
        );
    }

    private void preflightOwnership(
        World world,
        long expectedAttackerGeneration,
        WorldNpc target
    ){
        requireAttacker(
            world,
            expectedAttackerGeneration,
            null
        );
        requireTarget(
            world,
            target,
            null
        );
    }

    private void requireAttacker(
        World world,
        long expectedGeneration,
        Throwable cause
    ){
        if(!world.players().owns(
                owner,
                expectedGeneration
            ))
            throw new StaleAttackerOwnershipException(
                owner,
                expectedGeneration,
                cause
            );
    }

    private static void requireTarget(
        World world,
        WorldNpc target,
        Throwable cause
    ){
        if(world.npcs().byId(
                target.id
            )!=target)
            throw new StaleTargetOwnershipException(
                target,
                cause
            );
    }
}
