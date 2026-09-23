package spk.local;

import java.util.*;

/**
 * Concrete Blood Shard Salvaging application wrapper over RecipeCatalog and
 * ConversionService.
 *
 * Exact-current client evidence proves an item-input salvage application, but
 * production recipes, values, outputs and outcome policy remain caller-owned.
 */
final class BloodShardSalvageService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final class Snapshot {
        final List<RecipeId> recipeIds;
        final List<ConversionService.Snapshot> attempts;
        final AtomicTransactionService.SourceAuthority
            policyAuthority;
        final String presentationAuthority;

        Snapshot(
            Collection<RecipeId> recipeIds,
            Collection<ConversionService.Snapshot> attempts,
            AtomicTransactionService.SourceAuthority policyAuthority
        ){
            ArrayList<RecipeId> recipes=
                new ArrayList<>(
                    recipeIds
                );
            recipes.sort(
                Comparator.naturalOrder()
            );

            this.recipeIds=
                Collections.unmodifiableList(
                    recipes
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
    }

    private final RecipeCatalog catalog;
    private final ConversionService conversions;
    private final AtomicTransactionService.SourceAuthority
        policyAuthority;

    private final LinkedHashMap<RecipeId,RecipeDefinition>
        recipes=
            new LinkedHashMap<>();

    private final LinkedHashMap<
        ConversionService.RecipeAttemptId,
        RecipeId
    > attempts=
        new LinkedHashMap<>();

    BloodShardSalvageService(
        RecipeCatalog catalog,
        ConversionService conversions,
        AtomicTransactionService.SourceAuthority policyAuthority
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

    RecipeDefinition registerRecipe(
        RecipeDefinition definition
    ){
        RecipeDefinition checked=
            Objects.requireNonNull(
                definition,
                "definition"
            );

        if(checked.sourceAuthority!=
                policyAuthority)
            throw new IllegalArgumentException(
                "Blood Shard salvage recipe authority mismatch "+
                checked.id
            );

        for(RecipeDefinition.RecipeIngredient input:
                checked.inputs)
            if(input.kind!=
                    RecipeDefinition.AssetKind.ITEM)
                throw new IllegalArgumentException(
                    "Blood Shard salvage input must be ITEM "+
                    checked.id+
                    " input="+
                    input.identityKey()
                );

        synchronized(this){
            if(recipes.containsKey(
                    checked.id))
                throw new IllegalStateException(
                    "duplicate Blood Shard salvage recipe "+
                    checked.id
                );
        }

        catalog.define(checked);

        synchronized(this){
            if(recipes.putIfAbsent(
                    checked.id,
                    checked)!=null)
                throw new IllegalStateException(
                    "duplicate Blood Shard salvage recipe "+
                    checked.id
                );
        }

        return checked;
    }

    ConversionService.RecipeAttemptId createAttempt(
        String actorRef,
        RecipeId recipeId
    ){
        RecipeId id=
            Objects.requireNonNull(
                recipeId,
                "recipeId"
            );

        synchronized(this){
            if(!recipes.containsKey(id))
                throw new IllegalArgumentException(
                    "recipe not registered for Blood Shard salvage "+
                    id
                );
        }

        ConversionService.RecipeAttemptId attemptId=
            conversions.createAttempt(
                actorRef,
                id
            );

        synchronized(this){
            attempts.put(
                attemptId,
                id
            );
        }

        return attemptId;
    }

    ConversionService.Snapshot reserveInputs(
        ConversionService.RecipeAttemptId attemptId,
        AtomicTransactionService.TransactionId transactionId
    ){
        requireOwnedAttempt(
            attemptId
        );
        return conversions.reserveInputs(
            attemptId,
            transactionId
        );
    }

    ConversionService.Snapshot resolveOutcome(
        ConversionService.RecipeAttemptId attemptId
    ){
        requireOwnedAttempt(
            attemptId
        );
        return conversions.resolveOutcome(
            attemptId
        );
    }

    boolean acknowledgeSettlement(
        ConversionService.RecipeAttemptId attemptId
    ){
        requireOwnedAttempt(
            attemptId
        );
        return conversions.acknowledgeSettlement(
            attemptId
        );
    }

    boolean cancelAttempt(
        ConversionService.RecipeAttemptId attemptId
    ){
        requireOwnedAttempt(
            attemptId
        );
        return conversions.cancelAttempt(
            attemptId
        );
    }

    ConversionService.Snapshot getAttempt(
        ConversionService.RecipeAttemptId attemptId
    ){
        ConversionService.RecipeAttemptId id=
            Objects.requireNonNull(
                attemptId,
                "attemptId"
            );

        synchronized(this){
            if(!attempts.containsKey(id))
                return null;
        }

        return conversions.get(id);
    }

    synchronized RecipeDefinition getRecipe(
        RecipeId recipeId
    ){
        return recipes.get(
            Objects.requireNonNull(
                recipeId,
                "recipeId"
            )
        );
    }

    Snapshot snapshot(){
        final ArrayList<RecipeId> recipeIds=
            new ArrayList<>();
        final ArrayList<ConversionService.RecipeAttemptId>
            attemptIds=
                new ArrayList<>();

        synchronized(this){
            recipeIds.addAll(recipes.keySet());
            attemptIds.addAll(attempts.keySet());
        }

        ArrayList<ConversionService.Snapshot>
            attemptSnapshots=
                new ArrayList<>();

        for(ConversionService.RecipeAttemptId id:
                attemptIds){
            ConversionService.Snapshot snapshot=
                conversions.get(id);

            if(snapshot==null)
                throw new IllegalStateException(
                    "Blood Shard salvage attempt disappeared "+
                    id
                );

            attemptSnapshots.add(snapshot);
        }

        return new Snapshot(
            recipeIds,
            attemptSnapshots,
            policyAuthority
        );
    }

    synchronized int recipeCount(){
        return recipes.size();
    }

    synchronized int attemptCount(){
        return attempts.size();
    }

    private RecipeId requireOwnedAttempt(
        ConversionService.RecipeAttemptId attemptId
    ){
        ConversionService.RecipeAttemptId id=
            Objects.requireNonNull(
                attemptId,
                "attemptId"
            );

        synchronized(this){
            RecipeId recipeId=
                attempts.get(id);

            if(recipeId==null)
                throw new IllegalArgumentException(
                    "attempt not owned by Blood Shard salvage "+
                    id
                );

            return recipeId;
        }
    }
}
