package spk.local;

/** Exact-current item-container action carried by the bounded typed FIFO. */
final class ItemContainerActionClientRequest
    implements ClientRequest {

    private final ItemContainerAction action;
    private final ClientRequestMetadata metadata;

    ItemContainerActionClientRequest(
        ItemContainerAction action,
        ClientRequestMetadata metadata
    ){
        this.action=
            java.util.Objects.requireNonNull(
                action,
                "action"
            );
        this.metadata=
            java.util.Objects.requireNonNull(
                metadata,
                "metadata"
            );
    }

    ItemContainerAction action(){
        return action;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "ItemContainerActionClientRequest{action="+
            action+
            ",metadata="+metadata+
            "}";
    }
}
