package spk.content.api;

/**
 * Transport-neutral presentation failure exposed to content.
 *
 * Internal runtime adapters may retain a lower-level cause for diagnostics, but
 * content modules are not required to depend on packet-writer or java.io types.
 */
public final class ContentPresentationException extends RuntimeException {
    public ContentPresentationException(
        String message,
        Throwable cause
    ){
        super(message,cause);
    }
}
