package spk.local;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * World-owned exact PvP killer attribution by victim death sequence.
 *
 * This consumes already-emitted semantic combat facts. It owns no rewards,
 * item-loss policy, bounty policy or packet presentation.
 */
final class PlayerPvpDeathLedger
    implements CombatOutcomeObserver, AutoCloseable {

    static final String AUTHORITY =
        "CUSTOM_LOCALLAB_PVP_DEATH_LEDGER_V1";

    static final class Entry {
        final EntityId attackerId;
        final long attackerGeneration;
        final String attackerUsername;
        final EntityId victimId;
        final long victimGeneration;
        final String victimUsername;
        final long deathSequence;
        final long deathTick;
        final String combatSourceAuthority;
        final String ledgerAuthority;

        private Entry(
            WorldPlayer attacker,
            long attackerGeneration,
            WorldPlayer victim,
            long victimGeneration,
            long deathSequence,
            long deathTick,
            String combatSourceAuthority
        ){
            this.attackerId=attacker.id();
            this.attackerGeneration=attackerGeneration;
            this.attackerUsername=
                requireText(
                    attacker.username(),
                    "attackerUsername"
                );
            this.victimId=victim.id();
            this.victimGeneration=victimGeneration;
            this.victimUsername=
                requireText(
                    victim.username(),
                    "victimUsername"
                );
            this.deathSequence=deathSequence;
            this.deathTick=deathTick;
            this.combatSourceAuthority=
                requireText(
                    combatSourceAuthority,
                    "combatSourceAuthority"
                );
            this.ledgerAuthority=AUTHORITY;
        }

        private boolean sameAs(
            Entry other
        ){
            return attackerId.equals(other.attackerId)&&
                attackerGeneration==other.attackerGeneration&&
                attackerUsername.equals(other.attackerUsername)&&
                victimId.equals(other.victimId)&&
                victimGeneration==other.victimGeneration&&
                victimUsername.equals(other.victimUsername)&&
                deathSequence==other.deathSequence&&
                deathTick==other.deathTick&&
                combatSourceAuthority.equals(
                    other.combatSourceAuthority
                );
        }
    }

    private final World world;
    private final LinkedHashMap<String,Entry> entries =
        new LinkedHashMap<>();
    private boolean closed;

    PlayerPvpDeathLedger(
        World world
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
    }

    @Override public void onCombatOutcome(
        CombatOutcome outcome
    ){
        CombatOutcome checked=
            Objects.requireNonNull(
                outcome,
                "outcome"
            );

        if(checked.type()!=
                CombatOutcomeType.PLAYER_KILL||
           checked.context()!=
                CombatOutcomeContext.PLAYER_PVP)
            return;

        final EntityId attackerId;
        final EntityId victimId;

        try{
            attackerId=
                parseEntityId(
                    checked.attacker(),
                    "attacker"
                );
            victimId=
                parseEntityId(
                    checked.victim(),
                    "victim"
                );
        }catch(IllegalArgumentException malformed){
            return;
        }

        WorldPlayer attacker=
            world.players().byId(
                attackerId
            );
        WorldPlayer victim=
            world.players().byId(
                victimId
            );

        if(attacker==null||
           victim==null||
           attacker==victim)
            return;

        long attackerGeneration=
            attacker.generation();
        long victimGeneration=
            victim.generation();

        if(!world.players().owns(
                attacker,
                attackerGeneration)||
           !world.players().owns(
                victim,
                victimGeneration))
            return;

        PlayerLifecycleState lifecycle=
            victim.lifecycle();

        if(!lifecycle.dead()||
           lifecycle.deathTick()!=
                checked.worldTick()||
           lifecycle.deathSequence()<=0L)
            return;

        Entry candidate=
            new Entry(
                attacker,
                attackerGeneration,
                victim,
                victimGeneration,
                lifecycle.deathSequence(),
                lifecycle.deathTick(),
                checked.sourceAuthority()
            );

        String key=
            key(
                candidate.victimId,
                candidate.deathSequence
            );

        synchronized(this){
            if(closed)
                return;

            Entry existing=
                entries.get(
                    key
                );

            if(existing==null){
                entries.put(
                    key,
                    candidate
                );
                return;
            }

            if(!existing.sameAs(
                    candidate))
                throw new IllegalStateException(
                    "conflicting PvP killer attribution victim="+
                    candidate.victimId+
                    " deathSequence="+
                    candidate.deathSequence+
                    " existingAttacker="+
                    existing.attackerId+
                    " candidateAttacker="+
                    candidate.attackerId
                );
        }
    }

    synchronized Entry get(
        EntityId victimId,
        long deathSequence
    ){
        return entries.get(
            key(
                Objects.requireNonNull(
                    victimId,
                    "victimId"
                ),
                deathSequence
            )
        );
    }

    synchronized int size(){
        return entries.size();
    }

    synchronized boolean closed(){
        return closed;
    }

    boolean isBoundTo(
        World expectedWorld
    ){
        return world==expectedWorld;
    }

    @Override public synchronized void close(){
        if(closed)
            return;

        closed=true;
        entries.clear();
    }

    private static EntityId parseEntityId(
        String value,
        String label
    ){
        String clean=
            requireText(
                value,
                label
            );

        final long parsed;

        try{
            parsed=Long.parseLong(
                clean
            );
        }catch(NumberFormatException invalid){
            throw new IllegalArgumentException(
                label+" is not EntityId: "+clean,
                invalid
            );
        }

        return new EntityId(
            parsed
        );
    }

    private static String key(
        EntityId victimId,
        long deathSequence
    ){
        if(deathSequence<=0L)
            throw new IllegalArgumentException(
                "deathSequence="+
                deathSequence
            );

        return victimId.toString()+
            ":"+
            Long.toUnsignedString(
                deathSequence
            );
    }

    private static String requireText(
        String value,
        String label
    ){
        if(value==null)
            throw new NullPointerException(
                label
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                label+" blank"
            );

        return clean;
    }
}
