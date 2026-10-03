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
 * Protocol-independent semantic dialogue-session state.
 *
 * Exact-current client evidence proves standard Continue, option 1..5 and
 * close/cancel input capabilities. This service owns only semantic session
 * state and caller-defined transitions. Raw roots/widgets/opcodes stay outside
 * the domain boundary.
 */
final class DialogueSessionService {
    static final String PRESENTATION_AUTHORITY =
        "EXACT_CURRENT_CLIENT";

    enum InputMode {
        CONTINUE,
        OPTIONS
    }

    enum IntentKind {
        CONTINUE,
        OPTION,
        CLOSE
    }

    enum TransitionKind {
        STAY,
        MOVE,
        END
    }

    static final class NodeDefinition {
        final String nodeKey;
        final InputMode inputMode;
        final int optionCount;
        final boolean closeSupported;
        final String policyAuthority;

        NodeDefinition(
            String nodeKey,
            InputMode inputMode,
            int optionCount,
            boolean closeSupported,
            String policyAuthority
        ) {
            this.nodeKey =
                normalizeKey(
                    nodeKey,
                    "nodeKey"
                );
            this.inputMode =
                Objects.requireNonNull(
                    inputMode,
                    "inputMode"
                );
            this.policyAuthority =
                requireLocalPolicy(
                    policyAuthority
                );

            if (inputMode == InputMode.CONTINUE) {
                if (optionCount != 0) {
                    throw new IllegalArgumentException(
                        "CONTINUE node optionCount=" +
                        optionCount
                    );
                }
                if (closeSupported) {
                    throw new IllegalArgumentException(
                        "CONTINUE node cannot claim standard option close control"
                    );
                }
            } else {
                if (optionCount < 1 ||
                    optionCount > 5) {
                    throw new IllegalArgumentException(
                        "OPTIONS optionCount=" +
                        optionCount +
                        " expected=1..5"
                    );
                }
            }

            this.optionCount = optionCount;
            this.closeSupported =
                closeSupported;
        }
    }

    static final class DialogueDefinition {
        final String dialogueKey;
        final String startNodeKey;
        final String policyAuthority;

        private final Map<String, NodeDefinition>
            nodes;

        DialogueDefinition(
            String dialogueKey,
            String startNodeKey,
            Collection<NodeDefinition> nodes,
            String policyAuthority
        ) {
            this.dialogueKey =
                normalizeKey(
                    dialogueKey,
                    "dialogueKey"
                );
            this.startNodeKey =
                normalizeKey(
                    startNodeKey,
                    "startNodeKey"
                );
            this.policyAuthority =
                requireLocalPolicy(
                    policyAuthority
                );

            Objects.requireNonNull(
                nodes,
                "nodes"
            );

            if (nodes.isEmpty()) {
                throw new IllegalArgumentException(
                    "dialogue nodes empty"
                );
            }

            LinkedHashMap<String, NodeDefinition>
                copy =
                    new LinkedHashMap<>();

            for (NodeDefinition node : nodes) {
                NodeDefinition checked =
                    Objects.requireNonNull(
                        node,
                        "node"
                    );

                if (!this.policyAuthority.equals(
                        checked.policyAuthority)) {
                    throw new IllegalArgumentException(
                        "node authority mismatch key=" +
                        checked.nodeKey
                    );
                }

                if (copy.put(
                        checked.nodeKey,
                        checked) != null) {
                    throw new IllegalArgumentException(
                        "duplicate dialogue node " +
                        checked.nodeKey
                    );
                }
            }

            if (!copy.containsKey(
                    this.startNodeKey)) {
                throw new IllegalArgumentException(
                    "missing start node " +
                    this.startNodeKey
                );
            }

            this.nodes =
                Collections.unmodifiableMap(
                    copy
                );
        }

        NodeDefinition node(
            String nodeKey
        ) {
            return nodes.get(
                normalizeKey(
                    nodeKey,
                    "nodeKey"
                )
            );
        }

        List<NodeDefinition> nodes() {
            return Collections.unmodifiableList(
                new ArrayList<>(
                    nodes.values()
                )
            );
        }
    }

    static final class Intent {
        final IntentKind kind;
        final int optionIndex;

        private Intent(
            IntentKind kind,
            int optionIndex
        ) {
            this.kind =
                Objects.requireNonNull(
                    kind,
                    "kind"
                );
            this.optionIndex =
                optionIndex;
        }

        static Intent continueIntent() {
            return new Intent(
                IntentKind.CONTINUE,
                0
            );
        }

