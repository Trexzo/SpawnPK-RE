package spk.local;

/** Exact-current opcode 57 item-on-NPC request. */
final class ItemOnNpcClientRequest
    implements ClientRequest {

    private final ItemOnNpcAction action;
    private final ClientRequestMetadata metadata;

    ItemOnNpcClientRequest(
        ItemOnNpcAction action,
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

    ItemOnNpcAction action(){
        return action;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "ItemOnNpcClientRequest{action="+
            action+
            ",metadata="+metadata+
            "}";
    }
}
