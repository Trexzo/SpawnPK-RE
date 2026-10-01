package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class DialogueSessionServiceTest {
    private static final String POLICY =
        "LOCAL_LAB_POLICY_DIALOGUE_TEST";

    public static void main(String[] args) {
        authorityFence();

        AtomicInteger resolverCalls =
            new AtomicInteger();

        DialogueSessionService service =
            new DialogueSessionService(
                POLICY,
                (player, definition, node, intent, before) -> {
                    resolverCalls.incrementAndGet();

                    if ("player:throw".equals(
                            player)) {
                        throw new IllegalStateException(
                            "resolver boom"
                        );
                    }

                    if ("player:missing".equals(
                            player)) {
                        return DialogueSessionService
                            .Transition.move(
                                "node:missing"
                            );
                    }

                    if (intent.kind ==
                            DialogueSessionService
                                .IntentKind.CLOSE) {
                        return DialogueSessionService
                            .Transition.end();
                    }

                    if ("node:intro".equals(
                            node.nodeKey)) {
                        return DialogueSessionService
                            .Transition.move(
                                "node:options"
                            );
                    }

                    if ("node:options".equals(
                            node.nodeKey)) {
                        if (intent.optionIndex == 1) {
                            return DialogueSessionService
                                .Transition.move(
                                    "node:final"
                                );
                        }

                        return DialogueSessionService
                            .Transition.end();
                    }

                    return DialogueSessionService
                        .Transition.end();
                }
            );

        DialogueSessionService.DialogueDefinition
            definition =
                definition();

        service.register(
            definition
        );

        require(
            service.catalog().size() == 1 &&
            service.catalog().get(0) ==
                definition,
            "dialogue catalog"
        );

        expect(
            UnsupportedOperationException.class,
            () -> service.catalog().clear(),
            "dialogue catalog mutable"
        );

        expect(
            UnsupportedOperationException.class,
            () -> definition.nodes().clear(),
            "dialogue nodes mutable"
        );

        DialogueSessionService.Snapshot
            empty =
                service.snapshot(
                    " Player:Alice "
                );

        require(
            "player:alice".equals(
                empty.playerRef
            ) &&
            !empty.active &&
            empty.revision == 0L &&
            service.playerStateCount() == 0,
            "read-only dialogue snapshot"
        );

        DialogueSessionService.Snapshot
            begun =
                service.begin(
                    "PLAYER:ALICE",
                    "DIALOGUE:TEST"
                );

        require(
            begun.active &&
            "dialogue:test".equals(
                begun.dialogueKey
            ) &&
            "node:intro".equals(
                begun.nodeKey
            ) &&
            begun.inputMode ==
                DialogueSessionService
                    .InputMode.CONTINUE &&
            begun.optionCount == 0 &&
            !begun.closeSupported &&
            begun.revision == 1L &&
            DialogueSessionService
                .PRESENTATION_AUTHORITY
                .equals(
                    begun
                        .presentationAuthority
                ),
            "dialogue begin"
        );

        expect(
            IllegalStateException.class,
            () -> service.begin(
                "player:alice",
                "dialogue:test"
            ),
            "double dialogue begin"
        );

        int beforeInvalid =
            resolverCalls.get();

        expect(
            IllegalStateException.class,
            () -> service.chooseOption(
                "player:alice",
                1
            ),
            "option on continue node"
        );

        require(
            resolverCalls.get() ==
                beforeInvalid &&
            service.snapshot(
                "player:alice"
            ).revision == 1L,
            "invalid intent invoked resolver"
        );

        DialogueSessionService.Snapshot
            options =
                service.continueDialogue(
                    "player:alice"
                );

        require(
            options.active &&
            "node:options".equals(
                options.nodeKey
            ) &&
            options.inputMode ==
                DialogueSessionService
                    .InputMode.OPTIONS &&
            options.optionCount == 2 &&
            options.closeSupported &&
            options.revision == 2L,
            "continue transition"
        );

        beforeInvalid =
            resolverCalls.get();

        expect(
            IllegalArgumentException.class,
            () -> service.chooseOption(
                "player:alice",
                3
            ),
            "out-of-node-range option"
        );

        require(
            resolverCalls.get() ==
                beforeInvalid &&
            service.snapshot(
                "player:alice"
            ).revision == 2L,
            "invalid option invoked resolver"
        );

        DialogueSessionService.Snapshot
            finalNode =
                service.chooseOption(
                    "player:alice",
                    1
                );

        require(
            finalNode.active &&
            "node:final".equals(
                finalNode.nodeKey
            ) &&
            finalNode.revision == 3L,
            "option move transition"
        );

        beforeInvalid =
            resolverCalls.get();

        expect(
            IllegalStateException.class,
            () -> service.close(
                "player:alice"
            ),
            "close on continue node"
        );

        require(
            resolverCalls.get() ==
                beforeInvalid,
            "invalid close invoked resolver"
        );

        DialogueSessionService.Snapshot
            ended =
                service.continueDialogue(
                    "player:alice"
                );

        require(
            !ended.active &&
            ended.revision == 4L,
            "explicit end transition"
        );

        service.begin(
            "player:bob",
            "dialogue:test"
        );
        service.continueDialogue(
            "player:bob"
        );

        DialogueSessionService.Snapshot
            bobClosed =
                service.close(
                    "player:bob"
                );

        require(
            !bobClosed.active &&
            bobClosed.revision == 3L,
            "semantic close delegated"
        );

        serverAbortLifecycle(
            service,
            resolverCalls
        );
        resolverFailureAtomic(
            service
        );
        rollbackTransitionMonotonic(
            service
        );
        unknownMoveAtomic(
            service
        );
        playerIsolation(
            service
        );
        resolverOutsideMonitorAndStaleCommitRejected();
        serverAbortFencesInFlightResolver();
        nodeValidation();
        protocolBoundary();

        System.out.println(
            "DIALOGUE_SESSION_SERVICE_PASS " +
            "semanticDefinitions=true " +
            "continueIntent=true " +
            "optionIntent=true " +
            "optionRange1to5=true " +
            "closeIntentDelegated=true " +
            "serverAbort=true " +
            "abortIdempotent=true " +
            "abortFencesStaleResolver=true " +
            "resolverOwnedTransitions=true " +
            "invalidIntentNoResolver=true " +
            "resolverFailureAtomic=true " +
            "rollbackTransitionMonotonic=true " +
            "unknownMoveFailClosed=true " +
            "revisionedState=true " +
            "playerIsolation=true " +
            "presentationAuthority=EXACT_CURRENT_CLIENT " +
            "policyAuthorityLocal=true " +
            "transportIndependent=true " +
            "rewardMutation=false " +
            "persistenceOwned=false"
        );
    }

    private static DialogueSessionService
        .DialogueDefinition definition() {
        return new DialogueSessionService
            .DialogueDefinition(
                "dialogue:test",
                "node:intro",
                Arrays.asList(
                    new DialogueSessionService
                        .NodeDefinition(
                            "node:intro",
                            DialogueSessionService
                                .InputMode.CONTINUE,
                            0,
                            false,
                            POLICY
                        ),
                    new DialogueSessionService
                        .NodeDefinition(
                            "node:options",
                            DialogueSessionService
                                .InputMode.OPTIONS,
                            2,
                            true,
                            POLICY
                        ),
                    new DialogueSessionService
                        .NodeDefinition(
                            "node:final",
                            DialogueSessionService
                                .InputMode.CONTINUE,
                            0,
                            false,
                            POLICY
                        )
                ),
                POLICY
            );
    }

    private static void serverAbortLifecycle(
        DialogueSessionService service,
        AtomicInteger resolverCalls
    ) {
        service.begin(
            "player:abort",
            "dialogue:test"
        );

        DialogueSessionService.Snapshot before =
            service.snapshot(
                "player:abort"
            );

        int beforeResolverCalls =
            resolverCalls.get();

        DialogueSessionService.Snapshot aborted =
            service.abort(
                "player:abort"
            );

        require(
            !aborted.active &&
            aborted.revision ==
                before.revision + 1L &&
            aborted.dialogueKey == null &&
            aborted.nodeKey == null,
            "server abort did not terminate active dialogue"
        );

        require(
            resolverCalls.get() ==
                beforeResolverCalls,
            "server abort invoked transition resolver"
        );

        DialogueSessionService.Snapshot again =
            service.abort(
                "player:abort"
            );

        require(
            !again.active &&
            again.revision ==
                aborted.revision,
            "inactive abort was not idempotent"
        );

        DialogueSessionService.Snapshot missing =
            service.abort(
                "player:never-begun"
            );

        require(
            !missing.active &&
            missing.revision == 0L,
            "missing-player abort created revisioned state"
        );
    }

    private static void resolverFailureAtomic(
        DialogueSessionService service
    ) {
        service.begin(
            "player:throw",
            "dialogue:test"
        );

        DialogueSessionService.Snapshot before =
            service.snapshot(
                "player:throw"
            );

        expect(
            IllegalStateException.class,
            () -> service.continueDialogue(
                "player:throw"
            ),
            "resolver failure"
        );

        DialogueSessionService.Snapshot after =
            service.snapshot(
                "player:throw"
            );

        require(
            after.active &&
            before.revision ==
                after.revision &&
            before.nodeKey.equals(
                after.nodeKey
            ),
            "resolver failure mutated dialogue"
        );
    }

    private static void rollbackTransitionMonotonic(
        DialogueSessionService service
    ) {
        service.begin(
            "player:rollback",
            "dialogue:test"
        );
        DialogueSessionService.Snapshot options =
            service.continueDialogue(
                "player:rollback"
            );
        DialogueSessionService.Snapshot ended =
            service.chooseOption(
                "player:rollback",
                2
            );

        require(
            !ended.active &&
            ended.revision ==
                options.revision + 1L,
            "rollback fixture did not end"
        );

        DialogueSessionService.Snapshot restored =
            service.rollbackTransition(
                options,
                ended
            );

        require(
            restored.active &&
            "node:options".equals(
                restored.nodeKey
            ) &&
            restored.revision ==
                ended.revision + 1L,
            "dialogue rollback was not monotonic"
        );

        expect(
            IllegalStateException.class,
            () -> service.rollbackTransition(
                options,
                ended
            ),
            "stale dialogue rollback"
        );
    }

    private static void unknownMoveAtomic(
        DialogueSessionService service
    ) {
        service.begin(
            "player:missing",
            "dialogue:test"
        );

        DialogueSessionService.Snapshot before =
            service.snapshot(
                "player:missing"
            );

        expect(
            IllegalArgumentException.class,
            () -> service.continueDialogue(
                "player:missing"
            ),
            "unknown move target"
        );

        DialogueSessionService.Snapshot after =
            service.snapshot(
                "player:missing"
            );

        require(
            after.active &&
            before.revision ==
                after.revision &&
            before.nodeKey.equals(
                after.nodeKey
            ),
            "unknown move mutated dialogue"
        );
    }

    private static void playerIsolation(
        DialogueSessionService service
    ) {
        service.begin(
            "player:charlie",
            "dialogue:test"
        );
        service.begin(
            "player:dana",
            "dialogue:test"
        );

        service.continueDialogue(
            "player:charlie"
        );

        DialogueSessionService.Snapshot charlie =
            service.snapshot(
                "player:charlie"
            );
        DialogueSessionService.Snapshot dana =
            service.snapshot(
                "player:dana"
            );

        require(
            "node:options".equals(
                charlie.nodeKey
            ) &&
            "node:intro".equals(
                dana.nodeKey
            ),
            "dialogue player isolation"
        );
    }

    private static void
        resolverOutsideMonitorAndStaleCommitRejected()
    {
        CountDownLatch firstEntered =
            new CountDownLatch(1);
        CountDownLatch releaseFirst =
            new CountDownLatch(1);
        CountDownLatch secondDone =
            new CountDownLatch(1);
        AtomicInteger calls =
            new AtomicInteger();
        AtomicReference<Throwable> firstFailure =
            new AtomicReference<>();
        AtomicReference<Throwable> secondFailure =
            new AtomicReference<>();

        DialogueSessionService concurrent =
            new DialogueSessionService(
                POLICY,
                (player, definition, node, intent, before) -> {
                    int call =
                        calls.incrementAndGet();

                    if (call == 1) {
                        firstEntered.countDown();

                        try {
                            if (!releaseFirst.await(
                                    2L,
                                    TimeUnit.SECONDS)) {
                                throw new AssertionError(
                                    "first resolver release timeout"
                                );
                            }
                        } catch (
                            InterruptedException interrupted
                        ) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(
                                "first resolver interrupted",
                                interrupted
                            );
                        }
                    }

                    return DialogueSessionService
                        .Transition.move(
                            "node:options"
                        );
                }
            );

        concurrent.register(
            definition()
        );
        concurrent.begin(
            "player:race",
            "dialogue:test"
        );

        Thread first =
            new Thread(
                () -> {
                    try {
                        concurrent.continueDialogue(
                            "player:race"
                        );
                    } catch (Throwable failure) {
                        firstFailure.set(
                            failure
                        );
                    }
                },
                "dialogue-first-resolver"
            );

        Thread second =
            new Thread(
                () -> {
                    try {
                        concurrent.continueDialogue(
                            "player:race"
                        );
                    } catch (Throwable failure) {
                        secondFailure.set(
                            failure
                        );
                    } finally {
                        secondDone.countDown();
                    }
                },
                "dialogue-second-resolver"
            );

        try {
            first.start();

            require(
                firstEntered.await(
                    2L,
                    TimeUnit.SECONDS
                ),
                "first dialogue resolver did not enter"
            );

            second.start();

            boolean completedWhileFirstBlocked =
                secondDone.await(
                    1L,
                    TimeUnit.SECONDS
                );

            releaseFirst.countDown();

            first.join(
                2000L
            );
            second.join(
                2000L
            );

            require(
                completedWhileFirstBlocked,
                "dialogue resolver still executed under service monitor"
            );
        } catch (
            InterruptedException interrupted
        ) {
            releaseFirst.countDown();
            Thread.currentThread().interrupt();
            throw new AssertionError(
                "dialogue concurrency test interrupted",
                interrupted
            );
        }

        require(
            secondFailure.get() == null,
            "second dialogue transition failed " +
            secondFailure.get()
        );

        require(
            firstFailure.get() instanceof
                IllegalStateException,
            "stale first dialogue transition not rejected failure=" +
            firstFailure.get()
        );

        DialogueSessionService.Snapshot current =
            concurrent.snapshot(
                "player:race"
            );

        require(
            current.active &&
            "node:options".equals(
                current.nodeKey
            ) &&
            current.revision == 2L &&
            calls.get() == 2,
            "dialogue stale callback overwrote newer state"
        );
    }

    private static void
        serverAbortFencesInFlightResolver()
    {
        CountDownLatch entered =
            new CountDownLatch(1);
        CountDownLatch release =
            new CountDownLatch(1);
        AtomicReference<Throwable> failure =
            new AtomicReference<>();

        DialogueSessionService concurrent =
            new DialogueSessionService(
                POLICY,
                (player, definition, node, intent, before) -> {
                    entered.countDown();

                    try {
                        if (!release.await(
                                2L,
                                TimeUnit.SECONDS)) {
                            throw new AssertionError(
                                "abort resolver release timeout"
                            );
                        }
                    } catch (
                        InterruptedException interrupted
                    ) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(
                            "abort resolver interrupted",
                            interrupted
                        );
                    }

                    return DialogueSessionService
                        .Transition.move(
                            "node:options"
                        );
                }
            );

        concurrent.register(
            definition()
        );
        concurrent.begin(
            "player:abort-race",
            "dialogue:test"
        );

        Thread resolver =
            new Thread(
                () -> {
                    try {
                        concurrent.continueDialogue(
                            "player:abort-race"
                        );
                    } catch (Throwable thrown) {
                        failure.set(
                            thrown
                        );
                    }
                },
                "dialogue-abort-resolver"
            );

        try {
            resolver.start();

            require(
                entered.await(
                    2L,
                    TimeUnit.SECONDS
                ),
                "abort resolver did not enter"
            );

            DialogueSessionService.Snapshot aborted =
                concurrent.abort(
                    "player:abort-race"
                );

            require(
                !aborted.active &&
                aborted.revision == 2L,
                "abort did not revision-fence active resolver"
            );

            release.countDown();

            resolver.join(
                2000L
            );
        } catch (
            InterruptedException interrupted
        ) {
            release.countDown();
            Thread.currentThread().interrupt();
            throw new AssertionError(
                "abort concurrency test interrupted",
                interrupted
            );
        }

        require(
            failure.get() instanceof
                IllegalStateException,
            "stale resolver survived server abort failure=" +
            failure.get()
        );

        DialogueSessionService.Snapshot current =
            concurrent.snapshot(
                "player:abort-race"
            );

        require(
            !current.active &&
            current.revision == 2L,
            "stale resolver resurrected aborted dialogue"
        );
    }

    private static void nodeValidation() {
        expect(
            IllegalArgumentException.class,
            () -> new DialogueSessionService
                .NodeDefinition(
                    "node:bad",
                    DialogueSessionService
                        .InputMode.OPTIONS,
                    0,
                    true,
                    POLICY
                ),
            "zero option node"
        );

        expect(
            IllegalArgumentException.class,
            () -> new DialogueSessionService
                .NodeDefinition(
                    "node:bad",
                    DialogueSessionService
                        .InputMode.OPTIONS,
                    6,
                    true,
                    POLICY
                ),
            "six option node"
        );

        expect(
            IllegalArgumentException.class,
            () -> new DialogueSessionService
                .NodeDefinition(
                    "node:bad",
                    DialogueSessionService
                        .InputMode.CONTINUE,
                    0,
                    true,
                    POLICY
                ),
            "continue node close support"
        );
    }

    private static void authorityFence() {
        expect(
            IllegalArgumentException.class,
            () -> new DialogueSessionService(
                "EXACT_CURRENT_CLIENT",
                (player, definition, node, intent, before) ->
                    DialogueSessionService
                        .Transition.stay()
            ),
            "exact client used as dialogue policy"
        );

        expect(
            IllegalArgumentException.class,
            () -> new DialogueSessionService(
                "UNKNOWN_SERVER_AUTHORITY",
                (player, definition, node, intent, before) ->
                    DialogueSessionService
                        .Transition.stay()
            ),
            "unknown authority used as dialogue policy"
        );
    }

    private static void protocolBoundary() {
        for (Class<?> type : new Class<?>[]{
                DialogueSessionService.class,
                DialogueSessionService
                    .DialogueDefinition.class,
                DialogueSessionService
                    .NodeDefinition.class,
                DialogueSessionService
                    .Snapshot.class,
                DialogueSessionService
                    .Intent.class
        }) {
            for (Field field :
                    type.getDeclaredFields()) {
                String name =
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if (name.contains("widget") ||
                    name.contains("opcode") ||
                    name.contains("packet") ||
                    name.contains("root") ||
                    name.contains("npcid") ||
                    name.contains("itemid") ||
                    name.contains("modelid") ||
                    name.contains("sceneindex")) {
                    throw new AssertionError(
                        "transport/presentation identity leaked into dialogue domain " +
                        type.getSimpleName() +
                        "." +
                        field.getName()
                    );
                }
            }
        }

        for (Method method :
                DialogueSessionService.class
                    .getDeclaredMethods()) {
            String name =
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if (name.contains("reward") ||
                name.contains("grant") ||
                name.contains("persist") ||
                name.contains("packet") ||
                name.contains("widget") ||
                name.contains("opcode")) {
                throw new AssertionError(
                    "non-domain behavior leaked into dialogue service " +
                    method.getName()
                );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ) {
        try {
            action.run();
        } catch (
            Throwable failure
        ) {
            if (type.isInstance(
                    failure)) {
                return;
            }

            throw new AssertionError(
                label +
                " wrong failure " +
                failure,
                failure
            );
        }

        throw new AssertionError(
            label +
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ) {
        if (!condition) {
            throw new AssertionError(
                label
            );
        }
    }

    private DialogueSessionServiceTest() {}
}
