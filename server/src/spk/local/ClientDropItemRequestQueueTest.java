package spk.local;

import java.io.*;

public final class ClientDropItemRequestQueueTest {
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

        writeDrop(wire,encoder,20776,3214,4);
        writeOpcode(wire,encoder,130);
        writeDrop(wire,encoder,28131,3214,27);

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(seed.clone()),
                "[typed-drop-item] "
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

        assertDrop(
            probe.takeTypedRequest(),
            20776,
            3214,
            4
        );

        ClientRequest close=
            probe.takeTypedRequest();
        if(!(close instanceof
                InterfaceCloseClientRequest))
            throw new AssertionError(
                "cross-family FIFO close="+
                close
            );

        assertDrop(
            probe.takeTypedRequest(),
            28131,
            3214,
            27
        );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "queue not empty"
            );

        System.out.println(
            "CLIENT_DROP_ITEM_REQUEST_QUEUE_PASS "+
            "transforms=true "+
            "duplicatesRetained=true "+
            "crossFamilyFifo=true "+
            "metadata=true"
        );
    }

    private static void assertDrop(
        ClientRequest request,
        int item,
        int widget,
        int slot
    ){
        if(!(request instanceof
                DropItemClientRequest))
            throw new AssertionError(
                "expected drop got "+
                request
            );

        DropItemClientRequest typed=
            (DropItemClientRequest)request;
        DropItemAction action=typed.action();

        if(action.itemId!=item||
           action.widgetId!=widget||
           action.slot!=slot)
            throw new AssertionError(
                "drop="+action
            );

        ClientRequestMetadata metadata=
            typed.metadata();
        if(metadata.opcode!=87||
           !"FIXED6_ITEM_BE_A_WIDGET_BE_SLOT_BE_A"
                .equals(metadata.schema)||
           !"PINNED_CLIENT_INVENTORY_DROP_WRITER"
                .equals(metadata.source)||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "metadata="+metadata
            );
    }

    private static void writeDrop(
        OutputStream output,
        IsaacCipher cipher,
        int item,
        int widget,
        int slot
    )throws IOException{
        writeOpcode(output,cipher,87);
        writeBeA(output,item);
        writeBe(output,widget);
        writeBeA(output,slot);
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

    private static void writeBeA(
        OutputStream output,
        int value
    )throws IOException{
        output.write((value>>>8)&255);
        output.write((value+128)&255);
    }
}
