package spk.local;

/** Exact-current NPC menu/attack request. */
final class NpcActionClientRequest
    implements ClientRequest {

    private final NpcAction action;
    private final ClientRequestMetadata metadata;

    NpcActionClientRequest(
        NpcAction action,
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

    NpcAction action(){
        return action;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "NpcActionClientRequest{action="+
            action+
            ",metadata="+metadata+
            "}";
    }
}
