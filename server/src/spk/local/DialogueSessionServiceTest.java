package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

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

        resolverFailureAtomic(
            service
        );
        unknownMoveAtomic(
            service
        );
        playerIsolation(
            service
        );
        nodeValidation();
        protocolBoundary();

        System.out.println(
            "DIALOGUE_SESSION_SERVICE_PASS " +
            "semanticDefinitions=true " +
            "continueIntent=true " +
            "optionIntent=true " +
            "optionRange1to5=true " +
            "closeIntentDelegated=true " +
            "resolverOwnedTransitions=true " +
            "invalidIntentNoResolver=true " +
            "resolverFailureAtomic=true " +
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
