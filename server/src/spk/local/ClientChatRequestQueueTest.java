package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

public final class ClientChatRequestQueueTest {
    private static final int[] SEED={
        0x11223344,
        0x55667788,
        0x10203040,
        0x13579BDF
    };

    private static final byte[] HELLO_WORLD={
        0x06,
        0x01,
        0x0B,
        0x0B,
        0x04,
        0x00,
        0x0E,
        0x04,
        0x09,
        0x0B,
        0x0A,
        0x26
    };

    private static final byte[] TEST_YES={
        0x02,
        0x01,
        0x08,
        0x02,
        0x27,
        0x00,
        0x10,
        0x01,
        0x08,
        0x28
    };

    public static void main(String[] args)throws Exception{
        assertExactCodecVectors();
        testTypedPublicPrivateFifo();
        testMalformedTableIndexFailsClosed();

        System.out.println(
            "CLIENT_CHAT_REQUEST_QUEUE_PASS "+
            "opcodes=4_126 "+
            "v308RsPVector=true "+
            "publicReverseAdd128=true "+
            "privateDirectTable=true "+
            "fifo=true "+
            "metadata=true "+
            "framingOnlyRetired=true "+
            "malformedIndexRejected=true "+
            "domainMutationFailClosed=true"
        );
    }

    private static void assertExactCodecVectors(){
        if(ClientChatTextCodec.tableSize()!=66)
            throw new AssertionError(
                "chat table size="+
                ClientChatTextCodec.tableSize()
            );

        if(!"Hello world!".equals(
                ClientChatTextCodec.decode(
                    HELLO_WORLD
                )))
            throw new AssertionError(
                "hello-world exact vector mismatch"
            );

        if(!"Test? Yes.".equals(
                ClientChatTextCodec.decode(
                    TEST_YES
                )))
            throw new AssertionError(
                "sentence casing exact vector mismatch"
            );
    }

    private static void testTypedPublicPrivateFifo()
        throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        IsaacCipher encoder=
            new IsaacCipher(
                SEED.clone()
            );

        int effect=5;
        int colour=9;
        long recipientNameKey=
            0x00123456789ABCDEFL;

        writePublicChat(
            wire,
            encoder,
            effect,
            colour,
            HELLO_WORLD
        );

        writePrivateMessage(
            wire,
            encoder,
            recipientNameKey,
            TEST_YES
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(
                    SEED.clone()
                ),
                "[typed-chat] "
            );

        if(!probe.readNextKnownPacket()||
           !probe.readNextKnownPacket())
            throw new AssertionError(
                "chat packets did not decode"
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

        PublicChatClientRequest publicChat=
            assertPublicChat(
                probe.takeTypedRequest(),
                effect,
                colour,
                "Hello world!"
            );

        PrivateMessageClientRequest privateMessage=
            assertPrivateMessage(
                probe.takeTypedRequest(),
                recipientNameKey,
                "Test? Yes."
            );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "typed request queue not empty"
            );

        if(ClientPacketProbe
                .isFramingOnlyVarByte(4)||
           ClientPacketProbe
                .isFramingOnlyVarByte(126))
            throw new AssertionError(
                "chat opcodes remained framing-only"
            );

        if(!ClientPacketProbe
                .isFramingOnlyVarByte(246))
            throw new AssertionError(
                "telemetry opcode246 lost framing-only status"
            );

        assertDiagnostic(
            LocalPendingRequestDispatcher
                .publicChatFailClosedDiagnostic(
                    publicChat
                ),
            "PUBLIC_CHAT_REQUEST_FAIL_CLOSED",
            "SERVER_PUBLIC_CHAT_POLICY_UNPROVEN"
        );

        assertDiagnostic(
            LocalPendingRequestDispatcher
                .privateMessageFailClosedDiagnostic(
                    privateMessage
                ),
            "PRIVATE_MESSAGE_REQUEST_FAIL_CLOSED",
            "RECIPIENT_MAPPING_AND_PM_POLICY_UNPROVEN"
        );
    }

    private static PublicChatClientRequest
        assertPublicChat(
            ClientRequest request,
            int effect,
            int colour,
            String message
        ){
        if(!(request instanceof
                PublicChatClientRequest))
            throw new AssertionError(
                "public-chat request="+
                request
            );

        PublicChatClientRequest typed=
            (PublicChatClientRequest)request;

        if(typed.effect()!=effect||
           typed.colour()!=colour||
           !message.equals(
                typed.message()
           ))
            throw new AssertionError(
                "public-chat values="+typed
            );

        assertMetadata(
            typed.metadata(),
            4,
            "VARBYTE_EFFECT_128_MINUS_COLOUR_128_MINUS_REVERSED_CHAT_TABLE_ADD128",
            "V308_CLIENT_PUBLIC_CHAT_WRITER"
        );

        return typed;
    }

    private static PrivateMessageClientRequest
        assertPrivateMessage(
            ClientRequest request,
            long recipientNameKey,
            String message
        ){
        if(!(request instanceof
                PrivateMessageClientRequest))
            throw new AssertionError(
                "private-message request="+
                request
            );

        PrivateMessageClientRequest typed=
            (PrivateMessageClientRequest)request;

        if(typed.recipientNameKey()!=
                recipientNameKey||
           !message.equals(
                typed.message()
           ))
            throw new AssertionError(
                "private-message values="+typed
            );

        assertMetadata(
            typed.metadata(),
            126,
            "VARBYTE_RECIPIENT_NAME_KEY_I64_BE_CHAT_TABLE",
            "V308_CLIENT_PRIVATE_MESSAGE_WRITER"
        );

        return typed;
    }

    private static void testMalformedTableIndexFailsClosed(){
        boolean rejected=false;

        try{
            ClientChatTextCodec.decode(
                new byte[]{
                    66
                }
            );
        }catch(IllegalArgumentException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "chat table index=66"
                );
        }

        if(!rejected)
            throw new AssertionError(
                "invalid chat table index was accepted"
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

    private static void assertDiagnostic(
        String value,
        String prefix,
        String reason
    ){
        if(value==null||
           !value.startsWith(prefix)||
           !value.contains(
                "reason="+reason
           )||
           !value.contains(
                "stateMutation=false"
           )||
           !value.contains(
                "authority=EXACT_CURRENT_CLIENT"
           ))
            throw new AssertionError(
                "fail-closed diagnostic="+value
            );
    }

    private static void writePublicChat(
        OutputStream output,
        IsaacCipher cipher,
        int effect,
        int colour,
        byte[] encoded
    )throws Exception{
        writeOpcode(
            output,
            cipher,
            4
        );

        output.write(
            encoded.length+2
        );
        output.write(
            (128-effect)&255
        );
        output.write(
            (128-colour)&255
        );

        for(int i=encoded.length-1;
            i>=0;
            i--)
            output.write(
                ((encoded[i]&255)+128)&255
            );
    }

    private static void writePrivateMessage(
        OutputStream output,
        IsaacCipher cipher,
        long recipientNameKey,
        byte[] encoded
    )throws Exception{
        writeOpcode(
            output,
            cipher,
            126
        );

        output.write(
            encoded.length+8
        );
        Binary.put64(
            output,
            recipientNameKey
        );
        output.write(encoded);
    }

    private static void writeOpcode(
        OutputStream output,
        IsaacCipher cipher,
        int opcode
    )throws Exception{
        output.write(
            (opcode+cipher.nextInt())&255
        );
    }

    private ClientChatRequestQueueTest(){}
}
