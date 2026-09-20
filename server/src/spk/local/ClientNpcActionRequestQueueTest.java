package spk.local;

import java.io.*;

public final class ClientNpcActionRequestQueueTest {
    private static final int[] OPCODES={
        155,72,17,21,18
    };

    private static final int[] SCENE_INDEXES={
        0x1234,0x2345,0x3456,0x4567,0x5678
    };

    private static final int[] OPTIONS={
        1,2,3,4,5
    };

    private static final String[] SCHEMAS={
        "FIXED2_NPC_SCENE_INDEX_LE",
        "FIXED2_NPC_SCENE_INDEX_BE_A",
        "FIXED2_NPC_SCENE_INDEX_LE_A",
        "FIXED2_NPC_SCENE_INDEX_BE",
        "FIXED2_NPC_SCENE_INDEX_LE"
    };

    private static final String[] SOURCES={
        "PINNED_CLIENT_NPC_OPTION_1_WRITER",
        "PINNED_CLIENT_NPC_OPTION_2_ATTACK_WRITER",
        "PINNED_CLIENT_NPC_OPTION_3_WRITER",
        "PINNED_CLIENT_NPC_OPTION_4_WRITER",
        "PINNED_CLIENT_NPC_OPTION_5_WRITER"
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
            writeNpcBody(
                wire,
                OPCODES[i],
                SCENE_INDEXES[i]
            );

            if(i==1)
                writeOpcode(wire,encoder,130);
        }

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(seed.clone()),
                "[typed-npc-action] "
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
            assertNpc(
                probe.takeTypedRequest(),
                OPCODES[i],
                SCENE_INDEXES[i],
                OPTIONS[i],
                SCHEMAS[i],
                SOURCES[i]
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
            "CLIENT_NPC_ACTION_REQUEST_QUEUE_PASS "+
            "options=5 transforms=true "+
            "duplicatesRetained=true crossFamilyFifo=true "+
            "metadata=true"
        );
    }

    private static void assertNpc(
        ClientRequest request,
        int opcode,
        int sceneIndex,
        int option,
        String schema,
        String source
    ){
        if(!(request instanceof
                NpcActionClientRequest))
            throw new AssertionError(
                "expected npc action got "+
                request
            );

        NpcActionClientRequest typed=
            (NpcActionClientRequest)request;
        NpcAction action=typed.action();

        if(action.opcode!=opcode||
           action.sceneIndex!=sceneIndex||
           NpcInteractionRouter.optionForOpcode(
               action.opcode
           )!=option)
            throw new AssertionError(
                "npc action="+action
            );

        ClientRequestMetadata metadata=
            typed.metadata();

        if(metadata.opcode!=opcode||
           !schema.equals(metadata.schema)||
           !source.equals(metadata.source)||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "metadata="+metadata
            );
    }

    private static void writeNpcBody(
        OutputStream output,
        int opcode,
        int sceneIndex
    )throws IOException{
        switch(opcode){
            case 155:
            case 18:
                writeLe(output,sceneIndex);
                return;
            case 72:
                writeBeA(output,sceneIndex);
                return;
            case 17:
                writeLeA(output,sceneIndex);
                return;
            case 21:
                writeBe(output,sceneIndex);
                return;
            default:
                throw new AssertionError(
                    "unsupported opcode="+opcode
                );
        }
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

    private static void writeBe(
        OutputStream output,
        int value
    )throws IOException{
        output.write((value>>>8)&255);
        output.write(value&255);
    }

    private static void writeLe(
        OutputStream output,
        int value
    )throws IOException{
        output.write(value&255);
        output.write((value>>>8)&255);
    }

    private static void writeBeA(
        OutputStream output,
        int value
    )throws IOException{
        output.write((value>>>8)&255);
        output.write((value+128)&255);
    }

    private static void writeLeA(
        OutputStream output,
        int value
    )throws IOException{
        output.write((value+128)&255);
        output.write((value>>>8)&255);
    }
}
