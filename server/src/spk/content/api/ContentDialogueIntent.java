package spk.content.api;

import java.util.Objects;

/** Protocol-independent dialogue input intent exposed to content. */
public final class ContentDialogueIntent {
    public enum Kind {
        CONTINUE,
        OPTION,
        CLOSE
    }

    private final Kind kind;
    private final int optionIndex;

    private ContentDialogueIntent(
        Kind kind,
        int optionIndex
    ){
        this.kind=Objects.requireNonNull(kind,"kind");
        this.optionIndex=optionIndex;
    }

    public static ContentDialogueIntent continueIntent(){
        return new ContentDialogueIntent(
            Kind.CONTINUE,
            0
        );
    }

    public static ContentDialogueIntent option(
        int optionIndex
    ){
        if(optionIndex<1||optionIndex>5)
            throw new IllegalArgumentException(
                "optionIndex="+optionIndex+
                " expected=1..5"
            );

        return new ContentDialogueIntent(
            Kind.OPTION,
            optionIndex
        );
    }

    public static ContentDialogueIntent closeIntent(){
        return new ContentDialogueIntent(
            Kind.CLOSE,
            0
        );
    }

    public Kind kind(){
        return kind;
    }

    public int optionIndex(){
        return optionIndex;
    }

    @Override public String toString(){
        return "ContentDialogueIntent{kind="+
            kind+
            ",optionIndex="+
            optionIndex+
            "}";
    }
}
