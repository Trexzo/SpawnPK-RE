package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Protocol-independent Bounty Hunter target/task lifecycle.
 *
 * Matching, objective progress, rewards and teleport behavior remain external.
 */
final class BountyHunterService {
    enum AssignmentState {
        ASSIGNED,
        ACTIVE,
        COMPLETED,
        EXPIRED,
        CANCELLED,
        TARGET_UNAVAILABLE
    }

    enum TaskState {
        ACTIVE,
        COMPLETED,
        SKIPPED,
        CANCELLED
    }

    static final class PlayerId {
        private final String value;

        PlayerId(String value) {
            this.value = requireId(value, "player id");
        }

        String value() { return value; }

        @Override public boolean equals(Object other) {
            return other instanceof PlayerId && value.equals(((PlayerId) other).value);
        }

        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    static final class AssignmentId {
        private final String value;

        AssignmentId(String value) {
            this.value = requireId(value, "assignment id");
        }

        String value() { return value; }

        @Override public boolean equals(Object other) {
            return other instanceof AssignmentId && value.equals(((AssignmentId) other).value);
        }

        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    static final class TaskId {
        private final String value;

        TaskId(String value) {
            this.value = requireId(value, "task id");
        }

        String value() { return value; }

        @Override public boolean equals(Object other) {
            return other instanceof TaskId && value.equals(((TaskId) other).value);
        }

        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    static final class ObjectiveReference {
        private final String value;

        ObjectiveReference(String value) {
            this.value = requireId(value, "objective reference");
        }

        String value() { return value; }

        @Override public boolean equals(Object other) {
            return other instanceof ObjectiveReference && value.equals(((ObjectiveReference) other).value);
        }

        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    static final class AssignmentSnapshot {
        private final AssignmentId id;
        private final PlayerId hunter;
        private final PlayerId target;
        private final long assignedTick;
        private final Long deadlineTick;
        private final AssignmentState state;
        private final long lastTransitionTick;

        private AssignmentSnapshot(
            AssignmentId id,
            PlayerId hunter,
            PlayerId target,
            long assignedTick,
            Long deadlineTick,
            AssignmentState state,
            long lastTransitionTick
        ) {
            this.id = id;
            this.hunter = hunter;
            this.target = target;
            this.assignedTick = assignedTick;
            this.deadlineTick = deadlineTick;
            this.state = state;
            this.lastTransitionTick = lastTransitionTick;
        }

        AssignmentId id() { return id; }
        PlayerId hunter() { return hunter; }
        PlayerId target() { return target; }
        long assignedTick() { return assignedTick; }
        OptionalLong deadlineTick() {
            return deadlineTick == null ? OptionalLong.empty() : OptionalLong.of(deadlineTick.longValue());
        }
        AssignmentState state() { return state; }
        long lastTransitionTick() { return lastTransitionTick; }
    }

    static final class TaskSnapshot {
        private final TaskId id;
        private final PlayerId hunter;
        private final ObjectiveReference objective;
        private final TaskState state;
        private final long assignedTick;
        private final long lastTransitionTick;

        private TaskSnapshot(
            TaskId id,
            PlayerId hunter,
            ObjectiveReference objective,
            TaskState state,
            long assignedTick,
            long lastTransitionTick
        ) {
            this.id = id;
            this.hunter = hunter;
            this.objective = objective;
            this.state = state;
            this.assignedTick = assignedTick;
            this.lastTransitionTick = lastTransitionTick;
        }

        TaskId id() { return id; }
        PlayerId hunter() { return hunter; }
        ObjectiveReference objective() { return objective; }
        TaskState state() { return state; }
        long assignedTick() { return assignedTick; }
        long lastTransitionTick() { return lastTransitionTick; }
    }

    static final class StatSnapshot {
        private final PlayerId player;
        private final long completedTargets;
        private final long expiredTargets;
        private final long cancelledTargets;
        private final long unavailableTargets;
        private final long skippedTasks;
        private final Long authoritativeStreak;
        private final BountyEvidenceAuthority streakAuthority;

        private StatSnapshot(
            PlayerId player,
            long completedTargets,
            long expiredTargets,
            long cancelledTargets,
            long unavailableTargets,
            long skippedTasks,
            Long authoritativeStreak,
            BountyEvidenceAuthority streakAuthority
        ) {
            this.player = player;
            this.completedTargets = completedTargets;
            this.expiredTargets = expiredTargets;
            this.cancelledTargets = cancelledTargets;
            this.unavailableTargets = unavailableTargets;
            this.skippedTasks = skippedTasks;
            this.authoritativeStreak = authoritativeStreak;
            this.streakAuthority = streakAuthority;
        }

        PlayerId player() { return player; }
        long completedTargets() { return completedTargets; }
        long expiredTargets() { return expiredTargets; }
        long cancelledTargets() { return cancelledTargets; }
        long unavailableTargets() { return unavailableTargets; }
        long skippedTasks() { return skippedTasks; }
        OptionalLong authoritativeStreak() {
            return authoritativeStreak == null
                ? OptionalLong.empty()
                : OptionalLong.of(authoritativeStreak.longValue());
        }
        BountyEvidenceAuthority streakAuthority() { return streakAuthority; }
    }

    private static final class Assignment {
        private final AssignmentId id;
        private final PlayerId hunter;
        private final PlayerId target;
        private final long assignedTick;
        private final Long deadlineTick;
        private AssignmentState state = AssignmentState.ASSIGNED;
        private long lastTransitionTick;

        private Assignment(
            AssignmentId id,
            PlayerId hunter,
            PlayerId target,
            long assignedTick,
            Long deadlineTick
        ) {
            this.id = id;
            this.hunter = hunter;
            this.target = target;
            this.assignedTick = assignedTick;
            this.deadlineTick = deadlineTick;
            this.lastTransitionTick = assignedTick;
        }
    }

    private static final class Task {
        private final TaskId id;
        private final PlayerId hunter;
        private final ObjectiveReference objective;
        private final long assignedTick;
        private TaskState state = TaskState.ACTIVE;
        private long lastTransitionTick;

        private Task(
            TaskId id,
            PlayerId hunter,
            ObjectiveReference objective,
            long assignedTick
        ) {
            this.id = id;
            this.hunter = hunter;
            this.objective = objective;
            this.assignedTick = assignedTick;
            this.lastTransitionTick = assignedTick;
        }
    }

    private static final class Stats {
        private long completedTargets;
        private long expiredTargets;
        private long cancelledTargets;
        private long unavailableTargets;
        private long skippedTasks;
        private Long authoritativeStreak;
        private BountyEvidenceAuthority streakAuthority = BountyEvidenceAuthority.UNKNOWN_SERVER_AUTHORITY;
    }

    private final BountyObjectivePort objectivePort;
    private final Map<AssignmentId, Assignment> assignments = new LinkedHashMap<AssignmentId, Assignment>();
    private final Map<PlayerId, AssignmentId> openAssignmentByParticipant =
        new LinkedHashMap<PlayerId, AssignmentId>();
    private final Map<TaskId, Task> tasks = new LinkedHashMap<TaskId, Task>();
    private final Map<PlayerId, TaskId> activeTaskByHunter = new LinkedHashMap<PlayerId, TaskId>();
    private final Map<PlayerId, Stats> stats = new LinkedHashMap<PlayerId, Stats>();

    BountyHunterService(BountyObjectivePort objectivePort) {
        this.objectivePort = Objects.requireNonNull(objectivePort, "objectivePort");
    }

    AssignmentSnapshot assign(
        AssignmentId id,
        PlayerId hunter,
        PlayerId target,
        long assignedTick,
        Long deadlineTick
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(hunter, "hunter");
        Objects.requireNonNull(target, "target");
        requireTick(assignedTick);
        if (hunter.equals(target)) {
            throw new IllegalArgumentException("hunter and target must differ");
        }
        if (deadlineTick != null && deadlineTick.longValue() <= assignedTick) {
            throw new IllegalArgumentException("deadlineTick must be after assignedTick");
        }
        if (assignments.containsKey(id)) {
            throw new IllegalStateException("Assignment id already exists: " + id);
        }
        requireParticipantAvailable(hunter);
        requireParticipantAvailable(target);

        Assignment assignment = new Assignment(id, hunter, target, assignedTick, deadlineTick);
        assignments.put(id, assignment);
        openAssignmentByParticipant.put(hunter, id);
        openAssignmentByParticipant.put(target, id);
        return snapshot(id);
    }

    AssignmentSnapshot assignWithPolicy(
        AssignmentId id,
        PlayerId hunter,
        List<PlayerId> candidates,
        BountyTargetSelectionPolicy policy,
        long assignedTick,
        Long deadlineTick
    ) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(hunter, "hunter");

        ArrayList<PlayerId> checkedCandidates =
            new ArrayList<PlayerId>(candidates.size());
        java.util.LinkedHashSet<PlayerId> candidateSet =
            new java.util.LinkedHashSet<PlayerId>();

        for (PlayerId candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("Candidate list contains null player");
            }
            if (!candidateSet.add(candidate)) {
                throw new IllegalArgumentException("Candidate list contains duplicate player: " + candidate);
            }
            checkedCandidates.add(candidate);
        }

        List<PlayerId> safeCandidates =
            Collections.unmodifiableList(checkedCandidates);
        Optional<PlayerId> selected = Objects.requireNonNull(
            policy.select(hunter, safeCandidates),
            "policy result"
        );
        if (!selected.isPresent()) {
            throw new IllegalStateException("External target policy selected no target");
        }
        if (!candidateSet.contains(selected.get())) {
            throw new IllegalStateException(
                "External target policy selected player outside candidate set: " + selected.get()
            );
        }
        return assign(id, hunter, selected.get(), assignedTick, deadlineTick);
    }

    AssignmentSnapshot activate(AssignmentId id, long tick) {
        Assignment assignment = requireAssignment(id);
        requireForwardTick(assignment, tick);
        if (assignment.state == AssignmentState.ACTIVE) return snapshot(id);
        if (assignment.state != AssignmentState.ASSIGNED) {
            throw new IllegalStateException("Cannot activate assignment in state " + assignment.state);
        }
        transition(assignment, AssignmentState.ACTIVE, tick);
        return snapshot(id);
    }

    AssignmentSnapshot complete(AssignmentId id, long tick) {
        Assignment assignment = requireAssignment(id);
        requireForwardTick(assignment, tick);
        if (assignment.state == AssignmentState.COMPLETED) return snapshot(id);
        if (assignment.state != AssignmentState.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE assignments can complete");
        }
        transitionTerminal(assignment, AssignmentState.COMPLETED, tick);
        stats(assignment.hunter).completedTargets++;
        return snapshot(id);
    }

    AssignmentSnapshot cancel(AssignmentId id, long tick) {
        Assignment assignment = requireAssignment(id);
        requireForwardTick(assignment, tick);
        if (assignment.state == AssignmentState.CANCELLED) return snapshot(id);
        requireOpen(assignment);
        transitionTerminal(assignment, AssignmentState.CANCELLED, tick);
        stats(assignment.hunter).cancelledTargets++;
        return snapshot(id);
    }

    AssignmentSnapshot targetUnavailable(AssignmentId id, long tick) {
        Assignment assignment = requireAssignment(id);
        requireForwardTick(assignment, tick);
        if (assignment.state == AssignmentState.TARGET_UNAVAILABLE) return snapshot(id);
        requireOpen(assignment);
        transitionTerminal(assignment, AssignmentState.TARGET_UNAVAILABLE, tick);
        stats(assignment.hunter).unavailableTargets++;
        return snapshot(id);
    }

    AssignmentSnapshot expire(AssignmentId id, long tick) {
        Assignment assignment = requireAssignment(id);
        requireForwardTick(assignment, tick);
        if (assignment.state == AssignmentState.EXPIRED) return snapshot(id);
        requireOpen(assignment);
        if (assignment.deadlineTick == null) {
            throw new IllegalStateException("Assignment has no semantic deadline");
        }
        if (tick < assignment.deadlineTick.longValue()) {
            throw new IllegalStateException("Assignment deadline has not been reached");
        }
        transitionTerminal(assignment, AssignmentState.EXPIRED, tick);
        stats(assignment.hunter).expiredTargets++;
        return snapshot(id);
    }

    AssignmentSnapshot snapshot(AssignmentId id) {
        Assignment a = requireAssignment(id);
        return new AssignmentSnapshot(
            a.id,
            a.hunter,
            a.target,
            a.assignedTick,
            a.deadlineTick,
            a.state,
            a.lastTransitionTick
        );
    }

    TaskSnapshot assignTask(
        TaskId taskId,
        PlayerId hunter,
        ObjectiveReference objective,
        long tick
    ) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(hunter, "hunter");
        Objects.requireNonNull(objective, "objective");
        requireTick(tick);
        if (tasks.containsKey(taskId)) {
            throw new IllegalStateException("Task id already exists: " + taskId);
        }
        if (activeTaskByHunter.containsKey(hunter)) {
            throw new IllegalStateException("Hunter already has an active Bounty task");
        }
        if (!objectivePort.exists(objective)) {
            throw new IllegalArgumentException("Objective reference does not exist: " + objective);
        }

        Task task = new Task(taskId, hunter, objective, tick);
        tasks.put(taskId, task);
        activeTaskByHunter.put(hunter, taskId);
        return taskSnapshot(taskId);
    }

