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
        boolean externalOperationInFlight;
        ObjectiveProgressService.Snapshot
            terminalObjective;
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

        Task snapshotCopy(){
            Task copy=
                new Task(
                    id,
                    playerRef,
                    definition,
                    assignedTick
                );
            copy.state=state;
            copy.terminalObjective=
                terminalObjective;
            copy.transitionTick=
                transitionTick;
            copy.lastObservedTick=
                lastObservedTick;
            return copy;
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

    Snapshot assign(
        String playerRef,
        String taskKey,
        long worldTick
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        observeTick(worldTick);

        final Definition definition;

        synchronized(this){
            if(activeByPlayer.containsKey(
                    player))
                throw new IllegalStateException(
                    "player already has active Slayer task "+
                    player
                );

            definition=
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
        }

        ObjectiveProgressService ledger=
            requireLedger(player);

        ObjectiveProgressService.Snapshot
            objective=
                requireObjective(
                    definition,
                    ledger
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

        synchronized(this){
            if(activeByPlayer.containsKey(
                    player))
                throw new IllegalStateException(
                    "player already has active Slayer task "+
                    player
                );

            if(definitions.get(
                    definition.taskKey)!=
                    definition)
                throw new IllegalStateException(
                    "Slayer definition changed "+
                    definition.taskKey
                );

            tasks.put(id,task);
            activeByPlayer.put(
                player,
                id
            );
        }

        return snapshotOf(
            task.snapshotCopy(),
            objective
        );
    }

    KillResult recordValidatedKill(
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

        final Task live;
        final Task captured;

        synchronized(this){
            live=
                requireActiveLocal(
                    player
                );
            reserveExternalOperation(
                live,
                worldTick
            );
            captured=
                live.snapshotCopy();
        }

        ObjectiveProgressService.Snapshot objective=null;
        ObjectiveProgressService.ProgressResult progress=null;
        RuntimeException failure=null;
        boolean matched=
            captured.definition.targetKey
                .equals(target);

        try{
            ObjectiveProgressService ledger=
                requireLedger(player);

            if(matched){
                progress=
                    ledger.advance(
                        captured.definition.objectiveKey,
                        amount
                    );
                objective=
                    requireObjective(
                        captured.definition,
                        ledger
                    );
            }else{
                objective=
                    requireObjective(
                        captured.definition,
                        ledger
                    );
            }
        }catch(RuntimeException error){
            failure=error;
        }

        final Task afterLocal;
        boolean completedNow=false;

        synchronized(this){
            Task current=
                requireTask(
                    captured.id
                );

            if(current!=live)
                throw new IllegalStateException(
                    "Slayer task identity changed "+
                    captured.id
                );

            if(failure==null){
                current.lastObservedTick=
                    worldTick;

                if(matched&&
                   objective.complete){
                    completedNow=
                        transition(
                            current,
                            State.COMPLETED,
                            worldTick
                        );
                    if(completedNow)
                        current.terminalObjective=
                            objective;
                }
            }

            current.externalOperationInFlight=
                false;
            afterLocal=
                current.snapshotCopy();
        }

        if(failure!=null)
            throw failure;

        return new KillResult(
            matched,
            matched&&
                progress.after.progress!=
                    progress.before.progress,
            completedNow,
            snapshotOf(
                afterLocal,
                objective
            )
        );
    }

    Snapshot refresh(
        TaskId taskId,
        long worldTick
    ){
        observeTick(worldTick);

        final Task live;
        final Task captured;

        synchronized(this){
            live=
                requireTask(
                    taskId
                );
            reserveExternalOperation(
                live,
                worldTick
            );
            captured=
                live.snapshotCopy();
        }

        ObjectiveProgressService.Snapshot objective=null;
        RuntimeException failure=null;

        try{
            ObjectiveProgressService ledger=
                requireLedger(
                    captured.playerRef
                );
            objective=
                requireObjective(
                    captured.definition,
                    ledger
                );
        }catch(RuntimeException error){
            failure=error;
        }

        final Task afterLocal;

        synchronized(this){
            Task current=
                requireTask(
                    captured.id
                );

            if(current!=live)
                throw new IllegalStateException(
                    "Slayer task identity changed "+
                    captured.id
                );

            if(failure==null){
                current.lastObservedTick=
                    worldTick;

                if(current.state==
                        State.ACTIVE&&
                   objective.complete&&
                   transition(
                        current,
                        State.COMPLETED,
                        worldTick
                   ))
                    current.terminalObjective=
                        objective;
            }

            current.externalOperationInFlight=
                false;
            afterLocal=
                current.snapshotCopy();
        }

        if(failure!=null)
            throw failure;

        return snapshotOf(
            afterLocal,
            objective
        );
    }

    Snapshot skip(
        TaskId taskId,
        long worldTick
    ){
        return terminalTransition(
            taskId,
            State.SKIPPED,
            worldTick
        );
    }

    Snapshot cancel(
        TaskId taskId,
        long worldTick
    ){
        return terminalTransition(
            taskId,
            State.CANCELLED,
            worldTick
        );
    }

    Snapshot get(
        TaskId taskId
    ){
        final Task captured;

        synchronized(this){
            Task task=
                tasks.get(
                    Objects.requireNonNull(
                        taskId,
                        "taskId"
                    )
                );

            if(task==null)
                return null;

            captured=
                task.snapshotCopy();
        }

        return resolveSnapshot(
            captured
        );
    }

    Snapshot active(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        final Task captured;

        synchronized(this){
            TaskId id=
                activeByPlayer.get(
                    player
                );

            if(id==null)
                return null;

            captured=
                requireTask(id)
                    .snapshotCopy();
        }

        return resolveSnapshot(
            captured
        );
    }

    synchronized int definitionCount(){
        return definitions.size();
    }

    synchronized int taskCount(){
        return tasks.size();
    }

    List<Snapshot> snapshot(){
        final ArrayList<Task> captured=
            new ArrayList<>();

        synchronized(this){
            for(Task task:tasks.values())
                captured.add(
                    task.snapshotCopy()
                );
        }

        captured.sort(
            Comparator.comparing(
                task->task.id
            )
        );

        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Task task:captured)
            out.add(
                resolveSnapshot(
                    task
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

        final Task captured;

        synchronized(this){
            Task task=
                requireTask(taskId);

            if(task.externalOperationInFlight)
                throw new IllegalStateException(
                    "Slayer external operation already in flight "+
                    task.id
                );

            if(task.state==target){
                captured=
                    task.snapshotCopy();
            }else{
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

                captured=
                    task.snapshotCopy();
            }
        }

        return resolveSnapshot(
            captured
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

    private Snapshot resolveSnapshot(
        Task task
    ){
        if(task.terminalObjective!=null)
            return snapshotOf(
                task,
                task.terminalObjective
            );

        ObjectiveProgressService ledger=
            requireLedger(
                task.playerRef
            );

        return snapshotOf(
            task,
            requireObjective(
                task.definition,
                ledger
            )
        );
    }

    private static Snapshot snapshotOf(
        Task task,
        ObjectiveProgressService.Snapshot
            objective
    ){
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

    private static ObjectiveProgressService.Snapshot
        requireObjective(
            Definition definition,
            ObjectiveProgressService ledger
        )
    {
        ObjectiveProgressService.Snapshot
            objective=
                ledger.get(
                    definition.objectiveKey
                );

        if(objective==null)
            throw new IllegalStateException(
                "Slayer objective disappeared "+
                definition.objectiveKey
            );

        return objective;
    }

    private Task requireActiveLocal(
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

    private static void reserveExternalOperation(
        Task task,
        long worldTick
    ){
        requireForwardTick(
            task,
            worldTick
        );

        if(task.externalOperationInFlight)
            throw new IllegalStateException(
                "Slayer external operation already in flight "+
                task.id
            );

        task.externalOperationInFlight=
            true;
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
