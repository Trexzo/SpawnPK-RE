package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Protocol-independent Task Scroll assignment/progress state.
 *
 * Exact client evidence proves task information, Track progress and Collect
 * reward surfaces. Reward payloads, attribution, reset cadence and persistence
 * remain external.
 */
final class TaskScrollService {
    static final int MAX_INFO_LINES=20;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum State {
        ACTIVE,
        COMPLETE_UNCLAIMED,
        CLAIMED,
        CANCELLED
    }

    @FunctionalInterface
    interface LedgerResolver {
        ObjectiveProgressService resolve(
            String playerRef
        );
    }

    static final class TaskScrollId
        implements Comparable<TaskScrollId> {

        private final long value;

        TaskScrollId(long value){
            if(value<=0)
                throw new IllegalArgumentException(
                    "taskScrollId="+value
                );
            this.value=value;
        }

        long value(){
            return value;
        }

        @Override public int compareTo(
            TaskScrollId other
        ){
            return Long.compare(
                value,
                Objects.requireNonNull(
                    other,
                    "other"
                ).value
            );
        }

        @Override public boolean equals(
            Object other
        ){
            return other instanceof TaskScrollId&&
                value==
                    ((TaskScrollId)other).value;
        }

        @Override public int hashCode(){
            return Long.hashCode(value);
        }

        @Override public String toString(){
            return "task-scroll-"+
                Long.toUnsignedString(value);
        }
    }

    static final class Definition {
        final String taskKey;
        final String objectiveKey;
        final List<String> informationLines;
        final String sourceAuthority;

        Definition(
            String taskKey,
            String objectiveKey,
            Collection<String> informationLines,
            String sourceAuthority
        ){
            this.taskKey=
                ObjectiveDefinition
                    .normalizeKey(
                        taskKey
                    );
            this.objectiveKey=
                ObjectiveDefinition
                    .normalizeKey(
                        objectiveKey
                    );

            Objects.requireNonNull(
                informationLines,
                "informationLines"
            );

            if(informationLines.size()>
                    MAX_INFO_LINES)
                throw new IllegalArgumentException(
                    "Task Scroll information lines="+
                    informationLines.size()+
                    " max="+MAX_INFO_LINES
                );

            ArrayList<String> lines=
                new ArrayList<>();

            for(String line:
                    informationLines){
                if(line==null)
                    throw new NullPointerException(
                        "information line"
                    );

                String normalized=
                    line.trim();

                if(normalized.isEmpty())
                    throw new IllegalArgumentException(
                        "blank information line"
                    );

                lines.add(normalized);
            }

            this.informationLines=
                Collections.unmodifiableList(
                    lines
                );

            this.sourceAuthority=
                MatchRules.requireText(
                    sourceAuthority,
                    "sourceAuthority"
                );
        }
    }

    static final class Snapshot {
        final TaskScrollId taskScrollId;
        final String playerRef;
        final Definition definition;
        final ObjectiveProgressService.Snapshot
            objective;
        final State state;
        final boolean tracked;
        final long assignedTick;
        final long lastObservedTick;
        final String presentationAuthority;

        Snapshot(
            Assignment assignment,
            ObjectiveProgressService.Snapshot
                objective
        ){
            this.taskScrollId=
                assignment.id;
            this.playerRef=
                assignment.playerRef;
            this.definition=
                assignment.definition;
            this.objective=objective;
            this.tracked=
                assignment.tracked;
            this.assignedTick=
                assignment.assignedTick;
            this.lastObservedTick=
                assignment.lastObservedTick;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;

            if(assignment.cancelled)
                this.state=State.CANCELLED;
            else if(objective.claimed)
                this.state=State.CLAIMED;
            else if(objective.complete)
                this.state=
                    State.COMPLETE_UNCLAIMED;
            else
                this.state=State.ACTIVE;
        }

        boolean terminal(){
            return state==
                    State.CLAIMED||
                state==
                    State.CANCELLED;
        }

