package spk.local;

import java.util.Objects;

/**
 * Authoritative PvP damage/lifecycle bridge.
 *
 * Player attack presentation and routing remain outside this service. This
 * service owns only resolved PvP damage/timing provenance and canonical target
 * HP/death mutation.
 */
final class PlayerCombatResolutionService {
    private static final CombatOutcomeObserver NO_OUTCOME_OBSERVER =
        outcome -> {};

    static final class Result {
        final CombatDamageRules.Result damage;
        final CombatAttackTimingRules.Result timing;
        final CombatSystemHooks.Snapshot hooks;
        final PlayerLifecycleService.DamageResult lifecycle;
        final int nextAttackDelayTicks;

        Result(
            CombatDamageRules.Result damage,
            CombatAttackTimingRules.Result timing,
            CombatSystemHooks.Snapshot hooks,
            PlayerLifecycleService.DamageResult lifecycle,
            int nextAttackDelayTicks
        ){
            this.damage=damage;
            this.timing=timing;
            this.hooks=hooks;
            this.lifecycle=lifecycle;
            this.nextAttackDelayTicks=nextAttackDelayTicks;
        }

        @Override public String toString(){
            return "PlayerCombatResolution{damage="+damage+
                ",timing="+timing+
                ",hooks="+hooks+
                ",lifecycle="+lifecycle+
                ",nextAttackDelayTicks="+nextAttackDelayTicks+"}";
        }
    }

    private final WorldPlayer owner;
    private final CombatDamageRules damageRules;
    private final CombatAttackTimingRules timingRules;
    private final CombatSystemHooks hooks;
    private final CombatOutcomeObserver outcomeObserver;

    PlayerCombatResolutionService(
        WorldPlayer owner,
        CombatDamageRules damageRules,
        CombatAttackTimingRules timingRules,
        CombatSystemHooks hooks
    ){
        this(
            owner,
            damageRules,
            timingRules,
            hooks,
            NO_OUTCOME_OBSERVER
        );
    }

    PlayerCombatResolutionService(
        WorldPlayer owner,
        CombatDamageRules damageRules,
        CombatAttackTimingRules timingRules,
        CombatSystemHooks hooks,
        CombatOutcomeObserver outcomeObserver
    ){
        this.owner=Objects.requireNonNull(owner,"owner");
        this.damageRules=Objects.requireNonNull(damageRules,"damageRules");
        this.timingRules=Objects.requireNonNull(timingRules,"timingRules");
        this.hooks=Objects.requireNonNull(hooks,"hooks");
        this.outcomeObserver=
            Objects.requireNonNull(
                outcomeObserver,
                "outcomeObserver"
            );
    }

    String damageAuthority(){
        return damageRules.authority();
    }

    String damageFormula(){
        return damageRules.formula();
    }

    Result resolveImmediate(
        WorldPlayer target,
        int weaponId,
        CombatStyleRepository.Style style,
        long worldTick
    ){
        Objects.requireNonNull(target,"target");

        CombatWeaponProfile profile=
            CombatWeaponRepository.resolve(weaponId);
        V913WeaponRuntimeAuthority.Profile runtime=
            V913WeaponRuntimeAuthority.resolve(weaponId);

        CombatAttackTimingRules.Result timing=
            timingRules.resolve(
                new CombatAttackTimingRules.Request(
                    weaponId,
                    profile,
                    runtime
                )
            );

        if(timing.hitDelayTicks!=0){
            throw new IllegalStateException(
                "PvP delayed-hit scheduler not yet wired hitDelayTicks="+
                timing.hitDelayTicks+
                " authority="+timing.hitDelayAuthority
            );
        }

        CombatSystemHooks.Snapshot snapshot=
            hooks.beforeDamage(
                CombatContext.PLAYER_PVP,
                weaponId,
                worldTick
            );

        CombatDamageRules.Result damage=
            damageRules.calculate(
                new CombatDamageRules.Request(
                    CombatContext.PLAYER_PVP,
                    weaponId,
                    style,
                    worldTick
                )
            );

        PlayerLifecycleService.DamageResult lifecycle=
            new PlayerLifecycleService(target).applyDamage(
                damage.damage,
                worldTick,
                "PVP_ATTACK attacker="+owner.id()+
                " weapon="+weaponId+
                " damageAuthority="+damage.authority
            );

        if(lifecycle.died&&!lifecycle.ignoredDead){
            publishOutcome(
                new CombatOutcome(
                    owner.id().toString(),
                    target.id().toString(),
                    CombatOutcomeType.PLAYER_KILL,
                    CombatOutcomeContext.PLAYER_PVP,
                    worldTick,
                    PlayerLifecycleService.AUTHORITY
                )
            );

            publishOutcome(
                new CombatOutcome(
                    owner.id().toString(),
                    target.id().toString(),
                    CombatOutcomeType.PLAYER_DEATH,
                    CombatOutcomeContext.PLAYER_PVP,
                    worldTick,
                    PlayerLifecycleService.AUTHORITY
                )
            );
        }

        int delay=
            timing.attackSpeedTicks>0
                ?timing.attackSpeedTicks
                :4;

        return new Result(
            damage,
            timing,
            snapshot,
            lifecycle,
            Math.max(1,delay)
        );
    }

    private void publishOutcome(
        CombatOutcome outcome
    ){
        try{
            outcomeObserver.onCombatOutcome(
                outcome
            );
        }catch(RuntimeException error){
            System.err.println(
                "[combat] outcome observer failure"+
                " type="+outcome.type()+
                " attacker="+outcome.attacker()+
                " victim="+outcome.victim()+
                " tick="+outcome.worldTick()+
                " error="+error
            );
        }
    }
}
