package spk.local;

import java.io.*;

public final class ClientPlayerActionRequestQueueTest {
    private static final int[] OPCODES={
        128,153,73,139,39
    };

    private static final int[] SLOTS={
        1,2,3,4,5
    };

    private static final String[] SEMANTICS={
        "Attack",
        "Follow",
        "Trade with",
        "Option 4",
        "Option 5"
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
            int playerIndex=0x0123+i;
            if(OPCODES[i]==128)
                writeBe(wire,playerIndex);
            else
                writeLe(wire,playerIndex);

            if(i==1)
                writeOpcode(wire,encoder,130);
        }

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(seed.clone()),
                "[typed-player-action] "
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
            assertPlayer(
                probe.takeTypedRequest(),
                OPCODES[i],
                SLOTS[i],
                0x0123+i,
                SEMANTICS[i]
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
            "CLIENT_PLAYER_ACTION_REQUEST_QUEUE_PASS "+
            "options=5 transforms=true "+
            "duplicatesRetained=true crossFamilyFifo=true "+
            "metadata=true"
        );
    }

    private static void assertPlayer(
        ClientRequest request,
        int opcode,
        int slot,
        int playerIndex,
        String semantic
    ){
        if(!(request instanceof
                PlayerActionClientRequest))
            throw new AssertionError(
                "expected player action got "+
                request
            );

        PlayerActionClientRequest typed=
            (PlayerActionClientRequest)request;
        PlayerAction action=typed.action();

        if(action.opcode!=opcode||
           action.optionSlot!=slot||
           action.playerIndex!=playerIndex||
           !semantic.equals(action.semantic))
            throw new AssertionError(
                "player action="+action
            );

        ClientRequestMetadata metadata=
            typed.metadata();

        String expectedSchema=
            opcode==128
                ?"FIXED2_PLAYER_INDEX_BE"
                :"FIXED2_PLAYER_INDEX_LE";
        String expectedSource=
            "PINNED_CLIENT_PLAYER_OPTION_"+
            slot+
            "_WRITER";

        if(metadata.opcode!=opcode||
           !expectedSchema.equals(metadata.schema)||
           !expectedSource.equals(metadata.source)||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "metadata="+metadata
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
}
