package spk.local;

import java.util.Objects;

/**
 * Exact-current response to a server-opened generic name-entry prompt.
 *
 * The raw 64-bit name key is preserved until the client key-to-account-name
 * mapping and active prompt/domain context are independently proven.
 */
final class NameEntryClientRequest
    implements ClientRequest {

    private final long nameKey;
    private final ClientRequestMetadata metadata;

    NameEntryClientRequest(
        long nameKey,
        ClientRequestMetadata metadata
    ){
        this.nameKey=nameKey;
        this.metadata=Objects.requireNonNull(
            metadata,
            "metadata"
        );
    }

    long nameKey(){
        return nameKey;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "NameEntryClientRequest{nameKey="+
            Long.toUnsignedString(nameKey)+
            ",metadata="+
            metadata+
            "}";
    }
}
