package spk.local;

/**
 * Pure exact-current decoder for the eight interaction packets historically
 * promoted through R85GenericC2SBridge.
 *
 * This class owns only byte length/transform authority. It has no probe
 * reflection, no per-session queue and no gameplay state.
 */
final class GenericInteractionPacketDecoder {
    static int length(int opcode){
        switch(opcode){
            case 14:return 8;
            case 25:return 12;
            case 70:return 6;
            case 176:return 6;
            case 192:return 12;
            case 228:return 6;
            case 234:return 6;
            case 252:return 6;
            default:return -1;
        }
    }

    static String schema(int opcode){
        switch(opcode){
            case 14:
                return "FIXED8_SELECTED_WIDGET_BE_A_PLAYER_BE_SELECTED_ITEM_BE_SELECTED_SLOT_LE";
            case 25:
                return "FIXED12_SELECTED_WIDGET_LE_SELECTED_ITEM_BE_A_GROUND_ITEM_BE_WORLD_X_BE_A_SELECTED_SLOT_LE_A_WORLD_Y_BE";
            case 70:
                return "FIXED6_WORLD_Y_LE_WORLD_X_BE_OBJECT_LE_A";
            case 176:
                return "FIXED6_SLOT_LE_WIDGET_BE_A_ITEM_LE";
            case 192:
                return "FIXED12_SELECTED_WIDGET_BE_OBJECT_LE_WORLD_X_LE_A_SELECTED_SLOT_LE_WORLD_Y_LE_A_SELECTED_ITEM_BE";
            case 228:
                return "FIXED6_OBJECT_BE_A_WORLD_X_BE_A_WORLD_Y_BE";
            case 234:
                return "FIXED6_WORLD_Y_LE_A_OBJECT_BE_A_WORLD_X_LE_A";
            case 252:
                return "FIXED6_OBJECT_LE_A_WORLD_X_LE_WORLD_Y_BE_A";
            default:
                throw new IllegalArgumentException(
                    "unsupported opcode "+opcode
                );
        }
    }

    static String source(int opcode){
        switch(opcode){
            case 14:
                return "PINNED_CLIENT_ITEM_ON_PLAYER_WRITER";
            case 25:
                return "PINNED_CLIENT_ITEM_ON_GROUND_WRITER";
            case 70:
                return "PINNED_CLIENT_OBJECT_OPTION_3_WRITER";
            case 176:
                return "PINNED_CLIENT_WIDGET_ITEM_OPTION_6_WRITER";
            case 192:
                return "PINNED_CLIENT_ITEM_ON_OBJECT_WRITER";
            case 228:
                return "PINNED_CLIENT_OBJECT_OPTION_5_WRITER";
            case 234:
                return "PINNED_CLIENT_OBJECT_OPTION_4_WRITER";
            case 252:
                return "PINNED_CLIENT_OBJECT_OPTION_2_WRITER";
            default:
                throw new IllegalArgumentException(
                    "unsupported opcode "+opcode
                );
        }
    }

    static GenericInteractionEvent decode(
        int opcode,
        byte[] payload
    ){
        int expected=length(opcode);
        if(payload==null||payload.length!=expected)
            throw new IllegalArgumentException(
                "opcode="+opcode+
                " len="+
                (payload==null?-1:payload.length)+
                " expected="+expected
            );

        switch(opcode){
            // selectedWidget=BE_A, player=BE, selectedItem=BE, selectedSlot=LE
            case 14:
                return GenericInteractionEvent.itemOnPlayer(
                    opcode,
                    beA(payload,0),
                    le(payload,6),
                    be(payload,4),
                    be(payload,2)
                );

            // selectedWidget=LE, selectedItem=BE_A, groundItem=BE,
            // worldX=BE_A, selectedSlot=LE_A, worldY=BE
            case 25:
                return GenericInteractionEvent.itemOnGround(
                    opcode,
                    le(payload,0),
                    leA(payload,8),
                    beA(payload,2),
                    be(payload,4),
                    beA(payload,6),
                    be(payload,10)
                );

            // worldY=LE, worldX=BE, objectId=LE_A
            case 70:
                return GenericInteractionEvent.objectOption(
                    opcode,
                    3,
                    leA(payload,4),
                    be(payload,2),
                    le(payload,0)
                );

            // slot=LE, widget=BE_A, item=LE
            case 176:
                return GenericInteractionEvent.widgetItemOption(
                    opcode,
                    6,
                    beA(payload,2),
                    le(payload,0),
                    le(payload,4)
                );

            // selectedWidget=BE, objectId=LE, worldX=LE_A,
            // selectedSlot=LE, worldY=LE_A, selectedItem=BE
            case 192:
                return GenericInteractionEvent.itemOnObject(
                    opcode,
                    be(payload,0),
                    le(payload,6),
                    be(payload,10),
                    le(payload,2),
                    leA(payload,4),
                    leA(payload,8)
                );

            // objectId=BE_A, worldX=BE_A, worldY=BE
            case 228:
                return GenericInteractionEvent.objectOption(
                    opcode,
                    5,
                    beA(payload,0),
                    beA(payload,2),
                    be(payload,4)
                );

            // worldY=LE_A, objectId=BE_A, worldX=LE_A
            case 234:
                return GenericInteractionEvent.objectOption(
                    opcode,
                    4,
                    beA(payload,2),
                    leA(payload,4),
                    leA(payload,0)
                );

            // objectId=LE_A, worldX=LE, worldY=BE_A
            case 252:
                return GenericInteractionEvent.objectOption(
                    opcode,
                    2,
                    leA(payload,0),
                    le(payload,2),
                    beA(payload,4)
                );

            default:
                throw new IllegalArgumentException(
                    "unsupported opcode "+opcode
                );
        }
    }

    private static int be(byte[] b,int o){
        return ((b[o]&255)<<8)|(b[o+1]&255);
    }

    private static int le(byte[] b,int o){
        return (b[o]&255)|((b[o+1]&255)<<8);
    }

    private static int beA(byte[] b,int o){
        return ((b[o]&255)<<8)|((b[o+1]-128)&255);
    }

    private static int leA(byte[] b,int o){
        return ((b[o]-128)&255)|((b[o+1]&255)<<8);
    }

    private GenericInteractionPacketDecoder(){}
}
