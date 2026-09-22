package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Protocol-independent Slayer task lifecycle composed over player-scoped
 * ObjectiveProgressService ledgers.
 *
 * Task selection, kill attribution, points, rewards, skip costs and exact
 * SpawnPK task catalogs remain caller/content authority.
 */
final class SlayerTaskService {
    enum State {
        ACTIVE,
        COMPLETED,
        SKIPPED,
        CANCELLED
    }

    @FunctionalInterface
    interface LedgerResolver {
        ObjectiveProgressService resolve(
            String playerRef
        );
    }

    static final class TaskId
        implements Comparable<TaskId> {

        private final long value;

        TaskId(long value){
            if(value<=0L)
                throw new IllegalArgumentException(
                    "taskId="+value
                );
            this.value=value;
        }

        long value(){
            return value;
        }

        @Override public int compareTo(TaskId other){
            return Long.compare(
                value,
                Objects.requireNonNull(
                    other,
                    "other"
                ).value
            );
        }

        @Override public boolean equals(Object other){
            return other instanceof TaskId&&
                value==((TaskId)other).value;
        }

        @Override public int hashCode(){
            return Long.hashCode(value);
        }

        @Override public String toString(){
            return "slayer-task-"+
                Long.toUnsignedString(value);
        }
    }

    static final class Definition {
        final String taskKey;
        final String familyKey;
        final String targetKey;
        final String objectiveKey;
        final String sourceAuthority;

        Definition(
            String taskKey,
            String familyKey,
            String targetKey,
            String objectiveKey,
            String sourceAuthority
        ){
            this.taskKey=
                normalizeKey(
                    taskKey,
                    "taskKey"
                );
            this.familyKey=
                normalizeKey(
                    familyKey,
                    "familyKey"
                );
            this.targetKey=
                normalizeKey(
                    targetKey,
                    "targetKey"
                );
            this.objectiveKey=
                ObjectiveDefinition
                    .normalizeKey(
                        objectiveKey
                    );
            this.sourceAuthority=
                requireText(
                    sourceAuthority,
                    "sourceAuthority"
                );
        }
    }

    static final class Snapshot {
        final TaskId taskId;
        final String playerRef;
        final Definition definition;
        final State state;
        final long assignedTick;
        final long transitionTick;
        final ObjectiveProgressService.Snapshot
            objective;

        Snapshot(
            Task task,
            ObjectiveProgressService.Snapshot
                objective
        ){
            this.taskId=task.id;
            this.playerRef=task.playerRef;
            this.definition=task.definition;
            this.state=task.state;
            this.assignedTick=
                task.assignedTick;
            this.transitionTick=
                task.transitionTick;
            this.objective=objective;
        }

        boolean terminal(){
            return state!=State.ACTIVE;
        }
    }

    static final class KillResult {
        final boolean matchedTarget;
        final boolean progressed;
        final boolean completedNow;
        final Snapshot task;

        KillResult(
            boolean matchedTarget,
            boolean progressed,
            boolean completedNow,
            Snapshot task
        ){
            this.matchedTarget=
                matchedTarget;
            this.progressed=progressed;
            this.completedNow=
                completedNow;
            this.task=task;
        }
    }

    private static final class Task {
        final TaskId id;
        final String playerRef;
        final Definition definition;
        final long assignedTick;

        State state=State.ACTIVE;
        long transitionTick=-1L;
        long lastObservedTick;

        Task(
            TaskId id,
            String playerRef,
            Definition definition,
            long assignedTick
        ){
            this.id=id;
            this.playerRef=playerRef;
            this.definition=definition;
            this.assignedTick=
                assignedTick;
            this.lastObservedTick=
                assignedTick;
        }
    }

    private final LedgerResolver ledgers;
    private final AtomicLong taskSequence=
        new AtomicLong();

    private final LinkedHashMap<String,Definition>
        definitions=
            new LinkedHashMap<>();

    private final HashMap<String,String>
        taskByObjective=
            new HashMap<>();

    private final LinkedHashMap<TaskId,Task>
        tasks=
            new LinkedHashMap<>();

    private final HashMap<String,TaskId>
        activeByPlayer=
            new HashMap<>();

    SlayerTaskService(
        LedgerResolver ledgers
    ){
        this.ledgers=
            Objects.requireNonNull(
                ledgers,
                "ledgers"
            );
    }

