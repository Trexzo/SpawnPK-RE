package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class BloodcoreSynthesisServiceTest {
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

        ArrayList<String> auxiliaryCalls=
            new ArrayList<>();

        final BloodcoreSynthesisService[] holder=
            new BloodcoreSynthesisService[1];

        BloodcoreSynthesisService service=
            new BloodcoreSynthesisService(
                catalog,
                conversions,
                (player,intent,authority)->{
                    if(holder[0]!=null)
                        holder[0].recipeCount();

                    auxiliaryCalls.add(
                        player+"|"+
                        intent+"|"+
                        authority
                    );

                    if(intent==
                            BloodcoreSynthesisService
                                .AuxiliaryIntent.SHOP)
                        return BloodcoreSynthesisService
                            .AuxiliaryResult.failure(
                                intent,
                                "caller-defined shop unavailable"
                            );

                    return BloodcoreSynthesisService
                        .AuxiliaryResult.success(
                            intent
                        );
                },
                POLICY
            );

        holder[0]=service;

        authorityFence(
            catalog,
            conversions
        );

        RecipeDefinition synthesisRecipe=
            recipe(
                "bloodcore:synthesis:test",
                POLICY
            );

        service.registerRecipe(
            synthesisRecipe
        );

        require(
            service.recipeCount()==1&&
            catalog.size()==1&&
            service.getRecipe(
                synthesisRecipe.id
            )==synthesisRecipe,
            "Bloodcore synthesis registration"
        );

        registrationGuards(
            service,
            catalog
        );

        BloodcoreSynthesisService.Snapshot initial=
            service.snapshot();

        require(
            initial.controls.equals(
                Arrays.asList(
                    BloodcoreSynthesisService
                        .Control.START,
                    BloodcoreSynthesisService
                        .Control.SHOP,
                    BloodcoreSynthesisService
                        .Control.GUIDE,
                    BloodcoreSynthesisService
                        .Control.LOTTO
                )
            )&&
            initial.recipeIds.size()==1&&
            initial.attempts.isEmpty()&&
            initial.policyAuthority==POLICY&&
            BloodcoreSynthesisService
                .PRESENTATION_AUTHORITY
                .equals(
                    initial
                        .presentationAuthority
                ),
            "exact Bloodcore application controls"
        );

        expect(
            UnsupportedOperationException.class,
            ()->initial.controls.clear(),
            "Bloodcore controls mutable"
        );

        RecipeDefinition externalRecipe=
            new RecipeDefinition(
                RecipeId.of(
                    "external:not-bloodcore"
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
            ()->service.startSynthesis(
                "player:alice",
                externalRecipe.id
            ),
            "unregistered Bloodcore recipe started"
        );

        require(
            service.attemptCount()==0,
            "rejected recipe created Bloodcore attempt"
        );

        ConversionService.RecipeAttemptId attempt=
            service.startSynthesis(
                " Player:Alice ",
                synthesisRecipe.id
            );

        require(
            service.attemptCount()==1&&
            service.getAttempt(attempt).state==
                ConversionService
                    .AttemptState.CREATED,
            "Bloodcore synthesis attempt"
        );

        AtomicTransactionService.TransactionId good=
            transactions.create(
                " Player:Alice ",
                "bloodcore:good",
                POLICY
            );

        transactions.reserve(
            good,
            Arrays.asList(
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:synthesis-input-a",
                    2L,
                    " Player:Alice ",
                    POLICY
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:synthesis-input-b",
                    3L,
                    " Player:Alice ",
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
            "Bloodcore synthesis reserve"
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
            "generic:caller-defined-output"
                .equals(
                    resolved.outputs
                        .get(0)
                        .semanticKey
                ),
            "Bloodcore caller-owned synthesis outcome"
        );

        expect(
            IllegalStateException.class,
            ()->service.acknowledgeSettlement(
                attempt
            ),
            "Bloodcore settlement before external commit"
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
            "Bloodcore synthesis settlement"
        );

        BloodcoreSynthesisService.AuxiliaryResult guide=
            service.requestAuxiliary(
                "PLAYER:ALICE",
                BloodcoreSynthesisService
                    .AuxiliaryIntent.GUIDE
            );

        BloodcoreSynthesisService.AuxiliaryResult lotto=
            service.requestAuxiliary(
                "player:alice",
                BloodcoreSynthesisService
                    .AuxiliaryIntent.LOTTO
            );

        BloodcoreSynthesisService.AuxiliaryResult shop=
            service.requestAuxiliary(
                "player:alice",
                BloodcoreSynthesisService
                    .AuxiliaryIntent.SHOP
            );

        require(
            guide.succeeded&&
            lotto.succeeded&&
            !shop.succeeded&&
            "caller-defined shop unavailable"
                .equals(shop.detail)&&
            auxiliaryCalls.equals(
                Arrays.asList(
                    "player:alice|GUIDE|"+
                        POLICY,
                    "player:alice|LOTTO|"+
                        POLICY,
                    "player:alice|SHOP|"+
                        POLICY
                )
            ),
            "Bloodcore auxiliary intent delegation"
        );

        ConversionService.RecipeAttemptId cancelled=
            service.startSynthesis(
                "player:bob",
                synthesisRecipe.id
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
            "Bloodcore synthesis cancellation"
        );

        BloodcoreSynthesisService.Snapshot snapshot=
            service.snapshot();

        require(
            snapshot.recipeIds.size()==1&&
            snapshot.attempts.size()==2,
            "Bloodcore synthesis snapshot"
        );

        expect(
            UnsupportedOperationException.class,
            ()->snapshot.recipeIds.clear(),
            "Bloodcore recipes mutable"
        );

        protocolBoundary();
        mechanicsBoundary();
        externalCompositionNotMethodSynchronized();

        System.out.println(
            "BLOODCORE_SYNTHESIS_SERVICE_PASS "+
            "exactControls=4 "+
            "start=true "+
            "shop=true "+
            "guide=true "+
            "lotto=true "+
            "itemInputsOnly=true "+
            "registeredRecipeOnly=true "+
            "conversionDelegation=true "+
            "exactEscrowCoverage=true "+
            "auxiliaryIntentDelegation=true "+
            "outcomeCallerOwned=true "+
            "secondItemRoleOwned=false "+
            "yieldEconomicsOwned=false "+
            "rngOwned=false "+
            "inputContainerMutationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void authorityFence(
        RecipeCatalog catalog,
        ConversionService conversions
    ){
        BloodcoreSynthesisService.AuxiliaryExecutor
            auxiliary=
                (player,intent,authority)->
                    BloodcoreSynthesisService
                        .AuxiliaryResult
                        .success(intent);

        expect(
            IllegalArgumentException.class,
            ()->new BloodcoreSynthesisService(
                catalog,
                conversions,
                auxiliary,
                AtomicTransactionService
                    .SourceAuthority
                    .EXACT_CURRENT_CLIENT
            ),
            "exact client used as Bloodcore gameplay policy"
        );

        expect(
            IllegalArgumentException.class,
            ()->new BloodcoreSynthesisService(
                catalog,
                conversions,
                auxiliary,
                AtomicTransactionService
                    .SourceAuthority
                    .UNKNOWN_SERVER_AUTHORITY
            ),
            "unknown authority used as Bloodcore gameplay policy"
        );
    }

    private static void registrationGuards(
        BloodcoreSynthesisService service,
        RecipeCatalog catalog
    ){
        int catalogBefore=
            catalog.size();

        RecipeDefinition wrongAuthority=
            recipe(
                "bloodcore:wrong-authority",
                AtomicTransactionService
                    .SourceAuthority
                    .EXACT_CURRENT_CLIENT
            );

        expect(
            IllegalArgumentException.class,
            ()->service.registerRecipe(
                wrongAuthority
            ),
            "Bloodcore recipe authority mismatch"
        );

        RecipeDefinition currencyInput=
            new RecipeDefinition(
                RecipeId.of(
                    "bloodcore:currency-input"
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
            "non-item Bloodcore input"
        );

        require(
            service.recipeCount()==1&&
            catalog.size()==catalogBefore,
            "failed Bloodcore registration mutated catalog"
        );
    }

    private static RecipeDefinition recipe(
        String id,
        AtomicTransactionService.SourceAuthority
            authority
    ){
        return new RecipeDefinition(
            RecipeId.of(id),
            Arrays.asList(
                new RecipeDefinition
                    .RecipeIngredient(
                        RecipeDefinition
                            .AssetKind.ITEM,
                        "item:synthesis-input-a",
                        2L
                    ),
                new RecipeDefinition
                    .RecipeIngredient(
                        RecipeDefinition
                            .AssetKind.ITEM,
                        "item:synthesis-input-b",
                        3L
                    )
            ),
            Collections.singletonList(
                new RecipeDefinition
                    .RecipeOutput(
                        RecipeDefinition
                            .AssetKind.GENERIC,
                        "generic:caller-defined-output",
                        1L
                    )
            ),
            authority
        );
    }

    private static void externalCompositionNotMethodSynchronized(){
        for(String name:new String[]{
                "registerRecipe",
                "startSynthesis",
                "requestAuxiliary",
                "reserveInputs",
                "resolveOutcome",
                "acknowledgeSettlement",
                "cancelAttempt",
                "getAttempt",
                "snapshot"
        }){
            boolean found=false;

            for(Method method:
                    BloodcoreSynthesisService.class
                        .getDeclaredMethods()){
                if(!method.getName().equals(name))
                    continue;

                found=true;
                require(
                    !Modifier.isSynchronized(
                        method.getModifiers()
                    ),
                    "Bloodcore external method still synchronized "+
                    name
                );
            }

            require(found,"Bloodcore method missing "+name);
        }
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                BloodcoreSynthesisService.class,
                BloodcoreSynthesisService.Snapshot.class,
                BloodcoreSynthesisService
                    .AuxiliaryResult.class
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
                   name.contains("root")||
                   name.contains("containerid"))
                    throw new AssertionError(
                        "protocol identity leaked into Bloodcore synthesis "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void mechanicsBoundary(){
        for(Field field:
                BloodcoreSynthesisService.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("yield")||
               name.contains("chance")||
               name.contains("cooldown")||
               name.contains("seconditem")||
               name.contains("tokenprice"))
                throw new AssertionError(
                    "unrecovered Bloodcore mechanics leaked into field "+
                    field.getName()
                );
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

    private BloodcoreSynthesisServiceTest(){}
}
