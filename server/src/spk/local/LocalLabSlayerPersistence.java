package spk.local;

import java.util.*;

/**
 * Snapshot-extension codec for the explicit G4 Blood Slayer one-shot policy.
 *
 * Historical world ticks are persisted as evidence only. A fresh World
 * replays the same canonical lifecycle at its current logical tick because
 * GameClock tick identity is not durable across server restart.
 */
final class LocalLabSlayerPersistence {
    static final String NAMESPACE=
        "blood-slayer-g4";
    static final String VERSION="1";

    enum State {
        ACTIVE,
        COMPLETED
    }

    static final class Snapshot {
        final State state;
        final long progress;
        final long sourceAssignedTick;
        final long sourceTransitionTick;

        Snapshot(
            State state,
            long progress,
            long sourceAssignedTick,
            long sourceTransitionTick
        ){
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

        if(status.selectedMode!=
                BloodSlayerModeService.Mode
                    .MONSTER_HUNTER_PVM)
            throw new IllegalArgumentException(
                "unsupported persisted Blood Slayer mode "+
                status.selectedMode
            );

        if(!LocalLabSlayerRuntime.TASK_KEY.equals(
                task.definition.taskKey)||
           !LocalLabSlayerRuntime.AUTHORITY.equals(
                task.definition.sourceAuthority))
            throw new IllegalArgumentException(
                "unsupported persisted Blood Slayer task authority"
            );

        if(task.objective==null||
           task.objective.goal!=
                LocalLabSlayerRuntime.OBJECTIVE_GOAL||
           !LocalLabSlayerRuntime.OBJECTIVE_KEY.equals(
                task.objective.key)||
           !LocalLabSlayerRuntime.AUTHORITY.equals(
                task.objective.sourceAuthority))
            throw new IllegalArgumentException(
                "unsupported persisted Blood Slayer objective"
            );

        State state;

        if(task.state==
                SlayerTaskService.State.ACTIVE&&
           task.objective.progress==0L){
            state=State.ACTIVE;
        }else if(task.state==
                    SlayerTaskService.State.COMPLETED&&
                task.objective.progress==
                    LocalLabSlayerRuntime.OBJECTIVE_GOAL){
            state=State.COMPLETED;
        }else{
            throw new IllegalArgumentException(
                "unsupported persisted Blood Slayer lifecycle state="+
                task.state+
                " progress="+
                task.objective.progress
            );
        }

        TreeMap<String,String> values=
            new TreeMap<>();

        values.put("version",VERSION);
        values.put(
            "authority",
            LocalLabSlayerRuntime.AUTHORITY
        );
        values.put(
            "task-key",
            LocalLabSlayerRuntime.TASK_KEY
        );
        values.put(
            "mode",
            BloodSlayerModeService.Mode
                .MONSTER_HUNTER_PVM
                .name()
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

        if(!VERSION.equals(
                clean(values.get("version"))))
            throw invalid(
                "version",
                values.get("version")
            );

        if(!LocalLabSlayerRuntime.AUTHORITY.equals(
                clean(values.get("authority"))))
            throw invalid(
                "authority",
                values.get("authority")
            );

        if(!LocalLabSlayerRuntime.TASK_KEY.equals(
                clean(values.get("task-key"))))
            throw invalid(
                "task-key",
                values.get("task-key")
            );

        if(!BloodSlayerModeService.Mode
                .MONSTER_HUNTER_PVM
                .name()
                .equals(
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
            state,
            progress,
            assigned,
            transition
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
