package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class BloodDiamondFuserServiceTest {
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

        defineCycleRecipes(
            catalog,
            "alpha",
            POLICY
        );
        defineCycleRecipes(
            catalog,
            "beta",
            POLICY
        );

        RecipeDefinition wrongAuthority=
            recipe(
                "wrong",
                0,
                AtomicTransactionService
                    .SourceAuthority
                    .EXACT_CURRENT_CLIENT
            );
        catalog.define(
            wrongAuthority
        );

        ConversionService conversions=
            new ConversionService(
                catalog,
                transactions,
                (actor,definition)->
                    "player:blocked".equals(actor)
                        ?ConversionService
                            .EligibilityDecision
                            .deny(
                                "test denial"
                            )
                        :ConversionService
                            .EligibilityDecision
                            .allow(),
                (actor,definition,attempt)->
                    ConversionService
                        .OutcomeResolution
                        .success(
                            ConversionService
                                .InputSettlement
                                .COMMIT_RESERVED_INPUTS
                        )
            );

        BloodDiamondFuserService service=
            new BloodDiamondFuserService(
                catalog,
                conversions,
                POLICY
            );

        BloodDiamondFuserService.CycleSnapshot
            alpha=
                service.registerCycle(
                    cycle(
                        "cycle:alpha",
                        "alpha"
                    )
                );

        require(
            alpha.recipeIds.size()==
                BloodDiamondFuserService
                    .ROW_COUNT&&
            service.snapshot()
                .selectedCycleKey
                .equals(
                    "cycle:alpha"
                ),
            "first Blood Diamond cycle selection"
        );

        service.registerCycle(
            cycle(
                "cycle:beta",
                "beta"
            )
        );

        registrationGuards(
            service,
            catalog,
            wrongAuthority.id
        );

        BloodDiamondFuserService.CycleSnapshot
            beta=
                service.advanceCycle();

        require(
            "cycle:beta".equals(
                beta.cycleKey
            ),
            "Blood Diamond advance cycle"
        );

        require(
            "cycle:alpha".equals(
                service.advanceCycle()
                    .cycleKey
            ),
            "Blood Diamond cycle wrap"
        );

        service.selectCycle(
            "cycle:alpha"
        );

        int attemptsBefore=
            service.attemptCount();

        expect(
            IllegalStateException.class,
            ()->service.createAttempt(
                "player:blocked",
                0
            ),
            "blocked Blood Diamond attempt"
        );

        require(
            service.attemptCount()==
                attemptsBefore,
            "failed delegate attempt created app tracking"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.createAttempt(
                "player:alice",
                3
            ),
            "Blood Diamond row 3 accepted"
        );

        ConversionService.RecipeAttemptId attempt=
            service.createAttempt(
                "player:alice",
                1
            );

        BloodDiamondFuserService.AttemptSnapshot
            created=
                service.getAttempt(
                    attempt
                );

        require(
            "cycle:alpha".equals(
                created.cycleKey
            )&&
            created.rowIndex==1&&
            created.recipeId.equals(
                RecipeId.of(
                    "fuser:alpha:1"
                )
            )&&
            created.conversion.recipeId
                .equals(
                    created.recipeId
                ),
            "Blood Diamond row recipe resolution"
        );

        service.selectCycle(
            "cycle:beta"
        );

        BloodDiamondFuserService.AttemptSnapshot
            stable=
                service.getAttempt(
                    attempt
                );

        require(
            "cycle:alpha".equals(
                stable.cycleKey
            )&&
            stable.recipeId.equals(
                RecipeId.of(
                    "fuser:alpha:1"
                )
            ),
            "in-flight Blood Diamond recipe changed with cycle"
        );

        AtomicTransactionService.TransactionId tx=
            transactions.create(
                "player:alice",
                "blood-diamond:test",
                POLICY
            );

        transactions.reserve(
            tx,
            Collections.singletonList(
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:alpha:1:input",
                    2L,
                    "player:alice",
                    POLICY
                )
            )
        );

        require(
            service.reserveInputs(
                attempt,
                tx
            ).state==
                ConversionService
                    .AttemptState.RESERVED,
            "Blood Diamond input reservation"
        );

        ConversionService.Snapshot resolved=
            service.resolveOutcome(
                attempt
            );

        require(
            resolved.outcome==
                ConversionService
                    .OutcomeKind.SUCCESS&&
            resolved.outputs.size()==1&&
            "item:alpha:1:output"
                .equals(
                    resolved.outputs
                        .get(0)
                        .semanticKey
                ),
            "Blood Diamond caller-owned outcome"
        );

        expect(
            IllegalStateException.class,
            ()->service
                .acknowledgeSettlement(
                    attempt
                ),
            "Blood Diamond settlement before transaction commit"
        );

        transactions.commit(tx);

        require(
            service.acknowledgeSettlement(
                attempt
            )&&
            !service.acknowledgeSettlement(
                attempt
            ),
            "Blood Diamond settlement acknowledgement"
        );

        ConversionService.RecipeAttemptId cancelled=
            service.createAttempt(
                "player:bob",
                2
            );

        require(
            service.cancelAttempt(
                cancelled
            )&&
            !service.cancelAttempt(
                cancelled
            ),
            "Blood Diamond cancellation"
        );

        BloodDiamondFuserService.Snapshot snapshot=
            service.snapshot();

        require(
            snapshot.cycles.size()==2&&
            snapshot.attempts.size()==2&&
            snapshot.policyAuthority==POLICY&&
            BloodDiamondFuserService
                .PRESENTATION_AUTHORITY
                .equals(
                    snapshot
                        .presentationAuthority
                ),
            "Blood Diamond Fuser snapshot"
        );

        boolean immutable=false;

        try{
            snapshot.cycles.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Blood Diamond cycles mutable"
        );

        protocolBoundary();

        System.out.println(
            "BLOOD_DIAMOND_FUSER_SERVICE_PASS "+
            "exactRows3=true "+
            "callerRecipeCycles=true "+
            "cycleRegistrationAtomic=true "+
            "firstCycleSelected=true "+
            "localNextWrapPolicy=true "+
            "rowRecipeResolution=true "+
            "row3Rejected=true "+
            "delegateFailureUntracked=true "+
            "inflightRecipeStable=true "+
            "conversionDelegation=true "+
            "settlementDelegation=true "+
            "cancellationDelegation=true "+
            "itemValueHardcoded=false "+
            "chanceHardcoded=false "+
            "protocolIndependent=true"
        );
    }

    private static void registrationGuards(
        BloodDiamondFuserService service,
        RecipeCatalog catalog,
        RecipeId wrongAuthority
    ){
        int before=service.cycleCount();

        expect(
            IllegalArgumentException.class,
            ()->new BloodDiamondFuserService
                .CycleSpec(
                    "cycle:short",
                    Arrays.asList(
                        RecipeId.of(
                            "fuser:alpha:0"
                        ),
                        RecipeId.of(
                            "fuser:alpha:1"
                        )
                    )
                ),
            "Blood Diamond short cycle"
        );

        expect(
            IllegalArgumentException.class,
            ()->new BloodDiamondFuserService
                .CycleSpec(
                    "cycle:duplicate",
                    Arrays.asList(
                        RecipeId.of(
                            "fuser:alpha:0"
                        ),
                        RecipeId.of(
                            "fuser:alpha:0"
                        ),
                        RecipeId.of(
                            "fuser:alpha:1"
                        )
                    )
                ),
            "Blood Diamond duplicate cycle recipe"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.registerCycle(
                new BloodDiamondFuserService
                    .CycleSpec(
                        "cycle:missing",
                        Arrays.asList(
                            RecipeId.of(
                                "fuser:alpha:0"
                            ),
                            RecipeId.of(
                                "fuser:missing:1"
                            ),
                            RecipeId.of(
                                "fuser:alpha:2"
                            )
                        )
                    )
            ),
            "Blood Diamond missing recipe"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.registerCycle(
                new BloodDiamondFuserService
                    .CycleSpec(
                        "cycle:authority",
                        Arrays.asList(
                            RecipeId.of(
                                "fuser:alpha:0"
                            ),
                            wrongAuthority,
                            RecipeId.of(
                                "fuser:alpha:2"
                            )
                        )
                    )
            ),
            "Blood Diamond recipe authority mismatch"
        );

        require(
            service.cycleCount()==before&&
            catalog.size()==7,
            "failed Blood Diamond registration mutated state"
        );
    }

    private static BloodDiamondFuserService.CycleSpec
        cycle(
            String cycleKey,
            String prefix
        ){
        return new BloodDiamondFuserService
            .CycleSpec(
                cycleKey,
                Arrays.asList(
                    RecipeId.of(
                        "fuser:"+prefix+":0"
                    ),
                    RecipeId.of(
                        "fuser:"+prefix+":1"
                    ),
                    RecipeId.of(
                        "fuser:"+prefix+":2"
                    )
                )
            );
    }

    private static void defineCycleRecipes(
        RecipeCatalog catalog,
        String prefix,
        AtomicTransactionService.SourceAuthority
            authority
    ){
        for(int row=0;
            row<BloodDiamondFuserService.ROW_COUNT;
            row++)
            catalog.define(
                recipe(
                    prefix,
                    row,
                    authority
                )
            );
    }

    private static RecipeDefinition recipe(
        String prefix,
        int row,
        AtomicTransactionService.SourceAuthority
            authority
    ){
        return new RecipeDefinition(
            RecipeId.of(
                "fuser:"+prefix+":"+row
            ),
            Collections.singletonList(
                new RecipeDefinition
                    .RecipeIngredient(
                        RecipeDefinition
                            .AssetKind.ITEM,
                        "item:"+prefix+":"+
                            row+":input",
                        2L
                    )
            ),
            Collections.singletonList(
                new RecipeDefinition
                    .RecipeOutput(
                        RecipeDefinition
                            .AssetKind.ITEM,
                        "item:"+prefix+":"+
                            row+":output",
                        1L
                    )
            ),
            authority
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                BloodDiamondFuserService.class,
                BloodDiamondFuserService.CycleSpec.class,
                BloodDiamondFuserService.Snapshot.class,
                BloodDiamondFuserService.AttemptSnapshot.class
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
                   name.contains("itemvalue")||
                   name.contains("chance"))
                    throw new AssertionError(
                        "protocol/item-value identity leaked into Blood Diamond Fuser "+
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

    private BloodDiamondFuserServiceTest(){}
}
