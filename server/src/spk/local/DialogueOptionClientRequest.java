package spk.local;

final class DialogueOptionClientRequest
    implements ClientRequest {
    private final int optionIndex;
    private final ClientRequestMetadata metadata;

    DialogueOptionClientRequest(
        int optionIndex,
        ClientRequestMetadata metadata
    ){
        if(optionIndex<1||optionIndex>5)
            throw new IllegalArgumentException(
                "optionIndex="+optionIndex
            );
        this.optionIndex=optionIndex;
        this.metadata=java.util.Objects.requireNonNull(
            metadata,
            "metadata"
        );
    }

    int optionIndex(){return optionIndex;}

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "DialogueOptionClientRequest{optionIndex="+
            optionIndex+
            ",metadata="+metadata+
            "}";
    }
}
