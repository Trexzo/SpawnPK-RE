package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public final class ItemEnchantmentPresentationTest {
    private static final AtomicTransactionService.SourceAuthority
        POLICY=
            AtomicTransactionService
                .SourceAuthority
                .CUSTOM_LOCALLAB;

    public static void main(String[] args)
        throws Exception{
        exactRootsAndActions();
        categoryRouting();
        rowAndItemRouting();
        semanticServiceComposition();
        rowCeilingAndSelectionIsolation();
        resultStateProjection();
        exactPacketCompatibility();
        exactWireTextFence();
        semanticServiceHasNoProtocolIdentity();
        policyStillUnowned();

        System.out.println(
            "ITEM_ENCHANTMENT_PRESENTATION_PASS "+
            "main31244=true "+
            "categories31243=true "+
            "categories7=true "+
            "rows16=true "+
            "itemWidget50253=true "+
            "attempt49991=true "+
            "search50314=true "+
            "back50319=true "+
            "resultTarget38=true "+
            "state0123=true "+
            "iso88591Fence=true "+
            "searchPromptWireOwned=false "+
            "selectionIsolation=true "+
            "rawProtocolInService=false "+
            "policyOwned=false"
        );
    }

    private static void exactRootsAndActions(){
        require(
            ItemEnchantmentPresentation
                .MAIN_ROOT==31244&&
            ItemEnchantmentPresentation
                .CATEGORY_ROOT==31243,
            "roots"
        );
        require(
            ItemEnchantmentPresentation
                .ROWS==16&&
            ItemEnchantmentPresentation
                .rowWidget(0)==49970&&
            ItemEnchantmentPresentation
                .rowWidget(15)==49985,
            "row range"
        );
        require(
            ItemEnchantmentPresentation
                .ATTEMPT_WIDGET==49991&&
            ItemEnchantmentPresentation
                .SEARCH_WIDGET==50314&&
            ItemEnchantmentPresentation
                .BACK_WIDGET==50319&&
            ItemEnchantmentPresentation
                .ITEM_SELECTION_WIDGET==50253,
            "actions"
        );
        require(
            ItemEnchantmentPresentation
                .RESULT_TARGET==38&&
            ItemEnchantmentPresentation
                .WIDGET_ACTION_OPCODE==185&&
            ItemEnchantmentPresentation
                .ITEM_OPTION_1_OPCODE==145&&
            !ItemEnchantmentPresentation
                .SEARCH_PROMPT_WIRE_OWNED,
            "transport authority"
        );

        requireKind(
            31244,
            49991,
            ItemEnchantmentPresentation
                .InputKind.ATTEMPT
        );
        requireKind(
            31244,
            50314,
            ItemEnchantmentPresentation
                .InputKind.SEARCH_REQUEST
        );
        requireKind(
            31244,
            50319,
            ItemEnchantmentPresentation
                .InputKind.BACK
        );

        require(
            ItemEnchantmentPresentation
                .resolveWidget(
                    31243,
                    49991
                )==null,
            "main action escaped root"
        );
    }

    private static void categoryRouting(){
        int[] widgets={
            50327,50329,50331,50333,
            50335,50337,50339
        };
        ItemEnchantmentService.Category[]
            categories=
                ItemEnchantmentService
                    .Category
                    .values();

        require(
            widgets.length==
                categories.length,
            "category count"
        );

        for(int i=0;i<widgets.length;i++){
            require(
                ItemEnchantmentPresentation
                    .categoryWidget(
                        categories[i]
                    )==widgets[i],
                "category widget "+
                categories[i]
            );

            ItemEnchantmentPresentation.Input
                input=
                    ItemEnchantmentPresentation
                        .resolveWidget(
                            31243,
                            widgets[i]
                        );

            require(
                input!=null&&
                input.kind==
                    ItemEnchantmentPresentation
                        .InputKind.SELECT_CATEGORY&&
                input.category==
                    categories[i],
                "category routing "+
                categories[i]
            );
        }

        require(
            ItemEnchantmentPresentation
                .resolveWidget(
                    31243,
                    50328
                )==null,
            "category neighbor"
        );
    }

    private static void rowAndItemRouting(){
        for(int i=0;i<16;i++){
            ItemEnchantmentPresentation.Input
                input=
                    ItemEnchantmentPresentation
                        .resolveWidget(
                            31244,
                            49970+i
                        );

            require(
                input!=null&&
                input.kind==
                    ItemEnchantmentPresentation
                        .InputKind.SELECT_ROW&&
                input.rowIndex==i,
                "row "+i
            );
        }

        require(
            ItemEnchantmentPresentation
                .resolveWidget(
                    31244,
                    49969
                )==null&&
            ItemEnchantmentPresentation
                .resolveWidget(
                    31244,
                    49986
                )==null,
            "row neighbors"
        );

        ItemEnchantmentPresentation.Input item=
            ItemEnchantmentPresentation
                .resolveItemOption1(
                    31244,
                    50253,
                    7,
                    4151
                );

        require(
            item!=null&&
            item.kind==
                ItemEnchantmentPresentation
                    .InputKind.SELECT_ITEM&&
            item.slot==7&&
            item.itemId==4151,
            "item selection"
        );

        require(
            ItemEnchantmentPresentation
                .resolveItemOption1(
                    31243,
                    50253,
                    7,
                    4151
                )==null&&
            ItemEnchantmentPresentation
                .resolveItemOption1(
                    31244,
                    50254,
                    7,
                    4151
                )==null,
            "item selection context"
        );
    }

    private static void semanticServiceComposition(){
        Fixture fixture=
            fixture(
                4
            );

        ItemEnchantmentPresentation.Action
            category=
                ItemEnchantmentPresentation
                    .applyWidget(
                        fixture.service,
                        " Player:One ",
                        31243,
                        50327
                    );

        require(
            category!=null&&
            category.player.selectedCategory==
                ItemEnchantmentService
                    .Category.ARMOR,
            "category service call"
        );

        ItemEnchantmentPresentation.Action
            row=
                ItemEnchantmentPresentation
                    .applyWidget(
                        fixture.service,
                        "player:one",
                        31244,
                        49971
                    );

        require(
            row!=null&&
            "enchant:1".equals(
                row.player
                    .selectedEnchantmentKey
            ),
            "row service selection"
        );

        String beforeSearch=
            row.player.searchQuery;

        ItemEnchantmentPresentation.Action
            search=
                ItemEnchantmentPresentation
                    .applyWidget(
                        fixture.service,
                        "player:one",
                        31244,
                        50314
                    );

        require(
            search!=null&&
            search.kind==
                ItemEnchantmentPresentation
                    .InputKind.SEARCH_REQUEST&&
            beforeSearch.equals(
                search.player.searchQuery
            ),
            "search click invented query transport"
        );

        ItemEnchantmentService.PlayerSnapshot
            filtered=
                ItemEnchantmentPresentation
                    .applySearchQuery(
                        fixture.service,
                        "player:one",
                        "entry 2"
                    );

        require(
            "entry 2".equals(
                filtered.searchQuery
            )&&
            filtered.selectedEnchantmentKey==
                null,
            "semantic search query"
        );

        ItemEnchantmentPresentation.Action
            back=
                ItemEnchantmentPresentation
                    .applyWidget(
                        fixture.service,
                        "player:one",
                        31244,
                        50319
                    );

        require(
            back.player.selectedCategory==
                null,
            "back did not clear category"
        );

        /*
         * Restore an unfiltered visible selection and prove Attempt delegates
         * to the semantic service rather than owning conversion mechanics.
         */
        fixture.service.clearSearchQuery(
            "player:one"
        );
        fixture.service.selectCategory(
            "player:one",
            ItemEnchantmentService
                .Category.ARMOR
        );
        fixture.service.selectEntry(
            "player:one",
            "enchant:0"
        );

        ItemEnchantmentPresentation.Action
            attempt=
                ItemEnchantmentPresentation
                    .applyWidget(
                        fixture.service,
                        "player:one",
                        31244,
                        49991
                    );

        require(
            attempt.attemptId!=null&&
            attempt.player.presentationState==
                ItemEnchantmentService
                    .PresentationState.PREPARATION&&
            fixture.service.attemptCount()==1,
            "attempt service delegation"
        );
    }

    private static void
        rowCeilingAndSelectionIsolation(){
        Fixture fixture=
            fixture(
                20
            );

        ItemEnchantmentPresentation.View view=
            ItemEnchantmentPresentation
                .project(
                    fixture.service,
                    "player:rows"
                );

        require(
            view.rows.size()==16&&
            view.rows.get(0).widgetId==49970&&
            view.rows.get(15).widgetId==49985&&
            "enchant:15".equals(
                view.rows.get(15)
                    .enchantmentKey
            ),
            "16-row ceiling"
        );

        fixture.service.selectEntry(
            "player:rows",
            "enchant:15"
        );

        ItemEnchantmentPresentation.Input item=
            ItemEnchantmentPresentation
                .resolveItemOption1(
                    31244,
                    50253,
                    3,
                    11802
                );

        require(
            item!=null&&
            "enchant:15".equals(
                fixture.service.getPlayer(
                    "player:rows"
                ).selectedEnchantmentKey
            ),
            "item intent mutated enchantment selection"
        );

        boolean immutable=false;
        try{
            view.rows.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }
        require(
            immutable,
            "rows mutable"
        );
    }

    private static void resultStateProjection(){
        require(
            ItemEnchantmentPresentation
                .resultState(
                    ItemEnchantmentService
                        .PresentationState.IDLE
                )==0&&
            ItemEnchantmentPresentation
                .resultState(
                    ItemEnchantmentService
                        .PresentationState.PREPARATION
                )==1&&
            ItemEnchantmentPresentation
                .resultState(
                    ItemEnchantmentService
                        .PresentationState.SUCCESS
                )==2&&
            ItemEnchantmentPresentation
                .resultState(
                    ItemEnchantmentService
                        .PresentationState.FAILURE
                )==3,
            "result states"
        );
    }

    private static void exactPacketCompatibility()
        throws Exception{
        byte[] main=
            BootstrapPackets.interface97(
                31244
            );
        byte[] categories=
            BootstrapPackets.interface97(
                31243
            );

        require(
            readU16(main)==31244&&
            readU16(categories)==31243,
            "S2C97 roots"
        );

        byte[] result=
            BootstrapPackets.widgetText126(
                38,
                "2"
            );
        int n=result.length;
        int target=
            ((result[n-2]&255)<<8)|
            (((result[n-1]&255)-128)&255);

        require(
            target==38&&
            result[0]=='2'&&
            result[1]==10,
            "S2C126 result state"
        );

        byte[] c2s145=
            new PacketPayloadWriter()
                .putU16BELowAdd128(
                    50253
                )
                .putU16BELowAdd128(
                    7
                )
                .putU16BELowAdd128(
                    4151
                )
                .toByteArray();

        require(
            c2s145.length==6&&
            (c2s145[0]&255)==0xC4&&
            (c2s145[1]&255)==0xCD,
            "C2S145 widget BE-A"
        );

        byte[] rowText=
            BootstrapPackets.widgetText126(
                49970,
                "Caller Entry"
            );
        int rowN=rowText.length;
        int rowTarget=
            ((rowText[rowN-2]&255)<<8)|
            (((rowText[rowN-1]&255)-128)&255);

        require(
            rowTarget==49970,
            "row text target"
        );
    }

    private static void exactWireTextFence()
        throws Exception{
        Method method=
            ItemEnchantmentPresentation.class
                .getDeclaredMethod(
                    "requireSingleLine",
                    String.class,
                    String.class
                );
        method.setAccessible(true);

        boolean rejected=false;

        try{
            method.invoke(
                null,
                "bad\u20ac",
                "wireText"
            );
        }catch(
            java.lang.reflect.InvocationTargetException
                expected
        ){
            rejected=
                expected.getCause() instanceof
                    IllegalArgumentException;
        }

        require(
            rejected,
            "non-ISO-8859-1 wire text accepted"
        );
    }

    private static void
        semanticServiceHasNoProtocolIdentity(){
        for(Field field:
                ItemEnchantmentService.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("widget")||
               name.contains("opcode")||
               name.contains("packet"))
                throw new AssertionError(
                    "semantic service leaked protocol field "+
                    field.getName()
                );
        }
    }

    private static void policyStillUnowned(){
        for(Method method:
                ItemEnchantmentPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("chancecalc")||
               name.contains("rng")||
               name.contains("eligible")||
               name.contains("consume")||
               name.contains("persist")||
               name.contains("settle"))
                throw new AssertionError(
                    "unowned enchantment policy method "+
                    method.getName()
                );
        }
    }

    private static void requireKind(
        int root,
        int widget,
        ItemEnchantmentPresentation.InputKind
            expected
    ){
        ItemEnchantmentPresentation.Input input=
            ItemEnchantmentPresentation
                .resolveWidget(
                    root,
                    widget
                );

        require(
            input!=null&&
            input.kind==expected,
            "widget "+widget+
            " -> "+expected
        );
    }

    private static Fixture fixture(
        int entries
    ){
        RecipeCatalog catalog=
            new RecipeCatalog();
        AtomicTransactionService transactions=
            new AtomicTransactionService();

        final List<RecipeDefinition> recipes=
            new ArrayList<>();

        for(int i=0;i<entries;i++){
            RecipeDefinition recipe=
                new RecipeDefinition(
                    RecipeId.of(
                        "enchant:recipe:"+
                        i
                    ),
                    Collections.singletonList(
                        new RecipeDefinition
                            .RecipeIngredient(
                                RecipeDefinition
                                    .AssetKind.ITEM,
                                "asset:input:"+
                                i,
                                1L
                            )
                    ),
                    Collections.singletonList(
                        new RecipeDefinition
                            .RecipeOutput(
                                RecipeDefinition
                                    .AssetKind.ITEM,
                                "asset:output:"+
                                i,
                                1L
                            )
                    ),
                    POLICY
                );
            catalog.define(recipe);
            recipes.add(recipe);
        }

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

        ItemEnchantmentService service=
            new ItemEnchantmentService(
                catalog,
                conversions,
                (entry,query)->
                    entry.displayName
                        .toLowerCase(
                            Locale.ROOT
                        )
                        .contains(query),
                POLICY
            );

        for(int i=0;i<entries;i++)
            service.registerEntry(
                new ItemEnchantmentService
                    .Entry(
                        "enchant:"+i,
                        "Entry "+i,
                        ItemEnchantmentService
                            .Category.ARMOR,
                        recipes.get(i).id,
                        Collections.singletonList(
                            "entry "+i
                        ),
                        POLICY
                    )
            );

        return new Fixture(
            service,
            transactions
        );
    }

    private static int readU16(
        byte[] body
    ){
        return ((body[0]&255)<<8)|
            (body[1]&255);
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private static final class Fixture {
        final ItemEnchantmentService service;
        final AtomicTransactionService transactions;

        Fixture(
            ItemEnchantmentService service,
            AtomicTransactionService transactions
        ){
            this.service=service;
            this.transactions=transactions;
        }
    }

    private ItemEnchantmentPresentationTest(){}
}
