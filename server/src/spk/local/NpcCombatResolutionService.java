package spk.local;

import java.util.Objects;

/**
 * Authoritative player-to-NPC immediate PvM resolution.
 *
 * Caller/world ownership is explicit.  Presentation, delayed-hit scheduling,
 * kill credit, rewards, drops, XP and respawn remain outside this service.
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

    enum Delivery {
        IMMEDIATE,
        SCHEDULED
    }

    static final class AttackResolution {
        final EntityId attackerId;
        final EntityId targetId;
        final CombatDamageRules.Result damage;
        final CombatAttackTimingRules.Result timing;
        final CombatSystemHooks.Snapshot hooks;
        final Delivery delivery;
        final NpcLifecycleService.DamageResult lifecycle;
        final NpcPvmDelayedHitService.Snapshot scheduledHit;
        final int nextAttackDelayTicks;

        AttackResolution(
            EntityId attackerId,
            EntityId targetId,
            CombatDamageRules.Result damage,
            CombatAttackTimingRules.Result timing,
            CombatSystemHooks.Snapshot hooks,
            Delivery delivery,
            NpcLifecycleService.DamageResult lifecycle,
            NpcPvmDelayedHitService.Snapshot scheduledHit,
            int nextAttackDelayTicks
        ){
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
            this.delivery=
                Objects.requireNonNull(
                    delivery,
                    "delivery"
                );

            if(delivery==Delivery.IMMEDIATE){
                this.lifecycle=
                    Objects.requireNonNull(
                        lifecycle,
                        "lifecycle"
                    );
                if(scheduledHit!=null)
                    throw new IllegalArgumentException(
                        "IMMEDIATE resolution cannot carry scheduledHit"
                    );
                this.scheduledHit=null;
            }else{
                if(lifecycle!=null)
                    throw new IllegalArgumentException(
                        "SCHEDULED resolution cannot carry lifecycle"
                    );
                this.lifecycle=null;
                this.scheduledHit=
                    Objects.requireNonNull(
                        scheduledHit,
                        "scheduledHit"
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

    boolean isBoundToLifecycle(
        NpcLifecycleService expectedLifecycle
    ){
        return lifecycle==expectedLifecycle;
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

        requireAttacker(
            world,
            expectedAttackerGeneration,
            null
        );
        requireTarget(
            world,
            checkedTarget,
            null
        );

        CombatAttackTimingRules.Result timing=
            resolveTiming(
                weaponId
            );

        if(timing.hitDelayTicks!=0)
            throw new IllegalStateException(
                "PvM delayed-hit scheduler not yet wired hitDelayTicks="+
                timing.hitDelayTicks+
                " authority="+
                timing.hitDelayAuthority
            );

        Prepared prepared=
            prepareAfterTiming(
                weaponId,
                style,
                worldTick,
                timing
            );

        NpcLifecycleService.DamageResult lifecycleResult=
            applyImmediateOwned(
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
            nextAttackDelayTicks(
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
    )throws Exception{
        World checkedWorld=
            Objects.requireNonNull(
                world,
                "world"
            );
        WorldNpc checkedTarget=
            Objects.requireNonNull(
                target,
                "target"
            );
        NpcPvmDelayedHitService checkedDelayedHits=
            Objects.requireNonNull(
                delayedHits,
                "delayedHits"
            );

        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+
                worldTick
            );

        if(!checkedDelayedHits.isBoundTo(
                checkedWorld,
                lifecycle
            ))
            throw new IllegalArgumentException(
                "delayedHits must be bound to resolver World + NpcLifecycleService"
            );

        requireAttacker(
            checkedWorld,
            expectedAttackerGeneration,
            null
        );
        requireTarget(
            checkedWorld,
            checkedTarget,
            null
        );

        CombatAttackTimingRules.Result timing=
            resolveTiming(
                weaponId
            );

        if(timing.hitDelayTicks>0&&
           worldTick!=checkedWorld.clock().tick())
            throw new IllegalStateException(
                "delayed PvM resolution requires shared world clock tick expected="+
                checkedWorld.clock().tick()+
                " actual="+
                worldTick
            );

        Prepared prepared=
            prepareAfterTiming(
                weaponId,
                style,
                worldTick,
                timing
            );

        int cadence=
            nextAttackDelayTicks(
                prepared.timing
            );

        if(prepared.timing.hitDelayTicks==0){
            NpcLifecycleService.DamageResult lifecycleResult=
                applyImmediateOwned(
                    checkedWorld,
                    expectedAttackerGeneration,
                    checkedTarget,
                    prepared,
                    worldTick
                );

            return new AttackResolution(
                owner.id(),
                checkedTarget.id,
                prepared.damage,
                prepared.timing,
                prepared.hooks,
                Delivery.IMMEDIATE,
                lifecycleResult,
                null,
                cadence
            );
        }

        NpcPvmDelayedHitService.Snapshot scheduled=
            checkedDelayedHits.scheduleAtExpectedTick(
                owner,
                expectedAttackerGeneration,
                checkedTarget,
                prepared.damage.damage,
                prepared.timing.hitDelayTicks,
                prepared.damage.authority,
                prepared.damage.formula,
                worldTick
            );

        return new AttackResolution(
            owner.id(),
            checkedTarget.id,
            prepared.damage,
            prepared.timing,
            prepared.hooks,
            Delivery.SCHEDULED,
            null,
            scheduled,
            cadence
        );
    }

    private NpcLifecycleService.DamageResult applyImmediateOwned(
        World world,
        long expectedAttackerGeneration,
        WorldNpc checkedTarget,
        Prepared prepared,
        long worldTick
    )throws java.io.IOException{
        final NpcLifecycleService.DamageResult[] lifecycleResult=
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
                        lifecycle.applyDamageOwned(
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

    private Prepared prepareAfterTiming(
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

    private static int nextAttackDelayTicks(
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
