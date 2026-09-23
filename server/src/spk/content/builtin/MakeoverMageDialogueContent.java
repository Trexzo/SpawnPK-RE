package spk.content.builtin;

import spk.content.api.*;

/**
 * LocalLab-owned Make-over Mage semantic dialogue transition policy.
 *
 * Exact client presentation/transport lives elsewhere; this class owns only
 * the reconstructed LocalLab branch policy.
 */
public final class MakeoverMageDialogueContent
    implements ContentDialogueHandler {

    public static final String DIALOGUE_KEY=
        "dialogue:makeover-mage";
    public static final String INTRO_NODE=
        "node:intro";
    public static final String OPTIONS_NODE=
        "node:options";

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
