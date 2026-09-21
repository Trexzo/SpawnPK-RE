package spk.local;

import java.util.*;

/**
 * Concrete binding between recovered Achievement chapter presentation rows and
 * caller-owned semantic objective state.
 *
 * The recovered catalog remains presentation/bootstrap evidence only. This
 * service never defines objective goals, seeds semantic progress/claim state,
 * parses reward payloads or grants rewards.
 */
final class AchievementChapterService {
    static final class BindingSpec {
        final int rowIndex;
        final String objectiveKey;

        BindingSpec(
            int rowIndex,
            String objectiveKey
        ){
            if(rowIndex<0||
               rowIndex>=
                    AchievementChapterBootstrapCatalog
                        .size())
                throw new IllegalArgumentException(
                    "achievement row index="+
                    rowIndex
                );

            this.rowIndex=rowIndex;
            this.objectiveKey=
                ObjectiveDefinition
                    .normalizeKey(
                        objectiveKey
                    );
        }
    }

    static final class RowSnapshot {
        final int rowIndex;
        final String objectiveKey;

        final String renderType;
        final int renderId;
        final String taskText;
        final String subtext;
        final String rewardPairsEvidence;
        final long displayedGoal;
        final long bootstrapInitialProgressEvidence;
        final boolean bootstrapClaimedEvidence;
        final String presentationAuthority;

        final long progress;
        final long semanticGoal;
        final boolean complete;
        final boolean claimed;
        final boolean claimable;
        final String objectiveAuthority;

        RowSnapshot(
            Binding binding,
            ObjectiveProgressService.Snapshot
                state
        ){
            AchievementChapterBootstrapCatalog.Row
                row=
                    binding.row;

            this.rowIndex=row.index;
            this.objectiveKey=
                binding.objectiveKey;

            this.renderType=row.renderType;
            this.renderId=row.renderId;
            this.taskText=row.taskText;
            this.subtext=row.subtext;
            this.rewardPairsEvidence=
                row.rewardPairs;
            this.displayedGoal=row.goal;
            this.bootstrapInitialProgressEvidence=
                row.initialProgress;
            this.bootstrapClaimedEvidence=
                row.claimed;
            this.presentationAuthority=
                row.presentationAuthority;

            this.progress=state.progress;
            this.semanticGoal=state.goal;
            this.complete=state.complete;
            this.claimed=state.claimed;
            this.claimable=
                state.complete&&
                !state.claimed;
            this.objectiveAuthority=
                state.sourceAuthority;
        }
    }

    static final class Snapshot {
        final List<RowSnapshot> rows;
        final List<String>
            claimableObjectiveKeys;

        Snapshot(
            List<RowSnapshot> rows
        ){
            this.rows=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        rows
                    )
                );

            ArrayList<String> claimable=
                new ArrayList<>();

            for(RowSnapshot row:rows)
                if(row.claimable)
                    claimable.add(
                        row.objectiveKey
                    );

