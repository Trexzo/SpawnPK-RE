package spk.local;

import java.io.IOException;
import java.util.*;

/**
 * Exact-v308 Legendary Pet Fusing presentation/input adapter.
 *
 * Exact client authority is limited to root/widget identity, the contextual
 * C2S185 Fuse alias, the close control, the three low-id item presentation
 * widgets and the legacy static defaults. Recipe, economy, RNG and settlement
 * policy remain owned by LegendaryPetFusionService / ConversionService callers.
 */
final class LegendaryPetFusionPresentation {
    static final int ROOT=18547;

    static final int INGREDIENT_WIDGET=18548;
    static final int COST_WIDGET=18549;
    static final int RESULT_WIDGET=18550;
    static final int AVAILABILITY_TEXT_WIDGET=18552;

    static final int FUSE_INTERNAL_WIDGET=65559;
    static final int FUSE_WIRE_WIDGET=
        FUSE_INTERNAL_WIDGET&0xffff;
    static final int CLOSE_WIDGET=65418;

    static final int WIDGET_ACTION_OPCODE=185;
    static final boolean STATE_WIRE_OWNED=false;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    /**
     * Static v308 interface defaults retained as evidence only.
     * These values are never consulted by project(...) or publish(...).
     */
    static final class LegacyEvidence {
        static final int INGREDIENT_0_ITEM=12111;
        static final int INGREDIENT_0_AMOUNT=3;
        static final int INGREDIENT_1_ITEM=15000;
        static final int INGREDIENT_1_AMOUNT=3;
        static final int COST_ITEM=11337;
        static final int COST_AMOUNT=500;
        static final int RESULT_ITEM=12113;
        static final int RESULT_AMOUNT=1;
        static final String STATUS_TEXT=
            "@gre@Limited time pet fusion!";
        static final String AVAILABILITY_TEXT=
            "New pet ETA: @whi@10/26/2016";

        private LegacyEvidence(){}
    }

    enum InputKind {
        FUSE,
        CLOSE
    }

    static final class Input {
        final InputKind kind;

        private Input(InputKind kind){
            this.kind=
                Objects.requireNonNull(
                    kind,
                    "kind"
                );
        }
    }

    static final class Action {
        final InputKind kind;
        final ConversionService.RecipeAttemptId
            attemptId;

        private Action(
            InputKind kind,
            ConversionService.RecipeAttemptId attemptId
        ){
            this.kind=
                Objects.requireNonNull(
                    kind,
                    "kind"
                );
            this.attemptId=attemptId;
        }
    }

    static final class ItemStack {
        final int itemId;
        final int amount;

        ItemStack(int itemId,int amount){
            if(itemId<0||itemId>=0xffff)
                throw new IllegalArgumentException(
                    "itemId="+itemId
                );
            if(amount<=0)
                throw new IllegalArgumentException(
                    "amount="+amount
                );

            this.itemId=itemId;
            this.amount=amount;
        }
    }

    static final class View {
        final String offeringKey;
        final String displayName;
        final String statusText;
        final String availabilityText;
        final LegendaryPetFusionService
            .PresentationState state;
        final List<ItemStack> ingredients;
        final List<ItemStack> costs;
        final List<ItemStack> results;

        private View(
            LegendaryPetFusionService.Offering offering,
            LegendaryPetFusionService
                .PresentationState state,
            List<ItemStack> ingredients,
            List<ItemStack> costs,
            List<ItemStack> results
        ){
            this.offeringKey=offering.offeringKey;
            this.displayName=offering.displayName;
            this.statusText=offering.statusText;
            this.availabilityText=
                offering.availabilityText;
            this.state=
                Objects.requireNonNull(
                    state,
                    "state"
                );
            this.ingredients=immutable(
                ingredients,
                2,
                "ingredients"
            );
            this.costs=immutable(
                costs,
                1,
                "costs"
            );
            this.results=immutable(
                results,
                1,
                "results"
            );
        }
    }

    static Input resolveWidget(
        int activeRoot,
        int wireWidgetId
    ){
        checkedU16(activeRoot,"activeRoot");
        checkedU16(wireWidgetId,"wireWidgetId");

        if(activeRoot!=ROOT)
            return null;

        if(wireWidgetId==FUSE_WIRE_WIDGET)
            return new Input(
                InputKind.FUSE
            );

        if(wireWidgetId==CLOSE_WIDGET)
            return new Input(
                InputKind.CLOSE
            );

        return null;
    }

    /**
     * Normalizes exact client input into the existing semantic service.
     * CLOSE is deliberately presentation-only and does not mutate the service.
     */
    static Action apply(
        LegendaryPetFusionService service,
        String playerRef,
        int activeRoot,
        int wireWidgetId
    ){
        Objects.requireNonNull(
            service,
            "service"
        );

        Input input=
            resolveWidget(
                activeRoot,
                wireWidgetId
            );

        if(input==null)
            return null;

        if(input.kind==InputKind.CLOSE)
            return new Action(
                InputKind.CLOSE,
                null
            );

        return new Action(
            InputKind.FUSE,
            service.beginFusion(
                playerRef
            )
        );
    }

