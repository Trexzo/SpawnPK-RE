package spk.local;

/** Exact-current spell-target request. */
final class SpellTargetClientRequest
    implements ClientRequest {

    private final SpellTargetRequest request;
    private final ClientRequestMetadata metadata;

    SpellTargetClientRequest(
        SpellTargetRequest request,
        ClientRequestMetadata metadata
    ){
        this.request=
            java.util.Objects.requireNonNull(
                request,
                "request"
            );
        this.metadata=
            java.util.Objects.requireNonNull(
                metadata,
                "metadata"
            );
    }

    SpellTargetRequest request(){
        return request;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "SpellTargetClientRequest{request="+
            request+
            ",metadata="+metadata+
            "}";
    }
}
