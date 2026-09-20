package spk.local;

import java.util.*;

/**
 * Exact-current Adventure book presentation projection.
 *
 * Semantic objective identity enters this mapper and exact generated client
 * widget identity leaves it. No gameplay authority or packet I/O lives here.
 */
final class AdventureBookProjectionMapper {
    static final int MAX_ROWS=25;

    static final int INFO_BASE=30400;
    static final int TELEPORT_BASE=30403;
    static final int CLAIM_BASE=30407;
    static final int ROW_STRIDE=15;

    static final int NEXT_CHAPTER_WIDGET=30380;
    static final int PREVIOUS_CHAPTER_WIDGET=30383;
    static final int CLAIM_CHAPTER_WIDGET=30390;

    enum DynamicAction {
        INFO,
        TELEPORT,
        CLAIM
    }

    enum ChapterAction {
        NEXT_CHAPTER,
        PREVIOUS_CHAPTER,
        CLAIM_REWARDS
    }

    static final class Entry {
        final String objectiveKey;
        final boolean claimed;
        final boolean claimable;

        Entry(
            String objectiveKey,
            boolean claimed,
            boolean claimable
        ){
            this.objectiveKey=
                ObjectiveDefinition
                    .normalizeKey(
                        objectiveKey
                    );

            if(claimed&&claimable)
                throw new IllegalArgumentException(
                    "claimed objective cannot be claimable "+
                    this.objectiveKey
                );

            this.claimed=claimed;
            this.claimable=claimable;
        }

        @Override public String toString(){
            return "AdventureProjectionEntry{"+
                "objectiveKey="+objectiveKey+
                ",claimed="+claimed+
                ",claimable="+claimable+
                "}";
        }
    }

    static final class Row {
        final int ordinal;
        final String objectiveKey;
        final boolean claimed;
        final boolean claimable;

        Row(
            int ordinal,
            Entry entry
        ){
            this.ordinal=ordinal;
            this.objectiveKey=
                entry.objectiveKey;
            this.claimed=
                entry.claimed;
            this.claimable=
                entry.claimable;
        }

        int infoWidget(){
            return widget(
                INFO_BASE,
                ordinal
            );
        }

        int teleportWidget(){
            return widget(
                TELEPORT_BASE,
                ordinal
            );
        }

        Integer claimWidget(){
            if(claimed||
               !claimable)
                return null;

            return widget(
                CLAIM_BASE,
                ordinal
            );
        }

        @Override public String toString(){
            return "AdventureProjectionRow{"+
                "ordinal="+ordinal+
                ",objectiveKey="+
                    objectiveKey+
                ",claimed="+claimed+
                ",claimable="+claimable+
                "}";
        }
    }

    static final class DynamicSelection {
        final DynamicAction action;
        final int rowOrdinal;
        final String objectiveKey;

        DynamicSelection(
            DynamicAction action,
            Row row
        ){
            this.action=action;
            this.rowOrdinal=
                row.ordinal;
            this.objectiveKey=
                row.objectiveKey;
        }

        @Override public String toString(){
            return "AdventureDynamicSelection{"+
                "action="+action+
                ",rowOrdinal="+
                    rowOrdinal+
                ",objectiveKey="+
                    objectiveKey+
                "}";
        }
    }

    static final class Snapshot {
        private final List<Row> rows;
        private final Map<String,Row>
            byObjectiveKey;

        Snapshot(
            List<Row> rows
        ){
            this.rows=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        rows
                    )
                );

            LinkedHashMap<String,Row> map=
                new LinkedHashMap<>();

            for(Row row:rows)
                map.put(
                    row.objectiveKey,
                    row
                );

            this.byObjectiveKey=
                Collections.unmodifiableMap(
                    map
                );
        }

        List<Row> rows(){
            return rows;
        }

        Row row(
            String objectiveKey
        ){
            return byObjectiveKey.get(
                ObjectiveDefinition
                    .normalizeKey(
                        objectiveKey
                    )
            );
        }

        DynamicSelection resolveDynamicWidget(
            int widgetId
        ){
            DynamicAction action=
                dynamicAction(
                    widgetId
                );

            if(action==null)
                return null;

            int base=
                action==DynamicAction.INFO
                    ?INFO_BASE
                    :action==DynamicAction.TELEPORT
                        ?TELEPORT_BASE
                        :CLAIM_BASE;

            int delta=
                widgetId-base;

            if(delta<0||
               delta%ROW_STRIDE!=0)
                return null;

            int ordinal=
                delta/ROW_STRIDE;

            if(ordinal<0||
               ordinal>=rows.size())
                return null;

            Row row=
                rows.get(
                    ordinal
                );

            if(action==DynamicAction.CLAIM&&
               (row.claimed||
                !row.claimable))
                return null;

            return new DynamicSelection(
                action,
                row
            );
        }

        private static DynamicAction dynamicAction(
            int widgetId
        ){
            if(inRangeForBase(
                    widgetId,
                    INFO_BASE))
                return DynamicAction.INFO;

            if(inRangeForBase(
                    widgetId,
                    TELEPORT_BASE))
                return DynamicAction.TELEPORT;

            if(inRangeForBase(
                    widgetId,
                    CLAIM_BASE))
                return DynamicAction.CLAIM;

            return null;
        }

        private static boolean inRangeForBase(
            int widgetId,
            int base
        ){
            int delta=
                widgetId-base;

            return delta>=0&&
                delta<=
                    ROW_STRIDE*
                    (MAX_ROWS-1)&&
                delta%ROW_STRIDE==0;
        }
    }

    Snapshot project(
        Collection<Entry> input
    ){
        Objects.requireNonNull(
            input,
            "input"
        );

        if(input.size()>MAX_ROWS)
            throw new IllegalArgumentException(
                "Adventure row cap "+
                MAX_ROWS+
                " exceeded by "+
                input.size()
            );

        ArrayList<Entry> unclaimed=
            new ArrayList<>();

        ArrayList<Entry> claimed=
            new ArrayList<>();

        HashSet<String> seen=
            new HashSet<>();

        for(Entry entry:input){
            Objects.requireNonNull(
                entry,
                "entry"
            );

            if(!seen.add(
                    entry.objectiveKey))
                throw new IllegalStateException(
                    "duplicate objectiveKey "+
                    entry.objectiveKey
                );

            if(entry.claimed)
                claimed.add(entry);
            else
                unclaimed.add(entry);
        }

        ArrayList<Row> rows=
            new ArrayList<>(
                input.size()
            );

        for(Entry entry:unclaimed)
            rows.add(
                new Row(
                    rows.size(),
                    entry
                )
            );

        for(Entry entry:claimed)
            rows.add(
                new Row(
                    rows.size(),
                    entry
                )
            );

        return new Snapshot(
            rows
        );
    }

    static ChapterAction resolveChapterWidget(
        int widgetId
    ){
        switch(widgetId){
            case NEXT_CHAPTER_WIDGET:
                return ChapterAction
                    .NEXT_CHAPTER;
            case PREVIOUS_CHAPTER_WIDGET:
                return ChapterAction
                    .PREVIOUS_CHAPTER;
            case CLAIM_CHAPTER_WIDGET:
                return ChapterAction
                    .CLAIM_REWARDS;
            default:
                return null;
        }
    }

    private static int widget(
        int base,
        int ordinal
    ){
        if(ordinal<0||
           ordinal>=MAX_ROWS)
            throw new IllegalArgumentException(
                "ordinal="+ordinal
            );

        return base+
            ROW_STRIDE*
            ordinal;
    }
}
