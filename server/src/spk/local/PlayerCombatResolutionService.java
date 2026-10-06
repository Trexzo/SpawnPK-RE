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

    enum Delivery {
        IMMEDIATE,
        SCHEDULED
    }

    static final class AttackResolution {
        final CombatDamageRules.Result damage;
        final CombatAttackTimingRules.Result timing;
        final CombatSystemHooks.Snapshot hooks;
        final Delivery delivery;
        final PlayerLifecycleService.DamageResult lifecycle;
        final PlayerPvpDelayedHitService.Snapshot scheduledHit;
        final int nextAttackDelayTicks;

        AttackResolution(
            CombatDamageRules.Result damage,
            CombatAttackTimingRules.Result timing,
            CombatSystemHooks.Snapshot hooks,
            Delivery delivery,
            PlayerLifecycleService.DamageResult lifecycle,
            PlayerPvpDelayedHitService.Snapshot scheduledHit,
            int nextAttackDelayTicks
        ){
            this.damage=Objects.requireNonNull(damage,"damage");
            this.timing=Objects.requireNonNull(timing,"timing");
            this.hooks=Objects.requireNonNull(hooks,"hooks");
            this.delivery=Objects.requireNonNull(delivery,"delivery");

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

    CombatOutcomeObserver outcomeObserver(){
        return this::publishOutcome;
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

        requireImmediateTiming(
            prepared.timing
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

        requireImmediateTiming(
            prepared.timing
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
                                    owner.username(),
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

    AttackResolution resolveOwned(
        World world,
        long expectedAttackerGeneration,
        WorldPlayer target,
        long expectedTargetGeneration,
        int weaponId,
        CombatStyleRepository.Style style,
        long worldTick,
        PlayerPvpDelayedHitService delayedHits
    )throws Exception{
        World checkedWorld=
            Objects.requireNonNull(
                world,
                "world"
            );
        WorldPlayer checkedTarget=
            Objects.requireNonNull(
                target,
                "target"
            );
        PlayerPvpDelayedHitService checkedDelayedHits=
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
                checkedWorld
            ))
            throw new IllegalArgumentException(
                "delayedHits must be bound to resolver World"
            );

        if(!checkedWorld.players().owns(
                owner,
                expectedAttackerGeneration
            ))
            throw new StaleAttackerOwnershipException(
                owner,
                expectedAttackerGeneration,
                null
            );

        if(!checkedWorld.players().owns(
                checkedTarget,
                expectedTargetGeneration
            ))
            throw new StaleTargetOwnershipException(
                checkedTarget,
                expectedTargetGeneration,
                null
            );

        Prepared prepared=
            prepare(
                weaponId,
                style,
                worldTick
            );

        int cadence=
            nextAttackDelayTicks(
                prepared.timing
            );

        if(prepared.timing.hitDelayTicks==0){
            final PlayerLifecycleService.DamageResult[]
                lifecycle=
                    new PlayerLifecycleService.DamageResult[1];

            try{
                checkedWorld.withOpenPlayerOwnership(
                    owner,
                    expectedAttackerGeneration,
                    ()->checkedWorld.withOpenPlayerOwnership(
                        checkedTarget,
                        expectedTargetGeneration,
                        ()->{
                            PlayerLifecycleService.DamageResult applied=
                                applyDamage(
                                    checkedTarget,
                                    weaponId,
                                    worldTick,
                                    prepared
                                );

                            lifecycle[0]=applied;

                            if(applied.died&&
                               !applied.ignoredDead)
                                checkedTarget.lifecycle()
                                    .attributeCurrentDeath(
                                        checkedTarget.lifecycle()
                                            .deathSequence(),
                                        owner.id(),
                                        expectedAttackerGeneration,
                                        "PLAYER_PVP"
                                    );
                        }
                    )
                );
            }catch(IllegalStateException error){
                if(!checkedWorld.players().owns(
                        owner,
                        expectedAttackerGeneration
                    ))
                    throw new StaleAttackerOwnershipException(
                        owner,
                        expectedAttackerGeneration,
                        error
                    );

                if(!checkedWorld.players().owns(
                        checkedTarget,
                        expectedTargetGeneration
                    ))
                    throw new StaleTargetOwnershipException(
                        checkedTarget,
                        expectedTargetGeneration,
                        error
                    );

                throw error;
            }

            Result immediate=
                finish(
                    checkedTarget,
                    worldTick,
                    prepared,
                    lifecycle[0]
                );

            return new AttackResolution(
                immediate.damage,
                immediate.timing,
                immediate.hooks,
                Delivery.IMMEDIATE,
                immediate.lifecycle,
                null,
                immediate.nextAttackDelayTicks
            );
        }

        if(worldTick!=checkedWorld.clock().tick())
            throw new IllegalStateException(
                "delayed PvP resolution requires shared world clock tick expected="+
                checkedWorld.clock().tick()+
                " actual="+
                worldTick
            );

        PlayerPvpDelayedHitService.Snapshot scheduled=
            checkedDelayedHits.scheduleAtExpectedTick(
                owner,
                expectedAttackerGeneration,
                checkedTarget,
                expectedTargetGeneration,
                prepared.damage.damage,
                prepared.timing.hitDelayTicks,
                prepared.damage.authority,
                prepared.damage.formula,
                worldTick
            );

        return new AttackResolution(
            prepared.damage,
            prepared.timing,
            prepared.hooks,
            Delivery.SCHEDULED,
            null,
            scheduled,
            cadence
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

    private static void requireImmediateTiming(
        CombatAttackTimingRules.Result timing
    ){
        if(timing.hitDelayTicks!=0)
            throw new IllegalStateException(
                "PvP immediate resolver requires hitDelayTicks=0 actual="+
                timing.hitDelayTicks+
                " authority="+timing.hitDelayAuthority
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

        return new Result(
            prepared.damage,
            prepared.timing,
            prepared.hooks,
            lifecycle,
            nextAttackDelayTicks(
                prepared.timing
            )
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
