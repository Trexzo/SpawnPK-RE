package spk.local;

import java.io.*;

public final class ClientBankControlRequestQueueTest {
    public static void main(String[] args)throws Exception{
        int[] seed={
            0x11223344,
            0x55667788,
            0x13572468,
            0x24681357
        };

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        IsaacCipher encoder=
            new IsaacCipher(seed.clone());

        writeAmount(
            wire,
            encoder,
            123456789
        );
        writeDrag(
            wire,
            encoder,
            5064,
            0,
            0,
            5
        );
        writeAmount(
            wire,
            encoder,
            -7
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(seed.clone()),
                "[typed-bank-control] "
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

        assertAmount(
            probe.takeTypedRequest(),
            123456789
        );

        ClientRequest dragRequest=
            probe.takeTypedRequest();

        if(!(dragRequest instanceof
                ContainerDragClientRequest))
            throw new AssertionError(
                "expected drag got "+
                dragRequest
            );

        ContainerDragClientRequest typedDrag=
            (ContainerDragClientRequest)dragRequest;

        ContainerDrag drag=
            typedDrag.drag();

        if(drag.widgetId!=5064||
           drag.mode!=0||
           drag.sourceSlot!=0||
           drag.destinationSlot!=5)
            throw new AssertionError(
                "drag="+drag
            );

        assertMetadata(
            typedDrag.metadata(),
            214,
            "FIXED7_WIDGET_LE_A_MODE_NEG_SOURCE_LE_A_DEST_LE",
            "PINNED_CLIENT_CONTAINER_DRAG_WRITER"
        );

        assertAmount(
            probe.takeTypedRequest(),
            -7
        );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "queue not empty"
            );

        System.out.println(
            "CLIENT_BANK_CONTROL_REQUEST_QUEUE_PASS "+
            "amount=true "+
            "drag=true "+
            "signedAmount=true "+
            "fifo=true "+
            "metadata=true"
        );
    }

    private static void assertAmount(
        ClientRequest request,
        int expected
    ){
        if(!(request instanceof
                AmountEntryClientRequest))
            throw new AssertionError(
                "expected amount got "+
                request
            );

        AmountEntryClientRequest amount=
            (AmountEntryClientRequest)request;

        if(amount.amount()!=expected)
            throw new AssertionError(
                "amount expected="+expected+
                " actual="+amount.amount()
            );

        assertMetadata(
            amount.metadata(),
            208,
            "FIXED4_BE_SIGNED_AMOUNT",
            "PINNED_CLIENT_AMOUNT_ENTRY_WRITER"
        );
    }

    private static void assertMetadata(
        ClientRequestMetadata metadata,
        int opcode,
        String schema,
        String source
    ){
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

    private static void writeAmount(
        OutputStream output,
        IsaacCipher cipher,
        int amount
    )throws IOException{
        writeOpcode(
            output,
            cipher,
            208
        );
        output.write((amount>>>24)&255);
        output.write((amount>>>16)&255);
        output.write((amount>>>8)&255);
        output.write(amount&255);
    }

    private static void writeDrag(
        OutputStream output,
        IsaacCipher cipher,
        int widget,
        int mode,
        int source,
        int destination
    )throws IOException{
        writeOpcode(
            output,
            cipher,
            214
        );

        // p(widget): LE short-A
        output.write(((widget&255)+128)&255);
        output.write((widget>>>8)&255);

        // l(mode): negated byte
        output.write((-mode)&255);

        // p(source): LE short-A
        output.write(((source&255)+128)&255);
        output.write((source>>>8)&255);

        // n(destination): plain LE short
        output.write(destination&255);
        output.write((destination>>>8)&255);
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
