package spk.local;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Exactly-once live PvP kill counters for the G1 playable vertical slice.
 *
 * Original SpawnPK reward economics are not recovered here. The first G1 policy
 * owns only two semantic counters: one kill and one LocalLab point per validated
 * lethal PLAYER_PVP death. Same-death dedupe is runtime-scoped because
 * EntityId/deathSequence are live-world identities and are not safe durable IDs
 * across server restarts.
 */
final class PvpKillRewardService {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G1_PVP_KILL_COUNTERS_V1";
    static final String NAMESPACE=
        "g1_pvp_reward";

    static final class Counters {
        final long kills;
        final long points;

        private Counters(
            long kills,
            long points
        ){
            this.kills=kills;
            this.points=points;
        }

        @Override public String toString(){
            return "Counters{kills="+kills+
                ",points="+points+"}";
        }
    }

    static final class Receipt {
        final EntityId victimId;
        final long deathSequence;
        final boolean granted;
        final boolean replay;
        final long kills;
        final long points;
        final String reason;
        final String authority;

        private Receipt(
            EntityId victimId,
            long deathSequence,
            boolean granted,
            boolean replay,
            long kills,
            long points,
            String reason
        ){
            this.victimId=victimId;
            this.deathSequence=deathSequence;
            this.granted=granted;
            this.replay=replay;
            this.kills=kills;
            this.points=points;
            this.reason=reason;
            this.authority=AUTHORITY;
        }

        Receipt replay(){
            return new Receipt(
                victimId,
                deathSequence,
                granted,
                true,
                kills,
                points,
                reason
            );
        }

        @Override public String toString(){
            return "Receipt{victim="+victimId+
                ",deathSequence="+deathSequence+
                ",granted="+granted+
                ",replay="+replay+
                ",kills="+kills+
                ",points="+points+
                ",reason="+reason+
                ",authority="+authority+"}";
        }
    }

    private final WorldPlayer victim;
    private final LinkedHashMap<Long,Receipt>
        settledByDeathSequence=
            new LinkedHashMap<>();

    PvpKillRewardService(
        WorldPlayer victim
    ){
        this.victim=Objects.requireNonNull(
            victim,
            "victim"
        );
    }

    Receipt settle(
        World world,
        WorldPlayer attacker,
        long expectedAttackerGeneration,
        long deathSequence
    ){
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(attacker,"attacker");

        synchronized(this){
            Receipt existing=
                settledByDeathSequence.get(
                    deathSequence
                );
            if(existing!=null)
                return existing.replay();
        }

        String validationFailure=
            validateCurrentPvpDeath(
                attacker,
                expectedAttackerGeneration,
                deathSequence
            );
        if(validationFailure!=null)
            return rejected(
                deathSequence,
                validationFailure
            );

        if(!world.players().owns(
                attacker,
                expectedAttackerGeneration))
            return rejected(
                deathSequence,
                "STALE_ATTACKER_GENERATION"
            );

        Receipt granted;

        synchronized(attacker.mutationLock()){
            if(!world.players().owns(
                    attacker,
                    expectedAttackerGeneration))
                return rejected(
                    deathSequence,
                    "STALE_ATTACKER_GENERATION"
                );

            final SortedMap<String,String>
                current=
                    attacker.snapshotExtensions()
                        .namespace(
                            NAMESPACE
                        );
            final long kills;
            final long points;
            final long nextKills;
            final long nextPoints;

            try{
                kills=
                    parseCounter(
                        current.get("kills"),
                        "kills"
                    );
                points=
                    parseCounter(
                        current.get("points"),
                        "points"
                    );
                nextKills=
                    Math.addExact(
                        kills,
                        1L
                    );
                nextPoints=
                    Math.addExact(
                        points,
                        1L
                    );
            }catch(
                IllegalArgumentException|
                ArithmeticException failure
            ){
                return rejected(
                    deathSequence,
                    "COUNTER_STATE_REJECTED:"+
                    failure.getMessage()
                );
            }

            TreeMap<String,String> next=
                new TreeMap<>(current);
            next.put(
                "kills",
                Long.toString(nextKills)
            );
            next.put(
                "points",
                Long.toString(nextPoints)
            );

            attacker.snapshotExtensions()
                .replaceNamespace(
                    NAMESPACE,
                    next
                );

            granted=
                new Receipt(
                    victim.id(),
                    deathSequence,
                    true,
                    false,
                    nextKills,
                    nextPoints,
                    "GRANTED"
                );
        }

        synchronized(this){
            Receipt existing=
                settledByDeathSequence.get(
                    deathSequence
                );
            if(existing!=null)
                throw new IllegalStateException(
                    "PvP reward death sequence concurrently settled victim="+
                    victim.id()+
                    " deathSequence="+
                    deathSequence
                );

            settledByDeathSequence.put(
                deathSequence,
                granted
            );
        }

        return granted;
    }

    static Counters counters(
        WorldPlayer player
    ){
        Objects.requireNonNull(
            player,
            "player"
        );

        synchronized(player.mutationLock()){
            SortedMap<String,String> values=
                player.snapshotExtensions()
                    .namespace(
                        NAMESPACE
                    );

            return new Counters(
                parseCounter(
                    values.get("kills"),
                    "kills"
                ),
                parseCounter(
                    values.get("points"),
                    "points"
                )
            );
        }
    }

    int settledDeathCount(){
        synchronized(this){
            return settledByDeathSequence.size();
        }
    }

    private String validateCurrentPvpDeath(
        WorldPlayer attacker,
        long expectedAttackerGeneration,
        long deathSequence
    ){
        synchronized(victim.mutationLock()){
            PlayerLifecycleState lifecycle=
                victim.lifecycle();

            if(!lifecycle.dead()||
               lifecycle.deathSequence()!=
                    deathSequence)
                return "STALE_OR_NONCURRENT_DEATH";

            PlayerLifecycleState.DeathAttribution
                attribution=
                    lifecycle.deathAttribution();

            if(attribution==null)
                return "MISSING_ATTRIBUTION";

            if(attribution.deathSequence!=
                    deathSequence)
                return "ATTRIBUTION_SEQUENCE_MISMATCH";

            if(!attacker.id().equals(
                    attribution.attackerId))
                return "ATTRIBUTION_ATTACKER_MISMATCH";

            if(attribution.attackerGeneration!=
                    expectedAttackerGeneration)
                return "ATTRIBUTION_GENERATION_MISMATCH";

            if(!"PLAYER_PVP".equals(
                    attribution.context))
                return "UNSUPPORTED_ATTRIBUTION_CONTEXT";

            return null;
        }
    }

    private Receipt rejected(
        long deathSequence,
        String reason
    ){
        Counters current;
        try{
            current=countersSafe();
        }catch(RuntimeException ignored){
            current=new Counters(0L,0L);
        }

        return new Receipt(
            victim.id(),
            deathSequence,
            false,
            false,
            current.kills,
            current.points,
            reason
        );
    }

    private Counters countersSafe(){
        /*
         * Rejection reporting must never inspect or mutate attacker state.
         * Victim-side reward history is intentionally not persisted, so zero is
         * the only protocol-independent fallback here.
         */
        return new Counters(0L,0L);
    }

    private static long parseCounter(
        String value,
        String key
    ){
        if(value==null)
            return 0L;

        final long parsed;
        try{
            parsed=Long.parseLong(value);
        }catch(NumberFormatException bad){
            throw new IllegalArgumentException(
                "malformed "+key+"="+value,
                bad
            );
        }

        if(parsed<0L)
            throw new IllegalArgumentException(
                "negative "+key+"="+parsed
            );

        return parsed;
    }
}