    TaskSnapshot refreshTask(TaskId taskId, long tick) {
        Task task = requireTask(taskId);
        requireTaskTick(task, tick);
        if (task.state != TaskState.ACTIVE) return taskSnapshot(taskId);
        if (objectivePort.isComplete(task.objective)) {
            task.state = TaskState.COMPLETED;
            task.lastTransitionTick = tick;
            activeTaskByHunter.remove(task.hunter);
        }
        return taskSnapshot(taskId);
    }

    TaskSnapshot skipTask(TaskId taskId, long tick) {
        Task task = requireTask(taskId);
        requireTaskTick(task, tick);
        if (task.state == TaskState.SKIPPED) return taskSnapshot(taskId);
        if (task.state != TaskState.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE task can be skipped");
        }
        task.state = TaskState.SKIPPED;
        task.lastTransitionTick = tick;
        activeTaskByHunter.remove(task.hunter);
        stats(task.hunter).skippedTasks++;
        return taskSnapshot(taskId);
    }

    TaskSnapshot cancelTask(TaskId taskId, long tick) {
        Task task = requireTask(taskId);
        requireTaskTick(task, tick);
        if (task.state == TaskState.CANCELLED) return taskSnapshot(taskId);
        if (task.state != TaskState.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE task can be cancelled");
        }
        task.state = TaskState.CANCELLED;
        task.lastTransitionTick = tick;
        activeTaskByHunter.remove(task.hunter);
        return taskSnapshot(taskId);
    }

