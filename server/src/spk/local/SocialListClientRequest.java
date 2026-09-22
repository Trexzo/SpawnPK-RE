package spk.local;

import java.util.Objects;

/**
 * Exact-current friends/ignore mutation intent.
 *
 * The client wire proves the semantic action plus one raw 64-bit name key.
 * Name-key decoding into a canonical account name is intentionally outside
 * this transport object until that mapping is independently proven.
 */
final class SocialListClientRequest
    implements ClientRequest {

    enum Action {
        ADD_FRIEND,
        REMOVE_FRIEND,
        ADD_IGNORE,
        REMOVE_IGNORE
    }

    private final Action action;
    private final long nameKey;
    private final ClientRequestMetadata metadata;

    SocialListClientRequest(
        Action action,
        long nameKey,
        ClientRequestMetadata metadata
    ){
        this.action=Objects.requireNonNull(
            action,
            "action"
        );
        this.nameKey=nameKey;
        this.metadata=Objects.requireNonNull(
            metadata,
            "metadata"
        );
    }

    Action action(){
        return action;
    }

    long nameKey(){
        return nameKey;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "SocialListClientRequest{action="+
            action+
            ",nameKey="+
            Long.toUnsignedString(nameKey)+
            ",metadata="+
            metadata+
            "}";
    }
}
