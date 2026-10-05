package spk.local;

import java.util.Objects;

/**
 * Deterministic player HP/death/respawn lifecycle.
 *
 * This is functional LocalLab gameplay authority, not a recovered SpawnPK
 * formula. Presentation is deliberately outside this service.
 *
 * Custom respawn delay / restored HP are supplied as already-resolved gameplay
 * inputs. No caller-owned policy callback executes while player ownership is
 * held.
 */
final class PlayerLifecycleService {
    static final long LOCALLAB_RESPAWN_DELAY_TICKS=5L;
    static final int LOCALLAB_RESTORED_HITPOINTS=99;
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

    static final class PreparedRespawn {
        final long worldTick;
        final long deathSequence;
        final long deathTick;
        final long respawnTick;
        final int hpBefore;
        final int restoredHitpoints;

        PreparedRespawn(
            long worldTick,
            long deathSequence,
            long deathTick,
            long respawnTick,
            int hpBefore,
            int restoredHitpoints
        ){
            this.worldTick=worldTick;
            this.deathSequence=deathSequence;
            this.deathTick=deathTick;
            this.respawnTick=respawnTick;
            this.hpBefore=hpBefore;
            this.restoredHitpoints=restoredHitpoints;
        }
    }

    private final WorldPlayer player;
    private final PlayerState state;
    private final PlayerLifecycleState lifecycle;
    private final MovementState movement;
    private final CombatState combat;
    private final String respawnAuthority;

    PlayerLifecycleService(
        WorldPlayer player
    ){
        this(
            player,
            AUTHORITY
        );
    }

    PlayerLifecycleService(
        WorldPlayer player,
        String respawnAuthority
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.state=player.playerState();
        this.lifecycle=player.lifecycle();
        this.movement=player.movement();
        this.combat=player.combatState();
        this.respawnAuthority=
            requireGameplayAuthority(
                respawnAuthority
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
        return applyDamageInternal(
            amount,
            worldTick,
            cause,
            LOCALLAB_RESPAWN_DELAY_TICKS,
            null
        );
    }

    DamageResult applyDamage(
        int amount,
        long worldTick,
        String cause,
        long respawnDelayTicks
    ){
        return applyDamageInternal(
            amount,
            worldTick,
            cause,
            respawnDelayTicks,
            null
        );
    }

    DamageResult applyDamageFromPlayer(
        int amount,
        long worldTick,
        String cause,
        EntityId responsiblePlayerId
    ){
        return applyDamageInternal(
            amount,
            worldTick,
            cause,
            LOCALLAB_RESPAWN_DELAY_TICKS,
            Objects.requireNonNull(
                responsiblePlayerId,
                "responsiblePlayerId"
            )
        );
    }

    private DamageResult applyDamageInternal(
        int amount,
        long worldTick,
        String cause,
        long respawnDelayTicks,
        EntityId responsiblePlayerId
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

            if(predictedAfter==0){
                validateRespawnDelay(
                    worldTick,
                    respawnDelayTicks
                );
            }

            int applied=
                state.applyHitpointsDamage(
                    requested
                );
            int after=
                state.currentLevel(
                    PlayerState.HITPOINTS
                );

            boolean died=after==0;

            if(died){
                lifecycle.markDead(
                    worldTick,
                    respawnDelayTicks,
                    normalizedCause,
                    responsiblePlayerId
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

    PreparedRespawn prepareRespawn(
        long worldTick
    ){
        return prepareRespawn(
            worldTick,
            LOCALLAB_RESTORED_HITPOINTS
        );
    }

    PreparedRespawn prepareRespawn(
        long worldTick,
        int restoredHitpoints
    ){
        synchronized(player.mutationLock()){
            if(!lifecycle.dueRespawn(
                    worldTick))
                return null;

            validateRestoredHitpoints(
                restoredHitpoints
            );

            return new PreparedRespawn(
                worldTick,
                lifecycle.deathSequence(),
                lifecycle.deathTick(),
                lifecycle.respawnTick(),
                state.currentLevel(
                    PlayerState.HITPOINTS
                ),
                restoredHitpoints
            );
        }
    }

    void requirePreparedRespawnCurrent(
        PreparedRespawn prepared
    ){
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        synchronized(player.mutationLock()){
            requirePreparedRespawnCurrentLocked(
                prepared
            );
        }
    }

    void commitPreparedRespawn(
        PreparedRespawn prepared
    ){
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        synchronized(player.mutationLock()){
            requirePreparedRespawnCurrentLocked(
                prepared
            );

            state.setCurrentLevel(
                PlayerState.HITPOINTS,
                prepared.restoredHitpoints
            );
            movement.returnHome();
            combat.clear();
            lifecycle.markRespawned();
        }
    }

    private void requirePreparedRespawnCurrentLocked(
        PreparedRespawn prepared
    ){
        if(!lifecycle.dead()||
           lifecycle.deathSequence()!=
                prepared.deathSequence||
           lifecycle.deathTick()!=
                prepared.deathTick||
           lifecycle.respawnTick()!=
                prepared.respawnTick||
           !lifecycle.dueRespawn(
                prepared.worldTick)||
           state.currentLevel(
                PlayerState.HITPOINTS
            )!=prepared.hpBefore)
            throw new IllegalStateException(
                "player respawn preimage changed before commit"
            );

        validateRestoredHitpoints(
            prepared.restoredHitpoints
        );
    }

    TickResult tick(
        long worldTick
    ){
        return tick(
            worldTick,
            LOCALLAB_RESTORED_HITPOINTS
        );
    }

    TickResult tick(
        long worldTick,
        int restoredHitpoints
    ){
        PreparedRespawn prepared=
            prepareRespawn(
                worldTick,
                restoredHitpoints
            );

        if(prepared==null)
            return TickResult.NONE;

        commitPreparedRespawn(
            prepared
        );
        return TickResult.RESPAWNED;
    }

    private void validateRestoredHitpoints(
        int restoredHitpoints
    ){
        if(restoredHitpoints<1||
           restoredHitpoints>255)
            throw new IllegalArgumentException(
                "restored hitpoints out of range value="+
                restoredHitpoints+
                " authority="+
                respawnAuthority
            );
    }

    private void validateRespawnDelay(
        long deathTick,
        long delay
    ){
        if(delay<0L)
            throw new IllegalArgumentException(
                "respawn delay negative delay="+
                delay+
                " authority="+
                respawnAuthority
            );

        try{
            Math.addExact(
                deathTick,
                delay
            );
        }catch(ArithmeticException overflow){
            throw new IllegalArgumentException(
                "respawn tick overflow deathTick="+
                deathTick+
                " delay="+
                delay+
                " authority="+
                respawnAuthority,
                overflow
            );
        }
    }

    private static String requireGameplayAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "respawnAuthority"
            );

        String clean=value.trim();

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

    private static String safeCause(String cause){
        return cause==null||cause.isEmpty()
            ?"UNSPECIFIED"
            :cause;
    }
}
