package spk.local;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Protocol-independent dialogue presentation/content definitions.
 *
 * Exact-current client evidence proves the supported presentation families.
 * This catalog binds those families to semantic DialogueSessionService nodes
 * without exposing roots, widgets, packets, model ids or item ids.
 */
final class DialoguePresentationCatalog {
    static final String PRESENTATION_AUTHORITY =
        "EXACT_CURRENT_CLIENT";

    enum Family {
        STATEMENT,
        NAMED_MODEL,
        ITEM_BACKED,
        OPTIONS
    }

    static final class NodePresentation {
        final String nodeKey;
        final Family family;
        final String speakerName;
        final String semanticRef;
        final List<String> lines;
        final String policyAuthority;
        final String presentationAuthority;

        private NodePresentation(
            String nodeKey,
            Family family,
            String speakerName,
            String semanticRef,
            Collection<String> lines,
            String policyAuthority
        ) {
            this.nodeKey =
                normalizeKey(
                    nodeKey,
                    "nodeKey"
                );
            this.family =
                Objects.requireNonNull(
                    family,
                    "family"
                );
            this.policyAuthority =
                requireLocalPolicy(
                    policyAuthority
                );
            this.presentationAuthority =
                PRESENTATION_AUTHORITY;

            ArrayList<String> lineCopy =
                validatedLines(
                    lines,
                    family
                );

            if (family ==
                    Family.STATEMENT) {
                requireNull(
                    speakerName,
                    "STATEMENT speakerName"
                );
                requireNull(
                    semanticRef,
                    "STATEMENT semanticRef"
                );
                this.speakerName = null;
                this.semanticRef = null;
            } else if (family ==
                    Family.NAMED_MODEL) {
                this.speakerName =
                    requireText(
                        speakerName,
                        "speakerName"
                    );
                this.semanticRef =
                    normalizeKey(
                        semanticRef,
                        "semanticRef"
                    );
            } else if (family ==
                    Family.ITEM_BACKED) {
                requireNull(
                    speakerName,
                    "ITEM_BACKED speakerName"
                );
                this.speakerName = null;
                this.semanticRef =
                    normalizeKey(
                        semanticRef,
                        "semanticRef"
                    );
            } else {
                requireNull(
                    speakerName,
                    "OPTIONS speakerName"
                );
                requireNull(
                    semanticRef,
                    "OPTIONS semanticRef"
                );
                this.speakerName = null;
                this.semanticRef = null;
            }

            this.lines =
                Collections.unmodifiableList(
                    lineCopy
                );
        }

        static NodePresentation statement(
            String nodeKey,
            Collection<String> lines,
            String policyAuthority
        ) {
            return new NodePresentation(
                nodeKey,
                Family.STATEMENT,
                null,
                null,
                lines,
                policyAuthority
            );
        }

        static NodePresentation namedModel(
            String nodeKey,
            String speakerName,
            String semanticModelRef,
            Collection<String> lines,
            String policyAuthority
        ) {
            return new NodePresentation(
                nodeKey,
                Family.NAMED_MODEL,
                speakerName,
                semanticModelRef,
                lines,
                policyAuthority
            );
        }

        static NodePresentation itemBacked(
            String nodeKey,
            String semanticItemRef,
            Collection<String> lines,
            String policyAuthority
        ) {
            return new NodePresentation(
                nodeKey,
                Family.ITEM_BACKED,
                null,
                semanticItemRef,
                lines,
                policyAuthority
            );
        }

        static NodePresentation options(
            String nodeKey,
            Collection<String> optionLabels,
            String policyAuthority
        ) {
            return new NodePresentation(
                nodeKey,
                Family.OPTIONS,
                null,
                null,
                optionLabels,
                policyAuthority
            );
        }

        boolean hasSemanticRef() {
            return semanticRef != null;
        }

        boolean hasSpeakerName() {
            return speakerName != null;
        }
    }

    private final DialogueSessionService.DialogueDefinition
        dialogue;
    private final List<NodePresentation> ordered;
    private final Map<String, NodePresentation> byNode;

