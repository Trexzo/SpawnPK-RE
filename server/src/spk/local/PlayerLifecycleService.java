package spk.local;

import java.util.Objects;

/**
 * Deterministic player HP/death/respawn lifecycle.
 *
 * Gameplay respawn policy is injectable. The legacy constructor preserves the
 * current LocalLab 5-tick / 99-HP behavior for compatibility.
 * Presentation remains outside this service.
 */
final class PlayerLifecycleService {
    static final long LOCALLAB_RESPAWN_DELAY_TICKS=5L;
    static final String AUTHORITY="CUSTOM_LOCALLAB";

    interface RespawnPolicy {
        long respawnDelayTicks(
            WorldPlayer player,
            long deathTick,
            String cause
        );

        int restoredHitpoints(
            WorldPlayer player
        );

        String authority();
    }

    private static final RespawnPolicy LEGACY_POLICY=
        new RespawnPolicy() {
            @Override public long respawnDelayTicks(
                WorldPlayer player,
                long deathTick,
                String cause
            ){
                return LOCALLAB_RESPAWN_DELAY_TICKS;
            }

            @Override public int restoredHitpoints(
                WorldPlayer player
            ){
                return 99;
            }

            @Override public String authority(){
                return AUTHORITY;
            }
        };

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
    private final RespawnPolicy respawnPolicy;
    private final String respawnAuthority;

    PlayerLifecycleService(
        WorldPlayer player
    ){
        this(
            player,
            LEGACY_POLICY
        );
    }

    PlayerLifecycleService(
        WorldPlayer player,
        RespawnPolicy respawnPolicy
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.state=
            player.playerState();
        this.lifecycle=
            player.lifecycle();
        this.movement=
            player.movement();
        this.combat=
            player.combatState();
        this.respawnPolicy=
            Objects.requireNonNull(
                respawnPolicy,
                "respawnPolicy"
            );
        this.respawnAuthority=
            requireGameplayAuthority(
                respawnPolicy.authority()
            );
    }

    String respawnAuthority(){
        return respawnAuthority;
    }

    DamageResult applyDamage(
        int amount,
        long worldTick,
        String cause
    ){
        synchronized(player.mutationLock()){
            int requested=
                Math.max(
                    0,
                    amount
                );
            int before=
                state.currentLevel(
                    PlayerState.HITPOINTS
                );
            String normalizedCause=
                safeCause(
                    cause
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
                    normalizedCause
                );
            }

            int predictedAfter=
                Math.max(
                    0,
                    before-requested
                );

            long respawnDelay=-1L;

            if(predictedAfter==0){
                respawnDelay=
                    respawnPolicy
                        .respawnDelayTicks(
                            player,
                            worldTick,
                            normalizedCause
                        );

                if(respawnDelay<0L)
                    throw new IllegalStateException(
                        "respawn delay negative delay="+
                        respawnDelay+
                        " authority="+
                        respawnAuthority
                    );

                try{
                    Math.addExact(
                        worldTick,
                        respawnDelay
                    );
                }catch(ArithmeticException overflow){
                    throw new IllegalStateException(
                        "respawn tick overflow deathTick="+
                        worldTick+
                        " delay="+
                        respawnDelay+
                        " authority="+
                        respawnAuthority,
                        overflow
                    );
                }
            }

            int applied=
                state.applyHitpointsDamage(
                    requested
                );
            int after=
                state.currentLevel(
                    PlayerState.HITPOINTS
                );

            boolean died=
                after==0;

            if(died){
                if(respawnDelay<0L)
                    throw new IllegalStateException(
                        "lethal damage missing preflighted respawn delay"
                    );

                lifecycle.markDead(
                    worldTick,
                    respawnDelay,
                    normalizedCause
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
                normalizedCause
            );
        }
    }

    TickResult tick(
        long worldTick
    ){
        synchronized(player.mutationLock()){
            if(!lifecycle.dueRespawn(
                    worldTick))
                return TickResult.NONE;

            int restoredHitpoints=
                respawnPolicy
                    .restoredHitpoints(
                        player
                    );

            if(restoredHitpoints<1||
               restoredHitpoints>255)
                throw new IllegalStateException(
                    "restored hitpoints out of range value="+
                    restoredHitpoints+
                    " authority="+
                    respawnAuthority
                );

            state.setCurrentLevel(
                PlayerState.HITPOINTS,
                restoredHitpoints
            );
            movement.returnHome();
            combat.clear();
            lifecycle.markRespawned();

            return TickResult.RESPAWNED;
        }
    }

    private static String requireGameplayAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "respawnAuthority"
            );

        String clean=
            value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "respawnAuthority blank"
            );

        if("EXACT_CURRENT_CLIENT".equals(
                clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(
                clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define respawn policy actual="+
                clean
            );

        return clean;
    }

    private static String safeCause(
        String cause
    ){
        return cause==null||
               cause.isEmpty()
            ?"UNSPECIFIED"
            :cause;
    }
}