    static View project(
        LegendaryPetFusionService service,
        String playerRef,
        List<ItemStack> ingredients,
        List<ItemStack> costs,
        List<ItemStack> results
    ){
        Objects.requireNonNull(
            service,
            "service"
        );

        LegendaryPetFusionService.Snapshot
            snapshot=
                service.snapshot();

        LegendaryPetFusionService.Offering
            offering=
                snapshot.currentOffering;

        if(offering==null)
            throw new IllegalStateException(
                "Legendary Pet Fusion offering missing"
            );

        LegendaryPetFusionService.PlayerSnapshot
            player=
                service.getPlayer(
                    playerRef
                );

        LegendaryPetFusionService
            .PresentationState state=
                player==null
                    ?LegendaryPetFusionService
                        .PresentationState
                        .IDLE
                    :player.presentationState;

        return new View(
            offering,
            state,
            ingredients,
            costs,
            results
        );
    }

    static void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        ).fixed(
            97,
            BootstrapPackets
                .interface97(ROOT)
        );
    }

    /**
     * Uses only exact low-id presentation routes.
     *
     * High static title/status/button ids in the 65552+ family are not rewritten
     * because ordinary widget-id packet fields cannot address their full ids.
     */
    static void publish(
        ServerPacketWriter packets,
        View view
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );
        Objects.requireNonNull(
            view,
            "view"
        );

        packets.varShort(
            53,
            itemContainer(
                INGREDIENT_WIDGET,
                view.ingredients
            )
        );
        packets.varShort(
            53,
            itemContainer(
                COST_WIDGET,
                view.costs
            )
        );
        packets.varShort(
            53,
            itemContainer(
                RESULT_WIDGET,
                view.results
            )
        );

        if(view.availabilityText!=null&&
           !view.availabilityText.trim()
               .isEmpty())
            ApplicationBus126Publisher.send(
                packets,
                AVAILABILITY_TEXT_WIDGET,
                requireSingleLine(
                    view.availabilityText,
                    "availabilityText"
                )
            );
    }

    static void publishEmpty(
        ServerPacketWriter packets,
        String availabilityText
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );

        packets.varShort(
            53,
            itemContainer(
                INGREDIENT_WIDGET,
                Collections.emptyList()
            )
        );
        packets.varShort(
            53,
            itemContainer(
                COST_WIDGET,
                Collections.emptyList()
            )
        );
        packets.varShort(
            53,
            itemContainer(
                RESULT_WIDGET,
                Collections.emptyList()
            )
        );

        if(availabilityText!=null&&
           !availabilityText.trim().isEmpty())
            ApplicationBus126Publisher.send(
                packets,
                AVAILABILITY_TEXT_WIDGET,
                requireSingleLine(
                    availabilityText,
                    "availabilityText"
                )
            );
    }

    static byte[] itemContainer(
        int widgetId,
        List<ItemStack> items
    )throws IOException{
        Objects.requireNonNull(
            items,
            "items"
        );

        int[] ids=new int[items.size()];
        int[] amounts=
            new int[items.size()];

        for(int i=0;i<items.size();i++){
            ItemStack item=
                Objects.requireNonNull(
                    items.get(i),
                    "item"
                );
            ids[i]=item.itemId;
            amounts[i]=item.amount;
        }

        return BootstrapPackets
            .itemContainer53(
                widgetId,
                ids,
                amounts
            );
    }

    private static List<ItemStack> immutable(
        List<ItemStack> input,
        int max,
        String field
    ){
        Objects.requireNonNull(
            input,
            field
        );

        if(input.size()>max)
            throw new IllegalArgumentException(
                field+" max="+max+
                " actual="+input.size()
            );

        ArrayList<ItemStack> copy=
            new ArrayList<>(
                input.size()
            );

        for(ItemStack item:input)
            copy.add(
                Objects.requireNonNull(
                    item,
                    field+" item"
                )
            );

        return Collections
            .unmodifiableList(copy);
    }

    private static String requireSingleLine(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(
                field
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        if(clean.indexOf('\n')>=0||
           clean.indexOf('\r')>=0)
            throw new IllegalArgumentException(
                field+" contains line terminator"
            );

        for(int i=0;i<clean.length();i++)
            if(clean.charAt(i)>0xff)
                throw new IllegalArgumentException(
                    field+
                    " not ISO-8859-1 at index="+
                    i
                );

        return clean;
    }

    private static void checkedU16(
        int value,
        String field
    ){
        if(value<0||value>0xffff)
            throw new IllegalArgumentException(
                field+"="+value
            );
    }

    private LegendaryPetFusionPresentation(){}
}
