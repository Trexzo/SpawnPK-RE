package spk.local;

import java.io.*;

public final class ClientObjectInteractionRequestQueueTest {
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

        // Exact live v2.2 regression vector from
        // protocol/OUTBOUND_OPCODE132_OBJECT_SCHEMA_V23.md:
        // 97 0C 69 5C 0D 25 -> worldX=3095,
        // objectId=26972, worldY=3493.
        writeOpcode(wire,encoder,132);
        wire.write(new byte[]{
            (byte)0x97,0x0C,
            0x69,0x5C,
            0x0D,0x25
        });

        writeOpcode(wire,encoder,130);

        // Second exact-format vector proves repeated requests
        // survive rather than overwriting one mutable slot.
        writeOpcode(wire,encoder,132);
        wire.write(new byte[]{
            (byte)0x90,0x0C,
            0x69,0x5C,
            0x0D,0x27
        });

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(seed.clone()),
                "[typed-object-interaction] "
            );

        for(int i=0;i<3;i++)
            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "decode stopped index="+i
                );

        if(!probe.isAligned())
            throw new AssertionError(
                "decoder lost alignment"
            );

        if(probe.typedRequestCount()!=3)
            throw new AssertionError(
                "typed request count="+
                probe.typedRequestCount()
            );

        assertObject(
            probe.takeTypedRequest(),
            26972,
            3095,
            3493
        );

        ClientRequest close=
            probe.takeTypedRequest();
        if(!(close instanceof
                InterfaceCloseClientRequest))
            throw new AssertionError(
                "cross-family FIFO close="+
                close
            );

        assertObject(
            probe.takeTypedRequest(),
            26972,
            3088,
            3495
        );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "queue not empty"
            );

        System.out.println(
            "CLIENT_OBJECT_INTERACTION_REQUEST_QUEUE_PASS "+
            "liveVector=true transforms=true "+
            "duplicatesRetained=true crossFamilyFifo=true "+
            "metadata=true"
        );
    }

    private static void assertObject(
        ClientRequest request,
        int objectId,
        int worldX,
        int worldY
    ){
        if(!(request instanceof
                ObjectInteractionClientRequest))
            throw new AssertionError(
                "expected object interaction got "+
                request
            );

        ObjectInteractionClientRequest typed=
            (ObjectInteractionClientRequest)request;
        ObjectInteraction interaction=
            typed.interaction();

        if(interaction.opcode!=132||
           interaction.objectId!=objectId||
           interaction.worldX!=worldX||
           interaction.worldY!=worldY)
            throw new AssertionError(
                "interaction="+interaction
            );

        ClientRequestMetadata metadata=
            typed.metadata();
        if(metadata.opcode!=132||
           !"FIXED6_WORLD_X_LE_A_OBJECT_BE_WORLD_Y_BE_A"
                .equals(metadata.schema)||
           !"PINNED_CLIENT_MENU_ACTION_502_AND_N_SERIALIZER"
                .equals(metadata.source)||
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
}
