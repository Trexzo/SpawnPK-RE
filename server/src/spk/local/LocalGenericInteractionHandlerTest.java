package spk.local;

public final class LocalGenericInteractionHandlerTest {
    public static void main(String[] args){
        LocalGenericInteractionHandler h=new LocalGenericInteractionHandler();

        assertContains(
            h.handle(GenericInteractionEvent.objectOption(70,3,26972,3087,3495)),
            "V5185_GENERIC_OBJECT_ACTION",
            "authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN"
        );

        assertContains(
            h.handle(GenericInteractionEvent.widgetItemOption(176,6,3214,2,4151)),
            "V5185_WIDGET_ITEM_ACTION",
            "result=DECODED_FAIL_CLOSED"
        );

        assertContains(
            h.handle(GenericInteractionEvent.itemOnPlayer(14,3214,1,4151,2)),
            "V5185_ITEM_ON_PLAYER",
            "playerIndex=2"
        );

        assertContains(
            h.handle(GenericInteractionEvent.itemOnGround(25,3214,1,4151,995,3088,3495)),
            "V5185_ITEM_ON_GROUND",
            "targetItem=995"
        );

        assertContains(
            h.handle(GenericInteractionEvent.itemOnObject(192,3214,1,4151,26972,3088,3495)),
            "V5185_ITEM_ON_OBJECT",
            "objectId=26972"
        );

        if(h.handle(null)!=null)
            throw new AssertionError("null event should remain unhandled");

        System.out.println(
            "LOCAL_GENERIC_INTERACTION_HANDLER_PASS typedEvents=true failClosed=true authorityPreserved=true");
    }

    private static void assertContains(String value,String a,String b){
        if(value==null||!value.contains(a)||!value.contains(b))
            throw new AssertionError("value="+value+" missing="+a+" / "+b);
    }
}