        static Intent option(
            int optionIndex
        ) {
            if (optionIndex < 1 ||
                optionIndex > 5) {
                throw new IllegalArgumentException(
                    "optionIndex=" +
                    optionIndex +
                    " expected=1..5"
                );
            }

            return new Intent(
                IntentKind.OPTION,
                optionIndex
            );
        }

        static Intent closeIntent() {
            return new Intent(
                IntentKind.CLOSE,
                0
            );
        }
    }

    static final class Transition {
        final TransitionKind kind;
        final String nextNodeKey;

        private Transition(
            TransitionKind kind,
            String nextNodeKey
        ) {
            this.kind =
                Objects.requireNonNull(
                    kind,
                    "kind"
                );
            this.nextNodeKey =
                nextNodeKey;
        }

        static Transition stay() {
            return new Transition(
                TransitionKind.STAY,
                null
            );
        }

        static Transition move(
            String nextNodeKey
        ) {
            return new Transition(
                TransitionKind.MOVE,
                normalizeKey(
                    nextNodeKey,
                    "nextNodeKey"
                )
            );
        }

        static Transition end() {
            return new Transition(
                TransitionKind.END,
                null
            );
        }
    }

    static final class Snapshot {
        final String playerRef;
        final boolean active;
        final String dialogueKey;
        final String nodeKey;
        final InputMode inputMode;
        final int optionCount;
        final boolean closeSupported;
        final long revision;
        final String policyAuthority;
        final String presentationAuthority;

        Snapshot(
            String playerRef,
            boolean active,
            String dialogueKey,
            String nodeKey,
            InputMode inputMode,
            int optionCount,
            boolean closeSupported,
            long revision,
            String policyAuthority
        ) {
            this.playerRef =
                playerRef;
            this.active =
                active;
            this.dialogueKey =
                dialogueKey;
            this.nodeKey =
                nodeKey;
            this.inputMode =
                inputMode;
            this.optionCount =
                optionCount;
            this.closeSupported =
                closeSupported;
            this.revision =
                revision;
            this.policyAuthority =
                policyAuthority;
            this.presentationAuthority =
                PRESENTATION_AUTHORITY;
        }
    }

    static final class PreparedBegin {
        private final DialogueSessionService owner;
        private final String playerRef;
        private final DialogueDefinition definition;
        private final long beforeRevision;
        private boolean committed;

        PreparedBegin(
            DialogueSessionService owner,
            String playerRef,
            DialogueDefinition definition,
            long beforeRevision
        ){
            this.owner=Objects.requireNonNull(owner,"owner");
            this.playerRef=playerRef;
            this.definition=definition;
            this.beforeRevision=beforeRevision;
        }
    }

    static final class PreparedTransition {
        private final DialogueSessionService owner;
        private final String playerRef;
        private final DialogueDefinition definition;
        private final NodeDefinition node;
        private final Snapshot before;
        private final Transition transition;
        private final NodeDefinition nextNode;
        private boolean committed;

        PreparedTransition(
            DialogueSessionService owner,
            String playerRef,
            DialogueDefinition definition,
            NodeDefinition node,
            Snapshot before,
            Transition transition,
            NodeDefinition nextNode
        ) {
            this.owner =
                Objects.requireNonNull(
                    owner,
                    "owner"
                );
            this.playerRef = playerRef;
            this.definition = definition;
            this.node = node;
            this.before = before;
            this.transition = transition;
            this.nextNode = nextNode;
        }
    }

    @FunctionalInterface
    interface TransitionResolver {
        Transition resolve(
            String playerRef,
            DialogueDefinition definition,
            NodeDefinition currentNode,
            Intent intent,
            Snapshot before
        );
    }

    private static final class PlayerSession {
        final String playerRef;
        boolean active;
        String dialogueKey;
        String nodeKey;
        long revision;

        PlayerSession(
            String playerRef
        ) {
            this.playerRef =
                playerRef;
        }
    }

    private final String policyAuthority;
    private final TransitionResolver resolver;

    private final LinkedHashMap<String, DialogueDefinition>
        definitions =
            new LinkedHashMap<>();

    private final LinkedHashMap<String, PlayerSession>
        players =
            new LinkedHashMap<>();

    DialogueSessionService(
        String policyAuthority,
        TransitionResolver resolver
    ) {
        this.policyAuthority =
            requireLocalPolicy(
                policyAuthority
            );
        this.resolver =
            Objects.requireNonNull(
                resolver,
                "resolver"
            );
    }

