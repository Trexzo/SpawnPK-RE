package spk.content.builtin;

import java.util.Arrays;
import java.util.Objects;
import spk.content.api.*;

/**
 * Explicit LocalLab supplies policy presented through exact-current standard
 * dialogue UI. NPC identity/action transport authority is kept outside this
 * policy; catalog/prices remain LocalLab gameplay choices.
 */
public final class SuppliesMerchantDialogueContent
    implements ContentDialogueHandler {

    public static final String DIALOGUE_KEY=
        "dialogue:locallab-supplies-merchant";
    public static final String ACTION_NODE=
        "node:action";
    public static final String ROCKTAIL_ACTION_NODE=
        "node:rocktail-action";
    public static final String BUY_QUANTITY_NODE=
        "node:buy-quantity";
    public static final String SELL_QUANTITY_NODE=
        "node:sell-quantity";
    public static final String WHIP_ACTION_NODE=
        "node:whip-action";

    public static final String OUTCOME_BUY_ONE=
        "supplies:buy:1";
    public static final String OUTCOME_BUY_FIVE=
        "supplies:buy:5";
    public static final String OUTCOME_SELL_ONE=
        "supplies:sell:1";
    public static final String OUTCOME_SELL_FIVE=
        "supplies:sell:5";
    public static final String OUTCOME_BUY_WHIP=
        "supplies:buy-whip:1";
    public static final String OUTCOME_CANCEL=
        "supplies:cancel";

    private static final ContentDialogueDefinition DEFINITION=
        new ContentDialogueDefinition(
            DIALOGUE_KEY,
            ACTION_NODE,
            Arrays.asList(
                new ContentDialogueNode(
                    ACTION_NODE,
                    ContentDialogueNode.InputMode.OPTIONS,
                    2,
                    true
                ),
                new ContentDialogueNode(
                    ROCKTAIL_ACTION_NODE,
                    ContentDialogueNode.InputMode.OPTIONS,
                    2,
                    true
                ),
                new ContentDialogueNode(
                    BUY_QUANTITY_NODE,
                    ContentDialogueNode.InputMode.OPTIONS,
                    2,
                    true
                ),
                new ContentDialogueNode(
                    SELL_QUANTITY_NODE,
                    ContentDialogueNode.InputMode.OPTIONS,
                    2,
                    true
                ),
                new ContentDialogueNode(
                    WHIP_ACTION_NODE,
                    ContentDialogueNode.InputMode.OPTIONS,
                    2,
                    true
                )
            )
        );

    @Override public ContentDialogueDefinition definition(){
        return DEFINITION;
    }

    public static void presentAction(
        ContentDialoguePresentation presentation
    ){
        Objects.requireNonNull(presentation,"presentation")
            .twoOptions(
                "LocalLab Supplies",
                Arrays.asList(
                    "Rocktail",
                    "Abyssal whip"
                )
            );
    }

    public static void presentRocktailAction(
        ContentDialoguePresentation presentation
    ){
        Objects.requireNonNull(presentation,"presentation")
            .twoOptions(
                "Rocktail",
                Arrays.asList(
                    "Buy Rocktail",
                    "Sell Rocktail"
                )
            );
    }

    public static void presentBuyQuantity(
        ContentDialoguePresentation presentation
    ){
        Objects.requireNonNull(presentation,"presentation")
            .twoOptions(
                "Buy Rocktail",
                Arrays.asList(
                    "Buy 1",
                    "Buy 5"
                )
            );
    }

    public static void presentSellQuantity(
        ContentDialoguePresentation presentation
    ){
        Objects.requireNonNull(presentation,"presentation")
            .twoOptions(
                "Sell Rocktail",
                Arrays.asList(
                    "Sell 1",
                    "Sell 5"
                )
            );
    }

    public static void presentWhipAction(
        ContentDialoguePresentation presentation
    ){
        Objects.requireNonNull(presentation,"presentation")
            .twoOptions(
                "Abyssal whip",
                Arrays.asList(
                    "Buy 1",
                    "Cancel"
                )
            );
    }

    @Override public ContentDialogueTransition handle(
        ContentDialogueContext context
    ){
        Objects.requireNonNull(context,"context");

        if(!DIALOGUE_KEY.equals(context.dialogueKey()))
            throw new IllegalArgumentException(
                "unexpected supplies dialogue "+context.dialogueKey()
            );

        ContentDialogueIntent intent=context.intent();

        if(intent.kind()==ContentDialogueIntent.Kind.CLOSE)
            return ContentDialogueTransition.end(
                OUTCOME_CANCEL
            );

        if(intent.kind()!=ContentDialogueIntent.Kind.OPTION)
            throw new IllegalStateException(
                "supplies dialogue requires option/close intent node="+
                context.nodeKey()
            );

        int option=intent.optionIndex();

        if(ACTION_NODE.equals(context.nodeKey())){
            if(option==1)
                return ContentDialogueTransition.move(
                    ROCKTAIL_ACTION_NODE
                );
            if(option==2)
                return ContentDialogueTransition.move(
                    WHIP_ACTION_NODE
                );
        }

        if(ROCKTAIL_ACTION_NODE.equals(context.nodeKey())){
            if(option==1)
                return ContentDialogueTransition.move(
                    BUY_QUANTITY_NODE
                );
            if(option==2)
                return ContentDialogueTransition.move(
                    SELL_QUANTITY_NODE
                );
        }

        if(BUY_QUANTITY_NODE.equals(context.nodeKey())){
            if(option==1)
                return ContentDialogueTransition.end(
                    OUTCOME_BUY_ONE
                );
            if(option==2)
                return ContentDialogueTransition.end(
                    OUTCOME_BUY_FIVE
                );
        }

        if(SELL_QUANTITY_NODE.equals(context.nodeKey())){
            if(option==1)
                return ContentDialogueTransition.end(
                    OUTCOME_SELL_ONE
                );
            if(option==2)
                return ContentDialogueTransition.end(
                    OUTCOME_SELL_FIVE
                );
        }

        if(WHIP_ACTION_NODE.equals(context.nodeKey())){
            if(option==1)
                return ContentDialogueTransition.end(
                    OUTCOME_BUY_WHIP
                );
            if(option==2)
                return ContentDialogueTransition.end(
                    OUTCOME_CANCEL
                );
        }

        throw new IllegalStateException(
            "unsupported supplies transition node="+
            context.nodeKey()+
            " option="+option
        );
    }
}
