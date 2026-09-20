package spk.local;

import java.util.*;

public final class CombatHitSchedulerTest {
    public static void main(String[] args){
        CombatState state=new CombatState();
        CombatHitScheduler scheduler=
            new CombatHitScheduler(state);

        NpcEntity target=
            new NpcEntity(
                77,
                1489,
                3090,
                3490
            );

        CombatDamageRules.Result rules=
            CombatDamageRules.localLabFallback()
                .calculate(
                    new CombatDamageRules.Request(
                        CombatContext.PLAYER_PVM,
                        21566,
                        null,
                        10L
                    )
                );

        CombatHitScheduler.PendingHit hit=
            scheduler.schedule(
                target,
                rules.damage,
                6,
                255,
                255,
                -1,
                10L,
                2L,
                rules,
                "CUSTOM_LOCALLAB_TEST_DELAY"
            );

        if(hit.dueTick!=12L||
           scheduler.pending()!=1)
            throw new AssertionError(
                "scheduled hit timing mismatch "+
                hit
            );

        if(!scheduler.drainDue(10L).isEmpty())
            throw new AssertionError(
                "hit published on attack tick"
            );

        // Target/cycle cancellation must not erase an already-launched hit.
        state.clear();

        if(!scheduler.drainDue(11L).isEmpty())
            throw new AssertionError(
                "hit published one tick early"
            );

        List<CombatHitScheduler.PendingHit> due=
            scheduler.drainDue(12L);

        if(due.size()!=1||
           due.get(0)!=hit||
           scheduler.pending()!=0)
            throw new AssertionError(
                "due hit not drained deterministically"
            );

        if(!"CUSTOM_LOCALLAB".equals(
                hit.damageAuthority)||
           !"CUSTOM_LOCALLAB_FLAT_10_V1".equals(
                hit.damageFormula)||
           !"CUSTOM_LOCALLAB_TEST_DELAY".equals(
                hit.delayAuthority))
            throw new AssertionError(
                "pending-hit provenance changed "+
                hit
            );

        CombatState compatibilityState=
            new CombatState();
        CombatHitScheduler compatibility=
            new CombatHitScheduler(
                compatibilityState
            );

        CombatHitScheduler.PendingHit immediate=
            compatibility.scheduleCompatibility(
                target,
                10,
                6,
                255,
                255,
                -1,
                20L,
                rules
            );

        if(immediate.dueTick!=20L||
           compatibility.drainDue(20L).size()!=1)
            throw new AssertionError(
                "zero-delay compatibility behavior changed"
            );

        System.out.println(
            "COMBAT_HIT_SCHEDULER_PASS "+
            "delayedAttackTick=10 dueTick=12 "+
            "survivesTargetClear=true "+
            "compatDelayTicks="+
            CombatHitScheduler.LOCALLAB_COMPAT_DELAY_TICKS+
            " compatAuthority="+
            CombatHitScheduler.DELAY_AUTHORITY
        );
    }
}
