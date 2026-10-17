package spk.local;

import java.util.*;

/**
 * Versioned PlayerSnapshot extension for the explicit G16 CUSTOM_LOCALLAB
 * Adventure PvM activation/progress contract.
 *
 * Reward, claim, teleport, tips/input and reset policy are deliberately absent.
 */
final class LocalLabAdventurePersistence {
    static final String NAMESPACE=
        "adventure-pvm-g16";
    static final String VERSION="1";

    static final class Snapshot {
        final long progress;

        Snapshot(
            long progress
        ){
            if(progress<0L||
               progress>LocalLabAdventureRuntime.GOAL)
                throw new IllegalArgumentException(
                    "Adventure persisted progress="+
                    progress
                );

            this.progress=progress;
        }

        boolean complete(){
            return progress==
                LocalLabAdventureRuntime.GOAL;
        }
    }

    static SortedMap<String,String> encode(
        ObjectiveProgressService.Snapshot objective
    ){
        ObjectiveProgressService.Snapshot checked=
            Objects.requireNonNull(
                objective,
                "objective"
            );

        if(!LocalLabAdventureRuntime
                .OBJECTIVE_KEY
                .equals(
                    checked.key
                )||
           !LocalLabAdventureRuntime
                .AUTHORITY
                .equals(
                    checked.sourceAuthority
                )||
           checked.goal!=
                LocalLabAdventureRuntime.GOAL||
           checked.progress<0L||
           checked.progress>
                LocalLabAdventureRuntime.GOAL||
           checked.claimed||
           checked.complete!=
                (checked.progress==
                    LocalLabAdventureRuntime.GOAL))
            throw new IllegalArgumentException(
                "unsupported LocalLab Adventure snapshot"
            );

        TreeMap<String,String> values=
            new TreeMap<>();

        values.put(
            "version",
            VERSION
        );
        values.put(
            "authority",
            LocalLabAdventureRuntime.AUTHORITY
        );
        values.put(
            "chapter-key",
            LocalLabAdventureRuntime.CHAPTER_KEY
        );
        values.put(
            "objective-key",
            LocalLabAdventureRuntime.OBJECTIVE_KEY
        );
        values.put(
            "progress",
            Long.toString(
                checked.progress
            )
        );
        values.put(
            "goal",
            Long.toString(
                checked.goal
            )
        );
        values.put(
            "state",
            checked.complete
                ?"COMPLETE_UNCLAIMED"
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
                    "chapter-key",
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
            LocalLabAdventureRuntime.AUTHORITY,
            values.get("authority"),
            "authority"
        );
        require(
            LocalLabAdventureRuntime.CHAPTER_KEY,
            values.get("chapter-key"),
            "chapter-key"
        );
        require(
            LocalLabAdventureRuntime.OBJECTIVE_KEY,
            values.get("objective-key"),
            "objective-key"
        );
        require(
            Long.toString(
                LocalLabAdventureRuntime.GOAL
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

        require(
            snapshot.complete()
                ?"COMPLETE_UNCLAIMED"
                :"ACTIVE",
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
        String clean=
            clean(value);

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

        String clean=
            value.trim();

        return clean.isEmpty()
            ?null
            :clean;
    }

    private static IllegalArgumentException invalid(
        String key,
        String value
    ){
        return new IllegalArgumentException(
            "invalid Adventure persistence "+
            key+
            "="+
            value
        );
    }

    private LocalLabAdventurePersistence(){}
}
