package spk.local;

import java.io.*;

public final class ClientItemOnItemRequestQueueTest {
    private static final int[] SEED={
        0x01020304,
        0x11223344,
        0x55667788,
        0x13572468
    };

    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        IsaacCipher encoder=
            new IsaacCipher(SEED.clone());

        writeItemOnItem(
            wire,
            encoder,
            6,
            2,
            3241,
            3214,
            28824,
            3214
        );
        writeOpcode(wire,encoder,130);
        writeItemOnItem(
            wire,
            encoder,
            6,
            2,
            3241,
            3214,
            28824,
            3214
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(SEED.clone()),
                "[typed-item-on-item] "
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

        assertItemOnItem(
            probe.takeTypedRequest(),
            6,
            2,
            3241,
            3214,
            28824,
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

        assertItemOnItem(
            probe.takeTypedRequest(),
            6,
            2,
            3241,
            3214,
            28824,
            3214
        );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "queue not empty"
            );

        System.out.println(
            "CLIENT_ITEM_ON_ITEM_REQUEST_QUEUE_PASS "+
            "transforms=true "+
            "duplicatesRetained=true "+
            "crossFamilyFifo=true "+
            "metadata=true"
        );
    }

    private static void assertItemOnItem(
        ClientRequest request,
        int targetSlot,
        int selectedSlot,
        int targetItem,
        int selectedWidget,
        int selectedItem,
        int targetWidget
    ){
        if(!(request instanceof
                ItemOnItemClientRequest))
            throw new AssertionError(
                "expected item-on-item got "+
                request
            );

        ItemOnItemClientRequest typed=
            (ItemOnItemClientRequest)request;
        ItemOnItemAction action=typed.action();

        if(action.targetSlot!=targetSlot||
           action.selectedSlot!=selectedSlot||
           action.targetItemId!=targetItem||
           action.selectedWidget!=selectedWidget||
           action.selectedItemId!=selectedItem||
           action.targetWidget!=targetWidget)
            throw new AssertionError(
                "item-on-item="+action
            );

        ClientRequestMetadata metadata=
            typed.metadata();

        if(metadata.opcode!=53||
           !"FIXED12_TARGET_SLOT_BE_SELECTED_SLOT_BE_A_TARGET_ITEM_LE_A_SELECTED_WIDGET_BE_SELECTED_ITEM_LE_TARGET_WIDGET_BE"
                .equals(metadata.schema)||
           !"PINNED_CLIENT_ITEM_ON_ITEM_WRITER"
                .equals(metadata.source)||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "metadata="+metadata
            );
    }

    private static void writeItemOnItem(
        OutputStream output,
        IsaacCipher cipher,
        int targetSlot,
        int selectedSlot,
        int targetItem,
        int selectedWidget,
        int selectedItem,
        int targetWidget
    )throws IOException{
        writeOpcode(output,cipher,53);
        writeBe(output,targetSlot);
        writeBeA(output,selectedSlot);
        writeLeA(output,targetItem);
        writeBe(output,selectedWidget);
        writeLe(output,selectedItem);
        writeBe(output,targetWidget);
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
