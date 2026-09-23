package spk.local;

import java.util.*;

/**
 * Concrete Blood Diamond Fuser application composition over RecipeCatalog and
 * ConversionService.
 *
 * Exact-current v308 proves three independent fuse rows and a cycle-items
 * control. Recipe bindings, costs, outcomes and exact cycling semantics remain
 * server authority. This implementation uses explicit caller-defined cycles
 * and deterministic next/wrap LocalLab policy.
 */
final class BloodDiamondFuserService {
    static final int ROW_COUNT=3;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final class CycleSpec {
        final String cycleKey;
        final List<RecipeId> recipeIds;

        CycleSpec(
            String cycleKey,
            Collection<RecipeId> recipeIds
        ){
            this.cycleKey=
                MatchRules.normalizeKey(
                    cycleKey,
                    "cycleKey"
                );

            Objects.requireNonNull(
                recipeIds,
                "recipeIds"
            );

            if(recipeIds.size()!=ROW_COUNT)
                throw new IllegalArgumentException(
                    "Blood Diamond Fuser cycle recipes="+
                    recipeIds.size()+
                    " expected="+ROW_COUNT
                );

            ArrayList<RecipeId> copy=
                new ArrayList<>();
            HashSet<RecipeId> unique=
                new HashSet<>();

            for(RecipeId recipeId:
                    recipeIds){
                RecipeId checked=
                    Objects.requireNonNull(
                        recipeId,
                        "recipeId"
                    );

                if(!unique.add(checked))
                    throw new IllegalArgumentException(
                        "duplicate Blood Diamond Fuser recipe "+
                        checked
                    );

                copy.add(checked);
            }

            this.recipeIds=
                Collections.unmodifiableList(
                    copy
                );
        }

        RecipeId recipeForRow(int rowIndex){
            requireRow(rowIndex);
            return recipeIds.get(rowIndex);
        }
    }

    static final class CycleSnapshot {
        final String cycleKey;
        final List<RecipeId> recipeIds;

        CycleSnapshot(CycleSpec spec){
            this.cycleKey=spec.cycleKey;
            this.recipeIds=spec.recipeIds;
        }

        RecipeId recipeForRow(int rowIndex){
            requireRow(rowIndex);
            return recipeIds.get(rowIndex);
        }
    }

    static final class AttemptSnapshot {
        final ConversionService.RecipeAttemptId attemptId;
        final String cycleKey;
        final int rowIndex;
        final RecipeId recipeId;
        final ConversionService.Snapshot conversion;

        AttemptSnapshot(
            AttemptBinding binding,
            ConversionService.Snapshot conversion
        ){
            this.attemptId=binding.attemptId;
            this.cycleKey=binding.cycleKey;
            this.rowIndex=binding.rowIndex;
            this.recipeId=binding.recipeId;
            this.conversion=
                Objects.requireNonNull(
                    conversion,
                    "conversion"
                );
        }
    }

    static final class Snapshot {
        final String selectedCycleKey;
        final List<CycleSnapshot> cycles;
        final List<AttemptSnapshot> attempts;
        final AtomicTransactionService.SourceAuthority
            policyAuthority;
        final String presentationAuthority;

