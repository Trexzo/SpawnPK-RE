package spk.local;

import java.util.*;

/**
 * Snapshot-extension codec for explicit LocalLab Blood Slayer PvM policy.
 *
 * v1 is the historical Monster-Hunter-only contract. v2 keeps the same
 * namespace/key set while binding persisted state to one supported PvM mode,
 * task identity and objective identity.
 */
final class LocalLabSlayerPersistence {
    static final String NAMESPACE=
        "blood-slayer-g4";
    static final String LEGACY_VERSION="1";
    static final String VERSION="2";

    enum State {
        ACTIVE,
        COMPLETED
    }

    static final class Snapshot {
        final BloodSlayerModeService.Mode mode;
        final State state;
        final long progress;
        final long sourceAssignedTick;
        final long sourceTransitionTick;

        Snapshot(
            BloodSlayerModeService.Mode mode,
            State state,
            long progress,
            long sourceAssignedTick,
            long sourceTransitionTick
        ){
            this.mode=Objects.requireNonNull(
                mode,
                "mode"
            );
            this.state=Objects.requireNonNull(
                state,
                "state"
            );
            this.progress=progress;
            this.sourceAssignedTick=
                sourceAssignedTick;
            this.sourceTransitionTick=
                sourceTransitionTick;
        }
    }

