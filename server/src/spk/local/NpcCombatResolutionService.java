package spk.local;

import java.util.Objects;

/**
 * Authoritative player-to-NPC PvM damage resolution.
 *
 * The attacker must remain owned by the supplied World generation through the
 * canonical NPC lifecycle mutation. Target NPC HP/death mutation is itself
 * linearized by NpcLifecycleService -> WorldNpcRegistry ownership.
 *
 * Routing, hit presentation, packet-65 publication, loot/XP/kill-credit and
 * respawn policy remain outside this service.
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
                "PvM attacker ownership changed: attacker="+
                attacker.id()+
                " expectedGeneration="+
                expectedGeneration,
                cause
            );
            this.attackerId=attacker.id();
            this.expectedGeneration=expectedGeneration;
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
            this.lifecycle=
                Objects.requireNonNull(
                    lifecycle,
                    "lifecycle"
                );

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
            this.damage=damage;
            this.timing=timing;
            this.hooks=hooks;
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
        this.owner=
            Objects.requireNonNull(
                owner,
                "owner"
            );
        this.lifecycle=
            Objects.requireNonNull(
                lifecycle,
                "lifecycle"
            );
        this.damageRules=
            Objects.requireNonNull(
                damageRules,
                "damageRules"
            );
        this.timingRules=
            Objects.requireNonNull(
                timingRules,
                "timingRules"
            );
        this.hooks=
            Objects.requireNonNull(
                hooks,
                "hooks"
            );
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

        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        if(!checkedWorld.players().owns(
                owner,
                expectedAttackerGeneration))
            throw new StaleAttackerOwnershipException(
                owner,
                expectedAttackerGeneration,
                null
            );

        Prepared prepared=
            prepare(
                weaponId,
                style,
                worldTick
            );

        final NpcLifecycleService.DamageResult[]
            lifecycleResult=
                new NpcLifecycleService.DamageResult[1];

        try{
            checkedWorld.withOpenPlayerOwnership(
                owner,
                expectedAttackerGeneration,
                ()->
                    lifecycleResult[0]=
                        lifecycle.applyDamage(
                            checkedTarget.id,
                            prepared.damage.damage,
                            worldTick
                        )
            );
        }catch(IllegalStateException error){
            if(!checkedWorld.players().owns(
                    owner,
                    expectedAttackerGeneration))
                throw new StaleAttackerOwnershipException(
                    owner,
                    expectedAttackerGeneration,
                    error
                );

            throw error;
        }

        int delay=
            prepared.timing.attackSpeedTicks>0
                ?prepared.timing.attackSpeedTicks
                :4;

        return new Result(
            owner.id(),
            checkedTarget.id,
            prepared.damage,
            prepared.timing,
            prepared.hooks,
            Objects.requireNonNull(
                lifecycleResult[0],
                "lifecycle result"
            ),
            Math.max(
                1,
                delay
            )
        );
    }

    private Prepared prepare(
        int weaponId,
        CombatStyleRepository.Style style,
        long worldTick
    ){
        CombatWeaponProfile profile=
            CombatWeaponRepository.resolve(
                weaponId
            );

        V913WeaponRuntimeAuthority.Profile runtime=
            V913WeaponRuntimeAuthority.resolve(
                weaponId
            );

        CombatAttackTimingRules.Result timing=
            Objects.requireNonNull(
                timingRules.resolve(
                    new CombatAttackTimingRules.Request(
                        weaponId,
                        profile,
                        runtime
                    )
                ),
                "timing result"
            );

        if(timing.hitDelayTicks!=0)
            throw new IllegalStateException(
                "PvM delayed-hit scheduler not yet wired hitDelayTicks="+
                timing.hitDelayTicks+
                " authority="+
                timing.hitDelayAuthority
            );

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
}
