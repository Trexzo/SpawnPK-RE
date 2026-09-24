package spk.content.api;

import java.util.Locale;
import java.util.Objects;

/** Immutable semantic dialogue node definition with no transport/UI identity. */
public final class ContentDialogueNode {
    public enum InputMode {
        CONTINUE,
        OPTIONS
    }

    private final String nodeKey;
    private final InputMode inputMode;
    private final int optionCount;
    private final boolean closeSupported;

    public ContentDialogueNode(
        String nodeKey,
        InputMode inputMode,
        int optionCount,
        boolean closeSupported
    ){
        this.nodeKey=
            normalizeKey(
                nodeKey,
                "nodeKey"
            );
        this.inputMode=
            Objects.requireNonNull(
                inputMode,
                "inputMode"
            );

        if(inputMode==InputMode.CONTINUE){
            if(optionCount!=0)
                throw new IllegalArgumentException(
                    "CONTINUE node optionCount="+
                    optionCount
                );
            if(closeSupported)
                throw new IllegalArgumentException(
                    "CONTINUE node cannot claim option close"
                );
        }else{
            if(optionCount<1||optionCount>5)
                throw new IllegalArgumentException(
                    "OPTIONS optionCount="+
                    optionCount+
                    " expected=1..5"
                );
        }

        this.optionCount=optionCount;
        this.closeSupported=closeSupported;
    }

    public String nodeKey(){
        return nodeKey;
    }

    public InputMode inputMode(){
        return inputMode;
    }

    public int optionCount(){
        return optionCount;
    }

    public boolean closeSupported(){
        return closeSupported;
    }

    static String normalizeKey(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        String clean=value.trim()
            .toLowerCase(Locale.ROOT);

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
