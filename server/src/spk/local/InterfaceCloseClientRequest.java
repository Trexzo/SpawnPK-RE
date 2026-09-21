package spk.local;

/** Domain-facing exact-current interface-close intent decoded from opcode 130. */
final class InterfaceCloseClientRequest
    implements ClientRequest {

    private final ClientRequestMetadata metadata;

    InterfaceCloseClientRequest(
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
        return "InterfaceCloseClientRequest{"+
            metadata+"}";
    }
}
