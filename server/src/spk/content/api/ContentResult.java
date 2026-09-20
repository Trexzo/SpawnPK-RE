package spk.content.api;

import java.util.Objects;

/** Content-domain outcome consumed by the session/domain adapter. */
public final class ContentResult {
    private final String logText;
    private final String saveReason;

    private ContentResult(
        String logText,
        String saveReason
    ){
        this.logText=Objects.requireNonNull(
            logText,
            "logText"
        );
        this.saveReason=saveReason;
    }

    public static ContentResult handled(
        String logText,
        String saveReason
    ){
        return new ContentResult(
            logText,
            saveReason
        );
    }

    public String logText(){
        return logText;
    }

    public String saveReason(){
        return saveReason;
    }

    @Override public String toString(){
        return "ContentResult{saveReason="+
            saveReason+
            ",logText="+logText+"}";
    }
}
