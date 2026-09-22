package spk.local;

import java.util.*;

/**
 * Concrete Boss Teleportation Network application coordinator.
 *
 * Exact-current client evidence proves 13 selectable boss rows and a teleport
 * intent, but coordinates, restrictions and real drop tables remain external.
 * This service therefore owns only semantic catalog/selection state and delegates
 * actual movement to an injected runtime executor.
 */
final class BossTeleportService {
    static final int MAX_ROWS=13;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final class Entry {
        final String bossKey;
        final String displayName;
        final String description;
        final String teleportTargetKey;
        final String fullDropTableKey;
        final List<String> details;
        final String policyAuthority;

        Entry(
            String bossKey,
            String displayName,
            String description,
            String teleportTargetKey,
            String fullDropTableKey,
            Collection<String> details,
            String policyAuthority
        ){
            this.bossKey=
                MatchRules.normalizeKey(
                    bossKey,
                    "bossKey"
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
            this.teleportTargetKey=
                MatchRules.normalizeKey(
                    teleportTargetKey,
                    "teleportTargetKey"
                );

            this.fullDropTableKey=
                fullDropTableKey==null
                    ?null
                    :MatchRules.normalizeKey(
                        fullDropTableKey,
                        "fullDropTableKey"
                    );

            Objects.requireNonNull(
                details,
                "details"
            );

            ArrayList<String> copy=
                new ArrayList<>();

            for(String detail:details)
                copy.add(
                    requireText(
                        detail,
                        "detail"
                    )
                );

            this.details=
                Collections.unmodifiableList(
                    copy
                );

            this.policyAuthority=
                requireText(
                    policyAuthority,
                    "policyAuthority"
                );
        }
    }

    static final class EligibilityDecision {
        final boolean allowed;
        final String reason;

        private EligibilityDecision(
            boolean allowed,
            String reason
        ){
            this.allowed=allowed;
            this.reason=
                reason==null
                    ?""
                    :reason.trim();
        }

        static EligibilityDecision allow(){
            return new EligibilityDecision(
                true,
                ""
            );
        }

        static EligibilityDecision deny(
            String reason
        ){
            return new EligibilityDecision(
                false,
                requireText(
                    reason,
                    "reason"
                )
            );
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

    interface EligibilityValidator {
        EligibilityDecision validate(
            String playerRef,
            Entry entry
        );
    }

    interface TeleportExecutor {
        ExecutionResult execute(
            String playerRef,
            String teleportTargetKey,
            String policyAuthority
        );
    }

    static final class PlayerSnapshot {
        final String playerRef;
        final String selectedBossKey;
        final String selectedTeleportTargetKey;
        final String selectedFullDropTableKey;
        final long successfulTeleports;

        PlayerSnapshot(
            PlayerState state,
            Entry selected
        ){
            this.playerRef=
                state.playerRef;
            this.selectedBossKey=
                state.selectedBossKey;
            this.selectedTeleportTargetKey=
                selected==null
                    ?null
                    :selected.teleportTargetKey;
            this.selectedFullDropTableKey=
                selected==null
                    ?null
                    :selected.fullDropTableKey;
            this.successfulTeleports=
                state.successfulTeleports;
        }

        boolean hasSelection(){
            return selectedBossKey!=null;
        }
    }

    static final class Snapshot {
        final List<Entry> entries;
        final List<PlayerSnapshot> players;
        final String presentationAuthority;

        Snapshot(
            Collection<Entry> entries,
            Collection<PlayerSnapshot> players
        ){
            this.entries=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        entries
                    )
                );
            this.players=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        players
                    )
                );
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        Entry boss(String bossKey){
            String key=
                MatchRules.normalizeKey(
                    bossKey,
                    "bossKey"
                );

            for(Entry entry:entries)
                if(entry.bossKey.equals(key))
                    return entry;

            return null;
        }
    }

    static final class TeleportRequestResult {
        final boolean eligibilityAllowed;
        final boolean executedSuccessfully;
        final String detail;
        final Entry entry;
        final PlayerSnapshot player;

        TeleportRequestResult(
            boolean eligibilityAllowed,
            boolean executedSuccessfully,
            String detail,
            Entry entry,
            PlayerSnapshot player
        ){
            this.eligibilityAllowed=
                eligibilityAllowed;
            this.executedSuccessfully=
                executedSuccessfully;
            this.detail=
                detail==null
                    ?""
                    :detail;
            this.entry=entry;
            this.player=player;
        }
    }

    private static final class PlayerState {
        final String playerRef;
        String selectedBossKey;
        long successfulTeleports;

        PlayerState(String playerRef){
            this.playerRef=playerRef;
        }
    }

    private final EligibilityValidator eligibilityValidator;
    private final TeleportExecutor teleportExecutor;

    private LinkedHashMap<String,Entry>
        entries=
            new LinkedHashMap<>();

    private final LinkedHashMap<String,PlayerState>
        players=
            new LinkedHashMap<>();

    BossTeleportService(
        EligibilityValidator eligibilityValidator,
        TeleportExecutor teleportExecutor
    ){
        this.eligibilityValidator=
            Objects.requireNonNull(
                eligibilityValidator,
                "eligibilityValidator"
            );
        this.teleportExecutor=
            Objects.requireNonNull(
                teleportExecutor,
                "teleportExecutor"
            );
    }

