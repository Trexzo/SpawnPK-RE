package spk.local;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Typed server-owned attribution for canonical player deaths.
 *
 * Combat/log strings are never parsed to recover killer identity. One exact
 * victim deathSequence may have at most one attacker attribution.
 */
final class PlayerDeathAttributionRegistry {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G1_PVP_DEATH_ATTRIBUTION";

    static final class Attribution {
        final EntityId attackerId;
        final long attackerGeneration;
        final String attackerRef;
        final EntityId victimId;
        final long victimGeneration;
        final String victimRef;
        final long deathTick;
        final long deathSequence;
        final String authority;

        private Attribution(
            WorldPlayer attacker,
            long attackerGeneration,
            WorldPlayer victim,
            long victimGeneration
        ){
            this.attackerId=attacker.id();
            this.attackerGeneration=attackerGeneration;
            this.attackerRef=requirePlayerRef(
                attacker.username(),
                "attacker"
            );
            this.victimId=victim.id();
            this.victimGeneration=victimGeneration;
            this.victimRef=requirePlayerRef(
                victim.username(),
                "victim"
            );
            this.deathTick=victim.lifecycle().deathTick();
            this.deathSequence=victim.lifecycle().deathSequence();
            this.authority=AUTHORITY;
        }

        private boolean same(
            WorldPlayer attacker,
            long expectedAttackerGeneration,
            WorldPlayer victim,
            long expectedVictimGeneration
        ){
            return attackerId.equals(attacker.id())&&
                attackerGeneration==expectedAttackerGeneration&&
                victimId.equals(victim.id())&&
                victimGeneration==expectedVictimGeneration&&
                deathTick==victim.lifecycle().deathTick()&&
                deathSequence==victim.lifecycle().deathSequence();
        }
    }

    private final Map<String,Attribution> byDeath=
        new LinkedHashMap<>();

    synchronized Attribution record(
        WorldPlayer attacker,
        long attackerGeneration,
        WorldPlayer victim,
        long victimGeneration
    ){
        WorldPlayer checkedAttacker=
            Objects.requireNonNull(attacker,"attacker");
        WorldPlayer checkedVictim=
            Objects.requireNonNull(victim,"victim");

        if(attackerGeneration<=0L||
           victimGeneration<=0L)
            throw new IllegalArgumentException(
                "player generation must be positive"
            );

        PlayerLifecycleState lifecycle=
            checkedVictim.lifecycle();

        if(!lifecycle.dead()||
           lifecycle.deathTick()<0L||
           lifecycle.deathSequence()<=0L)
            throw new IllegalStateException(
                "victim has no canonical active death id="+
                checkedVictim.id()
            );

        String key=key(
            checkedVictim.id(),
            lifecycle.deathSequence()
        );

        Attribution existing=byDeath.get(key);
        if(existing!=null){
            if(existing.same(
                    checkedAttacker,
                    attackerGeneration,
                    checkedVictim,
                    victimGeneration))
                return existing;

            throw new IllegalStateException(
                "conflicting player death attribution victim="+
                checkedVictim.id()+
                " deathSequence="+
                lifecycle.deathSequence()
            );
        }

        Attribution created=
            new Attribution(
                checkedAttacker,
                attackerGeneration,
                checkedVictim,
                victimGeneration
            );

        byDeath.put(key,created);
        return created;
    }

    synchronized Attribution get(
        EntityId victimId,
        long deathSequence
    ){
        if(deathSequence<=0L)
            return null;

        return byDeath.get(
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
        return byDeath.size();
    }

    private static String key(
        EntityId victimId,
        long deathSequence
    ){
        return victimId.toString()+"#"+deathSequence;
    }

    private static String requirePlayerRef(
        String value,
        String label
    ){
        if(value==null)
            throw new IllegalStateException(
                label+" player has no username"
            );

        String clean=value.trim();
        if(clean.isEmpty())
            throw new IllegalStateException(
                label+" player has blank username"
            );

        return clean;
    }
}
