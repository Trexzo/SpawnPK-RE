package spk.local;

import java.util.Objects;

/** Exact-current C2S126 private-message intent decoded from v308 wire format. */
final class PrivateMessageClientRequest
    implements ClientRequest {

    private final long recipientNameKey;
    private final String message;
    private final ClientRequestMetadata metadata;

    PrivateMessageClientRequest(
        long recipientNameKey,
        String message,
        ClientRequestMetadata metadata
    ){
        this.recipientNameKey=
            recipientNameKey;
        this.message=Objects.requireNonNull(
            message,
            "message"
        );
        this.metadata=Objects.requireNonNull(
            metadata,
            "metadata"
        );
    }

    long recipientNameKey(){
        return recipientNameKey;
    }

    String message(){
        return message;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "PrivateMessageClientRequest{recipientNameKey="+
            Long.toUnsignedString(
                recipientNameKey
            )+
            ",messageLength="+
            message.length()+
            ",metadata="+
            metadata+
            "}";
    }
}
