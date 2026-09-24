package spk.content.api;

import java.util.Locale;
import java.util.Objects;

/** Allow/deny decision for one semantic runtime action. */
public final class ContentActionResult {
    public enum Decision {
        ALLOW,
        DENY
    }

    private final Decision decision;
    private final String reasonKey;

    private ContentActionResult(
        Decision decision,
        String reasonKey
    ){
        this.decision=
            Objects.requireNonNull(
                decision,
                "decision"
            );
        this.reasonKey=reasonKey;
    }

    public static ContentActionResult allow(){
        return new ContentActionResult(
            Decision.ALLOW,
            null
        );
    }

    public static ContentActionResult deny(){
        return new ContentActionResult(
            Decision.DENY,
            null
        );
    }

    public static ContentActionResult deny(
        String reasonKey
    ){
        return new ContentActionResult(
            Decision.DENY,
            normalizeKey(
                reasonKey,
                "reasonKey"
            )
        );
    }

    public Decision decision(){
        return decision;
    }

    public boolean allowed(){
        return decision==Decision.ALLOW;
    }

    public String reasonKey(){
        return reasonKey;
    }

    private static String normalizeKey(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        String clean=value.trim()
            .toLowerCase(Locale.ROOT);

        if(clean.isEmpty()||
           clean.length()>160)
            throw new IllegalArgumentException(
                field+" invalid length"
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