        boolean claimable(){
            return state==
                State.COMPLETE_UNCLAIMED;
        }
    }

    static final class ProgressResult {
        final boolean completedNow;
        final Snapshot task;

        ProgressResult(
            boolean completedNow,
            Snapshot task
        ){
            this.completedNow=
                completedNow;
            this.task=task;
        }
    }

    static final class ClaimResult {
        final boolean changed;
        final Snapshot task;

        ClaimResult(
            boolean changed,
            Snapshot task
        ){
            this.changed=changed;
            this.task=task;
        }
    }

    private static final class Assignment {
        final TaskScrollId id;
        final String playerRef;
        final Definition definition;
        final long assignedTick;

        boolean tracked;
        boolean cancelled;
        boolean externalOperationInFlight;
        long lastObservedTick;

        Assignment(
            TaskScrollId id,
            String playerRef,
            Definition definition,
            long worldTick
        ){
            this.id=id;
            this.playerRef=playerRef;
            this.definition=definition;
            this.assignedTick=worldTick;
            this.lastObservedTick=worldTick;
        }

        Assignment snapshotCopy(){
            Assignment copy=
                new Assignment(
                    id,
                    playerRef,
                    definition,
                    assignedTick
                );
            copy.tracked=tracked;
            copy.cancelled=cancelled;
            copy.lastObservedTick=
                lastObservedTick;
            return copy;
        }
    }

    private final LedgerResolver ledgers;
    private final AtomicLong sequence=
        new AtomicLong();

    private final LinkedHashMap<String,Definition>
        definitions=
            new LinkedHashMap<>();

    private final HashMap<String,String>
        taskByObjective=
            new HashMap<>();

    private final LinkedHashMap<TaskScrollId,Assignment>
        assignments=
            new LinkedHashMap<>();

    private final HashMap<String,TaskScrollId>
        activeByPlayer=
            new HashMap<>();

    TaskScrollService(
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
                "duplicate Task Scroll key "+
                checked.taskKey
            );

        String prior=
            taskByObjective.putIfAbsent(
                checked.objectiveKey,
                checked.taskKey
            );