    synchronized Snapshot replaceCatalog(
        Collection<Entry> catalog
    ){
        Objects.requireNonNull(
            catalog,
            "catalog"
        );

        if(catalog.size()>MAX_ROWS)
            throw new IllegalArgumentException(
                "Boss Teleport rows="+
                catalog.size()+
                " max="+MAX_ROWS
            );

        LinkedHashMap<String,Entry>
            next=
                new LinkedHashMap<>();

        HashSet<String> targets=
            new HashSet<>();

        for(Entry entry:catalog){
            Entry checked=
                Objects.requireNonNull(
                    entry,
                    "entry"
                );

            if(next.put(
                    checked.bossKey,
                    checked)!=null)
                throw new IllegalArgumentException(
                    "duplicate Boss Teleport bossKey "+
                    checked.bossKey
                );

            if(!targets.add(
                    checked.teleportTargetKey))
                throw new IllegalArgumentException(
                    "duplicate Boss Teleport target "+
                    checked.teleportTargetKey
                );
        }

        for(PlayerState player:
                players.values())
            if(player.selectedBossKey!=null&&
               !next.containsKey(
                    player.selectedBossKey))
                throw new IllegalStateException(
                    "catalog replacement removes selected boss "+
                    player.selectedBossKey+
                    " player="+
                    player.playerRef
                );

        entries=next;
        return snapshot();
    }

    synchronized PlayerSnapshot selectBoss(
        String playerRef,
        String bossKey
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        String key=
            MatchRules.normalizeKey(
                bossKey,
                "bossKey"
            );

        Entry entry=
            entries.get(key);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown Boss Teleport boss "+
                key
            );

        PlayerState state=
            players.computeIfAbsent(
                player,
                PlayerState::new
            );

        state.selectedBossKey=
            entry.bossKey;

        return snapshotOf(state);
    }

    synchronized PlayerSnapshot clearSelection(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        PlayerState state=
            players.computeIfAbsent(
                player,
                PlayerState::new
            );

        state.selectedBossKey=null;

        return snapshotOf(state);
    }

    TeleportRequestResult
        requestTeleport(
            String playerRef
        ){
        String player=
            normalizePlayer(
                playerRef
            );

        final Entry entry;

        /*
         * Capture one immutable semantic selection while holding local state,
         * then release the monitor before caller-owned eligibility/movement.
         */
        synchronized(this){
            PlayerState state=
                players.get(player);

            if(state==null||
               state.selectedBossKey==null)
                throw new IllegalStateException(
                    "Boss Teleport selection missing player="+
                    player
                );

            entry=
                entries.get(
                    state.selectedBossKey
                );

            if(entry==null)
                throw new IllegalStateException(
                    "selected Boss Teleport entry disappeared "+
                    state.selectedBossKey
                );
        }

        EligibilityDecision eligibility=
            Objects.requireNonNull(
                eligibilityValidator
                    .validate(
                        player,
                        entry
                    ),
                "eligibility decision"
            );

        if(!eligibility.allowed){
            synchronized(this){
                PlayerState current=
                    players.get(player);

                return new TeleportRequestResult(
                    false,
                    false,
                    eligibility.reason,
                    entry,
                    snapshotOf(current)
                );
            }
        }

        ExecutionResult execution=
            Objects.requireNonNull(
                teleportExecutor.execute(
                    player,
                    entry.teleportTargetKey,
                    entry.policyAuthority
                ),
                "teleport execution result"
            );

        synchronized(this){
            PlayerState current=
                players.get(player);

            if(!execution.succeeded)
                return new TeleportRequestResult(
                    true,
                    false,
                    execution.detail,
                    entry,
                    snapshotOf(current)
                );

            current.successfulTeleports=
                Math.addExact(
                    current.successfulTeleports,
                    1L
                );

            return new TeleportRequestResult(
                true,
                true,
                execution.detail,
                entry,
                snapshotOf(current)
            );
        }
    }

    synchronized PlayerSnapshot getPlayer(
        String playerRef
    ){
        PlayerState state=
            players.get(
                normalizePlayer(
                    playerRef
                )
            );

        return state==null
            ?null
            :snapshotOf(state);
    }

    synchronized Entry getBoss(
        String bossKey
    ){
        return entries.get(
            MatchRules.normalizeKey(
                bossKey,
                "bossKey"
            )
        );
    }

    synchronized Snapshot snapshot(){
        ArrayList<PlayerSnapshot>
            playerSnapshots=
                new ArrayList<>();

        ArrayList<PlayerState>
            orderedPlayers=
                new ArrayList<>(
                    players.values()
                );

        orderedPlayers.sort(
            Comparator.comparing(
                value->
                    value.playerRef
            )
        );

        for(PlayerState state:
                orderedPlayers)
            playerSnapshots.add(
                snapshotOf(state)
            );

        return new Snapshot(
            entries.values(),
            playerSnapshots
        );
    }

    synchronized int size(){
        return entries.size();
    }

    private PlayerSnapshot snapshotOf(
        PlayerState state
    ){
        Entry selected=
            state.selectedBossKey==null
                ?null
                :entries.get(
                    state.selectedBossKey
                );

        return new PlayerSnapshot(
            state,
            selected
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
}
