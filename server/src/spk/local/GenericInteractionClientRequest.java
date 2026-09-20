package spk.local;

/** Exact-current promoted generic interaction request. */
final class GenericInteractionClientRequest
    implements ClientRequest {

    private final GenericInteractionEvent event;
    private final ClientRequestMetadata metadata;

    GenericInteractionClientRequest(
        GenericInteractionEvent event,
        ClientRequestMetadata metadata
    ){
        this.event=
            java.util.Objects.requireNonNull(
                event,
                "event"
            );
        this.metadata=
            java.util.Objects.requireNonNull(
                metadata,
                "metadata"
            );
    }

    GenericInteractionEvent event(){
        return event;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "GenericInteractionClientRequest{event="+
            event+
            ",metadata="+metadata+
            "}";
    }
}
