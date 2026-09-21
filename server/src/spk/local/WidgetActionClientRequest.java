package spk.local;

/** Domain-facing widget action decoded from exact-current opcode 185. */
final class WidgetActionClientRequest
    implements ClientRequest {

    private final int widgetId;
    private final ClientRequestMetadata metadata;

    WidgetActionClientRequest(
        int widgetId,
        ClientRequestMetadata metadata
    ){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        this.widgetId=widgetId;
        this.metadata=
            java.util.Objects.requireNonNull(
                metadata,
                "metadata"
            );
    }

    int widgetId(){
        return widgetId;
    }

    @Override public ClientRequestMetadata metadata(){
        return metadata;
    }

    @Override public String toString(){
        return "WidgetActionClientRequest{widgetId="+
            widgetId+
            ",metadata="+metadata+
            "}";
    }
}
