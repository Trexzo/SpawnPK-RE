package spk.local;

import java.io.*;

public final class ClientSpellTargetRequestQueueTest {
    private static final int[] OPCODES={
        249,131,35,181,237
    };

    private static final byte[][] BODIES={
        bytes(0x12,0xB4,0x45,0x23),
        bytes(0xD6,0x34,0x45,0xE7),
        bytes(0x78,0x56,0x67,0x09,0x78,0x1A,0xAB,0x89),
        bytes(0x34,0x12,0x23,0x45,0x56,0x34,0x45,0xE7),
        bytes(0x01,0x23,0x23,0xC5,0x34,0x56,0x45,0xE7)
    };

    private static final String[] SCHEMAS={
        "FIXED4_PLAYER_BE_A_SPELL_LE",
        "FIXED4_NPC_LE_A_SPELL_BE_A",
        "FIXED8_WORLD_X_LE_SPELL_BE_A_WORLD_Y_BE_A_OBJECT_LE",
        "FIXED8_WORLD_Y_LE_ITEM_BE_WORLD_X_LE_SPELL_BE_A",
        "FIXED8_SLOT_BE_ITEM_BE_A_WIDGET_BE_SPELL_BE_A"
    };

    private static final String[] SOURCES={
        "PINNED_CLIENT_SPELL_ON_PLAYER_WRITER",
        "PINNED_CLIENT_SPELL_ON_NPC_WRITER",
        "PINNED_CLIENT_SPELL_ON_OBJECT_WRITER",
        "PINNED_CLIENT_SPELL_ON_GROUND_ITEM_WRITER",
        "PINNED_CLIENT_SPELL_ON_INVENTORY_ITEM_WRITER"
    };

    public static void main(String[] args)throws Exception{
        int[] seed={
            0x01020304,
            0x11223344,
            0x55667788,
            0x13572468
        };

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        IsaacCipher encoder=
            new IsaacCipher(seed.clone());

        for(int i=0;i<OPCODES.length;i++){
            writeOpcode(wire,encoder,OPCODES[i]);
            wire.write(BODIES[i]);

            if(i==1)
                writeOpcode(wire,encoder,130);
        }

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(seed.clone()),
                "[typed-spell-target] "
            );

        int packetCount=OPCODES.length+1;
        for(int i=0;i<packetCount;i++)
            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "decode stopped index="+i
                );

        if(!probe.isAligned())
            throw new AssertionError(
                "decoder lost alignment"
            );

        if(probe.typedRequestCount()!=packetCount)
            throw new AssertionError(
                "typed request count="+
                probe.typedRequestCount()
            );

        for(int i=0;i<OPCODES.length;i++){
            assertSpell(
                probe.takeTypedRequest(),
                i
            );

            if(i==1){
                ClientRequest close=
                    probe.takeTypedRequest();
                if(!(close instanceof
                        InterfaceCloseClientRequest))
                    throw new AssertionError(
                        "cross-family FIFO close="+
                        close
                    );
            }
        }

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "queue not empty"
            );

        System.out.println(
            "CLIENT_SPELL_TARGET_REQUEST_QUEUE_PASS "+
            "targets=5 transforms=true "+
            "duplicatesRetained=true crossFamilyFifo=true "+
            "metadata=true"
        );
    }

    private static void assertSpell(
        ClientRequest request,
        int index
    ){
        if(!(request instanceof
                SpellTargetClientRequest))
            throw new AssertionError(
                "expected spell target got "+
                request
            );

        SpellTargetClientRequest typed=
            (SpellTargetClientRequest)request;
        SpellTargetRequest spell=typed.request();

        if(spell.opcode!=OPCODES[index])
            throw new AssertionError(
                "opcode request="+spell
            );

        switch(spell.opcode){
            case 249:
                assertKind(spell,SpellTargetRequest.Kind.PLAYER);
                assertEq("spell",0x2345,spell.spellWidget);
                assertEq("player",0x1234,spell.targetIndex);
                break;

            case 131:
                assertKind(spell,SpellTargetRequest.Kind.NPC);
                assertEq("spell",0x4567,spell.spellWidget);
                assertEq("npc",0x3456,spell.targetIndex);
                break;

            case 35:
                assertKind(spell,SpellTargetRequest.Kind.OBJECT);
                assertEq("spell",0x6789,spell.spellWidget);
                assertEq("object",0x89AB,spell.targetId);
                assertEq("worldX",0x5678,spell.worldX);
                assertEq("worldY",0x789A,spell.worldY);
                break;

            case 181:
                assertKind(spell,SpellTargetRequest.Kind.GROUND_ITEM);
                assertEq("spell",0x4567,spell.spellWidget);
                assertEq("groundItem",0x2345,spell.targetId);
                assertEq("worldX",0x3456,spell.worldX);
                assertEq("worldY",0x1234,spell.worldY);
                break;

            case 237:
                assertKind(spell,SpellTargetRequest.Kind.INVENTORY_ITEM);
                assertEq("spell",0x4567,spell.spellWidget);
                assertEq("item",0x2345,spell.targetId);
                assertEq("widget",0x3456,spell.targetWidget);
                assertEq("slot",0x0123,spell.targetSlot);
                break;

            default:
                throw new AssertionError(
                    "missing assertion opcode="+
                    spell.opcode
                );
        }

        ClientRequestMetadata metadata=
            typed.metadata();

        if(metadata.opcode!=OPCODES[index]||
           !SCHEMAS[index].equals(metadata.schema)||
           !SOURCES[index].equals(metadata.source)||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "metadata="+metadata
            );
    }

    private static void assertKind(
        SpellTargetRequest request,
        SpellTargetRequest.Kind expected
    ){
        if(request.kind!=expected)
            throw new AssertionError(
                "kind expected="+expected+
                " actual="+request.kind+
                " request="+request
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

    private static void writeOpcode(
        OutputStream output,
        IsaacCipher cipher,
        int opcode
    )throws IOException{
        output.write(
            (opcode+cipher.nextInt())&255
        );
    }

    private static byte[] bytes(int... values){
        byte[] out=new byte[values.length];
        for(int i=0;i<values.length;i++)
            out[i]=(byte)values[i];
        return out;
    }
}
