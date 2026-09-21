package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ClientUiRequestQueueTest {
    public static void main(String[] args)throws Exception{
        int[] seed={
            0x0A0B0C0D,
            0x10203040,
            0x55667788,
            0x13572468
        };

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        IsaacCipher encoder=
            new IsaacCipher(
                seed.clone()
            );

        writeOpcode(
            wire,
            encoder,
            130
        );
        writeWidget(
            wire,
            encoder,
            152
        );
        writeOpcode(
            wire,
            encoder,
            130
        );
        writeCommand(
            wire,
            encoder,
            "::authority"
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(
                    seed.clone()
                ),
                "[typed-ui-test] "
            );

        for(int i=0;i<4;i++)
            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "decode stopped index="+i
                );

        if(!probe.isAligned())
            throw new AssertionError(
                "decoder lost alignment"
            );

        if(probe.typedRequestCount()!=4)
            throw new AssertionError(
                "typed request count="+
                probe.typedRequestCount()
            );

        assertInterfaceClose(
            probe.takeTypedRequest()
        );
        assertWidget(
            probe.takeTypedRequest(),
            152
        );
        assertInterfaceClose(
            probe.takeTypedRequest()
        );

        ClientRequest finalRequest=
            probe.takeTypedRequest();

        if(!(finalRequest instanceof
                CommandClientRequest)||
           !"::authority".equals(
                ((CommandClientRequest)finalRequest)
                    .command()))
            throw new AssertionError(
                "cross-family FIFO command="+
                finalRequest
            );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "queue not empty"
            );

        System.out.println(
            "CLIENT_UI_REQUEST_QUEUE_PASS "+
            "interfaceCloseRetainedTwice=true "+
            "widget=true "+
            "crossFamilyFifo=true "+
            "metadata=true"
        );
    }

    private static void assertInterfaceClose(
        ClientRequest request
    ){
        if(!(request instanceof
                InterfaceCloseClientRequest))
            throw new AssertionError(
                "expected interface close got "+
                request
            );

        assertMetadata(
            request.metadata(),
            130,
            "FIXED0_INTERFACE_CLOSE",
            "PINNED_CLIENT_CLIENT_BQ"
        );
    }

    private static void assertWidget(
        ClientRequest request,
        int expectedWidget
    ){
        if(!(request instanceof
                WidgetActionClientRequest))
            throw new AssertionError(
                "expected widget request got "+
                request
            );

        WidgetActionClientRequest widget=
            (WidgetActionClientRequest)request;

        if(widget.widgetId()!=expectedWidget)
            throw new AssertionError(
                "widget expected="+expectedWidget+
                " actual="+widget.widgetId()
            );

        assertMetadata(
            widget.metadata(),
            185,
            "FIXED2_WIDGET_U16_BE",
            "PINNED_CLIENT_OPCODE_185_ALL_CALLSITES"
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

    private static void writeOpcode(
        OutputStream output,
        IsaacCipher cipher,
        int opcode
    )throws IOException{
        output.write(
            (opcode+cipher.nextInt())&255
        );
    }

    private static void writeWidget(
        OutputStream output,
        IsaacCipher cipher,
        int widget
    )throws IOException{
        writeOpcode(
            output,
            cipher,
            185
        );
        output.write((widget>>>8)&255);
        output.write(widget&255);
    }

    private static void writeCommand(
        OutputStream output,
        IsaacCipher cipher,
        String command
    )throws IOException{
        byte[] text=
            command.getBytes(
                StandardCharsets.ISO_8859_1
            );

        writeOpcode(
            output,
            cipher,
            103
        );
        output.write(text.length+1);
        output.write(text);
        output.write(10);
    }
}