            this.claimableObjectiveKeys=
                Collections.unmodifiableList(
                    claimable
                );
        }

        RowSnapshot row(
            int rowIndex
        ){
            for(RowSnapshot row:rows)
                if(row.rowIndex==
                        rowIndex)
                    return row;

            return null;
        }

        RowSnapshot objective(
            String objectiveKey
        ){
            String normalized=
                ObjectiveDefinition
                    .normalizeKey(
                        objectiveKey
                    );

            for(RowSnapshot row:rows)
                if(row.objectiveKey
                        .equals(normalized))
                    return row;

            return null;
        }
    }

    private static final class Binding {
        final AchievementChapterBootstrapCatalog.Row
            row;
        final String objectiveKey;

        Binding(
            AchievementChapterBootstrapCatalog.Row
                row,
            String objectiveKey
        ){
            this.row=row;
            this.objectiveKey=
                objectiveKey;
        }
    }

    private final ObjectiveProgressService
        objectives;

    private List<Binding> bindings=
        Collections.emptyList();

    private Map<Integer,Binding> byRow=
        Collections.emptyMap();

    private Map<String,Binding> byObjective=
        Collections.emptyMap();

    AchievementChapterService(
        ObjectiveProgressService objectives
    ){
        this.objectives=
            Objects.requireNonNull(
                objectives,
                "objectives"
            );
    }

    synchronized Snapshot replaceBindings(
        Collection<BindingSpec> specs
    ){
        Objects.requireNonNull(
            specs,
            "specs"
        );

        int expected=
            AchievementChapterBootstrapCatalog
                .size();

        if(specs.size()!=expected)
            throw new IllegalArgumentException(
                "Achievement binding count="+
                specs.size()+
                " expected="+expected
            );

        TreeMap<Integer,Binding> nextByRow=
            new TreeMap<>();

        LinkedHashMap<String,Binding>
            nextByObjective=
                new LinkedHashMap<>();

        for(BindingSpec spec:specs){
            BindingSpec checked=
                Objects.requireNonNull(
                    spec,
                    "binding"
                );

            if(nextByRow.containsKey(
                    checked.rowIndex))
                throw new IllegalArgumentException(
                    "duplicate Achievement row binding "+
                    checked.rowIndex
                );

            if(nextByObjective.containsKey(
                    checked.objectiveKey))
                throw new IllegalArgumentException(
                    "duplicate Achievement objective binding "+
                    checked.objectiveKey
                );

            AchievementChapterBootstrapCatalog.Row
                row=
                    AchievementChapterBootstrapCatalog
                        .get(
                            checked.rowIndex
                        );

            ObjectiveProgressService.Snapshot
                state=
                    objectives.get(
                        checked.objectiveKey
                    );

            if(state==null)
                throw new IllegalArgumentException(
                    "unknown Achievement objective "+
                    checked.objectiveKey
                );

            if(state.goal!=row.goal)
                throw new IllegalArgumentException(
                    "Achievement presentation goal mismatch row="+
                    row.index+
                    " displayed="+row.goal+
                    " semantic="+state.goal+
                    " objective="+
                    checked.objectiveKey
                );

            Binding binding=
                new Binding(
                    row,
                    checked.objectiveKey
                );

            nextByRow.put(
                checked.rowIndex,
                binding
            );
            nextByObjective.put(
                checked.objectiveKey,
                binding
            );
        }

        for(int index=0;
            index<expected;
            index++)
            if(!nextByRow.containsKey(index))
                throw new IllegalArgumentException(
                    "missing Achievement row binding "+
                    index
                );

        ArrayList<Binding> next=
            new ArrayList<>(
                nextByRow.values()
            );

        bindings=
            Collections.unmodifiableList(
                next
            );
        byRow=
            Collections.unmodifiableMap(
                new LinkedHashMap<>(
                    nextByRow
                )
            );
        byObjective=
            Collections.unmodifiableMap(
                nextByObjective
            );

        return snapshot();
    }

    synchronized Snapshot snapshot(){
        requireConfigured();

        ArrayList<RowSnapshot> rows=
            new ArrayList<>();

        for(Binding binding:
                bindings)
            rows.add(
                snapshotOf(
                    binding
                )
            );

        return new Snapshot(rows);
    }

    synchronized RowSnapshot row(
        int rowIndex
    ){
        requireConfigured();

        Binding binding=
            byRow.get(
                rowIndex
            );

        return binding==null
            ?null
            :snapshotOf(
                binding
            );
    }

    synchronized RowSnapshot objective(
        String objectiveKey
    ){
        requireConfigured();

        Binding binding=
            byObjective.get(
                ObjectiveDefinition
                    .normalizeKey(
                        objectiveKey
                    )
            );

        return binding==null
            ?null
            :snapshotOf(
                binding
            );
    }

    synchronized boolean
        confirmRewardSettledAndMarkClaimed(
            String objectiveKey
        ){
        requireConfigured();

        String normalized=
            ObjectiveDefinition
                .normalizeKey(
                    objectiveKey
                );

        Binding binding=
            byObjective.get(
                normalized
            );

        if(binding==null)
            throw new IllegalArgumentException(
                "objective not bound to Achievement chapter "+
                normalized
            );

        ObjectiveProgressService.Snapshot
            state=
                objectives.get(
                    normalized
                );

        if(state==null)
            throw new IllegalStateException(
                "Achievement objective disappeared "+
                normalized
            );

        if(!state.complete)
            throw new IllegalStateException(
                "Achievement objective incomplete "+
                normalized
            );

        return objectives.markClaimed(
            normalized
        );
    }

    synchronized int size(){
        return bindings.size();
    }

    private RowSnapshot snapshotOf(
        Binding binding
    ){
        ObjectiveProgressService.Snapshot
            state=
                objectives.get(
                    binding.objectiveKey
                );

        if(state==null)
            throw new IllegalStateException(
                "Achievement objective disappeared "+
                binding.objectiveKey
            );

        if(state.goal!=binding.row.goal)
            throw new IllegalStateException(
                "Achievement semantic goal drift objective="+
                binding.objectiveKey+
                " displayed="+
                binding.row.goal+
                " semantic="+
                state.goal
            );

        return new RowSnapshot(
            binding,
            state
        );
    }

    private void requireConfigured(){
        if(bindings.size()!=
                AchievementChapterBootstrapCatalog
                    .size())
            throw new IllegalStateException(
                "Achievement chapter bindings not configured"
            );
    }
}
