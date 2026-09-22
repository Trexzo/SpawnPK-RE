package spk.local;

import java.util.*;

/**
 * Protocol-independent semantic privilege/rank state for player appearance.
 *
 * Exact-current client proves a packet-81 privilege-like field exists, but the
 * original named-rank -> numeric client mapping remains unknown. This service
 * therefore stores semantic privilege identity only.
 */
final class PlayerPrivilegeService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final class Definition {
        final String privilegeKey;
        final String displayName;
        final AtomicTransactionService.SourceAuthority
            sourceAuthority;

        Definition(
            String privilegeKey,
            String displayName,
            AtomicTransactionService.SourceAuthority
                sourceAuthority
        ){
            this.privilegeKey=
                normalizeKey(
                    privilegeKey,
                    "privilegeKey"
                );
            this.displayName=
                requireText(
                    displayName,
                    "displayName"
                );
            this.sourceAuthority=
                Objects.requireNonNull(
                    sourceAuthority,
                    "sourceAuthority"
                );
        }
    }

    static final class PlayerSnapshot {
        final String playerRef;
        final Definition privilege;
        final long revision;
        final AtomicTransactionService.SourceAuthority
            policyAuthority;
        final String presentationAuthority;

        PlayerSnapshot(
            String playerRef,
            Definition privilege,
            long revision,
            AtomicTransactionService.SourceAuthority
                policyAuthority
        ){
            this.playerRef=playerRef;
            this.privilege=privilege;
            this.revision=revision;
            this.policyAuthority=
                policyAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        boolean assigned(){
            return privilege!=null;
        }
    }

    static final class CatalogSnapshot {
        final List<Definition> definitions;
        final AtomicTransactionService.SourceAuthority
            policyAuthority;
        final String presentationAuthority;

        CatalogSnapshot(
            Collection<Definition> definitions,
            AtomicTransactionService.SourceAuthority
                policyAuthority
        ){
            this.definitions=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        definitions
                    )
                );
            this.policyAuthority=
                policyAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }
    }

    private static final class PlayerAssignment {
        final String playerRef;
        String privilegeKey;
        long revision;

        PlayerAssignment(
            String playerRef
        ){
            this.playerRef=playerRef;
        }
    }

    private final AtomicTransactionService.SourceAuthority
        policyAuthority;

    private final LinkedHashMap<String,Definition>
        definitions=
            new LinkedHashMap<>();

    private final LinkedHashMap<String,PlayerAssignment>
        assignments=
            new LinkedHashMap<>();

    PlayerPrivilegeService(
        AtomicTransactionService.SourceAuthority
            policyAuthority
    ){
        this.policyAuthority=
            Objects.requireNonNull(
                policyAuthority,
                "policyAuthority"
            );

        if(policyAuthority!=
                AtomicTransactionService
                    .SourceAuthority
                    .CUSTOM_LOCALLAB)
            throw new IllegalArgumentException(
                "player privilege first policy requires CUSTOM_LOCALLAB authority actual="+
                policyAuthority
            );
    }

    synchronized Definition registerDefinition(
        Definition definition
    ){
        Definition checked=
            Objects.requireNonNull(
                definition,
                "definition"
            );

        if(checked.sourceAuthority!=
                policyAuthority)
            throw new IllegalArgumentException(
                "player privilege definition authority mismatch key="+
                checked.privilegeKey+
                " actual="+
                checked.sourceAuthority+
                " expected="+
                policyAuthority
            );

        if(definitions.containsKey(
                checked.privilegeKey))
            throw new IllegalStateException(
                "duplicate player privilege definition "+
                checked.privilegeKey
            );

        definitions.put(
            checked.privilegeKey,
            checked
        );

        return checked;
    }

    synchronized PlayerSnapshot assign(
        String playerRef,
        String privilegeKey
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        String key=
            normalizeKey(
                privilegeKey,
                "privilegeKey"
            );

        Definition definition=
            definitions.get(key);

        if(definition==null)
            throw new IllegalArgumentException(
                "unknown player privilege "+
                key
            );

        PlayerAssignment current=
            assignments.get(player);

        if(current!=null&&
           key.equals(
               current.privilegeKey))
            return snapshotOf(
                player,
                current
            );

        long nextRevision=
            addOne(
                current==null
                    ?0L
                    :current.revision,
                "player privilege revision"
            );

        PlayerAssignment state=current;

        if(state==null){
            state=
                new PlayerAssignment(
                    player
                );
            assignments.put(
                player,
                state
            );
        }

        state.privilegeKey=key;
        state.revision=nextRevision;

        return snapshotOf(
            player,
            state
        );
    }

    synchronized PlayerSnapshot clear(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        PlayerAssignment state=
            assignments.get(player);

        if(state==null)
            return new PlayerSnapshot(
                player,
                null,
                0L,
                policyAuthority
            );

        if(state.privilegeKey==null)
            return snapshotOf(
                player,
                state
            );

        state.revision=
            addOne(
                state.revision,
                "player privilege revision"
            );
        state.privilegeKey=null;

        return snapshotOf(
            player,
            state
        );
    }

    synchronized PlayerSnapshot snapshot(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        PlayerAssignment state=
            assignments.get(player);

        if(state==null)
            return new PlayerSnapshot(
                player,
                null,
                0L,
                policyAuthority
            );

        return snapshotOf(
            player,
            state
        );
    }

    synchronized CatalogSnapshot catalog(){
        return new CatalogSnapshot(
            definitions.values(),
            policyAuthority
        );
    }

    synchronized int definitionCount(){
        return definitions.size();
    }

    synchronized int playerStateCount(){
        return assignments.size();
    }

    private PlayerSnapshot snapshotOf(
        String player,
        PlayerAssignment state
    ){
        Definition definition=
            state.privilegeKey==null
                ?null
                :definitions.get(
                    state.privilegeKey
                );

        if(state.privilegeKey!=null&&
           definition==null)
            throw new IllegalStateException(
                "player privilege definition disappeared key="+
                state.privilegeKey
            );

        return new PlayerSnapshot(
            player,
            definition,
            state.revision,
            policyAuthority
        );
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

    private static String normalizeKey(
        String value,
        String field
    ){
        String clean=
            requireText(
                value,
                field
            ).toLowerCase(
                Locale.ROOT
            );

        if(clean.length()>160)
            throw new IllegalArgumentException(
                field+" too long"
            );

        return clean;
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
