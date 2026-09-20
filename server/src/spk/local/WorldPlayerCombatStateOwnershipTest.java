package spk.local;

public final class WorldPlayerCombatStateOwnershipTest {
    public static void main(String[] args){
        WorldPlayer player=new WorldPlayer();
        CombatState canonical=player.combatState();

        if(canonical==null)
            throw new AssertionError(
                "WorldPlayer combat state missing"
            );

        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();

        CombatEngine first=
            new CombatEngine(
                canonical,
                dev
            );

        MovementState movement=
            player.movement();

        NpcEntity target=
            new NpcEntity(
                77,
                1489,
                movement.x()+1,
                movement.y()
            );

        String acquired=
            first.request(
                target,
                movement,
                player.equipment().weapon(),
                1000L
            );

        if(!acquired.startsWith(
                "TARGET_ACQUIRED"))
            throw new AssertionError(
                "combat target was not acquired: "+
                acquired
            );

        if(first.state()!=canonical)
            throw new AssertionError(
                "first engine does not use canonical state"
            );

        if(!canonical.active()||
           canonical.targetSceneIndex!=77||
           canonical.targetDefinitionId!=1489)
            throw new AssertionError(
                "canonical combat state was not mutated"
            );

        CombatEngine replacement=
            new CombatEngine(
                player.combatState(),
                dev
            );

        if(replacement.state()!=canonical)
            throw new AssertionError(
                "replacement engine received different combat state"
            );

        if(!replacement.active()||
           replacement.state().targetSceneIndex!=77)
            throw new AssertionError(
                "combat state did not survive engine-wrapper replacement"
            );

        boolean cancelled=
            replacement.cancelForManualMovement();

        if(!cancelled)
            throw new AssertionError(
                "replacement engine did not observe active canonical combat"
            );

        if(first.active()||
           canonical.active())
            throw new AssertionError(
                "canonical cancellation was not visible to original engine"
            );

        System.out.println(
            "WORLD_PLAYER_COMBAT_STATE_OWNERSHIP_PASS "+
            "canonicalOwner=WorldPlayer "+
            "engineWrapperReplace=true "+
            "sharedCancellation=true"
        );
    }
}
