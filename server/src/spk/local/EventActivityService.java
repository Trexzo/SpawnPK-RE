package spk.local;

import java.util.*;

/**
 * Semantic Event Activity Viewer composition.
 *
 * Exact-current client presentation distinguishes locked (-1), unlimited (0)
 * and finite (>0) rows. UsageQuotaService intentionally owns only positive
 * finite semantic limits, so this adapter preserves that boundary instead of
 * weakening the generic quota model.
 */
final class EventActivityService {
    static final int MAX_ROWS=7;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum Mode {
        LOCKED,
        UNLIMITED,
        FINITE
    }

    static final class RowSpec {
        final String activityKey;
        final String title;
        final List<String> details;
        final Mode mode;
        final String finiteQuotaKey;
        final long initialObservedUsage;
        final long timerDurationMillis;
        final String policyAuthority;

        RowSpec(
            String activityKey,
            String title,
            Collection<String> details,
            Mode mode,
            String finiteQuotaKey,
            long initialObservedUsage,
            long timerDurationMillis,
            String policyAuthority
        ){
            this.activityKey=
                UsageQuotaDefinition
                    .normalizeKey(
                        activityKey
                    );

            this.title=
                requireText(
                    title,
                    "title"
                );

            Objects.requireNonNull(
                details,
                "details"
            );

            ArrayList<String> normalizedDetails=
                new ArrayList<>();

            for(String detail:details)
                normalizedDetails.add(
                    requireText(
                        detail,
                        "detail"
                    )
                );

            this.details=
                Collections.unmodifiableList(
                    normalizedDetails
                );

            this.mode=
                Objects.requireNonNull(
                    mode,
                    "mode"
                );

            if(initialObservedUsage<0)
                throw new IllegalArgumentException(
                    "initialObservedUsage="+
                    initialObservedUsage
                );

            if(timerDurationMillis<0)
                throw new IllegalArgumentException(
                    "timerDurationMillis="+
                    timerDurationMillis
                );

            this.policyAuthority=
                requireText(
                    policyAuthority,
                    "policyAuthority"
                );

            if(mode==Mode.FINITE){
                this.finiteQuotaKey=
                    UsageQuotaDefinition
                        .normalizeKey(
                            Objects.requireNonNull(
                                finiteQuotaKey,
                                "finiteQuotaKey"
                            )
                        );

                if(initialObservedUsage!=0L)
                    throw new IllegalArgumentException(
                        "FINITE rows derive usage from UsageQuotaService"
                    );
            }else{
                if(finiteQuotaKey!=null)
                    throw new IllegalArgumentException(
                        mode+
                        " row must not bind finiteQuotaKey"
                    );

                this.finiteQuotaKey=null;
            }

            this.initialObservedUsage=
                initialObservedUsage;
            this.timerDurationMillis=
                timerDurationMillis;
        }
    }

    static final class RowSnapshot {
        final int ordinal;
        final String activityKey;
        final String title;
        final List<String> details;
        final Mode mode;
        final String finiteQuotaKey;
        final long currentUsage;
        final long presentationLimit;
        final long timerDurationMillis;
        final String windowKey;
        final long externalTimestamp;
        final String policyAuthority;
        final String presentationAuthority;

        RowSnapshot(
            int ordinal,
            Entry entry,
            UsageQuotaService.Snapshot quota
        ){
            this.ordinal=ordinal;
            this.activityKey=
                entry.spec.activityKey;
            this.title=
                entry.spec.title;
            this.details=
                entry.spec.details;
            this.mode=
                entry.spec.mode;
            this.finiteQuotaKey=
                entry.spec.finiteQuotaKey;
            this.timerDurationMillis=
                entry.spec.timerDurationMillis;
            this.policyAuthority=
                entry.spec.policyAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;

            if(entry.spec.mode==Mode.FINITE){
                if(quota==null)
                    throw new IllegalStateException(
                        "finite Event Activity quota window unavailable "+
                        entry.spec.finiteQuotaKey
                    );

                this.currentUsage=
                    quota.used;
                this.presentationLimit=
                    quota.limit;
                this.windowKey=
                    quota.windowKey;
                this.externalTimestamp=
                    quota.externalTimestamp;
            }else{
                this.currentUsage=
                    entry.observedUsage;
                this.presentationLimit=
                    entry.spec.mode==
                        Mode.LOCKED
                        ?-1L
                        :0L;
                this.windowKey=null;
                this.externalTimestamp=
                    UsageQuotaService
                        .NO_EXTERNAL_TIMESTAMP;
            }
        }

