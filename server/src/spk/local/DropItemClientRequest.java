package spk.local;

/** Exact-current opcode 87 inventory-drop request. */
final class DropItemClientRequest
    implements ClientRequest {

    private final DropItemAction action;
    private final ClientRequestMetadata metadata;

    DropItemClientRequest(
        DropItemAction action,
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

    DropItemAction action(){
        return action;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "DropItemClientRequest{action="+
            action+
            ",metadata="+metadata+
            "}";
    }
}
