package spk.local;

final class DialogueContinueClientRequest
    implements ClientRequest {
    private final int widgetId;
    private final ClientRequestMetadata metadata;

    DialogueContinueClientRequest(
        int widgetId,
        ClientRequestMetadata metadata
    ){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );
        this.widgetId=widgetId;
        this.metadata=java.util.Objects.requireNonNull(
            metadata,
            "metadata"
        );
    }

    int widgetId(){return widgetId;}

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "DialogueContinueClientRequest{widgetId="+
            widgetId+
            ",metadata="+metadata+
            "}";
    }
}
