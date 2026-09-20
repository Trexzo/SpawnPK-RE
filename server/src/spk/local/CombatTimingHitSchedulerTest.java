package spk.local;

import java.io.*;

public final class CombatTimingHitSchedulerTest {
    public static void main(String[] args)throws Exception{
        testRecoveredCompatibilityTiming();
        testStandaloneHitScheduler();
        testDelayedHitEngineIntegration();

        System.out.println(
            "COMBAT_TIMING_HIT_SCHEDULER_PASS "+
            "cadenceSeparated=true "+
            "hitDelaySeparated=true "+
            "delayedPublication=true "+
            "damageRuleIndependent=true"
        );
    }

    private static void testRecoveredCompatibilityTiming(){
        int weaponId=21566;
        CombatWeaponProfile profile=
            CombatWeaponRepository.resolve(
                weaponId
            );
        V913WeaponRuntimeAuthority.Profile runtime=
            V913WeaponRuntimeAuthority.resolve(
                weaponId
            );

        CombatAttackTimingRules.Result timing=
            CombatAttackTimingRules
                .recoveredCompatibility()
                .resolve(
                    new CombatAttackTimingRules.Request(
                        weaponId,
                        profile,
                        runtime
                    )
                );

        if(timing.hitDelayTicks!=0)
            throw new AssertionError(
                "current compatibility hit delay changed "+
                timing
            );

        if(!"CUSTOM_LOCALLAB_COMPATIBILITY".equals(
                timing.hitDelayAuthority))
            throw new AssertionError(
                "hit-delay provenance changed "+
                timing
            );

        if(!"CURRENT_BEHAVIOR_IMMEDIATE_0_TICKS".equals(
                timing.hitDelayRule))
            throw new AssertionError(
                "hit-delay rule changed "+
                timing
            );
    }

    private static void testStandaloneHitScheduler(){
        CombatState state=
            new CombatState();

        CombatHitScheduler.ScheduledHit hit=
            new CombatHitScheduler.ScheduledHit(
                7,
                CombatTargetRepository.PVM_DUMMY_DEF,
                10,
                1,
                255,
                255,
                -1,
                "CUSTOM_LOCALLAB",
                "TEST",
                5L
            );

        CombatHitScheduler.schedule(
            state,
            hit
        );

        if(CombatHitScheduler.consumeDue(
                state,
                4L)!=null)
            throw new AssertionError(
                "hit published before scheduled tick"
            );

        if(state.pendingHit==null)
            throw new AssertionError(
                "pending hit disappeared early"
            );

        CombatHitScheduler.ScheduledHit due=
            CombatHitScheduler.consumeDue(
                state,
                5L
            );

        if(due!=hit)
            throw new AssertionError(
                "scheduled hit not returned at due tick"
            );

        if(state.pendingHit!=null)
            throw new AssertionError(
                "due hit remained queued"
            );
    }

    private static void testDelayedHitEngineIntegration()
        throws Exception{
        final CombatAttackTimingRules delayed=
            new CombatAttackTimingRules(){
                @Override public Result resolve(
                    Request request
                ){
                    return new Result(
                        4,
                        2,
                        "TEST_CADENCE",
                        "CUSTOM_LOCALLAB_TEST",
                        "TEST_DELAY_2_TICKS"
                    );
                }
            };

        CombatState state=
            new CombatState();

        CombatEngine combat=
            new CombatEngine(
                state,
                new DevAuthorityWorkbench(),
                CombatDamageRules.localLabFallback(),
                delayed
            );

        MovementState movement=
            new MovementState();

        EquipmentState equipment=
            new EquipmentState();
        equipment.setWeapon(21566);

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    new int[]{4,3,2,1}
                )
            );

        NpcEntity target=
            npcs.spawnMirroredNpc(
                CombatTargetRepository.PVM_DUMMY_DEF,
                movement.x()+1,
                movement.y(),
                null,
                movement,
                writer
            );

        String request=
            combat.request(
                target,
                movement,
                equipment.weapon(),
                1000L
            );

        if(!request.startsWith(
                "TARGET_ACQUIRED"))
            throw new AssertionError(
                "target not acquired "+
                request
            );

        String attack=
            combat.tick(
                movement,
                npcs,
                equipment,
                writer,
                10L,
                null,
                null
            );

        if(attack==null||
           !attack.startsWith(
                "M2_ATTACK_SENT"))
            throw new AssertionError(
                "attack presentation missing "+
                attack
            );

        if(!attack.contains(
                "hitDelayTicks=2")||
           !attack.contains(
                "hitDelayAuthority=CUSTOM_LOCALLAB_TEST")||
           !attack.contains(
                "cadenceAuthority=TEST_CADENCE"))
            throw new AssertionError(
                "timing provenance missing "+
                attack
            );

        if(combat.consumeLastDamage()!=0)
            throw new AssertionError(
                "damage published on attack tick despite delay"
            );

        if(state.pendingHit==null||
           state.pendingHit.scheduledTick!=12L)
            throw new AssertionError(
                "hit not scheduled for tick 12 "+
                state.pendingHit
            );

        String beforeDue=
            combat.tick(
                movement,
                npcs,
                equipment,
                writer,
                11L,
                null,
                null
            );

        if(beforeDue!=null)
            throw new AssertionError(
                "unexpected tick-11 publication "+
                beforeDue
            );

        if(combat.consumeLastDamage()!=0)
            throw new AssertionError(
                "damage published before due tick"
            );

        String due=
            combat.tick(
                movement,
                npcs,
                equipment,
                writer,
                12L,
                null,
                null
            );

        if(due==null||
           !due.startsWith(
                "M2_HIT_PUBLISHED"))
            throw new AssertionError(
                "scheduled hit not published "+
                due
            );

        int damage=
            combat.consumeLastDamage();

        if(damage!=10)
            throw new AssertionError(
                "delayed damage changed expected=10 actual="+
                damage
            );

        if(state.pendingHit!=null)
            throw new AssertionError(
                "pending hit survived publication"
            );

        if(state.nextAttackTick!=14L)
            throw new AssertionError(
                "attack cadence changed next="+
                state.nextAttackTick
            );
    }
}
