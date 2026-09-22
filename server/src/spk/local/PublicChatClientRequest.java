package spk.local;

import java.util.Objects;

/** Exact-current C2S4 public-chat intent decoded from v308 wire format. */
final class PublicChatClientRequest
    implements ClientRequest {

    private final int effect;
    private final int colour;
    private final String message;
    private final ClientRequestMetadata metadata;

    PublicChatClientRequest(
        int effect,
        int colour,
        String message,
        ClientRequestMetadata metadata
    ){
        this.effect=requireU8(
            effect,
            "effect"
        );
        this.colour=requireU8(
            colour,
            "colour"
        );
        this.message=Objects.requireNonNull(
            message,
            "message"
        );
        this.metadata=Objects.requireNonNull(
            metadata,
            "metadata"
        );
    }

    int effect(){return effect;}
    int colour(){return colour;}
    String message(){return message;}

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "PublicChatClientRequest{effect="+
            effect+
            ",colour="+
            colour+
            ",messageLength="+
            message.length()+
            ",metadata="+
            metadata+
            "}";
    }

    private static int requireU8(
        int value,
        String label
    ){
        if(value<0||value>255)
            throw new IllegalArgumentException(
                label+"="+value
            );
        return value;
    }
}
