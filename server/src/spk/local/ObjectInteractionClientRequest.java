package spk.local;

/** Exact-current opcode 132 object first-option request. */
final class ObjectInteractionClientRequest
    implements ClientRequest {

    private final ObjectInteraction interaction;
    private final ClientRequestMetadata metadata;

    ObjectInteractionClientRequest(
        ObjectInteraction interaction,
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

    ObjectInteraction interaction(){
        return interaction;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "ObjectInteractionClientRequest{interaction="+
            interaction+
            ",metadata="+metadata+
            "}";
    }
}
