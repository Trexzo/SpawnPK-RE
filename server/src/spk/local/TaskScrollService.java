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

    synchronized Snapshot assign(
        String playerRef,
        String taskKey,
        long worldTick
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        requireWorldTick(worldTick);

        if(activeByPlayer.containsKey(
                player))
            throw new IllegalStateException(
                "player already has Task Scroll "+
                player
            );

        Definition definition=
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

        ObjectiveProgressService ledger=
            requireLedger(player);

        ObjectiveProgressService.Snapshot
            objective=
                ledger.get(
                    definition.objectiveKey
                );

        if(objective==null)
            throw new IllegalArgumentException(
                "missing Task Scroll objective "+
                definition.objectiveKey+
                " player="+player
            );

        if(!definition.sourceAuthority.equals(
                objective.sourceAuthority))
            throw new IllegalArgumentException(
                "Task Scroll objective authority mismatch "+
                definition.objectiveKey+
                " player="+player
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

        assignments.put(
            id,
            assignment
        );
        activeByPlayer.put(
            player,
            id
        );

        return snapshotOf(
            assignment,
            ledger
        );
    }

    synchronized ProgressResult
        recordValidatedProgress(
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

        Assignment assignment=
            requireActive(
                player
            );

        requireForwardTick(
            assignment,
            worldTick
        );

        ObjectiveProgressService ledger=
            requireLedger(player);

        ObjectiveProgressService.ProgressResult
            result=
                ledger.advance(
                    assignment.definition
                        .objectiveKey,
                    amount
                );

        assignment.lastObservedTick=
            worldTick;

        return new ProgressResult(
            result.completedNow,
            snapshotOf(
                assignment,
                ledger
            )
        );
    }

    synchronized Snapshot refresh(
        TaskScrollId taskScrollId,
        long worldTick
    ){
        requireWorldTick(worldTick);

        Assignment assignment=
            requireAssignment(
                taskScrollId
            );

        requireForwardTick(
            assignment,
            worldTick
        );

        assignment.lastObservedTick=
            worldTick;

        return snapshotOf(
            assignment,
            requireLedger(
                assignment.playerRef
            )
        );
    }

    synchronized Snapshot setTracked(
        TaskScrollId taskScrollId,
        boolean tracked,
        long worldTick
    ){
        requireWorldTick(worldTick);

        Assignment assignment=
            requireAssignment(
                taskScrollId
            );

        Snapshot before=
            snapshotOf(
                assignment,
                requireLedger(
                    assignment.playerRef
                )
            );

        if(before.terminal())
            throw new IllegalStateException(
                "Task Scroll terminal "+
                assignment.id+
                " state="+before.state
            );

        requireForwardTick(
            assignment,
            worldTick
        );

        assignment.tracked=tracked;
        assignment.lastObservedTick=
            worldTick;

        return snapshotOf(
            assignment,
            requireLedger(
                assignment.playerRef
            )
        );
    }

    synchronized ClaimResult
        confirmRewardSettledAndMarkClaimed(
            TaskScrollId taskScrollId,
            long worldTick
        ){
        requireWorldTick(worldTick);

        Assignment assignment=
            requireAssignment(
                taskScrollId
            );

        requireForwardTick(
            assignment,
            worldTick
        );

        ObjectiveProgressService ledger=
            requireLedger(
                assignment.playerRef
            );

        Snapshot before=
            snapshotOf(
                assignment,
                ledger
            );

        if(before.state==
                State.CANCELLED)
            throw new IllegalStateException(
                "cancelled Task Scroll cannot claim "+
                assignment.id
            );

        if(before.state==State.CLAIMED){
            assignment.lastObservedTick=
                worldTick;

            return new ClaimResult(
                false,
                snapshotOf(
                    assignment,
                    ledger
                )
            );
        }

        if(!before.objective.complete)
            throw new IllegalStateException(
                "Task Scroll objective incomplete "+
                assignment.definition
                    .objectiveKey
            );

        boolean changed=
            ledger.markClaimed(
                assignment.definition
                    .objectiveKey
            );

        assignment.lastObservedTick=
            worldTick;

        if(changed)
            activeByPlayer.remove(
                assignment.playerRef,
                assignment.id
            );

        return new ClaimResult(
            changed,
            snapshotOf(
                assignment,
                ledger
            )
        );
    }

    synchronized Snapshot cancel(
        TaskScrollId taskScrollId,
        long worldTick
    ){
        requireWorldTick(worldTick);

        Assignment assignment=
            requireAssignment(
                taskScrollId
            );

        requireForwardTick(
            assignment,
            worldTick
        );

        Snapshot before=
            snapshotOf(
                assignment,
                requireLedger(
                    assignment.playerRef
                )
            );

        if(before.state==State.CLAIMED)
            throw new IllegalStateException(
                "claimed Task Scroll cannot cancel "+
                assignment.id
            );

        if(!assignment.cancelled){
            assignment.cancelled=true;
            activeByPlayer.remove(
                assignment.playerRef,
                assignment.id
            );
        }

        assignment.lastObservedTick=
            worldTick;

        return snapshotOf(
            assignment,
            requireLedger(
                assignment.playerRef
            )
        );
    }

    synchronized Snapshot get(
        TaskScrollId taskScrollId
    ){
        Assignment assignment=
            assignments.get(
                Objects.requireNonNull(
                    taskScrollId,
                    "taskScrollId"
                )
            );

        return assignment==null
            ?null
            :snapshotOf(
                assignment,
                requireLedger(
                    assignment.playerRef
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

        TaskScrollId id=
            activeByPlayer.get(
                player
            );

        return id==null
            ?null
            :get(id);
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Assignment> ordered=
            new ArrayList<>(
                assignments.values()
            );

        ordered.sort(
            Comparator.comparing(
                value->value.id
            )
        );

        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Assignment assignment:
                ordered)
            out.add(
                snapshotOf(
                    assignment,
                    requireLedger(
                        assignment.playerRef
                    )
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

    private Snapshot snapshotOf(
        Assignment assignment,
        ObjectiveProgressService ledger
    ){
        ObjectiveProgressService.Snapshot
            objective=
                ledger.get(
                    assignment.definition
                        .objectiveKey
                );

        if(objective==null)
            throw new IllegalStateException(
                "Task Scroll objective disappeared "+
                assignment.definition
                    .objectiveKey
            );

        if(!assignment.definition
                .sourceAuthority
                .equals(
                    objective.sourceAuthority))
            throw new IllegalStateException(
                "Task Scroll objective authority drift "+
                assignment.definition
                    .objectiveKey
            );

        return new Snapshot(
            assignment,
            objective
        );
    }

    private Assignment requireActive(
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

        Snapshot snapshot=
            snapshotOf(
                assignment,
                requireLedger(player)
            );

        if(snapshot.terminal())
            throw new IllegalStateException(
                "active Task Scroll index points to "+
                snapshot.state
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
