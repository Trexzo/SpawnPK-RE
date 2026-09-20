package spk.local;

/** Exact-current opcode 208 amount-entry request. */
final class AmountEntryClientRequest
    implements ClientRequest {

    private final int amount;
    private final ClientRequestMetadata metadata;

    AmountEntryClientRequest(
        int amount,
        ClientRequestMetadata metadata
    ){
        this.amount=amount;
        this.metadata=
            java.util.Objects.requireNonNull(
                metadata,
                "metadata"
            );
    }

    int amount(){
        return amount;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "AmountEntryClientRequest{amount="+
            amount+
            ",metadata="+metadata+
            "}";
    }
}
