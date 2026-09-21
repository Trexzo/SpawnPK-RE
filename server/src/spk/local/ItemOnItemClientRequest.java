package spk.local;

/** Exact-current opcode 53 item-on-item request carried by the bounded typed FIFO. */
final class ItemOnItemClientRequest
    implements ClientRequest {

    private final ItemOnItemAction action;
    private final ClientRequestMetadata metadata;

    ItemOnItemClientRequest(
        ItemOnItemAction action,
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

    ItemOnItemAction action(){
        return action;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "ItemOnItemClientRequest{action="+
            action+
            ",metadata="+metadata+
            "}";
    }
}
