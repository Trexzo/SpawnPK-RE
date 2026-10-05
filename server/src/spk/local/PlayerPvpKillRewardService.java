package spk.local;

import java.util.*;

/**
 * Victim-scoped exactly-once PvP kill reward settlement.
 *
 * This is explicit LocalLab gameplay policy. It does not claim recovered
 * original SpawnPK PK-point economics and does not grant item/coin rewards.
 *
 * One service instance belongs to one victim coordinator, so deathSequence is
 * the exact idempotency key for that victim lifecycle.
 */
final class PlayerPvpKillRewardService {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G1_PVP_KILL_REWARD_MVP_V1";
    static final String NAMESPACE=
        "pvp_reward";
    static final String VERSION="1";

    static final class Result {
        final boolean applied;
        final boolean replayed;
        final String reason;
        final EntityId victimId;
        final long deathSequence;
        final long kills;
        final long points;

        private Result(
            boolean applied,
            boolean replayed,
            String reason,
            EntityId victimId,
            long deathSequence,
            long kills,
            long points
        ){
            this.applied=applied;
            this.replayed=replayed;
            this.reason=reason;
            this.victimId=victimId;
            this.deathSequence=deathSequence;
            this.kills=kills;
            this.points=points;
        }

        static Result applied(
            EntityId victimId,
            long deathSequence,
            long kills,
            long points
        ){
            return new Result(
                true,
                false,
                "APPLIED",
                victimId,
                deathSequence,
                kills,
                points
            );
        }

        static Result replay(Result prior){
            return new Result(
                false,
                true,
                "REPLAY",
                prior.victimId,
                prior.deathSequence,
                prior.kills,
                prior.points
            );
        }

        static Result rejected(
            String reason,
            EntityId victimId,
            long deathSequence,
            long kills,
            long points
        ){
            return new Result(
                false,
                false,
                reason,
                victimId,
                deathSequence,
                kills,
                points
            );
        }

        @Override public String toString(){
            return "Result{applied="+applied+
                ",replayed="+replayed+
                ",reason="+reason+
                ",victim="+victimId+
                ",deathSequence="+deathSequence+
                ",kills="+kills+
                ",points="+points+"}";
        }
    }

    private static final class State {
        final boolean valid;
        final String reason;
        final long kills;
        final long points;

        private State(
            boolean valid,
            String reason,
            long kills,
            long points
        ){
            this.valid=valid;
            this.reason=reason;
            this.kills=kills;
            this.points=points;
        }

        static State valid(
            long kills,
            long points
        ){
            return new State(
                true,
                "VALID",
                kills,
                points
            );
        }

        static State invalid(
            String reason
        ){
            return new State(
                false,
                reason,
                0L,
                0L
            );
        }
    }

    private final WorldPlayer victim;
    private final LinkedHashMap<Long,Result>
        settledByDeathSequence=
            new LinkedHashMap<>();

    PlayerPvpKillRewardService(
        WorldPlayer victim
    ){
        this.victim=
            Objects.requireNonNull(
                victim,
                "victim"
            );
    }

    synchronized Result settle(
        WorldPlayer killer,
        long deathSequence
    ){
        Objects.requireNonNull(
            killer,
            "killer"
        );

        if(deathSequence<=0L)
            throw new IllegalArgumentException(
                "deathSequence="+
                deathSequence
            );

        Result existing=
            settledByDeathSequence.get(
                deathSequence
            );

        if(existing!=null)
            return Result.replay(
                existing
            );

        synchronized(killer.mutationLock()){
            SortedMap<String,String> before=
                killer.snapshotExtensions()
                    .namespace(
                        NAMESPACE
                    );

            State state=
                parse(
                    before
                );

            if(!state.valid)
                return Result.rejected(
                    state.reason,
                    victim.id(),
                    deathSequence,
                    state.kills,
                    state.points
                );

            final long nextKills;
            final long nextPoints;

            try{
                nextKills=
                    Math.addExact(
                        state.kills,
                        1L
                    );
                nextPoints=
                    Math.addExact(
                        state.points,
                        1L
                    );
            }catch(ArithmeticException overflow){
                return Result.rejected(
                    "OVERFLOW_STATE",
                    victim.id(),
                    deathSequence,
                    state.kills,
                    state.points
                );
            }

            TreeMap<String,String> replacement=
                new TreeMap<>(
                    before
                );

            replacement.put(
                "version",
                VERSION
            );
            replacement.put(
                "authority",
                AUTHORITY
            );
            replacement.put(
                "kills",
                Long.toString(
                    nextKills
                )
            );
            replacement.put(
                "points",
                Long.toString(
                    nextPoints
                )
            );

            try{
                killer.snapshotExtensions()
                    .replaceNamespace(
                        NAMESPACE,
                        replacement
                    );
            }catch(
                IllegalArgumentException failure
            ){
                return Result.rejected(
                    "NAMESPACE_REJECTED",
                    victim.id(),
                    deathSequence,
                    state.kills,
                    state.points
                );
            }

            Result result=
                Result.applied(
                    victim.id(),
                    deathSequence,
                    nextKills,
                    nextPoints
                );

            settledByDeathSequence.put(
                deathSequence,
                result
            );

            return result;
        }
    }

    synchronized int settledCount(){
        return settledByDeathSequence.size();
    }

    private static State parse(
        SortedMap<String,String> values
    ){
        if(values.isEmpty())
            return State.valid(
                0L,
                0L
            );

        String version=
            values.get(
                "version"
            );

        if(version==null||
           !VERSION.equals(
                version
           ))
            return State.invalid(
                "UNSUPPORTED_VERSION"
            );

        String authority=
            values.get(
                "authority"
            );

        if(authority==null||
           !AUTHORITY.equals(
                authority
           ))
            return State.invalid(
                "AUTHORITY_MISMATCH"
            );

        try{
            long kills=
                parseNonNegative(
                    values.get(
                        "kills"
                    ),
                    "kills"
                );
            long points=
                parseNonNegative(
                    values.get(
                        "points"
                    ),
                    "points"
                );

            return State.valid(
                kills,
                points
            );
        }catch(
            IllegalArgumentException malformed
        ){
            return State.invalid(
                "MALFORMED_STATE"
            );
        }
    }

    private static long parseNonNegative(
        String value,
        String field
    ){
        if(value==null)
            throw new IllegalArgumentException(
                field+" missing"
            );

        final long parsed;

        try{
            parsed=
                Long.parseLong(
                    value
                );
        }catch(NumberFormatException failure){
            throw new IllegalArgumentException(
                field+" invalid",
                failure
            );
        }

        if(parsed<0L)
            throw new IllegalArgumentException(
                field+" negative"
            );

        return parsed;
    }
}
