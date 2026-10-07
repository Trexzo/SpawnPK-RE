package spk.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Versioned PlayerSnapshot extension for the explicit LocalLab G15 Task Scroll.
 *
 * This stores only assignment/progress contract state. Runtime TaskScrollId,
 * Track state, reward payload/settlement and original SpawnPK persistence
 * semantics are deliberately absent.
 */
final class LocalLabTaskScrollPersistence {
    static final String NAMESPACE=
        "task-scroll-pvm-g15";
    static final String VERSION="1";

    static final class Snapshot {
        final long progress;

        Snapshot(
            long progress
        ){
            if(progress<0L||
               progress>LocalLabTaskScrollRuntime.GOAL)
                throw new IllegalArgumentException(
                    "Task Scroll persisted progress="+
                    progress
                );

            this.progress=progress;
        }

        boolean complete(){
            return progress==
                LocalLabTaskScrollRuntime.GOAL;
        }
    }

    static SortedMap<String,String> encode(
        TaskScrollService.Snapshot task
    ){
        TaskScrollService.Snapshot checked=
            Objects.requireNonNull(
                task,
                "task"
            );

        if(!LocalLabTaskScrollRuntime
                .TASK_KEY
                .equals(
                    checked.definition.taskKey
                )||
           !LocalLabTaskScrollRuntime
                .OBJECTIVE_KEY
                .equals(
                    checked.definition.objectiveKey
                )||
           !LocalLabTaskScrollRuntime
                .AUTHORITY
                .equals(
                    checked.definition.sourceAuthority
                )||
           !LocalLabTaskScrollRuntime
                .OBJECTIVE_KEY
                .equals(
                    checked.objective.key
                )||
           !LocalLabTaskScrollRuntime
                .AUTHORITY
                .equals(
                    checked.objective.sourceAuthority
                )||
           checked.objective.goal!=
                LocalLabTaskScrollRuntime.GOAL||
           checked.objective.claimed||
           checked.tracked||
           checked.objective.progress<0L||
           checked.objective.progress>
                LocalLabTaskScrollRuntime.GOAL||
           checked.state!=
                (
                    checked.objective.complete
                        ?TaskScrollService.State
                            .COMPLETE_UNCLAIMED
                        :TaskScrollService.State.ACTIVE
                ))
            throw new IllegalArgumentException(
                "unsupported LocalLab Task Scroll snapshot"
            );

        TreeMap<String,String> values=
            new TreeMap<>();

        values.put(
            "version",
            VERSION
        );
        values.put(
            "authority",
            LocalLabTaskScrollRuntime.AUTHORITY
        );
        values.put(
            "task-key",
            LocalLabTaskScrollRuntime.TASK_KEY
        );
        values.put(
            "objective-key",
            LocalLabTaskScrollRuntime.OBJECTIVE_KEY
        );
        values.put(
            "progress",
            Long.toString(
                checked.objective.progress
            )
        );
        values.put(
            "goal",
            Long.toString(
                checked.objective.goal
            )
        );
        values.put(
            "state",
            checked.objective.complete
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
                    "task-key",
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
            LocalLabTaskScrollRuntime.AUTHORITY,
            values.get("authority"),
            "authority"
        );
        require(
            LocalLabTaskScrollRuntime.TASK_KEY,
            values.get("task-key"),
            "task-key"
        );
        require(
            LocalLabTaskScrollRuntime.OBJECTIVE_KEY,
            values.get("objective-key"),
            "objective-key"
        );
        require(
            Long.toString(
                LocalLabTaskScrollRuntime.GOAL
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
            "invalid Task Scroll persistence "+
            key+
            "="+
            value
        );
    }

    private LocalLabTaskScrollPersistence(){}
}
