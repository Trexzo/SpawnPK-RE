package spk.local;

/** Exact-current ground-item option request. */
final class GroundItemClientRequest
    implements ClientRequest {

    private final GroundItemInteraction interaction;
    private final ClientRequestMetadata metadata;

    GroundItemClientRequest(
        GroundItemInteraction interaction,
        ClientRequestMetadata metadata
    ){
        this.interaction=
            java.util.Objects.requireNonNull(
                interaction,
                "interaction"
            );
        this.metadata=
            java.util.Objects.requireNonNull(
                metadata,
                "metadata"
            );
    }

    GroundItemInteraction interaction(){
        return interaction;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "GroundItemClientRequest{interaction="+
            interaction+
            ",metadata="+metadata+
            "}";
    }
}
