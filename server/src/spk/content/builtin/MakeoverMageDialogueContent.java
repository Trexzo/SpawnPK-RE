package spk.content.builtin;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import spk.content.api.*;

/**
 * LocalLab-owned Make-over Mage semantic dialogue content.
 *
 * This class owns reconstructed LocalLab branch policy plus the dialogue copy.
 * Exact client roots/widgets/packets remain behind ContentDialoguePresentation.
 */
public final class MakeoverMageDialogueContent
    implements ContentDialogueHandler {

    public static final String DIALOGUE_KEY=
        "dialogue:makeover-mage";
    public static final String INTRO_NODE=
        "node:intro";
    public static final String OPTIONS_NODE=
        "node:options";

    private static final ContentDialogueDefinition DEFINITION=
        new ContentDialogueDefinition(
            DIALOGUE_KEY,
            INTRO_NODE,
            Arrays.asList(
                new ContentDialogueNode(
                    INTRO_NODE,
                    ContentDialogueNode.InputMode.CONTINUE,
                    0,
                    false
                ),
                new ContentDialogueNode(
                    OPTIONS_NODE,
                    ContentDialogueNode.InputMode.OPTIONS,
                    2,
                    true
                )
            )
        );

    @Override public ContentDialogueDefinition definition(){
        return DEFINITION;
    }

    public static void presentIntro(
        ContentDialoguePresentation presentation
    ){
        Objects.requireNonNull(
            presentation,
            "presentation"
        ).namedNpc(
            LocalLabCoreContentModule
                .MAKEOVER_MAGE_NPC,
            "Make-over Mage",
            Collections.singletonList(
                "How may I help you?"
            )
        );
    }

    public static void presentOptions(
        ContentDialoguePresentation presentation
    ){
        Objects.requireNonNull(
            presentation,
            "presentation"
        ).twoOptions(
            "Select an Option",
            Arrays.asList(
                "I'd like to change my look.",
                "Nevermind."
            )
        );
    }

    @Override public ContentDialogueTransition handle(
        ContentDialogueContext context
    ){
        if(context==null)
            throw new NullPointerException("context");

        ContentDialogueIntent intent=
            context.intent();

        if(!DIALOGUE_KEY.equals(
                context.dialogueKey()))
            throw new IllegalArgumentException(
                "unexpected dialogue "+
                context.dialogueKey()
            );

        if(INTRO_NODE.equals(
                context.nodeKey())&&
           intent.kind()==
                ContentDialogueIntent.Kind.CONTINUE)
            return ContentDialogueTransition.move(
                OPTIONS_NODE
            );

        if(OPTIONS_NODE.equals(
                context.nodeKey())&&
           (intent.kind()==
                ContentDialogueIntent.Kind.OPTION||
            intent.kind()==
                ContentDialogueIntent.Kind.CLOSE))
            return ContentDialogueTransition.end();

        throw new IllegalStateException(
            "unsupported Make-over dialogue transition node="+
            context.nodeKey()+
            " intent="+
            intent.kind()
        );
    }
}
