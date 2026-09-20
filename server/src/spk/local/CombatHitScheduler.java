package spk.local;

import java.util.*;

/**
 * Canonical pending-hit scheduler.
 *
 * Scheduling is separate from damage calculation and attack presentation.
 * Exact SpawnPK hit-delay rules are not recovered; the current compatibility
 * provider uses an explicitly labeled zero-tick delay.
 */
final class CombatHitScheduler {
    static final long LOCALLAB_COMPAT_DELAY_TICKS=0L;
    static final String DELAY_AUTHORITY="CUSTOM_LOCALLAB_IMMEDIATE_HIT_COMPAT";

    static final class PendingHit {
        final int targetSceneIndex;
        final int targetDefinitionId;
        final int damage;
        final int hitType;
        final int hp;
        final int maxHp;
        final int targetGfx;
        final long attackTick;
        final long dueTick;
        final String damageAuthority;
        final String damageFormula;
        final String delayAuthority;

        PendingHit(
            int targetSceneIndex,
            int targetDefinitionId,
            int damage,
            int hitType,
            int hp,
            int maxHp,
            int targetGfx,
            long attackTick,
            long dueTick,
            String damageAuthority,
            String damageFormula,
            String delayAuthority
        ){
            this.targetSceneIndex=targetSceneIndex;
            this.targetDefinitionId=targetDefinitionId;
            this.damage=damage;
            this.hitType=hitType;
            this.hp=hp;
            this.maxHp=maxHp;
            this.targetGfx=targetGfx;
            this.attackTick=attackTick;
            this.dueTick=dueTick;
            this.damageAuthority=damageAuthority;
            this.damageFormula=damageFormula;
            this.delayAuthority=delayAuthority;
        }

        @Override public String toString(){
            return "PendingHit{targetScene="+targetSceneIndex+
                ",def="+targetDefinitionId+
                ",damage="+damage+
                ",attackTick="+attackTick+
                ",dueTick="+dueTick+
                ",damageAuthority="+damageAuthority+
                ",damageFormula="+damageFormula+
                ",delayAuthority="+delayAuthority+"}";
        }
    }

    private final CombatState state;

    CombatHitScheduler(CombatState state){
        this.state=Objects.requireNonNull(state,"state");
    }

    PendingHit scheduleCompatibility(
        NpcEntity target,
        int damage,
        int hitType,
        int hp,
        int maxHp,
        int targetGfx,
        long attackTick,
        CombatDamageRules.Result rules
    ){
        return schedule(
            target,
            damage,
            hitType,
            hp,
            maxHp,
            targetGfx,
            attackTick,
            LOCALLAB_COMPAT_DELAY_TICKS,
            rules,
            DELAY_AUTHORITY
        );
    }

    PendingHit schedule(
        NpcEntity target,
        int damage,
        int hitType,
        int hp,
        int maxHp,
        int targetGfx,
        long attackTick,
        long delayTicks,
        CombatDamageRules.Result rules,
        String delayAuthority
    ){
        Objects.requireNonNull(target,"target");
        Objects.requireNonNull(rules,"rules");

        if(delayTicks<0)
            throw new IllegalArgumentException(
                "delayTicks="+delayTicks
            );

        String authority=
            delayAuthority==null||
            delayAuthority.isEmpty()
                ?"UNSPECIFIED_DELAY_AUTHORITY"
                :delayAuthority;

        PendingHit hit=
            new PendingHit(
                target.sceneIndex,
                target.definitionId,
                damage,
                hitType,
                hp,
                maxHp,
                targetGfx,
                attackTick,
                attackTick+delayTicks,
                rules.authority,
                rules.formula,
                authority
            );

        state.pendingHits.addLast(hit);
        return hit;
    }

    List<PendingHit> drainDue(long worldTick){
        ArrayList<PendingHit> due=
            new ArrayList<>();

        while(!state.pendingHits.isEmpty()){
            PendingHit next=state.pendingHits.peekFirst();

            if(next.dueTick>worldTick)
                break;

            due.add(
                state.pendingHits.removeFirst()
            );
        }

        return due;
    }

    int pending(){
        return state.pendingHits.size();
    }
}
