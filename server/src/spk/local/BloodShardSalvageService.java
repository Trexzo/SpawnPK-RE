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

    synchronized RecipeDefinition registerRecipe(
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

        if(recipes.containsKey(
                checked.id))
            throw new IllegalStateException(
                "duplicate Blood Shard salvage recipe "+
                checked.id
            );

        // Shared catalog is mutated only after all salvage-side validation.
        // If the catalog rejects, this service remains unchanged.
        catalog.define(checked);

        recipes.put(
            checked.id,
            checked
        );

        return checked;
    }

    synchronized ConversionService.RecipeAttemptId
        createAttempt(
            String actorRef,
            RecipeId recipeId
        ){
        RecipeId id=
            Objects.requireNonNull(
                recipeId,
                "recipeId"
            );

        if(!recipes.containsKey(id))
            throw new IllegalArgumentException(
                "recipe not registered for Blood Shard salvage "+
                id
            );

        ConversionService.RecipeAttemptId attemptId=
            conversions.createAttempt(
                actorRef,
                id
            );

        attempts.put(
            attemptId,
            id
        );

        return attemptId;
    }

    synchronized ConversionService.Snapshot
        reserveInputs(
            ConversionService.RecipeAttemptId attemptId,
            AtomicTransactionService.TransactionId transactionId
        ){
        requireAttempt(attemptId);

        return conversions.reserveInputs(
            attemptId,
            transactionId
        );
    }

    synchronized ConversionService.Snapshot
        resolveOutcome(
            ConversionService.RecipeAttemptId attemptId
        ){
        requireAttempt(attemptId);

        return conversions.resolveOutcome(
            attemptId
        );
    }

    synchronized boolean acknowledgeSettlement(
        ConversionService.RecipeAttemptId attemptId
    ){
        requireAttempt(attemptId);

        return conversions.acknowledgeSettlement(
            attemptId
        );
    }

    synchronized boolean cancelAttempt(
        ConversionService.RecipeAttemptId attemptId
    ){
        requireAttempt(attemptId);

        return conversions.cancelAttempt(
            attemptId
        );
    }

    synchronized ConversionService.Snapshot getAttempt(
        ConversionService.RecipeAttemptId attemptId
    ){
        ConversionService.RecipeAttemptId id=
            Objects.requireNonNull(
                attemptId,
                "attemptId"
            );

        if(!attempts.containsKey(id))
            return null;

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

    synchronized Snapshot snapshot(){
        ArrayList<ConversionService.Snapshot>
            attemptSnapshots=
                new ArrayList<>();

        for(ConversionService.RecipeAttemptId id:
                attempts.keySet()){
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
            recipes.keySet(),
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

    private RecipeId requireAttempt(
        ConversionService.RecipeAttemptId attemptId
    ){
        ConversionService.RecipeAttemptId id=
            Objects.requireNonNull(
                attemptId,
                "attemptId"
            );

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
