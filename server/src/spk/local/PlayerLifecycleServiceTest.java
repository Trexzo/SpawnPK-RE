package spk.local;

public final class PlayerLifecycleServiceTest {
    public static void main(String[] args){
        WorldPlayer player=new WorldPlayer();
        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(player);

        PlayerState state=player.playerState();
        MovementState movement=player.movement();
        CombatEngine combat=
            new CombatEngine(
                player.combatState(),
                new DevAuthorityWorkbench()
            );

        if(state.currentLevel(PlayerState.HITPOINTS)!=99)
            throw new AssertionError(
                "initial HP changed"
            );

        PlayerLifecycleService.DamageResult first=
            lifecycle.applyDamage(
                20,
                10L,
                "TEST_NONLETHAL"
            );

        if(first.applied!=20||
           first.hpBefore!=99||
           first.hpAfter!=79||
           first.died||
           player.lifecycle().dead())
            throw new AssertionError(
                "nonlethal damage mismatch "+
                first
            );

        // Move off the default HOME spawn and queue another step so lethal
        // cleanup/respawn can prove movement authority as well.
        int initialX=movement.x();
        int initialY=movement.y();

        String move=
            movement.accept(
                new MovementRequest(
                    164,
                    false,
                    new int[]{initialX+1},
                    new int[]{initialY},
                    new byte[0]
                )
            );

        if(!move.startsWith("ACCEPTED"))
            throw new AssertionError(move);

        movement.advance();

        String queued=
            movement.accept(
                new MovementRequest(
                    164,
                    false,
                    new int[]{initialX+2},
                    new int[]{initialY},
                    new byte[0]
                )
            );

        if(!queued.startsWith("ACCEPTED")||
           movement.queued()==0)
            throw new AssertionError(
                "movement cleanup fixture missing "+
                queued
            );

        NpcEntity target=
            new NpcEntity(
                77,
                1489,
                movement.x()+1,
                movement.y()
            );

        String acquired=
            combat.request(
                target,
                movement,
                player.equipment().weapon(),
                1000L
            );

        if(!acquired.startsWith("TARGET_ACQUIRED")||
           !combat.active())
            throw new AssertionError(
                "combat cleanup fixture missing "+
                acquired
            );

        PlayerLifecycleService.DamageResult lethal=
            lifecycle.applyDamage(
                500,
                20L,
                "TEST_LETHAL"
            );

        if(!lethal.died||
           lethal.hpAfter!=0||
           state.currentLevel(PlayerState.HITPOINTS)!=0)
            throw new AssertionError(
                "lethal HP result mismatch "+
                lethal
            );

        if(!player.lifecycle().dead()||
           player.lifecycle().deathTick()!=20L||
           player.lifecycle().respawnTick()!=25L)
            throw new AssertionError(
                "death lifecycle timing mismatch "+
                player.lifecycle()
            );

        if(combat.active())
            throw new AssertionError(
                "lethal damage did not clear combat"
            );

        if(movement.queued()!=0)
            throw new AssertionError(
                "lethal damage did not clear queued movement"
            );

        PlayerLifecycleService.DamageResult whileDead=
            lifecycle.applyDamage(
                10,
                21L,
                "TEST_WHILE_DEAD"
            );

        if(!whileDead.ignoredDead||
           whileDead.applied!=0||
           state.currentLevel(PlayerState.HITPOINTS)!=0)
            throw new AssertionError(
                "dead player accepted damage "+
                whileDead
            );

        if(lifecycle.tick(24L)!=
                PlayerLifecycleService.TickResult.NONE)
            throw new AssertionError(
                "respawn occurred one tick early"
            );

        if(!player.lifecycle().dead()||
           state.currentLevel(PlayerState.HITPOINTS)!=0)
            throw new AssertionError(
                "pre-respawn state changed"
            );

        if(lifecycle.tick(25L)!=
                PlayerLifecycleService.TickResult.RESPAWNED)
            throw new AssertionError(
                "respawn did not occur on deterministic tick"
            );

        if(!player.lifecycle().alive()||
           !state.alive()||
           state.currentLevel(PlayerState.HITPOINTS)!=99)
            throw new AssertionError(
                "respawn HP/lifecycle mismatch"
            );

        if(!movement.inHomeWindow()||
           movement.x()!=MovementState.INITIAL_X||
           movement.y()!=MovementState.INITIAL_Y)
            throw new AssertionError(
                "respawn did not restore HOME spawn"
            );

        if(!PlayerLifecycleService.AUTHORITY.equals(
                PlayerLifecycleState.AUTHORITY))
            throw new AssertionError(
                "lifecycle provenance mismatch"
            );

        WorldPlayer stalePlayer=
            new WorldPlayer();
        PlayerLifecycleService staleLifecycle=
            new PlayerLifecycleService(
                stalePlayer
            );

        staleLifecycle.applyDamage(
            500,
            30L,
            "STALE_PREPARED_RESPAWN_A",
            0L
        );

        PlayerLifecycleService.PreparedRespawn stalePrepared=
            staleLifecycle.prepareRespawn(
                30L
            );

        if(stalePrepared==null)
            throw new AssertionError(
                "stale prepared respawn fixture missing"
            );

        staleLifecycle.commitPreparedRespawn(
            stalePrepared
        );

        staleLifecycle.applyDamage(
            500,
            31L,
            "STALE_PREPARED_RESPAWN_B",
            0L
        );

        boolean staleRejected=false;

        try{
            staleLifecycle.commitPreparedRespawn(
                stalePrepared
            );
        }catch(IllegalStateException expected){
            staleRejected=true;
        }

        if(!staleRejected||
           !stalePlayer.lifecycle().dead()||
           stalePlayer.playerState().currentLevel(
                PlayerState.HITPOINTS)!=0)
            throw new AssertionError(
                "stale prepared respawn did not fail closed"
            );

        System.out.println(
            "PLAYER_LIFECYCLE_SERVICE_PASS "+
            "nonlethal=99->79 "+
            "lethal=79->0 "+
            "deathTick=20 respawnTick=25 "+
            "combatCleanup=true movementCleanup=true "+
            "respawnHome=true stalePreparedRejected=true authority="+
            PlayerLifecycleService.AUTHORITY
        );
    }
}
