package spk.local;

import java.io.*;

public final class ClientGroundItemRequestQueueTest {
    private static final int[] OPCODES={
        156,23,236,253,79
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
            int option=i+1;
            writeGround(
                wire,
                encoder,
                OPCODES[i],
                option,
                4151+i,
                3095+i,
                3493+i
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
                "[typed-ground-item] "
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
            assertGround(
                probe.takeTypedRequest(),
                OPCODES[i],
                i+1,
                4151+i,
                3095+i,
                3493+i
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
            "CLIENT_GROUND_ITEM_REQUEST_QUEUE_PASS "+
            "options=5 transforms=true "+
            "duplicatesRetained=true crossFamilyFifo=true "+
            "metadata=true"
        );
    }

    private static void assertGround(
        ClientRequest request,
        int opcode,
        int option,
        int item,
        int worldX,
        int worldY
    ){
        if(!(request instanceof
                GroundItemClientRequest))
            throw new AssertionError(
                "expected ground item got "+
                request
            );

        GroundItemClientRequest typed=
            (GroundItemClientRequest)request;
        GroundItemInteraction action=
            typed.interaction();

        if(action.opcode!=opcode||
           action.option!=option||
           action.itemId!=item||
           action.worldX!=worldX||
           action.worldY!=worldY)
            throw new AssertionError(
                "ground="+action
            );

        ClientRequestMetadata metadata=
            typed.metadata();

        String expectedSchema=schema(opcode);
        String expectedSource=
            "PINNED_CLIENT_GROUND_ITEM_OPTION_"+
            option+
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

    private static String schema(int opcode){
        switch(opcode){
            case 156:
                return "FIXED6_WORLD_X_BE_A_WORLD_Y_LE_ITEM_LE_A";
            case 23:
                return "FIXED6_WORLD_Y_LE_ITEM_LE_WORLD_X_LE";
            case 236:
                return "FIXED6_WORLD_Y_LE_ITEM_BE_WORLD_X_LE";
            case 253:
                return "FIXED6_WORLD_X_LE_WORLD_Y_LE_A_ITEM_BE_A";
            case 79:
                return "FIXED6_WORLD_Y_LE_ITEM_BE_WORLD_X_BE_A";
            default:
                throw new AssertionError(
                    "opcode="+opcode
                );
        }
    }

    private static void writeGround(
        OutputStream output,
        IsaacCipher cipher,
        int opcode,
        int option,
        int item,
        int worldX,
        int worldY
    )throws IOException{
        writeOpcode(output,cipher,opcode);

        switch(option){
            case 1:
                writeBeA(output,worldX);
                writeLe(output,worldY);
                writeLeA(output,item);
                break;
            case 2:
                writeLe(output,worldY);
                writeLe(output,item);
                writeLe(output,worldX);
                break;
            case 3:
                writeLe(output,worldY);
                writeBe(output,item);
                writeLe(output,worldX);
                break;
            case 4:
                writeLe(output,worldX);
                writeLeA(output,worldY);
                writeBeA(output,item);
                break;
            case 5:
                writeLe(output,worldY);
                writeBe(output,item);
                writeBeA(output,worldX);
                break;
            default:
                throw new AssertionError(
                    "option="+option
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
