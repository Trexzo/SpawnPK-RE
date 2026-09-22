package spk.local;

import java.util.*;

/**
 * Exact-current top-level teleport/navigation concepts with runtime-owned
 * execution. Coordinates, destination catalogues and eligibility rules remain
 * external server policy.
 */
final class TeleportNavigationService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum EntryKind {
        HOME,
        MONEY,
        TRAINING,
        BOSS,
        PK,
        MINIGAME,
        HOUSE,
        BOUNTY
    }

    static final class Entry {
        final EntryKind kind;
        final String displayName;
        final String description;
        final String presentationAuthority;

        private Entry(
            EntryKind kind,
            String displayName,
            String description
        ){
            this.kind=
                Objects.requireNonNull(
                    kind,
                    "kind"
                );
            this.displayName=
                requireText(
                    displayName,
                    "displayName"
                );
            this.description=
                requireText(
                    description,
                    "description"
                );
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }
    }

    static final class ExecutionResult {
        final boolean succeeded;
        final String detail;

        private ExecutionResult(
            boolean succeeded,
            String detail
        ){
            this.succeeded=succeeded;
            this.detail=
                detail==null
                    ?""
                    :detail.trim();
        }

        static ExecutionResult success(){
            return new ExecutionResult(
                true,
                ""
            );
        }

        static ExecutionResult success(
            String detail
        ){
            return new ExecutionResult(
                true,
                detail
            );
        }

        static ExecutionResult failure(
            String detail
        ){
            return new ExecutionResult(
                false,
                requireText(
                    detail,
                    "detail"
                )
            );
        }
    }

    interface Executor {
        ExecutionResult execute(
            String playerRef,
            EntryKind entry,
            String policyAuthority
        );
    }

    static final class PlayerSnapshot {
        final String playerRef;
        final Map<EntryKind,Long>
            successfulRequests;
        final long totalSuccessfulRequests;

        PlayerSnapshot(
            String playerRef,
            EnumMap<EntryKind,Long> counts,
            long total
        ){
            this.playerRef=playerRef;

            EnumMap<EntryKind,Long> copy=
                new EnumMap<>(
                    EntryKind.class
                );

            for(EntryKind kind:
                    EntryKind.values())
                copy.put(
                    kind,
                    counts.getOrDefault(
                        kind,
                        0L
                    )
                );

            this.successfulRequests=
                Collections.unmodifiableMap(
                    copy
                );
            this.totalSuccessfulRequests=
                total;
        }

        long successful(
            EntryKind kind
        ){
            return successfulRequests.get(
                Objects.requireNonNull(
                    kind,
                    "kind"
                )
            );
        }
    }

    static final class RequestResult {
        final Entry entry;
        final boolean executedSuccessfully;
        final String detail;
        final PlayerSnapshot player;

        RequestResult(
            Entry entry,
            ExecutionResult execution,
            PlayerSnapshot player
        ){
            this.entry=
                Objects.requireNonNull(
                    entry,
                    "entry"
                );
            this.executedSuccessfully=
                execution.succeeded;
            this.detail=execution.detail;
            this.player=
                Objects.requireNonNull(
                    player,
                    "player"
                );
        }
    }

    private static final class PlayerState {
        final String playerRef;
        final EnumMap<EntryKind,Long>
            successfulRequests=
                new EnumMap<>(
                    EntryKind.class
                );

        long totalSuccessfulRequests;

        PlayerState(String playerRef){
            this.playerRef=playerRef;
        }
    }

    private static final List<Entry> ENTRIES=
        buildEntries();

    private static final EnumMap<EntryKind,Entry>
        BY_KIND=
            indexEntries();

    private final Executor executor;
    private final String policyAuthority;
    private final LinkedHashMap<String,PlayerState>
        players=
            new LinkedHashMap<>();

    TeleportNavigationService(
        Executor executor,
        String policyAuthority
    ){
        this.executor=
            Objects.requireNonNull(
                executor,
                "executor"
            );
        this.policyAuthority=
            localPolicyAuthority(
                policyAuthority
            );
    }

    static List<Entry> entries(){
        return ENTRIES;
    }

    RequestResult request(
        String playerRef,
        EntryKind kind
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        EntryKind checked=
            Objects.requireNonNull(
                kind,
                "kind"
            );
        Entry entry=
            BY_KIND.get(checked);

        if(entry==null)
            throw new IllegalStateException(
                "missing teleport navigation entry "+
                checked
            );

        /*
         * Runtime navigation is caller-owned. Execute it without holding the
         * semantic request-ledger monitor, then commit success accounting from
         * the latest ledger state so concurrent successful requests cannot
         * overwrite each other.
         */
        ExecutionResult execution=
            Objects.requireNonNull(
                executor.execute(
                    player,
                    checked,
                    policyAuthority
                ),
                "execution result"
            );

        synchronized(this){
            PlayerState state=
                players.get(player);

            if(!execution.succeeded)
                return new RequestResult(
                    entry,
                    execution,
                    snapshotOf(
                        player,
                        state
                    )
                );

            if(state==null){
                state=
                    new PlayerState(
                        player
                    );
                players.put(
                    player,
                    state
                );
            }

            long nextEntry=
                addOne(
                    state.successfulRequests
                        .getOrDefault(
                            checked,
                            0L
                        ),
                    "entry success count"
                );
            long nextTotal=
                addOne(
                    state.totalSuccessfulRequests,
                    "total success count"
                );

            state.successfulRequests.put(
                checked,
                nextEntry
            );
            state.totalSuccessfulRequests=
                nextTotal;

            return new RequestResult(
                entry,
                execution,
                snapshotOf(
                    player,
                    state
                )
            );
        }
    }

    synchronized PlayerSnapshot snapshot(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        return snapshotOf(
            player,
            players.get(player)
        );
    }

    synchronized int playerCount(){
        return players.size();
    }

    private static PlayerSnapshot snapshotOf(
        String playerRef,
        PlayerState state
    ){
        if(state==null)
            return new PlayerSnapshot(
                playerRef,
                new EnumMap<>(
                    EntryKind.class
                ),
                0L
            );

        return new PlayerSnapshot(
            state.playerRef,
            state.successfulRequests,
            state.totalSuccessfulRequests
        );
    }

    private static List<Entry> buildEntries(){
        ArrayList<Entry> entries=
            new ArrayList<>();

        entries.add(
            new Entry(
                EntryKind.HOME,
                "Home Teleport",
                "Cast Home Teleport"
            )
        );
        entries.add(
            new Entry(
                EntryKind.MONEY,
                "Money Making",
                "Teleport to money areas"
            )
        );
        entries.add(
            new Entry(
                EntryKind.TRAINING,
                "Training & Slayer",
                "Teleport to various monsters"
            )
        );
        entries.add(
            new Entry(
                EntryKind.BOSS,
                "Boss Teleports",
                "Teleport to powerful foes"
            )
        );
        entries.add(
            new Entry(
                EntryKind.PK,
                "PK Teleports",
                "Teleport Pking spots"
            )
        );
        entries.add(
            new Entry(
                EntryKind.MINIGAME,
                "Minigame Teleport",
                "Teleport to shop areas"
            )
        );
        entries.add(
            new Entry(
                EntryKind.HOUSE,
                "Teleport to House",
                "Teleport to your PoH"
            )
        );
        entries.add(
            new Entry(
                EntryKind.BOUNTY,
                "Teleport to Target",
                "Teleports you to Bounty Target"
            )
        );

        return Collections.unmodifiableList(
            entries
        );
    }

    private static EnumMap<EntryKind,Entry>
        indexEntries()
    {
        EnumMap<EntryKind,Entry> out=
            new EnumMap<>(
                EntryKind.class
            );

        for(Entry entry:ENTRIES){
            if(out.put(
                    entry.kind,
                    entry)!=null)
                throw new IllegalStateException(
                    "duplicate teleport navigation entry "+
                    entry.kind
                );
        }

        if(out.size()!=
                EntryKind.values().length)
            throw new IllegalStateException(
                "incomplete teleport navigation catalog"
            );

        return out;
    }

    private static long addOne(
        long value,
        String field
    ){
        try{
            return Math.addExact(
                value,
                1L
            );
        }catch(ArithmeticException error){
            throw new IllegalStateException(
                field+" overflow",
                error
            );
        }
    }

    private static String normalizePlayer(
        String value
    ){
        return requireText(
            value,
            "playerRef"
        ).toLowerCase(
            Locale.ROOT
        );
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(
                field
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }

    private static String localPolicyAuthority(
        String value
    ){
        String authority=
            requireText(
                value,
                "policyAuthority"
            );

        if(!authority
                .toUpperCase(
                    Locale.ROOT
                )
                .startsWith(
                    "LOCAL_LAB_POLICY_"))
            throw new IllegalArgumentException(
                "teleport navigation requires explicit LOCAL_LAB_POLICY_* authority actual="+
                authority
            );

        return authority;
    }
}
