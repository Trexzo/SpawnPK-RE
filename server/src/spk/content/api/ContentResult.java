package spk.content.api;

import java.util.Locale;
import java.util.Objects;

/** Content-domain outcome consumed by the session/domain adapter. */
public final class ContentResult {
    private final String logText;
    private final String saveReason;
    private final String actionKey;

    private ContentResult(
        String logText,
        String saveReason,
        String actionKey
    ){
        this.logText=Objects.requireNonNull(
            logText,
            "logText"
        );
        this.saveReason=saveReason;
        this.actionKey=actionKey;
    }

    public static ContentResult handled(
        String logText,
        String saveReason
    ){
        return new ContentResult(
            logText,
            saveReason,
            null
        );
    }

    /**
     * Request one explicitly runtime-supported semantic command effect.
     *
     * Content owns the command/subcommand policy. The runtime retains a strict
     * allowlist for session-bound effects and must fail closed on unknown keys.
     */
    public static ContentResult action(
        String actionKey
    ){
        return new ContentResult(
            "",
            null,
            normalizeActionKey(
                actionKey
            )
        );
    }

    public String logText(){
        return logText;
    }

    public String saveReason(){
        return saveReason;
    }

    public boolean hasAction(){
        return actionKey!=null;
    }

    public String actionKey(){
        return actionKey;
    }

    @Override public String toString(){
        return "ContentResult{saveReason="+
            saveReason+
            (actionKey==null
                ?""
                :",actionKey="+actionKey)+
            ",logText="+logText+"}";
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
