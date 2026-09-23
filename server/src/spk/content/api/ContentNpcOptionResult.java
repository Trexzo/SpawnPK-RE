package spk.content.api;

import java.util.Locale;
import java.util.Objects;

/** Typed NPC-option semantic decision. */
public final class ContentNpcOptionResult {
    private final ContentNpcService service;
    private final String actionKey;

    private ContentNpcOptionResult(
        ContentNpcService service,
        String actionKey
    ){
        this.service=Objects.requireNonNull(
            service,
            "service"
        );
        this.actionKey=actionKey;
    }

    public static ContentNpcOptionResult handled(
        ContentNpcService service
    ){
        return new ContentNpcOptionResult(
            service,
            null
        );
    }

    /**
     * Request one explicitly runtime-supported semantic content action.
     *
     * Action keys are protocol-independent identifiers. The runtime retains
     * the allowlist/executor boundary and must fail closed on unknown keys.
     */
    public static ContentNpcOptionResult action(
        String actionKey
    ){
        return new ContentNpcOptionResult(
            ContentNpcService.NONE,
            normalizeActionKey(
                actionKey
            )
        );
    }

    public ContentNpcService service(){
        return service;
    }

    public String actionKey(){
        return actionKey;
    }

    public boolean hasAction(){
        return actionKey!=null;
    }

    @Override public String toString(){
        return "ContentNpcOptionResult{service="+
            service+
            (actionKey==null
                ?""
                :",actionKey="+actionKey)+
            "}";
    }

    private static String normalizeActionKey(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "actionKey"
            );

        String key=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(key.isEmpty()||
           key.length()>160)
            throw new IllegalArgumentException(
                "actionKey invalid length"
            );

        for(int i=0;i<key.length();i++){
            char c=key.charAt(i);
            boolean valid=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||
                c=='_'||
                c=='-'||
                c==':';

            if(!valid)
                throw new IllegalArgumentException(
                    "actionKey invalid="+
                    value
                );
        }

        return key;
    }
}
