package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public final class LegendaryPetFusionPresentationTest {
    private static final AtomicTransactionService.SourceAuthority
        POLICY=
            AtomicTransactionService
                .SourceAuthority
                .CUSTOM_LOCALLAB;

    public static void main(String[] args)
        throws Exception{
        exactClientContract();
        contextualFuseRouting();
        callerDefinedProjection();
        serviceStateProjection();
        packetCompatibility();
        exactWireTextFence();
        legacyDefaultsStayEvidenceOnly();
        semanticServiceHasNoProtocolIdentity();
        policyStillUnowned();

        System.out.println(
            "LEGENDARY_PET_FUSION_PRESENTATION_PASS "+
            "root18547=true "+
            "fuseInternal65559=true "+
            "fuseWire23=true "+
            "close65418=true "+
            "oneOffering=true "+
            "callerDefinedProjection=true "+
            "stateProjection=true "+
            "stateWireOwned=false "+
            "iso88591Fence=true "+
            "legacyDefaultsEvidenceOnly=true "+
            "rawProtocolInService=false "+
            "policyOwned=false"
        );
    }

    private static void exactClientContract(){
        require(
            LegendaryPetFusionPresentation
                .ROOT==18547,
            "root"
        );
        require(
            LegendaryPetFusionPresentation
                .INGREDIENT_WIDGET==18548&&
            LegendaryPetFusionPresentation
                .COST_WIDGET==18549&&
            LegendaryPetFusionPresentation
                .RESULT_WIDGET==18550&&
            LegendaryPetFusionPresentation
                .AVAILABILITY_TEXT_WIDGET==18552,
            "low-id presentation widgets"
        );
        require(
            LegendaryPetFusionPresentation
                .FUSE_INTERNAL_WIDGET==65559&&
            LegendaryPetFusionPresentation
                .FUSE_WIRE_WIDGET==23,
            "high-id fuse alias"
        );
        require(
            LegendaryPetFusionPresentation
                .CLOSE_WIDGET==65418&&
            LegendaryPetFusionPresentation
                .WIDGET_ACTION_OPCODE==185&&
            !LegendaryPetFusionPresentation
                .STATE_WIRE_OWNED,
            "close/C2S185/state authority"
        );
    }

    private static void contextualFuseRouting(){
        LegendaryPetFusionPresentation.Input
            fuse=
                LegendaryPetFusionPresentation
                    .resolveWidget(
                        18547,
                        23
                    );

        require(
            fuse!=null&&
            fuse.kind==
                LegendaryPetFusionPresentation
                    .InputKind.FUSE,
            "fuse routing"
        );

        LegendaryPetFusionPresentation.Input
            close=
                LegendaryPetFusionPresentation
                    .resolveWidget(
                        18547,
                        65418
                    );

        require(
            close!=null&&
            close.kind==
                LegendaryPetFusionPresentation
                    .InputKind.CLOSE,
            "close routing"
        );

        require(
            LegendaryPetFusionPresentation
                .resolveWidget(
                    31244,
                    23
                )==null,
            "wire alias escaped root context"
        );

        expect(
            IllegalArgumentException.class,
            ()->LegendaryPetFusionPresentation
                .resolveWidget(
                    18547,
                    65559
                ),
            "full high widget accepted as wire id"
        );
    }

    private static void callerDefinedProjection(){
        Fixture fixture=
            fixture(
                false
            );

        LegendaryPetFusionPresentation.View
            view=
                LegendaryPetFusionPresentation
                    .project(
                        fixture.service,
                        "player:view",
                        Arrays.asList(
                            new LegendaryPetFusionPresentation
                                .ItemStack(
                                    4151,
                                    2
                                ),
                            new LegendaryPetFusionPresentation
                                .ItemStack(
                                    11802,
                                    4
                                )
                        ),
                        Collections.singletonList(
                            new LegendaryPetFusionPresentation
                                .ItemStack(
                                    995,
                                    123456
                                )
                        ),
                        Collections.singletonList(
                            new LegendaryPetFusionPresentation
                                .ItemStack(
                                    13263,
                                    1
                                )
                        )
                    );

        require(
            "offering:caller".equals(
                view.offeringKey
            )&&
            "Caller-defined legendary fusion"
                .equals(
                    view.displayName
                )&&
            "Caller-defined status"
                .equals(
                    view.statusText
                )&&
            "Caller-defined availability"
                .equals(
                    view.availabilityText
                )&&
            view.state==
                LegendaryPetFusionService
                    .PresentationState.IDLE,
            "caller-defined offering projection"
        );

        require(
            view.ingredients.size()==2&&
            view.ingredients.get(0)
                .itemId==4151&&
            view.costs.get(0)
                .itemId==995&&
            view.results.get(0)
                .itemId==13263,
            "caller-defined item projection"
        );

        boolean immutable=false;
        try{
            view.ingredients.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }
        require(
            immutable,
            "projection items mutable"
        );

        LegendaryPetFusionPresentation.Action
            close=
                LegendaryPetFusionPresentation
                    .apply(
                        fixture.service,
                        "player:view",
                        18547,
                        65418
                    );

        require(
            close!=null&&
            close.kind==
                LegendaryPetFusionPresentation
                    .InputKind.CLOSE&&
            close.attemptId==null&&
            fixture.service.attemptCount()==0,
            "close mutated service"
        );

        LegendaryPetFusionPresentation.Action
            fuse=
                LegendaryPetFusionPresentation
                    .apply(
                        fixture.service,
                        "player:view",
                        18547,
                        23
                    );

        require(
            fuse!=null&&
            fuse.kind==
                LegendaryPetFusionPresentation
                    .InputKind.FUSE&&
            fuse.attemptId!=null&&
            fixture.service.attemptCount()==1,
            "fuse did not normalize to service attempt"
        );
    }

    private static void serviceStateProjection(){
        Fixture success=
            fixture(
                false
            );

        require(
            viewState(
                success,
                "player:success"
            )==
                LegendaryPetFusionService
                    .PresentationState.IDLE,
            "idle"
        );

        success.service.beginFusion(
            "player:success"
        );

        require(
            viewState(
                success,
                "player:success"
            )==
                LegendaryPetFusionService
                    .PresentationState.PREPARATION,
            "preparation"
        );

        AtomicTransactionService.TransactionId
            successTx=
                reserve(
                    success.transactions,
                    "player:success",
                    "asset:input",
                    "tx:presentation:success"
                );

        success.service.reserveInputs(
            "player:success",
            successTx
        );
        success.service.resolveOutcome(
            "player:success"
        );

        require(
            viewState(
                success,
                "player:success"
            )==
                LegendaryPetFusionService
                    .PresentationState.SUCCESS,
            "success"
        );

        Fixture failure=
            fixture(
                true
            );
        failure.service.beginFusion(
            "player:failure"
        );

        AtomicTransactionService.TransactionId
            failureTx=
                reserve(
                    failure.transactions,
                    "player:failure",
                    "asset:input",
                    "tx:presentation:failure"
                );

        failure.service.reserveInputs(
            "player:failure",
            failureTx
        );
        failure.service.resolveOutcome(
            "player:failure"
        );

        require(
            viewState(
                failure,
                "player:failure"
            )==
                LegendaryPetFusionService
                    .PresentationState.FAILURE,
            "failure"
        );
    }

    private static LegendaryPetFusionService
        .PresentationState viewState(
            Fixture fixture,
            String player
        ){
        return LegendaryPetFusionPresentation
            .project(
                fixture.service,
                player,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
            ).state;
    }

    private static void packetCompatibility()
        throws Exception{
        byte[] root=
            BootstrapPackets.interface97(
                LegendaryPetFusionPresentation
                    .ROOT
            );

        require(
            root.length==2&&
            readU16(root)==18547,
            "S2C97 root"
        );

        byte[] ingredients=
            LegendaryPetFusionPresentation
                .itemContainer(
                    18548,
                    Arrays.asList(
                        new LegendaryPetFusionPresentation
                            .ItemStack(
                                4151,
                                2
                            ),
                        new LegendaryPetFusionPresentation
                            .ItemStack(
                                11802,
                                300
                            )
                    )
                );

        require(
            readU16(ingredients,0)==18548&&
            readU16(ingredients,2)==2,
            "S2C53 header"
        );

        int firstAmount=
            ingredients[4]&255;
        int firstWireItem=
            readU16LowAdd128(
                ingredients,
                5
            );

        require(
            firstAmount==2&&
            firstWireItem==4152,
            "S2C53 caller first item"
        );

        byte[] availability=
            BootstrapPackets.widgetText126(
                LegendaryPetFusionPresentation
                    .AVAILABILITY_TEXT_WIDGET,
                "Caller-defined availability"
            );

        int n=availability.length;
        int target=
            ((availability[n-2]&255)<<8)|
            (((availability[n-1]&255)-128)&255);

        require(
            target==18552,
            "S2C126 availability target"
        );
    }

    private static void
        legacyDefaultsStayEvidenceOnly(){
        require(
            LegendaryPetFusionPresentation
                .LegacyEvidence
                .INGREDIENT_0_ITEM==12111&&
            LegendaryPetFusionPresentation
                .LegacyEvidence
                .INGREDIENT_0_AMOUNT==3&&
            LegendaryPetFusionPresentation
                .LegacyEvidence
                .INGREDIENT_1_ITEM==15000&&
            LegendaryPetFusionPresentation
                .LegacyEvidence
                .INGREDIENT_1_AMOUNT==3&&
            LegendaryPetFusionPresentation
                .LegacyEvidence
                .COST_ITEM==11337&&
            LegendaryPetFusionPresentation
                .LegacyEvidence
                .COST_AMOUNT==500&&
            LegendaryPetFusionPresentation
                .LegacyEvidence
                .RESULT_ITEM==12113&&
            LegendaryPetFusionPresentation
                .LegacyEvidence
                .RESULT_AMOUNT==1&&
            "@gre@Limited time pet fusion!"
                .equals(
                    LegendaryPetFusionPresentation
                        .LegacyEvidence
                        .STATUS_TEXT
                )&&
            "New pet ETA: @whi@10/26/2016"
                .equals(
                    LegendaryPetFusionPresentation
                        .LegacyEvidence
                        .AVAILABILITY_TEXT
                ),
            "legacy static evidence"
        );

        Fixture fixture=
            fixture(
                false
            );

        LegendaryPetFusionPresentation.View
            view=
                LegendaryPetFusionPresentation
                    .project(
                        fixture.service,
                        "player:legacy-check",
                        Collections.singletonList(
                            new LegendaryPetFusionPresentation
                                .ItemStack(
                                    4151,
                                    7
                                )
                        ),
                        Collections.singletonList(
                            new LegendaryPetFusionPresentation
                                .ItemStack(
                                    995,
                                    9
                                )
                        ),
                        Collections.singletonList(
                            new LegendaryPetFusionPresentation
                                .ItemStack(
                                    13263,
                                    11
                                )
                        )
                    );

        require(
            view.ingredients.get(0)
                .itemId==4151&&
            view.costs.get(0)
                .itemId==995&&
            view.results.get(0)
                .itemId==13263,
            "legacy evidence became recipe policy"
        );
    }

    private static void exactWireTextFence()
        throws Exception{
        Method method=
            LegendaryPetFusionPresentation.class
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
                LegendaryPetFusionService.class
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
                LegendaryPetFusionPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("chance")||
               name.contains("rng")||
               name.contains("eligible")||
               name.contains("consume")||
               name.contains("persist")||
               name.contains("schedule")||
               name.contains("settle"))
                throw new AssertionError(
                    "unowned fusion policy method "+
                    method.getName()
                );
        }
    }

    private static Fixture fixture(
        boolean fail
    ){
        RecipeCatalog catalog=
            new RecipeCatalog();

        RecipeDefinition recipe=
            new RecipeDefinition(
                RecipeId.of(
                    "fusion:presentation"
                ),
                Collections.singletonList(
                    new RecipeDefinition
                        .RecipeIngredient(
                            RecipeDefinition
                                .AssetKind.ITEM,
                            "asset:input",
                            1L
                        )
                ),
                Collections.singletonList(
                    new RecipeDefinition
                        .RecipeOutput(
                            RecipeDefinition
                                .AssetKind.ITEM,
                            "asset:output",
                            1L
                        )
                ),
                POLICY
            );

        catalog.define(
            recipe
        );

        AtomicTransactionService transactions=
            new AtomicTransactionService();

        ConversionService conversions=
            new ConversionService(
                catalog,
                transactions,
                (actor,definition)->
                    ConversionService
                        .EligibilityDecision
                        .allow(),
                (actor,definition,attempt)->
                    fail
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

        service.replaceOffering(
            new LegendaryPetFusionService
                .Offering(
                    "offering:caller",
                    "Caller-defined legendary fusion",
                    recipe.id,
                    "Caller-defined status",
                    "Caller-defined availability",
                    POLICY
                )
        );

        return new Fixture(
            service,
            transactions
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

    private static int readU16(
        byte[] bytes
    ){
        return readU16(
            bytes,
            0
        );
    }

    private static int readU16(
        byte[] bytes,
        int offset
    ){
        return ((bytes[offset]&255)<<8)|
            (bytes[offset+1]&255);
    }

    private static int readU16LowAdd128(
        byte[] bytes,
        int offset
    ){
        return (((bytes[offset+1]&255)<<8)|
            (((bytes[offset]&255)-128)&255));
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
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
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
        final LegendaryPetFusionService service;
        final AtomicTransactionService transactions;

        Fixture(
            LegendaryPetFusionService service,
            AtomicTransactionService transactions
        ){
            this.service=service;
            this.transactions=transactions;
        }
    }

    private LegendaryPetFusionPresentationTest(){}
}
