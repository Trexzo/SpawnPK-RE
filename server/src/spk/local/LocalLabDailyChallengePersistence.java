package spk.local;

import java.util.*;

/**
 * Versioned PlayerSnapshot extension for the explicit LocalLab G14 Daily PvM
 * challenge. This is not claimed as original SpawnPK persistence authority.
 */
final class LocalLabDailyChallengePersistence {
    static final String NAMESPACE=
        "daily-pvm-g14";
    static final String VERSION="1";

    static final class Snapshot {
        final long progress;

        Snapshot(long progress){
            if(progress<0L||
               progress>LocalLabDailyChallengeRuntime.GOAL)
                throw new IllegalArgumentException(
                    "Daily PvM persisted progress="+progress
                );

            this.progress=progress;
        }

        boolean complete(){
            return progress==
                LocalLabDailyChallengeRuntime.GOAL;
        }
    }

    static SortedMap<String,String> encode(
        DailyChallengeApplicationService.ChallengeSnapshot
            challenge
    ){
        DailyChallengeApplicationService.ChallengeSnapshot
            checked=
                Objects.requireNonNull(
                    challenge,
                    "challenge"
                );

        if(!LocalLabDailyChallengeRuntime
                .CHALLENGE_KEY
                .equals(
                    checked.challengeKey
                )||
           !LocalLabDailyChallengeRuntime
                .OBJECTIVE_KEY
                .equals(
                    checked.objectiveKey
                )||
           !LocalLabDailyChallengeRuntime
                .AUTHORITY
                .equals(
                    checked.sourceAuthority
                )||
           checked.target!=
                LocalLabDailyChallengeRuntime.GOAL||
           checked.claimed||
           checked.current<0L||
           checked.current>
                LocalLabDailyChallengeRuntime.GOAL||
           checked.complete!=
                (checked.current==
                    LocalLabDailyChallengeRuntime.GOAL))
            throw new IllegalArgumentException(
                "unsupported Daily PvM snapshot"
            );

        TreeMap<String,String> values=
            new TreeMap<>();

        values.put("version",VERSION);
        values.put(
            "authority",
            LocalLabDailyChallengeRuntime.AUTHORITY
        );
        values.put(
            "challenge-key",
            LocalLabDailyChallengeRuntime.CHALLENGE_KEY
        );
        values.put(
            "objective-key",
            LocalLabDailyChallengeRuntime.OBJECTIVE_KEY
        );
        values.put(
            "progress",
            Long.toString(
                checked.current
            )
        );
        values.put(
            "goal",
            Long.toString(
                checked.target
            )
        );
        values.put(
            "state",
            checked.complete
                ?"COMPLETE"
                :"ACTIVE"
        );

        return Collections.unmodifiableSortedMap(
            values
        );
    }

    static Snapshot decode(
        SortedMap<String,String> values
    ){
        if(values==null||
           values.isEmpty())
            return null;

        TreeSet<String> expected=
            new TreeSet<>(
                Arrays.asList(
                    "version",
                    "authority",
                    "challenge-key",
                    "objective-key",
                    "progress",
                    "goal",
                    "state"
                )
            );

        if(!expected.equals(
                new TreeSet<>(
                    values.keySet()
                )))
            throw invalid(
                "key-set",
                values.keySet().toString()
            );

        require(
            VERSION,
            values.get("version"),
            "version"
        );
        require(
            LocalLabDailyChallengeRuntime.AUTHORITY,
            values.get("authority"),
            "authority"
        );
        require(
            LocalLabDailyChallengeRuntime.CHALLENGE_KEY,
            values.get("challenge-key"),
            "challenge-key"
        );
        require(
            LocalLabDailyChallengeRuntime.OBJECTIVE_KEY,
            values.get("objective-key"),
            "objective-key"
        );
        require(
            Long.toString(
                LocalLabDailyChallengeRuntime.GOAL
            ),
            values.get("goal"),
            "goal"
        );

        long progress;

        try{
            progress=
                Long.parseLong(
                    cleanRequired(
                        values.get("progress"),
                        "progress"
                    )
                );
        }catch(RuntimeException failure){
            throw invalid(
                "progress",
                values.get("progress")
            );
        }

        Snapshot snapshot=
            new Snapshot(
                progress
            );

        String expectedState=
            snapshot.complete()
                ?"COMPLETE"
                :"ACTIVE";

        require(
            expectedState,
            values.get("state"),
            "state"
        );

        return snapshot;
    }

    private static void require(
        String expected,
        String actual,
        String key
    ){
        if(!expected.equals(
                clean(actual)
            ))
            throw invalid(
                key,
                actual
            );
    }

    private static String cleanRequired(
        String value,
        String key
    ){
        String clean=clean(value);

        if(clean==null)
            throw invalid(
                key,
                value
            );

        return clean;
    }

    private static String clean(
        String value
    ){
        if(value==null)
            return null;

        String clean=value.trim();

        return clean.isEmpty()
            ?null
            :clean;
    }

    private static IllegalArgumentException invalid(
        String key,
        String value
    ){
        return new IllegalArgumentException(
            "invalid Daily PvM persistence "+
            key+
            "="+
            value
        );
    }

    private LocalLabDailyChallengePersistence(){}
}
