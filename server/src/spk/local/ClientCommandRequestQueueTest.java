package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ClientCommandRequestQueueTest {
    public static void main(String[] args)throws Exception{
        testTypedCommandFifoAndMetadata();
        testBoundedBackpressure();

        System.out.println(
            "CLIENT_COMMAND_REQUEST_QUEUE_PASS "+
            "fifo=true "+
            "metadata=true "+
            "capacity="+
                ClientRequestQueue.DEFAULT_CAPACITY+
            " overflowFailClosed=true"
        );
    }

    private static void testTypedCommandFifoAndMetadata()
        throws Exception{
        int[] seed={
            0x01020304,
            0x11223344,
            0x55667788,
            0x99AABBCC
        };

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        IsaacCipher encoder=
            new IsaacCipher(
                seed.clone()
            );

        writeCommand(
            wire,
            encoder,
            "::one"
        );
        writeCommand(
            wire,
            encoder,
            "::two"
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(
                    seed.clone()
                ),
                "[typed-command-test] "
            );

        if(!probe.readNextKnownPacket()||
           !probe.readNextKnownPacket())
            throw new AssertionError(
                "command packets did not decode"
            );

        if(!probe.isAligned())
            throw new AssertionError(
                "decoder lost alignment"
            );

        if(probe.typedRequestCount()!=2)
            throw new AssertionError(
                "typed request count="+
                probe.typedRequestCount()
            );

        assertCommand(
            probe.takeTypedRequest(),
            "::one"
        );
        assertCommand(
            probe.takeTypedRequest(),
            "::two"
        );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "typed request queue not empty"
            );
    }

    private static void testBoundedBackpressure()
        throws Exception{
        int[] seed={
            0x10203040,
            0x50607080,
            0x11224488,
            0x13579BDF
        };

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        IsaacCipher encoder=
            new IsaacCipher(
                seed.clone()
            );

        for(int i=0;
            i<ClientRequestQueue.DEFAULT_CAPACITY+1;
            i++)
            writeCommand(
                wire,
                encoder,
                "::q"+i
            );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(
                    seed.clone()
                ),
                "[typed-command-overflow] "
            );

        for(int i=0;
            i<ClientRequestQueue.DEFAULT_CAPACITY;
            i++){
            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "decoder stopped before capacity index="+
                    i
                );
        }

        if(probe.typedRequestCount()!=
                ClientRequestQueue.DEFAULT_CAPACITY)
            throw new AssertionError(
                "queue count before overflow="+
                probe.typedRequestCount()
            );

        boolean rejected=false;

        try{
            probe.readNextKnownPacket();
        }catch(IOException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "CLIENT_REQUEST_QUEUE_FULL"
                )&&
                expected.getMessage().contains(
                    "opcode=103"
                );
        }

        if(!rejected)
            throw new AssertionError(
                "65th typed command was not rejected"
            );

        if(probe.isAligned())
            throw new AssertionError(
                "overflow did not fail decoder closed"
            );

        if(probe.typedRequestCount()!=
                ClientRequestQueue.DEFAULT_CAPACITY)
            throw new AssertionError(
                "overflow changed queued request count="+
                probe.typedRequestCount()
            );

        for(int i=0;
            i<ClientRequestQueue.DEFAULT_CAPACITY;
            i++)
            assertCommand(
                probe.takeTypedRequest(),
                "::q"+i
            );
    }

    private static void assertCommand(
        ClientRequest request,
        String expected
    ){
        if(!(request instanceof
                CommandClientRequest))
            throw new AssertionError(
                "request type="+request
            );

        CommandClientRequest command=
            (CommandClientRequest)request;

        if(!expected.equals(
                command.command()))
            throw new AssertionError(
                "command order expected="+
                expected+
                " actual="+command.command()
            );

        ClientRequestMetadata metadata=
            command.metadata();

        if(metadata.opcode!=103||
           !"VAR_BYTE_ISO_8859_1_OPTIONAL_LF".equals(
                metadata.schema
           )||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT||
           !"PINNED_CLIENT_OPCODE_103_WRITER".equals(
                metadata.source
           ))
            throw new AssertionError(
                "command metadata="+metadata
            );
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

        int length=text.length+1;

        if(length>255)
            throw new IllegalArgumentException(
                "test command too long"
            );

        output.write(
            (103+cipher.nextInt())&255
        );
        output.write(length);
        output.write(text);
        output.write(10);
    }
}
