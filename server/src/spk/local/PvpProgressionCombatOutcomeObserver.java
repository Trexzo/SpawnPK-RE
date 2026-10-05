package spk.local;

import java.util.Objects;

/**
 * Live-world bridge from semantic PvP combat outcomes to persistent player
 * progression.
 */
final class PvpProgressionCombatOutcomeObserver
    implements CombatOutcomeObserver {

    private final World world;

    PvpProgressionCombatOutcomeObserver(World world){
        this.world=Objects.requireNonNull(world,"world");
    }

    @Override public void onCombatOutcome(CombatOutcome outcome){
        CombatOutcome fact=Objects.requireNonNull(outcome,"outcome");

        if(fact.context()!=CombatOutcomeContext.PLAYER_PVP)
            return;

        if(fact.type()==CombatOutcomeType.PLAYER_KILL){
            WorldPlayer attacker=resolve(fact.attacker());
            if(attacker!=null)
                new PvpProgressionService(attacker).recordKill();
            return;
        }

        if(fact.type()==CombatOutcomeType.PLAYER_DEATH){
            WorldPlayer victim=resolve(fact.victim());
            if(victim!=null)
                new PvpProgressionService(victim).recordDeath();
        }
    }

    private WorldPlayer resolve(String entityRef){
        final long value;
        try{
            value=Long.parseUnsignedLong(entityRef);
        }catch(NumberFormatException invalid){
            return null;
        }

        if(value<=0L)return null;

        WorldPlayer player=
            world.players().byId(
                new EntityId(value)
            );

        if(player==null||!player.registered())
            return null;

        return player;
    }
}