    synchronized DialogueDefinition register(
        DialogueDefinition definition
    ) {
        DialogueDefinition checked =
            Objects.requireNonNull(
                definition,
                "definition"
            );

        if (!policyAuthority.equals(
                checked.policyAuthority)) {
            throw new IllegalArgumentException(
                "dialogue authority mismatch key=" +
                checked.dialogueKey
            );
        }

        if (definitions.containsKey(
                checked.dialogueKey)) {
            throw new IllegalStateException(
                "duplicate dialogue definition " +
                checked.dialogueKey
            );
        }

        definitions.put(
            checked.dialogueKey,
            checked
        );

        return checked;
    }

    PreparedBegin prepareBegin(
        String playerRef,
        String dialogueKey
    ){
        String player=
            normalizePlayer(playerRef);
        String key=
            normalizeKey(
                dialogueKey,
                "dialogueKey"
            );

        synchronized(this){
            DialogueDefinition definition=
                definitions.get(key);

            if(definition==null)
                throw new IllegalArgumentException(
                    "unknown dialogue "+key
                );

            PlayerSession existing=
                players.get(player);

            if(existing!=null&&existing.active)
                throw new IllegalStateException(
                    "player already has active dialogue player="+
                    player+
                    " dialogue="+
                    existing.dialogueKey
                );

            return new PreparedBegin(
                this,
                player,
                definition,
                existing==null
                    ?0L
                    :existing.revision
            );
        }
    }

    synchronized Snapshot commitPreparedBegin(
        PreparedBegin prepared
    ){
        PreparedBegin checked=
            Objects.requireNonNull(
                prepared,
                "prepared"
            );

        if(checked.owner!=this)
            throw new IllegalArgumentException(
                "prepared begin belongs to another dialogue service"
            );

        if(checked.committed)
            throw new IllegalStateException(
                "prepared dialogue begin already committed player="+
                checked.playerRef
            );

        PlayerSession state=
            players.get(
                checked.playerRef
            );

        if(state!=null&&
           (state.active||
            state.revision!=
                checked.beforeRevision))
            throw new IllegalStateException(
                "dialogue state changed before prepared begin commit player="+
                checked.playerRef
            );

        if(state==null){
            if(checked.beforeRevision!=0L)
                throw new IllegalStateException(
                    "dialogue state disappeared before prepared begin commit player="+
                    checked.playerRef
                );

            state=
                new PlayerSession(
                    checked.playerRef
                );
            players.put(
                checked.playerRef,
                state
            );
        }

        long nextRevision=
            addOne(
                checked.beforeRevision,
                "dialogue revision"
            );

        state.active=true;
        state.dialogueKey=
            checked.definition.dialogueKey;
        state.nodeKey=
            checked.definition.startNodeKey;
        state.revision=
            nextRevision;
        checked.committed=true;

        return snapshotOf(state);
    }

    synchronized Snapshot begin(
        String playerRef,
        String dialogueKey
    ) {
        String player =
            normalizePlayer(
                playerRef
            );
        String key =
            normalizeKey(
                dialogueKey,
                "dialogueKey"
            );

        DialogueDefinition definition =
            definitions.get(key);

        if (definition == null) {
            throw new IllegalArgumentException(
                "unknown dialogue " +
                key
            );
        }

        PlayerSession existing =
            players.get(player);

        if (existing != null &&
            existing.active) {
            throw new IllegalStateException(
                "player already has active dialogue player=" +
                player +
                " dialogue=" +
                existing.dialogueKey
            );
        }

        long nextRevision =
            addOne(
                existing == null
                    ? 0L
                    : existing.revision,
                "dialogue revision"
            );

        PlayerSession state =
            existing;

        if (state == null) {
            state =
                new PlayerSession(
                    player
                );
            players.put(
                player,
                state
            );
        }

        state.active = true;
        state.dialogueKey =
            definition.dialogueKey;
        state.nodeKey =
            definition.startNodeKey;
        state.revision =
            nextRevision;

        return snapshotOf(
            state
        );
    }

    Snapshot continueDialogue(
        String playerRef
    ) {
        return applyIntent(
            playerRef,
            Intent.continueIntent()
        );
    }

    PreparedTransition prepareContinue(
        String playerRef
    ) {
        return prepareIntent(
            playerRef,
            Intent.continueIntent()
        );
    }

    PreparedTransition prepareClose(
        String playerRef
    ) {
        return prepareIntent(
            playerRef,
            Intent.closeIntent()
        );
    }

    PreparedTransition prepareOption(
        String playerRef,
        int optionIndex
    ) {
        return prepareIntent(
            playerRef,
            Intent.option(
                optionIndex
            )
        );
    }

