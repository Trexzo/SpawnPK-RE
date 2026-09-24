package spk.content.api;

import java.util.Locale;
import java.util.Objects;

/** Protocol-independent content decision for a semantic dialogue transition. */
public final class ContentDialogueTransition {
    public enum Kind {
        STAY,
        MOVE,
        END
    }

    private final Kind kind;
    private final String nextNodeKey;
    private final String outcomeKey;

    private ContentDialogueTransition(
        Kind kind,
        String nextNodeKey,
        String outcomeKey
    ){
        this.kind=Objects.requireNonNull(kind,"kind");
        this.nextNodeKey=nextNodeKey;
        this.outcomeKey=outcomeKey;
    }

    public static ContentDialogueTransition stay(){
        return new ContentDialogueTransition(
            Kind.STAY,
            null,
            null
        );
    }

    public static ContentDialogueTransition move(
        String nextNodeKey
    ){
        return new ContentDialogueTransition(
            Kind.MOVE,
            normalizeKey(
                nextNodeKey,
                "nextNodeKey"
            ),
            null
        );
    }

    public static ContentDialogueTransition end(){
        return new ContentDialogueTransition(
            Kind.END,
            null,
            null
        );
    }

    public static ContentDialogueTransition end(
        String outcomeKey
    ){
        return new ContentDialogueTransition(
            Kind.END,
            null,
            normalizeKey(
                outcomeKey,
                "outcomeKey"
            )
        );
    }

    public Kind kind(){
        return kind;
    }

    public String nextNodeKey(){
        return nextNodeKey;
    }

    public String outcomeKey(){
        return outcomeKey;
    }

    private static String normalizeKey(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        String clean=value.trim().toLowerCase(
            Locale.ROOT
        );

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        if(clean.length()>160)
            throw new IllegalArgumentException(
                field+" too long"
            );

        for(int i=0;i<clean.length();i++){
            char ch=clean.charAt(i);
            if((ch>='a'&&ch<='z')||
               (ch>='0'&&ch<='9')||
               ch=='.'||ch=='_'||
               ch=='-'||ch==':')
                continue;

            throw new IllegalArgumentException(
                field+
                " invalid character at index="+
                i
            );
        }

        return clean;
    }
}
