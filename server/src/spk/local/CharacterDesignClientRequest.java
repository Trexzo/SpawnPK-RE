package spk.local;

final class CharacterDesignClientRequest
    implements ClientRequest {
    private final CharacterDesignRequest design;
    private final ClientRequestMetadata metadata;

    CharacterDesignClientRequest(
        CharacterDesignRequest design,
        ClientRequestMetadata metadata
    ){
        this.design=java.util.Objects.requireNonNull(
            design,
            "design"
        );
        this.metadata=java.util.Objects.requireNonNull(
            metadata,
            "metadata"
        );
    }

    CharacterDesignRequest design(){return design;}

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "CharacterDesignClientRequest{design="+
            design+
            ",metadata="+metadata+
            "}";
    }
}
