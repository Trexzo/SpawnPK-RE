package spk.local;

import java.util.*;

/**
 * Concrete Bloodcore Token Synthesis application over RecipeCatalog and
 * ConversionService.
 *
 * Exact-current client evidence proves the application shell and ITEM input
 * surface, but recipes, yields, economics, RNG and the second item surface
 * remain caller-owned.
 */
final class BloodcoreSynthesisService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum Control {
        START,
        SHOP,
        GUIDE,
        LOTTO
    }

    enum AuxiliaryIntent {
        SHOP,
        GUIDE,
        LOTTO
    }

    static final class AuxiliaryResult {
        final AuxiliaryIntent intent;
        final boolean succeeded;
        final String detail;

        private AuxiliaryResult(
            AuxiliaryIntent intent,
            boolean succeeded,
            String detail
        ){
            this.intent=
                Objects.requireNonNull(
                    intent,
                    "intent"
                );
            this.succeeded=succeeded;
            this.detail=
                detail==null
                    ?""
                    :detail.trim();
        }

        static AuxiliaryResult success(
            AuxiliaryIntent intent
        ){
            return new AuxiliaryResult(
                intent,
                true,
                ""
            );
        }

        static AuxiliaryResult success(
            AuxiliaryIntent intent,
            String detail
        ){
            return new AuxiliaryResult(
                intent,
                true,
                detail
            );
        }

        static AuxiliaryResult failure(
            AuxiliaryIntent intent,
            String detail
        ){
            if(detail==null||
               detail.trim().isEmpty())
                throw new IllegalArgumentException(
                    "detail blank"
                );

            return new AuxiliaryResult(
                intent,
                false,
                detail
            );
        }
    }

    interface AuxiliaryExecutor {
        AuxiliaryResult execute(
            String playerRef,
            AuxiliaryIntent intent,
            AtomicTransactionService.SourceAuthority
                policyAuthority
        );
    }

    static final class Snapshot {
        final List<Control> controls;
        final List<RecipeId> recipeIds;
        final List<ConversionService.Snapshot>
            attempts;
        final AtomicTransactionService.SourceAuthority
            policyAuthority;
        final String presentationAuthority;

        Snapshot(
            Collection<RecipeId> recipeIds,
            Collection<ConversionService.Snapshot>
                attempts,
            AtomicTransactionService.SourceAuthority
                policyAuthority
        ){
            this.controls=
                Collections.unmodifiableList(
                    Arrays.asList(
                        Control.values()
                    )
                );

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
    private final AuxiliaryExecutor auxiliaryExecutor;
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

    BloodcoreSynthesisService(
        RecipeCatalog catalog,
        ConversionService conversions,
        AuxiliaryExecutor auxiliaryExecutor,
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
        this.auxiliaryExecutor=
            Objects.requireNonNull(
                auxiliaryExecutor,
                "auxiliaryExecutor"
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
                "Bloodcore synthesis first policy requires CUSTOM_LOCALLAB authority actual="+
                policyAuthority
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
                "Bloodcore synthesis recipe authority mismatch "+
                checked.id
            );

        for(RecipeDefinition.RecipeIngredient input:
                checked.inputs)
            if(input.kind!=
                    RecipeDefinition.AssetKind.ITEM)
                throw new IllegalArgumentException(
                    "Bloodcore synthesis input must be ITEM "+
                    checked.id+
                    " input="+
                    input.identityKey()
                );

        synchronized(this){
            if(recipes.containsKey(
                    checked.id))
                throw new IllegalStateException(
                    "duplicate Bloodcore synthesis recipe "+
                    checked.id
                );
        }

        // RecipeCatalog owns its own lock; never nest it under this wrapper.
        catalog.define(checked);

        synchronized(this){
            if(recipes.putIfAbsent(
                    checked.id,
                    checked)!=null)
                throw new IllegalStateException(
                    "duplicate Bloodcore synthesis recipe "+
                    checked.id
                );
        }

        return checked;
    }

    ConversionService.RecipeAttemptId
        startSynthesis(
            String playerRef,
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
                    "recipe not registered for Bloodcore synthesis "+
                    id
                );
        }

        ConversionService.RecipeAttemptId attemptId=
            conversions.createAttempt(
                playerRef,
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

    AuxiliaryResult requestAuxiliary(
        String playerRef,
        AuxiliaryIntent intent
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        AuxiliaryIntent checked=
            Objects.requireNonNull(
                intent,
                "intent"
            );

        AuxiliaryResult result=
            Objects.requireNonNull(
                auxiliaryExecutor.execute(
                    player,
                    checked,
                    policyAuthority
                ),
                "auxiliary result"
            );

        if(result.intent!=checked)
            throw new IllegalStateException(
                "auxiliary result intent mismatch expected="+
                checked+
                " actual="+
                result.intent
            );

        return result;
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
            recipeIds.addAll(
                recipes.keySet()
            );
            attemptIds.addAll(
                attempts.keySet()
            );
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
                    "Bloodcore synthesis attempt disappeared "+
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
                    "attempt not owned by Bloodcore synthesis "+
                    id
                );

            return recipeId;
        }
    }

    private static String normalizePlayer(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "playerRef"
            );

        String normalized=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "playerRef blank"
            );

        return normalized;
    }
}
