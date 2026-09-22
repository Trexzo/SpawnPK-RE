package spk.local;

import java.util.Objects;

/**
 * Exact-current three-byte chat-control mode snapshot.
 *
 * Field ordering is preserved as mode0/mode1/mode2 because original-server
 * privacy/presence policy is not implied by the transport alone.
 */
final class ChatModeClientRequest
    implements ClientRequest {

    private final int mode0;
    private final int mode1;
    private final int mode2;
    private final ClientRequestMetadata metadata;

    ChatModeClientRequest(
        int mode0,
        int mode1,
        int mode2,
        ClientRequestMetadata metadata
    ){
        this.mode0=requireU8(mode0,"mode0");
        this.mode1=requireU8(mode1,"mode1");
        this.mode2=requireU8(mode2,"mode2");
        this.metadata=Objects.requireNonNull(
            metadata,
            "metadata"
        );
    }

    int mode0(){return mode0;}
    int mode1(){return mode1;}
    int mode2(){return mode2;}

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "ChatModeClientRequest{mode0="+
            mode0+
            ",mode1="+
            mode1+
            ",mode2="+
            mode2+
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
