package spk.local;

import java.lang.reflect.Field;
import java.util.*;

/** Deterministic regressions for Issue #168 recipe/conversion foundation. */
public final class ConversionServiceTest {
    public static void main(String[] args){
        RecipeDefinition recipe=
            new RecipeDefinition(
                RecipeId.of("custom:fixture-conversion"),
                Arrays.asList(
                    new RecipeDefinition.RecipeIngredient(
                        RecipeDefinition.AssetKind.ITEM,
                        "item:fixture-input",
                        2L
                    ),
                    new RecipeDefinition.RecipeIngredient(
                        RecipeDefinition.AssetKind.CURRENCY,
                        "currency:fixture-token",
                        5L
                    )
                ),
                Collections.singletonList(
                    new RecipeDefinition.RecipeOutput(
                        RecipeDefinition.AssetKind.ITEM,
                        "item:fixture-output",
                        1L
                    )
                ),
                AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB
            );

        expect(
            UnsupportedOperationException.class,
            ()->recipe.inputs.add(
                new RecipeDefinition.RecipeIngredient(
                    RecipeDefinition.AssetKind.ITEM,
                    "item:illegal",
                    1L
                )
            ),
            "recipe input immutability"
        );

        expect(
            UnsupportedOperationException.class,
            ()->recipe.outputs.clear(),
            "recipe output immutability"
        );

        RecipeCatalog catalog=new RecipeCatalog();
        catalog.define(recipe);

        eq(recipe,catalog.get("custom:fixture-conversion"),"catalog lookup");

        expect(
            IllegalStateException.class,
            ()->catalog.define(recipe),
            "duplicate recipe id"
        );

        AtomicTransactionService transactions=
            new AtomicTransactionService();

        final ConversionService[] holder=
            new ConversionService[1];

        ConversionService service=
            new ConversionService(
                catalog,
                transactions,
                (actor,definition)->{
                    if(holder[0]!=null)
                        holder[0].size();

                    return "player:blocked".equals(actor)
                        ?ConversionService.EligibilityDecision.deny(
                            "CUSTOM_LOCALLAB fixture denial"
                        )
                        :ConversionService.EligibilityDecision.allow();
                },
                (actor,definition,attempt)->{
                    if(holder[0]!=null)
                        holder[0].get(
                            attempt.attemptId
                        );

                    return "player:bob".equals(actor)
                        ?ConversionService.OutcomeResolution.failure(
                            ConversionService.InputSettlement.CANCEL_RESERVED_INPUTS
                        )
                        :ConversionService.OutcomeResolution.success(
                            ConversionService.InputSettlement.COMMIT_RESERVED_INPUTS
                        );
                }
            );

        holder[0]=service;

        expect(
            IllegalStateException.class,
            ()->service.createAttempt(
                "player:blocked",
                recipe.id
            ),
            "external eligibility hook"
        );

        AtomicTransactionService.TransactionId aliceTransaction=
            matchingReservation(
                transactions,
                "player:alice",
                "recipe:alice"
            );

        ConversionService.RecipeAttemptId alice=
            service.createAttempt(
                "player:alice",
                recipe.id
            );

        ConversionService.Snapshot aliceReserved=
            service.reserveInputs(
                alice,
                aliceTransaction
            );

        eq(
            ConversionService.AttemptState.RESERVED,
            aliceReserved.state,
            "alice reserved"
        );
        eq(
            aliceTransaction,
            aliceReserved.transactionId,
            "transaction reference"
        );

        ConversionService.Snapshot aliceResolved=
            service.resolveOutcome(alice);

        eq(
            ConversionService.OutcomeKind.SUCCESS,
            aliceResolved.outcome,
            "deterministic success outcome"
        );
        eq(
            ConversionService.InputSettlement.COMMIT_RESERVED_INPUTS,
            aliceResolved.inputSettlement,
            "success settlement policy supplied externally"
        );
        eq(1,aliceResolved.outputs.size(),"success output count");
        eq(
            "item:fixture-output",
            aliceResolved.outputs.get(0).semanticKey,
            "success output identity"
        );

        expect(
            IllegalStateException.class,
            ()->service.acknowledgeSettlement(alice),
            "settlement requires external transaction terminal state"
        );

        transactions.commit(aliceTransaction);

        check(
            service.acknowledgeSettlement(alice),
            "first settlement acknowledgement"
        );
        check(
            !service.acknowledgeSettlement(alice),
            "terminal settlement acknowledgement idempotent"
        );
        eq(
            ConversionService.AttemptState.SETTLED,
            service.get(alice).state,
            "alice settled"
        );

        AtomicTransactionService.TransactionId bobTransaction=
            matchingReservation(
                transactions,
                "player:bob",
                "recipe:bob"
            );

        ConversionService.RecipeAttemptId bob=
            service.createAttempt(
                "player:bob",
                recipe.id
            );

        service.reserveInputs(bob,bobTransaction);
        ConversionService.Snapshot bobResolved=
            service.resolveOutcome(bob);

        eq(
            ConversionService.OutcomeKind.FAILURE,
            bobResolved.outcome,
            "explicit failure outcome"
        );
        eq(
            ConversionService.InputSettlement.CANCEL_RESERVED_INPUTS,
            bobResolved.inputSettlement,
            "failure settlement policy remains external"
        );
        eq(0,bobResolved.outputs.size(),"failure has no outputs");

        transactions.cancel(bobTransaction);
        check(
            service.acknowledgeSettlement(bob),
            "cancelled input settlement acknowledged"
        );

        ConversionService.RecipeAttemptId carol=
            service.createAttempt(
                "player:carol",
                recipe.id
            );

        check(service.cancelAttempt(carol),"created cancellation");
        check(
            !service.cancelAttempt(carol),
            "repeated cancellation idempotent"
        );

        AtomicTransactionService.TransactionId daveTransaction=
            transactions.create(
                "player:dave",
                "recipe:dave-bad",
                AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB
            );

        transactions.reserve(
            daveTransaction,
            Collections.singletonList(
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:fixture-input",
                    2L,
                    "player:dave",
                    AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB
                )
            )
        );