        boolean locked(){
            return mode==Mode.LOCKED;
        }

        boolean unlimited(){
            return mode==Mode.UNLIMITED;
        }

        boolean finite(){
            return mode==Mode.FINITE;
        }

        boolean exhausted(){
            return finite()&&
                currentUsage>=
                    presentationLimit;
        }

        boolean hasExternalTimestamp(){
            return externalTimestamp!=
                UsageQuotaService
                    .NO_EXTERNAL_TIMESTAMP;
        }
    }

    static final class Snapshot {
        final List<RowSnapshot> rows;

        Snapshot(
            Collection<RowSnapshot> rows
        ){
            this.rows=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        rows
                    )
                );
        }

        RowSnapshot row(
            int ordinal
        ){
            if(ordinal<0||
               ordinal>=rows.size())
                return null;

            return rows.get(ordinal);
        }

        RowSnapshot activity(
            String activityKey
        ){
            String key=
                UsageQuotaDefinition
                    .normalizeKey(
                        activityKey
                    );

            for(RowSnapshot row:rows)
                if(row.activityKey.equals(key))
                    return row;

            return null;
        }
    }

    static final class ConsumeResult {
        final RowSnapshot before;
        final RowSnapshot after;
        final long requested;
        final boolean accepted;

        ConsumeResult(
            RowSnapshot before,
            RowSnapshot after,
            long requested,
            boolean accepted
        ){
            this.before=before;
            this.after=after;
            this.requested=requested;
            this.accepted=accepted;
        }
    }

    private static final class Entry {
        final RowSpec spec;
        long observedUsage;

        Entry(RowSpec spec){
            this.spec=spec;
            this.observedUsage=
                spec.initialObservedUsage;
        }
    }

    private final UsageQuotaService quotas;

    private List<Entry> rows=
        Collections.emptyList();

    private LinkedHashMap<String,Entry>
        byActivityKey=
            new LinkedHashMap<>();

    EventActivityService(
        UsageQuotaService quotas
    ){
        this.quotas=
            Objects.requireNonNull(
                quotas,
                "quotas"
            );
    }

    synchronized Snapshot replaceRows(
        Collection<RowSpec> specs
    ){
        Objects.requireNonNull(
            specs,
            "specs"
        );

        if(specs.size()>MAX_ROWS)
            throw new IllegalArgumentException(
                "Event Activity rows="+
                specs.size()+
                " max="+MAX_ROWS
            );

        ArrayList<Entry> nextRows=
            new ArrayList<>();

        LinkedHashMap<String,Entry>
            nextByKey=
                new LinkedHashMap<>();

        for(RowSpec spec:specs){
            RowSpec checked=
                Objects.requireNonNull(
                    spec,
                    "row spec"
                );

            if(nextByKey.containsKey(
                    checked.activityKey))
                throw new IllegalArgumentException(
                    "duplicate Event Activity key "+
                    checked.activityKey
                );

            if(checked.mode==Mode.FINITE){
                UsageQuotaService.Snapshot quota=
                    quotas.get(
                        checked.finiteQuotaKey
                    );

                if(quota==null)
                    throw new IllegalStateException(
                        "finite Event Activity requires open quota window "+
                        checked.finiteQuotaKey
                    );

                if(!checked.policyAuthority.equals(
                        quota.sourceAuthority))
                    throw new IllegalArgumentException(
                        "finite Event Activity quota authority mismatch key="+
                        checked.finiteQuotaKey
                    );

                if(quota.limit<=0)
                    throw new IllegalStateException(
                        "finite quota must remain positive "+
                        checked.finiteQuotaKey
                    );
            }

            Entry entry=
                new Entry(checked);

            nextRows.add(entry);
            nextByKey.put(
                checked.activityKey,
                entry
            );
        }

        rows=
            Collections.unmodifiableList(
                nextRows
            );
        byActivityKey=
            nextByKey;

        return snapshot();
    }

    synchronized Snapshot snapshot(){
        ArrayList<RowSnapshot> out=
            new ArrayList<>();

        for(int i=0;
            i<rows.size();
            i++)
            out.add(
                snapshotOf(
                    i,
                    rows.get(i)
                )
            );

        return new Snapshot(out);
    }

    synchronized RowSnapshot activity(
        String activityKey
    ){
        String key=
            UsageQuotaDefinition
                .normalizeKey(
                    activityKey
                );

        Entry entry=
            byActivityKey.get(key);

        if(entry==null)
            return null;

        return snapshotOf(
            ordinalOf(entry),
            entry
        );
    }

    synchronized ConsumeResult consume(
        String activityKey,
        long amount
    ){
        if(amount<=0)
            throw new IllegalArgumentException(
                "amount="+amount
            );

        String key=
            UsageQuotaDefinition
                .normalizeKey(
                    activityKey
                );

        Entry entry=
            byActivityKey.get(key);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown Event Activity "+
                key
            );

        int ordinal=
            ordinalOf(entry);

        RowSnapshot before=
            snapshotOf(
                ordinal,
                entry
            );

        switch(entry.spec.mode){
            case LOCKED:
                return new ConsumeResult(
                    before,
                    before,
                    amount,
                    false
                );

            case UNLIMITED:
                try{
                    entry.observedUsage=
                        Math.addExact(
                            entry.observedUsage,
                            amount
                        );
                }catch(
                    ArithmeticException overflow
                ){
                    throw new IllegalStateException(
                        "Event Activity unlimited usage overflow "+
                        entry.spec.activityKey,
                        overflow
                    );
                }

                return new ConsumeResult(
                    before,
                    snapshotOf(
                        ordinal,
                        entry
                    ),
                    amount,
                    true
                );

            case FINITE:
                UsageQuotaService.ConsumeResult
                    quotaResult=
                        quotas.tryConsume(
                            entry.spec.finiteQuotaKey,
                            amount
                        );

                return new ConsumeResult(
                    before,
                    snapshotOf(
                        ordinal,
                        entry
                    ),
                    amount,
                    quotaResult.accepted
                );

            default:
                throw new AssertionError(
                    entry.spec.mode
                );
        }
    }

    synchronized int size(){
        return rows.size();
    }

    private RowSnapshot snapshotOf(
        int ordinal,
        Entry entry
    ){
        UsageQuotaService.Snapshot quota=
            entry.spec.mode==Mode.FINITE
                ?quotas.get(
                    entry.spec.finiteQuotaKey
                )
                :null;

        if(quota!=null&&
           !entry.spec.policyAuthority.equals(
                quota.sourceAuthority))
            throw new IllegalStateException(
                "finite Event Activity quota authority drift key="+
                entry.spec.finiteQuotaKey
            );

        return new RowSnapshot(
            ordinal,
            entry,
            quota
        );
    }

    private int ordinalOf(Entry entry){
        for(int i=0;
            i<rows.size();
            i++)
            if(rows.get(i)==entry)
                return i;

        throw new IllegalStateException(
            "Event Activity entry not in live row list"
        );
    }

    private static String requireText(
        String value,
        String label
    ){
        if(value==null)
            throw new NullPointerException(
                label
            );

        String normalized=
            value.trim();

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                label+" blank"
            );

        return normalized;
    }
}