    TaskSnapshot taskSnapshot(TaskId taskId) {
        Task task = requireTask(taskId);
        return new TaskSnapshot(
            task.id,
            task.hunter,
            task.objective,
            task.state,
            task.assignedTick,
            task.lastTransitionTick
        );
    }

    void updateAuthoritativeStreak(
        PlayerId player,
        long streak,
        BountyEvidenceAuthority authority
    ) {
        Objects.requireNonNull(player, "player");
        if (streak < 0L) throw new IllegalArgumentException("streak must be non-negative");
        Stats s = stats(player);
        s.authoritativeStreak = Long.valueOf(streak);
        s.streakAuthority = Objects.requireNonNull(authority, "authority");
    }

    StatSnapshot statSnapshot(PlayerId player) {
        Objects.requireNonNull(player, "player");
        Stats s = stats.get(player);
        if (s == null) {
            return new StatSnapshot(
                player, 0L, 0L, 0L, 0L, 0L, null,
                BountyEvidenceAuthority.UNKNOWN_SERVER_AUTHORITY
            );
        }
        return new StatSnapshot(
            player,
            s.completedTargets,
            s.expiredTargets,
            s.cancelledTargets,
            s.unavailableTargets,
            s.skippedTasks,
            s.authoritativeStreak,
            s.streakAuthority
        );
    }