    synchronized Definition registerDefinition(
        Definition definition
    ){
        Definition checked=
            Objects.requireNonNull(
                definition,
                "definition"
            );

        if(definitions.containsKey(
                checked.taskKey))
            throw new IllegalStateException(
                "duplicate Slayer task key "+
                checked.taskKey
            );

        String prior=
            taskByObjective.putIfAbsent(
                checked.objectiveKey,
                checked.taskKey
            );

        if(prior!=null)
            throw new IllegalStateException(
                "Slayer objective "+
                checked.objectiveKey+
                " already bound to task "+
                prior
            );

        definitions.put(
            checked.taskKey,
            checked
        );

        return checked;
    }

    synchronized Snapshot assign(
        String playerRef,
        String taskKey,
        long worldTick
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        observeTick(worldTick);

        if(activeByPlayer.containsKey(
                player))
            throw new IllegalStateException(
                "player already has active Slayer task "+
                player
            );

        Definition definition=
            definitions.get(
                normalizeKey(
                    taskKey,
                    "taskKey"
                )
            );

        if(definition==null)
            throw new IllegalArgumentException(
                "unknown Slayer task "+
                taskKey
            );

        ObjectiveProgressService ledger=
            requireLedger(player);

        ObjectiveProgressService.Snapshot
            objective=
                ledger.get(
                    definition.objectiveKey
                );

        if(objective==null)
            throw new IllegalArgumentException(
                "missing Slayer objective "+
                definition.objectiveKey+
                " player="+player
            );

        if(objective.complete)
            throw new IllegalStateException(
                "cannot assign completed Slayer objective "+
                definition.objectiveKey+
                " player="+player
            );

        TaskId id=nextTaskId();

        Task task=
            new Task(
                id,
                player,
                definition,
                worldTick
            );

        tasks.put(id,task);
        activeByPlayer.put(
            player,
            id
        );

        return snapshotOf(
            task,
            ledger
        );
    }

    synchronized KillResult recordValidatedKill(
        String playerRef,
        String targetKey,
        long amount,
        long worldTick
    ){
        if(amount<=0L)
            throw new IllegalArgumentException(
                "amount="+amount
            );

        String player=
            normalizePlayer(
                playerRef
            );
        String target=
            normalizeKey(
                targetKey,
                "targetKey"
            );
        observeTick(worldTick);

        Task task=requireActive(player);

        requireForwardTick(
            task,
            worldTick
        );
        task.lastObservedTick=
            worldTick;

        ObjectiveProgressService ledger=
            requireLedger(player);

        if(!task.definition.targetKey
                .equals(target))
            return new KillResult(
                false,
                false,
                false,
                snapshotOf(
                    task,
                    ledger
                )
            );

        ObjectiveProgressService.ProgressResult
            progress=
                ledger.advance(
                    task.definition.objectiveKey,
                    amount
                );

        boolean completedNow=false;

        if(progress.after.complete){
            completedNow=
                transition(
                    task,
                    State.COMPLETED,
                    worldTick
                );
        }

        return new KillResult(
            true,
            progress.after.progress!=
                progress.before.progress,
            completedNow,
            snapshotOf(
                task,
                ledger
            )
        );
    }

    synchronized Snapshot refresh(
        TaskId taskId,
        long worldTick
    ){
        observeTick(worldTick);

        Task task=requireTask(taskId);
        requireForwardTick(
            task,
            worldTick
        );
        task.lastObservedTick=
            worldTick;
        ObjectiveProgressService ledger=
            requireLedger(
                task.playerRef
            );

        if(task.state==State.ACTIVE){
            ObjectiveProgressService.Snapshot
                objective=
                    ledger.get(
                        task.definition.objectiveKey
                    );

            if(objective==null)
                throw new IllegalStateException(
                    "Slayer objective disappeared "+
                    task.definition.objectiveKey
                );

            if(objective.complete)
                transition(
                    task,
                    State.COMPLETED,
                    worldTick
                );
        }

        return snapshotOf(
            task,
            ledger
        );
    }

    synchronized Snapshot skip(
        TaskId taskId,
        long worldTick
    ){
        return terminalTransition(
            taskId,
            State.SKIPPED,
            worldTick
        );
    }

    synchronized Snapshot cancel(
        TaskId taskId,
        long worldTick
    ){
        return terminalTransition(
            taskId,
            State.CANCELLED,
            worldTick
        );
    }