    DialoguePresentationCatalog(
        DialogueSessionService.DialogueDefinition dialogue,
        Collection<NodePresentation> presentations
    ) {
        this.dialogue =
            Objects.requireNonNull(
                dialogue,
                "dialogue"
            );
        Objects.requireNonNull(
            presentations,
            "presentations"
        );

        LinkedHashMap<String, NodePresentation>
            supplied =
                new LinkedHashMap<>();

        for (NodePresentation presentation :
                presentations) {
            NodePresentation checked =
                Objects.requireNonNull(
                    presentation,
                    "presentation"
                );

            if (!dialogue.policyAuthority.equals(
                    checked.policyAuthority)) {
                throw new IllegalArgumentException(
                    "dialogue presentation authority mismatch node=" +
                    checked.nodeKey
                );
            }

            DialogueSessionService.NodeDefinition node =
                dialogue.node(
                    checked.nodeKey
                );

            if (node == null) {
                throw new IllegalArgumentException(
                    "unknown dialogue presentation node " +
                    checked.nodeKey
                );
            }

            validateCompatibility(
                node,
                checked
            );

            if (supplied.put(
                    checked.nodeKey,
                    checked) != null) {
                throw new IllegalArgumentException(
                    "duplicate dialogue presentation node " +
                    checked.nodeKey
                );
            }
        }

        ArrayList<NodePresentation> orderedCopy =
            new ArrayList<>();

        for (DialogueSessionService.NodeDefinition node :
                dialogue.nodes()) {
            NodePresentation presentation =
                supplied.get(
                    node.nodeKey
                );

            if (presentation == null) {
                throw new IllegalArgumentException(
                    "missing dialogue presentation node " +
                    node.nodeKey
                );
            }

            orderedCopy.add(
                presentation
            );
        }

        if (supplied.size() !=
                orderedCopy.size()) {
            throw new IllegalStateException(
                "dialogue presentation coverage mismatch supplied=" +
                supplied.size() +
                " nodes=" +
                orderedCopy.size()
            );
        }

        LinkedHashMap<String, NodePresentation>
            finalMap =
                new LinkedHashMap<>();

        for (NodePresentation presentation :
                orderedCopy) {
            finalMap.put(
                presentation.nodeKey,
                presentation
            );
        }

        this.ordered =
            Collections.unmodifiableList(
                orderedCopy
            );
        this.byNode =
            Collections.unmodifiableMap(
                finalMap
            );
    }

    String dialogueKey() {
        return dialogue.dialogueKey;
    }

    String policyAuthority() {
        return dialogue.policyAuthority;
    }

    String presentationAuthority() {
        return PRESENTATION_AUTHORITY;
    }

    NodePresentation forNode(
        String nodeKey
    ) {
        return byNode.get(
            normalizeKey(
                nodeKey,
                "nodeKey"
            )
        );
    }

    List<NodePresentation> presentations() {
        return ordered;
    }

    int size() {
        return ordered.size();
    }

    private static void validateCompatibility(
        DialogueSessionService.NodeDefinition node,
        NodePresentation presentation
    ) {
        if (presentation.family ==
                Family.OPTIONS) {
            if (node.inputMode !=
                    DialogueSessionService
                        .InputMode.OPTIONS) {
                throw new IllegalArgumentException(
                    "OPTIONS presentation requires OPTIONS node " +
                    node.nodeKey
                );
            }

            if (presentation.lines.size() !=
                    node.optionCount) {
                throw new IllegalArgumentException(
                    "OPTIONS label count mismatch node=" +
                    node.nodeKey +
                    " labels=" +
                    presentation.lines.size() +
                    " optionCount=" +
                    node.optionCount
                );
            }

            return;
        }

        if (node.inputMode !=
                DialogueSessionService
                    .InputMode.CONTINUE) {
            throw new IllegalArgumentException(
                presentation.family +
                " presentation requires CONTINUE node " +
                node.nodeKey
            );
        }
    }

    private static ArrayList<String>
        validatedLines(
            Collection<String> lines,
            Family family
        ) {
        Objects.requireNonNull(
            lines,
            "lines"
        );

        int maximum;

        if (family ==
                Family.STATEMENT) {
            maximum = 5;
        } else if (family ==
                Family.OPTIONS) {
            maximum = 5;
        } else {
            maximum = 4;
        }

        if (lines.isEmpty() ||
            lines.size() > maximum) {
            throw new IllegalArgumentException(
                family +
                " line count=" +
                lines.size() +
                " expected=1.." +
                maximum
            );
        }

        ArrayList<String> copy =
            new ArrayList<>();

        for (String line : lines) {
            copy.add(
                requireText(
                    line,
                    "line"
                )
            );
        }

        return copy;
    }

    private static void requireNull(
        String value,
        String field
    ) {
        if (value != null) {
            throw new IllegalArgumentException(
                field +
                " must be absent"
            );
        }
    }

    private static String normalizeKey(
        String value,
        String field
    ) {
        String clean =
            requireText(
                value,
                field
            ).toLowerCase(
                Locale.ROOT
            );

        if (clean.length() > 160) {
            throw new IllegalArgumentException(
                field +
                " too long"
            );
        }

        return clean;
    }

    private static String requireLocalPolicy(
        String value
    ) {
        String clean =
            requireText(
                value,
                "policyAuthority"
            );

        if (!clean.startsWith(
                "LOCAL_LAB_POLICY_")) {
            throw new IllegalArgumentException(
                "dialogue presentation policy must be LOCAL_LAB_POLICY_* actual=" +
                clean
            );
        }

        return clean;
    }

    private static String requireText(
        String value,
        String field
    ) {
        if (value == null) {
            throw new NullPointerException(
                field
            );
        }

        String clean =
            value.trim();

        if (clean.isEmpty()) {
            throw new IllegalArgumentException(
                field +
                " blank"
            );
        }

        return clean;
    }
}
