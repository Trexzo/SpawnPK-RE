package spk.local;

import java.util.*;

/**
 * Exact-current Blood Fountain hub navigation with runtime-owned execution.
 *
 * Target application mechanics remain owned by their dedicated services.
 */
final class BloodFountainHubService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum Intent {
        PERK_TREE,
        BLOOD_POOL_STORE,
        BLOOD_DIAMOND_FUSER,
        BLOOD_DIAMOND_STORE,
        BLOOD_SHARD_SALVAGING,
        BLOOD_SHARD_STORE
    }

    static final class Entry {
        final Intent intent;
        final String displayName;
        final String presentationAuthority;

        private Entry(
            Intent intent,
            String displayName
        ){
            this.intent=
                Objects.requireNonNull(
                    intent,
                    "intent"
                );
            this.displayName=
                requireText(
                    displayName,
                    "displayName"
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
            Intent intent,
            String policyAuthority
        );
    }

    static final class PlayerSnapshot {
        final String playerRef;
        final Map<Intent,Long>
            successfulRequests;
        final long totalSuccessfulRequests;

        PlayerSnapshot(
            String playerRef,
            EnumMap<Intent,Long> counts,
            long total
        ){
            this.playerRef=playerRef;

            EnumMap<Intent,Long> copy=
                new EnumMap<>(
                    Intent.class
                );

            for(Intent intent:
                    Intent.values())
                copy.put(
                    intent,
                    counts.getOrDefault(
                        intent,
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

        long successful(Intent intent){
            return successfulRequests.get(
                Objects.requireNonNull(
                    intent,
                    "intent"
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
        final EnumMap<Intent,Long>
            successfulRequests=
                new EnumMap<>(
                    Intent.class
                );
        long totalSuccessfulRequests;

        PlayerState(String playerRef){
            this.playerRef=playerRef;
        }
    }

    private static final List<Entry> ENTRIES=
        buildEntries();

    private static final EnumMap<Intent,Entry>
        BY_INTENT=
            indexEntries();

    private final Executor executor;
    private final String policyAuthority;
    private final LinkedHashMap<String,PlayerState>
        players=
            new LinkedHashMap<>();

    BloodFountainHubService(
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
        Intent intent
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        Intent checked=
            Objects.requireNonNull(
                intent,
                "intent"
            );
        Entry entry=
            BY_INTENT.get(checked);

        if(entry==null)
            throw new IllegalStateException(
                "missing Blood Fountain hub intent "+
                checked
            );

        /*
         * Caller-owned runtime execution runs without the semantic ledger
         * monitor. Successful accounting is committed afterward from current
         * state so concurrent successful requests cannot lose increments.
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

            long nextIntent=
                addOne(
                    state.successfulRequests
                        .getOrDefault(
                            checked,
                            0L
                        ),
                    "intent success count"
                );
            long nextTotal=
                addOne(
                    state.totalSuccessfulRequests,
                    "total success count"
                );

            state.successfulRequests.put(
                checked,
                nextIntent
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
                    Intent.class
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
                Intent.PERK_TREE,
                "Blood perk tree"
            )
        );
        entries.add(
            new Entry(
                Intent.BLOOD_POOL_STORE,
                "Blood pool store"
            )
        );
        entries.add(
            new Entry(
                Intent.BLOOD_DIAMOND_FUSER,
                "Blood diamond fuser"
            )
        );
        entries.add(
            new Entry(
                Intent.BLOOD_DIAMOND_STORE,
                "Blood diamond store"
            )
        );
        entries.add(
            new Entry(
                Intent.BLOOD_SHARD_SALVAGING,
                "Blood shard salvaging"
            )
        );
        entries.add(
            new Entry(
                Intent.BLOOD_SHARD_STORE,
                "Blood shard store"
            )
        );

        return Collections.unmodifiableList(
            entries
        );
    }

    private static EnumMap<Intent,Entry>
        indexEntries()
    {
        EnumMap<Intent,Entry> out=
            new EnumMap<>(
                Intent.class
            );

        for(Entry entry:ENTRIES){
            if(out.put(
                    entry.intent,
                    entry)!=null)
                throw new IllegalStateException(
                    "duplicate Blood Fountain hub intent "+
                    entry.intent
                );
        }

        if(out.size()!=
                Intent.values().length)
            throw new IllegalStateException(
                "incomplete Blood Fountain hub catalog"
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
                "Blood Fountain hub requires explicit LOCAL_LAB_POLICY_* authority actual="+
                authority
            );

        return authority;
    }
}