    synchronized Snapshot get(
        TaskId taskId
    ){
        Task task=
            tasks.get(
                Objects.requireNonNull(
                    taskId,
                    "taskId"
                )
            );

        if(task==null)
            return null;

        return snapshotOf(
            task,
            requireLedger(
                task.playerRef
            )
        );
    }

    synchronized Snapshot active(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        TaskId id=
            activeByPlayer.get(
                player
            );

        return id==null
            ?null
            :get(id);
    }

    synchronized int definitionCount(){
        return definitions.size();
    }

    synchronized int taskCount(){
        return tasks.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Task> ordered=
            new ArrayList<>(
                tasks.values()
            );

        ordered.sort(
            Comparator.comparing(
                task->task.id
            )
        );

        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Task task:ordered)
            out.add(
                snapshotOf(
                    task,
                    requireLedger(
                        task.playerRef
                    )
                )
            );

        return Collections.unmodifiableList(
            out
        );
    }

    private Snapshot terminalTransition(
        TaskId taskId,
        State target,
        long worldTick
    ){
        observeTick(worldTick);

        Task task=requireTask(taskId);

        if(task.state==target)
            return snapshotOf(
                task,
                requireLedger(
                    task.playerRef
                )
            );

        if(task.state!=State.ACTIVE)
            throw new IllegalStateException(
                "Slayer task already terminal "+
                task.id+
                " state="+task.state
            );

        requireForwardTick(
            task,
            worldTick
        );
        task.lastObservedTick=
            worldTick;

        transition(
            task,
            target,
            worldTick
        );

        return snapshotOf(
            task,
            requireLedger(
                task.playerRef
            )
        );
    }

    private boolean transition(
        Task task,
        State target,
        long worldTick
    ){
        if(task.state!=State.ACTIVE)
            return false;

        task.state=target;
        task.transitionTick=worldTick;
        task.lastObservedTick=
            worldTick;

        TaskId active=
            activeByPlayer.get(
                task.playerRef
            );

        if(task.id.equals(active))
            activeByPlayer.remove(
                task.playerRef
            );

        return true;
    }

    private Snapshot snapshotOf(
        Task task,
        ObjectiveProgressService ledger
    ){
        ObjectiveProgressService.Snapshot
            objective=
                ledger.get(
                    task.definition.objectiveKey
                );

        if(objective==null)
            throw new IllegalStateException(
                "Slayer objective disappeared "+
                task.definition.objectiveKey
            );

        return new Snapshot(
            task,
            objective
        );
    }

    private Task requireActive(
        String player
    ){
        TaskId id=
            activeByPlayer.get(player);

        if(id==null)
            throw new IllegalStateException(
                "player has no active Slayer task "+
                player
            );

        Task task=requireTask(id);

        if(task.state!=State.ACTIVE)
            throw new IllegalStateException(
                "active Slayer index points at "+
                task.state
            );

        return task;
    }

    private Task requireTask(
        TaskId taskId
    ){
        TaskId id=
            Objects.requireNonNull(
                taskId,
                "taskId"
            );
        Task task=tasks.get(id);

        if(task==null)
            throw new IllegalArgumentException(
                "unknown Slayer task id="+id
            );

        return task;
    }

    private ObjectiveProgressService requireLedger(
        String player
    ){
        ObjectiveProgressService ledger=
            ledgers.resolve(player);

        if(ledger==null)
            throw new IllegalStateException(
                "missing Slayer objective ledger player="+
                player
            );

        return ledger;
    }

    private TaskId nextTaskId(){
        long value=
            taskSequence.incrementAndGet();

        if(value<=0L)
            throw new IllegalStateException(
                "Slayer task sequence exhausted"
            );

        return new TaskId(value);
    }

    private static void requireForwardTick(
        Task task,
        long worldTick
    ){
        if(worldTick<
                task.lastObservedTick)
            throw new IllegalArgumentException(
                "Slayer task tick moved backwards "+
                worldTick+
                " < "+
                task.lastObservedTick
            );
    }

    private static void observeTick(long worldTick){
        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );
    }

    private static String normalizePlayer(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "playerRef"
            );

        String normalized=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "playerRef blank"
            );

        return normalized;
    }

    private static String normalizeKey(
        String value,
        String field
    ){
        return MatchRules.normalizeKey(
            value,
            field
        );
    }

    private static String requireText(
        String value,
        String field
    ){
        return MatchRules.requireText(
            value,
            field
        );
    }
}