    Snapshot chooseOption(
        String playerRef,
        int optionIndex
    ) {
        return applyIntent(
            playerRef,
            Intent.option(
                optionIndex
            )
        );
    }

    Snapshot close(
        String playerRef
    ) {
        return applyIntent(
            playerRef,
            Intent.closeIntent()
        );
    }

    /**
     * Server-owned lifecycle termination.
     *
     * Unlike close(...), abort is not a client dialogue intent and therefore
     * does not validate the current node or invoke the transition resolver.
     * Active state is revisioned before being cleared so any resolver already
     * executing outside the service monitor is fenced from committing stale
     * dialogue state.
     *
     * Aborting an already-inactive player is idempotent and does not advance
     * the revision.
     */
    synchronized Snapshot abort(
        String playerRef
    ) {
        String player =
            normalizePlayer(
                playerRef
            );

        PlayerSession state =
            players.get(player);

        if (state == null) {
            return inactiveSnapshot(
                player,
                0L
            );
        }

        if (!state.active) {
            return inactiveSnapshot(
                player,
                state.revision
            );
        }

        state.revision =
            addOne(
                state.revision,
                "dialogue revision"
            );
        state.active = false;
        state.dialogueKey = null;
        state.nodeKey = null;

        return inactiveSnapshot(
            player,
            state.revision
        );
    }

    synchronized Snapshot snapshot(
        String playerRef
    ) {
        String player =
            normalizePlayer(
                playerRef
            );

        PlayerSession state =
            players.get(player);

        if (state == null) {
            return inactiveSnapshot(
                player,
                0L
            );
        }

        return snapshotOf(
            state
        );
    }

    synchronized List<DialogueDefinition>
        catalog() {
        return Collections.unmodifiableList(
            new ArrayList<>(
                definitions.values()
            )
        );
    }

    synchronized int playerStateCount() {
        return players.size();
    }

    private Snapshot applyIntent(
        String playerRef,
        Intent intent
    ) {
        return commitPrepared(
            prepareIntent(
                playerRef,
                intent
            )
        );
    }

    private PreparedTransition prepareIntent(
        String playerRef,
        Intent intent
    ) {
        String player =
            normalizePlayer(
                playerRef
            );
        Intent checkedIntent =
            Objects.requireNonNull(
                intent,
                "intent"
            );

        final DialogueDefinition definition;
        final NodeDefinition node;
        final Snapshot before;

        /*
         * Capture one exact semantic session state while holding the service
         * monitor. Caller-owned transition policy runs only after release.
         * The returned prepared transition does not mutate session state.
         */
        synchronized (this) {
            PlayerSession state =
                players.get(player);

            if (state == null ||
                !state.active) {
                throw new IllegalStateException(
                    "no active dialogue player=" +
                    player
                );
            }

            definition =
                definitions.get(
                    state.dialogueKey
                );

            if (definition == null) {
                throw new IllegalStateException(
                    "active dialogue definition disappeared " +
                    state.dialogueKey
                );
            }

            node =
                definition.node(
                    state.nodeKey
                );

            if (node == null) {
                throw new IllegalStateException(
                    "active dialogue node disappeared " +
                    state.nodeKey
                );
            }

            validateIntent(
                node,
                checkedIntent
            );

            before =
                snapshotOf(
                    state
                );
        }

        Transition transition =
            Objects.requireNonNull(
                resolver.resolve(
                    player,
                    definition,
                    node,
                    checkedIntent,
                    before
                ),
                "transition"
            );

        NodeDefinition nextNode =
            validateTransition(
                definition,
                transition
            );

        return new PreparedTransition(
            this,
            player,
            definition,
            node,
            before,
            transition,
            nextNode
        );
    }

