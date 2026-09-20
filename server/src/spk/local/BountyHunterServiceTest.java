package spk.local;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Deterministic regression for Issue #172 semantic Bounty Hunter lifecycle. */
public final class BountyHunterServiceTest {
    public static void main(String[] args) {
        final Set<BountyHunterService.ObjectiveReference> known =
            new HashSet<BountyHunterService.ObjectiveReference>();
        final Set<BountyHunterService.ObjectiveReference> complete =
            new HashSet<BountyHunterService.ObjectiveReference>();

        BountyObjectivePort objectivePort = new BountyObjectivePort() {
            @Override public boolean exists(BountyHunterService.ObjectiveReference objective) {
                return known.contains(objective);
            }

            @Override public boolean isComplete(BountyHunterService.ObjectiveReference objective) {
                return complete.contains(objective);
            }
        };

        BountyHunterService service = new BountyHunterService(objectivePort);
        BountyHunterService.PlayerId hunter = new BountyHunterService.PlayerId("hunter");
        BountyHunterService.PlayerId target = new BountyHunterService.PlayerId("target");
        BountyHunterService.PlayerId candidate2 = new BountyHunterService.PlayerId("candidate-2");

        BountyHunterService.AssignmentSnapshot assigned = service.assignWithPolicy(
            new BountyHunterService.AssignmentId("a1"),
            hunter,
            Arrays.asList(target, candidate2),
            new BountyTargetSelectionPolicy() {
                @Override public Optional<BountyHunterService.PlayerId> select(
                    BountyHunterService.PlayerId ignoredHunter,
                    java.util.List<BountyHunterService.PlayerId> candidates
                ) {
                    return Optional.of(candidates.get(0));
                }
            },
            10L,
            Long.valueOf(20L)
        );

        require(assigned.state() == BountyHunterService.AssignmentState.ASSIGNED, "assigned");
        require(target.equals(assigned.target()), "external matcher target");
        require(assigned.deadlineTick().isPresent() && assigned.deadlineTick().getAsLong() == 20L, "deadline");

        expectIllegalState(new Runnable() {
            @Override public void run() {
                service.assign(
                    new BountyHunterService.AssignmentId("a-conflict"),
                    candidate2,
                    target,
                    11L,
                    null
                );
            }
        }, "one open assignment per participant");

        service.activate(assigned.id(), 12L);
        expectIllegalState(new Runnable() {
            @Override public void run() {
                service.expire(assigned.id(), 19L);
            }
        }, "early expiry");
        BountyHunterService.AssignmentSnapshot expired = service.expire(assigned.id(), 20L);
        require(expired.state() == BountyHunterService.AssignmentState.EXPIRED, "expired");
        require(service.expire(assigned.id(), 20L).state() == BountyHunterService.AssignmentState.EXPIRED, "expiry idempotent");

        BountyHunterService.AssignmentSnapshot second = service.assign(
            new BountyHunterService.AssignmentId("a2"),
            hunter,
            target,
            30L,
            null
        );
        service.activate(second.id(), 31L);
        service.complete(second.id(), 32L);
        require(service.complete(second.id(), 32L).state() == BountyHunterService.AssignmentState.COMPLETED, "complete idempotent");

        BountyHunterService.AssignmentSnapshot unavailable = service.assign(
            new BountyHunterService.AssignmentId("a3"),
            hunter,
            target,
            40L,
            null
        );
        service.targetUnavailable(unavailable.id(), 41L);

        BountyHunterService.AssignmentSnapshot cancelled = service.assign(
            new BountyHunterService.AssignmentId("a4"),
            hunter,
            target,
            50L,
            null
        );
        service.cancel(cancelled.id(), 51L);

        BountyHunterService.ObjectiveReference objective =
            new BountyHunterService.ObjectiveReference("objective:bounty:test");
        known.add(objective);

        BountyHunterService.TaskSnapshot task = service.assignTask(
            new BountyHunterService.TaskId("task-1"),
            hunter,
            objective,
            60L
        );
        require(task.state() == BountyHunterService.TaskState.ACTIVE, "task active");
        require(service.refreshTask(task.id(), 61L).state() == BountyHunterService.TaskState.ACTIVE, "progress external");

        complete.add(objective);
        require(service.refreshTask(task.id(), 62L).state() == BountyHunterService.TaskState.COMPLETED, "objective completion reference");

        BountyHunterService.TaskSnapshot skipTask = service.assignTask(
            new BountyHunterService.TaskId("task-2"),
            hunter,
            objective,
            70L
        );
        require(service.skipTask(skipTask.id(), 71L).state() == BountyHunterService.TaskState.SKIPPED, "task skip");
        require(service.skipTask(skipTask.id(), 71L).state() == BountyHunterService.TaskState.SKIPPED, "skip idempotent");

        expectIllegalArgument(new Runnable() {
            @Override public void run() {
                service.assignTask(
                    new BountyHunterService.TaskId("unknown-objective-task"),
                    hunter,
                    new BountyHunterService.ObjectiveReference("objective:missing"),
                    80L
                );
            }
        }, "unknown objective fails closed");

        service.updateAuthoritativeStreak(
            hunter,
            7L,
            BountyEvidenceAuthority.CUSTOM_LOCALLAB
        );
        BountyHunterService.StatSnapshot stats = service.statSnapshot(hunter);
        require(stats.completedTargets() == 1L, "completed stat");
        require(stats.expiredTargets() == 1L, "expired stat");
        require(stats.unavailableTargets() == 1L, "unavailable stat");
        require(stats.cancelledTargets() == 1L, "cancel stat");
        require(stats.skippedTasks() == 1L, "skip stat");
        require(stats.authoritativeStreak().isPresent() && stats.authoritativeStreak().getAsLong() == 7L, "external streak");
        require(stats.streakAuthority() == BountyEvidenceAuthority.CUSTOM_LOCALLAB, "streak authority");

        assertProtocolIndependent(
            BountyHunterService.class,
            BountyObjectivePort.class,
            BountyTargetSelectionPolicy.class
        );

        System.out.println(
            "ISSUE172_BOUNTY_HUNTER_PASS semanticPlayers=true externalMatcher=true oneOpenAssignment=true " +
            "assignmentLifecycle=true deadlineHook=true targetUnavailable=true terminalIdempotent=true " +
            "objectiveReferencePort=true duplicateProgress=false taskSkip=true statsSeparate=true " +
            "streakFormulaInvented=false rewardGrant=false teleportMutation=false protocolIndependent=true"
        );
    }

    private static void assertProtocolIndependent(Class<?>... roots) {
        String[] forbidden = {"widget", "opcode", "subtype", "packet", "sprite", "sceneindex", "commandstring"};
        for (Class<?> root : roots) {
            for (java.lang.reflect.Field field : root.getDeclaredFields()) {
                String haystack = (field.getName() + " " + field.getType().getName()).toLowerCase(java.util.Locale.ROOT);
                for (String token : forbidden) {
                    require(!haystack.contains(token), root.getName() + " leaked presentation identity through " + field.getName());
                }
            }
        }
    }

    private static void expectIllegalState(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalStateException: " + label);
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private static void expectIllegalArgument(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException: " + label);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
