package spk.local;

import spk.content.api.ContentInteractionResult;

/**
 * Typed fail-closed routing for exact-current promoted generic interactions.
 * Transport/framing authority lives in GenericInteractionPacketDecoder and
 * ClientPacketProbe; this handler owns only semantic/domain routing.
 */
final class LocalGenericInteractionHandler {
    private final ContentRegistry contentRegistry;

    LocalGenericInteractionHandler(){
        this(null);
    }

    LocalGenericInteractionHandler(
        ContentRegistry contentRegistry
    ){
        this.contentRegistry=contentRegistry;
    }

    String handle(GenericInteractionEvent event){
        if(event==null)return null;

        switch(event.family){
            case OBJECT_OPTION:
                if(contentRegistry!=null){
                    ContentInteractionResult content=
                        contentRegistry.dispatchObjectOption(
                            event.targetId,
                            event.option,
                            event.worldX,
                            event.worldY
                        );

                    if(content!=null)
                        return "V5185_GENERIC_OBJECT_ACTION "+event+
                            " result="+content.outcome()+
                            " authority=EXACT_CLIENT_WIRE_AND_TRIGGER_SERVER_BEHAVIOR_UNKNOWN";
                }

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
