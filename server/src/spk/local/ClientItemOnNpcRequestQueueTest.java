package spk.local;

import java.io.*;

public final class ClientItemOnNpcRequestQueueTest {
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

        writeItemOnNpc(
            wire,
            encoder,
            20542,
            4,
            7,
            3214
        );
        writeOpcode(wire,encoder,130);
        writeItemOnNpc(
            wire,
            encoder,
            20699,
            5,
            9,
            3214
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(seed.clone()),
                "[typed-item-on-npc] "
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

        assertItemOnNpc(
            probe.takeTypedRequest(),
            20542,
            4,
            7,
            3214
        );

        ClientRequest close=
            probe.takeTypedRequest();
        if(!(close instanceof
                InterfaceCloseClientRequest))
            throw new AssertionError(
                "cross-family FIFO close="+
                close
            );

        assertItemOnNpc(
            probe.takeTypedRequest(),
            20699,
            5,
            9,
            3214
        );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "queue not empty"
            );

        System.out.println(
            "CLIENT_ITEM_ON_NPC_REQUEST_QUEUE_PASS "+
            "transforms=true "+
            "duplicatesRetained=true "+
            "crossFamilyFifo=true "+
            "metadata=true"
        );
    }

    private static void assertItemOnNpc(
        ClientRequest request,
        int item,
        int targetNpc,
        int slot,
        int widget
    ){
        if(!(request instanceof
                ItemOnNpcClientRequest))
            throw new AssertionError(
                "expected item-on-npc got "+
                request
            );

        ItemOnNpcClientRequest typed=
            (ItemOnNpcClientRequest)request;
        ItemOnNpcAction action=typed.action();

        if(action.itemId!=item||
           action.targetNpcIndex!=targetNpc||
           action.slot!=slot||
           action.widgetId!=widget)
            throw new AssertionError(
                "item-on-npc="+action
            );

        ClientRequestMetadata metadata=
            typed.metadata();
        if(metadata.opcode!=57||
           !"FIXED8_ITEM_BE_A_NPC_BE_A_SLOT_LE_WIDGET_BE_A"
                .equals(metadata.schema)||
           !"PINNED_CLIENT_ITEM_ON_NPC_WRITER"
                .equals(metadata.source)||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "metadata="+metadata
            );
    }

    private static void writeItemOnNpc(
        OutputStream output,
        IsaacCipher cipher,
        int item,
        int targetNpc,
        int slot,
        int widget
    )throws IOException{
        writeOpcode(output,cipher,57);
        writeBeA(output,item);
        writeBeA(output,targetNpc);
        writeLe(output,slot);
        writeBeA(output,widget);
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

    private static void writeBeA(
        OutputStream output,
        int value
    )throws IOException{
        output.write((value>>>8)&255);
        output.write((value+128)&255);
    }

    private static void writeLe(
        OutputStream output,
        int value
    )throws IOException{
        output.write(value&255);
        output.write((value>>>8)&255);
    }
}