    static SortedMap<String,String> encode(
        LocalLabSlayerRuntime.StatusSnapshot status
    ){
        Objects.requireNonNull(
            status,
            "status"
        );

        SlayerTaskService.Snapshot task=
            Objects.requireNonNull(
                status.task,
                "task"
            );

        if(status.persistenceError!=null)
            throw new IllegalArgumentException(
                "cannot persist invalid Slayer state "+
                status.persistenceError
            );

        BloodSlayerModeService.Mode mode=
            Objects.requireNonNull(
                status.selectedMode,
                "selectedMode"
            );

        if(!LocalLabSlayerRuntime
                .supportedMode(mode))
            throw new IllegalArgumentException(
                "unsupported persisted Blood Slayer mode "+
                mode
            );

        String taskKey=
            LocalLabSlayerRuntime
                .taskKeyFor(mode);
        String objectiveKey=
            LocalLabSlayerRuntime
                .objectiveKeyFor(mode);

        if(!taskKey.equals(
                task.definition.taskKey)||
           !LocalLabSlayerRuntime.AUTHORITY.equals(
                task.definition.sourceAuthority))
            throw new IllegalArgumentException(
                "unsupported persisted Blood Slayer task authority"
            );

        if(task.objective==null||
           task.objective.goal!=
                LocalLabSlayerRuntime.OBJECTIVE_GOAL||
           !objectiveKey.equals(
                task.objective.key)||
           !LocalLabSlayerRuntime.AUTHORITY.equals(
                task.objective.sourceAuthority))
            throw new IllegalArgumentException(
                "unsupported persisted Blood Slayer objective"
            );

        State state=
            lifecycleState(task);

        TreeMap<String,String> values=
            new TreeMap<>();

        values.put("version",VERSION);
        values.put(
            "authority",
            LocalLabSlayerRuntime.AUTHORITY
        );
        values.put(
            "task-key",
            taskKey
        );
        values.put(
            "mode",
            mode.name()
        );
        values.put(
            "state",
            state.name()
        );
        values.put(
            "progress",
            Long.toString(
                task.objective.progress
            )
        );
        values.put(
            "goal",
            Long.toString(
                task.objective.goal
            )
        );
        values.put(
            "source-assigned-tick",
            Long.toString(
                task.assignedTick
            )
        );
        values.put(
            "source-transition-tick",
            Long.toString(
                task.transitionTick
            )
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

        requireExactKeys(values);

        String version=
            cleanRequired(
                values.get("version"),
                "version"
            );

        if(LEGACY_VERSION.equals(version))
            return decodeVersion(
                values,
                BloodSlayerModeService.Mode
                    .MONSTER_HUNTER_PVM,
                LocalLabSlayerRuntime
                    .LEGACY_AUTHORITY,
                LocalLabSlayerRuntime.TASK_KEY
            );

        if(!VERSION.equals(version))
            throw invalid(
                "version",
                values.get("version")
            );

        final BloodSlayerModeService.Mode mode;

        try{
            mode=
                BloodSlayerModeService.Mode
                    .valueOf(
                        cleanRequired(
                            values.get("mode"),
                            "mode"
                        )
                    );
        }catch(RuntimeException failure){
            throw invalid(
                "mode",
                values.get("mode")
            );
        }

        if(!LocalLabSlayerRuntime
                .supportedMode(mode))
            throw invalid(
                "mode",
                values.get("mode")
            );

        return decodeVersion(
            values,
            mode,
            LocalLabSlayerRuntime.AUTHORITY,
            LocalLabSlayerRuntime
                .taskKeyFor(mode)
        );
    }

    private static Snapshot decodeVersion(
        SortedMap<String,String> values,
        BloodSlayerModeService.Mode mode,
        String expectedAuthority,
        String expectedTaskKey
    ){
        if(!expectedAuthority.equals(
                clean(values.get("authority"))))
            throw invalid(
                "authority",
                values.get("authority")
            );

        if(!expectedTaskKey.equals(
                clean(values.get("task-key"))))
            throw invalid(
                "task-key",
                values.get("task-key")
            );

        if(!mode.name().equals(
                clean(values.get("mode"))))
            throw invalid(
                "mode",
                values.get("mode")
            );

        final State state;

        try{
            state=State.valueOf(
                cleanRequired(
                    values.get("state"),
                    "state"
                )
            );
        }catch(RuntimeException failure){
            throw invalid(
                "state",
                values.get("state")
            );
        }

        long progress=
            parseLong(
                values,
                "progress"
            );
        long goal=
            parseLong(
                values,
                "goal"
            );
        long assigned=
            parseLong(
                values,
                "source-assigned-tick"
            );
        long transition=
            parseLong(
                values,
                "source-transition-tick"
            );

        if(goal!=LocalLabSlayerRuntime
                .OBJECTIVE_GOAL)
            throw invalid(
                "goal",
                Long.toString(goal)
            );

        if(assigned<0L)
            throw invalid(
                "source-assigned-tick",
                Long.toString(assigned)
            );

        if(state==State.ACTIVE){
            if(progress!=0L||
               transition!=-1L)
                throw new IllegalArgumentException(
                    "invalid active Blood Slayer persistence progress="+
                    progress+
                    " transition="+
                    transition
                );
        }else{
            if(progress!=
                    LocalLabSlayerRuntime
                        .OBJECTIVE_GOAL||
               transition<assigned)
                throw new IllegalArgumentException(
                    "invalid completed Blood Slayer persistence progress="+
                    progress+
                    " assigned="+
                    assigned+
                    " transition="+
                    transition
                );
        }

        return new Snapshot(
            mode,
            state,
            progress,
            assigned,
            transition
        );
    }

    private static State lifecycleState(
        SlayerTaskService.Snapshot task
    ){
        if(task.state==
                SlayerTaskService.State.ACTIVE&&
           task.objective.progress==0L)
            return State.ACTIVE;

        if(task.state==
                SlayerTaskService.State.COMPLETED&&
           task.objective.progress==
                LocalLabSlayerRuntime.OBJECTIVE_GOAL)
            return State.COMPLETED;

        throw new IllegalArgumentException(
            "unsupported persisted Blood Slayer lifecycle state="+
            task.state+
            " progress="+
            task.objective.progress
        );
    }

    private static void requireExactKeys(
        SortedMap<String,String> values
    ){
        TreeSet<String> expected=
            new TreeSet<>(
                Arrays.asList(
                    "version",
                    "authority",
                    "task-key",
                    "mode",
                    "state",
                    "progress",
                    "goal",
                    "source-assigned-tick",
                    "source-transition-tick"
                )
            );

        if(!expected.equals(
                new TreeSet<>(
                    values.keySet()
                )))
            throw new IllegalArgumentException(
                "Blood Slayer persistence key set mismatch expected="+
                expected+
                " actual="+
                values.keySet()
            );
    }

    private static long parseLong(
        Map<String,String> values,
        String key
    ){
        try{
            return Long.parseLong(
                cleanRequired(
                    values.get(key),
                    key
                )
            );
        }catch(RuntimeException failure){
            throw invalid(
                key,
                values.get(key)
            );
        }
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

    private static IllegalArgumentException
        invalid(
            String key,
            String value
        ){
        return new IllegalArgumentException(
            "invalid Blood Slayer persistence "+
            key+
            "="+
            value
        );
    }

    private LocalLabSlayerPersistence(){}
}
