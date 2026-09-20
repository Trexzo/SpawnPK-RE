package spk.local;

import java.util.Objects;

/**
 * Deterministic player HP/death/respawn lifecycle.
 *
 * This is functional LocalLab gameplay authority, not a recovered SpawnPK
 * formula. Presentation is deliberately outside this service.
 */
final class PlayerLifecycleService {
    static final long LOCALLAB_RESPAWN_DELAY_TICKS=5L;
    static final String AUTHORITY="CUSTOM_LOCALLAB";

    static final class DamageResult {
        final int requested;
        final int applied;
        final int hpBefore;
        final int hpAfter;
        final boolean died;
        final boolean ignoredDead;
        final long worldTick;
        final String cause;

        DamageResult(
            int requested,
            int applied,
            int hpBefore,
            int hpAfter,
            boolean died,
            boolean ignoredDead,
            long worldTick,
            String cause
        ){
            this.requested=requested;
            this.applied=applied;
            this.hpBefore=hpBefore;
            this.hpAfter=hpAfter;
            this.died=died;
            this.ignoredDead=ignoredDead;
            this.worldTick=worldTick;
            this.cause=cause;
        }

        @Override public String toString(){
            return "DamageResult{requested="+requested+
                ",applied="+applied+
                ",hp="+hpBefore+"->"+hpAfter+
                ",died="+died+
                ",ignoredDead="+ignoredDead+
                ",tick="+worldTick+
                ",cause="+cause+
                ",authority="+AUTHORITY+"}";
        }
    }

    enum TickResult {
        NONE,
        RESPAWNED
    }

    private final WorldPlayer player;
    private final PlayerState state;
    private final PlayerLifecycleState lifecycle;
    private final MovementState movement;
    private final CombatState combat;

    PlayerLifecycleService(WorldPlayer player){
        this.player=Objects.requireNonNull(player,"player");
        this.state=player.playerState();
        this.lifecycle=player.lifecycle();
        this.movement=player.movement();
        this.combat=player.combatState();
    }

    DamageResult applyDamage(
        int amount,
        long worldTick,
        String cause
    ){
        synchronized(player.mutationLock()){
            int requested=Math.max(0,amount);
            int before=state.currentLevel(
                PlayerState.HITPOINTS
            );

            if(lifecycle.dead()){
                return new DamageResult(
                    requested,
                    0,
                    before,
                    before,
                    false,
                    true,
                    worldTick,
                    safeCause(cause)
                );
            }

            int applied=
                state.applyHitpointsDamage(
                    requested
                );
            int after=state.currentLevel(
                PlayerState.HITPOINTS
            );

            boolean died=after==0;

            if(died){
                lifecycle.markDead(
                    worldTick,
                    LOCALLAB_RESPAWN_DELAY_TICKS,
                    safeCause(cause)
                );
                combat.clear();
                movement.clearQueuedPath();
            }

            return new DamageResult(
                requested,
                applied,
                before,
                after,
                died,
                false,
                worldTick,
                safeCause(cause)
            );
        }
    }

    TickResult tick(long worldTick){
        synchronized(player.mutationLock()){
            if(!lifecycle.dueRespawn(worldTick))
                return TickResult.NONE;

            state.restoreHitpointsDefault();
            movement.returnHome();
            combat.clear();
            lifecycle.markRespawned();

            return TickResult.RESPAWNED;
        }
    }

    private static String safeCause(String cause){
        return cause==null||cause.isEmpty()
            ?"UNSPECIFIED"
            :cause;
    }
}
