package spk.local;

import java.util.*;

/**
 * Semantic PK Ratings application state for the exact-current 50-row feed.
 *
 * Rating formulas, score calculation, sort/reset policy, rewards and transport
 * remain external authority.
 */
final class PkRatingsService {
    static final int MAX_ROWS=50;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum Navigation {
        DAILY_PK,
        TOURNAMENT_PK
    }

    static final class Row {
        final String rowKey;
        final String displayText;
        final boolean selectable;
        final AtomicTransactionService.SourceAuthority
            sourceAuthority;

        Row(
            String rowKey,
            String displayText,
            boolean selectable,
            AtomicTransactionService.SourceAuthority
                sourceAuthority
        ){
            this.rowKey=
                normalizeKey(
                    rowKey,
                    "rowKey"
                );
            this.displayText=
                requireText(
                    displayText,
                    "displayText"
                );
            this.selectable=selectable;
            this.sourceAuthority=
                Objects.requireNonNull(
                    sourceAuthority,
                    "sourceAuthority"
                );
        }

        Row withDisplayText(
            String nextText
        ){
            return new Row(
                rowKey,
                nextText,
                selectable,
                sourceAuthority
            );
        }
    }

    static final class ActionResult {
        final boolean succeeded;
        final String detail;

        private ActionResult(
            boolean succeeded,
            String detail
        ){
            this.succeeded=succeeded;
            this.detail=
                detail==null
                    ?""
                    :detail.trim();
        }

        static ActionResult success(){
            return new ActionResult(
                true,
                ""
            );
        }

        static ActionResult success(
            String detail
        ){
            return new ActionResult(
                true,
                detail
            );
        }

        static ActionResult failure(
            String detail
        ){
            return new ActionResult(
                false,
                requireText(
                    detail,
                    "detail"
                )
            );
        }
    }

    interface SelectionExecutor {
        ActionResult select(
            String playerRef,
            Row row,
            AtomicTransactionService.SourceAuthority
                policyAuthority
        );
    }

    interface NavigationExecutor {
        ActionResult navigate(
            String playerRef,
            Navigation navigation,
            AtomicTransactionService.SourceAuthority
                policyAuthority
        );
    }

    static final class Snapshot {
        final List<Row> rows;
        final long datasetRevision;
        final AtomicTransactionService.SourceAuthority
            policyAuthority;
        final String presentationAuthority;

        Snapshot(
            Collection<Row> rows,
            long datasetRevision,
            AtomicTransactionService.SourceAuthority
                policyAuthority
        ){
            this.rows=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        rows
                    )
                );
            this.datasetRevision=
                datasetRevision;
            this.policyAuthority=
                policyAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        Row row(String rowKey){
            String key=
                normalizeKey(
                    rowKey,
                    "rowKey"
                );

            for(Row row:rows)
                if(row.rowKey.equals(key))
                    return row;

            return null;
        }
    }

    private final SelectionExecutor selectionExecutor;
    private final NavigationExecutor navigationExecutor;
    private final AtomicTransactionService.SourceAuthority
        policyAuthority;

    private LinkedHashMap<String,Row> rows=
        new LinkedHashMap<>();

    private long datasetRevision;

    PkRatingsService(
        SelectionExecutor selectionExecutor,
        NavigationExecutor navigationExecutor,
        AtomicTransactionService.SourceAuthority
            policyAuthority
    ){
        this.selectionExecutor=
            Objects.requireNonNull(
                selectionExecutor,
                "selectionExecutor"
            );
        this.navigationExecutor=
            Objects.requireNonNull(
                navigationExecutor,
                "navigationExecutor"
            );
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
                "PK Ratings first policy requires CUSTOM_LOCALLAB authority actual="+
                policyAuthority
            );
    }

    synchronized Snapshot replaceRows(
        Collection<Row> nextRows
    ){
        Objects.requireNonNull(
            nextRows,
            "nextRows"
        );

        if(nextRows.size()>MAX_ROWS)
            throw new IllegalArgumentException(
                "PK Ratings rows="+
                nextRows.size()+
                " max="+MAX_ROWS
            );

        LinkedHashMap<String,Row> candidate=
            new LinkedHashMap<>();

        for(Row row:nextRows){
            Row checked=
                Objects.requireNonNull(
                    row,
                    "row"
                );

            requirePolicyAuthority(
                checked
                    .sourceAuthority
            );

            if(candidate.put(
                    checked.rowKey,
                    checked)!=null)
                throw new IllegalArgumentException(
                    "duplicate PK Ratings rowKey "+
                    checked.rowKey
                );
        }

        long nextRevision=
            addOne(
                datasetRevision,
                "PK Ratings dataset revision"
            );

        rows=candidate;
        datasetRevision=
            nextRevision;

        return snapshot();
    }

    synchronized Snapshot updateDisplayText(
        String rowKey,
        String displayText
    ){
        String key=
            normalizeKey(
                rowKey,
                "rowKey"
            );

        Row current=
            rows.get(key);

        if(current==null)
            throw new IllegalArgumentException(
                "unknown PK Ratings row "+
                key
            );

        String text=
            requireText(
                displayText,
                "displayText"
            );

        if(current.displayText.equals(text))
            return snapshot();

        long nextRevision=
            addOne(
                datasetRevision,
                "PK Ratings dataset revision"
            );

        rows.put(
            key,
            current.withDisplayText(
                text
            )
        );
        datasetRevision=
            nextRevision;

        return snapshot();
    }

    synchronized ActionResult selectRow(
        String playerRef,
        String rowKey
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        String key=
            normalizeKey(
                rowKey,
                "rowKey"
            );

        Row row=
            rows.get(key);

        if(row==null)
            throw new IllegalArgumentException(
                "unknown PK Ratings row "+
                key
            );

        if(!row.selectable)
            throw new IllegalStateException(
                "PK Ratings row not selectable "+
                key
            );

        return Objects.requireNonNull(
            selectionExecutor.select(
                player,
                row,
                policyAuthority
            ),
            "selection result"
        );
    }

    synchronized ActionResult requestNavigation(
        String playerRef,
        Navigation navigation
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        Navigation checked=
            Objects.requireNonNull(
                navigation,
                "navigation"
            );

        return Objects.requireNonNull(
            navigationExecutor.navigate(
                player,
                checked,
                policyAuthority
            ),
            "navigation result"
        );
    }

    synchronized Snapshot snapshot(){
        return new Snapshot(
            rows.values(),
            datasetRevision,
            policyAuthority
        );
    }

    synchronized int size(){
        return rows.size();
    }

    private void requirePolicyAuthority(
        AtomicTransactionService.SourceAuthority
            authority
    ){
        if(authority!=policyAuthority)
            throw new IllegalArgumentException(
                "PK Ratings row authority mismatch actual="+
                authority+
                " expected="+
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
        String normalized=
            requireText(
                value,
                field
            ).toLowerCase(
                Locale.ROOT
            );

        if(normalized.length()>160)
            throw new IllegalArgumentException(
                field+" too long"
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
