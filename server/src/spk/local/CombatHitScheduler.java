package spk.local;

/**
 * Canonical pending-hit scheduler.
 *
 * Presentation and damage calculation stay outside this class. It owns only
 * the deterministic world-tick at which an already-calculated hit becomes due.
 */
final class CombatHitScheduler {
    static final class ScheduledHit {
        final int targetSceneIndex;
        final int targetDefinitionId;
        final int damage;
        final int hitType;
        final int hp;
        final int hpMax;
        final int targetGfx;
        final String damageAuthority;
        final String damageFormula;
        final long scheduledTick;

        ScheduledHit(
            int targetSceneIndex,
            int targetDefinitionId,
            int damage,
            int hitType,
            int hp,
            int hpMax,
            int targetGfx,
            String damageAuthority,
            String damageFormula,
            long scheduledTick
        ){
            if(damage<0)
                throw new IllegalArgumentException(
                    "damage="+damage
                );
            if(scheduledTick<0)
                throw new IllegalArgumentException(
                    "scheduledTick="+scheduledTick
                );

            this.targetSceneIndex=targetSceneIndex;
            this.targetDefinitionId=targetDefinitionId;
            this.damage=damage;
            this.hitType=hitType;
            this.hp=hp;
            this.hpMax=hpMax;
            this.targetGfx=targetGfx;
            this.damageAuthority=damageAuthority;
            this.damageFormula=damageFormula;
            this.scheduledTick=scheduledTick;
        }

        @Override public String toString(){
            return "ScheduledHit{scene="+targetSceneIndex+
                ",def="+targetDefinitionId+
                ",damage="+damage+
                ",hitType="+hitType+
                ",tick="+scheduledTick+
                ",authority="+damageAuthority+
                ",formula="+damageFormula+"}";
        }
    }

    static ScheduledHit schedule(
        CombatState state,
        ScheduledHit hit
    ){
        if(state==null)
            throw new NullPointerException("state");
        if(hit==null)
            throw new NullPointerException("hit");

        state.pendingHit=hit;
        return hit;
    }

    static ScheduledHit consumeDue(
        CombatState state,
        long worldTick
    ){
        if(state==null)
            throw new NullPointerException("state");

        ScheduledHit hit=state.pendingHit;

        if(hit==null||
           worldTick<hit.scheduledTick)
            return null;

        state.pendingHit=null;
        return hit;
    }

    static void clear(CombatState state){
        if(state!=null)
            state.pendingHit=null;
    }

    private CombatHitScheduler(){}
}