        ConversionService.RecipeAttemptId dave=
            service.createAttempt(
                "player:dave",
                recipe.id
            );

        expect(
            IllegalStateException.class,
            ()->service.reserveInputs(
                dave,
                daveTransaction
            ),
            "reservation must exactly cover semantic recipe inputs"
        );

        eq(
            ConversionService.AttemptState.CREATED,
            service.get(dave).state,
            "failed reservation leaves attempt unchanged"
        );
        check(service.cancelAttempt(dave),"failed reservation cancellable");

        expect(
            UnsupportedOperationException.class,
            ()->service.snapshot().clear(),
            "attempt snapshot immutability"
        );

        protocolBoundaryGuard();
        externalLifecycleNotMethodSynchronized();

        System.out.println(
            "ISSUE168_RECIPE_CONVERSION_PASS "+
            "catalog=true "+
            "eligibilityHook=true "+
            "outcomeHook=true "+
            "escrowReference=true "+
            "exactReservation=true "+
            "settlementIdempotent=true "+
            "inventoryMutation=false "+
            "protocolIndependent=true "+
            "authorityPreserved=true "+
            "attempts="+service.size()
        );
    }

    private static AtomicTransactionService.TransactionId matchingReservation(
        AtomicTransactionService transactions,
        String actor,
        String reference
    ){
        AtomicTransactionService.TransactionId transactionId=
            transactions.create(
                actor,
                reference,
                AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB
            );

        transactions.reserve(
            transactionId,
            Arrays.asList(
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:fixture-input",
                    2L,
                    actor,
                    AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:fixture-token",
                    5L,
                    actor,
                    AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB
                )
            )
        );

        return transactionId;
    }

    private static void externalLifecycleNotMethodSynchronized(){
        for(String name:new String[]{
                "createAttempt",
                "reserveInputs",
                "resolveOutcome",
                "acknowledgeSettlement",
                "cancelAttempt"
        }){
            boolean found=false;

            for(java.lang.reflect.Method method:
                    ConversionService.class
                        .getDeclaredMethods()){
                if(!method.getName().equals(name))
                    continue;

                found=true;

                check(
                    !java.lang.reflect.Modifier
                        .isSynchronized(
                            method.getModifiers()
                        ),
                    "Conversion external lifecycle still synchronized "+
                    name
                );
            }

            check(
                found,
                "Conversion lifecycle method missing "+
                name
            );
        }
    }

    private static void protocolBoundaryGuard(){
        Class<?>[] classes={
            RecipeId.class,
            RecipeDefinition.class,
            RecipeDefinition.RecipeIngredient.class,
            RecipeDefinition.RecipeOutput.class,
            RecipeCatalog.class,
            ConversionService.class,
            ConversionService.Snapshot.class,
            ConversionService.OutcomeResolution.class
        };

        String[] banned={
            "widget",
            "opcode",
            "subtype",
            "sprite",
            "packet",
            "clientclass",
            "random",
            "rng",
            "chance"
        };

        for(Class<?> type:classes){
            for(Field field:type.getDeclaredFields()){
                String name=field.getName().toLowerCase(Locale.ROOT);

                for(String token:banned){
                    if(name.contains(token))
                        fail(
                            "forbidden domain field "+
                            type.getName()+"."+
                            field.getName()
                        );
                }

                String fieldType=field.getType().getName();

                if(fieldType.contains("Inventory")||
                   fieldType.contains("Bank"))
                    fail(
                        "direct item-storage mutation dependency "+
                        type.getName()+"."+
                        field.getName()
                    );
            }
        }
    }

    private static void check(boolean condition,String label){
        if(!condition)
            fail(label);
    }

    private static void eq(Object expected,Object actual,String label){
        if(!Objects.equals(expected,actual))
            fail(
                label+
                " expected="+expected+
                " actual="+actual
            );
    }

    private static void eq(long expected,long actual,String label){
        if(expected!=actual)
            fail(
                label+
                " expected="+expected+
                " actual="+actual
            );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Throwing action,
        String label
    ){
        try{
            action.run();
            fail(
                label+
                " did not throw "+
                type.getSimpleName()
            );
        }catch(Throwable error){
            if(!type.isInstance(error))
                fail(
                    label+
                    " threw "+
                    error
                );
        }
    }

    private static void fail(String message){
        throw new AssertionError(message);
    }

    private interface Throwing {
        void run() throws Exception;
    }
}
