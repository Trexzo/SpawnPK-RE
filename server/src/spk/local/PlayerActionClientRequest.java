package spk.local;

/** Exact-current player-menu option request. */
final class PlayerActionClientRequest
    implements ClientRequest {

    private final PlayerAction action;
    private final ClientRequestMetadata metadata;

    PlayerActionClientRequest(
        PlayerAction action,
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

    PlayerAction action(){
        return action;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "PlayerActionClientRequest{action="+
            action+
            ",metadata="+metadata+
            "}";
    }
}
