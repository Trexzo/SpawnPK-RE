package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class BloodShardSalvageServiceTest {
    private static final AtomicTransactionService.SourceAuthority
        POLICY=
            AtomicTransactionService
                .SourceAuthority
                .CUSTOM_LOCALLAB;

    public static void main(String[] args){
        RecipeCatalog catalog=
            new RecipeCatalog();
        AtomicTransactionService transactions=
            new AtomicTransactionService();

        ConversionService conversions=
            new ConversionService(
                catalog,
                transactions,
                (actor,recipe)->
                    ConversionService
                        .EligibilityDecision
                        .allow(),
                (actor,recipe,attempt)->
                    ConversionService
                        .OutcomeResolution
                        .success(
                            ConversionService
                                .InputSettlement
                                .COMMIT_RESERVED_INPUTS
                        )
            );

        BloodShardSalvageService service=
            new BloodShardSalvageService(
                catalog,
                conversions,
                POLICY
            );

        RecipeDefinition salvageRecipe=
            recipe(
                "blood-shard:salvage:test",
                POLICY
            );

        service.registerRecipe(
            salvageRecipe
        );

        require(
            service.recipeCount()==1&&
            catalog.size()==1&&
            service.getRecipe(
                salvageRecipe.id
            )==salvageRecipe,
            "Blood Shard salvage registration"
        );

        registrationGuards(
            service,
            catalog
        );

        RecipeDefinition externalRecipe=
            new RecipeDefinition(
                RecipeId.of(
                    "external:not-salvage"
                ),
                Collections.singletonList(
                    new RecipeDefinition
                        .RecipeIngredient(
                            RecipeDefinition
                                .AssetKind.ITEM,
                            "item:external",
                            1L
                        )
                ),
                Collections.singletonList(
                    new RecipeDefinition
                        .RecipeOutput(
                            RecipeDefinition
                                .AssetKind.ITEM,
                            "item:external-output",
                            1L
                        )
                ),
                POLICY
            );

        catalog.define(
            externalRecipe
        );

        expect(
            IllegalArgumentException.class,
            ()->service.createAttempt(
                "player:alice",
                externalRecipe.id
            ),
            "unregistered recipe salvaged"
        );

        require(
            service.attemptCount()==0,
            "rejected recipe created salvage attempt"
        );

        ConversionService.RecipeAttemptId attempt=
            service.createAttempt(
                "player:alice",
                salvageRecipe.id
            );

        require(
            service.attemptCount()==1&&
            service.getAttempt(attempt).state==
                ConversionService
                    .AttemptState.CREATED,
            "Blood Shard salvage attempt"
        );

        AtomicTransactionService.TransactionId bad=
            transactions.create(
                "player:alice",
                "salvage:bad",
                POLICY
            );

        transactions.reserve(
            bad,
            Collections.singletonList(
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:salvage-input",
                    1L,
                    "player:alice",
                    POLICY
                )
            )
        );

        expect(
            IllegalStateException.class,
            ()->service.reserveInputs(
                attempt,
                bad
            ),
            "partial salvage input reservation"
        );

        require(
            service.getAttempt(attempt).state==
                ConversionService
                    .AttemptState.CREATED,
            "failed reserve mutated salvage attempt"
        );

        AtomicTransactionService.TransactionId good=
            transactions.create(
                "player:alice",
                "salvage:good",
                POLICY
            );

        transactions.reserve(
            good,
            Collections.singletonList(
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:salvage-input",
                    2L,
                    "player:alice",
                    POLICY
                )
            )
        );

        ConversionService.Snapshot reserved=
            service.reserveInputs(
                attempt,
                good
            );

        require(
            reserved.state==
                ConversionService
                    .AttemptState.RESERVED&&
            reserved.transactionId.equals(good),
            "Blood Shard salvage reserve"
        );

        ConversionService.Snapshot resolved=
            service.resolveOutcome(
                attempt
            );

        require(
            resolved.state==
                ConversionService
                    .AttemptState.RESOLVED&&
            resolved.outcome==
                ConversionService
                    .OutcomeKind.SUCCESS&&
            resolved.outputs.size()==1&&
            "item:caller-defined-output".equals(
                resolved.outputs.get(0)
                    .semanticKey
            ),
            "Blood Shard salvage caller outcome"
        );

        expect(
            IllegalStateException.class,
            ()->service.acknowledgeSettlement(
                attempt
            ),
            "salvage settlement acknowledged before transaction commit"
        );

        transactions.commit(good);

        require(
            service.acknowledgeSettlement(
                attempt
            )&&
            !service.acknowledgeSettlement(
                attempt
            )&&
            service.getAttempt(attempt).state==
                ConversionService
                    .AttemptState.SETTLED,
            "Blood Shard salvage settlement"
        );

        ConversionService.RecipeAttemptId cancelled=
            service.createAttempt(
                "player:bob",
                salvageRecipe.id
            );

        require(
            service.cancelAttempt(
                cancelled
            )&&
            !service.cancelAttempt(
                cancelled
            )&&
            service.getAttempt(cancelled).state==
                ConversionService
                    .AttemptState.CANCELLED,
            "Blood Shard salvage cancellation"
        );

        BloodShardSalvageService.Snapshot snapshot=
            service.snapshot();

        require(
            snapshot.recipeIds.size()==1&&
            snapshot.attempts.size()==2&&
            snapshot.policyAuthority==POLICY&&
            BloodShardSalvageService
                .PRESENTATION_AUTHORITY
                .equals(
                    snapshot
                        .presentationAuthority
                ),
            "Blood Shard salvage snapshot"
        );

        boolean immutable=false;

        try{
            snapshot.recipeIds.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Blood Shard salvage recipes mutable"
        );

        protocolBoundary();

        System.out.println(
            "BLOOD_SHARD_SALVAGE_SERVICE_PASS "+
            "recipeAuthorityFence=true "+
            "itemInputsOnly=true "+
            "failedRegistrationAtomic=true "+
            "registeredRecipeOnly=true "+
            "conversionDelegation=true "+
            "exactEscrowCoverage=true "+
            "outcomeCallerOwned=true "+
            "settlementDelegation=true "+
            "cancellationDelegation=true "+
            "rewardValueHardcoded=false "+
            "protocolIndependent=true"
        );
    }

    private static void registrationGuards(
        BloodShardSalvageService service,
        RecipeCatalog catalog
    ){
        int catalogBefore=
            catalog.size();

        RecipeDefinition wrongAuthority=
            recipe(
                "blood-shard:wrong-authority",
                AtomicTransactionService
                    .SourceAuthority
                    .EXACT_CURRENT_CLIENT
            );

        expect(
            IllegalArgumentException.class,
            ()->service.registerRecipe(
                wrongAuthority
            ),
            "salvage recipe authority mismatch"
        );

        RecipeDefinition currencyInput=
            new RecipeDefinition(
                RecipeId.of(
                    "blood-shard:currency-input"
                ),
                Collections.singletonList(
                    new RecipeDefinition
                        .RecipeIngredient(
                            RecipeDefinition
                                .AssetKind.CURRENCY,
                            "currency:test",
                            1L
                        )
                ),
                Collections.singletonList(
                    new RecipeDefinition
                        .RecipeOutput(
                            RecipeDefinition
                                .AssetKind.ITEM,
                            "item:test-output",
                            1L
                        )
                ),
                POLICY
            );

        expect(
            IllegalArgumentException.class,
            ()->service.registerRecipe(
                currencyInput
            ),
            "non-item salvage input"
        );

        require(
            service.recipeCount()==1&&
            catalog.size()==catalogBefore,
            "failed salvage registration mutated catalog"
        );
    }

    private static RecipeDefinition recipe(
        String id,
        AtomicTransactionService.SourceAuthority
            authority
    ){
        return new RecipeDefinition(
            RecipeId.of(id),
            Collections.singletonList(
                new RecipeDefinition
                    .RecipeIngredient(
                        RecipeDefinition
                            .AssetKind.ITEM,
                        "item:salvage-input",
                        2L
                    )
            ),
            Collections.singletonList(
                new RecipeDefinition
                    .RecipeOutput(
                        RecipeDefinition
                            .AssetKind.ITEM,
                        "item:caller-defined-output",
                        1L
                    )
            ),
            authority
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                BloodShardSalvageService.class,
                BloodShardSalvageService.Snapshot.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("interface")||
                   name.contains("shardvalue")||
                   name.contains("rewardvalue")||
                   name.contains("chance"))
                    throw new AssertionError(
                        "protocol/value identity leaked into Blood Shard salvage "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private BloodShardSalvageServiceTest(){}
}