        if(prior!=null)
            throw new IllegalStateException(
                "Task Scroll objective "+
                checked.objectiveKey+
                " already bound to "+
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
        requireWorldTick(worldTick);

        final Definition definition;

        synchronized(this){
            if(activeByPlayer.containsKey(
                    player))
                throw new IllegalStateException(
                    "player already has Task Scroll "+
                    player
                );

            definition=
                definitions.get(
                    ObjectiveDefinition
                        .normalizeKey(
                            taskKey
                        )
                );

            if(definition==null)
                throw new IllegalArgumentException(
                    "unknown Task Scroll "+
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
                "cannot assign completed Task Scroll objective "+
                definition.objectiveKey+
                " player="+player
            );

        TaskScrollId id=nextId();

        Assignment assignment=
            new Assignment(
                id,
                player,
                definition,
                worldTick
            );

        synchronized(this){
            if(activeByPlayer.containsKey(
                    player))
                throw new IllegalStateException(
                    "player already has Task Scroll "+
                    player
                );

            if(definitions.get(
                    definition.taskKey)!=
                    definition)
                throw new IllegalStateException(
                    "Task Scroll definition changed "+
                    definition.taskKey
                );

            assignments.put(
                id,
                assignment
            );
            activeByPlayer.put(
                player,
                id
            );
        }

        return snapshotOf(
            assignment.snapshotCopy(),
            objective
        );
    }

    /**
     * Reconstructs one already-authoritative active assignment after external
     * persistence restored its semantic objective state.
     *
     * Unlike normal assign(), a complete-but-unclaimed objective is permitted.
     * Claimed state remains terminal and cannot be resurrected as active.
     */
    Snapshot restoreActiveAssignment(
        String playerRef,
        String taskKey,
        long worldTick
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        requireWorldTick(worldTick);

        final Definition definition;

        synchronized(this){
            if(activeByPlayer.containsKey(
                    player))
                throw new IllegalStateException(
                    "player already has Task Scroll "+
                    player
                );

            definition=
                definitions.get(
                    ObjectiveDefinition
                        .normalizeKey(
                            taskKey
                        )
                );

            if(definition==null)
                throw new IllegalArgumentException(
                    "unknown Task Scroll "+
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

        if(objective.claimed)
            throw new IllegalStateException(
                "cannot restore claimed Task Scroll objective "+
                definition.objectiveKey+
                " player="+player
            );

        TaskScrollId id=
            nextId();

        Assignment assignment=
            new Assignment(
                id,
                player,
                definition,
                worldTick
            );

        synchronized(this){
            if(activeByPlayer.containsKey(
                    player))
                throw new IllegalStateException(
                    "player already has Task Scroll "+
                    player
                );

            if(definitions.get(
                    definition.taskKey)!=
                    definition)
                throw new IllegalStateException(
                    "Task Scroll definition changed "+
                    definition.taskKey
                );

            assignments.put(
                id,
                assignment
            );
            activeByPlayer.put(
                player,
                id
            );
        }

        return snapshotOf(
            assignment.snapshotCopy(),
            objective
        );
    }

    ProgressResult recordValidatedProgress(
        String playerRef,
        long amount,
        long worldTick
    ){
        if(amount<=0)
            throw new IllegalArgumentException(
                "amount="+amount
            );

        String player=
            normalizePlayer(
                playerRef
            );
        requireWorldTick(worldTick);

        final Assignment live;
        final Assignment captured;

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

        ObjectiveProgressService.ProgressResult
            result=null;
        RuntimeException failure=null;

        try{
            ObjectiveProgressService ledger=
                requireLedger(player);

            ObjectiveProgressService.Snapshot before=
                requireObjective(
                    captured.definition,
                    ledger
                );

            if(before.claimed)
                throw new IllegalStateException(
                    "active Task Scroll objective already claimed "+
                    captured.definition
                        .objectiveKey
                );

            result=
                ledger.advance(
                    captured.definition
                        .objectiveKey,
                    amount
                );

            validateObjective(
                captured.definition,
                result.after
            );
        }catch(RuntimeException error){
            failure=error;
        }

        final Assignment afterLocal;

        synchronized(this){
            Assignment current=
                requireAssignment(
                    captured.id
                );

            if(current!=live)
                throw new IllegalStateException(
                    "Task Scroll assignment identity changed "+
                    captured.id
                );

            if(failure==null)
                current.lastObservedTick=
                    worldTick;

            current.externalOperationInFlight=
                false;
            afterLocal=
                current.snapshotCopy();
        }

        if(failure!=null)
            throw failure;

        return new ProgressResult(
            result.completedNow,
            snapshotOf(
                afterLocal,
                result.after
            )
        );
    }

    Snapshot refresh(
        TaskScrollId taskScrollId,
        long worldTick
    ){
        requireWorldTick(worldTick);

        final Assignment live;
        final Assignment captured;

        synchronized(this){
            live=
                requireAssignment(
                    taskScrollId
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

        final Assignment afterLocal;

        synchronized(this){
            Assignment current=
                requireAssignment(
                    captured.id
                );

            if(current!=live)
                throw new IllegalStateException(
                    "Task Scroll assignment identity changed "+
                    captured.id
                );

            if(failure==null)
                current.lastObservedTick=
                    worldTick;

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

    Snapshot setTracked(
        TaskScrollId taskScrollId,
        boolean tracked,
        long worldTick
    ){
        requireWorldTick(worldTick);

        final Assignment live;
        final Assignment captured;

        synchronized(this){
            live=
                requireAssignment(
                    taskScrollId
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

            Snapshot before=
                snapshotOf(
                    captured,
                    objective
                );

            if(before.terminal())
                throw new IllegalStateException(
                    "Task Scroll terminal "+
                    captured.id+
                    " state="+before.state
                );
        }catch(RuntimeException error){
            failure=error;
        }

        final Assignment afterLocal;

        synchronized(this){
            Assignment current=
                requireAssignment(
                    captured.id
                );

            if(current!=live)
                throw new IllegalStateException(
                    "Task Scroll assignment identity changed "+
                    captured.id
                );

            if(failure==null){
                current.tracked=tracked;
                current.lastObservedTick=
                    worldTick;
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

    ClaimResult confirmRewardSettledAndMarkClaimed(
        TaskScrollId taskScrollId,
        long worldTick
    ){
        requireWorldTick(worldTick);

        final Assignment live;
        final Assignment captured;

        synchronized(this){
            live=
                requireAssignment(
                    taskScrollId
                );
            reserveExternalOperation(
                live,
                worldTick
            );
            captured=
                live.snapshotCopy();
        }

        ObjectiveProgressService.Snapshot objective=null;
        boolean changed=false;
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

            Snapshot before=
                snapshotOf(
                    captured,
                    objective
                );

            if(before.state==
                    State.CANCELLED)
                throw new IllegalStateException(
                    "cancelled Task Scroll cannot claim "+
                    captured.id
                );

            if(before.state!=State.CLAIMED){
                if(!before.objective.complete)
                    throw new IllegalStateException(
                        "Task Scroll objective incomplete "+
                        captured.definition
                            .objectiveKey
                    );

                changed=
                    ledger.markClaimed(
                        captured.definition
                            .objectiveKey
                    );

                objective=
                    requireObjective(
                        captured.definition,
                        ledger
                    );
            }
        }catch(RuntimeException error){
            failure=error;
        }

        final Assignment afterLocal;

        synchronized(this){
            Assignment current=
                requireAssignment(
                    captured.id
                );

            if(current!=live)
                throw new IllegalStateException(
                    "Task Scroll assignment identity changed "+
                    captured.id
                );

            if(failure==null){
                current.lastObservedTick=
                    worldTick;

                if(objective!=null&&
                   objective.claimed)
                    activeByPlayer.remove(
                        current.playerRef,
                        current.id
                    );
            }

            current.externalOperationInFlight=
                false;
            afterLocal=
                current.snapshotCopy();
        }

        if(failure!=null)
            throw failure;

        return new ClaimResult(
            changed,
            snapshotOf(
                afterLocal,
                objective
            )
        );
    }

    Snapshot cancel(
        TaskScrollId taskScrollId,
        long worldTick
    ){
        requireWorldTick(worldTick);

        final Assignment live;
        final Assignment captured;

        synchronized(this){
            live=
                requireAssignment(
                    taskScrollId
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

            if(objective.claimed)
                throw new IllegalStateException(
                    "claimed Task Scroll cannot cancel "+
                    captured.id
                );
        }catch(RuntimeException error){
            failure=error;
        }

        final Assignment afterLocal;

        synchronized(this){
            Assignment current=
                requireAssignment(
                    captured.id
                );

            if(current!=live)
                throw new IllegalStateException(
                    "Task Scroll assignment identity changed "+
                    captured.id
                );

            if(failure==null){
                if(!current.cancelled){
                    current.cancelled=true;
                    activeByPlayer.remove(
                        current.playerRef,
                        current.id
                    );
                }

                current.lastObservedTick=
                    worldTick;
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

    Snapshot get(
        TaskScrollId taskScrollId
    ){
        final Assignment captured;

        synchronized(this){
            Assignment assignment=
                assignments.get(
                    Objects.requireNonNull(
                        taskScrollId,
                        "taskScrollId"
                    )
                );

            if(assignment==null)
                return null;

            captured=
                assignment.snapshotCopy();
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

        final Assignment captured;

        synchronized(this){
            TaskScrollId id=
                activeByPlayer.get(
                    player
                );

            if(id==null)
                return null;

            captured=
                requireAssignment(id)
                    .snapshotCopy();
        }

        return resolveSnapshot(
            captured
        );
    }

    List<Snapshot> snapshot(){
        final ArrayList<Assignment>
            captured=
                new ArrayList<>();

        synchronized(this){
            for(Assignment assignment:
                    assignments.values())
                captured.add(
                    assignment.snapshotCopy()
                );
        }

        captured.sort(
            Comparator.comparing(
                value->value.id
            )
        );

        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Assignment assignment:
                captured)
            out.add(
                resolveSnapshot(
                    assignment
                )
            );

        return Collections.unmodifiableList(
            out
        );
    }

    synchronized int definitionCount(){
        return definitions.size();
    }

    synchronized int assignmentCount(){
        return assignments.size();
    }

    private Snapshot resolveSnapshot(
        Assignment assignment
    ){
        ObjectiveProgressService ledger=
            requireLedger(
                assignment.playerRef
            );

        return snapshotOf(
            assignment,
            requireObjective(
                assignment.definition,
                ledger
            )
        );
    }

    private static Snapshot snapshotOf(
        Assignment assignment,
        ObjectiveProgressService.Snapshot
            objective
    ){
        validateObjective(
            assignment.definition,
            objective
        );

        return new Snapshot(
            assignment,
            objective
        );
    }

    private static void validateObjective(
        Definition definition,
        ObjectiveProgressService.Snapshot
            objective
    ){
        if(objective==null)
            throw new IllegalStateException(
                "Task Scroll objective disappeared "+
                definition.objectiveKey
            );

        if(!definition.sourceAuthority.equals(
                objective.sourceAuthority))
            throw new IllegalStateException(
                "Task Scroll objective authority drift "+
                definition.objectiveKey
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

        validateObjective(
            definition,
            objective
        );

        return objective;
    }

    private Assignment requireActiveLocal(
        String player
    ){
        TaskScrollId id=
            activeByPlayer.get(
                player
            );

        if(id==null)
            throw new IllegalStateException(
                "player has no active Task Scroll "+
                player
            );

        Assignment assignment=
            requireAssignment(id);

        if(assignment.cancelled)
            throw new IllegalStateException(
                "active Task Scroll index points to CANCELLED"
            );

        return assignment;
    }

    private Assignment requireAssignment(
        TaskScrollId id
    ){
        TaskScrollId checked=
            Objects.requireNonNull(
                id,
                "taskScrollId"
            );

        Assignment assignment=
            assignments.get(
                checked
            );

        if(assignment==null)
            throw new IllegalArgumentException(
                "unknown Task Scroll "+
                checked
            );

        return assignment;
    }

    private static void reserveExternalOperation(
        Assignment assignment,
        long worldTick
    ){
        requireForwardTick(
            assignment,
            worldTick
        );

        if(assignment.externalOperationInFlight)
            throw new IllegalStateException(
                "Task Scroll external operation already in flight "+
                assignment.id
            );

        assignment.externalOperationInFlight=
            true;
    }

    private ObjectiveProgressService requireLedger(
        String player
    ){
        ObjectiveProgressService ledger=
            ledgers.resolve(player);

        if(ledger==null)
            throw new IllegalStateException(
                "missing Task Scroll ledger player="+
                player
            );

        return ledger;
    }

    private TaskScrollId nextId(){
        long value=
            sequence.incrementAndGet();

        if(value<=0)
            throw new IllegalStateException(
                "Task Scroll id sequence exhausted"
            );

        return new TaskScrollId(value);
    }

    private static void requireForwardTick(
        Assignment assignment,
        long worldTick
    ){
        if(worldTick<
                assignment.lastObservedTick)
            throw new IllegalArgumentException(
                "Task Scroll tick moved backwards "+
                worldTick+
                " < "+
                assignment.lastObservedTick
            );
    }

    private static void requireWorldTick(
        long worldTick
    ){
        if(worldTick<0)
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
}
