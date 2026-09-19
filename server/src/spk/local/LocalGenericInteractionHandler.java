package spk.local;

/**
 * Typed fail-closed routing for the exact-current generic R8.5 interaction
 * bridge. Transport/framing remains in R85GenericC2SBridge.
 */
final class LocalGenericInteractionHandler {
    String handle(GenericInteractionEvent event){
        if(event==null)return null;

        switch(event.family){
            case OBJECT_OPTION:
                return "V5185_GENERIC_OBJECT_ACTION "+event+
                    " result=DECODED_FAIL_CLOSED authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN";
            case WIDGET_ITEM_OPTION:
                return "V5185_WIDGET_ITEM_ACTION "+event+
                    " result=DECODED_FAIL_CLOSED authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN";
            case ITEM_ON_PLAYER:
                return "V5185_ITEM_ON_PLAYER "+event+
                    " result=DECODED_FAIL_CLOSED authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN";
            case ITEM_ON_GROUND_ITEM:
                return "V5185_ITEM_ON_GROUND "+event+
                    " result=DECODED_FAIL_CLOSED authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN";
            case ITEM_ON_OBJECT:
                return "V5185_ITEM_ON_OBJECT "+event+
                    " result=DECODED_FAIL_CLOSED authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN";
            default:
                return "V5185_GENERIC_INTERACTION "+event+
                    " result=DECODED_FAIL_CLOSED";
        }
    }
}
