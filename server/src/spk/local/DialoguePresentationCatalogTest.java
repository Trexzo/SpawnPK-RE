package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public final class DialoguePresentationCatalogTest {
    private static final String POLICY =
        "LOCAL_LAB_POLICY_DIALOGUE_PRESENTATION_TEST";

    public static void main(String[] args) {
        DialogueSessionService.DialogueDefinition
            dialogue =
                dialogue();

        DialoguePresentationCatalog.NodePresentation
            statement =
                DialoguePresentationCatalog
                    .NodePresentation.statement(
                        "node:statement",
                        Arrays.asList(
                            "Line 1",
                            "Line 2",
                            "Line 3",
                            "Line 4",
                            "Line 5"
                        ),
                        POLICY
                    );

        DialoguePresentationCatalog.NodePresentation
            named =
                DialoguePresentationCatalog
                    .NodePresentation.namedModel(
                        "node:named",
                        " Guide ",
                        " MODEL:GUIDE ",
                        Arrays.asList(
                            "Named 1",
                            "Named 2",
                            "Named 3",
                            "Named 4"
                        ),
                        POLICY
                    );

        DialoguePresentationCatalog.NodePresentation
            item =
                DialoguePresentationCatalog
                    .NodePresentation.itemBacked(
                        "node:item",
                        " ITEM:REWARD_PREVIEW ",
                        Arrays.asList(
                            "Item 1",
                            "Item 2",
                            "Item 3",
                            "Item 4"
                        ),
                        POLICY
                    );

        DialoguePresentationCatalog.NodePresentation
            options =
                DialoguePresentationCatalog
                    .NodePresentation.options(
                        "node:options",
                        Arrays.asList(
                            "Option 1",
                            "Option 2",
                            "Option 3",
                            "Option 4",
                            "Option 5"
                        ),
                        POLICY
                    );

        DialoguePresentationCatalog catalog =
            new DialoguePresentationCatalog(
                dialogue,
                Arrays.asList(
                    statement,
                    named,
                    item,
                    options
                )
            );

        require(
            catalog.size() == 4,
            "catalog size"
        );

        require(
            "dialogue:presentation".equals(
                catalog.dialogueKey()
            ),
            "dialogue key"
        );

        require(
            POLICY.equals(
                catalog.policyAuthority()
            ),
            "policy authority"
        );

        require(
            DialoguePresentationCatalog
                .PRESENTATION_AUTHORITY
                .equals(
                    catalog
                        .presentationAuthority()
                ),
            "presentation authority"
        );

        require(
            catalog.presentations().equals(
                Arrays.asList(
                    statement,
                    named,
                    item,
                    options
                )
            ),
            "dialogue-node order"
        );

        require(
            catalog.forNode(
                "NODE:NAMED"
            ) == named,
            "normalized node lookup"
        );

        require(
            named.family ==
                DialoguePresentationCatalog
                    .Family.NAMED_MODEL &&
            "Guide".equals(
                named.speakerName
            ) &&
            "model:guide".equals(
                named.semanticRef
            ) &&
            named.hasSemanticRef() &&
            named.hasSpeakerName(),
            "named-model semantic projection"
        );

        require(
            item.family ==
                DialoguePresentationCatalog
                    .Family.ITEM_BACKED &&
            "item:reward_preview".equals(
                item.semanticRef
            ) &&
            item.hasSemanticRef() &&
            !item.hasSpeakerName(),
            "item-backed semantic projection"
        );

        require(
            statement.lines.size() == 5 &&
            named.lines.size() == 4 &&
            item.lines.size() == 4 &&
            options.lines.size() == 5,
            "exact family capacities"
        );

        expectUnsupported(
            () -> catalog
                .presentations()
                .clear(),
            "catalog mutable"
        );

        expectUnsupported(
            () -> named.lines.add(
                "Injected"
            ),
            "presentation lines mutable"
        );

        constructionGuards(
            dialogue,
            statement,
            named,
            item,
            options
        );

        familyCapacityGuards();
        boundaryGuard();

        System.out.println(
            "DIALOGUE_PRESENTATION_CATALOG_PASS " +
            "statement1to5=true " +
            "namedModel1to4=true " +
            "itemBacked1to4=true " +
            "options1to5=true " +
            "optionCountMatchesNode=true " +
            "completeNodeCoverage=true " +
            "unknownNodeRejected=true " +
            "duplicateNodeRejected=true " +
            "authorityMatched=true " +
            "semanticRefs=true " +
            "immutableCatalog=true " +
            "rawWidgetIdentity=false " +
            "transitionOwned=false " +
            "protocolIndependent=true"
        );
    }

    private static DialogueSessionService
        .DialogueDefinition dialogue() {
        return new DialogueSessionService
            .DialogueDefinition(
                "dialogue:presentation",
                "node:statement",
                Arrays.asList(
                    new DialogueSessionService
                        .NodeDefinition(
                            "node:statement",
                            DialogueSessionService
                                .InputMode.CONTINUE,
                            0,
                            false,
                            POLICY
                        ),
                    new DialogueSessionService
                        .NodeDefinition(
                            "node:named",
                            DialogueSessionService
                                .InputMode.CONTINUE,
                            0,
                            false,
                            POLICY
                        ),
                    new DialogueSessionService
                        .NodeDefinition(
                            "node:item",
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
                            5,
                            true,
                            POLICY
                        )
                ),
                POLICY
            );
    }

    private static void constructionGuards(
        DialogueSessionService.DialogueDefinition
            dialogue,
        DialoguePresentationCatalog.NodePresentation
            statement,
        DialoguePresentationCatalog.NodePresentation
            named,
        DialoguePresentationCatalog.NodePresentation
            item,
        DialoguePresentationCatalog.NodePresentation
            options
    ) {
        expect(
            IllegalArgumentException.class,
            () -> new DialoguePresentationCatalog(
                dialogue,
                Arrays.asList(
                    statement,
                    named,
                    item
                )
            ),
            "incomplete node coverage"
        );

        expect(
            IllegalArgumentException.class,
            () -> new DialoguePresentationCatalog(
                dialogue,
                Arrays.asList(
                    statement,
                    named,
                    item,
                    options,
                    options
                )
            ),
            "duplicate node presentation"
        );

        DialoguePresentationCatalog.NodePresentation
            unknown =
                DialoguePresentationCatalog
                    .NodePresentation.statement(
                        "node:unknown",
                        Collections.singletonList(
                            "Unknown"
                        ),
                        POLICY
                    );

        expect(
            IllegalArgumentException.class,
            () -> new DialoguePresentationCatalog(
                dialogue,
                Arrays.asList(
                    statement,
                    named,
                    item,
                    options,
                    unknown
                )
            ),
            "unknown node presentation"
        );

        DialoguePresentationCatalog.NodePresentation
            wrongAuthority =
                DialoguePresentationCatalog
                    .NodePresentation.statement(
                        "node:statement",
                        Collections.singletonList(
                            "Wrong"
                        ),
                        "LOCAL_LAB_POLICY_OTHER"
                    );

        expect(
            IllegalArgumentException.class,
            () -> new DialoguePresentationCatalog(
                dialogue,
                Arrays.asList(
                    wrongAuthority,
                    named,
                    item,
                    options
                )
            ),
            "authority mismatch"
        );

        DialoguePresentationCatalog.NodePresentation
            shortOptions =
                DialoguePresentationCatalog
                    .NodePresentation.options(
                        "node:options",
                        Arrays.asList(
                            "One",
                            "Two"
                        ),
                        POLICY
                    );

        expect(
            IllegalArgumentException.class,
            () -> new DialoguePresentationCatalog(
                dialogue,
                Arrays.asList(
                    statement,
                    named,
                    item,
                    shortOptions
                )
            ),
            "option label count mismatch"
        );

        DialoguePresentationCatalog.NodePresentation
            optionsOnContinue =
                DialoguePresentationCatalog
                    .NodePresentation.options(
                        "node:statement",
                        Collections.singletonList(
                            "One"
                        ),
                        POLICY
                    );

        expect(
            IllegalArgumentException.class,
            () -> new DialoguePresentationCatalog(
                dialogue,
                Arrays.asList(
                    optionsOnContinue,
                    named,
                    item,
                    options
                )
            ),
            "options family on continue node"
        );

        DialoguePresentationCatalog.NodePresentation
            statementOnOptions =
                DialoguePresentationCatalog
                    .NodePresentation.statement(
                        "node:options",
                        Collections.singletonList(
                            "Not options"
                        ),
                        POLICY
                    );

        expect(
            IllegalArgumentException.class,
            () -> new DialoguePresentationCatalog(
                dialogue,
                Arrays.asList(
                    statement,
                    named,
                    item,
                    statementOnOptions
                )
            ),
            "continue family on options node"
        );
    }

    private static void familyCapacityGuards() {
        expect(
            IllegalArgumentException.class,
            () -> DialoguePresentationCatalog
                .NodePresentation.statement(
                    "node:s",
                    Collections.emptyList(),
                    POLICY
                ),
            "empty statement"
        );

        expect(
            IllegalArgumentException.class,
            () -> DialoguePresentationCatalog
                .NodePresentation.statement(
                    "node:s",
                    lines(6),
                    POLICY
                ),
            "six-line statement"
        );

        expect(
            IllegalArgumentException.class,
            () -> DialoguePresentationCatalog
                .NodePresentation.namedModel(
                    "node:n",
                    "Speaker",
                    "model:speaker",
                    lines(5),
                    POLICY
                ),
            "five-line named model"
        );

        expect(
            IllegalArgumentException.class,
            () -> DialoguePresentationCatalog
                .NodePresentation.itemBacked(
                    "node:i",
                    "item:semantic",
                    lines(5),
                    POLICY
                ),
            "five-line item backed"
        );

        expect(
            IllegalArgumentException.class,
            () -> DialoguePresentationCatalog
                .NodePresentation.options(
                    "node:o",
                    lines(6),
                    POLICY
                ),
            "six-option presentation"
        );

        expect(
            IllegalArgumentException.class,
            () -> DialoguePresentationCatalog
                .NodePresentation.statement(
                    "node:s",
                    Collections.singletonList(
                        "Line"
                    ),
                    "EXACT_CURRENT_CLIENT"
                ),
            "exact client used as content policy"
        );
    }

    private static ArrayList<String> lines(
        int count
    ) {
        ArrayList<String> out =
            new ArrayList<>();

        for (int i = 1;
             i <= count;
             i++) {
            out.add(
                "Line " + i
            );
        }

        return out;
    }

    private static void boundaryGuard() {
        for (Class<?> type :
                new Class<?>[]{
                    DialoguePresentationCatalog.class,
                    DialoguePresentationCatalog
                        .NodePresentation.class
                }) {
            for (Field field :
                    type.getDeclaredFields()) {
                String name =
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if (name.contains("widget") ||
                    name.contains("root") ||
                    name.contains("opcode") ||
                    name.contains("packet") ||
                    name.contains("npcid") ||
                    name.contains("itemid") ||
                    name.contains("modelid") ||
                    name.contains("animation") ||
                    name.contains("camera")) {
                    throw new AssertionError(
                        "raw presentation identity leaked " +
                        type.getSimpleName() +
                        "." +
                        field.getName()
                    );
                }
            }
        }

        for (Method method :
                DialoguePresentationCatalog.class
                    .getDeclaredMethods()) {
            String name =
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if (name.contains("transition") ||
                name.contains("advance") ||
                name.contains("reward") ||
                name.contains("persist") ||
                name.contains("publish") ||
                name.contains("packet")) {
                throw new AssertionError(
                    "state/effect behavior leaked into presentation catalog " +
                    method.getName()
                );
            }
        }
    }

    private static void expectUnsupported(
        Runnable action,
        String label
    ) {
        try {
            action.run();
            throw new AssertionError(
                "Expected immutable view: " +
                label
            );
        } catch (
            UnsupportedOperationException expected
        ) {
            // expected
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

    private DialoguePresentationCatalogTest() {}
}
