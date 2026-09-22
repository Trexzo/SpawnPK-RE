package spk.local;

/** Exact-current opcode-121 region/scene loading completion acknowledgement. */
final class RegionLoadAckClientRequest
    implements ClientRequest {

    private final ClientRequestMetadata metadata;

    RegionLoadAckClientRequest(
        ClientRequestMetadata metadata
    ){
        this.metadata=
            java.util.Objects.requireNonNull(
                metadata,
                "metadata"
            );
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "RegionLoadAckClientRequest{"+
            metadata+
            "}";
    }
}