        Snapshot(
            String selectedCycleKey,
            Collection<CycleSnapshot> cycles,
            Collection<AttemptSnapshot> attempts,
            AtomicTransactionService.SourceAuthority
                policyAuthority
        ){
            this.selectedCycleKey=
                selectedCycleKey;
            this.cycles=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        cycles
                    )
                );
            this.attempts=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        attempts
                    )
                );
            this.policyAuthority=
                policyAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        CycleSnapshot selectedCycle(){
            if(selectedCycleKey==null)
                return null;

            for(CycleSnapshot cycle:cycles)
                if(cycle.cycleKey.equals(
                        selectedCycleKey))
                    return cycle;

            throw new IllegalStateException(
                "selected Blood Diamond Fuser cycle missing "+
                selectedCycleKey
            );
        }
    }

    private static final class AttemptBinding {
        final ConversionService.RecipeAttemptId attemptId;
        final String cycleKey;
        final int rowIndex;
        final RecipeId recipeId;

        AttemptBinding(
            ConversionService.RecipeAttemptId attemptId,
            String cycleKey,
            int rowIndex,
            RecipeId recipeId
        ){
            this.attemptId=attemptId;
            this.cycleKey=cycleKey;
            this.rowIndex=rowIndex;
            this.recipeId=recipeId;
        }
    }

    private final RecipeCatalog catalog;
    private final ConversionService conversions;
    private final AtomicTransactionService.SourceAuthority
        policyAuthority;

    private final LinkedHashMap<String,CycleSpec>
        cycles=
            new LinkedHashMap<>();

    private final LinkedHashMap<
        ConversionService.RecipeAttemptId,
        AttemptBinding
    > attempts=
        new LinkedHashMap<>();

    private String selectedCycleKey;

    BloodDiamondFuserService(
        RecipeCatalog catalog,
        ConversionService conversions,
        AtomicTransactionService.SourceAuthority
            policyAuthority
    ){
        this.catalog=
            Objects.requireNonNull(
                catalog,
                "catalog"
            );
        this.conversions=
            Objects.requireNonNull(
                conversions,
                "conversions"
            );
        this.policyAuthority=
            Objects.requireNonNull(
                policyAuthority,
                "policyAuthority"
            );
    }

    CycleSnapshot registerCycle(
        CycleSpec spec
    ){
        CycleSpec checked=
            Objects.requireNonNull(
                spec,
                "spec"
            );

        synchronized(this){
            if(cycles.containsKey(
                    checked.cycleKey))
                throw new IllegalStateException(
                    "duplicate Blood Diamond Fuser cycle "+
                    checked.cycleKey
                );
        }

        /*
         * RecipeCatalog is shared state. Validate the immutable recipe
         * definitions without holding the application monitor.
         */
        for(RecipeId recipeId:
                checked.recipeIds){
            RecipeDefinition recipe=
                catalog.require(recipeId);

            if(recipe.sourceAuthority!=
                    policyAuthority)
                throw new IllegalArgumentException(
                    "Blood Diamond Fuser recipe authority mismatch "+
                    recipeId
                );
        }

        synchronized(this){
            if(cycles.containsKey(
                    checked.cycleKey))
                throw new IllegalStateException(
                    "duplicate Blood Diamond Fuser cycle "+
                    checked.cycleKey
                );

            cycles.put(
                checked.cycleKey,
                checked
            );

            if(selectedCycleKey==null)
                selectedCycleKey=
                    checked.cycleKey;
        }

        return new CycleSnapshot(checked);
    }

    synchronized CycleSnapshot selectCycle(
        String cycleKey
    ){
        String key=
            MatchRules.normalizeKey(
                cycleKey,
                "cycleKey"
            );

        CycleSpec cycle=
            cycles.get(key);

        if(cycle==null)
            throw new IllegalArgumentException(
                "unknown Blood Diamond Fuser cycle "+
                key
            );

        selectedCycleKey=key;
        return new CycleSnapshot(cycle);
    }

    /**
     * Explicit LocalLab policy for the recovered cycle-items intent.
     *
     * Registration order defines the cycle order and advancing from the final
     * entry wraps to the first. This is not claimed original SpawnPK policy.
     */
    synchronized CycleSnapshot advanceCycle(){
        if(cycles.isEmpty())
            throw new IllegalStateException(
                "Blood Diamond Fuser has no cycles"
            );

        ArrayList<String> keys=
            new ArrayList<>(
                cycles.keySet()
            );

        if(selectedCycleKey==null){
            selectedCycleKey=keys.get(0);
            return new CycleSnapshot(
                cycles.get(
                    selectedCycleKey
                )
            );
        }

        int index=
            keys.indexOf(
                selectedCycleKey
            );

        if(index<0)
            throw new IllegalStateException(
                "selected Blood Diamond Fuser cycle missing "+
                selectedCycleKey
            );

        selectedCycleKey=
            keys.get(
                (index+1)%
                    keys.size()
            );

        return new CycleSnapshot(
            cycles.get(
                selectedCycleKey
            )
        );
    }

    ConversionService.RecipeAttemptId
        createAttempt(
            String actorRef,
            int rowIndex
        ){
        requireRow(rowIndex);

        final String cycleKey;
        final RecipeId recipeId;

        synchronized(this){
            CycleSpec selected=
                requireSelectedCycle();

            cycleKey=selected.cycleKey;
            recipeId=
                selected.recipeForRow(
                    rowIndex
                );
        }

        ConversionService.RecipeAttemptId attemptId=
            conversions.createAttempt(
                actorRef,
                recipeId
            );

        synchronized(this){
            attempts.put(
                attemptId,
                new AttemptBinding(
                    attemptId,
                    cycleKey,
                    rowIndex,
                    recipeId
                )
            );
        }

        return attemptId;
    }

    ConversionService.Snapshot
        reserveInputs(
            ConversionService.RecipeAttemptId attemptId,
            AtomicTransactionService.TransactionId transactionId
        ){
        synchronized(this){
            requireAttempt(attemptId);
        }

        return conversions.reserveInputs(
            attemptId,
            transactionId
        );
    }

    ConversionService.Snapshot
        resolveOutcome(
            ConversionService.RecipeAttemptId attemptId
        ){
        synchronized(this){
            requireAttempt(attemptId);
        }

        return conversions.resolveOutcome(
            attemptId
        );
    }

    boolean acknowledgeSettlement(
        ConversionService.RecipeAttemptId attemptId
    ){
        synchronized(this){
            requireAttempt(attemptId);
        }

        return conversions.acknowledgeSettlement(
            attemptId
        );
    }

    boolean cancelAttempt(
        ConversionService.RecipeAttemptId attemptId
    ){
        synchronized(this){
            requireAttempt(attemptId);
        }

        return conversions.cancelAttempt(
            attemptId
        );
    }

    AttemptSnapshot getAttempt(
        ConversionService.RecipeAttemptId attemptId
    ){
        final AttemptBinding binding;

        synchronized(this){
            binding=
                attempts.get(
                    Objects.requireNonNull(
                        attemptId,
                        "attemptId"
                    )
                );

            if(binding==null)
                return null;
        }

        ConversionService.Snapshot conversion=
            conversions.get(
                binding.attemptId
            );

        if(conversion==null)
            throw new IllegalStateException(
                "Blood Diamond Fuser conversion attempt disappeared "+
                binding.attemptId
            );

        return new AttemptSnapshot(
            binding,
            conversion
        );
    }

    Snapshot snapshot(){
        final String selected;
        final List<CycleSnapshot> cycleSnapshots=
            new ArrayList<>();
        final List<AttemptBinding> bindings=
            new ArrayList<>();

        synchronized(this){
            selected=selectedCycleKey;

            for(CycleSpec cycle:
                    cycles.values())
                cycleSnapshots.add(
                    new CycleSnapshot(cycle)
                );

            bindings.addAll(
                attempts.values()
            );
        }

        ArrayList<AttemptSnapshot>
            attemptSnapshots=
                new ArrayList<>();

        for(AttemptBinding binding:
                bindings){
            ConversionService.Snapshot conversion=
                conversions.get(
                    binding.attemptId
                );

            if(conversion==null)
                throw new IllegalStateException(
                    "Blood Diamond Fuser conversion attempt disappeared "+
                    binding.attemptId
                );

            attemptSnapshots.add(
                new AttemptSnapshot(
                    binding,
                    conversion
                )
            );
        }

        return new Snapshot(
            selected,
            cycleSnapshots,
            attemptSnapshots,
            policyAuthority
        );
    }

    synchronized int cycleCount(){
        return cycles.size();
    }

    synchronized int attemptCount(){
        return attempts.size();
    }

    private CycleSpec requireSelectedCycle(){
        if(selectedCycleKey==null)
            throw new IllegalStateException(
                "Blood Diamond Fuser has no selected cycle"
            );

        CycleSpec cycle=
            cycles.get(
                selectedCycleKey
            );

        if(cycle==null)
            throw new IllegalStateException(
                "selected Blood Diamond Fuser cycle missing "+
                selectedCycleKey
            );

        return cycle;
    }

    private AttemptBinding requireAttempt(
        ConversionService.RecipeAttemptId attemptId
    ){
        ConversionService.RecipeAttemptId id=
            Objects.requireNonNull(
                attemptId,
                "attemptId"
            );

        AttemptBinding binding=
            attempts.get(id);

        if(binding==null)
            throw new IllegalArgumentException(
                "attempt not owned by Blood Diamond Fuser "+
                id
            );

        return binding;
    }

    private static void requireRow(int rowIndex){
        if(rowIndex<0||
           rowIndex>=ROW_COUNT)
            throw new IllegalArgumentException(
                "Blood Diamond Fuser rowIndex="+
                rowIndex
            );
    }
}
