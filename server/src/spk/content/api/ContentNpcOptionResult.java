package spk.content.api;

import java.util.Objects;

/** Typed NPC-option semantic decision. */
public final class ContentNpcOptionResult {
    private final ContentNpcService service;

    private ContentNpcOptionResult(
        ContentNpcService service
    ){
        this.service=Objects.requireNonNull(
            service,
            "service"
        );
    }

    public static ContentNpcOptionResult handled(
        ContentNpcService service
    ){
        return new ContentNpcOptionResult(
            service
        );
    }

    public ContentNpcService service(){
        return service;
    }

    @Override public String toString(){
        return "ContentNpcOptionResult{service="+
            service+"}";
    }
}
