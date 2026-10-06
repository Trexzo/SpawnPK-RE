package spk.local;

import java.util.*;

/**
 * Deterministic semantic objective/progression state.
 *
 * This service tracks progress/completion/claim bookkeeping only. It does not
 * decide attribution, eligibility, prerequisites, reset schedules or rewards.
 */
final class ObjectiveProgressService {
    static final class Snapshot {
        final String key;
        final long progress;
        final long goal;
        final boolean complete;
        final boolean claimed;
        final String sourceAuthority;

        Snapshot(
            ObjectiveDefinition definition,
            long progress,
            boolean claimed
        ){
            this.key=definition.key;
            this.progress=progress;
            this.goal=definition.goal;
            this.complete=
                progress>=definition.goal;
            this.claimed=claimed;
            this.sourceAuthority=
                definition.sourceAuthority;
        }

        @Override public String toString(){
            return "ObjectiveSnapshot{"+
                "key="+key+
                ",progress="+progress+
                ",goal="+goal+
                ",complete="+complete+
                ",claimed="+claimed+
                ",sourceAuthority="+
                    sourceAuthority+
                "}";
        }
    }

    static final class ProgressResult {
        final Snapshot before;
        final Snapshot after;
        final boolean completedNow;

        ProgressResult(
            Snapshot before,
            Snapshot after
        ){
            this.before=before;
            this.after=after;
            this.completedNow=
                !before.complete&&
                after.complete;
        }
    }

    private static final class Entry {
        final ObjectiveDefinition definition;
        long progress;
        boolean claimed;

        Entry(
            ObjectiveDefinition definition,
            long progress,
            boolean claimed
        ){
            this.definition=definition;
            this.progress=progress;
            this.claimed=claimed;
        }

        Snapshot snapshot(){
            return new Snapshot(
                definition,
                progress,
                claimed
            );
        }
    }

    private final LinkedHashMap<String,Entry>
        entries=
            new LinkedHashMap<>();

    synchronized Snapshot define(
        ObjectiveDefinition definition
    ){
        return define(
            definition,
            0,
            false
        );
    }

    synchronized Snapshot define(
        ObjectiveDefinition definition,
        long initialProgress,
        boolean claimed
    ){
        Objects.requireNonNull(
            definition,
            "definition"
        );

        if(initialProgress<0||
           initialProgress>definition.goal)
            throw new IllegalArgumentException(
                "initialProgress="+
                initialProgress+
                " goal="+
                definition.goal
            );

        if(claimed&&
           initialProgress<definition.goal)
            throw new IllegalArgumentException(
                "claimed incomplete objective "+
                definition.key
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
                    "conflicting objective definition "+
                    definition.key
                );

            if(existing.progress!=
                    initialProgress||
               existing.claimed!=claimed)
                throw new IllegalStateException(
                    "objective already defined with different state "+
                    definition.key
                );

            return existing.snapshot();
        }

        Entry entry=
            new Entry(
                definition,
                initialProgress,
                claimed
            );

        entries.put(
            definition.key,
            entry
        );

        return entry.snapshot();
    }

    synchronized ProgressResult advance(
        String key,
        long amount
    ){
        if(amount<=0)
            throw new IllegalArgumentException(
                "amount="+amount
            );

        Entry entry=
            requireEntry(key);

        Snapshot before=
            entry.snapshot();

        if(entry.progress<
                entry.definition.goal){
            long remaining=
                entry.definition.goal-
                entry.progress;

            entry.progress+=
                amount>=remaining
                    ?remaining
                    :amount;
        }

        return new ProgressResult(
            before,
            entry.snapshot()
        );
    }

    synchronized Snapshot resetCompleted(
        String key
    ){
        Entry entry=
            requireEntry(key);

        if(entry.progress<
                entry.definition.goal)
            throw new IllegalStateException(
                "objective incomplete "+
                entry.definition.key
            );

        entry.progress=0L;
        entry.claimed=false;
        return entry.snapshot();
    }

    synchronized boolean markClaimed(
        String key
    ){
        Entry entry=
            requireEntry(key);

        if(entry.progress<
                entry.definition.goal)
            throw new IllegalStateException(
                "objective incomplete "+
                entry.definition.key
            );

        if(entry.claimed)
            return false;

        entry.claimed=true;
        return true;
    }

    synchronized Snapshot get(
        String key
    ){
        Entry entry=
            entries.get(
                ObjectiveDefinition
                    .normalizeKey(key)
            );

        return entry==null
            ?null
            :entry.snapshot();
    }

    synchronized int size(){
        return entries.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Entry entry:
                entries.values())
            out.add(
                entry.snapshot()
            );

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

    private Entry requireEntry(
        String key
    ){
        String normalized=
            ObjectiveDefinition
                .normalizeKey(key);

        Entry entry=
            entries.get(
                normalized
            );

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown objective key="+
                normalized
            );

        return entry;
    }
}
