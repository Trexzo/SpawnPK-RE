package spk.local;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.UUID;

/**
 * R82 OFFLINE ONLY: producer-scoped source occurrence keys for the
 * real R79-backed PvP resolver. This is NOT a live event ID protocol.
 *
 * A new ordinal is allocated only by the actual resolver entry; its
 * real PLAYER_KILL and PLAYER_DEATH outcomes share that ordinal but
 * have different type-specific keys. A test-only reissue can copy
 * one ALREADY RECORDED immutable fact while preserving its origin.
 * Attacker/victim strings, tick and authority are never a key.
 *
 * Keys are in memory only: a fresh JVM cannot reconstruct this scope,
 * no crash recovery or durable exactly-once property is claimed.
 */
final class R82OfflineCombatOccurrenceKeys {
    static final class Key {
        final UUID scope;
        final EntityId attacker;
        final long attackerGeneration;
        final EntityId victim;
        final long victimGeneration;
        final long ordinal;
        final CombatOutcomeType type;

        Key(
            UUID scope,WorldPlayer attacker,long attackerGeneration,
            WorldPlayer victim,long victimGeneration,
            long ordinal,CombatOutcomeType type
        ){
            this.scope=scope;
            this.attacker=attacker.id();
            this.attackerGeneration=attackerGeneration;
            this.victim=victim.id();
            this.victimGeneration=victimGeneration;
            this.ordinal=ordinal;
            this.type=type;
        }

        @Override public boolean equals(Object other){
            if(!(other instanceof Key))return false;
            Key k=(Key)other;
            return scope.equals(k.scope)&&
                attacker.equals(k.attacker)&&
                attackerGeneration==k.attackerGeneration&&
                victim.equals(k.victim)&&
                victimGeneration==k.victimGeneration&&
                ordinal==k.ordinal&&type==k.type;
        }
        @Override public int hashCode(){
            return Objects.hash(scope,attacker,attackerGeneration,
                victim,victimGeneration,ordinal,type);
        }
        @Override public String toString(){
            return "R82SourceKey{scope="+scope+
                ",ordinal="+ordinal+",type="+type+"}";
        }
    }

    private final UUID scope=UUID.randomUUID();
    private final WorldPlayer attacker;
    private final long attackerGeneration;
    private final WorldPlayer victim;
    private final long victimGeneration;
    private final IdentityHashMap<CombatOutcome,Key> known=
        new IdentityHashMap<>();
    private final ThreadLocal<Long> currentResolution=new ThreadLocal<>();
    private long nextOrdinal;

    R82OfflineCombatOccurrenceKeys(
        WorldPlayer attacker,long attackerGeneration,
        WorldPlayer victim,long victimGeneration
    ){
        this.attacker=Objects.requireNonNull(attacker,"attacker");
        this.attackerGeneration=attackerGeneration;
        this.victim=Objects.requireNonNull(victim,"victim");
        this.victimGeneration=victimGeneration;
        if(attacker==victim||attackerGeneration<=0||victimGeneration<=0)
            throw new IllegalArgumentException(
                "R82_SOURCE_DENIED invalid actor pair"
            );
    }

    void beginRealResolution(){
        if(currentResolution.get()!=null)
            throw new IllegalStateException(
                "R82_SOURCE_DENIED nested PvP resolution"
            );
        if(nextOrdinal==Long.MAX_VALUE)
            throw new IllegalStateException(
                "R82_SOURCE_DENIED occurrence ordinal exhausted"
            );
        currentResolution.set(Long.valueOf(++nextOrdinal));
    }

    void endRealResolution(){
        currentResolution.remove();
    }

    void recordProducerFact(CombatOutcome fact){
        Objects.requireNonNull(fact,"fact");
        Key existing=known.get(fact);
        if(existing!=null)
            return; // explicitly registered test reissue, not new source

        Long ordinal=currentResolution.get();
        if(ordinal==null)
            throw new IllegalStateException(
                "R82_SOURCE_DENIED no active real resolution"
            );

        if(!attacker.id().toString().equals(fact.attacker())||
           !victim.id().toString().equals(fact.victim())||
           fact.context()!=CombatOutcomeContext.PLAYER_PVP||
           (fact.type()!=CombatOutcomeType.PLAYER_KILL&&
            fact.type()!=CombatOutcomeType.PLAYER_DEATH))
            throw new IllegalStateException(
                "R82_SOURCE_DENIED mismatched real PvP fact"
            );

        Key key=new Key(
            scope,attacker,attackerGeneration,
            victim,victimGeneration,ordinal.longValue(),fact.type()
        );
        for(Key prior:known.values()){
            if(prior.equals(key))
                throw new IllegalStateException(
                    "R82_SOURCE_DENIED duplicate source outcome type"
                );
        }
        known.put(fact,key);
    }

    CombatOutcome prepareRecordedReissueForTest(CombatOutcome original){
        Key key=known.get(original);
        if(key==null)
            throw new IllegalStateException(
                "R82_SOURCE_DENIED original fact unknown"
            );
        if(currentResolution.get()!=null)
            throw new IllegalStateException(
                "R82_SOURCE_DENIED reissue within live resolution"
            );
        CombatOutcome clone=new CombatOutcome(
            original.attacker(),original.victim(),original.type(),
            original.context(),original.worldTick(),original.sourceAuthority()
        );
        known.put(clone,key);
        return clone;
    }

    Key recordedKey(CombatOutcome fact){return known.get(fact);}
    int recordedFactCountForTest(){return known.size();}
}
