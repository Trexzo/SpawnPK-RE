package spk.local;

import java.util.*;

/**
 * Semantic Event Chest application shell for the exact-current client-compatible
 * projection. Reward generation, roll mechanics and economics remain external.
 */
final class EventChestService {
    static final int MAIN_GRID_CAPACITY=175;
    static final int SMALL_GRID_COUNT=3;
    static final int SMALL_GRID_CAPACITY=4;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum Action {
        EXCHANGE,
        ENTER_NEXT_TIER,
        RESET_EVENT_ITEMS
    }

    static final class DisplayEntry {
        final String entryKey;
        final long quantity;

        DisplayEntry(
            String entryKey,
            long quantity
        ){
            this.entryKey=
                normalizeKey(
                    entryKey,
                    "entryKey"
                );

            if(quantity<=0L)
                throw new IllegalArgumentException(
                    "quantity="+quantity
                );

            this.quantity=quantity;
        }
    }

    static final class Projection {
        final String headingText;
        final String progressText;
        final String statusText;
        final List<DisplayEntry> mainEntries;
        final List<List<DisplayEntry>> smallGrids;
        final AtomicTransactionService.SourceAuthority
            sourceAuthority;
        final String presentationAuthority;

        Projection(
            String headingText,
            String progressText,
            String statusText,
            Collection<DisplayEntry> mainEntries,
            Collection<? extends Collection<DisplayEntry>>
                smallGrids,
            AtomicTransactionService.SourceAuthority
                sourceAuthority
        ){
            this.headingText=
                requireText(
                    headingText,
                    "headingText"
                );
            this.progressText=
                requireText(
                    progressText,
                    "progressText"
                );
            this.statusText=
                requireText(
                    statusText,
                    "statusText"
                );

            Objects.requireNonNull(
                mainEntries,
                "mainEntries"
            );

            if(mainEntries.size()>
                    MAIN_GRID_CAPACITY)
                throw new IllegalArgumentException(
                    "Event Chest main entries="+
                    mainEntries.size()+
                    " max="+
                    MAIN_GRID_CAPACITY
                );

            this.mainEntries=
                immutableEntries(
                    mainEntries,
                    "mainEntries"
                );

            Objects.requireNonNull(
                smallGrids,
                "smallGrids"
            );

            if(smallGrids.size()!=
                    SMALL_GRID_COUNT)
                throw new IllegalArgumentException(
                    "Event Chest small grids="+
                    smallGrids.size()+
                    " expected="+
                    SMALL_GRID_COUNT
                );

            ArrayList<List<DisplayEntry>> grids=
                new ArrayList<>();

            int index=0;

            for(Collection<DisplayEntry> grid:
                    smallGrids){
                Objects.requireNonNull(
                    grid,
                    "smallGrid"
                );

                if(grid.size()>
                        SMALL_GRID_CAPACITY)
                    throw new IllegalArgumentException(
                        "Event Chest small grid "+
                        index+
                        " entries="+
                        grid.size()+
                        " max="+
                        SMALL_GRID_CAPACITY
                    );

                grids.add(
                    immutableEntries(
                        grid,
                        "smallGrid["+index+"]"
                    )
                );
                index++;
            }

            this.smallGrids=
                Collections.unmodifiableList(
                    grids
                );
            this.sourceAuthority=
                Objects.requireNonNull(
                    sourceAuthority,
                    "sourceAuthority"
                );
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        private static List<DisplayEntry>
            immutableEntries(
                Collection<DisplayEntry> values,
                String field
            )
        {
            ArrayList<DisplayEntry> copy=
                new ArrayList<>();
            HashSet<String> keys=
                new HashSet<>();

            for(DisplayEntry entry:values){
                DisplayEntry checked=
                    Objects.requireNonNull(
                        entry,
                        field+" entry"
                    );

                if(!keys.add(
                        checked.entryKey))
                    throw new IllegalArgumentException(
                        "duplicate "+
                        field+
                        " entryKey "+
                        checked.entryKey
                    );

                copy.add(checked);
            }

            return Collections.unmodifiableList(
                copy
            );
        }
    }

    static final class Snapshot {
        final Projection projection;
        final long revision;
        final AtomicTransactionService.SourceAuthority
            policyAuthority;
        final String presentationAuthority;

        Snapshot(
            Projection projection,
            long revision,
            AtomicTransactionService.SourceAuthority
                policyAuthority
        ){
            this.projection=projection;
            this.revision=revision;
            this.policyAuthority=
                policyAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        boolean configured(){
            return projection!=null;
        }
    }

    static final class ActionResult {
        final Action action;
        final boolean succeeded;
        final String detail;

        private ActionResult(
            Action action,
            boolean succeeded,
            String detail
        ){
            this.action=
                Objects.requireNonNull(
                    action,
                    "action"
                );
            this.succeeded=succeeded;
            this.detail=
                detail==null
                    ?""
                    :detail.trim();
        }

        static ActionResult success(
            Action action
        ){
            return new ActionResult(
                action,
                true,
                ""
            );
        }

        static ActionResult success(
            Action action,
            String detail
        ){
            return new ActionResult(
                action,
                true,
                detail
            );
        }

        static ActionResult failure(
            Action action,
            String detail
        ){
            return new ActionResult(
                action,
                false,
                requireText(
                    detail,
                    "detail"
                )
            );
        }
    }

    interface ActionExecutor {
        ActionResult execute(
            String playerRef,
            Action action,
            Projection projection,
            AtomicTransactionService.SourceAuthority
                policyAuthority
        );
    }

    private final ActionExecutor actionExecutor;
    private final AtomicTransactionService.SourceAuthority
        policyAuthority;

    private Projection projection;
    private long revision;

    EventChestService(
        ActionExecutor actionExecutor,
        AtomicTransactionService.SourceAuthority
            policyAuthority
    ){
        this.actionExecutor=
            Objects.requireNonNull(
                actionExecutor,
                "actionExecutor"
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
                "Event Chest first policy requires CUSTOM_LOCALLAB authority actual="+
                policyAuthority
            );
    }

    synchronized Snapshot replaceProjection(
        Projection candidate
    ){
        Projection checked=
            Objects.requireNonNull(
                candidate,
                "candidate"
            );

        if(checked.sourceAuthority!=
                policyAuthority)
            throw new IllegalArgumentException(
                "Event Chest projection authority mismatch actual="+
                checked.sourceAuthority+
                " expected="+
                policyAuthority
            );

        long nextRevision=
            addOne(
                revision,
                "Event Chest projection revision"
            );

        projection=checked;
        revision=nextRevision;

        return snapshot();
    }

    ActionResult requestAction(
        String playerRef,
        Action action
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        Action checked=
            Objects.requireNonNull(
                action,
                "action"
            );

        final Projection captured;

        /*
         * Capture the immutable projection under the service monitor, then
         * release it before invoking caller-owned application code. Actions
         * do not mutate EventChestService state, so no post-callback commit is
         * required and a later projection replacement does not retroactively
         * rewrite the request that was already issued.
         */
        synchronized(this){
            if(projection==null)
                throw new IllegalStateException(
                    "Event Chest projection not configured"
                );

            captured=projection;
        }

        ActionResult result=
            Objects.requireNonNull(
                actionExecutor.execute(
                    player,
                    checked,
                    captured,
                    policyAuthority
                ),
                "action result"
            );

        if(result.action!=checked)
            throw new IllegalStateException(
                "Event Chest action result mismatch expected="+
                checked+
                " actual="+
                result.action
            );

        return result;
    }

    synchronized Snapshot snapshot(){
        return new Snapshot(
            projection,
            revision,
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
