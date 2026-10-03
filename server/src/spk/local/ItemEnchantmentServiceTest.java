package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class ItemEnchantmentServiceTest {
    private static final AtomicTransactionService.SourceAuthority
        POLICY=
            AtomicTransactionService
                .SourceAuthority
                .CUSTOM_LOCALLAB;

    public static void main(String[] args){
        require(
            ItemEnchantmentService.Category
                .values().length==7,
            "exact Item Enchantment category count"
        );

        RecipeCatalog catalog=
            new RecipeCatalog();

        for(int i=0;i<20;i++)
            catalog.define(
                recipe(
                    i,
                    POLICY
                )
            );

        RecipeDefinition wrongAuthority=
            recipe(
                99,
                AtomicTransactionService
                    .SourceAuthority
                    .EXACT_CURRENT_CLIENT
            );

        catalog.define(
            wrongAuthority
        );

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
                        RecipeId.of(
                            "enchant:recipe:failure"
                        )
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

        ItemEnchantmentService service=
            new ItemEnchantmentService(
                catalog,
                conversions,
                (entry,query)->
                    entry.displayName
                        .toLowerCase(
                            Locale.ROOT
                        ).contains(query)||
                    entry.searchTerms
                        .contains(query),
                POLICY
            );

        registerCatalog(
            service
        );

        require(
            service.catalogSize()==20,
            "Item Enchantment catalog size"
        );

        List<ItemEnchantmentService.Entry>
            all=
                service.visibleEntries(
                    "player:alice"
                );

        require(
            all.size()==
                ItemEnchantmentService
                    .VISIBLE_ROW_LIMIT,
            "Item Enchantment visible row cap"
        );

        ItemEnchantmentService.PlayerSnapshot
            armor=
                service.selectCategory(
                    " Player:Alice ",
                    ItemEnchantmentService
                        .Category.ARMOR
                );

        require(
            "player:alice".equals(
                armor.playerRef
            )&&
            armor.selectedCategory==
                ItemEnchantmentService
                    .Category.ARMOR&&
            service.visibleEntries(
                "player:alice"
            ).size()==3,
            "Item Enchantment category selection"
        );

        service.setSearchQuery(
            "player:alice",
            "special"
        );

        require(
            service.visibleEntries(
                "player:alice"
            ).size()==1&&
            "enchant:item:7".equals(
                service.visibleEntries(
                    "player:alice"
                ).get(0)
                .enchantmentKey
            ),
            "caller-owned Item Enchantment search"
        );

        expect(
            IllegalStateException.class,
            ()->service.selectEntry(
                "player:alice",
                "enchant:item:0"
            ),
            "invisible Item Enchantment selection"
        );

        service.selectEntry(
            "player:alice",
            "enchant:item:7"
        );

        service.selectCategory(
            "player:bob",
            ItemEnchantmentService
                .Category.WEAPONS
        );
        service.selectEntry(
            "player:bob",
            "enchant:item:1"
        );

        require(
            "enchant:item:7".equals(
                service.getPlayer(
                    "player:alice"
                ).selectedEnchantmentKey
            )&&
            "enchant:item:1".equals(
                service.getPlayer(
                    "player:bob"
                ).selectedEnchantmentKey
            ),
            "Item Enchantment player selection isolation"
        );

        int beforeAttempts=
            service.attemptCount();

        service.selectCategory(
            "player:blocked",
            ItemEnchantmentService
                .Category.ARMOR
        );
        service.setSearchQuery(
            "player:blocked",
            "special"
        );
        service.selectEntry(
            "player:blocked",
            "enchant:item:7"
        );

        expect(
            IllegalStateException.class,
            ()->service.beginAttempt(
                "player:blocked"
            ),
            "blocked Item Enchantment attempt"
        );

        require(
            service.attemptCount()==
                beforeAttempts,
            "failed delegate created app attempt"
        );

        ConversionService.RecipeAttemptId
            successAttempt=
                service.beginAttempt(
                    "player:alice"
                );

        require(
            service.getPlayer(
                "player:alice"
            ).presentationState==
                ItemEnchantmentService
                    .PresentationState
                    .PREPARATION&&
            service.getAttempt(
                successAttempt
            ).recipeId.equals(
                RecipeId.of(
                    "enchant:recipe:7"
                )
            ),
            "Item Enchantment preparation state"
        );

        // Later search/category/selection changes must not rewrite the
        // captured attempt binding.
        service.clearSearchQuery(
            "player:alice"
        );
        service.selectCategory(
            "player:alice",
            ItemEnchantmentService
                .Category.WEAPONS
        );
        service.selectEntry(
            "player:alice",
            "enchant:item:1"
        );

        require(
            service.getAttempt(
                successAttempt
            ).recipeId.equals(
                RecipeId.of(
                    "enchant:recipe:7"
                )
            )&&
            "enchant:item:7".equals(
                service.getAttempt(
                    successAttempt
                ).enchantmentKey
            ),
            "in-flight Item Enchantment binding drift"
        );

        AtomicTransactionService.TransactionId
            successTx=
                reserve(
                    transactions,
                    "player:alice",
                    "item:input:7",
                    2L,
                    "tx:enchant:success"
                );

        require(
            service.reserveInputs(
                "player:alice",
                successTx
            ).presentationState==
                ItemEnchantmentService
                    .PresentationState
                    .PREPARATION,
            "Item Enchantment reserve preparation state"
        );

        ItemEnchantmentService.AttemptSnapshot
            resolved=
                service.resolveOutcome(
                    "player:alice"
                );

        require(
            resolved.presentationState==
                ItemEnchantmentService
                    .PresentationState
                    .SUCCESS&&
            resolved.conversion.outcome==
                ConversionService
                    .OutcomeKind.SUCCESS,
            "Item Enchantment success state"
        );

        expect(
            IllegalStateException.class,
            ()->service.resetResult(
                "player:alice"
            ),
            "Item Enchantment reset before terminal"
        );

        transactions.commit(
            successTx
        );

        require(
            service.acknowledgeSettlement(
                "player:alice"
            )&&
            service.getPlayer(
                "player:alice"
            ).presentationState==
                ItemEnchantmentService
                    .PresentationState
                    .SUCCESS,
            "Item Enchantment settlement"
        );

        require(
            service.resetResult(
                "player:alice"
            ).presentationState==
                ItemEnchantmentService
                    .PresentationState.IDLE,
            "Item Enchantment terminal reset"
        );

        failureOutcome(
            service,
            transactions,
            catalog
        );
        cancellation(
            service,
            transactions
        );
        registrationGuards(
            service,
            wrongAuthority.id
        );
        immutableSnapshots(
            service
        );
        protocolBoundary();

        System.out.println(
            "ITEM_ENCHANTMENT_SERVICE_PASS "+
            "exactCategories7=true "+
            "visibleRows16=true "+
            "callerSearchMatcher=true "+
            "normalizedPlayerIdentity=true "+
            "playerSelectionIsolation=true "+
            "invisibleSelectionRejected=true "+
            "recipeAuthorityFence=true "+
            "delegateFailureUntracked=true "+
            "preparationState=true "+
            "successState=true "+
            "failureState=true "+
            "settlementDelegation=true "+
            "cancellationIdle=true "+
            "inflightBindingStable=true "+
            "terminalResetOnly=true "+
            "recipeHardcoded=false "+
            "rngHardcoded=false "+
            "protocolIndependent=true"
        );
    }

    private static void failureOutcome(
        ItemEnchantmentService service,
        AtomicTransactionService transactions,
        RecipeCatalog catalog
    ){
        RecipeDefinition failure=
            new RecipeDefinition(
                RecipeId.of(
                    "enchant:recipe:failure"
                ),
                Collections.singletonList(
                    new RecipeDefinition
                        .RecipeIngredient(
                            RecipeDefinition
                                .AssetKind.ITEM,
                            "item:input:failure",
                            1L
                        )
                ),
                Collections.singletonList(
                    new RecipeDefinition
                        .RecipeOutput(
                            RecipeDefinition
                                .AssetKind.ITEM,
                            "item:output:failure",
                            1L
                        )
                ),
                POLICY
            );

        catalog.define(failure);

        service.registerEntry(
            new ItemEnchantmentService.Entry(
                "enchant:item:failure",
                "Failure Test",
                ItemEnchantmentService
                    .Category.MISC,
                failure.id,
                Collections.singletonList(
                    "failure"
                ),
                POLICY
            )
        );

        service.selectCategory(
            "player:failure",
            ItemEnchantmentService
                .Category.MISC
        );
        service.setSearchQuery(
            "player:failure",
            "failure"
        );
        service.selectEntry(
            "player:failure",
            "enchant:item:failure"
        );
        service.beginAttempt(
            "player:failure"
        );

        AtomicTransactionService.TransactionId
            tx=
                reserve(
                    transactions,
                    "player:failure",
                    "item:input:failure",
                    1L,
                    "tx:enchant:failure"
                );

        service.reserveInputs(
            "player:failure",
            tx
        );

        ItemEnchantmentService.AttemptSnapshot
            resolved=
                service.resolveOutcome(
                    "player:failure"
                );

        require(
            resolved.presentationState==
                ItemEnchantmentService
                    .PresentationState.FAILURE&&
            resolved.conversion.outcome==
                ConversionService
                    .OutcomeKind.FAILURE,
            "Item Enchantment failure state"
        );

        transactions.cancel(tx);

        require(
            service.acknowledgeSettlement(
                "player:failure"
            )&&
            service.getPlayer(
                "player:failure"
            ).presentationState==
                ItemEnchantmentService
                    .PresentationState.FAILURE,
            "Item Enchantment failure settlement"
        );
    }

    private static void cancellation(
        ItemEnchantmentService service,
        AtomicTransactionService transactions
    ){
        service.selectCategory(
            "player:cancel",
            ItemEnchantmentService
                .Category.ARMOR
        );
        service.setSearchQuery(
            "player:cancel",
            "special"
        );
        service.selectEntry(
            "player:cancel",
            "enchant:item:7"
        );
        service.beginAttempt(
            "player:cancel"
        );

        require(
            service.cancelAttempt(
                "player:cancel"
            )&&
            !service.cancelAttempt(
                "player:cancel"
            )&&
            service.getPlayer(
                "player:cancel"
            ).presentationState==
                ItemEnchantmentService
                    .PresentationState.IDLE,
            "Item Enchantment created-attempt cancellation"
        );

        service.resetResult(
            "player:cancel"
        );

        service.beginAttempt(
            "player:cancel"
        );

        AtomicTransactionService.TransactionId
            tx=
                reserve(
                    transactions,
                    "player:cancel",
                    "item:input:7",
                    2L,
                    "tx:enchant:cancel"
                );

        service.reserveInputs(
            "player:cancel",
            tx
        );

        expect(
            IllegalStateException.class,
            ()->service.cancelAttempt(
                "player:cancel"
            ),
            "reserved Item Enchantment cancelled before tx"
        );

        transactions.cancel(tx);

        require(
            service.cancelAttempt(
                "player:cancel"
            )&&
            service.getPlayer(
                "player:cancel"
            ).presentationState==
                ItemEnchantmentService
                    .PresentationState.IDLE,
            "reserved Item Enchantment cancellation"
        );
    }

    private static void registrationGuards(
        ItemEnchantmentService service,
        RecipeId wrongAuthority
    ){
        int before=
            service.catalogSize();

        expect(
            IllegalArgumentException.class,
            ()->service.registerEntry(
                new ItemEnchantmentService.Entry(
                    "enchant:wrong-authority",
                    "Wrong",
                    ItemEnchantmentService
                        .Category.MISC,
                    wrongAuthority,
                    Collections.emptyList(),
                    POLICY
                )
            ),
            "Item Enchantment wrong recipe authority"
        );

        expect(
            IllegalStateException.class,
            ()->service.registerEntry(
                new ItemEnchantmentService.Entry(
                    "enchant:item:duplicate-recipe",
                    "Duplicate recipe",
                    ItemEnchantmentService
                        .Category.MISC,
                    RecipeId.of(
                        "enchant:recipe:0"
                    ),
                    Collections.emptyList(),
                    POLICY
                )
            ),
            "Item Enchantment duplicate recipe binding"
        );

        require(
            service.catalogSize()==before,
            "failed Item Enchantment registration mutated catalog"
        );
    }

    private static void registerCatalog(
        ItemEnchantmentService service
    ){
        ItemEnchantmentService.Category[] categories=
            ItemEnchantmentService.Category
                .values();

        for(int i=0;i<20;i++){
            ItemEnchantmentService.Category category=
                categories[
                    i%categories.length
                ];

            service.registerEntry(
                new ItemEnchantmentService.Entry(
                    "enchant:item:"+i,
                    i==7
                        ?"Special Armor Seven"
                        :"Enchantable Item "+i,
                    category,
                    RecipeId.of(
                        "enchant:recipe:"+i
                    ),
                    i==7
                        ?Collections.singletonList(
                            "special"
                        )
                        :Collections.singletonList(
                            "generic"
                        ),
                    POLICY
                )
            );
        }
    }

    private static RecipeDefinition recipe(
        int id,
        AtomicTransactionService.SourceAuthority
            authority
    ){
        return new RecipeDefinition(
            RecipeId.of(
                "enchant:recipe:"+id
            ),
            Collections.singletonList(
                new RecipeDefinition
                    .RecipeIngredient(
                        RecipeDefinition
                            .AssetKind.ITEM,
                        "item:input:"+id,
                        2L
                    )
            ),
            Collections.singletonList(
                new RecipeDefinition
                    .RecipeOutput(
                        RecipeDefinition
                            .AssetKind.ITEM,
                        "item:output:"+id,
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
            String itemKey,
            long quantity,
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
                    itemKey,
                    quantity,
                    owner,
                    POLICY
                )
            )
        );

        return tx;
    }

    private static void immutableSnapshots(
        ItemEnchantmentService service
    ){
        boolean catalogImmutable=false;

        try{
            service.snapshot()
                .catalog.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            catalogImmutable=true;
        }

        boolean visibleImmutable=false;

        try{
            service.visibleEntries(
                "player:alice"
            ).clear();
        }catch(
            UnsupportedOperationException expected
        ){
            visibleImmutable=true;
        }

        require(
            catalogImmutable&&
            visibleImmutable,
            "Item Enchantment snapshots mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                ItemEnchantmentService.class,
                ItemEnchantmentService.Entry.class,
                ItemEnchantmentService.PlayerSnapshot.class,
                ItemEnchantmentService.AttemptSnapshot.class
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
                   name.contains("s2c")||
                   name.contains("c2s")||
                   name.contains("chance")||
                   name.contains("random")||
                   name.contains("rng")||
                   name.contains("itemid")||
                   name.contains("cost"))
                    throw new AssertionError(
                        "protocol/RNG/recipe identity leaked into Item Enchantment "+
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

    private ItemEnchantmentServiceTest(){}
}
