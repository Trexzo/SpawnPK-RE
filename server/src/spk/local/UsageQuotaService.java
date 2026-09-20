package spk.local;

import java.util.*;

/**
 * Explicit-window semantic usage-quota state.
 *
 * This service never decides when a window begins or resets. Callers supply a
 * semantic window key and may carry an uninterpreted external timestamp for
 * later presentation/adapters. Consumption is atomic and fails closed when it
 * would exceed the configured limit.
 */
final class UsageQuotaService {
    static final long NO_EXTERNAL_TIMESTAMP=
        Long.MIN_VALUE;

    static final class Snapshot {
        final String key;
        final String windowKey;
        final long used;
        final long limit;
        final long remaining;
        final long externalTimestamp;
        final String sourceAuthority;

        Snapshot(
            Entry entry
        ){
            this.key=
                entry.definition.key;
            this.windowKey=
                entry.windowKey;
            this.used=
                entry.used;
            this.limit=
                entry.definition.limit;
            this.remaining=
                entry.definition.limit-
                entry.used;
            this.externalTimestamp=
                entry.externalTimestamp;
            this.sourceAuthority=
                entry.definition
                    .sourceAuthority;
        }

        boolean hasExternalTimestamp(){
            return externalTimestamp!=
                NO_EXTERNAL_TIMESTAMP;
        }

        boolean exhausted(){
            return used>=limit;
        }

        @Override public String toString(){
            return "UsageQuotaSnapshot{"+
                "key="+key+
                ",windowKey="+windowKey+
                ",used="+used+
                ",limit="+limit+
                ",remaining="+remaining+
                ",externalTimestamp="+
                    (hasExternalTimestamp()
                        ?Long.toString(
                            externalTimestamp
                        )
                        :"absent")+
                ",sourceAuthority="+
                    sourceAuthority+
                "}";
        }
    }

    static final class ConsumeResult {
        final Snapshot before;
        final Snapshot after;
        final long requested;
        final boolean accepted;

        ConsumeResult(
            Snapshot before,
            Snapshot after,
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
        final UsageQuotaDefinition definition;
        String windowKey;
        long used;
        long externalTimestamp;

        Entry(
            UsageQuotaDefinition definition
        ){
            this.definition=definition;
        }

        Snapshot snapshot(){
            if(windowKey==null)
                return null;

            return new Snapshot(
                this
            );
        }
    }

    private final LinkedHashMap<String,Entry>
        entries=
            new LinkedHashMap<>();

    synchronized void define(
        UsageQuotaDefinition definition
    ){
        Objects.requireNonNull(
            definition,
            "definition"
        );

        Entry existing=
            entries.get(
                definition.key
            );

        if(existing!=null){
            if(!existing.definition
                    .sameContract(
                        definition))
                throw new IllegalStateException(
                    "conflicting quota definition "+
                    definition.key
                );

            return;
        }

        entries.put(
            definition.key,
            new Entry(
                definition
            )
        );
    }

    synchronized Snapshot openWindow(
        String key,
        String windowKey,
        long initialUsed
    ){
        return openWindow(
            key,
            windowKey,
            initialUsed,
            NO_EXTERNAL_TIMESTAMP
        );
    }

    synchronized Snapshot openWindow(
        String key,
        String windowKey,
        long initialUsed,
        long externalTimestamp
    ){
        Entry entry=
            requireDefined(key);

        String normalizedWindow=
            UsageQuotaDefinition
                .normalizeWindowKey(
                    windowKey
                );

        if(initialUsed<0||
           initialUsed>
                entry.definition.limit)
            throw new IllegalArgumentException(
                "initialUsed="+
                initialUsed+
                " limit="+
                entry.definition.limit
            );

        entry.windowKey=
            normalizedWindow;
        entry.used=
            initialUsed;
        entry.externalTimestamp=
            externalTimestamp;

        return entry.snapshot();
    }

    synchronized ConsumeResult tryConsume(
        String key,
        long amount
    ){
        if(amount<=0)
            throw new IllegalArgumentException(
                "amount="+amount
            );

        Entry entry=
            requireWindow(key);

        Snapshot before=
            entry.snapshot();

        long remaining=
            entry.definition.limit-
                entry.used;

        boolean accepted=
            amount<=remaining;

        if(accepted)
            entry.used+=amount;

        return new ConsumeResult(
            before,
            entry.snapshot(),
            amount,
            accepted
        );
    }

    synchronized Snapshot get(
        String key
    ){
        Entry entry=
            entries.get(
                UsageQuotaDefinition
                    .normalizeKey(key)
            );

        return entry==null
            ?null
            :entry.snapshot();
    }

    synchronized boolean closeWindow(
        String key
    ){
        Entry entry=
            requireDefined(key);

        if(entry.windowKey==null)
            return false;

        entry.windowKey=null;
        entry.used=0;
        entry.externalTimestamp=
            NO_EXTERNAL_TIMESTAMP;
        return true;
    }

    synchronized int definitionCount(){
        return entries.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Entry entry:
                entries.values()){
            Snapshot snapshot=
                entry.snapshot();

            if(snapshot!=null)
                out.add(snapshot);
        }

        out.sort(
            Comparator.comparing(
                value->
                    value.key
            )
        );

        return Collections.unmodifiableList(
            out
        );
    }

    private Entry requireDefined(
        String key
    ){
        String normalized=
            UsageQuotaDefinition
                .normalizeKey(key);

        Entry entry=
            entries.get(
                normalized
            );

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown quota key="+
                normalized
            );

        return entry;
    }

    private Entry requireWindow(
        String key
    ){
        Entry entry=
            requireDefined(key);

        if(entry.windowKey==null)
            throw new IllegalStateException(
                "quota window not open "+
                entry.definition.key
            );

        return entry;
    }
}
