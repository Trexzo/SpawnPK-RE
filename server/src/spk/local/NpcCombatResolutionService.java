package spk.local;

import java.util.Objects;

/**
 * Authoritative player-to-NPC PvM damage resolution.
 *
 * Routing, attack presentation, packet-65 publication, accuracy/loot/XP,
 * kill-credit and respawn policy remain outside this service.
 */
final class NpcCombatResolutionService {
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

    Result resolveImmediate(
        WorldNpc target,
        int weaponId,
        CombatStyleRepository.Style style,
        long worldTick
    ){
        WorldNpc checkedTarget=
            Objects.requireNonNull(
                target,
                "target"
            );

        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

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

        NpcLifecycleService.DamageResult
            lifecycleResult=
                lifecycle.applyDamage(
                    checkedTarget.id,
                    damage.damage,
                    worldTick
                );

        int delay=
            timing.attackSpeedTicks>0
                ?timing.attackSpeedTicks
                :4;

        return new Result(
            owner.id(),
            checkedTarget.id,
            damage,
            timing,
            hookSnapshot,
            lifecycleResult,
            Math.max(1,delay)
        );
    }
}