    synchronized Snapshot commitPrepared(
        PreparedTransition prepared
    ) {
        PreparedTransition checked =
            Objects.requireNonNull(
                prepared,
                "prepared"
            );

        if (checked.owner != this)
            throw new IllegalArgumentException(
                "prepared transition belongs to another dialogue service"
            );

        if (checked.committed)
            throw new IllegalStateException(
                "prepared dialogue transition already committed player=" +
                checked.playerRef
            );

        PlayerSession state =
            players.get(
                checked.playerRef
            );

        if (state == null ||
            !state.active ||
            state.revision !=
                checked.before.revision ||
            !checked.definition.dialogueKey
                .equals(
                    state.dialogueKey
                ) ||
            !checked.node.nodeKey.equals(
                state.nodeKey
            )) {
            throw new IllegalStateException(
                "dialogue state changed before prepared transition commit player=" +
                checked.playerRef
            );
        }

        if (checked.transition.kind ==
                TransitionKind.STAY) {
            checked.committed = true;
            return snapshotOf(
                state
            );
        }

        long nextRevision =
            addOne(
                state.revision,
                "dialogue revision"
            );

        if (checked.transition.kind ==
                TransitionKind.END) {
            state.active = false;
            state.dialogueKey = null;
            state.nodeKey = null;
            state.revision =
                nextRevision;
            checked.committed = true;

            return inactiveSnapshot(
                checked.playerRef,
                nextRevision
            );
        }

        state.nodeKey =
            checked.nextNode.nodeKey;
        state.revision =
            nextRevision;
        checked.committed = true;

        return snapshotOf(
            state
        );
    }

    private static void validateIntent(
        NodeDefinition node,
        Intent intent
    ) {
        if (intent.kind ==
                IntentKind.CONTINUE) {
            if (node.inputMode !=
                    InputMode.CONTINUE) {
                throw new IllegalStateException(
                    "dialogue node does not accept Continue node=" +
                    node.nodeKey
                );
            }
            return;
        }

        if (intent.kind ==
                IntentKind.OPTION) {
            if (node.inputMode !=
                    InputMode.OPTIONS) {
                throw new IllegalStateException(
                    "dialogue node does not accept options node=" +
                    node.nodeKey
                );
            }

            if (intent.optionIndex < 1 ||
                intent.optionIndex >
                    node.optionCount) {
                throw new IllegalArgumentException(
                    "dialogue option out of node range option=" +
                    intent.optionIndex +
                    " count=" +
                    node.optionCount
                );
            }
            return;
        }

        if (node.inputMode !=
                InputMode.OPTIONS ||
            !node.closeSupported) {
            throw new IllegalStateException(
                "dialogue node does not expose semantic close/cancel node=" +
                node.nodeKey
            );
        }
    }

    private static NodeDefinition
        validateTransition(
            DialogueDefinition definition,
            Transition transition
        ) {
        if (transition.kind ==
                TransitionKind.STAY) {
            if (transition.nextNodeKey !=
                    null) {
                throw new IllegalArgumentException(
                    "STAY cannot carry next node"
                );
            }
            return null;
        }

        if (transition.kind ==
                TransitionKind.END) {
            if (transition.nextNodeKey !=
                    null) {
                throw new IllegalArgumentException(
                    "END cannot carry next node"
                );
            }
            return null;
        }

        if (transition.nextNodeKey ==
                null) {
            throw new IllegalArgumentException(
                "MOVE requires next node"
            );
        }

        NodeDefinition next =
            definition.node(
                transition.nextNodeKey
            );

        if (next == null) {
            throw new IllegalArgumentException(
                "unknown dialogue transition target " +
                transition.nextNodeKey
            );
        }

        return next;
    }

    private Snapshot snapshotOf(
        PlayerSession state
    ) {
        if (!state.active) {
            return inactiveSnapshot(
                state.playerRef,
                state.revision
            );
        }

        DialogueDefinition definition =
            definitions.get(
                state.dialogueKey
            );

        if (definition == null) {
            throw new IllegalStateException(
                "active dialogue definition disappeared " +
                state.dialogueKey
            );
        }

        NodeDefinition node =
            definition.node(
                state.nodeKey
            );

        if (node == null) {
            throw new IllegalStateException(
                "active dialogue node disappeared " +
                state.nodeKey
            );
        }

        return new Snapshot(
            state.playerRef,
            true,
            definition.dialogueKey,
            node.nodeKey,
            node.inputMode,
            node.optionCount,
            node.closeSupported,
            state.revision,
            policyAuthority
        );
    }

    private Snapshot inactiveSnapshot(
        String player,
        long revision
    ) {
        return new Snapshot(
            player,
            false,
            null,
            null,
            null,
            0,
            false,
            revision,
            policyAuthority
        );
    }

    private static long addOne(
        long value,
        String field
    ) {
        try {
            return Math.addExact(
                value,
                1L
            );
        } catch (
            ArithmeticException error
        ) {
            throw new IllegalStateException(
                field +
                " overflow",
                error
            );
        }
    }

    private static String normalizePlayer(
        String value
    ) {
        return requireText(
            value,
            "playerRef"
        ).toLowerCase(
            Locale.ROOT
        );
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
                "dialogue policy must be LOCAL_LAB_POLICY_* actual=" +
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
