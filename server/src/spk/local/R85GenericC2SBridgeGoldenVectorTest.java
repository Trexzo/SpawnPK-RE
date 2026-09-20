package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class R85GenericC2SBridgeGoldenVectorTest {
    private static final int[] SEED={
        0x01020304,
        0x11223344,
        0x55667788,
        0x13572468
    };

    private static final int[] OPCODES={
        14,25,70,176,192,228,234,252
    };

    private static final byte[][] PAYLOADS={
        bytes(0x12,0xB4,0x45,0x67,0x23,0x45,0x56,0x34),
        bytes(0x34,0x12,0x23,0xC5,0x34,0x56,0x45,0xE7,0xF8,0x56,0x67,0x89),
        bytes(0x56,0x34,0x23,0x45,0xB4,0x12),
        bytes(0x34,0x12,0x23,0xC5,0x56,0x34),
        bytes(0x12,0x34,0x45,0x23,0xD6,0x34,0x67,0x45,0xF8,0x56,0x67,0x89),
        bytes(0x12,0xB4,0x23,0xC5,0x34,0x56),
        bytes(0xB4,0x12,0x23,0xC5,0xD6,0x34),
        bytes(0xB4,0x12,0x45,0x23,0x34,0xD6)
    };

    public static void main(String[] args)throws Exception{
        if(OPCODES.length!=8||PAYLOADS.length!=8)
            throw new AssertionError("fixture count");

        for(int i=0;i<OPCODES.length;i++)
            runVector(OPCODES[i],PAYLOADS[i]);

        if(R85GenericC2SBridge.length(5)!=-1)
            throw new AssertionError("unsupported opcode length");

        System.out.println(
            "R85_GENERIC_C2S_BRIDGE_GOLDEN_VECTOR_PASS "+
            "opcodes=8 transforms=true alignmentFence=true "+
            "isaacStream=true exactLengths=true"
        );
    }

    private static void runVector(
        int opcode,
        byte[] payload
    )throws Exception{
        int bridgeLength=R85GenericC2SBridge.length(opcode);
        if(bridgeLength!=payload.length)
            throw new AssertionError(
                "bridge length opcode="+opcode+
                " expected="+payload.length+
                " actual="+bridgeLength
            );

        int registryLength=
            ClientPacketProbe.framingOnlyFixedLength(opcode);
        if(registryLength!=payload.length)
            throw new AssertionError(
                "framing registry opcode="+opcode+
                " expected="+payload.length+
                " actual="+registryLength
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        IsaacCipher encoder=
            new IsaacCipher(SEED.clone());

        wire.write((opcode+encoder.nextInt())&255);
        wire.write(payload);

        // Alignment fence: a correctly consumed promoted body must leave
        // the next exact-current packet opcode aligned under the same ISAAC stream.
        wire.write((130+encoder.nextInt())&255);

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(SEED.clone()),
                "[generic-golden-"+opcode+"] "
            );

        if(!probe.readNextKnownPacket())
            throw new AssertionError(
                "promoted decode stopped opcode="+opcode
            );

        GenericInteractionEvent event=
            R85GenericC2SBridge.take(probe);
        if(event==null)
            throw new AssertionError(
                "missing bridge event opcode="+opcode
            );

        assertEvent(opcode,event);

        if(R85GenericC2SBridge.take(probe)!=null)
            throw new AssertionError(
                "unexpected duplicate event opcode="+opcode
            );

        if(!probe.readNextKnownPacket())
            throw new AssertionError(
                "alignment fence decode stopped opcode="+opcode
            );

        ClientRequest close=
            probe.takeTypedRequest();
        if(!(close instanceof InterfaceCloseClientRequest))
            throw new AssertionError(
                "alignment fence request opcode="+opcode+
                " request="+close
            );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "extra typed request opcode="+opcode
            );

        if(!probe.isAligned())
            throw new AssertionError(
                "unaligned opcode="+opcode
            );

        if(probe.decodedCount()!=2)
            throw new AssertionError(
                "decoded count opcode="+opcode+
                " count="+probe.decodedCount()
            );
    }

    private static void assertEvent(
        int opcode,
        GenericInteractionEvent e
    ){
        assertEq("opcode",opcode,e.opcode);

        switch(opcode){
            case 14:
                assertFamily(GenericInteractionEvent.Family.ITEM_ON_PLAYER,e);
                assertEq("selectedWidget",0x1234,e.selectedWidget);
                assertEq("selectedSlot",0x3456,e.selectedSlot);
                assertEq("selectedItemId",0x2345,e.selectedItemId);
                assertEq("playerIndex",0x4567,e.targetId);
                break;

            case 25:
                assertFamily(GenericInteractionEvent.Family.ITEM_ON_GROUND_ITEM,e);
                assertEq("selectedWidget",0x1234,e.selectedWidget);
                assertEq("selectedSlot",0x5678,e.selectedSlot);
                assertEq("selectedItemId",0x2345,e.selectedItemId);
                assertEq("targetItem",0x3456,e.targetId);
                assertEq("worldX",0x4567,e.worldX);
                assertEq("worldY",0x6789,e.worldY);
                break;

            case 70:
                assertFamily(GenericInteractionEvent.Family.OBJECT_OPTION,e);
                assertEq("option",3,e.option);
                assertEq("objectId",0x1234,e.targetId);
                assertEq("worldX",0x2345,e.worldX);
                assertEq("worldY",0x3456,e.worldY);
                break;

            case 176:
                assertFamily(GenericInteractionEvent.Family.WIDGET_ITEM_OPTION,e);
                assertEq("option",6,e.option);
                assertEq("widgetId",0x2345,e.widgetId);
                assertEq("slot",0x1234,e.slot);
                assertEq("itemId",0x3456,e.itemId);
                break;

            case 192:
                assertFamily(GenericInteractionEvent.Family.ITEM_ON_OBJECT,e);
                assertEq("selectedWidget",0x1234,e.selectedWidget);
                assertEq("selectedSlot",0x4567,e.selectedSlot);
                assertEq("selectedItemId",0x6789,e.selectedItemId);
                assertEq("objectId",0x2345,e.targetId);
                assertEq("worldX",0x3456,e.worldX);
                assertEq("worldY",0x5678,e.worldY);
                break;

            case 228:
                assertFamily(GenericInteractionEvent.Family.OBJECT_OPTION,e);
                assertEq("option",5,e.option);
                assertEq("objectId",0x1234,e.targetId);
                assertEq("worldX",0x2345,e.worldX);
                assertEq("worldY",0x3456,e.worldY);
                break;

            case 234:
                assertFamily(GenericInteractionEvent.Family.OBJECT_OPTION,e);
                assertEq("option",4,e.option);
                assertEq("objectId",0x2345,e.targetId);
                assertEq("worldX",0x3456,e.worldX);
                assertEq("worldY",0x1234,e.worldY);
                break;

            case 252:
                assertFamily(GenericInteractionEvent.Family.OBJECT_OPTION,e);
                assertEq("option",2,e.option);
                assertEq("objectId",0x1234,e.targetId);
                assertEq("worldX",0x2345,e.worldX);
                assertEq("worldY",0x3456,e.worldY);
                break;

            default:
                throw new AssertionError(
                    "missing expected event opcode="+opcode
                );
        }
    }

    private static void assertFamily(
        GenericInteractionEvent.Family expected,
        GenericInteractionEvent e
    ){
        if(e.family!=expected)
            throw new AssertionError(
                "family expected="+expected+
                " actual="+e.family+
                " event="+e
            );
    }

    private static void assertEq(
        String field,
        int expected,
        int actual
    ){
        if(actual!=expected)
            throw new AssertionError(
                field+
                " expected="+expected+
                " actual="+actual
            );
    }

    private static byte[] bytes(int... values){
        byte[] out=new byte[values.length];
        for(int i=0;i<values.length;i++)
            out[i]=(byte)values[i];
        return out;
    }
}
