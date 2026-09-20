package spk.local;

/** Exact-current opcode 214 container-drag request. */
final class ContainerDragClientRequest
    implements ClientRequest {

    private final ContainerDrag drag;
    private final ClientRequestMetadata metadata;

    ContainerDragClientRequest(
        ContainerDrag drag,
        ClientRequestMetadata metadata
    ){
        this.drag=
            java.util.Objects.requireNonNull(
                drag,
                "drag"
            );
        this.metadata=
            java.util.Objects.requireNonNull(
                metadata,
                "metadata"
            );
    }

    ContainerDrag drag(){
        return drag;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "ContainerDragClientRequest{drag="+
            drag+
            ",metadata="+metadata+
            "}";
    }
}