    private void requireParticipantAvailable(PlayerId participant) {
        if (openAssignmentByParticipant.containsKey(participant)) {
            throw new IllegalStateException("Participant already has an open target assignment: " + participant);
        }
    }

    private static void requireOpen(Assignment assignment) {
        if (assignment.state != AssignmentState.ASSIGNED && assignment.state != AssignmentState.ACTIVE) {
            throw new IllegalStateException("Assignment is terminal: " + assignment.state);
        }
    }

    private void transition(Assignment assignment, AssignmentState state, long tick) {
        assignment.state = state;
        assignment.lastTransitionTick = tick;
    }

    private void transitionTerminal(Assignment assignment, AssignmentState state, long tick) {
        transition(assignment, state, tick);
        openAssignmentByParticipant.remove(assignment.hunter);
        openAssignmentByParticipant.remove(assignment.target);
    }

    private Assignment requireAssignment(AssignmentId id) {
        Objects.requireNonNull(id, "id");
        Assignment assignment = assignments.get(id);
        if (assignment == null) throw new IllegalArgumentException("Unknown assignment: " + id);
        return assignment;
    }

    private Task requireTask(TaskId id) {
        Objects.requireNonNull(id, "taskId");
        Task task = tasks.get(id);
        if (task == null) throw new IllegalArgumentException("Unknown task: " + id);
        return task;
    }

    private Stats stats(PlayerId player) {
        Stats value = stats.get(player);
        if (value == null) {
            value = new Stats();
            stats.put(player, value);
        }
        return value;
    }

    private static void requireForwardTick(Assignment assignment, long tick) {
        requireTick(tick);
        if (tick < assignment.lastTransitionTick) {
            throw new IllegalArgumentException("tick moved backwards");
        }
    }

    private static void requireTaskTick(Task task, long tick) {
        requireTick(tick);
        if (tick < task.lastTransitionTick) {
            throw new IllegalArgumentException("tick moved backwards");
        }
    }

    private static void requireTick(long tick) {
        if (tick < 0L) throw new IllegalArgumentException("tick must be non-negative");
    }

    private static String requireId(String value, String label) {
        if (value == null) throw new NullPointerException(label);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return normalized;
    }
}
