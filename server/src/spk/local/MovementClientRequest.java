package spk.local;

/** Exact-current decoded movement request carried by the bounded typed FIFO. */
final class MovementClientRequest
    implements ClientRequest {

    private final MovementRequest movement;
    private final ClientRequestMetadata metadata;

    MovementClientRequest(
        MovementRequest movement,
        ClientRequestMetadata metadata
    ){
        this.movement=
            java.util.Objects.requireNonNull(
                movement,
                "movement"
            );
        this.metadata=
            java.util.Objects.requireNonNull(
                metadata,
                "metadata"
            );
    }

    MovementRequest movement(){
        return movement;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "MovementClientRequest{movement="+
            movement+
            ",metadata="+metadata+
            "}";
    }
}
