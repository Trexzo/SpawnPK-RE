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
                "PvP attacker ownership changed: attacker="+
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
        final long expectedGeneration;

        StaleTargetOwnershipException(
            WorldPlayer target,
            long expectedGeneration,
            Throwable cause
        ){
            super(
                "PvP target ownership changed: target="+
                target.id()+
                " expectedGeneration="+
                expectedGeneration,
                cause
            );
            this.targetId=target.id();
            this.expectedGeneration=expectedGeneration;
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

        Prepared prepared=
            prepare(
                weaponId,
                style,
                worldTick
            );

        PlayerLifecycleService.DamageResult lifecycle=
            applyDamage(
                target,
                weaponId,
                worldTick,
                prepared
            );

        return finish(
            target,
            worldTick,
            prepared,
            lifecycle
        );
    }

    Result resolveImmediateOwned(
        World world,
        WorldPlayer target,
        long expectedGeneration,
        int weaponId,
        CombatStyleRepository.Style style,
        long worldTick
    )throws java.io.IOException{
        return resolveImmediateOwned(
            world,
            owner.generation(),
            target,
            expectedGeneration,
            weaponId,
            style,
            worldTick
        );
    }

    Result resolveImmediateOwned(
        World world,
        long expectedAttackerGeneration,
        WorldPlayer target,
        long expectedTargetGeneration,
        int weaponId,
        CombatStyleRepository.Style style,
        long worldTick
    )throws java.io.IOException{
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(target,"target");

        if(!world.players().owns(
                owner,
                expectedAttackerGeneration
            ))
            throw new StaleAttackerOwnershipException(
                owner,
                expectedAttackerGeneration,
                null
            );

        if(!world.players().owns(
                target,
                expectedTargetGeneration
            ))
            throw new StaleTargetOwnershipException(
                target,
                expectedTargetGeneration,
                null
            );

        Prepared prepared=
            prepare(
                weaponId,
                style,
                worldTick
            );

        final PlayerLifecycleService.DamageResult[] lifecycle=
            new PlayerLifecycleService.DamageResult[1];

        try{
            world.withOpenPlayerOwnership(
                owner,
                expectedAttackerGeneration,
                ()->world.withOpenPlayerOwnership(
                    target,
                    expectedTargetGeneration,
                    ()->{
                        PlayerLifecycleService.DamageResult applied=
                            applyDamage(
                                target,
                                weaponId,
                                worldTick,
                                prepared
                            );

                        lifecycle[0]=applied;

                        if(applied.died&&
                           !applied.ignoredDead)
                            target.lifecycle()
                                .attributeCurrentDeath(
                                    target.lifecycle()
                                        .deathSequence(),
                                    owner.id(),
                                    expectedAttackerGeneration,
                                    "PLAYER_PVP"
                                );
                    }
                )
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

            if(!world.players().owns(
                    target,
                    expectedTargetGeneration
                ))
                throw new StaleTargetOwnershipException(
                    target,
                    expectedTargetGeneration,
                    error
                );

            throw error;
        }

        return finish(
            target,
            worldTick,
            prepared,
            lifecycle[0]
        );
    }

    private Prepared prepare(
        int weaponId,
        CombatStyleRepository.Style style,
        long worldTick
    ){
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

        return new Prepared(
            damage,
            timing,
            snapshot
        );
    }

    private PlayerLifecycleService.DamageResult applyDamage(
        WorldPlayer target,
        int weaponId,
        long worldTick,
        Prepared prepared
    ){
        return new PlayerLifecycleService(target).applyDamage(
            prepared.damage.damage,
            worldTick,
            "PVP_ATTACK attacker="+owner.id()+
            " weapon="+weaponId+
            " damageAuthority="+prepared.damage.authority
        );
    }

    private Result finish(
        WorldPlayer target,
        long worldTick,
        Prepared prepared,
        PlayerLifecycleService.DamageResult lifecycle
    ){
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
            prepared.timing.attackSpeedTicks>0
                ?prepared.timing.attackSpeedTicks
                :4;

        return new Result(
            prepared.damage,
            prepared.timing,
            prepared.hooks,
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
