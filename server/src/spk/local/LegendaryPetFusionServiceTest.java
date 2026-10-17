package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class LegendaryPetFusionServiceTest {
    private static final AtomicTransactionService.SourceAuthority
        POLICY=
            AtomicTransactionService
                .SourceAuthority
                .CUSTOM_LOCALLAB;

    public static void main(String[] args){
        RecipeCatalog catalog=
            new RecipeCatalog();

        RecipeDefinition success=
            recipe(
                "fusion:success",
                "asset:success:input",
                "asset:success:output",
                POLICY
            );
        RecipeDefinition failure=
            recipe(
                "fusion:failure",
                "asset:failure:input",
                "asset:failure:output",
                POLICY
            );
        RecipeDefinition replacement=
            recipe(
                "fusion:replacement",
                "asset:replacement:input",
                "asset:replacement:output",
                POLICY
            );
        RecipeDefinition wrong=
            recipe(
                "fusion:wrong",
                "asset:wrong:input",
                "asset:wrong:output",
                AtomicTransactionService
                    .SourceAuthority
                    .EXACT_CURRENT_CLIENT
            );

        catalog.define(success);
        catalog.define(failure);
        catalog.define(replacement);
        catalog.define(wrong);

        AtomicTransactionService transactions=
            new AtomicTransactionService();

        ConversionService conversions=
            new ConversionService(
                catalog,
                transactions,
                (actor,recipe)->
                    "player:blocked"
                        .equals(actor)
                        ?ConversionService
                            .EligibilityDecision
                            .deny(
                                "blocked"
                            )
                        :ConversionService
                            .EligibilityDecision
                            .allow(),
                (actor,recipe,attempt)->
                    recipe.id.equals(
                        failure.id
                    )
                        ?ConversionService
                            .OutcomeResolution
                            .failure(
                                ConversionService
                                    .InputSettlement
                                    .CANCEL_RESERVED_INPUTS
                            )
                        :ConversionService
                            .OutcomeResolution
                            .success(
                                ConversionService
                                    .InputSettlement
                                    .COMMIT_RESERVED_INPUTS
                            )
            );

        LegendaryPetFusionService service=
            new LegendaryPetFusionService(
                catalog,
                conversions,
                POLICY
            );

        LegendaryPetFusionService.Offering
            successOffering=
                offering(
                    "offering:success",
                    success.id
                );

        service.replaceOffering(
            successOffering
        );

        require(
            service.snapshot()
                .currentOffering
                .recipeId.equals(
                    success.id
                )&&
            LegendaryPetFusionService
                .PRESENTATION_AUTHORITY
                .equals(
                    service.snapshot()
                        .presentationAuthority
                ),
            "Legendary Pet Fusion offering"
        );

        authorityGuard(
            service,
            wrong.id
        );

        int before=
            service.attemptCount();

        expect(
            IllegalStateException.class,
            ()->service.beginFusion(
                "player:blocked"
            ),
            "blocked Legendary Pet Fusion"
        );

        require(
            service.attemptCount()==before,
            "blocked fusion created app attempt"
        );

        ConversionService.RecipeAttemptId
            attempt=
                service.beginFusion(
                    " Player:Alice "
                );

        LegendaryPetFusionService.AttemptSnapshot
            created=
                service.getAttempt(
                    attempt
                );

        require(
            "player:alice".equals(
                created.playerRef
            )&&
            "offering:success".equals(
                created.offeringKey
            )&&
            created.recipeId.equals(
                success.id
            )&&
            created.presentationState==
                LegendaryPetFusionService
                    .PresentationState
                    .PREPARATION,
            "Legendary Pet Fusion attempt capture"
        );

        expect(
            IllegalStateException.class,
            ()->service.replaceOffering(
                offering(
                    "offering:replacement",
                    replacement.id
                )
            ),
            "active fusion allowed offering replacement"
        );

        expect(
            IllegalStateException.class,
            service::clearOffering,
            "active fusion allowed offering clear"
        );

        AtomicTransactionService.TransactionId tx=
            reserve(
                transactions,
                "player:alice",
                "asset:success:input",
                "tx:fusion:success"
            );

        require(
            service.reserveInputs(
                "player:alice",
                tx
            ).presentationState==
                LegendaryPetFusionService
                    .PresentationState
                    .PREPARATION,
            "Legendary Pet Fusion reservation"
        );

        LegendaryPetFusionService.AttemptSnapshot
            resolved=
                service.resolveOutcome(
                    "player:alice"
                );

        require(
            resolved.presentationState==
                LegendaryPetFusionService
                    .PresentationState.SUCCESS&&
            resolved.conversion.outcome==
                ConversionService
                    .OutcomeKind.SUCCESS,
            "Legendary Pet Fusion success"
        );

        expect(
            IllegalStateException.class,
            ()->service.resetResult(
                "player:alice"
            ),
            "fusion result reset before settlement"
        );

        transactions.commit(tx);

        require(
            service.acknowledgeSettlement(
                "player:alice"
            )&&
            service.getPlayer(
                "player:alice"
            ).presentationState==
                LegendaryPetFusionService
                    .PresentationState.SUCCESS,
            "Legendary Pet Fusion settlement"
        );

        LegendaryPetFusionService.Snapshot replaced=
            service.replaceOffering(
                offering(
                    "offering:failure",
                    failure.id
                )
            );

        require(
            "offering:failure".equals(
                replaced.currentOffering
                    .offeringKey
            )&&
            service.getAttempt(
                attempt
            ).recipeId.equals(
                success.id
            ),
            "terminal replacement / prior attempt stability"
        );

        service.resetResult(
            "player:alice"
        );

        failureFlow(
            service,
            transactions,
            failure.id
        );

        cancellationFlow(
            service,
            transactions,
            replacement.id
        );

        immutableSnapshots(service);
        protocolBoundary();

        System.out.println(
            "LEGENDARY_PET_FUSION_SERVICE_PASS "+
            "singleOffering=true "+
            "recipeAuthorityFence=true "+
            "normalizedPlayerIdentity=true "+
            "delegateFailureUntracked=true "+
            "attemptBindingStable=true "+
            "activeAttemptBlocksReplacement=true "+
            "terminalAttemptAllowsReplacement=true "+
            "preparationState=true "+
            "successState=true "+
            "failureState=true "+
            "cancellationIdle=true "+
            "settlementDelegation=true "+
            "legacyDefaultsHardcoded=false "+
            "rngHardcoded=false "+
            "protocolIndependent=true"
        );
    }

    private static void failureFlow(
        LegendaryPetFusionService service,
        AtomicTransactionService transactions,
        RecipeId failureRecipe
    ){
        service.beginFusion(
            "player:failure"
        );

        AtomicTransactionService.TransactionId tx=
            reserve(
                transactions,
                "player:failure",
                "asset:failure:input",
                "tx:fusion:failure"
            );

        service.reserveInputs(
            "player:failure",
            tx
        );

        LegendaryPetFusionService.AttemptSnapshot
            failed=
                service.resolveOutcome(
                    "player:failure"
                );

        require(
            failed.recipeId.equals(
                failureRecipe
            )&&
            failed.presentationState==
                LegendaryPetFusionService
                    .PresentationState.FAILURE,
            "Legendary Pet Fusion failure state"
        );

        transactions.cancel(tx);

        require(
            service.acknowledgeSettlement(
                "player:failure"
            )&&
            service.getPlayer(
                "player:failure"
            ).presentationState==
                LegendaryPetFusionService
                    .PresentationState.FAILURE,
            "Legendary Pet Fusion failure settlement"
        );

        service.resetResult(
            "player:failure"
        );
    }

    private static void cancellationFlow(
        LegendaryPetFusionService service,
        AtomicTransactionService transactions,
        RecipeId replacementRecipe
    ){
        service.replaceOffering(
            offering(
                "offering:replacement",
                replacementRecipe
            )
        );

        service.beginFusion(
            "player:cancel"
        );

        require(
            service.cancelFusion(
                "player:cancel"
            )&&
            !service.cancelFusion(
                "player:cancel"
            )&&
            service.getPlayer(
                "player:cancel"
            ).presentationState==
                LegendaryPetFusionService
                    .PresentationState.IDLE,
            "Legendary Pet Fusion created cancellation"
        );

        service.resetResult(
            "player:cancel"
        );

        service.beginFusion(
            "player:cancel"
        );

        AtomicTransactionService.TransactionId tx=
            reserve(
                transactions,
                "player:cancel",
                "asset:replacement:input",
                "tx:fusion:cancel"
            );

        service.reserveInputs(
            "player:cancel",
            tx
        );

        expect(
            IllegalStateException.class,
            ()->service.cancelFusion(
                "player:cancel"
            ),
            "reserved fusion cancelled before transaction"
        );

        transactions.cancel(tx);

        require(
            service.cancelFusion(
                "player:cancel"
            )&&
            service.getPlayer(
                "player:cancel"
            ).presentationState==
                LegendaryPetFusionService
                    .PresentationState.IDLE,
            "Legendary Pet Fusion reserved cancellation"
        );
    }

    private static void authorityGuard(
        LegendaryPetFusionService service,
        RecipeId wrongRecipe
    ){
        LegendaryPetFusionService.Offering before=
            service.snapshot()
                .currentOffering;

        expect(
            IllegalArgumentException.class,
            ()->service.replaceOffering(
                offering(
                    "offering:wrong",
                    wrongRecipe
                )
            ),
            "Legendary Pet Fusion wrong recipe authority"
        );

        require(
            service.snapshot()
                .currentOffering==before,
            "failed offering replacement mutated current offering"
        );
    }

    private static LegendaryPetFusionService.Offering
        offering(
            String key,
            RecipeId recipeId
        ){
        return new LegendaryPetFusionService
            .Offering(
                key,
                "Caller-defined legendary fusion",
                recipeId,
                "Caller-defined status",
                "Caller-defined availability",
                POLICY
            );
    }

    private static RecipeDefinition recipe(
        String id,
        String input,
        String output,
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
                        input,
                        1L
                    )
            ),
            Collections.singletonList(
                new RecipeDefinition
                    .RecipeOutput(
                        RecipeDefinition
                            .AssetKind.ITEM,
                        output,
                        1L
                    )
            ),
            authority
        );
    }

    private static AtomicTransactionService.TransactionId
        reserve(
            AtomicTransactionService transactions,
            String owner,
            String semanticKey,
            String purpose
        ){
        AtomicTransactionService.TransactionId tx=
            transactions.create(
                owner,
                purpose,
                POLICY
            );

        transactions.reserve(
            tx,
            Collections.singletonList(
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    semanticKey,
                    1L,
                    owner,
                    POLICY
                )
            )
        );

        return tx;
    }

    private static void immutableSnapshots(
        LegendaryPetFusionService service
    ){
        boolean immutable=false;

        try{
            service.snapshot()
                .players.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Legendary Pet Fusion snapshot mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                LegendaryPetFusionService.class,
                LegendaryPetFusionService.Offering.class,
                LegendaryPetFusionService.AttemptSnapshot.class,
                LegendaryPetFusionService.PlayerSnapshot.class
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
                   name.contains("itemid")||
                   name.contains("chance")||
                   name.contains("random")||
                   name.contains("rng")||
                   name.contains("legacy")||
                   name.contains("eta")||
                   name.contains("date"))
                    throw new AssertionError(
                        "protocol/legacy/RNG identity leaked into Legendary Pet Fusion "+
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

    private LegendaryPetFusionServiceTest(){}
}
